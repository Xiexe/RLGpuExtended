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
out mat4 gProjMatrix;
out vec3 gCameraPosition;
out float gAlpha;

void main() {
    int hsl = vHsl & 0xffff;
    float a = float(vHsl >> 24 & 0xff) / 255.f;

    vec3 vPos = vPosition;
//    int plane              = ((vFlags.x >> BIT_PLANE) & 3);
//    bool isBridge          = ((vFlags.x >> BIT_ISBRIDGE) & 1) > 0;
    bool isTerrain         = ((vFlags.x >> BIT_ISTERRAIN) & 1) > 0;
//    bool isDynamicModel    = ((vFlags.x >> BIT_ISDYNAMICMODEL) & 1) > 0;
//
//    if(isDynamicModel)
//    {
//        vPos = vec3(0);
//    }

    gVertex = vPos;
    gTextureId = int(vUv.x);
    gTexPos = vUv.yzw;
    gProjMatrix = mainLight.projectionMatrix;
    gAlpha = 1.0 - a;

    gl_Position = vec4(vPosition, 1);
}
