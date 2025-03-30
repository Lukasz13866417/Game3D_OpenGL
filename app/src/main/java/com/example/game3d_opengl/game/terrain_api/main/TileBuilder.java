package com.example.game3d_opengl.game.terrain_api.main;

import static com.example.game3d_opengl.rendering.util3d.GameMath.EPSILON;
import static com.example.game3d_opengl.rendering.util3d.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.GameMath.rotateAroundAxis;
import static com.example.game3d_opengl.rendering.util3d.GameMath.rotateAroundTwoPoints;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;
import static java.lang.Math.abs;
import static java.lang.Math.atan;
import static java.lang.Math.min;

import com.example.game3d_opengl.game.terrain_api.Tile;
import com.example.game3d_opengl.game.terrain_api.terrainutil.FixedMaxSizeDeque;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain_api.terrainutil.OverflowingPreallocatedCoordinateBuffer;


public class TileBuilder {

    private final float segWidth, segLength, rowSpacing;
    private float lastTileProgress, dHorizontalAng, dVerticalAng, currHorizontalAng, currVerticalAng;
    private float lastLastTileProgress;
    private int lastTimeCntRowsAdded = 0;
    private final int nCols;
    public int currRowCount;

    private final OverflowingPreallocatedCoordinateBuffer leftSideBuffer, rightSideBuffer;

    /**
     * Deque to hold all tiles (including the guardian).
     */
    private final FixedMaxSizeDeque<Tile> tiles;

    /**
     * Points to the newest tile added.
     */
    private Tile lastTile;

    private final Tile guardian;

    public TileBuilder(int maxSegments, int nCols, Vector3D startMid, float segWidth, float segLength, float rowSpacing) {

        this.nCols = nCols;
        this.segWidth = segWidth;
        this.segLength = segLength;
        this.rowSpacing = rowSpacing;
        this.lastTileProgress = 0.0f;

        this.tiles = new FixedMaxSizeDeque<>(maxSegments + 1);

        // Create the "guardian" tile as the first tile
        Vector3D startLeft = V3(startMid.sub(segWidth / 2, 0, 0));
        Vector3D startRight = V3(startMid.add(segWidth / 2, 0, 0));

        // Guardian tile: store near and far edges the same
        this.guardian = new Tile(
                startLeft,                                       // nearLeft
                startRight,                                      // nearRight
                startLeft.sub(0, 0, 0.01f),              // farLeft
                startRight.sub(0, 0, 0.01f),             // farRight
                0.0f                                             // slope
        );
        this.lastTileProgress = (float) (Math.sqrt(guardian.farLeft.sub(guardian.nearLeft).sqlen()));
        this.lastLastTileProgress = 0.0f;
        this.tiles.pushBack(guardian);
        this.lastTile = guardian;

        this.currHorizontalAng = 0.0f;
        this.currVerticalAng = 0.0f;
        this.dHorizontalAng = 0.0f;
        this.dVerticalAng = 0.0f;
        this.currRowCount = 0;

        this.leftSideBuffer = new OverflowingPreallocatedCoordinateBuffer();
        this.rightSideBuffer = new OverflowingPreallocatedCoordinateBuffer();

        Vector3D startLeftRow = new Vector3D(guardian.farLeft.x, guardian.farLeft.y, guardian.farLeft.z);
        Vector3D startRightRow = new Vector3D(guardian.farRight.x, guardian.farRight.y, guardian.farRight.z);

        leftSideBuffer.addPos(startLeftRow.x, startLeftRow.y, startLeftRow.z);
        rightSideBuffer.addPos(startRightRow.x, startRightRow.y, startRightRow.z);

    }

    /**
     * Helper: builds a new tile and adds it to the deque.
     */
    void addTile(Vector3D nl, Vector3D nr, Vector3D fl, Vector3D fr) {
        float slopeVal = (float) atan((fl.y - nl.y) /
                Math.sqrt((fl.x - nl.x) * (fl.x - nl.x) + (fl.z - nl.z) * (fl.z - nl.z)));
        Tile t = new Tile(nl, nr, fl, fr, slopeVal);
        this.tiles.pushBack(t);
        float lastLen = lastTile == null ? 0.0f
                : (float) (Math.sqrt(lastTile.farLeft.sub(lastTile.nearLeft).sqlen()));
        lastTile = t;
        Vector3D edgeLeft = fl.sub(nl), edgeRight = fr.sub(nr);
        float len = (float) (Math.sqrt(edgeLeft.sqlen()));

        Vector3D leftSide, rightSide;
        float borderRowPosHere;
        lastTimeCntRowsAdded = 0;
        if (abs(lastLen - lastTileProgress) < 0.0001f) {
            borderRowPosHere = 0.0f;
            if (this.tiles.size() == 1) {
                lastTileProgress = len;
                return;
            }
        } else {
            // Add the row that overlaps this and last tile
            borderRowPosHere = rowSpacing - (lastLen - lastTileProgress);
            leftSide = nl.add(edgeLeft.withLen(borderRowPosHere));
            rightSide = nr.add(edgeRight.withLen(borderRowPosHere));

            leftSideBuffer.addPos(leftSide.x, leftSide.y, leftSide.z);
            rightSideBuffer.addPos(rightSide.x, rightSide.y, rightSide.z);
            ++currRowCount;
            ++lastTimeCntRowsAdded;
        }
        lastLastTileProgress = lastTileProgress;
        lastTileProgress = borderRowPosHere;
        while (lastTileProgress + rowSpacing <= len + 10f * EPSILON) {
            lastTileProgress = min(lastTileProgress + rowSpacing, len);
            leftSide = nl.add(edgeLeft.withLen(lastTileProgress));
            rightSide = nr.add(edgeRight.withLen(lastTileProgress));
            leftSideBuffer.addPos(leftSide.x, leftSide.y, leftSide.z);
            rightSideBuffer.addPos(rightSide.x, rightSide.y, rightSide.z);
            ++currRowCount;
            ++lastTimeCntRowsAdded;
        }
    }

