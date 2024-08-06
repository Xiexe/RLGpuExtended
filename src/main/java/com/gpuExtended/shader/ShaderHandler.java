package com.gpuExtended.shader;

import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.opengl.OpenCLManager;
import com.gpuExtended.shader.template.Template;
import com.gpuExtended.util.Props;
import com.gpuExtended.util.ResourcePath;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.OSType;

import javax.inject.Inject;
import java.io.IOException;

import static com.gpuExtended.util.ResourcePath.path;
import static org.lwjgl.opengl.GL11C.glFinish;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL20C.glDeleteProgram;
import static org.lwjgl.opengl.GL32C.GL_GEOMETRY_SHADER;
import static org.lwjgl.opengl.GL43C.GL_COMPUTE_SHADER;

@Slf4j
public class ShaderHandler {
    public ResourcePath SHADER_PATH = Props
            .getPathOrDefault("shader-path", () -> path(GpuExtendedPlugin.class))
            .chroot();

    public String LINUX_VERSION_HEADER =
            "#version 430\n" +
                    "#extension GL_ARB_compute_shader : require\n" +
                    "#extension GL_ARB_shader_storage_buffer_object : require\n" +
                    "#extension GL_ARB_explicit_attrib_location : require\n";
    public String WINDOWS_VERSION_HEADER = "#version 430\n";

    public Shader PROGRAM = new Shader()
            .add(GL_VERTEX_SHADER, "vert.glsl")
            .add(GL_GEOMETRY_SHADER, "geom.glsl")
            .add(GL_FRAGMENT_SHADER, "frag.glsl");

    public Shader SHADOW_PROGRAM = new Shader()
            .add(GL_VERTEX_SHADER, "vert_shadow.glsl")
            .add(GL_GEOMETRY_SHADER, "geom_depth.glsl")
            .add(GL_FRAGMENT_SHADER, "frag_depth.glsl");

    public Shader DEPTH_PROGRAM = new Shader()
            .add(GL_VERTEX_SHADER, "vert_depth.glsl")
            .add(GL_GEOMETRY_SHADER, "geom_depth.glsl")
            .add(GL_FRAGMENT_SHADER, "frag_depth.glsl");

    public Shader ORDERED_COMPUTE_PROGRAM = new Shader()
            .add(GL_COMPUTE_SHADER, "comp.glsl");

    public Shader UNORDERED_COMPUTE_PROGRAM = new Shader()
            .add(GL_COMPUTE_SHADER, "comp_unordered.glsl");

    public Shader LIGHT_BINNING_COMPUTE_PROGRAM = new Shader()
            .add(GL_COMPUTE_SHADER, "comp_light_binning.glsl");

    public Shader UI_PROGRAM = new Shader()
            .add(GL_VERTEX_SHADER, "vertui.glsl")
            .add(GL_FRAGMENT_SHADER, "fragui.glsl");

    public Shader BLOOM_DOWNSAMPLE_PROGRAM = new Shader()
            .add(GL_VERTEX_SHADER, "vert_postProcess.glsl")
            .add(GL_FRAGMENT_SHADER, "bloom_downsample.glsl");

    public Shader BLOOM_UPSAMPLE_PROGRAM = new Shader()
            .add(GL_VERTEX_SHADER, "vert_postProcess.glsl")
            .add(GL_FRAGMENT_SHADER, "bloom_upsample.glsl");

    public Shader BLOOM_PREFILTER_PROGRAM = new Shader()
            .add(GL_VERTEX_SHADER, "vert_postProcess.glsl")
            .add(GL_FRAGMENT_SHADER, "bloom_prefilter.glsl");

    public int glProgram;
    public int glShadowProgram;
    public int glDepthProgram;
    public int glComputeProgram;
    public int glSmallComputeProgram;
    public int glUnorderedComputeProgram;
    public int glLightBinningComputeProgram;
    public int glUiProgram;

    public int glBloomUpsampleProgram;
    public int glBloomDownsampleProgram;
    public int glBloomPrefilterProgram;

    @Inject
    private GpuExtendedPlugin plugin;
    @Inject
    private ClientThread clientThread;
    @Inject
    private OpenCLManager openCLManager;

    public void Initialize()
    {
        initShaders();
    }

    public void Recompile() throws ShaderException, IOException {
        // Avoid recompiling if we haven't already compiled once
        if (glProgram == 0)
            return;

        destroyShaders();
        compileShaders();
    }

    private void initShaders() {
        try
        {
            initShaderHotReloading();
            compileShaders();
        }
        catch (ShaderException ex)
        {
            throw new RuntimeException(ex);
        }
    }

    private void initShaderHotReloading() {
        SHADER_PATH.watch("\\.(glsl|cl)$", path -> {
            log.info("Recompiling shaders: {}", path);
            clientThread.invoke(() -> {
                try {
                    waitUntilIdle();
                    Recompile();
                } catch (ShaderException | IOException ex) {
                    log.error("Error while recompiling shaders:", ex);
                    plugin.stopPlugin();
                }
            });
        });
    }

    private void waitUntilIdle() {
        if (plugin.computeMode == GpuExtendedPlugin.ComputeMode.OPENCL)
            openCLManager.finish();

        glFinish();
    }

