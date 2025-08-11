package com.gpuExtended.scene;

import com.gpuExtended.GpuExtendedConfig;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.regions.Area;
import com.gpuExtended.regions.Bounds;
import com.gpuExtended.rendering.Vector4;
import com.gpuExtended.util.*;
import com.gpuExtended.config.ShadowResolution;
import com.gpuExtended.util.constants.Variables;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.*;
import java.util.List;

import static com.gpuExtended.scene.Environment.lerpColor;
import static com.gpuExtended.util.ResourcePath.path;
import static com.gpuExtended.util.Utils.GenerateTileHash;

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

    public Environment[] environments;
    public HashMap<String, Environment> environmentMap = new HashMap<>();
    public Environment currentEnvironment;
    private Environment newEnvironment;

    public Area[] areas;
    public HashMap<Bounds, Area> areaMap = new HashMap<>();
    public HashMap<WorldPoint, Bounds> boundsMap = new HashMap<>();
    public Area currentArea;
    public Bounds currentBounds;

    private boolean loadingLights = false;

    public Light mainLight = new Light();
    public float mainLightPitch = 0.0f;
    public float mainLightYaw = 0.0f;
    public Color skyColor = new Color(0, 0, 0);
    public Color ambientColor = new Color(0, 0, 0);

    public Light[] lightsDefinitions;
    public ArrayList<Light> sceneLights = new ArrayList<>();
    public HashMap<Light, Boolean> sceneLightVisibility = new HashMap<>();
    public HashMap<Integer, ArrayList<Light>> tileLights = new HashMap<>();
    public HashMap<Integer, ArrayList<Light>> decorationLights = new HashMap<>();
    public HashMap<Integer, ArrayList<Light>> gameObjectLights = new HashMap<>();
    public HashMap<Integer, ArrayList<Light>> wallLights = new HashMap<>();
    public HashMap<Integer, ArrayList<Light>> projectileLights = new HashMap<>();
    public HashMap<Integer, ArrayList<Light>> npcLights = new HashMap<>();

    public HashSet<Projectile> sceneProjectiles = new HashSet<>();
    public HashMap<Projectile, Light> projectileLightHashMap = new HashMap<>();

    public HashMap<GameObject, Light> gameObjectLightHashMap = new HashMap<>();
    public HashMap<NPC, Light> npcLightHashMap = new HashMap<>();

