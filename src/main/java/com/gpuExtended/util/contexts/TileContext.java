package com.gpuExtended.util.contexts;

import net.runelite.api.*;

public class TileContext {
    public final Scene scene;
    public final Tile tile;
    public final SceneTilePaint tilePaint;
    public final SceneTileModel tileModel;
    public final int x, y, plane;

    public TileContext(Scene scene, Tile tile) {
        Point tilePoint = tile.getSceneLocation();

        this.scene = scene;
        this.tile = tile;
        this.tilePaint = tile.getSceneTilePaint();
        this.tileModel = tile.getSceneTileModel();
        this.x = tilePoint.getX();
        this.y = tilePoint.getY();
        this.plane = tile.getRenderLevel();
    }
}
