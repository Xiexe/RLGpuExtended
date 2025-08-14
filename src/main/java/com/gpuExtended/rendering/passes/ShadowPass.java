package com.gpuExtended.rendering.passes;

import com.google.common.base.Stopwatch;
import com.google.inject.Singleton;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.overlays.PerformanceOverlay;
import com.gpuExtended.regions.Area;
import com.gpuExtended.regions.Bounds;
import com.gpuExtended.rendering.FrameBuffer;
import com.gpuExtended.rendering.Texture2D;
import com.gpuExtended.rendering.Vector4;
import com.gpuExtended.shader.ShaderVariables;
import com.gpuExtended.util.GpuFloatBuffer;
import com.gpuExtended.util.Mat4;
import com.gpuExtended.util.contexts.RenderableContext;
import com.gpuExtended.util.contexts.TileContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import org.lwjgl.opengl.GL30;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.gpuExtended.util.SceneUploader.*;
import static com.gpuExtended.util.constants.Variables.*;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static net.runelite.api.Perspective.LOCAL_TILE_SIZE;
import static net.runelite.api.Perspective.UNIT;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL14C.GL_DEPTH_COMPONENT24;
import static org.lwjgl.opengl.GL15C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL20C.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL31C.glUniformBlockBinding;
import static org.lwjgl.opengl.GL41C.glClearDepthf;

@Slf4j
@Singleton
public class ShadowPass implements IPassBase {
    @Inject
    public GpuExtendedPlugin plugin;

    private boolean sceneGeometryDirty = false;
    private boolean loadingNewSceneGeometry = false;

    // TODO:: Figure out a way to do this without 2 framebuffer maybe.
    @Getter
    private FrameBuffer frameBuffer;
    @Getter
    private FrameBuffer dynamicFrameBuffer;

    private int staticVertexArrayObjectId;
    private int staticVertexBufferObjectId;
    private GpuFloatBuffer workingShadowVertexBuffer;
    private GpuFloatBuffer currentShadowVertexBuffer;
    private GpuFloatBuffer workingShadowUvBuffer;
    private GpuFloatBuffer currentShadowUvBuffer;

    private int dynamicVertexArrayObjectId;
    private int dynamicVertexBufferObjectId;
    private GpuFloatBuffer dynamicShadowVertexBuffer;
    private GpuFloatBuffer dynamicShadowUvBuffer;

    private int numStaticModels = 0;
    private int numStaticVertices = 0;
    private int newNumStaticSceneVertices = 0;

    private int numDynamicModels = 0;
    private int numDynamicVertices = 0;

    private boolean loadingScene = false;
    private Scene cachedScene = null;

    public List<Integer> objectsToConsiderDynamicShadows = new ArrayList<>();

    @Override
    public void Init() {
        FrameBuffer.FrameBufferSettings fboSettings = new FrameBuffer.FrameBufferSettings();
        fboSettings.name = "shadow_pass";
        fboSettings.width = plugin.config.shadowResolution().getValue();
        fboSettings.height = plugin.config.shadowResolution().getValue();
        fboSettings.glAttachment = GL_DEPTH_ATTACHMENT;
        fboSettings.awtContext = plugin.awtContext;

        Texture2D.TextureSettings textureSettings = new Texture2D.TextureSettings();
        textureSettings.internalFormat = GL_DEPTH_COMPONENT24;
        textureSettings.format = GL_DEPTH_COMPONENT;
        textureSettings.type = GL_FLOAT;
        textureSettings.minFilter = GL_NEAREST;
        textureSettings.magFilter = GL_NEAREST;
        textureSettings.wrapS = GL_CLAMP_TO_EDGE;
        textureSettings.wrapT = GL_CLAMP_TO_EDGE;

        frameBuffer = new FrameBuffer(fboSettings, textureSettings);
        dynamicFrameBuffer = new FrameBuffer(fboSettings, textureSettings);

        InitStaticShadowBuffer();
        InitDynamicShadowBuffer();
        log.info("[Shadow Pass] Initialized Shadow Render Pass");
    }

