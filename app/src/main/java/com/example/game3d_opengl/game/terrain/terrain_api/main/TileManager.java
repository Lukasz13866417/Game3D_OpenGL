package com.example.game3d_opengl.game.terrain.terrain_api.main;

import static com.example.game3d_opengl.game.util.GameMath.EPSILON;
import static com.example.game3d_opengl.game.util.GameMath.NINF;
import static com.example.game3d_opengl.game.util.GameMath.PI;
import static com.example.game3d_opengl.game.util.GameMath.rotateAroundAxis;
import static com.example.game3d_opengl.game.util.GameMath.roundToDecimals;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;
import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.round;

import com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.FixedMaxSizeDeque;
import com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.OverflowingPreallocatedRowInfoBuffer;
import com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.OverflowingPreallocatedSegmentHistoryBuffer;
import com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.TerrainLandscapeRenderer;
import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.game.stage.stages.test.util.LineSet3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a deque of terrain tiles and, in parallel, keeps three buffers
 * – left edge points, right edge points and per-row metadata – that are always
 * size-synchronised.  Row placement is driven by a single "centre-line"
 * distance counter so it remains consistent even when the tile turns.
 */
public class TileManager implements GPUResourceOwner {

    /*––––––––––––  CONFIG & STATE  ––––––––––––*/
    private final float rowSpacing;                 // desired spacing between logical rows
    private final float segLength;                  // preferred tile length (used by generators)
    private final int nCols;                      // grid columns – passed to GridCreator

    /*––––––––––––  BUFFERS  ––––––––––––*/
    private final OverflowingPreallocatedRowInfoBuffer rowInfoBuffer;
    private final OverflowingPreallocatedSegmentHistoryBuffer segmentHistoryBuffer;

    private final FixedMaxSizeDeque<Tile> tiles;    // includes the guardian
    private final TerrainLandscapeRenderer landscapeRenderer;

    private Tile lastTile;                          // newest tile (back of deque)

    /*–––––––––––– Information about the state of the geometry ––––––––––––*/
    private float dHorizontalAng = 0f, currHorizontalAng = 0f, currVerticalAng = 0f;
    private float pendingLift = 0f;
    private long nextId = 0L;

    /**
     * "Upcoming" alphas applied to vertices pushed to {@link TerrainLandscapeRenderer}.
     * Important: the last-tile rewrite path in {@link #addSegment(boolean)} must not
     * accidentally apply these upcoming alphas to the *previous* tile.
     */
    private float alphaL = 1, alphaR = 1;

    // ============================================================================
    // PUBLIC CONSTRUCTOR
    // ============================================================================

    private static final float BIG_LEN = 10f;



    public TileManager(int maxSegments, int nCols,
                       Vector3D startMid,
                       float segWidth, float segLength,
                       float rowSpacing) {

        this.rowSpacing = rowSpacing;
        this.segLength = segLength;
        this.nCols = nCols;

        /*–––– data structures ––––*/
        this.rowInfoBuffer = new OverflowingPreallocatedRowInfoBuffer();

        this.tiles = new FixedMaxSizeDeque<>(maxSegments + 1);
        this.segmentHistoryBuffer = new OverflowingPreallocatedSegmentHistoryBuffer();


        /*–––– guardian tile (length close to 0) ––––*/
        Vector3D startLeft = V3(startMid.sub(segWidth / 2, 0, 0));
        Vector3D startRight = V3(startMid.add(segWidth / 2, 0, 0));

        Vector3D forward = V3(0,0,-BIG_LEN);

        this.landscapeRenderer = new TerrainLandscapeRenderer();

        addTile(startLeft.sub(forward),
                startRight.sub(forward),
                startLeft,   // farLeft = nearLeft shifted so len>0
                startRight,  // farRight
                true, false, false);

    }

    // ============================================================================
    // PUBLIC METHODS (THE API)
    // ============================================================================

