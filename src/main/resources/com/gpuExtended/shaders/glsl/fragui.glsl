
#version 330

#define SAMPLING_MITCHELL 1
#define SAMPLING_CATROM 2
#define SAMPLING_XBR 3
#include "SHADOW_MAP_OVERLAY"

uniform sampler2D tex;

uniform int samplingMode;
uniform ivec2 sourceDimensions;
uniform ivec2 targetDimensions;
uniform int colorBlindMode;
uniform vec4 alphaOverlay;

#if SHADOW_MAP_OVERLAY
uniform sampler2D shadowMap;
uniform ivec4 shadowMapOverlayDimensions;
#endif

#include "scale/bicubic.glsl"

#include "scale/xbr_lv2_frag.glsl"
#include "shaders/glsl/colorblind.glsl"

in vec2 TexCoord;
in XBRTable xbrTable;

out vec4 FragColor;

vec4 alphaBlend(vec4 src, vec4 dst) {
  return vec4(src.rgb + dst.rgb * (1.0f - src.a), src.a + dst.a * (1.0f - src.a));
}

void main() {
  #if SHADOW_MAP_OVERLAY
    vec2 uv = (gl_FragCoord.xy - shadowMapOverlayDimensions.xy) / shadowMapOverlayDimensions.zw;
    if (0 <= uv.x && uv.x <= 1 && 0 <= uv.y && uv.y <= 1) {
      FragColor = texture(shadowMap, uv);
      return;
    }
  #endif

  vec4 c;

  if (samplingMode == SAMPLING_CATROM || samplingMode == SAMPLING_MITCHELL) {
    c = textureCubic(tex, TexCoord, samplingMode);
  } else if (samplingMode == SAMPLING_XBR) {
    c = textureXBR(tex, TexCoord, xbrTable, ceil(1.0 * targetDimensions.x / sourceDimensions.x));
  } else {  // NEAREST or LINEAR, which uses GL_TEXTURE_MIN_FILTER/GL_TEXTURE_MAG_FILTER to affect sampling
    c = texture(tex, TexCoord);
  }

  c = alphaBlend(c, alphaOverlay);
  c.rgb = colorblind(colorBlindMode, c.rgb);

  FragColor = c;
}
