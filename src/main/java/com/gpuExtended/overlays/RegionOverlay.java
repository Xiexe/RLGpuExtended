package com.gpuExtended.overlays;

import com.google.inject.Inject;
import com.gpuExtended.GpuExtendedPlugin;
import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

import java.awt.*;

public class RegionOverlay extends OverlayPanel {

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

    public boolean isActive;

    public RegionOverlay()
    {
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(300, 200));
    }

    public void setActive(boolean activate) {
        if (activate == isActive)
            return;

        isActive = activate;
        if (activate) {
            overlayManager.add(this);
            plugin.showRegionOverlay = true;
            eventBus.register(this);
        } else {
            overlayManager.remove(this);
            plugin.showRegionOverlay = false;
            eventBus.unregister(this);
        }
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!plugin.showRegionOverlay)
        {
            return null;
        }

        panelComponent.setWrap(true);
        panelComponent.setPreferredSize(new Dimension(1000, 200));

        WorldPoint worldPoint = client.getLocalPlayer().getWorldLocation();
        LocalPoint localPoint = client.getLocalPlayer().getLocalLocation();

        if (client.isInInstancedRegion())
        {
            worldPoint = WorldPoint.fromLocalInstance(client, localPoint);

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Instance")
                    .build());
        }

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Local")
                .right(localPoint.getX() + ", " + localPoint.getY())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("World")
                .right(worldPoint.getX() + ", " + worldPoint.getY() + ", " + client.getPlane())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Scene")
                .right(localPoint.getSceneX() + ", " + localPoint.getSceneY())
                .build());

        int region = worldPoint.getRegionID();
        int mx = region >> 8;
        int my = region & 0xff;

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Region Id")
                .right(region + "")
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Region XY")
                .right(mx + ", " + my)
                .build());

        if(plugin.environmentManager.currentArea != null) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Region")
                    .right(plugin.environmentManager.currentArea.getName())
                    .build());

            if(plugin.environmentManager.currentBounds != null) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Area")
                        .right(plugin.environmentManager.currentBounds.getName())
                        .build());
            }
        }

        return super.render(graphics);
    }
}
