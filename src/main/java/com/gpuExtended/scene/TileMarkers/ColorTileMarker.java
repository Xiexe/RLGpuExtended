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
    private Color fillColor;
    @Nullable
    private int cornerLength;
    @Nullable
    private int borderWidth;
    @Nullable
    private String label;
}
