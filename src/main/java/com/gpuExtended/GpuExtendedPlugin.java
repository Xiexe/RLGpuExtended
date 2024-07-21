package com.gpuExtended;

import com.google.common.primitives.Ints;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Provides;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.swing.SwingUtilities;

import com.gpuExtended.overlays.RegionOverlay;
import com.gpuExtended.overlays.SceneTileMaskOverlay;
import com.gpuExtended.regions.Area;
import com.gpuExtended.rendering.Vector4;
import com.gpuExtended.scene.Light;
import com.gpuExtended.scene.TileMarkers.TileMarkerManager;
import com.gpuExtended.util.deserializers.AreaDeserializer;
import com.gpuExtended.util.deserializers.ColorDeserializer;
import com.gpuExtended.util.deserializers.LightDeserializer;
import com.gpuExtended.util.deserializers.VectorDeserializer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.Constants;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;

import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.OSType;
import net.runelite.rlawt.AWTContext;
import org.lwjgl.BufferUtils;
import org.lwjgl.opencl.CL10;
import org.lwjgl.opencl.CL10GL;
import org.lwjgl.opencl.CL12;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.Callback;
import org.lwjgl.system.Configuration;

import com.gpuExtended.overlays.ShadowMapOverlay;
import com.gpuExtended.rendering.FrameBuffer;
import com.gpuExtended.rendering.Texture2D;
import com.gpuExtended.scene.Environment;
import com.gpuExtended.config.AntiAliasingMode;
import com.gpuExtended.config.UIScalingMode;
import com.gpuExtended.shader.template.Template;
import com.gpuExtended.opengl.GLBuffer;
import com.gpuExtended.opengl.OpenCLManager;
import com.gpuExtended.scene.EnvironmentManager;
import com.gpuExtended.shader.Shader;
import com.gpuExtended.shader.ShaderException;
import com.gpuExtended.shader.Uniforms;
import com.gpuExtended.util.*;

import static com.gpuExtended.util.ConstantVariables.*;
import static com.gpuExtended.util.ResourcePath.path;
import static com.gpuExtended.util.Utils.InverseLerp;
import static net.runelite.api.Constants.*;
import static net.runelite.api.Perspective.LOCAL_TILE_SIZE;
import static org.lwjgl.opencl.CL10.CL_MEM_READ_ONLY;
import static org.lwjgl.opengl.GL43C.*;

@Slf4j
@PluginDescriptor(
	name = "GPU Extended"
)
public class GpuExtendedPlugin extends Plugin implements DrawCallbacks
{
	public static GpuExtendedPlugin Instance;
	// This is the maximum number of triangles the compute shaders support
	public static final int MAX_TRIANGLE = 6144;
	public static final int SMALL_TRIANGLE_COUNT = 512;
	private static final int FLAG_SCENE_BUFFER = Integer.MIN_VALUE;
	private static final int DEFAULT_DISTANCE = 25;
	static final int MAX_DISTANCE = 184;
	static final int MAX_FOG_DEPTH = 100;
	public static final int SCENE_OFFSET = (EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2; // offset for sxy -> msxy
	private static final int GROUND_MIN_Y = 350; // how far below the ground models extend

	// overlap is ok here because this is vtx attrib, and the other is uniform buffers
	private int VPOS_BINDING_ID = 0;
	private int VHSL_BINDING_ID = 1;
	private int VUV_BINDING_ID = 2;
	private int VNORM_BINDING_ID = 3;
	private int VFLAGS_BINDING_ID = 4;

	private int CAMERA_BUFFER_BINDING_ID = 0;
	private int PLAYER_BUFFER_BINDING_ID = 1;
	private int ENVIRONMENT_BUFFER_BINDING_ID = 2;
	private int TILEMARKER_BUFFER_BINDING_ID = 3;
	private int SYSTEMINFO_BUFFER_BINDING_ID = 4;
	private int CONFIG_BUFFER_BINDING_ID = 5;

	private int MODEL_BUFFER_IN_BINDING_ID = 1;
	private int VERTEX_BUFFER_OUT_BINDING_ID = 2;
	private int VERTEX_BUFFER_IN_BINDING_ID = 3;
	private int TEMP_VERTEX_BUFFER_IN_BINDING_ID = 4;
	private int TEXTURE_BUFFER_OUT_BINDING_ID = 5;
	private int TEXTURE_BUFFER_IN_BINDING_ID = 6;
	private int TEMP_TEXTURE_BUFFER_IN_BINDING_ID = 7;
	private int NORMAL_BUFFER_OUT_BINDING_ID = 8;
	private int NORMAL_BUFFER_IN_BINDING_ID = 9;
	private int TEMP_NORMAL_BUFFER_IN_BINDING_ID = 10;

	private int FLAGS_BUFFER_OUT_BINDING_ID = 11;
	private int FLAGS_BUFFER_IN_BINDING_ID = 12;
	private int TEMP_FLAGS_BUFFER_IN_BINDING_ID = 13;


	@Inject
	private Client client;

	@Inject
	private ClientUI clientUI;

	@Inject
	private OpenCLManager openCLManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	@Inject
	private GpuExtendedConfig config;

	@Getter
	private Gson gson;

	@Inject
	private TextureManager textureManager;

	@Inject
	private SceneUploader sceneUploader;

	@Inject
	private DrawManager drawManager;

	@Inject
	private PluginManager pluginManager;

	private Uniforms uniforms;

	@Inject
	public EnvironmentManager environmentManager;

	public enum ComputeMode
	{
		NONE,
		OPENGL,
		OPENCL
	}

	private ComputeMode computeMode = ComputeMode.OPENGL;

	private Canvas canvas;
	private AWTContext awtContext;
	private Callback debugCallback;

	private GLCapabilities glCapabilities;

	static final String LINUX_VERSION_HEADER =
		"#version 420\n" +
			"#extension GL_ARB_compute_shader : require\n" +
			"#extension GL_ARB_shader_storage_buffer_object : require\n" +
			"#extension GL_ARB_explicit_attrib_location : require\n";
	static final String WINDOWS_VERSION_HEADER = "#version 430\n";

	static final Shader PROGRAM = new Shader()
		.add(GL_VERTEX_SHADER, "vert.glsl")
		.add(GL_GEOMETRY_SHADER, "geom.glsl")
		.add(GL_FRAGMENT_SHADER, "frag.glsl");

	static final Shader SHADOW_PROGRAM = new Shader()
		.add(GL_VERTEX_SHADER, "vert_shadow.glsl")
		.add(GL_FRAGMENT_SHADER, "frag_depth.glsl");

	static final Shader DETPH_PROGRAM = new Shader()
			.add(GL_VERTEX_SHADER, "vert_depth.glsl")
			.add(GL_FRAGMENT_SHADER, "frag_depth.glsl");

	static final Shader COMPUTE_PROGRAM = new Shader()
		.add(GL_COMPUTE_SHADER, "comp.glsl");

	static final Shader SMALL_COMPUTE_PROGRAM = new Shader()
		.add(GL_COMPUTE_SHADER, "comp.glsl");

	static final Shader UNORDERED_COMPUTE_PROGRAM = new Shader()
		.add(GL_COMPUTE_SHADER, "comp_unordered.glsl");

	static final Shader UI_PROGRAM = new Shader()
		.add(GL_VERTEX_SHADER, "vertui.glsl")
		.add(GL_FRAGMENT_SHADER, "fragui.glsl");

	private static final ResourcePath SHADER_PATH = Props
			.getPathOrDefault("shader-path", () -> path(GpuExtendedPlugin.class))
			.chroot();

	public boolean enableShadowMapOverlay = false;
	public boolean enableTileMaskOverlay = false;
	public boolean showRegionOverlay = false;
	public boolean showPerformanceOverlay = false;
	private int glProgram;
	private int glShadowProgram;
	private int glDepthProgram;
	private int glComputeProgram;
	private int glSmallComputeProgram;
	private int glUnorderedComputeProgram;
	public int glUiProgram;

	private int mainDrawVertexArrayObject;
	private int mainDrawTempVertexArrayObject;

	private int interfaceTexture;
	private int interfacePbo;

	private FrameBuffer colorFramebuffer;
	private FrameBuffer shadowMapFramebuffer;
	private FrameBuffer depthMapFramebuffer;

	private int vaoUiHandle;
	private int vboUiHandle;

	private int fboSceneHandle;
	private int rboSceneHandle;

	//private FrameBufferObject shadowMapFbo;

	private final GLBuffer sceneVertexBuffer = new GLBuffer("scene vertex buffer");
	private final GLBuffer sceneUvBuffer = new GLBuffer("scene tex buffer");
	private final GLBuffer sceneNormalBuffer = new GLBuffer("scene normal buffer");
	private final GLBuffer sceneFlagsBuffer = new GLBuffer("scene flags buffer");

	private final GLBuffer tmpVertexBuffer = new GLBuffer("tmp vertex buffer");
	private final GLBuffer tmpUvBuffer = new GLBuffer("tmp tex buffer");
	private final GLBuffer tmpNormalBuffer = new GLBuffer("tmp normal buffer");
	private final GLBuffer tmpFlagsBuffer = new GLBuffer("tmp flags buffer");

	private final GLBuffer renderVertexBuffer = new GLBuffer("out vertex buffer");
	private final GLBuffer renderUvBuffer = new GLBuffer("out tex buffer");
	private final GLBuffer renderNormalBuffer = new GLBuffer("out normal buffer");
	private final GLBuffer renderFlagsBuffer = new GLBuffer("out flags buffer");


	// Used for model sorting.
	private final GLBuffer tmpModelBufferLarge = new GLBuffer("model buffer large");
	private final GLBuffer tmpModelBufferSmall = new GLBuffer("model buffer small");
	private final GLBuffer tmpModelBufferUnordered = new GLBuffer("model buffer unordered");

	private int textureArrayId;
	private int tileHeightTex;

	private final GLBuffer glCameraUniformBuffer = new GLBuffer("camera uniform buffer");
	private final GLBuffer glPlayerUniformBuffer = new GLBuffer("player uniform buffer");
	private final GLBuffer glEnvironmentUniformBuffer = new GLBuffer("environment uniform buffer");
	private final GLBuffer glTileMarkerUniformBuffer = new GLBuffer("tile marker uniform buffer");
	private final GLBuffer glSystemInfoUniformBuffer = new GLBuffer("system info uniform buffer");
	private final GLBuffer glConfigUniformBuffer = new GLBuffer("config uniform buffer");

	private ByteBuffer bBufferCameraBlock;
	private ByteBuffer bBufferPlayerBlock;
	private ByteBuffer bBufferEnvironmentBlock;
	private ByteBuffer bBufferTileMarkerBlock;
	private ByteBuffer bBufferSystemInfoBlock;
	private ByteBuffer bBufferConfigBlock;

	public GpuIntBuffer vertexBuffer;
	public GpuIntBuffer flagsBuffer;
	public GpuFloatBuffer uvBuffer;
	public GpuFloatBuffer normalBuffer;

	private GpuIntBuffer modelBufferUnordered;
	private GpuIntBuffer modelBufferSmall;
	private GpuIntBuffer modelBuffer;

	private int unorderedModels;

	/**
	 * number of models in small buffer
	 */
	private int smallModels;

	/**
	 * number of models in large buffer
	 */
	private int largeModels;

	/**
	 * offset in the target buffer for model
	 */
	private int targetBufferOffset;

	/**
	 * offset into the temporary scene vertex buffer
	 */
	private int tempOffset;

	/**
	 * offset into the temporary scene uv buffer
	 */
	private int tempUvOffset;

	private int lastCanvasWidth;
	private int lastCanvasHeight;
	private int lastStretchedCanvasWidth;
	private int lastStretchedCanvasHeight;
	private AntiAliasingMode lastAntiAliasingMode;
	private int lastAnisotropicFilteringLevel = -1;

	private double cameraX, cameraY, cameraZ;
	private double cameraYaw, cameraPitch;

	private int viewportOffsetX;
	private int viewportOffsetY;


	private boolean lwjglInitted = false;

	private int sceneId;
	private int nextSceneId;
	private GpuIntBuffer nextSceneVertexBuffer;
	private GpuFloatBuffer nextSceneTexBuffer;
	private GpuFloatBuffer nextSceneNormalBuffer;
	private GpuIntBuffer nextSceneFlagsBuffer;

	private long Time;
	private long DeltaTime;
	private long StartTime;
	private float currentTrueTileAlpha = 1;
	private int currentPlane = 0;
	private int[] lastPlayerPosition = new int[2];

	private int[] currentViewport = new int[4];
	HashMap<Integer, Boolean> modelRoofCache = new HashMap<>();

	@Inject
	private ShadowMapOverlay shadowMapOverlay;

	@Inject
	private SceneTileMaskOverlay sceneTileMaskOverlay;

	@Inject
	private TileMarkerManager tileMarkerManager;

	@Inject
	private RegionOverlay regionOverlay;

//	Stopwatch dynamicModelUpload = Stopwatch.createUnstarted();
//	Stopwatch staticModelUpload = Stopwatch.createUnstarted();
//	Stopwatch uniformsStopwatch = Stopwatch.createUnstarted();

	@Override
	protected void startUp()
	{
		clientThread.invoke(() ->
		{
			try
			{
				StartTime = System.currentTimeMillis();

				if(Instance == null)
				{
					Instance = this;
				}

				client.setDrawCallbacks(this);
				client.setGpuFlags(DrawCallbacks.GPU | DrawCallbacks.HILLSKEW | DrawCallbacks.NORMALS);
				client.setExpandedMapLoading(config.expandedMapLoadingChunks());

				if(client.getGameState() == GameState.LOGGED_IN) {
					client.setGameState(GameState.LOADING);
				}

				setupCustomGsonSerializers();

				fboSceneHandle = rboSceneHandle = -1; // AA FBO
				targetBufferOffset = 0;
				unorderedModels = smallModels = largeModels = 0;

				AWTContext.loadNatives();

				canvas = client.getCanvas();

				synchronized (canvas.getTreeLock())
				{
					if (!canvas.isValid())
					{
						return false;
					}

					awtContext = new AWTContext(canvas);
					awtContext.configurePixelFormat(0, 0, 0);
				}

				awtContext.createGLContext();

				canvas.setIgnoreRepaint(true);

				computeMode = OSType.getOSType() == OSType.MacOS ? ComputeMode.OPENCL : ComputeMode.OPENGL;

				// lwjgl defaults to lwjgl- + user.name, but this breaks if the username would cause an invalid path
				// to be created.
				Configuration.SHARED_LIBRARY_EXTRACT_DIRECTORY.set("lwjgl-rl");

				glCapabilities = GL.createCapabilities();

				log.info("Using device: {}", glGetString(GL_RENDERER));
				log.info("Using driver: {}", glGetString(GL_VERSION));

				if (!glCapabilities.OpenGL31)
				{
					throw new RuntimeException("OpenGL 3.1 is required but not available");
				}

				if (!glCapabilities.OpenGL43 && computeMode == ComputeMode.OPENGL)
				{
					log.info("disabling compute shaders because OpenGL 4.3 is not available");
					computeMode = ComputeMode.NONE;
				}

				lwjglInitted = true;

				checkGLErrors();
				if (log.isDebugEnabled() && glCapabilities.glDebugMessageControl != 0)
				{
					debugCallback = GLUtil.setupDebugMessageCallback();
					if (debugCallback != null)
					{
						//	GLDebugEvent[ id 0x20071
						//		type Warning: generic
						//		severity Unknown (0x826b)
						//		source GL API
						//		msg Buffer detailed info: Buffer object 11 (bound to GL_ARRAY_BUFFER_ARB, and GL_SHADER_STORAGE_BUFFER (4), usage hint is GL_STREAM_DRAW) will use VIDEO memory as the source for buffer object operations.
						glDebugMessageControl(GL_DEBUG_SOURCE_API, GL_DEBUG_TYPE_OTHER,
							GL_DONT_CARE, 0x20071, false);

						//	GLDebugMessageHandler: GLDebugEvent[ id 0x20052
						//		type Warning: implementation dependent performance
						//		severity Medium: Severe performance/deprecation/other warnings
						//		source GL API
						//		msg Pixel-path performance warning: Pixel transfer is synchronized with 3D rendering.
						glDebugMessageControl(GL_DEBUG_SOURCE_API, GL_DEBUG_TYPE_PERFORMANCE,
							GL_DONT_CARE, 0x20052, false);
					}
				}

				vertexBuffer = new GpuIntBuffer();
				uvBuffer = new GpuFloatBuffer();
				normalBuffer = new GpuFloatBuffer();
				flagsBuffer = new GpuIntBuffer();

				modelBufferUnordered = new GpuIntBuffer();
				modelBufferSmall = new GpuIntBuffer();
				modelBuffer = new GpuIntBuffer();

				setupSyncMode();

				initBuffers();
				initVao();
				initShaders();
				initInterfaceTexture();
				initShadowMapTexture();
				initDepthMapTexture();

				eventBus.register(tileMarkerManager);
				tileMarkerManager.Initialize(EXTENDED_SCENE_SIZE);
				environmentManager.Initialize();

				// force rebuild of main buffer provider to enable alpha channel
				client.resizeCanvas();

				lastCanvasWidth = lastCanvasHeight = -1;
				lastStretchedCanvasWidth = lastStretchedCanvasHeight = -1;
				lastAntiAliasingMode = null;

				textureArrayId = -1;

				if (client.getGameState() == GameState.LOGGED_IN)
				{
					Scene scene = client.getScene();
					loadScene(scene);
					swapScene(scene);
				}

				environmentManager.LoadAreas();
				checkGLErrors();
			}
			catch (Throwable e)
			{
				log.error("Error starting GPU plugin", e);

				stopPlugin();
			}
			return true;
		});
	}

	public void stopPlugin()
	{
		SwingUtilities.invokeLater(() ->
		{
			try
			{
				pluginManager.setPluginEnabled(this, false);
				pluginManager.stopPlugin(this);
			}
			catch (PluginInstantiationException ex)
			{
				log.error("Error stopping plugin:", ex);
			}
		});

		shutDown();
	}

	private void setupCustomGsonSerializers()
	{
		GsonBuilder builder = new GsonBuilder();
		builder.registerTypeAdapter(Color.class, new ColorDeserializer());
		builder.registerTypeAdapter(Vector4.class, new VectorDeserializer());
		builder.registerTypeAdapter(Light.class, new LightDeserializer());
		builder.registerTypeAdapter(Area.class, new AreaDeserializer());
		gson = builder.create();
	}

	@Override
	protected void shutDown()
	{
		clientThread.invoke(() ->
		{
			client.setGpuFlags(0);
			client.setDrawCallbacks(null);
			client.setUnlockedFps(false);
			client.setExpandedMapLoading(0);

			if (lwjglInitted)
			{
				if (textureArrayId != -1)
				{
					textureManager.freeTextureArray(textureArrayId);
					textureArrayId = -1;
				}

				if (tileHeightTex != 0)
				{
					glDeleteTextures(tileHeightTex);
					tileHeightTex = 0;
				}

				shutdownInterfaceTexture();
				shutdownProgram();
				shutdownVao();
				shutdownBuffers();
				shutdownAAFbo();

				eventBus.unregister(tileMarkerManager);

				if (colorFramebuffer != null)
				{
					colorFramebuffer.cleanup();
					colorFramebuffer = null;
				}

				if (shadowMapFramebuffer != null)
				{
					shadowMapFramebuffer.cleanup();
					shadowMapFramebuffer = null;
				}

				if (depthMapFramebuffer != null)
				{
					depthMapFramebuffer.cleanup();
					depthMapFramebuffer = null;
				}
			}

			// this must shutdown after the clgl buffers are freed
			openCLManager.cleanup();

			if (awtContext != null)
			{
				awtContext.destroy();
				awtContext = null;
			}

			if (debugCallback != null)
			{
				debugCallback.free();
				debugCallback = null;
			}

			glCapabilities = null;

			vertexBuffer = null;
			uvBuffer = null;
			normalBuffer = null;
			flagsBuffer = null;

			modelBufferSmall = null;
			modelBuffer = null;
			modelBufferUnordered = null;

			lastAnisotropicFilteringLevel = -1;

			// force main buffer provider rebuild to turn off alpha channel
			client.resizeCanvas();
		});
	}

	@Provides
	GpuExtendedConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GpuExtendedConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		if (configChanged.getGroup().equals(GpuExtendedConfig.GROUP))
		{
			if (configChanged.getKey().equals("unlockFps")
				|| configChanged.getKey().equals("vsyncMode")
				|| configChanged.getKey().equals("fpsTarget"))
			{
				log.debug("Rebuilding sync mode");
				clientThread.invokeLater(this::setupSyncMode);
			}
			else if (configChanged.getKey().equals("expandedMapLoadingChunks"))
			{
				clientThread.invokeLater(() ->
				{
					client.setExpandedMapLoading(config.expandedMapLoadingChunks());
					if (client.getGameState() == GameState.LOGGED_IN)
					{
						client.setGameState(GameState.LOADING);
					}
				});
			}

			client.getScene().setRoofRemovalMode(config.roofFading() ? 16 : 0);
		}
	}

