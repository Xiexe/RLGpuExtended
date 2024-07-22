#version 420

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
out mat4 gProjMatrix;

void main() {
    gVertex = vPosition;
    gTextureId = int(vUv.x);
    gTexPos = vUv.yzw;
    gProjMatrix = mainLight.projectionMatrix;

    gl_Position = vec4(vPosition, 1);
}
