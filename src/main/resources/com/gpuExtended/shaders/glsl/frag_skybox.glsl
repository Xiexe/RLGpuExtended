#version 430

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/structs.glsl"
#include "shaders/glsl/uniforms.glsl"

in vec3 TexCoords;
out vec4 FragColor;

void main() {

    vec3 bottomColor = vec3(0.0, 0.0, 0.0);
    vec3 topColor = skyColor.rgb;

    float interpolator = smoothstep(0.99, 1.0, TexCoords.y + 0.7f);
    vec3 col = mix(topColor, bottomColor, interpolator);
    FragColor = vec4(col, 1.0);
}