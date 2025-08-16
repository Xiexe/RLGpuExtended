package com.gpuExtended.overlays;

import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.shader.ShaderException;
import com.gpuExtended.util.Utils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.Scene;
import net.runelite.api.Perspective;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.ComponentConstants;

import javax.inject.Inject;
import java.awt.*;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.gpuExtended.util.constants.Variables.SCENE_OFFSET;

@Slf4j
public class TileInspectorOverlay extends Overlay
{

    @Inject
    public GpuExtendedPlugin plugin;

    @Inject
    private EventBus eventBus;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private FontManager fontManager;

    private boolean isActive = false;

    public TileInspectorOverlay()
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(OverlayPriority.HIGH);
    }

    public void setActive(boolean activate) {
        if (activate == isActive)
            return;

        isActive = activate;

        if (activate) {
            overlayManager.add(this);
            plugin.showTileInspectorOverlay = true;
            eventBus.register(this);
        } else {
            overlayManager.remove(this);
            plugin.showTileInspectorOverlay = false;
            eventBus.unregister(this);
        }
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        if (plugin.client == null || plugin.client.getMouseCanvasPosition() == null)
            return null;

        Point mousePos = plugin.client.getMouseCanvasPosition();
        Tile tile = getHoveredTile(mousePos);
        if (tile == null)
            return null;

        drawTileOverlay(g, tile);
        drawTooltip(g, tile, mousePos);
        return null;
    }

    private Tile getHoveredTile(Point mousePos)
    {
        Scene scene = plugin.client.getScene();
        Tile[][][] tiles = scene.getTiles();
        int plane = plugin.client.getPlane();

        for (int x = 0; x < Constants.SCENE_SIZE; x++)
        {
            for (int y = 0; y < Constants.SCENE_SIZE; y++)
            {
                Tile tile = tiles[plane][x][y];
                if (tile == null)
                    continue;

                Polygon poly = Perspective.getCanvasTileAreaPoly(plugin.client, tile.getLocalLocation(), 1);
                if (poly != null && poly.contains(mousePos.getX(), mousePos.getY()))
                    return tile;
            }
        }

        return null;
    }

    private void drawTileOverlay(Graphics2D g, Tile tile)
    {
        Polygon poly = Perspective.getCanvasTilePoly(plugin.client, tile.getLocalLocation());
        if (poly != null)
        {
            g.setColor(Color.magenta);
            g.draw(poly);
        }
    }

    private void drawTooltip(Graphics2D g, Tile tile, Point location)
    {
        Font rsFontSmall = fontManager.getRunescapeSmallFont();
        Font rsBoldFont = fontManager.getRunescapeFont();

        int plane = tile.getPlane();
        WorldPoint worldLocationPoint = tile.getWorldLocation();
        int sceneX = tile.getSceneLocation().getX();
        int sceneY = tile.getSceneLocation().getY();

        int underlayId = plugin.client.getScene().getUnderlayIds()[plane][sceneX][sceneY];
        int overlayId = plugin.client.getScene().getOverlayIds()[plane][sceneX][sceneY];

        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("Scene: ", String.format("(%d, %d, %d)", sceneX, sceneY, plane));
        lines.put("World: ", String.format("(%d, %d, %d)", worldLocationPoint.getX(), worldLocationPoint.getY(), plane));
        lines.put(" ", "");
        lines.put("Overlay: ", String.valueOf(overlayId));
        lines.put("Underlay: ", String.valueOf(underlayId));

        DecorativeObject decorObjects = tile.getDecorativeObject();
        if (decorObjects != null)
        {
            int decorId = decorObjects.getId();
            int orientation = Utils.getModelOrientation(decorObjects.getConfig());
            lines.put("Decorative: ", String.format("id: {%d}, ori: {%d}", decorId, orientation));
        }

        FontMetrics metricsRegular = g.getFontMetrics(rsFontSmall);
        FontMetrics metricsBold = g.getFontMetrics(rsBoldFont);

        int maxWidth = 0;
        for (Map.Entry<String, String> entry : lines.entrySet())
        {
            int labelWidth = metricsBold.stringWidth(entry.getKey());
            int valueWidth = metricsRegular.stringWidth(entry.getValue());
            maxWidth = Math.max(maxWidth, labelWidth + valueWidth);
        }

        int lineHeight = metricsRegular.getHeight();
        int padding = 4;
        int linePadding = 2;
        int totalWidth = maxWidth + (padding * 2);
        int totalHeight = (lineHeight * lines.size()) + (linePadding * (lines.size() - 1)) + (padding * 2);

        int tooltipPosX = location.getX() + 8;
        int tooltipPosY = location.getY() + 16;
        int boxX = tooltipPosX - padding;
        int boxY = tooltipPosY - metricsRegular.getAscent() - padding;

        g.setColor(ComponentConstants.STANDARD_BACKGROUND_COLOR);
        g.fillRect(boxX, boxY, totalWidth, totalHeight);

        Stroke originalStroke = g.getStroke();
        g.setStroke(new BasicStroke(ComponentConstants.STANDARD_BORDER / 2));
        g.setColor(ComponentConstants.STANDARD_BACKGROUND_COLOR);
        g.drawRect(boxX, boxY, totalWidth, totalHeight);
        g.setStroke(originalStroke);

        g.setColor(Color.WHITE);
        int textX = tooltipPosX;
        int textY = tooltipPosY;

        for (Map.Entry<String, String> entry : lines.entrySet())
        {
            String label = entry.getKey();
            String value = entry.getValue();

            g.setFont(rsBoldFont);
            g.drawString(label, textX, textY);

            int labelWidth = metricsBold.stringWidth(label);

            g.setFont(rsFontSmall);
            g.drawString(value, textX + labelWidth, textY);

            textY += lineHeight + linePadding;
        }
    }
}
