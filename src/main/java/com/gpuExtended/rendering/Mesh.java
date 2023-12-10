package com.gpuExtended.rendering;

import com.google.common.collect.ArrayListMultimap;
import com.gpuExtended.util.GpuFloatBuffer;
import com.gpuExtended.util.GpuIntBuffer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class Mesh
{
    public ArrayListMultimap<Vector3, Vertex> sharedVertexPositionMap = ArrayListMultimap.create();
    public List<Triangle> triangles = new ArrayList<>();
    public List<Vertex> vertices = new ArrayList<>();
    public boolean flatNormals = false; // Just use flat normals from the shader.
    public boolean useRecomputedNormals = false; // Recompute normals for this mesh because Jagex normals are bad.

    public void AddTriangle(Triangle t)
    {
        t.index = this.triangles.size() + 1;
        triangles.add(t);

        vertices.add(t.v0);
        vertices.add(t.v1);
        vertices.add(t.v2);
    }

    public void Clear()
    {
        vertices.clear();
        triangles.clear();
    }

    public void ComputePerFaceNormals()
    {
        for (Triangle t : triangles)
        {
            t.CalculateNormal();
        }
    }

    // TODO:: blend color
    public void ComputeSmoothNormals()
    {
        for (Collection<Vertex> sharedVerts : sharedVertexPositionMap.asMap().values())
        {
            if (sharedVerts.isEmpty()) continue;

            Vector3 avgNormal = new Vector3(0, 0, 0);
            for (Vertex vertex : sharedVerts)
            {
                avgNormal.x += vertex.normal.x;
                avgNormal.y += vertex.normal.y;
                avgNormal.z += vertex.normal.z;
            }

            avgNormal.x /= sharedVerts.size();
            avgNormal.y /= sharedVerts.size();
            avgNormal.z /= sharedVerts.size();

            for (Vertex vertex : sharedVerts)
            {
                vertex.SetNormal(new Vector3(avgNormal.x, avgNormal.y, avgNormal.z));
            }
        }
    }

    public void PushToBuffers(GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer)
    {
        vertexBuffer.ensureCapacity(this.triangles.size() * 12);
        normalBuffer.ensureCapacity(this.triangles.size() * 12);
        uvBuffer.ensureCapacity(this.triangles.size() * 12);
        for (Triangle triangle : this.triangles)
        {
            // Push Vertex Positions
            vertexBuffer.put((int)triangle.v0.position.x, (int)triangle.v0.position.y, (int)triangle.v0.position.z, triangle.v0.color);
            vertexBuffer.put((int)triangle.v1.position.x, (int)triangle.v1.position.y, (int)triangle.v1.position.z, triangle.v1.color);
            vertexBuffer.put((int)triangle.v2.position.x, (int)triangle.v2.position.y, (int)triangle.v2.position.z, triangle.v2.color);

            // Push Normal Directions
            normalBuffer.put((float)triangle.v0.normal.x, (float)triangle.v0.normal.y, (float)triangle.v0.normal.z, 0);
            normalBuffer.put((float)triangle.v1.normal.x, (float)triangle.v1.normal.y, (float)triangle.v1.normal.z, 0);
            normalBuffer.put((float)triangle.v2.normal.x, (float)triangle.v2.normal.y, (float)triangle.v2.normal.z, 0);

            // Push UV coords
            uvBuffer.put((float) triangle.v0.uv.x, (float) triangle.v0.uv.y, (float) triangle.v0.uv.z, (float) triangle.v0.uv.w);
            uvBuffer.put((float) triangle.v1.uv.x, (float) triangle.v1.uv.y, (float) triangle.v1.uv.z, (float) triangle.v1.uv.w);
            uvBuffer.put((float) triangle.v2.uv.x, (float) triangle.v2.uv.y, (float) triangle.v2.uv.z, (float) triangle.v2.uv.w);
        }
    }
}

