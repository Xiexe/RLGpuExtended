package com.gpuExtended.rendering;

public class Triangle
{
    public Vertex v0, v1, v2;
    public Vector3 normal;
    public int index;

    public Triangle(Vertex v0, Vertex v1, Vertex v2)
    {
        this.v0 = v0;
        this.v1 = v1;
        this.v2 = v2;
    }

    public Vector3 GetNormal()
    {
        Vector3 edge1 = new Vector3(v1.localPosition.x - v0.localPosition.x, v1.localPosition.y - v0.localPosition.y, v1.localPosition.z - v0.localPosition.z);
        Vector3 edge2 = new Vector3(v2.localPosition.x - v0.localPosition.x, v2.localPosition.y - v0.localPosition.y, v2.localPosition.z - v0.localPosition.z);
        this.normal = Vector3.Cross(edge1, edge2).Normalize();
        this.v0.normal = this.normal;
        this.v1.normal = this.normal;
        this.v2.normal = this.normal;

        return normal;
    }
}
