package com.gpuExtended.scene;

import com.gpuExtended.GpuExtendedConfig;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.rendering.Vector3;
import com.gpuExtended.rendering.Vector4;
import com.gpuExtended.util.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;

import static com.gpuExtended.util.ResourcePath.path;
import static net.runelite.api.Constants.SCENE_SIZE;
import static net.runelite.api.Perspective.LOCAL_TILE_SIZE;

@Singleton
@Slf4j
public class EnvironmentManager
{
    private static final ResourcePath ENVIRONMENT_PATH = Props.getPathOrDefault(
            "environments-path", () -> path(GpuExtendedPlugin.class, "environment/environments.json"));

    private static final ResourcePath LIGHTS_PATH = Props.getPathOrDefault(
            "lights-path", () -> path(GpuExtendedPlugin.class, "environment/lights.json"));

    public enum EnvironmentType {
        DEFAULT(0),
        UNDERGROUND(1);

        private final int value;

        EnvironmentType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static EnvironmentType fromValue(int value) {
            for (EnvironmentType type : values()) {
                if (type.getValue() == value) {
                    return type;
                }
            }
            return null;
        }
    }

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private GpuExtendedPlugin plugin;

    @Inject
    private GpuExtendedConfig config;

    public float[] lightProjectionMatrix;
    public float[] lightViewMatrix;

    public Environment[] environments;
    public HashMap<String, Environment> environmentMap = new HashMap<>();
    public Environment currentEnvironment;

    public Light[] lightsDefinitions;
    public ArrayList<Light> renderedLights = new ArrayList<>();
    public ArrayList<Light> sceneLights = new ArrayList<>();
    public HashMap<int[], Light> tileLights = new HashMap<>();
    public HashMap<Integer, Light> decorationLights = new HashMap<>();
    public HashMap<Integer, Light> gameObjectLights = new HashMap<>();
    public HashMap<Integer, Light> projectileLights = new HashMap<>();


    public void Initialize() {
        environments = new Environment[0];

        ENVIRONMENT_PATH.watch("\\.(json)$", path -> {
            LoadEnvironments();
        });

        LIGHTS_PATH.watch("\\.(json)$", path -> {
            LoadLights();
            LoadSceneLights(client.getScene());
        });
    }

    public void Update(float deltaTime)
    {
        if(currentEnvironment == null) {
            currentEnvironment = GetDefaultEnvironment();
        }

        Player player = client.getLocalPlayer();
        if (player != null)
        {
            boolean isInOverworld = WorldPoint.getMirrorPoint(player.getWorldLocation(), true).getY() < Constants.OVERWORLD_MAX_Y;
            Environment targetEnvironment = isInOverworld ? GetDefaultEnvironment() : GetDefaultUndergroundEnvironment();
            currentEnvironment = targetEnvironment;
        }

        UpdateMainLightProjectionMatrix();
    }

    private void LoadEnvironments()
    {
        try {
            log.info("Fetching new environment information: " + ENVIRONMENT_PATH.resolve().toAbsolute());
            environments = ENVIRONMENT_PATH.loadJson(plugin.getGson(), Environment[].class);

            environmentMap.clear();
            for(int i = 0; i < environments.length; i++) {
                environmentMap.put(environments[i].Name, environments[i]);

                log.info("loaded environment: " + environments[i]);
            }
            log.info("Loaded " + environments.length + " environments");

        } catch (Exception e) {
            log.error("Failed to load environment: " + ENVIRONMENT_PATH, e);
        }
    }

    private void LoadLights()
    {
        try {
            log.info("Fetching new light information: " + LIGHTS_PATH.resolve().toAbsolute());
            lightsDefinitions = LIGHTS_PATH.loadJson(plugin.getGson(), Light[].class);

            tileLights.clear();
            decorationLights.clear();
            gameObjectLights.clear();
            projectileLights.clear();

            int uniqueLightAssignements = 0;
            for(int i = 0; i < lightsDefinitions.length; i++) {
                Light light = lightsDefinitions[i];
                log.info("Processing Light: {}", light);

                int[][] tiles = light.tiles;
                if(tiles != null) {
                    for(int j = 0; j < tiles.length; j++) {
                        tileLights.put(tiles[j], light);
                        uniqueLightAssignements++;
                    }
                }

                int[] decorations = light.decorations;
                if(decorations != null) {
                    for(int j = 0; j < decorations.length; j++) {
                        decorationLights.put(decorations[j], light);
                        uniqueLightAssignements++;
                    }
                }

                int[] gameObjects = light.gameObjects;
                if(gameObjects != null) {
                    for(int j = 0; j < gameObjects.length; j++) {
                        gameObjectLights.put(gameObjects[j], light);
                        uniqueLightAssignements++;
                    }
                }

                int[] projectiles = light.projectiles;
                if(projectiles != null) {
                    for(int j = 0; j < projectiles.length; j++) {
                        projectileLights.put(projectiles[j], light);
                        uniqueLightAssignements++;
                    }
                }
            }

            log.info("Loaded {} lights across {} objects", lightsDefinitions.length, uniqueLightAssignements);
        } catch (Exception e) {
            log.error("Failed to load lights: " + LIGHTS_PATH, e);
        }
    }

