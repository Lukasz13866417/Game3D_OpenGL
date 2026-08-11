package com.example.game3d_opengl.game.terrain.terrain_api.main.tilemanager;

import static com.example.game3d_opengl.game.util.GameMath.EPSILON;
import static com.example.game3d_opengl.game.util.GameMath.NINF;
import static com.example.game3d_opengl.game.util.GameMath.PI;
import static com.example.game3d_opengl.game.util.GameMath.rotateAroundAxisTo;
import static com.example.game3d_opengl.game.util.GameMath.roundToDecimals;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;
import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.round;

import com.example.game3d_opengl.game.pooling.PooledSlotLease;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Tile;
import com.example.game3d_opengl.game.terrain.terrain_api.main.TileProfile;
import com.example.game3d_opengl.game.terrain.terrain_api.main.TerrainGridField;
import com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.FixedMaxSizeDeque;
import com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.OverflowingPreallocatedRowInfoBuffer;
import com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.OverflowingPreallocatedSegmentHistoryBuffer;
import com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.TerrainLandscapeRenderer;
import com.example.game3d_opengl.game.util.GameMath.MutableVec3;
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
    private int removedTileCount = 0;
    private final MutableVec3 scratchRotatedLeft = new MutableVec3();
    private final MutableVec3 scratchRotatedRight = new MutableVec3();
    private final MutableVec3 scratchForwardDir = new MutableVec3();
    private final MutableVec3 scratchTiltedDir = new MutableVec3();

    /**
     * "Upcoming" alphas applied to vertices pushed to {@link TerrainLandscapeRenderer}.
     * Important: the last-tile rewrite path in {@link #addSegment(boolean)} must not
     * accidentally apply these upcoming alphas to the *previous* tile.
     */
    private float alphaL = 1, alphaR = 1;
    private TileProfile upcomingTileProfile = TileProfile.NORMAL;
    private float upcomingBrightnessMultiplier = TileProfile.NORMAL.getBrightnessMultiplier();

    // ============================================================================
    // PUBLIC CONSTRUCTOR
    // ============================================================================

    private static final float BIG_LEN = 10f;



    public TileManager(int maxSegments, int nCols,
                       Vector3D startMid,
                       float segWidth, float segLength,
                       float rowSpacing) {
        this(maxSegments, nCols, startMid, segWidth, segLength, rowSpacing,
                TileManagerResourcePack.defaultInstance());
    }

    public TileManager(int maxSegments, int nCols,
                Vector3D startMid,
                float segWidth, float segLength,
                float rowSpacing,
                TileManagerResourcePack resourcePack) {
        OverflowingPreallocatedRowInfoBuffer createdRowInfoBuffer = null;
        OverflowingPreallocatedSegmentHistoryBuffer createdSegmentHistoryBuffer = null;
        try {
            this.rowSpacing = rowSpacing;
            this.segLength = segLength;
            this.nCols = nCols;

            PooledSlotLease<GridRowInfo[]> rowInfoLease = resourcePack.rowInfoPool().acquire();
            createdRowInfoBuffer = new OverflowingPreallocatedRowInfoBuffer(rowInfoLease);
            this.rowInfoBuffer = createdRowInfoBuffer;

            /*–––– data structures ––––*/
            this.tiles = new FixedMaxSizeDeque<>(maxSegments + 1);

            PooledSlotLease<SegmentHistory[]> segmentHistoryLease =
                    resourcePack.segmentHistoryPool().acquire();
            createdSegmentHistoryBuffer =
                    new OverflowingPreallocatedSegmentHistoryBuffer(segmentHistoryLease);
            this.segmentHistoryBuffer = createdSegmentHistoryBuffer;


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
        } catch (Throwable t) {
            if (createdSegmentHistoryBuffer != null) {
                createdSegmentHistoryBuffer.release();
            }
            if (createdRowInfoBuffer != null) {
                createdRowInfoBuffer.release();
            }
            throw t;
        }
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
        float l1x = l1.x;
        float l1y = l1.y;
        float l1z = l1.z;
        float r1x = r1.x;
        float r1y = r1.y;
        float r1z = r1.z;
        float midX = 0.5f * (l1x + r1x);
        float midY = 0.5f * (l1y + r1y);
        float midZ = 0.5f * (l1z + r1z);

        rotateAroundAxisTo(
                scratchRotatedLeft,
                midX, midY, midZ,
                0f, -1f, 0f,
                l1x, l1y, l1z,
                dHorizontalAng
        );
        rotateAroundAxisTo(
                scratchRotatedRight,
                midX, midY, midZ,
                0f, -1f, 0f,
                r1x, r1y, r1z,
                dHorizontalAng
        );

        dHorizontalAng = 0.0f;

        float edgeX = scratchRotatedRight.x - scratchRotatedLeft.x;
        float edgeY = scratchRotatedRight.y - scratchRotatedLeft.y;
        float edgeZ = scratchRotatedRight.z - scratchRotatedLeft.z;
        rotateAroundAxisTo(
                scratchForwardDir,
                0f, 0f, 0f,
                0f, -1f, 0f,
                edgeX, edgeY, edgeZ,
                -PI / 2f
        );
        float horizontalDirLen = (float) Math.sqrt(
                scratchForwardDir.x * scratchForwardDir.x
                        + scratchForwardDir.y * scratchForwardDir.y
                        + scratchForwardDir.z * scratchForwardDir.z
        );
        float forwardScale = horizontalDirLen > EPSILON ? segLength / horizontalDirLen : 0f;
        scratchForwardDir.x *= forwardScale;
        scratchForwardDir.y *= forwardScale;
        scratchForwardDir.z *= forwardScale;
        rotateAroundAxisTo(
                scratchTiltedDir,
                0f, 0f, 0f,
                edgeX, edgeY, edgeZ,
                scratchForwardDir.x, scratchForwardDir.y, scratchForwardDir.z,
                currVerticalAng
        );

        float newL1x = scratchRotatedLeft.x;
        float newL1y = scratchRotatedLeft.y;
        float newL1z = scratchRotatedLeft.z;
        float newR1x = scratchRotatedRight.x;
        float newR1y = scratchRotatedRight.y;
        float newR1z = scratchRotatedRight.z;
        float l2x = newL1x + scratchTiltedDir.x;
        float l2y = newL1y + scratchTiltedDir.y;
        float l2z = newL1z + scratchTiltedDir.z;
        float r2x = newR1x + scratchTiltedDir.x;
        float r2y = newR1y + scratchTiltedDir.y;
        float r2z = newR1z + scratchTiltedDir.z;
        Vector3D rewrittenFarLeft = V3(newL1x, newL1y, newL1z);
        Vector3D rewrittenFarRight = V3(newR1x, newR1y, newR1z);


        // Update the last tile's far edge by replacing it with [newL1,newR1].
        // NOTE: addSegment() temporarily removes and re-adds the previous tile to rewrite its far edge.
        // We must preserve that tile's original alpha values, otherwise a future call to
        // setUpcomingAlphas() would "leak" into the previous strip and cause visible alpha mixing
        // at strip boundaries.
        SegmentHistory lastHistory = segmentHistoryBuffer.get(segmentHistoryBuffer.size() - 1);
        boolean wasLastLiftedUp = lastHistory.isFirstLiftedUp;
        final float lastAlphaL = lastHistory.alphaL;
        final float lastAlphaR = lastHistory.alphaR;
        final float lastBrightnessMultiplier = lastHistory.brightnessMultiplier;
        Tile oldLast = removeLastTile();
        TileProfile lastProfile = oldLast.getProfile();

        isReAdded = true;
        if(tiles.isEmpty()){ // re-adding guardian - oldLast.isEmptySegment() -> true
            // so oldLast.isFirstLiftedUp() -> false, and isFirstLiftedUp doesn't matter
            addTile(oldLast.nearLeft, oldLast.nearRight, rewrittenFarLeft, rewrittenFarRight,
                    oldLast.isEmptySegment(), false, false,
                    lastAlphaL, lastAlphaR, lastProfile, lastBrightnessMultiplier);
        }else{
            addTile(oldLast.nearLeft, oldLast.nearRight, rewrittenFarLeft, rewrittenFarRight,
                    oldLast.isEmptySegment(),
                    tiles.getLast().isEmptySegment(),
                    wasLastLiftedUp,
                    lastAlphaL, lastAlphaR, lastProfile, lastBrightnessMultiplier);
        }
        isReAdded = false;

        float lift = pendingLift;
        boolean hasLift = abs(lift) > EPSILON;
        Vector3D nL = hasLift ? V3(newL1x, newL1y + lift, newL1z) : rewrittenFarLeft;
        Vector3D nR = hasLift ? V3(newR1x, newR1y + lift, newR1z) : rewrittenFarRight;
        Vector3D fL = V3(l2x, l2y + lift, l2z);
        Vector3D fR = V3(r2x, r2y + lift, r2z);

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
                hasLift,
                // use current upcoming alphas for the NEW tile
                alphaL, alphaR, upcomingTileProfile, upcomingBrightnessMultiplier);

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
        while (tiles.size() > 1
                && (tiles.getFirst().isEmptySegment() || tiles.getFirst().getID() < oldestAcceptable)) {
            if(!tiles.getFirst().isEmptySegment()) {
                landscapeRenderer.popFront();
            }
            tiles.popFirst();
            removedTileCount++;
        }
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        while (!tiles.isEmpty()) {
            tiles.popFirst();
        }
        landscapeRenderer.cleanupGPUResourcesRecursively();

        // TODO this should only do GPU stuff. Make separate method for buffers etc
        segmentHistoryBuffer.release();
        rowInfoBuffer.release();
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

    public int getFirstVisibleTileAbsoluteIndex() {
        return tiles.isEmpty() ? -1 : removedTileCount;
    }

    public int getAbsoluteTileIndexForVisibleIndex(int visibleTileIndex) {
        if (visibleTileIndex < 0 || visibleTileIndex >= tiles.size()) {
            throw new IndexOutOfBoundsException(
                    "Visible tile index " + visibleTileIndex + " out of bounds for size " + tiles.size()
            );
        }
        return removedTileCount + visibleTileIndex;
    }

    public int getLastGeneratedTileIndex() {
        return tiles.isEmpty() ? -1 : removedTileCount + tiles.size() - 1;
    }

    public Tile getTile(int i) {
        return tiles.get(i);
    }

    public int findLastTileIndexAtOrBefore(long tileId) {
        int low = 0;
        int high = tiles.size() - 1;
        int best = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midId = tiles.get(mid).getID();
            if (midId <= tileId) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    public int getAbsoluteTileIndexAtOrBefore(long tileId) {
        if (tiles.isEmpty()) {
            return -1;
        }
        int visibleIdx = tileId >= 0 ? findLastTileIndexAtOrBefore(tileId) : -1;
        if (visibleIdx < 0) {
            return getFirstVisibleTileAbsoluteIndex();
        }
        return getAbsoluteTileIndexForVisibleIndex(visibleIdx);
    }

    public float getSegmentLength() {
        return segLength;
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
     * Mapped to the actual geometry:
     * nearLeft = (row, col-1) top
     * nearRight = (row, col) top
     * farLeft = (row, col-1) bottom
     * farRight = (row, col) bottom
     */
    public TerrainGridField getField(int row, int col) {
        float[] corners = new float[12];
        writeFieldCorners(row, col, corners);

        // nearLeft=p0, nearRight=p1, farLeft=p3, farRight=p2.
        return new TerrainGridField(
                V3(corners[0], corners[1], corners[2]),
                V3(corners[3], corners[4], corners[5]),
                V3(corners[6], corners[7], corners[8]),
                V3(corners[9], corners[10], corners[11])
        );
    }

    /**
     * Returns a named-corner region for a horizontal strip of {@code length} cells,
     * starting at {@code (row, startCol)}.
     *
     * <p>The returned corners describe the entire region footprint and can be used
     * for addons that span multiple neighboring fields.
     */
    public TerrainGridField getHorizontalRegionField(int row, int startCol, int length) {
        assert row >= 1 && row <= rowInfoBuffer.size();
        assert startCol >= 1 && startCol <= nCols;
        assert length >= 1;
        assert startCol + length - 1 <= nCols;

        float[] corners = new float[12];
        writeHorizontalRegionFieldCorners(row, startCol, length, corners);
        return new TerrainGridField(
                V3(corners[0], corners[1], corners[2]),
                V3(corners[3], corners[4], corners[5]),
                V3(corners[6], corners[7], corners[8]),
                V3(corners[9], corners[10], corners[11])
        );
    }

    public void writeFieldCorners(int row, int col, float[] outCorners) {
        if (outCorners == null || outCorners.length < 12) {
            throw new IllegalArgumentException("outCorners must contain at least 12 floats");
        }
        GridRowInfo info = rowInfoBuffer.get(row - 1);
        writeGridPoint(info, col - 1, true, outCorners, 0);
        writeGridPoint(info, col, true, outCorners, 3);
        writeGridPoint(info, col - 1, false, outCorners, 6);
        writeGridPoint(info, col, false, outCorners, 9);
    }

    public void writeHorizontalRegionFieldCorners(int row, int startCol, int length, float[] outCorners) {
        assert row >= 1 && row <= rowInfoBuffer.size();
        assert startCol >= 1 && startCol <= nCols;
        assert length >= 1;
        assert startCol + length - 1 <= nCols;
        if (outCorners == null || outCorners.length < 12) {
            throw new IllegalArgumentException("outCorners must contain at least 12 floats");
        }
        GridRowInfo info = rowInfoBuffer.get(row - 1);
        writeGridPoint(info, startCol - 1, true, outCorners, 0);
        writeGridPoint(info, startCol + length - 1, true, outCorners, 3);
        writeGridPoint(info, startCol - 1, false, outCorners, 6);
        writeGridPoint(info, startCol + length - 1, false, outCorners, 9);
    }

    public void setUpcomingAlphas(float alphaL, float alphaR){
        this.alphaL = alphaL;
        this.alphaR = alphaR;
    }

    public void setUpcomingTileProfile(TileProfile profile) {
        upcomingTileProfile = profile != null ? profile : TileProfile.NORMAL;
    }

    public void setUpcomingBrightnessMultiplier(float brightnessMultiplier) {
        upcomingBrightnessMultiplier = brightnessMultiplier;
    }

    // ============================================================================
    // PACKAGE-PRIVATE AND PRIVATE METHODS
    // ============================================================================

    /**
     * Helper: builds a new tile and fully integrates it with row/buffer tracking.
     */
    private void addTile(Vector3D nl, Vector3D nr, Vector3D fl, Vector3D fr, boolean isEmptySegment, boolean wasPreviousEmpty, boolean isFirstLiftedUp) {
        addTile(
                nl,
                nr,
                fl,
                fr,
                isEmptySegment,
                wasPreviousEmpty,
                isFirstLiftedUp,
                alphaL,
                alphaR,
                upcomingTileProfile,
                upcomingBrightnessMultiplier
        );
    }

    /**
     * Internal helper that allows the caller to control which alpha values are used for the
     * vertices appended to the {@link TerrainLandscapeRenderer}.
     */
    private void addTile(Vector3D nl, Vector3D nr, Vector3D fl, Vector3D fr,
                         boolean isEmptySegment, boolean wasPreviousEmpty, boolean isFirstLiftedUp,
                         float alphaLUsed, float alphaRUsed, TileProfile tileProfileUsed,
                         float brightnessMultiplierUsed) {

        TileProfile safeProfile = tileProfileUsed != null ? tileProfileUsed : TileProfile.NORMAL;
        Tile tile = new Tile(
                nl, nr, fl, fr, nextId++, isEmptySegment, safeProfile, brightnessMultiplierUsed
        );
        assert tiles.isEmpty() || tile.getID() > tiles.getLast().getID();
        tiles.pushBack(tile);
        if(!isEmptySegment) {
            if (wasPreviousEmpty || isFirstLiftedUp) {
                landscapeRenderer.newStrip();
                landscapeRenderer.pushBack(
                        nl,
                        nr,
                        alphaLUsed,
                        alphaRUsed,
                        brightnessMultiplierUsed,
                        brightnessMultiplierUsed
                );
            }
            landscapeRenderer.pushBack(
                    fl,
                    fr,
                    alphaLUsed,
                    alphaRUsed,
                    brightnessMultiplierUsed,
                    brightnessMultiplierUsed
            );
        }

        generateRowsForTile(
                tile, wasPreviousEmpty, isFirstLiftedUp, alphaLUsed, alphaRUsed, brightnessMultiplierUsed
        );

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
                                     float alphaLUsed, float alphaRUsed,
                                     float brightnessMultiplierUsed) {
        // Currently, row ends are spaced by rowSpacing only on the longer side.
        // On the shorter side, they are spaced proportionally more densely.
        // But there might be another approach.
        // Like, force perpendicularity of row edges to long edge, don't calc spacing on short side.
        // Another approach is to force the row edge to be parallel to tile's near edge or far edge.
        Vector3D nl = tile.nearLeft;
        Vector3D nr = tile.nearRight;
        Vector3D fl = tile.farLeft;
        Vector3D fr = tile.farRight;
        final long tileId = tile.getID();
        final float nlx = nl.x, nly = nl.y, nlz = nl.z;
        final float nrx = nr.x, nry = nr.y, nrz = nr.z;
        final float flx = fl.x, fly = fl.y, flz = fl.z;
        final float frx = fr.x, fry = fr.y, frz = fr.z;

        final float elx = flx - nlx;
        final float ely = fly - nly;
        final float elz = flz - nlz;
        final float erx = frx - nrx;
        final float ery = fry - nry;
        final float erz = frz - nrz;
        final float elL = (float) Math.sqrt(elx * elx + ely * ely + elz * elz);
        final float elR = (float) Math.sqrt(erx * erx + ery * ery + erz * erz);

        if (tile.isEmptySegment()) {
            segmentHistoryBuffer.add(
                    true,
                    false,
                    0,
                    nlx, nly, nlz,
                    nrx, nry, nrz,
                    flx, fly, flz,
                    frx, fry, frz,
                    flx, fly, flz,
                    frx, fry, frz,
                    0,
                    alphaLUsed, alphaRUsed,
                    tile.getProfile(),
                    brightnessMultiplierUsed
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

        float lastNLx = lastSegmentInfo.lastLx;
        float lastNLy = lastSegmentInfo.lastLy;
        float lastNLz = lastSegmentInfo.lastLz;
        float lastNRx = lastSegmentInfo.lastRx;
        float lastNRy = lastSegmentInfo.lastRy;
        float lastNRz = lastSegmentInfo.lastRz;
        if(wasPreviousEmpty || isFirstLiftedUp){
            lastNLx = nlx;
            lastNLy = nly;
            lastNLz = nlz;
            lastNRx = nrx;
            lastNRy = nry;
            lastNRz = nrz;
        }
        for(; distLonger < longerLen; distLonger += rowSpacing){
            float currNLx, currNLy, currNLz;
            float currNRx, currNRy, currNRz;
            if(isLeftLonger){
                float leftScale = distLonger / elL;
                float rightScale = distShorter / elR;
                currNLx = nlx + elx * leftScale;
                currNLy = nly + ely * leftScale;
                currNLz = nlz + elz * leftScale;
                currNRx = nrx + erx * rightScale;
                currNRy = nry + ery * rightScale;
                currNRz = nrz + erz * rightScale;
            }else{
                float leftScale = distShorter / elL;
                float rightScale = distLonger / elR;
                currNLx = nlx + elx * leftScale;
                currNLy = nly + ely * leftScale;
                currNLz = nlz + elz * leftScale;
                currNRx = nrx + erx * rightScale;
                currNRy = nry + ery * rightScale;
                currNRz = nrz + erz * rightScale;
            }
            distShorter += spacingShorter;
            rowInfoBuffer.add(
                    tileId,
                    lastNLx, lastNLy, lastNLz,
                    lastNRx, lastNRy, lastNRz,
                    currNLx, currNLy, currNLz,
                    currNRx, currNRy, currNRz
            );
            lastNLx = currNLx;
            lastNLy = currNLy;
            lastNLz = currNLz;
            lastNRx = currNRx;
            lastNRy = currNRy;
            lastNRz = currNRz;
            ++cntRows;
        }

        float ourLeftover;
        if (isLeftLonger) {
            float dx = flx - lastNLx;
            float dy = fly - lastNLy;
            float dz = flz - lastNLz;
            ourLeftover = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        } else {
            float dx = frx - lastNRx;
            float dy = fry - lastNRy;
            float dz = frz - lastNRz;
            ourLeftover = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        assert ourLeftover >= 0;

        segmentHistoryBuffer.add(
                false,
                isFirstLiftedUp,
                cntRows,
                nlx, nly, nlz,
                nrx, nry, nrz,
                flx, fly, flz,
                frx, fry, frz,
                lastNLx, lastNLy, lastNLz,
                lastNRx, lastNRy, lastNRz,
                ourLeftover,
                alphaLUsed, alphaRUsed,
                tile.getProfile(),
                brightnessMultiplierUsed);
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
            float t = (float) c / nCols;
            float lx = rowInfo.LSx;
            float ly = rowInfo.LSy;
            float lz = rowInfo.LSz;
            float rx = rowInfo.RSx;
            float ry = rowInfo.RSy;
            float rz = rowInfo.RSz;
            return V3(
                    lx + (rx - lx) * t,
                    ly + (ry - ly) * t,
                    lz + (rz - lz) * t
            );
        }
        float t = (float) c / nCols;
        float lx = rowInfo.LS_lastx;
        float ly = rowInfo.LS_lasty;
        float lz = rowInfo.LS_lastz;
        float rx = rowInfo.RS_lastx;
        float ry = rowInfo.RS_lasty;
        float rz = rowInfo.RS_lastz;
        return V3(
                lx + (rx - lx) * t,
                ly + (ry - ly) * t,
                lz + (rz - lz) * t
        );
    }

    private void writeGridPoint(GridRowInfo rowInfo, int c, boolean isTop, float[] out, int offset) {
        float t = (float) c / nCols;
        float lx;
        float ly;
        float lz;
        float rx;
        float ry;
        float rz;
        if (isTop) {
            lx = rowInfo.LSx;
            ly = rowInfo.LSy;
            lz = rowInfo.LSz;
            rx = rowInfo.RSx;
            ry = rowInfo.RSy;
            rz = rowInfo.RSz;
        } else {
            lx = rowInfo.LS_lastx;
            ly = rowInfo.LS_lasty;
            lz = rowInfo.LS_lastz;
            rx = rowInfo.RS_lastx;
            ry = rowInfo.RS_lasty;
            rz = rowInfo.RS_lastz;
        }
        out[offset] = lx + (rx - lx) * t;
        out[offset + 1] = ly + (ry - ly) * t;
        out[offset + 2] = lz + (rz - lz) * t;
    }

    public void updateBeforeDraw(float dt){
    }

    public void draw(FColor color, float[] vp, LightSource light) {
        landscapeRenderer.draw(color, vp, light);
    }


    public void updateAfterDraw(float dt){
    }

    public void rebasePosition(Vector3D delta) {
        if (delta == null) return;
        float dx = delta.x;
        float dy = delta.y;
        float dz = delta.z;
        if (dx == 0f && dy == 0f && dz == 0f) return;
        for (Tile tile : tiles) {
            if (tile != null) {
                tile.rebasePosition(delta);
            }
        }
        for (int i = 0; i < rowInfoBuffer.size(); i++) {
            GridRowInfo info = rowInfoBuffer.get(i);
            info.LSx += dx;
            info.LSy += dy;
            info.LSz += dz;
            info.RSx += dx;
            info.RSy += dy;
            info.RSz += dz;
            info.LS_lastx += dx;
            info.LS_lasty += dy;
            info.LS_lastz += dz;
            info.RS_lastx += dx;
            info.RS_lasty += dy;
            info.RS_lastz += dz;
        }
        for (int i = 0; i < segmentHistoryBuffer.size(); i++) {
            SegmentHistory info = segmentHistoryBuffer.get(i);
            info.nLx += dx;
            info.nLy += dy;
            info.nLz += dz;
            info.nRx += dx;
            info.nRy += dy;
            info.nRz += dz;
            info.fLx += dx;
            info.fLy += dy;
            info.fLz += dz;
            info.fRx += dx;
            info.fRy += dy;
            info.fRz += dz;
            info.lastLx += dx;
            info.lastLy += dy;
            info.lastLz += dz;
            info.lastRx += dx;
            info.lastRy += dy;
            info.lastRz += dz;
        }
        landscapeRenderer.rebasePosition(delta);
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
        public TileProfile tileProfile = TileProfile.NORMAL;
        public float brightnessMultiplier = TileProfile.NORMAL.getBrightnessMultiplier();
        public float nLx = 0f, nLy = 0f, nLz = 0f;
        public float nRx = 0f, nRy = 0f, nRz = 0f;
        public float fLx = 0f, fLy = 0f, fLz = 0f;
        public float fRx = 0f, fRy = 0f, fRz = 0f;

        public float lastLx = 0f, lastLy = 0f, lastLz = 0f;
        public float lastRx = 0f, lastRy = 0f, lastRz = 0f;

        public float leftover = 0.0f;

        public SegmentHistory() {
        }

        public void set(boolean isEmpty,
                        boolean isFirstLiftedUp,
                        int rowsAddedCnt,
                        float nLx, float nLy, float nLz,
                        float nRx, float nRy, float nRz,
                        float fLx, float fLy, float fLz,
                        float fRx, float fRy, float fRz,
                        float lastLx, float lastLy, float lastLz,
                        float lastRx, float lastRy, float lastRz,
                        float leftover,
                        float alphaL, float alphaR,
                        TileProfile tileProfile,
                        float brightnessMultiplier) {
            this.isEmpty = isEmpty;
            this.isFirstLiftedUp = isFirstLiftedUp;
            this.rowsAddedCnt = rowsAddedCnt;
            this.nLx = nLx;
            this.nLy = nLy;
            this.nLz = nLz;
            this.nRx = nRx;
            this.nRy = nRy;
            this.nRz = nRz;
            this.fLx = fLx;
            this.fLy = fLy;
            this.fLz = fLz;
            this.fRx = fRx;
            this.fRy = fRy;
            this.fRz = fRz;
            this.lastLx = lastLx;
            this.lastLy = lastLy;
            this.lastLz = lastLz;
            this.lastRx = lastRx;
            this.lastRy = lastRy;
            this.lastRz = lastRz;
            this.leftover = leftover;
            this.alphaL = alphaL;
            this.alphaR = alphaR;
            this.tileProfile = tileProfile != null ? tileProfile : TileProfile.NORMAL;
            this.brightnessMultiplier = brightnessMultiplier;
        }
    }

    public static class GridRowInfo {

        public long tileID = -1;
        public float LSx = 0f, LSy = 0f, LSz = 0f;
        public float RSx = 0f, RSy = 0f, RSz = 0f;
        public float LS_lastx = 0f, LS_lasty = 0f, LS_lastz = 0f;
        public float RS_lastx = 0f, RS_lasty = 0f, RS_lastz = 0f;

        public GridRowInfo() {
        }

        public void set(long tileID,
                        float LSx, float LSy, float LSz,
                        float RSx, float RSy, float RSz,
                        float LS_lastx, float LS_lasty, float LS_lastz,
                        float RS_lastx, float RS_lasty, float RS_lastz) {
            this.tileID = tileID;
            this.LSx = LSx;
            this.LSy = LSy;
            this.LSz = LSz;
            this.RSx = RSx;
            this.RSy = RSy;
            this.RSz = RSz;
            this.LS_lastx = LS_lastx;
            this.LS_lasty = LS_lasty;
            this.LS_lastz = LS_lastz;
            this.RS_lastx = RS_lastx;
            this.RS_lasty = RS_lasty;
            this.RS_lastz = RS_lastz;
        }
    }
}
