package com.example.game3d_opengl.game.stage.stages.main;


import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.game.util.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.Context;
import android.content.res.AssetManager;
import android.opengl.GLES20;
import android.util.Log;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.hud.GameHUD;
import com.example.game3d_opengl.game.player.player_character.PlayerAssets;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.game.stage.stage_api.RenderContext;
import com.example.game3d_opengl.game.stage.stage_api.SceneRenderer;
import com.example.game3d_opengl.game.terrain.terrain_structures.Terrain2DCurve;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainLineWithSpikeRect;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainStairs;
import com.example.game3d_opengl.game.terrain.track_elements.potion.Potion;
import com.example.game3d_opengl.game.terrain.track_elements.portal.PortalLightingEnvironment;
import com.example.game3d_opengl.game.terrain.track_elements.spike.DeathSpike;
import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.game.util.GameRandom;
/**
 * Demonstration of a gameplay stage that:
 * - Spawns multiple terrain segments via your Terrain class
 * - Renders them with slope-based coloring or as a "guardian" tile
 */
public class GameplayStage extends Stage {

    private static final float REBASE_DISTANCE = 1000f;
    private static final float REBASE_DISTANCE_SQ = REBASE_DISTANCE * REBASE_DISTANCE;
    private static final float THEME_CHANGE_INTERVAL_MS = 3500f;
    private static final float THEME_TRANSITION_DURATION_MS = 900f;
    private static final float THEME_RGB_SUM = 1f;
    private static final float THEME_MIN_CHANNEL = 0.15f;
    private static final float THEME_MAX_CHANNEL = 0.80f;
    private static boolean SHARED_ASSETS_LOADED = false;

    public GameplayStage(MyGLRenderer.StageManager stageManager){
        super(stageManager);
    }

    @Override
    protected void setupAssets(AssetManager assetManager) {
        ensureSharedAssetsLoaded(assetManager);
    }

    @Override
    protected void onTouchDown(float x, float y) {
        if (gameHUD != null && gameHUD.handleTouchDown(x, y)) {
            if (gameHUD.isPaused() && playerInputAPI != null) {
                playerInputAPI.setTouchUp();
            }
            return;
        }
        if (gameHUD != null && gameHUD.isPaused()) {
            return;
        }
        if (playerInputAPI == null) {
            return;
        }
        playerInputAPI.setTouchDown();
    }

    @Override
    protected void onTouchUp(float x, float y) {
        if (gameHUD != null && gameHUD.isPaused()) {
            return;
        }
        if (playerInputAPI == null) return;
        playerInputAPI.setTouchUp();
    }

    @Override
    protected void onTouchMove(float x1, float y1, float x2, float y2) {
        if (gameHUD != null && gameHUD.isPaused()) {
            return;
        }
        float dx = x2 - x1;
        float dy = y2 - y1;
        if (playerInputAPI == null) return;
        playerInputAPI.swipe(dx, dy);
    }

    Terrain terrain;

    private Camera camera;
    private int frameCounter = 0; // throttled logging counter
    private FColor colorTheme = CLR(0.33f, 0.66f, 0f, 1f);
    private float themeR = 0.7f;
    private float themeG = 0f;
    private float themeB = 0f;
    private float targetThemeR = themeR;
    private float targetThemeG = themeG;
    private float targetThemeB = themeB;
    private float deltaThemeR = 0f;
    private float deltaThemeG = 0f;
    private float deltaThemeB = 0f;
    private float themeCountdownMs = THEME_CHANGE_INTERVAL_MS;
    private float themeTransitionRemainingMs = 0f;
    private boolean themeTransitioning = false;
    private Player player;
    private Player.PlayerInputAPI playerInputAPI;
    private LightSource lightSource;

    private GameHUD gameHUD;
    private BloomPostProcessor bloomPostProcessor;
    private final RenderContext mainRenderCtx = new RenderContext();
    private final SceneRenderer sceneRenderer = this::renderScene;

    public static boolean __DEBUG_IS_TERRAIN_GENERATING = false;

    @Override
    protected void initScene(Context context, int screenWidth, int screenHeight) {
        // --- Camera Setup ---
        this.camera = new Camera();
        initializeThemeCycle();

        this.camera.set(0f, 0f, 3f, // eye pos
                0f, 0f, 0f, // look at
                0f, 1f, 0f ); // which way is up
        camera.setProjectionAsScreen();

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

        bloomPostProcessor = new BloomPostProcessor(screenWidth, screenHeight);
        gameHUD = GameHUD.makeGameHUD(context, screenWidth, screenHeight);
        if (gameHUD != null) {
            gameHUD.setThemeColor(colorTheme);
        }

    }

