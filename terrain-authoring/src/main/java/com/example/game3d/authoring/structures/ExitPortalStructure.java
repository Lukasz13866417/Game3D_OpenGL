package com.example.game3d.authoring.structures;

import com.example.game3d.authoring.AddonBlueprint;
import com.example.game3d.authoring.AdvancedTerrainStructure;
import com.example.game3d.authoring.Terrain;
import com.example.game3d.core.terrain.addon.Portal;

/** Dedicated child section for an exit portal and its protected headspace. */
public class ExitPortalStructure extends AdvancedTerrainStructure {
    private final String sourcePrefix;
    private final String pairKey;
    private int lastGeneratedRows = -1;

    public ExitPortalStructure(String pairKey) {
        this("handwritten:exit-portal", PortalStructureDefaults.EXIT_STRUCTURE_ROWS,
                pairKey);
    }

    public ExitPortalStructure(int tilesToMake, String pairKey) {
        this("handwritten:exit-portal", tilesToMake, pairKey);
    }

    public ExitPortalStructure(
            String sourcePrefix, int tilesToMake, String pairKey) {
        super(StructureSupport.requirePositive(tilesToMake, "tilesToMake"));
        this.sourcePrefix = StructureSupport.requirePrefix(sourcePrefix);
        this.pairKey = StructureSupport.requirePrefix(pairKey);
        this.name = sourcePrefix;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        for (int i = 0; i < tilesToMake; i++) {
            brush.addSegment(StructureSupport.tileId(sourcePrefix, i));
        }
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int rows, int columns) {
        lastGeneratedRows = rows;
        if (rows <= 0 || columns <= 0) {
            return;
        }
        int portalLength = Math.max(1,
                Math.min(PortalStructureDefaults.CELLS_PER_PORTAL_SEGMENT, columns));
        int portalColumn = centeredStartColumn(columns, portalLength);
        brush.reserveHorizontalRegion(1, portalColumn, portalLength,
                AddonBlueprint.portal(sourcePrefix + ":portal:exit",
                        pairKey, Portal.Role.EXIT));

        int potionRow = manualPotionRow(rows);
        if (potionRow >= 2 && potionRow <= rows) {
            brush.reserveVertical(potionRow, centeredStartColumn(columns, 1), 1,
                    new AddonBlueprint[] {
                            AddonBlueprint.airJumpPotion(sourcePrefix + ":potion:0")
                    });
        }
    }

    @Override
    protected int[] getParentBlockedRowsRange(int rows, int columns) {
        return rows == 0 ? null : new int[] {1, rows};
    }

    @Override
    protected boolean shouldPropagateReservationsToParent() {
        return false;
    }

    public int getLastGeneratedRows() {
        return lastGeneratedRows;
    }

    private static int centeredStartColumn(int columns, int length) {
        int maxStart = columns - length + 1;
        return Math.max(1, 1 + Math.max(0, maxStart - 1) / 2);
    }

    private static int manualPotionRow(int rows) {
        if (rows < 2) {
            return -1;
        }
        int desired = 1 + Math.max(1,
                PortalStructureDefaults.MIN_ENTRANCE_EXIT_ROW_GAP / 2);
        return Math.min(rows, desired);
    }
}
