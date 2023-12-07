package com.gpuExtended.scene;

import com.gpuExtended.rendering.*;
import org.apache.commons.lang3.NotImplementedException;

import java.util.ArrayList;

public class Environment
{
    public ArrayList<Light> directionalLights;
    public ArrayList<Light> pointLights;
    public ArrayList<Light> spotLights;
    public Color ambientColor;
    public Color fogColor;
    public int drawDistance = -1;

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