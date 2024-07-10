#version 420

in vec4 fColor;
in vec4 fNormal;
noperspective centroid in float fHsl;
flat in int fTextureId;
in vec2 fUv;
in vec3 fPosition;
in float fFogAmount;

in float fPlane;
in float fOnBridge;
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
    float shadowTex = GetShadowMap(fPosition);
    float depthTex = 1-texture(depthMap, gl_FragCoord.xy / resolution).r;

    float ndl = max(dot(s.normal.xyz, mainLight.pos.xyz), 0);
    float shadow = shadowTex * ndl;

    vec3 litFragment = s.albedo.rgb * (ndl * shadowTex * mainLight.color.rgb + ambientColor.rgb);

    float fog = fFogAmount;
    vec3 finalColor = mix(CheckIsUnlitTexture(fTextureId) ? s.albedo.rgb : litFragment, fogColor.rgb, 0);

    vec3 playerPos = vec3(0,0,0);
    playerPos.x = ((playerPosition.x - sceneOffsetX) + 0.5f) * TILE_SIZE;
    playerPos.z = ((playerPosition.y - sceneOffsetZ) + 0.5f) * TILE_SIZE;
    playerPos.y = playerPosition.z - (TILE_SIZE * 1.5);

    float distanceToPlayer = length(playerPosition.xy - fPosition.xz);
    distanceToPlayer = smoothstep(1700, 1000, distanceToPlayer);

    bool isOnBridge = fOnBridge > 0;
    float realPlane = max(0, fPlane - (isOnBridge ? 1 : 0));

    bool isOnSamePlane = approximatelyEqual(realPlane, playerPosition.z, 0.001);
    bool isAbovePlayer = realPlane > playerPosition.z;
    bool isUnderPlayer = realPlane < playerPosition.z;

    bool isTerrainRoof = fIsRoof > 0 && fIsTerrain > 0 && !isOnSamePlane && !isUnderPlayer;
    bool isNonTerrainRoof = fIsRoof > 0 && !(fIsTerrain > 0) && isAbovePlayer;

    //TODO:: check tiles around terrain roofs to see if they are roofs.
    if(isTerrainRoof || isNonTerrainRoof)
    {
        //finalColor = vec3(.5,0,.5);
       clip((dither - 0.001 - distanceToPlayer));
    }
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

    if(!(fIsDyanmicModel > 0))
    {
        DrawTileMarker(finalColor, fPosition, vec3(currentTile.xy, fPlane), 0.025f);
        DrawTileMarker(finalColor, fPosition, vec3(targetTile.xy, fPlane), 0.025f);
        DrawTileMarker(finalColor, fPosition, vec3(hoveredTile.xy, fPlane), 0.025f);
    }

    PostProcessImage(finalColor, colorBlindMode, fog);
    FragColor = vec4(finalColor, s.albedo.a);
}
