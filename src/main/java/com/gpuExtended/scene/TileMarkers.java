package com.gpuExtended.scene;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.rendering.Texture2D;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.overlay.OverlayManager;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.*;

@Slf4j
@Singleton
public class TileMarkers {
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
}
