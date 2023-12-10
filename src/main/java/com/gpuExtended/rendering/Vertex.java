package com.gpuExtended.rendering;

public class Vertex
{
    public Vector3 position;
    public Vector3 wPosition;
    public Vector4 normal = new Vector4(0,0,0,0);
    public Vector4 uv = new Vector4(0,0,0,0);
    public int color;

    public static Vertex GetEmptyVertex()
    {
        Vertex v = new Vertex(0, 0, 0);
        v.normal = new Vector4(0, 0, 0, 0);

        return v;
    }

    public Vertex(int x, int y, int z)
    {
        this.position = new Vector3((float)x, (float)y, (float)z);
    }

    public void SetColor(int color)
    {
        this.color = color;
    }

    public void SetUv(int x, int y, int z, int w)
    {
        this.uv = new Vector4(x, y, z, w);
    }

    public void SetNormal(Vector4 normal)
    {
        this.normal = new Vector4(normal.x, normal.y, normal.z, normal.w);
    }

    public void Blend(Vertex other)
    {
        this.color = (this.color + other.color) / 2;
        this.normal = normal.Blend(other.normal);
    }

    boolean IsSamePosition(Vertex other, double tolerance)
    {
        return Math.abs(this.position.x - other.position.x) < tolerance &&
               Math.abs(this.position.y - other.position.y) < tolerance &&
               Math.abs(this.position.z - other.position.z) < tolerance;
    }
}
