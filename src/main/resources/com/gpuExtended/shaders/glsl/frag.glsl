#version 420

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

// Simple noise function
float noise(vec2 p) {
    return fract(sin(dot(p ,vec2(127.1,311.7))) * 43758.5453);
}

// Interpolated noise
float smoothNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f*f*(3.0-2.0*f);
    float n = mix(mix(noise(i + vec2(0.0, 0.0)), noise(i + vec2(1.0, 0.0)), f.x),
    mix(noise(i + vec2(0.0, 1.0)), noise(i + vec2(1.0, 1.0)), f.x), f.y);
    return n;
}

// Raymarching to calculate the distance to the underwater surface
float raymarch(vec3 ro, vec3 rd) {
    float depth = 0.0;
    for (int i = 0; i < 100; i++) {
        vec3 p = ro + depth * rd;
        float h = smoothNoise(p.xz * 0.1 + vec2(time * 0.1, 0.0)) * 0.5;
        if (p.y < h) break;
        depth += 0.1;
    }
    return depth;
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
    float shadowMap = GetShadowMap(fPosition, ndl);

    float distanceToPlayer = length(playerPosition.xy - fPosition.xz);
    float distanceToCamera = length(cameraPosition.xyz - fPosition.xyz);

    vec3 litFragment = s.albedo.rgb * (mainLight.color.rgb * ((shadowMap * ndl)) + ambientColor.rgb);

    float maxDistance = drawDistance * TILE_SIZE;
    float fogStart = maxDistance * (1.0 - (fogDepth / drawDistance));
    float fogFalloff = maxDistance * (1.0 - (fogDepth / drawDistance) * 0.5); // Adjust the 0.9 to control the range of falloff
    float fogEnd = fogStart + fogFalloff;
    float distanceFog = smoothstep(fogStart, fogEnd, distanceToCamera);

    float fog = distanceFog;
    vec3 finalColor = CheckIsUnlitTexture(fTextureId) ? s.albedo.rgb : litFragment;

    ApplyAdditiveLighting(finalColor, s.albedo.rgb, s.normal.xyz, fPosition);
    FadeRoofs(flags, fPosition, dither, distanceToPlayer);
    PostProcessImage(finalColor, colorBlindMode, fog, isEmissive);
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
//    finalColor = vec3(float(flags.plane) / 4.);
//    finalColor = vec3(flags.isBridge);
    //finalColor = vec3(flags.isTerrain);
//    finalColor = vec3(isEmissive);
//    finalColor = vec3(flags.isDynamicModel);
//    finalColor = vec3(s.normal.rgb * 0.5 + 0.5);

//    if(flags.isTerrain)
//    {
//        finalColor *= vec3(fSurfaceDepth);
//    }

    FragColor = vec4(finalColor.rgb, s.albedo.a);
}
