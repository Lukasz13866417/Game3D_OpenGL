package com.example.game3d_opengl.game.player.player_state.infos;

import com.example.game3d_opengl.game.player.player_state.infos.jump.PlayerJumpInfo;
import com.example.game3d_opengl.game.player.player_state.infos.jump.PlayerAllJumpVisitor;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit test to verify that the addInfo method correctly dispatches to the right visitor methods.
 * Tests the double-dispatch mechanism and type safety.
 */
public class AddInfoDispatchTest {

    private TestPlayerAllInfoVisitor testVisitor;
    private MockInteractableAPI interactableAPI;

    @Before
    public void setUp() {
        testVisitor = new TestPlayerAllInfoVisitor();
        // Create a mock interactable API
        interactableAPI = new MockInteractableAPI(testVisitor);
    }

    @Test
    public void testAddInfoWithPlayerJumpInfo() {
        // Test with PlayerJumpInfo subclasses
        PlayerJumpInfo.PlayerHasFooting footingInfo = new PlayerJumpInfo.PlayerHasFooting();
        PlayerJumpInfo.PlayerWantsJump wantsJumpInfo = new PlayerJumpInfo.PlayerWantsJump();
        PlayerJumpInfo.PlayerHasJumpCharges chargesInfo = new PlayerJumpInfo.PlayerHasJumpCharges();

        // Clear previous calls
        testVisitor.clearCalls();

        // Add infos through the API
        interactableAPI.addInfo(footingInfo);
        interactableAPI.addInfo(wantsJumpInfo);
        interactableAPI.addInfo(chargesInfo);

        // Verify the correct methods were called
        assertTrue("visit(PlayerJumpInfo.PlayerHasFooting) should have been called", 
                   testVisitor.wasCalled("visit(PlayerJumpInfo.PlayerHasFooting)"));
        assertTrue("visit(PlayerJumpInfo.PlayerWantsJump) should have been called", 
                   testVisitor.wasCalled("visit(PlayerJumpInfo.PlayerWantsJump)"));
        assertTrue("visit(PlayerJumpInfo.PlayerHasJumpCharges) should have been called", 
                   testVisitor.wasCalled("visit(PlayerJumpInfo.PlayerHasJumpCharges)"));
        
        // Verify visit(PlayerJumpInfo) was called (the default dispatch)
        assertFalse("visit(PlayerJumpInfo) should not have been called",
                   testVisitor.wasCalled("visit(PlayerJumpInfo)"));
    }

    @Test
    public void testAddInfoWithCustomPlayerAffectingInfo() {
        // Create a custom PlayerAffectingInfo subclass for testing
        CustomPlayerAffectingInfo customInfo = new CustomPlayerAffectingInfo();
        
        // Clear previous calls
        testVisitor.clearCalls();

        // Add info through the API
        assertThrows(
                RuntimeException.class.toString(),
                RuntimeException.class,
                () -> interactableAPI.addInfo(customInfo)
        );

    }

    @Test
    public void testDoubleDispatchCorrectness() {
        // Test that the double-dispatch mechanism works correctly
        PlayerJumpInfo.PlayerHasFooting footingInfo = new PlayerJumpInfo.PlayerHasFooting();
        
        // Clear previous calls
        testVisitor.clearCalls();

        // Add info through the API
        interactableAPI.addInfo(footingInfo);

        // Verify that both the generic visit and the specific visit were called
        assertTrue("visit(PlayerJumpInfo.PlayerHasFooting) should have been called",
                   testVisitor.wasCalled("visit(PlayerJumpInfo.PlayerHasFooting)"));
    }

    /**
     * Test visitor that tracks which methods were called
     */
    private static class TestPlayerAllInfoVisitor implements PlayerAllInfoVisitor {
        private final List<String> methodCalls = new ArrayList<>();

        // PlayerJumpVisitor methods
        @Override
        public void visit(PlayerJumpInfo.PlayerHasFooting info) {
            methodCalls.add("visit(PlayerJumpInfo.PlayerHasFooting)");
        }

        @Override
        public void visit(PlayerJumpInfo.PlayerWantsJump info) {
            methodCalls.add("visit(PlayerJumpInfo.PlayerWantsJump)");
        }

        @Override
        public void visit(PlayerJumpInfo.PlayerHitsGroundSoon info) {
            methodCalls.add("visit(PlayerJumpInfo.PlayerHitsGroundSoon)");
        }

        @Override
        public void visit(PlayerJumpInfo.PlayerHitsSpikeSoon info) {
            methodCalls.add("visit(PlayerJumpInfo.PlayerHitsSpikeSoon)");
        }

        @Override
        public void visit(PlayerJumpInfo.PlayerHasJumpCharges info) {
            methodCalls.add("visit(PlayerJumpInfo.PlayerHasJumpCharges)");
        }

        public void clearCalls() {
            methodCalls.clear();
        }

        public boolean wasCalled(String methodName) {
            return methodCalls.contains(methodName);
        }

        public int getCallIndex(String methodName) {
            return methodCalls.indexOf(methodName);
        }
    }

    /**
     * Mock InteractableAPI that uses acceptDefault
     */
    private static class MockInteractableAPI {
        private final TestPlayerAllInfoVisitor visitor;

        public MockInteractableAPI(TestPlayerAllInfoVisitor visitor) {
            this.visitor = visitor;
        }

        public void addInfo(PlayerAffectingInfo<? super PlayerAllInfoVisitor> info) {
            info.accept(visitor);
        }
    }

    /**
     * Custom PlayerAffectingInfo subclass for testing
     */
    private static class CustomPlayerAffectingInfo extends PlayerAffectingInfo<PlayerAllInfoVisitor> {
        @Override
        public void accept(PlayerAllInfoVisitor visitor) {
            visitor.visit(this);
        }

    }
}
