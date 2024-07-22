#version 420

in vec4 fColor;
in vec4 fNormal;
noperspective centroid in float fHsl;
flat in int fTextureId;
in vec2 fUv;
in vec3 fPosition;
in float fFogAmount;

in flat ivec4 fFlags;
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

vec4 boxBlur(sampler2D tex, vec2 uv, vec2 texelSize) {
    vec4 color = vec4(0.0);

    // Sample the surrounding texels
    color += texture(tex, uv + vec2(-texelSize.x, -texelSize.y));
    color += texture(tex, uv + vec2(0.0, -texelSize.y));
    color += texture(tex, uv + vec2(texelSize.x, -texelSize.y));

    color += texture(tex, uv + vec2(-texelSize.x, 0.0));
    color += texture(tex, uv);
    color += texture(tex, uv + vec2(texelSize.x, 0.0));

    color += texture(tex, uv + vec2(-texelSize.x, texelSize.y));
    color += texture(tex, uv + vec2(0.0, texelSize.y));
    color += texture(tex, uv + vec2(texelSize.x, texelSize.y));

    // Average the colors
    color /= 9.0;

    return color;
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
    float ndl = max(dot(s.normal.xyz, mainLight.pos.xyz), 0);

   // vec4 tileMask = boxBlur(tileMaskTexture, sceneUV - 0.00001, 0.5 / textureSize(tileMaskTexture, 0).xy);
    float shadowTex = GetShadowMap(fPosition, ndl);

    float distanceToPlayer = length(playerPosition.xy - fPosition.xz);
    float distanceToCamera = length(cameraPosition.xyz - fPosition.xyz);
    float shadow = (shadowTex * ndl);

    vec3 litFragment = s.albedo.rgb * (shadow * mainLight.color.rgb + ambientColor.rgb);

    float fog = fFogAmount;
    vec3 finalColor = CheckIsUnlitTexture(fTextureId) ? s.albedo.rgb : litFragment;

    ApplyAdditiveLighting(finalColor, s.albedo.rgb, s.normal.xyz, fPosition);
    FadeRoofs(flags, fPosition, dither, distanceToPlayer);
    PostProcessImage(finalColor, colorBlindMode, fog);
    finalColor = mix(finalColor, skyColor.rgb, fog);

    if(!flags.isDynamicModel)
    {
        DrawMarkedTilesFromMap(finalColor, flags, fPosition, distanceToPlayer);
        DrawTileMarker(finalColor, flags, fPosition, vec4(targetTile.xy, flags.plane, targetTile.w), targetTileFillColor, targetTileOutlineColor, targetTile.z, distanceToPlayer);
        DrawTileMarker(finalColor, flags, fPosition, vec4(hoveredTile.xy, flags.plane, hoveredTile.w), hoveredTileFillColor, hoveredTileOutlineColor, hoveredTile.z, distanceToPlayer);
        DrawTileMarker(finalColor, flags, fPosition, vec4(currentTile.xy, flags.plane, currentTile.w), currentTileFillColor, currentTileOutlineColor, currentTile.z, distanceToPlayer);
    }

//    finalColor = vec3(fFlags.z == 1);
    //finalColor = vec3(float(flags.tileX) / EXTENDED_SCENE_SIZE, float(flags.tileY) / EXTENDED_SCENE_SIZE, 0.0);
    //finalColor = vec3(float(flags.plane) / 4.);
    //finalColor = vec3(flags.isTerrain);


//    finalColor = vec3(s.albedo);

    //finalColor = vec3(fColor);
    FragColor = vec4(finalColor.rgb, s.albedo.a);
}
