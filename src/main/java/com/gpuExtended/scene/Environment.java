package com.gpuExtended.scene;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.gpuExtended.opengl.GLBuffer;
import com.gpuExtended.rendering.*;
import com.gpuExtended.util.GpuFloatBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.FloatBuffer;
import java.util.HashMap;

public class Environment
{
    public Color ambientColor;
    public Color fogColor;
    public int drawDistance = -1;

    public GLBuffer renderLightBuffer = new GLBuffer("render light buffer");
    public GpuFloatBuffer lightBuffer = new GpuFloatBuffer();
    public HashMap<int[], Light> tileLights = new HashMap<>();
    public HashMap<Integer, Light> decorationLights = new HashMap<>();
    public HashMap<Integer, Light> gameObjectLights = new HashMap<>();
    public HashMap<Integer, Light> projectileLights = new HashMap<>();
    public Integer lightCount = 0;

    public void ReloadLights() throws IOException {
        lightBuffer.clear();
        lightCount = 0;
        Type mapType = new TypeToken<HashMap<String, Light>>(){}.getType();
        InputStream inputStream = this.getClass().getResourceAsStream("/environment/lights.json");
        try (InputStreamReader reader = new InputStreamReader(inputStream))
        {
            HashMap<String, Light> lightDefinitions = new Gson().fromJson(reader, mapType);
            for (Light light : lightDefinitions.values())
            {
                for (int[] tileLocation : light.tiles)
                {
                    tileLights.put(tileLocation, light);
                }

                for (int decorationId : light.decorations)
                {
                    decorationLights.put(decorationId, light);
                }

                for (int gameObjectId : light.gameObjects)
                {
                    gameObjectLights.put(gameObjectId, light);
                }

                for (int projectileId : light.projectiles)
                {
                    projectileLights.put(projectileId, light);
                }
            }
        }
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

