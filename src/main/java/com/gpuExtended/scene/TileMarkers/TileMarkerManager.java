package com.gpuExtended.scene.TileMarkers;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.gpuExtended.GpuExtendedConfig;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.rendering.Texture2D;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.overlay.OverlayManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.gpuExtended.GpuExtendedPlugin.SCENE_OFFSET;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;

@Slf4j
@Singleton
public class TileMarkerManager {
    private static final String CONFIG_GROUP = "groundMarker";
    private static final String WALK_HERE = "Walk here";
    private static final String REGION_PREFIX = "region_";

    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private EventBus eventBus;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GpuExtendedPlugin plugin;
    @Inject
    private Gson gson;
    @Inject
    private ConfigManager configManager;
    @Inject
    private GpuExtendedConfig config;

    @Getter(AccessLevel.PACKAGE)
    private final List<ColorTileMarker> points = new ArrayList<>();

    public Texture2D tileSettingsTexture;
    public Texture2D tileFillColorTexture;
    public Texture2D tileBorderColorTexture;

    Texture2D.TextureSettings textureSettings;

    public void Initialize(int extendedSceneSize) {
        Texture2D.TextureSettings textureSettings = new Texture2D.TextureSettings();
        textureSettings.minFilter = GL_NEAREST;
        textureSettings.magFilter = GL_NEAREST;
        textureSettings.wrapS = GL_CLAMP_TO_EDGE;
        textureSettings.wrapT = GL_CLAMP_TO_EDGE;
        textureSettings.internalFormat = GL_RGBA8;
        textureSettings.format = GL_RGBA;
        textureSettings.type = GL_UNSIGNED_BYTE;
        textureSettings.width = extendedSceneSize;
        textureSettings.height = extendedSceneSize;

        tileFillColorTexture = new Texture2D(textureSettings);
        tileFillColorTexture.floodPixels(0, 0, 0, 0);

        tileSettingsTexture = new Texture2D(textureSettings);
        tileSettingsTexture.floodPixels(0, 0, 0, 0);

        tileBorderColorTexture = new Texture2D(textureSettings);
        tileBorderColorTexture.floodPixels(0, 0, 0, 0);
    }

    public void Reset()
    {
        if (tileSettingsTexture != null)
        {
            tileSettingsTexture.floodPixels(0, 0, 0, 0);
        }

        if (tileFillColorTexture != null)
        {
            tileFillColorTexture.floodPixels(0, 0, 0, 0);
        }

        if (tileBorderColorTexture != null)
        {
            tileBorderColorTexture.floodPixels(0, 0, 0, 0);
        }
    }

    public Collection<TileMarker> LoadRegionTileData(int regionId)
    {
        String json = configManager.getConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId);
        if (Strings.isNullOrEmpty(json))
        {
            return Collections.emptyList();
        }

        log.info("Tile Marker JSON: {}", json);
        return gson.fromJson(json, new TypeToken<List<TileMarker>>(){}.getType());
    }

    public Collection<ColorTileMarker> ConvertToColorTileMarker(Collection<TileMarker> points)
    {
        if(points.isEmpty())
        {
            return Collections.emptyList();
        }

        return points.stream()
                .map(point -> new ColorTileMarker(
                        WorldPoint.fromRegion(point.getRegionId(), point.getRegionX(), point.getRegionY(), point.getZ()),
                        point.getColor(), point.getLabel()))
                .flatMap(colorTile ->
                {
                    final Collection<WorldPoint> localWorldPoints = WorldPoint.toLocalInstance(client, colorTile.getWorldPoint());
                    return localWorldPoints.stream().map(wp -> new ColorTileMarker(wp, colorTile.getColor(), colorTile.getLabel()));
                })
                .collect(Collectors.toList());
    }

    public void LoadTileMarkers()
    {
        points.clear();

        int[] regions = client.getMapRegions();

        if (regions == null)
        {
            return;
        }

        for (int regionId : regions)
        {
            Collection<TileMarker> regionPoints = LoadRegionTileData(regionId);
            Collection<ColorTileMarker> colorTileMarkers = ConvertToColorTileMarker(regionPoints);
            points.addAll(colorTileMarkers);
        }

        for(int i = 0; i < points.size(); i++)
        {
            ColorTileMarker tileMarker = points.get(i);

            LocalPoint localPoint = LocalPoint.fromWorld(client, tileMarker.getWorldPoint());
            int x = (localPoint.getX() / Perspective.LOCAL_TILE_SIZE) + SCENE_OFFSET;
            int y = (localPoint.getY() / Perspective.LOCAL_TILE_SIZE) + SCENE_OFFSET;

            log.info("Marking Tile at: " + x + ", " + y);

            int fillColorR = config.tileMarkerFillColor().getRed();
            int fillColorG = config.tileMarkerFillColor().getGreen();
            int fillColorB = config.tileMarkerFillColor().getBlue();
            int fillColorA = config.tileMarkerFillColor().getAlpha();

            int outlineColorR = tileMarker.getColor().getRed();
            int outlineColorG = tileMarker.getColor().getGreen();
            int outlineColorB = tileMarker.getColor().getBlue();
            int outlineColorA = tileMarker.getColor().getAlpha();

            tileFillColorTexture.setPixel(x, y, fillColorR, fillColorG, fillColorB, fillColorA);
            tileBorderColorTexture.setPixel(x, y, outlineColorR, outlineColorG, outlineColorB, outlineColorA);
            tileSettingsTexture.setPixel(x, y, config.tileMarkerCornerLength(), config.tileMarkerBorderWidth(), 0, 0);
        }
    }

    public void SaveTileMarkers(int regionId, Collection<TileMarker> points)
    {
        if (points == null || points.isEmpty())
        {
            configManager.unsetConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId);
            return;
        }

        String json = gson.toJson(points);
        configManager.setConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId, json);
    }
}
