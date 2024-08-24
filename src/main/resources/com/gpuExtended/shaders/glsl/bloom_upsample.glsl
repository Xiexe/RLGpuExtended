#version 430

// This shader performs upsampling on a texture,
// as taken from Call Of Duty method, presented at ACM Siggraph 2014.

// Remember to add bilinear minification filter for this texture!
// Remember to use a floating-point texture format (for HDR)!
// Remember to use edge clamping for this texture!
uniform sampler2D srcTexture;
uniform float filterRadius;

in vec2 TexCoord;
layout (location = 0) out vec3 upsample;

vec3 sampleBloomMip(int mip)
{
    vec2 texCoord = vec2(TexCoord.x, 1.0-TexCoord.y);

    vec3 bloomMip = vec3(0);
    // The filter kernel is applied with a radius, specified in texture
    // coordinates, so that the radius will vary across mip resolutions.
    float x = 0.0025;
    float y = 0.0025;

    // Take 9 samples around current texel:
    // a - b - c
    // d - e - f
    // g - h - i
    // === ('e' is the current texel) ===
    vec3 a = textureLod(srcTexture, vec2(texCoord.x - x, texCoord.y + y), mip).rgb;
    vec3 b = textureLod(srcTexture, vec2(texCoord.x,     texCoord.y + y), mip).rgb;
    vec3 c = textureLod(srcTexture, vec2(texCoord.x + x, texCoord.y + y), mip).rgb;

    vec3 d = textureLod(srcTexture, vec2(texCoord.x - x, texCoord.y), mip).rgb;
    vec3 e = textureLod(srcTexture, vec2(texCoord.x,     texCoord.y), mip).rgb;
    vec3 f = textureLod(srcTexture, vec2(texCoord.x + x, texCoord.y), mip).rgb;

    vec3 g = textureLod(srcTexture, vec2(texCoord.x - x, texCoord.y - y), mip).rgb;
    vec3 h = textureLod(srcTexture, vec2(texCoord.x,     texCoord.y - y), mip).rgb;
    vec3 i = textureLod(srcTexture, vec2(texCoord.x + x, texCoord.y - y), mip).rgb;

    // Apply weighted distribution, by using a 3x3 tent filter:
    //  1   | 1 2 1 |
    // -- * | 2 4 2 |
    // 16   | 1 2 1 |
    bloomMip = e*4.0;
    bloomMip += (b+d+f+h)*2.0;
    bloomMip += (a+c+g+i);
    bloomMip *= 1.0 / 16.0;
    return bloomMip;
}

void main()
{
    int numMips = textureQueryLevels(srcTexture);

    upsample = vec3(0);
    for(int i = numMips - 1; i > 0; i--)
    {
        upsample += sampleBloomMip(i);
    }
    upsample /= float(numMips);
    upsample = max(vec3(0.0001f), upsample);
}