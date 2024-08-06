package com.gpuExtended.overlays;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.shader.ShaderHandler;
import com.gpuExtended.shader.ShaderException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.*;
import java.io.IOException;

import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL20C.glUniform4i;

@Slf4j
@Singleton
public class ShadowMapOverlay extends Overlay {
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
    private ShaderHandler shaderHandler;

    private boolean isActive;

    public ShadowMapOverlay() {
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.DYNAMIC);
        setResizable(true);
    }

    public void setActive(boolean activate) {
        if (activate == isActive)
            return;

        isActive = activate;

        if (activate) {
            overlayManager.add(this);
            plugin.enableShadowMapOverlay = true;
            eventBus.register(this);
        } else {
            overlayManager.remove(this);
            plugin.enableShadowMapOverlay = false;
            eventBus.unregister(this);
        }

        clientThread.invoke(() -> {
            try {
                shaderHandler.Recompile();
            } catch (IOException | ShaderException ex) {
                log.error("Error while recompiling shaders:", ex);
                plugin.stopPlugin();
            }
        });
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
            glUseProgram(shaderHandler.uiShader.id());
            int uniBounds = glGetUniformLocation(shaderHandler.uiShader.id(), "shadowMapOverlayDimensions");
            if (uniBounds != -1)
                glUniform4i(uniBounds, 0, 0, 0, 0);
        }
    }

    @Override
    public Dimension render(Graphics2D g) {
        var bounds = getBounds();

        clientThread.invoke(() -> {
            if (shaderHandler.uiShader.id() == 0) {
                log.error("ShadowMapOverlay: glUiProgram is 0");
                return;
            }
            glUseProgram(shaderHandler.uiShader.id());
            int uniShadowMap = glGetUniformLocation(shaderHandler.uiShader.id(), "shadowMap");
            glUniform1i(uniShadowMap, 2);

            int uniBounds = glGetUniformLocation(shaderHandler.uiShader.id(), "shadowMapOverlayDimensions");
            if (uniBounds != -1) {
                if (client.getGameState().getState() < GameState.LOGGED_IN.getState()) {
                    glUniform4i(uniBounds, 0, 0, 0, 0);
                } else {
                    int canvasWidth = client.getCanvasWidth();
                    int canvasHeight = client.getCanvasHeight();
                    float scaleX = 1;
                    float scaleY = 1;
                    if (client.isStretchedEnabled()) {
                        var stretchedDims = client.getStretchedDimensions();
                        scaleX = (float) stretchedDims.width / canvasWidth;
                        scaleY = (float) stretchedDims.height / canvasHeight;
                    }
                    glUniform4i(uniBounds,
                            (int) Math.floor((bounds.x + 1) * scaleX), (int) Math.floor((canvasHeight - bounds.height - bounds.y) * scaleY),
                            (int) Math.ceil((bounds.width - 1) * scaleX), (int) Math.ceil((bounds.height - 1) * scaleY)
                    );
                }
            }

            plugin.checkGLErrors();
        });

        return new Dimension(256, 256);
    }
}