    /*
    Helper. Never called twice in a row.
     */
    private Tile removeLastTile() {
        Tile oldLast = tiles.popLast();
        for (int i = 0; i < lastTimeCntRowsAdded; ++i) {
            leftSideBuffer.pop();
            rightSideBuffer.pop();
            --currRowCount;
        }
        lastTile = tiles.peekLast();
        lastTileProgress = lastLastTileProgress;
        return oldLast;
    }

    /**
     * Creates one additional tile, continuing from lastTile’s far edge.
     */
    public void addSegment() {
        if (tiles.size() == tiles.getMaxSize()) {
            throw new IllegalStateException("Already at capacity");
        }

        Vector3D l1 = lastTile.farLeft;
        Vector3D r1 = lastTile.farRight;

        Vector3D axis;
        if (tiles.size() == 1) {
            axis = V3(0, -1, 0);
        } else {
            // Get the previous tile (second-to-last in the deque)
            Tile prevTile = tiles.get(tiles.size() - 2);
            Vector3D r0 = prevTile.farRight;
            Vector3D l0 = prevTile.farLeft;
            Vector3D e1 = l0.sub(r0);
            Vector3D e2 = r1.sub(r0);
            axis = e1.crossProduct(e2).mult(1);
        }

        Vector3D mid = l1.add(r1).div(2);
        Vector3D newL1 = rotateAroundAxis(mid, axis, l1, dHorizontalAng);
        Vector3D newR1 = rotateAroundAxis(mid, axis, r1, dHorizontalAng);

        dHorizontalAng = 0.0f;

        Vector3D dir = rotateAroundTwoPoints(axis, V3(0, 0, 0),
                                            newR1.sub(newL1), PI / 2).withLen(segLength);

        Vector3D l2 = newL1.add(dir);
        Vector3D r2 = newR1.add(dir);

        l2 = rotateAroundTwoPoints(newR1, newL1, l2, -dVerticalAng);
        r2 = rotateAroundTwoPoints(newR1, newL1, r2, -dVerticalAng);

        dVerticalAng = 0.0f;

        // Update the last tile's far edge.
        // Remove the old last tile, update it, and push it back.
        Tile oldLast = removeLastTile();
        Tile updatedLast = new Tile(oldLast.nearLeft, oldLast.nearRight, newL1, newR1, oldLast.slope);
        addTile(updatedLast.nearLeft, updatedLast.nearRight,
                updatedLast.farLeft, updatedLast.farRight);

        // Add the new tile segment.
        addTile(newL1, newR1, l2, r2);
    }

    public void removeOldTiles(float playerX, float playerY, float playerZ) {
        Vector3D pp = V3(playerX,playerY,playerZ);
        while (!tiles.isEmpty() && tiles.getFirst().farLeft.sub(pp).sqlen() > 100f) {
            tiles.removeFirst();
        }
    }

    public void addVerticalAngle(float angle) {
        this.dVerticalAng = angle;
    }

    public void addHorizontalAngle(float angle) {
        this.dHorizontalAng = angle;
    }

    public void setHorizontalAngle(float angle) {
        this.dHorizontalAng = angle - currHorizontalAng;
        currHorizontalAng += dHorizontalAng;
    }

    public void setVerticalAngle(float angle) {
        this.dVerticalAng = angle - currVerticalAng;
        currVerticalAng += dVerticalAng;
    }

    public int getTileCount() {
        return tiles.size();
    }

    public Tile getTile(int i) {
        return tiles.get(i);
    }

    public Vector3D[] getField(int row, int col) {

        Vector3D nearLeftOnSide = V3(
                leftSideBuffer.getX(row - 1),
                leftSideBuffer.getY(row - 1),
                leftSideBuffer.getZ(row - 1)
        );
        Vector3D nearRightOnSide = V3(
                rightSideBuffer.getX(row - 1),
                rightSideBuffer.getY(row - 1),
                rightSideBuffer.getZ(row - 1)
        );
        Vector3D farLeftOnSide = V3(
                leftSideBuffer.getX(row),
                leftSideBuffer.getY(row),
                leftSideBuffer.getZ(row)
        );
        Vector3D farRightOnSide = V3(
                rightSideBuffer.getX(row),
                rightSideBuffer.getY(row),
                rightSideBuffer.getZ(row)
        );

        Vector3D nearEdge = nearRightOnSide.sub(nearLeftOnSide);
        Vector3D farEdge = farRightOnSide.sub(farLeftOnSide);

        Vector3D nearLeft = nearLeftOnSide.add(nearEdge.mult(col - 1).div(nCols));
        Vector3D farLeft = farLeftOnSide.add(nearEdge.mult(col - 1).div(nCols));

        Vector3D nearRight = nearLeftOnSide.add(farEdge.mult(col).div(nCols));
        Vector3D farRight = farLeftOnSide.add(farEdge.mult(col).div(nCols));

        return new Vector3D[]{
                nearLeft,
                nearRight,
                farLeft,
                farRight
        };
    }

}
