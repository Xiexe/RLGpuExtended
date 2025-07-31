package com.gpuExtended.rendering.passes;

import com.google.inject.Singleton;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.opengl.GLBuffer;
import com.gpuExtended.rendering.FrameBuffer;
import com.gpuExtended.rendering.Texture2D;
import com.gpuExtended.shader.Uniforms;
import com.gpuExtended.util.GpuFloatBuffer;
import com.gpuExtended.util.GpuIntBuffer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.rlawt.AWTContext;
import org.lwjgl.opencl.CL12;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gpuExtended.GpuExtendedPlugin.nextPowerOfTwo;
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
import static org.lwjgl.opengl.GL43C.*;

@Slf4j
@Singleton
public class ShadowPass {
    @Inject
    public GpuExtendedPlugin plugin;

    private FrameBuffer frameBuffer;
    private int vertexArrayObject;

    private GpuIntBuffer modelBuffer;
    private GpuIntBuffer vertexBuffer;
    private GpuFloatBuffer uvBuffer;
    private GpuFloatBuffer normalBuffer;
    private GpuIntBuffer flagsBuffers;

    private GLBuffer modelInfoInBuffer = new GLBuffer("model_info_in_buffer");
    private GLBuffer vertexOutBuffer = new GLBuffer("vertex_out_buffer");
    private GLBuffer vertexInBuffer = new GLBuffer("vertex_in_buffer");


    private int numModels = 0;
    private int bufferOffset = 0;

    public void Initialize(int resolution, AWTContext awtContext) {
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
        InitGLBuffer(vertexOutBuffer);
        InitGLBuffer(vertexInBuffer);
        InitGLBuffer(modelInfoInBuffer);
        InitVAO();

        modelBuffer = new GpuIntBuffer();

        log.info("Shadow Pass Handler Initialized");
    }

    public void OnPreSceneUpdated(Scene scene) {

    }

    public void OnSceneUpdated(Scene scene) {
        numModels = 0;
        bufferOffset = 0;
        scene.buildRoofs();

        Tile[][][] tiles = scene.getExtendedTiles();
        for (int z = 0; z < Constants.MAX_Z; z++) {
            for (int x = 0; x < Constants.EXTENDED_SCENE_SIZE; x++) {
                for (int y = 0; y < Constants.EXTENDED_SCENE_SIZE; y++) {
                    Tile tile = tiles[z][x][y];
                    if (tile == null) {
                        continue;
                    }

                    SceneTilePaint sceneTilePaint = tile.getSceneTilePaint();
                    if (sceneTilePaint != null)
                    {}

                    SceneTileModel sceneTileModel = tile.getSceneTileModel();
                    if (sceneTileModel != null)
                    {}

                    WallObject wallObject = tile.getWallObject();
                    if (wallObject != null)
                    {
                        Renderable r1 = wallObject.getRenderable1();
                        Renderable r2 = wallObject.getRenderable2();

                        PushRenderable(r1, wallObject.getX(), wallObject.getY(), wallObject.getZ(), wallObject.getOrientationA(), wallObject.getHash());
                        PushRenderable(r2, wallObject.getX(), wallObject.getY(), wallObject.getZ(), wallObject.getOrientationB(), wallObject.getHash());
                    }

                    GroundObject groundObject = tile.getGroundObject();
                    if (groundObject != null)
                    {
                        Renderable r = groundObject.getRenderable();
                        PushRenderable(r, groundObject.getX(), groundObject.getY(), groundObject.getZ(), 0, groundObject.getHash());
                    }

                    DecorativeObject decorativeObject = tile.getDecorativeObject();
                    if (decorativeObject != null)
                    {
                        Renderable r = decorativeObject.getRenderable();
                        PushRenderable(r, decorativeObject.getX(), decorativeObject.getY(), decorativeObject.getZ(), 0, decorativeObject.getHash());
                    }

                    GameObject[] gameObjects = tile.getGameObjects();
                    for (GameObject gameObject : gameObjects)
                    {
                        if (gameObject == null) continue;
                        Renderable r = gameObject.getRenderable();
                        PushRenderable(r, gameObject.getX(), gameObject.getY(), gameObject.getZ(), gameObject.getModelOrientation(), gameObject.getHash());
                    }
                }
            }
        }

        modelBuffer.flip();
        UpdateGLBuffer(modelInfoInBuffer, GL_SHADER_STORAGE_BUFFER, modelBuffer.getBuffer(), GL_STREAM_DRAW, 0);
        // each element is an ivec4, which is 16 bytes
        UpdateGLBuffer(vertexOutBuffer, GL_ARRAY_BUFFER, bufferOffset * 16, GL_STREAM_DRAW, CL12.CL_MEM_WRITE_ONLY);

        log.info("Dispatching Compute Shader with ID: {}", plugin.shaderHandler.largeOrderedComputeShader.id());
        DispatchSceneCompute(plugin.shaderHandler.largeOrderedComputeShader.id());
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

        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        glDrawArrays(GL_TRIANGLES, 0, bufferOffset);
        GL30.glBindVertexArray(lastVertexArray);

        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);

