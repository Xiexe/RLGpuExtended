package com.gpuExtended.scene;

import com.gpuExtended.rendering.*;

public class Light
{
    public enum LightType
    {
        Directional,
        Point,
        Spot
    }

    public Color color;
    public Vector4 position;
    public Vector4 direction;
    public float intensity;
    public float attenuation;
    public LightType type;
    public String guid;
}
