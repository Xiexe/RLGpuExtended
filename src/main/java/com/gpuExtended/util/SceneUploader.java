
package com.gpuExtended.util;

import com.google.common.base.Stopwatch;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.collect.*;
import com.gpuExtended.GpuExtendedConfig;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.overlays.PerformanceOverlay;
import com.gpuExtended.regions.Area;
import com.gpuExtended.regions.Bounds;
import com.gpuExtended.rendering.*;
import com.gpuExtended.scene.EnvironmentManager;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;

import static com.gpuExtended.GpuExtendedPlugin.SCENE_OFFSET;
import static com.gpuExtended.util.ConstantVariables.*;
import static net.runelite.api.Constants.*;

@Singleton
@Slf4j
public class SceneUploader
{
	public class TerrainVertex
	{
		public int x;
		public int y;
		public int z;
		public int color;
		public boolean isWater;

		public TerrainVertex(int x, int y, int z, int colorHSL, boolean isWater) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.color = colorHSL;
			this.isWater = isWater;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (obj == null || getClass() != obj.getClass()) return false;
			TerrainVertex that = (TerrainVertex) obj;
			return Float.compare(that.x, x) == 0 &&
					Float.compare(that.y, y) == 0 &&
					Float.compare(that.z, z) == 0;
		}

