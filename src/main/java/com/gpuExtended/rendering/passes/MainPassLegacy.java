package com.gpuExtended.rendering.passes;

import com.google.inject.Singleton;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.opengl.GLBuffer;
import com.gpuExtended.rendering.FrameBuffer;
import com.gpuExtended.rendering.Texture2D;
import com.gpuExtended.rendering.Vector4;
import com.gpuExtended.shader.ShaderHandler;
import com.gpuExtended.shader.Uniforms;
import com.gpuExtended.util.GpuFloatBuffer;
import com.gpuExtended.util.GpuIntBuffer;
import com.gpuExtended.util.contexts.ComputeBufferContext;
import com.gpuExtended.util.contexts.VertexBufferContext;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Scene;
import net.runelite.api.TextureProvider;
import net.runelite.api.events.GameStateChanged;
import org.lwjgl.opencl.CL12;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gpuExtended.util.constants.Variables.*;
import static com.gpuExtended.util.constants.Variables.VFLAGS_BINDING_ID;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL11C.GL_LINEAR;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL43C.*;

// Main pass, where sorting is done with compute shaders / priority renderer.
@Slf4j
@Singleton
public class MainPassLegacy {
    @Inject
    public GpuExtendedPlugin plugin;

    public FrameBuffer frameBuffer;
    VertexBufferContext vertexBufferContext;
    VertexBufferContext nextSceneVertexBufferContext;
    ComputeBufferContext computeBufferContext;

    public void Init() {
        InitFramebuffer();
        InitBuffers();
        InitVAO();
    }

    public void OnPreRender() {
        frameBuffer.clearFramebuffer();
    }

    public void Render() {
        if (colorFramebuffer.getTexture().getWidth() != currentViewport[2] || colorFramebuffer.getTexture().getHeight() != currentViewport[3]) {
            colorFramebuffer.resize(currentViewport[2], currentViewport[3]);
            bloomFramebuffer.resize(currentViewport[2], currentViewport[3]);

            log.info("Resizing Color Framebuffers: {}x{}", currentViewport[2], currentViewport[3]);
            log.info("Resizing Bloom Framebuffers: {}x{}", currentViewport[2], currentViewport[3]);
        }

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glEnable(GL_DEPTH_TEST);

        glBindVertexArray(mainDrawVertexArrayObject);

        glViewport(0, 0, colorFramebuffer.getTexture().getWidth(), colorFramebuffer.getTexture().getHeight());
        colorFramebuffer.bind();
        environmentManager.RenderSkybox();

        glUseProgram(shaderHandler.mainPassShader.id());
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        Uniforms.ShaderVariables uni = uniforms.GetUniforms(shaderHandler.mainPassShader.id());

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, shadowPassHandler.GetFramebuffer().getTexture().getId());
        glUniform1i(uni.ShadowMap, 2);

        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_2D, shadowPassHandler.GetDynamicFramebuffer().getTexture().getId());
        glUniform1i(uni.DynamicShadowMap, 3);

        glActiveTexture(GL_TEXTURE4);
        glBindTexture(GL_TEXTURE_2D, tileMarkerManager.tileFillColorTexture.getId());
        glUniform1i(uni.TileMarkerFillColorMap, 4);

        glActiveTexture(GL_TEXTURE5);
        glBindTexture(GL_TEXTURE_2D, tileMarkerManager.tileBorderColorTexture.getId());
        glUniform1i(uni.TileMarkerBorderColorMap, 5);

        glActiveTexture(GL_TEXTURE6);
        glBindTexture(GL_TEXTURE_2D, tileMarkerManager.tileSettingsTexture.getId());
        glUniform1i(uni.TileMarkerSettingsMap, 6);

        glUniformBlockBinding(shaderHandler.mainPassShader.id(), uni.CameraBlock, CAMERA_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderHandler.mainPassShader.id(), uni.PlayerBlock, PLAYER_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderHandler.mainPassShader.id(), uni.EnvironmentBlock, ENVIRONMENT_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderHandler.mainPassShader.id(), uni.TileMarkerBlock, TILEMARKER_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderHandler.mainPassShader.id(), uni.SystemInfoBlock, SYSTEMINFO_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderHandler.mainPassShader.id(), uni.ConfigBlock, CONFIG_BUFFER_BINDING_ID);

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, lightBinsBuffer.glBufferId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, lightBinsBuffer.glBufferId);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        final TextureProvider textureProvider = client.getTextureProvider();
        if (textureArrayId == -1) {
            // lazy init textures as they may not be loaded at plugin start.
            // this will return -1 and retry if not all textures are loaded yet, too.
            textureArrayId = textureManager.initTextureArray(textureProvider);
            if (textureArrayId > -1) {
                // if texture upload is successful, compute and set texture animations
                float[] texAnims = textureManager.computeTextureAnimations(textureProvider);
                glUniform2fv(uni.TextureAnimations, texAnims);
            }
        }
