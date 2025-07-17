package com.example.game3d_opengl.game.terrain_api.main;

import static com.example.game3d_opengl.rendering.util3d.GameMath.EPSILON;
import static com.example.game3d_opengl.rendering.util3d.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.GameMath.rotateAroundAxis;
import static com.example.game3d_opengl.rendering.util3d.GameMath.rotateAroundTwoPoints;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;
import static java.lang.Math.abs;
import static java.lang.Math.atan;
import static java.lang.Math.min;
import static java.lang.Math.sqrt;

import com.example.game3d_opengl.game.terrain_api.Tile;
import com.example.game3d_opengl.game.terrain_api.terrainutil.FixedMaxSizeDeque;
import com.example.game3d_opengl.game.terrain_api.terrainutil.OverflowingPreallocatedBuffer;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain_api.terrainutil.OverflowingPreallocatedCoordinateBuffer;

public class TileBuilder {

    private final float rowSpacing, segLength;
    private float lastTileProgressLeft, lastTileProgressRight;
    private float dHorizontalAng, currHorizontalAng, currVerticalAng;
    private float lastLastTileProgressLeft, lastLastTileProgressRight;
    private int lastTimeCntRowsLeft = 0, lastTimeCntRowsRight = 0;
    private final int nCols;
    private final OverflowingPreallocatedCoordinateBuffer leftSideBuffer, rightSideBuffer;
    private final OverflowingPreallocatedBuffer<GridRowHelper> rowInfoBuffer;

    /**
     * Deque to hold all tiles (including the guardian).
     */
    private final FixedMaxSizeDeque<Tile> tiles;

    /**
     * Points to the newest tile added.
     */
    private Tile lastTile;
    private Tile currTile;

    public int getCurrRowCount() {
        return min(leftSideBuffer.size(), rightSideBuffer.size())-1;
    }

    public int getLastTimeCntRowsAdded() {
        return getCurrRowCount() - min(lastTimeCntRowsLeft, lastTimeCntRowsRight) + 1;
    }

    public TileBuilder(int maxSegments, int nCols, Vector3D startMid, float segWidth, float segLength, float rowSpacing) {

        this.tiles = new FixedMaxSizeDeque<>(maxSegments + 1);

        this.nCols = nCols;
        this.rowSpacing = rowSpacing;
        this.segLength = segLength;

        // Create the "guardian" tile as the first tile
        Vector3D startLeft = V3(startMid.sub(segWidth / 2, 0, 0));
        Vector3D startRight = V3(startMid.add(segWidth / 2, 0, 0));

        // Guardian tile: store near and far edges the same
        // nearLeft
        // nearRight
        // farLeft
        // farRight
        // slope
        Tile guardian = new Tile(
                startLeft,                                       // nearLeft
                startRight,                                      // nearRight
                startLeft.sub(0, 0, 0.01f),              // farLeft
                startRight.sub(0, 0, 0.01f),             // farRight
                0.0f                                             // slope
        );

        this.lastTileProgressLeft = (float) (Math.sqrt(guardian.farLeft.sub(guardian.nearLeft).sqlen()));
        this.lastTileProgressRight = (float) (Math.sqrt(guardian.farRight.sub(guardian.nearRight).sqlen()));

        this.tiles.pushBack(guardian);
        this.lastTile = guardian;
        this.currTile = null;

        this.currHorizontalAng = 0.0f;
        this.currVerticalAng = 0.0f;
        this.dHorizontalAng = 0.0f;

        this.leftSideBuffer = new OverflowingPreallocatedCoordinateBuffer();
        this.rightSideBuffer = new OverflowingPreallocatedCoordinateBuffer();

        Vector3D startLeftRow = new Vector3D(guardian.farLeft.x, guardian.farLeft.y, guardian.farLeft.z);
        Vector3D startRightRow = new Vector3D(guardian.farRight.x, guardian.farRight.y, guardian.farRight.z);

        leftSideBuffer.addPos(startLeftRow.x, startLeftRow.y, startLeftRow.z);
        rightSideBuffer.addPos(startRightRow.x, startRightRow.y, startRightRow.z);

        this.rowInfoBuffer = new OverflowingPreallocatedBuffer<>(
                GridRowHelper.class,
                GridRowHelper::new
        );

    }

