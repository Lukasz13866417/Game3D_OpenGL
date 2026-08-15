package com.example.game3d.terrain.io.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class StructureDocument implements TerrainSourceDocument {
    private final int formatVersion;
    private final String id;
    private final GridMode gridMode;
    private final List<TileRecord> tiles;
    private final List<AddonReservation> addons;

    public StructureDocument(int formatVersion, String id, GridMode gridMode,
                             List<TileRecord> tiles, List<AddonReservation> addons) {
        this.formatVersion = formatVersion;
        this.id = Objects.requireNonNull(id, "id");
        this.gridMode = Objects.requireNonNull(gridMode, "gridMode");
        this.tiles = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(tiles, "tiles")));
        this.addons = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(addons, "addons")));
    }

    @Override public int formatVersion() { return formatVersion; }
    @Override public String id() { return id; }
    public GridMode gridMode() { return gridMode; }
    public List<TileRecord> tiles() { return tiles; }
    public List<AddonReservation> addons() { return addons; }

    public StructureDocument withTiles(List<TileRecord> value) {
        return new StructureDocument(formatVersion, id, gridMode, value, addons);
    }

    public StructureDocument withAddons(List<AddonReservation> value) {
        return new StructureDocument(formatVersion, id, gridMode, tiles, value);
    }
}
