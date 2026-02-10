package com.example.game3d_opengl.game.stage.stages.main;


import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.game.util.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;
import static java.lang.Math.abs;

import android.content.Context;
import android.content.res.AssetManager;
import android.opengl.GLES20;
import android.util.Log;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.hud.GameHUD;
import com.example.game3d_opengl.game.terrain.track_elements.portal.Portal;
import com.example.game3d_opengl.game.player.player_character.PlayerAssets;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.game.stage.stage_api.RenderContext;
import com.example.game3d_opengl.game.stage.stage_api.SceneRenderer;
import com.example.game3d_opengl.game.terrain.terrain_structures.Terrain2DCurve;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainLineWithSpikeRect;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainStairs;
import com.example.game3d_opengl.game.terrain.track_elements.potion.Potion;
import com.example.game3d_opengl.game.terrain.track_elements.spike.DeathSpike;
import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain.track_elements.portal.rendering.PortalShaderPair;
import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.LightSource;

import java.util.ArrayList;
import java.util.List;
/**
 * Demonstration of a gameplay stage that:
 * - Spawns multiple terrain segments via your Terrain class
 * - Renders them with slope-based coloring or as a "guardian" tile
 */
public class GameplayStage extends Stage {

    private static final float REBASE_DISTANCE = 1000f;
    private static final float REBASE_DISTANCE_SQ = REBASE_DISTANCE * REBASE_DISTANCE;

    public GameplayStage(MyGLRenderer.StageManager stageManager){
        super(stageManager);
    }

    @Override
    protected void onTouchDown(float x, float y) {
        playerInputAPI.setTouchDown();
    }

    @Override
    protected void onTouchUp(float x, float y) {
        if (playerInputAPI == null) return;
        playerInputAPI.setTouchUp();
    }

    @Override
    protected void onTouchMove(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        if (playerInputAPI == null) return;
        playerInputAPI.swipe(dx, dy);
    }

    Terrain terrain;

    private Camera camera;
    private int frameCounter = 0; // throttled logging counter
    private final FColor colorTheme = CLR(0.7f,0,0,1);
    private Player player;
    private Player.PlayerInputAPI playerInputAPI;
    private LightSource lightSource;

    private GameHUD gameHUD;
    private final List<Portal> entrancePortals = new ArrayList<>();
    private final RenderContext mainRenderCtx = new RenderContext();
    private final RenderContext portalRenderCtx = new RenderContext();
    private final SceneRenderer sceneRenderer = this::renderScene;

    public static boolean __DEBUG_IS_TERRAIN_GENERATING = false;

    @Override
    protected void initScene(Context context, int screenWidth, int screenHeight) {
        // --- Camera Setup ---
        this.camera = new Camera();

        this.camera.set(0f, 0f, 3f, // eye pos
                0f, 0f, 0f, // look at
                0f, 1f, 0f ); // which way is up
        camera.setProjectionAsScreen();

        AssetManager assetManager = context.getAssets();

        Potion.LOAD_POTION_ASSETS(assetManager);
        DeathSpike.LOAD_DEATHSPIKE_ASSETS();
        PortalShaderPair.LOAD_SHADER_CODE();

        PlayerAssets.LOAD_PLAYER_ASSETS(assetManager);
        player = Player.createPlayer();
        playerInputAPI = player.getInputAPI();
        lightSource = new LightSource(CLR(1,1,1,1));


        float segWidth = 3.2f, segLength = 1.4f;
        this.terrain = new Terrain(2000,6,
                V3(player.getX(), player.getY() - 3f, player.getZ()),
                segWidth,
                segLength,
                1f,
                lightSource

        );
        //terrain.enqueueStructure(new TerrainLineWithSpikeRect(60));
        terrain.enqueueStructure(new TerrainLineWithSpikeRect(70));
        terrain.enqueueStructure(new TerrainStairs(70,4,2, PI/6,-1f));
        terrain.enqueueStructure(new TerrainLineWithSpikeRect(70));
        terrain.enqueueStructure(new Terrain2DCurve(50,0,PI/16f));

        terrain.generateChunks(-1);

        System.out.println("GAMEPLAY STAGE INIT");

        gameHUD = GameHUD.makeGameHUD(context, screenWidth, screenHeight);

    }

