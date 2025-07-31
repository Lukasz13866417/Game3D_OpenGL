package com.example.game3d_opengl.game.terrain_api.main;

import static com.example.game3d_opengl.rendering.util3d.GameMath.EPSILON;
import static com.example.game3d_opengl.rendering.util3d.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.GameMath.rotateAroundAxis;
import static com.example.game3d_opengl.rendering.util3d.GameMath.rotateAroundTwoPoints;
import static com.example.game3d_opengl.rendering.util3d.GameMath.tan;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;
import static java.lang.Math.abs;
import static java.lang.Math.atan;
import static java.lang.Math.min;
import static java.lang.Math.sqrt;

import com.example.game3d_opengl.game.terrain_api.Tile;
import com.example.game3d_opengl.game.terrain_api.terrainutil.ArrayStack;
import com.example.game3d_opengl.game.terrain_api.terrainutil.FixedMaxSizeDeque;
import com.example.game3d_opengl.game.terrain_api.terrainutil.OverflowingPreallocatedRowInfoBuffer;
import com.example.game3d_opengl.game.terrain_api.terrainutil.OverflowingPreallocatedCoordinateBuffer;
import com.example.game3d_opengl.game.terrain_api.terrainutil.OverflowingPreallocatedFloatBuffer;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Builds a deque of terrain tiles and, in parallel, keeps three buffers
 * – left edge points, right edge points and per-row metadata – that are always
 * size-synchronised.  Row placement is driven by a single “centre-line”
 * distance counter so it remains consistent even when the tile turns.
 */
public class TileBuilder {

    private class LastTileInfo {
        Vector3D farRight, farLeft, nearRight, nearLeft;

        public LastTileInfo(Vector3D nearLeft,
                            Vector3D nearRight,
                            Vector3D farLeft,
                            Vector3D farRight) {
            this.nearLeft = nearLeft;
            this.nearRight = nearRight;
            this.farLeft = farLeft;
            this.farRight = farRight;
        }


    }

    private LastTileInfo infoFromLastTile() {
        return new LastTileInfo(lastTile.nearLeft, lastTile.nearRight, lastTile.farLeft, lastTile.farRight);
    }
    private LastTileInfo lastTileInfo;

    private boolean wasLastTileEmpty = false;

    /**
     * Creates one additional tile, continuing from {@code lastTile}’s far edge.
     */
    public void addSegment() {
        if (tiles.size() == tiles.getMaxSize()) {
            throw new IllegalStateException("Already at capacity");
        }

        Vector3D l1 = lastTileInfo.farLeft;
        Vector3D r1 = lastTileInfo.farRight;

        Vector3D axis = V3(0, -1, 0);

        Vector3D mid = l1.add(r1).div(2);

        Vector3D line1 = l1.sub(lastTileInfo.nearLeft).setY(0),
                line2 = r1.sub(lastTileInfo.nearRight).setY(0);
        Vector3D baseLen = r1.sub(l1).div(2);
        float dLen = (float) (Math.sqrt(baseLen.sqlen()) * tan(dHorizontalAng));

        Vector3D rotatedL1 = rotateAroundAxis(mid, axis, l1, dHorizontalAng),
                rotatedR1 = rotateAroundAxis(mid, axis, r1, dHorizontalAng);

        Vector3D newL1 = l1.sub(line1.withLen(dLen));
        Vector3D newR1 = r1.add(line2.withLen(dLen));

        assert abs(newL1.y - newR1.y) < 0.01f;

        dHorizontalAng = 0.0f;

        Vector3D dir = rotateAroundTwoPoints(
                V3(0, 0, 0),
                rotatedR1.sub(rotatedL1),
                axis,
                PI / 2 + currVerticalAng).withLen(segLength);

        Vector3D l2 = rotatedL1.add(dir);
        Vector3D r2 = rotatedR1.add(dir);

        if (!wasLastTileEmpty) {
            // Update the last tile's far edge by replacing it with [newL1,newR1].
            Tile oldLast = removeLastTile();
            addTile(oldLast.nearLeft, oldLast.nearRight, newL1, newR1);
            oldLast.cleanupGPUResources();
        }
        // Add the new segment tile.
        addTile(newL1, newR1, l2, r2);
        wasLastTileEmpty = false;
    }