		@Override
		public int hashCode() {
			return Objects.hash(x, y, z);
		}
	}

	@Inject
	private GpuExtendedPlugin plugin;

	@Inject
	private PerformanceOverlay performanceOverlay;

	private final Client client;
	private final GpuExtendedConfig gpuConfig;
	private final EnvironmentManager enviornmentManager;

	public int sceneId = (int) System.nanoTime();
	private int offset;
	private int uvoffset;
	private int uniqueModels;

	public ArrayListMultimap<TerrainVertex, Integer> terrainSharedVertexMap;
	public ArrayListMultimap<Vector3, Integer> staticSharedVertexMap;
	public ArrayListMultimap<Vector3, Integer> dynamicSharedVertexMap;

	@Inject
	SceneUploader(Client client, GpuExtendedConfig config, EnvironmentManager environmentManager)
	{
		this.client = client;
		this.gpuConfig = config;
		this.enviornmentManager = environmentManager;
	}

	public void UploadScene(Scene scene, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer, GpuIntBuffer flagsBuffer)
	{
		Stopwatch stopwatchEntire = Stopwatch.createStarted();
		Stopwatch stopwatch = Stopwatch.createStarted();
		sceneId++;
		offset = 0;
		uvoffset = 0;
		uniqueModels = 0;

		terrainSharedVertexMap = ArrayListMultimap.create();
		staticSharedVertexMap = ArrayListMultimap.create();
		dynamicSharedVertexMap = ArrayListMultimap.create();

		vertexBuffer.clear();
		normalBuffer.clear();
		uvBuffer.clear();

		stopwatch = Stopwatch.createStarted();
		PopulateSceneGeometry(scene, vertexBuffer, uvBuffer, normalBuffer, flagsBuffer);
		stopwatch.stop();
		log.debug("Scene Generate Meshes: {}", stopwatch);

		stopwatch = Stopwatch.createStarted();
		ComputeSmoothNormalsAndColor(terrainSharedVertexMap, normalBuffer, vertexBuffer);
		ComputeWaterDepth(terrainSharedVertexMap, normalBuffer, vertexBuffer);
		stopwatch.stop();
		log.debug("Generate Terrain Normals: {}", stopwatch);

		stopwatchEntire.stop();
		log.debug("Scene Upload Total Time: {}", stopwatchEntire);
	}

	private void PopulateSceneGeometry(Scene scene, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer, GpuIntBuffer flagsBuffer)
	{
		for (int z = 0; z < Constants.MAX_Z; ++z)
		{
			for (int x = 0; x < Constants.EXTENDED_SCENE_SIZE; ++x)
			{
				for (int y = 0; y < Constants.EXTENDED_SCENE_SIZE; ++y)
				{
					Tile tile = scene.getExtendedTiles()[z][x][y];
					if (tile != null)
					{
						GenerateSceneGeometry(scene, tile, vertexBuffer, uvBuffer, normalBuffer, flagsBuffer, false);
					}
				}
			}
		}
	}

	private void GenerateSceneGeometry(Scene scene, Tile tile, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer, GpuIntBuffer flagsBuffer, boolean isUnderBridge)
	{
		EnvironmentManager env = GpuExtendedPlugin.Instance.environmentManager;
		WorldPoint worldLocation = tile.getWorldLocation();
		Point tilePoint = tile.getSceneLocation();

		Tile bridge = tile.getBridge();
		if (bridge != null)
		{   // draw the tile underneath the bridge.
			GenerateSceneGeometry(scene, bridge, vertexBuffer, uvBuffer, normalBuffer, flagsBuffer, true);
		}

		SceneTilePaint sceneTilePaint = tile.getSceneTilePaint();
		if (sceneTilePaint != null)
		{
			sceneTilePaint.setBufferOffset(offset);
			if (sceneTilePaint.getTexture() != -1)
			{
				sceneTilePaint.setUvBufferOffset(uvoffset);
			}
			else
			{
				sceneTilePaint.setUvBufferOffset(-1);
			}

			int vertexCount = PushTerrainTile(scene, sceneTilePaint, vertexBuffer, uvBuffer, normalBuffer, flagsBuffer, bridge != null, isUnderBridge, tile.getRenderLevel(), tilePoint.getX(), tilePoint.getY(), 0, 0);
			int realPlane = GetTileRealPlane(tilePoint.getX() + SCENE_OFFSET, tilePoint.getY() + SCENE_OFFSET, tile.getRenderLevel(), scene);
			int isBridge = bridge != null ? 1 : 0;
			int isUnderneathBridge = isUnderBridge ? 1 : 0;
			if(vertexCount > 0)
			{
				sceneTilePaint.setBufferLen(isUnderneathBridge << 6 | isBridge << 5 | realPlane << 3 | (vertexCount / 3));
			}
			else
			{
				sceneTilePaint.setBufferLen(vertexCount);
			}

			offset += vertexCount;
			if (sceneTilePaint.getTexture() != -1)
			{
				uvoffset += vertexCount;
			}
		}

		SceneTileModel sceneTileModel = tile.getSceneTileModel();
		if (sceneTileModel != null)
		{
			sceneTileModel.setBufferOffset(offset);
			if (sceneTileModel.getTriangleTextureId() != null)
			{
				sceneTileModel.setUvBufferOffset(uvoffset);
			}
			else
			{
				sceneTileModel.setUvBufferOffset(-1);
			}

			int vertexCount = PushTerrainDetailedTile(scene, sceneTileModel, vertexBuffer, uvBuffer, normalBuffer, flagsBuffer, bridge != null, isUnderBridge, tile.getRenderLevel(), tilePoint.getX(), tilePoint.getY(), 0, 0);
			int realPlane = GetTileRealPlane(tilePoint.getX() + SCENE_OFFSET, tilePoint.getY() + SCENE_OFFSET, tile.getRenderLevel(), scene);
			int isBridge = bridge != null ? 1 : 0;
			int isUnderneathBridge = isUnderBridge ? 1 : 0;

			if(vertexCount > 0) {
				sceneTileModel.setBufferLen(isUnderneathBridge << 6 | isBridge << 5 | realPlane << 3 | (vertexCount / 3));
			}
			else {
				sceneTileModel.setBufferLen(vertexCount);
			}

			offset += vertexCount;
			if (sceneTileModel.getTriangleTextureId() != null)
			{
				uvoffset += vertexCount;
			}
		}

		WallObject wallObject = tile.getWallObject();
		if (wallObject != null)
		{
			int wallConfig = wallObject.getConfig();
			Renderable renderable1 = wallObject.getRenderable1();
			if (renderable1 instanceof Model)
			{
				PushStaticModel((Model) renderable1, tile, wallConfig, vertexBuffer, uvBuffer, normalBuffer, flagsBuffer);
			}

			Renderable renderable2 = wallObject.getRenderable2();
			if (renderable2 instanceof Model)
			{
				PushStaticModel((Model) renderable2, tile, wallConfig, vertexBuffer, uvBuffer, normalBuffer, flagsBuffer);
			}
		}

		GroundObject groundObject = tile.getGroundObject();
		if (groundObject != null)
		{
			Renderable renderable = groundObject.getRenderable();
			if (renderable instanceof Model)
			{
				PushStaticModel((Model) renderable, tile, groundObject.getConfig(), vertexBuffer, uvBuffer, normalBuffer, flagsBuffer);
			}
		}

		DecorativeObject decorativeObject = tile.getDecorativeObject();
		if (decorativeObject != null)
		{
			Renderable renderable = decorativeObject.getRenderable();
			if (renderable instanceof Model)
			{
				PushStaticModel((Model) renderable, tile, decorativeObject.getConfig(), vertexBuffer, uvBuffer, normalBuffer, flagsBuffer);
			}

			Renderable renderable2 = decorativeObject.getRenderable2();
			if (renderable2 instanceof Model)
			{
				PushStaticModel((Model) renderable2, tile, decorativeObject.getConfig(), vertexBuffer, uvBuffer, normalBuffer, flagsBuffer);
			}
		}


		GameObject[] gameObjects = tile.getGameObjects();
		for (GameObject gameObject : gameObjects)
		{
			if (gameObject == null)
			{
				continue;
			}

			Renderable renderable = gameObject.getRenderable();
			if (renderable instanceof Model)
			{
				PushStaticModel((Model) gameObject.getRenderable(), tile, gameObject.getConfig(), vertexBuffer, uvBuffer, normalBuffer, flagsBuffer);
			}
		}
	}

	private void PushStaticModel(Model model, Tile tile, int modelConfig, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer, GpuIntBuffer flagsBuffer)
	{
		// deduplicate hillskewed models
		if (model.getUnskewedModel() != null)
		{
			model = model.getUnskewedModel();
		}

		if (model.getSceneId() == sceneId)
		{
			return; // model has already been uploaded
		}

		model.setBufferOffset(offset);
		if (model.getFaceTextures() != null)
		{
			model.setUvBufferOffset(uvoffset);
		}
		else
		{
			model.setUvBufferOffset(-1);
		}
		model.setSceneId(sceneId);
		uniqueModels++;

		Point tilePoint = tile.getSceneLocation();
		int vertexCount = PushGeometryToBuffers(model, vertexBuffer, uvBuffer, normalBuffer, flagsBuffer, tilePoint.getX(), tilePoint.getY(), modelConfig, false, true, staticSharedVertexMap);
		offset += vertexCount;
		if (model.getFaceTextures() != null)
		{
			uvoffset += vertexCount;
		}
	}

	public int PushDynamicModel(Model model, int modelConfig, boolean isNPC, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer, GpuIntBuffer flagsBuffer)
	{
		int vertexCount = PushGeometryToBuffers(model, vertexBuffer, uvBuffer, normalBuffer, flagsBuffer, 0, 0, modelConfig, isNPC, false, dynamicSharedVertexMap);
		return vertexCount;
	}

	// Map Tiles
	public int PushTerrainTile(Scene scene, SceneTilePaint tile, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer, GpuIntBuffer flagsBuffer, boolean hasBridge, boolean isUnderBridge, int tileZ, int tileX, int tileY, int offsetX, int offsetY)
	{
		final int[][][] tileHeights = scene.getTileHeights();

		final int localX = offsetX;
		final int localY = offsetY;

		tileX += SCENE_OFFSET;
		tileY += SCENE_OFFSET;

		int swHeight = tileHeights[tileZ][tileX    ][tileY    ];
		int seHeight = tileHeights[tileZ][tileX + 1][tileY    ];
		int neHeight = tileHeights[tileZ][tileX + 1][tileY + 1];
		int nwHeight = tileHeights[tileZ][tileX    ][tileY + 1];

		final int neColor = tile.getNeColor();
		final int nwColor = tile.getNwColor();
		final int seColor = tile.getSeColor();
		final int swColor = tile.getSwColor();

		if (neColor == 12345678)
		{
			return 0;
		}

		// 0,0
		int vertexDx = localX;
		int vertexDy = localY;
		int vertexDz = swHeight;
		final int c1 = swColor;

		// 1,0
		int vertexCx = localX + Perspective.LOCAL_TILE_SIZE;
		int vertexCy = localY;
		int vertexCz = seHeight;
		final int c2 = seColor;

		// 1,1
		int vertexAx = localX + Perspective.LOCAL_TILE_SIZE;
		int vertexAy = localY + Perspective.LOCAL_TILE_SIZE;
		int vertexAz = neHeight;
		final int c3 = neColor;

		// 0,1
		int vertexBx = localX;
		int vertexBy = localY + Perspective.LOCAL_TILE_SIZE;
		int vertexBz = nwHeight;
		final int c4 = nwColor;

		vertexBuffer.ensureCapacity(24);
		normalBuffer.ensureCapacity(24);
		uvBuffer.ensureCapacity(24);
		flagsBuffer.ensureCapacity(24);

		vertexBuffer.put(vertexAx, vertexAz, vertexAy, c3);
		vertexBuffer.put(vertexBx, vertexBz, vertexBy, c4);
		vertexBuffer.put(vertexCx, vertexCz, vertexCy, c2);

		vertexBuffer.put(vertexDx, vertexDz, vertexDy, c1);
		vertexBuffer.put(vertexCx, vertexCz, vertexCy, c2);
		vertexBuffer.put(vertexBx, vertexBz, vertexBy, c4);

		// Flags are populated in the draw call for tiles in GPUExtendedPlugin
		flagsBuffer.put(0, 0, 0, 1);
		flagsBuffer.put(0, 0, 0, 1);
		flagsBuffer.put(0, 0, 0, 1);

		flagsBuffer.put(0, 0, 0, 1);
		flagsBuffer.put(0, 0, 0, 1);
		flagsBuffer.put(0, 0, 0, 1);

		boolean isWaterVertex = false;
		if (tile.getTexture() != -1)
		{
			int tex = tile.getTexture() + 1;
			isWaterVertex = (tex == 2 || tex == 26);
			uvBuffer.put(tex, vertexDx, vertexDz, vertexDy);
			uvBuffer.put(tex, vertexCx, vertexCz, vertexCy);
			uvBuffer.put(tex, vertexBx, vertexBz, vertexBy);

			uvBuffer.put(tex, vertexDx, vertexDz, vertexDy);
			uvBuffer.put(tex, vertexCx, vertexCz, vertexCy);
			uvBuffer.put(tex, vertexBx, vertexBz, vertexBy);
		}

		int tx = tileX * Perspective.LOCAL_TILE_SIZE;
		int ty = tileY * Perspective.LOCAL_TILE_SIZE;

		Vector3 normA = CalculateBaseNormal(vertexAx, vertexAz, vertexAy, vertexBx, vertexBz, vertexBy, vertexCx, vertexCz, vertexCy);
		Vector3 normB = CalculateBaseNormal(vertexDx, vertexDz, vertexDy, vertexCx, vertexCz, vertexCy, vertexBx, vertexBz, vertexBy);

		int startOfTileBufferIndex = normalBuffer.getBuffer().position();
		normalBuffer.put(normA.x, normA.y, normA.z, isWaterVertex ? 0:1);
		normalBuffer.put(normA.x, normA.y, normA.z, isWaterVertex ? 0:1);
		normalBuffer.put(normA.x, normA.y, normA.z, isWaterVertex ? 0:1);

		normalBuffer.put(normB.x, normB.y, normB.z, isWaterVertex ? 0:1);
		normalBuffer.put(normB.x, normB.y, normB.z, isWaterVertex ? 0:1);
		normalBuffer.put(normB.x, normB.y, normB.z, isWaterVertex ? 0:1);

		terrainSharedVertexMap.put(new TerrainVertex(vertexAx + tx, vertexAz, vertexAy + ty, 0, isWaterVertex), startOfTileBufferIndex + 0*4);
		terrainSharedVertexMap.put(new TerrainVertex(vertexBx + tx, vertexBz, vertexBy + ty, 0, isWaterVertex), startOfTileBufferIndex + 1*4);
		terrainSharedVertexMap.put(new TerrainVertex(vertexCx + tx, vertexCz, vertexCy + ty, 0, isWaterVertex), startOfTileBufferIndex + 2*4);

		terrainSharedVertexMap.put(new TerrainVertex(vertexDx + tx, vertexDz, vertexDy + ty, 0, isWaterVertex), startOfTileBufferIndex + 3*4);
		terrainSharedVertexMap.put(new TerrainVertex(vertexCx + tx, vertexCz, vertexCy + ty, 0, isWaterVertex), startOfTileBufferIndex + 4*4);
		terrainSharedVertexMap.put(new TerrainVertex(vertexBx + tx, vertexBz, vertexBy + ty, 0, isWaterVertex), startOfTileBufferIndex + 5*4);

		// 0    3    6    11   15   19   | 23   27   31   35   39   43
		// xyzw xyzw xyzw xyzw xyzw xyzw | xyzw xyzw xyzw xyzw xyzw xyzw

		return 6;
	}

	// Map tiles with extra geometry
	public int PushTerrainDetailedTile(Scene scene, SceneTileModel sceneTileModel, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer, GpuIntBuffer flagsBuffer, boolean hasBridge, boolean isUnderBridge, int tileZ, int tileX, int tileY, int offsetX, int offsetZ)
	{
		final int[] faceX = sceneTileModel.getFaceX();
		final int[] faceY = sceneTileModel.getFaceY();
		final int[] faceZ = sceneTileModel.getFaceZ();

		final int[] vertexX = sceneTileModel.getVertexX();
		final int[] vertexY = sceneTileModel.getVertexY();
		final int[] vertexZ = sceneTileModel.getVertexZ();

		final int[] triangleColorA = sceneTileModel.getTriangleColorA();
		final int[] triangleColorB = sceneTileModel.getTriangleColorB();
		final int[] triangleColorC = sceneTileModel.getTriangleColorC();

		final int[] triangleTextures = sceneTileModel.getTriangleTextureId();

		final int faceCount = faceX.length;
		vertexBuffer.ensureCapacity(faceCount * 12);
		normalBuffer.ensureCapacity(faceCount * 12);
		uvBuffer.ensureCapacity(faceCount * 12);
		flagsBuffer.ensureCapacity(faceCount * 12);

		int baseX = tileX << Perspective.LOCAL_COORD_BITS;
		int baseY = tileY << Perspective.LOCAL_COORD_BITS;

		int vertexCount = 0;
		for (int i = 0; i < faceCount; ++i)
		{
			final int triangleA = faceX[i];
			final int triangleB = faceY[i];
			final int triangleC = faceZ[i];

			final int colorA = triangleColorA[i];
			final int colorB = triangleColorB[i];
			final int colorC = triangleColorC[i];

			if (colorA == 12345678)
			{
				continue;
			}

			// vertexes are stored in scene local, convert to tile local
			int vertexXA = vertexX[triangleA] - baseX;
			int vertexYA = vertexY[triangleA];
			int vertexZA = vertexZ[triangleA] - baseY;

			int vertexXB = vertexX[triangleB] - baseX;
			int vertexYB = vertexY[triangleB];
			int vertexZB = vertexZ[triangleB] - baseY;

			int vertexXC = vertexX[triangleC] - baseX;
			int vertexYC = vertexY[triangleC];
			int vertexZC = vertexZ[triangleC] - baseY;

			vertexBuffer.put(vertexXA + offsetX, vertexYA, vertexZA + offsetZ, colorA);
			vertexBuffer.put(vertexXB + offsetX, vertexYB, vertexZB + offsetZ, colorB);
			vertexBuffer.put(vertexXC + offsetX, vertexYC, vertexZC + offsetZ, colorC);

			// Flags are populated in the draw call for tiles in GPUExtendedPlugin
			flagsBuffer.put(0, 0, 0, 1);
			flagsBuffer.put(0, 0, 0, 1);
			flagsBuffer.put(0, 0, 0, 1);

			boolean isWaterVertex = false;
			if (triangleTextures != null)
			{
				if (triangleTextures[i] != -1)
				{
					int tex = triangleTextures[i] + 1;
					isWaterVertex = (tex == 2 || tex == 26);
					uvBuffer.put(tex, offsetX, vertexYA, offsetZ);
					uvBuffer.put(tex, offsetX + 128, vertexYB, offsetZ);
					uvBuffer.put(tex, offsetX, vertexYC, offsetZ + 128);
				}
				else
				{
					uvBuffer.put(0, 0, 0, 0f);
					uvBuffer.put(0, 0, 0, 0f);
					uvBuffer.put(0, 0, 0, 0f);
				}
			}

			int startOfTileBufferIndex = normalBuffer.getBuffer().position();
			Vector3 norm = CalculateBaseNormal(vertexXA + offsetX, vertexYA, vertexZA + offsetZ, vertexXB + offsetX, vertexYB, vertexZB + offsetZ, vertexXC + offsetX, vertexYC, vertexZC + offsetZ);
			normalBuffer.put(norm.x, norm.y, norm.z, isWaterVertex ? 0:1);
			normalBuffer.put(norm.x, norm.y, norm.z, isWaterVertex ? 0:1);
			normalBuffer.put(norm.x, norm.y, norm.z, isWaterVertex ? 0:1);

			int tx = (tileX + SCENE_OFFSET) * Perspective.LOCAL_TILE_SIZE;
			int tz = (tileY + SCENE_OFFSET) * Perspective.LOCAL_TILE_SIZE;
			terrainSharedVertexMap.put(new TerrainVertex(vertexXA + tx, vertexYA, vertexZA + tz, 0, isWaterVertex), startOfTileBufferIndex + 0*4);
			terrainSharedVertexMap.put(new TerrainVertex(vertexXB + tx, vertexYB, vertexZB + tz, 0, isWaterVertex), startOfTileBufferIndex + 1*4);
			terrainSharedVertexMap.put(new TerrainVertex(vertexXC + tx, vertexYC, vertexZC + tz, 0, isWaterVertex), startOfTileBufferIndex + 2*4);

			vertexCount += 3;
		}

		return vertexCount;
	}

	public static int GetTileRealPlane(int sceneTileX, int sceneTileY, int tileZ, Scene scene)
	{
		int realPlane = tileZ;
		return Math.max(0, realPlane);
	}

	public static boolean CheckIsOnBridge(int sceneTileX, int sceneTileY, int tileZ, Scene scene)
	{
		boolean isOnBridge = false;
		byte[][][] tileSettings = scene.getExtendedTileSettings();
		if (1 <= sceneTileX && sceneTileX < EXTENDED_SCENE_SIZE - 1 && 1 <= sceneTileY && sceneTileY < EXTENDED_SCENE_SIZE - 1) {
			for (int i = 0; i < MAX_Z; i++) {
				int belowPlane = Math.max(0, tileZ - i);

				int tileSetting = tileSettings[belowPlane][sceneTileX][sceneTileY];
				isOnBridge = (tileSetting & TILE_FLAG_BRIDGE) != 0;

				if (isOnBridge) {
					break;
				}
			}
		}

		return isOnBridge;
	}

	private int PushGeometryToBuffers(Model model,
									  GpuIntBuffer vertexBuffer,
									  GpuFloatBuffer uvBuffer,
									  GpuFloatBuffer normalBuffer,
									  GpuIntBuffer flagsBuffer,
									  int tileX,
									  int tileY,
									  int modelConfig,
									  boolean isNPC,
									  boolean isStatic,
									  ArrayListMultimap<Vector3, Integer> sharedVertexMap)
	{
		final int triCount = Math.min(model.getFaceCount(), GpuExtendedPlugin.MAX_TRIANGLE);
		vertexBuffer.ensureCapacity(triCount * 12);
		normalBuffer.ensureCapacity(triCount * 12);
		uvBuffer.ensureCapacity(triCount * 12);
		flagsBuffer.ensureCapacity(triCount * 12);

		final float[] vertexX = model.getVerticesX();
		final float[] vertexY = model.getVerticesY();
		final float[] vertexZ = model.getVerticesZ();

		final int[] normalX = model.getVertexNormalsX();
		final int[] normalY = model.getVertexNormalsY();
		final int[] normalZ = model.getVertexNormalsZ();

		final int[] indices1 = model.getFaceIndices1();
		final int[] indices2 = model.getFaceIndices2();
		final int[] indices3 = model.getFaceIndices3();

		final int[] color1s = model.getFaceColors1();
		final int[] color2s = model.getFaceColors2();
		final int[] color3s = model.getFaceColors3();

		final short[] faceTextures = model.getFaceTextures();
		final byte[] textureFaces = model.getTextureFaces();
		final int[] texIndices1 = model.getTexIndices1();
		final int[] texIndices2 = model.getTexIndices2();
		final int[] texIndices3 = model.getTexIndices3();

		final byte[] transparencies = model.getFaceTransparencies();
		final byte[] facePriorities = model.getFaceRenderPriorities();

		final byte overrideAmount = model.getOverrideAmount();
		final byte overrideHue = model.getOverrideHue();
		final byte overrideSat = model.getOverrideSaturation();
		final byte overrideLum = model.getOverrideLuminance();

		int vertexCount = 0;
		for (int tri = 0; tri < triCount; tri++)
		{
			int color1 = color1s[tri];
			int color2 = color2s[tri];
			int color3 = color3s[tri];

			if (color3 == -1) // Model only has one color.
			{
				color2 = color3 = color1;
			}
			else if (color3 == -2) // Model should be skipped. Pad buffer.
			{
				vertexBuffer.put(0, 0, 0, 0);
				vertexBuffer.put(0, 0, 0, 0);
				vertexBuffer.put(0, 0, 0, 0);

				normalBuffer.put(0, 0, 0, 0);
				normalBuffer.put(0, 0, 0, 0);
				normalBuffer.put(0, 0, 0, 0);

				flagsBuffer.put(0, 0, 0, 0);
				flagsBuffer.put(0, 0, 0, 0);
				flagsBuffer.put(0, 0, 0, 0);

				if (faceTextures != null)
				{
					uvBuffer.put(0, 0, 0, 0);
					uvBuffer.put(0, 0, 0, 0);
					uvBuffer.put(0, 0, 0, 0);
				}

				vertexCount += 3;
				continue;
			}

			if(isNPC) {
				if(IsBakedGroundShading(model, tri))
				{
					color1 = color2 = color3 = 0x12345678;
					vertexBuffer.put(0, 0, 0, 0);
					vertexBuffer.put(0, 0, 0, 0);
					vertexBuffer.put(0, 0, 0, 0);

					normalBuffer.put(0, 0, 0, 0);
					normalBuffer.put(0, 0, 0, 0);
					normalBuffer.put(0, 0, 0, 0);

					flagsBuffer.put(0, 0, 0, 0);
					flagsBuffer.put(0, 0, 0, 0);
					flagsBuffer.put(0, 0, 0, 0);

					if (faceTextures != null)
					{
						uvBuffer.put(0, 0, 0, 0);
						uvBuffer.put(0, 0, 0, 0);
						uvBuffer.put(0, 0, 0, 0);
					}
					vertexCount += 3;
					continue;
				}
			}

			// HSL override is not applied to textured faces
			if (faceTextures == null || faceTextures[tri] == -1)
			{
				if (overrideAmount > 0)
				{
					color1 = interpolateHSL(color1, overrideHue, overrideSat, overrideLum, overrideAmount);
					color2 = interpolateHSL(color2, overrideHue, overrideSat, overrideLum, overrideAmount);
					color3 = interpolateHSL(color3, overrideHue, overrideSat, overrideLum, overrideAmount);
				}
			}

			int i0 = indices1[tri];
			int i1 = indices2[tri];
			int i2 = indices3[tri];

			int packedAlphaPriorityFlags = packAlphaPriority(faceTextures, transparencies, facePriorities, tri);

			vertexBuffer.put(vertexX[i0], vertexY[i0], vertexZ[i0], packedAlphaPriorityFlags | color1);
			vertexBuffer.put(vertexX[i1], vertexY[i1], vertexZ[i1], packedAlphaPriorityFlags | color2);
			vertexBuffer.put(vertexX[i2], vertexY[i2], vertexZ[i2], packedAlphaPriorityFlags | color3);

			flagsBuffer.put(0, 0, 0, 0);
			flagsBuffer.put(0, 0, 0, 0);
			flagsBuffer.put(0, 0, 0, 0);

			if (faceTextures != null)
			{
				if (faceTextures[tri] != -1)
				{
					int texA, texB, texC;

					if (textureFaces != null && textureFaces[tri] != -1)
					{
						int tface = textureFaces[tri] & 0xff;
						texA = texIndices1[tface];
						texB = texIndices2[tface];
						texC = texIndices3[tface];
					}
					else
					{
						texA = i0;
						texB = i1;
						texC = i2;
					}

					int texture = faceTextures[tri] + 1;
					uvBuffer.put(texture, vertexX[texA], vertexY[texA], vertexZ[texA]);
					uvBuffer.put(texture, vertexX[texB], vertexY[texB], vertexZ[texB]);
					uvBuffer.put(texture, vertexX[texC], vertexY[texC], vertexZ[texC]);
				}
				else
				{
					uvBuffer.put(0, 0, 0, 0);
					uvBuffer.put(0, 0, 0, 0);
					uvBuffer.put(0, 0, 0, 0);
				}
			}

			if(normalX != null) {
				normalBuffer.put(normalX[i0], normalY[i0], normalZ[i0], 0);
				normalBuffer.put(normalX[i1], normalY[i1], normalZ[i1], 0);
				normalBuffer.put(normalX[i2], normalY[i2], normalZ[i2], 0);
			}
			else
			{
				normalBuffer.put(0, 0, 0, 0);
				normalBuffer.put(0, 0, 0, 0);
				normalBuffer.put(0, 0, 0, 0);
			}

			vertexCount += 3;
		}

		return vertexCount;
	}

	private boolean IsBakedGroundShading(Model model, int face) {
		final byte[] faceTransparencies = model.getFaceTransparencies();
		if (faceTransparencies == null || (faceTransparencies[face] & 0xFF) <= 100)
			return false;

		final short[] faceTextures = model.getFaceTextures();
		if (faceTextures != null && faceTextures[face] != -1)
			return false;

		final float[] yVertices = model.getVerticesY();
		float heightA = yVertices[model.getFaceIndices1()[face]];
		if (heightA < -8)
			return false;

		float heightB = yVertices[model.getFaceIndices2()[face]];
		float heightC = yVertices[model.getFaceIndices3()[face]];
		return heightA == heightB && heightA == heightC;
	}

	public Vector3 CalculateBaseNormal(float p0x, float p0y, float p0z, float p1x, float p1y, float p1z, float p2x, float p2y, float p2z)
	{
		Vector3 edge1 = new Vector3(p1x - p0x, p1y - p0y, p1z - p0z);
		Vector3 edge2 = new Vector3(p2x - p0x, p2y - p0y, p2z - p0z);
		Vector3 norm = Vector3.Cross(edge1, edge2).Normalize();

		return norm;
	}

	public void ComputeSmoothNormalsAndColor(ArrayListMultimap<TerrainVertex, Integer> sharedVertexPositionMap, GpuFloatBuffer normalBuffer, GpuIntBuffer vertexBuffer)
	{
		FloatBuffer normalFloatBuffer = normalBuffer.getBuffer();
		for (Collection<Integer> sharedVerts : sharedVertexPositionMap.asMap().values()) {
			float avgNormalX = 0;
			float avgNormalY = 0;
			float avgNormalZ = 0;

			// Loop the verts that share that position
			for (Integer indexToVertexAtLocation : sharedVerts) {
				int normalIndex = indexToVertexAtLocation;
				float normalX = normalFloatBuffer.get(normalIndex + 0);
				float normalY = normalFloatBuffer.get(normalIndex + 1);
				float normalZ = normalFloatBuffer.get(normalIndex + 2);

				avgNormalX += normalX;
				avgNormalY += normalY;
				avgNormalZ += normalZ;
			}

			int numVerts = sharedVerts.size();
			avgNormalX /= numVerts;
			avgNormalY /= numVerts;
			avgNormalZ /= numVerts;

			// Loop the verts that share that position
			for (Integer vertexStartBufferIndex : sharedVerts) {
				int normalIndex = vertexStartBufferIndex;
				normalFloatBuffer.put(normalIndex + 0, avgNormalX);
				normalFloatBuffer.put(normalIndex + 1, avgNormalY);
				normalFloatBuffer.put(normalIndex + 2, avgNormalZ);
			}
		}
	}


	public void ComputeWaterDepth(ArrayListMultimap<TerrainVertex, Integer> sharedVertexPositionMap, GpuFloatBuffer normalBuffer, GpuIntBuffer vertexBuffer)
	{
//		FloatBuffer normalFloatBuffer = normalBuffer.getBuffer();
//		IntBuffer vertexIntBuffer = vertexBuffer.getBuffer();
//
//		List<TerrainVertex> nonWaterVertices = new ArrayList<>();
//		for (TerrainVertex vertex : sharedVertexPositionMap.keySet()) {
//			if (!vertex.isWater) {
//				nonWaterVertices.add(vertex);
//			}
//		}
//		TerrainKDTree kdTree = new TerrainKDTree(nonWaterVertices);
//
//		for (TerrainVertex vertex : sharedVertexPositionMap.keySet()) {
//			if (vertex.isWater) {
//				TerrainVertex nearestNonWaterVertex = kdTree.findNearest(vertex);
//				float distance = calculateDistance(vertex, nearestNonWaterVertex);
//
//				for (Integer vertexIndex : sharedVertexPositionMap.get(vertex)) {
//					// Example: store the distance in the w component of the vertex buffer
//					normalFloatBuffer.put(vertexIndex + 3, distance);
//				}
//			}
//		}
	}

	private float calculateDistance(TerrainVertex v1, TerrainVertex v2) {
		return (v1.x - v2.x) * (v1.x - v2.x) +
				(v1.y - v2.y) * (v1.y - v2.y) +
				(v1.z - v2.z) * (v1.z - v2.z);
	}

	private static final float EPS = 1e-10f;
	private static float clamp(float value, float min, float max) {
		return Math.min(Math.max(value, min), max);
	}

	private static int clamp(int value, int min, int max) {
		return Math.min(Math.max(value, min), max);
	}

	private static float mod(float x, float modulus) {
		return (float) (x - Math.floor(x / modulus) * modulus);
	}

	public static float[] hslToSrgb(float[] hsl) {
		float C = hsl[1] * (1 - Math.abs(2 * hsl[2] - 1));
		float H_prime = hsl[0] * 6;
		float m = hsl[2] - C / 2;

		float r = clamp(Math.abs(H_prime - 3) - 1, 0, 1) * C + m;
		float g = clamp(2 - Math.abs(H_prime - 2), 0, 1) * C + m;
		float b = clamp(2 - Math.abs(H_prime - 4), 0, 1) * C + m;
		return new float[] { r, g, b };
	}

	public static float[] unpackHsl(int hsl) {
		// 6-bit hue | 3-bit saturation | 7-bit lightness
		float H = (hsl >>> 10 & 0x3F) / (0x3F + 1f) + .0078125f;
		float S = (hsl >>> 7 & 0x7) / (0x7 + 1f) + .0625f;
		float L = (hsl & 0x7F) / (0x7F + 1f);
		return new float[] { H, S, L };
	}

	public static float[] packedHslToSrgb(int packedHsl) {
		return hslToSrgb(unpackHsl(packedHsl));
	}

	public static float[] srgbToHsl(float[] srgb) {
		float V = Math.max(Math.max(srgb[0], srgb[1]), srgb[2]);
		float X_min = Math.min(Math.min(srgb[0], srgb[1]), srgb[2]);
		float C = V - X_min;

		float H = 0;
		if (C > 0) {
			if (V == srgb[0]) {
				H = mod((srgb[1] - srgb[2]) / C, 6);
			} else if (V == srgb[1]) {
				H = (srgb[2] - srgb[0]) / C + 2;
			} else {
				H = (srgb[0] - srgb[1]) / C + 4;
			}
			assert H >= 0 && H <= 6;
		}

		float L = (V + X_min) / 2;
		float divisor = 1 - Math.abs(2 * L - 1);
		float S_L = Math.abs(divisor) < EPS ? 0 : C / divisor;
		return new float[] { H / 6, S_L, L };
	}

	public static int packRawHsl(int... hsl) {
		return hsl[0] << 10 | hsl[1] << 7 | hsl[2];
	}

	public static int packHsl(float... hsl) {
		int H = clamp(Math.round((hsl[0] - .0078125f) * (0x3F + 1)), 0, 0x3F);
		int S = clamp(Math.round((hsl[1] - .0625f) * (0x7 + 1)), 0, 0x7);
		int L = clamp(Math.round(hsl[2] * (0x7F + 1)), 0, 0x7F);
		return packRawHsl(H, S, L);
	}

	public static int srgbToPackedHsl(float[] srgb) {
		return packHsl(srgbToHsl(srgb));
	}

	private static int interpolateHSL(int hsl, byte hue2, byte sat2, byte lum2, byte lerp)
	{
		int hue = hsl >> 10 & 63;
		int sat = hsl >> 7 & 7;
		int lum = hsl & 127;
		int var9 = lerp & 255;
		if (hue2 != -1)
		{
			hue += var9 * (hue2 - hue) >> 7;
		}

		if (sat2 != -1)
		{
			sat += var9 * (sat2 - sat) >> 7;
		}

		if (lum2 != -1)
		{
			lum += var9 * (lum2 - lum) >> 7;
		}

		return (hue << 10 | sat << 7 | lum) & 65535;
	}

	private static int packAlphaPriority(short[] faceTextures, byte[] faceTransparencies, byte[] facePriorities, int face)
	{
		int alpha = 0;
		if (faceTransparencies != null && (faceTextures == null || faceTextures[face] == -1))
		{
			alpha = (faceTransparencies[face] & 0xFF) << 24;
		}
		int priority = 0;
		if (facePriorities != null)
		{
			priority = (facePriorities[face] & 0xff) << 16;
		}
		return alpha | priority;
	}

	public void PrepareScene(Scene scene)
	{
		Area currentArea = enviornmentManager.currentArea;

		if(currentArea != null)
		{
			if(currentArea.isHideOtherAreas())
			{
				if (scene.isInstance() || !gpuConfig.hideUnrelatedMaps()) return;

				Bounds[] areaBounds = currentArea.getBounds();
				if(areaBounds == null) return;

				log.info("Hiding unrelated maps");
				ArrayList<Tile> invalidTiles = new ArrayList<>();

				Tile[][][] tiles = scene.getExtendedTiles();
				for (int x = 0; x < Constants.EXTENDED_SCENE_SIZE; ++x) {
					for (int y = 0; y < Constants.EXTENDED_SCENE_SIZE; ++y) {
						for (int z = 0; z < Constants.MAX_Z; ++z) {
							Tile tile = tiles[z][x][y];
							if (tile == null) continue;

							WorldPoint tileLocation = tile.getWorldLocation();
							boolean tileValid = false;
							for(Bounds currentSubBounds : areaBounds)
							{
								if (currentSubBounds.contains(tileLocation, 2)) {
									tileValid = true;
									break;
								}
							}

							if(!tileValid) {
								invalidTiles.add(tile);
							}
						}
					}
				}

				for(Tile tile : invalidTiles)
				{
					scene.removeTile(tile);
				}
			}
		}

		Bounds currentBounds = enviornmentManager.currentBounds;
		if(currentBounds != null)
		{
			scene.setRoofRemovalMode(gpuConfig.roofFading() && currentBounds.isAllowRoofFading() ? 16 : 0);

			if (scene.isInstance() || !gpuConfig.hideUnrelatedMaps()) return;
			if(!currentBounds.isHideOtherAreas()) return;

			Tile[][][] tiles = scene.getExtendedTiles();
			for (int x = 0; x < Constants.EXTENDED_SCENE_SIZE; ++x) {
				for (int y = 0; y < Constants.EXTENDED_SCENE_SIZE; ++y) {

					for (int z = 0; z < Constants.MAX_Z; ++z) {
						Tile tile = tiles[z][x][y];
						if (tile == null) continue;

						WorldPoint tileLocation = tile.getWorldLocation();
						boolean insideCurrentSubarea = currentBounds.contains(tileLocation, 2);
						if (!insideCurrentSubarea) {
							scene.removeTile(tile);
						}
					}
				}
			}
		}
		else {
			scene.setRoofRemovalMode(gpuConfig.roofFading() ? 16 : 0);
		}
	}

	private static void removeChunk(Scene scene, int cx, int cy)
	{
		int wx = cx * 8;
		int wy = cy * 8;
		int sx = wx - scene.getBaseX();
		int sy = wy - scene.getBaseY();
		int cmsx = sx + SCENE_OFFSET;
		int cmsy = sy + SCENE_OFFSET;
		Tile[][][] tiles = scene.getExtendedTiles();
		for (int x = 0; x < 8; ++x)
		{
			for (int y = 0; y < 8; ++y)
			{
				int msx = cmsx + x;
				int msy = cmsy + y;
				if (msx >= 0 && msx < Constants.EXTENDED_SCENE_SIZE && msy >= 0 && msy < Constants.EXTENDED_SCENE_SIZE)
				{
					for (int z = 0; z < Constants.MAX_Z; ++z)
					{
						Tile tile = tiles[z][msx][msy];
						if (tile != null)
						{
							scene.removeTile(tile);
						}
					}
				}
			}
		}
	}

	public static int getBakedOrientation(int config) {
		switch (config >> 6 & 3) {
			case 0: // Rotated 180 degrees
				return 1024;
			case 1: // Rotated 90 degrees counter-clockwise
				return 1536;
			case 2: // Not rotated
			default:
				return 0;
			case 3: // Rotated 90 degrees clockwise
				return 512;
		}
	}
}
