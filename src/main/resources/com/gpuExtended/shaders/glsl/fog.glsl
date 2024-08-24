float fogFactorLinear(const float dist, const float start, const float end) {
    return 1.0 - clamp((dist - start) / (end - start), 0.0, 1.0);
}

float CalculateFogAmount(vec3 position) {
    if (fogDepth == 0)
    return 0.f;

    float drawDistance2 = (drawDistance * TILE_SIZE);

    // the client draws one less tile to the north and east than it does to the south
    // and west, so subtract a tiles width from the north and east edges.
    float fogWest = max(FOG_SCENE_EDGE_MIN, cameraPosition.x - drawDistance2);
    float fogEast = min(FOG_SCENE_EDGE_MAX, cameraPosition.x + drawDistance2 - TILE_SIZE);
    float fogSouth = max(FOG_SCENE_EDGE_MIN, cameraPosition.z - drawDistance2);
    float fogNorth = min(FOG_SCENE_EDGE_MAX, cameraPosition.z + drawDistance2 - TILE_SIZE);

    // Calculate distance from the scene edge
    float xDist = min(position.x - fogWest, fogEast - position.x);
    float zDist = min(position.z - fogSouth, fogNorth - position.z);
    float nearestEdgeDistance = min(xDist, zDist);
    float secondNearestEdgeDistance = max(xDist, zDist);
    float fogDistance = nearestEdgeDistance - FOG_CORNER_ROUNDING * TILE_SIZE * max(0.f, (nearestEdgeDistance + FOG_CORNER_ROUNDING_SQUARED) / (secondNearestEdgeDistance + FOG_CORNER_ROUNDING_SQUARED));

    float edgeFogAmount = fogFactorLinear(fogDistance, 0, 20 * TILE_SIZE);

    // Combine distance fog with edge fog
    return edgeFogAmount;
}