package com.gpuExtended.rendering;

import lombok.Getter;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;
import static org.lwjgl.opengl.GL13C.GL_CLAMP_TO_BORDER;

public class Texture3D {
    //todo: wrapr
    public static class TextureSettings {
        public int level;
        public int internalFormat;
        public int width;
        public int height;
        public int depth;
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
    @Getter
    private int depth;
    private final TextureSettings textureSettings;

    public Texture3D(TextureSettings settings) {
        this.textureSettings = settings;
        this.width = settings.width;
        this.height = settings.height;
        this.depth = settings.depth;
        this.id = GL11.glGenTextures();
        bind();
        glTexImage3D(
                GL_TEXTURE_3D,
                0,
                settings.internalFormat,
                settings.width,
                settings.height,
                settings.depth,
                0,
                settings.format,
                settings.type,
                0
        );

        //todo: use texturesettings for wrap and filter things pls
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_BORDER);
        unbind();
    }

    public void setPixel(int x, int y, int z, int r, int g, int b, int a) {
        bind();

        ByteBuffer pixelData = BufferUtils.createByteBuffer(4);
        pixelData
                .put((byte)r)
                .put((byte)g)
                .put((byte)b)
                .put((byte)a);
        pixelData.flip();

        glTexSubImage3D(
                GL_TEXTURE_3D,
                0,
                x,
                y,
                z,
                1,
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
        ByteBuffer pixelData = BufferUtils.createByteBuffer(width * height * depth * 4);
        for (int i = 0; i < width * height * depth; i++)
        {
            pixelData
                    .put((byte) r)
                    .put((byte) g)
                    .put((byte) b)
                    .put((byte) a);
        }

        pixelData.flip();
        glTexSubImage3D(
                GL_TEXTURE_3D,
                0,
                0,
                0,
                0,
                width,
                height,
                depth,
                textureSettings.format,
                textureSettings.type,
                pixelData
        );
        unbind();
    }

    public void bind() {
        glBindTexture(GL_TEXTURE_3D, id);
    }

    public void unbind() {

        glBindTexture(GL_TEXTURE_3D, 0);
    }

    public TextureSettings getSettings() {
        return textureSettings;
    }

    public void cleanup() {
        glDeleteTextures(id);
    }
}

