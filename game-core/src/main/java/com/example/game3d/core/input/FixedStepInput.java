package com.example.game3d.core.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FixedStepInput {
    public static final FixedStepInput EMPTY =
            new FixedStepInput(Collections.<PlayerInputEvent>emptyList());

    public final List<PlayerInputEvent> events;

    public FixedStepInput(List<PlayerInputEvent> events) {
        ArrayList<PlayerInputEvent> copy = new ArrayList<PlayerInputEvent>(events);
        Collections.sort(copy);
        this.events = Collections.unmodifiableList(copy);
    }
}
