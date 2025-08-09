package com.gpuExtended.rendering.passes;

import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.rendering.FrameBuffer;
import com.gpuExtended.rendering.Texture2D;
import com.gpuExtended.shader.ShaderVariables;
import com.gpuExtended.util.GpuFloatBuffer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;

import javax.inject.Inject;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;

@Slf4j
public class PostProcessingPass implements IPassBase {
    @Inject
    public GpuExtendedPlugin plugin;

    public FrameBuffer bloomFramebuffer;

    private int vertexArrayObject;
    private int vertexBufferObject;

    @Override
    public void Init() {
        FrameBuffer.FrameBufferSettings fboSettings = new FrameBuffer.FrameBufferSettings();
        fboSettings.name = "bloom";
        fboSettings.width = 64;
        fboSettings.height = 64;
        fboSettings.glAttachment = GL_COLOR_ATTACHMENT0;
        fboSettings.awtContext = plugin.awtContext;

        Texture2D.TextureSettings textureSettings = new Texture2D.TextureSettings();
        textureSettings.internalFormat = GL_RGBA16F;
        textureSettings.format = GL_RGBA;
        textureSettings.type = GL_FLOAT;
        textureSettings.minFilter = GL_LINEAR_MIPMAP_LINEAR;
        textureSettings.magFilter = GL_LINEAR;
        textureSettings.wrapS = GL_CLAMP_TO_EDGE;
        textureSettings.wrapT = GL_CLAMP_TO_EDGE;

        bloomFramebuffer = new FrameBuffer(fboSettings, textureSettings);
        InitVAO();

        log.info("[Post Processing Pass] Initialized post processing.");
    }

    @Override
    public void Dispose() {
        if (bloomFramebuffer != null)
        {
            bloomFramebuffer.dispose();
            bloomFramebuffer = null;
        }

        glDeleteVertexArrays(vertexArrayObject);
        glDeleteBuffers(vertexBufferObject);
        vertexArrayObject = -1;
        vertexBufferObject = -1;
    }

    @Override
    public void OnPreRenderFrame() {
        if (!bloomFramebuffer.isComplete()) return;

        bloomFramebuffer.clearFramebuffer();
    }

    @Override
    public void OnRenderFrame() {
        RenderBloom(plugin.mainPassLegacy.frameBuffer);
    }

    private void RenderBloom(FrameBuffer primaryFramebuffer) {
        if (!bloomFramebuffer.isComplete()) return;

        primaryFramebuffer.generateMipmaps();
        primaryFramebuffer.blit(bloomFramebuffer, GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT0, GL_LINEAR);

        bloomFramebuffer.bind();
        glBindVertexArray(vertexArrayObject);

        // Prefilter
        glUseProgram(plugin.shaders.bloomPrefilterShader.id());
        ShaderVariables uniP = plugin.uniforms.GetUniforms(plugin.shaders.bloomPrefilterShader.id());
        glActiveTexture(GL_TEXTURE1);

        glBindTexture(GL_TEXTURE_2D, primaryFramebuffer.getTexture().getId());
        glUniform1i(uniP.SourceTexture, 1);

        glViewport(0, 0, bloomFramebuffer.getTexture().getWidth(), bloomFramebuffer.getTexture().getHeight());
        glDrawArrays(GL_TRIANGLE_FAN, 0, 4);
        // ---
        bloomFramebuffer.unbind();

        bloomFramebuffer.generateMipmaps();
        bloomFramebuffer.bind();
        // Downsample
        glUseProgram(plugin.shaders.bloomDownsampleShader.id());
        ShaderVariables uniB = plugin.uniforms.GetUniforms(plugin.shaders.bloomDownsampleShader.id());

        glActiveTexture(GL_TEXTURE1);
        glUniform1i(uniB.SourceTexture, 1);
        glUniform2f(uniB.SourceResolution, bloomFramebuffer.getTexture().getWidth(), bloomFramebuffer.getTexture().getHeight());
        glUniform1i(uniB.MipmapLevel, 0);

        for (int i = 0; i < 6; i++) {
            int mipWidth = bloomFramebuffer.getTexture().getWidth() >> i;
            int mipHeight = bloomFramebuffer.getTexture().getHeight() >> i;

            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, bloomFramebuffer.getTexture().getId(), i);
            glBindTexture(GL_TEXTURE_2D, bloomFramebuffer.getTexture().getId());

            glViewport(0, 0, mipWidth, mipHeight);
            glDrawArrays(GL_TRIANGLE_FAN, 0, 4);

            // Set current mip as src for next iteration
            glUniform2f(uniB.SourceResolution, mipWidth, mipHeight);
            glUniform1i(uniB.MipmapLevel, i);
        }

        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, bloomFramebuffer.getTexture().getId(), 0);
        // ---

        // Upsample
        glUseProgram(plugin.shaders.bloomUpsampleShader.id());
        ShaderVariables uniU = plugin.uniforms.GetUniforms(plugin.shaders.bloomUpsampleShader.id());

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, bloomFramebuffer.getTexture().getId());
        glUniform1i(uniU.SourceTexture, 1);

        glViewport(0, 0, bloomFramebuffer.getTexture().getWidth(), bloomFramebuffer.getTexture().getHeight());
        glDrawArrays(GL_TRIANGLE_FAN, 0, 4);

        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, bloomFramebuffer.getTexture().getId(), 0);
        // ---

        // Reset
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindVertexArray(0);
        glUseProgram(0);
        bloomFramebuffer.unbind();
    }

    // Create a full screen quad.
    private void InitVAO()
    {
        vertexArrayObject = glGenVertexArrays();
        vertexBufferObject = glGenBuffers();
        glBindVertexArray(vertexArrayObject);

        FloatBuffer vboUiBuf = GpuFloatBuffer.allocateDirect(5 * 4);
        vboUiBuf.put(new float[]{
                // positions     // texture coords
                1f, 1f, 0.0f, 1.0f, 0f, // top right
                1f, -1f, 0.0f, 1.0f, 1f, // bottom right
                -1f, -1f, 0.0f, 0.0f, 1f, // bottom left
                -1f, 1f, 0.0f, 0.0f, 0f  // top left
        });
        vboUiBuf.rewind();
        glBindBuffer(GL_ARRAY_BUFFER, vertexBufferObject);
        glBufferData(GL_ARRAY_BUFFER, vboUiBuf, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    @Override
    public void OnPostRenderFrame() {}

    @Override
    public void OnPreLoadScene(Scene scene) {}

    @Override
    public void OnSceneLoadStart(Scene scene) {}

    @Override
    public void OnSceneLoadFinished(Scene scene) {}

    @Override
    public void OnPreDrawScene() {}

    @Override
    public void OnDrawScene() {}

    @Override
    public void OnPostDrawScene() {}

    @Override
    public void OnDrawSceneTile(Scene scene, SceneTilePaint paint, int plane, int tileX, int tileY) {}

    @Override
    public void OnDrawSceneTileModel(Scene scene, SceneTileModel model, int tileX, int tileY) {}

    @Override
    public void OnDrawModel(Projection projection, Scene scene, Renderable renderable, int orientation, int x, int y, int z, long hash) {}

    @Override
    public void OnGameStateChanged(GameStateChanged gameStateChanged) {}
}
