package com.gpuExtended.rendering.passes;

import com.google.inject.Singleton;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.opengl.GLBuffer;
import com.gpuExtended.overlays.PerformanceOverlay;
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

    public void Dispose() {
        vertexBufferContext.Dispose();
        computeBufferContext.Dispose();
        frameBuffer.dispose();
    }

    public void OnPreRender() {
        frameBuffer.clearFramebuffer();
    }

    public void OnRender() {
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glEnable(GL_DEPTH_TEST);

        glBindVertexArray(vertexBufferContext.vertexArrayObjectId);

        glViewport(0, 0, frameBuffer.getTexture().getWidth(), frameBuffer.getTexture().getHeight());
        frameBuffer.bind();

        plugin.skybox.Render(); // Render the skybox first.

        glUseProgram(plugin.shaderHandler.mainPassShader.id());
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        Uniforms.ShaderVariables uni = plugin.uniforms.GetUniforms(plugin.shaderHandler.mainPassShader.id());

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, plugin.shadowPassHandler.GetFramebuffer().getTexture().getId());
        glUniform1i(uni.ShadowMap, 2);

        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_2D, plugin.shadowPassHandler.GetDynamicFramebuffer().getTexture().getId());
        glUniform1i(uni.DynamicShadowMap, 3);

        glActiveTexture(GL_TEXTURE4);
        glBindTexture(GL_TEXTURE_2D, plugin.tileMarkerManager.tileFillColorTexture.getId());
        glUniform1i(uni.TileMarkerFillColorMap, 4);

        glActiveTexture(GL_TEXTURE5);
        glBindTexture(GL_TEXTURE_2D, plugin.tileMarkerManager.tileBorderColorTexture.getId());
        glUniform1i(uni.TileMarkerBorderColorMap, 5);

        glActiveTexture(GL_TEXTURE6);
        glBindTexture(GL_TEXTURE_2D, plugin.tileMarkerManager.tileSettingsTexture.getId());
        glUniform1i(uni.TileMarkerSettingsMap, 6);

        glUniformBlockBinding(plugin.shaderHandler.mainPassShader.id(), uni.CameraBlock, CAMERA_BUFFER_BINDING_ID);
        glUniformBlockBinding(plugin.shaderHandler.mainPassShader.id(), uni.PlayerBlock, PLAYER_BUFFER_BINDING_ID);
        glUniformBlockBinding(plugin.shaderHandler.mainPassShader.id(), uni.EnvironmentBlock, ENVIRONMENT_BUFFER_BINDING_ID);
        glUniformBlockBinding(plugin.shaderHandler.mainPassShader.id(), uni.TileMarkerBlock, TILEMARKER_BUFFER_BINDING_ID);
        glUniformBlockBinding(plugin.shaderHandler.mainPassShader.id(), uni.SystemInfoBlock, SYSTEMINFO_BUFFER_BINDING_ID);
        glUniformBlockBinding(plugin.shaderHandler.mainPassShader.id(), uni.ConfigBlock, CONFIG_BUFFER_BINDING_ID);

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, plugin.lightBinsBuffer.glBufferId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, plugin.lightBinsBuffer.glBufferId);
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

        final TextureProvider textureProvider = plugin.client.getTextureProvider();
        if (plugin.textureArrayId == -1) {
            // lazy init textures as they may not be loaded at plugin start.
            // this will return -1 and retry if not all textures are loaded yet, too.
            plugin.textureArrayId = plugin.textureManager.initTextureArray(textureProvider);
            if (plugin.textureArrayId > -1) {
                // if texture upload is successful, compute and set texture animations
                float[] texAnims = plugin.textureManager.computeTextureAnimations(textureProvider);
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

        glDrawArrays(GL_TRIANGLES, 0, computeBufferContext.totalVertices);

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

        computeBufferContext.largeModelBuffer.clear();
        computeBufferContext.unsortedModelBuffer.clear();

        computeBufferContext.numLargeModels = 0;
        computeBufferContext.numUnsortedModels = 0;

        computeBufferContext.totalDynamicVertices = 0;
        computeBufferContext.totalDynamicUvs = 0;
    }

    public void OnLoadScene(Scene scene) {
        GpuIntBuffer newVertexBuffer = new GpuIntBuffer();
        GpuFloatBuffer newUvBuffer = new GpuFloatBuffer();
        GpuFloatBuffer newNormalBuffer = new GpuFloatBuffer();

        plugin.sceneUploader.UploadScene(scene, newVertexBuffer, newUvBuffer, newNormalBuffer);

        newVertexBuffer.flip();
        newUvBuffer.flip();
        newNormalBuffer.flip();

        nextSceneVertexBufferContext.vertexBuffer = newVertexBuffer;
        nextSceneVertexBufferContext.uvBuffer = newUvBuffer;
        nextSceneVertexBufferContext.normalBuffer = newNormalBuffer;
    }

    public void OnSceneLoaded() {
        plugin.updateBuffer(computeBufferContext.staticVertexInBuffer, GL_ARRAY_BUFFER, nextSceneVertexBufferContext.vertexBuffer.getBuffer(), GL_STATIC_COPY);
        plugin.updateBuffer(computeBufferContext.staticUvInBuffer, GL_ARRAY_BUFFER, nextSceneVertexBufferContext.uvBuffer.getBuffer(), GL_STATIC_COPY);
        plugin.updateBuffer(computeBufferContext.staticNormalInBuffer, GL_ARRAY_BUFFER, nextSceneVertexBufferContext.normalBuffer.getBuffer(), GL_STATIC_COPY);

        nextSceneVertexBufferContext.vertexBuffer = null;
        nextSceneVertexBufferContext.uvBuffer = null;
        nextSceneVertexBufferContext.normalBuffer = null;
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
        cCtx.largeModelBuffer.flip();

        /*{
            // computeBufferContext.totalVertices
			int PRIORITY_DATA_BINDING_ID = 0;
            int sizeOfPriorityDataStruct = (100 * Byte.BYTES);
            int bufferRequiredSize = cCtx.numLargeModels * sizeOfPriorityDataStruct;

            glBindBuffer(GL_SHADER_STORAGE_BUFFER, cCtx.gl_modelPriorityDataBuffer);
            glBufferData(GL_SHADER_STORAGE_BUFFER, bufferRequiredSize, GL_DYNAMIC_COPY);
            glUseProgram(plugin.shaderHandler.priorityPrepassShader.id());
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, PRIORITY_DATA_BINDING_ID, cCtx.gl_modelPriorityDataBuffer);
            glDispatchCompute(cCtx.numLargeModels, 1, 1);
            glUseProgram(0);
        }*/

        IntBuffer vertexBuffer = vCtx.vertexBuffer.getBuffer();
        FloatBuffer uvBuffer = vCtx.uvBuffer.getBuffer();
        FloatBuffer normalBuffer = vCtx.normalBuffer.getBuffer();

        IntBuffer modelBufferUnordered = cCtx.unsortedModelBuffer.getBuffer();
        IntBuffer modelBufferLarge = cCtx.largeModelBuffer.getBuffer();

        // compute sorting buffers
        plugin.updateBuffer(cCtx.tmpUnsortedModelBuffer, GL_ARRAY_BUFFER, modelBufferUnordered, GL_DYNAMIC_DRAW);
        plugin.updateBuffer(cCtx.tmpLargeModelBuffer, GL_ARRAY_BUFFER, modelBufferLarge, GL_DYNAMIC_DRAW);

        // dynamic model buffers
        plugin.updateBuffer(cCtx.dynamicVertexInBuffer, GL_ARRAY_BUFFER, vertexBuffer, GL_DYNAMIC_DRAW);
        plugin.updateBuffer(cCtx.dynamicUvInBuffer, GL_ARRAY_BUFFER, uvBuffer, GL_DYNAMIC_DRAW);
        plugin.updateBuffer(cCtx.dynamicNormalInBuffer, GL_ARRAY_BUFFER, normalBuffer, GL_DYNAMIC_DRAW);

        // Output buffers
        final int size = cCtx.totalVertices * Vector4.BYTES; // each buffer contains a Vector4 for each vertex
        plugin.updateBuffer(cCtx.vertexOutBuffer, GL_ARRAY_BUFFER, size, GL_STREAM_DRAW);
        plugin.updateBuffer(cCtx.uvOutBuffer, GL_ARRAY_BUFFER, size, GL_STREAM_DRAW);
        plugin.updateBuffer(cCtx.normalOutBuffer, GL_ARRAY_BUFFER, size, GL_STREAM_DRAW);
        plugin.updateBuffer(cCtx.flagsOutBuffer, GL_ARRAY_BUFFER, size, GL_STREAM_DRAW);

        DispatchSortingCompute(cCtx.tmpUnsortedModelBuffer, cCtx.numUnsortedModels, plugin.shaderHandler.unorderedComputeShader.id());
        DispatchSortingCompute(cCtx.tmpLargeModelBuffer, cCtx.numLargeModels, plugin.shaderHandler.largeOrderedComputeShader.id());
    }

    public void OnGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
        {
            // Avoid drawing the last frame's buffer during LOADING after LOGIN_SCREEN
            computeBufferContext.totalVertices = 0;
        }
    }

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
            buffer.put(paint.getBufferOffset()); // offset into vertex buffer
            buffer.put(paint.getUvBufferOffset());// offset into texture buffer
            buffer.put(2); // length in faces
            buffer.put(computeBufferContext.totalVertices); // idx
            buffer.put(FLAG_SCENE_BUFFER); // flags

            buffer.put(localX); // scene x
            buffer.put(localY); // scene y
            buffer.put(localZ); // scene z

            int isTerrainFlag = 1;

            buffer.put(flags); // exFlags.x
            buffer.put(isTerrainFlag); // exFlags.y
            buffer.put(-1); // exFlags.z
            buffer.put(-1); // exflags.w

            computeBufferContext.totalVertices += 2 * 3;
        }
    }

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
            buffer.put(model.getBufferOffset()); // offset into vertex buffer
            buffer.put(model.getUvBufferOffset()); // offset into texture buffer
            buffer.put(faceCount); // length in faces
            buffer.put(computeBufferContext.totalVertices); // idx
            buffer.put(FLAG_SCENE_BUFFER); // flags

            buffer.put(localX); // scene x
            buffer.put(localY); // scene y
            buffer.put(localZ); // scene z

            int isTerrainFlag = 0;

            buffer.put(flags); // exFlags.x
            buffer.put(isTerrainFlag); // exFlags.y
            buffer.put(-1); // exFlags.z
            buffer.put(-1); // exflags.w

            computeBufferContext.totalVertices += faceCount * 3;
        }
    }

    public void OnDrawModel(Projection projection, Scene scene, Renderable renderable, int orientation, int x, int y, int z, long hash) {
        Model model, offsetModel;
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
            int tileX = (x / LOCAL_TILE_SIZE) + SCENE_OFFSET;
            int tileY = (z / LOCAL_TILE_SIZE) + SCENE_OFFSET;

            int faceCount = Math.min(MAX_TRIANGLE, offsetModel.getFaceCount());
            int uvOffset = offsetModel.getUvBufferOffset();
            int flags = GetModelPackedFlags(hash, model, offsetModel, orientation);
            int exFlags = GetExFlags(hash, tileX, tileY, z, false);

            GpuIntBuffer b = GetCorrectModelBufferForTriangleCount(faceCount);

            b.ensureCapacity(12);
            IntBuffer buffer = b.getBuffer();
            buffer.put(offsetModel.getBufferOffset());// offset into vertex buffer
            buffer.put(uvOffset);// offset into texture buffer
            buffer.put(faceCount);// length in faces
            buffer.put(computeBufferContext.totalVertices);// idx
            buffer.put(FLAG_SCENE_BUFFER | flags);// flags

            buffer.put(x);// scene x
            buffer.put(y);// scene y
            buffer.put(z);// scene z

            int isTerrainFlag = 0;

            buffer.put(exFlags);// exFlags.x
            buffer.put(isTerrainFlag);// exFlags.y
            buffer.put(-1);// exFlags.z
            buffer.put(GetModelConfig(hash, tileX, tileY, z));// exflags.w

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
            int flags = GetModelPackedFlags(hash, model, offsetModel, orientation);
            int exFlags = GetExFlags(hash, tileX, tileY, z, true);
            boolean hasUv = model.getFaceTextures() != null;
            boolean isNPC = renderable instanceof NPC;

            int vertexCount = plugin.sceneUploader.PushDynamicModel(model, 0, isNPC, vertexBufferContext.vertexBuffer, vertexBufferContext.uvBuffer, vertexBufferContext.normalBuffer);

            GpuIntBuffer b = GetCorrectModelBufferForTriangleCount(vertexCount / 3);
            b.ensureCapacity(12);
            IntBuffer buffer = b.getBuffer();
            buffer.put(computeBufferContext.totalDynamicVertices);// offset into vertex buffer
            buffer.put(hasUv ? computeBufferContext.totalDynamicUvs : -1);// offset into texture buffer
            buffer.put(vertexCount / 3);// length in faces
            buffer.put(computeBufferContext.totalVertices);// idx
            buffer.put(flags);// flags

            buffer.put(x);// scene x
            buffer.put(y);// scene y
            buffer.put(z);// scene z

            int isTerrainFlag = 0;

            buffer.put(exFlags);// exFlags.x
            buffer.put(isTerrainFlag);// exFlags.y
            buffer.put(-1);// exFlags.z
            buffer.put(GetModelConfig(hash, x, y, z));// exflags.w

            computeBufferContext.totalDynamicVertices += vertexCount;
            computeBufferContext.totalVertices += vertexCount;
            if (hasUv) {
                computeBufferContext.totalDynamicUvs += vertexCount;
            }
        }

        plugin.performanceOverlay.EndTimer(PerformanceOverlay.TimerType.PUSH_DYNAMIC_GEOMETRY);
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

    private GpuIntBuffer GetCorrectModelBufferForTriangleCount(int triangles) {
        computeBufferContext.numLargeModels++;
        return computeBufferContext.largeModelBuffer;
    }

    private void DispatchSortingCompute(GLBuffer modelBuffer, int numModels, int computeShader) {
        Uniforms uniforms = plugin.uniforms;
        ShaderHandler shaders = plugin.shaderHandler;

        // Bind uniforms for compute shaders | TODO:: this may not need to be done every frame. Also, move uniform buffers to uniform wrapper or something
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

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TEMP_VERTEX_BUFFER_IN_BINDING_ID, computeBufferContext.dynamicVertexInBuffer.glBufferId); // tempvertexbuffer_in
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TEMP_TEXTURE_BUFFER_IN_BINDING_ID, computeBufferContext.dynamicUvInBuffer.glBufferId); // temptexturebuffer_in
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TEMP_NORMAL_BUFFER_IN_BINDING_ID, computeBufferContext.dynamicNormalInBuffer.glBufferId); // tempnormalbuffer_in

        glDispatchCompute(numModels, 1, 1);
    }

    private boolean CalculateModelBoundsAndClickbox(Projection projection, Model model, int orientation, int x, int y, int z, long hash) {
        if (projection == null) return false;

        if (projection instanceof IntProjection)
        {
            IntProjection p = (IntProjection) projection;
            if (!isVisible(model, p.getPitchSin(), p.getPitchCos(), p.getYawSin(), p.getYawCos(), x - p.getCameraX(), y - p.getCameraY(), z - p.getCameraZ()))
            {
                return false;
            }
        }

        plugin.client.checkClickbox(projection, model, orientation, x, y, z, hash);
        return true;
    }

    private boolean CheckModelIsVisible(Model model, Projection projection, int x, int y, int z) {
        if (projection instanceof IntProjection) {
            IntProjection p = (IntProjection) projection;
            return isVisible(model, p.getPitchSin(), p.getPitchCos(), p.getYawSin(), p.getYawCos(), x - p.getCameraX(), y - p.getCameraY(), z - p.getCameraZ());
        }
        return true;
    }

    private boolean isVisible(Model model, int pitchSin, int pitchCos, int yawSin, int yawCos, int x, int y, int z)
    {
        model.calculateBoundsCylinder();

        final int xzMag = model.getXYZMag();
        final int bottomY = model.getBottomY();
        final int zoom = plugin.client.get3dZoom() / 2;
        final int modelHeight = model.getModelHeight();

        int Rasterizer3D_clipMidX2 = plugin.client.getRasterizer3D_clipMidX2(); // width / 2
        int Rasterizer3D_clipNegativeMidX = plugin.client.getRasterizer3D_clipNegativeMidX(); // -width / 2
        int Rasterizer3D_clipNegativeMidY = plugin.client.getRasterizer3D_clipNegativeMidY(); // -height / 2
        int Rasterizer3D_clipMidY2 = plugin.client.getRasterizer3D_clipMidY2(); // height / 2

        int var11 = yawCos * z - yawSin * x >> 16;
        int var12 = pitchSin * y + pitchCos * var11 >> 16;
        int var13 = pitchCos * xzMag >> 16;
        int depth = var12 + var13;
        if (depth > 50)
        {
            int rx = z * yawSin + yawCos * x >> 16;
            int var16 = (rx - xzMag) * zoom;
            if (var16 / depth < Rasterizer3D_clipMidX2)
            {
                int var17 = (rx + xzMag) * zoom;
                if (var17 / depth > Rasterizer3D_clipNegativeMidX)
                {
                    int ry = pitchCos * y - var11 * pitchSin >> 16;
                    int yheight = pitchSin * xzMag >> 16;
                    int ybottom = (pitchCos * bottomY >> 16) + yheight; // use bottom height instead of y pos for height
                    int var20 = (ry + ybottom) * zoom;
                    if (var20 / depth > Rasterizer3D_clipNegativeMidY)
                    {
                        int ytop = (pitchCos * modelHeight >> 16) + yheight;
                        int var22 = (ry - ytop) * zoom;
                        return var22 / depth < Rasterizer3D_clipMidY2;
                    }
                }
            }
        }
        return false;
    }
}
