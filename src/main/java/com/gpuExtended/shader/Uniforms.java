package com.gpuExtended.shader;

import com.gpuExtended.GpuExtendedConfig;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.opengl.GLBuffer;
import com.gpuExtended.regions.Bounds;
import com.gpuExtended.rendering.Texture2D;
import com.gpuExtended.scene.Environment;
import com.gpuExtended.scene.Light;
import com.gpuExtended.util.Mat4;
import com.gpuExtended.util.Props;
import com.gpuExtended.util.ResourcePath;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.TextureProvider;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import org.lwjgl.BufferUtils;

import javax.inject.Inject;
import java.nio.ByteBuffer;
import java.util.HashMap;

import static com.gpuExtended.util.ResourcePath.path;
import static com.gpuExtended.util.constants.Variables.*;
import static net.runelite.api.Constants.EXTENDED_SCENE_SIZE;
import static net.runelite.api.Constants.MAX_Z;
import static org.lwjgl.opengl.GL43C.*;

@Slf4j
public class Uniforms
{ // A.K.A. Global Shader Variables

    @Inject
    public GpuExtendedPlugin plugin;

    public final GLBuffer glCameraUniformBuffer = new GLBuffer("camera uniform buffer");
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

    public HashMap<Integer, ShaderVariables> map;

    ResourcePath blueNoisePath = Props.getPathOrDefault(
            "blue-noise", () ->
                path(GpuExtendedPlugin.class, "textures/noise/blue_noise_rgba.png")
        );

    @Getter
    private Texture2D blueNoiseTexture;

    public void InitializeResourceTextures() {
        Texture2D.TextureSettings settings = new Texture2D.TextureSettings() {{
            level = 0;
            internalFormat = GL_RGBA;
            border = 0;
            format = GL_RGBA;
            type = GL_UNSIGNED_BYTE;
            pixels = 0;
            minFilter = GL_NEAREST;
            magFilter = GL_NEAREST;
            wrapS = GL_REPEAT;
            wrapT = GL_REPEAT;
        }};

        blueNoiseTexture = Texture2D.loadFromResourcePath(blueNoisePath, settings);
    }

    public void InitializeShaderUniformsForShader(int shader, GpuExtendedPlugin.ComputeMode computeMode)
    {
        if(map == null)
        {
            map = new HashMap<>();
        }

        ShaderVariables shaderVariables = new ShaderVariables();
        shaderVariables.ShadowMap = glGetUniformLocation(shader, "shadowMap");
        shaderVariables.DynamicShadowMap = glGetUniformLocation(shader, "dynamicShadowMap");
        shaderVariables.DepthMap = glGetUniformLocation(shader, "depthMap");

        shaderVariables.TileMarkerFillColorMap = glGetUniformLocation(shader, "tileFillColorMap");
        shaderVariables.TileMarkerBorderColorMap = glGetUniformLocation(shader, "tileBorderColorMap");
        shaderVariables.TileMarkerSettingsMap = glGetUniformLocation(shader, "tileSettingsMap");
        shaderVariables.TileHeightMap = glGetUniformLocation(shader, "tileHeightMap");
        shaderVariables.BlueNoiseTexture = glGetUniformLocation(shader, "blueNoiseTexture");
        
        shaderVariables.SourceTexture = glGetUniformLocation(shader, "srcTexture");
        shaderVariables.DestinationTexture = glGetUniformLocation(shader, "dstTexture");
        shaderVariables.SourceResolution = glGetUniformLocation(shader, "srcResolution");
        shaderVariables.DestinationResolution = glGetUniformLocation(shader, "dstResolution");
        shaderVariables.MipmapLevel = glGetUniformLocation(shader, "mipMapLevel");

        shaderVariables.ColorBlindMode = glGetUniformLocation(shader, "colorBlindMode");
        shaderVariables.Textures = glGetUniformLocation(shader, "textures");
        shaderVariables.TextureAnimations = glGetUniformLocation(shader, "textureAnimations");

        shaderVariables.InterfaceTexture = glGetUniformLocation(shader, "interfaceTexture");
        shaderVariables.MainTexture = glGetUniformLocation(shader, "mainTexture");
        shaderVariables.BloomTexture = glGetUniformLocation(shader, "bloomTexture");
        shaderVariables.TexSamplingMode = glGetUniformLocation(shader, "samplingMode");
        shaderVariables.TexTargetDimensions = glGetUniformLocation(shader, "targetDimensions");
        shaderVariables.TexSourceDimensions = glGetUniformLocation(shader, "sourceDimensions");
        shaderVariables.UiColorBlindMode = glGetUniformLocation(shader, "colorBlindMode");
        shaderVariables.UiAlphaOverlay = glGetUniformLocation(shader, "alphaOverlay");

        shaderVariables.CameraBlock = glGetUniformBlockIndex(shader, "CameraBlock");
        shaderVariables.PlayerBlock = glGetUniformBlockIndex(shader, "PlayerBlock");
        shaderVariables.EnvironmentBlock = glGetUniformBlockIndex(shader, "EnvironmentBlock");
        shaderVariables.TileMarkerBlock = glGetUniformBlockIndex(shader, "TileMarkerBlock");
        shaderVariables.SystemInfoBlock = glGetUniformBlockIndex(shader, "SystemInfoBlock");
        shaderVariables.ConfigBlock = glGetUniformBlockIndex(shader, "ConfigBlock");

        if (computeMode == GpuExtendedPlugin.ComputeMode.OPENGL)
        {
            shaderVariables.BlockSmall = glGetUniformBlockIndex(shader, "CameraBlock");
            shaderVariables.BlockLarge = glGetUniformBlockIndex(shader, "CameraBlock");
        }

        map.put(shader, shaderVariables);
    }

