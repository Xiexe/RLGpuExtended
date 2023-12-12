package com.gpuExtended.scene;

import com.google.gson.annotations.SerializedName;
import com.gpuExtended.rendering.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Light
{
    public enum LightType
    {
        @SerializedName("0")
        None,
        @SerializedName("1")
        Directional,
        @SerializedName("2")
        Point,
        @SerializedName("3")
        Spot;
    }

    public enum LightAnimation
    {
        @SerializedName("0")
        None,
        @SerializedName("1")
        Flicker,
        @SerializedName("2")
        Pulse;
    }

    @SerializedName("type")
    public LightType type = LightType.None;

    @SerializedName("animation")
    public LightAnimation animation;

    public Color color = new Color(1,1,1);
    public Vector3 position = new Vector3(0,0,0); // used for offsets
    public Vector3 direction = new Vector3(0,0,0);
    public float intensity = 1;
    public float radius = 2;
    public int[][] tiles = new int[0][0];
    public int[] decorations = new int[0];
    public int[] gameObjects = new int[0];
    public int[] projectiles = new int[0];
}