    @Override
    public void updateThenDraw(float dt) {
        processTouchEvents();

        if (gameHUD != null && gameHUD.isPaused()) {
            drawCurrentFrameWithoutSimulation();
            return;
        }

        updateThemeCycle(dt);

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
        if (player.isDead()) {
            stageManager.toLoadingThenGameplay();
            return;
        }

        Vector3D camPos = V3(player.getX(), player.getY() + 0.75f, player.getZ())
                 .sub(player.getDir().withLen(3.8f));
        camera.updateEyePos(camPos);
        camera.updateLookPos(camPos.add(player.getDir().setY(0.0f)));
        lightSource.position = //playerPos.add(player.getDir().withLen(5f))
                                        camPos.add(V3(0, 30f, 2f));
        PortalLightingEnvironment.update(lightSource, camPos, colorTheme);
        float[] vpMatrix = camera.getViewProjectionMatrix();

        if (bloomPostProcessor != null && bloomPostProcessor.getSceneTarget() != null) {
            mainRenderCtx.vp = vpMatrix;
            mainRenderCtx.target = bloomPostProcessor.getSceneTarget();
            mainRenderCtx.viewportW = mainRenderCtx.target.getWidth();
            mainRenderCtx.viewportH = mainRenderCtx.target.getHeight();
            mainRenderCtx.flags = 0;
            mainRenderCtx.clear = true;
            sceneRenderer.render(mainRenderCtx);
            bloomPostProcessor.compositeToScreen();
        } else {
            mainRenderCtx.vp = vpMatrix;
            mainRenderCtx.target = null;
            mainRenderCtx.viewportW = com.example.game3d_opengl.rendering.ScreenInfo.getScreenW();
            mainRenderCtx.viewportH = com.example.game3d_opengl.rendering.ScreenInfo.getScreenH();
            mainRenderCtx.flags = 0;
            mainRenderCtx.clear = false; // main framebuffer is already cleared by renderer
            sceneRenderer.render(mainRenderCtx);
        }

        player.updateAfterDraw(dt);
        terrain.updateAfterDraw(dt);

        if (gameHUD != null) {
            gameHUD.setThemeColor(colorTheme);
            gameHUD.collectInfo(player.getHUDAPI());
            gameHUD.draw();
        }

        if ((frameCounter++ & 127) == 0) {
            Log.d("Perf", "dt=" + dt + " visible=" + terrain.getTileCount() + ","+ terrain.getAddonCount());
        }


    }

