package com.gpuExtended.scene;

import com.google.gson.annotations.SerializedName;
import com.gpuExtended.GpuExtendedConfig;
import com.gpuExtended.rendering.*;
import com.gpuExtended.util.Mat4;
import com.gpuExtended.util.Mathmatics;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Perspective;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.Arrays;
import java.util.Objects;

import static net.runelite.api.Constants.SCENE_SIZE;
import static net.runelite.api.Perspective.LOCAL_TILE_SIZE;

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
    public int[][] tiles = new int[0][0];
    public int[] decorations = new int[0];
    public int[] gameObjects = new int[0];
    public int[] walls = new int[0];
    public int[] projectiles = new int[0];

    @Nullable
    public float distanceSquared = 0;

    @Nullable
    public float[] projectionMatrix = Mat4.identity();

    @Nullable
    public float[] viewMatrix = Mat4.identity();

    @Nullable
    public boolean isDynamic = false;

    @Nullable
    public float hash = -1;

    public Light (String name, LightType type, LightAnimation animation, Color color, Vector3 offset, float intensity, float radius, int[][] tiles, int[] decorations, int[] gameObjects, int[] walls, int[] projectiles)
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
    }

    private static Vector4 GetLightPositionWithOffset(Vector3 position, Vector3 offset, int orientation)
    {
        Vector4 pos = new Vector4(position.x, position.y, position.z, 0);
        Vector4 off = new Vector4(offset.x * LOCAL_TILE_SIZE, offset.y * LOCAL_TILE_SIZE, offset.z * LOCAL_TILE_SIZE, 0);

        switch (orientation)
        {
            case 0: // Rotated 180 degrees
                pos.x -= off.x;
                pos.y -= off.y;
                break;

            case 1: // Rotated 90 degrees counter-clockwise
                pos.x -= off.y;
                pos.y += off.x;
                break;

            case 2: // Not rotated
                pos.x += off.x;
                pos.y += off.y;
                break;

            case 3: // Rotated 90 degrees clockwise
                pos.x += off.y;
                pos.y -= off.x;
                break;

            case 16: // south-east (config orientation = 0)
                pos.x += off.x;
                pos.y -= off.y;
                break;

            case 32: // south-west (config orientation = 1)
                pos.x -= off.y;
                pos.y += off.x;
                break;

            case 64: // north-west (config orientation = 2)
                pos.x -= off.x;
                pos.y += off.y;
                break;

            case 128: // north-east (config orientation = 3)
                pos.x += off.y;
                pos.y -= off.x;
                break;

            default:
                break;
        }

        pos.z -= off.z;
        return pos;
    }

    public static Light CreateLightFromTemplate(Light template, Vector4 position, int plane, int orientation)
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

        return light;
    }

    public void UpdateProjectionViewMatrix(int camX, int camY)
    {
        if(this.type != LightType.Directional)
            return;

        this.projectionMatrix = Mat4.identity();
        this.viewMatrix = Mat4.rotateX((float) Math.PI + this.position.x);
        Mat4.mul(this.viewMatrix, Mat4.rotateY((float) Math.PI + this.position.y));

        int shadowDrawDistance = 90;
        int drawDistanceSceneUnits = shadowDrawDistance * LOCAL_TILE_SIZE / 2;
        int east = Math.min(camX + drawDistanceSceneUnits, LOCAL_TILE_SIZE * SCENE_SIZE);
        int west = Math.max(camX - drawDistanceSceneUnits, 0);
        int north = Math.min(camY + drawDistanceSceneUnits, LOCAL_TILE_SIZE * SCENE_SIZE);
        int south = Math.max(camY - drawDistanceSceneUnits, 0);
        int width = east - west;
        int height = north - south;
        int farPlane = 10000;

        int maxDrawDistance = 100;
        float maxScale = 0.7f;
        float minScale = 0.4f;
        float scaleMultiplier = 1.0f - (shadowDrawDistance / (maxDrawDistance * maxScale));
        float scale = Mathmatics.lerp(maxScale, minScale, scaleMultiplier);
        Mat4.mul(this.projectionMatrix, Mat4.scale(scale, scale, scale));
        Mat4.mul(this.projectionMatrix, Mat4.ortho(width, height, 1, farPlane));
        Mat4.mul(this.projectionMatrix, this.viewMatrix);
        Mat4.mul(this.projectionMatrix, Mat4.translate(-(width / 2f + west), 0, -(height / 2f + south)));
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
