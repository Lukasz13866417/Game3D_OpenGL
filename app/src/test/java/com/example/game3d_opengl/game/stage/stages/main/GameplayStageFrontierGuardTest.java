package com.example.game3d_opengl.game.stage.stages.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.game3d_opengl.rendering.util3d.FColor;

import org.junit.Test;

/**
 * Covers gameplay-stage policies that do not require an OpenGL rendering context.
 */
public class GameplayStageFrontierGuardTest {
    @Test
    public void frameTimingUsesItsDedicatedLogcatChannel() {
        assertEquals("GameFrameTiming", GameplayStage.FRAME_TIMING_LOG_TAG);
        assertEquals("GameFrameDetail", GameplayStage.FRAME_DETAIL_LOG_TAG);
    }

    @Test
    public void newGameplayRunsStartWithoutAnAirJumpCharge() {
        assertEquals(0, GameplayStage.DEFAULT_INITIAL_AIR_JUMP_CHARGES);
    }

    @Test
    public void emergencyTerrainCatchUpCannotRunAnUnboundedGlThreadBurst() {
        assertEquals(16, GameplayStage.COMMITTED_FRONTIER_EXTRA_GENERATION_BUDGET);
        assertEquals(128, GameplayStage.TERRAIN_AUTHORING_COMMAND_BUDGET);
        assertEquals(256, PreparedGameplaySession.TERRAIN_PREPARATION_COMMAND_BUDGET);
    }

    @Test
    public void repeatedSimulationOverrunsAreLoggedAtMostOncePerSecond() {
        long firstLogNanos = 5_000_000_000L;

        assertTrue(GameplayStage.shouldLogSimulationOverrun(
                Long.MIN_VALUE, firstLogNanos));
        assertFalse(GameplayStage.shouldLogSimulationOverrun(
                firstLogNanos,
                firstLogNanos
                        + GameplayStage.SIMULATION_OVERRUN_LOG_INTERVAL_NANOS
                        - 1L));
        assertTrue(GameplayStage.shouldLogSimulationOverrun(
                firstLogNanos,
                firstLogNanos
                        + GameplayStage.SIMULATION_OVERRUN_LOG_INTERVAL_NANOS));
        assertTrue(GameplayStage.shouldLogSimulationOverrun(
                firstLogNanos, firstLogNanos - 1L));
    }

    @Test
    public void repeatedInvariantFailuresAreLoggedAtMostOncePerSecond() {
        long firstLogNanos = 7_000_000_000L;

        assertTrue(GameplayStage.shouldLogInvariantFailure(
                Long.MIN_VALUE, firstLogNanos));
        assertFalse(GameplayStage.shouldLogInvariantFailure(
                firstLogNanos,
                firstLogNanos
                        + GameplayStage.INVARIANT_FAILURE_LOG_INTERVAL_NANOS
                        - 1L));
        assertTrue(GameplayStage.shouldLogInvariantFailure(
                firstLogNanos,
                firstLogNanos
                        + GameplayStage.INVARIANT_FAILURE_LOG_INTERVAL_NANOS));
    }

    @Test
    public void target_committed_lead_tiles_grows_with_player_speed() {
        int interactionAhead = 64;
        int slowLead = GameplayStage.computeTargetCommittedLeadTiles(32f, 1.4f, interactionAhead);
        int fastLead = GameplayStage.computeTargetCommittedLeadTiles(64f, 1.4f, interactionAhead);

        assertEquals(72, slowLead);
        assertEquals(82, fastLead);
    }

    @Test
    public void hard_minimum_lead_stays_below_target_but_above_interaction_window() {
        int interactionAhead = 64;
        int targetLead = GameplayStage.computeTargetCommittedLeadTiles(50f, 1.4f, interactionAhead);
        int hardMinLead = GameplayStage.computeHardMinimumCommittedLeadTiles(targetLead, interactionAhead);

        assertTrue(hardMinLead >= interactionAhead);
        assertTrue(hardMinLead < targetLead);
    }

    @Test
    public void player_simulation_blocks_only_when_committed_lead_is_below_floor() {
        assertTrue(GameplayStage.shouldBlockPlayerSimulation(12, 13));
        assertFalse(GameplayStage.shouldBlockPlayerSimulation(13, 13));
        assertFalse(GameplayStage.shouldBlockPlayerSimulation(20, 13));
    }

    @Test
    public void gameplay_levels_are_not_replenished_while_generation_work_is_already_pending() {
        assertFalse(GameplayStage.shouldEnqueueGameplayLevels(80, true));
        assertTrue(GameplayStage.shouldEnqueueGameplayLevels(80, false));
        assertFalse(GameplayStage.shouldEnqueueGameplayLevels(450, false));
    }

    @Test
    public void lost_run_falls_back_to_loading_only_after_wait_expires_and_next_session_is_not_ready() {
        assertFalse(GameplayStage.shouldFallbackToLoading(400f, false));
        assertFalse(GameplayStage.shouldFallbackToLoading(1000f, true));
        assertTrue(GameplayStage.shouldFallbackToLoading(1000f, false));
    }

    @Test
    public void current_and_next_session_generation_alternate_while_next_session_is_not_ready() {
        assertTrue(GameplayStage.shouldGenerateCurrentTerrainThisFrame(1, false));
        assertFalse(GameplayStage.shouldGenerateNextSessionThisFrame(1, false));

        assertFalse(GameplayStage.shouldGenerateCurrentTerrainThisFrame(2, false));
        assertTrue(GameplayStage.shouldGenerateNextSessionThisFrame(2, false));
    }

    @Test
    public void current_session_generation_runs_every_frame_once_next_session_is_ready() {
        assertTrue(GameplayStage.shouldGenerateCurrentTerrainThisFrame(1, true));
        assertTrue(GameplayStage.shouldGenerateCurrentTerrainThisFrame(2, true));
        assertFalse(GameplayStage.shouldGenerateNextSessionThisFrame(1, true));
        assertFalse(GameplayStage.shouldGenerateNextSessionThisFrame(2, true));
    }

    @Test
    public void theme_brightness_coefficients_dim_green_heavy_palettes_more_than_red_heavy() {
        FColor greenHeavy = GameplayStage.applyThemeBrightnessCoefficients(0.20f, 0.70f, 0.10f);
        FColor redHeavy = GameplayStage.applyThemeBrightnessCoefficients(0.70f, 0.20f, 0.10f);

        float greenTotal = greenHeavy.r() + greenHeavy.g() + greenHeavy.b();
        float redTotal = redHeavy.r() + redHeavy.g() + redHeavy.b();

        assertTrue(greenHeavy.g() < 0.70f);
        assertTrue(redTotal > greenTotal);
    }

    @Test
    public void theme_brightness_coefficients_preserve_neutral_balance() {
        FColor neutral = GameplayStage.applyThemeBrightnessCoefficients(0.30f, 0.30f, 0.30f);

        assertEquals(neutral.r(), neutral.g(), 1e-6f);
        assertEquals(neutral.g(), neutral.b(), 1e-6f);
    }

    @Test
    public void gameplay_theme_is_mixed_toward_a_lighter_pastel() {
        FColor source = FColor.CLR(0.70f, 0.20f, 0.10f, 1f);
        FColor pastel = GameplayStage.makePastelTheme(source);

        assertTrue(pastel.r() > source.r());
        assertTrue(pastel.g() > source.g());
        assertTrue(pastel.b() > source.b());
        assertTrue(pastel.r() - pastel.b() < source.r() - source.b());
        assertTrue(pastel.r() > pastel.g());
        assertTrue(pastel.g() > pastel.b());
    }
}
