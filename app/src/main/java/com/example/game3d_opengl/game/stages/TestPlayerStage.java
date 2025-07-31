package com.example.game3d_opengl.game.stages;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.Player;
import com.example.game3d_opengl.game.stage_api.Stage;
import com.example.game3d_opengl.rendering.Camera;

public class TestPlayerStage extends Stage {

    private Camera camera;
    private Player player;

    // Camera position
    private float camX = 0f;
    private float camY = 2f;     // slightly above ground
    private float camZ = 3f;     // behind the player
    private float rotationY = 0f; // camera rotation

    public TestPlayerStage(MyGLRenderer.StageManager stageManager) {
        super(stageManager);
    }

    @Override
    public void onTouchDown(float x, float y) {}
    
    @Override
    public void onTouchUp(float x, float y) {}
    
    @Override
    public void onTouchMove(float x1, float y1, float x2, float y2) {
        // Allow rotating the camera around the player
        float dx = x2 - x1;
        rotationY += dx * 0.01f; // adjust sensitivity
    }

    @Override
    public void initScene(Context context, int screenWidth, int screenHeight) {
        // Initialize camera
        this.camera = new Camera();
        Camera.setGlobalScreenSize(screenWidth, screenHeight);
        
        // Load player assets and create player
        Player.LOAD_PLAYER_ASSETS(context.getAssets());
        player = Player.createPlayer();
        
        updateCamera();
        camera.setProjectionAsScreen();
    }

    private void updateCamera() {
        // Position camera in a circle around the player
        float radius = 3f;
        camX = player.getX() + radius * (float) Math.sin(rotationY);
        camZ = player.getZ() + radius * (float) Math.cos(rotationY);
        
        camera.set(
                camX, camY, camZ,           // eye position (behind/around player)
                player.getX(), player.getY(), player.getZ(),  // look at player
                0f, 1f, 0f                  // up vector
        );
    }

    @Override
    public void updateThenDraw(float dt) {
        // Update camera position
       // updateCamera();
        
        // Update and draw player
     //   player.updateBeforeDraw(dt);
        player.draw(camera.getViewProjectionMatrix());
      //  player.updateAfterDraw(dt);
    }

    @Override
    public void onClose() {
        // Cleanup player resources if needed
    }

    @Override
    public void onSwitch() {
        System.out.println("SWITCHING FROM TEST STAGE 3");
    }

    @Override
    public void onReturn() {
        System.out.println("RETURNING TO TEST STAGE 3");
    }

    @Override
    public void onPause() {

    }

    @Override
    public void onResume() {

    }

    @Override
    public void resetGPUResources() {

    }
} 