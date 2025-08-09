package com.gpuExtended.rendering.passes;

import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.config.UIScalingMode;
import com.gpuExtended.shader.ShaderVariables;
import com.gpuExtended.util.GpuFloatBuffer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;

import javax.inject.Inject;
import java.awt.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static com.gpuExtended.util.constants.Variables.CONFIG_BUFFER_BINDING_ID;
import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL20C.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20C.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL31C.glUniformBlockBinding;

// Composites the rendering, post processing, and UI passes together.
@Slf4j
public class CompositePass implements IPassBase {
    @Inject
    public GpuExtendedPlugin plugin;

    private int compositeTexture;
    private int compositePbo;
    private int vertexArrayObject;
    private int vertexBufferObject;

    private int overlayColor;
    private int lastCanvasWidth;
    private int lastCanvasHeight;

    @Override
    public void Init() {
        InitBuffers();

        compositePbo = glGenBuffers();
        compositeTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, compositeTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, 0);

        lastCanvasWidth = lastCanvasHeight = -1;

        log.info("[Composite Pass] Composite pass initialized.");
    }

    private void InitBuffers() {
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
    public void Dispose() {
        glDeleteBuffers(compositePbo);
        glDeleteTextures(compositeTexture);
        compositeTexture = -1;

        glDeleteBuffers(vertexBufferObject);
        vertexBufferObject = -1;

        glDeleteVertexArrays(vertexArrayObject);
        vertexArrayObject = -1;
    }

    @Override
    public void OnPreRenderFrame() {
        PrepareTexture();
    }

    @Override
    public void OnRenderFrame() {

    }

    @Override
    public void OnPostRenderFrame() {
        final int canvasHeight = plugin.client.getCanvasHeight();
        final int canvasWidth = plugin.client.getCanvasWidth();

        // Use the texture bound in the first pass
        final UIScalingMode uiScalingMode = plugin.config.uiScalingMode();

        glUseProgram(plugin.shaders.uiShader.id());
        ShaderVariables shaderVars = plugin.uniforms.GetUniforms(plugin.shaders.uiShader.id());

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, plugin.mainPassLegacy.frameBuffer.getTexture().getId());
        glUniform1i(shaderVars.MainTexture, 1);

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, plugin.postProcessingPass.bloomFramebuffer.getTexture().getId());
        glUniform1i(shaderVars.BloomTexture, 2);

        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_2D, compositeTexture);
        glUniform1i(shaderVars.InterfaceTexture, 3);

        glActiveTexture(GL_TEXTURE4);
        glBindTexture(GL_TEXTURE_2D, plugin.shadowPass.GetFramebuffer().getTexture().getId());
        glUniform1i(shaderVars.ShadowMap, 4);

        glUniform1i(shaderVars.TexSamplingMode, uiScalingMode.getMode());
        glUniform2i(shaderVars.TexSourceDimensions, canvasWidth, canvasHeight);
        glUniform1i(shaderVars.UiColorBlindMode, plugin.config.colorBlindMode().ordinal());
        glUniform4f(shaderVars.UiAlphaOverlay,
                (overlayColor >> 16 & 0xFF) / 255f,
                (overlayColor >> 8 & 0xFF) / 255f,
                (overlayColor & 0xFF) / 255f,
                (overlayColor >>> 24) / 255f
        );

        glUniformBlockBinding(plugin.shaders.uiShader.id(), shaderVars.ConfigBlock, CONFIG_BUFFER_BINDING_ID);

        if (plugin.client.isStretchedEnabled())
        {
            Dimension dim = plugin.client.getStretchedDimensions();
            plugin.glDpiAwareViewport(0, 0, dim.width, dim.height);
            glUniform2i(shaderVars.TexTargetDimensions, dim.width, dim.height);
        }
        else
        {
            plugin.glDpiAwareViewport(0, 0, canvasWidth, canvasHeight);
            glUniform2i(shaderVars.TexTargetDimensions, canvasWidth, canvasHeight);
        }

        // Set the sampling function used when stretching the UI.
        // This is probably better done with sampler objects instead of texture parameters, but this is easier and likely more portable.
        // See https://www.khronos.org/opengl/wiki/Sampler_Object for details.
        if (plugin.client.isStretchedEnabled())
        {
            // GL_NEAREST makes sampling for bicubic/xBR simpler, so it should be used whenever linear isn't
            final int function = uiScalingMode == UIScalingMode.LINEAR ? GL_LINEAR : GL_NEAREST;
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, function);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, function);
        }

        glBindVertexArray(vertexArrayObject);
        glDrawArrays(GL_TRIANGLE_FAN, 0, 4);

        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindVertexArray(0);
        glUseProgram(0);
    }

    public void SetOverlayColor(int overlayColor) {
        this.overlayColor = overlayColor;
    }

    private void PrepareTexture() {
        final int canvasHeight = plugin.client.getCanvasHeight();
        final int canvasWidth = plugin.client.getCanvasWidth();
        if (canvasWidth != lastCanvasWidth || canvasHeight != lastCanvasHeight)
        {
            lastCanvasWidth = canvasWidth;
            lastCanvasHeight = canvasHeight;

            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, compositePbo);
            glBufferData(GL_PIXEL_UNPACK_BUFFER, canvasWidth * canvasHeight * 4L, GL_STREAM_DRAW);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            glBindTexture(GL_TEXTURE_2D, compositeTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, canvasWidth, canvasHeight, 0, GL_BGRA, GL_UNSIGNED_BYTE, 0);
            glBindTexture(GL_TEXTURE_2D, 0);
        }

        final BufferProvider bufferProvider = plugin.client.getBufferProvider();
        final int[] pixels = bufferProvider.getPixels();
        final int width = bufferProvider.getWidth();
        final int height = bufferProvider.getHeight();

        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, compositePbo);
        ByteBuffer interfaceBuf = glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY);
        if (interfaceBuf != null)
        {
            interfaceBuf
                    .asIntBuffer()
                    .put(pixels, 0, width * height);
            glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);
        }
        glBindTexture(GL_TEXTURE_2D, compositeTexture);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, 0);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    @Override
    public void OnPreLoadScene(Scene scene) {

    }

    @Override
    public void OnSceneLoadStart(Scene scene) {

    }

    @Override
    public void OnSceneLoadFinished(Scene scene) {

    }

    @Override
    public void OnPreDrawScene() {

    }

    @Override
    public void OnDrawScene() {

    }

    @Override
    public void OnPostDrawScene() {

    }

    @Override
    public void OnDrawSceneTile(Scene scene, SceneTilePaint paint, int plane, int tileX, int tileY) {

    }

    @Override
    public void OnDrawSceneTileModel(Scene scene, SceneTileModel model, int tileX, int tileY) {

    }

    @Override
    public void OnDrawModel(Projection projection, Scene scene, Renderable renderable, int orientation, int x, int y, int z, long hash) {

    }

    @Override
    public void OnGameStateChanged(GameStateChanged gameStateChanged) {

    }
}
