package com.gpuExtended.scene;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.gpuExtended.rendering.*;
import org.apache.commons.lang3.NotImplementedException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class Environment
{
    public ArrayList<Light> directionalLights = new ArrayList<>();
    public ArrayList<Light> pointLights = new ArrayList<>();
    public ArrayList<Light> spotLights = new ArrayList<>();
    public Color ambientColor;
    public Color fogColor;
    public int drawDistance = -1;


    public void ReloadLights() throws IOException {
        pointLights.clear();
        spotLights.clear();

        Gson gson = new Gson();
        Type mapType = new TypeToken<HashMap<String, Light>>(){}.getType();
        InputStream inputStream = this.getClass().getResourceAsStream("/environment/lights.json");
        try (InputStreamReader reader = new InputStreamReader(inputStream))
        {
            HashMap<String, Light> lightDefinitions = gson.fromJson(reader, mapType);

            for (Light light : lightDefinitions.values()) {
                switch (light.type)
                {
                    case Directional:
                        directionalLights.add(light);
                        break;

                    case Point:
                        pointLights.add(light);
                        break;

                    case Spot:
                        spotLights.add(light);
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void AddDirectionalLight(Vector4 direction, Color color, float intensity)
    {
        if(directionalLights == null)
            directionalLights = new ArrayList<Light>();

        Light light = new Light();
        light.type = Light.LightType.Directional;
        light.direction = direction;
        light.color = color;
        light.intensity = intensity;

        directionalLights.add(light);
    }

    public void AddPointLight(Vector4 position, Color color, float intensity)
    {
        if(pointLights == null)
            pointLights = new ArrayList<Light>();

        Light light = new Light();
        light.type = Light.LightType.Point;
        light.position = position;
        light.color = color;
        light.intensity = intensity;

        pointLights.add(light);
    }

    public void AddSpotLight(Vector4 position, Vector4 direction, Color color, float intensity)
    {
        if(spotLights == null)
            spotLights = new ArrayList<Light>();

        Light light = new Light();
        light.type = Light.LightType.Spot;
        light.position = position;
        light.direction = direction;
        light.color = color;
        light.intensity = intensity;

        spotLights.add(light);
    }

    public void RemoveLight()
    {
        throw new NotImplementedException("You have not implemented this yet dummy.");
    }

    public void ClearAllLights()
    {
        directionalLights.clear();
        pointLights.clear();
        spotLights.clear();
    }
}

