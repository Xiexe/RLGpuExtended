package com.gpuExtended.util.contexts;

import com.gpuExtended.util.GpuFloatBuffer;
import com.gpuExtended.util.GpuIntBuffer;

import static org.lwjgl.opengl.GL15C.glGenBuffers;
import static org.lwjgl.opengl.GL30C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;

public class VertexBufferContext {
    public int vertexArrayObjectId;
    public int vertexBufferObjectId;
    public GpuIntBuffer vertexBuffer;
    public GpuFloatBuffer uvBuffer;
    public GpuFloatBuffer normalBuffer;
    public GpuIntBuffer flagsBuffer;

    public VertexBufferContext() {
        this.vertexArrayObjectId = -1; // Default to -1 to indicate uninitialized
        this.vertexBufferObjectId = -1; // Default to -1 to indicate uninitialized
        this.vertexBuffer = new GpuIntBuffer();
        this.uvBuffer = new GpuFloatBuffer();
        this.normalBuffer = new GpuFloatBuffer();
        this.flagsBuffer = new GpuIntBuffer();
    }

    public void GenArrayAndBuffer() {
        this.vertexArrayObjectId = glGenVertexArrays();
        this.vertexBufferObjectId = glGenBuffers();
    }

    public void FlipBuffers() {
        this.vertexBuffer.flip();
        this.uvBuffer.flip();
        this.normalBuffer.flip();
        this.flagsBuffer.flip();
    }

    public void Dispose() {
        glDeleteVertexArrays(vertexArrayObjectId);

        this.vertexArrayObjectId = -1;
        this.vertexBufferObjectId = -1;

        this.vertexBuffer = null;
        this.uvBuffer = null;
        this.normalBuffer = null;
        this.flagsBuffer = null;
    }
}
