package com.gpuExtended.util.deserializers;

import com.google.gson.*;
import com.gpuExtended.rendering.Vector2;
import com.gpuExtended.rendering.Vector3;
import com.gpuExtended.rendering.Vector4;

import java.awt.*;
import java.lang.reflect.Type;

public class VectorDeserializer implements JsonDeserializer<Object> {
    @Override
    public Object deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonArray jsonArray = json.getAsJsonArray();
        int length = jsonArray.size();

        switch (length) {
            case 2:
                return new Vector2(
                        jsonArray.get(0).getAsFloat(),
                        jsonArray.get(1).getAsFloat()
                );
            case 3:
                return new Vector3(
                        jsonArray.get(0).getAsFloat(),
                        jsonArray.get(1).getAsFloat(),
                        jsonArray.get(2).getAsFloat()
                );
            case 4:
                return new Vector4(
                        jsonArray.get(0).getAsFloat(),
                        jsonArray.get(1).getAsFloat(),
                        jsonArray.get(2).getAsFloat(),
                        jsonArray.get(3).getAsFloat()
                );
            default:
                throw new JsonParseException("Invalid vector length: " + length);
        }
    }
}