	private void setupSyncMode()
	{
		final boolean unlockFps = config.unlockFps();
		client.setUnlockedFps(unlockFps);

		// Without unlocked fps, the client manages sync on its 20ms timer
		GpuExtendedConfig.SyncMode syncMode = unlockFps
			? this.config.syncMode()
			: GpuExtendedConfig.SyncMode.OFF;

		int swapInterval = 0;
		switch (syncMode)
		{
			case ON:
				swapInterval = 1;
				break;
			case OFF:
				swapInterval = 0;
				break;
			case ADAPTIVE:
				swapInterval = -1;
				break;
		}

		int actualSwapInterval = awtContext.setSwapInterval(swapInterval);
		if (actualSwapInterval != swapInterval)
		{
			log.info("unsupported swap interval {}, got {}", swapInterval, actualSwapInterval);
		}

		client.setUnlockedFpsTarget(actualSwapInterval == 0 ? config.fpsTarget() : 0);
		checkGLErrors();
	}

	private Template createTemplate(int threadCount, int facesPerThread)
	{
		//log.debug("Creating shader template with path: {}", SHADER_PATH.toPath().toAbsolutePath());
		String versionHeader = OSType.getOSType() == OSType.Linux ? LINUX_VERSION_HEADER : WINDOWS_VERSION_HEADER;
		Template template = new Template()
			.addInclude("VERSION_HEADER", versionHeader)
			.define("THREAD_COUNT", threadCount)
			.define("FACES_PER_THREAD", facesPerThread)
			.define("SHADOW_MAP_OVERLAY", enableShadowMapOverlay)
			.define("TILE_MASK_OVERLAY", enableTileMaskOverlay)
			.addIncludePath(SHADER_PATH);

		return template;
	}

