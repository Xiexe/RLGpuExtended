package com.gpuExtended.overlays;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.util.Counter;
import com.gpuExtended.util.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

import java.awt.*;
import java.util.HashMap;

@Singleton
@Slf4j
public class PerformanceOverlay extends OverlayPanel {

    public enum TimerType {
        DRAW_MAIN_PASS,
        DRAW_SHADOW_PASS,
        PUSH_DYNAMIC_GEOMETRY,
        PUSH_STATIC_GEOMETRY,
        FRAME_CPU
    }

    public enum TimeScale {
        NANOSECONDS,
        MILLISECONDS,
        SECONDS
    }

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

    public HashMap<TimerType, Timer> timers = new HashMap<>();

    public PerformanceOverlay()
    {
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(150, 200));
    }

    public void StartTimer(TimerType type) {
        if(!plugin.showPerformanceOverlay)
            return;

        if(!timers.containsKey(type))
        {
            timers.put(type, new Timer());
        }

        timers.get(type).start();
    }

    public void EndTimer(TimerType type) {
        if(!plugin.showPerformanceOverlay)
            return;

        if(!timers.containsKey(type))
        {
            return;
        }

        timers.get(type).stop();
    }

    public double GetTimerElapsedTime(TimerType type, TimeScale timeScale) {
        if(!plugin.showPerformanceOverlay)
            return -1;

        if(!timers.containsKey(type))
        {
            return -1;
        }

        switch (timeScale) {
            case NANOSECONDS:
                return timers.get(type).getElapsedNanoSeconds();
            case MILLISECONDS:
                return timers.get(type).getElapsedMilliseconds();
            case SECONDS:
                return timers.get(type).getElapsedTimeSeconds();
            default:
                return -1;
        }
    }

    public void setActive(boolean activate) {
        if (activate == isActive)
            return;

        isActive = activate;
        if (activate) {
            overlayManager.add(this);
            plugin.showPerformanceOverlay = true;
            eventBus.register(this);
        } else {
            overlayManager.remove(this);
            plugin.showPerformanceOverlay = false;
            eventBus.unregister(this);
        }
    }

    public void ResetTimers() {
        if(!plugin.showPerformanceOverlay)
            return;

        for (Timer timer : timers.values()) {
            timer.reset();
        }
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!plugin.showPerformanceOverlay)
        {
            return null;
        }

        double staticPushTime = GetTimerElapsedTime(TimerType.PUSH_STATIC_GEOMETRY, TimeScale.MILLISECONDS);
        double dynamicPushTime = GetTimerElapsedTime(TimerType.PUSH_DYNAMIC_GEOMETRY, TimeScale.MILLISECONDS);
        double totalPushTime = staticPushTime + dynamicPushTime;

        int staticCalls = timers.get(TimerType.PUSH_STATIC_GEOMETRY).getCount();
        int dynamicCalls = timers.get(TimerType.PUSH_DYNAMIC_GEOMETRY).getCount();
        int totalCalls = staticCalls + dynamicCalls;

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Dynamic Draw Calls")
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left(String.format("    %d", dynamicCalls))
                .right(String.format("%.3f ms", dynamicPushTime))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Static Draw Calls")
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left(String.format("    %d", staticCalls))
                .right(String.format("%.3f ms", staticPushTime))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Draw Calls")
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left(String.format("    %d", totalCalls))
                .right(String.format("%.3f ms", totalPushTime))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("-")
                .right("-")
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Main Pass")
                .right(String.format("%.3f ms", GetTimerElapsedTime(TimerType.DRAW_MAIN_PASS, TimeScale.MILLISECONDS)))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Shadow Pass")
                .right(String.format("%.3f ms", GetTimerElapsedTime(TimerType.DRAW_SHADOW_PASS, TimeScale.MILLISECONDS)))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("-")
                .right("-")
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Total (CPU)")
                .right(String.format("%.3f ms", GetTimerElapsedTime(TimerType.FRAME_CPU, TimeScale.MILLISECONDS)))
                .build());

        return super.render(graphics);
    }
}
