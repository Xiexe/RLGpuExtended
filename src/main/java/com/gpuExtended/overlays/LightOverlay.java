package com.gpuExtended.overlays;

import com.google.inject.Inject;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.rendering.Vector3;
import com.gpuExtended.scene.Light;
import com.gpuExtended.util.Mat4;
import com.gpuExtended.util.Props;
import com.gpuExtended.util.ResourcePath;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import org.apache.commons.lang3.NotImplementedException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static com.gpuExtended.util.ResourcePath.path;

public class LightOverlay extends Overlay {

    ResourcePath POINT_LIGHT_SPRITE = Props.getPathOrDefault(
            "sprites-pointlight-path", () -> path(GpuExtendedPlugin.class, "sprites/sprite_pointlight.png"));

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

    private BufferedImage pointLightSprite;

    public LightOverlay() {
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPosition(OverlayPosition.DYNAMIC);
    }

    public void SetActive(boolean activate) {
        if (activate == isActive)
            return;

        try {
            if (pointLightSprite == null)
                pointLightSprite = POINT_LIGHT_SPRITE.loadImage();
        } catch (IOException e) {
            e.printStackTrace();
        }

        isActive = activate;
        if (activate) {
            overlayManager.add(this);
            plugin.showLightOverlay = true;
            eventBus.register(this);
        } else {
            overlayManager.remove(this);
            plugin.showLightOverlay = false;
            eventBus.unregister(this);
        }
    }

    @Override
    public Dimension render(Graphics2D graphics2D) {
        float pitch = (float) -(Math.PI - plugin.cameraPitch);
        float yaw = (float)plugin.cameraYaw;

        float[] rotationMatrix = new float[] {
                1, 0, 0, 0,
                0, 0, -1, 0,
                0, 1, 0, 0,
                0, 0, 0, 1
        };
        float[] flipMatrix = new float[] {
                1, 0, 0, 0,
                0, -1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1
        };

        float[] projectionMatrix = Mat4.identity();
        int viewportWidth = client.getViewportWidth();
        int viewportHeight = client.getViewportHeight();
        Mat4.mul(projectionMatrix, Mat4.translate(client.getViewportXOffset(), client.getViewportYOffset(), 0));
        Mat4.mul(projectionMatrix, Mat4.scale(viewportWidth, viewportHeight, 1));
        Mat4.mul(projectionMatrix, Mat4.translate(0.5f, 0.5f, 0.5f));
        Mat4.mul(projectionMatrix, Mat4.scale(0.5f, -0.5f, 0.5f));

        // NDC clip space
        Mat4.mul(projectionMatrix, Mat4.scale(client.getScale(), client.getScale(), 1));
        Mat4.mul(projectionMatrix, Mat4.projection(viewportWidth, viewportHeight, 50));
        Mat4.mul(projectionMatrix, Mat4.rotateX(pitch));
        Mat4.mul(projectionMatrix, Mat4.rotateY(yaw));
        Mat4.mul(projectionMatrix, Mat4.translate(
                (float) -plugin.cameraX,
                (float) -plugin.cameraY,
                (float) -plugin.cameraZ
        ));

        Mat4.mul(projectionMatrix, rotationMatrix);
        Mat4.mul(projectionMatrix, flipMatrix);

        float[] inverseProjection = null;
        try {
            inverseProjection = Mat4.inverse(projectionMatrix);
        } catch (IllegalArgumentException ex) {
            System.out.println("Not invertible");
        }

        for(int i = 0; i < plugin.environmentManager.sceneLights.size(); i++) {
            Light light = plugin.environmentManager.sceneLights.get(i);
            if(light == null)
                continue;

            float[] screenPoint = new float[]{
                light.position.x,
                light.position.y,
                light.position.z,
                1
            };
            Mat4.projectVec(screenPoint, projectionMatrix, screenPoint);
            // light is behind the camera
            if(screenPoint[2] < 0)
                continue;

            int x = (int)screenPoint[0];
            int y = (int)screenPoint[1];

            Vector3 lightToCamera = new Vector3(
                    (float) plugin.cameraX - light.position.x,
                    (float) plugin.cameraY - light.position.y,
                    (float) plugin.cameraZ - light.position.z
            );
            float distanceToCamera = lightToCamera.Length();

//            drawRing(graphics2D, x, y, 12, light.color, new BasicStroke(12));
//            if (pointLightSprite != null) {
//                int imageWidth = pointLightSprite.getWidth();
//                int imageHeight = pointLightSprite.getHeight();
//                int scaledWidth = imageWidth / 4;  // Example: scaling the image to half its original size
//                int scaledHeight = imageHeight / 4;
//
//                graphics2D.drawImage(pointLightSprite, x - scaledWidth / 2, y - scaledHeight / 2, scaledWidth, scaledHeight, null);
//            } else {
//                // Fallback to drawing a ring if image is not loaded
//
//            }

            drawRing(graphics2D, x, y, 10, Color.BLACK, new BasicStroke(3));
            drawRing(graphics2D, x, y, 10, light.color, new BasicStroke(2));
            drawAlignedString(graphics2D, light.name, x, y + 20, TextAlignment.CENTER_ON_COLONS);
        }

        return null;
    }

