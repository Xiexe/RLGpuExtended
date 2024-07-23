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
in float gAlpha[3];

flat out int fTextureId;
out vec2 fUv;
out float fAlpha;

bool CheckIsTree(int texId)
{
  return texId == TREE_TOP || texId == TREE_BOTTOM || texId == TREE_WILLOW;
}

void main() {
  int textureId = gTextureId[0];
  vec2 uv[3];

  if (textureId > 0)
  {
    compute_uv(mainLight.pos.xyz, gVertex[0], gVertex[1], gVertex[2], gTexPos[0], gTexPos[1], gTexPos[2], uv[0], uv[1], uv[2]);

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
    vec3 vertex = gVertex[i];
    if(CheckIsTree(gTextureId[i]))
    {
      float frequency = 0.005;
      float phase = vertex.x + vertex.z;

      vertex.x += sin(time * frequency + phase) * 2;
      vertex.z += cos(time * frequency + phase) * 2;
    }

    fTextureId = gTextureId[i];
    fUv = uv[i];
    fAlpha = gAlpha[i];
    gl_Position = gProjMatrix[0] * vec4(vertex, 1);
    EmitVertex();
  }

  EndPrimitive();
}