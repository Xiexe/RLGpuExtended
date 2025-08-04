package com.gpuExtended.util.contexts;

import com.gpuExtended.opengl.GLBuffer;
import com.gpuExtended.util.GpuIntBuffer;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.opengl.GL;

import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL15C.glGenBuffers;
import static org.lwjgl.opengl.GL43C.GL_BUFFER;
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

    // Buffers used for dynamic models
    public GLBuffer dynamicVertexInBuffer = new GLBuffer("dynamic model vertex buffer");
    public GLBuffer dynamicUvInBuffer = new GLBuffer("dynamic model uv buffer");
    public GLBuffer dynamicNormalInBuffer = new GLBuffer("dynamic model normal buffer");

    // Buffers used for model sorting
    public GLBuffer tmpUnsortedModelBuffer;
    public GLBuffer tmpLargeModelBuffer;

    public GpuIntBuffer unsortedModelBuffer;
    public GpuIntBuffer largeModelBuffer;
    public int gl_modelPriorityDataBuffer;

    public int numUnsortedModels;
    public int numLargeModels;

    public int totalVertices;
    public int totalDynamicVertices;
    public int totalDynamicUvs;

    public ComputeBufferContext() {

        this.unsortedModelBuffer = new GpuIntBuffer();
        this.largeModelBuffer = new GpuIntBuffer();

        // Output
        this.vertexOutBuffer = new GLBuffer("vertex out buffer");
        this.uvOutBuffer = new GLBuffer("uv out buffer");
        this.normalOutBuffer = new GLBuffer("normal out buffer");
        this.flagsOutBuffer = new GLBuffer("model flags out buffer");

        // Static input
        this.staticVertexInBuffer = new GLBuffer("static model vertex in buffer");
        this.staticUvInBuffer = new GLBuffer("static model uv in buffer");
        this.staticNormalInBuffer = new GLBuffer("static model normal in buffer");

        // Dynamic input
        this.dynamicVertexInBuffer = new GLBuffer("dynamic model vertex buffer");
        this.dynamicUvInBuffer = new GLBuffer("dynamic model uv buffer");
        this.dynamicNormalInBuffer = new GLBuffer("dynamic model normal buffer");
        this.gl_modelPriorityDataBuffer = glGenBuffers();

        // Model Sorting buffers
        this.tmpUnsortedModelBuffer = new GLBuffer("unsorted model buffer");
        this.tmpLargeModelBuffer = new GLBuffer("large model buffer");

        InitGLBuffer(this.vertexOutBuffer);
        InitGLBuffer(this.uvOutBuffer);
        InitGLBuffer(this.normalOutBuffer);
        InitGLBuffer(this.flagsOutBuffer);

        InitGLBuffer(this.staticVertexInBuffer);
        InitGLBuffer(this.staticUvInBuffer);
        InitGLBuffer(this.staticNormalInBuffer);

        InitGLBuffer(this.dynamicVertexInBuffer);
        InitGLBuffer(this.dynamicUvInBuffer);
        InitGLBuffer(this.dynamicNormalInBuffer);

        InitGLBuffer(this.tmpUnsortedModelBuffer);
        InitGLBuffer(this.tmpLargeModelBuffer);
    }

    private void InitGLBuffer(GLBuffer glBuffer) {
        glBuffer.glBufferId = glGenBuffers();
        log.info("Initialized GLBuffer: {}, {}", glBuffer.glBufferId, glBuffer.name);
    }

    public void Clear() {
        this.numUnsortedModels = 0;
        this.numLargeModels = 0;

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

        this.dynamicVertexInBuffer = null;
        this.dynamicUvInBuffer = null;
        this.dynamicNormalInBuffer = null;

        this.tmpUnsortedModelBuffer = null;
        this.tmpLargeModelBuffer = null;

        // TODO: shouldn't we delete all of these?
        glDeleteBuffers(this.gl_modelPriorityDataBuffer);

        this.unsortedModelBuffer = null;
        this.largeModelBuffer = null;

        this.numUnsortedModels = 0;
        this.numLargeModels = 0;

        this.totalVertices = 0;
    }
}