    public void LoadSceneLights(Scene scene)
    {
        sceneLights.clear();
        int[] tilePosition = new int[3];
        for (int z = 0; z < Constants.MAX_Z; ++z)
        {
            for (int x = 0; x < Constants.EXTENDED_SCENE_SIZE; ++x)
            {
                for (int y = 0; y < Constants.EXTENDED_SCENE_SIZE; ++y)
                {
                    Tile tile = scene.getExtendedTiles()[z][x][y];
                    if(tile == null)
                        continue;

                    WorldPoint tileWorldLocation = tile.getWorldLocation();
                    tilePosition[0] = tileWorldLocation.getX();
                    tilePosition[1] = tileWorldLocation.getY();
                    tilePosition[2] = tileWorldLocation.getPlane();

                    if(tileLights.containsKey(tilePosition))
                    {
                        Light tileLight = tileLights.get(tilePosition);
                        Vector4 position = new Vector4(tileWorldLocation.getX(), tileWorldLocation.getY(), tileWorldLocation.getPlane(), 0);
                        sceneLights.add(Light.CreateLightFromTemplate(tileLight, position));
                    }

                    DecorativeObject decorativeObject = tile.getDecorativeObject();
                    if (decorativeObject != null)
                    {
                        Light decorationLight = decorationLights.get(decorativeObject.getId());
                        if(decorationLight != null) {

                            LocalPoint location = decorativeObject.getLocalLocation();
                            Vector4 position = new Vector4(location.getX(), location.getY(), z + decorativeObject.getZ(), decorativeObject.getConfig() >> 6 & 3);
                            sceneLights.add(Light.CreateLightFromTemplate(decorationLight, position));
                        }
                    }

                    for (GameObject gameObject : tile.getGameObjects())
                    {
                        if (gameObject == null || gameObject.getRenderable() instanceof Actor)
                            continue;

                        Light gameObjectLight = gameObjectLights.get(gameObject.getId());
                        if(gameObjectLight != null) {
                            LocalPoint location = gameObject.getLocalLocation();
                            Vector4 position = new Vector4(location.getX(), location.getY(), z + decorativeObject.getZ(), gameObject.getConfig() >> 6 & 3);
                            sceneLights.add(Light.CreateLightFromTemplate(gameObjectLight, position));
                        }
                    }
                }
            }
        }

        log.info("Loaded {} lights", sceneLights.size());
//
//        for(Light light : sceneLights) {
//            log.info("Light: {}", light);
//        }
    }

    private void DetermineRenderedLights()
    {
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return;
        }

        int localX = player.getLocalLocation().getX();
        int localY = player.getLocalLocation().getY();
        int plane = client.getPlane();

        int drawDistance = plugin.getDrawDistance() * LOCAL_TILE_SIZE;




    }

    private void UpdateMainLightProjectionMatrix()
    {
        // Calculate light matrix
        boolean overrideLightDirection = config.customLightRotation();
        int customLightPitch = config.lightPitch();
        int customLightYaw = config.lightYaw();

        float lightPitch = (float) Math.toRadians(overrideLightDirection ? customLightPitch : currentEnvironment.LightPitch);
        float lightYaw = (float) Math.toRadians(overrideLightDirection ? customLightYaw : currentEnvironment.LightYaw);

        lightProjectionMatrix = Mat4.identity();
        lightViewMatrix = Mat4.rotateX((float) Math.PI + lightPitch);
        Mat4.mul(lightViewMatrix, Mat4.rotateY((float) Math.PI + lightYaw));

        int camX = (int) client.getCameraFpX();
        int camY = (int) client.getCameraFpY();

        int shadowDrawDistance = 90;
        int drawDistanceSceneUnits = shadowDrawDistance * LOCAL_TILE_SIZE / 2;
        int east = Math.min(camX + drawDistanceSceneUnits, LOCAL_TILE_SIZE * SCENE_SIZE);
        int west = Math.max(camX - drawDistanceSceneUnits, 0);
        int north = Math.min(camY + drawDistanceSceneUnits, LOCAL_TILE_SIZE * SCENE_SIZE);
        int south = Math.max(camY - drawDistanceSceneUnits, 0);
        int width = east - west;
        int height = north - south;
        int farPlane = 10000;

        int maxDrawDistance = 100;
        float maxScale = 0.7f;
        float minScale = 0.4f;
        float scaleMultiplier = 1.0f - (shadowDrawDistance / (maxDrawDistance * maxScale));
        float scale = Mathmatics.lerp(maxScale, minScale, scaleMultiplier);
        Mat4.mul(lightProjectionMatrix, Mat4.scale(scale, scale, scale));
        Mat4.mul(lightProjectionMatrix, Mat4.ortho(width, height, farPlane));
        Mat4.mul(lightProjectionMatrix, lightViewMatrix);
        Mat4.mul(lightProjectionMatrix, Mat4.translate(-(width / 2f + west), 0, -(height / 2f + south)));
    }

    private Environment GetDefaultEnvironment() {
        try {
            return environmentMap.get("DEFAULT");
        } catch (Exception e) {
            log.error("Failed to get default environment", e);
            return null;
        }
    }

    private Environment GetDefaultUndergroundEnvironment() {
        try {
            return environmentMap.get("DEFAULT_UNDERGROUND");
        } catch (Exception e) {
            log.error("Failed to get default underground environment", e);
            return null;
        }
    }
}