    /**
     * Creates one additional tile, continuing from {@code lastTile}'s far edge.
     */
    public void addSegment(boolean isEmpty) {
        if (tiles.size() == tiles.getMaxSize()) {
            throw new IllegalStateException("Already at capacity");
        }

        Vector3D l1 = lastTile.farLeft;
        Vector3D r1 = lastTile.farRight;

        Vector3D axis = V3(0, -1, 0);

        Vector3D mid = l1.add(r1).div(2);

        Vector3D newL1 = rotateAroundAxis(mid,axis,l1,dHorizontalAng);
        Vector3D newR1 = rotateAroundAxis(mid,axis,r1,dHorizontalAng);

        dHorizontalAng = 0.0f;

        Vector3D dir = rotateAroundAxis(
                V3(0,0,0),
                V3(0,-1,0),
                newR1.sub(newL1),
                -PI/2
        ).withLen(segLength);

        dir = rotateAroundAxis(
                V3(0,0,0),
                newR1.sub(newL1),
                dir,
                currVerticalAng
        );

        Vector3D l2 = newL1.add(dir);
        Vector3D r2 = newR1.add(dir);


        // Update the last tile's far edge by replacing it with [newL1,newR1].
        // NOTE: addSegment() temporarily removes and re-adds the previous tile to rewrite its far edge.
        // We must preserve that tile's original alpha values, otherwise a future call to
        // setUpcomingAlphas() would "leak" into the previous strip and cause visible alpha mixing
        // at strip boundaries.
        SegmentHistory lastHistory = segmentHistoryBuffer.get(segmentHistoryBuffer.size() - 1);
        boolean wasLastLiftedUp = lastHistory.isFirstLiftedUp;
        final float lastAlphaL = lastHistory.alphaL;
        final float lastAlphaR = lastHistory.alphaR;
        Tile oldLast = removeLastTile();

        isReAdded = true;
        if(tiles.isEmpty()){ // re-adding guardian - oldLast.isEmptySegment() -> true
            // so oldLast.isFirstLiftedUp() -> false, and isFirstLiftedUp doesn't matter
            addTile(oldLast.nearLeft, oldLast.nearRight, newL1, newR1,
                    oldLast.isEmptySegment(), false, false,
                    lastAlphaL, lastAlphaR);
        }else{
            addTile(oldLast.nearLeft, oldLast.nearRight, newL1, newR1,
                    oldLast.isEmptySegment(),
                    tiles.getLast().isEmptySegment(),
                    wasLastLiftedUp,
                    lastAlphaL, lastAlphaR);
        }
        isReAdded = false;

        Vector3D nL = newL1.addY(pendingLift);
        Vector3D nR = newR1.addY(pendingLift);
        Vector3D fL = l2.addY(pendingLift);
        Vector3D fR = r2.addY(pendingLift);

        /*assert(testParallel(
                (fL.sub(nL)),
                (fR.sub(nR))
        ));*/ // this should pass for every segment right after it's created.
        // Changing horizontal ang with addHorizontalAng and then adding a new segment should
        // cause them to stop being parallel

        // Add the new segment tile.
        addTile(nL,
                nR,
                fL,
                fR,
                isEmpty,
                oldLast.isEmptySegment(),
                (abs(pendingLift) > EPSILON),
                // use current upcoming alphas for the NEW tile
                alphaL, alphaR);

        pendingLift = 0;
    }

    public void liftUp(float dy) {
        this.pendingLift += dy;
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

    public void removeOldTiles(long oldestAcceptable) {
        // If first tile is far from player, this it has been visited a long time ago
        // In that case, it can be removed because we are sure it wont be displayed anymore.
        System.out.println("%%%"+oldestAcceptable+ " "+tiles.getFirst().getID());
        while (tiles.size() > 1
                && (tiles.getFirst().isEmptySegment() || tiles.getFirst().getID() < oldestAcceptable)) {
            if(!tiles.getFirst().isEmptySegment()) {
                landscapeRenderer.popFront();
            }
            tiles.popFirst();
        }
    }

    @Override
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {
        while (!tiles.isEmpty()) {
            tiles.popFirst();
        }
        landscapeRenderer.cleanupGPUResourcesRecursivelyOnContextLoss();

        // TODO this should only do GPU stuff. Make separate method for buffers etc
        rowInfoBuffer.free();
        segmentHistoryBuffer.free();
    }

    /**
     * Recreate GL buffers for the landscape renderer and restore geometry
     * from the CPU mirror after a context loss or first-time init.
     */
    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss(){
        landscapeRenderer.reloadGPUResourcesRecursivelyOnContextLoss();
    }


    // ============================================================================
    // GETTERS AND SETTERS
    // ============================================================================

    public int getCurrRowCount() {
        return rowInfoBuffer.size();
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
            return rowInfoBuffer.get(0).tileID;
        }
        return rowInfoBuffer.get(row - 1).tileID;
    }

