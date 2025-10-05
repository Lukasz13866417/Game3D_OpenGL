package com.example.game3d_opengl.game.player.player_state.infos;

import org.junit.Test;

import static org.junit.Assert.*;

import com.example.game3d_opengl.game.player.player_state.infos.jump.PlayerJumpInfo;
import com.example.game3d_opengl.game.player.player_state.infos.jump.PlayerAllJumpLogicImplementation;

public class PlayerJumpLogicImplementationTest {

    @Test
    public void wants_then_footing_results_in_jump_yes1() {
        PlayerAllJumpLogicImplementation impl = new PlayerAllJumpLogicImplementation();
        impl.resetFrame();
        new PlayerJumpInfo.PlayerWantsJump().accept(impl);
        new PlayerJumpInfo.PlayerHasFooting().accept(impl);
        assertTrue(impl.shouldJump());
        // cached result stays consistent on repeated calls
        assertTrue(impl.shouldJump());
    }

    @Test
    public void wants_then_charges_then_ground_then_spike_results_in_yes2() {
        PlayerAllJumpLogicImplementation impl = new PlayerAllJumpLogicImplementation();
        impl.resetFrame();
        // Provide in scrambled order to verify explicit ordering is used
        new PlayerJumpInfo.PlayerHitsSpikeSoon().accept(impl);
        new PlayerJumpInfo.PlayerHasJumpCharges().accept(impl);
        new PlayerJumpInfo.PlayerWantsJump().accept(impl);
        new PlayerJumpInfo.PlayerHitsGroundSoon().accept(impl);
        assertTrue(impl.shouldJump());
    }

    @Test
    public void missing_want_means_no_jump_even_with_other_infos() {
        PlayerAllJumpLogicImplementation impl = new PlayerAllJumpLogicImplementation();
        impl.resetFrame();
        new PlayerJumpInfo.PlayerHasFooting().accept(impl);
        new PlayerJumpInfo.PlayerHasJumpCharges().accept(impl);
        assertFalse(impl.shouldJump());
    }

    @Test
    public void reset_clears_cached_state() {
        PlayerAllJumpLogicImplementation impl = new PlayerAllJumpLogicImplementation();
        impl.resetFrame();
        new PlayerJumpInfo.PlayerWantsJump().accept(impl);
        new PlayerJumpInfo.PlayerHasFooting().accept(impl);
        assertTrue(impl.shouldJump());

        // Next frame: different inputs lead to different result
        impl.resetFrame();
        new PlayerJumpInfo.PlayerHasFooting().accept(impl);
        assertFalse(impl.shouldJump());
    }
}


