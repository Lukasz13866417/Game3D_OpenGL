package com.example.game3d_opengl.game.terrain_api.main;

import static com.example.game3d_opengl.rendering.util3d.GameMath.EPSILON;
import static com.example.game3d_opengl.rendering.util3d.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.GameMath.rotateAroundAxis;
import static com.example.game3d_opengl.rendering.util3d.GameMath.rotateAroundTwoPoints;
import static com.example.game3d_opengl.rendering.util3d.GameMath.tan;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;
import static java.lang.Math.abs;
import static java.lang.Math.atan;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sqrt;

import com.example.game3d_opengl.game.terrain_api.Tile;
import com.example.game3d_opengl.game.terrain_api.terrainutil.FixedMaxSizeDeque;
import com.example.game3d_opengl.game.terrain_api.terrainutil.OverflowingPreallocatedRowInfoBuffer;
import com.example.game3d_opengl.game.terrain_api.terrainutil.OverflowingPreallocatedCoordinateBuffer;
import com.example.game3d_opengl.game.terrain_api.terrainutil.OverflowingPreallocatedSegmentHistoryBuffer;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Builds a deque of terrain tiles and, in parallel, keeps three buffers
 * – left edge points, right edge points and per-row metadata – that are always
 * size-synchronised.  Row placement is driven by a single “centre-line”
 * distance counter so it remains consistent even when the tile turns.
 */
public class TileBuilder {

    private boolean wasLastTileEmpty = false;
    private float pendingLift = 0f;

    /**
     * Creates one additional tile, continuing from {@code lastTile}’s far edge.
     */
    public void addSegment(boolean isEmpty) {
        if (tiles.size() == tiles.getMaxSize()) {
            throw new IllegalStateException("Already at capacity");
        }

        Vector3D l1 = lastTile.farLeft;
        Vector3D r1 = lastTile.farRight;
        Vector3D near_l1 = lastTile.nearLeft;
        Vector3D near_r1 = lastTile.nearRight;

        Vector3D axis = V3(0, -1, 0);

        Vector3D mid = l1.add(r1).div(2);

        Vector3D line1 = l1.sub(near_l1).setY(0),
                line2 = r1.sub(near_r1).setY(0);
        Vector3D baseLen = r1.sub(l1);//.div(2);
        float dLen = (float) (Math.sqrt(baseLen.sqlen()) * tan(dHorizontalAng));

        Vector3D newL1 = l1.sub(line1.withLen(dLen));
        Vector3D newR1 = r1;//r1.add(line2.withLen(dLen));

        assert abs(newL1.y - newR1.y) < 0.01f;

        dHorizontalAng = 0.0f;

        Vector3D dir = rotateAroundTwoPoints(
                V3(0, 0, 0),
                newR1.sub(newL1),
                axis,
                PI / 2 + currVerticalAng).withLen(segLength);

        Vector3D l2 = newL1.add(dir);
        Vector3D r2 = newR1.add(dir);

        // Update the last tile's far edge by replacing it with [newL1,newR1].
        Tile oldLast = removeLastTile();
        wasLastTileEmpty = oldLast.isEmptySegment();
        addTile(oldLast.nearLeft, oldLast.nearRight, newL1, newR1, wasLastTileEmpty);
        oldLast.cleanupGPUResources();

        // Add the new segment tile.
        addTile(newL1.addY(pendingLift),
                newR1.addY(pendingLift),
                l2.addY(pendingLift),
                r2.addY(pendingLift), isEmpty);
        pendingLift = 0;
        wasLastTileEmpty = isEmpty;
    }

