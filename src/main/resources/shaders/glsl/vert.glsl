#version 330

#define TILE_SIZE 128

#define FOG_SCENE_EDGE_MIN ((-expandedMapLoadingChunks * 8 + 1) * TILE_SIZE)
#define FOG_SCENE_EDGE_MAX ((104 + expandedMapLoadingChunks * 8 - 1) * TILE_SIZE)
#define FOG_CORNER_ROUNDING 1.5
#define FOG_CORNER_ROUNDING_SQUARED (FOG_CORNER_ROUNDING * FOG_CORNER_ROUNDING)

layout(location = 0) in ivec4 vPos;
layout(location = 1) in vec4 vUv;
layout(location = 2) in vec4 vNorm;

layout(std140) uniform uniforms {
  float cameraYaw;
  float cameraPitch;
  int centerX;
  int centerY;
  int zoom;
  float cameraX;
  float cameraY;
  float cameraZ;
  ivec2 sinCosTable[2048];
};

uniform float brightness;
uniform int drawDistance;
uniform int expandedMapLoadingChunks;

out ivec3 gVertex;
out vec3 gNormal;
out vec3 gPosition;
out vec4 gColor;
out float gHsl;
out int gTextureId;
out vec3 gTexPos;
out float gFogAmount;

#include "/shaders/glsl/hsl_to_rgb.glsl"

float fogFactorLinear(const float dist, const float start, const float end) {
  return 1.0 - clamp((dist - start) / (end - start), 0.0, 1.0);
}

void main() {
  ivec3 vertex = vPos.xyz;
  vec3 position = vec3(vPos.xyz);
  int ahsl = vPos.w;
  int hsl = ahsl & 0xffff;
  float a = float(ahsl >> 24 & 0xff) / 255.f;

  vec3 rgb = hslToRgb(hsl);

  gVertex = vertex;
  gNormal = vNorm.xyz;
  gPosition = position;
  gColor = vec4(rgb, 1.f - a);
  gHsl = float(hsl);
  gTextureId = int(vUv.x);  // the texture id + 1;
  gTexPos = vUv.yzw;

  // the client draws one less tile to the north and east than it does to the south
  // and west, so subtract a tiles width from the north and east edges.
  int fogWest = max(FOG_SCENE_EDGE_MIN, int(cameraX) - drawDistance);
  int fogEast = min(FOG_SCENE_EDGE_MAX, int(cameraX) + drawDistance - TILE_SIZE);
  int fogSouth = max(FOG_SCENE_EDGE_MIN, int(cameraZ) - drawDistance);
  int fogNorth = min(FOG_SCENE_EDGE_MAX, int(cameraZ) + drawDistance - TILE_SIZE);

  // Calculate distance from the scene edge
  int xDist = min(vertex.x - fogWest, fogEast - vertex.x);
  int zDist = min(vertex.z - fogSouth, fogNorth - vertex.z);
  float nearestEdgeDistance = min(xDist, zDist);
  float secondNearestEdgeDistance = max(xDist, zDist);
  float fogDistance =
      nearestEdgeDistance - FOG_CORNER_ROUNDING * TILE_SIZE *
                                max(0.f, (nearestEdgeDistance + FOG_CORNER_ROUNDING_SQUARED) / (secondNearestEdgeDistance + FOG_CORNER_ROUNDING_SQUARED));

  gFogAmount = fogFactorLinear(fogDistance, 0, 10 * TILE_SIZE);
}
