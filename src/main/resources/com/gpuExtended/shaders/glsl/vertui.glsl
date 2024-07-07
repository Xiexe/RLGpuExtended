
#version 330

#define SAMPLING_DEFAULT 0
#define SAMPLING_MITCHELL 1
#define SAMPLING_CATROM 2
#define SAMPLING_XBR 3

uniform int samplingMode;
uniform ivec2 sourceDimensions;
uniform ivec2 targetDimensions;

#include "scale/xbr_lv2_vert.glsl"

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aTexCoord;

out vec2 TexCoord;
out XBRTable xbrTable;

void main() {
  gl_Position = vec4(aPos, 1.0);
  TexCoord = aTexCoord;

  if (samplingMode == SAMPLING_XBR)
    xbrTable = xbr_vert(TexCoord, sourceDimensions);
}