    public void ClearUniforms()
    {
        if(map != null) {
            map.clear();
        }
    }

    public void InitializeUniformBlocks() {
        plugin.initGlBuffer(glCameraUniformBuffer);
        plugin.initGlBuffer(glPlayerUniformBuffer);
        plugin.initGlBuffer(glEnvironmentUniformBuffer);
        plugin.initGlBuffer(glTileMarkerUniformBuffer);
        plugin.initGlBuffer(glSystemInfoUniformBuffer);
        plugin.initGlBuffer(glConfigUniformBuffer);

        bBufferCameraBlock = InitBufferBlock(glCameraUniformBuffer, 128);
        bBufferPlayerBlock = InitBufferBlock(glPlayerUniformBuffer, 24);
        bBufferEnvironmentBlock = InitBufferBlock(glEnvironmentUniformBuffer, 16 + 16 + 4 + 4 + 4 + 4 + 128 + (64 * MAX_LIGHTS));
        bBufferTileMarkerBlock = InitBufferBlock(glTileMarkerUniformBuffer, 144);
        bBufferSystemInfoBlock = InitBufferBlock(glSystemInfoUniformBuffer, 24);
        bBufferConfigBlock = InitBufferBlock(glConfigUniformBuffer, 8 * Float.BYTES);

        glBindBufferBase(GL_UNIFORM_BUFFER, CAMERA_BUFFER_BINDING_ID, glCameraUniformBuffer.glBufferId);
        glBindBufferBase(GL_UNIFORM_BUFFER, PLAYER_BUFFER_BINDING_ID, glPlayerUniformBuffer.glBufferId);
        glBindBufferBase(GL_UNIFORM_BUFFER, ENVIRONMENT_BUFFER_BINDING_ID, glEnvironmentUniformBuffer.glBufferId);
        glBindBufferBase(GL_UNIFORM_BUFFER, TILEMARKER_BUFFER_BINDING_ID, glTileMarkerUniformBuffer.glBufferId);
        glBindBufferBase(GL_UNIFORM_BUFFER, SYSTEMINFO_BUFFER_BINDING_ID, glSystemInfoUniformBuffer.glBufferId);
        glBindBufferBase(GL_UNIFORM_BUFFER, CONFIG_BUFFER_BINDING_ID, glConfigUniformBuffer.glBufferId);

        glBindBuffer(GL_UNIFORM_BUFFER, 0);
    }

