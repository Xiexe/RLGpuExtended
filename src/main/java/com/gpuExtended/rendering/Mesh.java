package com.gpuExtended.rendering;

import com.gpuExtended.util.GpuFloatBuffer;
import com.gpuExtended.util.GpuIntBuffer;

import java.util.ArrayList;
import java.util.List;

public class Mesh
{
    public List<Vertex> vertices = new ArrayList<>();
    public List<Triangle> triangles = new ArrayList<>();

    public void AddTriangle(Triangle t)
    {
        t.index = this.triangles.size() + 1;
        triangles.add(t);
    }

    public void Clear()
    {
        vertices.clear();
        triangles.clear();
    }

    public void PushToBuffers(GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer)
    {
        vertexBuffer.ensureCapacity(this.triangles.size() * 12);
        normalBuffer.ensureCapacity(this.triangles.size() * 12);
        uvBuffer.ensureCapacity(this.triangles.size() * 12);
        for (Triangle triangle : this.triangles)
        {
            // Push Vertex Positions
            vertexBuffer.put((int)triangle.v0.localPosition.x, (int)triangle.v0.localPosition.y, (int)triangle.v0.localPosition.z, triangle.v0.color);
            vertexBuffer.put((int)triangle.v1.localPosition.x, (int)triangle.v1.localPosition.y, (int)triangle.v1.localPosition.z, triangle.v1.color);
            vertexBuffer.put((int)triangle.v2.localPosition.x, (int)triangle.v2.localPosition.y, (int)triangle.v2.localPosition.z, triangle.v2.color);

            // Push Normal Directions
            normalBuffer.put((float)triangle.v0.normal.x, (float)triangle.v0.normal.y, (float)triangle.v0.normal.z, (float)triangle.v0.normal.w);
            normalBuffer.put((float)triangle.v1.normal.x, (float)triangle.v1.normal.y, (float)triangle.v1.normal.z, (float)triangle.v1.normal.w);
            normalBuffer.put((float)triangle.v2.normal.x, (float)triangle.v2.normal.y, (float)triangle.v2.normal.z, (float)triangle.v2.normal.w);

            // Push UV coords
            uvBuffer.put((float) triangle.v0.uv.x, (float) triangle.v0.uv.y, (float) triangle.v0.uv.z, (float) triangle.v0.uv.w);
            uvBuffer.put((float) triangle.v1.uv.x, (float) triangle.v1.uv.y, (float) triangle.v1.uv.z, (float) triangle.v1.uv.w);
            uvBuffer.put((float) triangle.v2.uv.x, (float) triangle.v2.uv.y, (float) triangle.v2.uv.z, (float) triangle.v2.uv.w);
        }
    }
}

