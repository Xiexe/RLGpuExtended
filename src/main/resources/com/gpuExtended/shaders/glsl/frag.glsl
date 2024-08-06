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

int tile_height(int z, int x, int y) {
    #define ESCENE_OFFSET 40 // (184-104)/2
    return texelFetch(tileHeightMap, ivec3(x + ESCENE_OFFSET, y + ESCENE_OFFSET, z), 0).r << 3;
}

void main() {
    Surface s;
    VertexFlags flags;

    PopulateVertexFlags(flags, fFlags);
    PopulateSurfaceColor(s);
    PopulateSurfaceNormal(s, fNormal);

    vec2 sceneUV = (fPosition.xz + (SCENE_OFFSET * TILE_SIZE)) / (TILE_SIZE * EXTENDED_SCENE_SIZE);
    float tileHeight = tile_height(flags.plane, flags.tileX, flags.tileY);

    float dither = Dither(gl_FragCoord.xy);
    vec2 resolution = vec2(float(screenWidth), float(screenHeight));
    float ndl = dot(s.normal.xyz, mainLight.pos.xyz) * 0.5 + 0.5;
    float shadowMapSampled = GetShadowMap(fPosition, ndl);

    float distanceToPlayer = length(playerPosition.xy - fPosition.xz);
    float distanceToCamera = length(cameraPosition.xyz - fPosition.xyz);

    vec3 litFragment = s.albedo.rgb * ((mainLight.color.rgb) * ((shadowMapSampled * ndl)) + (ambientColor.rgb));

    float fogHeight = 3;
    float distanceFogHeightFalloff = smoothstep(10.0, 0.0, (1-fPosition.y) / (TILE_SIZE * fogHeight));
    float heightFogFalloff = smoothstep(20.0, 0.0, ((1-(fPosition.y - (1 * TILE_SIZE))) / (TILE_SIZE * fogHeight * 0.05)));

    float normalizedFogDistance = (fogDepth / drawDistance);
    float maxDistance = drawDistance * TILE_SIZE;
    float fogStart = maxDistance * (1.0 - normalizedFogDistance);
    float fogFalloff = maxDistance * (1.0 - normalizedFogDistance) * normalizedFogDistance * 2;
    float fogEnd = fogStart + fogFalloff;
    float distanceFog = smoothstep(fogStart, fogEnd, distanceToCamera);

    float fog = mix(0, max(distanceFog, fFogAmount), distanceFogHeightFalloff);
    vec3 finalColor = CheckIsUnlitTexture(fTextureId) ? s.albedo.rgb : litFragment;

    ApplyAdditiveLighting(finalColor, flags, s.albedo.rgb, s.normal.xyz, fPosition);
    //FadeRoofs(flags, fPosition, dither, distanceToPlayer);
    finalColor = mix(finalColor, vec3(1,1,1), heightFogFalloff);
    finalColor = mix(finalColor, skyColor.rgb, fog);


    if(!flags.isDynamicModel)
    {
        DrawMarkedTilesFromMap(finalColor, flags, fPosition, distanceToPlayer);
        DrawTileMarker(finalColor, flags, fPosition, vec4(targetTile.xy, flags.plane, targetTile.w), targetTileFillColor, targetTileOutlineColor, targetTile.z, distanceToPlayer);
        DrawTileMarker(finalColor, flags, fPosition, vec4(hoveredTile.xy, flags.plane, hoveredTile.w), hoveredTileFillColor, hoveredTileOutlineColor, hoveredTile.z, distanceToPlayer);
        DrawTileMarker(finalColor, flags, fPosition, vec4(currentTile.xy, flags.plane, currentTile.w), currentTileFillColor, currentTileOutlineColor, currentTile.z, distanceToPlayer);
    }

    FragColor = vec4(finalColor.rgb, s.albedo.a);
}
