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

    float ndl = max(dot(s.normal.xyz, lightDirection), 0);
    vec3 litFragment = s.albedo.rgb * (ndl * lightColor + ambientColor);

    float distFog = distance(fPosition, fCamPos) / drawDistance;
    distFog = smoothstep(1 - (float(fogDepth) / 100), 1, distFog);
    distFog = max(distFog, fFogAmount);

    float heightFog = distance(fPosition / 20000, vec3(fPosition.x, 0, fPosition.z));
    //heightFog = smoothstep(1 - (float(5) / 100), 1, heightFog);
//    heightFog = max(distFog, fFogAmount);

    vec3 finalColor = mix(CheckIsUnlitTexture(fTextureId) ? s.albedo.rgb : litFragment, fogColor.rgb, distFog);
    finalColor = s.normal.xyz;

    PostProcessImage(finalColor, colorBlindMode);
    FragColor = vec4(finalColor, s.albedo.a);
}
