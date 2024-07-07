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

out vec4 FragColor;

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/structs.glsl"
#include "shaders/glsl/uniforms.glsl"
#include "shaders/glsl/hsl_to_rgb.glsl"
#include "shaders/glsl/colorblind.glsl"
#include "shaders/glsl/helpers.glsl"
#include "shaders/glsl/lighting.glsl"


float Dither8x8Bayer( int x, int y ) {
    const float dither[ 64 ] = {
        1, 49, 13, 61,  4, 52, 16, 64,
        33, 17, 45, 29, 36, 20, 48, 32,
        9, 57,  5, 53, 12, 60,  8, 56,
        41, 25, 37, 21, 44, 28, 40, 24,
        3, 51, 15, 63,  2, 50, 14, 62,
        35, 19, 47, 31, 34, 18, 46, 30,
        11, 59,  7, 55, 10, 58,  6, 54,
        43, 27, 39, 23, 42, 26, 38, 22};

    int r = y * 8 + x;
    return dither[r] / 64;
}

float Dither(vec2 screenPos) {
    float dither = Dither8x8Bayer(
        int(mod(screenPos.x, 8)),
        int(mod(screenPos.y, 8))
    );
    return dither;
}

void clip(float value) {
    if(value < 0) discard;
}

bool approximatelyEqual(float a, float b, float epsilon) {
    return abs(a - b) < epsilon;
}

void main() {
    Surface s;
    PopulateSurfaceColor(s);
    PopulateSurfaceNormal(s, fNormal);

    vec2 resolution = vec2(float(screenWidth), float(screenHeight));

    float dither = Dither(gl_FragCoord.xy / 2);
    float shadowMap = GetShadowMap(fPosition);
    shadowMap *= mix(1, (dither + shadowMap), 1-shadowMap);

    float ndl = max(dot(s.normal.xyz, lightDirection), 0);
    vec3 litFragment = s.albedo.rgb * (ndl * shadowMap * lightColor + ambientColor);

    float fog = fFogAmount;
    vec3 finalColor = mix(CheckIsUnlitTexture(fTextureId) ? s.albedo.rgb : litFragment, fogColor.rgb, fog);

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
    //finalColor = vec3(isNonTerrainRoof);

    if(isTerrainRoof || isNonTerrainRoof)
    {
        //finalColor = vec3(.5,0,.5);
        clip(dither - 0.001 - distanceToPlayer);
    }

    //finalColor = s.albedo.rgb * 0.1 + vec3(fNormal.w / 4);
    //finalColor = s.normal.rgb * 0.5 + 0.5;
    //finalColor = vec3(ndl * shadowMap);
    //finalColor = vec3(gl_FragCoord.x, gl_FragCoord.y, 0) / vec3(screenWidth, screenHeight, 1);

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

    PostProcessImage(finalColor, colorBlindMode);
    FragColor = vec4(finalColor, s.albedo.a);
}
