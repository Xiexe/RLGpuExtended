package com.gpuExtended.regions;

import com.gpuExtended.rendering.Vector3;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import net.runelite.api.coords.WorldPoint;

import javax.annotation.Nullable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Bounds {
    @Nullable
    String name;

    Vector3 start;
    Vector3 end;

    @Nullable
    boolean hideOtherAreas;

    public int getWidth() {
        return (int)this.end.x - (int)this.start.x;
    }

    public int getHeight() {
        return (int)this.end.y - (int)this.start.y;
    }

    public boolean contains(WorldPoint point, int padding) {
        boolean isInsideZ = true;

        boolean isInsideXY =
                point.getX() >= this.start.x - padding && point.getX() <= this.end.x + padding &&
                point.getY() >= this.start.y - padding && point.getY() <= this.end.y + padding;

        if((this.start.z != -1 && this.end.z != -1)) {
            isInsideZ = point.getPlane() >= this.start.z && point.getPlane() <= this.end.z;
        }

        return isInsideXY && isInsideZ;
    }
}
