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
import java.util.ArrayList;

import static com.gpuExtended.util.ResourcePath.path;
import static org.lwjgl.opengl.GL11C.glFinish;
import static org.lwjgl.opengl.GL20C.*;
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

    public Shader mainPassShader = new Shader()
            .add(GL_VERTEX_SHADER, "vert.glsl")
            .add(GL_GEOMETRY_SHADER, "geom.glsl")
            .add(GL_FRAGMENT_SHADER, "frag.glsl");

    public Shader shadowPassShader = new Shader()
            .add(GL_VERTEX_SHADER, "vert_shadow.glsl")
            .add(GL_GEOMETRY_SHADER, "geom_depth.glsl")
            .add(GL_FRAGMENT_SHADER, "frag_depth.glsl");

    public Shader depthPassShader = new Shader()
            .add(GL_VERTEX_SHADER, "vert_depth.glsl")
            .add(GL_GEOMETRY_SHADER, "geom_depth.glsl")
            .add(GL_FRAGMENT_SHADER, "frag_depth.glsl");

    public Shader skyboxShader = new Shader()
            .add(GL_VERTEX_SHADER, "vert_skybox.glsl")
            .add(GL_FRAGMENT_SHADER, "frag_skybox.glsl");

    public Shader uiShader = new Shader()
            .add(GL_VERTEX_SHADER, "vertui.glsl")
            .add(GL_FRAGMENT_SHADER, "fragui.glsl");

    public Shader bloomDownsampleShader = new Shader()
            .add(GL_VERTEX_SHADER, "vert_postProcess.glsl")
            .add(GL_FRAGMENT_SHADER, "bloom_downsample.glsl");

    public Shader bloomUpsampleShader = new Shader()
            .add(GL_VERTEX_SHADER, "vert_postProcess.glsl")
            .add(GL_FRAGMENT_SHADER, "bloom_upsample.glsl");

    public Shader bloomPrefilterShader = new Shader()
            .add(GL_VERTEX_SHADER, "vert_postProcess.glsl")
            .add(GL_FRAGMENT_SHADER, "bloom_prefilter.glsl");

    public Shader largeOrderedComputeShader = new Shader()
            .add(GL_COMPUTE_SHADER, "comp.glsl");

    public Shader smallOrderedComputeShader = new Shader()
            .add(GL_COMPUTE_SHADER, "comp.glsl");

    public Shader unorderedComputeShader = new Shader()
            .add(GL_COMPUTE_SHADER, "comp_unordered.glsl");

    public Shader lightBinningComputeShader = new Shader()
            .add(GL_COMPUTE_SHADER, "comp_light_binning.glsl");

    private ArrayList<Shader> compiledShaders = new ArrayList<>();

//    public int mainPassShader;
//    public int shadowPassShader;
//    public int depthPassShader;
//    public int uiShader;
//    public int largeOrderedComputeShader;
//    public int smallOrderedComputeShader;
//    public int unorderedComputeShader;
//    public int lightBinningComputeShader;
//    public int bloomPrefilterShader;
//    public int bloomDownsampleShader;
//    public int bloomUpsampleShader;

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
        if (mainPassShader.id() == 0)
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
        mainPassShader.compile(template, compiledShaders);
        uiShader.compile(template, compiledShaders);
        shadowPassShader.compile(template, compiledShaders);
        skyboxShader.compile(template, compiledShaders);
        depthPassShader.compile(template, compiledShaders);
        bloomDownsampleShader.compile(template, compiledShaders);
        bloomUpsampleShader.compile(template, compiledShaders);
        bloomPrefilterShader.compile(template, compiledShaders);

        GpuExtendedPlugin.ComputeMode computeMode = plugin.computeMode;
        switch (computeMode)
        {
            case OPENGL:
                largeOrderedComputeShader.compile(createTemplate(1024, 6), compiledShaders);
                smallOrderedComputeShader.compile(createTemplate(512, 1), compiledShaders);
                unorderedComputeShader.compile(template, compiledShaders);
                lightBinningComputeShader.compile(template, compiledShaders);
                break;
            case OPENCL:
                openCLManager.init(plugin.awtContext);
                break;
        }

        plugin.uniforms.ClearUniforms();
        for(Shader shader : compiledShaders)
        {
            plugin.uniforms.InitializeShaderUniformsForShader(shader.id(), computeMode);
        }
    }

    private void destroyShaders() {
        for(Shader shader : compiledShaders)
        {
            shader.destroy();
        }

        if (plugin.computeMode == GpuExtendedPlugin.ComputeMode.OPENCL)
        {
            openCLManager.destroyPrograms();
        }

        compiledShaders.clear();
    }

    public void cleanup() {
        destroyShaders();
    }
}
