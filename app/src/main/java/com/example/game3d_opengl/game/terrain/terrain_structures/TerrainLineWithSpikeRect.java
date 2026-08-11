package com.example.game3d_opengl.game.terrain.terrain_structures;

import static java.lang.Math.min;

import com.example.game3d_opengl.game.terrain.terrain_api.main.AdvancedTerrainStructure;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.track_elements.portal.ExitPortal;
import com.example.game3d_opengl.game.terrain.track_elements.portal.Portal;
import com.example.game3d_opengl.game.terrain.track_elements.portal.PortalConfig;
import com.example.game3d_opengl.game.terrain.track_elements.potion.Potion;
import com.example.game3d_opengl.game.terrain.track_elements.spike.DeathSpike;

public class TerrainLineWithSpikeRect extends AdvancedTerrainStructure {
    private final boolean portalEncounterEnabled;
    private final ExitPortal exitPortal;
    private final Portal entrancePortal;
    private final ExitPortalStructure exitPortalStructure;

    public TerrainLineWithSpikeRect(int nTiles) {
        this(nTiles, true);
    }

    public TerrainLineWithSpikeRect(int nTiles, boolean portalEncounterEnabled) {
        super(nTiles);
        this.portalEncounterEnabled = portalEncounterEnabled;
        if (portalEncounterEnabled) {
            this.exitPortal = ExitPortal.createExitPortal();
            this.entrancePortal = Portal.createPortal(exitPortal);
            this.exitPortalStructure = new ExitPortalStructure(PortalConfig.EXIT_STRUCTURE_ROWS, exitPortal);
        } else {
            this.exitPortal = null;
            this.entrancePortal = null;
            this.exitPortalStructure = null;
        }
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        if (exitPortalStructure != null) {
            addChild(exitPortalStructure, brush);
        }
        for (int i = 0; i < tilesToMake; ++i) {
            brush.addSegment();
        }
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
        int occupiedTopRows = exitPortalStructure != null ? exitPortalStructure.getLastGeneratedRows() : 0;
        if (occupiedTopRows < 0) {
            occupiedTopRows = 0;
        }
        occupiedTopRows = Math.min(occupiedTopRows, Math.max(0, nRows - 1));
        int freeRowStart = occupiedTopRows + 1;
        int freeRowCount = nRows - occupiedTopRows;

        final int sideSize = min(nCols - 2, freeRowCount - 2);
        if (sideSize >= 2) {
            // Keep the spike frame centered inside the rows not owned by the exit child.
            final int topLeftRow = freeRowStart + (freeRowCount - sideSize) / 2;
            final int topLeftCol = 1 + (nCols - sideSize) / 2;
            // 4 = sides in rectangle
            DeathSpike[][] spikes = new DeathSpike[4][sideSize - 1];
            for (int i = 0; i < spikes.length; ++i) {
                for (int j = 0; j < spikes[i].length; ++j) {
                    spikes[i][j] = DeathSpike.createDeathSpike();
                }
            }
            brush.reserveHorizontal(
                    topLeftRow, topLeftCol, sideSize - 1, spikes[0]
            );
            brush.reserveVertical(
                    topLeftRow, topLeftCol + sideSize - 1, sideSize - 1, spikes[1]
            );
            brush.reserveHorizontal(
                    topLeftRow + sideSize - 1, topLeftCol + 1, sideSize - 1, spikes[2]
            );
            brush.reserveVertical(
                    topLeftRow + 1, topLeftCol, sideSize - 1, spikes[3]
            );
        }

        for(int i = 0;i<3;++i) {
            Potion[] potions = new Potion[1];
            for (int j = 0; j < potions.length; ++j) {
                potions[j] = Potion.createPotion();
            }
            brush.reserveRandomFittingVertical(1, potions);
        }

        if (portalEncounterEnabled) {
            int portalSegLen = Math.max(1, min(PortalConfig.CELLS_PER_PORTAL_SEGMENT, nCols));
            if (PortalConfig.EXIT_STRUCTURE_ROWS < PortalConfig.MIN_ENTRANCE_EXIT_ROW_GAP) {
                throw new IllegalStateException(
                        "EXIT_STRUCTURE_ROWS must be >= MIN_ENTRANCE_EXIT_ROW_GAP");
            }
            brush.reserveRandomHorizontalRegion(portalSegLen, entrancePortal);
        }
    }

    public boolean hasPortalEncounter() {
        return portalEncounterEnabled;
    }
}
