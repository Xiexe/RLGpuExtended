package com.gpuExtended.scene;

import com.google.gson.annotations.SerializedName;
import com.gpuExtended.rendering.*;
import com.gpuExtended.util.Mat4;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.runelite.rlawt.AWTContext;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

import static net.runelite.api.Perspective.LOCAL_TILE_SIZE;
import static net.runelite.api.Perspective.UNIT;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL12C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL14C.GL_DEPTH_COMPONENT24;
import static org.lwjgl.opengl.GL30C.GL_DEPTH_ATTACHMENT;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Slf4j
public class Light
{
    public enum LightType
    {
        @SerializedName("none")
        None(0),
        @SerializedName("directional")
        Directional(1),
        @SerializedName("point")
        Point(2),
        @SerializedName("spot")
        Spot(3);

        private final int value;

        LightType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static LightType fromValue(int value) {
            for (LightType type : values()) {
                if (type.getValue() == value) {
                    return type;
                }
            }
            return null;
        }
    }

    public enum LightAnimation
    {
        @SerializedName("none")
        None(0),
        @SerializedName("flicker")
        Flicker(1),
        @SerializedName("pulse")
        Pulse(2);

        private final int value;

        LightAnimation(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static LightAnimation fromValue(int value) {
            for (LightAnimation type : values()) {
                if (type.getValue() == value) {
                    return type;
                }
            }
            return null;
        }
    }

    public String name = "";
    public LightType type = LightType.None;
    public LightAnimation animation = LightAnimation.None;
    public Color color = new Color(1,1,1);
    public Vector4 position = new Vector4(0,0,0, 0); // world position, does not get populated by JSON.
    public Vector3 offset = new Vector3(0,0,0); // local position to the thing it's attached to
    public float intensity = 1;
    public float radius = 2;
    public int plane = 0;
    public List<KeyframedLightAnimation> animations = null;
    public int[][] tiles = new int[0][0];
    public int[] decorations = new int[0];
    public int[] gameObjects = new int[0];
    public int[] walls = new int[0];
    public int[] projectiles = new int[0];
    public int[] npcs = new int[0];

    @Nullable
    public FrameBuffer shadowMapFramebuffer = null;

    @Nullable
    public float distanceSquared = 0;

    @Nullable
    public float[] projectionMatrix = Mat4.identity();

    @Nullable
    public float[] viewMatrix = Mat4.identity();

    @Nullable
    public float[] projectionMatrixClose = Mat4.identity();

    @Nullable
    public float[] viewMatrixClose = Mat4.identity();

    @Nullable
    public boolean isDynamic = false;

    @Nullable
    public float hash = -1;

    public Light (String name, LightType type, LightAnimation animation, Color color, Vector3 offset,
                  float intensity, float radius,
                  int[][] tiles,
                  int[] decorations,
                  int[] gameObjects,
                  int[] walls,
                  int[] projectiles,
                  int[] npcs,
                  List<KeyframedLightAnimation> animations
    )
    {
        this.name = name;
        this.type = type;
        this.animation = animation;
        this.color = color;
        this.offset = offset;
        this.intensity = intensity;
        this.radius = radius;
        this.tiles = tiles;
        this.decorations = decorations;
        this.gameObjects = gameObjects;
        this.walls = walls;
        this.projectiles = projectiles;
        this.npcs = npcs;
        this.animations = animations;
    }

    public static Light GetDebugLight() {
        return new Light(
                "Debug Light",
                LightType.Point,
                LightAnimation.None,
                new Color(1, 1, 1),
                new Vector3(0, 0, 0),
                .5f,
                2.0f,
                new int[][]{{0, 0}},
                new int[]{},
                new int[]{},
                new int[]{},
                new int[]{},
                new int[]{},
                new ArrayList<>()
        );
    }

