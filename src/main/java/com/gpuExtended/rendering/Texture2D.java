package com.gpuExtended.rendering;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL13C.GL_CLAMP_TO_BORDER;
import static org.lwjgl.opengl.GL14C.GL_DEPTH_COMPONENT24;


public class Texture2D {
    public static class TextureSettings {
        public int level;
        public int internalFormat;
        public int width;
        public int height;
        public int border;
        public int format;
        public int type;
        public int pixels;
        public int minFilter;
        public int magFilter;
        public int wrapS;
        public int wrapT;
    }
    private int id;
    private int width;
    private int height;
    private final TextureSettings textureSettings;

    public Texture2D(TextureSettings settings) {
        this.textureSettings = settings;
        this.width = settings.width;
        this.height = settings.height;
        this.id = GL11.glGenTextures();
        bind();
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                settings.internalFormat,
                settings.width,
                settings.height,
                0,
                settings.format,
                settings.type,
                0
        );

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        unbind();
    }

    public void setPixel(int x, int y, int r, int g, int b, int a) {
        bind();

        ByteBuffer pixelData = BufferUtils.createByteBuffer(4);
        pixelData
                .put((byte)r)
                .put((byte)g)
                .put((byte)b)
                .put((byte)a);
        pixelData.flip();

        glTexSubImage2D(
                GL_TEXTURE_2D,
                0,
                x,
                y,
                1,
                1,
                textureSettings.format,
                textureSettings.type,
                pixelData
        );

        unbind();
    }

    public void floodPixels(int r, int g, int b, int a)
    {
        bind();
        ByteBuffer pixelData = BufferUtils.createByteBuffer(width * height * 4);
        for (int i = 0; i < width * height; i++)
        {
            pixelData
                    .put((byte) r)
                    .put((byte) g)
                    .put((byte) b)
                    .put((byte) a);
        }
        pixelData.flip();
        glTexSubImage2D(
                GL_TEXTURE_2D,
                0,
                0,
                0,
                width,
                height,
                textureSettings.format,
                textureSettings.type,
                pixelData
        );
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

    public TextureSettings getSettings() {
        return textureSettings;
    }

    public void cleanup() {

        GL11.glDeleteTextures(id);
    }
}

