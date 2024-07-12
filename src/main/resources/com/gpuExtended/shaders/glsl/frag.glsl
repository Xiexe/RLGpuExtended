#version 420

in vec4 fColor;
in vec4 fNormal;
noperspective centroid in float fHsl;
flat in int fTextureId;
in vec2 fUv;
in vec3 fPosition;
in float fFogAmount;

in float fPlane;
in float fIsBridge;
in float fIsRoof;
in float fIsTerrain;
in float fIsDyanmicModel;

out vec4 FragColor;

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/structs.glsl"
#include "shaders/glsl/uniforms.glsl"
#include "shaders/glsl/hsl_to_rgb.glsl"
#include "shaders/glsl/colorblind.glsl"
#include "shaders/glsl/helpers.glsl"
#include "shaders/glsl/lighting.glsl"

vec3 TranslatePositionToScene(vec3 position)
{
    vec2 normalizedPositionXZ = (position.xz + (SCENE_OFFSET * TILE_SIZE)) / (TILE_SIZE * EXTENDED_SCENE_SIZE);
    return vec3(normalizedPositionXZ, position.y);
}

void main() {
    Surface s;
    PopulateSurfaceColor(s);
    PopulateSurfaceNormal(s, fNormal);

    float dither = Dither(gl_FragCoord.xy);
    vec2 resolution = vec2(float(screenWidth), float(screenHeight));
    float ndl = max(dot(s.normal.xyz, mainLight.pos.xyz), 0);

    float shadowTex = GetShadowMap(fPosition, ndl);
    float distanceToPlayer = length(playerPosition.xy - fPosition.xz);
    float distanceToCamera = length(cameraPosition.xyz - fPosition.xyz);
    float shadow = shadowTex * ndl;

    vec3 litFragment = s.albedo.rgb * (ndl * shadowTex * mainLight.color.rgb + ambientColor.rgb);

    float fog = fFogAmount;
    vec3 finalColor = CheckIsUnlitTexture(fTextureId) ? s.albedo.rgb : litFragment;

    ApplyAdditiveLighting(finalColor, s.albedo.rgb, s.normal.xyz, fPosition);
    FadeRoofs(dither, distanceToPlayer);
    PostProcessImage(finalColor, colorBlindMode, fog);
    finalColor = mix(finalColor, skyColor.rgb, fog);

    if(!(fIsDyanmicModel > 0))
    {
        DrawMarkedTilesFromMap(finalColor, fPosition, fPlane, distanceToPlayer);
        DrawTileMarker(finalColor, fPosition, vec4(targetTile.xy, fPlane, targetTile.w), targetTileFillColor, targetTileOutlineColor, targetTile.z, distanceToPlayer);
        DrawTileMarker(finalColor, fPosition, vec4(hoveredTile.xy, fPlane, hoveredTile.w), hoveredTileFillColor, hoveredTileOutlineColor, hoveredTile.z, distanceToPlayer);
        DrawTileMarker(finalColor, fPosition, vec4(currentTile.xy, fPlane, currentTile.w), currentTileFillColor, currentTileOutlineColor, currentTile.z, distanceToPlayer);
    }
    FragColor = vec4(finalColor, s.albedo.a);
}