    public void addEmptySegment() {
        Vector3D l1 = lastTileInfo.farLeft;
        Vector3D r1 = lastTileInfo.farRight;

        Vector3D axis = V3(0, -1, 0);

        Vector3D mid = l1.add(r1).div(2);

        Vector3D line1 = l1.sub(lastTileInfo.nearLeft).setY(0),
                line2 = r1.sub(lastTileInfo.nearRight).setY(0);
        Vector3D baseLen = r1.sub(l1).div(2);
        float dLen = (float) (Math.sqrt(baseLen.sqlen()) * tan(dHorizontalAng));

        Vector3D rotatedL1 = rotateAroundAxis(mid, axis, l1, dHorizontalAng),
                rotatedR1 = rotateAroundAxis(mid, axis, r1, dHorizontalAng);

        Vector3D newL1 = l1.sub(line1.withLen(dLen));
        Vector3D newR1 = r1.add(line2.withLen(dLen));

        assert abs(newL1.y - newR1.y) < 0.01f;

        dHorizontalAng = 0.0f;

        Vector3D dir = rotateAroundTwoPoints(
                V3(0, 0, 0),
                rotatedR1.sub(rotatedL1),
                axis,
                PI / 2 + currVerticalAng).withLen(segLength);

        Vector3D l2 = rotatedL1.add(dir);
        Vector3D r2 = rotatedR1.add(dir);

        lastTileInfo = new LastTileInfo(newL1, newR1, l2, r2);
        wasLastTileEmpty = true;
    }


    public void removeOldTiles(long playerTileId) {
        // If first tile is far from player, this it has been visited a long time ago
        // In that case, it can be removed because we are sure it wont be displayed anymore.
        while (tiles.size() > 1 && (playerTileId - tiles.getFirst().getID() > 50L)) {
            tiles.popFirst().cleanupGPUResources();
        }
    }

    public int getCurrRowCount() {
        return rowInfoBuffer.size() + 1;
    }

    public void cleanup() {
        while (!tiles.isEmpty()) {
            tiles.popFirst().cleanupGPUResources();
        }
        leftSideBuffer.free();
        rightSideBuffer.free();
        rowInfoBuffer.free();
        leftoverHistory.free();
        rowsAddedHistory.free();
    }

    public TileBuilder(int maxSegments, int nCols,
                       Vector3D startMid,
                       float segWidth, float segLength,
                       float rowSpacing) {

        this.rowSpacing = rowSpacing;
        this.segLength = segLength;
        this.nCols = nCols;

        /*–––– data structures ––––*/
        this.leftSideBuffer = new OverflowingPreallocatedCoordinateBuffer();
        this.rightSideBuffer = new OverflowingPreallocatedCoordinateBuffer();
        this.rowInfoBuffer = new OverflowingPreallocatedRowInfoBuffer();

        this.tiles = new FixedMaxSizeDeque<>(maxSegments + 1);
        this.leftoverHistory = new OverflowingPreallocatedFloatBuffer();
        this.rowsAddedHistory = new OverflowingPreallocatedFloatBuffer();

        /*–––– guardian tile (length ≈ 0) ––––*/
        Vector3D startLeft = V3(startMid.sub(segWidth / 2, 0, 0));
        Vector3D startRight = V3(startMid.add(segWidth / 2, 0, 0));

        Tile guardian = Tile.createTile(
                startLeft,
                startRight,
                startLeft.sub(0, 0, 0.01f),   // farLeft = nearLeft shifted a bit so len>0
                startRight.sub(0, 0, 0.01f),  // farRight
                0f, nextId++);

        tiles.pushBack(guardian);
        lastTile = guardian;
        lastTileInfo = infoFromLastTile();

        // seed buffers with a single starting row placed at guardian’s far edge
        leftSideBuffer.addPos(guardian.farLeft.x, guardian.farLeft.y, guardian.farLeft.z);
        rightSideBuffer.addPos(guardian.farRight.x, guardian.farRight.y, guardian.farRight.z);
        // no rowInfo for the guardian

        leftoverHistory.add(leftover);   // 0
        rowsAddedHistory.add(1);          // the single seed-row
    }

    public void addVerticalAngle(float angle) {
        currVerticalAng += angle;
    }

    public void addHorizontalAngle(float angle) {
        dHorizontalAng = angle;
        currHorizontalAng += angle;
    }

    public void setHorizontalAngle(float angle) {
        dHorizontalAng = angle - currHorizontalAng;
        currHorizontalAng = angle;
    }

    public void setVerticalAngle(float angle) {
        currVerticalAng = angle;
    }

    public int getTileCount() {
        return tiles.size();
    }

    public Tile getTile(int i) {
        return tiles.get(i);
    }

