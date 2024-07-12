package com.gpuExtended.util;

import com.google.gson.*;
import java.awt.Color;
import java.lang.reflect.Type;

public class ColorDeserializer implements JsonDeserializer<Color> {
    @Override
    public Color deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonArray jsonArray = json.getAsJsonArray();
        int r = jsonArray.get(0).getAsInt();
        int g = jsonArray.get(1).getAsInt();
        int b = jsonArray.get(2).getAsInt();
        int a = jsonArray.get(3).getAsInt();
        return new Color(r, g, b, a);
    }
}