#version 420

layout (location = 0) in vec3 vPosition;
layout (location = 1) in int vHsl;
layout (location = 2) in vec4 vUv;
layout (location = 3) in vec4 vNormal;

uniform mat4 projectionMatrix;

void main() {
    gl_Position = projectionMatrix * vec4(vPosition, 1);
}
