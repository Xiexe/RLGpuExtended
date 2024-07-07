package com.gpuExtended.rendering;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL13C.GL_CLAMP_TO_BORDER;
import static org.lwjgl.opengl.GL14C.GL_DEPTH_COMPONENT24;

public class Texture {
    private final int id;
    private final int width;
    private final int height;

    public Texture(int width, int height) {
        this.width = width;
        this.height = height;
        this.id = GL11.glGenTextures();
        bind();
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_DEPTH_COMPONENT24,
                width,
                height,
                0,
                GL_DEPTH_COMPONENT,
                GL_FLOAT,
                0
        );
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        unbind();
    }

    public void bind() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
    }

    public void unbind() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    public int getId() {
        return id;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void cleanup() {
        GL11.glDeleteTextures(id);
    }
}

