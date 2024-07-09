#version 420

layout (location = 0) in vec3 vPosition;
layout (location = 1) in int vHsl;
layout (location = 2) in vec4 vUv;
layout (location = 3) in vec4 vNormal;

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/structs.glsl"
#include "shaders/glsl/uniforms.glsl"

void main() {
    gl_Position = mainLight.projectionMatrix * vec4(vPosition, 1);
}
