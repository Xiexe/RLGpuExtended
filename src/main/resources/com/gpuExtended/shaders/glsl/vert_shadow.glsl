#version 420

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/structs.glsl"
#include "shaders/glsl/uniforms.glsl"

layout (location = VPOS_BINDING_ID) in vec3 vPosition;
layout (location = VHSL_BINDING_ID) in int vHsl;
layout (location = VUV_BINDING_ID) in vec4 vUv;
layout (location = VNORM_BINDING_ID) in vec4 vNormal;
layout (location = VFLAGS_BINDING_ID) in ivec4 vFlags;

void main() {
    gl_Position = mainLight.projectionMatrix * vec4(vPosition, 1);
}
