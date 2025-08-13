#version 430

layout(std430, binding = 0) readonly buffer LightBinningBlock {
    int lightBinIndicies[];
};

in vec4 fColor;
in vec4 fNormal;
in vec4 fFlatNormal;
noperspective centroid in float fHsl;
in vec3 fLinearRgb;
flat in int fTextureId;
in vec2 fUv;
in vec3 fPosition;
in float fFogAmount;
in float fSurfaceDepth;
in flat int isEmissive;
in flat ivec4 fFlags;
in vec2 fBarycentricCoordinate;

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

    float normalizedFogDistance = (fogDepth / drawDistance);
    float maxDistance = drawDistance * TILE_SIZE;
    float fogStart = maxDistance * (1.0 - normalizedFogDistance);
    float fogFalloff = maxDistance * (1.0 - normalizedFogDistance) * normalizedFogDistance;
    float distanceFog = smoothstep(fogStart - fogFalloff, fogStart + fogFalloff, distanceToCamera);

    float fog = mix(0, max(distanceFog, fFogAmount), distanceFogHeightFalloff);

    float fogSpeed = time / 1000;
    float noise0 = 0.5 * snoise(vec4(fragPos / (TILE_SIZE), fogSpeed), 1 * 0.25);
    float noise1 = 0.25 * snoise(vec4(fragPos / (TILE_SIZE), -fogSpeed), 2 * 0.25);
//    float noise2 = 0.125 * snoise(vec4(fragPos / (TILE_SIZE), fogSpeed), 4 * 0.25);
    float noise = (noise0 + noise1) * 0.5 + 0.5;

    if(smoothBanding > 0)
    {
        noise = round(noise * 15) / 15;
    }

    float dyanmicInterpolator = clamp(1 - fog, 0, 1);
    image = mix(image, skyColor.rgb, fog * mix(1, noise, dyanmicInterpolator));
}

void main() {
    Surface s;
    VertexFlags flags;

    PopulateVertexFlags(flags, fFlags);
    PopulateSurfaceColor(s);
    PopulateSurfaceNormal(s, flags, fNormal, fFlatNormal);
    float distanceToPlayer = length(playerPosition.xy - fPosition.xz);
    float distanceToCamera = length(cameraPosition.xyz - fPosition.xyz);

//    if (flags.objectType == TYPE_GROUND_OBJECT) {
//        float MAX_DISTANCE_IN_TILES = 32.0 * TILE_SIZE;
//        float fade = smoothstep(MAX_DISTANCE_IN_TILES * 0.8, MAX_DISTANCE_IN_TILES, distanceToPlayer);
//        s.albedo.a *= 1-fade;
//    }

    vec2 sceneUV = (fPosition.xz + (SCENE_OFFSET * TILE_SIZE)) / (TILE_SIZE * EXTENDED_SCENE_SIZE);
    float tileHeightmap = GetTileHeight(vec3(sceneUV, flags.plane));

    float dither = Dither(gl_FragCoord.xy);
    vec2 resolution = vec2(float(screenWidth), float(screenHeight));
    float ndl = max(dot(s.normal.xyz, mainLight.pos.xyz), 0);

    float staticShadowSpread = mix(0.0008, 3.0, shadowMode == SHADOW_MODE_PCSS);
    float dynamicShadowSpread = mix(0.0008, 3.0, shadowMode == SHADOW_MODE_PCSS) * (MAX_SHADOW_DISTANCE/shadowDistance);

    float staticShadowMap = GetShadowMap(shadowMap, mainLight.projectionMatrix, fPosition, ndl, staticShadowSpread, false);
    float dynamicShadowMap = GetShadowMap(dynamicShadowMap, mainLight.projectionMatrixClose, fPosition, ndl, dynamicShadowSpread, true);
    float combinedShadowMap = min(staticShadowMap, dynamicShadowMap);

    vec3 diffuse = s.albedo.rgb * ndl;
    vec3 lighting = diffuse * mainLight.color.rgb * combinedShadowMap;
    vec3 litFragment = lighting.rgb + ambientColor.rgb * s.albedo.rgb;
    ApplyAdditiveLighting(litFragment, flags, s.albedo.rgb, s.normal.xyz, fPosition, tileHeightmap);

    vec3 finalColor = CheckIsUnlitTexture(fTextureId) ? s.albedo.rgb : litFragment;
    ApplyFog(finalColor, fPosition, distanceToCamera);

    if(!flags.isDynamicModel)
    {
        float transitionRange = 16;
        float tileHeightMarkerRangeEdge0 = tileHeightmap - transitionRange * 2;
        float tileHeightMarkerRangeEdge1 = tileHeightmap - transitionRange * 0.5;
        float markerIntensity = smoothstep(tileHeightMarkerRangeEdge0, tileHeightMarkerRangeEdge1, fPosition.y);

//        float height = GetTileHeight(vec3(fPosition.x + sceneOffsetX, fPosition.z + sceneOffsetZ, flags.plane));
        DrawMarkedTilesFromMap(finalColor, flags, fPosition, distanceToPlayer, markerIntensity);
        DrawTileMarker(finalColor, flags, fPosition, vec4(targetTile.xy, flags.plane, targetTile.w), targetTileFillColor, targetTileOutlineColor, targetTile.z, distanceToPlayer, markerIntensity);
        DrawTileMarker(finalColor, flags, fPosition, vec4(hoveredTile.xy, flags.plane, hoveredTile.w), hoveredTileFillColor, hoveredTileOutlineColor, hoveredTile.z, distanceToPlayer, markerIntensity);
        DrawTileMarker(finalColor, flags, fPosition, vec4(currentTile.xy, flags.plane, currentTile.w), currentTileFillColor, currentTileOutlineColor, currentTile.z, distanceToPlayer, markerIntensity);
    }

    vec4 outColor = vec4(finalColor, s.albedo.a);

    float wireFrame = GetWireframe(fBarycentricCoordinate, 0.25, 1);
    float wireAlpha = showWireframe == 1 ? wireFrame : 0.0;

    outColor = mix(outColor, vec4(vec3(0.25), 1.0), wireAlpha);
    FragColor = outColor;

//    bool matches = (flags.objectType == TYPE_OBJECT);
//    FragColor = vec4(vec3(flags.objectType == TYPE_GROUND_OBJECT), 1.0);
//    FragColor = vec4(vec3(combinedShadowMap), 1);
}
