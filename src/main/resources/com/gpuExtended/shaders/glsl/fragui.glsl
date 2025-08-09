
#version 420

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
uniform vec4 alphaOverlay;

#if SHADOW_MAP_OVERLAY
uniform sampler2D shadowMap;
uniform ivec4 shadowMapOverlayDimensions;
#endif
#if TILE_MASK_OVERLAY
uniform sampler2D tileMask;
uniform ivec4 tileMaskOverlayDimensions;
#endif

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/structs.glsl"
#include "shaders/glsl/uniforms.glsl"

#include "scale/bicubic.glsl"
#include "scale/xbr_lv2_frag.glsl"
#include "shaders/glsl/colorblind.glsl"
#include "shaders/glsl/tonemapping/aces.glsl"
#include "shaders/glsl/tonemapping/agx.glsl"
#include "shaders/glsl/tonemapping/filmic.glsl"
#include "shaders/glsl/tonemapping/neutral.glsl"
#include "shaders/glsl/tonemapping/reinhard2.glsl"
#include "shaders/glsl/tonemapping/lottes.glsl"

in vec2 TexCoord;
in XBRTable xbrTable;

out vec4 FragColor;

vec3 gammaToLinear(vec3 color) {
  return pow(color, vec3(2.2));
}

vec3 linearToGamma(vec3 color) {
  return pow(color, vec3(1.0 / 2.2));
}

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

void ApplyTonemapping(inout vec3 image) {
    switch (tonemapper) {
        case TONEMAP_NEUTRAL:
          image = neutral(image);
        break;
        case TONEMAP_ACES:
          image = aces(image);
        break;
        case TONEMAP_AGX:
          image = agx(image);
        break;
        case TONEMAP_FILMIC:
          image = filmic(image);
        break;
        case TONEMAP_REINHARD:
          image = reinhard(image);
        break;
        case TONEMAP_LOTTES:
          image = lottes(image);
        break;
        default:
          image = neutral(image);
        break;
    }
}

float saturate(float value) {
  return clamp(value, 0.0, 1.0);
}

vec3 saturate(vec3 color) {
  return clamp(color, vec3(0.0), vec3(1.0));
}

void PostProcessImage(inout vec3 image, vec3 bloom, int colorBlindMode, float fogFalloff, int isEmissive)
{
  image += bloom;
  ApplyTonemapping(image);

  image = adjustContrast(image, (configContrast / 255f));
  image = adjustSaturation(image, (configSaturation / 255f));

  if (colorBlindMode > 0) {
    image = colorblind(colorBlindMode, image);
  }

  float brightness = ((configBrightness * 2.2) / 255f);
  float gamma = brightness * 2.2; // Adjust gamma based on brightness setting
  vec3 srgb = pow(image, vec3(1.0 / gamma));
  srgb = saturate(srgb);
  image = srgb;//linearToGamma(image);
}

void main() {
  #if SHADOW_MAP_OVERLAY
    vec2 uv = (gl_FragCoord.xy - shadowMapOverlayDimensions.xy) / shadowMapOverlayDimensions.zw;
    if (0 <= uv.x && uv.x <= 1 && 0 <= uv.y && uv.y <= 1) {
      vec4 shadowMap = texture(shadowMap, uv);
      FragColor = vec4(vec3(shadowMap), 1);
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
  PostProcessImage(mainColor.rgb, bloom.rgb, colorBlindMode, 0.0, 0);

  vec4 ui = sampleUiTexture();
  mainColor.rgb = max(vec3(0), mix(mainColor.rgb, ui.rgb, ui.a));
  FragColor = vec4(mainColor.rgb, 1);
}
