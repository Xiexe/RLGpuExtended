#version 330

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

in vec4 fColor;
in vec4 fNormal;
noperspective centroid in float fHsl;
flat in int fTextureId;
in vec2 fUv;
in vec3 fPosition;
in vec3 fCamPos;
in float fFogAmount;

out vec4 FragColor;

#include "/shaders/glsl/constants.glsl"
#include "/shaders/glsl/hsl_to_rgb.glsl"
#include "/shaders/glsl/colorblind.glsl"
#include "/shaders/glsl/structs.glsl"
#include "/shaders/glsl/helpers.glsl"

void main() {
    Surface s;
    PopulateSurfaceColor(s);
    PopulateSurfaceNormal(s, fNormal);

    float ndl = max(dot(s.normal.xyz, vec3(1, 1, 0)), 0);
    vec3 litFragment = s.albedo.rgb * (ndl * lightColor + ambientColor);

    float distFog = distance(fPosition, fCamPos) / drawDistance;
    distFog = smoothstep(1 - (float(fogDepth) / 100), 1, distFog);
    distFog = max(distFog, fFogAmount);

    vec3 finalColor = mix(CheckIsUnlitTexture(fTextureId) ? s.albedo.rgb : litFragment, fogColor.rgb, distFog);
    finalColor = vec3(fNormal.x / drawDistance);

    PostProcessImage(finalColor, colorBlindMode);
    FragColor = vec4(finalColor, s.albedo.a);
}