    private static Vector4 GetLightPositionWithOffset(Vector3 position, Vector3 offset, int orientation)
    {
        int effectiveOrientation = orientation;
        Vector3 finalOffset = new Vector3(offset.x, offset.y, offset.z);

        // This block remaps the orientation for specific diagonal objects to match their
        // actual visual rotation in the game world, as described by your findings.
        // For example, an object with orientation 1280 is visually rotated by 45 degrees (which is 256).
        // A diagonal orientation is any that is not a cardinal direction (0, 512, 1024, 1536).
        // This can be checked by seeing if it has a remainder when divided by 512.
        if (orientation % 512 != 0)
        {
            // Remap the orientation to its true visual angle by adding 180 degrees (1024 units).
            // The modulo operator handles the wrap-around for 1280 and 1792.
            effectiveOrientation = (orientation + 1024) % 2048;

            // Add the extra 0.5 forward offset for diagonal lights.
            // Since local 'x' is our forward axis, we add to it.
            finalOffset.x += 0.5f;
        }

        // A full rotation is 2048 units. This constant converts the effective orientation
        // into radians for use in trigonometric functions.
        // UNIT = (2 * Math.PI) / 2048  or simply  Math.PI / 1024
        float radians = effectiveOrientation * (float)UNIT;

        // Scale the local offsets
        float localX = finalOffset.x * LOCAL_TILE_SIZE; // Represents the local forward/backward axis
        float localY = finalOffset.y * LOCAL_TILE_SIZE; // Represents the local right/left axis
        float localZ = finalOffset.z * LOCAL_TILE_SIZE;

        float cosYaw = (float)Math.cos(radians);
        float sinYaw = (float)Math.sin(radians);

        // This formula, derived from your original hardcoded switch statement,
        // correctly applies a reflection on the X-axis and then a clockwise rotation.
        float worldOffsetX = -localX * cosYaw + localY * sinYaw;
        float worldOffsetY =  localX * sinYaw + localY * cosYaw;

        return new Vector4(
                position.x + worldOffsetX,
                position.y + worldOffsetY,
                position.z - localZ, // Z offset is independent of XY rotation
                0f
        );
    }

    public static Light CreateLightFromTemplate(Light template, Vector4 position, int plane, int orientation, AWTContext awtContext)
    {
        Vector4 pos = GetLightPositionWithOffset(position, template.offset, orientation);

        Light light = new Light();
        light.name = template.name;
        light.type = template.type;
        light.animation = template.animation;
        light.color = template.color;
        light.position = pos;
        light.offset = new Vector3(template.offset.x, template.offset.y, template.offset.z);
        light.intensity = template.intensity;
        light.radius = template.radius;
        light.plane = plane;
        light.hash = light.generateHash();

        if(template.tiles != null)
            light.tiles = template.tiles.clone();

        if(template.decorations != null)
            light.decorations = template.decorations.clone();

        if(template.gameObjects != null)
            light.gameObjects = template.gameObjects.clone();

        if(template.projectiles != null)
            light.projectiles = template.projectiles.clone();

        //light.InitShadowMap(awtContext);
        return light;
    }

    public void UpdateProjectionViewMatrix(int camX, int camY, int shadowResolution, int shadowDistance)
    {
        if (this.type != LightType.Directional)
            return;

        // This defines the direction the light is "looking".
        this.viewMatrix = Mat4.rotateX((float) Math.PI + this.position.x);
        Mat4.mul(this.viewMatrix, Mat4.rotateY((float) Math.PI + this.position.y));

        // This defines the size of the area we want to cover with shadows.
        int shadowDrawDistance = shadowDistance;
        int drawDistanceSceneUnits = shadowDrawDistance * LOCAL_TILE_SIZE / 2;
        int west = camX - drawDistanceSceneUnits;
        int east = camX + drawDistanceSceneUnits;
        int north = camY + drawDistanceSceneUnits;
        int south = camY - drawDistanceSceneUnits;

        // The total width and height of the shadowable area.
        int orthoWidth = east - west;
        int orthoHeight = north - south;
        int farPlane = 10000; // Should be large enough to contain all scene geometry within the ortho box.

        // Calculate the size of one shadow map texel in world-space units. This is our "snap" interval.
        float worldUnitsPerTexel = (float)orthoWidth / shadowResolution; // Assuming square ortho box for simplicity

        // Transform the camera's world position into the light's view space.
        // The camera's world position is our initial, un-snapped center point.
        float[] worldCenter = {camX, 0, camY, 1.0f};
        float[] lightSpaceCenter = new float[4];
        Mat4.mulVec(lightSpaceCenter, this.viewMatrix, worldCenter);

        // Snap the light-space coordinates to the texel grid.
        // We floor the coordinates in texel-space, effectively aligning them to the grid.
        lightSpaceCenter[0] = (float)Math.floor(lightSpaceCenter[0] / worldUnitsPerTexel) * worldUnitsPerTexel;
        lightSpaceCenter[1] = (float)Math.floor(lightSpaceCenter[1] / worldUnitsPerTexel) * worldUnitsPerTexel;
        // We don't snap the Z coordinate, as that's the depth.

        // Transform the snapped light-space center back into world space.
        // This gives us a new, stabilized world-space center point to aim our projection at.
        float[] invViewMatrix = Mat4.inverse(this.viewMatrix);
        float[] snappedWorldCenter = new float[4];
        Mat4.mulVec(snappedWorldCenter, invViewMatrix, lightSpaceCenter);

        // Create the orthographic projection. It's always centered at the origin.
        float[] lightProjection = Mat4.ortho(orthoWidth, orthoHeight, 0, farPlane);

        // Create a translation matrix that moves the new, snapped world center to the origin.
        // This effectively aims the light's "camera" at our stabilized point.
        float[] lightTranslation = Mat4.translate(-snappedWorldCenter[0], 0, -snappedWorldCenter[2]);

        this.projectionMatrix = lightProjection;
        Mat4.mul(this.projectionMatrix, this.viewMatrix);
        Mat4.mul(this.projectionMatrix, lightTranslation);
    }