    public void UpdateUniformBlocks() {
        Client client = plugin.client;
        GpuExtendedConfig config = plugin.config;

        if(client.getGameState().getState() != GameState.LOGGED_IN.getState())
        {
            return;
        }

        // Calculate camera matrix
        float[] cameraProjectionMatrix = Mat4.scale(client.getScale(), client.getScale(), 1);
        Mat4.mul(cameraProjectionMatrix, Mat4.projection(client.getViewportWidth(), client.getViewportHeight(), 50));
        Mat4.mul(cameraProjectionMatrix, Mat4.rotateX((float) -(Math.PI - plugin.cameraPitch)));
        Mat4.mul(cameraProjectionMatrix, Mat4.rotateY((float) plugin.cameraYaw));
        Mat4.mul(cameraProjectionMatrix, Mat4.translate((float) -plugin.cameraX, (float) -plugin.cameraY, (float) -plugin.cameraZ));

        int playerX = client.getLocalPlayer().getLocalLocation().getX();
        int playerY = client.getLocalPlayer().getLocalLocation().getY();
        int playerPlane = client.getPlane();

        final TextureProvider textureProvider = client.getTextureProvider();
        Environment env = plugin.environmentManager.GetCurrentEnvironment();

        Bounds currentBounds = plugin.environmentManager.currentBounds;
        boolean roofFadingEnabled = currentBounds != null ? currentBounds.isAllowRoofFading() : true;

        // <editor-fold defaultstate="collapsed" desc="Populate Camera Buffer Block">
        bBufferCameraBlock.clear();

        // Fill the cameraProjectionMatrix (16 floats, 64 bytes)
        for(int i = 0; i < cameraProjectionMatrix.length; i++) {
            bBufferCameraBlock.putFloat(cameraProjectionMatrix[i]);
        }

        // Fill cameraPosition (4 floats, 16 bytes)
        bBufferCameraBlock.putFloat((float) plugin.cameraX);
        bBufferCameraBlock.putFloat((float) plugin.cameraY);
        bBufferCameraBlock.putFloat((float) plugin.cameraZ);
        bBufferCameraBlock.putFloat(0); // pad

        // Fill cameraFocalPoint (4 floats, 16 bytes)
        bBufferCameraBlock.putFloat((float) client.getCameraFpX());
        bBufferCameraBlock.putFloat((float) client.getCameraFpY());
        bBufferCameraBlock.putFloat((float) client.getCameraFpZ());
        bBufferCameraBlock.putFloat(0); // pad

        // Fill cameraPitch (4 bytes), cameraYaw (4 bytes), zoom (4 bytes), centerX (4 bytes), centerY (4 bytes)
        // According to std140 layout rules, each of these must be 4 bytes aligned
        bBufferCameraBlock.putFloat((float) plugin.cameraPitch);
        bBufferCameraBlock.putFloat((float) plugin.cameraYaw);
        bBufferCameraBlock.putInt(client.getScale());
        bBufferCameraBlock.putInt(client.getCenterX());
        bBufferCameraBlock.putInt(client.getCenterY());

        bBufferCameraBlock.flip();

        glBindBuffer(GL_UNIFORM_BUFFER, glCameraUniformBuffer.glBufferId);
        glBufferData(GL_UNIFORM_BUFFER, glCameraUniformBuffer.size, GL_DYNAMIC_DRAW);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, bBufferCameraBlock);
        // </editor-fold>

        // <editor-fold defaultstate="collapsed" desc="Populate Player Buffer Block">
        bBufferPlayerBlock.clear();
        bBufferPlayerBlock.putFloat((float) playerX);
        bBufferPlayerBlock.putFloat((float) playerY);
        bBufferPlayerBlock.putFloat((float) playerPlane);
        bBufferPlayerBlock.putFloat(0); // pad

        bBufferPlayerBlock.putInt(client.getScene().getBaseX());
        bBufferPlayerBlock.putInt(client.getScene().getBaseY());
        bBufferPlayerBlock.flip();

