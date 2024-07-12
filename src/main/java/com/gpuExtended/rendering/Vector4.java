package com.gpuExtended.rendering;

public class Vector4 extends Vector3
{
    public float x = 0;
    public float y = 0;
    public float z = 0;
    public float w = 0;

    public Vector4(float x, float y, float z, float w)
    {
        super(x, y, z);
        this.w = w;
    }

    public static Vector4 Cross(Vector4 a, Vector4 b) {
        return new Vector4(
            a.y * b.z - a.z * b.y,
            a.z * b.x - a.x * b.z,
            a.x * b.y - a.y * b.x,
            1.0f
        );
    }

    public Vector4 Blend(Vector4 other) {
        return new Vector4(
            (this.x + other.x) / 2,
            (this.y + other.y) / 2,
            (this.z + other.z) / 2,
                this.w
        );
    }

    public Vector4 Normalize()
    {
        float length = (float) Math.sqrt(x*x + y*y + z*z);
        return new Vector4(x / length, y / length, z / length, this.w);
    }

}
