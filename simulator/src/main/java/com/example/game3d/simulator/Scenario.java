package com.example.game3d.simulator;

import com.example.game3d.core.input.FixedStepInput;
import com.example.game3d.core.input.PlayerInputEvent;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.simulation.PhysicsConfig;
import com.example.game3d.core.terrain.TerrainWorld;
import com.example.game3d.core.terrain.TerrainCommit;
import com.example.game3d.core.terrain.TerrainSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Scenario {
    public final String name;
    public final String description;
    public final TerrainWorld terrain;
    public final TerrainSnapshot terrainSnapshot;
    public final Vec3 initialPosition;
    public final Vec3 initialVelocity;
    public final double initialAngularVelocity;
    public final int initialAirJumpCharges;
    public final int defaultTicks;
    private final Map<Long, List<PlayerInputEvent>> inputsByTick;
    private final Map<Long, List<TerrainCommit>> commitsByTick;

    Scenario(String name, String description, TerrainWorld terrain, Vec3 initialPosition,
             Vec3 initialVelocity, int initialAirJumpCharges, int defaultTicks,
             List<PlayerInputEvent> inputs) {
        this(name, description, terrain, initialPosition, initialVelocity,
                0.0, initialAirJumpCharges, defaultTicks, inputs);
    }

    Scenario(String name, String description, TerrainWorld terrain, Vec3 initialPosition,
             Vec3 initialVelocity, double initialAngularVelocity,
             int initialAirJumpCharges, int defaultTicks,
             List<PlayerInputEvent> inputs) {
        this.name = name;
        this.description = description;
        this.terrain = terrain;
        this.terrainSnapshot = null;
        this.initialPosition = initialPosition;
        this.initialVelocity = initialVelocity;
        this.initialAngularVelocity = initialAngularVelocity;
        this.initialAirJumpCharges = initialAirJumpCharges;
        this.defaultTicks = defaultTicks;
        LinkedHashMap<Long, List<PlayerInputEvent>> grouped =
                new LinkedHashMap<Long, List<PlayerInputEvent>>();
        ArrayList<PlayerInputEvent> ordered = new ArrayList<PlayerInputEvent>(inputs);
        Collections.sort(ordered);
        for (PlayerInputEvent input : ordered) {
            long tick = firstTickAtOrAfter(input.timeNanos);
            List<PlayerInputEvent> bucket = grouped.get(tick);
            if (bucket == null) {
                bucket = new ArrayList<PlayerInputEvent>();
                grouped.put(tick, bucket);
            }
            bucket.add(input);
        }
        inputsByTick = Collections.unmodifiableMap(grouped);
        commitsByTick = Collections.emptyMap();
    }

    Scenario(
            String name,
            String description,
            TerrainWorld terrain,
            TerrainSnapshot terrainSnapshot,
            Vec3 initialPosition,
            Vec3 initialVelocity,
            double initialAngularVelocity,
            int initialAirJumpCharges,
            int defaultTicks,
            List<PlayerInputEvent> inputs,
            Map<Long, List<TerrainCommit>> scheduledCommits) {
        this.name = name;
        this.description = description;
        this.terrain = terrain;
        this.terrainSnapshot = terrainSnapshot;
        this.initialPosition = initialPosition;
        this.initialVelocity = initialVelocity;
        this.initialAngularVelocity = initialAngularVelocity;
        this.initialAirJumpCharges = initialAirJumpCharges;
        this.defaultTicks = defaultTicks;
        LinkedHashMap<Long, List<PlayerInputEvent>> groupedInputs =
                new LinkedHashMap<Long, List<PlayerInputEvent>>();
        ArrayList<PlayerInputEvent> ordered = new ArrayList<PlayerInputEvent>(inputs);
        Collections.sort(ordered);
        for (PlayerInputEvent input : ordered) {
            long tick = firstTickAtOrAfter(input.timeNanos);
            List<PlayerInputEvent> bucket = groupedInputs.get(tick);
            if (bucket == null) {
                bucket = new ArrayList<PlayerInputEvent>();
                groupedInputs.put(tick, bucket);
            }
            bucket.add(input);
        }
        inputsByTick = Collections.unmodifiableMap(groupedInputs);
        LinkedHashMap<Long, List<TerrainCommit>> groupedCommits =
                new LinkedHashMap<Long, List<TerrainCommit>>();
        for (Map.Entry<Long, List<TerrainCommit>> entry : scheduledCommits.entrySet()) {
            if (entry.getKey().longValue() < 0L) {
                throw new IllegalArgumentException("Commit tick cannot be negative");
            }
            groupedCommits.put(entry.getKey(),
                    Collections.unmodifiableList(
                            new ArrayList<TerrainCommit>(entry.getValue())));
        }
        commitsByTick = Collections.unmodifiableMap(groupedCommits);
    }

    public FixedStepInput inputForTick(long tick) {
        List<PlayerInputEvent> inputs = inputsByTick.get(tick);
        return inputs == null ? FixedStepInput.EMPTY : new FixedStepInput(inputs);
    }

    public List<TerrainCommit> commitsForTick(long tick) {
        List<TerrainCommit> commits = commitsByTick.get(tick);
        return commits == null
                ? Collections.<TerrainCommit>emptyList() : commits;
    }

    public boolean usesCanonicalTerrain() {
        return terrainSnapshot != null;
    }

    static long atMillis(long millis) {
        return millis * 1_000_000L;
    }

    private static long firstTickAtOrAfter(long timeNanos) {
        if (timeNanos == 0L) {
            return 0L;
        }
        return 1L + (timeNanos - 1L) / PhysicsConfig.FIXED_DT_NANOS;
    }
}
