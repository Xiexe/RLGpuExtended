package com.gpuExtended.scene;

import java.awt.*;

public class LightKeyframe {
    public final int frameNumber;
    public final Color color;
    public final Float intensity;
    public final Float radius;

    public LightKeyframe(int frameNumber, Color color, Float intensity, Float radius) {
        this.frameNumber = frameNumber;
        this.color = color;
        this.intensity = intensity;
        this.radius = radius;
    }
}
