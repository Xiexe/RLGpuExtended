#version 330

#include "shaders/glsl/constants.glsl"

void main() {
    float depth = gl_FragCoord.z;
    gl_FragDepth = depth;
}