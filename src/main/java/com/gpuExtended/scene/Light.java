package com.gpuExtended.scene;

import com.gpuExtended.rendering.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Light
{
    public enum LightType
    {
        Directional,
        Point,
        Spot
    }

    public LightType type;
    public Color color;
    public Vector4 position;
    public Vector4 direction;
    public float intensity;
    public float radius;
    public int[][] tiles;
    public int[] models;
    public String animation;
}
