package com.example.game3d.simulator;

import com.example.game3d.core.simulation.PhysicsConfig;
import com.example.game3d.core.simulation.SimulationEngine;
import com.example.game3d.core.simulation.StepObserver;
import com.example.game3d.core.simulation.JumpRuleId;
import com.example.game3d.core.simulation.SimulationEvent;
import com.example.game3d.core.simulation.StepResult;
import com.example.game3d.core.input.PlayerInputEvent;
import com.example.game3d.core.terrain.TerrainCommit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScenarioRegistryTest {
    @Test
    public void scenariosRetainTheirExplicitInitialAirJumpCharges() {
        ScenarioRegistry registry = new ScenarioRegistry();

        assertEquals(1, registry.require("gap_recovery").initialAirJumpCharges);
        assertEquals(1, registry.require("spike_avoidance").initialAirJumpCharges);
        assertEquals(0, registry.require("feather_collection").initialAirJumpCharges);
    }

    @Test
    public void allScenariosUseCanonicalTerrainAndRun() {
        PhysicsConfig config = new PhysicsConfig();
        for (Scenario scenario : new ScenarioRegistry().all()) {
            assertTrue(scenario.usesCanonicalTerrain());
            assertFalse(scenario.terrainSnapshot.segments.isEmpty());
            SimulationEngine engine = new SimulationEngine(
                    scenario.terrainSnapshot, config, scenario.initialPosition,
                    scenario.initialVelocity,
                    scenario.initialAngularVelocity,
                    scenario.initialAirJumpCharges, StepObserver.NONE);
            for (int tick = 0; tick < Math.min(30, scenario.defaultTicks); tick++) {
                for (TerrainCommit commit : scenario.commitsForTick(tick)) {
                    engine.applyTerrainCommit(commit);
                }
                engine.step(scenario.inputForTick(tick));
            }
            assertTrue(engine.snapshot().tick > 0);
        }
    }

    @Test
    public void recoveryAndSpikeForecastScenariosSpendOnePersistentCharge() {
        assertAirJump("gap_recovery", JumpRuleId.AIRBORNE_UNRECOVERABLE);
        assertAirJump("spike_avoidance", JumpRuleId.AIRBORNE_SPIKE_FIRST);
    }

    @Test
    public void scheduledInputNeverAffectsPhysicsBeforeItsTimestamp() {
        Scenario scenario = new ScenarioRegistry().require("ground_jump");

        assertFalse(containsRelease(scenario.inputForTick(34).events));
        assertTrue(containsRelease(scenario.inputForTick(35).events));
    }

    @Test
    public void jumpChargeXGuardScenariosBracketBothPhysicalLimits() {
        PhysicsConfig config = new PhysicsConfig();
        ScenarioRegistry registry = new ScenarioRegistry();

        PlayerInputEvent accepted = onlySwipe(
                registry.require("jump_charge_x_boundary_accept"));
        double acceptedX = Math.abs(accepted.rawDeltaXScreenHeights);
        double acceptedUp = -accepted.rawDeltaYScreenHeights;
        assertTrue(acceptedX < config.maxJumpChargeXScreenHeights);
        assertTrue(acceptedX < config.maxJumpChargeXToYRatio * acceptedUp);
        assertTrue("accepted case is not close to the absolute boundary",
                acceptedX >= config.maxJumpChargeXScreenHeights * 0.98);

        PlayerInputEvent ratioRejected = onlySwipe(
                registry.require("jump_charge_x_ratio_reject"));
        double ratioRejectedX = Math.abs(ratioRejected.rawDeltaXScreenHeights);
        double ratioRejectedUp = -ratioRejected.rawDeltaYScreenHeights;
        assertTrue(ratioRejectedX < config.maxJumpChargeXScreenHeights);
        assertTrue(ratioRejectedX
                > config.maxJumpChargeXToYRatio * ratioRejectedUp);

        PlayerInputEvent absoluteRejected = onlySwipe(
                registry.require("jump_charge_x_absolute_reject"));
        double absoluteRejectedX = Math.abs(absoluteRejected.rawDeltaXScreenHeights);
        double absoluteRejectedUp = -absoluteRejected.rawDeltaYScreenHeights;
        assertTrue(absoluteRejectedX > config.maxJumpChargeXScreenHeights);
        assertTrue(absoluteRejectedX
                < config.maxJumpChargeXToYRatio * absoluteRejectedUp);
    }

    private static void assertAirJump(String scenarioName, JumpRuleId expectedRule) {
        Scenario scenario = new ScenarioRegistry().require(scenarioName);
        SimulationEngine engine = new SimulationEngine(
                scenario.terrainSnapshot, new PhysicsConfig(), scenario.initialPosition,
                scenario.initialVelocity, scenario.initialAngularVelocity,
                scenario.initialAirJumpCharges, StepObserver.NONE);
        boolean found = false;
        for (int tick = 0; tick < 180; tick++) {
            for (TerrainCommit commit : scenario.commitsForTick(tick)) {
                engine.applyTerrainCommit(commit);
            }
            StepResult result = engine.step(scenario.inputForTick(tick));
            for (SimulationEvent event : result.events) {
                if (event.type == SimulationEvent.Type.JUMP
                        && result.jumpDecision.rule == expectedRule) {
                    found = true;
                }
            }
            if (found) break;
        }
        assertTrue("missing " + expectedRule + " in " + scenarioName, found);
        assertTrue(!engine.snapshot().dead);
        assertTrue(engine.snapshot().airJumpCharges == 0);
    }

    private static boolean containsRelease(
            java.util.List<PlayerInputEvent> events) {
        for (PlayerInputEvent event : events) {
            if (event.type == PlayerInputEvent.Type.TOUCH_UP) {
                return true;
            }
        }
        return false;
    }

    private static PlayerInputEvent onlySwipe(Scenario scenario) {
        PlayerInputEvent found = null;
        for (long tick = 0; tick < scenario.defaultTicks; tick++) {
            for (PlayerInputEvent event : scenario.inputForTick(tick).events) {
                if (event.type != PlayerInputEvent.Type.SWIPE) {
                    continue;
                }
                assertTrue("scenario contains more than one swipe", found == null);
                found = event;
            }
        }
        assertTrue("scenario contains no swipe", found != null);
        assertTrue("guard scenario swipe must move upward",
                found.rawDeltaYScreenHeights < 0.0);
        return found;
    }
}
