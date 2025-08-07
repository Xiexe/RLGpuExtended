package com.gpuExtended.rendering;

public class Vector2
{
    public float x;
    public float y;

    public Vector2(float x, float y)
    {
        this.x = x;
        this.y = y;
    }

    public Vector2 Normalize()
    {
        float length = (float) Math.sqrt(x * x + y * y);
        return new Vector2(x / length, y / length);
    }
}
