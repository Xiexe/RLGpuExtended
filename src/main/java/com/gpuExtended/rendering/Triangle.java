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

    public Vector3 CalculateNormal()
    {
        Vector3 edge1 = new Vector3(v1.position.x - v0.position.x, v1.position.y - v0.position.y, v1.position.z - v0.position.z);
        Vector3 edge2 = new Vector3(v2.position.x - v0.position.x, v2.position.y - v0.position.y, v2.position.z - v0.position.z);
        Vector3 norm = Vector3.Cross(edge1, edge2).Normalize();

        SetNormal(new Vector3(norm.x, norm.y, norm.z));
        return normal;
    }

    // TODO:: Use clone to make vertex normals..?
    public void SetNormal(Vector3 normal)
    {
        this.normal    = new Vector3(normal.x, normal.y, normal.z);
        this.v0.normal = new Vector3(normal.x, normal.y, normal.z);
        this.v1.normal = new Vector3(normal.x, normal.y, normal.z);
        this.v2.normal = new Vector3(normal.x, normal.y, normal.z);
    }
}
