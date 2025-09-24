package com.example.game3d_opengl.game.stages.main;


import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.rendering.util3d.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.sub;
import static java.lang.Math.abs;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.stage_api.Stage;
import com.example.game3d_opengl.game.terrain_structures.TerrainLineWithSpikeRect;
import com.example.game3d_opengl.game.terrain_structures.TerrainStairs;
import com.example.game3d_opengl.game.track_elements.Potion;
import com.example.game3d_opengl.game.track_elements.spike.DeathSpike;
import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.Player;
import com.example.game3d_opengl.game.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain_api.main.Tile;

/**
 * Demonstration of a gameplay stage that:
 * - Spawns multiple terrain segments via your Terrain class
 * - Renders them with slope-based coloring or as a "guardian" tile
 */
public class GameplayStage extends Stage {


    public GameplayStage(MyGLRenderer.StageManager stageManager){
        super(stageManager);
    }

    @Override
    public void onTouchDown(float x, float y) {
    }

    @Override
    public void onTouchUp(float x, float y) {
    }

    @Override
    public void onTouchMove(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        if(abs(dx) > abs(dy) && abs(dx) > 2) {
            player.rotDirOnTouch(dx);
        }
    }

    Terrain terrain;

    private Camera camera;
    private int frameCounter = 0; // throttled logging counter
    private final FColor colorTheme = CLR(0.7f,0,0,1);
    private Player player;


    public static boolean __DEBUG_IS_TERRAIN_GENERATING = false;

    @Override
    protected void initScene(Context context, int screenWidth, int screenHeight) {
        // --- Camera Setup ---
        this.camera = new Camera();

        Camera.setGlobalScreenSize(screenWidth, screenHeight);
        this.camera.set(0f, 0f, 3f, // eye pos
                0f, 0f, 0f, // look at
                0f, 1f, 0f ); // which way is up
        camera.setProjectionAsScreen();

        AssetManager assetManager = context.getAssets();

        Potion.LOAD_POTION_ASSETS(assetManager);
        DeathSpike.LOAD_DEATHSPIKE_ASSETS();

        Player.LOAD_PLAYER_ASSETS(assetManager);
        player = Player.createPlayer();

        float segWidth = 3.2f, segLength = 1.4f;
        this.terrain = new Terrain(2000,6,
                V3(player.getX(), player.getY() - 3f, player.getZ()),
                segWidth,
                segLength,
                1f
        );
        terrain.enqueueStructure(new TerrainLineWithSpikeRect(30));
        terrain.enqueueStructure(new TerrainLineWithSpikeRect(30));
        terrain.enqueueStructure(new TerrainStairs(100,4,2, PI/6,-1f));
        terrain.enqueueStructure(new TerrainLineWithSpikeRect(30));
        terrain.enqueueStructure(new TerrainStairs(30,4,2, PI/6,-1f));

        terrain.generateChunks(-1);

        System.out.println("GAMEPLAY STAGE INIT");

    }

    @Override
    public void updateThenDraw(float dt) {

        terrain.removeOldTerrainElements(player.getNearestTileId());
        if (terrain.getTileCount() < 400) {
            terrain.enqueueStructure(new TerrainLineWithSpikeRect(30));
            terrain.enqueueStructure(new TerrainLineWithSpikeRect(30));
            terrain.enqueueStructure(new TerrainStairs(50,4,2, PI/6,-1f));
            terrain.enqueueStructure(new TerrainStairs(30,4,2, PI/6,-1f));

        }
        if (terrain.getTileCount() < 300) {
            GameplayStage.__DEBUG_IS_TERRAIN_GENERATING = true;
            terrain.generateChunks(1);
        }

        for(int i=0;i<terrain.getTileCount();++i){
            Tile tile = terrain.getTile(i);
            if(player.collidesTile(tile)){
                player.setFooting(tile);
            }
        }


        // Includes player interactions: footing, distances to player, addon collisions
        player.updateBeforeDraw(dt);
        terrain.updateBeforeDraw(dt);

        Vector3D camPos = V3(player.getX(), player.getY() + 0.75f, player.getZ())
                .sub(player.getDir().withLen(3.8f));
        camera.updateEyePos(camPos);
        camera.updateLookPos(camPos.add(player.getDir().setY(0.0f)));

        float[] vpMatrix = camera.getViewProjectionMatrix();

        player.draw(vpMatrix);
        terrain.draw(colorTheme, vpMatrix);

        player.updateAfterDraw(dt);
        terrain.updateAfterDraw(dt);

        if ((frameCounter++ & 127) == 0) {
            Log.d("Perf", "dt=" + dt + " visible=" + terrain.getTileCount() + ","+ terrain.getAddonCount());
        }


    }



    @Override
    public void onSwitch() {
        System.out.println("SWITCHING FROM GAMEPLAY");
    }

    @Override
    public void onReturn() {
        System.out.println("RETURNING TO GAMEPLAY");
    }

    @Override
    public void onPause() {

    }

    @Override
    public void onResume() {

    }

    @Override
    public void onClose() {
        player.cleanupGPUResourcesRecursivelyOnContextLoss();
        terrain.cleanupGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        player.reloadGPUResourcesRecursivelyOnContextLoss();
        terrain.reloadGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {}
}