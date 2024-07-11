package com.gpuExtended.scene.TileMarkers;

import lombok.Value;
import net.runelite.api.coords.WorldPoint;

import javax.annotation.Nullable;
import java.awt.*;

@Value
public class ColorTileMarker {
    private WorldPoint worldPoint;
    @Nullable
    private Color color;
    @Nullable
    private String label;
}
