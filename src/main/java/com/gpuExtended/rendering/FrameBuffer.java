package com.gpuExtended.rendering;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL11;
import net.runelite.rlawt.AWTContext;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30C.*;

@Slf4j
public class FrameBuffer {
    public static class FrameBufferSettings {
        public String name;
        public int width;
        public int height;
        public int glAttachment;
        public AWTContext awtContext;
    }

    @Getter
    private final int id;

    @Getter
    private Texture2D texture;

    @Getter
    private Texture2D[] mipChain;

    private FrameBufferSettings settings;

    @Getter
    private boolean isInitialized = false;


    private AWTContext awtContext;

    public FrameBuffer(FrameBufferSettings settings, Texture2D.TextureSettings textureSettings) {
        this.settings = settings;
        this.awtContext = settings.awtContext;
        this.id = GL30.glGenFramebuffers();
        bind();

        textureSettings.width = settings.width;
        textureSettings.height = settings.height;

        texture = new Texture2D(textureSettings);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, settings.glAttachment, GL11.GL_TEXTURE_2D, texture.getId(), 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);

        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer " + settings.name + " is not complete!");
        }

        unbind();

        isInitialized = true;
    }

    public void bind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, id);
    }

    public void unbind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, awtContext.getFramebuffer(false));
    }

    public void dispose() {
        GL30.glDeleteFramebuffers(id);
        texture.cleanup();
        isInitialized = false;
    }

    public void resize(int width, int height) {
        bind();
        Texture2D.TextureSettings texSettings = texture.getSettings();

        this.settings.width = width;
        this.settings.height = height;
        texSettings.width = width;
        texSettings.height = height;

        texture.cleanup();
        texture = new Texture2D(texSettings);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, settings.glAttachment, GL11.GL_TEXTURE_2D, texture.getId(), 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);

        unbind();
    }

    public void blit(FrameBuffer target, int srcAttachment, int dstAttachment, int interpolation)
    {
        this.bind();
        glReadBuffer(srcAttachment); // Read from source texture

        target.bind();
        glDrawBuffer(dstAttachment); // Draw to target texture

//                log.info("Blitting framebuffer {} to {}", this.settings.name, target.settings.name);
        GL30.glBlitFramebuffer(
                0,
                0,
                texture.getWidth(),
                texture.getHeight(),
                0,
                0,
                target.getTexture().getWidth(),
                target.getTexture().getHeight(),
                GL_COLOR_BUFFER_BIT,
                interpolation
        );

        this.unbind();
    }

    public void generateMipmaps()
    {
        bind();
        texture.generateMipmaps();
        unbind();
    }

    public void clearFramebuffer()
    {
        this.bind();
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        this.unbind();
    }

    public boolean isComplete() {
        boolean isComplete = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        return isComplete;
    }
}
