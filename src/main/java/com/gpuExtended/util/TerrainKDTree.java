package com.gpuExtended.util;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TerrainKDTree {
    private KDNode root;

    private static class KDNode {
        SceneUploader.TerrainVertex vertex;
        KDNode left, right;
        int axis;

        KDNode(SceneUploader.TerrainVertex vertex, int axis) {
            this.vertex = vertex;
            this.axis = axis;
        }
    }

    public TerrainKDTree(List<SceneUploader.TerrainVertex> vertices) {
        root = buildTree(vertices, 0);
    }

    private KDNode buildTree(List<SceneUploader.TerrainVertex> vertices, int depth) {
        if (vertices.isEmpty()) {
            return null;
        }

        int axis = depth % 3;
        Collections.sort(vertices, Comparator.comparingInt(v -> (axis == 0) ? v.x : (axis == 1) ? v.y : v.z));
        int medianIndex = vertices.size() / 2;

        KDNode node = new KDNode(vertices.get(medianIndex), axis);
        node.left = buildTree(vertices.subList(0, medianIndex), depth + 1);
        node.right = buildTree(vertices.subList(medianIndex + 1, vertices.size()), depth + 1);

        return node;
    }

    public SceneUploader.TerrainVertex findNearest(SceneUploader.TerrainVertex target) {
        return findNearest(root, target, null, Float.MAX_VALUE);
    }

    private SceneUploader.TerrainVertex findNearest(KDNode node, SceneUploader.TerrainVertex target, SceneUploader.TerrainVertex best, float bestDist) {
        if (node == null) {
            return best;
        }

        float d = calculateSquaredDistance(node.vertex, target);
        float dx = (node.axis == 0) ? target.x - node.vertex.x : (node.axis == 1) ? target.y - node.vertex.y : target.z - node.vertex.z;
        float dx2 = dx * dx;

        SceneUploader.TerrainVertex bestLocal = (d < bestDist) ? node.vertex : best;
        float bestDistLocal = Math.min(d, bestDist);

        KDNode first = (dx > 0) ? node.left : node.right;
        KDNode second = (dx > 0) ? node.right : node.left;

        bestLocal = findNearest(first, target, bestLocal, bestDistLocal);
        if (dx2 < bestDistLocal) {
            bestLocal = findNearest(second, target, bestLocal, bestDistLocal);
        }

        return bestLocal;
    }

    private float calculateSquaredDistance(SceneUploader.TerrainVertex v1, SceneUploader.TerrainVertex v2) {
        return (v1.x - v2.x) * (v1.x - v2.x) +
                (v1.y - v2.y) * (v1.y - v2.y) +
                (v1.z - v2.z) * (v1.z - v2.z);
    }
}