    public void liftUp(float dy) {
        this.pendingLift += dy;
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
        segmentHistoryBuffer.free();
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
        this.segmentHistoryBuffer = new OverflowingPreallocatedSegmentHistoryBuffer();


        /*–––– guardian tile (length ≈ 0) ––––*/
        Vector3D startLeft = V3(startMid.sub(segWidth / 2, 0, 0));
        Vector3D startRight = V3(startMid.add(segWidth / 2, 0, 0));

        addTile(startLeft,
                startRight,
                startLeft.sub(0, 0, 0.01f),   // farLeft = nearLeft shifted a bit so len>0
                startRight.sub(0, 0, 0.01f),  // farRight
                true);

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

    public long getTileIdForRow(int row) {
        if (row <= 0) {
            // Guardian row or invalid – fall back to first real row.
            return rowInfoBuffer.get(0).tileId;
        }
        return rowInfoBuffer.get(row - 1).tileId;
    }

    public Vector3D[] getField(int row, int col) {
        GridRowInfo info = rowInfoBuffer.get(row - 1);
        return new Vector3D[]{
                getGridPoint(info, col - 1, true),
                getGridPoint(info, col, true),
                getGridPoint(info, col - 1, false),
                getGridPoint(info, col, false)
        };
    }

    public Vector3D[] leftSideToArrayDebug() {
        int total = leftSideBuffer.size();
        if (total <= 1) return new Vector3D[0]; // should not happen, but be safe
        Vector3D[] res = new Vector3D[total - 1];
        for (int i = 1; i < total; i++) {
            res[i - 1] = V3(leftSideBuffer.getX(i), leftSideBuffer.getY(i), leftSideBuffer.getZ(i)).addY(0.01f);
        }
        return res;
    }

    public Vector3D[] rightSideToArrayDebug() {
        int total = rightSideBuffer.size();
        if (total <= 1) return new Vector3D[0];
        Vector3D[] res = new Vector3D[total - 1];
        for (int i = 1; i < total; i++) {
            res[i - 1] = V3(rightSideBuffer.getX(i), rightSideBuffer.getY(i), rightSideBuffer.getZ(i)).addY(0.01f);
        }
        return res;
    }

    public void printRowInfoToArrayDebug() {
        GridRowInfo[] res = new GridRowInfo[rowInfoBuffer.size()];
        for (int i = 0; i < res.length; i++) {
            System.out.println(rowInfoBuffer.get(i));
        }
    }


    private long nextId = 0L;

    /*––––––––––––  CONFIG & STATE  ––––––––––––*/

    private final float rowSpacing;                 // desired spacing between logical rows
    private final float segLength;                  // preferred tile length (used by generators)
    private final int nCols;                      // grid columns – passed to GridCreator

    /*––––––––––––  BUFFERS  ––––––––––––*/

    private final OverflowingPreallocatedCoordinateBuffer leftSideBuffer;
    private final OverflowingPreallocatedCoordinateBuffer rightSideBuffer;
    private final OverflowingPreallocatedRowInfoBuffer rowInfoBuffer;
    private final OverflowingPreallocatedSegmentHistoryBuffer segmentHistoryBuffer;

    private final FixedMaxSizeDeque<Tile> tiles;    // includes the guardian
    private Tile lastTile;                          // newest tile (back of deque)

    private float dHorizontalAng = 0f, currHorizontalAng = 0f, currVerticalAng = 0f;

    /**
     * Helper: builds a new tile and fully integrates it with row/buffer tracking.
     */
    private void addTile(Vector3D nl, Vector3D nr, Vector3D fl, Vector3D fr, boolean isEmptySegment) {

        float slopeVal = (float) atan((fl.y - nl.y) /
                sqrt((fl.x - nl.x) * (fl.x - nl.x) + (fl.z - nl.z) * (fl.z - nl.z)));

        Tile tile = Tile.createTile(nl, nr, fl, fr, slopeVal, nextId++, isEmptySegment);
        tiles.pushBack(tile);

        generateRowsForTile(tile);

        lastTile = tile;
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
            throw new IllegalStateException("Tile deque is empty");
        }

        Tile oldLast = tiles.popLast();

        SegmentHistory history = segmentHistoryBuffer.pop();
        for (int i = 0; i < history.leftCnt; ++i) leftSideBuffer.pop();
        for (int i = 0; i < history.rightCnt; ++i) rightSideBuffer.pop();
        for (int i = 0; i < min(history.leftCnt, history.rightCnt); ++i) rowInfoBuffer.pop();


        lastTile = tiles.peekLast();
        return oldLast;
    }

