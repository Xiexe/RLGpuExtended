package com.gpuExtended.rendering.camera;

import com.gpuExtended.rendering.Vector3;

public class Frustum {
    public final Plane left;
    public final Plane right;
    public final Plane bottom;
    public final Plane top;
    public final Plane near;
    public final Plane far;

    public Frustum(Plane left, Plane right, Plane bottom, Plane top, Plane near, Plane far) {
        this.left = left;
        this.right = right;
        this.bottom = bottom;
        this.top = top;
        this.near = near;
        this.far = far;
    }

    /**
     * Checks if a point is inside the frustum.
     * @param point The point to check.
     * @return True if the point is inside or on the boundary of the frustum, false otherwise.
     */
    public boolean contains(Vector3 point) {
        // A point is inside the frustum if it is in front of or on all six planes.
        // "In front of" means it's on the side the normal is pointing to.
        if (left.getSignedDistance(point) < 0) return false;
        if (right.getSignedDistance(point) < 0) return false;
        if (bottom.getSignedDistance(point) < 0) return false;
        if (top.getSignedDistance(point) < 0) return false;
        if (near.getSignedDistance(point) < 0) return false;
        if (far.getSignedDistance(point) < 0) return false;

        return true;
    }
}
