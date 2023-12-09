package com.gpuExtended.rendering;

public class Triangle
{
    Vertex v0, v1, v2;

    public Triangle(Vertex v0, Vertex v1, Vertex v2)
    {
        this.v0 = v0;
        this.v1 = v1;
        this.v2 = v2;
    }

    public Vertex Cross(Vertex v1, Vertex v2) {
        return new Vertex(
                v1.y * v2.z - v1.z * v2.y,
                v1.z * v2.x - v1.x * v2.z,
                v1.x * v2.y - v1.y * v2.x
        );
    }

    public Vector3 GetNormal()
    {
        Vertex edge1 = this.v1.Subtract(v0);
        Vertex edge2 = this.v2.Subtract(v0);
        Vertex normal = Cross(edge1, edge2);

        return new Vector3(normal.x, normal.y, normal.z).Normalize();
    }
}
