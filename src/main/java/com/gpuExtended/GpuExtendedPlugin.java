package com.gpuExtended;

import com.google.common.primitives.Ints;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Provides;
import com.gpuExtended.config.AntiAliasingMode;
import com.gpuExtended.opengl.GLBuffer;
import com.gpuExtended.opengl.OpenCLManager;
import com.gpuExtended.overlays.*;
import com.gpuExtended.regions.Area;
import com.gpuExtended.rendering.Vector4;
import com.gpuExtended.rendering.passes.*;
import com.gpuExtended.scene.EnvironmentManager;
import com.gpuExtended.scene.Light;
import com.gpuExtended.scene.Skybox;
import com.gpuExtended.scene.TileMarkers.TileMarkerManager;
import com.gpuExtended.shader.ShaderHandler;
import com.gpuExtended.shader.Uniforms;
import com.gpuExtended.util.*;
import com.gpuExtended.util.config.ShadowResolution;
import com.gpuExtended.util.deserializers.AreaDeserializer;
import com.gpuExtended.util.deserializers.ColorDeserializer;
import com.gpuExtended.util.deserializers.LightDeserializer;
import com.gpuExtended.util.deserializers.VectorDeserializer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
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
import org.lwjgl.opengl.GLDebugMessageCallback;
import org.lwjgl.opengl.GLUtil;
import org.lwjgl.system.Callback;
import org.lwjgl.system.Configuration;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.*;

import static com.gpuExtended.util.ResourcePath.path;
import static com.gpuExtended.util.constants.Variables.*;
import static java.lang.Character.getType;
import static net.runelite.api.Constants.EXTENDED_SCENE_SIZE;
import static net.runelite.api.Constants.MAX_Z;
import static org.lwjgl.opengl.GL43C.*;
import static org.lwjgl.opengl.GLDebugMessageCallback.getMessage;

@Slf4j
@PluginDescriptor(
	name = "_GPU Extended"
)
public class GpuExtendedPlugin extends Plugin implements DrawCallbacks
{
	public static GpuExtendedPlugin Instance;

	@Inject
	public Client client;

	@Inject
	public ClientUI clientUI;

	@Inject
	private OpenCLManager openCLManager;

	@Inject
	public ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	@Inject
	public GpuExtendedConfig config;

	@Getter
	private Gson gson;

	@Inject
	public TextureManager textureManager;

	@Inject
	public SceneUploader sceneUploader;

	@Inject
	private DrawManager drawManager;

	@Inject
	private PluginManager pluginManager;

	@Inject
	public Uniforms uniforms;

	@Inject
	public ShaderHandler shaders;

	@Inject
	public EnvironmentManager environmentManager;

	@Inject
	public Skybox skybox;

	@Inject
	public PerformanceOverlay performanceOverlay;

	@Inject
	public LightOverlay lightOverlay;

	@Inject
	public MainPassLegacy mainPassLegacy;

	@Inject
	public MainPass mainPass;

	@Inject
	public ShadowPass shadowPass;

	@Inject
	public PostProcessingPass postProcessingPass;

	@Inject
	public CompositePass compositePass;

	public enum ComputeMode
	{
		NONE,
		OPENGL,
		OPENCL
	}

	public ComputeMode computeMode = ComputeMode.OPENGL;

	private Canvas canvas;
	public AWTContext awtContext;
	private Callback debugCallback;
	private GLDebugMessageCallback glDebugCallback;

	public GLCapabilities glCapabilities;

	public boolean enableShadowMapOverlay = false;
	public boolean enableTileMaskOverlay = false;
	public boolean showRegionOverlay = false;
	public boolean showPerformanceOverlay = false;
	public boolean showLightOverlay = false;
	public boolean showTileInspectorOverlay = false;

	private int fboSceneHandle;
	private int rboSceneHandle;

	public final GLBuffer lightBinsBuffer = new GLBuffer("light bins buffer");

	public int textureArrayId;
	public int tileHeightTex;

	private int lastStretchedCanvasWidth;
	private int lastStretchedCanvasHeight;
	private AntiAliasingMode lastAntiAliasingMode;
	private int lastAnisotropicFilteringLevel = -1;

	public double cameraX, cameraY, cameraZ;
	public double cameraYaw, cameraPitch;

	private int viewportOffsetX;
	private int viewportOffsetY;

	private boolean lwjglInitted = false;

	public int sceneId;
	private int nextSceneId;

	public long Time;
	private long LastTime;
	public float DeltaTime;
	private long StartTime;
	public float currentTrueTileAlpha = 1;
	private int currentPlane = 0;
	private long frameTime = 0;

