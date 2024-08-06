#version 430

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/structs.glsl"
#include "shaders/glsl/uniforms.glsl"

layout (location = 0) in vec3 vPos;

out vec3 TexCoords;

void main() {
    TexCoords = vPos;
    vec4 pos = cameraProjectionMatrix * vec4(cameraPosition.xyz + vPos * 128 * 10, 1.0);
    gl_Position = pos;
}