    /**
     * Returns the tile ID owning the logical grid row.
     * <p>
     * Row numbering exposed outside TileBuilder includes the guardian row at
     * index&nbsp;0, while {@code rowInfoBuffer} starts at the first *real* row
     * (bridging row). Therefore, for any public row {@code r &gt; 0} we need to
     * access {@code rowInfoBuffer[r-1]}.
     */
    public long getTileIdForRow(int row) {
        if (row <= 0) {
            // Guardian row or invalid – fall back to first real row.
            return rowInfoBuffer.get(0).tileId;
        }
        return rowInfoBuffer.get(row - 1).tileId;
    }

    public Vector3D[] getField(int row, int col) {
        GridRowHelper info = rowInfoBuffer.get(row - 1);
        return new Vector3D[]{
                getGridPoint(info.prevStartL, info.prevEndR, col - 1),
                getGridPoint(info.prevStartL, info.prevEndR, col),
                getGridPoint(info.startL, info.endR, col - 1),
                getGridPoint(info.startL, info.endR, col)
        };
    }


    public Vector3D getGridPointDebug(int row, int col) {
        GridRowHelper info = rowInfoBuffer.get(row);
        return getGridPoint(info.startL, info.endR, col);
    }

    public Vector3D[] leftSideToArrayDebug() {
        int total = leftSideBuffer.size();
        if(total <= 1) return new Vector3D[0]; // should not happen, but be safe
        Vector3D[] res = new Vector3D[total - 1];
        for (int i = 1; i < total; i++) {
            res[i - 1] = V3(leftSideBuffer.getX(i), leftSideBuffer.getY(i), leftSideBuffer.getZ(i)).addY(0.01f);
        }
        return res;
    }

    public Vector3D[] rightSideToArrayDebug() {
        int total = rightSideBuffer.size();
        if(total <= 1) return new Vector3D[0];
        Vector3D[] res = new Vector3D[total - 1];
        for (int i = 1; i < total; i++) {
            res[i - 1] = V3(rightSideBuffer.getX(i), rightSideBuffer.getY(i), rightSideBuffer.getZ(i)).addY(0.01f);
        }
        return res;
    }

    public void printRowInfoToArrayDebug() {
        GridRowHelper[] res = new GridRowHelper[rowInfoBuffer.size()];
        for (int i = 0; i < res.length; i++) {
            System.out.println(rowInfoBuffer.get(i));
        }
    }


    private long nextId = 0L;

    /*––––––––––––  CONFIG & STATE  ––––––––––––*/

    private final float rowSpacing;                 // desired spacing between logical rows
    private final float segLength;                  // preferred tile length (used by generators)
    private final int nCols;                      // grid columns – passed to GridCreator

    // Distance already travelled since the last emitted row, in the range [0,rowSpacing).
    private float leftover = 0f;

    /*––––––––––––  BUFFERS  ––––––––––––*/

    private final OverflowingPreallocatedCoordinateBuffer leftSideBuffer;
    private final OverflowingPreallocatedCoordinateBuffer rightSideBuffer;
    private final OverflowingPreallocatedRowInfoBuffer rowInfoBuffer;

    // One entry per tile – value of `leftover` *before* rows for that tile were generated.
    private final OverflowingPreallocatedFloatBuffer leftoverHistory;
    // One entry per tile – how many rows were appended by that tile.
    private final OverflowingPreallocatedFloatBuffer rowsAddedHistory;

    private final FixedMaxSizeDeque<Tile> tiles;    // includes the guardian
    private Tile lastTile;                          // newest tile (back of deque)

    private float dHorizontalAng = 0f, currHorizontalAng = 0f, currVerticalAng = 0f;

    /**
     * Helper: builds a new tile and fully integrates it with row/buffer tracking.
     */
    private void addTile(Vector3D nl, Vector3D nr, Vector3D fl, Vector3D fr) {

        float slopeVal = (float) atan((fl.y - nl.y) /
                sqrt((fl.x - nl.x) * (fl.x - nl.x) + (fl.z - nl.z) * (fl.z - nl.z)));

        Tile tile = Tile.createTile(nl, nr, fl, fr, slopeVal, nextId++);
        tiles.pushBack(tile);

        /* save state *before* generating rows so we can roll back later if needed */
        leftoverHistory.add(leftover);
        int rowCountBefore = getCurrRowCount();

        // Insert the very first logical row (flush with near edge) only once –
        // i.e. when this is the first tile ever that generates rows.
        if (rowInfoBuffer.size() == 0 || wasLastTileEmpty) {
            // First real tile – add bridging row along its near edge so the grid starts flush.
            leftSideBuffer.addPos(nl.x, nl.y, nl.z);
            rightSideBuffer.addPos(nr.x, nr.y, nr.z);
            rowInfoBuffer.add(tile.getID(), nl, nr, nl, nr); // prev points are the same as current
        }

        generateRowsForTile(tile);

        /* rows added by this tile (including possible bridging row) */
        int rowsAdded = getCurrRowCount() - rowCountBefore;
        rowsAddedHistory.add(rowsAdded);

        lastTile = tile;
        lastTileInfo = infoFromLastTile();
    }

