package com.gpuExtended.util.contexts;

import com.gpuExtended.util.GpuFloatBuffer;
import com.gpuExtended.util.GpuIntBuffer;

public class VertexBufferContext {
    public int vertexArrayObjectId;
    public int vertexBufferObjectId;
    public GpuFloatBuffer vertexBuffer;
    public GpuFloatBuffer uvBuffer;
    public GpuFloatBuffer normalBuffer;
    public GpuIntBuffer flagsBuffer;

    public VertexBufferContext() {
        this.vertexArrayObjectId = -1; // Default to -1 to indicate uninitialized
        this.vertexBufferObjectId = -1; // Default to -1 to indicate uninitialized
        this.vertexBuffer = new GpuFloatBuffer();
        this.uvBuffer = new GpuFloatBuffer();
        this.normalBuffer = new GpuFloatBuffer();
        this.flagsBuffer = new GpuIntBuffer();
    }

    public void FlipBuffers() {
        vertexBuffer.flip();
        uvBuffer.flip();
        normalBuffer.flip();
        flagsBuffer.flip();
    }
}