        frameBuffer.unbind();
        glUseProgram(0);
    }

    private void DispatchSceneCompute(int computeShaderId) {
        // We have to run a compute shader to sort the scene's verts because of weirdness with runescape.
        // It doesn't just handle sorting, it also handles offsetting the model, and rotating it in the world.
        GL20C.glUseProgram(computeShaderId);

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, MODEL_BUFFER_IN_BINDING_ID, modelInfoInBuffer.glBufferId); // modelbuffer_in
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, VERTEX_BUFFER_IN_BINDING_ID, vertexInBuffer.glBufferId); // vertexbuffer_in
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, VERTEX_BUFFER_OUT_BINDING_ID, vertexOutBuffer.glBufferId); // vertex out

        glDispatchCompute(numModels, 1, 1);
        glUseProgram(0);
    }

    public FrameBuffer GetFramebuffer() {
        return frameBuffer;
    }

    private void PushRenderable(Renderable r, int x, int y, int z, int orientation, long hash) {
        if (r == null) return;

        Model model = r.getModel();
        if (model == null) return;

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
        else
        {
            model = r.getModel();
            if (model == null)
            {
                return;
            }
            offsetModel = model;
        }

        int tileX = (x / LOCAL_TILE_SIZE) + SCENE_OFFSET;
        int tileY = (z / LOCAL_TILE_SIZE) + SCENE_OFFSET;

        final int[] indices1 = model.getFaceIndices1();
        final int[] indices2 = model.getFaceIndices2();
        final int[] indices3 = model.getFaceIndices3();

        float[] vx = model.getVerticesX();
        float[] vy = model.getVerticesY();
        float[] vz = model.getVerticesZ();

        int faceCount = Math.min(MAX_TRIANGLE, offsetModel.getFaceCount());
        int vertexCount = faceCount * 3;
        int uvOffset = offsetModel.getUvBufferOffset();
        int flags = GetModelPackedFlags(hash, model, offsetModel, orientation);
        int exFlags = GetExFlags(hash, tileX, tileY, z, false);

        GpuIntBuffer b = modelBuffer;

        b.ensureCapacity(12);
        IntBuffer buffer = b.getBuffer();
        buffer.put(offsetModel.getBufferOffset());
        buffer.put(uvOffset);
        buffer.put(faceCount);
        buffer.put(bufferOffset);
        buffer.put(FLAG_SCENE_BUFFER | flags);
        buffer.put(x).put(y).put(z);
        buffer.put(exFlags);
        buffer.put(-1);
        buffer.put(-1);
        buffer.put(-1);

        numModels++;
        bufferOffset += vertexCount;
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

        DestroyGLBuffer(modelInfoInBuffer);
        DestroyGLBuffer(vertexOutBuffer);
        DestroyGLBuffer(vertexInBuffer);

        modelBuffer = null;
    }

    private void InitVAO() {
        glBindVertexArray(vertexArrayObject);

        glEnableVertexAttribArray(VPOS_BINDING_ID);
        glBindBuffer(GL_ARRAY_BUFFER, vertexOutBuffer.glBufferId);
        glVertexAttribPointer(VPOS_BINDING_ID, 3, GL_FLOAT, false, 16, 0);

        glEnableVertexAttribArray(VHSL_BINDING_ID);
        glBindBuffer(GL_ARRAY_BUFFER, vertexOutBuffer.glBufferId);
        glVertexAttribIPointer(VHSL_BINDING_ID, 1, GL_INT, 16, 12);
    }

    private void InitGLBuffer(GLBuffer glBuffer)
    {
        glBuffer.glBufferId = glGenBuffers();
    }

    private void DestroyGLBuffer(GLBuffer glBuffer)
    {
        if (glBuffer.glBufferId != -1)
        {
            glDeleteBuffers(glBuffer.glBufferId);
            glBuffer.glBufferId = -1;
        }
        glBuffer.size = -1;

        if (glBuffer.clBuffer != -1)
        {
            CL12.clReleaseMemObject(glBuffer.clBuffer);
            glBuffer.clBuffer = -1;
        }
    }

    private void UpdateGLBuffer(@Nonnull GLBuffer glBuffer, int target, @Nonnull IntBuffer data, int usage, long clFlags)
    {
        int size = data.remaining() << 2;
        UpdateGLBuffer(glBuffer, target, size, usage, clFlags);
        glBufferSubData(target, 0, data);
    }

    private void UpdateGLBuffer(@Nonnull GLBuffer glBuffer, int target, @Nonnull FloatBuffer data, int usage, long clFlags)
    {
        int size = data.remaining() << 2;
        UpdateGLBuffer(glBuffer, target, size, usage, clFlags);
        glBufferSubData(target, 0, data);
    }

    private void UpdateGLBuffer(@Nonnull GLBuffer glBuffer, int target, int size, int usage, long clFlags)
    {
        glBindBuffer(target, glBuffer.glBufferId);
        if (size > glBuffer.size)
        {
            int newSize = Math.max(1024, nextPowerOfTwo(size));
            log.trace("Buffer resize: {} {} -> {}", glBuffer.name, glBuffer.size, newSize);

            glBuffer.size = newSize;
            glBufferData(target, newSize, usage);
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
