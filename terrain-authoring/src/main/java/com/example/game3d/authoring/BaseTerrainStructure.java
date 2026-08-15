package com.example.game3d.authoring;

/** One-shot command source retaining the original structure/brush override shape. */
public abstract class BaseTerrainStructure<B extends Terrain.BaseGridBrush> {
    protected final int tilesToMake;
    private boolean captured;
    public String name = "NOT SET";

    protected BaseTerrainStructure(int tilesToMake) {
        if (tilesToMake < 0) {
            throw new IllegalArgumentException("tilesToMake < 0");
        }
        this.tilesToMake = tilesToMake;
    }

    protected abstract void generateTiles(Terrain.TileBrush brush);

    protected abstract void generateAddons(B brush, int rows, int columns);

    protected abstract B selectBrush(Terrain.CaptureSession session);

    public int getMinimumGeneratedTileCount() {
        return tilesToMake;
    }

    /** Optional 1-indexed local row range that a composite reserves in its parent grid. */
    protected int[] getParentBlockedRowsRange(int rows, int columns) {
        return null;
    }

    /** Whether this structure's concrete reservations participate in a parent's occupancy map. */
    protected boolean shouldPropagateReservationsToParent() {
        return true;
    }

    final void capture(Terrain.CaptureSession session) {
        if (captured) {
            throw new IllegalStateException("A terrain structure instance is one-shot: " + name);
        }
        captured = true;
        int first = session.tileCount();
        int firstRow = session.physicalRowCount();
        session.beginStructureCapture();
        try {
            generateTiles(session.tileBrush());
            session.ensurePreviewGeometry();
            int tiles = session.tileCount() - first;
            int rows = session.physicalRowCount() - firstRow;
            boolean propagate = shouldPropagateReservationsToParent();
            session.beginAddonScope(first, tiles, firstRow, rows, propagate);
            try {
                generateAddons(selectBrush(session), rows, session.profile().gridColumns);
            } finally {
                session.endAddonScope();
            }
            session.finishStructureCapture(
                    firstRow, rows, propagate,
                    getParentBlockedRowsRange(rows, session.profile().gridColumns));
        } catch (RuntimeException failure) {
            session.abortStructureCapture();
            throw failure;
        }
    }

    protected final void addChild(BaseTerrainStructure<?> child, Terrain.TileBrush brush) {
        if (child != null) {
            brush.addChild(child);
        }
    }
}
