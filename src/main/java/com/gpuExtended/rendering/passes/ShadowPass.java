package com.gpuExtended.rendering.passes;

import com.google.inject.Singleton;
import com.gpuExtended.opengl.GLBuffer;
import com.gpuExtended.rendering.FrameBuffer;
import com.gpuExtended.rendering.Texture2D;
import com.gpuExtended.rendering.Vector3;
import com.gpuExtended.shader.Uniforms;
import com.gpuExtended.util.GpuIntBuffer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.rlawt.AWTContext;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import javax.annotation.Nonnull;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

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
import static org.lwjgl.opengl.GL20C.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20C.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL31C.glUniformBlockBinding;
import static org.lwjgl.opengl.GL41C.glClearDepthf;
import static org.lwjgl.opengl.GL43C.*;

@Slf4j
@Singleton
public class ShadowPass {
    private FrameBuffer frameBuffer;
    private int vaoId;
    private GLBuffer computeModelBuffer = new GLBuffer("shadow_compute_model_buffer");
    private GpuIntBuffer modelBuffer;
    private GLBuffer vertexInBuffer = new GLBuffer("shadow_vertex_in_buffer");
    private GLBuffer vertexOutBuffer = new GLBuffer("shadow_vertex_out_buffer");
    private GLBuffer vertexTempBuffer = new GLBuffer("shadow_temp_vertex_buffer");
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

        vaoId = GL30.glGenVertexArrays();
        InitGLBuffer(computeModelBuffer);
        InitGLBuffer(vertexInBuffer);
        InitGLBuffer(vertexOutBuffer);
        InitGLBuffer(vertexTempBuffer);

        log.info("Shadow Pass Handler Initialized");
    }

    public void UpdateSceneVertexBuffer(Scene scene, int computeShaderId) {
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

        IntBuffer cpuBuffer = modelBuffer.getBuffer();
        cpuBuffer.flip();
        updateBuffer(computeModelBuffer, GL_SHADER_STORAGE_BUFFER, cpuBuffer, GL_DYNAMIC_DRAW, 0);
        DispatchSceneCompute(computeShaderId, numModels, computeModelBuffer);
    }

    public void Render(int shaderProgram, Uniforms uniforms) {
        glViewport(0, 0, frameBuffer.getTexture().getWidth(), frameBuffer.getTexture().getHeight());
        frameBuffer.bind();

        glClearDepthf(1);
        glClear(GL_DEPTH_BUFFER_BIT);
        glDepthFunc(GL_LEQUAL);

        glUseProgram(shaderProgram);
        Uniforms.ShaderVariables uni = uniforms.GetUniforms(shaderProgram);

        glUniformBlockBinding(shaderProgram, uni.CameraBlock, CAMERA_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, uni.PlayerBlock,  PLAYER_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, uni.EnvironmentBlock, ENVIRONMENT_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, uni.TileMarkerBlock, TILEMARKER_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, uni.SystemInfoBlock, SYSTEMINFO_BUFFER_BINDING_ID);
        glUniformBlockBinding(shaderProgram, uni.ConfigBlock, CONFIG_BUFFER_BINDING_ID);

        glEnable(GL_CULL_FACE);
        glEnable(GL_DEPTH_TEST);

        int lastVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL30.glBindVertexArray(vaoId);
        GL30.glBindBuffer(GL_ARRAY_BUFFER, vertexOutBuffer.glBufferId);

        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        glDrawArrays(GL_TRIANGLES, 0, bufferOffset);
        GL30.glBindVertexArray(lastVertexArray);

        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);

        frameBuffer.unbind();
        glUseProgram(0);
    }

    private void DispatchSceneCompute(int computeShaderId, int models, GLBuffer modelBuffer) {
        // We have to run a compute shader to sort the scene's verts because of weirdness with runescape.
        // It doesn't just handle sorting, it also handles offsetting the model, and rotating it in the world.
        int totalVertices = bufferOffset;
        int vertexSizeBytes = 16;
        int totalBytes = totalVertices * vertexSizeBytes;

        // Allocate output and temp storage
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, vertexInBuffer.glBufferId);
        glBufferData(GL_SHADER_STORAGE_BUFFER, totalBytes, GL_DYNAMIC_DRAW);

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, vertexOutBuffer.glBufferId);
        glBufferData(GL_SHADER_STORAGE_BUFFER, totalBytes, GL_DYNAMIC_DRAW);

        glBindBuffer(GL_SHADER_STORAGE_BUFFER, vertexTempBuffer.glBufferId);
        glBufferData(GL_SHADER_STORAGE_BUFFER, totalBytes, GL_DYNAMIC_DRAW);

        GL20C.glUseProgram(computeShaderId);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, MODEL_BUFFER_IN_BINDING_ID, modelBuffer.glBufferId); // modelbuffer_in

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, VERTEX_BUFFER_OUT_BINDING_ID, vertexOutBuffer.glBufferId); // vertex out
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, VERTEX_BUFFER_IN_BINDING_ID, vertexInBuffer.glBufferId); // vertexbuffer_in
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TEMP_VERTEX_BUFFER_IN_BINDING_ID, vertexTempBuffer.glBufferId); // tempvertexbuffer_in

        int workgroupSize = 6; // match your compute shader
        int groupCount = (models + workgroupSize - 1) / workgroupSize;
        glDispatchCompute(groupCount, 1, 1);

        glUseProgram(0);
    }

    public FrameBuffer GetFramebuffer() {
        return frameBuffer;
    }

    private void InitGLBuffer(GLBuffer glBuffer)
    {
        glBuffer.glBufferId = glGenBuffers();
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

    private void updateBuffer(@Nonnull GLBuffer glBuffer, int target, @Nonnull IntBuffer data, int usage, long clFlags)
    {
        int size = data.remaining() << 2;
        updateBuffer(glBuffer, target, size, usage, clFlags);
        glBufferSubData(target, 0, data);
    }

    private void updateBuffer(@Nonnull GLBuffer glBuffer, int target, @Nonnull FloatBuffer data, int usage, long clFlags)
    {
        int size = data.remaining() << 2;
        updateBuffer(glBuffer, target, size, usage, clFlags);
        glBufferSubData(target, 0, data);
    }

    private void updateBuffer(@Nonnull GLBuffer glBuffer, int target, int size, int usage, long clFlags)
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

    private void Dispose() {
        if (frameBuffer != null) {
            frameBuffer.cleanup();
            frameBuffer = null;
        }
        if (vaoId != 0) {
            GL30.glDeleteVertexArrays(vaoId);
            vaoId = 0;
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
