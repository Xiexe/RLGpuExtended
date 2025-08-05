package com.gpuExtended.rendering.passes;

import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;

public interface IPassBase {
    void Init();

    void Dispose();

    // OpenGL render logic
    void OnPreRenderFrame();

    void OnRenderFrame();

    void OnPostRenderFrame();

    // Scene loading callbacks from runelite client
    void OnPreLoadScene(Scene scene);

    void OnSceneLoadStart(Scene scene);

    void OnSceneLoadFinished(Scene scene);

    // Draw call callbacks from runelite client
    void OnPreDrawScene();

    void OnDrawScene();

    void OnPostDrawScene();

    // Draw call for simple tiles. 6 vertices, 2 triangles.
    void OnDrawSceneTile(Scene scene, SceneTilePaint paint, int plane, int tileX, int tileY);

    // Draw call for complex tiles, could have many vertices and triangles. (like those with paths on them)
    void OnDrawSceneTileModel(Scene scene, SceneTileModel model, int tileX, int tileY);

    // Draw call for general other models.
    void OnDrawModel(Projection projection, Scene scene, Renderable renderable, int orientation, int x, int y, int z, long hash);

    void OnGameStateChanged(GameStateChanged gameStateChanged);
}
