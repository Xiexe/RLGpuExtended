package com.gpuExtended.rendering.passes;

import com.google.inject.Singleton;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.opengl.GLBuffer;
import com.gpuExtended.overlays.PerformanceOverlay;
import com.gpuExtended.rendering.FrameBuffer;
import com.gpuExtended.rendering.Texture2D;
import com.gpuExtended.rendering.Vector3;
import com.gpuExtended.rendering.Vector4;
import com.gpuExtended.rendering.camera.Camera;
import com.gpuExtended.shader.ShaderVariables;
import com.gpuExtended.shader.ShaderHandler;
import com.gpuExtended.shader.Uniforms;
import com.gpuExtended.util.GpuFloatBuffer;
import com.gpuExtended.util.GpuIntBuffer;
import com.gpuExtended.util.contexts.ComputeBufferContext;
import com.gpuExtended.util.contexts.VertexBufferContext;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;

import javax.inject.Inject;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gpuExtended.util.constants.Variables.*;
import static com.gpuExtended.util.constants.Variables.VFLAGS_BINDING_ID;
import static net.runelite.api.Perspective.LOCAL_TILE_SIZE;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL11C.GL_LINEAR;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL43C.*;

// Main pass, where sorting is done with compute shaders / priority renderer.
@Slf4j
@Singleton
public class MainPassLegacy implements IPassBase {
    @Inject
    public GpuExtendedPlugin plugin;

    public Camera camera;
    public FrameBuffer frameBuffer;
    VertexBufferContext vertexBufferContext;
    VertexBufferContext nextSceneVertexBufferContext;
    ComputeBufferContext computeBufferContext;

    @Override
    public void Init() {
        InitFramebuffer();
        InitBuffers();
        InitVAO();

        Vector3 worldUp = new Vector3(0, 1, 0);
        Vector3 position = Vector3.Zero();
        camera = new Camera(position, worldUp, 0, 0);
    }

    private void InitBuffers() {
        vertexBufferContext = new VertexBufferContext();
        nextSceneVertexBufferContext = new VertexBufferContext();
        computeBufferContext = new ComputeBufferContext();

        vertexBufferContext.PrepareBufferArray();
        log.info("[Main Pass Legacy] Initialized Main Render Pass");
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

    @Override
    public void Dispose() {
        vertexBufferContext.Dispose();
        computeBufferContext.Dispose();
        frameBuffer.dispose();
    }

    @Override
    public void OnPreRenderFrame() {
//        plugin.performanceOverlay.StartTimer(PerformanceOverlay.TimerType.DRAW_MAIN_PASS);
        frameBuffer.clearFramebuffer();
    }

    @Override
    public void OnRenderFrame() {
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glEnable(GL_DEPTH_TEST);

        glBindVertexArray(vertexBufferContext.vertexArrayObjectId);

        glViewport(0, 0, frameBuffer.getTexture().getWidth(), frameBuffer.getTexture().getHeight());
        frameBuffer.bind();

        plugin.skybox.Render(); // Render the skybox first.

        glUseProgram(plugin.shaders.mainPassShader.id());
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        ShaderVariables shaderVars = plugin.uniforms.GetUniforms(plugin.shaders.mainPassShader.id());

        glUniform1i(shaderVars.Textures, 1); // texture sampler array is bound to texture1

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, plugin.shadowPass.GetFramebuffer().getTexture().getId());
        glUniform1i(shaderVars.ShadowMap, 2);

        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_2D, plugin.shadowPass.GetDynamicFramebuffer().getTexture().getId());
        glUniform1i(shaderVars.DynamicShadowMap, 3);

        glActiveTexture(GL_TEXTURE4);
        glBindTexture(GL_TEXTURE_2D, plugin.tileMarkerManager.tileFillColorTexture.getId());
        glUniform1i(shaderVars.TileMarkerFillColorMap, 4);

        glActiveTexture(GL_TEXTURE5);
        glBindTexture(GL_TEXTURE_2D, plugin.tileMarkerManager.tileBorderColorTexture.getId());
        glUniform1i(shaderVars.TileMarkerBorderColorMap, 5);

        glActiveTexture(GL_TEXTURE6);
        glBindTexture(GL_TEXTURE_2D, plugin.tileMarkerManager.tileSettingsTexture.getId());
        glUniform1i(shaderVars.TileMarkerSettingsMap, 6);