//    private Light testLight = new Light();

    private float timeOfDay = 0.0f; // 0.0 = midnight, 12.0 = noon, 24.0 = next midnight
    private float timeOfDayCycleLength = 2700; // 45 minutes (in seconds)
    public List<ColorKey> sunColorKeys = new ArrayList<>();
    public List<ColorKey> ambientColorKeys = new ArrayList<>();
    public List<ColorKey> skyColorKeys = new ArrayList<>();


    public void Initialize() {
        plugin.skybox.Initialize();
        environments = new Environment[0];
        areas = new Area[0];

        if (plugin.config.customTimeOfDay() <= 0) {
            timeOfDay = 7; // Start at 7AM
        }
        else {
            timeOfDay = plugin.config.customTimeOfDay();
        }

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
        if(client.getGameState() != GameState.LOGGED_IN)
            return;

        if(currentEnvironment.isTransitioning) {
            currentEnvironment.SwitchToEnvironment(newEnvironment, deltaTime * 0.5f);
        }

        if (plugin.config.customTimeOfDay() <= 0) {
            timeOfDay += (24f / timeOfDayCycleLength) * deltaTime; // 24 hours in 45 minutes, 0.6 is the game tick rate (600ms)
            if (timeOfDay >= 24f)
                timeOfDay -= 24f;
        }
        else {
            timeOfDay = plugin.config.customTimeOfDay();
        }

        UpdateMainLightSettings();
        UpdateNpcLights();
    }

    private void UpdateNpcLights() {
        for (int i = 0; i < npcLightHashMap.size(); i++) {
            NPC npc = (NPC) npcLightHashMap.keySet().toArray()[i];
            Light light = npcLightHashMap.get(npc);
            if (npc == null || light == null) {
                continue;
            }

            if (npc.getWorldLocation() == null || npc.getWorldLocation().getPlane() != client.getPlane()) {
                continue;
            }

            LocalPoint localPoint = npc.getLocalLocation();
            if (localPoint == null) {
                continue;
            }

            float tileHeight = Perspective.getTileHeight(client, localPoint, npc.getWorldLocation().getPlane());
            Vector4 oldPosition = light.position;

//            float newZ = 0;
//            if (oldPosition.z != 0) {
//                float targetZ = tileHeight - (npc.getModelHeight() / 2f) + light.offset.z;
//                float currentZ = oldPosition.z;
//                newZ = currentZ + (targetZ - currentZ) * plugin.DeltaTime * 10; // Smoothly transition the Z position
//            }
//            else {
//                newZ = tileHeight - (npc.getModelHeight() / 2f) + light.offset.z;
//            }

            Vector4 newPosition = new Vector4(
                    localPoint.getX() + light.offset.x,
                    localPoint.getY() + light.offset.y,
                    tileHeight + light.offset.z,
                    0
            );
            light.position = newPosition;
        }
    }

    public void OnTick() {
        CleanupOldProjectiles();
        CheckRegion();

//        if (plugin.config.customTimeOfDay() <= 0) {
//            timeOfDay += (24f / timeOfDayCycleLength) * 0.6; // 24 hours in 45 minutes, 0.6 is the game tick rate (600ms)
//            if (timeOfDay >= 24f)
//                timeOfDay -= 24f;
//        }
//        else {
//            timeOfDay = plugin.config.customTimeOfDay();
//        }
    }

    public void RenderSkybox()
    {
        plugin.skybox.Render();
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
                    //log.info("Loaded area: " + area.getName() + " with bounds: " + bounds);
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

            Environment sunriseEnvironment = GetDefaultSunriseEnvironment();
            Environment middayEnvironment = GetDefaultMiddayEnvironment();
            Environment sunsetEnvironment = GetDefaultSunsetEnvironment();
            Environment nightEnvironment = GetDefaultNightEnvironment();

            assert sunriseEnvironment != null;
            assert middayEnvironment != null;
            assert sunsetEnvironment != null;
            assert nightEnvironment != null;

            float dayStart        = 0.0f;
            float sunriseStart    = 0.10f;
            float sunriseEnd      = 0.20f;
            float middayStart     = 0.30f;
            float middayEnd       = 0.70f;
            float sunsetStart     = 0.80f;
            float sunsetEnd       = 0.90f;
            float dayEnd          = 1.00f;

            sunColorKeys.clear();
            sunColorKeys.add(new ColorKey(dayStart, nightEnvironment.LightColor));             // Full night start
            sunColorKeys.add(new ColorKey(sunriseStart, nightEnvironment.LightColor));     // Pre-dawn
            sunColorKeys.add(new ColorKey(sunriseEnd, sunriseEnvironment.LightColor));     // Transition to day
            sunColorKeys.add(new ColorKey(middayStart, middayEnvironment.LightColor));     // Full daylight
            sunColorKeys.add(new ColorKey(middayEnd, middayEnvironment.LightColor));       // Still daylight
            sunColorKeys.add(new ColorKey(sunsetStart, sunsetEnvironment.LightColor));     // Begin sunset
            sunColorKeys.add(new ColorKey(sunsetEnd, nightEnvironment.LightColor));        // Back to night
            sunColorKeys.add(new ColorKey(dayEnd, nightEnvironment.LightColor));            // Loop closure

            skyColorKeys.clear();
            skyColorKeys.add(new ColorKey(dayStart, nightEnvironment.SkyColor));
            skyColorKeys.add(new ColorKey(sunriseStart, nightEnvironment.SkyColor));
            skyColorKeys.add(new ColorKey(sunriseEnd, sunriseEnvironment.SkyColor));
            skyColorKeys.add(new ColorKey(middayStart, middayEnvironment.SkyColor));
            skyColorKeys.add(new ColorKey(middayEnd, middayEnvironment.SkyColor));
            skyColorKeys.add(new ColorKey(sunsetStart, sunsetEnvironment.SkyColor));
            skyColorKeys.add(new ColorKey(sunsetEnd, nightEnvironment.SkyColor));
            skyColorKeys.add(new ColorKey(dayEnd, nightEnvironment.SkyColor));

            ambientColorKeys.clear();
            ambientColorKeys.add(new ColorKey(dayStart, nightEnvironment.AmbientColor));
            ambientColorKeys.add(new ColorKey(sunriseStart, nightEnvironment.AmbientColor));
            ambientColorKeys.add(new ColorKey(sunriseEnd, sunriseEnvironment.AmbientColor));
            ambientColorKeys.add(new ColorKey(middayStart, middayEnvironment.AmbientColor));
            ambientColorKeys.add(new ColorKey(middayEnd, middayEnvironment.AmbientColor));
            ambientColorKeys.add(new ColorKey(sunsetStart, sunsetEnvironment.AmbientColor));
            ambientColorKeys.add(new ColorKey(sunsetEnd, nightEnvironment.AmbientColor));
            ambientColorKeys.add(new ColorKey(dayEnd, nightEnvironment.AmbientColor));
        } catch (Exception e) {
            log.error("Failed to load environment: " + ENVIRONMENT_PATH, e);
        }

        if(currentEnvironment != null) {
            currentEnvironment.isTransitioning = true;
            currentEnvironment.transitionProgress = 1.0f; // Force transition to the new environment immediately
        }
    }

    private void LoadLights()
    {
        try {
            loadingLights = true;
            log.info("Fetching new light information: " + LIGHTS_PATH.resolve().toAbsolute());
            lightsDefinitions = LIGHTS_PATH.loadJson(plugin.getGson(), Light[].class);

            tileLights.clear();
            decorationLights.clear();
            gameObjectLights.clear();
            projectileLights.clear();
            wallLights.clear();
            npcLights.clear();

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

                int[] walls = light.walls;
                if(walls != null) {
                    for(int j = 0; j < walls.length; j++) {
                        wallLights.computeIfAbsent(walls[j], k -> new ArrayList<>());

                        if(!wallLights.get(walls[j]).contains(light)) {
                            wallLights.get(walls[j]).add(light);
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

                int[] npcs = light.npcs;
                if(npcs != null) {
                    for(int j = 0; j < npcs.length; j++) {
                        npcLights.computeIfAbsent(npcs[j], k -> new ArrayList<>());

                        if(!npcLights.get(npcs[j]).contains(light)) {
                            npcLights.get(npcs[j]).add(light);
                        }
                        uniqueLightAssignements++;
                    }
                }
            }

            log.info("Loaded {} lights across {} objects", lightsDefinitions.length, uniqueLightAssignements);
            log.info("Loaded {} projectile lights", projectileLights.size());
        } catch (Exception e) {
            log.error("Failed to load lights: " + LIGHTS_PATH, e);
        }
    }

    public void LoadSceneLights(Scene scene)
    {
        sceneLights.clear();
        sceneLightVisibility.clear();
        sceneProjectiles.clear();

        projectileLightHashMap.clear();
        gameObjectLightHashMap.clear();
        npcLightHashMap.clear();

        GameState gameState = client.getGameState();
        if(gameState == GameState.LOGGED_IN || plugin.loadingScene) {
//            sceneLights.add(testLight);

            // Load lights for static objects that are a part of the tile
            for (int z = 0; z < Constants.MAX_Z; ++z) {
                for (int x = 0; x < Constants.EXTENDED_SCENE_SIZE; ++x) {
                    for (int y = 0; y < Constants.EXTENDED_SCENE_SIZE; ++y) {
                        Tile tile = scene.getExtendedTiles()[z][x][y];
                        if (tile == null) {
                            continue;
                        }

                        // Skip tiles that are not on the same plane as the player
                        // TODO:: Maybe we dont need to skip this with light binning.
                        if (tile.getPlane() != client.getPlane()) {
                            continue;
                        }


                        WorldPoint tileWorldLocation = tile.getWorldLocation();
                        int[] worldLocation = new int[]{
                                tileWorldLocation.getX(),
                                tileWorldLocation.getY(),
                                tileWorldLocation.getPlane()
                        };

                        int hash = GenerateTileHash(worldLocation);
                        if (tileLights.containsKey(hash))
                        {
                            ArrayList<Light> lightsForTile = tileLights.get(hash);
                            LocalPoint location = tile.getLocalLocation();
                            float tileHeight = Perspective.getTileHeight(client, location, z);

                            Vector4 position = new Vector4(location.getX(), location.getY(), z + tileHeight, 0);
                            for (int i = 0; i < lightsForTile.size(); i++) {
                                Light light = Light.CreateLightFromTemplate(lightsForTile.get(i), position, tile.getPlane(), 0, plugin.awtContext);
                                sceneLights.add(light);
                            }
                        }

                        WallObject wallObject = tile.getWallObject();
                        if(wallObject != null)
                        {
                            ArrayList<Light> lightsForWallObject = wallLights.get(wallObject.getId());
                            if (lightsForWallObject != null)
                            {
                                int orientation = wallObject.getOrientationA();
                                LocalPoint location = wallObject.getLocalLocation();
                                float tileHeight = Perspective.getTileHeight(client, location, z);
                                Vector4 position = new Vector4(location.getX(), location.getY(), z + tileHeight, 0);

                                for (int i = 0; i < lightsForWallObject.size(); i++) {
                                    Light light = Light.CreateLightFromTemplate(lightsForWallObject.get(i), position, tile.getPlane(), orientation, plugin.awtContext);
                                    sceneLights.add(light);
                                }
                            }
                        }

                        DecorativeObject decorativeObject = tile.getDecorativeObject();
                        if (decorativeObject != null)
                        {
                            ArrayList<Light> lightsForDecoration = decorationLights.get(decorativeObject.getId());
                            if (lightsForDecoration != null)
                            {
                                int orientation = decorativeObject.getConfig() >> 6 & 3;
                                LocalPoint location = decorativeObject.getLocalLocation();
                                float tileHeight = Perspective.getTileHeight(client, location, z);
                                Vector4 position = new Vector4(location.getX(), location.getY(), z + tileHeight, orientation);

                                for (int i = 0; i < lightsForDecoration.size(); i++) {

                                    Light light = Light.CreateLightFromTemplate(lightsForDecoration.get(i), position, tile.getPlane(), orientation, plugin.awtContext);
                                    sceneLights.add(light);
                                }
                            }
                        }

                        for (GameObject gameObject : tile.getGameObjects())
                        {
                            if (gameObject != null)
                            {
                                ArrayList<Light> lightsForGameobject = gameObjectLights.get(gameObject.getId());
                                if (lightsForGameobject != null)
                                {
                                    int orientation = gameObject.getConfig() >> 6 & 3;
                                    LocalPoint location = gameObject.getLocalLocation();
                                    float tileHeight = Perspective.getTileHeight(client, location, z);
                                    Vector4 position = new Vector4(location.getX(), location.getY(), z + tileHeight, 0);

                                    for (int i = 0; i < lightsForGameobject.size(); i++) {
                                        Light light = Light.CreateLightFromTemplate(lightsForGameobject.get(i), position, tile.getPlane(), orientation, plugin.awtContext);
                                        sceneLights.add(light);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Load lights for npcs that are already loaded (won't fire spawn event by itself, do it manually)
            for (NPC npc : client.getNpcs()) {
                if (npc == null) continue;
                AddNpcLight(npc);
            }

            log.info("Loaded {} lights across scene total.", sceneLights.size());
            loadingLights = false;
        }
    }

    public void CheckRegion()
    {
        if(currentEnvironment == null)
        {
            newEnvironment = GetDefaultEnvironment();
            currentEnvironment = new Environment();

            Environment defaultEnvironment = GetDefaultEnvironment();
            currentEnvironment.SkyColor = defaultEnvironment.SkyColor;
            currentEnvironment.AmbientColor = defaultEnvironment.AmbientColor;
            currentEnvironment.LightColor = defaultEnvironment.LightColor;
            currentEnvironment.LightPitch = defaultEnvironment.LightPitch;
            currentEnvironment.LightYaw = defaultEnvironment.LightYaw;
            currentEnvironment.FogDepth = defaultEnvironment.FogDepth;
            currentEnvironment.Type = defaultEnvironment.Type;
            currentEnvironment.UseDynamicTimeOfDay = defaultEnvironment.UseDynamicTimeOfDay;

            newEnvironment.SkyColor = defaultEnvironment.SkyColor;
            newEnvironment.AmbientColor = defaultEnvironment.AmbientColor;
            newEnvironment.LightColor = defaultEnvironment.LightColor;
            newEnvironment.LightPitch = defaultEnvironment.LightPitch;
            newEnvironment.LightYaw = defaultEnvironment.LightYaw;
            newEnvironment.FogDepth = defaultEnvironment.FogDepth;
            newEnvironment.Type = defaultEnvironment.Type;
            newEnvironment.UseDynamicTimeOfDay = defaultEnvironment.UseDynamicTimeOfDay;
        }

        if(client.getGameState() == GameState.LOGGED_IN || plugin.loadingScene)
        {
            Bounds lastBounds = currentBounds;
            Area lastArea = currentArea;
            currentArea = null;
            currentBounds = null;

            Player player = client.getLocalPlayer();
            if (player == null || client.getScene() == null) {
                return;
            }

            WorldPoint playerLocation = player.getWorldLocation();
            LocalPoint localPoint = player.getLocalLocation();

            if (client.isInInstancedRegion()) {
                playerLocation = WorldPoint.fromLocalInstance(client, localPoint);
            }

            currentBounds = boundsMap.get(playerLocation);
            currentArea = currentBounds == null ? null : areaMap.getOrDefault(currentBounds, null);

            if (plugin.loadingScene) {
                return;
            }

            if (lastBounds != currentBounds || lastArea != currentArea) {
                if(lastBounds != null && lastArea != null)
                {
                    log.info("Player left area: {}, {}", lastArea.getName(), lastBounds.getName());
                }
            }

            Environment lastEnvironment = newEnvironment;
            if(currentArea != null)
            {
                if(currentArea.getEnvironment() != null)
                {
                    Environment targetEnvironment = environmentMap.get(currentArea.getEnvironment());
                    if(targetEnvironment != null) {
                        newEnvironment = targetEnvironment;
                    }
                }
                else
                {
                    if(currentBounds != null)
                    {
                        if(currentBounds.getEnvironment() != null)
                        {
                            Environment targetEnvironment = environmentMap.get(currentBounds.getEnvironment());
                            if(targetEnvironment != null) {
                                newEnvironment = targetEnvironment;
                            }
                        }
                        else
                        {
                            newEnvironment = GetDefaultEnvironment();
                        }
                    }
                    else
                    {
                        newEnvironment = GetDefaultEnvironment();
                    }
                }
            }
            else
            {
                newEnvironment = GetDefaultEnvironment();
            }

            if(lastEnvironment != newEnvironment)
            {
                Environment cached = new Environment();
                cached.Name = currentEnvironment.Name;
                cached.SkyColor = skyColor;
                cached.AmbientColor = ambientColor;
                cached.LightColor = mainLight.color;
                cached.LightPitch = mainLightPitch;
                cached.LightYaw = mainLightYaw;
                cached.FogDepth = currentEnvironment.FogDepth;
                cached.Type = currentEnvironment.Type;
                cached.UseDynamicTimeOfDay = currentEnvironment.UseDynamicTimeOfDay;

                // TODO:: Temp hack to make transitions not fade when going to or from underground
                currentEnvironment.PrepareEnvironmentTransition(cached);
                if (currentEnvironment.Name != null) {
                    currentEnvironment.transitionProgress = currentEnvironment.Name.equals("DEFAULT_UNDERGROUND") || newEnvironment.Name.equals("DEFAULT_UNDERGROUND") ? 1 : 0;
                }
                currentEnvironment.isTransitioning = true;
            }

            if (currentBounds != null && currentArea != null) {
                if (currentBounds != lastBounds || currentArea != lastArea) {
                    log.info("Player entered area: {}, {}", currentArea.getName(), currentBounds.getName());

                    if (lastBounds != null) {
                        if (!lastBounds.isHideOtherAreas() && !currentBounds.isHideOtherAreas()) {
                            return;
                        }
                    } else {
                        if (!currentBounds.isHideOtherAreas()) {
                            return;
                        }
                    }

                    if (client.getGameState() == GameState.LOGGED_IN) {
                        clientThread.invoke(() -> {
                            Scene scene = client.getScene();

                            client.setGameState(GameState.LOADING);
                            plugin.loadScene(scene);
                            plugin.swapScene(scene);
                        });
                    }
                }
            }
        }
    }

    public void SetEnvironmentNoLerp()
    {
        currentEnvironment = new Environment();
        currentEnvironment.SkyColor = newEnvironment.SkyColor;
        currentEnvironment.AmbientColor = newEnvironment.AmbientColor;
        currentEnvironment.LightColor = newEnvironment.LightColor;
        currentEnvironment.LightPitch = newEnvironment.LightPitch;
        currentEnvironment.LightYaw = newEnvironment.LightYaw;
        currentEnvironment.FogDepth = newEnvironment.FogDepth;
        currentEnvironment.Type = newEnvironment.Type;
        currentEnvironment.UseDynamicTimeOfDay = newEnvironment.UseDynamicTimeOfDay;

        log.info("Setting environment: " + currentEnvironment.Name);
    }

    private void UpdateMainLightSettings()
    {
        if(config.shadowResolution() == ShadowResolution.RES_OFF) {
            mainLight.projectionMatrix = Mat4.identity();
            mainLight.viewMatrix = Mat4.identity();
            return;
        }

        boolean overrideLightDirection = config.customLightRotation();
        int customLightPitch = config.lightPitch();
        int customLightYaw = config.lightYaw();
        int camX = (int) client.getCameraFpX();
        int camY = (int) client.getCameraFpY();

        float lightPitch = 0.0f;
        float lightYaw = 0.0f;
        Color lightColor = currentEnvironment.LightColor;
        Color ambient = currentEnvironment.AmbientColor;
        Color sky = currentEnvironment.SkyColor;

        if (currentEnvironment.UseDynamicTimeOfDay) {
            float normalizedTime = timeOfDay / 24f;
            float radiansPerDegree = (float)Math.PI / 180f;

            // Pitch: 0 → 90 → 0 (sunrise → noon → sunset)
            float maxPitchRadians = 0.75f * (float)(Math.PI / 2f); // limit pitch so it doesn't go to exactly 90
            float pitch = (float)Math.sin(normalizedTime * Math.PI) * maxPitchRadians;

            // Yaw: 90° (east) → 180° (south) → 270° (west)
            float yaw = (270f - normalizedTime * 180f) * radiansPerDegree;

            lightPitch = pitch;
            lightYaw   = yaw;
            lightColor = GetSunColor(normalizedTime);
            ambient = GetAmbientColor(normalizedTime);
            sky = GetSkyColor(normalizedTime);
        }
        else {
            lightPitch = (float) Math.toRadians(currentEnvironment.LightPitch);
            lightYaw = (float) Math.toRadians(currentEnvironment.LightYaw);
        }

        if (overrideLightDirection) {
            lightPitch = (float) Math.toRadians(customLightPitch);
            lightYaw = (float) Math.toRadians(customLightYaw);
        }

        mainLight.type = Light.LightType.Directional;
        mainLight.color = lightColor;
        mainLight.intensity = 1;
        mainLight.radius = 0;
        mainLight.plane = 0;
        mainLight.position = new Vector4(lightPitch, lightYaw, 0, 0);
        mainLight.isDynamic = false;

        // Do projection from the center of the scene instead of the camera.
        int sceneCenter = ((Constants.EXTENDED_SCENE_SIZE / 2) - Variables.SCENE_OFFSET) * 128; // TODO:: make this a constant (128 is TILE_SIZE)
        mainLight.UpdateProjectionViewMatrix(sceneCenter, sceneCenter, plugin.config.shadowResolution().getValue(), Constants.EXTENDED_SCENE_SIZE);

        int playerPosX = (int) client.getLocalPlayer().getLocalLocation().getX();
        int playerPosY = (int) client.getLocalPlayer().getLocalLocation().getY() + 128;
        mainLight.UpdateCloseProjectionViewMatrix(playerPosX, playerPosY, plugin.config.shadowResolution().getValue(), config.shadowDistance());

        ambientColor = ambient;
        skyColor = sky;
        mainLightPitch = lightPitch;
        mainLightYaw = lightYaw;
    }

    public void CleanupOldProjectiles()
    {
        if(sceneProjectiles.size() == 0)
            return;

        List<Projectile> projectiles = new ArrayList<>(sceneProjectiles);
        projectiles.sort(Comparator.comparing(Projectile::getEndCycle));

        List<Projectile> projectilesToRemove = new ArrayList<>();

        for (Projectile projectile : projectiles) {
            if (projectile.getEndCycle() <= client.getGameCycle()) {
                projectilesToRemove.add(projectile);
            }
        }

        // Remove projectiles and associated lights
        for (Projectile projectileToRemove : projectilesToRemove) {
            sceneProjectiles.remove(projectileToRemove);
            sceneLights.remove(projectileLightHashMap.get(projectileToRemove));
            projectileLightHashMap.remove(projectileToRemove);
            log.info("Projectile Removed: " + projectileToRemove.getId());
        }
    }

    // TODO:: Add lights for SpotAnims
    public void OnProjectileMoved(ProjectileMoved event) {
        Projectile projectile = event.getProjectile();

        boolean projectileExists = sceneProjectiles.contains(projectile);
        if(projectileExists) {
            if(projectileLightHashMap.containsKey(projectile)) {
                Light light = projectileLightHashMap.get(projectile);
                Vector4 position = new Vector4((float)projectile.getX(), (float)projectile.getY(), (float)projectile.getZ(), 0);
                light.position = position;
            }
            else
            {
                if(projectileLights.containsKey(projectile.getId())) {
                    ArrayList<Light> lightsForProjectile = projectileLights.get(projectile.getId());
                    if(lightsForProjectile == null) return;

                    Vector4 position = new Vector4((float)projectile.getX(), (float)projectile.getY(), (float)projectile.getZ(), 0);
                    for(int i = 0; i < lightsForProjectile.size(); i++) {
                        Light light = Light.CreateLightFromTemplate(lightsForProjectile.get(i), position, client.getPlane(), 0, plugin.awtContext);
                        light.isDynamic = true;
                        sceneLights.add(light);
                        projectileLightHashMap.put(projectile, light);
                    }
                }
            }
        }
        else {
            int remainingCycles = projectile.getRemainingCycles();
            if (remainingCycles <= 0) {
                return;
            }

            sceneProjectiles.add(projectile);
            log.info("Projectile added: " + projectile.getId());
        }
    }

    public void OnGameObjectSpawned(GameObjectSpawned event)
    {
        GameObject gameObject = event.getGameObject();
        Renderable renderable = gameObject.getRenderable();
        if (renderable instanceof DynamicObject) {
            if(gameObjectLights.containsKey(gameObject.getId()))
            {
                if(gameObjectLightHashMap.containsKey(gameObject))
                {
                    return; // Already tracking this gameobject.
                }

                ArrayList<Light> lightsForGameObject = gameObjectLights.get(gameObject.getId());
                if(lightsForGameObject == null) return;

                int orientation = gameObject.getConfig() >> 6 & 3;
                LocalPoint location = gameObject.getLocalLocation();
                Vector4 position = new Vector4(location.getX(), location.getY(), gameObject.getZ(), 0);
                for(int i = 0; i < lightsForGameObject.size(); i++) {
                    Light light = Light.CreateLightFromTemplate(lightsForGameObject.get(i), position, gameObject.getPlane(), orientation, plugin.awtContext);
                    sceneLights.add(light);
                    gameObjectLightHashMap.put(gameObject, light);
                }
                log.info("GameObject Light Spawned: " + event.getGameObject().getId());
            }
        }
    }

    public void OnGameObjectDespawned(GameObjectDespawned event)
    {
        GameObject gameObject = event.getGameObject();
        if(gameObjectLightHashMap.containsKey(gameObject))
        {
            sceneLights.remove(gameObjectLightHashMap.get(gameObject));
            gameObjectLightHashMap.remove(gameObject);
            log.info("GameObject Light De-spawned: " + event.getGameObject().getId());
        }
    }

    public void OnNpcSpawned(NpcSpawned event) {
        NPC npc = event.getNpc();
        AddNpcLight(npc);
    }

    public void AddNpcLight(NPC npc) {
        if (npcLightHashMap.containsKey(npc)) {
            log.info("NPC already tracked: " + npc.getId());
            return; // Already tracking this npc
        } else {

            ArrayList<Light> lightsForNpc = npcLights.get(npc.getId());
            if (lightsForNpc == null)
                return;

            int orientation = npc.getOrientation();
            LocalPoint location = npc.getLocalLocation();
            float tileHeight = Perspective.getTileHeight(client, location, npc.getWorldLocation().getPlane());
            Vector4 position = new Vector4(location.getX(), location.getY(), tileHeight, 0);
            for(int i = 0; i < lightsForNpc.size(); i++) {
                Light light = Light.CreateLightFromTemplate(lightsForNpc.get(i), position, npc.getWorldLocation().getPlane(), orientation, plugin.awtContext);
                sceneLights.add(light);
                npcLightHashMap.put(npc, light);
            }

            log.info("NPC light spawned: {}, {}", npc.getName(), npc.getId());
        }
    }

    public void OnNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        if (npcLightHashMap.containsKey(npc)) {
            log.info("NPC light de-spawned: {}, {}", npc.getName(), npc.getId());
            Light testNpcLight = npcLightHashMap.get(npc);
            sceneLights.remove(testNpcLight);
            npcLightHashMap.remove(npc);
        }
    }

    public void OnAnimationChanged(AnimationChanged event) {
        Actor actor = event.getActor();
        int animationId = actor.getAnimation();

        // Find a light with animations for this animation Id

        // Create the light if it doesn't exist


        // On Update, update the light sources according to the animation keyframes.
    }

    public Light GetLightAtIndex(int index)
    {
        if(index < 0 || index >= sceneLights.size())
        {
            return null;
        }

        return sceneLights.get(index);
    }

    public Bounds CheckTileRegion(WorldPoint tileWorldPosition)
    {
        if(boundsMap.containsKey(tileWorldPosition)) {
            return boundsMap.get(tileWorldPosition);
        }

        return null;
    }

    private Color GetSunColor(float normalizedTime) {
        ColorKey prev = sunColorKeys.get(0);
        for (int i = 1; i < sunColorKeys.size(); i++) {
            ColorKey next = sunColorKeys.get(i);
            if (normalizedTime <= next.time) {
                float t = (normalizedTime - prev.time) / (next.time - prev.time);
                return lerpColor(prev.color, next.color, t);
            }
            prev = next;
        }
        // fallback in case normalizedTime == 1.0 exactly
        return sunColorKeys.get(sunColorKeys.size() - 1).color;
    }

    private Color GetAmbientColor(float normalizedTime) {
        ColorKey prev = ambientColorKeys.get(0);
        for (int i = 1; i < ambientColorKeys.size(); i++) {
            ColorKey next = ambientColorKeys.get(i);
            if (normalizedTime <= next.time) {
                float t = (normalizedTime - prev.time) / (next.time - prev.time);
                return lerpColor(prev.color, next.color, t);
            }
            prev = next;
        }
        // fallback in case normalizedTime == 1.0 exactly
        return ambientColorKeys.get(ambientColorKeys.size() - 1).color;
    }

    private Color GetSkyColor(float normalizedTime) {
        ColorKey prev = skyColorKeys.get(0);
        for (int i = 1; i < skyColorKeys.size(); i++) {
            ColorKey next = skyColorKeys.get(i);
            if (normalizedTime <= next.time) {
                float t = (normalizedTime - prev.time) / (next.time - prev.time);
                return lerpColor(prev.color, next.color, t);
            }
            prev = next;
        }
        // fallback in case normalizedTime == 1.0 exactly
        return skyColorKeys.get(skyColorKeys.size() - 1).color;
    }

    private Environment GetDefaultEnvironment()
    {
        Player player = client.getLocalPlayer();
        if(player == null) return null;

        LocalPoint localPoint = player.getLocalLocation();
        WorldPoint worldPoint = player.getWorldLocation();
        if (client.isInInstancedRegion())
        {
            worldPoint = WorldPoint.fromLocalInstance(client, localPoint);
        }

        boolean isInOverworld = WorldPoint.getMirrorPoint(worldPoint, true).getY() < Constants.OVERWORLD_MAX_Y;
        Environment targetEnvironment = isInOverworld ? GetDefaultOverworldEnvironment() : GetDefaultUndergroundEnvironment();
        return targetEnvironment;
    }

    private Environment GetDefaultOverworldEnvironment() {
        try {
            return environmentMap.get("DEFAULT");
        } catch (Exception e) {
            log.error("Failed to get default environment", e);
            return null;
        }
    }

    private Environment GetDefaultSunriseEnvironment() {
        try {
            return environmentMap.get("DEFAULT_SUNRISE");
        } catch (Exception e) {
            log.error("Failed to get default sunrise environment", e);
            return null;
        }
    }

    private Environment GetDefaultMiddayEnvironment() {
        try {
            return environmentMap.get("DEFAULT_MIDDAY");
        } catch (Exception e) {
            log.error("Failed to get default day environment", e);
            return null;
        }
    }

    private Environment GetDefaultSunsetEnvironment() {
        try {
            return environmentMap.get("DEFAULT_SUNSET");
        } catch (Exception e) {
            log.error("Failed to get default sunset environment", e);
            return null;
        }
    }

    private Environment GetDefaultNightEnvironment() {
        try {
            return environmentMap.get("DEFAULT_NIGHT");
        } catch (Exception e) {
            log.error("Failed to get default night environment", e);
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

    public Environment GetCurrentEnvironment()
    {
        if(currentEnvironment == null)
        {
            return GetDefaultEnvironment();
        }

        return currentEnvironment;
    }
}

