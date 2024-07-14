package com.gpuExtended.util.deserializers;

import com.google.gson.*;
import com.gpuExtended.regions.Area;
import com.gpuExtended.regions.Region;
import com.gpuExtended.regions.SubArea;
import com.gpuExtended.rendering.Vector3;

import java.lang.reflect.Type;

public class AreaDeserializer implements JsonDeserializer<Area> {
    @Override
    public Area deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        String name = jsonObject.get("name").getAsString();
        Region[] regions = null;
        SubArea[] subAreas = null;

        if (jsonObject.has("regions")) {
            JsonArray regionsArray = jsonObject.getAsJsonArray("regions");
            regions = new Region[regionsArray.size()];
            for (int i = 0; i < regionsArray.size(); i++) {
                JsonArray regionCoordinates = regionsArray.get(i).getAsJsonArray();
                int x = regionCoordinates.get(0).getAsInt();
                int y = regionCoordinates.get(1).getAsInt();
                regions[i] = new Region(x, y);
            }
        }

        if (jsonObject.has("subAreas")) {
            JsonArray subAreasArray = jsonObject.getAsJsonArray("subAreas");
            subAreas = new SubArea[subAreasArray.size()];
            for (int i = 0; i < subAreasArray.size(); i++) {
                JsonObject subAreaObject = subAreasArray.get(i).getAsJsonObject();
                String subAreaName = subAreaObject.get("name").getAsString();

                JsonArray startArray = subAreaObject.getAsJsonArray("start");
                Vector3 start = new Vector3(
                        startArray.get(0).getAsInt(),
                        startArray.get(1).getAsInt(),
                        startArray.get(2).getAsInt()
                );

                JsonArray endArray = subAreaObject.getAsJsonArray("end");
                Vector3 end = new Vector3(
                        endArray.get(0).getAsInt(),
                        endArray.get(1).getAsInt(),
                        endArray.get(2).getAsInt()
                );

                subAreas[i] = new SubArea(subAreaName, start, end);
            }
        }

        return new Area(name, regions, subAreas);
    }
}
