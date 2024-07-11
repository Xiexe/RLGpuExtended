package com.gpuExtended.scene;

import com.google.gson.Gson;
import com.gpuExtended.GpuExtendedConfig;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.opengl.GLBuffer;
import com.gpuExtended.rendering.*;
import com.gpuExtended.util.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;

import static com.gpuExtended.util.ResourcePath.path;
import static net.runelite.api.Constants.SCENE_SIZE;
import static net.runelite.api.Perspective.LOCAL_TILE_SIZE;

@Singleton
@Slf4j
public class EnvironmentManager
{
    private static final ResourcePath ENVIRONMENT_PATH = Props.getPathOrDefault("environments-path", () -> path("/environment/"));

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
    public Environment currentEnvironment;

    public float[] lightProjectionMatrix;
    public float[] lightViewMatrix;

    final int[] lightPitches = new int[] { 45, 60, 75, 90, 105, 120, 135 };
    final int[] lightYaws = new int[] { 30, 60, 90, 120, 150, 180, 210 };
    int dayNightCycleIndex = 0;
    float interpolationSpeed = 0.01f; // Adjust this for smoother/faster transitions
    float interpolationProgress = 0.0f;

    public void Initialize() {
        /*
        ENVIRONMENT_PATH.watch("\\.(json)$", path -> {
            log.info("Environment was updated.");
            try{
                environments = path.loadJson(plugin.getGson(), com.gpuExtended.scene.Environment[].class);
            }
            catch (Exception e) {
                log.error("Failed to load environment: " + path, e);
            }
        });
         */

        currentEnvironment = Environment.GetDefaultEnvironment();
        //ReloadLights();
    }

    public void ReloadLights() {
//        lightBuffer.clear();
//        lightCount = 0;

        /*
        HashMap<String, Light> lightDefinitions = new Gson().fromJson(reader, mapType);
        for (Light light : lightDefinitions.values()) {
            for (int[] tileLocation : light.tiles) {
                tileLights.put(tileLocation, light);
            }

            for (int decorationId : light.decorations) {
                decorationLights.put(decorationId, light);
            }

            for (int gameObjectId : light.gameObjects) {
                gameObjectLights.put(gameObjectId, light);
            }

            for (int projectileId : light.projectiles) {
                projectileLights.put(projectileId, light);
            }
        }
         */
    }

    public void PushLightToBuffer(Light light, int xPos, int yPos, int zPos, int plane, int config)
    {
//        lightBuffer.ensureCapacity(12);
//        lightBuffer.put(xPos, yPos, zPos, plane);
//        lightBuffer.put(light.color.getRed(), light.color.getGreen(), light.color.getBlue(), config);
//        lightBuffer.put(light.intensity, light.radius + 1, light.type.ordinal(), light.animation.ordinal());
    }

    public void UpdateLightBuffer()
    {
//        lightBuffer.flip();
    }

    public void ClearLightBuffer()
    {
//        lightBuffer.clear();
    }

    private float interpolate(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    public void UpdateEnvironment(float deltaTime)
    {
        if(currentEnvironment == null) {
            currentEnvironment = Environment.GetDefaultEnvironment();
        }


//        int nextIndex = (dayNightCycleIndex + 1) % lightPitches.length;
//
//        if(nextIndex == 0)
//        {
//            currentEnvironment.LightDirection.x = lightPitches[dayNightCycleIndex];
//            currentEnvironment.LightDirection.y = lightYaws[dayNightCycleIndex];
//            dayNightCycleIndex = nextIndex;
//        }
//        else {
//            // Interpolate between the current value and the next value
//            float interpolatedPitch = interpolate(lightPitches[dayNightCycleIndex], lightPitches[nextIndex], interpolationProgress);
//            float interpolatedYaw = interpolate(lightYaws[dayNightCycleIndex], lightYaws[nextIndex], interpolationProgress);
//
//            currentEnvironment.LightDirection.x = interpolatedPitch;
//            currentEnvironment.LightDirection.y = interpolatedYaw;
//
//            // Update the interpolation progress
//            interpolationProgress += (deltaTime / 512f);
//
//            // Once interpolation is complete, move to the next index
//            if (interpolationProgress >= 1.0f) {
//                interpolationProgress = 0.0f;
//                dayNightCycleIndex = nextIndex;
//            }
//        }

        UpdateMainLightProjectionMatrix();
    }

    public void UpdateMainLightProjectionMatrix()
    {
        // Calculate light matrix
        boolean overrideLightDirection = config.customLightRotation();
        int customLightPitch = config.lightPitch();
        int customLightYaw = config.lightYaw();

        float lightPitch = (float) Math.toRadians(overrideLightDirection ? customLightPitch : currentEnvironment.LightDirection.x);
        float lightYaw = (float) Math.toRadians(overrideLightDirection ? customLightYaw : currentEnvironment.LightDirection.y);

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
}

