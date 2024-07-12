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

void main() {
    Surface s;
    PopulateSurfaceColor(s);
    PopulateSurfaceNormal(s, fNormal);

    vec2 resolution = vec2(float(screenWidth), float(screenHeight));

    float dither = Dither(gl_FragCoord.xy / 2);
    float ndl = max(dot(s.normal.xyz, mainLight.pos.xyz), 0);

    float shadowTex = GetShadowMap(fPosition, ndl);
    float distanceToPlayer = length(playerPosition.xy - fPosition.xz);
    float distanceToCamera = length(cameraPosition.xyz - fPosition.xyz);

    float shadow = shadowTex * ndl;

    vec3 litFragment = s.albedo.rgb * (ndl * shadowTex * mainLight.color.rgb + ambientColor.rgb);

    float fog = fFogAmount;
    vec3 finalColor = CheckIsUnlitTexture(fTextureId) ? s.albedo.rgb : litFragment;
        //
//    finalColor = vec3(cameraPosition);

//    for(int i = 0; i < LIGHT_COUNT; i++)
//    {
//        Light light = LightsArray[i];
//        if(light.type == LIGHT_TYPE_INVALID) break;
//        light.radius += 2; // just a magic number to make radius match tile widths
//
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
//            finalColor += s.albedo.rgb * light.color.rgb * ndl * atten;
//        }
//    }

    FadeRoofs(dither, distanceToPlayer);
    PostProcessImage(finalColor, colorBlindMode, fog);
    vec2 sceneUV = (fPosition.xz + (SCENE_OFFSET * TILE_SIZE)) / (TILE_SIZE * EXTENDED_SCENE_SIZE);

    finalColor = mix(finalColor, skyColor.rgb, fog);

//    if(sceneUV.x > 1 || sceneUV.y > 1 || sceneUV.x < 0 || sceneUV.y < 0)
//    {
//        finalColor = vec3(1,0,1);
//    }
//    else
//    {
//        finalColor = vec3(sceneUV, 0.0);
//    }

    if(!(fIsDyanmicModel > 0))
    {
        DrawMarkedTilesFromMap(finalColor, fPosition, fPlane, distanceToPlayer);
        DrawTileMarker(finalColor, fPosition, vec4(targetTile.xy, fPlane, targetTile.w), targetTileFillColor, targetTileOutlineColor, targetTile.z, distanceToPlayer);
        DrawTileMarker(finalColor, fPosition, vec4(hoveredTile.xy, fPlane, hoveredTile.w), hoveredTileFillColor, hoveredTileOutlineColor, hoveredTile.z, distanceToPlayer);
        DrawTileMarker(finalColor, fPosition, vec4(currentTile.xy, fPlane, currentTile.w), currentTileFillColor, currentTileOutlineColor, currentTile.z, distanceToPlayer);
    }
    FragColor = vec4(finalColor, s.albedo.a);
}