        glBindBuffer(GL_UNIFORM_BUFFER, glPlayerUniformBuffer.glBufferId);
        glBufferData(GL_UNIFORM_BUFFER, glPlayerUniformBuffer.size, GL_DYNAMIC_DRAW);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, bBufferPlayerBlock);
        // </editor-fold>

        // <editor-fold defaultstate="collapsed" desc="Populate Environment Buffer Block">
        bBufferEnvironmentBlock.clear();

        // Ambient Color
        bBufferEnvironmentBlock.putFloat(plugin.environmentManager.ambientColor.getRed() / 255f);
        bBufferEnvironmentBlock.putFloat(plugin.environmentManager.ambientColor.getGreen() / 255f);
        bBufferEnvironmentBlock.putFloat(plugin.environmentManager.ambientColor.getBlue() / 255f);
        bBufferEnvironmentBlock.putFloat(0);

        // Sky Color
        bBufferEnvironmentBlock.putFloat(plugin.environmentManager.skyColor.getRed() / 255f);
        bBufferEnvironmentBlock.putFloat(plugin.environmentManager.skyColor.getGreen() / 255f);
        bBufferEnvironmentBlock.putFloat(plugin.environmentManager.skyColor.getBlue() / 255f);
        bBufferEnvironmentBlock.putFloat(0);

        // Fog
        bBufferEnvironmentBlock.putInt(env.Type); // Pad
        bBufferEnvironmentBlock.putFloat(env.FogDepth);
        bBufferEnvironmentBlock.putInt(0);
        bBufferEnvironmentBlock.putInt(0);


        // Pack Main Light

        Light mainLight = plugin.environmentManager.mainLight;
        // Pos
        bBufferEnvironmentBlock.putFloat(mainLight.viewMatrix[2]);
        bBufferEnvironmentBlock.putFloat(-mainLight.viewMatrix[6]);
        bBufferEnvironmentBlock.putFloat(mainLight.viewMatrix[10]);
        bBufferEnvironmentBlock.putFloat(client.getPlane()); // light type / directional

        // Offset
        bBufferEnvironmentBlock.putFloat(0);
        bBufferEnvironmentBlock.putFloat(0);
        bBufferEnvironmentBlock.putFloat(0);
        bBufferEnvironmentBlock.putFloat(0); // pad

        // Color
        bBufferEnvironmentBlock.putFloat(mainLight.color.getRed() / 255f);
        bBufferEnvironmentBlock.putFloat(mainLight.color.getGreen() / 255f);
        bBufferEnvironmentBlock.putFloat(mainLight.color.getBlue() / 255f);
        bBufferEnvironmentBlock.putFloat(0); // pad

        bBufferEnvironmentBlock.putFloat(0); // light intensity
        bBufferEnvironmentBlock.putFloat(0); // light radius
        bBufferEnvironmentBlock.putInt(0); // light animation
        bBufferEnvironmentBlock.putFloat(0); // pad

        for(int i = 0; i < mainLight.projectionMatrix.length; i++)
        {
            bBufferEnvironmentBlock.putFloat(mainLight.projectionMatrix[i]);
        }

        // Pack Lights
        //environmentManager.DetermineRenderedLights();
        for(int i = 0; i < MAX_LIGHTS; i++)
        {
            // TODO:: check visibility of light from frustum.
            Light light = plugin.environmentManager.GetLightAtIndex(i);
            if(light != null)
            {
                bBufferEnvironmentBlock.putFloat(light.position.x);
                bBufferEnvironmentBlock.putFloat(light.position.y);
                bBufferEnvironmentBlock.putFloat(light.position.z);
                bBufferEnvironmentBlock.putFloat(light.plane);

                bBufferEnvironmentBlock.putFloat(light.offset.x);
                bBufferEnvironmentBlock.putFloat(light.offset.y);
                bBufferEnvironmentBlock.putFloat(light.offset.z);
                bBufferEnvironmentBlock.putFloat(light.hash);

                bBufferEnvironmentBlock.putFloat(light.color.getRed() / 255f);
                bBufferEnvironmentBlock.putFloat(light.color.getGreen() / 255f);
                bBufferEnvironmentBlock.putFloat(light.color.getBlue() / 255f);
                bBufferEnvironmentBlock.putFloat(0);

                bBufferEnvironmentBlock.putFloat(light.intensity);
                bBufferEnvironmentBlock.putFloat(light.radius);
                bBufferEnvironmentBlock.putInt(light.animation.ordinal());
                bBufferEnvironmentBlock.putInt(light.type.ordinal());
            }
        }

        bBufferEnvironmentBlock.flip();

        glBindBuffer(GL_UNIFORM_BUFFER, glEnvironmentUniformBuffer.glBufferId);
        glClearBufferData(GL_UNIFORM_BUFFER, GL_R32I, GL_RED_INTEGER, GL_INT, new int[]{0});
        glBufferData(GL_UNIFORM_BUFFER, glEnvironmentUniformBuffer.size, GL_DYNAMIC_DRAW);
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

        if(plugin.config.trueTileFadeOut())
        {
            if( client.getLocalPlayer().getLocalLocation().getX() == plugin.lastPlayerPosition[0] &&
                    client.getLocalPlayer().getLocalLocation().getY() == plugin.lastPlayerPosition[1])
            {
                plugin.currentTrueTileAlpha = Math.max(0, plugin.currentTrueTileAlpha - (float)plugin.DeltaTime / plugin.config.trueTileFadeOutTime());
            }
            else
            {
                plugin.currentTrueTileAlpha = 1;
            }
        }
        else
        {
            plugin.currentTrueTileAlpha = 1;
        }

        bBufferTileMarkerBlock.clear();
        bBufferTileMarkerBlock.putFloat(currentTileX);
        bBufferTileMarkerBlock.putFloat(currentTileY);
        bBufferTileMarkerBlock.putFloat(config.trueTileBorderWidth());
        bBufferTileMarkerBlock.putFloat(config.trueTileCornerLength());

        bBufferTileMarkerBlock.putFloat(config.highlightTrueTile() ? config.trueTileFillColor().getRed() / 255f : 0);
        bBufferTileMarkerBlock.putFloat(config.highlightTrueTile() ? config.trueTileFillColor().getGreen() / 255f : 0);
        bBufferTileMarkerBlock.putFloat(config.highlightTrueTile() ? config.trueTileFillColor().getBlue() / 255f : 0);
        bBufferTileMarkerBlock.putFloat(config.highlightTrueTile() ? (config.trueTileFillColor().getAlpha() / 255f) * plugin.currentTrueTileAlpha : 0);

        bBufferTileMarkerBlock.putFloat(config.highlightTrueTile() ? config.trueTileBorderColor().getRed() / 255f : 0);
        bBufferTileMarkerBlock.putFloat(config.highlightTrueTile() ? config.trueTileBorderColor().getGreen() / 255f : 0);
        bBufferTileMarkerBlock.putFloat(config.highlightTrueTile() ? config.trueTileBorderColor().getBlue() / 255f : 0);
        bBufferTileMarkerBlock.putFloat(config.highlightTrueTile() ? (config.trueTileBorderColor().getAlpha() / 255f) * plugin.currentTrueTileAlpha : 0);

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
        glBufferData(GL_UNIFORM_BUFFER, glTileMarkerUniformBuffer.size, GL_DYNAMIC_DRAW);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, bBufferTileMarkerBlock);
        // </editor-fold>

        // <editor-fold defaultstate="collapsed" desc="Populate System Info Block">
        bBufferSystemInfoBlock.clear();

        bBufferSystemInfoBlock.putInt(client.getGameState() == GameState.LOGGED_IN ? (client.getGameCycle() & 127) : 0);
        bBufferSystemInfoBlock.putInt(plugin.currentViewport[2]);
        bBufferSystemInfoBlock.putInt(plugin.currentViewport[3]);
        bBufferSystemInfoBlock.putFloat(plugin.DeltaTime);
        bBufferSystemInfoBlock.putFloat(plugin.Time);

        bBufferSystemInfoBlock.flip();

        glBindBuffer(GL_UNIFORM_BUFFER, glSystemInfoUniformBuffer.glBufferId);
        glBufferData(GL_UNIFORM_BUFFER, glSystemInfoUniformBuffer.size, GL_DYNAMIC_DRAW);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, bBufferSystemInfoBlock);
        // </editor-fold>

        // <editor-fold defaultstate="collapsed" desc="Populate Config Block">
        bBufferConfigBlock.clear();

        bBufferConfigBlock.putFloat((float) textureProvider.getBrightness());
        bBufferConfigBlock.putFloat(config.smoothBanding() ? 0 : 1);
        bBufferConfigBlock.putInt(config.expandedMapLoadingChunks());
        bBufferConfigBlock.putInt(plugin.getDrawDistance());
        bBufferConfigBlock.putInt(config.colorBlindMode().ordinal());
        bBufferConfigBlock.putInt(config.shadowMode().getValue());
        bBufferConfigBlock.putInt(config.shadowDistance());
        bBufferConfigBlock.put((byte) (config.showWireframe() ? 1 : 0));
        bBufferConfigBlock.put((byte) (0));
        bBufferConfigBlock.put((byte) (0));
        bBufferConfigBlock.put((byte) (0));

        bBufferConfigBlock.flip();

        glBindBuffer(GL_UNIFORM_BUFFER, glConfigUniformBuffer.glBufferId);
        glBufferData(GL_UNIFORM_BUFFER, glConfigUniformBuffer.size, GL_DYNAMIC_DRAW);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, bBufferConfigBlock);
        // </editor-fold>

        int[] lightClearValue = new int[]{-1};
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, plugin.lightBinsBuffer.glBufferId);
        glClearBufferData(GL_SHADER_STORAGE_BUFFER, GL_R32I, GL_RED_INTEGER, GL_INT, lightClearValue);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, plugin.lightBinsBuffer.glBufferId);

        glUseProgram(plugin.shaders.lightBinningComputeShader.id());
        ShaderVariables shaderVars = GetUniforms(plugin.shaders.lightBinningComputeShader.id());
        glUniformBlockBinding(plugin.shaders.lightBinningComputeShader.id(), shaderVars.EnvironmentBlock, ENVIRONMENT_BUFFER_BINDING_ID);

        glDispatchCompute(EXTENDED_SCENE_SIZE / 8, EXTENDED_SCENE_SIZE / 8, MAX_Z);
        glUseProgram(0);
    }

    private ByteBuffer InitBufferBlock(GLBuffer glBuffer, int blockSizeBytes)
    {
        ByteBuffer byteBuffer = BufferUtils.createByteBuffer(blockSizeBytes);
        plugin.updateBuffer(glBuffer, GL_UNIFORM_BUFFER, blockSizeBytes, GL_DYNAMIC_DRAW);
        return byteBuffer;
    }

    public void Dispose() {
        plugin.destroyGlBuffer(glCameraUniformBuffer);
        plugin.destroyGlBuffer(glPlayerUniformBuffer);
        plugin.destroyGlBuffer(glEnvironmentUniformBuffer);
        plugin.destroyGlBuffer(glTileMarkerUniformBuffer);
        plugin.destroyGlBuffer(glSystemInfoUniformBuffer);
        plugin.destroyGlBuffer(glConfigUniformBuffer);

        bBufferCameraBlock = null;
        bBufferPlayerBlock = null;
        bBufferEnvironmentBlock = null;
        bBufferTileMarkerBlock = null;
        bBufferSystemInfoBlock = null;
        bBufferConfigBlock = null;
    }

    public ShaderVariables GetUniforms(int shader)
    {
        if(map == null)
        {
            return null;
        }

        if(!map.containsKey(shader))
        {
            log.info("Shader uniforms not found");
            return null;
        }

        return map.get(shader);
    }
}
