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
    public String Name;
    public Color SkyColor;
    public Color AmbientColor;
    public Color LightColor;
    public int LightPitch;
    public int LightYaw;
    public int FogDepth;
    public int Type;

    @Override
    public String toString() {
        if (this != null) {
            return "\n Environment {" +
                    "\n    Name='" + Name + '\'' +
                    ", \n    SkyColor=" + SkyColor +
                    ", \n    AmbientColor=" + AmbientColor +
                    ", \n    LightColor=" + LightColor +
                    ", \n    LightPitch=" + LightPitch +
                    ", \n    LightYaw=" + LightYaw +
                    ", \n    FogDepth=" + FogDepth +
                    ", \n    Type=" + Type +
                    "\n}";
        }
        return "null";
    }
}
