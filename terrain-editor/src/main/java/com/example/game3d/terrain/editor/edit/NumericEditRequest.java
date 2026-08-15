package com.example.game3d.terrain.editor.edit;

import java.util.Objects;

/** Validated user intent for one compound multi-tile numeric edit. */
public record NumericEditRequest(
        TileEdits.Field field,
        TileEdits.Mode mode,
        double start,
        double increment) {
    public NumericEditRequest {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(mode, "mode");
        if (!Double.isFinite(start) || !Double.isFinite(increment))
            throw new IllegalArgumentException("Numeric edit values must be finite");
        if (mode != TileEdits.Mode.LINEAR_SEQUENCE && increment != 0.0)
            throw new IllegalArgumentException("Only a linear sequence has an increment");
    }
}
