#version 430

in vec4 fColor;
in vec4 fNormal;
noperspective centroid in float fHsl;
flat in int fTextureId;
in vec2 fUv;
in vec3 fPosition;
in float fFogAmount;
in float fSurfaceDepth;
in flat int isEmissive;
in flat ivec4 fFlags;

out vec4 FragColor;

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/structs.glsl"
#include "shaders/glsl/uniforms.glsl"
#include "shaders/glsl/hsl_to_rgb.glsl"
#include "shaders/glsl/colorblind.glsl"
#include "shaders/glsl/helpers.glsl"
#include "shaders/glsl/lighting.glsl"
#include "shaders/glsl/tanoise/tanoise.glsl"

void ApplyFog(inout vec3 image, vec3 fragPos, float distanceToCamera)
{
    float fogHeight = 4;
    float distanceFogHeightFalloff = smoothstep(10.0, 0.0, (1-fragPos.y) / (TILE_SIZE * fogHeight));
//    float heightFogFalloff = smoothstep(30.0, 0.0, ((1-(fragPos.y - (2.5 * TILE_SIZE))) / (TILE_SIZE * fogHeight * 0.05)));

    float normalizedFogDistance = (fogDepth / drawDistance);
    float maxDistance = drawDistance * TILE_SIZE;
    float fogStart = maxDistance * (1.0 - normalizedFogDistance);
    float fogFalloff = maxDistance * (1.0 - normalizedFogDistance) * normalizedFogDistance * 2;
    float fogEnd = fogStart + fogFalloff;
    float distanceFog = smoothstep(fogStart, fogEnd, distanceToCamera);

    float fog = mix(0, max(distanceFog, fFogAmount), distanceFogHeightFalloff);

    float fogSpeed = time * 0.0005;
    float noise0 = 0.5 * snoise(vec4(fragPos / (TILE_SIZE), fogSpeed), 1 * 0.25);
    float noise1 = 0.25 * snoise(vec4(fragPos / (TILE_SIZE), -fogSpeed), 2 * 0.25);
    float noise2 = 0.125 * snoise(vec4(fragPos / (TILE_SIZE), fogSpeed), 4 * 0.25);
    float noise = (noise0 + noise1 + noise2) * 0.5 + 0.5;

    if(smoothBanding > 0)
    {
        noise = round(noise * 15) / 15;
    }

    float dyanmicInterpolator = clamp((1 - pow(fog, 2)), 0, 1);

//    image = mix(image, vec3(1,1,1), heightFogFalloff * noise);
    image = mix(image, skyColor.rgb, fog * mix(1, noise, dyanmicInterpolator));
}

void main() {
    Surface s;
    VertexFlags flags;

    PopulateVertexFlags(flags, fFlags);
    PopulateSurfaceColor(s);
    PopulateSurfaceNormal(s, fNormal);

    vec2 sceneUV = (fPosition.xz + (SCENE_OFFSET * TILE_SIZE)) / (TILE_SIZE * EXTENDED_SCENE_SIZE);

    float dither = Dither(gl_FragCoord.xy);
    vec2 resolution = vec2(float(screenWidth), float(screenHeight));
    float ndl = dot(s.normal.xyz, mainLight.pos.xyz) * 0.5 + 0.5;
    float shadowMapSampled = GetShadowMap(fPosition, ndl);

    float distanceToPlayer = length(playerPosition.xy - fPosition.xz);
    float distanceToCamera = length(cameraPosition.xyz - fPosition.xyz);

    vec3 litFragment = s.albedo.rgb * ((mainLight.color.rgb) * ((shadowMapSampled * ndl)) + (ambientColor.rgb));

    vec3 finalColor = CheckIsUnlitTexture(fTextureId) ? s.albedo.rgb : litFragment;

    ApplyFog(finalColor, fPosition, distanceToCamera);
    ApplyAdditiveLighting(finalColor, flags, s.albedo.rgb, s.normal.xyz, fPosition);
    //FadeRoofs(flags, fPosition, dither, distanceToPlayer);

    if(!flags.isDynamicModel)
    {
        DrawMarkedTilesFromMap(finalColor, flags, fPosition, distanceToPlayer);
        DrawTileMarker(finalColor, flags, fPosition, vec4(targetTile.xy, flags.plane, targetTile.w), targetTileFillColor, targetTileOutlineColor, targetTile.z, distanceToPlayer);
        DrawTileMarker(finalColor, flags, fPosition, vec4(hoveredTile.xy, flags.plane, hoveredTile.w), hoveredTileFillColor, hoveredTileOutlineColor, hoveredTile.z, distanceToPlayer);
        DrawTileMarker(finalColor, flags, fPosition, vec4(currentTile.xy, flags.plane, currentTile.w), currentTileFillColor, currentTileOutlineColor, currentTile.z, distanceToPlayer);
    }

    FragColor = vec4(finalColor.rgb, s.albedo.a);
}
