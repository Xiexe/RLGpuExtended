#version 430

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/structs.glsl"
#include "shaders/glsl/uniforms.glsl"

layout (location = 0) in vec3 aPos;

out vec3 TexCoords;

void main() {
    TexCoords = aPos;
    vec4 pos = cameraProjectionMatrix * vec4(aPos, 1.0);
    gl_Position = pos.xyww; // Make sure the w component is 1.0 to create an infinite cube
}
