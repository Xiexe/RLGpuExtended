package com.gpuExtended.rendering;

import lombok.Getter;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL11;
import net.runelite.rlawt.AWTContext;

public class FrameBufferObject {
    private final int id;
    @Getter
    private final Texture texture;

    private AWTContext awtContext;

    public FrameBufferObject(int width, int height, AWTContext awtContext) {
        this.awtContext = awtContext;
        this.id = GL30.glGenFramebuffers();
        bind();

        texture = new Texture(width, height);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, texture.getId(), 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);

        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer is not complete!");
        }

        unbind();
    }

    public void bind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, id);
    }

    public void unbind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, awtContext.getFramebuffer(false));
    }

    public int getId() {
        return id;
    }

    public void cleanup() {
        GL30.glDeleteFramebuffers(id);
        texture.cleanup();
    }
}
