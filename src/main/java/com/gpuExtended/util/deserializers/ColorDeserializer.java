package com.gpuExtended.util.deserializers;

import com.google.gson.*;
import java.awt.Color;
import java.lang.reflect.Type;

public class ColorDeserializer implements JsonDeserializer<Color> {
    @Override
    public Color deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonArray jsonArray = json.getAsJsonArray();
        int r = (int)(jsonArray.get(0).getAsFloat() * 255.0f);
        int g = (int)(jsonArray.get(1).getAsFloat() * 255.0f);
        int b = (int)(jsonArray.get(2).getAsFloat() * 255.0f);
        int a = (int)(jsonArray.get(3).getAsFloat() * 255.0f);
        return new Color(r, g, b, a);
    }
}