	public void initShaders() {
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

	public void initShaderHotReloading() {
		SHADER_PATH.watch("\\.(glsl|cl)$", path -> {
			log.info("Recompiling shaders: {}", path);
			clientThread.invoke(() -> {
				try {
					waitUntilIdle();
					recompileShaders();
				} catch (ShaderException | IOException ex) {
					log.error("Error while recompiling shaders:", ex);
					stopPlugin();
				}
			});
		});
	}

	public void waitUntilIdle() {
		if (computeMode == ComputeMode.OPENCL)
			openCLManager.finish();
		glFinish();
	}

	public void recompileShaders() throws ShaderException, IOException {
		// Avoid recompiling if we haven't already compiled once
		if (glProgram == 0)
			return;

		destroyShaders();
		compileShaders();
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

		if (computeMode == ComputeMode.OPENGL) {
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

	private void compileShaders() throws ShaderException
	{
		Template template = createTemplate(-1, -1);
		glProgram = PROGRAM.compile(template);
		glUiProgram = UI_PROGRAM.compile(template);
		glShadowProgram = SHADOW_PROGRAM.compile(template);
		glDepthProgram = DETPH_PROGRAM.compile(template);

		if (computeMode == ComputeMode.OPENGL)
		{
			glComputeProgram = COMPUTE_PROGRAM.compile(createTemplate(1024, 6));
			glSmallComputeProgram = SMALL_COMPUTE_PROGRAM.compile(createTemplate(512, 1));
			glUnorderedComputeProgram = UNORDERED_COMPUTE_PROGRAM.compile(template);
		}
		else if (computeMode == ComputeMode.OPENCL)
		{
			openCLManager.init(awtContext);
		}

		if(uniforms == null)
		{
			uniforms = new Uniforms();
		}
		else
		{
			uniforms.ClearUniforms();
		}

		uniforms.InitializeShaderUniformsForShader(glProgram, computeMode);
		uniforms.InitializeShaderUniformsForShader(glUiProgram, computeMode);
		uniforms.InitializeShaderUniformsForShader(glShadowProgram, computeMode);
		uniforms.InitializeShaderUniformsForShader(glDepthProgram, computeMode);
		uniforms.InitializeShaderUniformsForShader(glComputeProgram, computeMode);
		uniforms.InitializeShaderUniformsForShader(glSmallComputeProgram, computeMode);
		uniforms.InitializeShaderUniformsForShader(glUnorderedComputeProgram, computeMode);
	}

	private void shutdownProgram()
	{
		FileWatcher.destroy();

		glDeleteProgram(glProgram);
		glProgram = -1;

		glDeleteProgram(glComputeProgram);
		glComputeProgram = -1;

		glDeleteProgram(glSmallComputeProgram);
		glSmallComputeProgram = -1;

		glDeleteProgram(glUnorderedComputeProgram);
		glUnorderedComputeProgram = -1;

		glDeleteProgram(glUiProgram);
		glUiProgram = -1;

		glDeleteProgram(glShadowProgram);
		glShadowProgram = -1;
	}

	private void initVao(int vaoHandle)
	{
		glBindVertexArray(vaoHandle);

		glEnableVertexAttribArray(VPOS_BINDING_ID);
		glBindBuffer(GL_ARRAY_BUFFER, renderVertexBuffer.glBufferId);
		glVertexAttribPointer(VPOS_BINDING_ID, 3, GL_FLOAT, false, 16, 0);

		glEnableVertexAttribArray(VHSL_BINDING_ID);
		glBindBuffer(GL_ARRAY_BUFFER, renderVertexBuffer.glBufferId);
		glVertexAttribIPointer(VHSL_BINDING_ID, 1, GL_INT, 16, 12);

		glEnableVertexAttribArray(VUV_BINDING_ID);
		glBindBuffer(GL_ARRAY_BUFFER, renderUvBuffer.glBufferId);
		glVertexAttribPointer(VUV_BINDING_ID, 4, GL_FLOAT, false, 0, 0);

		glEnableVertexAttribArray(VNORM_BINDING_ID);
		glBindBuffer(GL_ARRAY_BUFFER, renderNormalBuffer.glBufferId);
		glVertexAttribPointer(VNORM_BINDING_ID, 4, GL_FLOAT, false, 0, 0);

		glEnableVertexAttribArray(VFLAGS_BINDING_ID);
		glBindBuffer(GL_ARRAY_BUFFER, renderFlagsBuffer.glBufferId);
		glVertexAttribIPointer(VFLAGS_BINDING_ID, 4, GL_INT, 0, 0);
	}

	private void initVao()
	{
		// Create compute VAO
		mainDrawVertexArrayObject = glGenVertexArrays();
		initVao(mainDrawVertexArrayObject);

		// Create temp VAO
		mainDrawTempVertexArrayObject = glGenVertexArrays();
		initVao(mainDrawTempVertexArrayObject);

		// Create UI VAO
		vaoUiHandle = glGenVertexArrays();
		// Create UI buffer
		vboUiHandle = glGenBuffers();
		glBindVertexArray(vaoUiHandle);

		FloatBuffer vboUiBuf = GpuFloatBuffer.allocateDirect(5 * 4);
		vboUiBuf.put(new float[]{
			// positions     // texture coords
			1f, 1f, 0.0f, 1.0f, 0f, // top right
			1f, -1f, 0.0f, 1.0f, 1f, // bottom right
			-1f, -1f, 0.0f, 0.0f, 1f, // bottom left
			-1f, 1f, 0.0f, 0.0f, 0f  // top left
		});
		vboUiBuf.rewind();
		glBindBuffer(GL_ARRAY_BUFFER, vboUiHandle);
		glBufferData(GL_ARRAY_BUFFER, vboUiBuf, GL_STATIC_DRAW);

		// position attribute
		glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0);
		glEnableVertexAttribArray(0);

		// uv attribute
		glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
		glEnableVertexAttribArray(1);

		// ui does not need normals

		// unbind VBO
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	private void shutdownVao()
	{
		glDeleteVertexArrays(mainDrawVertexArrayObject);
		mainDrawVertexArrayObject = -1;

		glDeleteVertexArrays(mainDrawTempVertexArrayObject);
		mainDrawTempVertexArrayObject = -1;

		glDeleteBuffers(vboUiHandle);
		vboUiHandle = -1;

		glDeleteVertexArrays(vaoUiHandle);
		vaoUiHandle = -1;
	}

	private void initBuffers()
	{
		initGlBuffer(sceneVertexBuffer);
		initGlBuffer(sceneUvBuffer);
		initGlBuffer(sceneNormalBuffer);
		initGlBuffer(sceneFlagsBuffer);

		initGlBuffer(tmpVertexBuffer);
		initGlBuffer(tmpUvBuffer);
		initGlBuffer(tmpNormalBuffer);
		initGlBuffer(tmpFlagsBuffer);

		initGlBuffer(tmpModelBufferLarge);
		initGlBuffer(tmpModelBufferSmall);
		initGlBuffer(tmpModelBufferUnordered);

		initGlBuffer(renderVertexBuffer);
		initGlBuffer(renderUvBuffer);
		initGlBuffer(renderNormalBuffer);
		initGlBuffer(renderFlagsBuffer);

		initGlBuffer(glCameraUniformBuffer);
		initGlBuffer(glPlayerUniformBuffer);
		initGlBuffer(glEnvironmentUniformBuffer);
		initGlBuffer(glTileMarkerUniformBuffer);
		initGlBuffer(glSystemInfoUniformBuffer);
		initGlBuffer(glConfigUniformBuffer);

		initUniformBufferBlocks();
	}

	private void initGlBuffer(GLBuffer glBuffer)
	{
		glBuffer.glBufferId = glGenBuffers();
	}

	private void initUniformBufferBlocks()
	{
		bBufferCameraBlock = initUniformBufferBlock(glCameraUniformBuffer, 128);
		bBufferPlayerBlock = initUniformBufferBlock(glPlayerUniformBuffer, 24);
		bBufferEnvironmentBlock = initUniformBufferBlock(glEnvironmentUniformBuffer, 12976);
		bBufferTileMarkerBlock = initUniformBufferBlock(glTileMarkerUniformBuffer, 144);
		bBufferSystemInfoBlock = initUniformBufferBlock(glSystemInfoUniformBuffer, 24);
		bBufferConfigBlock = initUniformBufferBlock(glConfigUniformBuffer, 7 * Float.BYTES);

		glBindBufferBase(GL_UNIFORM_BUFFER, CAMERA_BUFFER_BINDING_ID, glCameraUniformBuffer.glBufferId);
		glBindBufferBase(GL_UNIFORM_BUFFER, PLAYER_BUFFER_BINDING_ID, glPlayerUniformBuffer.glBufferId);
		glBindBufferBase(GL_UNIFORM_BUFFER, ENVIRONMENT_BUFFER_BINDING_ID, glEnvironmentUniformBuffer.glBufferId);
		glBindBufferBase(GL_UNIFORM_BUFFER, TILEMARKER_BUFFER_BINDING_ID, glTileMarkerUniformBuffer.glBufferId);
		glBindBufferBase(GL_UNIFORM_BUFFER, SYSTEMINFO_BUFFER_BINDING_ID, glSystemInfoUniformBuffer.glBufferId);
		glBindBufferBase(GL_UNIFORM_BUFFER, CONFIG_BUFFER_BINDING_ID, glConfigUniformBuffer.glBufferId);

		glBindBuffer(GL_UNIFORM_BUFFER, 0);
	}

	private ByteBuffer initUniformBufferBlock(GLBuffer glBuffer, int blockSizeBytes)
	{
		ByteBuffer byteBuffer = BufferUtils.createByteBuffer(blockSizeBytes);
		updateBuffer(glBuffer, GL_UNIFORM_BUFFER, blockSizeBytes, GL_DYNAMIC_DRAW, CL_MEM_READ_ONLY);
		return byteBuffer;
	}

	private void shutdownBuffers()
	{
		destroyGlBuffer(sceneVertexBuffer);
		destroyGlBuffer(sceneUvBuffer);
		destroyGlBuffer(sceneNormalBuffer);
		destroyGlBuffer(sceneFlagsBuffer);

		destroyGlBuffer(tmpVertexBuffer);
		destroyGlBuffer(tmpUvBuffer);
		destroyGlBuffer(tmpNormalBuffer);
		destroyGlBuffer(tmpFlagsBuffer);

		destroyGlBuffer(tmpModelBufferLarge);
		destroyGlBuffer(tmpModelBufferSmall);
		destroyGlBuffer(tmpModelBufferUnordered);

		destroyGlBuffer(renderVertexBuffer);
		destroyGlBuffer(renderUvBuffer);
		destroyGlBuffer(renderNormalBuffer);
		destroyGlBuffer(renderFlagsBuffer);

		destroyGlBuffer(glCameraUniformBuffer);
		destroyGlBuffer(glPlayerUniformBuffer);
		destroyGlBuffer(glEnvironmentUniformBuffer);
		destroyGlBuffer(glTileMarkerUniformBuffer);
		destroyGlBuffer(glSystemInfoUniformBuffer);
		destroyGlBuffer(glConfigUniformBuffer);

		bBufferCameraBlock = null;
		bBufferPlayerBlock = null;
		bBufferEnvironmentBlock = null;
		bBufferTileMarkerBlock = null;
		bBufferSystemInfoBlock = null;
		bBufferConfigBlock = null;
	}

	private void destroyGlBuffer(GLBuffer glBuffer)
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

	private void initInterfaceTexture()
	{
		interfacePbo = glGenBuffers();

		interfaceTexture = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, interfaceTexture);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glBindTexture(GL_TEXTURE_2D, 0);
	}

	private void initShadowMapTexture()
	{
		FrameBuffer.FrameBufferSettings fboSettings = new FrameBuffer.FrameBufferSettings();
		fboSettings.width = 8192;
		fboSettings.height = 8192;
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

		shadowMapFramebuffer = new FrameBuffer(fboSettings, textureSettings);
	}

	private void initDepthMapTexture()
	{
		FrameBuffer.FrameBufferSettings fboSettings = new FrameBuffer.FrameBufferSettings();
		fboSettings.width = 64;
		fboSettings.height = 64;
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

		depthMapFramebuffer = new FrameBuffer(fboSettings, textureSettings);
	}

	private void shutdownInterfaceTexture()
	{
		glDeleteBuffers(interfacePbo);
		glDeleteTextures(interfaceTexture);
		interfaceTexture = -1;
	}

	private void initAAFbo(int width, int height, int aaSamples)
	{
		if (OSType.getOSType() != OSType.MacOS)
		{
			final GraphicsConfiguration graphicsConfiguration = clientUI.getGraphicsConfiguration();
			final AffineTransform transform = graphicsConfiguration.getDefaultTransform();

			width = getScaledValue(transform.getScaleX(), width);
			height = getScaledValue(transform.getScaleY(), height);
		}

		// Create and bind the FBO
		fboSceneHandle = glGenFramebuffers();
		glBindFramebuffer(GL_FRAMEBUFFER, fboSceneHandle);

		// Create color render buffer
		rboSceneHandle = glGenRenderbuffers();
		glBindRenderbuffer(GL_RENDERBUFFER, rboSceneHandle);
		glRenderbufferStorageMultisample(GL_RENDERBUFFER, aaSamples, GL_RGBA, width, height);
		glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, rboSceneHandle);

		int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
		if (status != GL_FRAMEBUFFER_COMPLETE)
		{
			throw new RuntimeException("FBO is incomplete. status: " + status);
		}

		// Reset
		glBindFramebuffer(GL_FRAMEBUFFER, awtContext.getFramebuffer(false));
		glBindRenderbuffer(GL_RENDERBUFFER, 0);
	}

	private void shutdownAAFbo()
	{
		if (fboSceneHandle != -1)
		{
			glDeleteFramebuffers(fboSceneHandle);
			fboSceneHandle = -1;
		}

		if (rboSceneHandle != -1)
		{
			glDeleteRenderbuffers(rboSceneHandle);
			rboSceneHandle = -1;
		}
	}

	@Override
	public void drawScene(double cameraX, double cameraY, double cameraZ, double cameraPitch, double cameraYaw, int plane)
	{
		this.cameraX = cameraX;
		this.cameraY = cameraY;
		this.cameraZ = cameraZ;
		this.cameraPitch = cameraPitch;
		this.cameraYaw = cameraYaw;
		viewportOffsetX = client.getViewportXOffset();
		viewportOffsetY = client.getViewportYOffset();

		final Scene scene = client.getScene();
		scene.setDrawDistance(getDrawDistance());
		scene.setRoofRemovalMode(config.roofFading() ? 16 : 0);

		// Only reset the target buffer offset right before drawing the scene. That way if there are frames
		// after this that don't involve a scene draw, like during LOADING/HOPPING/CONNECTION_LOST, we can
		// still redraw the previous frame's scene to emulate the client behavior of not painting over the
		// viewport buffer.
		targetBufferOffset = 0;
		checkGLErrors();
	}

	@Override
	public void postDrawScene()
	{
		// Upload buffers
		vertexBuffer.flip();
		uvBuffer.flip();
		normalBuffer.flip();
		flagsBuffer.flip();
		modelBuffer.flip();
		modelBufferSmall.flip();
		modelBufferUnordered.flip();

		IntBuffer vertexBuffer = this.vertexBuffer.getBuffer();
		FloatBuffer uvBuffer = this.uvBuffer.getBuffer();
		FloatBuffer normalBuffer = this.normalBuffer.getBuffer();
		IntBuffer flagsBuffer = this.flagsBuffer.getBuffer();
		IntBuffer modelBuffer = this.modelBuffer.getBuffer();
		IntBuffer modelBufferSmall = this.modelBufferSmall.getBuffer();
		IntBuffer modelBufferUnordered = this.modelBufferUnordered.getBuffer();

		// temp buffers
		updateBuffer(tmpVertexBuffer, GL_ARRAY_BUFFER, vertexBuffer, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);
		updateBuffer(tmpUvBuffer, GL_ARRAY_BUFFER, uvBuffer, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);
		updateBuffer(tmpNormalBuffer, GL_ARRAY_BUFFER, normalBuffer, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);
		updateBuffer(tmpFlagsBuffer, GL_ARRAY_BUFFER, flagsBuffer, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);

		// model buffers
		updateBuffer(tmpModelBufferLarge, GL_ARRAY_BUFFER, modelBuffer, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);
		updateBuffer(tmpModelBufferSmall, GL_ARRAY_BUFFER, modelBufferSmall, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);
		updateBuffer(tmpModelBufferUnordered, GL_ARRAY_BUFFER, modelBufferUnordered, GL_DYNAMIC_DRAW, CL12.CL_MEM_READ_ONLY);

		// Output buffers
		updateBuffer(renderVertexBuffer,
			GL_ARRAY_BUFFER,
			targetBufferOffset * 16, // each element is an ivec4, which is 16 bytes
			GL_STREAM_DRAW,
			CL12.CL_MEM_WRITE_ONLY);

		updateBuffer(renderUvBuffer,
			GL_ARRAY_BUFFER,
			targetBufferOffset * 16, // each element is a vec4, which is 16 bytes
			GL_STREAM_DRAW,
			CL12.CL_MEM_WRITE_ONLY);

		updateBuffer(renderNormalBuffer,
			GL_ARRAY_BUFFER,
			targetBufferOffset * 16, // each element is a vec4, which is 16 bytes
			GL_STREAM_DRAW,
			CL12.CL_MEM_WRITE_ONLY);

		updateBuffer(renderFlagsBuffer,
			GL_ARRAY_BUFFER,
			targetBufferOffset * 16, // each element is a vec4, which is 16 bytes
			GL_STREAM_DRAW,
			CL12.CL_MEM_WRITE_ONLY);
		// TODO:: make OpenCL work. This is for Mac.

		// Bind UBO to compute programs
		glUniformBlockBinding(glSmallComputeProgram, uniforms.GetUniforms(glSmallComputeProgram).BlockSmall, CAMERA_BUFFER_BINDING_ID);
		glBindBufferBase(GL_UNIFORM_BUFFER, CAMERA_BUFFER_BINDING_ID, glCameraUniformBuffer.glBufferId);

		glUniformBlockBinding(glComputeProgram, uniforms.GetUniforms(glComputeProgram).BlockLarge, CAMERA_BUFFER_BINDING_ID);
		glBindBufferBase(GL_UNIFORM_BUFFER, CAMERA_BUFFER_BINDING_ID, glCameraUniformBuffer.glBufferId);

		dispatchModelSortingComputeShader(glUnorderedComputeProgram, unorderedModels, tmpModelBufferUnordered);
		dispatchModelSortingComputeShader(glSmallComputeProgram, smallModels, tmpModelBufferSmall);
		dispatchModelSortingComputeShader(glComputeProgram, largeModels, tmpModelBufferLarge);

		checkGLErrors();
	}

	public void dispatchModelSortingComputeShader(int computeShader, int models, GLBuffer modelBuffer)
	{
		glUseProgram(computeShader);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, MODEL_BUFFER_IN_BINDING_ID, modelBuffer.glBufferId); // modelbuffer_in

		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, VERTEX_BUFFER_OUT_BINDING_ID, renderVertexBuffer.glBufferId); // vertex out
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, VERTEX_BUFFER_IN_BINDING_ID, sceneVertexBuffer.glBufferId); // vertexbuffer_in
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TEMP_VERTEX_BUFFER_IN_BINDING_ID, tmpVertexBuffer.glBufferId); // tempvertexbuffer_in

		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TEXTURE_BUFFER_OUT_BINDING_ID, renderUvBuffer.glBufferId); // uv out
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TEXTURE_BUFFER_IN_BINDING_ID, sceneUvBuffer.glBufferId); // texturebuffer_in
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TEMP_TEXTURE_BUFFER_IN_BINDING_ID, tmpUvBuffer.glBufferId); // temptexturebuffer_in

		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, NORMAL_BUFFER_OUT_BINDING_ID, renderNormalBuffer.glBufferId); // normal_out
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, NORMAL_BUFFER_IN_BINDING_ID, sceneNormalBuffer.glBufferId); // normalbuffer_in
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TEMP_NORMAL_BUFFER_IN_BINDING_ID, tmpNormalBuffer.glBufferId); // tempnormalbuffer_in

		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, FLAGS_BUFFER_OUT_BINDING_ID, renderFlagsBuffer.glBufferId); // flags out
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, FLAGS_BUFFER_IN_BINDING_ID, sceneFlagsBuffer.glBufferId); // flagsbuffer_in
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, TEMP_FLAGS_BUFFER_IN_BINDING_ID, tmpFlagsBuffer.glBufferId); // tempflagsbuffer_in

		glDispatchCompute(models, 1, 1);
	}

	public void dispatchModelSortingComputeForMacOS()
	{
//		if (computeMode == ComputeMode.OPENCL)
//		{
//			// The docs for clEnqueueAcquireGLObjects say all pending GL operations must be completed before calling
//			// clEnqueueAcquireGLObjects, and recommends calling glFinish() as the only portable way to do that.
//			// However no issues have been observed from not calling it, and so will leave disabled for now.
//			// glFinish();
//
//			openCLManager.compute
//			(
//				unorderedModels,
//				smallModels,
//				largeModels,
//				sceneVertexBuffer,
//				sceneUvBuffer,
//				sceneNormalBuffer,
//				tmpVertexBuffer,
//				tmpUvBuffer,
//				tmpModelBufferUnordered,
//				tmpModelBufferSmall,
//				tmpModelBufferLarge,
//				renderVertexBuffer,
//				renderUvBuffer,
//				renderNormalBuffer,
//				uniformBuffer
//			);
//
//			checkGLErrors();
//			return;
//		}
	}

	private void prepareInterfaceTexture(int canvasWidth, int canvasHeight)
	{
		if (canvasWidth != lastCanvasWidth || canvasHeight != lastCanvasHeight)
		{
			lastCanvasWidth = canvasWidth;
			lastCanvasHeight = canvasHeight;

			glBindBuffer(GL_PIXEL_UNPACK_BUFFER, interfacePbo);
			glBufferData(GL_PIXEL_UNPACK_BUFFER, canvasWidth * canvasHeight * 4L, GL_STREAM_DRAW);
			glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);

			glBindTexture(GL_TEXTURE_2D, interfaceTexture);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, canvasWidth, canvasHeight, 0, GL_BGRA, GL_UNSIGNED_BYTE, 0);
			glBindTexture(GL_TEXTURE_2D, 0);
		}

		final BufferProvider bufferProvider = client.getBufferProvider();
		final int[] pixels = bufferProvider.getPixels();
		final int width = bufferProvider.getWidth();
		final int height = bufferProvider.getHeight();

		glBindBuffer(GL_PIXEL_UNPACK_BUFFER, interfacePbo);
		glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY)
			.asIntBuffer()
			.put(pixels, 0, width * height);
		glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER);
		glBindTexture(GL_TEXTURE_2D, interfaceTexture);
		glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_BGRA, GL_UNSIGNED_INT_8_8_8_8_REV, 0);
		glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
		glBindTexture(GL_TEXTURE_2D, 0);
	}

	@Override
	public void drawScenePaint(Scene scene, SceneTilePaint paint, int plane, int tileX, int tileY)
	{
		if (paint.getBufferLen() > 0)
		{
			final int localX = tileX << Perspective.LOCAL_COORD_BITS;
			final int localY = 0;
			final int localZ = tileY << Perspective.LOCAL_COORD_BITS;

			GpuIntBuffer b = modelBufferUnordered;
			++unorderedModels;

			b.ensureCapacity(12);
			IntBuffer buffer = b.getBuffer();
			buffer.put(paint.getBufferOffset());
			buffer.put(paint.getUvBufferOffset());
			buffer.put(2);
			buffer.put(targetBufferOffset);
			buffer.put(FLAG_SCENE_BUFFER);
			buffer.put(localX).put(localY).put(localZ);
			buffer.put(0);
			buffer.put(0);
			buffer.put(0);
			buffer.put(0);

			targetBufferOffset += 2 * 3;
		}
	}

	@Override
	public void drawSceneTileModel(Scene scene, SceneTileModel model, int tileX, int tileY)
	{
		if (model.getBufferLen() > 0)
		{
			final int localX = tileX << Perspective.LOCAL_COORD_BITS;
			final int localY = 0;
			final int localZ = tileY << Perspective.LOCAL_COORD_BITS;

			GpuIntBuffer b = modelBufferUnordered;
			++unorderedModels;

			b.ensureCapacity(12);
			IntBuffer buffer = b.getBuffer();
			buffer.put(model.getBufferOffset());
			buffer.put(model.getUvBufferOffset());
			buffer.put(model.getBufferLen() / 3);
			buffer.put(targetBufferOffset);
			buffer.put(FLAG_SCENE_BUFFER);
			buffer.put(localX).put(localY).put(localZ);
			buffer.put(0);
			buffer.put(0);
			buffer.put(0);
			buffer.put(0);

			targetBufferOffset += model.getBufferLen();
		}
	}

	// MAIN DRAW
	@Override
	public void draw(int overlayColor)
	{
		final GameState gameState = client.getGameState();
		if (gameState == GameState.STARTING)
		{
			return;
		}

		shadowMapOverlay.setActive(config.showShadowMap());
		sceneTileMaskOverlay.setActive(config.showTileMask());
		regionOverlay.setActive(config.showRegionOverlay());

		final int canvasHeight = client.getCanvasHeight();
		final int canvasWidth = client.getCanvasWidth();

		final int viewportHeight = client.getViewportHeight();
		final int viewportWidth = client.getViewportWidth();

		prepareInterfaceTexture(canvasWidth, canvasHeight);

		// Setup anti-aliasing
		final AntiAliasingMode antiAliasingMode = config.antiAliasingMode();
		final boolean aaEnabled = antiAliasingMode != AntiAliasingMode.DISABLED;

		if (aaEnabled)
		{
			glEnable(GL_MULTISAMPLE);

			final Dimension stretchedDimensions = client.getStretchedDimensions();

			final int stretchedCanvasWidth = client.isStretchedEnabled() ? stretchedDimensions.width : canvasWidth;
			final int stretchedCanvasHeight = client.isStretchedEnabled() ? stretchedDimensions.height : canvasHeight;

			// Re-create fbo
			if (lastStretchedCanvasWidth != stretchedCanvasWidth
				|| lastStretchedCanvasHeight != stretchedCanvasHeight
				|| lastAntiAliasingMode != antiAliasingMode)
			{
				shutdownAAFbo();

				// Bind default FBO to check whether anti-aliasing is forced
				glBindFramebuffer(GL_FRAMEBUFFER, awtContext.getFramebuffer(false));
				final int forcedAASamples = glGetInteger(GL_SAMPLES);
				final int maxSamples = glGetInteger(GL_MAX_SAMPLES);
				final int samples = forcedAASamples != 0 ? forcedAASamples :
					Math.min(antiAliasingMode.getSamples(), maxSamples);

				log.debug("AA samples: {}, max samples: {}, forced samples: {}", samples, maxSamples, forcedAASamples);

				initAAFbo(stretchedCanvasWidth, stretchedCanvasHeight, samples);

				lastStretchedCanvasWidth = stretchedCanvasWidth;
				lastStretchedCanvasHeight = stretchedCanvasHeight;
			}

			glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fboSceneHandle);
		}
		else
		{
			glDisable(GL_MULTISAMPLE);
			shutdownAAFbo();
		}

		lastAntiAliasingMode = antiAliasingMode;

		Environment env = environmentManager.currentEnvironment;
		if(gameState.getState() == GameState.LOGIN_SCREEN.getState())
		{
			glClearColor(0, 0, 0, 1f);
		}
		else if(gameState.getState() == GameState.LOGGED_IN.getState())
		{
			glClearColor(env.SkyColor.getRed() / 255f, env.SkyColor.getGreen() / 255f, env.SkyColor.getBlue() / 255f, 1f);
		}
		glClear(GL_COLOR_BUFFER_BIT);

		// Draw 3d scene
		if (gameState.getState() >= GameState.LOADING.getState())
		{
			long currentTime = System.currentTimeMillis();
			DeltaTime = currentTime - (StartTime + Time);
			Time = currentTime - StartTime;
			environmentManager.Update(DeltaTime);
			updateUniformBlocks();

			int renderWidthOff = viewportOffsetX;
			int renderHeightOff = viewportOffsetY;
			int renderCanvasHeight = canvasHeight;
			int renderViewportHeight = viewportHeight;
			int renderViewportWidth = viewportWidth;

			// Setup anisotropic filtering
			final int anisotropicFilteringLevel = config.anisotropicFilteringLevel();

			if (textureArrayId != -1 && lastAnisotropicFilteringLevel != anisotropicFilteringLevel)
			{
				textureManager.setAnisotropicFilteringLevel(textureArrayId, anisotropicFilteringLevel);
				lastAnisotropicFilteringLevel = anisotropicFilteringLevel;
			}

			if (client.isStretchedEnabled())
			{
				Dimension dim = client.getStretchedDimensions();
				renderCanvasHeight = dim.height;

				double scaleFactorY = dim.getHeight() / canvasHeight;
				double scaleFactorX = dim.getWidth() / canvasWidth;

				// Pad the viewport a little because having ints for our viewport dimensions can introduce off-by-one errors.
				final int padding = 1;

				// Ceil the sizes because even if the size is 599.1 we want to treat it as size 600 (i.e. render to the x=599 pixel).
				renderViewportHeight = (int) Math.ceil(scaleFactorY * (renderViewportHeight)) + padding * 2;
				renderViewportWidth = (int) Math.ceil(scaleFactorX * (renderViewportWidth)) + padding * 2;

				// Floor the offsets because even if the offset is 4.9, we want to render to the x=4 pixel anyway.
				renderHeightOff = (int) Math.floor(scaleFactorY * (renderHeightOff)) - padding;
				renderWidthOff = (int) Math.floor(scaleFactorX * (renderWidthOff)) - padding;
			}

			if(client.getPlane() != currentPlane)
			{
				tileMarkerManager.Reset();
				tileMarkerManager.LoadTileMarkers();
				tileMarkerManager.InitializeSceneRoofMask(client.getScene());
				environmentManager.LoadSceneLights(client.getScene());
			}

			glBindVertexArray(mainDrawVertexArrayObject);

			drawShadowPass();

			// This needs to be run before drawing the depth pass or the main pass
			glDpiAwareViewport(renderWidthOff, renderCanvasHeight - renderViewportHeight - renderHeightOff, renderViewportWidth, renderViewportHeight);
			glGetIntegerv(GL_VIEWPORT, currentViewport);
			drawMainPass();

			lastPlayerPosition[0] = client.getLocalPlayer().getLocalLocation().getX();
			lastPlayerPosition[1] = client.getLocalPlayer().getLocalLocation().getY();
			currentPlane = client.getPlane();
		}

		if (aaEnabled)
		{
			int width = lastStretchedCanvasWidth;
			int height = lastStretchedCanvasHeight;

			if (OSType.getOSType() != OSType.MacOS)
			{
				final GraphicsConfiguration graphicsConfiguration = clientUI.getGraphicsConfiguration();
				final AffineTransform transform = graphicsConfiguration.getDefaultTransform();

				width = getScaledValue(transform.getScaleX(), width);
				height = getScaledValue(transform.getScaleY(), height);
			}

			glBindFramebuffer(GL_READ_FRAMEBUFFER, fboSceneHandle);
			glBindFramebuffer(GL_DRAW_FRAMEBUFFER, awtContext.getFramebuffer(false));
			glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
				GL_COLOR_BUFFER_BIT, GL_NEAREST);

			// Reset
			glBindFramebuffer(GL_READ_FRAMEBUFFER, awtContext.getFramebuffer(false));
		}

		// Clear buffers
		vertexBuffer.clear();
		uvBuffer.clear();
		normalBuffer.clear();
		flagsBuffer.clear();

		modelBuffer.clear();
		modelBufferSmall.clear();
		modelBufferUnordered.clear();

		smallModels = largeModels = unorderedModels = 0;
		tempOffset = 0;
		tempUvOffset = 0;

		// Texture on UI
		drawUi(overlayColor, canvasHeight, canvasWidth);

		try
		{
			awtContext.swapBuffers();
		}
		catch (RuntimeException ex)
		{
			// this is always fatal
			if (!canvas.isValid())
			{
				// this might be AWT shutting down on VM shutdown, ignore it
				return;
			}

			throw ex;
		}

		drawManager.processDrawComplete(this::screenshot);

		glBindFramebuffer(GL_FRAMEBUFFER, awtContext.getFramebuffer(false));

		checkGLErrors();
	}

	private void updateUniformBlocks()
	{
//		uniformsStopwatch.reset();
//		uniformsStopwatch.start();
		if(client.getGameState().getState() != GameState.LOGGED_IN.getState())
		{
			return;
		}

		// Calculate camera matrix
		float[] cameraProjectionMatrix = Mat4.scale(client.getScale(), client.getScale(), 1);
		Mat4.mul(cameraProjectionMatrix, Mat4.projection(client.getViewportWidth(), client.getViewportHeight(), 50));
		Mat4.mul(cameraProjectionMatrix, Mat4.rotateX((float) -(Math.PI - cameraPitch)));
		Mat4.mul(cameraProjectionMatrix, Mat4.rotateY((float) cameraYaw));
		Mat4.mul(cameraProjectionMatrix, Mat4.translate((float) -cameraX, (float) -cameraY, (float) -cameraZ));

		int playerX = client.getLocalPlayer().getLocalLocation().getX();
		int playerY = client.getLocalPlayer().getLocalLocation().getY();
		int playerPlane = client.getPlane();

		Environment env = environmentManager.currentEnvironment;

		// <editor-fold defaultstate="collapsed" desc="Write Tilemap Data">
		// </editor-fold>

		// <editor-fold defaultstate="collapsed" desc="Populate Camera Buffer Block">
			bBufferCameraBlock.clear();

			// Fill the cameraProjectionMatrix (16 floats, 64 bytes)
			for(int i = 0; i < cameraProjectionMatrix.length; i++) {
				bBufferCameraBlock.putFloat(cameraProjectionMatrix[i]);
			}

			// Fill cameraPosition (4 floats, 16 bytes)
			bBufferCameraBlock.putFloat((float) cameraX);
			bBufferCameraBlock.putFloat((float) cameraY);
			bBufferCameraBlock.putFloat((float) cameraZ);
			bBufferCameraBlock.putFloat(0); // pad

			// Fill cameraFocalPoint (4 floats, 16 bytes)
			bBufferCameraBlock.putFloat((float) client.getCameraFpX());
			bBufferCameraBlock.putFloat((float) client.getCameraFpY());
			bBufferCameraBlock.putFloat((float) client.getCameraFpZ());
			bBufferCameraBlock.putFloat(0); // pad

			// Fill cameraPitch (4 bytes), cameraYaw (4 bytes), zoom (4 bytes), centerX (4 bytes), centerY (4 bytes)
			// According to std140 layout rules, each of these must be 4 bytes aligned
			bBufferCameraBlock.putFloat((float) cameraPitch);
			bBufferCameraBlock.putFloat((float) cameraYaw);
			bBufferCameraBlock.putInt(client.getScale());
			bBufferCameraBlock.putInt(client.getCenterX());
			bBufferCameraBlock.putInt(client.getCenterY());

			bBufferCameraBlock.flip();

			glBindBuffer(GL_UNIFORM_BUFFER, glCameraUniformBuffer.glBufferId);
			glBufferSubData(GL_UNIFORM_BUFFER, 0, bBufferCameraBlock);
		// </editor-fold>

		// <editor-fold defaultstate="collapsed" desc="Populate Player Buffer Block">
			bBufferPlayerBlock.clear();
			bBufferPlayerBlock.putFloat((float) playerX);
			bBufferPlayerBlock.putFloat((float) playerY);
			bBufferPlayerBlock.putFloat((float) playerPlane);
			bBufferPlayerBlock.putFloat(0); // pad

			bBufferEnvironmentBlock.putInt(client.getScene().getBaseX());
			bBufferEnvironmentBlock.putInt(client.getScene().getBaseY());
			bBufferPlayerBlock.flip();

			glBindBuffer(GL_UNIFORM_BUFFER, glPlayerUniformBuffer.glBufferId);
			glBufferSubData(GL_UNIFORM_BUFFER, 0, bBufferPlayerBlock);
		// </editor-fold>

		// <editor-fold defaultstate="collapsed" desc="Populate Environment Buffer Block">
			bBufferEnvironmentBlock.clear();

			// Ambient Color
			bBufferEnvironmentBlock.putFloat(env.AmbientColor.getRed() / 255f);
			bBufferEnvironmentBlock.putFloat(env.AmbientColor.getGreen() / 255f);
			bBufferEnvironmentBlock.putFloat(env.AmbientColor.getBlue() / 255f);
			bBufferEnvironmentBlock.putFloat(0);

			// Sky Color
			bBufferEnvironmentBlock.putFloat(env.SkyColor.getRed() / 255f);
			bBufferEnvironmentBlock.putFloat(env.SkyColor.getGreen() / 255f);
			bBufferEnvironmentBlock.putFloat(env.SkyColor.getBlue() / 255f);
			bBufferEnvironmentBlock.putFloat(0);

			// Fog
			bBufferEnvironmentBlock.putInt(env.Type); // Pad
			bBufferEnvironmentBlock.putInt(env.FogDepth);
			bBufferEnvironmentBlock.putInt(0);
			bBufferEnvironmentBlock.putInt(0);


			// Pack Main Light
			// Pos
			bBufferEnvironmentBlock.putFloat(environmentManager.lightViewMatrix[2]);
			bBufferEnvironmentBlock.putFloat(-environmentManager.lightViewMatrix[6]);
			bBufferEnvironmentBlock.putFloat(environmentManager.lightViewMatrix[10]);
			bBufferEnvironmentBlock.putFloat(1); // light type / directional

			// Offset
			bBufferEnvironmentBlock.putFloat(0);
			bBufferEnvironmentBlock.putFloat(0);
			bBufferEnvironmentBlock.putFloat(0);
			bBufferEnvironmentBlock.putFloat(0); // pad

			// Color
			bBufferEnvironmentBlock.putFloat(env.LightColor.getRed() / 255f);
			bBufferEnvironmentBlock.putFloat(env.LightColor.getGreen() / 255f);
			bBufferEnvironmentBlock.putFloat(env.LightColor.getBlue() / 255f);
			bBufferEnvironmentBlock.putFloat(0); // pad

			bBufferEnvironmentBlock.putFloat(0); // light intensity
			bBufferEnvironmentBlock.putFloat(0); // light radius
			bBufferEnvironmentBlock.putInt(0); // light animation
			bBufferEnvironmentBlock.putFloat(0); // pad

			for(int i = 0; i < environmentManager.lightProjectionMatrix.length; i++)
			{
				bBufferEnvironmentBlock.putFloat(environmentManager.lightProjectionMatrix[i]);
			}

			// Pack Lights
			environmentManager.DetermineRenderedLights();
			for(int i = 0; i < MAX_LIGHTS; i++)
			{
				boolean lightExists = environmentManager.sceneLights.size() > i;
				// TODO:: check visibility of light from frustum.
				if(!lightExists)
				{
					PushEmptyLightToBuffer();
				}
				else
				{
					Light light = environmentManager.sceneLights.get(i);
					float dx = playerX - light.position.x;
					float dy = playerY - light.position.y;
					float distanceSquared = dx * dx + dy * dy;
					float renderDistance = MAX_LIGHT_RENDER_DISTANCE * LOCAL_TILE_SIZE;
					float maxDistanceSquared = renderDistance * renderDistance;
					//log.info("Light Distance: {}", distanceSquared);
					if(distanceSquared > maxDistanceSquared)
					{
						PushEmptyLightToBuffer();
						continue;
					}

					float normalizedDistance = InverseLerp(0.0f, maxDistanceSquared, distanceSquared);
					float fadeMultiplier = (float) (1.0f - Math.pow(normalizedDistance, 5.0));
					fadeMultiplier = Math.max(0.0f, Math.min(1.0f, fadeMultiplier)); // Clamp between 0 and 1

					float intensity = light.intensity * fadeMultiplier;

					if(intensity <= 0)
					{
						PushEmptyLightToBuffer();
						continue;
					}

					bBufferEnvironmentBlock.putFloat(light.position.x);
					bBufferEnvironmentBlock.putFloat(light.position.y);
					bBufferEnvironmentBlock.putFloat(light.position.z);
					bBufferEnvironmentBlock.putFloat(light.position.w);

					bBufferEnvironmentBlock.putFloat(light.offset.x);
					bBufferEnvironmentBlock.putFloat(light.offset.y);
					bBufferEnvironmentBlock.putFloat(light.offset.z);
					bBufferEnvironmentBlock.putFloat(0);

					bBufferEnvironmentBlock.putFloat(light.color.getRed() / 255f);
					bBufferEnvironmentBlock.putFloat(light.color.getGreen() / 255f);
					bBufferEnvironmentBlock.putFloat(light.color.getBlue() / 255f);
					bBufferEnvironmentBlock.putFloat(0);

					bBufferEnvironmentBlock.putFloat(intensity);
					bBufferEnvironmentBlock.putFloat(light.radius);
					bBufferEnvironmentBlock.putInt(light.animation.ordinal());
					bBufferEnvironmentBlock.putInt(light.type.ordinal());

					for(int j = 0; j < environmentManager.lightProjectionMatrix.length; j++)
					{
						// TODO:: these lights don't actually have projection matricies at the moment.
						// Pad it. Future stuff.
						bBufferEnvironmentBlock.putFloat(0);
					}
				}
			}

			bBufferEnvironmentBlock.flip();

			glBindBuffer(GL_UNIFORM_BUFFER, glEnvironmentUniformBuffer.glBufferId);
			glBufferSubData(GL_UNIFORM_BUFFER, 0, bBufferEnvironmentBlock);
		// </editor-fold>

		// <editor-fold defaultstate="collapsed" desc="Populate Tile Marker Buffer Block">
			float currentTileX = -1;
			float currentTileY = -1;
			float currentTileZ = -1;
			float targetTileX = -1;
			float targetTileY = -1;
			float targetTileZ = -1;
			float hoveredTileX = -1;
			float hoveredTileY = -1;
			float hoveredTileZ = -1;

			final WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
			if (playerPos != null)
			{
				final LocalPoint playerPosLocal = LocalPoint.fromWorld(client, playerPos);
				if (playerPosLocal != null)
				{
					currentTileX = (float)playerPosLocal.getX();
					currentTileY = (float)playerPosLocal.getY();
					currentTileZ = (float)client.getPlane();
				}
			}

			if(client.getLocalDestinationLocation() != null)
			{
				targetTileX = (float)client.getLocalDestinationLocation().getX();
				targetTileY = (float)client.getLocalDestinationLocation().getY();
				targetTileZ = (float)client.getPlane();
			}

			if(client.getSelectedSceneTile() != null)
			{
				hoveredTileX = (float)client.getSelectedSceneTile().getLocalLocation().getX();
				hoveredTileY = (float)client.getSelectedSceneTile().getLocalLocation().getY();
				hoveredTileZ = (float)client.getPlane();
			}

			if(config.trueTileFadeOut())
			{
				if( client.getLocalPlayer().getLocalLocation().getX() == lastPlayerPosition[0] &&
					client.getLocalPlayer().getLocalLocation().getY() == lastPlayerPosition[1])
				{
					currentTrueTileAlpha = Math.max(0, currentTrueTileAlpha - (float)DeltaTime / config.trueTileFadeOutTime());
				}
				else
				{
					currentTrueTileAlpha = 1;
				}
			}
			else
			{
				currentTrueTileAlpha = 1;
			}

			bBufferTileMarkerBlock.clear();
			bBufferTileMarkerBlock.putFloat(currentTileX);
			bBufferTileMarkerBlock.putFloat(currentTileY);
			bBufferTileMarkerBlock.putFloat(config.trueTileBorderWidth());
			bBufferTileMarkerBlock.putFloat(config.trueTileCornerLength());

			bBufferTileMarkerBlock.putFloat(config.highlightTrueTile() ? config.trueTileFillColor().getRed() / 255f : 0);
			bBufferTileMarkerBlock.putFloat(config.highlightTrueTile() ? config.trueTileFillColor().getGreen() / 255f : 0);
			bBufferTileMarkerBlock.putFloat(config.highlightTrueTile() ? config.trueTileFillColor().getBlue() / 255f : 0);
			bBufferTileMarkerBlock.putFloat(config.highlightTrueTile() ? (config.trueTileFillColor().getAlpha() / 255f) * currentTrueTileAlpha : 0);

			bBufferTileMarkerBlock.putFloat(config.highlightTrueTile() ? config.trueTileBorderColor().getRed() / 255f : 0);
			bBufferTileMarkerBlock.putFloat(config.highlightTrueTile() ? config.trueTileBorderColor().getGreen() / 255f : 0);
			bBufferTileMarkerBlock.putFloat(config.highlightTrueTile() ? config.trueTileBorderColor().getBlue() / 255f : 0);
			bBufferTileMarkerBlock.putFloat(config.highlightTrueTile() ? (config.trueTileBorderColor().getAlpha() / 255f) * currentTrueTileAlpha : 0);

			bBufferTileMarkerBlock.putFloat(targetTileX);
			bBufferTileMarkerBlock.putFloat(targetTileY);
			bBufferTileMarkerBlock.putFloat(config.destinationTileBorderWidth());
			bBufferTileMarkerBlock.putFloat(config.destinationTileCornerLength());

			bBufferTileMarkerBlock.putFloat(config.highlightDestinationTile() ? config.destinationTileFillColor().getRed() / 255f : 0);
			bBufferTileMarkerBlock.putFloat(config.highlightDestinationTile() ? config.destinationTileFillColor().getGreen() / 255f : 0);
			bBufferTileMarkerBlock.putFloat(config.highlightDestinationTile() ? config.destinationTileFillColor().getBlue() / 255f : 0);
			bBufferTileMarkerBlock.putFloat(config.highlightDestinationTile() ? config.destinationTileFillColor().getAlpha() / 255f : 0);

			bBufferTileMarkerBlock.putFloat(config.highlightDestinationTile() ? config.destinationTileBorderColor().getRed() / 255f : 0);
			bBufferTileMarkerBlock.putFloat(config.highlightDestinationTile() ? config.destinationTileBorderColor().getGreen() / 255f : 0);
			bBufferTileMarkerBlock.putFloat(config.highlightDestinationTile() ? config.destinationTileBorderColor().getBlue() / 255f : 0);
			bBufferTileMarkerBlock.putFloat(config.highlightDestinationTile() ? config.destinationTileBorderColor().getAlpha() / 255f : 0);

			bBufferTileMarkerBlock.putFloat(hoveredTileX);
			bBufferTileMarkerBlock.putFloat(hoveredTileY);
			bBufferTileMarkerBlock.putFloat(config.hoveredTileBorderWidth());
			bBufferTileMarkerBlock.putFloat(config.hoveredTileCornerLength());

			bBufferTileMarkerBlock.putFloat(config.highlightHoveredTile() ? config.hoveredTileFillColor().getRed() / 255f : 0);
			bBufferTileMarkerBlock.putFloat(config.highlightHoveredTile() ? config.hoveredTileFillColor().getGreen() / 255f : 0);
			bBufferTileMarkerBlock.putFloat(config.highlightHoveredTile() ? config.hoveredTileFillColor().getBlue() / 255f : 0);
			bBufferTileMarkerBlock.putFloat(config.highlightHoveredTile() ? config.hoveredTileFillColor().getAlpha() / 255f : 0);

			bBufferTileMarkerBlock.putFloat(config.highlightHoveredTile() ? config.hoveredTileBorderColor().getRed() / 255f : 0);
			bBufferTileMarkerBlock.putFloat(config.highlightHoveredTile() ? config.hoveredTileBorderColor().getGreen() / 255f : 0);
			bBufferTileMarkerBlock.putFloat(config.highlightHoveredTile() ? config.hoveredTileBorderColor().getBlue() / 255f : 0);
			bBufferTileMarkerBlock.putFloat(config.highlightHoveredTile() ? config.hoveredTileBorderColor().getAlpha() / 255f : 0);

			bBufferTileMarkerBlock.flip();

			glBindBuffer(GL_UNIFORM_BUFFER, glTileMarkerUniformBuffer.glBufferId);
			glBufferSubData(GL_UNIFORM_BUFFER, 0, bBufferTileMarkerBlock);
		// </editor-fold>

		// <editor-fold defaultstate="collapsed" desc="Populate System Info Block">
			bBufferSystemInfoBlock.clear();

			bBufferSystemInfoBlock.putInt(client.getGameState() == GameState.LOGGED_IN ? (client.getGameCycle() & 127) : 0);
			bBufferSystemInfoBlock.putInt(currentViewport[2]);
			bBufferSystemInfoBlock.putInt(currentViewport[3]);
			bBufferSystemInfoBlock.putFloat(DeltaTime);
			bBufferSystemInfoBlock.putFloat(Time);

			bBufferSystemInfoBlock.flip();

			glBindBuffer(GL_UNIFORM_BUFFER, glSystemInfoUniformBuffer.glBufferId);
			glBufferSubData(GL_UNIFORM_BUFFER, 0, bBufferSystemInfoBlock);
		// </editor-fold>

		// <editor-fold defaultstate="collapsed" desc="Populate Config Block">
			bBufferConfigBlock.clear();

			bBufferConfigBlock.putFloat((float) client.getTextureProvider().getBrightness());
			bBufferConfigBlock.putFloat(config.smoothBanding() ? 0 : 1);
			bBufferConfigBlock.putInt(config.expandedMapLoadingChunks());
			bBufferConfigBlock.putInt(getDrawDistance());
			bBufferConfigBlock.putInt(config.colorBlindMode().ordinal());
			bBufferConfigBlock.putInt(config.roofFading() ? 1 : 0);
			bBufferConfigBlock.putInt(config.roofFadingRange());

			bBufferConfigBlock.flip();

			glBindBuffer(GL_UNIFORM_BUFFER, glConfigUniformBuffer.glBufferId);
			glBufferSubData(GL_UNIFORM_BUFFER, 0, bBufferConfigBlock);
		// </editor-fold>

//		uniformsStopwatch.stop();
	}

	private void PushEmptyLightToBuffer()
	{
		// push an empty light
		bBufferEnvironmentBlock.putFloat(0);
		bBufferEnvironmentBlock.putFloat(0);
		bBufferEnvironmentBlock.putFloat(0);
		bBufferEnvironmentBlock.putFloat(0);

		bBufferEnvironmentBlock.putFloat(0);
		bBufferEnvironmentBlock.putFloat(0);
		bBufferEnvironmentBlock.putFloat(0);
		bBufferEnvironmentBlock.putFloat(0);

		bBufferEnvironmentBlock.putFloat(0);
		bBufferEnvironmentBlock.putFloat(0);
		bBufferEnvironmentBlock.putFloat(0);
		bBufferEnvironmentBlock.putFloat(0);

		bBufferEnvironmentBlock.putFloat(0);
		bBufferEnvironmentBlock.putFloat(0);
		bBufferEnvironmentBlock.putInt(0);
		bBufferEnvironmentBlock.putInt(0);

		for(int j = 0; j < environmentManager.lightProjectionMatrix.length; j++)
		{
			bBufferEnvironmentBlock.putFloat(0);
		}
	}

	private void drawMainPass()
	{
		glUseProgram(glProgram);
		Uniforms.ShaderVariables uni = uniforms.GetUniforms(glProgram);

		glActiveTexture(GL_TEXTURE2);
		glBindTexture(GL_TEXTURE_2D, shadowMapFramebuffer.getTexture().getId());
		glUniform1i(uni.ShadowMap, 2);
		glActiveTexture(GL_TEXTURE0);

		glActiveTexture(GL_TEXTURE3);
		glBindTexture(GL_TEXTURE_2D, tileMarkerManager.tileFillColorTexture.getId());
		glUniform1i(uni.TileMarkerFillColorMap, 3);
		glActiveTexture(GL_TEXTURE0);

		glActiveTexture(GL_TEXTURE4);
		glBindTexture(GL_TEXTURE_2D, tileMarkerManager.tileBorderColorTexture.getId());
		glUniform1i(uni.TileMarkerBorderColorMap, 4);
		glActiveTexture(GL_TEXTURE0);

		glActiveTexture(GL_TEXTURE5);
		glBindTexture(GL_TEXTURE_2D, tileMarkerManager.tileSettingsTexture.getId());
		glUniform1i(uni.TileMarkerSettingsMap, 5);
		glActiveTexture(GL_TEXTURE0);

		glActiveTexture(GL_TEXTURE6);
		glBindTexture(GL_TEXTURE_3D, tileMarkerManager.roofMaskTexture.getId());
		glUniform1i(uni.RoofMaskTextureMap, 6);
		glActiveTexture(GL_TEXTURE0);

		glUniformBlockBinding(glProgram, uni.CameraBlock, CAMERA_BUFFER_BINDING_ID);
		glUniformBlockBinding(glProgram, uni.PlayerBlock,  PLAYER_BUFFER_BINDING_ID);
		glUniformBlockBinding(glProgram, uni.EnvironmentBlock, ENVIRONMENT_BUFFER_BINDING_ID);
		glUniformBlockBinding(glProgram, uni.TileMarkerBlock, TILEMARKER_BUFFER_BINDING_ID);
		glUniformBlockBinding(glProgram, uni.SystemInfoBlock, SYSTEMINFO_BUFFER_BINDING_ID);
		glUniformBlockBinding(glProgram, uni.ConfigBlock, CONFIG_BUFFER_BINDING_ID);

		final TextureProvider textureProvider = client.getTextureProvider();
		if (textureArrayId == -1)
		{
			// lazy init textures as they may not be loaded at plugin start.
			// this will return -1 and retry if not all textures are loaded yet, too.
			textureArrayId = textureManager.initTextureArray(textureProvider);
			if (textureArrayId > -1)
			{
				// if texture upload is successful, compute and set texture animations
				float[] texAnims = textureManager.computeTextureAnimations(textureProvider);
				glUniform2fv(uni.TextureAnimations, texAnims);
			}
		}

		glUniform1i(uni.Textures, 1); // texture sampler array is bound to texture1

		// We just allow the GL to do face culling. Note this requires the priority renderer
		// to have logic to disregard culled faces in the priority depth testing.
		glEnable(GL_CULL_FACE);

		// Enable blending for alpha
		glEnable(GL_BLEND);
		glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE);

		if (computeMode == ComputeMode.OPENGL)
		{
			// Before reading the SSBOs written to from postDrawScene() we must insert a barrier
			glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);
		}
		else
		{
			// Wait for the command queue to finish, so that we know the compute is done
			openCLManager.finish();
		}

		glDrawArrays(GL_TRIANGLES, 0, targetBufferOffset);

		glDisable(GL_BLEND);
		glDisable(GL_CULL_FACE);
		glUseProgram(0);
	}

	private void drawShadowPass()
	{
		glViewport(0, 0, shadowMapFramebuffer.getTexture().getWidth(), shadowMapFramebuffer.getTexture().getHeight());
		glBindFramebuffer(GL_FRAMEBUFFER, shadowMapFramebuffer.getId());

		glClearDepthf(1);
		glClear(GL_DEPTH_BUFFER_BIT);
		glDepthFunc(GL_LEQUAL);

		glUseProgram(glShadowProgram);
		Uniforms.ShaderVariables uni = uniforms.GetUniforms(glShadowProgram);

		glUniformBlockBinding(glProgram, uni.CameraBlock, CAMERA_BUFFER_BINDING_ID);
		glUniformBlockBinding(glProgram, uni.PlayerBlock,  PLAYER_BUFFER_BINDING_ID);
		glUniformBlockBinding(glProgram, uni.EnvironmentBlock, ENVIRONMENT_BUFFER_BINDING_ID);
		glUniformBlockBinding(glProgram, uni.TileMarkerBlock, TILEMARKER_BUFFER_BINDING_ID);
		glUniformBlockBinding(glProgram, uni.SystemInfoBlock, SYSTEMINFO_BUFFER_BINDING_ID);
		glUniformBlockBinding(glProgram, uni.ConfigBlock, CONFIG_BUFFER_BINDING_ID);

		glEnable(GL_CULL_FACE);
		glEnable(GL_DEPTH_TEST);

		glDrawArrays(GL_TRIANGLES, 0, targetBufferOffset);

		glDisable(GL_CULL_FACE);
		glDisable(GL_DEPTH_TEST);

		glBindFramebuffer(GL_FRAMEBUFFER, awtContext.getFramebuffer(false));
		glUseProgram(0);
	}

	private void drawUi(final int overlayColor, final int canvasHeight, final int canvasWidth)
	{
		glEnable(GL_BLEND);
		glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
		glBindTexture(GL_TEXTURE_2D, interfaceTexture);

		// Use the texture bound in the first pass
		final UIScalingMode uiScalingMode = config.uiScalingMode();
		Uniforms.ShaderVariables uni = uniforms.GetUniforms(glUiProgram);
		glUseProgram(glUiProgram);
		glUniform1i(uni.Tex, 0);
		glUniform1i(uni.TexSamplingMode, uiScalingMode.getMode());
		glUniform2i(uni.TexSourceDimensions, canvasWidth, canvasHeight);
		glUniform1i(uni.UiColorBlindMode, config.colorBlindMode().ordinal());
		glUniform4f(uni.UiAlphaOverlay,
			(overlayColor >> 16 & 0xFF) / 255f,
			(overlayColor >> 8 & 0xFF) / 255f,
			(overlayColor & 0xFF) / 255f,
			(overlayColor >>> 24) / 255f
		);

		if (client.isStretchedEnabled())
		{
			Dimension dim = client.getStretchedDimensions();
			glDpiAwareViewport(0, 0, dim.width, dim.height);
			glUniform2i(uni.TexTargetDimensions, dim.width, dim.height);
		}
		else
		{
			glDpiAwareViewport(0, 0, canvasWidth, canvasHeight);
			glUniform2i(uni.TexTargetDimensions, canvasWidth, canvasHeight);
		}

		// Set the sampling function used when stretching the UI.
		// This is probably better done with sampler objects instead of texture parameters, but this is easier and likely more portable.
		// See https://www.khronos.org/opengl/wiki/Sampler_Object for details.
		if (client.isStretchedEnabled())
		{
			// GL_NEAREST makes sampling for bicubic/xBR simpler, so it should be used whenever linear isn't
			final int function = uiScalingMode == UIScalingMode.LINEAR ? GL_LINEAR : GL_NEAREST;
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, function);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, function);
		}

		// Texture on UI
		glBindVertexArray(vaoUiHandle);
		glDrawArrays(GL_TRIANGLE_FAN, 0, 4);

		// Reset
		glBindTexture(GL_TEXTURE_2D, 0);
		glBindVertexArray(0);
		glUseProgram(0);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
		glDisable(GL_BLEND);
	}

	/**
	 * Convert the front framebuffer to an Image
	 *
	 * @return
	 */
	private Image screenshot()
	{
		int width = client.getCanvasWidth();
		int height = client.getCanvasHeight();

		if (client.isStretchedEnabled())
		{
			Dimension dim = client.getStretchedDimensions();
			width = dim.width;
			height = dim.height;
		}

		if (OSType.getOSType() != OSType.MacOS)
		{
			final GraphicsConfiguration graphicsConfiguration = clientUI.getGraphicsConfiguration();
			final AffineTransform t = graphicsConfiguration.getDefaultTransform();
			width = getScaledValue(t.getScaleX(), width);
			height = getScaledValue(t.getScaleY(), height);
		}

		ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4)
			.order(ByteOrder.nativeOrder());

		glReadBuffer(awtContext.getBufferMode());
		glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

		for (int y = 0; y < height; ++y)
		{
			for (int x = 0; x < width; ++x)
			{
				int r = buffer.get() & 0xff;
				int g = buffer.get() & 0xff;
				int b = buffer.get() & 0xff;
				buffer.get(); // alpha

				pixels[(height - y - 1) * width + x] = (r << 16) | (g << 8) | b;
			}
		}

		return image;
	}

	@Override
	public void animate(Texture texture, int diff)
	{
		// texture animation happens on gpu
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Avoid drawing the last frame's buffer during LOADING after LOGIN_SCREEN
			targetBufferOffset = 0;
		}
	}

	public boolean loadingScene = false;
	@Override
	public void loadScene(Scene scene)
	{
		loadingScene = true;
		GpuIntBuffer vertexBuffer = new GpuIntBuffer();
		GpuFloatBuffer uvBuffer = new GpuFloatBuffer();
		GpuFloatBuffer normalBuffer = new GpuFloatBuffer();
		GpuIntBuffer flagsBuffer = new GpuIntBuffer();

		sceneUploader.UploadScene(scene, vertexBuffer, uvBuffer, normalBuffer, flagsBuffer);

		vertexBuffer.flip();
		uvBuffer.flip();
		normalBuffer.flip();

		nextSceneVertexBuffer = vertexBuffer;
		nextSceneTexBuffer = uvBuffer;
		nextSceneNormalBuffer = normalBuffer;
		nextSceneFlagsBuffer = flagsBuffer;
		nextSceneId = sceneUploader.sceneId;

		if(config.roofFading()) {
			scene.setRoofRemovalMode(16);
		} else {
			scene.setRoofRemovalMode(0);
		}
	}

	private void uploadTileHeights(Scene scene)
	{
		if (tileHeightTex != 0)
		{
			glDeleteTextures(tileHeightTex);
			tileHeightTex = 0;
		}

		final int TILEHEIGHT_BUFFER_SIZE = Constants.MAX_Z * EXTENDED_SCENE_SIZE * EXTENDED_SCENE_SIZE * Short.BYTES;
		ShortBuffer tileBuffer = ByteBuffer
			.allocateDirect(TILEHEIGHT_BUFFER_SIZE)
			.order(ByteOrder.nativeOrder())
			.asShortBuffer();

		int[][][] tileHeights = scene.getTileHeights();
		for (int z = 0; z < Constants.MAX_Z; ++z)
		{
			for (int y = 0; y < EXTENDED_SCENE_SIZE; ++y)
			{
				for (int x = 0; x < EXTENDED_SCENE_SIZE; ++x)
				{
					int h = tileHeights[z][x][y];
					assert (h & 0b111) == 0;
					h >>= 3;
					tileBuffer.put((short) h);
				}
			}
		}
		tileBuffer.flip();

		tileHeightTex = glGenTextures();
		glBindTexture(GL_TEXTURE_3D, tileHeightTex);
		glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
		glTexImage3D(GL_TEXTURE_3D, 0, GL_R16I,
			EXTENDED_SCENE_SIZE, EXTENDED_SCENE_SIZE, Constants.MAX_Z,
			0, GL_RED_INTEGER, GL_SHORT, tileBuffer);
		glBindTexture(GL_TEXTURE_3D, 0);

		// bind to texture 2
		glActiveTexture(GL_TEXTURE2);
		glBindTexture(GL_TEXTURE_3D, tileHeightTex); // binding = 2 in the shader
		glActiveTexture(GL_TEXTURE0);
	}

	@Override
	public void swapScene(Scene scene)
	{
		SwapSceneInternal(scene);
	}

	private void SwapSceneInternal(Scene scene)
	{
		if (computeMode == ComputeMode.OPENCL)
		{
			openCLManager.uploadTileHeights(scene);
		}
		else
		{
			assert computeMode == ComputeMode.OPENGL;
			uploadTileHeights(scene);
		}

		sceneId = nextSceneId;
		updateBuffer(sceneVertexBuffer, GL_ARRAY_BUFFER, nextSceneVertexBuffer.getBuffer(), GL_STATIC_COPY, CL12.CL_MEM_READ_ONLY);
		updateBuffer(sceneUvBuffer, GL_ARRAY_BUFFER, nextSceneTexBuffer.getBuffer(), GL_STATIC_COPY, CL12.CL_MEM_READ_ONLY);
		updateBuffer(sceneNormalBuffer, GL_ARRAY_BUFFER, nextSceneNormalBuffer.getBuffer(), GL_STATIC_COPY, CL12.CL_MEM_READ_ONLY);
		updateBuffer(sceneFlagsBuffer, GL_ARRAY_BUFFER, nextSceneFlagsBuffer.getBuffer(), GL_STATIC_COPY, CL12.CL_MEM_READ_ONLY);

		nextSceneVertexBuffer = null;
		nextSceneTexBuffer = null;
		nextSceneNormalBuffer = null;
		nextSceneFlagsBuffer = null;
		nextSceneId = -1;

		modelRoofCache.clear();
		tileMarkerManager.Reset();
		tileMarkerManager.LoadTileMarkers();
		tileMarkerManager.InitializeSceneRoofMask(scene);
		environmentManager.LoadSceneLights(scene);
		environmentManager.CheckRegion();
		sceneUploader.PrepareScene(scene);
		loadingScene = false;

		checkGLErrors();
	}

	@Override
	public boolean tileInFrustum(Scene scene, int pitchSin, int pitchCos, int yawSin, int yawCos, int cameraX, int cameraY, int cameraZ, int plane, int msx, int msy) {
		// Get the tile heights from the scene
		int[][][] tileHeights = scene.getTileHeights();

		// Calculate the relative x and z coordinates of the tile from the camera's perspective
		int x = ((msx - SCENE_OFFSET) << Perspective.LOCAL_COORD_BITS) + 64 - cameraX;
		int z = ((msy - SCENE_OFFSET) << Perspective.LOCAL_COORD_BITS) + 64 - cameraZ;

		// Determine the highest point on the tile
		int y = Math.max(
				Math.max(tileHeights[plane][msx][msy], tileHeights[plane][msx][msy + 1]),
				Math.max(tileHeights[plane][msx + 1][msy], tileHeights[plane][msx + 1][msy + 1])
		) + GROUND_MIN_Y - cameraY;

		// Radius for frustum culling
		int radius = 96; // ~ 64 * sqrt(2)

		// Get the necessary rendering parameters from the client
		int zoom = client.get3dZoom() / 2;
		int clipMaxX = client.getRasterizer3D_clipMidX2();
		int clipMinX = client.getRasterizer3D_clipNegativeMidX();
		int clipCeilY = client.getRasterizer3D_clipNegativeMidY();

		// Transform the coordinates using yaw
		int transformedX = yawCos * z - yawSin * x >> 16;
		int transformedY = pitchSin * y + pitchCos * transformedX >> 16;
		int transformedRadius = pitchCos * radius >> 16;
		int depth = transformedY + transformedRadius;

		// Check if the depth is within the view frustum
		if (depth > 50) {
			int rotatedX = z * yawSin + yawCos * x >> 16;
			int minX = (rotatedX - radius) * zoom;
			int maxX = (rotatedX + radius) * zoom;

			// Check if the tile is within the left and right bounds of the view frustum
			if (minX < clipMaxX * depth && maxX > clipMinX * depth) {
				int rotatedY = pitchCos * y - transformedX * pitchSin >> 16;
				int minY = pitchSin * radius >> 16;
				int maxY = (rotatedY + minY) * zoom;

				// Check if the tile is within the top bound of the view frustum
				if (maxY > clipCeilY * depth) {
					// We don't test the bottom bound to avoid calculating the height of all models on the tile
					return true;
				}
			}
		}

		return false;
	}


	/**
	 * Check is a model is visible and should be drawn.
	 */
	private boolean isVisible(Model model, int pitchSin, int pitchCos, int yawSin, int yawCos, int x, int y, int z)
	{
		model.calculateBoundsCylinder();

		final int xzMag = model.getXYZMag();
		final int bottomY = model.getBottomY();
		final int zoom = client.get3dZoom() / 2;
		final int modelHeight = model.getModelHeight();

		int Rasterizer3D_clipMidX2 = client.getRasterizer3D_clipMidX2(); // width / 2
		int Rasterizer3D_clipNegativeMidX = client.getRasterizer3D_clipNegativeMidX(); // -width / 2
		int Rasterizer3D_clipNegativeMidY = client.getRasterizer3D_clipNegativeMidY(); // -height / 2
		int Rasterizer3D_clipMidY2 = client.getRasterizer3D_clipMidY2(); // height / 2

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

	/**
	 * Draw a renderable in the scene
	 */
	@Override
	public void draw(Projection projection, Scene scene, Renderable renderable, int orientation, int x, int y, int z, long hash)
	{
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

		if (offsetModel.getSceneId() == sceneId)
		{
			PackStaticModel(projection, model, offsetModel, renderable, orientation, x, y, z, hash);
		}
		else
		{
			PackDynamicModel(projection, model, offsetModel, renderable, orientation, x, y, z, hash);
		}
	}

	private void PackStaticModel(Projection projection, Model model, Model offsetModel, Renderable renderable, int orientation, int x, int y, int z, long hash)
	{
		//staticModelUpload.start();
		assert model == renderable;

		CalculateModelBoundsAndClickbox(projection, model, orientation, x, y, z, hash);

		int tc = Math.min(MAX_TRIANGLE, offsetModel.getFaceCount());
		int uvOffset = offsetModel.getUvBufferOffset();
		int flags = GetModelPackedFlags(hash, model, offsetModel, orientation);
		int exFlags = GetExFlags(hash, x, y, z, false);

		GpuIntBuffer b = bufferForTriangles(tc);

		b.ensureCapacity(12);
		IntBuffer buffer = b.getBuffer();
		buffer.put(offsetModel.getBufferOffset());
		buffer.put(uvOffset);
		buffer.put(tc);
		buffer.put(targetBufferOffset);
		buffer.put(FLAG_SCENE_BUFFER | flags);
		buffer.put(x).put(y).put(z);
		buffer.put(exFlags);
		buffer.put(0);
		buffer.put(0);
		buffer.put(0);

		targetBufferOffset += tc * 3;
		//staticModelUpload.stop();
	}

	private void PackDynamicModel(Projection projection, Model model, Model offsetModel, Renderable renderable, int orientation, int x, int y, int z, long hash)
	{
		//dynamicModelUpload.start();
		// Apply height to renderable from the model
		if (model != renderable)
		{
			renderable.setModelHeight(model.getModelHeight());
		}

		CalculateModelBoundsAndClickbox(projection, model, orientation, x, y, z, hash);
		int flags = GetModelPackedFlags(hash, model, offsetModel, orientation);
		int exFlags = GetExFlags(hash, x, y, z, true);
		boolean hasUv = model.getFaceTextures() != null;

		int len = sceneUploader.PushDynamicModel(model, vertexBuffer, uvBuffer, normalBuffer, flagsBuffer);

		GpuIntBuffer b = bufferForTriangles(len / 3);
		b.ensureCapacity(12);
		IntBuffer buffer = b.getBuffer();
		buffer.put(tempOffset);
		buffer.put(hasUv ? tempUvOffset : -1);
		buffer.put(len / 3);
		buffer.put(targetBufferOffset);
		buffer.put(flags);
		buffer.put(x).put(y).put(z);
		buffer.put(exFlags);
		buffer.put(0);
		buffer.put(0);
		buffer.put(0);

		tempOffset += len;
		targetBufferOffset += len;
		if(hasUv) {
			tempUvOffset += len;
		}
		//dynamicModelUpload.stop();
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

	private void CalculateModelBoundsAndClickbox(Projection projection, Model model, int orientation, int x, int y, int z, long hash)
	{
		model.calculateBoundsCylinder();

		if (projection instanceof IntProjection)
		{
			IntProjection p = (IntProjection) projection;
			if (!isVisible(model, p.getPitchSin(), p.getPitchCos(), p.getYawSin(), p.getYawCos(), x - p.getCameraX(), y - p.getCameraY(), z - p.getCameraZ()))
			{
				return;
			}
		}

		client.checkClickbox(projection, model, orientation, x, y, z, hash);
	}

	private boolean CheckModelIsVisible( Model model, Projection projection, int x, int y, int z )
	{
		model.calculateBoundsCylinder();
		if (projection instanceof IntProjection)
		{
			IntProjection p = (IntProjection) projection;
			return isVisible(model, p.getPitchSin(), p.getPitchCos(), p.getYawSin(), p.getYawCos(), x - p.getCameraX(), y - p.getCameraY(), z - p.getCameraZ());
		}
		return true;
	}

	/**
	 * returns the correct buffer based on triangle count and updates model count
	 *
	 * @param triangles
	 * @return
	 */
	private GpuIntBuffer bufferForTriangles(int triangles)
	{
		if (triangles <= SMALL_TRIANGLE_COUNT)
		{
			smallModels++;
			return modelBufferSmall;
		}
		else
		{
			largeModels++;
			return modelBuffer;
		}
	}

	private int getScaledValue(final double scale, final int value)
	{
		return (int) (value * scale + .5);
	}

	private void glDpiAwareViewport(final int x, final int y, final int width, final int height)
	{
		if (OSType.getOSType() == OSType.MacOS)
		{
			// macos handles DPI scaling for us already
			glViewport(x, y, width, height);
		}
		else
		{
			final GraphicsConfiguration graphicsConfiguration = clientUI.getGraphicsConfiguration();
			final AffineTransform t = graphicsConfiguration.getDefaultTransform();
			glViewport(
				getScaledValue(t.getScaleX(), x),
				getScaledValue(t.getScaleY(), y),
				getScaledValue(t.getScaleX(), width),
				getScaledValue(t.getScaleY(), height));
		}
	}

	public int getDrawDistance()
	{
		final int limit = computeMode != ComputeMode.NONE ? MAX_DISTANCE : DEFAULT_DISTANCE;
		return Ints.constrainToRange(config.drawDistance(), 0, limit);
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
		if (glCapabilities.glInvalidateBufferData != 0L)
		{
			// https://www.khronos.org/opengl/wiki/Buffer_Object_Streaming suggests buffer re-specification is useful
			// to avoid implicit synching. We always need to trash the whole buffer anyway so this can't hurt.
			glInvalidateBufferData(glBuffer.glBufferId);
		}
		if (size > glBuffer.size)
		{
			int newSize = Math.max(1024, nextPowerOfTwo(size));
			log.trace("Buffer resize: {} {} -> {}", glBuffer.name, glBuffer.size, newSize);

			glBuffer.size = newSize;
			glBufferData(target, newSize, usage);
			recreateCLBuffer(glBuffer, clFlags);
		}
	}

	private static int nextPowerOfTwo(int v)
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

	private void recreateCLBuffer(GLBuffer glBuffer, long clFlags)
	{
		if (computeMode == ComputeMode.OPENCL)
		{
			if (glBuffer.clBuffer != -1)
			{
				CL10.clReleaseMemObject(glBuffer.clBuffer);
			}
			if (glBuffer.size == 0)
			{
				glBuffer.clBuffer = -1;
			}
			else
			{
				glBuffer.clBuffer = CL10GL.clCreateFromGLBuffer(openCLManager.context, clFlags, glBuffer.glBufferId, (int[]) null);
			}
		}
	}

	public void checkGLErrors()
	{
//		if (!log.isDebugEnabled())
//		{
//			return;
//		}
//
//		for (; ; )
//		{
//			int err = glGetError();
//			if (err == GL_NO_ERROR)
//			{
//				return;
//			}
//
//			String errStr;
//			switch (err)
//			{
//				case GL_INVALID_ENUM:
//					errStr = "INVALID_ENUM";
//					break;
//				case GL_INVALID_VALUE:
//					errStr = "INVALID_VALUE";
//					break;
//				case GL_INVALID_OPERATION:
//					errStr = "INVALID_OPERATION";
//					break;
//				case GL_INVALID_FRAMEBUFFER_OPERATION:
//					errStr = "INVALID_FRAMEBUFFER_OPERATION";
//					break;
//				default:
//					errStr = "" + err;
//					break;
//			}
//
//			log.debug("glGetError:", new Exception(errStr));
//		}
	}
}
