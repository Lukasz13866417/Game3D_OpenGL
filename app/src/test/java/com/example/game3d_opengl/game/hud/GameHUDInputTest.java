package com.example.game3d_opengl.game.hud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.game3d_opengl.game.progress_bar.ProgressBar;
import com.example.game3d_opengl.rendering.icon.GearIcon;
import com.example.game3d_opengl.rendering.text.Button;
import com.example.game3d_opengl.rendering.text.TextRenderer;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.rect.Rect;

import org.junit.Test;

import java.lang.reflect.Constructor;

public class GameHUDInputTest {
    private static final Rect PAUSE = new Rect(10f, 10f, 60f, 60f);
    private static final Rect SETTINGS = new Rect(70f, 10f, 120f, 60f);

    @Test
    public void pauseChangesOnlyOnMatchingRelease() throws Exception {
        GameHUD hud = createInputOnlyHud();

        assertTrue(hud.handleTouchDown(30f, 30f));
        assertFalse(hud.isPaused());

        assertEquals(
                GameHUD.HudAction.TOGGLE_PAUSE,
                hud.handleTouchUp(30f, 30f)
        );
        assertTrue(hud.isPaused());
    }

    @Test
    public void settingsActionAlsoWaitsForRelease() throws Exception {
        GameHUD hud = createInputOnlyHud();

        assertTrue(hud.handleTouchDown(90f, 30f));
        assertFalse(hud.isPaused());

        assertEquals(
                GameHUD.HudAction.OPEN_SETTINGS,
                hud.handleTouchUp(90f, 30f)
        );
        assertFalse(hud.isPaused());
    }

    @Test
    public void dragOutAndCancelNeverActivateHudButtons() throws Exception {
        GameHUD hud = createInputOnlyHud();
        assertTrue(hud.handleTouchDown(30f, 30f));
        hud.handleTouchMove(200f, 200f);
        hud.handleTouchMove(30f, 30f);

        assertEquals(
                GameHUD.HudAction.NONE,
                hud.handleTouchUp(30f, 30f)
        );
        assertFalse(hud.isPaused());

        assertTrue(hud.handleTouchDown(30f, 30f));
        hud.cancelTouchGesture();
        assertEquals(
                GameHUD.HudAction.NONE,
                hud.handleTouchUp(30f, 30f)
        );
        assertFalse(hud.isPaused());
    }

    @Test
    public void gameplayBarsAndIconsUseSevenPercentLargerGeometry() {
        assertEquals(42.8f, GameHUD.barHeightPx(1080), 1e-5f);
        assertEquals(64.2f, GameHUD.iconButtonSizePx(1080), 1e-5f);
        assertEquals(1842.912f, GameHUD.hudBarWidthPx(1920, 1080), 1e-3f);
        assertTrue("larger icons must shorten the bars",
                GameHUD.hudBarWidthPx(1920, 1080) < 1845.6f);
    }

    @Test
    public void mutableGameplayThemeUpdatesStableHudColorWithoutAliasing() throws Exception {
        GameHUD hud = createInputOnlyHud();
        FColor mutableTheme = FColor.CLR(0.2f, 0.4f, 0.6f, 1f);

        hud.setThemeColor(mutableTheme);
        assertColor(hud.currentThemeColorForTest(), 0.2f, 0.4f, 0.6f, 1f);

        mutableTheme.rgba[0] = 0.8f;
        mutableTheme.rgba[1] = 0.3f;
        mutableTheme.rgba[2] = 0.1f;
        hud.setThemeColor(mutableTheme);

        assertColor(hud.currentThemeColorForTest(), 0.8f, 0.3f, 0.1f, 1f);
    }

    private static void assertColor(
            FColor actual, float r, float g, float b, float a) {
        assertEquals(r, actual.r(), 1e-6f);
        assertEquals(g, actual.g(), 1e-6f);
        assertEquals(b, actual.b(), 1e-6f);
        assertEquals(a, actual.a(), 1e-6f);
    }

    private static GameHUD createInputOnlyHud() throws Exception {
        Constructor<GameHUD> constructor = GameHUD.class.getDeclaredConstructor(
                ProgressBar.class,
                ProgressBar.class,
                Button.class,
                Button.class,
                Button.class,
                Button.class,
                GearIcon.class,
                Rect.class,
                Rect.class,
                Rect.class,
                Rect.class,
                float.class,
                TextRenderer.class,
                TextRenderer.TextLabel.class,
                float.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                PAUSE,
                SETTINGS,
                null,
                null,
                1f,
                null,
                null,
                1f
        );
    }
}
