package com.gpuExtended.util.deserializers;

import com.google.gson.*;
import com.gpuExtended.rendering.Vector3;
import com.gpuExtended.rendering.Vector4;
import com.gpuExtended.scene.Light;

import java.awt.Color;
import java.lang.reflect.Type;

public class LightDeserializer implements JsonDeserializer<Light> {
    @Override
    public Light deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject lightObject = json.getAsJsonObject();

        String name = lightObject.get("name").getAsString();
        Light.LightType type = context.deserialize(lightObject.get("type"), Light.LightType.class);
        Light.LightAnimation animation = context.deserialize(lightObject.get("animation"), Light.LightAnimation.class);

        JsonArray colorArray = lightObject.getAsJsonArray("color");
        Color color = new Color(
                colorArray.get(0).getAsFloat(),
                colorArray.get(1).getAsFloat(),
                colorArray.get(2).getAsFloat()
        );

        JsonArray positionArray = lightObject.getAsJsonArray("offset");
        Vector3 offset = new Vector3(
                positionArray.get(0).getAsFloat(),
                positionArray.get(1).getAsFloat(),
                positionArray.get(2).getAsFloat()
        );

        float intensity = lightObject.get("intensity").getAsFloat();
        float radius = lightObject.get("radius").getAsFloat();

        int[][] tiles = context.deserialize(lightObject.get("tiles"), int[][].class);
        int[] decorations = context.deserialize(lightObject.get("decorations"), int[].class);
        int[] gameObjects = context.deserialize(lightObject.get("gameObjects"), int[].class);
        int[] projectiles = context.deserialize(lightObject.get("projectiles"), int[].class);
        int[] walls = context.deserialize(lightObject.get("walls"), int[].class);

        return new Light(name, type, animation, color, new Vector4(0,0,0,0), offset, intensity, radius, tiles, decorations, gameObjects, walls, projectiles, 0);
    }
}
