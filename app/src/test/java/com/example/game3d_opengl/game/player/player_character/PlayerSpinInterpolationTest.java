package com.example.game3d_opengl.game.player.player_character;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies exact physics-phase interpolation and the slower presentation-only spin.
 */
public class PlayerSpinInterpolationTest {
    @Test
    public void interpolationPreservesMoreThanHalfTurnInOneTick() {
        double previous = 0.25;
        double exactDelta = -Math.PI * 1.5;

        double halfway = Player.interpolateAxleRadians(
                previous, exactDelta, 0.5);
        double endpoint = Player.interpolateAxleRadians(
                previous, exactDelta, 1.0);

        assertEquals(Math.IEEEremainder(
                previous + exactDelta * 0.5, Math.PI * 2.0),
                halfway, 0.0);
        assertEquals(Math.IEEEremainder(
                previous + exactDelta, Math.PI * 2.0),
                endpoint, 0.0);
        assertTrue("shortest-path interpolation would rotate the opposite way",
                halfway < previous);
    }

    @Test
    public void visualSpinCompressionPreservesDirectionAndCapsFastRotation() {
        double veryFast = Math.PI * 2.0 * 22.0;
        double compressed =
                Player.compressVisualAngularVelocity(veryFast);

        assertTrue(compressed > 0.0);
        assertTrue(compressed
                <= Math.PI * 2.0 * Player.MAX_VISUAL_SPIN_RPS);
        assertEquals(
                -compressed,
                Player.compressVisualAngularVelocity(-veryFast),
                1.0e-12);
        assertEquals(
                0.0,
                Player.compressVisualAngularVelocity(0.0),
                0.0);
    }

    @Test
    public void violetDetailUsesNestedAnglePerFrameTransitions() {
        assertEquals(
                1f,
                Player.fadeOutForAngle(
                        5.0,
                        Player.DETAIL_GROOVES_FULL_BELOW_DEGREES,
                        Player.DETAIL_GROOVES_GONE_AT_DEGREES),
                0f);
        assertEquals(
                0f,
                Player.fadeOutForAngle(
                        20.0,
                        Player.SECONDARY_GROOVES_FULL_BELOW_DEGREES,
                        Player.SECONDARY_GROOVES_GONE_AT_DEGREES),
                0f);
        assertEquals(
                1f,
                Player.fadeOutForAngle(
                        20.0,
                        Player.PRIMARY_GROOVES_FULL_BELOW_DEGREES,
                        Player.PRIMARY_GROOVES_GONE_AT_DEGREES),
                0f);
        assertEquals(
                1f,
                Player.fadeInForAngle(
                        30.0,
                        Player.CORE_GLOW_START_DEGREES,
                        Player.CORE_GLOW_FULL_DEGREES),
                0f);
    }
}