    /**
     * Removes the current last tile (never the guardian) and rolls back all
     * associated buffers so the builder can overwrite that tile.
     */
    private Tile removeLastTile() {
        // It is legal to remove the guardian (initial placeholder) – the very
        // next addTile() will immediately create a proper first tile.  We only
        // forbid popping when the deque is already empty (should never happen).
        if (tiles.isEmpty()) {
            throw new IllegalStateException("Tile deque is empty – nothing to remove");
        }

        Tile oldLast = tiles.popLast();

        // pop rows belonging to that tile
        int rowsToRemove = (int) (rowsAddedHistory.pop());
        for (int i = 0; i < rowsToRemove; i++) {
            leftSideBuffer.pop();
            rightSideBuffer.pop();
            if (rowInfoBuffer.size() > 0) {
                rowInfoBuffer.removeLast();
            }
        }

        // restore leftover to the value before that tile was generated
        leftover = leftoverHistory.pop();

        lastTile = tiles.peekLast();
        lastTileInfo = lastTile == null ? null : infoFromLastTile();
        return oldLast;
    }

    private void generateRowsForTile(Tile tile) {
        Vector3D nl = tile.nearLeft;
        Vector3D nr = tile.nearRight;
        Vector3D fl = tile.farLeft;
        Vector3D fr = tile.farRight;

        Vector3D eL = fl.sub(nl);
        Vector3D eR = fr.sub(nr);

        float lenL = (float) sqrt(eL.sqlen());
        float lenR = (float) sqrt(eR.sqlen());
        float len = min(lenL, lenR);          // safe distance inside both edges

        // distance from tile.near edge to first potential row position
        float firstDist = (rowSpacing - leftover);
        if (abs(firstDist - rowSpacing) < 1e-6f) firstDist = rowSpacing; // leftover == 0

        Vector3D prevLeft = nl;
        Vector3D prevRight = nr;

        for (float d = firstDist; d <= len + 10f * EPSILON; d += rowSpacing) {
            float fraction = d / lenL;          // 0..1 along left edge

            Vector3D leftP = nl.add(eL.withLen(d));
            Vector3D rightP = nr.add(eR.mult(fraction));

            leftSideBuffer.addPos(leftP.x, leftP.y, leftP.z); // only for debug
            rightSideBuffer.addPos(rightP.x, rightP.y, rightP.z); // only for debug
            rowInfoBuffer.add(tile.getID(), leftP, rightP, prevLeft, prevRight);

            prevLeft = leftP;
            prevRight = rightP;
        }

        // Advance leftover: total distance progressed inside this tile = len
        leftover = (leftover + len) % rowSpacing;
    }

    private Vector3D getGridPoint(Vector3D startL, Vector3D endR, int c) {
        Vector3D edge = endR.sub(startL);
        return startL.add(edge.mult((float) c / nCols));
    }

    private Vector3D[] bufferToArray(OverflowingPreallocatedCoordinateBuffer buf) {
        Vector3D[] res = new Vector3D[buf.size()];
        for (int i = 0; i < res.length; i++) {
            res[i] = V3(buf.getX(i), buf.getY(i), buf.getZ(i)).addY(0.01f);
        }
        return res;
    }

    public static class GridRowHelper {
        public long tileId;
        public Vector3D startL = V3(0, 0, 0), endR = V3(0, 0, 0);
        public Vector3D prevStartL = V3(0, 0, 0), prevEndR = V3(0, 0, 0);

        public GridRowHelper() {
        }

        public void set(long tileId, Vector3D startL, Vector3D endR, Vector3D prevStartL, Vector3D prevEndR) {
            this.tileId = tileId;
            this.startL = startL;
            this.endR = endR;
            this.prevStartL = prevStartL;
            this.prevEndR = prevEndR;
        }
    }
}
