#version 430

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/structs.glsl"
#include "shaders/glsl/uniforms.glsl"

out vec4 FragColor;

void main() {
    FragColor = vec4(skyColor.rgb, 1.0);
}