package com.gpuExtended.rendering.camera;

import com.gpuExtended.rendering.Vector3;
import com.gpuExtended.util.Mat4;

public class Camera {

    // Camera attributes
    public Vector3 position;
    public Vector3 front;
    public Vector3 up;
    public Vector3 right;
    private final Vector3 worldUp;

    // Euler angles (in radians)
    public float yaw;
    public float pitch;

    // Projection attributes
    public float fov = (float) Math.toRadians(70.0); // Field of View
    public float aspectRatio = 16.0f / 9.0f;
    public float nearPlane = 0.1f;
    public float farPlane = 10000.0f;

    /**
     * Constructor for a new Camera.
     * @param position The starting position of the camera in world space.
     * @param worldUp The universal up direction (usually 0, 1, 0).
     * @param yaw The initial yaw (rotation around Y-axis) in radians.
     * @param pitch The initial pitch (rotation around X-axis) in radians.
     */
    public Camera(Vector3 position, Vector3 worldUp, float yaw, float pitch) {
        this.position = position;
        this.worldUp = worldUp;
        this.yaw = yaw;
        this.pitch = pitch;

        this.front = Vector3.Zero();
        this.up = Vector3.Zero();
        this.right = Vector3.Zero();

        updateCameraVectors();
    }

    /**
     * Calculates and returns the view matrix for this camera's current state.
     * This matrix transforms world coordinates to view (camera) space.
     * @return The calculated 4x4 view matrix.
     */
    public float[] getViewMatrix() {
        Vector3 target = Vector3.Add(this.position, this.front);
        return Mat4.lookAt(this.position, target, this.up);
    }
    /**
     * Calculates and returns the projection matrix.
     * This matrix transforms view space coordinates to clip space.
     * @return The calculated 4x4 projection matrix.
     */
    public float[] getProjectionMatrix() {
        return Mat4.perspective(fov, aspectRatio, nearPlane, farPlane);
    }

    /**
     * Updates the camera's aspect ratio. Call this when the game window is resized.
     * @param width The new width of the viewport.
     * @param height The new height of the viewport.
     */
    public void setAspectRatio(float width, float height) {
        if (height > 0) {
            this.aspectRatio = width / height;
        }
    }

    /**
     * Calculates and returns the viewing frustum for the camera's current state.
     * The frustum is derived from the combined view and projection matrices.
     * This is essential for performing frustum culling.
     *
     * @return A Frustum object representing the camera's visible volume.
     */
    public Frustum getFrustum() {
        // Get the current view and projection matrices.
        float[] viewMatrix = getViewMatrix();
        float[] projectionMatrix = getProjectionMatrix();

        // Combine them into a single View-Projection matrix.
        // The order is important: projection * view
        float[] viewProjectionMatrix = Mat4.multiply(projectionMatrix, viewMatrix);

        // Extract and return the frustum planes from the combined matrix.
        return Mat4.extractFrustumPlanes(viewProjectionMatrix);
    }

    /**
     * Recalculates the Front, Right, and Up vectors from the camera's updated Euler angles.
     * This must be called any time the yaw or pitch is changed.
     */
    public final void updateCameraVectors() {
        // Calculate the new Front vector
        Vector3 newFront = Vector3.Zero();
        newFront.x = (float) (Math.cos(yaw) * Math.cos(pitch));
        newFront.y = (float) (Math.sin(pitch));
        newFront.z = (float) (Math.sin(yaw) * Math.cos(pitch));
        this.front = newFront.Normalize();

        this.right = Vector3.Cross(this.front, this.worldUp).Normalize();  // Normalize the vectors, because their length gets closer to 0 the more you look up or down which results in slower movement.
        this.up = Vector3.Cross(this.right, this.front).Normalize();
    }
}