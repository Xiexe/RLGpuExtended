#version 430

#include "shaders/glsl/constants.glsl"
#include "shaders/glsl/structs.glsl"
#include "shaders/glsl/uniforms.glsl"

flat in int fTextureId;
in vec2 fUv;
in float fAlpha;

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

void main() {
    float dither = Dither(gl_FragCoord.xy);

    if (fTextureId > 0) {
        int textureIdx = fTextureId - 1;
        // This error is fake news.
        float alpha = texture(textures, vec3(fUv, float(textureIdx))).a;
        clip((alpha * fAlpha) - dither);
    }
    else
    {
        clip(fAlpha - dither);
    }

    gl_FragDepth = gl_FragCoord.z;
}