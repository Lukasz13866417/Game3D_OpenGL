package com.example.game3d_opengl.game.stage.stages.main;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GameplayPhaseTimelineTest {
    @Test
    public void phase_durations_grow_and_roll_forward_after_completion() {
        GameplayPhaseTimeline timeline = new GameplayPhaseTimeline(1000f, 2f);

        assertEquals(0, timeline.getCompletedPhaseCount());
        assertEquals(1000f, timeline.getRemainingUntilNextPhaseMs(), 1e-5f);

        assertEquals(1, timeline.advance(1000f));
        assertEquals(1, timeline.getCompletedPhaseCount());
        assertEquals(2000f, timeline.getRemainingUntilNextPhaseMs(), 1e-5f);

        assertEquals(0, timeline.advance(500f));
        assertEquals(1500f, timeline.getRemainingUntilNextPhaseMs(), 1e-5f);
    }

    @Test
    public void upcoming_transition_offsets_include_current_boundary_when_requested() {
        GameplayPhaseTimeline timeline = new GameplayPhaseTimeline(1000f, 2f);
        float[] offsets = new float[3];

        assertEquals(3, timeline.fillUpcomingTransitionOffsets(offsets, false));
        assertArrayEquals(new float[]{1000f, 3000f, 7000f}, offsets, 1e-5f);

        timeline.advance(1000f);
        assertEquals(3, timeline.fillUpcomingTransitionOffsets(offsets, true));
        assertArrayEquals(new float[]{0f, 2000f, 6000f}, offsets, 1e-5f);
    }

    @Test
    public void player_speed_schedule_starts_below_old_speed_and_increases() {
        float oldSpeed = 0.04f;
        float initialSpeed = com.example.game3d_opengl.game.player.player_character.PlayerConfig
                .speedForCompletedPhases(0);
        float laterSpeed = com.example.game3d_opengl.game.player.player_character.PlayerConfig
                .speedForCompletedPhases(3);

        assertTrue(initialSpeed < oldSpeed);
        assertTrue(laterSpeed > initialSpeed);
    }
}
