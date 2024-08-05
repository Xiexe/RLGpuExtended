package com.gpuExtended.rendering;

import lombok.Getter;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_BASE_LEVEL;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL;
import static org.lwjgl.opengl.GL13C.GL_CLAMP_TO_BORDER;
import static org.lwjgl.opengl.GL14C.GL_DEPTH_COMPONENT24;
import static org.lwjgl.opengl.GL30C.glGenerateMipmap;
import static org.lwjgl.opengl.GL30C.glTexParameterIi;


public class Texture2D {
    public static int MIP_LEVELS = 6;

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
    @Getter
    private int id;
    @Getter
    private int width;
    @Getter
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

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, settings.minFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, settings.magFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, settings.wrapS);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, settings.wrapT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, MIP_LEVELS);

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

    public TextureSettings getSettings() {
        return textureSettings;
    }

    public void cleanup() {

        GL11.glDeleteTextures(id);
    }

    public void generateMipmaps() {
        bind();
        glGenerateMipmap(GL_TEXTURE_2D);
        unbind();
    }
}