    /**
     * Returns a named-corner view of the grid cell at (row, col).
     *
     * Historical note: this used to return a {@code Vector3D[]} with a confusing implicit order.
     * We keep the mapping consistent with the previous behavior:
     * array[0]=nearLeft, array[1]=farLeft, array[2]=farRight, array[3]=nearRight.
     */
    public TerrainGridField getField(int row, int col) {
        GridRowInfo info = rowInfoBuffer.get(row - 1);
        Vector3D p0 = getGridPoint(info, col - 1, true);
        Vector3D p1 = getGridPoint(info, col, true);
        Vector3D p2 = getGridPoint(info, col, false);
        Vector3D p3 = getGridPoint(info, col - 1, false);

        // Preserve the previous implicit ordering semantics:
        // nearLeft=p0, farLeft=p1, farRight=p2, nearRight=p3.
        return new TerrainGridField(
                p0, /* nearLeft */
                p3, /* nearRight */
                p1, /* farLeft */
                p2  /* farRight */
        );
    }

    public void setUpcomingAlphas(float alphaL, float alphaR){
        this.alphaL = alphaL;
        this.alphaR = alphaR;
    }

    // ============================================================================
    // PACKAGE-PRIVATE AND PRIVATE METHODS
    // ============================================================================

    /**
     * Helper: builds a new tile and fully integrates it with row/buffer tracking.
     */
    private void addTile(Vector3D nl, Vector3D nr, Vector3D fl, Vector3D fr, boolean isEmptySegment, boolean wasPreviousEmpty, boolean isFirstLiftedUp) {
        addTile(nl, nr, fl, fr, isEmptySegment, wasPreviousEmpty, isFirstLiftedUp, alphaL, alphaR);
    }

    /**
     * Internal helper that allows the caller to control which alpha values are used for the
     * vertices appended to the {@link TerrainLandscapeRenderer}.
     */
    private void addTile(Vector3D nl, Vector3D nr, Vector3D fl, Vector3D fr,
                         boolean isEmptySegment, boolean wasPreviousEmpty, boolean isFirstLiftedUp,
                         float alphaLUsed, float alphaRUsed) {

        Tile tile = new Tile(nl, nr, fl, fr, nextId++, isEmptySegment);
        assert tiles.isEmpty() || tile.getID() > tiles.getLast().getID();
        tiles.pushBack(tile);
        if(!isEmptySegment) {
            if (wasPreviousEmpty || isFirstLiftedUp) {
                landscapeRenderer.newStrip();
                landscapeRenderer.pushBack(nl, nr, alphaLUsed, alphaRUsed);
            }
            landscapeRenderer.pushBack(fl, fr, alphaLUsed, alphaRUsed);
        }

        generateRowsForTile(tile, wasPreviousEmpty, isFirstLiftedUp, alphaLUsed, alphaRUsed);

        lastTile = tile;
    }

    boolean isReAdded = false;

    /**
     * Removes the current last tile (never the guardian) and rolls back all
     * associated buffers so the builder can overwrite that tile.
     */
    private Tile removeLastTile() {
        // It is legal to remove the guardian (only inside addSegment() ) – the very
        // next addTile() will immediately create a proper first tile.  We only
        // forbid popping when the deque is already empty (should never happen).
        if (tiles.isEmpty()) {
            throw new IllegalStateException("Tile deque is empty");
        }

        Tile oldLast = tiles.popLast();

        SegmentHistory history = segmentHistoryBuffer.pop();
        for (int i = 0; i < history.rowsAddedCnt; ++i) rowInfoBuffer.removeLast();

        // Only pop geometry from the ribbon if the removed tile actually added geometry.
        if (!oldLast.isEmptySegment()) {
            landscapeRenderer.popBack();
        }
        lastTile = tiles.peekLast();
        return oldLast;
    }


