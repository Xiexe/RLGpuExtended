package com.gpuExtended.scene;

import com.google.gson.annotations.JsonAdapter;
import java.util.Objects;
import java.awt.Color;
import com.gpuExtended.rendering.Vector3;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter(value = AccessLevel.PRIVATE)
@NoArgsConstructor
public class Environment {
    public enum EnvironmentType {
        DEFAULT(0),
        UNDERGROUND(1);

        private final int value;

        EnvironmentType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static EnvironmentType fromValue(int value) {
            for (EnvironmentType type : values()) {
                if (type.getValue() == value) {
                    return type;
                }
            }
            return null;
        }
    }

    public String   Name;
    public Vector3  LightDirection;
    public Color    LightColor;
    public Color    AmbientColor;
    public Color    SkyColor;
    public int      FogDepth;
    public int      Type;

    public static Environment GetDefaultEnvironment() {
        Environment env = new Environment();
        env.Name = "DEFAULT";
        env.LightDirection = new Vector3(45, 200, 0); // 90 on X would be straight down from the top, 90 on Y points East
        env.LightColor = new Color(0.75f, 0.75f, 0.75f, 1f);
        env.AmbientColor = new Color(1f, 0.9f, 0.8f, 1f);
        env.SkyColor = new Color(0, 0, 0);
        env.FogDepth = 0;
        env.Type = EnvironmentType.DEFAULT.getValue();
        return env;
    }

    public static Environment GetDefaultUndergroundEnvironment() {
        Environment env = new Environment();
        env.Name = "DEFAULT_UNDERGROUND";
        env.LightDirection = new Vector3(90f, 0, 0); // 90 on X would be straight down from the top, 90 on Y points East
        env.LightColor = new Color(0.45f, 0.42f, 0.4f, 1f);
        env.AmbientColor = new Color(0.65f, 0.65f, 0.65f, 1f);
        env.SkyColor = new Color(0, 0, 0);
        env.FogDepth = 100;
        env.Type = EnvironmentType.UNDERGROUND.getValue();
        return env;
    }

    @Override
    public String toString() {
        if (Name != null)
            return Name;
        return "null";
    }
}
