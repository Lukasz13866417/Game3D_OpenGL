package com.example.game3d.terrain.io.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** An authored addon reservation. Parameters are kind-specific and retain insertion order. */
public final class AddonReservation {
    private final String sourceId;
    private final AddonKind kind;
    private final Placement placement;
    private final String pairSourceId;
    private final Map<String, Double> parameters;

    public AddonReservation(String sourceId, AddonKind kind, Placement placement,
                            String pairSourceId, Map<String, Double> parameters) {
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.placement = Objects.requireNonNull(placement, "placement");
        this.pairSourceId = pairSourceId;
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(parameters, "parameters")));
    }

    public String sourceId() { return sourceId; }
    public AddonKind kind() { return kind; }
    public Placement placement() { return placement; }
    public String pairSourceId() { return pairSourceId; }
    public Map<String, Double> parameters() { return parameters; }
}
