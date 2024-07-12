package com.gpuExtended.scene;

import com.google.gson.annotations.SerializedName;
import com.gpuExtended.rendering.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.awt.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
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

    public LightType type;
    public LightAnimation animation;
    public Color color = new Color(1,1,1);
    public Vector4 position = new Vector4(0,0,0, 0); // world position, does not get populated by JSON.
    public Vector3 offset = new Vector3(0,0,0); // local position to the thing it's attached to
    public float intensity = 1;
    public float radius = 2;
    public int[][] tiles = new int[0][0];
    public int[] decorations = new int[0];
    public int[] gameObjects = new int[0];
    public int[] projectiles = new int[0];

    public static Light CreateLightFromTemplate(Light template, Vector4 position)
    {
        Light light = new Light();
        light.type = template.type;
        light.animation = template.animation;
        light.color = template.color;
        light.position = position;
        light.offset = new Vector3(template.offset.x, template.offset.y, template.offset.z);
        light.intensity = template.intensity;
        light.radius = template.radius;
        light.tiles = template.tiles.clone();
        light.decorations = template.decorations.clone();
        light.gameObjects = template.gameObjects.clone();
        light.projectiles = template.projectiles.clone();
        return light;
    }

    public String toString()
    {
        return "Light{" +
                "type=" + type +
                ", animation=" + animation +
                ", color=" + color +
                ", position=" + position +
                ", intensity=" + intensity +
                ", radius=" + radius +
                ", tiles=" + tiles +
                ", decorations=" + decorations +
                ", gameObjects=" + gameObjects +
                ", projectiles=" + projectiles +
                '}';
    }
}
