package com.gpuExtended.rendering;

public class Vertex
{
    public Vector3 localPosition;
    public Vector3 worldPosition;
    public Vector3 normal;

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
}
