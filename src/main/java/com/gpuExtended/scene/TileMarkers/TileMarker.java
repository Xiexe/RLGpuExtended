package com.gpuExtended.scene.TileMarkers;

import lombok.EqualsAndHashCode;
import lombok.Value;

import javax.annotation.Nullable;
import java.awt.*;

@Value
@EqualsAndHashCode(exclude = { "color", "label" })
public class TileMarker {
    private int regionId;
    private int regionX;
    private int regionY;
    private int z;
    @Nullable
    private Color color;
    @Nullable
    private String label;
}
