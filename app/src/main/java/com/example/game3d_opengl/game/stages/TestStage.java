package com.example.game3d_opengl.game.stages;

import static com.example.game3d_opengl.engine.util3d.FColor.CLR;
import static com.example.game3d_opengl.engine.util3d.vector.Vector3D.V3;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.engine.object3d.Camera;
import com.example.game3d_opengl.engine.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain.main.TileBuilder;
import com.example.game3d_opengl.game.track_elements.DeathSpike;

public class TestStage implements Stage {

    DeathSpike deathSpike;
    private Camera camera;
    TileBuilder tileBuilder;

    public TestStage(MyGLRenderer.StageManager stageManager) {
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
        deathSpike = new DeathSpike();
        deathSpike.place(
                V3(-0.25f, 0.25f, -1.25f),
                V3(0.25f, 0.25f, -1.25f),
                V3(-0.25f, 0.25f, -1.50f),
                V3(0.25f, 0.25f, -1.50f)
        );
        tileBuilder = new TileBuilder(
                200, 2,
                V3(0, -0.5f, -0.5f),
                2.5f, 1.5f, 1.5f
        );
        for (int i = 0; i < 1; ++i) {
            tileBuilder.addSegment();
        }
        for (int i = 0; i < tileBuilder.getTileCount(); ++i) {
            System.out.println(tileBuilder.getTile(i));
        }
        System.out.println("FIELDS\n");
        int cntRows = tileBuilder.currRowCount;
        System.out.println("COUNT ROWS: " + tileBuilder.currRowCount);
        for (int r = 1; r <= cntRows; ++r) {
            for (int c = 1; c <= 3; ++c) {
                System.out.println("ROW: " + r + " COL: " + c);
                Vector3D[] field = tileBuilder.getField(r, c);
                System.out.println("NEAR L: " + field[0] + " R: " + field[1]);
                System.out.println("FAR L: " + field[2] + " R: " + field[3]);
                System.out.println();
            }
        }
    }

    @Override
    public void updateThenDraw(float dt) {
        for (int i = 0; i < tileBuilder.getTileCount(); ++i) {
            tileBuilder.getTile(i).setTileColor(CLR(1, 0, 0, 1));
            tileBuilder.getTile(i).draw(camera.getViewProjectionMatrix());
        }
    }
}
