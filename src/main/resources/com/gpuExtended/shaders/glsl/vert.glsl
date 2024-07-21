#version 420

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/structs.glsl"
#include "shaders/glsl/uniforms.glsl"
#include "shaders/glsl/fog.glsl"
#include "shaders/glsl/hsl_to_rgb.glsl"

layout(location = VPOS_BINDING_ID) in vec3 vPos;
layout(location = VHSL_BINDING_ID) in int vHsl;
layout(location = VUV_BINDING_ID) in vec4 vUv;
layout(location = VNORM_BINDING_ID) in vec4 vNorm;
layout(location = VFLAGS_BINDING_ID) in ivec4 vFlags;

out vec3 gVertex;
out vec3 gPosition;
out vec4 gNormal;
out vec4 gColor;
out float gHsl;
out int gTextureId;
out vec3 gTexPos;
out float gFogAmount;
out ivec4 gFlags;

void main() {
    int hsl = vHsl & 0xffff;
    float a = float(vHsl >> 24 & 0xff) / 255.f;

    vec3 rgb = hslToRgb(hsl);

    gVertex = vPos;
    gNormal = vNorm;
    gPosition = vPos;
    gFogAmount = CalculateFogAmount(vPos);
    gColor = vec4(rgb, 1.f - a);
    gHsl = float(hsl);
    gTextureId = int(vUv.x);  // the texture id + 1;
    gTexPos = vUv.yzw;
    gFlags = vFlags;
}
