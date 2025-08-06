package com.gpuExtended.scene;

import java.util.List;

public class KeyframedLightAnimation {
    public final int id;
    public final List<LightKeyframe> frames;

    public KeyframedLightAnimation(int id, List<LightKeyframe> frames) {
        this.id = id;
        this.frames = frames;
    }
}
