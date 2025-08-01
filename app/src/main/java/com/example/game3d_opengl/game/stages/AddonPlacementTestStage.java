package com.example.game3d_opengl.game.stages;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.stage_api.Stage;
import com.example.game3d_opengl.game.terrain_api.Tile;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain_api.main.TerrainStructure;
import com.example.game3d_opengl.game.terrain_api.main.TileBuilder;
import com.example.game3d_opengl.game.terrain_structures.TerrainLine;
import com.example.game3d_opengl.game.track_elements.DeathSpike;
import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.rendering.LineSet3D;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public class AddonPlacementTestStage extends Stage {

    private Camera camera;

    private Terrain terrain;

    // Camera position and movement
    private float camX = 0f;
    private float camY = 10f;    // height above ground
    private float camZ = -6.5f;   // initial distance from origin
    private float moveSpeed = 0.00f; // movement per frame

    public AddonPlacementTestStage(MyGLRenderer.StageManager stageManager) {
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

    }

    @Override
    public void initScene(Context context, int screenWidth, int screenHeight) {
        this.camera = new Camera();
        Camera.setGlobalScreenSize(screenWidth, screenHeight);
        // initial camera setup: looking straight down
        camera.set(
                camX, camY, camZ,   // eye position
                camX, 0f, camZ,     // look straight down to ground below
                0f, 0f, -1f         // up vector to keep orientation stable
        );
        camera.setProjectionAsScreen();
        float segWidth = 3.2f, segLength = 1.4f;
        this.terrain = new Terrain(2000, 6,
                V3(0, -0.5f, -3f),
                segWidth,
                segLength
        );
        terrain.enqueueStructure(new TerrainStructure(100) {
            @Override
            protected void generateTiles(Terrain.TileBrush brush) {
                for(int i=0;i<tilesToMake;++i){
                    brush.addSegment();
                }
            }

            @Override
            protected void generateAddons(Terrain.GridBrush brush, int nRows, int nCols) {
                for(int i=1;i<nRows;++i){
                    for(int j=1;j<=nCols;++j){
                        Addon[] addons = new Addon[1];
                        for (int k = 0; k < addons.length; ++k) {
                            addons[k] = DeathSpike.createDeathSpike();
                        }
                        brush.reserveVertical(i,j,1,addons);
                    }
                }
            }
        });
        terrain.enqueueStructure(new TerrainLine(100));
        terrain.generateChunks(-1);
    }

    @Override
    public void updateThenDraw(float dt) {
        camZ -= moveSpeed;
        // reset camera to look straight down (no rotation toward origin)
        camera.set(
                camX, camY, camZ,
                camX, 0f, camZ,
                0f, 0f, -1f
        );

        for (int i = 0; i < terrain.getAddonCount(); ++i) {
            terrain.getAddon(i).updateBeforeDraw(dt);
            terrain.getAddon(i).draw(camera.getViewProjectionMatrix());
            terrain.getAddon(i).updateAfterDraw(dt);
        }

        for (int i = 0; i < terrain.getTileCount(); ++i) {
            Tile tile = terrain.getTile(i);
            tile.setTileColor(new FColor(1, 0, 0));
            tile.draw(camera.getViewProjectionMatrix());
        }
    }

    @Override
    public void onClose() {

    }

    @Override
    public void onSwitch() {

    }

    @Override
    public void onReturn() {

    }

    @Override
    public void resetGPUResources() {

    }

    @Override
    protected void onPause() {

    }

    @Override
    protected void onResume() {

    }
}
