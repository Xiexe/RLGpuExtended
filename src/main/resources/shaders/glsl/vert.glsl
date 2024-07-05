#version 330
layout(location = 0) in vec3 vPos;
layout(location = 1) in int vHsl;
layout(location = 2) in vec4 vUv;
layout(location = 3) in vec4 vNorm;

out vec3 gVertex;
out vec3 gPosition;
out vec4 gNormal;
out vec4 gColor;
out float gHsl;
out int gTextureId;
out vec3 gTexPos;
out float gFogAmount;

#include "/shaders/glsl/constants.glsl"
#include "/shaders/glsl/uniforms.glsl"
#include "/shaders/glsl/hsl_to_rgb.glsl"

float fogFactorLinear(const float dist, const float start, const float end) {
    return 1.0 - clamp((dist - start) / (end - start), 0.0, 1.0);
}

void main() {
    int hsl = vHsl & 0xffff;
    float a = float(vHsl >> 24 & 0xff) / 255.f;

    vec3 rgb = hslToRgb(hsl);

    // the client draws one less tile to the north and east than it does to the south
    // and west, so subtract a tiles width from the north and east edges.
    int fogWest = max(FOG_SCENE_EDGE_MIN, int(cameraPosition.x) - drawDistance);
    int fogEast = min(FOG_SCENE_EDGE_MAX, int(cameraPosition.x) + drawDistance - TILE_SIZE);
    int fogSouth = max(FOG_SCENE_EDGE_MIN, int(cameraPosition.z) - drawDistance);
    int fogNorth = min(FOG_SCENE_EDGE_MAX, int(cameraPosition.z) + drawDistance - TILE_SIZE);

    // Calculate distance from the scene edge
    float xDist = min(vPos.x - fogWest, fogEast - vPos.x);
    float zDist = min(vPos.z - fogSouth, fogNorth - vPos.z);
    float nearestEdgeDistance = min(xDist, zDist);
    float secondNearestEdgeDistance = max(xDist, zDist);
    float fogDistance = nearestEdgeDistance - FOG_CORNER_ROUNDING * TILE_SIZE * max(0.f, (nearestEdgeDistance + FOG_CORNER_ROUNDING_SQUARED) / (secondNearestEdgeDistance + FOG_CORNER_ROUNDING_SQUARED));

    gVertex = vPos;
    gNormal = vNorm;
    gPosition = vPos;
    gFogAmount = fogFactorLinear(fogDistance, 0, fogDepth * TILE_SIZE);
    gColor = vec4(rgb, 1.f - a);
    gHsl = float(hsl);
    gTextureId = int(vUv.x);  // the texture id + 1;
    gTexPos = vUv.yzw;
}
