package com.gpuExtended.util.contexts;

import net.runelite.api.Renderable;

public class RenderableContext {
    public final Renderable renderable;
    public final int sceneId;
    public final int x, y, z;
    public final int orientation;
    public final long hash;
    public final boolean isStatic;

    public RenderableContext(Renderable renderable, int sceneId, int x, int y, int z, int orientation, long hash, boolean isStatic) {
        this.renderable = renderable;
        this.sceneId = sceneId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.orientation = orientation;
        this.hash = hash;
        this.isStatic = isStatic;
    }
}