    private void generateRowsForTile(Tile tile, boolean wasPreviousEmpty, boolean isFirstLiftedUp,
                                     float alphaLUsed, float alphaRUsed) {
        // Currently, row ends are spaced by rowSpacing only on the longer side.
        // On the shorter side, they are spaced proportionally more densely.
        // But there might be another approach.
        // Like, force perpendicularity of row edges to long edge, don't calc spacing on short side.
        // Another approach is to force the row edge to be parallel to tile's near edge or far edge.
        Vector3D nl = tile.nearLeft;
        Vector3D nr = tile.nearRight;
        Vector3D fl = tile.farLeft;
        Vector3D fr = tile.farRight;
        Vector3D el = fl.sub(nl);
        Vector3D er = fr.sub(nr);
        float elL = (float) Math.sqrt(el.sqlen());
        float elR = (float) Math.sqrt(er.sqlen());

        if (tile.isEmptySegment()) {
            segmentHistoryBuffer.add(
                    true,
                    false,
                    0,
                    nl, nr,
                    fl, fr,
                    fl, fr,
                    0,
                    alphaLUsed, alphaRUsed
            );
            return;
        }

        // Thanks to guardian rows (for empty&guardian segments), segmentHistoryBuffer is not empty.
        SegmentHistory lastSegmentInfo = segmentHistoryBuffer.get(segmentHistoryBuffer.size()-1);

        int cntRows = 0;
        float oldLeftover = roundToDecimals(lastSegmentInfo.leftover,2);
        float spacingShorter = NINF, distShorter = NINF;
        float longerLen = elL;
        boolean isLeftLonger = elL > elR;
        float distLonger = rowSpacing - oldLeftover;
        if(isLeftLonger){
            spacingShorter = rowSpacing  * elR / elL;
            distShorter = (rowSpacing - oldLeftover) * elR / elL;
            longerLen = elL;
        }else{
            spacingShorter = rowSpacing * elL / elR;
            distShorter = (rowSpacing - oldLeftover) * elL / elR;
            longerLen = elR;
        }

        Vector3D lastNL = lastSegmentInfo.lastL, lastNR = lastSegmentInfo.lastR;
        if(wasPreviousEmpty || isFirstLiftedUp){
            lastNL = nl;
            lastNR = nr;
        }
        assert lastNL != null;
        for(; distLonger < longerLen; distLonger += rowSpacing){
            Vector3D currNL, currNR;
            if(isLeftLonger){
                currNL = nl.add(el.withLen(distLonger));
                currNR = nr.add(er.withLen(distShorter));
            }else{
                currNL = nl.add(el.withLen(distShorter));
                currNR = nr.add(er.withLen(distLonger));
            }
            distShorter += spacingShorter;
            rowInfoBuffer.add(
                    tile.getID(),
                    lastNL,
                    lastNR,
                    currNL,
                    currNR
            );
            lastNL = currNL;
            lastNR = currNR;
            ++cntRows;
        }

        float ourLeftover = (float)(Math.sqrt(isLeftLonger ? fl.sub(lastNL).sqlen() : fr.sub(lastNR).sqlen()));
        assert ourLeftover >= 0;

        segmentHistoryBuffer.add(
                false,
                isFirstLiftedUp,
                cntRows,
                nl, nr,
                fl, fr,
                lastNL, lastNR,
                ourLeftover,
                alphaLUsed, alphaRUsed);
    }

    public void printTiles(){
        System.out.println("<> TILES: ");
        for(Tile t  :tiles){
            System.out.println("<> "+t);
        }
        System.out.println("<> ====================");
    }

