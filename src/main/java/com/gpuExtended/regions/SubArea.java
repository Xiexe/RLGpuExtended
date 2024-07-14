package com.gpuExtended.regions;

import com.gpuExtended.rendering.Vector3;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import net.runelite.api.coords.WorldPoint;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class SubArea {
    String name;
    Vector3 start;
    Vector3 end;

    public boolean isInside(WorldPoint point) {
        boolean isInsideZ = true;
        boolean isInsideXY = point.getX() >= this.start.x && point.getX() <= this.end.x &&
                point.getY() >= this.start.y && point.getY() <= this.end.y;

        if((this.start.z != -1 && this.end.z != -1)) {
            isInsideZ = point.getPlane() >= this.start.z && point.getPlane() <= this.end.z;
        }

        return isInsideXY && isInsideZ;
    }
}