    private void drawRing(Graphics2D g, int centerX, int centerY, int diameter, Color strokeColor, Stroke stroke) {
        // Round down to an odd number
        diameter = (int) Math.ceil(diameter / 2.f) * 2 - 1;
        int r = (int) Math.ceil(diameter / 2.f);
        g.setColor(strokeColor);
        g.setStroke(stroke);
        g.drawOval(centerX - r, centerY - r, diameter, diameter);
    }

    private enum TextAlignment {
        LEFT, RIGHT, CENTER, CENTER_ON_COLONS
    }

    private Color alpha(Color rgb, int alpha) {
        if (alpha == 255)
            return rgb;
        return new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), alpha);
    }

    private void drawAlignedString(Graphics g, String text, int centerX, int topY, TextAlignment alignment) {
        drawAlignedString(g, text.split("\\n"), centerX, topY, alignment);
    }

    private void drawAlignedString(Graphics g, String[] lines, int centerX, int topY, TextAlignment alignment) {
        var color = g.getColor();
        var shadow = alpha(Color.BLACK, color.getAlpha());
        FontMetrics metrics = g.getFontMetrics();
        int fontHeight = metrics.getHeight();
        int yOffset = 0;

        if (alignment == TextAlignment.CENTER_ON_COLONS) {
            int longestLeft = 0, longestRight = 0;
            for (String line : lines) {
                int dotIndex = line.indexOf(":");
                String left, right;
                if (dotIndex == -1) {
                    left = line;
                    right = "";
                } else {
                    left = line.substring(0, dotIndex);
                    right = line.substring(dotIndex + 1);
                }
                int leftLen = metrics.stringWidth(left);
                if (leftLen > longestLeft) {
                    longestLeft = leftLen;
                }
                int rightLen = metrics.stringWidth(right);
                if (rightLen > longestRight) {
                    longestRight = rightLen;
                }
            }

            int dotOffset = -metrics.stringWidth(":") / 2;

            for (String line : lines) {
                int dotIndex = line.indexOf(":");
                int xOffset = dotOffset;
                if (dotIndex == -1) {
                    xOffset -= metrics.stringWidth(line) / 2;
                } else {
                    xOffset -= metrics.stringWidth(line.substring(0, dotIndex));
                }
                g.setColor(shadow);
                g.drawString(line, centerX + xOffset + 1, topY + yOffset + 1);
                g.setColor(color);
                g.drawString(line, centerX + xOffset, topY + yOffset);
                yOffset += fontHeight;
            }
        } else {
            int longestLine = 0;
            if (alignment != TextAlignment.CENTER) {
                for (String line : lines) {
                    int length = metrics.stringWidth(line);
                    if (longestLine < length) {
                        longestLine = length;
                    }
                }
            }
            for (String line : lines) {
                int xOffset;
                switch (alignment) {
                    case LEFT:
                        xOffset = -longestLine / 2;
                        break;
                    case RIGHT:
                        int length = metrics.stringWidth(line);
                        xOffset = longestLine / 2 - length;
                        break;
                    case CENTER:
                        xOffset = -metrics.stringWidth(line) / 2;
                        break;
                    default:
                        throw new NotImplementedException("Alignment " + alignment + " has not been implemented");
                }
                g.setColor(shadow);
                g.drawString(line, centerX + xOffset + 1, topY + yOffset + 1);
                g.setColor(color);
                g.drawString(line, centerX + xOffset, topY + yOffset);
                yOffset += fontHeight;
            }
        }
    }
}