    public void UpdateCloseProjectionViewMatrix(int camX, int camY, int shadowResolution, int shadowDistance)
    {
        if (this.type != LightType.Directional)
            return;

        // This defines the direction the light is "looking".
        this.viewMatrixClose = Mat4.rotateX((float) Math.PI + this.position.x);
        Mat4.mul(this.viewMatrixClose, Mat4.rotateY((float) Math.PI + this.position.y));

        // This defines the size of the area we want to cover with shadows.
        int shadowDrawDistance = shadowDistance;
        int drawDistanceSceneUnits = shadowDrawDistance * LOCAL_TILE_SIZE / 2;
        int west = camX - drawDistanceSceneUnits;
        int east = camX + drawDistanceSceneUnits;
        int north = camY + drawDistanceSceneUnits;
        int south = camY - drawDistanceSceneUnits;

        // The total width and height of the shadowable area.
        int orthoWidth = east - west;
        int orthoHeight = north - south;
        int farPlane = 10000; // Should be large enough to contain all scene geometry within the ortho box.

        // Calculate the size of one shadow map texel in world-space units. This is our "snap" interval.
        float worldUnitsPerTexel = (float)orthoWidth / shadowResolution; // Assuming square ortho box for simplicity

        // Transform the camera's world position into the light's view space.
        // The camera's world position is our initial, un-snapped center point.
        float[] worldCenter = {camX, 0, camY, 1.0f};
        float[] lightSpaceCenter = new float[4];
        Mat4.mulVec(lightSpaceCenter, this.viewMatrixClose, worldCenter);

        // Snap the light-space coordinates to the texel grid.
        // We floor the coordinates in texel-space, effectively aligning them to the grid.
        lightSpaceCenter[0] = (float)Math.floor(lightSpaceCenter[0] / worldUnitsPerTexel) * worldUnitsPerTexel;
        lightSpaceCenter[1] = (float)Math.floor(lightSpaceCenter[1] / worldUnitsPerTexel) * worldUnitsPerTexel;
        // We don't snap the Z coordinate, as that's the depth.

        // Transform the snapped light-space center back into world space.
        // This gives us a new, stabilized world-space center point to aim our projection at.
        float[] invViewMatrix = Mat4.inverse(this.viewMatrixClose);
        float[] snappedWorldCenter = new float[4];
        Mat4.mulVec(snappedWorldCenter, invViewMatrix, lightSpaceCenter);

        // Create the orthographic projection. It's always centered at the origin.
        float[] lightProjection = Mat4.ortho(orthoWidth, orthoHeight, 0, farPlane);

        // Create a translation matrix that moves the new, snapped world center to the origin.
        // This effectively aims the light's "camera" at our stabilized point.
        float[] lightTranslation = Mat4.translate(-snappedWorldCenter[0], 0, -snappedWorldCenter[2]);

        this.projectionMatrixClose = lightProjection;
        Mat4.mul(this.projectionMatrixClose, this.viewMatrixClose);
        Mat4.mul(this.projectionMatrixClose, lightTranslation);
    }

    private void InitShadowMap(AWTContext awtContext)
    {
        FrameBuffer.FrameBufferSettings fboSettings = new FrameBuffer.FrameBufferSettings();
        fboSettings.name = "shadow_light_" + name;
        fboSettings.width = 128;
        fboSettings.height = 128;
        fboSettings.glAttachment = GL_DEPTH_ATTACHMENT;
        fboSettings.awtContext = awtContext;

        Texture2D.TextureSettings textureSettings = new Texture2D.TextureSettings();
        textureSettings.internalFormat = GL_DEPTH_COMPONENT24;
        textureSettings.format = GL_DEPTH_COMPONENT;
        textureSettings.type = GL_FLOAT;
        textureSettings.minFilter = GL_NEAREST;
        textureSettings.magFilter = GL_NEAREST;
        textureSettings.wrapS = GL_CLAMP_TO_EDGE;
        textureSettings.wrapT = GL_CLAMP_TO_EDGE;

        this.shadowMapFramebuffer = new FrameBuffer(fboSettings, textureSettings);
    }

    public void Animate(float time)
    {
        // TODO:: move light animation to cpu
    }

    public String toString()
    {
        return "Light: " + name + " Type: " + type + " Animation: " + animation + " Color: " + color + " Position: " + position + " Offset: " + offset + " Intensity: " + intensity + " Radius: " + radius;
    }

    public float generateHash()
    {
        return Objects.hash(
                this.name,
                this.type,
                this.animation,
                this.color,
                this.position,
                this.offset,
                this.intensity,
                this.radius,
                this.plane
        );
    }
}
