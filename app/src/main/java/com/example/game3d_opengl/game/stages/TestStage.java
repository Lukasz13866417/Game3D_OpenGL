package com.example.game3d_opengl.game.stages;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.rendering.object3d.Camera;
import com.example.game3d_opengl.rendering.object3d.LineSet3D;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain_api.main.TileBuilder;

import java.util.stream.IntStream;

public class TestStage implements Stage {

    Camera camera;
    TileBuilder tileBuilder;
    LineSet3D grid, left, right;

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

        tileBuilder = new TileBuilder(
                200, 2,
                V3(0, -0.5f, -3f),
                2f, 1.5f, 1.5f
        );
        for (int i = 0; i < 2; ++i) {
            tileBuilder.addSegment();
        }
        for (int i = 0; i < tileBuilder.getTileCount(); ++i) {
            System.out.println(tileBuilder.getTile(i));
        }
        /*System.out.println("FIELDS\n");
        int cntRows = tileBuilder.getCurrRowCount();
        System.out.println("COUNT ROWS: " + tileBuilder.getCurrRowCount());
        for (int r = 1; r <= cntRows; ++r) {
            for (int c = 1; c <= cntRows; ++c) {
                System.out.println("ROW: " + r + " COL: " + c);
                Vector3D[] field = tileBuilder.getField(r, c);
                System.out.println("NEAR L: " + field[0] + " R: " + field[1]);
                System.out.println("FAR  L: " + field[2] + " R: " + field[3]);
                System.out.println();
            }
        }*/

        grid = new LineSet3D(
                IntStream.rangeClosed(1,1).boxed()
                        .flatMap(r ->
                                IntStream.rangeClosed(0, 0)
                                        .mapToObj(c -> tileBuilder.getGridPointDebug(r, c)
                                                          .addY(0.01f)
                                        )
                        )
                        .toArray(Vector3D[]::new),
                new int[][]{},
                FColor.CLR(1,1,1),
                FColor.CLR(0,1,0)

        );

        left = new LineSet3D(
                tileBuilder.leftSideToArrayDebug(),
                new int[][]{},
                FColor.CLR(1,1,1),
                FColor.CLR(1,0,1)
        );

        right = new LineSet3D(
                tileBuilder.rightSideToArrayDebug(),
                new int[][]{},
                FColor.CLR(1,1,1),
                FColor.CLR(0,0,1)
        );

        System.out.println("XDDDDDDDDDDDDDDDD");
        for(Vector3D v : tileBuilder.leftSideToArrayDebug()){
            System.out.println(v);
        }
        System.out.println("========");
        for(Vector3D v : tileBuilder.rightSideToArrayDebug()){
            System.out.println(v);
        }
        System.out.println("========");

        for(TileBuilder.GridRowHelper grh : tileBuilder.rowInfoToArrayDebug()){
            System.out.println(grh);
        }



    }

    @Override
    public void updateThenDraw(float dt) {
        for (int i = 0; i < tileBuilder.getTileCount(); ++i) {
            tileBuilder.getTile(i).setTileColor(CLR(1, 0, 0, 1));
            tileBuilder.getTile(i).draw(camera.getViewProjectionMatrix());
        }
        grid.draw(camera.getViewProjectionMatrix());
        left.draw(camera.getViewProjectionMatrix());
        right.draw(camera.getViewProjectionMatrix());
    }
}
