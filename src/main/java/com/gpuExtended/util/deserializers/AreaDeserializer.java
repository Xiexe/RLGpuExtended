package com.gpuExtended.util.deserializers;

import com.google.gson.*;
import com.gpuExtended.regions.Area;
import com.gpuExtended.regions.Region;
import com.gpuExtended.regions.Bounds;
import com.gpuExtended.rendering.Vector3;

import java.lang.reflect.Type;

public class AreaDeserializer implements JsonDeserializer<Area> {
    @Override
    public Area deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        String name = jsonObject.get("name").getAsString();
        Bounds[] bounds = null;

        if(jsonObject.has("bounds")) {
            JsonArray boundsArray = jsonObject.getAsJsonArray("bounds");
            bounds = new Bounds[boundsArray.size()];
            for (int i = 0; i < boundsArray.size(); i++) {
                JsonObject boundObject = boundsArray.get(i).getAsJsonObject();

                String boundName = name;
                if(boundObject.has("name")) {
                    boundName = boundObject.get("name").getAsString();
                }

                JsonArray startArray = boundObject.getAsJsonArray("start");
                Vector3 start = new Vector3(
                        startArray.get(0).getAsInt(),
                        startArray.get(1).getAsInt(),
                        startArray.get(2).getAsInt()
                );

                JsonArray endArray = boundObject.getAsJsonArray("end");
                Vector3 end = new Vector3(
                        endArray.get(0).getAsInt(),
                        endArray.get(1).getAsInt(),
                        endArray.get(2).getAsInt()
                );

                boolean hideOtherAreas = false;
                if(boundObject.has("hideOtherAreas"))
                {
                    hideOtherAreas = boundObject.get("hideOtherAreas").getAsBoolean();
                }

                bounds[i] = new Bounds(boundName, start, end, hideOtherAreas);
            }
        }

        return new Area(name, bounds);
    }
}
