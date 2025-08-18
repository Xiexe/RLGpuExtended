package com.gpuExtended.util.contexts;

import com.gpuExtended.opengl.GLBuffer;
import com.gpuExtended.util.GpuIntBuffer;
import com.gpuExtended.util.MappedGLBuffer;
import lombok.extern.slf4j.Slf4j;

import static com.gpuExtended.util.constants.Variables.MAX_TRIANGLE;
import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL43C.glObjectLabel;

@Slf4j
public class ComputeBufferContext {
    // buffers used for outputting from compute shader
    public GLBuffer vertexOutBuffer;
    public GLBuffer uvOutBuffer;
    public GLBuffer normalOutBuffer;
    public GLBuffer flagsOutBuffer;

    // Buffers used for static models
    public GLBuffer staticVertexInBuffer;
    public GLBuffer staticUvInBuffer;
    public GLBuffer staticNormalInBuffer;
    public GLBuffer staticFlagsInBuffer;

    // Buffers used for dynamic models
    public MappedGLBuffer _dynamicVertexInBuffer; // TODO: This needs setup
    public MappedGLBuffer _dynamicUvInBuffer;
    public MappedGLBuffer _dynamicNormalInBuffer;
    public MappedGLBuffer _dynamicFlagsBuffer;

    public GLBuffer dynamicVertexInBuffer;
    public GLBuffer dynamicUvInBuffer;
    public GLBuffer dynamicNormalInBuffer;
    public GLBuffer dynamicFlagsBuffer;

    // Buffers used for model sorting
    public GLBuffer tmpUnsortedModelBuffer;

    public GpuIntBuffer unsortedModelBuffer;
    public MappedGLBuffer[] sortedModelInfos;
    public int[] numSortedModels;

    public int numUnsortedModels;

    public int totalVertices;
    public int totalDynamicVertices;
    public int totalDynamicUvs;

    public ComputeBufferContext() {

        this.unsortedModelBuffer = new GpuIntBuffer();

        // Output
        this.vertexOutBuffer = new GLBuffer("vertex out buffer");
        this.uvOutBuffer = new GLBuffer("uv out buffer");
        this.normalOutBuffer = new GLBuffer("normal out buffer");
        this.flagsOutBuffer = new GLBuffer("model flags out buffer");

        // Static input
        this.staticVertexInBuffer = new GLBuffer("static model vertex in buffer");
        this.staticUvInBuffer = new GLBuffer("static model uv in buffer");
        this.staticNormalInBuffer = new GLBuffer("static model normal in buffer");
        this.staticFlagsInBuffer = new GLBuffer("static model flags in buffer");

        // Dynamic input
        this.dynamicVertexInBuffer = new GLBuffer("dynamic model vertex buffer");
        this.dynamicUvInBuffer = new GLBuffer("dynamic model uv buffer");
        this.dynamicNormalInBuffer = new GLBuffer("dynamic model normal buffer");
        this.dynamicFlagsBuffer = new GLBuffer("dynamic model flags buffer");

        _dynamicVertexInBuffer = new MappedGLBuffer("dynamic model vertex buffer");
        _dynamicUvInBuffer = new MappedGLBuffer("dynamic model uv buffer");
        _dynamicNormalInBuffer = new MappedGLBuffer("dynamic model normal buffer");
        _dynamicFlagsBuffer = new MappedGLBuffer("dynamic model flags buffer");

        // Model Sorting buffers
        this.tmpUnsortedModelBuffer = new GLBuffer("unsorted model buffer");

        InitGLBuffer(this.vertexOutBuffer);
        InitGLBuffer(this.uvOutBuffer);
        InitGLBuffer(this.normalOutBuffer);
        InitGLBuffer(this.flagsOutBuffer);

        InitGLBuffer(this.staticVertexInBuffer);
        InitGLBuffer(this.staticUvInBuffer);
        InitGLBuffer(this.staticNormalInBuffer);
        InitGLBuffer(this.staticFlagsInBuffer);

        InitGLBuffer(this.dynamicVertexInBuffer);
        InitGLBuffer(this.dynamicUvInBuffer);
        InitGLBuffer(this.dynamicNormalInBuffer);
        InitGLBuffer(this.dynamicFlagsBuffer);

        InitGLBuffer(this.tmpUnsortedModelBuffer);

        InitSortedModelBuffers();
    }

