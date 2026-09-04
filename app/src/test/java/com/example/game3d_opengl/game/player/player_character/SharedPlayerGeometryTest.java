package com.example.game3d_opengl.game.player.player_character;

import com.example.game3d.core.simulation.PhysicsConfig;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
}
