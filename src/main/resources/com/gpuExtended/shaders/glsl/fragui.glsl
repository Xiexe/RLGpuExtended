
#version 330

#define SAMPLING_MITCHELL 1
#define SAMPLING_CATROM 2
#define SAMPLING_XBR 3
#include "SHADOW_MAP_OVERLAY"
#include "TILE_MASK_OVERLAY"

uniform sampler2D mainTexture;
uniform sampler2D bloomTexture;
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
#include "shaders/glsl/tonemapping/aces.glsl"
#include "shaders/glsl/tonemapping/agx.glsl"
#include "shaders/glsl/tonemapping/filmic.glsl"
#include "shaders/glsl/tonemapping/neutral.glsl"
#include "shaders/glsl/tonemapping/reinhard2.glsl"

in vec2 TexCoord;
in XBRTable xbrTable;

out vec4 FragColor;

vec4 alphaBlend(vec4 src, vec4 dst) {
  return vec4(src.rgb + dst.rgb * (1.0f - src.a), src.a + dst.a * (1.0f - src.a));
}

vec4 sampleMainColor()
{
  vec2 uv = vec2(TexCoord.x, 1.0 - TexCoord.y);
  return textureLod(mainTexture, uv, 0);
}

vec4 sampleBloom()
{
    vec2 uv = vec2(TexCoord.x, 1.0 - TexCoord.y);
    return textureLod(bloomTexture, uv, 0);
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

// Function to adjust saturation
vec3 adjustSaturation(vec3 color, float saturation) {
  // Convert RGB to grayscale by calculating luminance
  float gray = dot(color, vec3(0.2126f, 0.7152f, 0.0722f));
  // Linearly interpolate between the grayscale value and the original color
  return mix(vec3(gray), color, saturation);
}

// Function to adjust contrast
vec3 adjustContrast(vec3 color, float contrast) {
  // Shift the color by 0.5 to center it, scale it by the contrast factor, and then shift it back
  return (color - 0.5) * contrast + 0.5;
}

vec3 adjustBrightness(vec3 color, float brightnessAdjust) {
  return color * brightnessAdjust;
}

// Function to combine upper and lower parts to form a 32-bit integer
int combine16(int upper, int lower) {
  return (upper << 16) | lower;
}

void PostProcessImage(inout vec3 image, int colorBlindMode, float fogFalloff, int isEmissive)
{
  image = agx(image);
  image = adjustBrightness(image, 1.2);
  image = adjustContrast(image, 1.4);
  image = adjustSaturation(image, 1.1);

  if (colorBlindMode > 0) {
    image = colorblind(colorBlindMode, image);
  }
}

// TODO:: fix shadowmap overlay rendering.
void main() {
  #if SHADOW_MAP_OVERLAY
    vec2 uv = (gl_FragCoord.xy - shadowMapOverlayDimensions.xy) / shadowMapOverlayDimensions.zw;
    if (0 <= uv.x && uv.x <= 1 && 0 <= uv.y && uv.y <= 1) {
      FragColor = texture(shadowMap, uv);
      return;
    }
  #endif
//
//  #if TILE_MASK_OVERLAY
//    vec2 uv = (gl_FragCoord.xy - tileMaskOverlayDimensions.xy) / tileMaskOverlayDimensions.zw;
//    if (0 <= uv.x && uv.x <= 1 && 0 <= uv.y && uv.y <= 1) {
//      FragColor = texture(tileMask, uv);
//      FragColor = vec4(1);
//    }
//  #endif

  vec4 mainColor = sampleMainColor();
  vec4 bloom = sampleBloom();
  vec4 ui = sampleUiTexture();

  vec3 composite = mainColor.rgb + bloom.rgb;
  PostProcessImage(composite, colorBlindMode, 0.0, 0);

  composite.rgb = mix(composite.rgb, ui.rgb, ui.a);
  FragColor = vec4(composite, 1);
}
