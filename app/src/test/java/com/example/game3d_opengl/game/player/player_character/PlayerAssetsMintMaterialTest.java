package com.example.game3d_opengl.game.player.player_character;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Verifies the semantic split required by wheel-local temporal emission rendering. */
public class PlayerAssetsMintMaterialTest {
    @Test
    public void grooveSideAndPhaseInvariantBandRemainIndependentlyAddressable() {
        assertTrue(PlayerAssets.isMintEmissionMaterial(
                PlayerAssets.MINT_GROOVE_EMISSIVE_MATERIAL));
        assertTrue(PlayerAssets.isMintEmissionMaterial(
                PlayerAssets.MINT_SIDE_EMISSIVE_MATERIAL));
        assertTrue(PlayerAssets.isMintEmissionMaterial(
                PlayerAssets.MINT_MOTION_BAND_EMISSIVE_MATERIAL));

        assertFalse(PlayerAssets.isMintEmissionMaterial("mint_light"));
        assertFalse(PlayerAssets.isMintEmissionMaterial("mint_tread"));
        assertFalse(PlayerAssets.isMintEmissionMaterial(null));
    }

    @Test
    public void motionBandUsesCanonicalMeasuredGrooveDutyCycle() {
        assertTrue(PlayerAssets.MINT_MOTION_BAND_DUTY_CYCLE > 0f);
        assertTrue(PlayerAssets.MINT_MOTION_BAND_DUTY_CYCLE < 1f);
        assertTrue(Math.abs(
                PlayerAssets.MINT_MOTION_BAND_DUTY_CYCLE - 0.26164f)
                < 1.0e-7f);
    }


    @Test
    public void halfTransitionConservesPremultipliedGrooveAndBandEnergy() {
        double frame = 1.0 / 120.0;
        WheelTemporalSamplingPlanner.Plan plan =
                new WheelTemporalSamplingPlanner().planFromPresentedDelta(
                        WheelTemporalSamplingPlanner.GROOVE_PITCH_RADIANS
                                * 0.425,
                        frame * 0.75,
                        frame,
                        100.0);
        double physical = 0.0;
        for (int index = 0; index < plan.sampleCount(); index++) {
            physical += plan.resolvedSampleWeight(index);
        }
        double physicalAngularEnergy = physical
                * PlayerAssets.MINT_MOTION_BAND_DUTY_CYCLE;
        double bandAngularEnergy = PlayerAssets.MINT_MOTION_BAND_DUTY_CYCLE
                * plan.continuousBandBlend();

        assertTrue(Math.abs(physical - 0.5) < 1.0e-12);
        assertTrue(Math.abs(physicalAngularEnergy - 0.13082) < 1.0e-7);
        assertTrue(Math.abs(bandAngularEnergy - 0.13082) < 1.0e-7);
        assertTrue(Math.abs(
                physicalAngularEnergy + bandAngularEnergy
                        - PlayerAssets.MINT_MOTION_BAND_DUTY_CYCLE)
                < 1.0e-7);
    }
}
