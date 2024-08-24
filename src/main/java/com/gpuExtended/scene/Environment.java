package com.gpuExtended.scene;

import java.awt.Color;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Setter(value = AccessLevel.PRIVATE)
@NoArgsConstructor
@Slf4j
public class Environment {
    public String Name;
    public Color SkyColor;
    public Color AmbientColor;
    public Color LightColor;
    public float LightPitch;
    public float LightYaw;
    public float FogDepth;
    public int Type;

    public boolean isTransitioning = false;
    public float transitionProgress = 0.0f;
    private Environment lastEnvironment;

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

    public void SwitchToEnvironment(Environment target, float deltaTime)
    {
        if (target == null)
        {
            return;
        }

        this.transitionProgress += deltaTime;
        float easedProgress = easeOutQuad(this.transitionProgress);

        this.Name           = target.Name;
        this.Type           = target.Type;
        this.SkyColor       = lerpColor(lastEnvironment.SkyColor, target.SkyColor, easedProgress);
        this.AmbientColor   = lerpColor(lastEnvironment.AmbientColor, target.AmbientColor, easedProgress);
        this.LightColor     = lerpColor(lastEnvironment.LightColor, target.LightColor, easedProgress);
        this.LightPitch     = lerpFloat(lastEnvironment.LightPitch, target.LightPitch, easedProgress);
        this.LightYaw       = lerpFloat(lastEnvironment.LightYaw, target.LightYaw, easedProgress);
        this.FogDepth       = lerpFloat(lastEnvironment.FogDepth, target.FogDepth, easedProgress);

        if (this.transitionProgress >= 1.0f)
        {
            this.transitionProgress = 0.0f;
            this.isTransitioning = false;
        }
    }

    public void PrepareEnvironmentTransition(Environment lastEnvironmentSettings)
    {
        lastEnvironment = lastEnvironmentSettings;
        this.isTransitioning = true;
        this.transitionProgress = 0.0f;
    }

    public static Color lerpColor(Color color1, Color color2, float t) {
        // Clamp t to the range [0, 1]
        t = Math.max(0, Math.min(1, t));

        // Interpolate each color component
        int r = (int) (color1.getRed() + t * (color2.getRed() - color1.getRed()));
        int g = (int) (color1.getGreen() + t * (color2.getGreen() - color1.getGreen()));
        int b = (int) (color1.getBlue() + t * (color2.getBlue() - color1.getBlue()));
        int a = (int) (color1.getAlpha() + t * (color2.getAlpha() - color1.getAlpha()));

        return new Color(r, g, b, a);
    }

    public static float lerpFloat(float start, float end, float t) {
        // Clamp t to the range [0, 1]
        t = Math.max(0, Math.min(1, t));

        return (start + t * (end - start));
    }

    public float easeOutQuad(float t) {
        return 1 - (float) Math.pow(1 - t, 4);
    }
}
