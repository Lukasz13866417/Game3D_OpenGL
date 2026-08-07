package com.example.game3d_opengl.game.player.player_character;

import com.example.game3d.core.simulation.PhysicsConfig;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SharedPlayerGeometryTest {
    @Test
    public void renderedTireDimensionsMatchAuthoritativeCylinder() {
        PhysicsConfig core = new PhysicsConfig();
        PlayerConfig rendered = new PlayerConfig();

        assertEquals(core.cylinderRadius * 2.0,
                rendered.playerHeight, 1.0e-7);
        assertEquals(core.cylinderHalfLength * 2.0,
                rendered.playerWidth, 1.0e-7);
    }

    @Test
    public void spinBlurSamplesOnlyRotatingHighContrastDetails() {
        assertTrue(PlayerAssets.isSpinBlurMaterial("mint_tread"));
        assertTrue(PlayerAssets.isSpinBlurMaterial("mint_light"));
        assertTrue(PlayerAssets.isSpinBlurMaterial("violet_energy"));
        assertTrue(PlayerAssets.isSpinBlurMaterial("violet_glow_primary"));
        assertTrue(PlayerAssets.isSpinBlurMaterial("violet_glow_secondary"));
        assertTrue(PlayerAssets.isSpinBlurMaterial("violet_glow_detail"));

        assertFalse(PlayerAssets.isSpinBlurMaterial("mint_rubber"));
        assertFalse(PlayerAssets.isSpinBlurMaterial("mint_hub"));
        assertFalse(PlayerAssets.isSpinBlurMaterial("violet_armor"));
        assertFalse(PlayerAssets.isSpinBlurMaterial("violet_core"));
        assertFalse(PlayerAssets.isSpinBlurMaterial(null));
    }
}
