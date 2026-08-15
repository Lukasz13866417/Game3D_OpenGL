package com.example.game3d.authoring.structures;

import com.example.game3d.authoring.AddonBlueprint;
import com.example.game3d.authoring.AdvancedTerrainStructure;
import com.example.game3d.authoring.Terrain;
import com.example.game3d.core.terrain.addon.Portal;

/** Straight obstacle section with a centered spike frame, potions, and optional portal pair. */
public class TerrainLineWithSpikeRect extends AdvancedTerrainStructure {
    private final String sourcePrefix;
    private final int lineTiles;
    private final boolean portalEncounterEnabled;
    private final String portalPairKey;
    private final ExitPortalStructure exitPortalStructure;

    public TerrainLineWithSpikeRect(int tilesToMake) {
        this("handwritten:terrain-line-with-spike-rect", tilesToMake, true);
    }

    public TerrainLineWithSpikeRect(int tilesToMake, boolean portalEncounterEnabled) {
        this("handwritten:terrain-line-with-spike-rect",
                tilesToMake, portalEncounterEnabled);
    }

    public TerrainLineWithSpikeRect(
            String sourcePrefix, int tilesToMake, boolean portalEncounterEnabled) {
        super(totalTiles(tilesToMake, portalEncounterEnabled));
        this.sourcePrefix = StructureSupport.requirePrefix(sourcePrefix);
        this.lineTiles = StructureSupport.requireNonNegative(tilesToMake, "tilesToMake");
        this.portalEncounterEnabled = portalEncounterEnabled;
        this.portalPairKey = sourcePrefix + ":portal-pair";
        this.exitPortalStructure = portalEncounterEnabled
                ? new ExitPortalStructure(sourcePrefix + ":exit-section",
                        PortalStructureDefaults.EXIT_STRUCTURE_ROWS, portalPairKey)
                : null;
        this.name = sourcePrefix;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        if (exitPortalStructure != null) {
            addChild(exitPortalStructure, brush);
        }
        for (int i = 0; i < lineTiles; i++) {
            brush.addSegment(StructureSupport.tileId(sourcePrefix, i));
        }
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int rows, int columns) {
        if (rows <= 0 || columns <= 0) {
            return;
        }
        int occupiedTopRows = exitPortalStructure == null
                ? 0 : Math.max(0, exitPortalStructure.getLastGeneratedRows());
        occupiedTopRows = Math.min(occupiedTopRows, Math.max(0, rows - 1));
        int freeRowStart = occupiedTopRows + 1;
        int freeRowCount = rows - occupiedTopRows;

        int sideSize = Math.min(columns - 2, freeRowCount - 2);
        if (sideSize >= 2) {
            int topRow = freeRowStart + (freeRowCount - sideSize) / 2;
            int leftColumn = 1 + (columns - sideSize) / 2;
            int sideLength = sideSize - 1;
            brush.reserveHorizontal(topRow, leftColumn, sideLength,
                    StructureSupport.spikes(sourcePrefix, "frame-top", sideLength));
            brush.reserveVertical(topRow, leftColumn + sideSize - 1, sideLength,
                    StructureSupport.spikes(sourcePrefix, "frame-right", sideLength));
            brush.reserveHorizontal(topRow + sideSize - 1, leftColumn + 1, sideLength,
                    StructureSupport.spikes(sourcePrefix, "frame-bottom", sideLength));
            brush.reserveVertical(topRow + 1, leftColumn, sideLength,
                    StructureSupport.spikes(sourcePrefix, "frame-left", sideLength));
        }

        for (int i = 0; i < 3; i++) {
            brush.reserveRandomFittingVertical(1,
                    StructureSupport.potions(sourcePrefix, "potion-" + i, 1));
        }

        if (portalEncounterEnabled) {
            if (PortalStructureDefaults.EXIT_STRUCTURE_ROWS
                    < PortalStructureDefaults.MIN_ENTRANCE_EXIT_ROW_GAP) {
                throw new IllegalStateException(
                        "EXIT_STRUCTURE_ROWS must cover the minimum portal row gap");
            }
            int length = Math.max(1, Math.min(
                    PortalStructureDefaults.CELLS_PER_PORTAL_SEGMENT, columns));
            brush.reserveRandomHorizontalRegion(length,
                    AddonBlueprint.portal(sourcePrefix + ":portal:entrance",
                            portalPairKey, Portal.Role.ENTRANCE));
        }
    }

    public boolean hasPortalEncounter() {
        return portalEncounterEnabled;
    }

    private static int totalTiles(int lineTiles, boolean portal) {
        StructureSupport.requireNonNegative(lineTiles, "tilesToMake");
        return Math.addExact(lineTiles,
                portal ? PortalStructureDefaults.EXIT_STRUCTURE_ROWS : 0);
    }
}
