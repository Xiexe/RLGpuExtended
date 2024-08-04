
#version 330

#define SAMPLING_MITCHELL 1
#define SAMPLING_CATROM 2
#define SAMPLING_XBR 3
#include "SHADOW_MAP_OVERLAY"
#include "TILE_MASK_OVERLAY"

uniform sampler2D mainTexture;
uniform sampler2D interfaceTexture;

uniform int samplingMode;
uniform ivec2 sourceDimensions;
uniform ivec2 targetDimensions;
uniform int colorBlindMode;
uniform vec4 alphaOverlay;

#if SHADOW_MAP_OVERLAY
uniform sampler2D shadowMap;
uniform ivec4 shadowMapOverlayDimensions;
#endif
#if TILE_MASK_OVERLAY
uniform sampler2D tileMask;
uniform ivec4 tileMaskOverlayDimensions;
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

vec4 sampleMainColor()
{
  vec2 uv = vec2(TexCoord.x, 1.0 - TexCoord.y);
  return texture(mainTexture, uv);
}

vec4 sampleUiTexture()
{
  vec4 frag = vec4(0);

  switch(samplingMode)
  {
    case SAMPLING_MITCHELL:
    case SAMPLING_CATROM:
    {
      frag = textureCubic(interfaceTexture, TexCoord, samplingMode);
      break;
    }

    case SAMPLING_XBR:
    {
      frag = textureXBR(interfaceTexture, TexCoord, xbrTable, ceil(1.0 * targetDimensions.x / sourceDimensions.x));
      break;
    }

    default:
    {
        frag = texture(interfaceTexture, TexCoord);
      break;
    }
  }

  frag = alphaBlend(frag, alphaOverlay);
  frag.rgb = colorblind(colorBlindMode, frag.rgb);
  return frag;
}

void main() {
  #if SHADOW_MAP_OVERLAY
    vec2 uv = (gl_FragCoord.xy - shadowMapOverlayDimensions.xy) / shadowMapOverlayDimensions.zw;
    if (0 <= uv.x && uv.x <= 1 && 0 <= uv.y && uv.y <= 1) {
      FragColor = texture(shadowMap, uv);
      return;
    }
  #endif
  #if TILE_MASK_OVERLAY
    vec2 uv = (gl_FragCoord.xy - tileMaskOverlayDimensions.xy) / tileMaskOverlayDimensions.zw;
    if (0 <= uv.x && uv.x <= 1 && 0 <= uv.y && uv.y <= 1) {
      FragColor = texture(tileMask, uv);
      FragColor = vec4(1);
    }
  #endif

  vec4 frag = sampleMainColor();
  vec4 uiFrag = sampleUiTexture();
  frag.rgb = mix(frag.rgb, uiFrag.rgb, uiFrag.a);
  FragColor = frag;
}
