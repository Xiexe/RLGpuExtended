#version 330
#define TILE_SIZE 128
#define PLANE_HEIGHT 256

uniform sampler2DArray textures;
uniform float brightness;
uniform float smoothBanding;
uniform int fogDepth;
uniform int colorBlindMode;
uniform float textureLightMode;
uniform vec3 lightColor;
uniform vec3 lightDirection;
uniform vec3 ambientColor;
uniform vec3 fogColor;
uniform int drawDistance;
uniform int sceneOffsetX;
uniform int sceneOffsetZ;

uniform float time;
uniform float deltaTime;

in vec4 fColor;
in vec4 fNormal;
noperspective centroid in float fHsl;
flat in int fTextureId;
in vec2 fUv;
in vec3 fPosition;
in vec3 fCamPos;
in float fFogAmount;

out vec4 FragColor;

#include "/shaders/glsl/hsl_to_rgb.glsl"
#include "/shaders/glsl/colorblind.glsl"

#include "/shaders/glsl/constants.glsl"
#include "/shaders/glsl/structs.glsl"
#include "/shaders/glsl/helpers.glsl"
#include "/shaders/glsl/lighting.glsl"

float LinearExponentialAttenuation(float dist, float maxDistance) {
    // Linear component
    float linearAttenuation = max(1.0 - dist / maxDistance, 0.0);

    // Exponential component
    float exponentialAttenuation = exp(-dist * 0.5);

    // Combined attenuation
    return linearAttenuation * exponentialAttenuation;
}

float hash(vec2 uv)
{
    return fract(sin(7.289 * uv.x + 11.23 * uv.y) * 23758.5453);
}

void main() {
    Surface s;
    PopulateSurfaceColor(s);
    PopulateSurfaceNormal(s, fNormal);

    float ndl = max(dot(s.normal.xyz, vec3(1, 1, 0)), 0);
    vec3 litFragment = s.albedo.rgb * (ndl * lightColor + ambientColor);

    float distFog = distance(fPosition, fCamPos) / drawDistance;
    distFog = smoothstep(1 - (float(fogDepth) / 100), 1, distFog);
    distFog = max(distFog, fFogAmount);

    float heightFog = (-fPosition.y / 2000);
    heightFog = smoothstep(0.3, -0.1, heightFog);

    vec3 finalColor = mix(CheckIsUnlitTexture(fTextureId) ? s.albedo.rgb : litFragment, fogColor.rgb, distFog);
    s.albedo.rgb = mix(s.albedo.rgb, vec3(heightFog) * vec3(0.1, 0.25, 0.1) , heightFog);

    finalColor.rgb = s.normal.rgb;

//    vec3 additiveLightColors = vec3(0);
//    for(int i = 0; i < LIGHT_COUNT; i++)
//    {
//        Light light = LightsArray[i];
//        if(light.type == LIGHT_TYPE_INVALID) break;
//        light.pos.x = ((light.pos.x - sceneOffsetX) + 0.5f) * TILE_SIZE;
//        light.pos.z = ((light.pos.z - sceneOffsetZ) + 0.5f) * TILE_SIZE;
//        light.pos.y = light.pos.y - (TILE_SIZE * 1.5);
//
//        vec3 dir = light.pos.xyz - fPosition.xyz;
//        float dist = length(dir) / TILE_SIZE;
//        if(dist < light.radius)
//        {
//            dir = normalize(dir);
//            dir.y = -dir.y;
//
//            float atten = LinearExponentialAttenuation(dist, light.radius);
//            float ndl = max(dot(s.normal.xyz, dir / dist), 0);
//
//            additiveLightColors += light.color.rgb * light.intensity * ndl * atten;
//
//            /*
//            if(light.animation != LIGHT_ANIM_NONE)
//            {
//                float hashXY = hash(vec2(light.pos.x, light.pos.z));
//                float hashHZ = hash(vec2(light.pos.y, light.pos.y));
//                float offset = (hashXY + hashHZ) * 1000;
//
//                switch(light.animation)
//                {
//                    case LIGHT_ANIM_FLICKER:
//                    float flicker = sin((time / 100) - offset) * 0.5 + 0.5;
//                    additiveLightColors *= flicker;
//                    break;
//
//                    case LIGHT_ANIM_PULSE:
//                    float pulse = sin((time / 100) - offset) * 0.5 + 0.5;
//                    additiveLightColors *= pulse;
//                    break;
//                }
//            }
//            */
//        }
//    }

    PostProcessImage(finalColor, colorBlindMode);
    FragColor = vec4(finalColor, s.albedo.a);
}
