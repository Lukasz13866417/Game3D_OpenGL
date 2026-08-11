package com.example.game3d.core.simulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StepResult {
    public final PlayerSnapshot snapshot;
    public final List<SimulationEvent> events;
    public final JumpDecision jumpDecision;

    StepResult(PlayerSnapshot snapshot, List<SimulationEvent> events,
               JumpDecision jumpDecision) {
        this.snapshot = snapshot;
        this.events = Collections.unmodifiableList(new ArrayList<SimulationEvent>(events));
        this.jumpDecision = jumpDecision;
    }
}
