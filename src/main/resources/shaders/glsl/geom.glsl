#version 330

// smallest unit of the texture which can be moved per tick. textures are all
// 128x128px - so this is equivalent to +1px
#define TEXTURE_ANIM_UNIT (1.0 / 128.0)

layout(triangles) in;
layout(triangle_strip, max_vertices = 3) out;
//layout(line_strip, max_vertices = 3) out;
//layout(points, max_vertices = 3) out;

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

#include "/shaders/glsl/uv.glsl"

uniform vec2 textureAnimations[128];
uniform int tick;
uniform mat4 projectionMatrix;

in ivec3 gVertex[3];
in vec3 gPosition[3];
in vec4 gNormal[3];
in vec4 gColor[3];
in float gHsl[3];
in int gTextureId[3];
in vec3 gTexPos[3];
in float gFogAmount[3];
in int gFarClip[3];

out vec4 fColor;
out vec4 fNormal;
noperspective centroid out float fHsl;
flat out int fTextureId;
out vec2 fUv;
out vec3 fPosition;
out vec3 fCamPos;
out float fFogAmount;

void main() {
  int textureId = gTextureId[0];
  vec2 uv[3];
  vec3 cameraPos = vec3(cameraX, cameraY, cameraZ);

  if (textureId > 0)
  {
    compute_uv(cameraPos.xyz, gVertex[0], gVertex[1], gVertex[2], gTexPos[0], gTexPos[1], gTexPos[2], uv[0], uv[1], uv[2]);

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
    if(gNormal[i].w >= 1)
    {
        normal = vec4(triangleNormal.x, triangleNormal.y, triangleNormal.z, gNormal[i].w);
    }

    fCamPos = cameraPos;
    fPosition = gPosition[i];
    fColor = gColor[i];
    fHsl = gHsl[i];
    fTextureId = gTextureId[i];
    fNormal = normal;
    fUv = uv[i];
    fFogAmount = gFogAmount[i];
    gl_Position = projectionMatrix * vec4(gVertex[i], 1);
    EmitVertex();
  }

  EndPrimitive();
}