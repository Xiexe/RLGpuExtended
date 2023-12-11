
package com.gpuExtended.util;

import com.google.common.base.Stopwatch;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.collect.*;
import com.gpuExtended.GpuExtendedConfig;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.rendering.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.SceneTileModel;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Tile;
import net.runelite.api.WallObject;
import com.gpuExtended.regions.Regions;

@Singleton
@Slf4j
public class SceneUploader
{
	private final Client client;
	private final GpuExtendedConfig gpuConfig;

	private Regions regions;

	public int sceneId = (int) System.nanoTime();
	private int offset;
	private int uvoffset;
	private int uniqueModels;

	public ArrayListMultimap<Vector3, Integer> terrainSharedVertexMap;
	public ArrayListMultimap<Vector3, Integer> staticSharedVertexMap;
	public ArrayListMultimap<Vector3, Integer> dynamicSharedVertexMap;

	@Inject
	SceneUploader(Client client, GpuExtendedConfig config)
	{
		this.client = client;
		this.gpuConfig = config;

		try (var in = SceneUploader.class.getResourceAsStream("/regions/regions.txt"))
		{
			regions = new Regions(in, "regions.txt");
		}
		catch (IOException ex)
		{
			throw new RuntimeException(ex);
		}
	}

	public void UploadScene(Scene scene, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer)
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

		PrepareScene(scene);
		stopwatch.stop();
		log.debug("Scene preparation time: {}", stopwatch);

		stopwatch = Stopwatch.createStarted();
		PopulateSceneGeometry(scene, vertexBuffer, uvBuffer, normalBuffer);
		stopwatch.stop();
		log.debug("Scene Generate Meshes: {}", stopwatch);

