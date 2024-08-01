#version 430

#include "shaders/glsl/structs.glsl"
#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/uniforms.glsl"

layout(std430, binding = 0) buffer _lightbinning {
    int lightBinIndiciesOut[];
};

int getLightBinIndex(int binSubIndex, int tileX, int tileY, int tileZ)
{
    return
    binSubIndex +
    tileZ * (LIGHTS_PER_TILE+1) +
    tileY * MAX_Z_HEIGHT * (LIGHTS_PER_TILE+1) +
    tileX * EXTENDED_SCENE_SIZE * MAX_Z_HEIGHT * (LIGHTS_PER_TILE+1);
}

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;
void main() {
    ivec3 tileIndex = ivec3(gl_GlobalInvocationID.xyz);
    ivec3 tilePosition = ivec3(gl_GlobalInvocationID.xyz);
    tilePosition.xy -= SCENE_OFFSET;

    int numLightsTile = 0;
    // writing to last index so it doesnt get overwritten later with a light??? (on purpose)
    int lastLightIndex = getLightBinIndex(LIGHTS_BIN_NUM_LIGHTS_INDEX, tileIndex.x, tileIndex.y, tileIndex.z);
    lightBinIndiciesOut[lastLightIndex] = 0;

    for(int i = 0; i < LIGHT_COUNT; i++)
    {
        Light light = additiveLights[i];
        vec3 lightPos = light.pos.xyz;
        lightPos.xy /= TILE_SIZE;
        if(light.intensity == 0) continue;

        float lightRadius = light.radius;
        float distanceTileToLight = distance(lightPos.xy, vec2(tilePosition.xy) + vec2(0.5));

        if(distanceTileToLight <= lightRadius) {
            for(int lightArrayIndex = 0; lightArrayIndex < LIGHTS_PER_TILE; lightArrayIndex++)
            {
                int oneDIndex = getLightBinIndex(lightArrayIndex, tileIndex.x, tileIndex.y, tileIndex.z);

                if(lightBinIndiciesOut[oneDIndex] < 0)
                {
                    lightBinIndiciesOut[oneDIndex] = i;
                    numLightsTile++;
                    break;
                }
            }
        }
    }

    // write number of lights to last index
    lightBinIndiciesOut[lastLightIndex] = numLightsTile;
}