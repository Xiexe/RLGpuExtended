#version 420

// smallest unit of the texture which can be moved per tick. textures are all
// 128x128px - so this is equivalent to +1px
#define TEXTURE_ANIM_UNIT (1.0 / 128.0)

layout(triangles) in;
layout(triangle_strip, max_vertices = 3) out;

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/structs.glsl"
#include "shaders/glsl/uniforms.glsl"
#include "shaders/glsl/uv.glsl"

in vec3 gVertex[3];
in int gTextureId[3];
in vec3 gTexPos[3];
in mat4 gProjMatrix[3];

flat out int fTextureId;
out vec2 fUv;

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

  for (int i = 0; i < 3; ++i) {
    fTextureId = gTextureId[i];
    fUv = uv[i];
    gl_Position = gProjMatrix[0] * vec4(gVertex[i], 1);
    EmitVertex();
  }

  EndPrimitive();
}