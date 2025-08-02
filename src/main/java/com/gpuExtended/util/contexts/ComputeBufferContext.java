package com.gpuExtended.util.contexts;

import com.gpuExtended.opengl.GLBuffer;
import com.gpuExtended.util.GpuIntBuffer;

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
    public GLBuffer tmpSmallModelBuffer;
    public GLBuffer tmpLargeModelBuffer;

    public GpuIntBuffer unsortedModelBuffer;
    public GpuIntBuffer smallModelBuffer;
    public GpuIntBuffer largeModelBuffer;

    public int numUnsortedModels;
    public int numSmallModels;
    public int numLargeModels;

    public ComputeBufferContext() {
        // Output
        vertexOutBuffer = new GLBuffer("vertex out buffer");
        uvOutBuffer = new GLBuffer("uv out buffer");
        normalOutBuffer = new GLBuffer("normal out buffer");
        flagsOutBuffer = new GLBuffer("model flags out buffer");

        // Static input
        staticVertexInBuffer = new GLBuffer("static model vertex in buffer");
        staticUvInBuffer = new GLBuffer("static model uv in buffer");
        staticNormalInBuffer = new GLBuffer("static model normal in buffer");
        staticFlagsInBuffer = new GLBuffer("static model flags in buffer");

        // Dynamic input
        dynamicVertexInBuffer = new GLBuffer("dynamic model vertex buffer");
        dynamicUvInBuffer = new GLBuffer("dynamic model uv buffer");
        dynamicNormalInBuffer = new GLBuffer("dynamic model normal buffer");
        dynamicFlagsBuffer = new GLBuffer("dynamic model flags buffer");

        // Model Sorting buffers
        tmpUnsortedModelBuffer = new GLBuffer("unsorted model buffer");
        tmpSmallModelBuffer = new GLBuffer("small model buffer");
        tmpLargeModelBuffer = new GLBuffer("large model buffer");
    }
}
