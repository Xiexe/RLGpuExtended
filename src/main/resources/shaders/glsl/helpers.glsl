void PopulateSurfaceColor(inout Surface s)
{
    vec4 color;

    if (fTextureId > 0) {
        int textureIdx = fTextureId - 1;
        vec4 textureColor = texture(textures, vec3(fUv, float(textureIdx)));
        color = textureColor;
    } else {
        // pick interpolated hsl or rgb depending on smooth banding setting
        vec3 rgb = hslToRgb(int(fHsl)) * smoothBanding + fColor.rgb * (1.f - smoothBanding);
        color = vec4(rgb, fColor.a);
    }

    s.albedo = color;
}

void PopulateSurfaceNormal(inout Surface s, vec3 normal)
{
    normal.y = -normal.y; // runescape uses -y as up by default. Lets make that more sane.
    s.normal = normalize(normal);
}

void PostProcessImage(inout vec3 image, int colorBlindMode)
{
    if (colorBlindMode > 0) {
        image = colorblind(colorBlindMode, image);
    }
}

bool CheckIsUnlitTexture(int texId)
{
    return (texId == FIRE_CAPE) || (texId == INFERNAL_CAPE);
}