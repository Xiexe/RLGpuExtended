package com.gpuExtended.rendering.passes;

import com.google.common.base.Stopwatch;
import com.google.inject.Singleton;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.regions.Area;
import com.gpuExtended.regions.Bounds;
import com.gpuExtended.rendering.FrameBuffer;
import com.gpuExtended.rendering.Texture2D;
import com.gpuExtended.shader.Uniforms;
import com.gpuExtended.util.GpuFloatBuffer;
import com.gpuExtended.util.contexts.RenderableContext;
import com.gpuExtended.util.contexts.TileContext;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.rlawt.AWTContext;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

import static com.gpuExtended.util.SceneUploader.*;
import static com.gpuExtended.util.constants.Variables.*;
import static net.runelite.api.Perspective.LOCAL_TILE_SIZE;
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

    // TODO:: Figure out a way to do this without 2 framebuffer maybe.
    private FrameBuffer frameBuffer;
    private FrameBuffer dynamicFrameBuffer;

    private int staticVertexArrayObjectId;
    private int staticVertexBufferObjectId;
    private GpuFloatBuffer workingShadowVertexBuffer;
    private GpuFloatBuffer currentShadowVertexBuffer;
    private GpuFloatBuffer workingShadowUvBuffer;
    private GpuFloatBuffer currentShadowUvBuffer;


    private int textureArrayId = -1;
    private int numStaticModels = 0;
    private int numStaticVertices = 0;
    private int newNumStaticSceneVertices = 0;

    private boolean loadingScene = false;

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
        log.info("[Shadow Pass] Initialized Shadow Render Pass");
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

                    Point tilePoint = tile.getSceneLocation();
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
//
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
                                wallObject.getHash()
                        );
                        RenderableContext r2ctx = new RenderableContext(
                                wallObject.getRenderable2(),
                                sceneId,
                                wallObject.getX(),
                                wallObject.getY(),
                                wallObject.getZ(),
                                wallObject.getOrientationB(),
                                wallObject.getHash()
                        );

                        vertexCount += PushRenderable(r1ctx, workingShadowVertexBuffer, workingShadowUvBuffer);
                        vertexCount += PushRenderable(r2ctx, workingShadowVertexBuffer, workingShadowUvBuffer);
                    }

                    // We only want gameobjects that are above the player. (so things like trees get handled by the dynamic map)
                    if (z > plugin.client.getLocalPlayer().getWorldLocation().getPlane()) {
                        GroundObject groundObject = tile.getGroundObject();
                        if (groundObject != null)
                        {
                            RenderableContext ctx = new RenderableContext(
                                    groundObject.getRenderable(),
                                    sceneId,
                                    groundObject.getX(),
                                    groundObject.getY(),
                                    groundObject.getZ(),
                                    0,
                                    groundObject.getHash()
                            );
                            vertexCount += PushRenderable(ctx, workingShadowVertexBuffer, workingShadowUvBuffer);
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
                                    decorativeObject.getHash()
                            );
                            vertexCount += PushRenderable(ctx, workingShadowVertexBuffer, workingShadowUvBuffer);
                        }

                        GameObject[] gameObjects = tile.getGameObjects();
                        for (GameObject gameObject : gameObjects) {
                            if (gameObject == null) continue;
                            RenderableContext ctx = new RenderableContext(
                                    gameObject.getRenderable(),
                                    sceneId,
                                    gameObject.getX(),
                                    gameObject.getY(),
                                    gameObject.getZ(),
                                    gameObject.getModelOrientation(),
                                    gameObject.getHash()
                            );
                            vertexCount += PushRenderable(ctx, workingShadowVertexBuffer, workingShadowUvBuffer);
                        }
                    }
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

        workingShadowVertexBuffer = new GpuFloatBuffer(); // Reset the buffer for the new scene.
        workingShadowUvBuffer = new GpuFloatBuffer();
        numStaticModels = 0;

        GatherSceneGeometry(scene, plugin.sceneUploader.sceneId); // Populate the scene buffer

        workingShadowVertexBuffer.flip(); // get the buffer ready for reading.
        workingShadowUvBuffer.flip(); // get the uv buffer ready for reading.

        sw.stop();
        log.debug("[Shadow Pass] Scene Loaded: sceneId={} numModels={} time={}ms", plugin.sceneUploader.sceneId, numStaticModels, sw.elapsed(TimeUnit.MILLISECONDS));
    }

    /** Called from {@link com.gpuExtended.GpuExtendedPlugin#swapScene(Scene)}*/
    @Override
    public void OnSceneLoadFinished(Scene scene) {
        numStaticVertices = newNumStaticSceneVertices;
        currentShadowVertexBuffer = workingShadowVertexBuffer; // Copy the working buffer, so we can use it to render. Cannot use the working buffer directly, as it's populated on another thread.
        currentShadowUvBuffer = workingShadowUvBuffer;

        glBindBuffer(GL_ARRAY_BUFFER, staticVertexBufferObjectId);
        glBufferData(GL_ARRAY_BUFFER, currentShadowVertexBuffer.getBuffer(), GL_STATIC_DRAW);

        // Done. Dispose of the old buffers
        currentShadowVertexBuffer = null;
        workingShadowVertexBuffer = null;
        currentShadowUvBuffer = null;
        workingShadowUvBuffer = null;
    }

    @Override
    public void OnPreRenderFrame() {}

    @Override
    public void OnRenderFrame() {
        OnRenderStaticShadowMap();
        OnRenderDynamicShadowMap();
    }

    @Override
    public void OnPostRenderFrame() {}

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

    /** Called anywhere in the render loop, but probably after {@link GpuExtendedPlugin#drawMainPass}*/
    public void OnRenderStaticShadowMap() {
        glViewport(0, 0, frameBuffer.getTexture().getWidth(), frameBuffer.getTexture().getHeight());
        frameBuffer.bind();

        glClearDepthf(1);
        glClear(GL_DEPTH_BUFFER_BIT);
        glDepthFunc(GL_LEQUAL);

        int shaderProgram = plugin.shaderHandler.shadowPassShader.id();
        glUseProgram(shaderProgram);
        Uniforms.ShaderVariables uni = plugin.uniforms.GetUniforms(shaderProgram);

        glUniformBlockBinding(shaderProgram, uni.CameraBlock, CAMERA_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, uni.PlayerBlock,  PLAYER_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, uni.EnvironmentBlock, ENVIRONMENT_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, uni.TileMarkerBlock, TILEMARKER_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, uni.SystemInfoBlock, SYSTEMINFO_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, uni.ConfigBlock, CONFIG_BUFFER_BINDING_ID);

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glEnable(GL_DEPTH_TEST);

        int lastVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(staticVertexArrayObjectId);

        glDrawArrays(GL_TRIANGLES, 0, numStaticVertices);
        GL30.glBindVertexArray(lastVertexArray);

        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);

        frameBuffer.unbind();
        glUseProgram(0);

