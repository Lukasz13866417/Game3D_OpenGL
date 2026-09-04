package com.example.game3d.authoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Immutable, explicitly ordered source catalog used for deterministic level selection. */
public final class GameplayLevelCatalog {
    private static final String[] BUILTIN_IDS = {
            "stairs_curve_line",
            "curve_stairs",
            "boost_ramp",
            "double_curve_boost",
            "long_stair_arc",
            "rect_curve_sprint"
    };

    private final List<GameplayLevelProvider> entries;

    public GameplayLevelCatalog(List<GameplayLevelProvider> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("A gameplay catalog cannot be empty");
        }
        ArrayList<GameplayLevelProvider> copy =
                new ArrayList<GameplayLevelProvider>(entries);
        HashSet<String> ids = new HashSet<String>();
        for (GameplayLevelProvider entry : copy) {
            if (entry == null || entry.stableId() == null
                    || entry.stableId().isEmpty() || !ids.add(entry.stableId())) {
                throw new IllegalArgumentException("Catalog entries need unique stable IDs");
            }
        }
        this.entries = Collections.unmodifiableList(copy);
    }

    public static GameplayLevelCatalog builtIns() {
        ArrayList<GameplayLevelProvider> providers =
                new ArrayList<GameplayLevelProvider>(BUILTIN_IDS.length);
        for (int i = 0; i < BUILTIN_IDS.length; i++) {
            final int templateIndex = i;
            final String id = BUILTIN_IDS[i];
            providers.add(new GameplayLevelProvider() {
                @Override
                public String stableId() {
                    return id;
                }

                @Override
                public BaseTerrainStructure<?> create(long levelOrdinal) {
                    return GameplayTerrainFactory.gameplayTemplate(
                            templateIndex, safeOrdinal(levelOrdinal));
                }
            });
        }
        return new GameplayLevelCatalog(providers);
    }

    public List<GameplayLevelProvider> entries() {
        return entries;
    }

    public GameplayLevelProvider select(long levelOrdinal) {
        long safe = Math.max(0L, levelOrdinal);
        long mixed = mix64(safe + 0x9e3779b97f4a7c15L);
        int selected = (int) Math.floorMod(mixed, (long) entries.size());
        return entries.get(selected);
    }

    /** Resolves one stable provider ID without involving ordinal-based random selection. */
    public GameplayLevelProvider require(String stableId) {
        if (stableId == null || stableId.isEmpty()) {
            throw new IllegalArgumentException("Gameplay provider ID is empty");
        }
        for (GameplayLevelProvider entry : entries) {
            if (stableId.equals(entry.stableId())) {
                return entry;
            }
        }
        throw new IllegalArgumentException(
                "Unknown gameplay terrain provider '" + stableId + "'");
    }

    public GameplayLevelCatalog withAdditionalEntries(
            List<GameplayLevelProvider> additions) {
        if (additions == null || additions.isEmpty()) {
            return this;
        }
        ArrayList<GameplayLevelProvider> combined =
                new ArrayList<GameplayLevelProvider>(entries);
        combined.addAll(additions);
        return new GameplayLevelCatalog(combined);
    }

    private static int safeOrdinal(long ordinal) {
        return ordinal > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, ordinal);
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }
}
