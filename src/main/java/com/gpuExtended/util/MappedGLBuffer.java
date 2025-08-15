package com.gpuExtended.util;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL30C.GL_MAP_WRITE_BIT;
import static org.lwjgl.opengl.GL30C.glMapBufferRange;
import static org.lwjgl.opengl.GL44C.GL_DYNAMIC_STORAGE_BIT;
import static org.lwjgl.opengl.GL44C.glBufferStorage;
import static org.lwjgl.opengl.GL45C.*;

public class MappedGLBuffer implements AutoCloseable {
    // NOTE: You don't have to flip this buffer! When you call .putFloat(x) or similar it is automatically written to the GPU
    public ByteBuffer cpuBuffer = null;
    public int glBufferId;
    public int capacity = 0;
    private final int MINIMUM_SIZE_BYTES = 65536;
    private final String debugLabel;
    // NOTE: This uses named buffers and opengl direct state access https://www.khronos.org/opengl/wiki/Direct_State_Access
    public MappedGLBuffer() {
        this("Unnamed MappedGLBuffer");
    }

    public MappedGLBuffer(String debugLabel) {
        this.debugLabel = debugLabel;
    }


    public void InitBufferIfNeeded() {
        if (glBufferId == 0) {
            glBufferId = glCreateBuffers();
            assert glBufferId != 0;
            glObjectLabel(GL_BUFFER, glBufferId, this.debugLabel);
            glNamedBufferStorage(glBufferId, MINIMUM_SIZE_BYTES, GL_DYNAMIC_STORAGE_BIT | GL_MAP_WRITE_BIT);
            cpuBuffer = glMapNamedBuffer(glBufferId, GL_MAP_WRITE_BIT);
            capacity = MINIMUM_SIZE_BYTES;
        }
    }

    public void reserveBytes(int requestedSize)
    {
        InitBufferIfNeeded();
        assert cpuBuffer != null;
        int requiredCapacity = cpuBuffer.position() + requestedSize;
        if (requiredCapacity <= capacity) return; // Enough space to write this

        // Otherwise time to increase the size
        int validDataSize = cpuBuffer.position(); // We only want to copy valid data to the new buffer, but there may be some bytes at the end that were never written to
        int newCapacity = Math.max(MINIMUM_SIZE_BYTES, nextPowerOfTwo(requiredCapacity));
        glUnmapNamedBuffer(glBufferId); cpuBuffer = null;

        // Copy to new buffer
        int newBufferId = glCreateBuffers();
        glObjectLabel(GL_BUFFER, glBufferId, this.debugLabel);
        glNamedBufferStorage(newBufferId, newCapacity, GL_DYNAMIC_STORAGE_BIT | GL_MAP_WRITE_BIT);
        glCopyNamedBufferSubData(glBufferId, newBufferId, 0, 0, validDataSize);

        capacity = newCapacity;
        glDeleteBuffers(glBufferId); // Delete old buffer
        glBufferId = newBufferId;

        cpuBuffer = glMapNamedBuffer(glBufferId, GL_MAP_WRITE_BIT);
        cpuBuffer.position(validDataSize); // Start back where we left off
    }

    public static int nextPowerOfTwo(int x)
    {
        if (x <= 0) return 1;
        x--;
        x |= x >> 1;
        x |= x >> 2;
        x |= x >> 4;
        x |= x >> 8;
        x |= x >> 16;
        x++;
        return x;
    }

    @Override
    public void close() throws Exception {
        if (glBufferId != 0) {
            if (cpuBuffer != null) {
                glUnmapNamedBuffer(glBufferId);
            }
            glDeleteBuffers(glBufferId);
            glBufferId = 0;
            cpuBuffer = null;
            capacity = 0;
        }
    }
}