    @Override
    public void updateThenDraw(float dt) {
        processTouchEvents();

        terrain.removeOldTerrainElements(player.getNearestTileId());
        if (terrain.getTileCount() < 400) {
            terrain.enqueueStructure(new TerrainLineWithSpikeRect(30));
            terrain.enqueueStructure(new TerrainLineWithSpikeRect(30));
            terrain.enqueueStructure(new TerrainStairs(50,4,2, PI/6,-1f));

        }
        if (terrain.getTileCount() < 300) {
            GameplayStage.__DEBUG_IS_TERRAIN_GENERATING = true;
            terrain.generateChunks(1);
        }

        maybeRebaseWorld();
        if (player != null) {
            player.beginFrame(dt);
        }

        for(int i=0;i<terrain.getTileCount();++i){
            terrain.getTile(i).accept(player);
        }
        for(int i=0;i<terrain.getAddonCount();++i){
            terrain.getAddon(i).accept(player);
        }


        // Includes player interactions: footing, distances to player, addon collisions
        player.updateBeforeDraw(dt);
        terrain.updateBeforeDraw(dt);

        Vector3D lightPos = V3(player.getX(), player.getY(), player.getZ() - 3f)
                .sub(player.getDir().withLen(6f));
        lightSource.setPosition(lightPos);

        Vector3D camPos = V3(player.getX(), player.getY() + 0.75f, player.getZ())
                 .sub(player.getDir().withLen(3.8f));
        camera.updateEyePos(camPos);
        camera.updateLookPos(camPos.add(player.getDir().setY(0.0f)));
        Vector3D playerPos = V3(player.getX(), player.getY(), player.getZ());
        lightSource.position = playerPos.add(player.getDir().withLen(5f))
                                        .add(V3(0, 70f, 0));
        float[] vpMatrix = camera.getViewProjectionMatrix();

        renderPortalViews();
        mainRenderCtx.vp = vpMatrix;
        mainRenderCtx.target = null;
        mainRenderCtx.viewportW = com.example.game3d_opengl.rendering.ScreenInfo.getScreenW();
        mainRenderCtx.viewportH = com.example.game3d_opengl.rendering.ScreenInfo.getScreenH();
        mainRenderCtx.flags = 0;
        mainRenderCtx.clear = false; // main framebuffer is already cleared by renderer
        sceneRenderer.render(mainRenderCtx);

        player.updateAfterDraw(dt);
        terrain.updateAfterDraw(dt);

        if (gameHUD != null) {
            gameHUD.collectInfo(player.getHUDAPI());
            gameHUD.draw();
        }

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
        if (gameHUD != null) {
            gameHUD.cleanupGPUResourcesRecursivelyOnContextLoss();
        }
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        player.reloadGPUResourcesRecursivelyOnContextLoss();
        terrain.reloadGPUResourcesRecursivelyOnContextLoss();
        if (gameHUD != null) {
            gameHUD.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    @Override
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {
        if (gameHUD != null) {
            gameHUD.cleanupGPUResourcesRecursivelyOnContextLoss();
        }
    }

    private void maybeRebaseWorld() {
        if (player == null || terrain == null) return;
        float px = player.getX();
        float py = player.getY();
        float pz = player.getZ();
        float distSq = px * px + py * py + pz * pz;
        if (distSq < REBASE_DISTANCE_SQ) return;
        Vector3D delta = V3(-px, -py, -pz);
        player.rebasePosition(delta);
        terrain.rebasePosition(delta);
    }

    private void renderPortalViews() {
        refreshEntrancePortals();
        for (Portal portal : entrancePortals) {
            if (!portal.isPlaced() || !portal.canRenderExitView()) {
                continue;
            }
            portalRenderCtx.vp = portal.getExitViewProjectionMatrix();
            portalRenderCtx.target = portal.getRenderTarget();
            portalRenderCtx.viewportW = portal.getRenderTarget().getWidth();
            portalRenderCtx.viewportH = portal.getRenderTarget().getHeight();
            portalRenderCtx.flags = RenderContext.FLAG_SKIP_PORTALS;
            portalRenderCtx.clear = true;
            sceneRenderer.render(portalRenderCtx);
        }
    }

    private void renderScene(RenderContext ctx) {
        if (ctx == null || ctx.vp == null) return;
        if (ctx.target != null) {
            ctx.target.bind();
        } else {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        }
        GLES20.glViewport(0, 0, ctx.viewportW, ctx.viewportH);
        if (ctx.clear) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        }

        if (player != null) {
            player.draw(ctx.vp);
        }
        if (terrain != null) {
            terrain.setColorTheme(colorTheme);
            terrain.draw(ctx.vp, !ctx.shouldSkipPortals());
        }
        if (ctx.target != null) {
            ctx.target.unbind();
        }
    }

    private void refreshEntrancePortals() {
        entrancePortals.clear();
        if (terrain == null) return;
        for (int i = 0; i < terrain.getAddonCount(); ++i) {
            if (terrain.getAddon(i) instanceof Portal) {
                entrancePortals.add((Portal) terrain.getAddon(i));
            }
        }
    }
}