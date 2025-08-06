package com.gpuExtended.rendering.camera;

import com.gpuExtended.rendering.Vector3;

/**
 * Represents a plane in 3D space, defined by the equation Ax + By + Cz + D = 0.
 */
public class Plane {
    /** The normal vector of the plane (A, B, C). */
    public final Vector3 normal;
    /** The distance of the plane from the origin. */
    public final float distance;

    public Plane(Vector3 normal, float distance) {
        this.normal = normal;
        this.distance = distance;
    }

    /**
     * Creates a plane from the four coefficients of its equation (A, B, C, D).
     * The plane equation is normalized during this process.
     */
    public static Plane fromCoefficients(float a, float b, float c, float d) {
        // Normalize the plane equation
        float length = (float) Math.sqrt(a * a + b * b + c * c);
        return new Plane(
                new Vector3(a / length, b / length, c / length),
                d / length
        );
    }

    /**
     * Calculates the signed distance from a point to the plane.
     * @param point The point to check.
     * @return > 0 if the point is in front of the plane (in the direction of the normal).
     *         < 0 if the point is behind the plane.
     *         = 0 if the point is on the plane.
     */
    public float getSignedDistance(Vector3 point) {
        return normal.Dot(point) + distance;
    }
}
