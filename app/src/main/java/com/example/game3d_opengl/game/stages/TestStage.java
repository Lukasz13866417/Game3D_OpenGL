package com.example.game3d_opengl.game.stages;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.rendering.util3d.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.Stage;
import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.rendering.object3d.LineSet3D;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain_api.main.TileBuilder;

import java.util.stream.IntStream;

public class TestStage extends Stage {

    private Camera camera;
    private TileBuilder tileBuilder;
    private LineSet3D grid, left, right;

    // Camera position and movement
    private float camX = 0f;
    private float camY = 10f;    // height above ground
    private float camZ = -6.5f;   // initial distance from origin
    private float moveSpeed = 0.00f; // movement per frame

    public TestStage(MyGLRenderer.StageManager stageManager) {}

    @Override
    public void onTouchDown(float x, float y) {}
    @Override
    public void onTouchUp(float x, float y) {}
    @Override
    public void onTouchMove(float x1, float y1, float x2, float y2) {}

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

        // build terrain
        tileBuilder = new TileBuilder(
                200, 2,
                V3(0, -0.5f, -3f),
                2f, 1f, 1.5f
        );
        for (int i = 0; i < 3; ++i) tileBuilder.addSegment();
        tileBuilder.addHorizontalAngle(PI/20);
        for (int i = 0; i < 3; ++i) tileBuilder.addSegment();
        //tileBuilder.addHorizontalAngle(PI/20);
        //for (int i = 0; i < 3; ++i) tileBuilder.addSegment();

        // line sets for debugging
        grid = new LineSet3D(
                IntStream.rangeClosed(0, tileBuilder.getCurrRowCount()-1)
                        .boxed()
                        .flatMap(r -> IntStream.rangeClosed(0,2)
                                .mapToObj(c -> tileBuilder.getGridPointDebug(r, c).addY(0.01f)))
                        .toArray(Vector3D[]::new),
                new int[][]{},
                FColor.CLR(1,1,1), FColor.CLR(0,1,0)
        );
        left  = new LineSet3D(tileBuilder.leftSideToArrayDebug(),  new int[][]{}, FColor.CLR(1,1,1), FColor.CLR(1,0,1));
        right = new LineSet3D(tileBuilder.rightSideToArrayDebug(), new int[][]{}, FColor.CLR(1,1,1), FColor.CLR(0,0,1));



    }

    @Override
    public void updateThenDraw(float dt) {
        // move camera forward each frame
        camZ -= moveSpeed;
        // reset camera to look straight down (no rotation toward origin)
        camera.set(
                camX, camY, camZ,
                camX, 0f, camZ,
                0f, 0f, -1f
        );

        // draw terrain
        for (int i = 0; i < tileBuilder.getTileCount(); ++i) {
            tileBuilder.getTile(i).setTileColor(CLR(1, 0, 0, 1));
            tileBuilder.getTile(i).draw(camera.getViewProjectionMatrix());
        }
        grid.draw(camera.getViewProjectionMatrix());
        left.draw(camera.getViewProjectionMatrix());
        right.draw(camera.getViewProjectionMatrix());

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
}
