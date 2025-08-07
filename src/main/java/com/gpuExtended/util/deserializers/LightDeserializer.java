package com.gpuExtended.util.deserializers;

import com.google.gson.*;
import com.gpuExtended.rendering.Vector3;
import com.gpuExtended.rendering.Vector4;
import com.gpuExtended.scene.KeyframedLightAnimation;
import com.gpuExtended.scene.Light;
import com.gpuExtended.scene.LightKeyframe;

import java.awt.Color;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class LightDeserializer implements JsonDeserializer<Light> {
    @Override
    public Light deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject lightObject = json.getAsJsonObject();

        String name = lightObject.get("name").getAsString();

        Light.LightType type = Light.LightType.None;
        if(lightObject.has("type")) {
            type = context.deserialize(lightObject.get("type"), Light.LightType.class);
        }

        Light.LightAnimation animation = Light.LightAnimation.None;
        if(lightObject.has("animation")) {
            animation = context.deserialize(lightObject.get("animation"), Light.LightAnimation.class);
        }

        Color color = new Color(1,1,1);
        if(lightObject.has("color")) {
            JsonArray colorArray = lightObject.getAsJsonArray("color");
            color = new Color(
                    colorArray.get(0).getAsFloat(),
                    colorArray.get(1).getAsFloat(),
                    colorArray.get(2).getAsFloat()
            );
        }

        // check is light has offset
        Vector3 offset = new Vector3(0,0,0);
        if(lightObject.has("offset")) {
            JsonArray positionArray = lightObject.getAsJsonArray("offset");
            offset = new Vector3(
                    positionArray.get(0).getAsFloat(),
                    positionArray.get(1).getAsFloat(),
                    positionArray.get(2).getAsFloat()
            );
        }

        float intensity = 1;
        if(lightObject.has("intensity")) {
            intensity = lightObject.get("intensity").getAsFloat();
        }

        float radius = 2;
        if (lightObject.has("radius")) {
            radius = lightObject.get("radius").getAsFloat();
        }

        List<KeyframedLightAnimation> lightAnimations = new ArrayList<>();
        if (lightObject.has("animations")) {
            JsonArray animationsArray = lightObject.getAsJsonArray("animations");
            for (JsonElement animElement : animationsArray) {
                JsonObject animObject = animElement.getAsJsonObject();
                int animId = animObject.get("id").getAsInt();

                List<LightKeyframe> keyframes = new ArrayList<>();
                if (animObject.has("frames")) {
                    JsonArray framesArray = animObject.getAsJsonArray("frames");
                    for (JsonElement frameElement : framesArray) {
                        JsonObject frameObject = frameElement.getAsJsonObject();
                        int frame = frameObject.get("frame").getAsInt();

                        // Use the light's default values if not specified in the keyframe
                        Color frameColor = color;
                        if (frameObject.has("color")) {
                            JsonArray frameColorArray = frameObject.getAsJsonArray("color");
                            frameColor = new Color(
                                    frameColorArray.get(0).getAsFloat(),
                                    frameColorArray.get(1).getAsFloat(),
                                    frameColorArray.get(2).getAsFloat()
                            );
                        }

                        Float frameIntensity = intensity;
                        if (frameObject.has("intensity")) {
                            frameIntensity = frameObject.get("intensity").getAsFloat();
                        }

                        Float frameRadius = radius;
                        if (frameObject.has("radius")) {
                            frameRadius = frameObject.get("radius").getAsFloat();
                        }

                        keyframes.add(new LightKeyframe(frame, frameColor, frameIntensity, frameRadius));
                    }
                }
                lightAnimations.add(new KeyframedLightAnimation(animId, keyframes));
            }
        }

        int[][] tiles = context.deserialize(lightObject.get("tiles"), int[][].class);
        int[] decorations = context.deserialize(lightObject.get("decorations"), int[].class);
        int[] gameObjects = context.deserialize(lightObject.get("gameObjects"), int[].class);
        int[] projectiles = context.deserialize(lightObject.get("projectiles"), int[].class);
        int[] walls = context.deserialize(lightObject.get("walls"), int[].class);
        int[] npcs = context.deserialize(lightObject.get("npcs"), int[].class);

        return new Light(name, type, animation, color, offset, intensity, radius, tiles, decorations, gameObjects, walls, projectiles, npcs);
    }
}
