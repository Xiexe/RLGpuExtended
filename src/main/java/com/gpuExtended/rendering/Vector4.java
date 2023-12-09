package com.gpuExtended.rendering;

public class Vector4
{
    public double x = 0;
    public double y = 0;
    public double z = 0;
    public double w = 0;

    public Vector4(double x, double y, double z, double w)
    {
        this.x = x;
        this.y = y;
        this.z = z;
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
}