//
        glUniform1i(uni.Textures, 1); // texture sampler array is bound to texture1

        // We just allow the GL to do face culling. Note this requires the priority renderer
        // to have logic to disregard culled faces in the priority depth testing.
        glEnable(GL_CULL_FACE);

        // Enable blending for alpha
        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE);

        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        glDrawArrays(GL_TRIANGLES, 0, targetBufferOffset);

        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        glActiveTexture(GL_TEXTURE0);
        frameBuffer.unbind();
        glUseProgram(0);
    }

    public void OnPostRender() {
        vertexBufferContext.vertexBuffer.clear();
        vertexBufferContext.uvBuffer.clear();
        vertexBufferContext.normalBuffer.clear();
        vertexBufferContext.flagsBuffer.clear();

        computeBufferContext.smallModelBuffer.clear();
        computeBufferContext.largeModelBuffer.clear();
        computeBufferContext.unsortedModelBuffer.clear();

        computeBufferContext.numSmallModels = 0;
        computeBufferContext.numLargeModels = 0;
        computeBufferContext.numUnsortedModels = 0;

        computeBufferContext.totalDynamicVertices = 0;
        computeBufferContext.totalDynamicUvs = 0;
    }

    public void OnLoadScene(Scene scene) {
        GpuIntBuffer newVertexBuffer = new GpuIntBuffer();
        GpuFloatBuffer newUvBuffer = new GpuFloatBuffer();
        GpuFloatBuffer newNormalBuffer = new GpuFloatBuffer();
        GpuIntBuffer newFlagsBuffer = new GpuIntBuffer();

        plugin.sceneUploader.UploadScene(scene, newVertexBuffer, newUvBuffer, newNormalBuffer, newFlagsBuffer);

        newVertexBuffer.flip();
        newUvBuffer.flip();
        newNormalBuffer.flip();
        newFlagsBuffer.flip();

        nextSceneVertexBufferContext.vertexBuffer = newVertexBuffer;
        nextSceneVertexBufferContext.uvBuffer = newUvBuffer;
        nextSceneVertexBufferContext.normalBuffer = newNormalBuffer;
        nextSceneVertexBufferContext.flagsBuffer = newFlagsBuffer;
    }

    public void OnSceneLoaded() {
        EnsureBufferCapacity(computeBufferContext.staticVertexInBuffer, GL_ARRAY_BUFFER, nextSceneVertexBufferContext.vertexBuffer.getBuffer(), GL_STATIC_COPY, CL12.CL_MEM_READ_ONLY);
        EnsureBufferCapacity(computeBufferContext.staticUvInBuffer, GL_ARRAY_BUFFER, nextSceneVertexBufferContext.uvBuffer.getBuffer(), GL_STATIC_COPY, CL12.CL_MEM_READ_ONLY);
        EnsureBufferCapacity(computeBufferContext.staticNormalInBuffer, GL_ARRAY_BUFFER, nextSceneVertexBufferContext.normalBuffer.getBuffer(), GL_STATIC_COPY, CL12.CL_MEM_READ_ONLY);
        EnsureBufferCapacity(computeBufferContext.staticFlagsInBuffer, GL_ARRAY_BUFFER, nextSceneVertexBufferContext.flagsBuffer.getBuffer(), GL_STATIC_COPY, CL12.CL_MEM_READ_ONLY);

        nextSceneVertexBufferContext.vertexBuffer = null;
        nextSceneVertexBufferContext.uvBuffer = null;
        nextSceneVertexBufferContext.normalBuffer = null;
        nextSceneVertexBufferContext.flagsBuffer = null;
    }

    public void OnPreDrawScene() {}

    public void OnDrawScene() {
        // Only reset the target buffer offset right before drawing the scene. That way if there are frames
        // after this that don't involve a scene draw, like during LOADING/HOPPING/CONNECTION_LOST, we can
        // still redraw the previous frame's scene to emulate the client behavior of not painting over the
        // viewport buffer.
        computeBufferContext.totalVertices = 0;
    }

    public void OnPostDrawScene() {
        VertexBufferContext vCtx = vertexBufferContext;
        ComputeBufferContext cCtx = computeBufferContext;

        vCtx.FlipBuffers();
        cCtx.unsortedModelBuffer.flip();
        cCtx.smallModelBuffer.flip();
        cCtx.largeModelBuffer.flip();

        FloatBuffer vertexBuffer = vCtx.vertexBuffer.getBuffer();
        FloatBuffer uvBuffer = vCtx.uvBuffer.getBuffer();
        FloatBuffer normalBuffer = vCtx.normalBuffer.getBuffer();
        IntBuffer flagsBuffer = vCtx.flagsBuffer.getBuffer();

        IntBuffer modelBufferUnordered = cCtx.unsortedModelBuffer.getBuffer();
        IntBuffer modelBufferSmall = cCtx.smallModelBuffer.getBuffer();
        IntBuffer modelBufferLarge = cCtx.largeModelBuffer.getBuffer();

        // compute sorting buffers
        EnsureBufferCapacity(cCtx.tmpUnsortedModelBuffer, GL_ARRAY_BUFFER, modelBufferUnordered, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);
        EnsureBufferCapacity(cCtx.tmpSmallModelBuffer, GL_ARRAY_BUFFER, modelBufferSmall, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);
        EnsureBufferCapacity(cCtx.tmpLargeModelBuffer, GL_ARRAY_BUFFER, modelBufferLarge, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);

        // dynamic model buffers
        EnsureBufferCapacity(cCtx.dynamicVertexInBuffer, GL_ARRAY_BUFFER, vertexBuffer, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);
        EnsureBufferCapacity(cCtx.dynamicUvInBuffer, GL_ARRAY_BUFFER, uvBuffer, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);
        EnsureBufferCapacity(cCtx.dynamicNormalInBuffer, GL_ARRAY_BUFFER, normalBuffer, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);
        EnsureBufferCapacity(cCtx.dynamicFlagsBuffer, GL_ARRAY_BUFFER, flagsBuffer, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);

        // Output buffers
        final int size = cCtx.totalVertices * Vector4.BYTES; // each buffer contains a Vector4 for each vertex
        EnsureBufferCapacity(cCtx.vertexOutBuffer, GL_ARRAY_BUFFER, size, GL_STREAM_DRAW, CL12.CL_MEM_WRITE_ONLY);
        EnsureBufferCapacity(cCtx.uvOutBuffer, GL_ARRAY_BUFFER, size, GL_STREAM_DRAW, CL12.CL_MEM_WRITE_ONLY);
        EnsureBufferCapacity(cCtx.normalOutBuffer, GL_ARRAY_BUFFER, size, GL_STREAM_DRAW, CL12.CL_MEM_WRITE_ONLY);
        EnsureBufferCapacity(cCtx.flagsOutBuffer, GL_ARRAY_BUFFER, size, GL_STREAM_DRAW, CL12.CL_MEM_WRITE_ONLY);

        SortModelsWithCompute(cCtx.tmpUnsortedModelBuffer, cCtx.numUnsortedModels, plugin.shaderHandler.unorderedComputeShader.id());
        SortModelsWithCompute(cCtx.tmpSmallModelBuffer, cCtx.numSmallModels, plugin.shaderHandler.smallOrderedComputeShader.id());
        SortModelsWithCompute(cCtx.tmpLargeModelBuffer, cCtx.numLargeModels, plugin.shaderHandler.largeOrderedComputeShader.id());
    }

    public void OnGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
        {
            // Avoid drawing the last frame's buffer during LOADING after LOGIN_SCREEN
            computeBufferContext.totalVertices = 0;
        }
    }

    private void SortModelsWithCompute(GLBuffer modelBuffer, int numModels, int computeShader) {
        Uniforms uniforms = plugin.uniforms;
        ShaderHandler shaders = plugin.shaderHandler;

        // Bind uniforms for compute shaders | TODO:: this may not need to be done every frame. Also, move uniform buffers to uniform wrapper or something
        glUniformBlockBinding(shaders.smallOrderedComputeShader.id(), uniforms.GetUniforms(shaders.smallOrderedComputeShader.id()).BlockSmall, CAMERA_BUFFER_BINDING_ID);
        glBindBufferBase(GL_UNIFORM_BUFFER, CAMERA_BUFFER_BINDING_ID, plugin.glCameraUniformBuffer.glBufferId);

        glUniformBlockBinding(shaders.largeOrderedComputeShader.id(), uniforms.GetUniforms(shaders.largeOrderedComputeShader.id()).BlockLarge, CAMERA_BUFFER_BINDING_ID);
        glBindBufferBase(GL_UNIFORM_BUFFER, CAMERA_BUFFER_BINDING_ID, plugin.glCameraUniformBuffer.glBufferId);

        glUseProgram(computeShader);

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, MODEL_BUFFER_IN_BINDING_ID, modelBuffer.glBufferId); // modelbuffer_in

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, VERTEX_BUFFER_OUT_BINDING_ID, computeBufferContext.vertexOutBuffer.glBufferId); // vertex out
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TEXTURE_BUFFER_OUT_BINDING_ID, computeBufferContext.uvOutBuffer.glBufferId); // uv out
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, NORMAL_BUFFER_OUT_BINDING_ID, computeBufferContext.normalOutBuffer.glBufferId); // normal_out
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, FLAGS_BUFFER_OUT_BINDING_ID, computeBufferContext.flagsOutBuffer.glBufferId); // flags out

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, VERTEX_BUFFER_IN_BINDING_ID, computeBufferContext.staticVertexInBuffer.glBufferId); // vertexbuffer_in
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TEXTURE_BUFFER_IN_BINDING_ID, computeBufferContext.staticUvInBuffer.glBufferId); // texturebuffer_in
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, NORMAL_BUFFER_IN_BINDING_ID, computeBufferContext.staticNormalInBuffer.glBufferId); // normalbuffer_in
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, FLAGS_BUFFER_IN_BINDING_ID,computeBufferContext. staticFlagsInBuffer.glBufferId); // flagsbuffer_in

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TEMP_VERTEX_BUFFER_IN_BINDING_ID, computeBufferContext.dynamicVertexInBuffer.glBufferId); // tempvertexbuffer_in
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TEMP_TEXTURE_BUFFER_IN_BINDING_ID, computeBufferContext.dynamicUvInBuffer.glBufferId); // temptexturebuffer_in
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TEMP_NORMAL_BUFFER_IN_BINDING_ID, computeBufferContext.dynamicNormalInBuffer.glBufferId); // tempnormalbuffer_in
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TEMP_FLAGS_BUFFER_IN_BINDING_ID, computeBufferContext.dynamicFlagsBuffer.glBufferId); // tempflagsbuffer_in

        glDispatchCompute(numModels, 1, 1);
    }

    public void Dispose() {
        vertexBufferContext.Dispose();
        computeBufferContext.Dispose();
        frameBuffer.dispose();
    }

    private void InitBuffers() {
        vertexBufferContext = new VertexBufferContext();
        nextSceneVertexBufferContext = new VertexBufferContext();
        computeBufferContext = new ComputeBufferContext();

        vertexBufferContext.PrepareBufferArray();
    }

    private void InitFramebuffer(){
        FrameBuffer.FrameBufferSettings fboSettings = new FrameBuffer.FrameBufferSettings();
        fboSettings.name = "color";
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

        frameBuffer = new FrameBuffer(fboSettings, textureSettings);
    }

    private void InitVAO() {
        glBindVertexArray(vertexBufferContext.vertexArrayObjectId);

        glEnableVertexAttribArray(VPOS_BINDING_ID);
        glBindBuffer(GL_ARRAY_BUFFER, computeBufferContext.vertexOutBuffer.glBufferId);
        glVertexAttribPointer(VPOS_BINDING_ID, 3, GL_FLOAT, false, 16, 0);

        glEnableVertexAttribArray(VHSL_BINDING_ID);
        glBindBuffer(GL_ARRAY_BUFFER, computeBufferContext.vertexOutBuffer.glBufferId);
        glVertexAttribIPointer(VHSL_BINDING_ID, 1, GL_INT, 16, 12);

        glEnableVertexAttribArray(VUV_BINDING_ID);
        glBindBuffer(GL_ARRAY_BUFFER, computeBufferContext.uvOutBuffer.glBufferId);
        glVertexAttribPointer(VUV_BINDING_ID, 4, GL_FLOAT, false, 0, 0);

        glEnableVertexAttribArray(VNORM_BINDING_ID);
        glBindBuffer(GL_ARRAY_BUFFER, computeBufferContext.normalOutBuffer.glBufferId);
        glVertexAttribPointer(VNORM_BINDING_ID, 4, GL_FLOAT, false, 0, 0);

        glEnableVertexAttribArray(VFLAGS_BINDING_ID);
        glBindBuffer(GL_ARRAY_BUFFER, computeBufferContext.flagsOutBuffer.glBufferId);
        glVertexAttribIPointer(VFLAGS_BINDING_ID, 4, GL_INT, 0, 0);

        glBindVertexArray(0);
    }

    private void EnsureBufferCapacity(@Nonnull GLBuffer glBuffer, int target, @Nonnull IntBuffer data, int usage, long clFlags)
    {
        int size = data.remaining() << 2;
        EnsureBufferCapacity(glBuffer, target, size, usage, clFlags);
        glBufferSubData(target, 0, data);
    }

    private void EnsureBufferCapacity(@Nonnull GLBuffer glBuffer, int target, @Nonnull FloatBuffer data, int usage, long clFlags)
    {
        int size = data.remaining() << 2;
        EnsureBufferCapacity(glBuffer, target, size, usage, clFlags);
        glBufferSubData(target, 0, data);
    }

    private void EnsureBufferCapacity(@Nonnull GLBuffer glBuffer, int target, int size, int usage, long clFlags)
    {
        glBindBuffer(target, glBuffer.glBufferId);

        // https://www.khronos.org/opengl/wiki/Buffer_Object_Streaming suggests buffer re-specification is useful
        // to avoid implicit syncing. We always need to trash the whole buffer anyway so this can't hurt.
        if (plugin.glCapabilities.glInvalidateBufferData != 0L) {
            glInvalidateBufferData(glBuffer.glBufferId);
        }

        if (size > glBuffer.size) {
            int newSize = Math.max(1024, NextPowerOfTwo(size));
            log.trace("Buffer resize: {} {} -> {}", glBuffer.name, glBuffer.size, newSize);

            glBuffer.size = newSize;
            glBufferData(target, newSize, usage);
        }
    }

    private static int NextPowerOfTwo(int v)
    {
        v--;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        v++;
        return v;
    }
}
