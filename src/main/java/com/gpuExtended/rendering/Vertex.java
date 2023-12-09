package com.gpuExtended.rendering;

public class Vertex
{
    public Vector3 localPosition;
    public Vector3 worldPosition;
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
        this.localPosition = new Vector3(x, y, z);
    }

    public Vertex(float x, float y, float z)
    {
        this.localPosition = new Vector3(x, y, z);
    }

    public void ApplyWorldPosition(int x, int y, int z)
    {
        this.worldPosition = new Vector3(localPosition.x + x, localPosition.y + y,  localPosition.z + z);
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
        this.normal = normal;
    }
}
