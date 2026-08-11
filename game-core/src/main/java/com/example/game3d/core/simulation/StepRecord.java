package com.example.game3d.core.simulation;

import com.example.game3d.core.input.PlayerInputEvent;
import com.example.game3d.core.terrain.TerrainTriangle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StepRecord {
    public final PlayerSnapshot before;
    public final PlayerSnapshot after;
    public final List<PlayerInputEvent> inputs;
    public final List<TerrainTriangle> queriedTriangles;
    public final List<ContactSnapshot> contacts;
    public final List<MotionSegment> motionSegments;
    public final List<SpinSegment> spinSegments;
    public final List<JumpDecision> jumpEvaluations;
    public final List<SimulationEvent> events;

    StepRecord(PlayerSnapshot before, PlayerSnapshot after,
               List<PlayerInputEvent> inputs,
               List<TerrainTriangle> queriedTriangles,
               List<ContactSnapshot> contacts,
               List<MotionSegment> motionSegments,
               List<SpinSegment> spinSegments,
               List<JumpDecision> jumpEvaluations,
               List<SimulationEvent> events) {
        this.before = before;
        this.after = after;
        this.inputs = immutable(inputs);
        this.queriedTriangles = immutable(queriedTriangles);
        this.contacts = immutable(contacts);
        this.motionSegments = immutable(motionSegments);
        this.spinSegments = immutable(spinSegments);
        this.jumpEvaluations = immutable(jumpEvaluations);
        this.events = immutable(events);
    }

    private static <T> List<T> immutable(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<T>(source));
    }
}