    private Template createTemplate(int threadCount, int facesPerThread)
    {
        //log.debug("Creating shader template with path: {}", SHADER_PATH.toPath().toAbsolutePath());
        String versionHeader = OSType.getOSType() == OSType.Linux ? LINUX_VERSION_HEADER : WINDOWS_VERSION_HEADER;
        Template template = new Template()
                .addInclude("VERSION_HEADER", versionHeader)
                .define("THREAD_COUNT", threadCount)
                .define("FACES_PER_THREAD", facesPerThread)
                .define("SHADOW_MAP_OVERLAY", plugin.enableShadowMapOverlay)
                .define("TILE_MASK_OVERLAY", plugin.enableTileMaskOverlay)
                .addIncludePath(SHADER_PATH);

        return template;
    }

    private void compileShaders() throws ShaderException
    {
        Template template = createTemplate(-1, -1);
        glProgram = PROGRAM.compile(template);
        glUiProgram = UI_PROGRAM.compile(template);
        glShadowProgram = SHADOW_PROGRAM.compile(template);
        glDepthProgram = DEPTH_PROGRAM.compile(template);
        glBloomDownsampleProgram = BLOOM_DOWNSAMPLE_PROGRAM.compile(template);
        glBloomUpsampleProgram = BLOOM_UPSAMPLE_PROGRAM.compile(template);
        glBloomPrefilterProgram = BLOOM_PREFILTER_PROGRAM.compile(template);

        GpuExtendedPlugin.ComputeMode computeMode = plugin.computeMode;

        switch (computeMode)
        {
            case OPENGL:
                glComputeProgram = ORDERED_COMPUTE_PROGRAM.compile(createTemplate(1024, 6));
                glSmallComputeProgram = ORDERED_COMPUTE_PROGRAM.compile(createTemplate(512, 1));
                glUnorderedComputeProgram = UNORDERED_COMPUTE_PROGRAM.compile(template);
                glLightBinningComputeProgram = LIGHT_BINNING_COMPUTE_PROGRAM.compile(template);
                break;
            case OPENCL:
                openCLManager.init(plugin.awtContext);
                break;
        }

        plugin.uniforms.ClearUniforms();
        plugin.uniforms.InitializeShaderUniformsForShader(glProgram, computeMode);
        plugin.uniforms.InitializeShaderUniformsForShader(glUiProgram, computeMode);
        plugin.uniforms.InitializeShaderUniformsForShader(glBloomUpsampleProgram, computeMode);
        plugin.uniforms.InitializeShaderUniformsForShader(glBloomDownsampleProgram, computeMode);
        plugin.uniforms.InitializeShaderUniformsForShader(glBloomPrefilterProgram, computeMode);
        plugin.uniforms.InitializeShaderUniformsForShader(glShadowProgram, computeMode);
        plugin.uniforms.InitializeShaderUniformsForShader(glDepthProgram, computeMode);
        plugin.uniforms.InitializeShaderUniformsForShader(glComputeProgram, computeMode);
        plugin.uniforms.InitializeShaderUniformsForShader(glSmallComputeProgram, computeMode);
        plugin.uniforms.InitializeShaderUniformsForShader(glUnorderedComputeProgram, computeMode);
        plugin.uniforms.InitializeShaderUniformsForShader(glLightBinningComputeProgram, computeMode);
    }

    private void destroyShaders() {
        if (glProgram != 0)
            glDeleteProgram(glProgram);
        glProgram = 0;

        if (glShadowProgram != 0)
            glDeleteProgram(glShadowProgram);
        glShadowProgram = 0;

        if (glUiProgram != 0)
            glDeleteProgram(glUiProgram);
        glUiProgram = 0;

        if (plugin.computeMode == GpuExtendedPlugin.ComputeMode.OPENGL) {
            if (glComputeProgram != 0)
                glDeleteProgram(glComputeProgram);
            glComputeProgram = 0;

            if (glSmallComputeProgram != 0)
                glDeleteProgram(glSmallComputeProgram);
            glSmallComputeProgram = 0;

            if (glUnorderedComputeProgram != 0)
                glDeleteProgram(glUnorderedComputeProgram);
            glUnorderedComputeProgram = 0;
        }
        else
        {
            openCLManager.destroyPrograms();
        }
    }

    public void cleanup() {
        glDeleteProgram(glProgram);
        glProgram = -1;

        glDeleteProgram(glComputeProgram);
        glComputeProgram = -1;

        glDeleteProgram(glSmallComputeProgram);
        glSmallComputeProgram = -1;

        glDeleteProgram(glUnorderedComputeProgram);
        glUnorderedComputeProgram = -1;

        glDeleteProgram(glLightBinningComputeProgram);
        glLightBinningComputeProgram = -1;

        glDeleteProgram(glUiProgram);
        glUiProgram = -1;

        glDeleteProgram(glShadowProgram);
        glShadowProgram = -1;

        glDeleteProgram(glDepthProgram);
        glDepthProgram = -1;

        glDeleteProgram(glBloomUpsampleProgram);
        glBloomUpsampleProgram = -1;

        glDeleteProgram(glBloomDownsampleProgram);
        glBloomDownsampleProgram = -1;

        glDeleteProgram(glBloomPrefilterProgram);
        glBloomPrefilterProgram = -1;
    }
}
