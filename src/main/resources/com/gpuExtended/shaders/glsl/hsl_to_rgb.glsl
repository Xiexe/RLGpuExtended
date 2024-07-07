vec3 hslToRgb(int hsl) {
  int var5 = hsl / 128;
  float var6 = float(var5 >> 3) / 64.0f + 0.0078125f;
  float var8 = float(var5 & 7) / 8.0f + 0.0625f;

  int var10 = hsl % 128;

  float var11 = float(var10) / 128.0f;
  float var13 = var11;
  float var15 = var11;
  float var17 = var11;

  if (var8 != 0.0f) {
    float var19;
    if (var11 < 0.5f) {
      var19 = var11 * (1.0f + var8);
    } else {
      var19 = var11 + var8 - var11 * var8;
    }

    float var21 = 2.0f * var11 - var19;
    float var23 = var6 + 0.3333333333333333f;
    if (var23 > 1.0f) {
      var23 -= 1.f;
    }

    float var27 = var6 - 0.3333333333333333f;
    if (var27 < 0.0f) {
      var27 += 1.f;
    }

    if (6.0f * var23 < 1.0f) {
      var13 = var21 + (var19 - var21) * 6.0f * var23;
    } else if (2.0f * var23 < 1.0f) {
      var13 = var19;
    } else if (3.0f * var23 < 2.0f) {
      var13 = var21 + (var19 - var21) * (0.6666666666666666f - var23) * 6.0f;
    } else {
      var13 = var21;
    }

    if (6.0f * var6 < 1.0f) {
      var15 = var21 + (var19 - var21) * 6.0f * var6;
    } else if (2.0f * var6 < 1.0f) {
      var15 = var19;
    } else if (3.0f * var6 < 2.0f) {
      var15 = var21 + (var19 - var21) * (0.6666666666666666f - var6) * 6.0f;
    } else {
      var15 = var21;
    }

    if (6.0f * var27 < 1.0f) {
      var17 = var21 + (var19 - var21) * 6.0f * var27;
    } else if (2.0f * var27 < 1.0f) {
      var17 = var19;
    } else if (3.0f * var27 < 2.0f) {
      var17 = var21 + (var19 - var21) * (0.6666666666666666f - var27) * 6.0f;
    } else {
      var17 = var21;
    }
  }

  vec3 rgb = vec3(pow(var13, brightness), pow(var15, brightness), pow(var17, brightness));

  return rgb;
}