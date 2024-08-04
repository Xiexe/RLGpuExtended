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

bool CheckIsWater(int texId)
{
    return texId == WATER;
}

bool CheckIsSwampWater(int texId)
{
    return texId == WATER_SWAMP;
}

bool CheckIsUnlitTexture(int texId)
{
    return CheckIsInfernalCapeFireCape(texId) || texId == LAVA;
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

float hash21(vec2 uv)
{
    return fract(sin(7.289 * uv.x + 11.23 * uv.y) * 23758.5453);
}

void PopulateSurfaceColor(inout Surface s)
{
    vec4 color;
    vec2 uv = fUv;

    if (fTextureId > 0) {
        int textureIdx = fTextureId - 1;
        // This error is fake news.
        vec4 textureColor = texture(textures, vec3(fUv, float(textureIdx)));
        vec4 textureColorBrightness = pow(textureColor, vec4(brightness, brightness, brightness, 1.0f));

        // textured triangles hsl is a 7 bit lightness 2-126
        float textureLightMode = 1;
        float light = fHsl / 127.f;
        vec3 mul = (1.f - textureLightMode) * vec3(light) + textureLightMode * fColor.rgb;;

        if(CheckIsUnlitTexture(fTextureId))
        {
            textureColor.rgb *= 1.5;
        }
        else
        {
            textureColor = textureColorBrightness * vec4(mul, 1.f);
        }

        if(CheckIsWater(fTextureId))
        {
            textureColor = vec4(0.4, 0.55, 0.6, 1);
        }

        if(CheckIsSwampWater(fTextureId))
        {
            textureColor = vec4(0.1, 0.3, 0.25, 1);
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

void PopulateVertexFlags(inout VertexFlags flags, ivec4 fFlags)
{
    flags.tileX             = ((fFlags.x >> BIT_XPOS) & 255);
    flags.tileY             = ((fFlags.x >> BIT_YPOS) & 255);
    flags.plane             = ((fFlags.x >> BIT_PLANE) & 3);
    flags.isBridge          = ((fFlags.x >> BIT_ISBRIDGE) & 1) > 0;
    flags.isTerrain         = ((fFlags.x >> BIT_ISTERRAIN) & 1) > 0;
    flags.isDynamicModel    = ((fFlags.x >> BIT_ISDYNAMICMODEL) & 1) > 0;
    flags.isOnBridge        = ((fFlags.x >> BIT_ISONBRIDGE) & 1) > 0;
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

vec3 adjustBrightness(vec3 color, float brightnessAdjust) {
    return color * brightnessAdjust;
}

// Function to combine upper and lower parts to form a 32-bit integer
int combine16(int upper, int lower) {
    return (upper << 16) | lower;
}


void PostProcessImage(inout vec3 image, int colorBlindMode, float fogFalloff, int isEmissive)
{
    image = mix(adjustSaturationAndContrast(image, 1.2, 1.0), image, fogFalloff);
    image = adjustBrightness(image, 0.8);

    if (colorBlindMode > 0) {
        image = colorblind(colorBlindMode, image);
    }
}

vec4 intToColor(int color) {
    float r = float((color >> 24) & 0xFF) / 255.0;
    float g = float((color >> 16) & 0xFF) / 255.0;
    float b = float((color >> 8) & 0xFF) / 255.0;
    float a = float(color & 0xFF) / 255.0;
    return vec4(r, g, b, a);
}

vec4 unpackColor(vec4 packedColor) {
    float r = packedColor.r;
    float g = packedColor.g;
    float b = packedColor.b;
    float a = packedColor.a;
    return vec4(r, g, b, a);
}

void DrawMarkedTilesFromMap(inout vec3 image, VertexFlags flags, vec3 fragPos, float distanceToPlayer)
{
    ivec2 cellUv = ivec2(flags.tileX, flags.tileY);
    vec4 packedFillColor = texelFetch(tileFillColorMap, cellUv, 0);
    vec4 packedOutlineColor = texelFetch(tileBorderColorMap, cellUv, 0);
    vec4 packedSettings = texelFetch(tileSettingsMap, cellUv, 0);

    float cornerLength = packedSettings.r * 255;
    float outlineWidth = packedSettings.g * 255;

    vec4 fillColor = unpackColor(packedFillColor);
    vec4 outlineColor = unpackColor(packedOutlineColor);

    float realPlane = max(0, flags.plane - (flags.isBridge ? 1 : 0));

    bool tileValidPlane = approximatelyEqual(realPlane, playerPosition.z, 0.01);
    bool isTileWalkable = /*(flags.isTerrain || flags.isBridge) &&*/ tileValidPlane;
    if(isTileWalkable)
    {
        vec2 tileUv = vec2(mod(fragPos.x, TILE_SIZE) / TILE_SIZE, mod(fragPos.z, TILE_SIZE) / TILE_SIZE);

        // Determine if the current fragment is within the border width
        outlineWidth = max(outlineWidth, outlineWidth * (distanceToPlayer / 2000f));
        outlineWidth /= TILE_SIZE;
        cornerLength /= TILE_SIZE;

        bool isBorder = (
            (tileUv.x < outlineWidth && tileUv.y < cornerLength) ||
            (tileUv.x < outlineWidth && tileUv.y > 1.0 - cornerLength) ||
            (tileUv.x > 1.0 - outlineWidth && tileUv.y < cornerLength) ||
            (tileUv.x > 1.0 - outlineWidth && tileUv.y > 1.0 - cornerLength) ||
            (tileUv.y < outlineWidth && tileUv.x < cornerLength) ||
            (tileUv.y < outlineWidth && tileUv.x > 1.0 - cornerLength) ||
            (tileUv.y > 1.0 - outlineWidth && tileUv.x < cornerLength) ||
            (tileUv.y > 1.0 - outlineWidth && tileUv.x > 1.0 - cornerLength)
        );

        image = mix(image, fillColor.rgb, fillColor.a * float(!isBorder));
        image = mix(image, outlineColor.rgb, outlineColor.a * float(isBorder));
    }
}

// TilePosition.w = corner length
// TilePosition.z = plane
void DrawTileMarker(inout vec3 image, VertexFlags flags, vec3 fragPos, vec4 tilePosition, vec4 fillColor, vec4 borderColor, float lineWidth, float distanceToPlayer)
{
    float x = fragPos.x;
    float z = fragPos.z;
    float u = mod(x, TILE_SIZE) / TILE_SIZE;
    float v = mod(z, TILE_SIZE) / TILE_SIZE;

    int cellX = int(floor(x / TILE_SIZE) * TILE_SIZE);
    int cellZ = int(floor(z / TILE_SIZE) * TILE_SIZE);

    float realPlane = max(0, flags.plane - (flags.isBridge ? 1 : 0));

    bool tileValidPlane = approximatelyEqual(realPlane, playerPosition.z, 0.01);
    bool isTileWalkable = /*(flags.isTerrain || flags.isBridge) &&*/ tileValidPlane;
    if (cellX >= int(tilePosition.x - TILE_SIZE) &&
        cellZ >= int(tilePosition.y - TILE_SIZE) &&
        cellX <= int(tilePosition.x) &&
        cellZ <= int(tilePosition.y) &&
        isTileWalkable
    )
    {
        float eps = 0.01;
        float cornerLength = tilePosition.w / TILE_SIZE;
        if (u > eps && u < 1.0 - eps && v > eps && v < 1.0 - eps)
        {
            lineWidth /= 64f;

            bool isBorder = (
                (u < lineWidth && v < cornerLength) ||
                (u < lineWidth && v > 1.0 - cornerLength) ||
                (u > 1.0 - lineWidth && v < cornerLength) ||
                (u > 1.0 - lineWidth && v > 1.0 - cornerLength) ||
                (v < lineWidth && u < cornerLength) ||
                (v < lineWidth && u > 1.0 - cornerLength) ||
                (v > 1.0 - lineWidth && u < cornerLength) ||
                (v > 1.0 - lineWidth && u > 1.0 - cornerLength)
            );
            if (isBorder)
            {
                image = mix(image, borderColor.rgb, borderColor.a);
            }
            else
            {
                image = mix(image, fillColor.rgb, fillColor.a);
            }
        }
    }
}

void FadeRoofs(VertexFlags flags, vec3 fragPos, float dither, float distanceToPlayer)
{
    if(!(roofFading > 0))
    {
        return;
    }

    ivec2 cellUv = ivec2(flags.tileX, flags.tileY);
    distanceToPlayer = smoothstep((roofFadeDistance + 8) * TILE_SIZE, roofFadeDistance * TILE_SIZE, distanceToPlayer);

    float roofTextureP0 = texelFetch(roofMaskMap, ivec3(cellUv, 0), 0).a;
    float roofTextureP1 = texelFetch(roofMaskMap, ivec3(cellUv, 1), 0).a;
    float roofTextureP2 = texelFetch(roofMaskMap, ivec3(cellUv, 2), 0).a;

    float roofMaskIsOnPlane0 = roofTextureP0 + roofTextureP1 + roofTextureP2;
    float roofMaskIsOnPlane1 = roofTextureP1 + roofTextureP2;
    float roofMaskIsOnPlane2 = roofTextureP2;

    float roofMask = 0;
    switch(int(playerPosition.z))
    {
        case 0:
        if(flags.plane > 0)
        {
            roofMask = roofMaskIsOnPlane0;
        }
        break;

        case 1:
        if(flags.plane > 1)
        {
            roofMask = roofMaskIsOnPlane1;
        }
        break;

        case 2:
        if(flags.plane > 2)
        {
            roofMask = roofMaskIsOnPlane2;
        }
        break;
    }
    roofMask = clamp(roofMask, 0, 1);

    float roofClip = roofMask * (dither - 0.001 - distanceToPlayer);
    clip(roofClip);
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