        glActiveTexture(GL_TEXTURE7);
        glBindTexture(GL_TEXTURE_2D_ARRAY, plugin.tileHeightTex);
        glUniform1i(shaderVars.TileHeightMap, 7);

        glActiveTexture(GL_TEXTURE8);
        glBindTexture(GL_TEXTURE_2D, plugin.uniforms.getBlueNoiseTexture().getId());
        glUniform1i(shaderVars.BlueNoiseTexture, 8);

        glUniformBlockBinding(plugin.shaders.mainPassShader.id(), shaderVars.CameraBlock, CAMERA_BUFFER_BINDING_ID);
        glUniformBlockBinding(plugin.shaders.mainPassShader.id(), shaderVars.PlayerBlock, PLAYER_BUFFER_BINDING_ID);
        glUniformBlockBinding(plugin.shaders.mainPassShader.id(), shaderVars.EnvironmentBlock, ENVIRONMENT_BUFFER_BINDING_ID);
        glUniformBlockBinding(plugin.shaders.mainPassShader.id(), shaderVars.TileMarkerBlock, TILEMARKER_BUFFER_BINDING_ID);
        glUniformBlockBinding(plugin.shaders.mainPassShader.id(), shaderVars.SystemInfoBlock, SYSTEMINFO_BUFFER_BINDING_ID);
        glUniformBlockBinding(plugin.shaders.mainPassShader.id(), shaderVars.ConfigBlock, CONFIG_BUFFER_BINDING_ID);

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, plugin.lightBinsBuffer.glBufferId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, plugin.lightBinsBuffer.glBufferId);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        // TODO:: if the shaders get recompiled, this uniform never gets set back to the shader.
        final TextureProvider textureProvider = plugin.client.getTextureProvider();
        if (plugin.textureArrayId == -1) {
            // lazy init textures as they may not be loaded at plugin start.
            // this will return -1 and retry if not all textures are loaded yet, too.
            plugin.textureArrayId = plugin.textureManager.initTextureArray(textureProvider);
            if (plugin.textureArrayId > -1) {
                // if texture upload is successful, compute and set texture animations
                float[] texAnims = plugin.textureManager.computeTextureAnimations(textureProvider);
                glUniform2fv(shaderVars.TextureAnimations, texAnims);
            }
        }

        glEnable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE);
        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        glDrawArrays(GL_TRIANGLES, 0, computeBufferContext.totalVertices);

        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        glActiveTexture(GL_TEXTURE0);
        frameBuffer.unbind();
        glUseProgram(0);
    }

    @Override
    public void OnPostRenderFrame() {
        vertexBufferContext.vertexBuffer.clear();
        vertexBufferContext.uvBuffer.clear();
        vertexBufferContext.normalBuffer.clear();
        vertexBufferContext.flagsBuffer.clear();

        computeBufferContext.unsortedModelBuffer.clear();
        computeBufferContext.ClearSortedModelBuffer();


        computeBufferContext.ResetSortedModelBufferCounts();
        computeBufferContext.numUnsortedModels = 0;
        computeBufferContext.totalDynamicVertices = 0;
        computeBufferContext.totalDynamicUvs = 0;

//        plugin.performanceOverlay.EndTimer(PerformanceOverlay.TimerType.DRAW_MAIN_PASS);
    }

    @Override
    public void OnPreLoadScene(Scene scene) {
        // Nothing needed here for now.
    }

    @Override
    public void OnSceneLoadStart(Scene scene) {
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

    @Override
    public void OnSceneLoadFinished(Scene scene) {
        plugin.updateBuffer(computeBufferContext.staticVertexInBuffer, GL_ARRAY_BUFFER, nextSceneVertexBufferContext.vertexBuffer.getBuffer(), GL_STATIC_COPY);
        plugin.updateBuffer(computeBufferContext.staticUvInBuffer, GL_ARRAY_BUFFER, nextSceneVertexBufferContext.uvBuffer.getBuffer(), GL_STATIC_COPY);
        plugin.updateBuffer(computeBufferContext.staticNormalInBuffer, GL_ARRAY_BUFFER, nextSceneVertexBufferContext.normalBuffer.getBuffer(), GL_STATIC_COPY);
        plugin.updateBuffer(computeBufferContext.staticFlagsInBuffer, GL_ARRAY_BUFFER, nextSceneVertexBufferContext.flagsBuffer.getBuffer(), GL_STATIC_COPY);

        nextSceneVertexBufferContext.vertexBuffer = null;
        nextSceneVertexBufferContext.uvBuffer = null;
        nextSceneVertexBufferContext.normalBuffer = null;
        nextSceneVertexBufferContext.flagsBuffer = null;
    }

    @Override
    public void OnPreDrawScene() {}

    @Override
    public void OnDrawScene() {
        // Only reset the target buffer offset right before drawing the scene. That way if there are frames
        // after this that don't involve a scene draw, like during LOADING/HOPPING/CONNECTION_LOST, we can
        // still redraw the previous frame's scene to emulate the client behavior of not painting over the
        // viewport buffer.
        computeBufferContext.totalVertices = 0;
        plugin.performanceOverlay.StartTimer(PerformanceOverlay.TimerType.DRAW_MAIN_PASS);
    }

    @Override
    public void OnPostDrawScene() {
        VertexBufferContext vCtx = vertexBufferContext;
        ComputeBufferContext cCtx = computeBufferContext;

        vCtx.FlipBuffers();
        cCtx.unsortedModelBuffer.flip();
        cCtx.FlipSortedModelBuffers();

        IntBuffer vertexBuffer = vCtx.vertexBuffer.getBuffer();
        FloatBuffer uvBuffer = vCtx.uvBuffer.getBuffer();
        FloatBuffer normalBuffer = vCtx.normalBuffer.getBuffer();
        IntBuffer flagsBuffer = vCtx.flagsBuffer.getBuffer();

        IntBuffer modelBufferUnordered = cCtx.unsortedModelBuffer.getBuffer();

        // compute sorting buffers
        plugin.updateBuffer(cCtx.tmpUnsortedModelBuffer, GL_ARRAY_BUFFER, modelBufferUnordered, GL_DYNAMIC_DRAW);
        cCtx.UpdateSortedModelBuffers(plugin);

        // dynamic model buffers
        plugin.updateBuffer(cCtx.dynamicVertexInBuffer, GL_ARRAY_BUFFER, vertexBuffer, GL_DYNAMIC_DRAW);
        plugin.updateBuffer(cCtx.dynamicUvInBuffer, GL_ARRAY_BUFFER, uvBuffer, GL_DYNAMIC_DRAW);
        plugin.updateBuffer(cCtx.dynamicNormalInBuffer, GL_ARRAY_BUFFER, normalBuffer, GL_DYNAMIC_DRAW);
        plugin.updateBuffer(cCtx.dynamicFlagsBuffer, GL_ARRAY_BUFFER, flagsBuffer, GL_DYNAMIC_DRAW);

        // Output buffers
        final int size = cCtx.totalVertices * Vector4.BYTES; // each buffer contains a Vector4 for each vertex
        plugin.updateBuffer(cCtx.vertexOutBuffer, GL_ARRAY_BUFFER, size, GL_STREAM_DRAW);
        plugin.updateBuffer(cCtx.uvOutBuffer, GL_ARRAY_BUFFER, size, GL_STREAM_DRAW);
        plugin.updateBuffer(cCtx.normalOutBuffer, GL_ARRAY_BUFFER, size, GL_STREAM_DRAW);
        plugin.updateBuffer(cCtx.flagsOutBuffer, GL_ARRAY_BUFFER, size, GL_STREAM_DRAW);

        DispatchSortingCompute(cCtx.tmpUnsortedModelBuffer, cCtx.numUnsortedModels, plugin.shaders.unorderedComputeShader.id());

        DispatchSortingCompute(cCtx.sortedModelGlBuffers[0], cCtx.numSortedModels[0], plugin.shaders.orderedComputeShader64.id());
        DispatchSortingCompute(cCtx.sortedModelGlBuffers[1], cCtx.numSortedModels[1], plugin.shaders.orderedComputeShader128.id());
        DispatchSortingCompute(cCtx.sortedModelGlBuffers[2], cCtx.numSortedModels[2], plugin.shaders.orderedComputeShader256.id());
        DispatchSortingCompute(cCtx.sortedModelGlBuffers[3], cCtx.numSortedModels[3], plugin.shaders.orderedComputeShader512.id());
        DispatchSortingCompute(cCtx.sortedModelGlBuffers[4], cCtx.numSortedModels[4], plugin.shaders.orderedComputeShader1024.id());
        DispatchSortingCompute(cCtx.sortedModelGlBuffers[5], cCtx.numSortedModels[5], plugin.shaders.orderedComputeShader2048.id());
        DispatchSortingCompute(cCtx.sortedModelGlBuffers[6], cCtx.numSortedModels[6], plugin.shaders.orderedComputeShader4096.id());
        DispatchSortingCompute(cCtx.sortedModelGlBuffers[7], cCtx.numSortedModels[7], plugin.shaders.orderedComputeShaderMAX_TRIANGLES.id());

        plugin.performanceOverlay.EndTimer(PerformanceOverlay.TimerType.DRAW_MAIN_PASS);
    }

    @Override
    public void OnGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
        {
            // Avoid drawing the last frame's buffer during LOADING after LOGIN_SCREEN
            computeBufferContext.totalVertices = 0;
        }
    }

    @Override
    // Draw call for simple tiles. 6 vertices, 2 triangles.
    public void OnDrawSceneTile(Scene scene, SceneTilePaint paint, int plane, int tileX, int tileY) {
        if (paint.getBufferLen() > 0)
        {
            final int localX = tileX << Perspective.LOCAL_COORD_BITS;
            final int localY = 0;
            final int localZ = tileY << Perspective.LOCAL_COORD_BITS;

            int faceCount = paint.getBufferLen();
            boolean isBridge = ((faceCount >> 5) & 1) != 0;
            boolean isUnderBridge = ((faceCount >> 6) & 1) != 0;
            int renderLevel = (faceCount >> 3) & 3;
            int flags = (renderLevel << BIT_PLANE) | (tileX + SCENE_OFFSET << BIT_XPOS) | (tileY + SCENE_OFFSET << BIT_YPOS) | (isBridge ? (1 << BIT_ISBRIDGE) : 0) | (!isUnderBridge ? (1 << BIT_ISTERRAIN) : 0);

            GpuIntBuffer b = computeBufferContext.unsortedModelBuffer;
            computeBufferContext.numUnsortedModels++;

            b.ensureCapacity(12);
            IntBuffer buffer = b.getBuffer();
            buffer.put(paint.getBufferOffset());
            buffer.put(paint.getUvBufferOffset());
            buffer.put(2);
            buffer.put(computeBufferContext.totalVertices);
            buffer.put(FLAG_SCENE_BUFFER);
            buffer.put(localX).put(localY).put(localZ);
            buffer.put(flags);
            buffer.put(OBJECT_TYPE.TYPE_TERRAIN.ordinal());
            buffer.put(-1);
            buffer.put(-1);

            computeBufferContext.totalVertices += 2 * 3;
        }
    }

    @Override
    // Draw call for complex tiles, could have many vertices and triangles. (like those with paths on them)
    public void OnDrawSceneTileModel(Scene scene, SceneTileModel model, int tileX, int tileY) {
        if (model.getBufferLen() > 0)
        {
            final int localX = tileX << Perspective.LOCAL_COORD_BITS;
            final int localY = 0;
            final int localZ = tileY << Perspective.LOCAL_COORD_BITS;

            int faceCount = model.getBufferLen();
            boolean isBridge = ((faceCount >> 5) & 1) != 0;
            boolean isUnderBridge = ((faceCount >> 6) & 1) != 0;
            int renderLevel = (faceCount >> 3) & 3;
            faceCount &= 7;

            int flags = (renderLevel << BIT_PLANE) | (tileX + SCENE_OFFSET << BIT_XPOS) | (tileY + SCENE_OFFSET << BIT_YPOS) | (isBridge ? (1 << BIT_ISBRIDGE) : 0) | (!isUnderBridge ? (1 << BIT_ISTERRAIN) : 0);;

            GpuIntBuffer b = computeBufferContext.unsortedModelBuffer;
            computeBufferContext.numUnsortedModels++;

            b.ensureCapacity(12);
            IntBuffer buffer = b.getBuffer();
            buffer.put(model.getBufferOffset());
            buffer.put(model.getUvBufferOffset());
            buffer.put(faceCount);
            buffer.put(computeBufferContext.totalVertices);
            buffer.put(FLAG_SCENE_BUFFER);
            buffer.put(localX).put(localY).put(localZ);
            buffer.put(flags);
            buffer.put(OBJECT_TYPE.TYPE_TERRAIN.ordinal());
            buffer.put(-1);
            buffer.put(-1);

            computeBufferContext.totalVertices += faceCount * 3;
        }
    }

    @Override
    public void OnDrawModel(Projection projection, Scene scene, Renderable renderable, int orientation, int x, int y, int z, long hash) {
        Model model, offsetModel;

        // TODO:: make a hashmap to track bad object ids to skip (53882 causes massive overdraw in guthix temple entrance)
        int objectId = (int)(hash >> 20);
        if(objectId == 53882) return;

        if (renderable instanceof Model)
        {
            model = (Model) renderable;
            offsetModel = model.getUnskewedModel();
            if (offsetModel == null)
            {
                offsetModel = model;
            }
        }
        else
        {
            model = renderable.getModel();
            if (model == null)
            {
                return;
            }
            offsetModel = model;
        }

        if (offsetModel.getSceneId() == plugin.sceneId)
        {
            PushStaticModelToComputeBuffer(projection, model, offsetModel, renderable, orientation, x, y, z, hash);
        }
        else
        {
            PushDynamicModelToComputeBuffer(projection, model, offsetModel, renderable, orientation, x, y, z, hash);
        }
    }

    private void PushStaticModelToComputeBuffer(Projection projection, Model model, Model offsetModel, Renderable renderable, int orientation, int x, int y, int z, long hash) {
        plugin.performanceOverlay.StartTimer(PerformanceOverlay.TimerType.PUSH_STATIC_GEOMETRY);
        assert model == renderable;

        if(CalculateModelBoundsAndClickbox(projection, model, orientation, x, y, z, hash)) {
            if (offsetModel.getFaceCount() <= 0) return;
            int tileX = (x / LOCAL_TILE_SIZE) + SCENE_OFFSET;
            int tileY = (z / LOCAL_TILE_SIZE) + SCENE_OFFSET;

            int faceCount = Math.min(MAX_TRIANGLE, offsetModel.getFaceCount());
            int uvOffset = offsetModel.getUvBufferOffset();
            int flags = GetModelPackedFlags(hash, model, offsetModel, orientation);
            int exFlags = GetExFlags(hash, tileX, tileY, z, false);

            GpuIntBuffer b = computeBufferContext.GetCorrectModelBufferForTriangleCount(faceCount);

            b.ensureCapacity(12);
            IntBuffer buffer = b.getBuffer();
            buffer.put(offsetModel.getBufferOffset());
            buffer.put(uvOffset);
            buffer.put(faceCount);
            buffer.put(computeBufferContext.totalVertices);
            buffer.put(FLAG_SCENE_BUFFER | flags);
            buffer.put(x).put(y).put(z);
            buffer.put(exFlags);
            buffer.put(GetObjectType(model, false).ordinal());
            buffer.put(-1);
            buffer.put(GetModelConfig(hash, tileX, tileY, z));

            computeBufferContext.totalVertices += faceCount * 3;
        }

        plugin.performanceOverlay.EndTimer(PerformanceOverlay.TimerType.PUSH_STATIC_GEOMETRY);
    }

    private void PushDynamicModelToComputeBuffer(Projection projection, Model model, Model offsetModel, Renderable renderable, int orientation, int x, int y, int z, long hash) {
        plugin.performanceOverlay.StartTimer(PerformanceOverlay.TimerType.PUSH_DYNAMIC_GEOMETRY);
        // Apply height to renderable from the model
        if (model != renderable)
        {
            renderable.setModelHeight(model.getModelHeight());
        }

        int tileX = (x / LOCAL_TILE_SIZE) + SCENE_OFFSET;
        int tileY = (z / LOCAL_TILE_SIZE) + SCENE_OFFSET;

        if(CalculateModelBoundsAndClickbox(projection, model, orientation, x, y, z, hash)) {
            if (model.getFaceCount() <= 0) return;
            int flags = GetModelPackedFlags(hash, model, offsetModel, orientation);
            int exFlags = GetExFlags(hash, tileX, tileY, z, true);
            boolean hasUv = model.getFaceTextures() != null;
            boolean isNPC = renderable instanceof NPC;

            int vertexCount = plugin.sceneUploader.PushDynamicModel(model, 0, isNPC, vertexBufferContext.vertexBuffer, vertexBufferContext.uvBuffer, vertexBufferContext.normalBuffer, vertexBufferContext.flagsBuffer);

            GpuIntBuffer b = computeBufferContext.GetCorrectModelBufferForTriangleCount(vertexCount / 3);
            b.ensureCapacity(12);
            IntBuffer buffer = b.getBuffer();
            buffer.put(computeBufferContext.totalDynamicVertices);
            buffer.put(hasUv ? computeBufferContext.totalDynamicUvs : -1);
            buffer.put(vertexCount / 3);
            buffer.put(computeBufferContext.totalVertices);
            buffer.put(flags);
            buffer.put(x).put(y).put(z);
            buffer.put(exFlags);
            buffer.put(GetObjectType(model, true).ordinal());
            buffer.put(-1);
            buffer.put(GetModelConfig(hash, x, y, z));

            computeBufferContext.totalDynamicVertices += vertexCount;
            computeBufferContext.totalVertices += vertexCount;
            if (hasUv) {
                computeBufferContext.totalDynamicUvs += vertexCount;
            }
        }

        plugin.performanceOverlay.EndTimer(PerformanceOverlay.TimerType.PUSH_DYNAMIC_GEOMETRY);
    }

    private OBJECT_TYPE GetObjectType(Model m, boolean isDynamicModel) {
        if (m instanceof WallObject)
            return OBJECT_TYPE.TYPE_WALL;

        if (m instanceof DecorativeObject)
            return OBJECT_TYPE.TYPE_DECORATION;

        if (m instanceof GameObject)
            return OBJECT_TYPE.TYPE_GAMEOBJECT;

        if (m instanceof GroundObject)
            return OBJECT_TYPE.TYPE_GROUND_OBJECT;

        if (m instanceof GraphicsObject)
            return OBJECT_TYPE.TYPE_GRAPHICS_OBJECT;

        if (m instanceof Model) {
            if (isDynamicModel)
                return OBJECT_TYPE.TYPE_DYNAMICMODEL;
            else
                return OBJECT_TYPE.TYPE_STATICMODEL;
        }

       return OBJECT_TYPE.TYPE_UNKNOWN;
    }

    private int GetModelPackedFlags(long hash, Model model, Model offsetModel, int orientation) {
        int plane = (int) ((hash >> TileObject.HASH_PLANE_SHIFT) & 3);
        boolean hillskew = offsetModel != model;

        int flags = (plane << BIT_ZHEIGHT) 					 		 |
                (hillskew ? (1 << BIT_HILLSKEW) : 0)  	 		 |
                orientation;

        return flags;
    }

    private int GetExFlags(long hash, int x, int y, int z, boolean isDynamicModel) {
        int plane = (int) ((hash >> TileObject.HASH_PLANE_SHIFT) & 3);
        int flags = (plane << BIT_PLANE) |
                (x << BIT_XPOS) |
                (y << BIT_YPOS) |
                (isDynamicModel ? (1 << BIT_ISDYNAMICMODEL) : 0);
        return flags;
    }

    private int GetModelConfig(long hash, int x, int y, int z) {
        // hash bits
        // | 1111 1111 1111 1 |    11 | 1  1111 1111 1111 1111 1111 1111 1111 111 |               1 |   11 |    11 1111 1 |     111 1111 |
        // |   13 unused bits | plane |                        32-bit id or index | right-clickable | type | 7-bit sceneY | 7-bit sceneX |
        //
        // 0    - straight walls, fences etc
        // 1    - diagonal walls corner, fences etc connectors
        // 2    - entire walls, fences etc corners
        // 3    - straight wall corners, fences etc connectors
        // 4    - straight inside wall decoration
        // 5    - straight outside wall decoration
        // 6    - diagonal outside wall decoration
        // 7    - diagonal inside wall decoration
        // 8    - diagonal in wall decoration
        // 9    - diagonal walls, fences etc
        // 10    - all kinds of objects, trees, statues, signs, fountains etc etc
        // 11    - ground objects like daisies etc
        // 12    - straight sloped roofs
        // 13    - diagonal sloped roofs
        // 14    - diagonal slope connecting roofs
        // 15    - straight sloped corner connecting roofs
        // 16    - straight sloped corner roof
        // 17    - straight flat top roofs
        // 18    - straight bottom egde roofs
        // 19    - diagonal bottom edge connecting roofs
        // 20    - straight bottom edge connecting roofs
        // 21    - straight bottom edge connecting corner roofs

        return -1;
    }

    private void DispatchSortingCompute(GLBuffer modelBuffer, int numModels, int computeShader) {
        if (numModels <= 0)  return;
        Uniforms uniforms = plugin.uniforms;

        // Bind uniforms for compute shaders | TODO:: this may not need to be done every frame. Also, move uniform buffers to uniform wrapper or something
        glUniformBlockBinding(computeShader, uniforms.GetUniforms(computeShader).BlockSmall, CAMERA_BUFFER_BINDING_ID);
        glBindBufferBase(GL_UNIFORM_BUFFER, CAMERA_BUFFER_BINDING_ID, uniforms.glCameraUniformBuffer.glBufferId);

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

    private boolean CalculateModelBoundsAndClickbox(Projection projection, Model model, int orientation, int x, int y, int z, long hash) {
        if (projection == null) return false;

        if (!CheckModelIsVisible(model, projection, x, y, z))
            return false;

        plugin.client.checkClickbox(projection, model, orientation, x, y, z, hash);
        return true;
    }

    private boolean CheckModelIsVisible(Model model, Projection projection, int x, int y, int z) {
        if (projection instanceof IntProjection) {
            IntProjection p = (IntProjection) projection;

            return isVisible(model,
                    p.getPitchSin(),
                    p.getPitchCos(),
                    p.getYawSin(),
                    p.getYawCos(),
                    x - p.getCameraX(),
                    y - p.getCameraY(),
                    z - p.getCameraZ()
            ); // For some reason java is getting mad now that these weren't casted to int manually? Wasn't an issue before...
        }
        return true;
    }

    private boolean isVisible(Model model, float pitchSin, float pitchCos, float yawSin, float yawCos, int x, int y, int z)
    {
        final int xzMag = model.getXYZMag();
        final int bottomY = model.getBottomY();
        final int zoom = plugin.client.get3dZoom();
        final int modelHeight = model.getModelHeight();

        int Rasterizer3D_clipMidX2 = plugin.client.getRasterizer3D_clipMidX2(); // width / 2
        int Rasterizer3D_clipNegativeMidX = plugin.client.getRasterizer3D_clipNegativeMidX(); // -width / 2
        int Rasterizer3D_clipNegativeMidY = plugin.client.getRasterizer3D_clipNegativeMidY(); // -height / 2
        int Rasterizer3D_clipMidY2 = plugin.client.getRasterizer3D_clipMidY2(); // height / 2

        float var11 = yawCos * z - yawSin * x;
        float var12 = pitchSin * y + pitchCos * var11;
        float var13 = pitchCos * xzMag;
        float depth = var12 + var13;
        if (depth > 50)
        {
            float rx = z * yawSin + yawCos * x;
            float var16 = (rx - xzMag) * zoom;
            if (var16 / depth < Rasterizer3D_clipMidX2)
            {
                float var17 = (rx + xzMag) * zoom;
                if (var17 / depth > Rasterizer3D_clipNegativeMidX)
                {
                    float ry = pitchCos * y - var11 * pitchSin;
                    float yheight = pitchSin * xzMag;
                    float ybottom = (pitchCos * bottomY) + yheight; // use bottom height instead of y pos for height
                    float var20 = (ry + ybottom) * zoom;
                    if (var20 / depth > Rasterizer3D_clipNegativeMidY)
                    {
                        float ytop = (pitchCos * modelHeight) + yheight;
                        float var22 = (ry - ytop) * zoom;
                        return var22 / depth < Rasterizer3D_clipMidY2;
                    }
                }
            }
        }
        return false;
    }
}
