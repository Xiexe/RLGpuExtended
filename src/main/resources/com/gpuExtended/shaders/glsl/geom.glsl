#version 420

// smallest unit of the texture which can be moved per tick. textures are all
// 128x128px - so this is equivalent to +1px
#define TEXTURE_ANIM_UNIT (1.0 / 128.0)

layout(triangles) in;
layout(triangle_strip, max_vertices = 3) out;
//#define LINE_DEBUG
//layout(line_strip, max_vertices = 4) out;
//layout(points, max_vertices = 3) out;

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/structs.glsl"
#include "shaders/glsl/uniforms.glsl"
#include "shaders/glsl/uv.glsl"

in vec3 gVertex[3];
in vec3 gPosition[3];
in vec4 gNormal[3];
in vec4 gColor[3];
in float gHsl[3];
in int gTextureId[3];
in vec3 gTexPos[3];
in int gFarClip[3];
in float gFogAmount[3];

out vec4 fColor;
out vec4 fNormal;
noperspective centroid out float fHsl;
flat out int fTextureId;
out vec2 fUv;
out vec3 fPosition;
out float fFogAmount;
out float fPlane;
out float fOnBridge;
out float fIsRoof;
out float fIsTerrain;

void main() {
  int textureId = gTextureId[0];
  vec2 uv[3];

  if (textureId > 0)
  {
    compute_uv(cameraPosition.xyz, gVertex[0], gVertex[1], gVertex[2], gTexPos[0], gTexPos[1], gTexPos[2], uv[0], uv[1], uv[2]);

    vec2 textureAnim = textureAnimations[textureId - 1];
    for (int i = 0; i < 3; ++i) {
      uv[i] += tick * textureAnim * TEXTURE_ANIM_UNIT;
    }
  }
  else
  {
    uv[0] = vec2(0);
    uv[1] = vec2(0);
    uv[2] = vec2(0);
  }

  // Calc flat normals
  vec3 v0 = gVertex[0];
  vec3 v1 = gVertex[1];
  vec3 v2 = gVertex[2];
  vec3 triangleNormal = normalize(cross(v1 - v0, v2 - v0));

  for (int i = 0; i < 3; ++i) {
    vec4 normal = gNormal[i];
    // TODO:: Bitshift to get flat normals flag
    if((gNormal[i].x == 0 && gNormal[i].y == 0 && gNormal[i].z == 0))
    {
        normal = vec4(triangleNormal.x, triangleNormal.y, triangleNormal.z, gNormal[i].w);
    }

    fPlane = (int(gNormal[0].w) >> 24) & 3;
    fOnBridge = (int(gNormal[0].w) >> 27) & 1;
    fIsRoof = (int(gNormal[0].w) >> 28) & 1;
    fIsTerrain = (int(gNormal[0].w) >> 29) & 1;

    fPosition = gPosition[i];
    fColor = gColor[i];
    fFogAmount = gFogAmount[i];
    fHsl = gHsl[i];
    fTextureId = gTextureId[i];
    fNormal = normal;
    fUv = uv[i];
    gl_Position = projectionMatrix * vec4(gVertex[i], 1);
    EmitVertex();
  }

#ifdef LINE_DEBUG
  fPosition = gPosition[0];
  gl_Position = projectionMatrix * vec4(gVertex[0], 1);
  EmitVertex();
#endif

  EndPrimitive();
}