    /**
     * Build a simple debug outline of the terrain using only current tiles.
     * Produces a {@link LineSet3D} that traces:
     * - a cap across the near edge at the start of each visible span,
     * - the left and right side edges of each non-empty tile,
     * - and a cap across the far edge at the end of each visible span.
     * A new span starts after an empty tile or whenever the seam between tiles is vertically lifted
     * (detected when previous far edge != current near edge within EPSILON).
     */
    public LineSet3D getTileLineSet() {
        List<Vector3D> points = new ArrayList<>();
        List<int[]> edges = new ArrayList<>();

        Tile prevVisible = null;
        boolean spanActive = false;
        int lastFarLIdx = -1, lastFarRIdx = -1;

        for (Tile t : tiles) {
            if (t.isEmptySegment()) {
                if (spanActive) {
                    // Close previous span with a far cap
                    edges.add(new int[]{lastFarLIdx, lastFarRIdx});
                    spanActive = false;
                    prevVisible = null;
                    lastFarLIdx = lastFarRIdx = -1;
                }
                continue;
            }

            boolean startNewSpan = !spanActive
                    || prevVisible == null
                    || !(Vector3D.approxEq(prevVisible.farLeft, t.nearLeft, EPSILON)
                    && Vector3D.approxEq(prevVisible.farRight, t.nearRight, EPSILON));

            int nearLIdx, nearRIdx;
            if (startNewSpan) {
                if (spanActive) {
                    // Close the previous span before starting a new one
                    edges.add(new int[]{lastFarLIdx, lastFarRIdx});
                }
                // Start cap at the near edge of this span
                nearLIdx = points.size(); points.add(t.nearLeft);
                nearRIdx = points.size(); points.add(t.nearRight);
                edges.add(new int[]{nearLIdx, nearRIdx});
            } else {
                // Continue span: near edge matches previous far edge
                nearLIdx = lastFarLIdx;
                nearRIdx = lastFarRIdx;
            }

            // Sides for this tile
            int farLIdx = points.size(); points.add(t.farLeft);
            int farRIdx = points.size(); points.add(t.farRight);
            edges.add(new int[]{nearLIdx, farLIdx}); // left side
            edges.add(new int[]{nearRIdx, farRIdx}); // right side

            lastFarLIdx = farLIdx;
            lastFarRIdx = farRIdx;
            prevVisible = t;
            spanActive = true;
        }

        // Close the last span if open
        if (spanActive) {
            edges.add(new int[]{lastFarLIdx, lastFarRIdx});
        }

        Vector3D[] ptsArr = points.toArray(new Vector3D[0]);
        int[][] edgesArr = edges.toArray(new int[0][]);

        return new LineSet3D(ptsArr, edgesArr, FColor.CLR(1f, 1f, 1f), FColor.CLR(1f, 0f, 1f));
    }

    private Vector3D getGridPoint(GridRowInfo rowInfo, int c, boolean isTop) {
        if (isTop) {
            Vector3D edge = rowInfo.RS.sub(rowInfo.LS);
            return rowInfo.LS.add(edge.mult((float) c / nCols));
        }
        Vector3D edge = rowInfo.RS_last.sub(rowInfo.LS_last);
        return rowInfo.LS_last.add(edge.mult((float) c / nCols));
    }

    public void updateBeforeDraw(float dt){
    }

    public void draw(FColor color, float[] vp, LightSource light) {
        landscapeRenderer.draw(color, vp, light);
    }


    public void updateAfterDraw(float dt){
    }

    // ============================================================================
    // INNER CLASSES
    // ============================================================================

    public static class SegmentHistory {
        public boolean isEmpty = false;
        public boolean isFirstLiftedUp = false;
        public int rowsAddedCnt = 0;
        /** Alphas used for this tile's ribbon vertices (left/right). */
        public float alphaL = 1f, alphaR = 1f;
        public Vector3D nLOfTile = V3(0,0,0), nROfTile = V3(0,0,0);
        public Vector3D fLOfTile = V3(0,0,0), fROfTile = V3(0,0,0);

        public Vector3D lastL =  V3(0,0,0), lastR = V3(0,0,0);

        public float leftover = 0.0f;

        public SegmentHistory() {
        }

        public void set(boolean isEmpty,
                        boolean isFirstLiftedUp,
                        int rowsAddedCnt,
                        Vector3D nLOfTile, Vector3D nROfTile,
                        Vector3D fLOfTile, Vector3D fROfTile,
                        Vector3D lastL, Vector3D lastR,
                        float leftover,
                        float alphaL, float alphaR) {
            this.isEmpty = isEmpty;
            this.isFirstLiftedUp = isFirstLiftedUp;
            this.rowsAddedCnt = rowsAddedCnt;
            this.nLOfTile = nLOfTile;
            this.nROfTile = nROfTile;
            this.fLOfTile = fLOfTile;
            this.fROfTile = fROfTile;
            this.lastL = lastL;
            this.lastR = lastR;
            this.leftover = leftover;
            this.alphaL = alphaL;
            this.alphaR = alphaR;
        }
    }

    public static class GridRowInfo {

        public long tileID = -1;
        public Vector3D LS = V3(0, 0, 0), RS = V3(0, 0, 0);
        public Vector3D LS_last = V3(0, 0, 0), RS_last = V3(0, 0, 0);

        public GridRowInfo() {
        }

        public void set(long tileID,
                        Vector3D LS, Vector3D RS,
                        Vector3D LS_last, Vector3D RS_last) {
            this.tileID = tileID;
            this.LS = LS;
            this.RS = RS;
            this.LS_last = LS_last;
            this.RS_last = RS_last;
        }
    }
}