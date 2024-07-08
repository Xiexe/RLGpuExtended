bool CheckIsInfernalCapeFireCape(int texId)
{
    return (texId == FIRE_CAPE) || (texId == INFERNAL_CAPE);
}

bool CheckIsUnlitTexture(int texId)
{
    return CheckIsInfernalCapeFireCape(texId);
}

void PopulateSurfaceColor(inout Surface s)
{
    vec4 color;
    vec2 uv = fUv;

    if (fTextureId > 0) {
        int textureIdx = fTextureId - 1;
        // This error is fake.
        vec4 textureColor = texture(textures, vec3(uv, float(textureIdx)));
        if(CheckIsInfernalCapeFireCape(fTextureId))
        {
            textureColor *= 1.2;
        }

        color = textureColor;
    } else {
        // pick interpolated hsl or rgb depending on smooth banding setting
        vec3 rgb = hslToRgb(int(fHsl)) * smoothBanding + fColor.rgb * (1.f - smoothBanding);
        color = vec4(rgb, fColor.a);
    }

    s.albedo = color;
}

void PopulateSurfaceNormal(inout Surface s, vec4 normal)
{
    normal.y = -normal.y; // runescape uses -y as up by default. Lets make that more sane.
    s.normal.rgb = normalize(normal.rgb);
}

// Function to adjust saturation
vec3 adjustSaturation(vec3 color, float saturation) {
    // Convert RGB to grayscale by calculating luminance
    float gray = dot(color, vec3(0.299, 0.587, 0.114));
    // Linearly interpolate between the grayscale value and the original color
    return mix(vec3(gray), color, saturation);
}

// Function to adjust contrast
vec3 adjustContrast(vec3 color, float contrast) {
    // Shift the color by 0.5 to center it, scale it by the contrast factor, and then shift it back
    return (color - 0.5) * contrast + 0.5;
}

// Main function to adjust both saturation and contrast
vec3 adjustSaturationAndContrast(vec3 color, float saturation, float contrast) {
    color = adjustSaturation(color, saturation);
    color = adjustContrast(color, contrast);
    return color;
}

vec3 adjustBrightness(vec3 color, float brightness) {
    return color * brightness;
}

void PostProcessImage(inout vec3 image, int colorBlindMode, float fogFalloff)
{
    image = mix(adjustSaturationAndContrast(image, 1.1, 1.3), image, fogFalloff);
    //image = adjustBrightness(image, 0.8);

    if (colorBlindMode > 0) {
        image = colorblind(colorBlindMode, image);
    }
}

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