package com.gpuExtended.util;

import java.awt.*;

public class Mathmatics {
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
    private static Color lerp(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t)); // Clamp to [0, 1]

        int r = (int) (a.getRed()   + (b.getRed()   - a.getRed())   * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bVal = (int) (a.getBlue()  + (b.getBlue()  - a.getBlue())  * t);

        return new Color(r, g, bVal);
    }
}
