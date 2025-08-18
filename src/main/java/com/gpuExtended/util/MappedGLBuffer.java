package com.gpuExtended.util;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL30C.GL_MAP_WRITE_BIT;
import static org.lwjgl.opengl.GL30C.glMapBufferRange;
import static org.lwjgl.opengl.GL44C.GL_DYNAMIC_STORAGE_BIT;
import static org.lwjgl.opengl.GL44C.glBufferStorage;
import static org.lwjgl.opengl.GL45C.*;

public class MappedGLBuffer {
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
            cpuBuffer = glMapNamedBuffer(glBufferId,  GL_WRITE_ONLY);
            if (cpuBuffer == null) {
                throw new RuntimeException("Failed to map buffer after creating. ID: " + glBufferId);
            }
            capacity = MINIMUM_SIZE_BYTES;
        }
    }

    // Call this every frame to reset the buffer and orphan the old buffer to prevent CPU/GPU stalls
    public void OrphanAndReset() {
        InitBufferIfNeeded();
        assert glBufferId != 0 : "Buffer has not been initialized.";
        // NOTE: Unmapping and remapping isn't the best way to do this, but it's easy to work with and prevents stalls
        glUnmapNamedBuffer(glBufferId);
        cpuBuffer = glMapNamedBuffer(glBufferId, GL_WRITE_ONLY);
        if (cpuBuffer == null) {
            throw new RuntimeException("Failed to map buffer after orphaning. ID: " + glBufferId);
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
        glUnmapNamedBuffer(glBufferId);
        cpuBuffer = null;

        // Copy to new buffer
        int newBufferId = glCreateBuffers();
        glObjectLabel(GL_BUFFER, glBufferId, this.debugLabel);
        glNamedBufferStorage(newBufferId, newCapacity, GL_DYNAMIC_STORAGE_BIT | GL_MAP_WRITE_BIT);
        glCopyNamedBufferSubData(glBufferId, newBufferId, 0, 0, validDataSize);

        capacity = newCapacity;
        glDeleteBuffers(glBufferId); // Delete old buffer
        glBufferId = newBufferId;

        cpuBuffer = glMapNamedBuffer(glBufferId, GL_WRITE_ONLY);
        if (cpuBuffer == null) {
            throw new RuntimeException("Failed to map buffer after resizing. ID: " + glBufferId);
        }
        cpuBuffer.position(validDataSize); // Start back where we left off
    }

    // NOTE: This will reserve space for N integers and give you a view to write them to. The view will write to the underlying GPU buffer too.
    public IntBuffer ReserveIntsAndGetView(int numberOfInts) {
        reserveBytes(numberOfInts * Integer.BYTES);
        IntBuffer intBuffer = cpuBuffer.asIntBuffer();
        intBuffer.limit(numberOfInts);
        cpuBuffer.position(cpuBuffer.position() + numberOfInts * Integer.BYTES);
        return intBuffer;
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

    public void Dispose() {
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
