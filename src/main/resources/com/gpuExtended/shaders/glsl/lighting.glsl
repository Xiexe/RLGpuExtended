const float bias = 0.00065;
const float lightSize = 0.0035;
const int shadowSamples = 32;

float LinearAttenuation(float dist, float maxDistance) {
    float linearAttenuation = max(1.0 - dist / maxDistance, 0.0);
    return linearAttenuation;
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
    float fadeOut = smoothstep(0.85, 1.0, dot(uv, uv));

    if (fadeOut >= 1.0)
        return 1.0;

    switch (envType)
    {
        case ENV_TYPE_DEFAULT:
            return 1.0 - PCSSShadows(projCoords, fadeOut, bias);
        case ENV_TYPE_UNDERGROUND:
            return 1.0 - PCFShadows(projCoords, fadeOut, bias);
        default:
            return 0.0;
    }
}

vec3 OffsetLight(Light light)
{
    vec3 pos = light.pos.xyz;
    vec3 offset = light.offset.xyz * TILE_SIZE;
    offset.x = -offset.x;

    int orientation = int(light.pos.w);
    switch (orientation)
    {
        case 0: // Rotated 180 degrees
        pos.x -= offset.x;
        pos.y -= offset.y;
        break;

        case 1: // Rotated 90 degrees counter-clockwise
        pos.x -= offset.y;
        pos.y += offset.x;
        break;

        case 2: // Not rotated
        pos.x += offset.x;
        pos.y += offset.y;
        break;

        case 3: // Rotated 90 degrees clockwise
        pos.x += offset.y;
        pos.y -= offset.x;
        break;
    }

    pos.z -= offset.z;
    return pos;
}

void ApplyLightAnimation(inout Light light)
{
    if(light.animation == LIGHT_ANIM_NONE) return;

    float hashXY = hash21(vec2(light.pos.x, light.pos.z)) * (EXTENDED_SCENE_SIZE * TILE_SIZE);
    float hashHZ = hash21(vec2(light.pos.y, light.pos.y)) * (EXTENDED_SCENE_SIZE * TILE_SIZE);
    float offset = (hashXY + hashHZ);

    switch(light.animation)
    {
        case LIGHT_ANIM_FLICKER:
        float flicker = sin((time / 75) - hashXY) * 0.05 + 0.95;
        float flicker2 = sin((time / 45) - hashHZ) * 0.05 + 0.95;
        light.intensity *= flicker * flicker2;
        break;

        case LIGHT_ANIM_PULSE:
        float pulse = sin((time / 500) - offset) * 0.5 + 1.5;
        light.intensity *= pulse;
        break;
    }
}

// TODO:: move light animation to CPU
void ApplyAdditiveLighting(inout vec3 image, vec3 albedo, vec3 normal, vec3 fragPos)
{
    for(int i = 0; i < LIGHT_COUNT; i++)
    {
        Light light = additiveLights[i];
        if(light.type == LIGHT_TYPE_INVALID) break;

        light.pos.xyz = OffsetLight(light);

        vec3 toLight = (light.pos.xyz - fragPos.xzy);
        float distToLight = length(toLight) / TILE_SIZE;
        if(distToLight > light.radius) continue;

        toLight = normalize(toLight);
        toLight.z = -toLight.z;

        ApplyLightAnimation(light);
        float atten = LinearAttenuation(distToLight, light.radius);
        float ndl = max(dot(normal.xzy, toLight), 0);
        image += albedo.rgb * light.color.rgb * light.intensity * ndl * atten;
    }
}