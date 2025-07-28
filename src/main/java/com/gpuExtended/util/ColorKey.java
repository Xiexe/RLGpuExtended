package com.gpuExtended.util;

import java.awt.*;

public class ColorKey {
    public final float time; // in [0.0, 1.0]
    public final Color color;

    public ColorKey(float time, Color color) {
        this.time = time;
        this.color = color;
    }
}
