const float bias = 0.0008;
const float lightSize = 0.01;
const int shadowSamples = 12;

float LightAttenuation(float dist, float radius) {
    return pow(clamp(1.0 - (dist * dist) / (radius * radius), 0.0, 1.0), 5);
}

float PCSSEstimatePenumbraSize(sampler2D shadowTex, vec4 projCoords, float currentDepth, float searchRadius) {
    float blockerDepthSum = 0.0;
    int blockerCount = 0;

    for (int i = 0; i < shadowSamples; i++) {
        vec2 offset = poissonDisk[i] * searchRadius;
        float depth = texture(shadowTex, projCoords.xy + offset).r;

        if (depth < currentDepth) {
            blockerDepthSum += depth;
            blockerCount++;
        }
    }

    blockerDepthSum /= float(blockerCount);

    float estimatedPenumbra = (currentDepth - blockerDepthSum) * lightSize / blockerDepthSum;
    return max(0, estimatedPenumbra);
}

float PCSSFilter(sampler2D shadowTex, vec4 projCoords, float currentDepth, float penumbraSize) {
    float shadow = 0.0;

    for (int i = 0; i < shadowSamples; i++) {
        vec2 offset = poissonDisk[i] * penumbraSize;
        float depth = texture(shadowTex, projCoords.xy + offset).r;

        if (currentDepth > depth)
            shadow += 1.0;
    }

    return shadow / float(shadowSamples);
}

float PCSSShadows(sampler2D shadowTex, vec4 projCoords, float fadeOut, float shadowBias) {
    float fudgeFactor = 2;
    vec2 shadowRes = textureSize(shadowTex, 0);
    float currentDepth = projCoords.z - shadowBias;
    float penumbraSize = PCSSEstimatePenumbraSize(shadowTex, projCoords, currentDepth, lightSize) * fudgeFactor;
    float shadow = PCSSFilter(shadowTex, projCoords, currentDepth, penumbraSize + 0.0004);

    return shadow * (1.0 - fadeOut);
}

float PCFShadows(sampler2D shadowTex, vec4 projCoords, float fadeOut, float shadowBias, float spread) {
    float shadow = 0.0;
    float currentDepth = projCoords.z - shadowBias;
    vec2 texelSize = 1.0 / textureSize(shadowTex, 0);

    for(int i = 0; i < shadowSamples; i++) {
        vec2 offset = poissonDisk[i] * spread;
        float pcfDepth = texture(shadowTex, projCoords.xy + offset).r;
        shadow += currentDepth > pcfDepth ? 1.0 : 0.0;
    }

    shadow /= shadowSamples;

    return shadow * (1.0 - fadeOut);
}

float GetShadowMap(sampler2D shadowTex, vec3 fragPos, float ndl) {
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
        if (shadowMode == SHADOW_MODE_PCSS)
            return 1.0 - PCSSShadows(shadowTex, projCoords, fadeOut, bias);
        else
            return 1.0 - PCFShadows(shadowTex, projCoords, fadeOut, bias, 0.001);

        case ENV_TYPE_UNDERGROUND:
            return 1.0 - PCFShadows(shadowTex, projCoords, fadeOut, bias, 0.0025);
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
        float flicker = sin((time / 75) - hash) * 0.01 + 1;
        float flicker2 = sin((time / 45) - hash * 2) * 0.01 + 1;
        light.intensity *= flicker * flicker2;
        light.radius *= (flicker * flicker2);
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

void ApplyAdditiveLighting(inout vec3 image, VertexFlags flags, vec3 albedo, vec3 normal, vec3 fragPos, float tileHeights)
{
    int numLights = lightBinIndicies[getLightBinIndex(LIGHTS_BIN_NUM_LIGHTS_INDEX, flags.tileX, flags.tileY, flags.plane)];
    vec3 lightDebug = vec3(float(numLights) / float(LIGHTS_PER_TILE - 1));
    //image = mix(image, vec3(1), numLights);
//    image = lightDebug * numLights;

//    image = vec3(0);
    if(numLights == 0) return;

    for(int binIndex = 0; binIndex < numLights; binIndex++)
    {
        int oneDIndex = getLightBinIndex(binIndex, flags.tileX, flags.tileY, flags.plane);
        int lightIndex = lightBinIndicies[oneDIndex];
        if(lightIndex >= 0) {
            Light light = additiveLights[lightIndex];
//            light.pos.z += tileHeights;

            vec3 toLight = ((light.pos.xyz / TILE_SIZE) - (fragPos.xzy / TILE_SIZE));
            float distToLight = length(toLight);

            toLight = normalize(toLight);
            toLight.z = -toLight.z;

            float bandWidth = 0.04f * light.radius;
            AnimateLight(light, bandWidth);

            if((smoothBanding > 0))
            {
                distToLight /= bandWidth;
                distToLight = floor(distToLight) * bandWidth;
            }

            float atten = LightAttenuation(distToLight, light.radius);
            float ndl = max(dot(normal.xzy, toLight), 0);
            image += albedo.rgb * light.color.rgb * light.intensity * ndl * atten;
//            image = vec3(ndl, ndl, ndl);
        }
    }
}