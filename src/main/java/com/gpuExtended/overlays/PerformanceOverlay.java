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

public class PerformanceOverlay extends OverlayPanel {

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

    public PerformanceOverlay()
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

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Static Upload")
                .right("" + "ms")
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Dynamic Upload")
                .right("" + "ms")
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Shader Uniform")
                .right("" + "ms")
                .build());

        return super.render(graphics);
    }
}