		log.debug("Push Mesh Buffers: {}", stopwatch);
		stopwatchEntire.stop();
		log.debug("Scene Upload Total Time: {}", stopwatchEntire);
	}

	private void PopulateSceneGeometry(Scene scene, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer)
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
						GenerateSceneGeometry(scene, tile, vertexBuffer, uvBuffer, normalBuffer);
					}
				}
			}
		}
	}

	private void GenerateSceneGeometry(Scene scene, Tile tile, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer)
	{
		Point tilePoint = tile.getSceneLocation();
		Tile bridge = tile.getBridge();
		if (bridge != null)
		{   // draw the tile underneath the bridge.
			GenerateSceneGeometry(scene, bridge, vertexBuffer, uvBuffer, normalBuffer);
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

			int len = PushTerrainTile(scene, sceneTilePaint, vertexBuffer, uvBuffer, normalBuffer, tile.getRenderLevel(), tilePoint.getX(), tilePoint.getY(), 0, 0);

			//TODO:: Refactor this to be in the method.
			sceneTilePaint.setBufferLen(len);
			offset += len;
			uvoffset += len;
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

			int len = PushTerrainDetailedTile(sceneTileModel, vertexBuffer, uvBuffer, normalBuffer, tilePoint.getX(), tilePoint.getY(), 0, 0);

			//TODO:: Refactor this to be in the method.
			sceneTileModel.setBufferLen(len);
			offset += len;
			uvoffset += len;
		}

		WallObject wallObject = tile.getWallObject();
		if (wallObject != null)
		{
			Renderable renderable1 = wallObject.getRenderable1();
			if (renderable1 instanceof Model)
			{
				PushStaticModel((Model) renderable1, tile, vertexBuffer, uvBuffer, normalBuffer);
			}

			Renderable renderable2 = wallObject.getRenderable2();
			if (renderable2 instanceof Model)
			{
				PushStaticModel((Model) renderable2, tile, vertexBuffer, uvBuffer, normalBuffer);
			}
		}

		GroundObject groundObject = tile.getGroundObject();
		if (groundObject != null)
		{
			Renderable renderable = groundObject.getRenderable();
			if (renderable instanceof Model)
			{
				PushStaticModel((Model) renderable, tile, vertexBuffer, uvBuffer, normalBuffer);
			}
		}

		DecorativeObject decorativeObject = tile.getDecorativeObject();
		if (decorativeObject != null)
		{
			Renderable renderable = decorativeObject.getRenderable();
			if (renderable instanceof Model)
			{
				PushStaticModel((Model) renderable, tile, vertexBuffer, uvBuffer, normalBuffer);
			}

			Renderable renderable2 = decorativeObject.getRenderable2();
			if (renderable2 instanceof Model)
			{
				PushStaticModel((Model) renderable2, tile, vertexBuffer, uvBuffer, normalBuffer);
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
				PushStaticModel((Model) gameObject.getRenderable(), tile, vertexBuffer, uvBuffer, normalBuffer);
			}
		}
	}

	private void PushStaticModel(Model model, Tile tile, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer)
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
		int len = PushGeometryToBuffers(model, vertexBuffer, uvBuffer, normalBuffer, tilePoint.getX(), tilePoint.getY(), false, staticSharedVertexMap);
		offset += len * 3;
		if (model.getFaceTextures() != null) {
			uvoffset += len * 3;
		}
	}

	public int PushDynamicModel(Model model, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer)
	{
		int triCount = PushGeometryToBuffers(model, vertexBuffer, uvBuffer, normalBuffer, 0, 0,false, dynamicSharedVertexMap);
		return triCount * 3;
	}

	// Map Tiles
	public int PushTerrainTile(Scene scene, SceneTilePaint tile, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer, int tileZ, int tileX, int tileY, int offsetX, int offsetY)
	{
		final int[][][] tileHeights = scene.getTileHeights();

		final int localX = offsetX;
		final int localY = offsetY;

		tileX += GpuExtendedPlugin.SCENE_OFFSET;
		tileY += GpuExtendedPlugin.SCENE_OFFSET;
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

		vertexBuffer.put(vertexAx, vertexAz, vertexAy, c3);
		vertexBuffer.put(vertexBx, vertexBz, vertexBy, c4);
		vertexBuffer.put(vertexCx, vertexCz, vertexCy, c2);

		vertexBuffer.put(vertexDx, vertexDz, vertexDy, c1);
		vertexBuffer.put(vertexCx, vertexCz, vertexCy, c2);
		vertexBuffer.put(vertexBx, vertexBz, vertexBy, c4);

		if (tile.getTexture() != -1)
		{
			int tex = tile.getTexture() + 1;
			uvBuffer.put(tex, vertexDx, vertexDz, vertexDy);
			uvBuffer.put(tex, vertexCx, vertexCz, vertexCy);
			uvBuffer.put(tex, vertexBx, vertexBz, vertexBy);

			uvBuffer.put(tex, vertexDx, vertexDz, vertexDy);
			uvBuffer.put(tex, vertexCx, vertexCz, vertexCy);
			uvBuffer.put(tex, vertexBx, vertexBz, vertexBy);
		}
		else
		{
			uvBuffer.put(0,0,0,0);
			uvBuffer.put(0,0,0,0);
			uvBuffer.put(0,0,0,0);
			uvBuffer.put(0,0,0,0);
		}

		int currentNormalBufferOffset = GetCurrentBufferArrayOffset(normalBuffer);
		Vector3 normA = CalculateBaseNormal(vertexAx, vertexAz, vertexAy, vertexBx, vertexBz, vertexBy, vertexCx, vertexCz, vertexCy);
		Vector3 normB = CalculateBaseNormal(vertexDx, vertexDz, vertexDy, vertexCx, vertexCz, vertexCy, vertexBx, vertexBz, vertexBy);
		normalBuffer.put(normA.x, normA.y, normA.z, 1);
		normalBuffer.put(normA.x, normA.y, normA.z, 1);
		normalBuffer.put(normA.x, normA.y, normA.z, 1);

		normalBuffer.put(normB.x, normB.y, normB.z, 1);
		normalBuffer.put(normB.x, normB.y, normB.z, 1);
		normalBuffer.put(normB.x, normB.y, normB.z, 1);

		int tx = tileX * Perspective.LOCAL_TILE_SIZE;
		int ty = tileY * Perspective.LOCAL_TILE_SIZE;
		terrainSharedVertexMap.put(new Vector3(vertexAx + tx, vertexAz, vertexAy + ty), currentNormalBufferOffset + 0*4);
		terrainSharedVertexMap.put(new Vector3(vertexBx + tx, vertexBz, vertexBy + ty), currentNormalBufferOffset + 1*4);
		terrainSharedVertexMap.put(new Vector3(vertexCx + tx, vertexCz, vertexCy + ty), currentNormalBufferOffset + 2*4);

		terrainSharedVertexMap.put(new Vector3(vertexDx + tx, vertexDz, vertexDy + ty), currentNormalBufferOffset + 3*4);
		terrainSharedVertexMap.put(new Vector3(vertexCx + tx, vertexCz, vertexCy + ty), currentNormalBufferOffset + 4*4);
		terrainSharedVertexMap.put(new Vector3(vertexBx + tx, vertexBz, vertexBy + ty), currentNormalBufferOffset + 5*4);

		return 6;
	}

	// Map tiles with extra geometry
	public int PushTerrainDetailedTile(SceneTileModel sceneTileModel, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer, int tileX, int tileY, int offsetX, int offsetZ)
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

		int baseX = tileX << Perspective.LOCAL_COORD_BITS;
		int baseY = tileY << Perspective.LOCAL_COORD_BITS;

		int len = 0;
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

			if (triangleTextures != null)
			{
				if (triangleTextures[i] != -1)
				{
					int tex = triangleTextures[i] + 1;
					uvBuffer.put(tex, offsetX, vertexYA, offsetZ);
					uvBuffer.put(tex, offsetX + 128, vertexYB, offsetZ);
					uvBuffer.put(tex, offsetX, vertexYC, offsetZ + 128);
				}
			}
			else
			{
				uvBuffer.put(0,0,0,0);
				uvBuffer.put(0,0,0,0);
				uvBuffer.put(0,0,0,0);
			}

			int currentNormalBufferOffset = GetCurrentBufferArrayOffset(normalBuffer);
			Vector3 norm = CalculateBaseNormal(vertexXA + offsetX, vertexYA, vertexZA + offsetZ, vertexXB + offsetX, vertexYB, vertexZB + offsetZ, vertexXC + offsetX, vertexYC, vertexZC + offsetZ);
			normalBuffer.put(norm.x, norm.y, norm.z, 1);
			normalBuffer.put(norm.x, norm.y, norm.z, 1);
			normalBuffer.put(norm.x, norm.y, norm.z, 1);

			int tx = (tileX + GpuExtendedPlugin.SCENE_OFFSET) * Perspective.LOCAL_TILE_SIZE;
			int tz = (tileY + GpuExtendedPlugin.SCENE_OFFSET) * Perspective.LOCAL_TILE_SIZE;
			terrainSharedVertexMap.put(new Vector3(vertexXA + tx, vertexYA, vertexZA + tz), currentNormalBufferOffset + 0*4);
			terrainSharedVertexMap.put(new Vector3(vertexXB + tx, vertexYB, vertexZB + tz), currentNormalBufferOffset + 1*4);
			terrainSharedVertexMap.put(new Vector3(vertexXC + tx, vertexYC, vertexZC + tz), currentNormalBufferOffset + 2*4);

			len += 3;
		}

		return len;
	}

	private int PushGeometryToBuffers(Model model, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer, int tileX, int tileY, boolean recomputeNormals, ArrayListMultimap<Vector3, Integer> sharedVertexMap)
	{
		final int triCount = Math.min(model.getFaceCount(), GpuExtendedPlugin.MAX_TRIANGLE);
		vertexBuffer.ensureCapacity(triCount * 12);
		normalBuffer.ensureCapacity(triCount * 12);
		uvBuffer.ensureCapacity(triCount * 12);

		final int[] vertexX = model.getVerticesX();
		final int[] vertexY = model.getVerticesY();
		final int[] vertexZ = model.getVerticesZ();

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

				uvBuffer.put(0, 0, 0, 0);
				uvBuffer.put(0, 0, 0, 0);
				uvBuffer.put(0, 0, 0, 0);

				normalBuffer.put(0, 0, 0, 0);
				normalBuffer.put(0, 0, 0, 0);
				normalBuffer.put(0, 0, 0, 0);
				continue;
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

			int packAlphaPriority = packAlphaPriority(faceTextures, transparencies, facePriorities, tri);

			int i0 = indices1[tri];
			int i1 = indices2[tri];
			int i2 = indices3[tri];

			// Push Vertex Positions
			vertexBuffer.put(vertexX[i0],  vertexY[i0], vertexZ[i0], packAlphaPriority | color1);
			vertexBuffer.put(vertexX[i1],  vertexY[i1], vertexZ[i1], packAlphaPriority | color2);
			vertexBuffer.put(vertexX[i2],  vertexY[i2], vertexZ[i2], packAlphaPriority | color3);

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
			}
			else
			{
				uvBuffer.put(0, 0, 0, 0);
				uvBuffer.put(0, 0, 0, 0);
				uvBuffer.put(0, 0, 0, 0);
			}

			int currentNormalBufferOffset = GetCurrentBufferArrayOffset(normalBuffer);
			if(recomputeNormals)
			{
				Vector3 triangleNormal = CalculateBaseNormal(vertexX[i0],  vertexY[i0], vertexZ[i0], vertexX[i1],  vertexY[i1], vertexZ[i1], vertexX[i2],  vertexY[i2], vertexZ[i2]);
				normalBuffer.put(triangleNormal.x, triangleNormal.y, triangleNormal.z, 1);
				normalBuffer.put(triangleNormal.x, triangleNormal.y, triangleNormal.z, 1);
				normalBuffer.put(triangleNormal.x, triangleNormal.y, triangleNormal.z, 1);

				int tx = (tileX + GpuExtendedPlugin.SCENE_OFFSET) * Perspective.LOCAL_TILE_SIZE;
				int tz = (tileY + GpuExtendedPlugin.SCENE_OFFSET) * Perspective.LOCAL_TILE_SIZE;
				sharedVertexMap.put(new Vector3(vertexX[i0] + tx, vertexY[i0], vertexZ[i0] + tz), currentNormalBufferOffset + 0*4);
				sharedVertexMap.put(new Vector3(vertexX[i1] + tx, vertexY[i1], vertexZ[i1] + tz), currentNormalBufferOffset + 1*4);
				sharedVertexMap.put(new Vector3(vertexX[i2] + tx, vertexY[i2], vertexZ[i2] + tz), currentNormalBufferOffset + 2*4);
			}
			else
			{
				// Push Normal Directions
				if (normalX != null)
				{
					normalBuffer.put(normalX[i0],  normalY[i0], normalZ[i0], 0);
					normalBuffer.put(normalX[i1],  normalY[i1], normalZ[i1], 0);
					normalBuffer.put(normalX[i2],  normalY[i2], normalZ[i2], 0);
				}
				else
				{
					normalBuffer.put(0, 0, 0, 0);
					normalBuffer.put(0, 0, 0, 0);
					normalBuffer.put(0, 0, 0, 0);
				}
			}
		}

		return triCount;
	}

	public Vector3 CalculateBaseNormal(float p0x, float p0y, float p0z, float p1x, float p1y, float p1z, float p2x, float p2y, float p2z)
	{
		Vector3 edge1 = new Vector3(p1x - p0x, p1y - p0y, p1z - p0z);
		Vector3 edge2 = new Vector3(p2x - p0x, p2y - p0y, p2z - p0z);
		Vector3 norm = Vector3.Cross(edge1, edge2).Normalize();

		return norm;
	}

	public void ComputeSmoothNormals(ArrayListMultimap<Vector3, Integer> sharedVertexPositionMap)
	{
//		for (Collection<Vertex> sharedVerts : sharedVertexPositionMap.asMap().values())
//		{
//			if (sharedVerts.isEmpty()) continue;
//
//			Vector3 avgNormal = new Vector3(0, 0, 0);
//			for (Vertex vertex : sharedVerts)
//			{
//				avgNormal.x += vertex.normal.x;
//				avgNormal.y += vertex.normal.y;
//				avgNormal.z += vertex.normal.z;
//			}
//
//			avgNormal.x /= sharedVerts.size();
//			avgNormal.y /= sharedVerts.size();
//			avgNormal.z /= sharedVerts.size();
//
//			for (Vertex vertex : sharedVerts)
//			{
//				vertex.SetNormal(new Vector3(avgNormal.x, avgNormal.y, avgNormal.z));
//			}
//		}
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

	// remove tiles from the scene that are outside the current region
	private void PrepareScene(Scene scene)
	{
		if (scene.isInstance() || !gpuConfig.hideUnrelatedMaps())
		{
			return;
		}

		int baseX = scene.getBaseX() / 8;
		int baseY = scene.getBaseY() / 8;
		int centerX = baseX + 6;
		int centerY = baseY + 6;
		int centerId = regions.getRegionId(centerX, centerY);

		int r = Constants.EXTENDED_SCENE_SIZE / 16;
		for (int offx = -r; offx <= r; ++offx)
		{
			for (int offy = -r; offy <= r; ++offy)
			{
				int cx = centerX + offx;
				int cy = centerY + offy;
				int id = regions.getRegionId(cx, cy);
				if (id != centerId)
				{
					removeChunk(scene, cx, cy);
				}
			}
		}
	}

	private static void removeChunk(Scene scene, int cx, int cy)
	{
		int wx = cx * 8;
		int wy = cy * 8;
		int sx = wx - scene.getBaseX();
		int sy = wy - scene.getBaseY();
		int cmsx = sx + GpuExtendedPlugin.SCENE_OFFSET;
		int cmsy = sy + GpuExtendedPlugin.SCENE_OFFSET;
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

	private int GetCurrentBufferArrayOffset(GpuIntBuffer buffer)
	{
		if(!buffer.getBuffer().hasArray())
			return 0;
		else
			return buffer.getBuffer().arrayOffset();
	}

	private int GetCurrentBufferArrayOffset(GpuFloatBuffer buffer)
	{
		if(!buffer.getBuffer().hasArray())
			return 0;
		else
			return buffer.getBuffer().arrayOffset();
	}
}