//        log.info("Rendering Shadow Map: numModels={} numVertices={}", numModels, numVertices);
    }

    public void OnRenderDynamicShadowMap() {
//        log.info("[Shadow Pass] Rendering dynamic shadow map from offset: {} from buffer with length: {}", plugin.tempOffset, plugin.vertexBuffer.getBuffer());
        glViewport(0, 0, frameBuffer.getTexture().getWidth(), frameBuffer.getTexture().getHeight());
        dynamicFrameBuffer.bind();

        glClearDepthf(1);
        glClear(GL_DEPTH_BUFFER_BIT);
        glDepthFunc(GL_LEQUAL);

        int shaderProgram = plugin.shaderHandler.shadowPassShader.id();
        glUseProgram(shaderProgram);
        Uniforms.ShaderVariables uni = plugin.uniforms.GetUniforms(shaderProgram);

        glUniformBlockBinding(shaderProgram, uni.CameraBlock, CAMERA_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, uni.PlayerBlock,  PLAYER_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, uni.EnvironmentBlock, ENVIRONMENT_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, uni.TileMarkerBlock, TILEMARKER_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, uni.SystemInfoBlock, SYSTEMINFO_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, uni.ConfigBlock, CONFIG_BUFFER_BINDING_ID);

        // TODO:: This probably should be initted somewhere else. Currently its in both the main pass and shadow pass.
//        final TextureProvider textureProvider = plugin.client.getTextureProvider();
//        if (textureArrayId == -1) {
//            // lazy init textures as they may not be loaded at plugin start.
//            // this will return -1 and retry if not all textures are loaded yet, too.
//            textureArrayId = plugin.textureManager.initTextureArray(textureProvider);
//            if (textureArrayId > -1) {
//                // if texture upload is successful, compute and set texture animations
//                float[] texAnims = plugin.textureManager.computeTextureAnimations(textureProvider);
//                glUniform2fv(uni.TextureAnimations, texAnims);
//            }
//        }
//
        glUniform1i(uni.Textures, 1); // texture sampler array is bound to texture1

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glEnable(GL_DEPTH_TEST);

        int lastVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(plugin.mainPassHandlerLegacy.vertexBufferContext.vertexArrayObjectId);

        glDrawArrays(GL_TRIANGLES, 0, plugin.mainPassHandlerLegacy.computeBufferContext.totalVertices);
        GL30.glBindVertexArray(lastVertexArray);

        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);

        dynamicFrameBuffer.unbind();
        glUseProgram(0);
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

    private int PushRenderable(RenderableContext context, GpuFloatBuffer vertexBuffer, GpuFloatBuffer uvBuffer) {
        if (context.renderable == null) return 0;

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
        else {
            return 0;
        }

        if (offsetModel.getSceneId() != context.sceneId)
             return 0;

        final int triCount = Math.min(model.getFaceCount(), MAX_TRIANGLE);
        vertexBuffer.ensureCapacity(triCount * 12); // 3 vertices * 4 floats per vertex (x, y, z, w)
        uvBuffer.ensureCapacity(triCount * 12); // 3 vertices * 4 floats per vertex (textureId, u, v, w)

        final int[] indices1 = model.getFaceIndices1();
        final int[] indices2 = model.getFaceIndices2();
        final int[] indices3 = model.getFaceIndices3();

        float[] vx = model.getVerticesX();
        float[] vy = model.getVerticesY();
        float[] vz = model.getVerticesZ();

        final int[] color1s = model.getFaceColors1();
        final int[] color2s = model.getFaceColors2();
        final int[] color3s = model.getFaceColors3();

        final short[] faceTextures = model.getFaceTextures();
        final byte[] textureFaces = model.getTextureFaces();
        final int[] texIndices1 = model.getTexIndices1();
        final int[] texIndices2 = model.getTexIndices2();
        final int[] texIndices3 = model.getTexIndices3();

        final byte[] transparencies = model.getFaceTransparencies();

        final byte overrideAmount = model.getOverrideAmount();
        final byte overrideHue = model.getOverrideHue();
        final byte overrideSat = model.getOverrideSaturation();
        final byte overrideLum = model.getOverrideLuminance();

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

            vertexBuffer.put(vx[i0] + context.x, vy[i0] + context.z, vz[i0] + context.y, alpha);
            vertexBuffer.put(vx[i1] + context.x, vy[i1] + context.z, vz[i1] + context.y, alpha);
            vertexBuffer.put(vx[i2] + context.x, vy[i2] + context.z, vz[i2] + context.y, alpha);

            vertexCount += 3;
        }

        numStaticModels++;
        return vertexCount;
    }

    private void PadBufferTriangle(GpuFloatBuffer buffer, int numPaddedVertices) {
        for (int i = 0; i < numPaddedVertices; i++) {
            buffer.put(0, 0, 0, 0); // x, y, z, w
        }
    }

    public FrameBuffer GetFramebuffer() {
        return frameBuffer;
    }

    public FrameBuffer GetDynamicFramebuffer() {
        return dynamicFrameBuffer;
    }

    public void Dispose() {
        GL30.glDeleteVertexArrays(staticVertexArrayObjectId);
        staticVertexArrayObjectId = -1;

        frameBuffer.dispose();
        frameBuffer = null;

        dynamicFrameBuffer.dispose();
        dynamicFrameBuffer = null;

        workingShadowVertexBuffer = null;
        currentShadowVertexBuffer = null;
        workingShadowUvBuffer = null;
        currentShadowUvBuffer = null;

        textureArrayId = -1;
        numStaticModels = 0;
        numStaticVertices = 0;
        newNumStaticSceneVertices = 0;
    }
}