    public void ResetSortedModelBufferCounts() {
        for(int i = 0; i < numSortedModels.length; i++) {
            this.numSortedModels[i] = 0;
        }
    }

    public void OrphanSortedModelBuffers() {
        for(int i = 0; i < numSortedModels.length; i++) {
            this.sortedModelInfos[i].OrphanAndReset();
        }
    }

    private void InitSortedModelBuffers() {
        // Sizes are powers of two starting at 64
        // 64 chosen as minimum because nvidia typically has 32 warp size and AMD has 64, so max between these two is a good default
        // 64
        // 128
        // 256
        // 512
        // 1024
        // 2048
        // 4096
        // MAX_TRIANGLE = 1024*6 = 6144
        // 8 different sizes
        this.sortedModelInfos = new MappedGLBuffer[8];
        this.numSortedModels = new int[8];
        for(int i = 0; i < numSortedModels.length; i++) {
            int size = 64 << i;
            this.sortedModelInfos[i] = new MappedGLBuffer("sorted models size=" + size);
            this.sortedModelInfos[i].InitBufferIfNeeded();
            this.numSortedModels[i] = 0;
        }
    }

    public static int log2(int bits) // returns 0 for bits=0
    {
        double x = Math.log(bits) / Math.log(2);
        return (int)Math.ceil(x);
    }

    public MappedGLBuffer GetCorrectModelBufferForTriangleCount(int triangles) {
        assert triangles > 0 : "Triangle count was " + triangles;
        assert triangles <= MAX_TRIANGLE : "Triangle count was greater than MAX_TRIANGLE=" + MAX_TRIANGLE + " triangles=" + triangles;
        int log = log2(triangles);
        if (log < 6) log = 6; // minimum 64. 6 == log2(64)

        this.numSortedModels[log-6]++;
        MappedGLBuffer buffer = this.sortedModelInfos[log-6];
        return buffer;
    }

    private void InitGLBuffer(GLBuffer glBuffer) {
        glBuffer.glBufferId = glGenBuffers();
        log.info("Initialized GLBuffer: {}, {}", glBuffer.glBufferId, glBuffer.name);
    }

    public void Clear() {
        this.numUnsortedModels = 0;

        this.totalVertices = 0;
    }

    public void Dispose() {
        this.vertexOutBuffer = null;
        this.uvOutBuffer = null;
        this.normalOutBuffer = null;
        this.flagsOutBuffer = null;

        this.staticVertexInBuffer = null;
        this.staticUvInBuffer = null;
        this.staticNormalInBuffer = null;
        this.staticFlagsInBuffer = null;

        this.dynamicVertexInBuffer = null;
        this.dynamicUvInBuffer = null;
        this.dynamicNormalInBuffer = null;
        this.dynamicFlagsBuffer = null;

        this.tmpUnsortedModelBuffer = null;

        this.unsortedModelBuffer = null;

        this.numUnsortedModels = 0;
        this.ResetSortedModelBufferCounts();

        for(int i = 0; i < sortedModelInfos.length; i++) {
            sortedModelInfos[i].Dispose();
        }
        this.numSortedModels = null;
        this.sortedModelInfos = null;
        this.totalVertices = 0;

        _dynamicVertexInBuffer.Dispose();
        _dynamicUvInBuffer.Dispose();
        _dynamicNormalInBuffer.Dispose();
        _dynamicFlagsBuffer.Dispose();
    }
}
