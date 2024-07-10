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

bool CheckIsInfernalCapeFireCape(int texId)
{
    return (texId == FIRE_CAPE) || (texId == INFERNAL_CAPE);
}

bool CheckIsUnlitTexture(int texId)
{
    return CheckIsInfernalCapeFireCape(texId);
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
    image = adjustBrightness(image, 0.8);

    if (colorBlindMode > 0) {
        image = colorblind(colorBlindMode, image);
    }
}

void DrawTileMarker(inout vec3 image, vec3 fragPos, vec3 tilePosition, float lineWidth)
{
    float gridSize = TILE_SIZE;
    float x = fragPos.x;
    float z = fragPos.z;

    float u = mod(x, gridSize) / gridSize;
    float v = mod(z, gridSize) / gridSize;

    int cellX = int(floor(x / gridSize) * gridSize);
    int cellZ = int(floor(z / gridSize) * gridSize);

    if (cellX >= int(tilePosition.x - TILE_SIZE) &&
        cellZ >= int(tilePosition.y - TILE_SIZE) &&
        cellX <= int(tilePosition.x) &&
        cellZ <= int(tilePosition.y) &&
        approximatelyEqual(tilePosition.z, playerPosition.z, 0.01)
    )
    {
        float eps = 0.00001;
        if (u > eps && u < 1.0 - eps && v > eps && v < 1.0 - eps)
        {
            bool isBorder = (u < lineWidth          ||
                             u > 1.0 - lineWidth    ||
                             v < lineWidth          ||
                             v > 1.0 - lineWidth
            );
            if (isBorder)
            {
                image = vec3(0, 1, 1);
            }
            else
            {
                image *= 0.75;
            }
        }
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