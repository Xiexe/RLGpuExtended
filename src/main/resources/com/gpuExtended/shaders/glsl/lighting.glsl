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

float PCSSShadows(vec4 projCoords, float fadeOut) {
    vec2 shadowRes = textureSize(shadowMap, 0);
    float currentDepth = projCoords.z - bias;

    // Estimate penumbra size
    float penumbraSize = PCSSEstimatePenumbraSize(projCoords, currentDepth, lightSize) * 5;

    // Calculate shadow using PCSS
    float shadow = PCSSFilter(projCoords, currentDepth, penumbraSize);

    return shadow * (1.0 - fadeOut);
}

float GetShadowMap(vec3 fragPos) {
    vec4 projCoords = mainLight.projectionMatrix * vec4(fragPos, 1);
    projCoords = projCoords / projCoords.w;
    projCoords = projCoords * 0.5 + 0.5;

    vec2 uv = projCoords.xy * 2.0 - 1.0;
    float fadeOut = smoothstep(0.75, 1.0, dot(uv, uv));

    float pcssShadowSample = PCSSShadows(projCoords, fadeOut);

    return 1-pcssShadowSample;
}

//float ScreenSpaceShadows(vec3 fragPos, vec3 viewDirection)
//{
//    // Compute ray position and direction (in view-space)
//    vec3 ray_pos = mul(float4(fragPos, 1.0f), viewDirection).xyz;
//    vec3 ray_dir = mul(float4(-lightDirection, 0.0f), viewDirection).xyz;
//
//    // Compute ray step
//    vec3 ray_step = ray_dir * g_sss_step_length;
//
//    // Ray march towards the light
//    float occlusion = 0.0;
//    vec2 ray_uv   = vec2(0.0);
//    for (int i = 0; i < g_sss_max_steps; i++)
//    {
//        // Step the ray
//        ray_pos += ray_step;
//        ray_uv  = project_uv(ray_pos, g_projection);
//
//        // Ensure the UV coordinates are inside the screen
//        if (is_saturated(ray_uv))
//        {
//            // Compute the difference between the ray's and the camera's depth
//            float depth_z     = get_linear_depth(ray_uv);
//            float depth_delta = ray_pos.z - depth_z;
//
//            // Check if the camera can't "see" the ray (ray depth must be larger than the camera depth, so positive depth_delta)
//            if ((depth_delta > 0.0f) && (depth_delta < g_sss_thickness))
//            {
//                // Mark as occluded
//                occlusion = 1.0f;
//
//                // Fade out as we approach the edges of the screen
//                //occlusion *= screen_fade(ray_uv);
//
//                break;
//            }
//        }
//    }
//
//    // Convert to visibility
//    return 1.0f - occlusion;
//}