    private void updateLeft(Vector3D nl, Vector3D fl) {

        float lastLen = lastTile == null ? 0.0f
                : (float) (Math.sqrt(lastTile.farLeft.sub(lastTile.nearLeft).sqlen()));

        Vector3D edgeLeft = fl.sub(nl);
        float len = (float) (Math.sqrt(edgeLeft.sqlen()));
        Vector3D leftSide;
        float borderRowPosHere;

        // Calc the amount of space we have in this tile
        // (considering progress in previous tile)
        if (abs(lastLen - lastTileProgressLeft) < 0.0001f) {
            // progress in previous tile was too small - ignoring it
            // (or we are the first real tile or even the guardian)
            borderRowPosHere = 0.0f;
            if (this.tiles.size() == 1) {
                // we are guardian
                leftSideBuffer.addPos(fl.x,fl.y,fl.z);
                lastTileProgressLeft = len;
                return;
            }
        } else {
            // Add the row that overlaps this and last tile
            borderRowPosHere = rowSpacing - (lastLen - lastTileProgressLeft);
            leftSide = nl.add(edgeLeft.withLen(borderRowPosHere));

            leftSideBuffer.addPos(leftSide.x, leftSide.y, leftSide.z);
        }
        lastLastTileProgressLeft = lastTileProgressLeft;
        lastTileProgressLeft = borderRowPosHere;
        while (lastTileProgressLeft + rowSpacing <= len + 10f * EPSILON) {
            lastTileProgressLeft = min(lastTileProgressLeft + rowSpacing, len);
            leftSide = nl.add(edgeLeft.withLen(lastTileProgressLeft));
            leftSideBuffer.addPos(leftSide.x, leftSide.y, leftSide.z);
        }
    }

    private void updateRight(Vector3D nr, Vector3D fr) {

        float lastLen = lastTile == null ? 0.0f
                : (float) (Math.sqrt(lastTile.farRight.sub(lastTile.nearRight).sqlen()));

        Vector3D edgeRight = fr.sub(nr);
        float len = (float) (Math.sqrt(edgeRight.sqlen()));
        Vector3D rightSide;
        float borderRowPosHere;
        // Calc the amount of space we have in this tile
        // (considering progress in previous tile)
        if (abs(lastLen - lastTileProgressRight) < 0.0001f) {
            // progress in previous tile was too small - ignoring it
            // (or we are the first real tile or even the guardian)
            borderRowPosHere = 0.0f;
            if (this.tiles.size() == 1) {
                // we are guardian
                rightSideBuffer.addPos(fr.x,fr.y,fr.z);
                lastTileProgressRight = len;
                return;
            }else{
                // we are first real tile
                rowInfoBuffer.add(new GridRowHelper(
                        currTile.farLeft,
                        currTile.farLeft.sub(currTile.nearLeft),
                        currTile.nearRight.sub(currTile.nearLeft),
                        V3(
                                leftSideBuffer.getX(0),
                                leftSideBuffer.getY(0),
                                leftSideBuffer.getZ(0)
                        ),
                        nr
                ));
            }
        } else {
            // Add the row that overlaps this and last tile
            borderRowPosHere = rowSpacing - (lastLen - lastTileProgressRight);
            rightSide = nr.add(edgeRight.withLen(borderRowPosHere));

            rightSideBuffer.addPos(rightSide.x, rightSide.y, rightSide.z);

            // Vector3D farLeft,
            // Vector3D eL,
            // Vector3D eN,
            // Vector3D startL,
            // Vector3D endR
            int rowInd = getCurrRowCount() - 1;
            rowInfoBuffer.add(new GridRowHelper(
                    currTile.farLeft,
                    currTile.farLeft.sub(currTile.nearLeft),
                    currTile.nearRight.sub(currTile.nearLeft),
                    V3(
                        leftSideBuffer.getX(rowInd),
                        leftSideBuffer.getY(rowInd),
                        leftSideBuffer.getZ(rowInd)
                    ),
                    V3(rightSide.x, rightSide.y, rightSide.z)
            ));
        }
        lastLastTileProgressRight = lastTileProgressRight;
        lastTileProgressRight = borderRowPosHere;
        while (lastTileProgressRight + rowSpacing <= len + 10f * EPSILON) {
            lastTileProgressRight = min(lastTileProgressRight + rowSpacing, len);
            rightSide = nr.add(edgeRight.withLen(lastTileProgressRight));
            rightSideBuffer.addPos(rightSide.x, rightSide.y, rightSide.z);

            // Vector3D farLeft,
            // Vector3D eL,
            // Vector3D eN,
            // Vector3D startL,
            // Vector3D endR
            int ind = rightSideBuffer.size()-1;
            rowInfoBuffer.add(new GridRowHelper(
                    currTile.farLeft,
                    currTile.farLeft.sub(currTile.nearLeft),
                    currTile.nearRight.sub(currTile.nearLeft),
                    V3(
                            leftSideBuffer.getX(ind),
                            leftSideBuffer.getY(ind),
                            leftSideBuffer.getZ(ind)
                    ),
                    V3(rightSide.x, rightSide.y, rightSide.z)
            ));
        }
    }

