package com.example.game3d.core.simulation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Durable immutable collectible activity state used by renderers and replay tools. */
public final class FeatureActivitySnapshot {
    private final Set<Long> inactiveFeatureIds;
    public final List<Long> sortedInactiveFeatureIds;

    FeatureActivitySnapshot(Collection<Long> inactiveFeatureIds) {
        ArrayList<Long> sorted = new ArrayList<Long>(inactiveFeatureIds);
        Collections.sort(sorted);
        this.sortedInactiveFeatureIds = Collections.unmodifiableList(sorted);
        this.inactiveFeatureIds = Collections.unmodifiableSet(
                new HashSet<Long>(inactiveFeatureIds));
    }

    public boolean isActive(long featureId) {
        return !inactiveFeatureIds.contains(featureId);
    }

    public boolean isInactive(long featureId) {
        return inactiveFeatureIds.contains(featureId);
    }
}