	public long staticDrawCallTimeTotal = 0;

	public boolean roofFading = false;

	public int[] lastPlayerPosition = new int[2];

	public int[] currentViewport = new int[4];

	@Inject
	private RenderTargetsOverlay shadowMapOverlay;

	@Inject
	private SceneTileMaskOverlay sceneTileMaskOverlay;

	@Inject
	public TileMarkerManager tileMarkerManager;

	@Inject
	private RegionOverlay regionOverlay;

	@Inject
	private TileInspectorOverlay tileInspectorOverlay;

	@Provides
	GpuExtendedConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GpuExtendedConfig.class);
	}

	@Override
	protected void startUp()
	{
		clientThread.invoke(() ->
		{
			try
			{
				StartTime = System.currentTimeMillis();
				LastTime = System.currentTimeMillis();

				if(Instance == null)
				{
					Instance = this;
				}

				if(client.getGameState() == GameState.LOGGED_IN) {
					client.setGameState(GameState.LOADING);
				}

				setupCustomGsonSerializers();
				fboSceneHandle = rboSceneHandle = -1; // AA FBO

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
				Configuration.SHARED_LIBRARY_EXTRACT_DIRECTORY.set("lwjgl-rl-");

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

				createGlDebugCallback();

				// Initialize Render Pass Handlers
				uniforms.InitializeResourceTextures();
				uniforms.InitializeUniformBlocks();

				shadowPass.Init();
				mainPassLegacy.Init();
				postProcessingPass.Init();
				compositePass.Init();
				// --

				setupSyncMode();

				shaders.Initialize();
				tileMarkerManager.Initialize(EXTENDED_SCENE_SIZE);
				environmentManager.Initialize();

				eventBus.register(tileMarkerManager);

				initBuffers();

				client.setDrawCallbacks(this);
				client.setGpuFlags(DrawCallbacks.GPU | DrawCallbacks.HILLSKEW | DrawCallbacks.NORMALS);
				client.setExpandedMapLoading(config.expandedMapLoadingChunks());

				// force rebuild of main buffer provider to enable alpha channel
				client.resizeCanvas();

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

				shutdownProgram();
				shutdownBuffers();
				shutdownAAFbo();

				eventBus.unregister(tileMarkerManager);
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

			mainPassLegacy.Dispose();
			shadowPass.Dispose();
			postProcessingPass.Dispose();
			compositePass.Dispose();

			shadowMapOverlay.setActive(false, 0);

			lastAnisotropicFilteringLevel = -1;

			// force main buffer provider rebuild to turn off alpha channel
			client.resizeCanvas();
		});
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
			else if(configChanged.getKey().equals("shadowResolution"))
			{
				clientThread.invokeLater(() ->
				{
					// TODO:: Move resizing to ShadowPass.java
					if (shadowPass.GetFramebuffer().isInitialized() && shadowPass.GetDynamicFramebuffer().isInitialized()) {
						int res = config.shadowResolution().getValue();
						if (config.shadowResolution() == ShadowResolution.RES_OFF) {
							shadowPass.GetFramebuffer().resize(1, 1);
							shadowPass.GetDynamicFramebuffer().resize(1, 1);
						} else {
							shadowPass.GetFramebuffer().resize(res, res);
							shadowPass.GetDynamicFramebuffer().resize(res, res);
						}
					}
				});
			}
		}
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		environmentManager.OnProjectileMoved(event);
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		environmentManager.OnGameObjectSpawned(event);
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		environmentManager.OnGameObjectDespawned(event);
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		environmentManager.OnNpcSpawned(event);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		environmentManager.OnNpcDespawned(event);
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event) {
		environmentManager.OnAnimationChanged(event);
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
	}

	private void shutdownProgram()
	{
		FileWatcher.destroy();
	    shaders.cleanup();
	}

	private void initBuffers()
	{
		initGlBuffer(lightBinsBuffer);

		glBindBuffer(GL_SHADER_STORAGE_BUFFER, lightBinsBuffer.glBufferId);
		// +1 for the light count
		glBufferData(GL_SHADER_STORAGE_BUFFER, Ints.BYTES * EXTENDED_SCENE_SIZE * EXTENDED_SCENE_SIZE * MAX_Z * (MAX_LIGHTS_PER_TILE + 1), GL_DYNAMIC_COPY);
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
	}

	public void initGlBuffer(GLBuffer glBuffer)
	{
		glBuffer.glBufferId = glGenBuffers();
		log.info("Initialized GLBuffer: {}, {}", glBuffer.glBufferId, glBuffer.name);
	}

	private ByteBuffer initUniformBufferBlock(GLBuffer glBuffer, int blockSizeBytes)
	{
		ByteBuffer byteBuffer = BufferUtils.createByteBuffer(blockSizeBytes);
		updateBuffer(glBuffer, GL_UNIFORM_BUFFER, blockSizeBytes, GL_DYNAMIC_DRAW);
		return byteBuffer;
	}

	private void shutdownBuffers()
	{
		destroyGlBuffer(lightBinsBuffer);
		uniforms.Dispose();
	}

	public void destroyGlBuffer(GLBuffer glBuffer)
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
		performanceOverlay.ResetTimers();
		performanceOverlay.StartTimer(PerformanceOverlay.TimerType.FRAME_CPU);

		this.cameraX = cameraX;
		this.cameraY = cameraY;
		this.cameraZ = cameraZ;
		this.cameraPitch = cameraPitch;
		this.cameraYaw = cameraYaw;
		viewportOffsetX = client.getViewportXOffset();
		viewportOffsetY = client.getViewportYOffset();

		final Scene scene = client.getScene();
		scene.setDrawDistance(getDrawDistance());

		mainPassLegacy.OnDrawScene();
	}

	@Override
	public void postDrawScene()
	{
		mainPassLegacy.OnPostDrawScene();
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

		final int canvasHeight = client.getCanvasHeight();
		final int canvasWidth = client.getCanvasWidth();

		final int viewportHeight = client.getViewportHeight();
		final int viewportWidth = client.getViewportWidth();

		shadowMapOverlay.setActive(config.showShadowMap(), shaders.uiShader.id());
		sceneTileMaskOverlay.setActive(config.showTileMask());
		regionOverlay.setActive(config.showRegionOverlay());
		performanceOverlay.setActive(config.showPerformanceOverlay());
		lightOverlay.SetActive(config.showLightOverlays());
		tileInspectorOverlay.setActive(config.showTileInspectorOverlay());

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

		glClearColor(0, 0, 0, 1f);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		shadowPass.OnPreRenderFrame();
		mainPassLegacy.OnPreRenderFrame();
		postProcessingPass.OnPreRenderFrame();
		compositePass.OnPreRenderFrame();

		if (gameState.getState() >= GameState.LOADING.getState()
				&& viewportHeight > 0
				&& viewportWidth > 0
		)
		{
			//<editor-fold defaultstate="collapsed" desc="Set up misc frame data">
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
			// </editor-fold>

			glDpiAwareViewport(renderWidthOff, renderCanvasHeight - renderViewportHeight - renderHeightOff, renderViewportWidth, renderViewportHeight);
			glGetIntegerv(GL_VIEWPORT, currentViewport);

			if (mainPassLegacy.frameBuffer.getTexture().getWidth() != currentViewport[2] || mainPassLegacy.frameBuffer.getTexture().getHeight() != currentViewport[3]) {
				mainPassLegacy.frameBuffer.resize(currentViewport[2], currentViewport[3]);
				postProcessingPass.bloomFramebuffer.resize(currentViewport[2], currentViewport[3]);

				log.info("Resizing Color Framebuffers: {}x{}", currentViewport[2], currentViewport[3]);
				log.info("Resizing Bloom Framebuffers: {}x{}", currentViewport[2], currentViewport[3]);
			}

			long currentTime = System.currentTimeMillis();
			DeltaTime = (currentTime - LastTime) / 1000.0f;
			Time = currentTime - StartTime;
			LastTime = currentTime;
			environmentManager.Update(DeltaTime);

			uniforms.UpdateUniformBlocks();
			shadowPass.OnRenderFrame();
			mainPassLegacy.OnRenderFrame();
			postProcessingPass.OnRenderFrame();
			compositePass.OnRenderFrame();

			lastPlayerPosition[0] = client.getLocalPlayer().getLocalLocation().getX();
			lastPlayerPosition[1] = client.getLocalPlayer().getLocalLocation().getY();
			currentPlane = client.getPlane();
		}

	    shadowPass.OnPostRenderFrame();
		mainPassLegacy.OnPostRenderFrame();
		postProcessingPass.OnPostRenderFrame();

		compositePass.SetOverlayColor(overlayColor);
		compositePass.OnPostRenderFrame();

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

		performanceOverlay.EndTimer(PerformanceOverlay.TimerType.FRAME_CPU);
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
		mainPassLegacy.OnGameStateChanged(gameStateChanged);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		environmentManager.OnTick();
	}

	public boolean loadingScene = false;
	@Override
	public void loadScene(Scene scene)
	{
		loadingScene = true;
		mainPassLegacy.OnSceneLoadStart(scene);
		shadowPass.OnSceneLoadStart(scene);

		nextSceneId = sceneUploader.sceneId;
	}

	private void uploadTileHeights(Scene scene)
	{
		if (tileHeightTex != 0)
		{
			glDeleteTextures(tileHeightTex);
			tileHeightTex = 0;
		}

		final int TILEHEIGHT_BUFFER_SIZE = Constants.MAX_Z * EXTENDED_SCENE_SIZE * EXTENDED_SCENE_SIZE * Float.BYTES;
		FloatBuffer tileBuffer = ByteBuffer
				.allocateDirect(TILEHEIGHT_BUFFER_SIZE)
				.order(ByteOrder.nativeOrder())
				.asFloatBuffer();

		int[][][] tileHeights = scene.getTileHeights();
		for (int z = 0; z < Constants.MAX_Z; ++z)
		{
			for (int y = 0; y < EXTENDED_SCENE_SIZE; ++y)
			{
				for (int x = 0; x < EXTENDED_SCENE_SIZE; ++x)
				{
					int h = tileHeights[z][x][y];
					assert (h & 0b111) == 0;
					tileBuffer.put((float) h);
				}
			}
		}
		tileBuffer.flip();

		tileHeightTex = glGenTextures();
		glBindTexture(GL_TEXTURE_2D_ARRAY, tileHeightTex);
		glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

		glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_R32F,
				EXTENDED_SCENE_SIZE, EXTENDED_SCENE_SIZE, Constants.MAX_Z,
				0, GL_RED, GL_FLOAT, tileBuffer);
		glBindTexture(GL_TEXTURE_2D_ARRAY, 0);

		glActiveTexture(GL_TEXTURE2);
		glBindTexture(GL_TEXTURE_2D_ARRAY, tileHeightTex);
		glActiveTexture(GL_TEXTURE0);
	}

	@Override
	public void swapScene(Scene scene)
	{
		assert computeMode == ComputeMode.OPENGL;
		uploadTileHeights(scene);

		sceneId = nextSceneId;

		environmentManager.CheckRegion();
		sceneUploader.PrepareScene(scene);

		mainPassLegacy.OnSceneLoadFinished(scene);
		shadowPass.OnSceneLoadFinished(scene);

		tileMarkerManager.Reset();
		tileMarkerManager.LoadTileMarkers();
		environmentManager.LoadSceneLights(scene);

		loadingScene = false;

		nextSceneId = -1;
	}

	/**
	 * Draw a renderable in the scene
	 */
	@Override
	public void draw(Projection projection, Scene scene, Renderable renderable, int orientation, int x, int y, int z, long hash)
	{
		mainPassLegacy.OnDrawModel(projection, scene, renderable, orientation, x, y, z, hash);
	}

	@Override
	public void drawScenePaint(Scene scene, SceneTilePaint paint, int plane, int tileX, int tileY)
	{
		mainPassLegacy.OnDrawSceneTile(scene, paint, plane, tileX, tileY);
	}

	@Override
	public void drawSceneTileModel(Scene scene, SceneTileModel model, int tileX, int tileY)
	{
		mainPassLegacy.OnDrawSceneTileModel(scene, model, tileX, tileY);
	}

	@Override
	public boolean tileInFrustum(Scene scene, float pitchSin, float pitchCos, float yawSin, float yawCos, int cameraX, int cameraY, int cameraZ, int plane, int msx, int msy)
	{
		int[][][] tileHeights = scene.getTileHeights();
		int x = ((msx - SCENE_OFFSET) << Perspective.LOCAL_COORD_BITS) + 64 - cameraX;
		int z = ((msy - SCENE_OFFSET) << Perspective.LOCAL_COORD_BITS) + 64 - cameraZ;
		int y = Math.max(
				Math.max(tileHeights[plane][msx][msy], tileHeights[plane][msx][msy + 1]),
				Math.max(tileHeights[plane][msx + 1][msy], tileHeights[plane][msx + 1][msy + 1])
		) + GROUND_MIN_Y - cameraY;

		int radius = 96; // ~ 64 * sqrt(2)

		int zoom = client.get3dZoom();
		int Rasterizer3D_clipMidX2 = client.getRasterizer3D_clipMidX2();
		int Rasterizer3D_clipNegativeMidX = client.getRasterizer3D_clipNegativeMidX();
		int Rasterizer3D_clipNegativeMidY = client.getRasterizer3D_clipNegativeMidY();

		float var11 = yawCos * z - yawSin * x;
		float var12 = pitchSin * y + pitchCos * var11;
		float var13 = pitchCos * radius;
		float depth = var12 + var13;
		if (depth > 50)
		{
			float rx = z * yawSin + yawCos * x;
			float var16 = (rx - radius) * zoom;
			float var17 = (rx + radius) * zoom;
			// left && right
			if (var16 < Rasterizer3D_clipMidX2 * depth && var17 > Rasterizer3D_clipNegativeMidX * depth)
			{
				float ry = pitchCos * y - var11 * pitchSin;
				float ybottom = pitchSin * radius;
				float var20 = (ry + ybottom) * zoom;
				// top
				if (var20 > Rasterizer3D_clipNegativeMidY * depth)
				{
					// we don't test the bottom so we don't have to find the height of all the models on the tile
					return true;
				}
			}
		}
		return false;
	}

	private int getScaledValue(final double scale, final int value)
	{
		return (int) (value * scale + .5);
	}

	public void glDpiAwareViewport(final int x, final int y, final int width, final int height)
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

	public void updateBuffer(@Nonnull GLBuffer glBuffer, int target, @Nonnull IntBuffer data, int usage)
	{
		int size = data.remaining() << 2;
		updateBuffer(glBuffer, target, size, usage);
		glBufferSubData(target, 0, data);
	}

	public void updateBuffer(@Nonnull GLBuffer glBuffer, int target, @Nonnull FloatBuffer data, int usage)
	{
		int size = data.remaining() << 2;
		updateBuffer(glBuffer, target, size, usage);
		glBufferSubData(target, 0, data);
	}

	public void updateBuffer(@Nonnull GLBuffer glBuffer, int target, int size, int usage)
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
		}
	}

	public static int nextPowerOfTwo(int v)
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

	public void createGlDebugCallback()
	{
		glDebugCallback = GLDebugMessageCallback.create((source, type, id, severity, length, message, userParam) -> {
			String msg = getMessage(length, message);

			System.err.printf(
					"[OpenGL Debug] Source: %s, Type: %s, ID: 0x%X, Severity: %s\nMessage: %s\n\n",
					getSource(source),
					getType(type),
					id,
					getSeverity(severity),
					msg
			);
		});

		glEnable(GL_DEBUG_OUTPUT);
		glEnable(GL_DEBUG_OUTPUT_SYNCHRONOUS); // Make callback synchronous for debugging

		glDebugMessageCallback(glDebugCallback, 0);
	}

	private static String getSource(int source) {
		switch (source) {
			case GL_DEBUG_SOURCE_API: return "API";
			case GL_DEBUG_SOURCE_WINDOW_SYSTEM: return "WINDOW_SYSTEM";
			case GL_DEBUG_SOURCE_SHADER_COMPILER: return "SHADER_COMPILER";
			case GL_DEBUG_SOURCE_THIRD_PARTY: return "THIRD_PARTY";
			case GL_DEBUG_SOURCE_APPLICATION: return "APPLICATION";
			case GL_DEBUG_SOURCE_OTHER: return "OTHER";
			default: return "UNKNOWN";
		}
	}

	private static String getType(int type) {
		switch (type) {
			case GL_DEBUG_TYPE_ERROR: return "ERROR";
			case GL_DEBUG_TYPE_DEPRECATED_BEHAVIOR: return "DEPRECATED_BEHAVIOR";
			case GL_DEBUG_TYPE_UNDEFINED_BEHAVIOR: return "UNDEFINED_BEHAVIOR";
			case GL_DEBUG_TYPE_PORTABILITY: return "PORTABILITY";
			case GL_DEBUG_TYPE_PERFORMANCE: return "PERFORMANCE";
			case GL_DEBUG_TYPE_MARKER: return "MARKER";
			case GL_DEBUG_TYPE_PUSH_GROUP: return "PUSH_GROUP";
			case GL_DEBUG_TYPE_POP_GROUP: return "POP_GROUP";
			case GL_DEBUG_TYPE_OTHER: return "OTHER";
			default: return "UNKNOWN";
		}
	}

	private static String getSeverity(int severity) {
		switch (severity) {
			case GL_DEBUG_SEVERITY_HIGH: return "HIGH";
			case GL_DEBUG_SEVERITY_MEDIUM: return "MEDIUM";
			case GL_DEBUG_SEVERITY_LOW: return "LOW";
			case GL_DEBUG_SEVERITY_NOTIFICATION: return "NOTIFICATION";
			default: return "UNKNOWN";
		}
	}
}