    /**
     * Helper: builds a new tile and adds it to the deque.
     */
    void addTile(Vector3D nl, Vector3D nr, Vector3D fl, Vector3D fr) {

        lastTimeCntRowsLeft = leftSideBuffer.size();
        lastTimeCntRowsRight = rightSideBuffer.size();

        float slopeVal = (float) atan((fl.y - nl.y) /
                Math.sqrt((fl.x - nl.x) * (fl.x - nl.x) + (fl.z - nl.z) * (fl.z - nl.z)));

        Tile t = new Tile(nl, nr, fl, fr, slopeVal);
        this.tiles.pushBack(t);

        currTile = t;

        updateLeft(nl, fl);
        updateRight(nr, fr);

        lastTile = t;
        currTile = null;

    }

    /*
    Helper. Never called twice in a row.
     */
    private Tile removeLastTile() {
        Tile oldLast = tiles.popLast();
        int last = getLastTimeCntRowsAdded();
        System.out.println("XD "+last);
        for (int i = 0; i < last; ++i) {
            leftSideBuffer.pop();
            rightSideBuffer.pop();
            if(rowInfoBuffer.size()>0) {
                rowInfoBuffer.pop();
            }
        }
        lastTile = tiles.peekLast();
        currTile = null;
        lastTileProgressLeft = lastLastTileProgressLeft; // that's why not called twice in a row
        lastTileProgressRight = lastLastTileProgressRight;
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

        Vector3D axis = V3(0, -1, 0);

        Vector3D mid = l1.add(r1).div(2);
        Vector3D newL1 = rotateAroundAxis(mid, axis, l1, dHorizontalAng);
        Vector3D newR1 = rotateAroundAxis(mid, axis, r1, dHorizontalAng);

        assert (abs(newL1.y - newR1.y) < 0.01f);

        dHorizontalAng = 0.0f;

        Vector3D dir = rotateAroundTwoPoints(
                V3(0, 0, 0),
                newR1.sub(newL1),
                axis,
                PI / 2 + currVerticalAng).withLen(segLength);

        Vector3D l2 = newL1.add(dir);
        Vector3D r2 = newR1.add(dir);

        // Update the last tile's far edge.
        // Remove the old last tile, update it, and push it back.
        Tile oldLast = removeLastTile();
        System.out.println();
        System.out.println("======================================");
        System.out.println("After remove: ");
        System.out.println("L: ");
        for(Vector3D v3d : leftSideToArrayDebug()){
            System.out.print(v3d);
        }
        System.out.println();
        System.out.println("R: ");
        for(Vector3D v3d : rightSideToArrayDebug()){
            System.out.print(v3d);
        }
        System.out.println();
        System.out.println("row infos: "+rowInfoBuffer.size());
        System.out.println();

        addTile(oldLast.nearLeft, oldLast.nearRight,
                newL1, newR1);

        System.out.println("After first add: ");
        System.out.println("L: ");
        for(Vector3D v3d : leftSideToArrayDebug()){
            System.out.print(v3d);
        }
        System.out.println();
        System.out.println("R: ");
        for(Vector3D v3d : rightSideToArrayDebug()){
            System.out.print(v3d);
        }
        System.out.println();
        System.out.println("row infos: "+rowInfoBuffer.size());
        System.out.println();

        // Add the new tile segment.
        addTile(newL1, newR1, l2, r2);

        System.out.println("After second add: ");
        System.out.println("L: ");
        for(Vector3D v3d : leftSideToArrayDebug()){
            System.out.print(v3d);
        }
        System.out.println();
        System.out.println("R: ");
        for(Vector3D v3d : rightSideToArrayDebug()){
            System.out.print(v3d);
        }
        System.out.println();
        System.out.println("row infos: "+rowInfoBuffer.size());
        System.out.println();
        System.out.println("======================================");
        System.out.println();
    }