    private void drawCurrentFrameWithoutSimulation() {
        if (camera == null) {
            return;
        }
        float[] vpMatrix = camera.getViewProjectionMatrix();
        if (bloomPostProcessor != null && bloomPostProcessor.getSceneTarget() != null) {
            mainRenderCtx.vp = vpMatrix;
            mainRenderCtx.target = bloomPostProcessor.getSceneTarget();
            mainRenderCtx.viewportW = mainRenderCtx.target.getWidth();
            mainRenderCtx.viewportH = mainRenderCtx.target.getHeight();
            mainRenderCtx.flags = 0;
            mainRenderCtx.clear = true;
            sceneRenderer.render(mainRenderCtx);
            bloomPostProcessor.compositeToScreen();
        } else {
            mainRenderCtx.vp = vpMatrix;
            mainRenderCtx.target = null;
            mainRenderCtx.viewportW = com.example.game3d_opengl.rendering.ScreenInfo.getScreenW();
            mainRenderCtx.viewportH = com.example.game3d_opengl.rendering.ScreenInfo.getScreenH();
            mainRenderCtx.flags = 0;
            mainRenderCtx.clear = false;
            sceneRenderer.render(mainRenderCtx);
        }

        if (gameHUD != null && player != null) {
            gameHUD.setThemeColor(colorTheme);
            gameHUD.collectInfo(player.getHUDAPI());
            gameHUD.draw();
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
        if (player != null) {
            player.cleanupGPUResourcesRecursively();
        }
        if (terrain != null) {
            terrain.cleanupGPUResourcesRecursively();
        }
        if (bloomPostProcessor != null) {
            bloomPostProcessor.cleanupGPUResourcesRecursively();
        }
        if (gameHUD != null) {
            gameHUD.cleanupGPUResourcesRecursively();
        }
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        if (player != null) {
            player.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (terrain != null) {
            terrain.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (bloomPostProcessor != null) {
            bloomPostProcessor.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (gameHUD != null) {
            gameHUD.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        if (bloomPostProcessor != null) {
            bloomPostProcessor.cleanupGPUResourcesRecursively();
        }
        if (gameHUD != null) {
            gameHUD.cleanupGPUResourcesRecursively();
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
            terrain.draw(ctx.vp, true);
        }
        if (ctx.target != null) {
            ctx.target.unbind();
        }
    }

    private void updateThemeCycle(float dt) {
        float remainingDt = Math.max(0f, dt);
        int guard = 0;

        while (remainingDt > 1e-5f && guard++ < 8) {
            if (themeTransitioning) {
                float step = Math.min(remainingDt, themeTransitionRemainingMs);
                themeR += deltaThemeR * step;
                themeG += deltaThemeG * step;
                themeB += deltaThemeB * step;
                themeTransitionRemainingMs -= step;
                remainingDt -= step;
                updateColorThemeObject();

                if (themeTransitionRemainingMs <= 1e-5f) {
                    // Snap exactly to target to avoid float drift.
                    themeR = targetThemeR;
                    themeG = targetThemeG;
                    themeB = targetThemeB;
                    updateColorThemeObject();
                    themeTransitionRemainingMs = 0f;
                    themeTransitioning = false;
                    // Countdown restarts only after transition is complete.
                    themeCountdownMs = THEME_CHANGE_INTERVAL_MS;
                } else {
                    break;
                }
            } else {
                float step = Math.min(remainingDt, themeCountdownMs);
                themeCountdownMs -= step;
                remainingDt -= step;

                if (themeCountdownMs <= 1e-5f) {
                    startThemeTransition(randomConstrainedTheme());
                } else {
                    break;
                }
            }
        }
    }

    private void initializeThemeCycle() {
        FColor initialTheme = randomConstrainedTheme();
        themeR = clamp01(initialTheme.r());
        themeG = clamp01(initialTheme.g());
        themeB = clamp01(initialTheme.b());
        targetThemeR = themeR;
        targetThemeG = themeG;
        targetThemeB = themeB;
        deltaThemeR = 0f;
        deltaThemeG = 0f;
        deltaThemeB = 0f;
        themeTransitionRemainingMs = 0f;
        themeTransitioning = false;
        themeCountdownMs = THEME_CHANGE_INTERVAL_MS;
        updateColorThemeObject();
    }

    private void startThemeTransition(FColor targetTheme) {
        if (targetTheme == null) {
            return;
        }

        targetThemeR = clamp01(targetTheme.r());
        targetThemeG = clamp01(targetTheme.g());
        targetThemeB = clamp01(targetTheme.b());

        float totalDelta = Math.abs(targetThemeR - themeR)
                + Math.abs(targetThemeG - themeG)
                + Math.abs(targetThemeB - themeB);
        if (totalDelta <= 1e-6f) {
            themeTransitioning = false;
            themeTransitionRemainingMs = 0f;
            themeCountdownMs = THEME_CHANGE_INTERVAL_MS;
            return;
        }

        float durationMs = Math.max(1f, THEME_TRANSITION_DURATION_MS);
        // Per-ms color deltas so all channels reach target at the same moment.
        deltaThemeR = (targetThemeR - themeR) / durationMs;
        deltaThemeG = (targetThemeG - themeG) / durationMs;
        deltaThemeB = (targetThemeB - themeB) / durationMs;
        themeTransitionRemainingMs = durationMs;
        themeTransitioning = true;
        themeCountdownMs = 0f;
    }

    private void updateColorThemeObject() {
        colorTheme = CLR(clamp01(themeR), clamp01(themeG), clamp01(themeB), 1f);
    }

    private static FColor randomConstrainedTheme() {
        for (int attempt = 0; attempt < 64; ++attempt) {
            float r = GameRandom.randFloat(THEME_MIN_CHANNEL, THEME_MAX_CHANNEL, 4);
            float gMin = Math.max(THEME_MIN_CHANNEL, THEME_RGB_SUM - r - THEME_MAX_CHANNEL);
            float gMax = Math.min(THEME_MAX_CHANNEL, THEME_RGB_SUM - r - THEME_MIN_CHANNEL);
            if (gMin > gMax) {
                continue;
            }
            float g = GameRandom.randFloat(gMin, gMax, 4);
            float b = THEME_RGB_SUM - r - g;
            if (b >= THEME_MIN_CHANNEL && b <= THEME_MAX_CHANNEL) {
                return CLR(clamp01(r), clamp01(g), clamp01(b), 1f);
            }
        }
        float c = clamp01(THEME_RGB_SUM / 3f);
        return CLR(c, c, c, 1f);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static void ensureSharedAssetsLoaded(AssetManager assetManager) {
        if (SHARED_ASSETS_LOADED) {
            return;
        }
        Potion.LOAD_POTION_ASSETS(assetManager);
        DeathSpike.LOAD_DEATHSPIKE_ASSETS();
        PlayerAssets.LOAD_PLAYER_ASSETS(assetManager);
        SHARED_ASSETS_LOADED = true;
    }
}
