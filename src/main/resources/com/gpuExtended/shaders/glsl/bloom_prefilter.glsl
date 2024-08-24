#version 330

uniform sampler2D srcTexture;

in vec2 TexCoord;
layout (location = 0) out vec3 filtered;

void main()
{
    vec2 texCoord = vec2(TexCoord.x, 1.0-TexCoord.y);

    vec3 color = textureLod(srcTexture, texCoord, 0).rgb - 1;
    filtered = max(vec3(0), color);
}