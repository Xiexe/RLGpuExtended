package com.gpuExtended.rendering;

import java.util.ArrayList;
import java.util.List;

public class Triangle
{
    public Vertex v0, v1, v2;
    public Vector4 normal;
    public int index;

    public Triangle(Vertex v0, Vertex v1, Vertex v2)
    {
        this.v0 = v0;
        this.v1 = v1;
        this.v2 = v2;
    }

    public Vector4 GetNormal()
    {
        Vector3 edge1 = new Vector3(v1.localPosition.x - v0.localPosition.x, v1.localPosition.y - v0.localPosition.y, v1.localPosition.z - v0.localPosition.z);
        Vector3 edge2 = new Vector3(v2.localPosition.x - v0.localPosition.x, v2.localPosition.y - v0.localPosition.y, v2.localPosition.z - v0.localPosition.z);
        Vector3 norm = Vector3.Cross(edge1, edge2).Normalize();

        SetNormal(new Vector4(norm.x, norm.y, norm.z, 0));
        return normal;
    }

    public void SetNormal(Vector4 normal)
    {
        this.normal = normal;
        this.v0.normal = normal;
        this.v1.normal = normal;
        this.v2.normal = normal;
    }
}
