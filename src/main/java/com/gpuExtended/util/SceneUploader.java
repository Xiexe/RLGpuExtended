
package com.gpuExtended.util;

import com.google.common.base.Stopwatch;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
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

	private int terrainTileCount;

	private ArrayList<Mesh> staticMeshes;
	private Mesh terrainMesh;

	@Inject
	SceneUploader(
		Client client,
		GpuExtendedConfig config
	)
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

	public void upload(Scene scene, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer)
	{
		Stopwatch stopwatch = Stopwatch.createStarted();
		sceneId++;
		offset = 0;
		uvoffset = 0;
		uniqueModels = 0;
		terrainTileCount = 0;
		terrainMesh = new Mesh();
		staticMeshes = new ArrayList<Mesh>();
		vertexBuffer.clear();
		normalBuffer.clear();
		uvBuffer.clear();
		PrepareScene(scene);

		stopwatch.stop();
		log.debug("Scene preparation time: {}", stopwatch);

		stopwatch = Stopwatch.createStarted();

		PopulateTerrainMesh(scene);
		PopulateStaticMeshes(scene);
		terrainMesh.PushToBuffers(vertexBuffer, uvBuffer, normalBuffer);
		for (Mesh mesh : staticMeshes)
		{
			// We can skip computing normals for these meshes for now as they're flat shaded on the gpu.
			mesh.PushToBuffers(vertexBuffer, uvBuffer, normalBuffer);
		}

		stopwatch.stop();
		log.debug("Scene upload time: {} unique models: {} length: {}KB", stopwatch, uniqueModels, (offset * 16) / 1024);
	}

	private void PopulateTerrainMesh(Scene scene)
	{
		ArrayListMultimap<Vector3, Vertex> vertexMultimap = ArrayListMultimap.create();
		int[][][] tileHeights = scene.getTileHeights();
		for (int z = 0; z < Constants.MAX_Z; ++z)
		{
			for (int x = 0; x < Constants.EXTENDED_SCENE_SIZE; ++x)
			{
				for (int y = 0; y < Constants.EXTENDED_SCENE_SIZE; ++y)
				{
					Tile tile = scene.getExtendedTiles()[z][x][y];
					if (tile != null)
					{
						GenerateTerrainMesh(scene, tile, tileHeights, vertexMultimap);
					}
				}
			}
		}

		// TODO:: blend color
//		for(Collection<Vertex> sharedVerts : vertexMultimap.asMap().values())
//		{
//			Vector3 avgNormal = new Vector3(0,0,0);
//			for (Vertex vertex : sharedVerts)
//			{
//				avgNormal.x += vertex.normal.x;
//				avgNormal.y += vertex.normal.y;
//				avgNormal.z += vertex.normal.z;
//			}
//			avgNormal.x /= sharedVerts.size();
//			avgNormal.y /= sharedVerts.size();
//			avgNormal.z /= sharedVerts.size();
//
//			for (Vertex vertex : sharedVerts)
//			{
//				vertex.SetNormal(new Vector4(avgNormal.x, avgNormal.y, avgNormal.z, 0));
//			}
//		}
	}

	private void PopulateStaticMeshes(Scene scene)
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
						GenerateStaticMeshes(tile);
					}
				}
			}
		}
	}

	private void GenerateTerrainMesh(Scene scene, Tile tile, int[][][] tileHeights, ArrayListMultimap<Vector3, Vertex> vertexMultimap)
	{
		Tile bridge = tile.getBridge();
		if (bridge != null)
		{   // draw the tile underneath the bridge.
			GenerateTerrainMesh(scene, bridge, tileHeights, vertexMultimap);
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

			Point tilePoint = tile.getSceneLocation();
			int len = AddWorldTileMesh(
				scene,
				sceneTilePaint,
				tile.getRenderLevel(),
				tilePoint.getX(),
				tilePoint.getY(),
				0,
				0,
				vertexMultimap
			);

			sceneTilePaint.setBufferLen(len);
			offset += len;
			uvoffset += len;
			terrainTileCount ++;
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

			Point tilePoint = tile.getSceneLocation();
			int len = AddWorldTileMeshDetailed(
				sceneTileModel,
				tilePoint.getX(),
				tilePoint.getY(),
				0,
				0,
				vertexMultimap
			);

			sceneTileModel.setBufferLen(len);
			offset += len;
			uvoffset += len;
			terrainTileCount ++;
		}
	}

	private void GenerateStaticMeshes(Tile tile)
	{
		WallObject wallObject = tile.getWallObject();
		if (wallObject != null)
		{
			Renderable renderable1 = wallObject.getRenderable1();
			if (renderable1 instanceof Model)
			{
				AddStaticModel((Model) renderable1);
			}

			Renderable renderable2 = wallObject.getRenderable2();
			if (renderable2 instanceof Model)
			{
				AddStaticModel((Model) renderable2);
			}
		}

		GroundObject groundObject = tile.getGroundObject();
		if (groundObject != null)
		{
			Renderable renderable = groundObject.getRenderable();
			if (renderable instanceof Model)
			{
				AddStaticModel((Model) renderable);
			}
		}

		DecorativeObject decorativeObject = tile.getDecorativeObject();
		if (decorativeObject != null)
		{
			Renderable renderable = decorativeObject.getRenderable();
			if (renderable instanceof Model)
			{
				AddStaticModel((Model) renderable);
			}

			Renderable renderable2 = decorativeObject.getRenderable2();
			if (renderable2 instanceof Model)
			{
				AddStaticModel((Model) renderable2);
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
				AddStaticModel((Model) gameObject.getRenderable());
			}
		}
	}

	// Map Tiles
	public int AddWorldTileMesh(
		Scene scene,
		SceneTilePaint tile,
		int tileZ,
		int tileX,
		int tileY,
		int offsetX,
		int offsetY,
		ArrayListMultimap<Vector3, Vertex> vertexMultimap
	)
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

		Vertex v0 = new Vertex(vertexAx, vertexAz, vertexAy);
		Vertex v1 = new Vertex(vertexBx, vertexBz, vertexBy);
		Vertex v2 = new Vertex(vertexCx, vertexCz, vertexCy);
		Vertex v3 = new Vertex(vertexDx, vertexDz, vertexDy);

		v0.SetColor(c3);
		v1.SetColor(c4);
		v2.SetColor(c2);
		v3.SetColor(c1);

		if (tile.getTexture() != -1)
		{
			int tex = tile.getTexture() + 1;
			v0.SetUv(tex, vertexDx, vertexDz, vertexDy);
			v1.SetUv(tex, vertexCx, vertexCz, vertexCy);
			v2.SetUv(tex, vertexBx, vertexBz, vertexBy);
			v3.SetUv(tex, vertexDx, vertexDz, vertexDy);
		}

		Triangle triangle = new Triangle(v0, v1, v2);
		Triangle triangle1 = new Triangle(v3, v2, v1);
		triangle.CalculateNormal();
		triangle1.CalculateNormal();

		int tx = tileX * Perspective.LOCAL_TILE_SIZE;
		int ty = tileY * Perspective.LOCAL_TILE_SIZE;
		vertexMultimap.put(new Vector3(vertexAx + tx, vertexAz, vertexAy + ty), v0);
		vertexMultimap.put(new Vector3(vertexBx + tx, vertexBz, vertexBy + ty), v1);
		vertexMultimap.put(new Vector3(vertexCx + tx, vertexCz, vertexCy + ty), v2);
		vertexMultimap.put(new Vector3(vertexDx + tx, vertexDz, vertexDy + ty), v3);

		v0.SetNormal(new Vector4(vertexAx + tx, vertexAz, vertexAy + ty, 0));
		v1.SetNormal(new Vector4(vertexBx + tx, vertexBz, vertexBy + ty, 0));
		v2.SetNormal(new Vector4(vertexCx + tx, vertexCz, vertexCy + ty, 0));
		v3.SetNormal(new Vector4(vertexDx + tx, vertexDz, vertexDy + ty, 0));

		terrainMesh.AddTriangle(triangle);
		terrainMesh.AddTriangle(triangle1);

		return 6;
	}

	// Map tiles with extra geometry
	public int AddWorldTileMeshDetailed(
		SceneTileModel sceneTileModel,
		int tileX,
		int tileY,
		int offsetX,
		int offsetZ,
		ArrayListMultimap<Vector3, Vertex> vertexMultimap
	)
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

			Vertex v0 = new Vertex(vertexXA + offsetX, vertexYA, vertexZA + offsetZ);
			Vertex v1 = new Vertex(vertexXB + offsetX, vertexYB, vertexZB + offsetZ);
			Vertex v2 = new Vertex(vertexXC + offsetX, vertexYC, vertexZC + offsetZ);
			v0.SetColor(colorA);
			v1.SetColor(colorB);
			v2.SetColor(colorC);

			if (triangleTextures != null)
			{
				if (triangleTextures[i] != -1)
				{
					int tex = triangleTextures[i] + 1;
					v0.SetUv(tex, offsetX, vertexYA, offsetZ);
					v1.SetUv(tex, offsetX + 128, vertexYB, offsetZ);
					v2.SetUv(tex, offsetX, vertexYC, offsetZ + 128);
				}
			}

			Triangle t = new Triangle(v0, v1, v2);
			t.CalculateNormal();

			int tx = (tileX * Perspective.LOCAL_TILE_SIZE);
			int tz = (tileY * Perspective.LOCAL_TILE_SIZE);
			vertexMultimap.put(new Vector3(vertexXA + tx + baseX, vertexYA, vertexZA + tz), v0);
			vertexMultimap.put(new Vector3(vertexXB + tx + baseX, vertexYB, vertexZB + tz), v1);
			vertexMultimap.put(new Vector3(vertexXC + tx + baseX, vertexYC, vertexZC + tz), v2);

			v0.SetNormal(new Vector4(vertexX[triangleA] + tx, vertexYA, vertexZ[triangleA] + tz, 0));
			v1.SetNormal(new Vector4(vertexX[triangleB] + tx, vertexYB, vertexZ[triangleB] + tz, 0));
			v2.SetNormal(new Vector4(vertexX[triangleC] + tx, vertexYC, vertexZ[triangleC] + tz, 0));

			terrainMesh.AddTriangle(t);

			len += 3;
		}

		return len;
	}

	private void AddStaticModel(
		Model model
	)
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

		int len = AddStaticMesh(model);

		offset += len;
		uvoffset += len;
	}

	public int AddStaticMesh(Model model)
	{
		Mesh mesh = GetMesh(model, 1);
		staticMeshes.add(mesh);

		return mesh.triangles.size() * 3;
	}

	public int PushDynamicMesh(
		Model model,
		GpuIntBuffer vertexBuffer,
		GpuFloatBuffer uvBuffer,
		GpuFloatBuffer normalBuffer
	)
	{
		Mesh mesh = GetMesh(model, 0);
		mesh.PushToBuffers(vertexBuffer, uvBuffer, normalBuffer);

		return mesh.triangles.size() * 3;
	}

	private Mesh GetMesh(Model model, int useFlatNormals)
	{
		Mesh thisMesh = new Mesh();
		final int triCount = Math.min(model.getFaceCount(), GpuExtendedPlugin.MAX_TRIANGLE);

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
			Vertex v0;
			Vertex v1;
			Vertex v2;

			int color1 = color1s[tri];
			int color2 = color2s[tri];
			int color3 = color3s[tri];

			if (color3 == -1) // Model only has one color.
			{
				color2 = color3 = color1;
			}
			else if (color3 == -2) // Model should be skipped. Pad buffer.
			{
				Vertex empty = Vertex.GetEmptyVertex();
				Triangle t = new Triangle(empty, empty, empty);
				thisMesh.AddTriangle(t);
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
			v0 = new Vertex(vertexX[i0], vertexY[i0], vertexZ[i0]);
			v1 = new Vertex(vertexX[i1], vertexY[i1], vertexZ[i1]);
			v2 = new Vertex(vertexX[i2], vertexY[i2], vertexZ[i2]);

			v0.SetColor(packAlphaPriority | color1);
			v1.SetColor(packAlphaPriority | color2);
			v2.SetColor(packAlphaPriority | color3);

			if (normalX != null) // The model does have normals
			{
				v0.SetNormal(new Vector4(normalX[i0], normalY[i0], normalZ[i0], useFlatNormals));
				v1.SetNormal(new Vector4(normalX[i1], normalY[i1], normalZ[i1], useFlatNormals));
				v2.SetNormal(new Vector4(normalX[i2], normalY[i2], normalZ[i2], useFlatNormals));
			}

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
					v0.SetUv(texture, vertexX[texA], vertexY[texA], vertexZ[texA]);
					v1.SetUv(texture, vertexX[texB], vertexY[texB], vertexZ[texB]);
					v2.SetUv(texture, vertexX[texC], vertexY[texC], vertexZ[texC]);
				}
			}

			Triangle t = new Triangle(v0, v1, v2);
			thisMesh.AddTriangle(t);
		}

		thisMesh.flatNormals = (useFlatNormals == 1);
		return thisMesh;
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

	int AddHSL (int a, int b) {
		int aA = (a >> 24 & 0xff);
		int aR = ((a & 0xff0000) >> 16);
		int aG = ((a & 0xff00) >> 8);
		int aB = (a & 0xff);

		int bA = (b >> 24 & 0xff);
		int bR = ((b & 0xff0000) >> 16);
		int bG = ((b & 0xff00) >> 8);
		int bB = (b & 0xff);

		int A = aA + bA;
		int R = aR + bR;
		int G = aG + bG;
		int B = aB + bB;

		return A << 24 | R << 16 | G << 8 | B;
	}

	int DivideHSL (int a, int divisor) {
		int aA = (a >> 24 & 0xff);
		int aR = ((a & 0xff0000) >> 16);
		int aG = ((a & 0xff00) >> 8);
		int aB = (a & 0xff);

		int A = aA / divisor;
		int R = aR / divisor;
		int G = aG / divisor;
		int B = aB / divisor;

		return A << 24 | R << 16 | G << 8 | B;
	}
}