    private void InitStaticShadowBuffer() {
        staticVertexArrayObjectId = GL30.glGenVertexArrays();
        staticVertexBufferObjectId = GL30.glGenBuffers();

        GL30.glBindVertexArray(staticVertexArrayObjectId);

        glEnableVertexAttribArray(VPOS_BINDING_ID);
        glBindBuffer(GL_ARRAY_BUFFER, staticVertexBufferObjectId);
        glVertexAttribPointer(VPOS_BINDING_ID, 3, GL_FLOAT, false, 16, 0);

        glEnableVertexAttribArray(VHSL_BINDING_ID);
        glBindBuffer(GL_ARRAY_BUFFER, staticVertexBufferObjectId);
        glVertexAttribIPointer(VHSL_BINDING_ID, 1, GL_INT, 16, 12);

        glEnableVertexAttribArray(VUV_BINDING_ID);
        glBindBuffer(GL_ARRAY_BUFFER, staticVertexBufferObjectId);
        glVertexAttribPointer(VUV_BINDING_ID, 4, GL_FLOAT, false, 0, 0);

        GL30.glBindVertexArray(0);
        glEnableVertexAttribArray(0);
    }

    private void InitDynamicShadowBuffer() {
        dynamicVertexArrayObjectId = GL30.glGenVertexArrays();
        dynamicVertexBufferObjectId = GL30.glGenBuffers();

        GL30.glBindVertexArray(dynamicVertexArrayObjectId);

        glEnableVertexAttribArray(VPOS_BINDING_ID);
        glBindBuffer(GL_ARRAY_BUFFER, dynamicVertexBufferObjectId);
        glVertexAttribPointer(VPOS_BINDING_ID, 3, GL_FLOAT, false, 16, 0);

        glEnableVertexAttribArray(VHSL_BINDING_ID);
        glBindBuffer(GL_ARRAY_BUFFER, dynamicVertexBufferObjectId);
        glVertexAttribIPointer(VHSL_BINDING_ID, 1, GL_INT, 16, 12);

        glEnableVertexAttribArray(VUV_BINDING_ID);
        glBindBuffer(GL_ARRAY_BUFFER, dynamicVertexBufferObjectId);
        glVertexAttribPointer(VUV_BINDING_ID, 4, GL_FLOAT, false, 0, 0);

        GL30.glBindVertexArray(0);
        glEnableVertexAttribArray(0);

        dynamicShadowVertexBuffer = new GpuFloatBuffer();
        dynamicShadowUvBuffer = new GpuFloatBuffer();
    }

