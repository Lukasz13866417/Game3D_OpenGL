package com.example.game3d.simulator;

import com.example.game3d.core.simulation.PhysicsConfig;
import com.example.game3d.core.simulation.PlayerSnapshot;
import com.example.game3d.core.simulation.SimulationEngine;
import com.example.game3d.core.simulation.StepObserver;
import com.example.game3d.core.simulation.StepResult;
import com.example.game3d.core.terrain.TerrainCommit;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/** Guards against a deterministic but behavior-changing production-terrain refactor. */
public final class GeneratedGameplayGoldenTest {
    private static final String FIXTURE =
            "/com/example/game3d/simulator/generated-gameplay-state-hashes-v1.txt";

    @Test
    public void generatedGameplayStreamMatchesCheckedInStateHashes() throws Exception {
        Scenario scenario = new ScenarioRegistry().require("generated_gameplay_stream");
        PhysicsConfig config = new PhysicsConfig();
        SimulationEngine engine = new SimulationEngine(
                scenario.terrainSnapshot,
                config,
                scenario.initialPosition,
                scenario.initialVelocity,
                scenario.initialAngularVelocity,
                scenario.initialAirJumpCharges,
                StepObserver.NONE);
        Map<Long, Expected> expectedByTick = readFixture();

        for (int tick = 0; tick < scenario.defaultTicks; tick++) {
            for (TerrainCommit commit : scenario.commitsForTick(tick)) {
                engine.applyTerrainCommit(commit);
            }
            StepResult result = engine.step(scenario.inputForTick(tick));
            Expected expected = expectedByTick.remove(result.snapshot.tick);
            if (expected != null) {
                assertCheckpoint(expected, result.snapshot, engine.terrainRevision());
            }
        }

        assertEquals("Fixture contains a checkpoint beyond the scenario", 0,
                expectedByTick.size());
    }

    private static Map<Long, Expected> readFixture() throws Exception {
        InputStream stream = GeneratedGameplayGoldenTest.class.getResourceAsStream(FIXTURE);
        assertNotNull("Missing fixture " + FIXTURE, stream);
        LinkedHashMap<Long, Expected> result = new LinkedHashMap<Long, Expected>();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] fields = trimmed.split("\\s+");
                if (fields.length != 4) {
                    throw new IllegalStateException("Invalid golden checkpoint: " + line);
                }
                long tick = Long.parseLong(fields[0]);
                Expected previous = result.put(tick, new Expected(
                        fields[1], Long.parseLong(fields[2]), Long.parseLong(fields[3])));
                if (previous != null) {
                    throw new IllegalStateException("Duplicate golden tick " + tick);
                }
            }
        } finally {
            reader.close();
        }
        return result;
    }

    private static void assertCheckpoint(
            Expected expected, PlayerSnapshot actual, long terrainRevision) {
        assertEquals(expected.unsignedStateHash,
                Long.toUnsignedString(actual.stateHash));
        assertEquals(expected.supportSegmentId, actual.supportSegmentId);
        assertEquals(expected.terrainRevision, terrainRevision);
    }

    private static final class Expected {
        final String unsignedStateHash;
        final long supportSegmentId;
        final long terrainRevision;

        Expected(String unsignedStateHash, long supportSegmentId, long terrainRevision) {
            this.unsignedStateHash = unsignedStateHash;
            this.supportSegmentId = supportSegmentId;
            this.terrainRevision = terrainRevision;
        }
    }
}
