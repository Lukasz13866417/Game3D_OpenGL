package com.example.game3d_opengl.game.stage.stages.test;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.game.player.player_character.PlayerAssets;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.terrain_structures.EmptySegmentTestStructure;
import com.example.game3d_opengl.game.terrain.track_elements.potion.Potion;
import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.rendering.util3d.FColor;
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
    protected void setupAssets(android.content.res.AssetManager assetManager) {
        // No-op.
    }

    @Override
    protected void initScene(Context context, int screenWidth, int screenHeight) {
        this.camera = new Camera();
        
        // Position camera to view the terrain
        camera.setProjectionAsScreen();
        updateCamera();
        
        // Load player assets (needed for DeathSpike and Potion)
        PlayerAssets.LOAD_PLAYER_ASSETS(context.getAssets());
        com.example.game3d_opengl.game.terrain.terrain_api.main.LegacyGameplayElementRenderers
                .ensureLoaded(context.getAssets());
        
        // Create terrain with empty segment test structure
        terrain = new Terrain(50, 10, 
            new Vector3D(0, 0, 0), 2.0f, 2.0f, 1f, new LightSource(new FColor(1.0f,0,0)));
        
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
        processTouchEvents();
        // Slowly rotate camera around the terrain
        cameraAngle += dt * 0.0005f; // 0.5 radians per second
        updateCamera();
        
        // Draw all addons (should not appear in empty segments)
        for (int i = 0; i < terrain.getAddonCount(); i++) {
            Addon addon = terrain.getAddon(i);
            addon.draw(camera.getViewProjectionMatrix());
        }
    }

    @Override
    protected void onDeactivated(DeactivationReason reason) {
        // Nothing special needed
    }

    @Override
    protected void onActivated(ActivationReason reason) {
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
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        terrain.reloadGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        terrain.cleanupGPUResourcesRecursively();
    }

    @Override
    protected void onTouchDown(float x, float y) {
        // Nothing special needed for this test
    }

    @Override
    protected void onTouchUp(float x, float y) {
        // Nothing special needed for this test
    }

    @Override
    protected void onTouchMove(float x1, float y1, float x2, float y2) {
        // Nothing special needed for this test
    }
}
