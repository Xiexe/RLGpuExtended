package com.gpuExtended.scene;

import com.google.gson.Gson;
import com.gpuExtended.GpuExtendedConfig;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.opengl.GLBuffer;
import com.gpuExtended.rendering.*;
import com.gpuExtended.util.GpuFloatBuffer;
import com.gpuExtended.util.Props;
import com.gpuExtended.util.ResourcePath;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import java.util.HashMap;

import static com.gpuExtended.util.ResourcePath.path;

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

    public GLBuffer renderLightBuffer = new GLBuffer("render light buffer");
    public GpuFloatBuffer lightBuffer = new GpuFloatBuffer();
    public HashMap<int[], Light> tileLights = new HashMap<>();
    public HashMap<Integer, Light> decorationLights = new HashMap<>();
    public HashMap<Integer, Light> gameObjectLights = new HashMap<>();
    public HashMap<Integer, Light> projectileLights = new HashMap<>();
    public Integer lightCount = 0;

    public Environment[] environments;
    public Environment currentEnvironment;

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
        lightBuffer.clear();
        lightCount = 0;

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
        lightBuffer.ensureCapacity(12);
        lightBuffer.put(xPos, yPos, zPos, plane);
        lightBuffer.put(light.color.r, light.color.g, light.color.b, config);
        lightBuffer.put(light.intensity, light.radius + 1, light.type.ordinal(), light.animation.ordinal());
    }

    public void UpdateLightBuffer()
    {
        lightBuffer.flip();
    }

    public void ClearLightBuffer()
    {
        lightBuffer.clear();
    }
}

