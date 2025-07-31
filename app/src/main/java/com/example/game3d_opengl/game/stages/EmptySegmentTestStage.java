package com.example.game3d_opengl.game.stages;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.Player;
import com.example.game3d_opengl.game.stage_api.Stage;
import com.example.game3d_opengl.game.terrain_api.Tile;
import com.example.game3d_opengl.game.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain_structures.EmptySegmentTestStructure;
import com.example.game3d_opengl.game.track_elements.Potion;
import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Test stage to verify that empty segments work correctly and prevent addon placement.
 */
public class EmptySegmentTestStage extends Stage {

    private Camera camera;
    private Terrain terrain;
    
    // Camera position for viewing the terrain
    private float cameraDistance = 15.0f;
    private float cameraHeight = 8.0f;
    private float cameraAngle = 0.0f;

    public EmptySegmentTestStage(MyGLRenderer.StageManager stageManager) {
        super(stageManager);
    }

    @Override
    public void initScene(Context context, int screenWidth, int screenHeight) {
        this.camera = new Camera();
        Camera.setGlobalScreenSize(screenWidth, screenHeight);
        
        // Position camera to view the terrain
        camera.setProjectionAsScreen();
        updateCamera();
        
        // Load player assets (needed for DeathSpike and Potion)
        Player.LOAD_PLAYER_ASSETS(context.getAssets());
        Potion.LOAD_POTION_ASSETS(context.getAssets());
        
        // Create terrain with empty segment test structure
        terrain = new Terrain(50, 10, 
            new Vector3D(0, 0, 0), 2.0f, 2.0f);
        
        // Add the test structure that alternates tiles and empty segments
        terrain.enqueueStructure(new EmptySegmentTestStructure(15));
        
        // Generate some terrain chunks to see the result
        terrain.generateChunks(20);
        
        System.out.println("EmptySegmentTestStage: Terrain generated with " + 
            terrain.getTileCount() + " tiles");
    }

    private void updateCamera() {
        // Orbit camera around the origin
        float x = (float) (cameraDistance * Math.cos(cameraAngle));
        float z = (float) (cameraDistance * Math.sin(cameraAngle));
        camera.set(
                x, cameraHeight, z,   // eye position
                0, 0, 0,              // look at origin
                0, 1, 0               // up vector
        );
    }

    @Override
    public void updateThenDraw(float dt) {
        // Slowly rotate camera around the terrain
        cameraAngle += dt * 0.0005f; // 0.5 radians per second
        updateCamera();
        
        // Draw all tiles
        for (int i = 0; i < terrain.getTileCount(); i++) {
            Tile tile = terrain.getTile(i);
            tile.draw(camera.getViewProjectionMatrix());
        }
        
        // Draw all addons (should not appear in empty segments)
        for (int i = 0; i < terrain.getAddonCount(); i++) {
            Addon addon = terrain.getAddon(i);
            addon.draw(camera.getViewProjectionMatrix());
        }
    }

    @Override
    public void onClose() {
        if (terrain != null) {
            terrain.cleanupGPUResources();
        }
    }

    @Override
    public void onSwitch() {
        // Nothing special needed
    }

    @Override
    public void onReturn() {
        // Nothing special needed
    }

    @Override
    protected void onPause() {
        // Nothing special needed
    }

    @Override
    protected void onResume() {
        // Nothing special needed
    }

    @Override
    public void resetGPUResources() {
        if (terrain != null) {
            terrain.resetGPUResources();
        }
    }

    @Override
    public void onTouchDown(float x, float y) {
        // Nothing special needed for this test
    }

    @Override
    public void onTouchUp(float x, float y) {
        // Nothing special needed for this test
    }

    @Override
    public void onTouchMove(float x1, float y1, float x2, float y2) {
        // Nothing special needed for this test
    }
} 