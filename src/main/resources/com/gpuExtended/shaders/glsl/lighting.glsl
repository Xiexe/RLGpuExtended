const float bias = 0.00065;
const float lightSize = 0.0025;
const int shadowSamples = 32;

float LightAttenuation(float dist, float radius) {
    return clamp(1.0 - ((dist * dist) / (radius * radius)), 0.0, 1.0);
}

float PCSSEstimatePenumbraSize(vec4 projCoords, float currentDepth, float searchRadius) {
    float blockerDepthSum = 0.0;
    int blockerCount = 0;

    for (int i = 0; i < shadowSamples; i++) {
        vec2 offset = poissonDisk[i] * searchRadius;
        float depth = texture(shadowMap, projCoords.xy + offset).r;

        if (depth < currentDepth) {
            blockerDepthSum += depth;
            blockerCount++;
        }
    }

    blockerDepthSum /= float(blockerCount);

    float estimatedPenumbra = (currentDepth - blockerDepthSum) * lightSize / blockerDepthSum;
    return max(0, estimatedPenumbra + 0.00001);
}

float PCSSFilter(vec4 projCoords, float currentDepth, float penumbraSize) {
    float shadow = 0.0;

    for (int i = 0; i < shadowSamples; i++) {
        vec2 offset = poissonDisk[i] * penumbraSize;
        float depth = texture(shadowMap, projCoords.xy + offset).r;

        if (currentDepth > depth)
            shadow += 1.0;
    }

    return shadow / float(shadowSamples);
}

float PCSSShadows(vec4 projCoords, float fadeOut, float shadowBias) {
    vec2 shadowRes = textureSize(shadowMap, 0);
    float currentDepth = projCoords.z - shadowBias;
    float penumbraSize = PCSSEstimatePenumbraSize(projCoords, currentDepth, lightSize) * 10;
    float shadow = PCSSFilter(projCoords, currentDepth, penumbraSize);

    return shadow * (1.0 - fadeOut);
}

float PCFShadows(vec4 projCoords, float fadeOut, float shadowBias, float spread) {
    float shadow = 0.0;
    float currentDepth = projCoords.z - shadowBias;
    vec2 texelSize = 1.0 / textureSize(shadowMap, 0);

    for(int i = 0; i < shadowSamples; i++) {
        vec2 offset = poissonDisk[i] * spread;
        float pcfDepth = texture(shadowMap, projCoords.xy + offset).r;
        shadow += currentDepth > pcfDepth ? 1.0 : 0.0;
    }

    shadow /= shadowSamples;

    return shadow * (1.0 - fadeOut);
}

float GetShadowMap(vec3 fragPos, float ndl) {
    vec4 projCoords = mainLight.projectionMatrix * vec4(fragPos, 1);
    projCoords = projCoords / projCoords.w;
    projCoords = projCoords * 0.5 + 0.5;

    vec2 uv = projCoords.xy * 2.0 - 1.0;
    float fadeOut = smoothstep(0.85, 1.0, dot(uv, uv));

    if (fadeOut >= 1.0)
        return 1.0;

    switch (envType)
    {
        case ENV_TYPE_DEFAULT:
            return 1.0 - PCSSShadows(projCoords, fadeOut, bias);
        case ENV_TYPE_UNDERGROUND:
            return 1.0 - PCFShadows(projCoords, fadeOut, bias, 0.005);
        default:
            return 0.0;
    }
}

void AnimateLight(inout Light light, inout float bandWidth)
{
    if(light.animation == LIGHT_ANIM_NONE) return;
    float hash = light.offset.w / 2000;

    switch(light.animation)
    {
        case LIGHT_ANIM_FLICKER:
        float flicker = sin((time / 75) - hash) * 0.05 + 0.95;
        float flicker2 = sin((time / 45) - hash * 2) * 0.05 + 0.95;
        light.intensity *= flicker * flicker2;
        break;

        case LIGHT_ANIM_PULSE:
        float pulse = sin((time / 500) - hash) * 0.5 + 1.5;
        light.intensity *= pulse;
        break;
    }
}

int getLightBinIndex(int binSubIndex, int tileX, int tileY, int tileZ)
{
    return
        binSubIndex +
        tileZ * (LIGHTS_PER_TILE+1) +
        tileY * MAX_Z_HEIGHT * (LIGHTS_PER_TILE+1) +
        tileX * EXTENDED_SCENE_SIZE * MAX_Z_HEIGHT * (LIGHTS_PER_TILE+1);
}

vec3 imaBandEdge(float bandDistance, float distToLight)
{
    vec3 band = vec3(0);
    if(distToLight > bandDistance * 0.999 && distToLight < bandDistance * 1.001)
    {
        band = vec3(1,0,1);
    }

    return band;
}

void ApplyAdditiveLighting(inout vec3 image, VertexFlags flags, vec3 albedo, vec3 normal, vec3 fragPos)
{
    int numLights = lightBinIndicies[getLightBinIndex(LIGHTS_BIN_NUM_LIGHTS_INDEX, flags.tileX, flags.tileY, flags.plane)];
//    vec3 lightDebug = vec3(float(numLights) / float(LIGHTS_PER_TILE - 1));
//    //    image = mix(image, vec3(1), lightDebug);
//    image = lightDebug;

//    image = vec3(0);
    if(numLights == 0) return;

    for(int binIndex = 0; binIndex < numLights; binIndex++)
    {
        int oneDIndex = getLightBinIndex(binIndex, flags.tileX, flags.tileY, flags.plane);
        int lightIndex = lightBinIndicies[oneDIndex];
        if(lightIndex >= 0) {
            Light light = additiveLights[lightIndex];

            vec3 toLight = ((light.pos.xyz / TILE_SIZE) - (fragPos.xzy / TILE_SIZE));
            float distToLight = length(toLight);

            toLight = normalize(toLight);
            toLight.z = -toLight.z;


            float bandWidth = 0.04f * light.radius;
            AnimateLight(light, bandWidth);

            if((smoothBanding > 0))
            {
//                for(int i = 0; i < floor(light.radius / bandWidth); i++)
//                {
//                    image += imaBandEdge(i*bandWidth, distToLight);
//                }

                distToLight /= bandWidth;
                distToLight = floor(distToLight) * bandWidth;
            }


            float atten = LightAttenuation(distToLight, light.radius);
            float ndl = max(dot(normal.xzy, toLight), 0);
            image += albedo.rgb * light.color.rgb * light.intensity * ndl * atten;
        }
    }
}