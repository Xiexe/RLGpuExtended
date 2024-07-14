package com.gpuExtended.scene;

import com.gpuExtended.GpuExtendedConfig;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.regions.Area;
import com.gpuExtended.regions.Region;
import com.gpuExtended.regions.Bounds;
import com.gpuExtended.rendering.Vector4;
import com.gpuExtended.util.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;

import static com.gpuExtended.util.ResourcePath.path;
import static com.gpuExtended.util.Utils.GenerateTileHash;
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

    private static final ResourcePath AREAS_PATH = Props.getPathOrDefault(
            "areas-path", () -> path(GpuExtendedPlugin.class, "environment/areaDefinitions.json"));

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

    public Area[] areas;
    public HashMap<Bounds, Area> areaMap = new HashMap<>();
    public HashMap<WorldPoint, Bounds> boundsMap = new HashMap<>();
    public Area currentArea;
    public Bounds currentBounds;

    public Light[] lightsDefinitions;
    public ArrayList<Light> sceneLights = new ArrayList<>();
    public HashMap<Light, Boolean> sceneLightVisibility = new HashMap<>();
    public HashMap<Integer, ArrayList<Light>> tileLights = new HashMap<>();
    public HashMap<Integer, ArrayList<Light>> decorationLights = new HashMap<>();
    public HashMap<Integer, ArrayList<Light>> gameObjectLights = new HashMap<>();
    public HashMap<Integer, ArrayList<Light>> projectileLights = new HashMap<>();


    public void Initialize() {
        environments = new Environment[0];
        areas = new Area[0];

        ENVIRONMENT_PATH.watch("\\.(json)$", path -> {
            LoadEnvironments();
        });

        LIGHTS_PATH.watch("\\.(json)$", path -> {
            LoadLights();
            LoadSceneLights(client.getScene());
        });

        AREAS_PATH.watch("\\.(json)$", path -> {
            LoadAreas();
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
        CheckRegion();
    }

    public void LoadAreas()
    {
        try {
            log.info("Fetching new area information: " + AREAS_PATH.resolve().toAbsolute());
            areas = AREAS_PATH.loadJson(plugin.getGson(), Area[].class);
            log.info("Loaded " + areas.length + " areas");

            // holy fuck
            for(Area area : areas) {
                if(area.getBounds() == null) continue;
                for (Bounds bounds : area.getBounds()) {
                    int startX = (int)bounds.getStart().x;
                    int startY = (int)bounds.getStart().y;
                    int endX =   (int)bounds.getEnd().x;
                    int endY =   (int)bounds.getEnd().y;
                    for (int x = startX; x <= endX; x++) {
                        for (int y = startY; y <= endY; y++) {
                            if(bounds.getStart().z != -1 && bounds.getEnd().z != -1) {
                                for (int z = (int)bounds.getStart().z; z <= (int)bounds.getEnd().z; z++) {
                                    WorldPoint point = new WorldPoint(x, y, z);
                                    boundsMap.put(point, bounds);
                                }
                            } else {
                                for(int z = 0; z < Constants.MAX_Z; z++) {
                                    WorldPoint point = new WorldPoint(x, y, z);
                                    boundsMap.put(point, bounds);
                                }
                            }
                        }
                    }
                    areaMap.put(bounds, area);
                    log.info("Loaded area: " + area.getName() + " with bounds: " + bounds);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load areas: " + AREAS_PATH, e);
        }
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

                int[][] tiles = light.tiles;
                if(tiles != null) {
                    for(int j = 0; j < tiles.length; j++) {
                        int[] tile = tiles[j];

                        int hash = GenerateTileHash(tile);
                        tileLights.computeIfAbsent(hash, k -> new ArrayList<>());

                        if(!tileLights.get(hash).contains(light)) {
                            tileLights.get(hash).add(light);
                        }

                        uniqueLightAssignements++;
                    }
                }

                int[] decorations = light.decorations;
                if(decorations != null) {
                    for(int j = 0; j < decorations.length; j++) {
                        decorationLights.computeIfAbsent(decorations[j], k -> new ArrayList<>());

                        if(!decorationLights.get(decorations[j]).contains(light)) {
                            decorationLights.get(decorations[j]).add(light);
                        }
                        uniqueLightAssignements++;
                    }
                }

                int[] gameObjects = light.gameObjects;
                if(gameObjects != null) {
                    for(int j = 0; j < gameObjects.length; j++) {
                        gameObjectLights.computeIfAbsent(gameObjects[j], k -> new ArrayList<>());

                        if(!gameObjectLights.get(gameObjects[j]).contains(light)) {
                            gameObjectLights.get(gameObjects[j]).add(light);
                        }
                        uniqueLightAssignements++;
                    }
                }

                int[] projectiles = light.projectiles;
                if(projectiles != null) {
                    for(int j = 0; j < projectiles.length; j++) {
                        projectileLights.computeIfAbsent(projectiles[j], k -> new ArrayList<>());

                        if(!projectileLights.get(projectiles[j]).contains(light)) {
                            projectileLights.get(projectiles[j]).add(light);
                        }
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
        if(client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        sceneLights.clear();
        sceneLightVisibility.clear();

        HashMap<Vector4, ArrayList<TileObject>> processedObjects = new HashMap<>();
        // TODO:: something causes duplicate lights sometimes. Fix it.
        for (int z = 0; z < Constants.MAX_Z; ++z)
        {
            for (int x = 0; x < Constants.EXTENDED_SCENE_SIZE; ++x)
            {
                for (int y = 0; y < Constants.EXTENDED_SCENE_SIZE; ++y)
                {
                    Tile tile = scene.getExtendedTiles()[z][x][y];
                    if(tile == null) {
                        continue;
                    }

                    if(tile.getPlane() != client.getPlane())
                    {
                        continue;
                    }


                    WorldPoint tileWorldLocation = tile.getWorldLocation();
                    int[] worldLocation = new int[] {
                            tileWorldLocation.getX(),
                            tileWorldLocation.getY(),
                            tileWorldLocation.getPlane()
                    };

                    int hash = GenerateTileHash(worldLocation);
                    if(tileLights.containsKey(hash))
                    {
                        ArrayList<Light> lightsForTile = tileLights.get(hash);

                        LocalPoint location = tile.getLocalLocation();
                        Vector4 position = new Vector4(location.getX(), location.getY(), z, 0);
                        for(int i = 0; i < lightsForTile.size(); i++) {
                            Light light = Light.CreateLightFromTemplate(lightsForTile.get(i), position);
                            sceneLights.add(light);
                        }
                    }

                    DecorativeObject decorativeObject = tile.getDecorativeObject();
                    if (decorativeObject != null)
                    {
                        ArrayList<Light> lightsForDecoration = decorationLights.get(decorativeObject.getId());
                        if(lightsForDecoration == null) continue;

                        LocalPoint location = decorativeObject.getLocalLocation();
                        Vector4 position = new Vector4(location.getX(), location.getY(), z + decorativeObject.getZ(), decorativeObject.getConfig() >> 6 & 3);

                        if(processedObjects.containsKey(position)) {
                            if(processedObjects.get(position).contains(decorativeObject))
                                continue;
                        }

                        if(!processedObjects.containsKey(position))
                        {
                            processedObjects.put(position, new ArrayList<>());
                        }

                        for(int i = 0; i < lightsForDecoration.size(); i++) {
                            Light light = Light.CreateLightFromTemplate(lightsForDecoration.get(i), position);
                            sceneLights.add(light);
                        }

                        processedObjects.get(position).add(decorativeObject);
                    }

                    for (GameObject gameObject : tile.getGameObjects())
                    {
                        if (gameObject == null) continue;

                        ArrayList<Light> lightsForGameobject = gameObjectLights.get(gameObject.getId());
                        if (lightsForGameobject == null) continue;

                        LocalPoint location = gameObject.getLocalLocation();
                        Vector4 position = new Vector4(location.getX(), location.getY(), z + gameObject.getZ(), gameObject.getConfig() >> 6 & 3);

                        if(processedObjects.containsKey(position)) {
                            if(processedObjects.get(position).contains(gameObject))
                                continue;
                        }

                        if(!processedObjects.containsKey(position))
                        {
                            processedObjects.put(position, new ArrayList<>());
                        }

                        for(int i = 0; i < lightsForGameobject.size(); i++)
                        {
                            Light light = Light.CreateLightFromTemplate(lightsForGameobject.get(i), position);
                            sceneLights.add(light);
                        }

                        processedObjects.get(position).add(gameObject);
                    }
                }
            }
        }

        log.info("Loaded {} lights across scene total.", sceneLights.size());
    }

    public void DetermineRenderedLights()
    {
        Player player = client.getLocalPlayer();
        if (player == null)
        {
            return;
        }

        if(sceneLights.size() < 2) {
            return;
        }

        int localX = player.getLocalLocation().getX();
        int localY = player.getLocalLocation().getY();

        sceneLights.sort((a, b) -> {
            float distanceA = (localX - a.position.x) * (localX - a.position.x) +
                    (localY - a.position.y) * (localY - a.position.y);

            float distanceB = (localX - b.position.x) * (localX - b.position.x) +
                    (localY - b.position.y) * (localY - b.position.y);

            a.distanceSquared = distanceA;
            b.distanceSquared = distanceB;

            return Float.compare(distanceA, distanceB);
        });
    }

    public void CheckRegion()
    {
        if(client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        Bounds lastBounds = currentBounds;
        Area lastArea = currentArea;
        currentArea = null;
        currentBounds = null;

        Player player = client.getLocalPlayer();
        if (player == null || client.getScene() == null)
        {
            return;
        };

        WorldPoint playerLocation = player.getWorldLocation();
        LocalPoint localPoint = player.getLocalLocation();

        if (client.isInInstancedRegion())
        {
            playerLocation = WorldPoint.fromLocalInstance(client, localPoint);
        }

        if(boundsMap.containsKey(playerLocation)) {
            currentBounds = boundsMap.get(playerLocation);
            if(currentBounds == null)
                return;

            currentArea = areaMap.get(currentBounds);
        }

        if(plugin.loadingScene) {
            return;
        }

        if(currentBounds != lastBounds || currentArea != lastArea) {
            if(client.getGameState() == GameState.LOGGED_IN) {
                clientThread.invoke(() -> {
                    client.setGameState(GameState.LOADING);
                    plugin.loadScene(client.getScene());
                    plugin.swapScene(client.getScene());
                });
            }

            log.info("Player entered new area: " + currentBounds.getName());
        }
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