    private void generateRowsForTile(Tile tile) {
        Vector3D nl = tile.nearLeft;
        Vector3D nr = tile.nearRight;
        Vector3D fl = tile.farLeft;
        Vector3D fr = tile.farRight;

        if (tile.isEmptySegment()) {
            segmentHistoryBuffer.add(
                    0, 0, 0, 0, nl, nr
            );
            return;
        }

        SegmentHistory lastHistory = segmentHistoryBuffer.get(segmentHistoryBuffer.size() - 1);

        if (wasLastTileEmpty) {
            // First real tile – add bridging row along its near edge so the grid starts flush.
            leftSideBuffer.addPos(nl.x, nl.y, nl.z);
            rightSideBuffer.addPos(nr.x, nr.y, nr.z);
        }

        Vector3D eL = fl.sub(nl);
        Vector3D eR = fr.sub(nr);

        float lenL = (float) sqrt(eL.sqlen());
        float lenR = (float) sqrt(eR.sqlen());

        int cntL = 0, cntR = 0;


        int lastLeftInd = leftSideBuffer.size();
        int lastRightInd = rightSideBuffer.size();


        // distance from tile.near edge to first potential row position
        float firstDistL = (rowSpacing - lastHistory.leftoverL);
        if (abs(firstDistL - rowSpacing) < 1e-6f) firstDistL = rowSpacing; // leftoverL == 0

        for (float d = firstDistL; d <= lenL + 10f * EPSILON; d += rowSpacing) {
            float fraction = d / lenL;
            Vector3D leftP = nl.add(eL.mult(fraction));
            leftSideBuffer.addPos(leftP.x, leftP.y, leftP.z);
            ++cntL;
        }


        float firstDistR = (rowSpacing - lastHistory.leftoverR);
        if (abs(firstDistR - rowSpacing) < 1e-6f) firstDistR = rowSpacing; // leftoverR == 0

        for (float d = firstDistR; d <= lenR + 10f * EPSILON; d += rowSpacing) {
            float fraction = d / lenR;
            Vector3D rightP = nr.add(eR.mult(fraction));

            rightSideBuffer.addPos(rightP.x, rightP.y, rightP.z);
            ++cntR;
        }



        int startingPosInd = min(lastRightInd - (cntR > 0 ? 0 : 1),lastLeftInd - (cntL > 0 ? 0 : 1));
        int lastLeftDiff = lastHistory.leftCnt - lastHistory.rightCnt;

        int cntRows = min(cntL + max(0,lastLeftDiff), cntR + max(0,-lastLeftDiff));
        for (int i = 0; i < cntRows; ++i) {
            Vector3D leftP = V3(
                    leftSideBuffer.getX(startingPosInd + i),
                    leftSideBuffer.getY(startingPosInd + i),
                    leftSideBuffer.getZ(startingPosInd + i)
            );
            Vector3D rightP = V3(
                    rightSideBuffer.getX(startingPosInd + i),
                    rightSideBuffer.getY(startingPosInd + i),
                    rightSideBuffer.getZ(startingPosInd + i)
            );
            Vector3D lastLeftP = V3(
                    leftSideBuffer.getX(startingPosInd + i - 1),
                    leftSideBuffer.getY(startingPosInd + i - 1),
                    leftSideBuffer.getZ(startingPosInd + i - 1)
            );
            Vector3D lastRightP = V3(
                    rightSideBuffer.getX(startingPosInd + i - 1),
                    rightSideBuffer.getY(startingPosInd + i - 1),
                    rightSideBuffer.getZ(startingPosInd + i - 1)
            );
            rowInfoBuffer.add(tile.getID(),
                    lastHistory.nl, nl, fl,
                    lastHistory.nr, nr, fr,
                    leftP, rightP,
                    lastLeftP, lastRightP);
        }

        System.out.println();

        float currLeftoverL = (lastHistory.leftoverL + lenL) % rowSpacing;
        float currLeftoverR = (lastHistory.leftoverR + lenR) % rowSpacing;

        segmentHistoryBuffer.add(cntL, cntR, currLeftoverL, currLeftoverR, nl, nr);

    }

    private Vector3D getGridPoint(GridRowInfo rowInfo, int c, boolean isTop) {
        if (isTop) {
            Vector3D edge = rowInfo.RS.sub(rowInfo.LS);
            return rowInfo.LS.add(edge.mult((float) c / nCols));
        }
        Vector3D edge = rowInfo.RS_last.sub(rowInfo.LS_last);
        return rowInfo.LS_last.add(edge.mult((float) c / nCols));
    }

    private Vector3D[] bufferToArray(OverflowingPreallocatedCoordinateBuffer buf) {
        Vector3D[] res = new Vector3D[buf.size()];
        for (int i = 0; i < res.length; i++) {
            res[i] = V3(buf.getX(i), buf.getY(i), buf.getZ(i)).addY(0.01f);
        }
        return res;
    }

    public static class SegmentHistory {
        public int leftCnt = 0, rightCnt = 0;
        public float leftoverL, leftoverR;
        public Vector3D nl = V3(0, 0, 0), nr = V3(0, 0, 0);

        public SegmentHistory() {
        }

        public void set(int leftCnt, int rightCnt,
                        float leftoverL, float leftoverR,
                        Vector3D nl, Vector3D nr) {
            this.leftCnt = leftCnt;
            this.rightCnt = rightCnt;
            this.leftoverL = leftoverL;
            this.leftoverR = leftoverR;
            this.nl = nl;
            this.nr = nr;
        }
    }

    public static class GridRowInfo {
        public long tileId;
        public Vector3D L1 = V3(0, 0, 0), L2 = V3(0, 0, 0), L3 = V3(0, 0, 0),
                R1 = V3(0, 0, 0), R2 = V3(0, 0, 0), R3 = V3(0, 0, 0);
        public Vector3D LS = V3(0, 0, 0), RS = V3(0, 0, 0);
        public Vector3D LS_last = V3(0, 0, 0), RS_last = V3(0, 0, 0);


        public GridRowInfo() {
        }

        public void set(long tileId,
                        Vector3D L1, Vector3D L2, Vector3D L3,
                        Vector3D R1, Vector3D R2, Vector3D R3,
                        Vector3D LS, Vector3D RS,
                        Vector3D LS_last, Vector3D RS_last) {
            this.tileId = tileId;
            this.L1 = L1;
            this.L2 = L2;
            this.L3 = L3;
            this.R1 = R1;
            this.R2 = R2;
            this.R3 = R3;
            this.LS = LS;
            this.RS = RS;
            this.LS_last = LS_last;
            this.RS_last = RS_last;
        }
    }
}
