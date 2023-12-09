package com.gpuExtended.rendering;

import com.gpuExtended.util.GpuFloatBuffer;
import com.gpuExtended.util.GpuIntBuffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public void ComputeSmoothNormals(double tolerance)
    {
        Map<Vertex, List<Vector4>> normalMap = new HashMap<>();

        // Calculate normals for each triangle and accumulate them
        for (Triangle t : triangles)
        {
            if(t.normal == null) continue;
            if(t.normal.w == 1) continue;
            for (Vertex v : new Vertex[]{t.v0, t.v1, t.v2}) {
                normalMap.computeIfAbsent(v, k -> new ArrayList<>()).add(t.normal);
            }
        }

        // Smooth normals
        for (Triangle t : triangles) {
            if(t.normal == null) continue;
            if(t.normal.w == 1) continue;
            Vector4 smoothed0 = smoothVertexNormal(t.v0, normalMap, tolerance);
            Vector4 smoothed1 = smoothVertexNormal(t.v1, normalMap, tolerance);
            Vector4 smoothed2 = smoothVertexNormal(t.v2, normalMap, tolerance);

            if(smoothed0 != null)
                t.v0.SetNormal(smoothed0);

            if(smoothed1 != null)
                t.v1.SetNormal(smoothed1);

            if(smoothed2 != null)
                t.v2.SetNormal(smoothed2);
        }
    }

    private Vector4 smoothVertexNormal(Vertex v, Map<Vertex, List<Vector4>> normalMap, double tolerance)
    {
        List<Vector4> similarNormals = new ArrayList<>();

        for (Vertex key : normalMap.keySet()) {
            if (v.IsSamePosition(key, tolerance)) {
                similarNormals.addAll(normalMap.get(key));
            }
        }

        if (!similarNormals.isEmpty()) {
            Vector4 smoothedNormal = averageNormals(similarNormals);
            return smoothedNormal;
        }

        return null;
    }

    private Vector4 averageNormals(List<Vector4> normals) {
        System.out.println("\nSimilar Normal Count: " + normals.size());

        double sumX = 0, sumY = 0, sumZ = 0;
        for (Vector4 n : normals) {
            if(n == null) break;
            sumX += n.x;
            sumY += n.y;
            sumZ += n.z;
        }
        double count = normals.size();
        return new Vector4((sumX / count), (sumY / count), (sumZ / count), 1);
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

