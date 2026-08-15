package com.example.game3d.authoring.structures;

import com.example.game3d.authoring.AddonBlueprint;

/** Shared validation and stable local source-ID helpers for handwritten structures. */
final class StructureSupport {
    private StructureSupport() {}

    static String requirePrefix(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("sourcePrefix is empty");
        }
        return value;
    }

    static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }

    static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
        return value;
    }

    static float requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    static String tileId(String prefix, int ordinal) {
        return prefix + ":tile:" + ordinal;
    }

    static String addonId(String prefix, String group, int ordinal) {
        return prefix + ":" + group + ":" + ordinal;
    }

    static AddonBlueprint[] spikes(String prefix, String group, int count) {
        AddonBlueprint[] result = new AddonBlueprint[count];
        for (int i = 0; i < count; i++) {
            result[i] = AddonBlueprint.deathSpike(addonId(prefix, group, i));
        }
        return result;
    }

    static AddonBlueprint[] potions(String prefix, String group, int count) {
        AddonBlueprint[] result = new AddonBlueprint[count];
        for (int i = 0; i < count; i++) {
            result[i] = AddonBlueprint.airJumpPotion(addonId(prefix, group, i));
        }
        return result;
    }
}
