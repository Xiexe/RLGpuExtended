package com.gpuExtended.rendering.passes;

import com.google.common.base.Stopwatch;
import com.google.inject.Singleton;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.rendering.FrameBuffer;
import com.gpuExtended.rendering.Texture2D;
import com.gpuExtended.shader.Uniforms;
import com.gpuExtended.util.GpuFloatBuffer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.rlawt.AWTContext;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

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
public class ShadowPass {
    @Inject
    public GpuExtendedPlugin plugin;

    private FrameBuffer frameBuffer;
    private int vertexArrayObject;
    private int vertexBuffer;

    private GpuFloatBuffer nextSceneVertexBuffer;
    private int numModels = 0;
    private int numVertices = 0;
    private int nextNumVertices = 0;

    public void Init(int resolution, AWTContext awtContext) {
        FrameBuffer.FrameBufferSettings fboSettings = new FrameBuffer.FrameBufferSettings();
        fboSettings.name = "shadow_pass";
        fboSettings.width = resolution;//config.shadowResolution().getValue();
        fboSettings.height = resolution;//config.shadowResolution().getValue();
        fboSettings.glAttachment = GL_DEPTH_ATTACHMENT;
        fboSettings.awtContext = awtContext;

        Texture2D.TextureSettings textureSettings = new Texture2D.TextureSettings();
        textureSettings.internalFormat = GL_DEPTH_COMPONENT24;
        textureSettings.format = GL_DEPTH_COMPONENT;
        textureSettings.type = GL_FLOAT;
        textureSettings.minFilter = GL_NEAREST;
        textureSettings.magFilter = GL_NEAREST;
        textureSettings.wrapS = GL_CLAMP_TO_EDGE;
        textureSettings.wrapT = GL_CLAMP_TO_EDGE;

        frameBuffer = new FrameBuffer(fboSettings, textureSettings);

        vertexArrayObject = GL30.glGenVertexArrays();
        vertexBuffer = GL30.glGenBuffers();

        GL30.glBindVertexArray(vertexArrayObject);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glVertexAttribPointer(0, 4, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        GL30.glBindVertexArray(0);

        log.info("Shadow Pass Handler Initialized");
    }

    public void OnPreSceneUpdated(Scene scene) {}

    public void OnSceneLoad(Scene scene, int sceneId, GpuFloatBuffer shadowVertexBuffer) {
        Stopwatch sw = Stopwatch.createStarted();
        log.debug("OnSceneUpdated: sceneId={}", sceneId);
        scene.buildRoofs();

        numModels = 0;

        int vertexCount = 0;
        Tile[][][] tiles = scene.getExtendedTiles();
        for (int z = 0; z < Constants.MAX_Z; z++) {
            for (int x = 0; x < Constants.EXTENDED_SCENE_SIZE; x++) {
                for (int y = 0; y < Constants.EXTENDED_SCENE_SIZE; y++) {
                    Tile tile = tiles[z][x][y];
                    if (tile == null) {
                        continue;
                    }

                    Point tilePoint = tile.getSceneLocation();

//                    SceneTilePaint sceneTilePaint = tile.getSceneTilePaint();
//                    if (sceneTilePaint != null)
//                    {}
//
//                    SceneTileModel sceneTileModel = tile.getSceneTileModel();
//                    if (sceneTileModel != null)
//                    {}
//
//                    Tile bridge = tile.getBridge();
//                    if (bridge != null)
//                    {
//                        SceneTileModel bridgeModel = bridge.getSceneTileModel();
//                        if (bridgeModel != null)
//                        {}
//                    }

                    WallObject wallObject = tile.getWallObject();
                    if (wallObject != null)
                    {
                        Renderable r1 = wallObject.getRenderable1();
                        Renderable r2 = wallObject.getRenderable2();

                        vertexCount += PushRenderable(r1, sceneId, wallObject.getX(), wallObject.getY(), wallObject.getZ(), wallObject.getOrientationA(), wallObject.getHash(), shadowVertexBuffer);
                        vertexCount += PushRenderable(r2, sceneId, wallObject.getX(), wallObject.getY(), wallObject.getZ(), wallObject.getOrientationB(), wallObject.getHash(), shadowVertexBuffer);
                    }

                    GroundObject groundObject = tile.getGroundObject();
                    if (groundObject != null)
                    {
                        Renderable r = groundObject.getRenderable();
                        vertexCount += PushRenderable(r, sceneId, groundObject.getX(), groundObject.getY(), groundObject.getZ(), 0, groundObject.getHash(), shadowVertexBuffer);
                    }

                    DecorativeObject decorativeObject = tile.getDecorativeObject();
                    if (decorativeObject != null)
                    {
                        Renderable r = decorativeObject.getRenderable();
                        vertexCount += PushRenderable(r, sceneId, decorativeObject.getX(), decorativeObject.getY(), decorativeObject.getZ(), 0, decorativeObject.getHash(), shadowVertexBuffer);
                    }
//
                    GameObject[] gameObjects = tile.getGameObjects();
                    for (GameObject gameObject : gameObjects)
                    {
                        if (gameObject == null) continue;
                        Renderable r = gameObject.getRenderable();
                        vertexCount += PushRenderable(r, sceneId, gameObject.getX(), gameObject.getY(), gameObject.getZ(), gameObject.getModelOrientation(), gameObject.getHash(), shadowVertexBuffer);
                    }
                }
            }
        }

        nextNumVertices = vertexCount;

        sw.stop();
        log.debug("OnSceneUpdated: sceneId={} numModels={} numVertices={} time={}", sceneId, numModels, numVertices, sw.elapsed(TimeUnit.MILLISECONDS));
    }

    public void OnSceneUpdated(GpuFloatBuffer shadowVertexBuffer) {
        numVertices = nextNumVertices;

        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glBufferData(GL_ARRAY_BUFFER, shadowVertexBuffer.getBuffer(), GL_STATIC_DRAW);
    }

    public void OnPostSceneUpdated(Scene scene) {

    }

    public void RenderShadowMap() {
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
        glEnable(GL_DEPTH_TEST);

        int lastVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(vertexArrayObject);

        glDrawArrays(GL_TRIANGLES, 0, numVertices);
        GL30.glBindVertexArray(lastVertexArray);

        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);

        frameBuffer.unbind();
        glUseProgram(0);
    }

