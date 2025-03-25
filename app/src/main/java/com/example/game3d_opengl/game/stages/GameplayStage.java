package com.example.game3d_opengl.game.stages;


import static com.example.game3d_opengl.engine.util3d.FColor.CLR;
import static com.example.game3d_opengl.engine.util3d.GameMath.PI;
import static com.example.game3d_opengl.engine.util3d.vector.Vector3D.V3;
import static java.lang.Math.abs;

import android.content.Context;
import android.content.res.AssetManager;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.engine.object3d.Camera;
import com.example.game3d_opengl.engine.util3d.FColor;
import com.example.game3d_opengl.engine.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.Player;
import com.example.game3d_opengl.game.terrain.Terrain;
import com.example.game3d_opengl.game.terrain.Tile;
import com.example.game3d_opengl.game.terrain.structures.Terrain2DCurve;
import com.example.game3d_opengl.game.terrain.structures.TerrainCurve;
import com.example.game3d_opengl.game.terrain.structures.TerrainLine;

/**
 * Demonstration of a gameplay stage that:
 * - Spawns multiple terrain segments via your Terrain class
 * - Renders them with slope-based coloring or as a "guardian" tile
 */
public class GameplayStage implements Stage {

    private final MyGLRenderer.StageManager stageManager;
    private Camera camera;

    private FColor colorTheme = CLR(0.7f,0,0,1);

    private Player player;

    public GameplayStage(MyGLRenderer.StageManager stageManager){
        this.stageManager = stageManager;
        this.camera = new Camera();
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
        //playerMove = rotY(playerMove,dx * 0.01f);
    }

    Terrain terrain;

    @Override
    public void initScene(Context context, int screenWidth, int screenHeight) {
        // --- Camera Setup ---
        Camera.setGlobalScreenSize(screenWidth, screenHeight);
        this.camera.set(0f, 0f, 3f, // eye pos
                0f, 0f, 0f, // look at
                0f, 1f, 0f ); // which way is up
        camera.setProjectionAsScreen();

        AssetManager assetManager = context.getAssets();
        Player.LOAD_PLAYER_ASSETS(assetManager);
        player = new Player();
        float segWidth = 3.2f, segLength = 1.4f;
        terrain = new Terrain(2000,
                V3(player.objX,player.objY - 3f, player.objZ + 3f),
                segWidth,
                segLength
        );
        terrain.enqueueStructure(new TerrainLine(100,terrain));
        terrain.enqueueStructure(new TerrainCurve(100,terrain, -PI/2));
        terrain.enqueueStructure(new Terrain2DCurve(50,terrain, 0, 0.5f * PI/4));
        terrain.enqueueStructure(new Terrain2DCurve(50,terrain, PI/12, -0.5f * PI/4));
        terrain.enqueueStructure(new TerrainLine(100,terrain));
        terrain.enqueueStructure(new TerrainLine(100,terrain));
        terrain.enqueueStructure(new TerrainCurve(100,terrain, -PI/2));
        terrain.generateChunks(-1);

    }

    @Override
    public void updateThenDraw(float dt) {

        terrain.removeOldTiles(player.objZ);
        for(int i=0;i<terrain.getTileCount();++i){
            Tile tile = terrain.getTile(i);
            if(player.collidesTile(tile)){
                player.setFooting(tile);
            }
        }

        player.updateBeforeDraw(dt);

        Vector3D camPos = V3(player.objX, player.objY + 0.75f, player.objZ)
                .sub(player.getDir().withLen(3.8f));
        camera.updateEyePos(camPos);
        camera.updateLookPos(camPos.add(player.getDir().setY(0.0f)));

        player.draw(camera.getViewProjectionMatrix());
        for(int i=0;i<terrain.getTileCount();++i) {
            Tile tile = terrain.getTile(i);
            Vector3D tc = tile.farLeft.add(tile.farRight).add(tile.nearLeft).add(tile.nearRight)
                    .div(4);
            if(tc.sub(V3(player.objX,player.objY,player.objZ)).sqlen() < 250*250) {
                tile.setTileColor(colorTheme);
                tile.draw(camera.getViewProjectionMatrix());
            }
        }

        player.updateAfterDraw(dt);
    }
}