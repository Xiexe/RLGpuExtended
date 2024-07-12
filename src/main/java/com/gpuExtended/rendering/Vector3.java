package com.gpuExtended.rendering;

public class Vector3 extends Vector2
{
    public float x = 0;
    public float y = 0;
    public float z = 0;

    public Vector3(float x, float y, float z) {
        super(x, y);
        this.z = z;
    }
    public static Vector3 Zero()
    {
        return new Vector3(0,0,0);
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

    public float Dot(Vector3 other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Vector3)) {
            return false;
        }
        Vector3 vector = (Vector3) obj;
        return Float.compare(vector.x, x) == 0 &&
               Float.compare(vector.y, y) == 0 &&
               Float.compare(vector.z, z) == 0;
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + Float.hashCode(x);
        result = 31 * result + Float.hashCode(y);
        result = 31 * result + Float.hashCode(z);
        return result;
    }

    @Override
    public String toString() {
        return "Vector3{" +
                       x +
                ", " + y +
                ", " + z +
                '}';
    }
}
