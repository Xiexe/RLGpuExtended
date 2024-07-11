float fogFactorLinear(const float dist, const float start, const float end) {
    return 1.0 - clamp((dist - start) / (end - start), 0.0, 1.0);
}

float CalculateFogAmount(vec3 position) {
    if (fogDepth == 0)
    return 0.f;

    float drawDistance2 = drawDistance * TILE_SIZE;

    // the client draws one less tile to the north and east than it does to the south
    // and west, so subtract a tile's width from the north and east edges.
    float fogWest = max(FOG_SCENE_EDGE_MIN,  cameraPosition.x - drawDistance2);
    float fogEast = min(FOG_SCENE_EDGE_MAX,  cameraPosition.x + drawDistance2 - TILE_SIZE);
    float fogSouth = max(FOG_SCENE_EDGE_MIN, cameraPosition.z - drawDistance2);
    float fogNorth = min(FOG_SCENE_EDGE_MAX, cameraPosition.z + drawDistance2 - TILE_SIZE);

    // Calculate distance from the scene edge
    float xDist = min(position.x - fogWest, fogEast - position.x);
    float zDist = min(position.z - fogSouth, fogNorth - position.z);
    float nearestEdgeDistance = min(xDist, zDist);
    float secondNearestEdgeDistance = max(xDist, zDist);
    float fogDistance = nearestEdgeDistance
    - FOG_CORNER_ROUNDING * TILE_SIZE * max(0,
    (nearestEdgeDistance + FOG_CORNER_ROUNDING_SQUARED)
    / (secondNearestEdgeDistance + FOG_CORNER_ROUNDING_SQUARED)
    );

    // This is different from the GPU plugin, and seems to have worked this way from the start
    float edgeFogAmount = fogFactorLinear(fogDistance, 0, fogDepth * TILE_SIZE);
    float fogStart1 = drawDistance2 * 0.85;
    float distance1 = length(cameraPosition.xz - position.xz);
    float distanceFogAmount = clamp((distance1 - fogStart1) / (drawDistance2 * .15), 0, 1);

    // Combine distance fog with edge fog
    return max(distanceFogAmount, edgeFogAmount);
}