    public FrameBuffer GetFramebuffer() {
        return frameBuffer;
    }

    private int PushRenderable(Renderable r, int sceneId, int x, int y, int z, int orientation, long hash, GpuFloatBuffer vertexBuffer) {
        if (r == null) return 0;

        Model model;
        Model offsetModel;
        if (r instanceof Model)
        {
            model = (Model) r;
            offsetModel = model.getUnskewedModel();
            if (offsetModel == null)
            {
                offsetModel = model;
            }
        }
        else {
            return 0;
        }
//        else
//        {
//            model = r.getModel();
//            if (model == null)
//            {
//                return;
//            }
//            offsetModel = model;
//        }

        if (offsetModel.getSceneId() != sceneId)
             return 0;

        int tileX = (x / LOCAL_TILE_SIZE) + SCENE_OFFSET;
        int tileY = (z / LOCAL_TILE_SIZE) + SCENE_OFFSET;

        final int[] indices1 = model.getFaceIndices1();
        final int[] indices2 = model.getFaceIndices2();
        final int[] indices3 = model.getFaceIndices3();

        float[] vx = model.getVerticesX();
        float[] vy = model.getVerticesY();
        float[] vz = model.getVerticesZ();

        final int triCount = Math.min(model.getFaceCount(), MAX_TRIANGLE);
        vertexBuffer.ensureCapacity(triCount * 12);

        int vertexCount = 0;
        for (int tri = 0; tri < triCount; tri++) {
            int i0 = indices1[tri];
            int i1 = indices2[tri];
            int i2 = indices3[tri];

            vertexBuffer.put(vx[i0] + x, vy[i0] + z, vz[i0] + y, 0);
            vertexBuffer.put(vx[i1] + x, vy[i1] + z, vz[i1] + y, 0);
            vertexBuffer.put(vx[i2] + x, vy[i2] + z, vz[i2] + y, 0);
            vertexCount += 3;
        }

        numModels++;
        return vertexCount;
    }

    public void Dispose() {
        if (frameBuffer != null) {
            frameBuffer.cleanup();
            frameBuffer = null;
        }
        if (vertexArrayObject != 0) {
            GL30.glDeleteVertexArrays(vertexArrayObject);
            vertexArrayObject = 0;
        }
    }

    private int GetModelPackedFlags(long hash, Model model, Model offsetModel, int orientation)
    {
        int plane = (int) ((hash >> TileObject.HASH_PLANE_SHIFT) & 3);
        boolean hillskew = offsetModel != model;

        int flags = (plane << BIT_ZHEIGHT) 					 		 |
                (hillskew ? (1 << BIT_HILLSKEW) : 0)  	 		 |
                orientation;

        return flags;
    }

    private int GetExFlags(long hash, int x, int y, int z, boolean isDynamicModel)
    {
        int plane = (int) ((hash >> TileObject.HASH_PLANE_SHIFT) & 3);
        int flags = (plane << BIT_PLANE) |
                (x << BIT_XPOS) |
                (y << BIT_YPOS) |
                (isDynamicModel ? (1 << BIT_ISDYNAMICMODEL) : 0);
        return flags;
    }
}