    public void removeOldTiles(float playerX, float playerY, float playerZ) {
        Vector3D pp = V3(playerX, playerY, playerZ);
        while (tiles.size() > 1 && tiles.getFirst().farLeft.sub(pp).sqlen() > 100f) {
            tiles.removeFirst();
        }
    }

    public void addVerticalAngle(float angle) {
        this.currVerticalAng += angle;
    }

    public void addHorizontalAngle(float angle) {
        this.dHorizontalAng = angle;
        this.currHorizontalAng += angle;
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

    private Vector3D getGridPoint(GridRowHelper info, int c){

        float leftEdgeLen = (float)(sqrt(info.eL.sqlen()));
        float nearEdgeLen = (float)(sqrt(info.eN.sqlen()));

        System.out.println("Info:");
        System.out.println(info);

        // Tales
        float d1 = nearEdgeLen * (float)(sqrt((info.farLeft.sub(info.startL)).sqlen())) / leftEdgeLen;

        Vector3D startR = info.startL.add(info.eN.withLen(d1));

        Vector3D eXD = info.endR.sub(startR);

        float d2 = (float)(sqrt((info.endR.sub(startR)).sqlen()));

        float colSpacing = (d1 + d2) / (float)(nCols);

        int borderIdx = (int)(nCols*d1 / (d1+d2));

        // Check which part
        if(c <= borderIdx){
            // Left triangle
            return info.startL.add(info.eN.mult(c*colSpacing / nearEdgeLen));
        }else{
            // Right triangle
            float len = (colSpacing * c) - d1;
            return startR.add(eXD.withLen(len));
        }
    }

    public Vector3D getGridPointDebug(int row, int col){
        return getGridPoint(rowInfoBuffer.get(row),col);
    }

    public Vector3D[] leftSideToArrayDebug(){
        Vector3D[] res = new Vector3D[leftSideBuffer.size()];
        for(int i=0;i<res.length;++i){
            res[i] = V3(leftSideBuffer.getX(i),leftSideBuffer.getY(i),leftSideBuffer.getZ(i)).addY(0.01f);
        }
        return res;
    }

    public Vector3D[] rightSideToArrayDebug(){
        Vector3D[] res = new Vector3D[rightSideBuffer.size()];
        for(int i=0;i<res.length;++i){
            res[i] = V3(rightSideBuffer.getX(i),rightSideBuffer.getY(i),rightSideBuffer.getZ(i)).addY(0.01f);
        }
        return res;
    }

    public GridRowHelper[] rowInfoToArrayDebug(){
        GridRowHelper[] res = new GridRowHelper[rowInfoBuffer.size()];
        for(int i=0;i<res.length;++i){
            GridRowHelper from = rowInfoBuffer.get(i);
            res[i] = new GridRowHelper(
                    from.farLeft,
                    from.eL,
                    from.eN,
                    from.startL,
                    from.endR
            );
        }
        return res;
    }


    public Vector3D[] getField(int row, int col) {

        /*Vector3D nearLeftOnSide = V3(
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
        Vector3D farRight = farLeftOnSide.add(farEdge.mult(col).div(nCols));*/

        return new Vector3D[]{
                getGridPoint(rowInfoBuffer.get(row-1),col-1),
                getGridPoint(rowInfoBuffer.get(row-1),col),
                getGridPoint(rowInfoBuffer.get(row),col-1),
                getGridPoint(rowInfoBuffer.get(row),col)
        };
    }

    public static class GridRowHelper {
        public Vector3D farLeft = V3(0, 0, 0), startL = V3(0, 0, 0), endR = V3(0, 0, 0);
        public Vector3D eL = V3(0, 0, 0), eN = V3(0, 0, 0);

        public GridRowHelper() {
        }

        public GridRowHelper(Vector3D farLeft, Vector3D eL, Vector3D eN, Vector3D startL, Vector3D endR) {
            this.farLeft = farLeft;
            this.eL = eL;
            this.eN = eN;
            this.startL = startL;
            this.endR = endR;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("INFO: ").append(System.lineSeparator());
            sb.append("farLeft: ").append(farLeft).append(System.lineSeparator());
            sb.append("eL:      ").append(eL).append(System.lineSeparator());
            sb.append("eN:      ").append(eN).append(System.lineSeparator());
            sb.append("startL:  ").append(startL).append(System.lineSeparator());
            sb.append("endR:    ").append(endR).append(System.lineSeparator());
            sb.append(System.lineSeparator()); // extra blank line
            return sb.toString();
        }
    }

}
