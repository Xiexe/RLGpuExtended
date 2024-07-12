const float bias = 0.00065;
const float lightSize = 0.0035;
const int shadowSamples = 64;

float LinearExponentialAttenuation(float dist, float maxDistance) {
    float linearAttenuation = max(1.0 - dist / maxDistance, 0.0);
    float exponentialAttenuation = exp(-dist * 0.5);
    return linearAttenuation * exponentialAttenuation;
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
    return max(0, estimatedPenumbra);
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
    float penumbraSize = PCSSEstimatePenumbraSize(projCoords, currentDepth, lightSize) * 5;
    penumbraSize += 0.0002;
    float shadow = PCSSFilter(projCoords, currentDepth, penumbraSize);

    return shadow * (1.0 - fadeOut);
}

float PCFShadows(vec4 projCoords, float fadeOut, float shadowBias) {
    float shadow = 0.0;
    float currentDepth = projCoords.z - shadowBias;
    vec2 texelSize = 1.0 / textureSize(shadowMap, 0);

    for(int i = 0; i < shadowSamples; i++) {
        vec2 offset = poissonDisk[i] * texelSize * 20;
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
    float fadeOut = smoothstep(0.75, 1.0, dot(uv, uv));
    if (fadeOut >= 1.0)
        return 1.0;

    switch (envType)
    {
        case ENV_TYPE_DEFAULT:
            return 1.0 - PCSSShadows(projCoords, fadeOut, bias);
        case ENV_TYPE_UNDERGROUND:
            return 1.0 - PCFShadows(projCoords, fadeOut, bias);
        default:
            return 1.0;

    }
}