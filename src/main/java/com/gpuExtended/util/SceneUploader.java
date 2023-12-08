
package com.gpuExtended.util;

import com.google.common.base.Stopwatch;
import java.io.IOException;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.gpuExtended.GpuExtendedConfig;
import com.gpuExtended.GpuExtendedPlugin;
import com.gpuExtended.rendering.Triangle;
import com.gpuExtended.rendering.Vector3;
import com.gpuExtended.rendering.Vector4;
import com.gpuExtended.rendering.Vertex;
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
		++sceneId;
		offset = 0;
		uvoffset = 0;
		uniqueModels = 0;
		vertexBuffer.clear();
		normalBuffer.clear();
		uvBuffer.clear();

		Stopwatch stopwatch = Stopwatch.createStarted();
		prepare(scene);
		stopwatch.stop();
		log.debug("Scene preparation time: {}", stopwatch);

		stopwatch = Stopwatch.createStarted();
		for (int z = 0; z < Constants.MAX_Z; ++z)
		{
			for (int x = 0; x < Constants.EXTENDED_SCENE_SIZE; ++x)
			{
				for (int y = 0; y < Constants.EXTENDED_SCENE_SIZE; ++y)
				{
					Tile tile = scene.getExtendedTiles()[z][x][y];
					if (tile != null)
					{
						upload(scene, tile, vertexBuffer, uvBuffer, normalBuffer);
					}
				}
			}
		}

		stopwatch.stop();
		log.debug("Scene upload time: {} unique models: {} length: {}KB", stopwatch, uniqueModels, (offset * 16) / 1024);
	}

	private void upload(Scene scene, Tile tile, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer)
	{
		Tile bridge = tile.getBridge();
		if (bridge != null)
		{
			upload(scene, bridge, vertexBuffer, uvBuffer, normalBuffer);
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
			int len = uploadMapTile(scene, sceneTilePaint,
				tile.getRenderLevel(), tilePoint.getX(), tilePoint.getY(),
				vertexBuffer, uvBuffer, normalBuffer,
				0, 0, false);
			sceneTilePaint.setBufferLen(len);
			offset += len;
			if (sceneTilePaint.getTexture() != -1)
			{
				uvoffset += len;
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
			Point tilePoint = tile.getSceneLocation();
			int len = upload(sceneTileModel,
				tilePoint.getX(), tilePoint.getY(),
				0, 0,
				vertexBuffer, uvBuffer, normalBuffer, false);
			sceneTileModel.setBufferLen(len);
			offset += len;
			if (sceneTileModel.getTriangleTextureId() != null)
			{
				uvoffset += len;
			}
		}

		WallObject wallObject = tile.getWallObject();
		if (wallObject != null)
		{
			Renderable renderable1 = wallObject.getRenderable1();
			if (renderable1 instanceof Model)
			{
				uploadSceneModel((Model) renderable1, vertexBuffer, uvBuffer, normalBuffer);
			}

			Renderable renderable2 = wallObject.getRenderable2();
			if (renderable2 instanceof Model)
			{
				uploadSceneModel((Model) renderable2, vertexBuffer, uvBuffer, normalBuffer);
			}
		}

		GroundObject groundObject = tile.getGroundObject();
		if (groundObject != null)
		{
			Renderable renderable = groundObject.getRenderable();
			if (renderable instanceof Model)
			{
				uploadSceneModel((Model) renderable, vertexBuffer, uvBuffer, normalBuffer);
			}
		}

		DecorativeObject decorativeObject = tile.getDecorativeObject();
		if (decorativeObject != null)
		{
			Renderable renderable = decorativeObject.getRenderable();
			if (renderable instanceof Model)
			{
				uploadSceneModel((Model) renderable, vertexBuffer, uvBuffer, normalBuffer);
			}

			Renderable renderable2 = decorativeObject.getRenderable2();
			if (renderable2 instanceof Model)
			{
				uploadSceneModel((Model) renderable2, vertexBuffer, uvBuffer, normalBuffer);
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
				uploadSceneModel((Model) gameObject.getRenderable(), vertexBuffer, uvBuffer, normalBuffer);
			}
		}
	}

	// Map Tiles
	public int uploadMapTile(Scene scene, SceneTilePaint tile, int tileZ, int tileX, int tileY, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer,
					  int offsetX, int offsetY, boolean padUvs)
	{
		final int[][][] tileHeights = scene.getTileHeights();

		final int localX = offsetX;
		final int localY = offsetY;

		tileX += GpuExtendedPlugin.SCENE_OFFSET;
		tileY += GpuExtendedPlugin.SCENE_OFFSET;
		int swHeight = tileHeights[tileZ][tileX][tileY];
		int seHeight = tileHeights[tileZ][tileX + 1][tileY];
		int neHeight = tileHeights[tileZ][tileX + 1][tileY + 1];
		int nwHeight = tileHeights[tileZ][tileX][tileY + 1];

		final int neColor = tile.getNeColor();
		final int nwColor = tile.getNwColor();
		final int seColor = tile.getSeColor();
		final int swColor = tile.getSwColor();

		if (neColor == 12345678)
		{
			return 0;
		}

		vertexBuffer.ensureCapacity(24);
		uvBuffer.ensureCapacity(24);
		normalBuffer.ensureCapacity(24);

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
		Triangle triangle = new Triangle(v0, v1, v2);
		Triangle triangle1 = new Triangle(v3, v2, v1);
		Vector3 norm = triangle.GetNormal();
		Vector3 norm1 = triangle1.GetNormal();

		// Face 1
		vertexBuffer.put((int)v0.x, (int)v0.y, (int)v0.z, c3);
		vertexBuffer.put((int)v1.x, (int)v1.y, (int)v1.z, c4);
		vertexBuffer.put((int)v2.x, (int)v2.y, (int)v2.z, c2);

		// Face 2
		vertexBuffer.put((int)v3.x, (int)v3.y, (int)v3.z, c1);
		vertexBuffer.put((int)v2.x, (int)v2.y, (int)v2.z, c2);
		vertexBuffer.put((int)v1.x, (int)v1.y, (int)v1.z, c4);

		normalBuffer.put(norm.x, norm.x, norm.x, 1);
		normalBuffer.put(norm.y, norm.y, norm.y, 1);
		normalBuffer.put(norm.z, norm.z, norm.z, 1);

		normalBuffer.put(norm1.x, norm1.x, norm1.x, 1);
		normalBuffer.put(norm1.y, norm1.y, norm1.y, 1);
		normalBuffer.put(norm1.z, norm1.z, norm1.z, 1);

		if (padUvs || tile.getTexture() != -1)
		{
			int tex = tile.getTexture() + 1;
			uvBuffer.put(tex, vertexDx, vertexDz, vertexDy);
			uvBuffer.put(tex, vertexCx, vertexCz, vertexCy);
			uvBuffer.put(tex, vertexBx, vertexBz, vertexBy);

			uvBuffer.put(tex, vertexDx, vertexDz, vertexDy);
			uvBuffer.put(tex, vertexCx, vertexCz, vertexCy);
			uvBuffer.put(tex, vertexBx, vertexBz, vertexBy);
		}

		return 6;
	}

	// Overlays
	public int upload(SceneTileModel sceneTileModel, int tileX, int tileY, int offsetX, int offsetZ,
					  GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer, boolean padUvs)
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
		uvBuffer.ensureCapacity(faceCount * 12);
		normalBuffer.ensureCapacity(faceCount * 12);

		int baseX = tileX << Perspective.LOCAL_COORD_BITS;
		int baseY = tileY << Perspective.LOCAL_COORD_BITS;

		int cnt = 0;
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

			cnt += 3;

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

			normalBuffer.put(0 + offsetX,0,1 + offsetZ,0);
			normalBuffer.put(0 + offsetX,1,0 + offsetZ,0);
			normalBuffer.put(1 + offsetX,0,0 + offsetZ,0);

			if (padUvs || triangleTextures != null)
			{
				if (triangleTextures != null && triangleTextures[i] != -1)
				{
					int tex = triangleTextures[i] + 1;
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
		}

		return cnt;
	}

	private void uploadSceneModel(Model model, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer)
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

		int len = pushModel(model, vertexBuffer, uvBuffer, normalBuffer);

		offset += len;
		if (model.getFaceTextures() != null)
		{
			uvoffset += len;
		}
	}

	public int pushModel(Model model, GpuIntBuffer vertexBuffer, GpuFloatBuffer uvBuffer, GpuFloatBuffer normalBuffer)
	{
		final int triangleCount = Math.min(model.getFaceCount(), GpuExtendedPlugin.MAX_TRIANGLE);

		vertexBuffer.ensureCapacity(triangleCount * 12);
		uvBuffer.ensureCapacity(triangleCount * 12);
		normalBuffer.ensureCapacity(triangleCount * 12);

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

		int len = 0;
		for (int tri = 0; tri < triangleCount; tri++)
		{
			int color1 = color1s[tri];
			int color2 = color2s[tri];
			int color3 = color3s[tri];

			if (color3 == -1)
			{
				color2 = color3 = color1;
			}
			else if (color3 == -2)
			{
				vertexBuffer.put(0, 0, 0, 0);
				vertexBuffer.put(0, 0, 0, 0);
				vertexBuffer.put(0, 0, 0, 0);

				normalBuffer.put(0,0,0,0);
				normalBuffer.put(0,0,0,0);
				normalBuffer.put(0,0,0,0);

				if (faceTextures != null)
				{
					uvBuffer.put(0, 0, 0, 0);
					uvBuffer.put(0, 0, 0, 0);
					uvBuffer.put(0, 0, 0, 0);
				}

				len += 3;
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

			int v0 = indices1[tri];
			int v1 = indices2[tri];
			int v2 = indices3[tri];

			vertexBuffer.put(vertexX[v0], vertexY[v0], vertexZ[v0], packAlphaPriority | color1);
			vertexBuffer.put(vertexX[v1], vertexY[v1], vertexZ[v1], packAlphaPriority | color2);
			vertexBuffer.put(vertexX[v2], vertexY[v2], vertexZ[v2], packAlphaPriority | color3);

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
						texA = v0;
						texB = v1;
						texC = v2;
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

			if (normalX != null || normalY != null || normalZ != null)
			{
				normalBuffer.put(vertexX[v0], vertexY[v0], vertexZ[v0], 1);
				normalBuffer.put(vertexX[v1], vertexY[v1], vertexZ[v1], 1);
				normalBuffer.put(vertexX[v2], vertexY[v2], vertexZ[v2], 1);
			}

			len += 3;
		}

		//		System.out.println("------ MODEL INFORMATION ------");
		//		System.out.println("Face Count: " + model.getFaceCount());
		//		System.out.println("Vert Count: " + (vertexX.length + vertexY.length + vertexZ.length));
		//		System.out.println("Norm Count: " + (normalX.length + normalY.length + normalZ.length));

		return len;
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
	private void prepare(Scene scene)
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
}
