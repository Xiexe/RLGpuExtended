#version 430

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/structs.glsl"
#include "shaders/glsl/uniforms.glsl"

layout (location = VPOS_BINDING_ID) in vec3 vPosition;
layout (location = VHSL_BINDING_ID) in int vHsl;
layout (location = VUV_BINDING_ID) in vec4 vUv;
layout (location = VNORM_BINDING_ID) in vec4 vNormal;
layout (location = VFLAGS_BINDING_ID) in ivec4 vFlags;

out vec3 gVertex;
out int  gTextureId;
out vec3 gTexPos;
out vec3 gCameraPosition;
out float gAlpha;

void main() {
    vec3 vPos = vPosition;
    float a = float(vHsl >> 24 & 0xff) / 255.f;

    gVertex = vPos;
    gTextureId = int(vUv.x);
    gTexPos = vUv.yzw;
    gAlpha = 1.0 - a;

    gl_Position = vec4(vPosition, 1);
}
