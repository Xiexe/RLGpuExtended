package com.gpuExtended.rendering;

public class Vertex
{
    public float x, y, z;

    public Vertex(int x, int y, int z)
    {
        this.x = (float)x;
        this.y = (float)y;
        this.z = (float)z;
    }

    public Vertex(float x, float y, float z)
    {
        this.x = (float)x;
        this.y = (float)y;
        this.z = (float)z;
    }

    public Vertex Subtract(Vertex v)
    {
        return new Vertex(this.x - v.x, this.y - v.y, this.z - v.z);
    }
}
