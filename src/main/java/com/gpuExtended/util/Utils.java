package com.gpuExtended.util;

import java.awt.Color;

public class Utils {
    public static Color colorLerp(Color color1, Color color2, float t) {
        // Ensure t is within the range [0, 1]
        t = Math.max(0, Math.min(1, t));

        int r1 = color1.getRed();
        int g1 = color1.getGreen();
        int b1 = color1.getBlue();
        int a1 = color1.getAlpha();

        int r2 = color2.getRed();
        int g2 = color2.getGreen();
        int b2 = color2.getBlue();
        int a2 = color2.getAlpha();

        int r = (int) (r1 + t * (r2 - r1));
        int g = (int) (g1 + t * (g2 - g1));
        int b = (int) (b1 + t * (b2 - b1));
        int a = (int) (a1 + t * (a2 - a1));

        return new Color(r, g, b, a);
    }
}
