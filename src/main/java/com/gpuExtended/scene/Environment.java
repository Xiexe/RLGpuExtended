package com.gpuExtended.scene;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.opengl.GLBuffer;
import com.gpuExtended.rendering.*;
import com.gpuExtended.util.GpuFloatBuffer;
import net.runelite.api.Perspective;
import net.runelite.api.Scene;
import net.runelite.api.coords.WorldPoint;
import org.apache.commons.lang3.NotImplementedException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class Environment
{
    public Color ambientColor;
    public Color fogColor;
    public int drawDistance = -1;

    public GLBuffer renderLightBuffer = new GLBuffer("render light buffer");
    public GpuFloatBuffer lightBuffer = new GpuFloatBuffer();

    public void ReloadLights(Scene scene) throws IOException {
        lightBuffer.clear();
        Type mapType = new TypeToken<HashMap<String, Light>>(){}.getType();
        InputStream inputStream = this.getClass().getResourceAsStream("/environment/lights.json");
        try (InputStreamReader reader = new InputStreamReader(inputStream))
        {
            HashMap<String, Light> lightDefinitions = new Gson().fromJson(reader, mapType);

            // need to match memory layout so use the raw buffer
            for (Light light : lightDefinitions.values())
            {
                for (int[] tileLocation : light.tiles)
                {
                    PushLightToBufferAtLocation(light, tileLocation, scene.getBaseX(), scene.getBaseY());
                }
            }
            lightBuffer.flip();

            System.out.println("Pushed " + lightDefinitions.values().size() + " lights to gpu"); //
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void PushLightToBufferAtLocation(Light light, int[] location, int offsetX, int offsetZ)
    {
        float lightOffsetX = ((location[0] - offsetX) + 0.5f) * Perspective.LOCAL_TILE_SIZE;
        float lightOffsetZ = ((location[1] - offsetZ) + 0.5f) * Perspective.LOCAL_TILE_SIZE;

        FloatBuffer rawLightBuffer = lightBuffer.getBuffer();
        // populate position of light in buffer
        rawLightBuffer.put(lightOffsetX);
        rawLightBuffer.put(location[2]);
        rawLightBuffer.put(lightOffsetZ);
        rawLightBuffer.put(0);

        // populate color of light in buffer
        rawLightBuffer.put(light.color.r);
        rawLightBuffer.put(light.color.g);
        rawLightBuffer.put(light.color.b);
        rawLightBuffer.put(0);

        // populate intensity and radius of light in buffer
        rawLightBuffer.put(light.intensity);
        rawLightBuffer.put(light.radius);

        // populate type and animation of light in buffer
        rawLightBuffer.put(light.type.ordinal());
        rawLightBuffer.put(light.animation.ordinal());

        System.out.println("--- LIGHT BUFFER RAW ---");
        System.out.println( rawLightBuffer.get(0) + " " + rawLightBuffer.get(1)+ " "  + rawLightBuffer.get(2)+ " "  + rawLightBuffer.get(3)+ " "  +
                            rawLightBuffer.get(4) + " " + rawLightBuffer.get(5)+ " "  + rawLightBuffer.get(6)+ " "  + rawLightBuffer.get(7)+ " "  +
                            rawLightBuffer.get(8) + " " + rawLightBuffer.get(9)+ " "  + rawLightBuffer.get(10)+ " "  + rawLightBuffer.get(11));
    }
}

