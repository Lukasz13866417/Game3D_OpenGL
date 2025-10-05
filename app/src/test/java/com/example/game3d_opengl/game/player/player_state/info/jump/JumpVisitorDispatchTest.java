package com.example.game3d_opengl.game.player.player_state.info.jump;

import org.junit.Test;

import static org.junit.Assert.*;

import com.example.game3d_opengl.game.player.player_state.infos.jump.PlayerJumpInfo;
import com.example.game3d_opengl.game.player.player_state.infos.jump.PlayerAllJumpVisitor;

public class JumpVisitorDispatchTest {

    private static class TestVisitorAll implements PlayerAllJumpVisitor {
        boolean baseCalled;
        boolean wantCalled;
        boolean footingCalled;
        boolean chargesCalled;
        boolean closeCalled;
        boolean spikeCalled;
        int lastCharges;
        float lastDistance;

        public void visit(PlayerJumpInfo.PlayerWantsJump i) { wantCalled = true; }
        public void visit(PlayerJumpInfo.PlayerHasFooting i) { footingCalled = true; }
        public void visit(PlayerJumpInfo.PlayerHasJumpCharges i) { chargesCalled = true; lastCharges = 3; }
        public void visit(PlayerJumpInfo.PlayerHitsGroundSoon i) { closeCalled = true; lastDistance = 0.15f; }
        public void visit(PlayerJumpInfo.PlayerHitsSpikeSoon i) { spikeCalled = true; }
    }

    @Test
    public void test_WantJump_dispatchesToTypedOverload() {
        TestVisitorAll v = new TestVisitorAll();
        new PlayerJumpInfo.PlayerWantsJump().accept(v);
        assertTrue(v.wantCalled);
        assertFalse(v.baseCalled);
    }

    @Test
    public void test_HasFooting_dispatchesToTypedOverload() {
        TestVisitorAll v = new TestVisitorAll();
        new PlayerJumpInfo.PlayerHasFooting().accept(v);
        assertTrue(v.footingCalled);
        assertFalse(v.baseCalled);
    }

    @Test
    public void test_HasJumpCharges_dispatchesToTypedOverload() {
        TestVisitorAll v = new TestVisitorAll();
        new PlayerJumpInfo.PlayerHasJumpCharges().accept(v);
        assertTrue(v.chargesCalled);
        assertEquals(3, v.lastCharges);
        assertFalse(v.baseCalled);
    }

    @Test
    public void test_CloseToGround_dispatchesToTypedOverload() {
        TestVisitorAll v = new TestVisitorAll();
        new PlayerJumpInfo.PlayerHitsGroundSoon().accept(v);
        assertTrue(v.closeCalled);
        assertEquals(0.15f, v.lastDistance, 1e-6f);
        assertFalse(v.baseCalled);
    }

    @Test
    public void test_SpikeBelow_dispatchesToTypedOverload() {
        TestVisitorAll v = new TestVisitorAll();
        new PlayerJumpInfo.PlayerHitsSpikeSoon().accept(v);
        assertTrue(v.spikeCalled);
        assertFalse(v.baseCalled);
    }
}
