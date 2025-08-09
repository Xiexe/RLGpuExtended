package com.gpuExtended.util.contexts;

import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.opengl.GLBuffer;
import com.gpuExtended.util.GpuIntBuffer;
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
    public GLBuffer dynamicVertexInBuffer = new GLBuffer("dynamic model vertex buffer");
    public GLBuffer dynamicUvInBuffer = new GLBuffer("dynamic model uv buffer");
    public GLBuffer dynamicNormalInBuffer = new GLBuffer("dynamic model normal buffer");
    public GLBuffer dynamicFlagsBuffer = new GLBuffer("dynamic model flags buffer");

    // Buffers used for model sorting
    public GLBuffer tmpUnsortedModelBuffer;

    public GpuIntBuffer unsortedModelBuffer;

    public GpuIntBuffer[] sortedModelIntBuffers;
    public GLBuffer[] sortedModelGlBuffers;
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
        for(int i = 0; i < 8; i++) {
            this.numSortedModels[i] = 0;
        }
    }

    public void ClearSortedModelBuffer() {
        for(int i = 0; i < 8; i++) {
            this.sortedModelIntBuffers[i].clear();
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
        this.sortedModelIntBuffers = new GpuIntBuffer[8];
        this.sortedModelGlBuffers = new GLBuffer[8];
        this.numSortedModels = new int[8];
        for(int i = 0; i < 8; i++) {
            int size = 64 << i;
            this.sortedModelGlBuffers[i] = new GLBuffer("sorted models size=" + size);
            InitGLBuffer(this.sortedModelGlBuffers[i]);
            this.sortedModelIntBuffers[i] = new GpuIntBuffer();
            this.numSortedModels[i] = 0;
        }
    }

    public void FlipSortedModelBuffers() {
        for(int i = 0; i < 8; i++) {
            this.sortedModelIntBuffers[i].flip();
        }
    }

    public void UpdateSortedModelBuffers(GpuExtendedPlugin plugin) {
        for(int i = 0; i < 8; i++) {
            plugin.updateBuffer(this.sortedModelGlBuffers[i], GL_ARRAY_BUFFER, this.sortedModelIntBuffers[i].getBuffer(), GL_DYNAMIC_DRAW);
        }
    }

    public static int log2(int bits) // returns 0 for bits=0
    {
        double x = Math.log(bits) / Math.log(2);
        return (int)Math.ceil(x);
    }

    public GpuIntBuffer GetCorrectModelBufferForTriangleCount(int triangles) {
        assert triangles > 0 : "Triangle count was " + triangles;
        assert triangles <= MAX_TRIANGLE : "Triangle count was greater than MAX_TRIANGLE=" + MAX_TRIANGLE + " triangles=" + triangles;
        int log = log2(triangles);
        if (log < 6) log = 6; // minimum 64. 6 == log2(64)

        this.numSortedModels[log-6]++;
        GpuIntBuffer buffer = this.sortedModelIntBuffers[log-6];
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

        this.numSortedModels = null;
        this.sortedModelGlBuffers = null;
        this.sortedModelIntBuffers = null;

        this.totalVertices = 0;
    }
}
