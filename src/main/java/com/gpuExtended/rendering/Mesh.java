package com.gpuExtended.rendering;

import java.util.ArrayList;
import java.util.List;

public class Mesh
{
    public List<Vertex> vertices = new ArrayList<>();
    public List<Triangle> triangles = new ArrayList<>();

    public void addVertex(Vertex v)
    {
        vertices.add(v);
    }

    public void addTriangle(Triangle t)
    {
        t.index = this.triangles.size() + 1;
        triangles.add(t);
    }

    public void addTriangle(Vertex v0, Vertex v1, Vertex v2)
    {
        Triangle t = new Triangle(v0, v1, v2);
        t.GetNormal();
        addVertex(t.v0);
        addVertex(t.v1);
        addVertex(t.v2);
        addTriangle(t);
    }
}

