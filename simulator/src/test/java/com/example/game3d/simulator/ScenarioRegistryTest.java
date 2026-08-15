package com.example.game3d.simulator;

import com.example.game3d.core.simulation.PhysicsConfig;
import com.example.game3d.core.simulation.SimulationEngine;
import com.example.game3d.core.simulation.StepObserver;
import com.example.game3d.core.simulation.JumpRuleId;
import com.example.game3d.core.simulation.SimulationEvent;
import com.example.game3d.core.simulation.StepResult;
import com.example.game3d.core.input.PlayerInputEvent;
import com.example.game3d.core.terrain.TerrainCommit;
import com.example.game3d.authoring.AdvancedTerrainStructure;
import com.example.game3d.authoring.BaseTerrainStructure;
import com.example.game3d.authoring.GameplayLevelCatalog;
import com.example.game3d.authoring.GameplayLevelProvider;
import com.example.game3d.authoring.Terrain;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ScenarioRegistryTest {
    @Test
    public void publishedCatalogIsPackagedForWorkingDirectoryIndependentRuns()
            throws Exception {
        try (java.io.InputStream input = ScenarioRegistry.class.getResourceAsStream(
                ScenarioRegistry.PACKAGED_TERRAIN_CATALOG)) {
            assertNotNull(input);
            try (java.io.Reader reader = new java.io.InputStreamReader(
                    input, java.nio.charset.StandardCharsets.UTF_8)) {
                assertEquals(GameplayLevelCatalog.builtIns().entries().size(),
                        new com.example.game3d.terrain.io.publish
                                .PublishedGameplayCatalogLoader()
                                .load(reader).entries().size());
            }
        }
    }

    @Test
    public void scenariosRetainTheirExplicitInitialAirJumpCharges() {
        ScenarioRegistry registry = new ScenarioRegistry();

        assertEquals(1, registry.require("gap_recovery").initialAirJumpCharges);
        assertEquals(1, registry.require("spike_avoidance").initialAirJumpCharges);
        assertEquals(0, registry.require("feather_collection").initialAirJumpCharges);
    }

    @Test
    public void publishedCatalogScenarioMaterializesTheCustomExtension() {
        GameplayLevelProvider custom = new GameplayLevelProvider() {
            @Override public String stableId() {
                return "simulator_custom_three_tiles";
            }

            @Override public BaseTerrainStructure<?> create(long levelOrdinal) {
                return new AdvancedTerrainStructure(3) {
                    @Override protected void generateTiles(Terrain.TileBrush brush) {
                        brush.addSegment("custom-0");
                        brush.addSegment("custom-1");
                        brush.addSegment("custom-2");
                    }

                    @Override protected void generateAddons(
                            Terrain.AdvancedGridBrush brush, int rows, int columns) {
                    }
                };
            }
        };
        GameplayLevelCatalog catalog = GameplayLevelCatalog.builtIns()
                .withAdditionalEntries(Collections.singletonList(custom));

        Scenario scenario = new ScenarioRegistry(catalog)
                .require("published_catalog_level");

        assertEquals(3, scenario.terrainSnapshot.segments.size());
        assertEquals(0L, scenario.terrainSnapshot.segments.get(0).id);
        assertEquals(2L, scenario.terrainSnapshot.segments.get(2).id);
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
    public void jumpChargeScenariosBracketThePerMovementVerticalRatio() {
        PhysicsConfig config = new PhysicsConfig();
        ScenarioRegistry registry = new ScenarioRegistry();

        PlayerInputEvent accepted = onlySwipe(
                registry.require("jump_charge_x_boundary_accept"));
        double acceptedX = Math.abs(accepted.rawDeltaXScreenHeights);
        double acceptedUp = -accepted.rawDeltaYScreenHeights;
        assertTrue(acceptedX < config.maxJumpChargeXToYRatio * acceptedUp);
        assertTrue("accepted case is not close to the legacy absolute boundary",
                acceptedX >= config.maxJumpChargeXScreenHeights * 0.98);

        PlayerInputEvent ratioRejected = onlySwipe(
                registry.require("jump_charge_x_ratio_reject"));
        double ratioRejectedX = Math.abs(ratioRejected.rawDeltaXScreenHeights);
        double ratioRejectedUp = -ratioRejected.rawDeltaYScreenHeights;
        assertTrue(ratioRejectedX < config.maxJumpChargeXScreenHeights);
        assertTrue(ratioRejectedX
                > config.maxJumpChargeXToYRatio * ratioRejectedUp);

        PlayerInputEvent largeAccepted = onlySwipe(
                registry.require("jump_charge_x_large_accept"));
        double largeAcceptedX = Math.abs(largeAccepted.rawDeltaXScreenHeights);
        double largeAcceptedUp = -largeAccepted.rawDeltaYScreenHeights;
        assertTrue(largeAcceptedX > config.maxJumpChargeXScreenHeights);
        assertTrue(largeAcceptedX
                < config.maxJumpChargeXToYRatio * largeAcceptedUp);
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
