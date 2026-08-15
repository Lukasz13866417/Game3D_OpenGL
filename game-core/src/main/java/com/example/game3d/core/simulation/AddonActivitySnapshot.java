package com.example.game3d.core.simulation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Durable immutable addon activity state used by renderers and replay tools. */
public final class AddonActivitySnapshot {
    private final Set<Long> inactiveAddonIds;
    public final List<Long> sortedInactiveAddonIds;

    AddonActivitySnapshot(Collection<Long> inactiveAddonIds) {
        ArrayList<Long> sorted = new ArrayList<Long>(inactiveAddonIds);
        Collections.sort(sorted);
        this.sortedInactiveAddonIds = Collections.unmodifiableList(sorted);
        this.inactiveAddonIds = Collections.unmodifiableSet(
                new HashSet<Long>(inactiveAddonIds));
    }

    /** Creates presentation/replay activity state from authoritative inactive addon IDs. */
    public static AddonActivitySnapshot ofInactiveAddonIds(
            Collection<Long> inactiveAddonIds) {
        if (inactiveAddonIds == null) {
            throw new IllegalArgumentException("inactiveAddonIds == null");
        }
        return new AddonActivitySnapshot(inactiveAddonIds);
    }

    public boolean isActive(long addonId) {
        return !inactiveAddonIds.contains(addonId);
    }

    public boolean isInactive(long addonId) {
        return inactiveAddonIds.contains(addonId);
    }
}
