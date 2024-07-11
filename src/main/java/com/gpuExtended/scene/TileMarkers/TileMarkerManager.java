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
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;

import java.awt.*;
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

    Color defaultFillColor = new Color(0, 0, 0, 75);

    private int currentPlane = 0;
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
                        WorldPoint.fromRegion(
                                point.getRegionId(),
                                point.getRegionX(),
                                point.getRegionY(),
                                point.getZ()),
                                point.getColor(),
                                point.getFillColor(),
                                point.getCornerLength(),
                                point.getBorderWidth(),
                                point.getLabel()))
                .flatMap(colorTile ->
                {
                    final Collection<WorldPoint> localWorldPoints = WorldPoint.toLocalInstance(client, colorTile.getWorldPoint());
                    return localWorldPoints.stream()
                        .map(wp -> new ColorTileMarker(
                            wp,
                            colorTile.getColor(),
                            colorTile.getFillColor(),
                            colorTile.getCornerLength(),
                            colorTile.getBorderWidth(),
                            colorTile.getLabel()
                        )
                    );
                })
                .collect(Collectors.toList());
    }

    public void DrawTileMarker(LocalPoint localPoint, Color fillColor, Color borderColor, int cornerLength, int borderWidth)
    {
        if(localPoint == null)
            return;

        int x = (localPoint.getX() / Perspective.LOCAL_TILE_SIZE) + SCENE_OFFSET;
        int y = (localPoint.getY() / Perspective.LOCAL_TILE_SIZE) + SCENE_OFFSET;

        int fillColorR = fillColor == null ? defaultFillColor.getRed() : fillColor.getRed();
        int fillColorG = fillColor == null ? defaultFillColor.getGreen() : fillColor.getGreen();
        int fillColorB = fillColor == null ? defaultFillColor.getBlue() : fillColor.getBlue();
        int fillColorA = fillColor == null ? defaultFillColor.getAlpha() : fillColor.getAlpha();

        int outlineColorR = borderColor == null ? 0 : borderColor.getRed();
        int outlineColorG = borderColor == null ? 0 : borderColor.getGreen();
        int outlineColorB = borderColor == null ? 0 : borderColor.getBlue();
        int outlineColorA = borderColor == null ? 0 : borderColor.getAlpha();

        int cornerL = cornerLength == 0 ? 64 : cornerLength;
        int borderW = borderWidth == 0 ? 4 : borderWidth;

        tileFillColorTexture.setPixel(x, y, fillColorR, fillColorG, fillColorB, fillColorA);
        tileBorderColorTexture.setPixel(x, y, outlineColorR, outlineColorG, outlineColorB, outlineColorA);
        tileSettingsTexture.setPixel(x, y, cornerL, borderW, 0, 0);
    }

    public void MarkTile(WorldPoint worldPoint, LocalPoint localPoint, Color fillColor, Color borderColor, int cornerLength, int borderWidth)
    {
        List<TileMarker> groundMarkerPoints = new ArrayList<>(LoadRegionTileData(worldPoint.getRegionID()));
        TileMarker point = new TileMarker(worldPoint.getRegionID(), worldPoint.getRegionX(), worldPoint.getRegionY(), worldPoint.getPlane(), borderColor, fillColor, null, cornerLength, borderWidth);
        if (groundMarkerPoints.contains(point))
        {
            groundMarkerPoints.remove(point);
        }
        else
        {
            groundMarkerPoints.add(point);
        }

        SaveTileMarkers(worldPoint.getRegionID(), groundMarkerPoints);
    }

    public void UpdateTile(WorldPoint worldPoint, LocalPoint localPoint, Color fillColor, Color borderColor, int cornerLength, int borderWidth)
    {
        List<TileMarker> groundMarkerPoints = new ArrayList<>(LoadRegionTileData(worldPoint.getRegionID()));
        TileMarker point = new TileMarker(worldPoint.getRegionID(), worldPoint.getRegionX(), worldPoint.getRegionY(), worldPoint.getPlane(), borderColor, fillColor, null, cornerLength, borderWidth);
        if (groundMarkerPoints.contains(point))
        {
            groundMarkerPoints.remove(point);
            groundMarkerPoints.add(point);
        }

        SaveTileMarkers(worldPoint.getRegionID(), groundMarkerPoints);
    }

    public void LoadTileMarkers()
    {
        points.clear();
        tileBorderColorTexture.floodPixels(0, 0, 0, 0);
        tileFillColorTexture.floodPixels(0, 0, 0, 0);
        tileSettingsTexture.floodPixels(0, 0, 0, 0);

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

        if(!points.isEmpty()) {
            for (int i = 0; i < points.size(); i++) {
                ColorTileMarker tileMarker = points.get(i);
                int plane = tileMarker.getWorldPoint().getPlane();
                if (plane != client.getPlane())
                    continue;

                LocalPoint localPoint = LocalPoint.fromWorld(client, tileMarker.getWorldPoint());
                DrawTileMarker(
                        localPoint,
                        tileMarker.getFillColor(),
                        tileMarker.getColor(),
                        tileMarker.getCornerLength(),
                        tileMarker.getBorderWidth()
                );
            }
        }
    }

    public void SaveTileMarkers(int regionId, Collection<TileMarker> points)
    {
        if (points == null || points.isEmpty())
        {
            configManager.unsetConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId);
            tileBorderColorTexture.floodPixels(0, 0, 0, 0);
            tileFillColorTexture.floodPixels(0, 0, 0, 0);
            tileSettingsTexture.floodPixels(0, 0, 0, 0);
            return;
        }

        String json = gson.toJson(points);
        configManager.setConfiguration(CONFIG_GROUP, REGION_PREFIX + regionId, json);

        LoadTileMarkers();
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        final boolean hotKeyPressed = client.isKeyPressed(KeyCode.KC_SHIFT);
        if (hotKeyPressed && event.getOption().equals(WALK_HERE)) {
            final Tile selectedSceneTile = client.getSelectedSceneTile();

            if (selectedSceneTile == null) {
                return;
            }

            final WorldPoint worldPoint = WorldPoint.fromLocalInstance(client, selectedSceneTile.getLocalLocation());
            final int regionId = worldPoint.getRegionID();
            var regionPoints = LoadRegionTileData(regionId);

            var existingOpt = regionPoints.stream()
                    .filter(p -> p.getRegionX() == worldPoint.getRegionX() && p.getRegionY() == worldPoint.getRegionY() && p.getZ() == worldPoint.getPlane())
                    .findFirst();

            boolean isMarked = existingOpt.isPresent();

            client.createMenuEntry(-1)
                    .setOption(isMarked ? "Unmark" : "Mark")
                    .setTarget("Tile")
                    .setType(MenuAction.RUNELITE)
                    .onClick(e ->
                    {
                        Tile target = client.getSelectedSceneTile();
                        if (target != null) {
                            MarkTile(
                                worldPoint,
                                target.getLocalLocation(),
                                config.tileMarkerFillColor(),
                                config.tileMarkerBorderColor(),
                                config.tileMarkerCornerLength(),
                                config.tileMarkerBorderWidth()
                            );
                        };
                    });

            if(isMarked)
            {
                client.createMenuEntry(-1)
                        .setOption("Remark")
                        .setTarget("Tile")
                        .setType(MenuAction.RUNELITE)
                        .onClick(e ->
                        {
                            Tile target = client.getSelectedSceneTile();
                            if (target != null) {
                                UpdateTile(
                                        worldPoint,
                                        target.getLocalLocation(),
                                        config.tileMarkerFillColor(),
                                        config.tileMarkerBorderColor(),
                                        config.tileMarkerCornerLength(),
                                        config.tileMarkerBorderWidth()
                                );
                            };
                        });
            }
        }
    }
}
