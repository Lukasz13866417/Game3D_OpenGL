package com.example.game3d_opengl.game.terrain.terrain_structures;

import static java.lang.Math.max;
import static java.lang.Math.min;

import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.terrain_api.main.AdvancedTerrainStructure;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.track_elements.portal.ExitPortal;
import com.example.game3d_opengl.game.terrain.track_elements.portal.PortalConfig;
import com.example.game3d_opengl.game.terrain.track_elements.potion.Potion;

/**
 * Dedicated child structure for exit-portal placement and controlled headspace.
 */
public class ExitPortalStructure extends AdvancedTerrainStructure {

    private final ExitPortal exitPortal;
    private int lastGeneratedRows = -1;

    public ExitPortalStructure(ExitPortal exitPortal) {
        this(PortalConfig.EXIT_STRUCTURE_ROWS, exitPortal);
    }

    public ExitPortalStructure(int nTiles, ExitPortal exitPortal) {
        super(nTiles);
        if (exitPortal == null) {
            throw new IllegalArgumentException("exitPortal must not be null");
        }
        this.exitPortal = exitPortal;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        for (int i = 0; i < tilesToMake; ++i) {
            brush.addSegment();
        }
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
        lastGeneratedRows = nRows;
        int portalSegLen = portalSegmentLength(nCols);
        int portalCol = centeredStartCol(nCols, portalSegLen);
        brush.reserveHorizontalRegion(1, portalCol, portalSegLen, exitPortal);

        int potionRow = manualPotionRow(nRows);
        if (potionRow >= 2 && potionRow <= nRows) {
            int potionCol = centeredStartCol(nCols, 1);
            brush.reserveVertical(potionRow, potionCol, 1, new Addon[]{Potion.createPotion()});
        }
    }

    @Override
    protected int[] getParentBlockedRowsRange(int nRows, int nCols) {
        return new int[]{1, nRows};
    }

    @Override
    protected boolean shouldPropagateReservationsToParent() {
        return false;
    }

    private static int portalSegmentLength(int nCols) {
        return max(1, min(PortalConfig.CELLS_PER_PORTAL_SEGMENT, nCols));
    }

    private static int centeredStartCol(int nCols, int segLen) {
        int maxStart = nCols - segLen + 1;
        return max(1, 1 + max(0, maxStart - 1) / 2);
    }

    private static int manualPotionRow(int nRows) {
        if (nRows < 2) {
            return -1;
        }
        int desired = 1 + max(1, PortalConfig.MIN_ENTRANCE_EXIT_ROW_GAP / 2);
        return min(nRows, desired);
    }

    int getLastGeneratedRows() {
        return lastGeneratedRows;
    }

}
