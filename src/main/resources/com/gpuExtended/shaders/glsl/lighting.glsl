const float bias = 0.00065;
const float lightSize = 0.00075;
const int shadowSamples = 16;

// Pre-defined set of sample points for blocker search and PCF
const vec2 poissonDisk[64] = vec2[](
    vec2(-0.499557, 0.035246), vec2(0.227272, -0.179687),
    vec2(0.171875, 0.40625), vec2(-0.132812, -0.375),
    vec2(0.453125, -0.007812), vec2(-0.367188, -0.296875),
    vec2(-0.421875, 0.242188), vec2(0.375, 0.257812),
    vec2(-0.25, -0.039062), vec2(0.296875, -0.40625),
    vec2(-0.085938, 0.117188), vec2(0.140625, -0.4375),
    vec2(-0.492188, -0.15625), vec2(0.226562, 0.132812),
    vec2(-0.335938, 0.429688), vec2(0.46875, -0.210938),
    vec2(0.078125, 0.046875), vec2(-0.210938, 0.085938),
    vec2(0.054688, 0.273438), vec2(-0.257812, -0.132812),
    vec2(0.320312, 0.40625), vec2(-0.492188, 0.382812),
    vec2(-0.09375, -0.492188), vec2(0.375, 0.039062),
    vec2(0.015625, -0.296875), vec2(-0.179688, 0.257812),
    vec2(0.46875, -0.328125), vec2(-0.273438, -0.40625),
    vec2(0.429688, 0.164062), vec2(-0.351562, 0.09375),
    vec2(-0.101562, 0.492188), vec2(0.132812, -0.203125),
    vec2(-0.445312, -0.46875), vec2(0.3125, -0.085938),
    vec2(-0.117188, -0.273438), vec2(0.234375, 0.28125),
    vec2(-0.023438, 0.445312), vec2(0.492188, -0.492188),
    vec2(-0.210938, -0.484375), vec2(0.367188, -0.1875),
    vec2(-0.4375, 0.03125), vec2(0.203125, -0.070312),
    vec2(0.070312, 0.140625), vec2(-0.164062, -0.117188),
    vec2(0.28125, -0.3125), vec2(-0.03125, 0.351562),
    vec2(0.375, 0.375), vec2(-0.492188, -0.375),
    vec2(0.140625, 0.476562), vec2(-0.3125, 0.1875),
    vec2(0.46875, -0.078125), vec2(-0.25, -0.234375),
    vec2(0.09375, 0.40625), vec2(-0.367188, -0.007812),
    vec2(0.445312, 0.320312), vec2(-0.15625, 0.367188),
    vec2(-0.46875, -0.210938), vec2(0.3125, -0.429688),
    vec2(-0.085938, 0.273438), vec2(0.234375, -0.234375),
    vec2(-0.320312, 0.351562), vec2(0.476562, -0.46875),
    vec2(-0.273438, 0.125), vec2(0.078125, -0.015625)
);

float LinearExponentialAttenuation(float dist, float maxDistance) {
    float linearAttenuation = max(1.0 - dist / maxDistance, 0.0);
    float exponentialAttenuation = exp(-dist * 0.5);
    return linearAttenuation * exponentialAttenuation;
}

float PCSSFindBlocker(vec4 projCoords, float currentDepth, float searchRadius) {
    float blockerDepthSum = 0.0;
    int blockerCount = 0;

    for (int i = 0; i < shadowSamples / 2; i++) {
        vec2 offset = poissonDisk[i] * searchRadius;
        float depth = texture(shadowMap, projCoords.xy + offset).r;

        if (depth < currentDepth) {
            blockerDepthSum += depth;
            blockerCount++;
        }
    }

    if (blockerCount == 0) return -1.0;
    return blockerDepthSum / float(blockerCount);
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

float PCSSShadows(vec4 projCoords, float fadeOut) {
    vec2 shadowRes = textureSize(shadowMap, 0);
    float currentDepth = projCoords.z - bias;

    float shadowRenderDistance = MAX_SHADOW_DISTANCE / 100;

    // Blocker search to find average blocker depth
    float searchRadius = 0.003 * shadowRenderDistance;
    float blockerDepth = PCSSFindBlocker(projCoords, currentDepth, searchRadius);

    if (blockerDepth <= -1.0)
        return 0.0; // No blockers, fully lit

    // Estimate penumbra size
    float penumbraSize = (currentDepth - blockerDepth) * lightSize / blockerDepth;
    penumbraSize += 0.00001;
    penumbraSize *= shadowSamples;
    penumbraSize *= shadowRenderDistance;

    // Calculate shadow using PCSS
    float shadow = PCSSFilter(projCoords, currentDepth, penumbraSize);

    return (shadow) * (1.0 - fadeOut);
}

float GetShadowMap(vec3 fragPos) {
    vec4 projCoords = lightProjectionMatrix * vec4(fragPos, 1);
    projCoords = projCoords / projCoords.w;
    projCoords = projCoords * 0.5 + 0.5;

    vec2 uv = projCoords.xy * 2.0 - 1.0;
    float fadeOut = smoothstep(0.95, 1.0, dot(uv, uv));

    float pcssShadowSample = PCSSShadows(projCoords, fadeOut);

    return 1-pcssShadowSample;
}