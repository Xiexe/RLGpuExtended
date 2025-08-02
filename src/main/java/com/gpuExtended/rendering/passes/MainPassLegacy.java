package com.gpuExtended.rendering.passes;

import com.google.inject.Singleton;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.opengl.GLBuffer;
import com.gpuExtended.rendering.FrameBuffer;
import com.gpuExtended.rendering.Texture2D;
import com.gpuExtended.rendering.Vector4;
import com.gpuExtended.shader.ShaderHandler;
import com.gpuExtended.shader.Uniforms;
import com.gpuExtended.util.contexts.ComputeBufferContext;
import com.gpuExtended.util.contexts.VertexBufferContext;
import lombok.extern.slf4j.Slf4j;
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

    private FrameBuffer frameBuffer;
    VertexBufferContext vertexBufferContext;
    ComputeBufferContext computeBufferContext;

    public void Init() {
        InitFramebuffer();
        InitBuffers();
        InitVAO();
    }

    public void Render() {

    }

    public void OnPreDrawScene() {}

    public void OnDrawScene() {}

    public void OnPostDrawScene() {
        vertexBufferContext.FlipBuffers();
        computeBufferContext.unsortedModelBuffer.flip();
        computeBufferContext.smallModelBuffer.flip();
        computeBufferContext.largeModelBuffer.flip();

        FloatBuffer vertexBuffer = vertexBufferContext.vertexBuffer.getBuffer();
        FloatBuffer uvBuffer = vertexBufferContext.uvBuffer.getBuffer();
        FloatBuffer normalBuffer = vertexBufferContext.normalBuffer.getBuffer();
        IntBuffer flagsBuffer = vertexBufferContext.flagsBuffer.getBuffer();

        IntBuffer modelBufferUnordered = computeBufferContext.unsortedModelBuffer.getBuffer();
        IntBuffer modelBufferSmall = computeBufferContext.smallModelBuffer.getBuffer();
        IntBuffer modelBufferLarge = computeBufferContext.largeModelBuffer.getBuffer();

        // model buffers
        EnsureBufferCapacity(computeBufferContext.tmpUnsortedModelBuffer, GL_ARRAY_BUFFER, modelBufferUnordered, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);
        EnsureBufferCapacity(computeBufferContext.tmpSmallModelBuffer, GL_ARRAY_BUFFER, modelBufferSmall, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);
        EnsureBufferCapacity(computeBufferContext.tmpLargeModelBuffer, GL_ARRAY_BUFFER, modelBufferLarge, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);

        // temp buffers
        EnsureBufferCapacity(computeBufferContext.dynamicVertexInBuffer, GL_ARRAY_BUFFER, vertexBuffer, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);
        EnsureBufferCapacity(computeBufferContext.dynamicUvInBuffer, GL_ARRAY_BUFFER, uvBuffer, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);
        EnsureBufferCapacity(computeBufferContext.dynamicNormalInBuffer, GL_ARRAY_BUFFER, normalBuffer, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);
        EnsureBufferCapacity(computeBufferContext.dynamicFlagsBuffer, GL_ARRAY_BUFFER, flagsBuffer, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);

        // Output buffers
        EnsureBufferCapacity(computeBufferContext.vertexOutBuffer, GL_ARRAY_BUFFER, targetBufferOffset * Vector4.BYTES, GL_STREAM_DRAW, CL12.CL_MEM_WRITE_ONLY);
        EnsureBufferCapacity(computeBufferContext.uvOutBuffer, GL_ARRAY_BUFFER, targetBufferOffset * Vector4.BYTES, GL_STREAM_DRAW, CL12.CL_MEM_WRITE_ONLY);
        EnsureBufferCapacity(computeBufferContext.normalOutBuffer, GL_ARRAY_BUFFER, targetBufferOffset * Vector4.BYTES, GL_STREAM_DRAW, CL12.CL_MEM_WRITE_ONLY);
        EnsureBufferCapacity(computeBufferContext.flagsOutBuffer, GL_ARRAY_BUFFER, targetBufferOffset * Vector4.BYTES, GL_STREAM_DRAW, CL12.CL_MEM_WRITE_ONLY);

        SortModelsWithCompute(computeBufferContext.tmpUnsortedModelBuffer, computeBufferContext.numUnsortedModels, plugin.shaderHandler.unorderedComputeShader.id());
        SortModelsWithCompute(computeBufferContext.tmpSmallModelBuffer, computeBufferContext.numSmallModels, plugin.shaderHandler.smallOrderedComputeShader.id());
        SortModelsWithCompute(computeBufferContext.tmpLargeModelBuffer, computeBufferContext.numLargeModels, plugin.shaderHandler.largeOrderedComputeShader.id());
    }

    private void SortModelsWithCompute(GLBuffer buffer, int numModels, int computeShader) {
        Uniforms uniforms = plugin.uniforms;
        ShaderHandler shaders = plugin.shaderHandler;

        glUseProgram(computeShader);

        // Bind uniforms for compute shaders | TODO:: this may not need to be done every frame.
        glUniformBlockBinding(shaders.smallOrderedComputeShader.id(), uniforms.GetUniforms(shaders.smallOrderedComputeShader.id()).BlockSmall, CAMERA_BUFFER_BINDING_ID);
        glBindBufferBase(GL_UNIFORM_BUFFER, CAMERA_BUFFER_BINDING_ID, glCameraUniformBuffer.glBufferId);

        glUniformBlockBinding(shaders.largeOrderedComputeShader.id(), uniforms.GetUniforms(shaders.largeOrderedComputeShader.id()).BlockLarge, CAMERA_BUFFER_BINDING_ID);
        glBindBufferBase(GL_UNIFORM_BUFFER, CAMERA_BUFFER_BINDING_ID, glCameraUniformBuffer.glBufferId);

        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, MODEL_BUFFER_IN_BINDING_ID, buffer.glBufferId); // modelbuffer_in

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

    }

    private void InitBuffers() {
        vertexBufferContext = new VertexBufferContext();
        computeBufferContext = new ComputeBufferContext();

        InitGLBuffer(computeBufferContext.vertexOutBuffer);
        InitGLBuffer(computeBufferContext.uvOutBuffer);
        InitGLBuffer(computeBufferContext.normalOutBuffer);
        InitGLBuffer(computeBufferContext.flagsOutBuffer);

        InitGLBuffer(computeBufferContext.staticVertexInBuffer);
        InitGLBuffer(computeBufferContext.staticUvInBuffer);
        InitGLBuffer(computeBufferContext.staticNormalInBuffer);
        InitGLBuffer(computeBufferContext.staticFlagsInBuffer);

        InitGLBuffer(computeBufferContext.dynamicVertexInBuffer);
        InitGLBuffer(computeBufferContext.dynamicUvInBuffer);
        InitGLBuffer(computeBufferContext.dynamicNormalInBuffer);
        InitGLBuffer(computeBufferContext.dynamicFlagsBuffer);

        tmpUnsortedModelBuffer = new GLBuffer("unsorted model buffer");
        tmpSmallModelBuffer = new GLBuffer("small model buffer");
        tmpLargeModelBuffer = new GLBuffer("large model buffer");
        InitGLBuffer(tmpUnsortedModelBuffer);
        InitGLBuffer(tmpSmallModelBuffer);
        InitGLBuffer(tmpLargeModelBuffer);
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
        vertexBufferContext.vertexArrayObjectId = glGenVertexArrays();

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

    private void InitGLBuffer(GLBuffer glBuffer) {
        glBuffer.glBufferId = glGenBuffers();
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
