package com.example.game3d_opengl.game.stages;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.rendering.util3d.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain_api.main.TerrainStructure;
import com.example.game3d_opengl.game.track_elements.DeathSpike;
import com.example.game3d_opengl.rendering.object3d.Camera;
import com.example.game3d_opengl.game.terrain_api.main.Terrain;

public class TestStage2 implements Stage {

    private Terrain terrain;
    private Camera camera;
    public TestStage2(MyGLRenderer.StageManager stageManager) {
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
        this.camera.set(0f, 0f, 3f, // eye pos
                0f, 0f, 0f, // look at
                0f, 1f, 0f); // which way is up
        camera.setProjectionAsScreen();
        terrain = new Terrain(
                200, 4,
                V3(-1f, -0.5f, -5.5f),
                2.5f, 1.5f
        );

        terrain.enqueueStructure(new TerrainStructure(23,"A") {
            @Override
            protected void generateTiles(Terrain.TileBrush brush) {
                brush.addSegment();
                brush.addSegment();
                brush.addSegment();
                addChild(new TerrainStructure(10, "B") {
                             @Override
                             protected void generateTiles(Terrain.TileBrush brush) {
                                 for(int i=0;i<10;++i){
                                     brush.addHorizontalAng(PI/60);
                                     brush.addSegment();
                                 }
                             }

                             @Override
                             protected void generateAddons(Terrain.GridBrush brush, int nRows, int nCols) {
                                    brush.reserveHorizontal(1,1,4,new Addon[]{
                                            new DeathSpike(),
                                            new DeathSpike(),
                                            new DeathSpike(),
                                            new DeathSpike(),
                                    });
                             }
                         },
                        brush);
                addChild(new TerrainStructure(10,"C") {
                             @Override
                             protected void generateTiles(Terrain.TileBrush brush) {
                                 for(int i=0;i<10;++i){
                                     brush.addHorizontalAng(-PI/60);
                                     brush.addSegment();
                                 }
                             }

                             @Override
                             protected void generateAddons(Terrain.GridBrush brush, int nRows, int nCols) {
                                 brush.reserveVertical(1,1,4,new Addon[]{
                                         new DeathSpike(),
                                         new DeathSpike(),
                                         new DeathSpike(),
                                         new DeathSpike(),
                                 });
                             }
                         },
                        brush);


            }

            @Override
            protected void generateAddons(Terrain.GridBrush brush, int nRows, int nCols) {

            }
        });

        /*System.out.println("TILE COUNT: "+terrain.getTileCount());
        for (int i = 0; i < terrain.getTileCount(); ++i) {
            System.out.println(terrain.getTile(i));
        }*/

    }

    @Override
    public void updateThenDraw(float dt) {
        terrain.generateChunks(1);
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        for (int i = 0; i < terrain.getTileCount(); ++i) {
            terrain.getTile(i).setTileColor(CLR((float)(i) / (float)(terrain.getTileCount()) , 0.2f, 0, 1));
            terrain.getTile(i).draw(camera.getViewProjectionMatrix());
        }
        for(int i=0;i<terrain.getAddonCount();++i){
            terrain.getAddon(i).draw(camera.getViewProjectionMatrix());
        }
    }
}
