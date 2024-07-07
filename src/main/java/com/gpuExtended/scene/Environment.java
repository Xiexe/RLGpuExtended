package com.gpuExtended.scene;

import com.google.gson.annotations.JsonAdapter;
import java.util.Objects;
import com.gpuExtended.rendering.Color;
import com.gpuExtended.rendering.Vector3;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter(value = AccessLevel.PRIVATE)
@NoArgsConstructor
public class Environment {

    public String   Name;
    public Vector3  LightDirection;
    public Color    LightColor;
    public Color    AmbientColor;
    public Color    FogColor;
    public int      FogDepth;

    public static Environment GetDefaultEnvironment() {
        Environment env = new Environment();
        env.Name = "Default";
        env.LightDirection = new Vector3(45, 200, 0); // 90 on X would be straight down from the top, 90 on Y points East
        env.LightColor = new Color(0.75f);
        env.AmbientColor = new Color(0.65f);
        env.FogColor = new Color(0, 0, 0);
        env.FogDepth = 25;
        return env;
    }

    @Override
    public String toString() {
        if (Name != null)
            return Name;
        return "null";
    }
}
