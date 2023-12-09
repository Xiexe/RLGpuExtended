package com.gpuExtended.rendering;

public class Vector3
{
    public float x;
    public float y;
    public float z;

    public Vector3(float x, float y, float z)
    {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3 Normalize()
    {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        return new Vector3(x / length, y / length, z / length);
    }
}
