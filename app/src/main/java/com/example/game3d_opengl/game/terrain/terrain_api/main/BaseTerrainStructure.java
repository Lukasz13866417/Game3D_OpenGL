package com.example.game3d_opengl.game.terrain.terrain_api.main;

/**
 * @deprecated Compatibility copy for Android diagnostic stages. Use
 * {@link com.example.game3d.authoring.BaseTerrainStructure} for production and editor content.
 */
@Deprecated
public abstract class BaseTerrainStructure<GridBrushType extends Terrain.BaseGridBrush> {
    protected final int tilesToMake;
    private int debugChildCounter = 0;

    public String name = "NOT SET"; // only for debug

    public BaseTerrainStructure(int nTiles) {
        this.tilesToMake = nTiles;
    }

    /**
     * Lower bound on the number of terrain rows emitted by this structure.
     *
     * <p>Composite structures override this to include their children. The value deliberately
     * remains a lower bound because optional children may add more rows at generation time.
     */
    public int getMinimumGeneratedTileCount() {
        return Math.max(0, tilesToMake);
    }

    protected abstract void generateTiles(Terrain.TileBrush brush);
    protected abstract void generateAddons(GridBrushType brush, int nRows, int nCols);

    protected abstract GridBrushType selectBrush(Terrain terrain);

    /**
     * Optional hook for structures that need to reserve a full row range in their parent grid.
     * Returned rows are 1-indexed and local to this structure.
     */
    protected int[] getParentBlockedRowsRange(int nRows, int nCols) {
        return null;
    }

    /**
     * Child structures can disable propagation when they reserve a dedicated area
     * in the parent via {@link #getParentBlockedRowsRange(int, int)}.
     */
    protected boolean shouldPropagateReservationsToParent() {
        return true;
    }

    public final void generateAddons(Terrain terrain, int nRows, int nCols){
        generateAddons(selectBrush(terrain), nRows, nCols);
    }

    protected void addChild(BaseTerrainStructure<? extends Terrain.BaseGridBrush> child, Terrain.TileBrush what){
        if (child != null && "NOT SET".equals(child.name)) {
            String parentName = "NOT SET".equals(name) ? getClass().getSimpleName() : name;
            child.name = parentName
                    + "/child_" + debugChildCounter
                    + "_" + child.getClass().getSimpleName();
        }
        debugChildCounter++;
        what.addChild(child);
    }

}
