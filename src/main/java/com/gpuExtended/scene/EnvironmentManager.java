package com.gpuExtended.scene;

import com.google.gson.Gson;
import com.gpuExtended.GpuExtendedConfig;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.opengl.GLBuffer;
import com.gpuExtended.rendering.*;
import com.gpuExtended.util.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;

import static com.gpuExtended.util.ResourcePath.path;
import static net.runelite.api.Constants.SCENE_SIZE;
import static net.runelite.api.Perspective.LOCAL_TILE_SIZE;

@Singleton
@Slf4j
public class EnvironmentManager
{
    private static final ResourcePath ENVIRONMENT_PATH = Props.getPathOrDefault(
            "environments-path", () -> path(GpuExtendedPlugin.class, "environment/environments.json"));

    public enum EnvironmentType {
        DEFAULT(0),
        UNDERGROUND(1);

        private final int value;

        EnvironmentType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static EnvironmentType fromValue(int value) {
            for (EnvironmentType type : values()) {
                if (type.getValue() == value) {
                    return type;
                }
            }
            return null;
        }
    }

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private GpuExtendedPlugin plugin;

    @Inject
    private GpuExtendedConfig config;


    public int drawDistance = -1;

    public HashMap<int[], Light> tileLights = new HashMap<>();
    public HashMap<Integer, Light> decorationLights = new HashMap<>();
    public HashMap<Integer, Light> gameObjectLights = new HashMap<>();
    public HashMap<Integer, Light> projectileLights = new HashMap<>();
    public ArrayList<Light> renderedLights = new ArrayList<>();

    public Environment[] environments;
    public HashMap<String, Environment> environmentMap = new HashMap<>();
    public Environment currentEnvironment;

    public float[] lightProjectionMatrix;
    public float[] lightViewMatrix;

    final int[] lightPitches = new int[] { 45, 60, 75, 90, 105, 120, 135 };
    final int[] lightYaws = new int[] { 30, 60, 90, 120, 150, 180, 210 };
    int dayNightCycleIndex = 0;
    float interpolationSpeed = 0.01f; // Adjust this for smoother/faster transitions
    float interpolationProgress = 0.0f;

    public void Initialize() {
        environments = new Environment[0];

        ENVIRONMENT_PATH.watch("\\.(json)$", path -> {
            LoadEnvironments();
        });
    }

    public void LoadEnvironments()
    {
        try {
            log.info("Fetching new environment information: " + ENVIRONMENT_PATH.resolve().toAbsolute());
            environments = ENVIRONMENT_PATH.loadJson(plugin.getGson(), Environment[].class);

            environmentMap.clear();
            for(int i = 0; i < environments.length; i++) {
                environmentMap.put(environments[i].Name, environments[i]);

                log.info("loaded environment: " + environments[i]);
            }
            log.info("Loaded " + environments.length + " environments");

        } catch (Exception e) {
            log.error("Failed to load environment: " + ENVIRONMENT_PATH, e);
        }
    }

    private float interpolate(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    public void UpdateEnvironment(float deltaTime)
    {
        if(currentEnvironment == null) {
            currentEnvironment = GetDefaultEnvironment();
        }

        Player player = client.getLocalPlayer();
        if (player != null)
        {
            boolean isInOverworld = WorldPoint.getMirrorPoint(player.getWorldLocation(), true).getY() < Constants.OVERWORLD_MAX_Y;
            Environment targetEnvironment = isInOverworld ? GetDefaultEnvironment() : GetDefaultUndergroundEnvironment();
            currentEnvironment = targetEnvironment;
        }

        UpdateMainLightProjectionMatrix();
    }

    public void UpdateMainLightProjectionMatrix()
    {
        // Calculate light matrix
        boolean overrideLightDirection = config.customLightRotation();
        int customLightPitch = config.lightPitch();
        int customLightYaw = config.lightYaw();

        float lightPitch = (float) Math.toRadians(overrideLightDirection ? customLightPitch : currentEnvironment.LightPitch);
        float lightYaw = (float) Math.toRadians(overrideLightDirection ? customLightYaw : currentEnvironment.LightYaw);

        lightProjectionMatrix = Mat4.identity();
        lightViewMatrix = Mat4.rotateX((float) Math.PI + lightPitch);
        Mat4.mul(lightViewMatrix, Mat4.rotateY((float) Math.PI + lightYaw));

        int camX = (int) client.getCameraFpX();
        int camY = (int) client.getCameraFpY();

        int shadowDrawDistance = 90;
        int drawDistanceSceneUnits = shadowDrawDistance * LOCAL_TILE_SIZE / 2;
        int east = Math.min(camX + drawDistanceSceneUnits, LOCAL_TILE_SIZE * SCENE_SIZE);
        int west = Math.max(camX - drawDistanceSceneUnits, 0);
        int north = Math.min(camY + drawDistanceSceneUnits, LOCAL_TILE_SIZE * SCENE_SIZE);
        int south = Math.max(camY - drawDistanceSceneUnits, 0);
        int width = east - west;
        int height = north - south;
        int farPlane = 10000;

        int maxDrawDistance = 100;
        float maxScale = 0.7f;
        float minScale = 0.4f;
        float scaleMultiplier = 1.0f - (shadowDrawDistance / (maxDrawDistance * maxScale));
        float scale = Mathmatics.lerp(maxScale, minScale, scaleMultiplier);
        Mat4.mul(lightProjectionMatrix, Mat4.scale(scale, scale, scale));
        Mat4.mul(lightProjectionMatrix, Mat4.ortho(width, height, farPlane));
        Mat4.mul(lightProjectionMatrix, lightViewMatrix);
        Mat4.mul(lightProjectionMatrix, Mat4.translate(-(width / 2f + west), 0, -(height / 2f + south)));
    }

    private Environment GetDefaultEnvironment() {
        try {
            return environmentMap.get("DEFAULT");
        } catch (Exception e) {
            log.error("Failed to get default environment", e);
            return null;
        }
    }

    private Environment GetDefaultUndergroundEnvironment() {
        try {
            return environmentMap.get("DEFAULT_UNDERGROUND");
        } catch (Exception e) {
            log.error("Failed to get default underground environment", e);
            return null;
        }
    }
}