    private void GatherSceneGeometry(Scene scene, int sceneId) {
        int vertexCount = 0;
        Tile[][][] tiles = scene.getExtendedTiles();

        for (int z = 0; z < Constants.MAX_Z; z++) {
            for (int x = 0; x < Constants.EXTENDED_SCENE_SIZE; x++) {
                for (int y = 0; y < Constants.EXTENDED_SCENE_SIZE; y++) {
                    Tile tile = tiles[z][x][y];
                    if (tile == null) {
                        continue;
                    }

                    boolean shouldSkipTile = false;
                    if (plugin.environmentManager.currentArea != null) {
                        Area currentArea = plugin.environmentManager.currentArea;
                        Bounds[] areaBounds = currentArea.getBounds();
                        if (areaBounds != null && currentArea.isHideOtherAreas()) {
                            WorldPoint tileLocation = tile.getWorldLocation();
                            for (Bounds currentSubBounds : areaBounds) {
                                if (!currentSubBounds.contains(tileLocation, 2)) {
                                    shouldSkipTile = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (shouldSkipTile)
                        continue;

                    SceneTilePaint sceneTilePaint = tile.getSceneTilePaint();
                    if (sceneTilePaint != null)
                    {
                        TileContext tileContext = new TileContext(scene, tile);
                        vertexCount += PushTile(tileContext, workingShadowVertexBuffer, workingShadowUvBuffer);
                    }

                    SceneTileModel sceneTileModel = tile.getSceneTileModel();
                    if (sceneTileModel != null)
                    {
                        TileContext tileContext = new TileContext(scene, tile);
                        vertexCount += PushComplexTile(tileContext, workingShadowVertexBuffer, workingShadowUvBuffer);
                    }

                    Tile bridge = tile.getBridge();
                    if (bridge != null)
                    {
                        SceneTileModel bridgeModelComplex = bridge.getSceneTileModel();
                        if (bridgeModelComplex != null)
                        {
                            TileContext bridgeContext = new TileContext(scene, bridge);
                            vertexCount += PushComplexTile(bridgeContext, workingShadowVertexBuffer, workingShadowUvBuffer);
                        }

                        SceneTilePaint bridgePaint = bridge.getSceneTilePaint();
                        if (bridgePaint != null)
                        {
                            TileContext bridgeContext = new TileContext(scene, bridge);
                            vertexCount += PushTile(bridgeContext, workingShadowVertexBuffer, workingShadowUvBuffer);
                        }
                    }

                    WallObject wallObject = tile.getWallObject();
                    if (wallObject != null)
                    {
                        RenderableContext r1ctx = new RenderableContext(
                                wallObject.getRenderable1(),
                                sceneId,
                                wallObject.getX(),
                                wallObject.getY(),
                                wallObject.getZ(),
                                wallObject.getOrientationA(),
                                wallObject.getHash(),
                                true
                        );
                        RenderableContext r2ctx = new RenderableContext(
                                wallObject.getRenderable2(),
                                sceneId,
                                wallObject.getX(),
                                wallObject.getY(),
                                wallObject.getZ(),
                                wallObject.getOrientationB(),
                                wallObject.getHash(),
                                true
                        );

                        vertexCount += PushStaticRenderable(r1ctx, workingShadowVertexBuffer, workingShadowUvBuffer);
                        vertexCount += PushStaticRenderable(r2ctx, workingShadowVertexBuffer, workingShadowUvBuffer);
                    }

                    DecorativeObject decorativeObject = tile.getDecorativeObject();
                    if (decorativeObject != null)
                    {
                        RenderableContext ctx = new RenderableContext(
                                decorativeObject.getRenderable(),
                                sceneId,
                                decorativeObject.getX(),
                                decorativeObject.getY(),
                                decorativeObject.getZ(),
                                0,
                                decorativeObject.getHash(),
                                true
                        );
                        vertexCount += PushStaticRenderable(ctx, workingShadowVertexBuffer, workingShadowUvBuffer);
                    }

                    GameObject[] gameObjects = tile.getGameObjects();
                    for (GameObject gameObject : gameObjects) {
                        if (gameObject == null) continue;
                        if (objectsToConsiderDynamicShadows.contains(gameObject.getId())) continue;

                        RenderableContext ctx = new RenderableContext(
                                gameObject.getRenderable(),
                                sceneId,
                                gameObject.getX(),
                                gameObject.getY(),
                                gameObject.getZ(),
                                gameObject.getModelOrientation(),
                                gameObject.getHash(),
                                true
                        );

                        vertexCount += PushStaticRenderable(ctx, workingShadowVertexBuffer, workingShadowUvBuffer);
                    }

                    // Dont render ground objects like grass, its too noisy.
                }
            }
        }

        newNumStaticSceneVertices = vertexCount;
    }

    @Override
    public void OnPreLoadScene(Scene scene) {}

    @Override
    public void OnSceneLoadStart(Scene scene) {
        Stopwatch sw = Stopwatch.createStarted();
        BuildSceneVertexBufferAsync(scene);
        sw.stop();
        log.debug("[Shadow Pass] Scene Loaded: sceneId={} numModels={} time={}ms", plugin.sceneUploader.sceneId, numStaticModels, sw.elapsed(TimeUnit.MILLISECONDS));
    }

    private void BuildSceneVertexBufferAsync(Scene scene) {
        workingShadowVertexBuffer = new GpuFloatBuffer(); // Reset the buffer for the new scene.
        workingShadowUvBuffer = new GpuFloatBuffer();
        numStaticModels = 0;

        GatherSceneGeometry(scene, plugin.sceneUploader.sceneId); // Populate the scene buffer

        workingShadowVertexBuffer.flip(); // get the buffer ready for reading.
        workingShadowUvBuffer.flip(); // get the uv buffer ready for reading.
    }

    private void UpdateSceneVertexBuffer() {
        numStaticVertices = newNumStaticSceneVertices;
        currentShadowVertexBuffer = workingShadowVertexBuffer; // Copy the working buffer, so we can use it to render. Cannot use the working buffer directly, as it's populated on another thread.
        currentShadowUvBuffer = workingShadowUvBuffer;

        glBindBuffer(GL_ARRAY_BUFFER, staticVertexBufferObjectId);
        glBufferData(GL_ARRAY_BUFFER, currentShadowVertexBuffer.getBuffer(), GL_STATIC_DRAW);

        // Done. Dispose of the old buffers
        currentShadowVertexBuffer.clear();
        currentShadowUvBuffer.clear();

        workingShadowVertexBuffer.clear();
        workingShadowUvBuffer.clear();
    }

    /** Called from {@link com.gpuExtended.GpuExtendedPlugin#swapScene(Scene)}*/
    @Override
    public void OnSceneLoadFinished(Scene scene) {
        UpdateSceneVertexBuffer();
        OnRenderStaticShadowMap();
        OnRenderDynamicShadowMap();

        cachedScene = scene; // Cache the scene for later use.
        sceneGeometryDirty = false; // Reset the dirty flag after the scene is loaded and buffers are updated.
    }

    @Override
    public void OnPreRenderFrame() {
        plugin.performanceOverlay.StartTimer(PerformanceOverlay.TimerType.DRAW_SHADOW_PASS);
    }

    @Override
    public void OnRenderFrame() {
        dynamicShadowVertexBuffer.flip();
        dynamicShadowUvBuffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, dynamicVertexBufferObjectId);
        glBufferData(GL_ARRAY_BUFFER, dynamicShadowVertexBuffer.getBuffer(), GL_DYNAMIC_DRAW);
        OnRenderDynamicShadowMap();

        RebuildSceneIfDirty();
    }

    private void RebuildSceneIfDirty() {
        if (cachedScene == null) return;
        if (!sceneGeometryDirty || loadingScene || loadingNewSceneGeometry) return;
        if (plugin.client.getGameState() != GameState.LOGGED_IN) return;

        log.info("[Shadow Pass] Starting background rebuild of scene : sceneId={}", plugin.sceneUploader.sceneId);

        this.sceneGeometryDirty = false;
        this.loadingNewSceneGeometry = true;
        Thread t = new Thread(() -> {
            BuildSceneVertexBufferAsync(cachedScene);
            log.info("[Shadow Pass] Building scene geometry: sceneId={} numModels={} numVertices={}",
                    plugin.sceneUploader.sceneId, numStaticModels, newNumStaticSceneVertices);

            plugin.clientThread.invokeLater( () -> {
                log.info("[Shadow Pass] Scene geometry updated: sceneId={} numModels={} numVertices={}",
                        plugin.sceneUploader.sceneId, numStaticModels, newNumStaticSceneVertices);
                this.loadingNewSceneGeometry = false;

                this.UpdateSceneVertexBuffer();
                this.OnRenderStaticShadowMap();
            });
        });
        t.start();
    }

    @Override
    public void OnPostRenderFrame() {
//        log.info("[Shadow Pass] Draw Scene Complete - Static Models: {}, Static Vertices: {}, Dynamic Models: {}, Dynamic Vertices: {}", numStaticModels, numStaticVertices, numDynamicModels, numDynamicVertices);

        numDynamicModels = 0;
        numDynamicVertices = 0;
        dynamicShadowVertexBuffer = new GpuFloatBuffer();
        dynamicShadowUvBuffer = new GpuFloatBuffer();

        plugin.performanceOverlay.EndTimer(PerformanceOverlay.TimerType.DRAW_SHADOW_PASS);
    }

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
    public void OnDrawModel(Projection projection, Scene scene, Renderable renderable, int orientation, int x, int y, int z, long hash) {
        // TODO:: Gather all dynamic models (NPCS, Players, Projectiles, animated objects, and specific "static" objects, like trees)

        Model model, offsetModel;

        // TODO:: make a hashmap to track bad object ids to skip (53882 causes massive overdraw in guthix temple entrance)
        int objectId = (int)(hash >> 20);

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

        // See SceneUploader.PushStaticModel to understand what this is doing.
        boolean isStaticModel = (offsetModel.getSceneId() & ~0xF) == (plugin.sceneId & ~0xF);
        boolean isForcedDynamic = objectsToConsiderDynamicShadows.contains(objectId);
        if (!isStaticModel || isForcedDynamic) {
            RenderableContext ctx = new RenderableContext(
                renderable,
                offsetModel.getSceneId(),
                x,
                z,
                y,
                orientation,
                hash,
                false
            );
//            log.info("Pos dynamic renderable: XYZ ({}, {}, {}) {}, {}", x, y, z, orientation, hash);
            numDynamicVertices += PushDynamicRenderable(ctx, dynamicShadowVertexBuffer, dynamicShadowUvBuffer);
        }
    }
    @Override
    public void OnGameStateChanged(GameStateChanged gameStateChanged) {}

    public void OnTick() {
        OnRenderStaticShadowMap();
    }

    public void OnRenderStaticShadowMap() {
        if (plugin.client.getGameState().getState() > GameState.LOGGED_IN.getState()) {
            return;
        }

        plugin.PushDebug(plugin.shaders.shadowPassShader);
        glViewport(0, 0, frameBuffer.getTexture().getWidth(), frameBuffer.getTexture().getHeight());
        frameBuffer.bind();

        glClearDepthf(1);
        glClear(GL_DEPTH_BUFFER_BIT);
        glDepthFunc(GL_LEQUAL);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        int shaderProgram = plugin.shaders.shadowPassShader.id();
        glUseProgram(shaderProgram);
        ShaderVariables shaderVars = plugin.uniforms.GetUniforms(shaderProgram);

        glUniform1i(shaderVars.Textures, 1);
        if (plugin.texAnims != null) {
            glUniform2fv(shaderVars.TextureAnimations, plugin.texAnims);
        }

        glActiveTexture(GL_TEXTURE8);
        glBindTexture(GL_TEXTURE_2D, plugin.uniforms.getBlueNoiseTexture().getId());
        glUniform1i(shaderVars.BlueNoiseTexture, 8);

        glUniformBlockBinding(shaderProgram, shaderVars.CameraBlock, CAMERA_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, shaderVars.PlayerBlock,  PLAYER_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, shaderVars.EnvironmentBlock, ENVIRONMENT_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, shaderVars.TileMarkerBlock, TILEMARKER_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, shaderVars.SystemInfoBlock, SYSTEMINFO_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, shaderVars.ConfigBlock, CONFIG_BUFFER_BINDING_ID);

        glUniformMatrix4fv(shaderVars.DepthProjectionMatrix, false, plugin.environmentManager.mainLight.projectionMatrix);

        GL30.glBindVertexArray(staticVertexArrayObjectId);
        glDrawArrays(GL_TRIANGLES, 0, numStaticVertices);

        glCullFace(GL_BACK);
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);

        frameBuffer.unbind();
        glUseProgram(0);
        GL30.glBindVertexArray(0);
        plugin.PopDebug();
    }

    public void OnRenderDynamicShadowMap() {
        if (plugin.client.getGameState().getState() > GameState.LOGGED_IN.getState()) {
            return;
        }

        plugin.PushDebug(plugin.shaders.shadowPassShader);
        glViewport(0, 0, dynamicFrameBuffer.getTexture().getWidth(), dynamicFrameBuffer.getTexture().getHeight());
        dynamicFrameBuffer.bind();

        glClearDepthf(1);
        glClear(GL_DEPTH_BUFFER_BIT);
        glDepthFunc(GL_LEQUAL);

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        int shaderProgram = plugin.shaders.shadowPassShader.id();
        glUseProgram(shaderProgram);
        ShaderVariables shaderVars = plugin.uniforms.GetUniforms(shaderProgram);

        glUniform1i(shaderVars.Textures, 1);
        if (plugin.texAnims != null) {
            glUniform2fv(shaderVars.TextureAnimations, plugin.texAnims);
        }

        glActiveTexture(GL_TEXTURE8);
        glBindTexture(GL_TEXTURE_2D, plugin.uniforms.getBlueNoiseTexture().getId());
        glUniform1i(shaderVars.BlueNoiseTexture, 8);

        glUniformBlockBinding(shaderProgram, shaderVars.CameraBlock, CAMERA_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, shaderVars.PlayerBlock,  PLAYER_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, shaderVars.EnvironmentBlock, ENVIRONMENT_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, shaderVars.TileMarkerBlock, TILEMARKER_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, shaderVars.SystemInfoBlock, SYSTEMINFO_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, shaderVars.ConfigBlock, CONFIG_BUFFER_BINDING_ID);

        glUniformMatrix4fv(shaderVars.DepthProjectionMatrix, false, plugin.environmentManager.mainLight.projectionMatrixClose);

        GL30.glBindVertexArray(dynamicVertexArrayObjectId);
        glDrawArrays(GL_TRIANGLES, 0, numDynamicVertices);

        glCullFace(GL_BACK);
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);

        dynamicFrameBuffer.unbind();
        glUseProgram(0);
        GL30.glBindVertexArray(0);
        plugin.PopDebug();
    }

    private int PushTile(TileContext context, GpuFloatBuffer vertexBuffer, GpuFloatBuffer uvBuffer) {
        final int[][][] tileHeights = context.scene.getTileHeights();

        // These used to be fed in through an overload in sceneUploader,
        // but they were always 0. So no need to pass them in anymore.
        // Keeping the variables here just in case for later.
        final int localX = 0;
        final int localY = 0;

        final int tileX = context.x + SCENE_OFFSET;
        final int tileY = context.y + SCENE_OFFSET;
        final int tileZ = context.plane;

//        log.info("Pushing tile at ({}, {}, {}) with paint: {}", tileX, tileY, context.plane, context.tilePaint);

        int swHeight = tileHeights[tileZ][tileX    ][tileY    ];
        int seHeight = tileHeights[tileZ][tileX + 1][tileY    ];
        int neHeight = tileHeights[tileZ][tileX + 1][tileY + 1];
        int nwHeight = tileHeights[tileZ][tileX    ][tileY + 1];

        final int neColor = context.tilePaint.getNeColor();
        final int nwColor = context.tilePaint.getNwColor();
        final int seColor = context.tilePaint.getSeColor();
        final int swColor = context.tilePaint.getSwColor();

        if (neColor == 12345678)
        {
            return 0;
        }

        // Since we're skipping compute shader for shadow map, we want to be in world space already.
        // Normally in sceneUploader, it would be in local space, and then transformed to world space by the shader.
        final int tileWorldX = context.x * LOCAL_TILE_SIZE;
        final int tileWorldY = context.y * LOCAL_TILE_SIZE;

        // 0,0
        int vertexDx = tileWorldX;
        int vertexDy = tileWorldY;
        int vertexDz = swHeight;
        final int c1 = swColor;

        // 1,0
        int vertexCx = tileWorldX + Perspective.LOCAL_TILE_SIZE;
        int vertexCy = tileWorldY;
        int vertexCz = seHeight;
        final int c2 = seColor;

        // 1,1
        int vertexAx = tileWorldX + Perspective.LOCAL_TILE_SIZE;
        int vertexAy = tileWorldY + Perspective.LOCAL_TILE_SIZE;
        int vertexAz = neHeight;
        final int c3 = neColor;

        // 0,1
        int vertexBx = tileWorldX;
        int vertexBy = tileWorldY + Perspective.LOCAL_TILE_SIZE;
        int vertexBz = nwHeight;
        final int c4 = nwColor;

        vertexBuffer.ensureCapacity(24); // 6 vertices * 4 floats per vertex (x, y, z, w)
        uvBuffer.ensureCapacity(24); // 6 vertices * 4 floats per vertex (textureId, u, v, w)

        vertexBuffer.put(vertexAx, vertexAz, vertexAy, 0);
        vertexBuffer.put(vertexBx, vertexBz, vertexBy, 0);
        vertexBuffer.put(vertexCx, vertexCz, vertexCy, 0);

        vertexBuffer.put(vertexDx, vertexDz, vertexDy, 0);
        vertexBuffer.put(vertexCx, vertexCz, vertexCy, 0);
        vertexBuffer.put(vertexBx, vertexBz, vertexBy, 0);

        PadBufferTriangle(uvBuffer, 6); // No UVs for tiles, pad the buffer with 0s.

        return 6;
    }

    private int PushComplexTile(TileContext context, GpuFloatBuffer vertexBuffer, GpuFloatBuffer uvBuffer) {
        final int[] faceX = context.tileModel.getFaceX();
        final int[] faceY = context.tileModel.getFaceY();
        final int[] faceZ = context.tileModel.getFaceZ();

        final int[] vertexX = context.tileModel.getVertexX();
        final int[] vertexY = context.tileModel.getVertexY();
        final int[] vertexZ = context.tileModel.getVertexZ();

        final int[] triangleColorA = context.tileModel.getTriangleColorA();
        final int[] triangleColorB = context.tileModel.getTriangleColorB();
        final int[] triangleColorC = context.tileModel.getTriangleColorC();

        final int[] triangleTextures = context.tileModel.getTriangleTextureId();

        final int faceCount = faceX.length;
        vertexBuffer.ensureCapacity(faceCount * 12); // 3 vertices * 4 floats per vertex (x, y, z, color)
        uvBuffer.ensureCapacity(faceCount * 12); // 3 vertices * 4 floats per vertex (textureId, u, v, w)

        int baseX = context.x << Perspective.LOCAL_COORD_BITS;
        int baseY = context.y << Perspective.LOCAL_COORD_BITS;

        int vertexCount = 0;
        for (int i = 0; i < faceCount; ++i) {
            final int triangleA = faceX[i];
            final int triangleB = faceY[i];
            final int triangleC = faceZ[i];

            final int colorA = triangleColorA[i];
            final int colorB = triangleColorB[i];
            final int colorC = triangleColorC[i];

            if (colorA == 12345678) {
                continue;
            }

            // vertexes are stored in scene local, convert to tile local
            int vertexXA = vertexX[triangleA];
            int vertexYA = vertexY[triangleA];
            int vertexZA = vertexZ[triangleA];

            int vertexXB = vertexX[triangleB];
            int vertexYB = vertexY[triangleB];
            int vertexZB = vertexZ[triangleB];

            int vertexXC = vertexX[triangleC];
            int vertexYC = vertexY[triangleC];
            int vertexZC = vertexZ[triangleC];

            vertexBuffer.put(vertexXA, vertexYA, vertexZA, 0);
            vertexBuffer.put(vertexXB, vertexYB, vertexZB, 0);
            vertexBuffer.put(vertexXC, vertexYC, vertexZC, 0);

            PadBufferTriangle(uvBuffer, 3); // No UVs for complex tiles, pad the buffer with 0s.

            vertexCount += 3;
        }

        return vertexCount;
    }

    private int PushStaticRenderable(RenderableContext context, GpuFloatBuffer vertexBuffer, GpuFloatBuffer uvBuffer) {
        numStaticModels++;
        return PushRenderable(context, vertexBuffer, uvBuffer);
    }

    private int PushDynamicRenderable(RenderableContext context, GpuFloatBuffer vertexBuffer, GpuFloatBuffer uvBuffer) {
        numDynamicModels++;
        return PushRenderable(context, vertexBuffer, uvBuffer);
    }

    private Model GetRenderableModel(RenderableContext context, boolean isStatic) {
        Model model;
        Model offsetModel;
        if (context.renderable instanceof Model)
        {
            model = (Model) context.renderable;
            offsetModel = model.getUnskewedModel();
            if (offsetModel == null)
            {
                offsetModel = model;
            }
        }
        else
        {
            // Dynamics must be on the main thread, and the extra check here isn't thread safe.
            if (isStatic) {
                return null;
            } else {
                model = context.renderable.getModel();
                if (model == null)
                {
                    return null;
                }
                offsetModel = model;
            }
        }

        return offsetModel;
    }

    private int PushRenderable(RenderableContext context, GpuFloatBuffer vertexBuffer, GpuFloatBuffer uvBuffer) {
        if (context.renderable == null) return 0;

        Model model = GetRenderableModel(context, context.isStatic);
        if (model == null) {
            return 0; // No model to render
        }

        final int triCount = Math.min(model.getFaceCount(), MAX_TRIANGLE);
        vertexBuffer.ensureCapacity(triCount * 12); // 3 vertices * 4 floats per vertex (x, y, z, w)
        uvBuffer.ensureCapacity(triCount * 12); // 3 vertices * 4 floats per vertex (textureId, u, v, w)

        final int[] indices1 = model.getFaceIndices1();
        final int[] indices2 = model.getFaceIndices2();
        final int[] indices3 = model.getFaceIndices3();

        float[] vx = model.getVerticesX();
        float[] vy = model.getVerticesY();
        float[] vz = model.getVerticesZ();

        final int[] color3s = model.getFaceColors3();

        final short[] faceTextures = model.getFaceTextures();
        final byte[] textureFaces = model.getTextureFaces();
        final int[] texIndices1 = model.getTexIndices1();
        final int[] texIndices2 = model.getTexIndices2();
        final int[] texIndices3 = model.getTexIndices3();

        final byte[] transparencies = model.getFaceTransparencies();

        float rotationInRadians = context.orientation * (float)UNIT;
        float cos = (float) Math.cos(rotationInRadians);
        float sin = (float) Math.sin(rotationInRadians);

        int vertexCount = 0;
        for (int tri = 0; tri < triCount; tri++) {
            int i0 = indices1[tri];
            int i1 = indices2[tri];
            int i2 = indices3[tri];

            int color3 = color3s[tri];
            int alpha = getFaceAlpha(faceTextures, transparencies, tri);

            if (color3 == -2 || alpha == -2) // Model should be skipped. Pad buffer.
            {
                PadBufferTriangle(vertexBuffer, 3);
                PadBufferTriangle(uvBuffer, 3);
                vertexCount += 3;
                continue;
            }

            if (faceTextures != null)
            {
                if (faceTextures[tri] != -1)
                {
                    int texA, texB, texC;

                    if (textureFaces != null && textureFaces[tri] != -1)
                    {
                        int tface = textureFaces[tri] & 0xff;
                        texA = texIndices1[tface];
                        texB = texIndices2[tface];
                        texC = texIndices3[tface];
                    }
                    else
                    {
                        texA = i0;
                        texB = i1;
                        texC = i2;
                    }

                    int texture = faceTextures[tri] + 1;
                    uvBuffer.put(texture, vx[texA] + context.x, vy[texA] + context.z, vz[texA] + context.y);
                    uvBuffer.put(texture, vx[texB] + context.x, vy[texB] + context.z, vz[texB] + context.y);
                    uvBuffer.put(texture, vx[texC] + context.x, vy[texC] + context.z, vz[texC] + context.y);
                }
                else
                {
                    PadBufferTriangle(uvBuffer, 3);
                }
            }

            float vertexAx = vx[i0];
            float vertexAy = vy[i0];
            float vertexAz = vz[i0];

            float vertexBx = vx[i1];
            float vertexBy = vy[i1];
            float vertexBz = vz[i1];

            float vertexCx = vx[i2];
            float vertexCy = vy[i2];
            float vertexCz = vz[i2];
            if (context.orientation != 0)
            {
                vertexAx = vx[i0] * cos + vz[i0] * sin;
                vertexAz = vz[i0] * cos - vx[i0] * sin;

                vertexBx = vx[i1] * cos + vz[i1] * sin;
                vertexBz = vz[i1] * cos - vx[i1] * sin;

                vertexCx = vx[i2] * cos + vz[i2] * sin;
                vertexCz = vz[i2] * cos - vx[i2] * sin;
            }

            vertexBuffer.put(vertexAx + context.x, vertexAy + context.z, vertexAz + context.y, alpha);
            vertexBuffer.put(vertexBx + context.x, vertexBy + context.z, vertexBz + context.y, alpha);
            vertexBuffer.put(vertexCx + context.x, vertexCy + context.z, vertexCz + context.y, alpha);

            vertexCount += 3;
        }

        return vertexCount;
    }

    private void PadBufferTriangle(GpuFloatBuffer buffer, int numPaddedVertices) {
        for (int i = 0; i < numPaddedVertices; i++) {
            buffer.put(0, 0, 0, 0); // x, y, z, w
        }
    }

    public void OnGameObjectSpawned(GameObjectSpawned event) {
        GameObject gameObject = event.getGameObject();
        if (gameObject == null) return;

        Renderable renderable = gameObject.getRenderable();
        if (renderable == null) return;

        Model model;
        Model offsetModel;
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
            if (model == null) return;

            offsetModel = model;
        }

        boolean isStatic = (offsetModel.getSceneId() & ~0xF) == (plugin.sceneId & ~0xF);
        if (isStatic)
            sceneGeometryDirty = true; // Mark the scene as dirty, so we can rebuild the vertex buffer on next frame.
    }

    public void OnGameObjectDespawned(GameObjectDespawned event) {
        GameObject gameObject = event.getGameObject();
        if (gameObject == null) return;

        Renderable renderable = gameObject.getRenderable();
        if (renderable == null) return;

        Model model;
        Model offsetModel;
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
            if (model == null) return;

            offsetModel = model;
        }

        boolean isStatic = (offsetModel.getSceneId() & ~0xF) == (plugin.sceneId & ~0xF);
        if (isStatic)
            sceneGeometryDirty = true; // Mark the scene as dirty, so we can rebuild the vertex buffer on next frame.
    }

    public void Dispose() {
        GL30.glDeleteVertexArrays(staticVertexArrayObjectId);
        staticVertexArrayObjectId = -1;

        frameBuffer.dispose();
        frameBuffer = null;

        workingShadowVertexBuffer = null;
        currentShadowVertexBuffer = null;
        workingShadowUvBuffer = null;
        currentShadowUvBuffer = null;

        numStaticModels = 0;
        numStaticVertices = 0;
        newNumStaticSceneVertices = 0;
        numDynamicModels = 0;
        numDynamicVertices = 0;
    }
}
