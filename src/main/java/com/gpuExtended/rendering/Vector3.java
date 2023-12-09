package com.gpuExtended.rendering;

public class Vector3
{
    public float x = 0;
    public float y = 0;
    public float z = 0;

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

    public static Vector3 Cross(Vector3 v1, Vector3 v2) {
        return new Vector3(
            v1.y * v2.z - v1.z * v2.y,
            v1.z * v2.x - v1.x * v2.z,
            v1.x * v2.y - v1.y * v2.x
        );
    }

    public static Vector3 Add(Vector3 v1, Vector3 v2) {
        return new Vector3(
                v1.x - v2.x,
                v1.y - v2.y,
                v1.z - v2.z
        );
    }

    public static Vector3 Subtract(Vector3 v1, Vector3 v2) {
        return new Vector3(
            v1.x - v2.x,
            v1.y - v2.y,
            v1.z - v2.z
        );
    }

    public static Vector3 Blend(Vector3 v1, Vector3 v2) {
        return new Vector3(
            (v1.x + v2.x) / 2,
            (v1.y + v2.y) / 2,
            (v1.z + v2.z) / 2
        );
    }
}
