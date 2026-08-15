package com.example.game3d.authoring;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.SurfaceProperties;
import com.example.game3d.core.terrain.TerrainCommit;
import com.example.game3d.core.terrain.TerrainOutput;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.core.terrain.TerrainState;
import com.example.game3d.core.terrain.TerrainVertexAppearance;
import com.example.game3d.core.terrain.addon.Addon;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CPU-only structure interpreter and bounded canonical terrain producer.
 *
 * <p>Each top-level structure is captured and materialized privately. Only a fully frozen result
 * is appended to the planned stream; failed captures do not consume public IDs.</p>
 */
public final class Terrain implements TerrainOutput {
    public static final int DEFAULT_INTERACTION_WINDOW_AHEAD = 64;
    public static final int MAX_CAPTURED_TILES = 65_536;
    public static final int MAX_CAPTURED_ADDON_PLACEMENTS = 32_768;
    public static final int MAX_CAPTURED_OCCUPANCY_RECORDS = 32_768;
    public static final int MAX_PHYSICAL_GRID_ROWS = 65_536;
    private static final int RETAIN_BEHIND_SEGMENTS = 50;

    private final TrackProfile profile;
    private final long seed;
    private final TerrainState state = new TerrainState();
    private final ArrayDeque<PendingBuild> builds =
            new ArrayDeque<PendingBuild>();
    private final ArrayDeque<PendingPublication> publications =
            new ArrayDeque<PendingPublication>();
    private final ArrayList<TerrainCommit> pendingCommits =
            new ArrayList<TerrainCommit>();
    private Vec3 plannedCursor;
    private double plannedYaw;
    private double plannedPitch;
    private Vec3 plannedFarLeft;
    private Vec3 plannedFarRight;
    private TerrainVertexAppearance plannedFarLeftAppearance;
    private TerrainVertexAppearance plannedFarRightAppearance;
    private boolean plannedPreviousSolid;
    private GridCarry plannedGridCarry = GridCarry.empty();
    private GeometryCursor captureForecast;
    private GridCarry captureGridCarry = GridCarry.empty();
    private long nextSegmentId;
    private long nextAddonId = 1L;
    private long nextPairId = 1L;
    private long structureOrdinal;
    private boolean closed;

    public Terrain(TrackProfile profile, Vec3 startCenter, long seed) {
        if (profile == null || startCenter == null) {
            throw new IllegalArgumentException("profile and startCenter are required");
        }
        this.profile = profile;
        this.plannedCursor = startCenter;
        this.captureForecast = new GeometryCursor(
                profile, startCenter, 0.0, 0.0,
                null, null, null, null, false);
        this.seed = seed;
    }

    public synchronized QueuedStructure enqueueStructure(
            BaseTerrainStructure<?> structure) {
        requireOpen();
        if (structure == null) {
            throw new IllegalArgumentException("structure == null");
        }
        CaptureSession capture = new CaptureSession(
                profile, new DeterministicRandom(mix64(seed + structureOrdinal)),
                captureForecast.copy(), captureGridCarry);
        structure.capture(capture);
        capture.ensurePreviewGeometry();
        captureForecast = capture.previewCursor.copy();
        captureGridCarry = capture.previewGrid.carry();
        QueuedStructure ticket = new QueuedStructure();
        builds.addLast(new PendingBuild(capture.tiles, capture.placements, ticket));
        structureOrdinal++;
        return ticket;
    }

    /** Returns the exact completed physical GRID row count for a fresh standalone structure. */
    public static int derivePhysicalGridRowCount(
            BaseTerrainStructure<?> structure, TrackProfile profile,
            Vec3 startCenter, long seed) {
        if (structure == null || profile == null || startCenter == null) {
            throw new IllegalArgumentException(
                    "structure, profile, and startCenter are required");
        }
        CaptureSession capture = new CaptureSession(
                profile, new DeterministicRandom(seed),
                new GeometryCursor(profile, startCenter, 0.0, 0.0,
                        null, null, null, null, false), GridCarry.empty());
        structure.capture(capture);
        return capture.physicalRowCount();
    }

    public synchronized int generate(GenerationBudget budget) {
        requireOpen();
        if (budget == null) {
            throw new IllegalArgumentException("budget == null");
        }
        interpretCapturedCommands(budget.commandLimit);
        int segmentLimit = budget.segmentPublishLimit < 0
                ? Integer.MAX_VALUE : budget.segmentPublishLimit;
        if (segmentLimit == 0 || publications.isEmpty()) {
            return 0;
        }
        ArrayList<TerrainSegment> upserts = new ArrayList<TerrainSegment>();
        while (upserts.size() < segmentLimit && !publications.isEmpty()) {
            PendingPublication pending = publications.peekFirst();
            int remaining = segmentLimit - upserts.size();
            pending.copyNext(upserts, remaining);
            if (pending.complete()) {
                publications.removeFirst();
            }
        }
        if (upserts.isEmpty()) {
            return 0;
        }
        TerrainCommit commit = new TerrainCommit(
                state.revision(), state.revision() + 1L,
                upserts.get(upserts.size() - 1).id,
                state.retireBeforeSegmentId(), upserts);
        state.apply(commit);
        pendingCommits.add(commit);
        return upserts.size();
    }

    public int generateChunks(int segmentBudget) {
        return generate(new GenerationBudget(-1, segmentBudget));
    }

    public synchronized boolean hasPendingGenerationWork() {
        return !builds.isEmpty() || !publications.isEmpty();
    }

    public synchronized int getSegmentCount() {
        int count = 0;
        for (TerrainSegment ignored : state.segments()) {
            count++;
        }
        return count;
    }

    /** Retained plus frozen/pending segments, used to plan safely around very short levels. */
    public synchronized int getPlannedSegmentCount() {
        long count = getSegmentCount();
        for (PendingPublication publication : publications) {
            count += publication.remaining();
        }
        for (PendingBuild build : builds) {
            count += build.tiles.size();
        }
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    public synchronized void removeOldTerrainElements(long referenceSegmentId) {
        requireOpen();
        long desired = Math.max(0L, referenceSegmentId - RETAIN_BEHIND_SEGMENTS);
        desired = Math.min(desired, state.committedThroughSegmentId() + 1L);
        if (desired <= state.retireBeforeSegmentId()) {
            return;
        }
        TerrainCommit commit = new TerrainCommit(
                state.revision(), state.revision() + 1L,
                state.committedThroughSegmentId(), desired,
                Collections.<TerrainSegment>emptyList());
        state.apply(commit);
        pendingCommits.add(commit);
    }

    public synchronized int getCommittedLeadAheadOf(long referenceSegmentId) {
        if (state.committedThroughSegmentId() < 0L) {
            return 0;
        }
        long reference = Math.max(state.retireBeforeSegmentId(), referenceSegmentId);
        long lead = state.committedThroughSegmentId() - reference;
        return lead <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, lead);
    }

    public int getInteractionWindowAhead() {
        return DEFAULT_INTERACTION_WINDOW_AHEAD;
    }

    public double getSegmentLength() {
        return profile.tileLength;
    }

    public synchronized long resolveStreamingReferenceSegmentId(
            long lastSupportedSegmentId, Vec3 absolutePosition) {
        if (absolutePosition == null) {
            throw new IllegalArgumentException("absolutePosition == null");
        }
        long bestId = -1L;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (TerrainSegment segment : state.segments()) {
            Vec3 center = segment.nearLeft.add(segment.nearRight)
                    .add(segment.farLeft).add(segment.farRight).multiply(0.25);
            double distance = center.subtract(absolutePosition).lengthSquared();
            if (distance < bestDistance || (distance == bestDistance && segment.id > bestId)) {
                bestDistance = distance;
                bestId = segment.id;
            }
        }
        return bestId < 0 ? lastSupportedSegmentId : Math.max(lastSupportedSegmentId, bestId);
    }

    @Override
    public synchronized TerrainSnapshot snapshot() {
        return state.snapshot();
    }

    @Override
    public synchronized List<TerrainCommit> drainPendingCommits() {
        if (pendingCommits.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<TerrainCommit> result = new ArrayList<TerrainCommit>(pendingCommits);
        pendingCommits.clear();
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized long revision() {
        return state.revision();
    }

    @Override
    public synchronized void close() {
        builds.clear();
        publications.clear();
        pendingCommits.clear();
        closed = true;
    }

    public static MaterializedStructure materialize(
            BaseTerrainStructure<?> structure, TrackProfile profile,
            Vec3 startCenter, long seed) {
        return materialize(structure, profile, startCenter, 0.0, 0.0,
                seed, 0L, 1L, 1L,
                null, null, null, null, false)
                .materialized;
    }

    static CapturedStructureCommands captureResolvedCommands(
            BaseTerrainStructure<?> structure, TrackProfile profile, long seed) {
        if (structure == null || profile == null) {
            throw new IllegalArgumentException("structure and profile are required");
        }
        CaptureSession capture = new CaptureSession(
                profile, new DeterministicRandom(seed),
                new GeometryCursor(profile, Vec3.ZERO, 0.0, 0.0,
                        null, null, null, null, false), GridCarry.empty());
        structure.capture(capture);
        ArrayList<CapturedTileCommand> commands =
                new ArrayList<CapturedTileCommand>(capture.tiles.size());
        double yaw = 0.0;
        double pitch = 0.0;
        for (TileDraft tile : capture.tiles) {
            double nextYaw = tile.absoluteYaw ? tile.yaw : yaw + tile.yaw;
            double nextPitch = tile.absolutePitch ? tile.pitch : pitch + tile.pitch;
            commands.add(new CapturedTileCommand(
                    tile.solid, nextYaw - yaw, nextPitch, tile.lift,
                    tile.surface, tile.alphaLeft, tile.alphaRight, tile.brightness));
            yaw = nextYaw;
            pitch = nextPitch;
        }
        ArrayList<CapturedAddonPlacement> addonPlacements =
                new ArrayList<CapturedAddonPlacement>(capture.placements.size());
        for (PlacementDraft placement : capture.placements) {
            addonPlacements.add(new CapturedAddonPlacement(
                    placement.tileIndex, placement.tileEndIndex,
                    placement.gridRowStart, placement.gridRowEnd,
                    placement.gridPlacement,
                    placement.declaration, placement.poseAligned,
                    placement.acrossStart, placement.acrossEnd,
                    placement.alongStart, placement.alongEnd));
        }
        return new CapturedStructureCommands(commands, addonPlacements);
    }

    private static BuildResult materialize(
            BaseTerrainStructure<?> structure, TrackProfile profile,
            Vec3 startCenter, double startYaw, double startPitch, long seed,
            long firstSegmentId, long firstAddonId, long firstPairId,
            Vec3 previousFarLeft, Vec3 previousFarRight,
            TerrainVertexAppearance previousFarLeftAppearance,
            TerrainVertexAppearance previousFarRightAppearance,
            boolean previousSolid) {
        CaptureSession capture = new CaptureSession(
                profile, new DeterministicRandom(seed),
                new GeometryCursor(profile, startCenter, startYaw, startPitch,
                        previousFarLeft, previousFarRight,
                        previousFarLeftAppearance, previousFarRightAppearance,
                        previousSolid), GridCarry.empty());
        structure.capture(capture);
        return new StructureBuild(
                profile, startCenter, startYaw, startPitch, firstSegmentId,
                firstAddonId, firstPairId, capture.tiles, capture.placements,
                previousFarLeft, previousFarRight,
                previousFarLeftAppearance, previousFarRightAppearance,
                previousSolid, GridCarry.empty()).build();
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Terrain is closed");
        }
    }

    private void interpretCapturedCommands(int commandLimit) {
        int remaining = commandLimit < 0 ? Integer.MAX_VALUE : commandLimit;
        while (remaining > 0 && !builds.isEmpty()) {
            PendingBuild pending = builds.peekFirst();
            pending.startIfNeeded(new StructureBuild(
                    profile, plannedCursor, plannedYaw, plannedPitch,
                    nextSegmentId, nextAddonId, nextPairId,
                    pending.tiles, pending.placements,
                    plannedFarLeft, plannedFarRight,
                    plannedFarLeftAppearance, plannedFarRightAppearance,
                    plannedPreviousSolid, plannedGridCarry));
            int consumed;
            try {
                consumed = pending.advance(remaining);
            } catch (RuntimeException failure) {
                // A private build is transactional. Discard the failed build so a caller that
                // reports the error and continues cannot accidentally publish its partially
                // interpreted geometry on a later generate call.
                while (!builds.isEmpty()) {
                    PendingBuild invalidated = builds.removeFirst();
                    invalidated.ticket.fail(failure);
                }
                captureForecast = new GeometryCursor(
                        profile, plannedCursor, plannedYaw, plannedPitch,
                        plannedFarLeft, plannedFarRight,
                        plannedFarLeftAppearance, plannedFarRightAppearance,
                        plannedPreviousSolid);
                captureGridCarry = plannedGridCarry;
                throw failure;
            }
            remaining -= consumed;
            if (!pending.ready()) {
                break;
            }
            BuildResult built = pending.result();
            pending.ticket.complete(built.materialized);
            publications.addLast(new PendingPublication(built.materialized.segments));
            plannedCursor = built.cursor;
            plannedYaw = built.yaw;
            plannedPitch = built.pitch;
            plannedFarLeft = built.farLeft;
            plannedFarRight = built.farRight;
            plannedFarLeftAppearance = built.farLeftAppearance;
            plannedFarRightAppearance = built.farRightAppearance;
            plannedPreviousSolid = built.previousSolid;
            plannedGridCarry = built.gridCarry;
            nextSegmentId = built.nextSegmentId;
            nextAddonId = built.nextAddonId;
            nextPairId = built.nextPairId;
            builds.removeFirst();
        }
    }

    /** Capture context exposed to structure base classes, not to runtime consumers. */
    public static final class CaptureSession {
        private final TrackProfile profile;
        private final DeterministicRandom random;
        private final ArrayList<TileDraft> tiles = new ArrayList<TileDraft>();
        private final ArrayList<PlacementDraft> placements = new ArrayList<PlacementDraft>();
        private final ArrayList<OccupancyDraft> occupancies =
                new ArrayList<OccupancyDraft>();
        private final ArrayDeque<StructureCapture> structureCaptures =
                new ArrayDeque<StructureCapture>();
        private final ArrayDeque<AddonScope> scopes = new ArrayDeque<AddonScope>();
        private final TileBrush tileBrush = new TileBrush(this);
        private final BasicGridBrush basicGridBrush = new BasicGridBrush(this);
        private final AdvancedGridBrush advancedGridBrush = new AdvancedGridBrush(this);
        private final GeometryCursor previewCursor;
        private final PhysicalGridBuilder previewGrid;
        private int previewedTileCount;

        CaptureSession(
                TrackProfile profile, DeterministicRandom random,
                GeometryCursor previewCursor, GridCarry initialGridCarry) {
            this.profile = profile;
            this.random = random;
            this.previewCursor = previewCursor;
            this.previewGrid = new PhysicalGridBuilder(
                    profile.rowSpacing, initialGridCarry, MAX_PHYSICAL_GRID_ROWS);
        }

        public TrackProfile profile() { return profile; }
        public DeterministicRandom random() { return random; }
        public TileBrush tileBrush() { return tileBrush; }
        public BasicGridBrush basicGridBrush() { return basicGridBrush; }
        public AdvancedGridBrush advancedGridBrush() { return advancedGridBrush; }
        int tileCount() { return tiles.size(); }
        int physicalRowCount() {
            ensurePreviewGeometry();
            return previewGrid.size();
        }

        void ensurePreviewGeometry() {
            while (previewedTileCount < tiles.size()) {
                Geometry geometry = previewCursor.emit(
                        previewedTileCount, tiles.get(previewedTileCount));
                previewGrid.append(geometry);
                previewedTileCount++;
            }
        }

        void addPlacement(PlacementDraft placement) {
            if (placements.size() >= MAX_CAPTURED_ADDON_PLACEMENTS) {
                throw new IllegalArgumentException(
                        "Structure exceeds the addon placement limit of "
                                + MAX_CAPTURED_ADDON_PLACEMENTS);
            }
            placements.add(placement);
        }

        void addOccupancy(OccupancyDraft occupancy) {
            if (occupancies.size() >= MAX_CAPTURED_OCCUPANCY_RECORDS) {
                throw new IllegalArgumentException(
                        "Structure exceeds the occupancy record limit of "
                                + MAX_CAPTURED_OCCUPANCY_RECORDS);
            }
            occupancies.add(occupancy);
        }

        void beginStructureCapture() {
            structureCaptures.push(new StructureCapture(
                    placements.size(), occupancies.size()));
        }

        void finishStructureCapture(
                int firstRow, int rows, boolean propagate,
                int[] parentBlockedRowsRange) {
            if (structureCaptures.isEmpty()) {
                throw new IllegalStateException("No structure capture is active");
            }
            if (parentBlockedRowsRange != null
                    && (parentBlockedRowsRange.length != 2
                    || parentBlockedRowsRange[0] < 1
                    || parentBlockedRowsRange[1] < parentBlockedRowsRange[0]
                    || parentBlockedRowsRange[1] > rows)) {
                throw new IllegalArgumentException(
                        "Parent-blocked rows must be an in-range inclusive pair");
            }
            StructureCapture capture = structureCaptures.pop();
            if (!propagate) {
                for (int i = capture.firstPlacement; i < placements.size(); i++) {
                    placements.get(i).propagate = false;
                }
                for (int i = capture.firstOccupancy; i < occupancies.size(); i++) {
                    occupancies.get(i).propagate = false;
                }
            }
            if (parentBlockedRowsRange == null) {
                return;
            }
            addOccupancy(new OccupancyDraft(
                    firstRow + parentBlockedRowsRange[0] - 1,
                    firstRow + parentBlockedRowsRange[1] - 1,
                    1, profile.gridColumns, true));
        }

        void abortStructureCapture() {
            if (!structureCaptures.isEmpty()) {
                structureCaptures.pop();
            }
        }

        void beginAddonScope(
                int firstTile, int tileCount, int firstRow, int rows,
                boolean propagate) {
            ensurePreviewGeometry();
            AddonScope scope = new AddonScope(
                    firstTile, tileCount, firstRow, rows,
                    profile.gridColumns, propagate,
                    previewGrid.copyRows(firstRow, rows));
            // Child placements already exist when a composite opens its grid phase.
            for (PlacementDraft placement : placements) {
                if (!placement.propagate) {
                    continue;
                }
                double start = placement.poseAligned
                        ? 0.5 + placement.acrossStart
                                - placement.acrossEnd / profile.width
                        : placement.acrossStart;
                double end = placement.poseAligned
                        ? 0.5 + placement.acrossStart
                                + placement.acrossEnd / profile.width
                        : placement.acrossEnd;
                int firstColumn = Math.max(1,
                        (int) Math.floor(start * profile.gridColumns) + 1);
                int lastColumn = Math.min(profile.gridColumns,
                        Math.max(firstColumn,
                                (int) Math.ceil(end * profile.gridColumns)));
                if (placement.gridPlacement) {
                    int localStart = Math.max(placement.gridRowStart, firstRow) - firstRow + 1;
                    int localEnd = Math.min(
                            placement.gridRowEnd, firstRow + rows - 1) - firstRow + 1;
                    if (localStart <= localEnd) {
                        scope.markOccupied(
                                localStart, localEnd, firstColumn, lastColumn);
                    }
                    continue;
                }
                if (placement.tileIndex < firstTile
                        || placement.tileIndex >= firstTile + tileCount) {
                    continue;
                }
                for (int row = 1; row <= scope.rows; row++) {
                    int ownerTile = scope.physicalRows.get(row - 1).ownerTileIndex;
                    if (ownerTile != placement.tileIndex) continue;
                    for (int column = firstColumn; column <= lastColumn; column++) {
                        scope.occupied[row][column] = true;
                    }
                }
            }
            for (OccupancyDraft occupancy : occupancies) {
                if (!occupancy.propagate) {
                    continue;
                }
                int localStart = Math.max(occupancy.firstTile, firstRow) - firstRow + 1;
                int localEnd = Math.min(
                        occupancy.lastTile, firstRow + rows - 1) - firstRow + 1;
                if (localStart > localEnd) {
                    continue;
                }
                for (int row = localStart; row <= localEnd; row++) {
                    for (int column = occupancy.firstColumn;
                            column <= occupancy.lastColumn; column++) {
                        scope.occupied[row][column] = true;
                    }
                }
            }
            scopes.push(scope);
        }

        void endAddonScope() {
            scopes.pop();
        }

        AddonScope scope() {
            if (scopes.isEmpty()) {
                throw new IllegalStateException("Addon reservation outside a structure");
            }
            return scopes.peek();
        }
    }

    /** Original landscape authoring surface, now captured into immutable double-valued commands. */
    public static final class TileBrush {
        private final CaptureSession session;
        private double logicalYaw;
        private double pendingTurnDelta;
        private Double pendingAbsoluteYaw;
        private double logicalPitch;
        private double pendingPitchDelta;
        private Double pendingAbsolutePitch;
        private double liftBefore;
        private SurfaceProperties surface = SurfaceProperties.NORMAL;
        private float alphaLeft = 1f;
        private float alphaRight = 1f;
        private float brightness = 1f;
        private int generatedSourceCounter;

        TileBrush(CaptureSession session) { this.session = session; }

        public void setHorizontalAng(double radians) {
            logicalYaw = finite(radians, "yaw");
            pendingAbsoluteYaw = logicalYaw;
            pendingTurnDelta = 0.0;
        }
        public void addHorizontalAng(double radians) {
            double delta = finite(radians, "yaw delta");
            logicalYaw += delta;
            if (pendingAbsoluteYaw != null) pendingAbsoluteYaw += delta;
            else pendingTurnDelta += delta;
        }
        public void setVerticalAng(double radians) {
            logicalPitch = finite(radians, "pitch");
            pendingAbsolutePitch = logicalPitch;
            pendingPitchDelta = 0.0;
        }
        public void addVerticalAng(double radians) {
            double delta = finite(radians, "pitch delta");
            logicalPitch += delta;
            if (pendingAbsolutePitch != null) pendingAbsolutePitch += delta;
            else pendingPitchDelta += delta;
        }
        public void liftUp(double amount) { liftBefore += finite(amount, "lift"); }
        public void setCornerAlphas(float left, float right) {
            if (!Float.isFinite(left) || !Float.isFinite(right) || left < 0f || right < 0f) {
                throw new IllegalArgumentException("Invalid alpha");
            }
            alphaLeft = left;
            alphaRight = right;
        }
        public void setUpcomingBrightnessMultiplier(float value) {
            if (!Float.isFinite(value) || value < 0f) {
                throw new IllegalArgumentException("Invalid brightness");
            }
            brightness = value;
        }
        public void setUpcomingSurface(SurfaceProperties value) {
            surface = value == null ? SurfaceProperties.NORMAL : value;
        }
        public void addSegment() { addSegment(nextSourceId()); }
        public void addSegment(String sourceId) { add(false, sourceId); }
        public void addEmptySegment() { addEmptySegment(nextSourceId()); }
        public void addEmptySegment(String sourceId) { add(true, sourceId); }

        /** Exact editor command: turn delta is applied before emission; slope is absolute. */
        public void addTileDegrees(
                String sourceId, boolean solid, double turnDeltaDegrees,
                double absoluteSlopeDegrees, double preTileLift,
                SurfaceProperties tileSurface, float alpha, float tileBrightness) {
            addHorizontalAng(Math.toRadians(turnDeltaDegrees));
            setVerticalAng(Math.toRadians(absoluteSlopeDegrees));
            liftUp(preTileLift);
            setUpcomingSurface(tileSurface);
            setCornerAlphas(alpha, alpha);
            setUpcomingBrightnessMultiplier(tileBrightness);
            if (solid) addSegment(sourceId); else addEmptySegment(sourceId);
        }

        public void addChild(BaseTerrainStructure<?> child) {
            if (child != null) child.capture(session);
        }

        private void add(boolean gap, String sourceId) {
            if (sourceId == null || sourceId.isEmpty()) {
                throw new IllegalArgumentException("Tile source ID is empty");
            }
            if (session.tiles.size() >= MAX_CAPTURED_TILES) {
                throw new IllegalArgumentException(
                        "Structure exceeds the tile limit of " + MAX_CAPTURED_TILES);
            }
            session.tiles.add(new TileDraft(
                    sourceId, !gap,
                    pendingAbsoluteYaw != null ? pendingAbsoluteYaw : pendingTurnDelta,
                    pendingAbsoluteYaw != null,
                    pendingAbsolutePitch != null ? pendingAbsolutePitch : pendingPitchDelta,
                    pendingAbsolutePitch != null,
                    liftBefore, surface,
                    alphaLeft, alphaRight, brightness));
            liftBefore = 0.0;
            pendingTurnDelta = 0.0;
            pendingAbsoluteYaw = null;
            pendingPitchDelta = 0.0;
            pendingAbsolutePitch = null;
        }

        private String nextSourceId() {
            return "generated-tile-" + generatedSourceCounter++;
        }

        private static double finite(double value, String label) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException(label + " is not finite");
            return value;
        }
    }

    public abstract static class BaseGridBrush {
        final CaptureSession session;
        BaseGridBrush(CaptureSession session) { this.session = session; }

        public final void reserveVertical(
                int row, int column, int length, AddonBlueprint[] addons) {
            validateCount(length, addons);
            reserve(row, column, length, true, addons, false);
        }

        public final void reserveHorizontal(
                int row, int column, int length, AddonBlueprint[] addons) {
            validateCount(length, addons);
            reserve(row, column, length, false, addons, false);
        }

        public final void placeNormalized(
                int segment, double acrossStart, double acrossEnd,
                double alongStart, double alongEnd, AddonBlueprint addon) {
            AddonScope scope = session.scope();
            if (addon == null || segment < 1 || segment > scope.tileCount
                    || !(acrossStart >= 0.0 && acrossStart < acrossEnd && acrossEnd <= 1.0)
                    || !(alongStart >= 0.0 && alongStart < alongEnd && alongEnd <= 1.0)) {
                throw new IllegalArgumentException("Invalid normalized placement");
            }
            addSegmentLocal(segment, acrossStart, acrossEnd,
                    alongStart, alongEnd, addon);
        }

        /**
         * Places one addon over an inclusive rectangular grid region.
         *
         * <p>The footprint begins one quarter into {@code rowStart}, ends three quarters into
         * {@code rowEnd}, and spans the requested columns. Its owner is the first covered row.
         * Advanced brushes reserve every covered cell atomically; basic brushes retain their
         * historical non-exclusive behavior.</p>
         */
        public final void placeGridRegion(
                int rowStart, int rowEnd, int columnStart, int columnEnd,
                AddonBlueprint addon) {
            AddonScope scope = session.scope();
            if (rowStart < 1 || rowEnd < rowStart || rowEnd > scope.rows) {
                throw new IllegalArgumentException(
                        "Grid row range [" + rowStart + ", " + rowEnd
                                + "] exceeds derived physical row count " + scope.rows);
            }
            if (addon == null || columnStart < 1
                    || columnEnd < columnStart || columnEnd > scope.columns) {
                throw new IllegalArgumentException(
                        "Grid region is outside the structure");
            }
            reserveGridRegion(scope, rowStart, rowEnd, columnStart, columnEnd);
            double cell = 1.0 / scope.columns;
            addGridRegion(rowStart, rowEnd,
                    (columnStart - 1) * cell, columnEnd * cell,
                    0.25, 0.75, addon);
        }

        /** Exact segment-local placement used by parity-locked production structures. */
        public final void placePoseAlignedOnSegment(
                int segment, double lateralFractionOfWidth,
                double halfAcrossWorld, double halfAlongWorld,
                AddonBlueprint addon) {
            AddonScope scope = session.scope();
            if (addon == null || segment < 1 || segment > scope.tileCount
                    || !Double.isFinite(lateralFractionOfWidth)
                    || !(halfAcrossWorld > 0.0) || !(halfAlongWorld > 0.0)) {
                throw new IllegalArgumentException("Invalid pose-aligned placement");
            }
            session.addPlacement(PlacementDraft.poseAligned(
                    scope.firstTile + segment - 1, lateralFractionOfWidth,
                    halfAcrossWorld, halfAlongWorld, addon,
                    session.placements.size(), scope.propagate));
        }

        /** @deprecated Use {@link #placePoseAlignedOnSegment}; this argument is a segment. */
        @Deprecated
        public final void placePoseAligned(
                int segment, double lateralFractionOfWidth,
                double halfAcrossWorld, double halfAlongWorld,
                AddonBlueprint addon) {
            placePoseAlignedOnSegment(segment, lateralFractionOfWidth,
                    halfAcrossWorld, halfAlongWorld, addon);
        }

        abstract void reserve(
                int row, int column, int length, boolean vertical,
                AddonBlueprint[] addons, boolean randomized);

        void reserveGridRegion(
                AddonScope scope, int rowStart, int rowEnd,
                int columnStart, int columnEnd) {
            // Basic grids intentionally allow overlapping reservations.
        }

        final void addGridCell(
                int row, double acrossStart, double acrossEnd,
                double alongStart, double alongEnd, AddonBlueprint addon) {
            addGridRegion(row, row, acrossStart, acrossEnd,
                    alongStart, alongEnd, addon);
        }

        final void addGridRegion(
                int rowStart, int rowEnd, double acrossStart, double acrossEnd,
                double alongStart, double alongEnd, AddonBlueprint addon) {
            AddonScope scope = session.scope();
            PhysicalGridRow first = scope.physicalRows.get(rowStart - 1);
            PhysicalGridRow last = scope.physicalRows.get(rowEnd - 1);
            session.addPlacement(PlacementDraft.grid(
                    first.ownerTileIndex, last.ownerTileIndex,
                    scope.firstRow + rowStart - 1,
                    scope.firstRow + rowEnd - 1,
                    acrossStart, acrossEnd,
                    alongStart, alongEnd, addon, session.placements.size(), false,
                    scope.propagate));
        }

        final void addSegmentLocal(
                int segment, double acrossStart, double acrossEnd,
                double alongStart, double alongEnd, AddonBlueprint addon) {
            AddonScope scope = session.scope();
            session.addPlacement(new PlacementDraft(
                    scope.firstTile + segment - 1,
                    scope.firstTile + segment - 1,
                    -1, -1, false,
                    acrossStart, acrossEnd, alongStart, alongEnd,
                    addon, session.placements.size(), false, scope.propagate));
        }

        static void validateCount(int length, AddonBlueprint[] addons) {
            if (length < 1 || addons == null || addons.length != length) {
                throw new IllegalArgumentException("Addon count does not match reservation");
            }
        }
    }

    public static final class BasicGridBrush extends BaseGridBrush {
        BasicGridBrush(CaptureSession session) { super(session); }

        @Override void reserve(
                int row, int column, int length, boolean vertical,
                AddonBlueprint[] addons, boolean randomized) {
            validateRange(session.scope(), row, column, length, vertical);
            double cell = 1.0 / session.profile.gridColumns;
            for (int i = 0; i < length; i++) {
                int targetRow = vertical ? row + i : row;
                int targetCol = vertical ? column : column + i;
                addGridCell(targetRow, (targetCol - 1) * cell, targetCol * cell,
                        0.35, 0.65, addons[i]);
            }
        }
    }

    public static final class AdvancedGridBrush extends BaseGridBrush {
        AdvancedGridBrush(CaptureSession session) { super(session); }

        @Override void reserve(
                int row, int column, int length, boolean vertical,
                AddonBlueprint[] addons, boolean randomized) {
            AddonScope scope = session.scope();
            validateRange(scope, row, column, length, vertical);
            for (int i = 0; i < length; i++) {
                int targetRow = vertical ? row + i : row;
                int targetCol = vertical ? column : column + i;
                scope.reserve(targetRow, targetCol);
            }
            new BasicGridBrush(session).reserve(row, column, length, vertical, addons, randomized);
        }

        @Override void reserveGridRegion(
                AddonScope scope, int rowStart, int rowEnd,
                int columnStart, int columnEnd) {
            scope.reserveRegion(rowStart, rowEnd, columnStart, columnEnd);
        }

        public void reserveRandomFittingHorizontal(int length, AddonBlueprint[] addons) {
            validateCount(length, addons);
            int[] position = findRandomFit(length, false);
            reserve(position[0], position[1], length, false, addons, true);
        }

        public void reserveRandomFittingVertical(int length, AddonBlueprint[] addons) {
            validateCount(length, addons);
            int[] position = findRandomFit(length, true);
            reserve(position[0], position[1], length, true, addons, true);
        }

        public void reserveKRandomFields(AddonBlueprint[] addons) {
            if (addons == null || addons.length == 0) {
                throw new IllegalArgumentException("addons are empty");
            }
            ArrayList<int[]> free = session.scope().freeCells();
            if (addons.length > free.size()) {
                throw new IllegalStateException("Not enough free grid cells");
            }
            for (int i = free.size() - 1; i > 0; i--) {
                int j = session.random.nextInt(i + 1);
                Collections.swap(free, i, j);
            }
            List<int[]> selected = free.subList(0, addons.length);
            Collections.sort(selected, CELL_ORDER);
            for (int i = 0; i < addons.length; i++) {
                int[] cell = selected.get(i);
                reserve(cell[0], cell[1], 1, true,
                        new AddonBlueprint[] { addons[i] }, true);
            }
        }

        public void reserveHorizontalRegion(
                int row, int column, int length, AddonBlueprint addon) {
            if (addon == null) throw new IllegalArgumentException("addon == null");
            AddonScope scope = session.scope();
            validateRange(scope, row, column, length, false);
            placeGridRegion(row, row, column, column + length - 1, addon);
        }

        public void reserveRandomHorizontalRegion(int length, AddonBlueprint addon) {
            int[] position = findRandomFit(length, false);
            reserveHorizontalRegion(position[0], position[1], length, addon);
        }

        private int[] findRandomFit(int length, boolean vertical) {
            AddonScope scope = session.scope();
            ArrayList<int[]> fits = scope.fits(length, vertical);
            if (fits.isEmpty()) throw new IllegalStateException("No fitting grid reservation");
            return fits.get(session.random.nextInt(fits.size()));
        }
    }

    static final class PlacementIds {
        private long nextPair;
        private final Map<String, Long> pairs = new LinkedHashMap<String, Long>();
        PlacementIds(long nextPair) { this.nextPair = nextPair; }
        long portalPair(String key) {
            Long value = pairs.get(key);
            if (value == null) {
                value = nextPair++;
                pairs.put(key, value);
            }
            return value;
        }
    }

    private static final Comparator<int[]> CELL_ORDER = new Comparator<int[]>() {
        @Override public int compare(int[] a, int[] b) {
            int rows = Integer.compare(a[0], b[0]);
            return rows != 0 ? rows : Integer.compare(a[1], b[1]);
        }
    };

    private static void validateRange(
            AddonScope scope, int row, int column, int length, boolean vertical) {
        int rowEnd = vertical ? row + length - 1 : row;
        if (row < 1 || rowEnd > scope.rows) {
            throw new IllegalArgumentException(
                    "Grid row range [" + row + ", " + rowEnd
                            + "] exceeds derived physical row count " + scope.rows);
        }
        if (column < 1 || length < 1
                || (!vertical && column + length - 1 > scope.columns)
                || column > scope.columns) {
            throw new IllegalArgumentException("Grid reservation is outside the structure");
        }
    }

    private static final class AddonScope {
        final int firstTile;
        final int tileCount;
        final int firstRow;
        final int rows;
        final int columns;
        final boolean[][] occupied;
        final boolean propagate;
        final List<PhysicalGridRow> physicalRows;
        AddonScope(
                int firstTile, int tileCount, int firstRow, int rows,
                int columns, boolean propagate,
                List<PhysicalGridRow> physicalRows) {
            this.firstTile = firstTile;
            this.tileCount = tileCount;
            this.firstRow = firstRow;
            this.rows = rows;
            this.columns = columns;
            this.occupied = new boolean[rows + 1][columns + 1];
            this.propagate = propagate;
            this.physicalRows = physicalRows;
        }
        void reserve(int row, int col) {
            if (occupied[row][col]) throw new IllegalStateException("Grid cell already reserved");
            occupied[row][col] = true;
        }
        void reserveRegion(
                int rowStart, int rowEnd, int columnStart, int columnEnd) {
            for (int row = rowStart; row <= rowEnd; row++) {
                for (int column = columnStart; column <= columnEnd; column++) {
                    if (occupied[row][column]) {
                        throw new IllegalStateException("Grid cell already reserved");
                    }
                }
            }
            for (int row = rowStart; row <= rowEnd; row++) {
                for (int column = columnStart; column <= columnEnd; column++) {
                    occupied[row][column] = true;
                }
            }
        }
        void markOccupied(
                int rowStart, int rowEnd, int columnStart, int columnEnd) {
            for (int row = rowStart; row <= rowEnd; row++) {
                for (int column = columnStart; column <= columnEnd; column++) {
                    occupied[row][column] = true;
                }
            }
        }
        ArrayList<int[]> freeCells() {
            ArrayList<int[]> result = new ArrayList<int[]>();
            for (int r = 1; r <= rows; r++) for (int c = 1; c <= columns; c++)
                if (!occupied[r][c]) result.add(new int[] { r, c });
            return result;
        }
        ArrayList<int[]> fits(int length, boolean vertical) {
            ArrayList<int[]> result = new ArrayList<int[]>();
            for (int r = 1; r <= rows; r++) for (int c = 1; c <= columns; c++) {
                boolean fits = vertical ? r + length - 1 <= rows : c + length - 1 <= columns;
                for (int i = 0; fits && i < length; i++)
                    fits = !occupied[vertical ? r + i : r][vertical ? c : c + i];
                if (fits) result.add(new int[] { r, c });
            }
            return result;
        }
    }

    private static final class StructureCapture {
        final int firstPlacement;
        final int firstOccupancy;

        StructureCapture(int firstPlacement, int firstOccupancy) {
            this.firstPlacement = firstPlacement;
            this.firstOccupancy = firstOccupancy;
        }
    }

    /** Full-cell occupancy inherited through structure nesting; it never emits an addon. */
    private static final class OccupancyDraft {
        final int firstTile;
        final int lastTile;
        final int firstColumn;
        final int lastColumn;
        boolean propagate;

        OccupancyDraft(
                int firstTile, int lastTile, int firstColumn, int lastColumn,
                boolean propagate) {
            this.firstTile = firstTile;
            this.lastTile = lastTile;
            this.firstColumn = firstColumn;
            this.lastColumn = lastColumn;
            this.propagate = propagate;
        }
    }

    private static final class TileDraft {
        final String sourceId;
        final boolean solid;
        final double yaw;
        final boolean absoluteYaw;
        final double pitch;
        final boolean absolutePitch;
        final double lift;
        final SurfaceProperties surface;
        final float alphaLeft, alphaRight, brightness;
        TileDraft(String sourceId, boolean solid, double yaw, boolean absoluteYaw,
                double pitch, boolean absolutePitch, double lift,
                SurfaceProperties surface, float alphaLeft, float alphaRight, float brightness) {
            this.sourceId = sourceId; this.solid = solid; this.yaw = yaw; this.pitch = pitch;
            this.absoluteYaw = absoluteYaw;
            this.absolutePitch = absolutePitch;
            this.lift = lift; this.surface = surface; this.alphaLeft = alphaLeft;
            this.alphaRight = alphaRight; this.brightness = brightness;
        }
    }

    private static final class PlacementDraft {
        final int tileIndex;
        final int tileEndIndex;
        final int gridRowStart;
        final int gridRowEnd;
        final boolean gridPlacement;
        final double acrossStart, acrossEnd, alongStart, alongEnd;
        final AddonBlueprint addon;
        final int declaration;
        final boolean poseAligned;
        boolean propagate;
        PlacementDraft(
                int tileIndex, int tileEndIndex,
                int gridRowStart, int gridRowEnd, boolean gridPlacement,
                double acrossStart, double acrossEnd,
                double alongStart, double alongEnd, AddonBlueprint addon, int declaration,
                boolean poseAligned, boolean propagate) {
            this.tileIndex = tileIndex; this.tileEndIndex = tileEndIndex;
            this.gridRowStart = gridRowStart; this.gridRowEnd = gridRowEnd;
            this.gridPlacement = gridPlacement;
            this.acrossStart = acrossStart;
            this.acrossEnd = acrossEnd; this.alongStart = alongStart;
            this.alongEnd = alongEnd; this.addon = addon; this.declaration = declaration;
            this.poseAligned = poseAligned;
            this.propagate = propagate;
        }
        static PlacementDraft poseAligned(int tileIndex,double lateralFraction,
                double halfAcross,double halfAlong,AddonBlueprint addon,int declaration,
                boolean propagate) {
            return new PlacementDraft(tileIndex,tileIndex,-1,-1,false,
                    lateralFraction,halfAcross,0.0,halfAlong,
                    addon,declaration,true,propagate);
        }
        static PlacementDraft grid(
                int tileIndex, int tileEndIndex, int gridRowStart, int gridRowEnd,
                double acrossStart, double acrossEnd,
                double alongStart, double alongEnd,
                AddonBlueprint addon, int declaration, boolean ignored,
                boolean propagate) {
            return new PlacementDraft(
                    tileIndex, tileEndIndex, gridRowStart, gridRowEnd, true,
                    acrossStart, acrossEnd, alongStart, alongEnd,
                    addon, declaration, false, propagate);
        }
    }

    private static final class Geometry {
        final long id;
        final TileDraft draft;
        final Vec3 nearLeft, nearRight, farLeft, farRight;
        final Vec3 poseCenter, direction, right, normal, horizontalForward;
        final boolean connected;
        final TerrainVertexAppearance nearLeftAppearance, nearRightAppearance,
                farLeftAppearance, farRightAppearance;
        Geometry(long id, TileDraft draft, Vec3 nearLeft, Vec3 nearRight,
                Vec3 farLeft, Vec3 farRight, boolean connected,
                Vec3 poseCenter, Vec3 direction, Vec3 right, Vec3 normal,
                Vec3 horizontalForward,
                TerrainVertexAppearance nearLeftAppearance,
                TerrainVertexAppearance nearRightAppearance,
                TerrainVertexAppearance farLeftAppearance,
                TerrainVertexAppearance farRightAppearance) {
            this.id=id; this.draft=draft; this.nearLeft=nearLeft; this.nearRight=nearRight;
            this.farLeft=farLeft; this.farRight=farRight; this.connected=connected;
            this.poseCenter=poseCenter; this.direction=direction; this.right=right;
            this.normal=normal; this.horizontalForward=horizontalForward;
            this.nearLeftAppearance=nearLeftAppearance; this.nearRightAppearance=nearRightAppearance;
            this.farLeftAppearance=farLeftAppearance; this.farRightAppearance=farRightAppearance;
        }
    }

    /** Mutable canonical geometry cursor shared by capture-time row derivation and materialization. */
    private static final class GeometryCursor {
        final TrackProfile profile;
        Vec3 cursor;
        double yaw;
        double pitch;
        Vec3 previousLeft;
        Vec3 previousRight;
        TerrainVertexAppearance previousLeftAppearance;
        TerrainVertexAppearance previousRightAppearance;
        boolean previousSolid;

        GeometryCursor(
                TrackProfile profile, Vec3 cursor, double yaw, double pitch,
                Vec3 previousLeft, Vec3 previousRight,
                TerrainVertexAppearance previousLeftAppearance,
                TerrainVertexAppearance previousRightAppearance,
                boolean previousSolid) {
            this.profile = profile;
            this.cursor = cursor;
            this.yaw = yaw;
            this.pitch = pitch;
            this.previousLeft = previousLeft;
            this.previousRight = previousRight;
            this.previousLeftAppearance = previousLeftAppearance;
            this.previousRightAppearance = previousRightAppearance;
            this.previousSolid = previousSolid;
        }

        GeometryCursor copy() {
            return new GeometryCursor(profile, cursor, yaw, pitch,
                    previousLeft, previousRight,
                    previousLeftAppearance, previousRightAppearance,
                    previousSolid);
        }

        Geometry emit(long id, TileDraft tile) {
            yaw = tile.absoluteYaw ? tile.yaw : yaw + tile.yaw;
            pitch = tile.absolutePitch ? tile.pitch : pitch + tile.pitch;
            boolean lifted = Math.abs(tile.lift) > 0.0;
            if (lifted) cursor = cursor.add(Vec3.UP.multiply(tile.lift));
            double cosPitch = Math.cos(pitch);
            Vec3 direction = new Vec3(Math.sin(yaw) * cosPitch, Math.sin(pitch),
                    -Math.cos(yaw) * cosPitch).normalized();
            Vec3 right = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
            Vec3 nearLeft = cursor.subtract(right.multiply(profile.width * 0.5));
            Vec3 nearRight = cursor.add(right.multiply(profile.width * 0.5));
            Vec3 next = cursor.add(direction.multiply(profile.tileLength));
            Vec3 farLeft = next.subtract(right.multiply(profile.width * 0.5));
            Vec3 farRight = next.add(right.multiply(profile.width * 0.5));
            TerrainVertexAppearance farLA =
                    new TerrainVertexAppearance(tile.alphaLeft, tile.brightness);
            TerrainVertexAppearance farRA =
                    new TerrainVertexAppearance(tile.alphaRight, tile.brightness);
            TerrainVertexAppearance nearLA = farLA;
            TerrainVertexAppearance nearRA = farRA;
            boolean connected = tile.solid && previousSolid && !lifted;
            if (connected) {
                nearLeft = previousLeft;
                nearRight = previousRight;
                nearLA = previousLeftAppearance;
                nearRA = previousRightAppearance;
            }
            Vec3 normal = right.cross(direction).normalized();
            if (normal.y < 0.0) normal = normal.multiply(-1.0);
            Vec3 horizontalForward = new Vec3(Math.sin(yaw), 0.0, -Math.cos(yaw));
            Vec3 poseCenter = cursor.add(direction.multiply(profile.tileLength * 0.5));
            Geometry result = new Geometry(id, tile,
                    nearLeft, nearRight, farLeft, farRight, connected,
                    poseCenter, direction, right, normal, horizontalForward,
                    nearLA, nearRA, farLA, farRA);
            cursor = next;
            previousLeft = farLeft;
            previousRight = farRight;
            previousLeftAppearance = farLA;
            previousRightAppearance = farRA;
            previousSolid = tile.solid;
            return result;
        }
    }

    /** The unfinished physical grid cell shared with the next connected structure. */
    private static final class GridCarry {
        final boolean active;
        final Vec3 nearLeft;
        final Vec3 nearRight;
        final double distance;

        private GridCarry(boolean active, Vec3 nearLeft, Vec3 nearRight, double distance) {
            this.active = active;
            this.nearLeft = nearLeft;
            this.nearRight = nearRight;
            this.distance = distance;
        }

        static GridCarry empty() {
            return new GridCarry(false, null, null, 0.0);
        }

        static GridCarry active(Vec3 nearLeft, Vec3 nearRight, double distance) {
            return new GridCarry(true, nearLeft, nearRight, distance);
        }
    }

    /** A completed row cell derived exclusively from the final canonical segment edges. */
    private static final class PhysicalGridRow {
        final int ownerTileIndex;
        final Vec3 nearLeft;
        final Vec3 nearRight;
        final Vec3 farLeft;
        final Vec3 farRight;

        PhysicalGridRow(
                int ownerTileIndex, Vec3 nearLeft, Vec3 nearRight,
                Vec3 farLeft, Vec3 farRight) {
            this.ownerTileIndex = ownerTileIndex;
            this.nearLeft = nearLeft;
            this.nearRight = nearRight;
            this.farLeft = farLeft;
            this.farRight = farRight;
        }
    }

    /** Distance sampler preserving the legacy longer-edge spacing and shorter-edge ratio. */
    private static final class PhysicalGridBuilder {
        final double spacing;
        final int maximumRows;
        final ArrayList<PhysicalGridRow> rows = new ArrayList<PhysicalGridRow>();
        GridCarry carry;

        PhysicalGridBuilder(double spacing, GridCarry initialCarry, int maximumRows) {
            this.spacing = spacing;
            this.carry = initialCarry == null ? GridCarry.empty() : initialCarry;
            this.maximumRows = maximumRows;
        }

        int size() { return rows.size(); }

        GridCarry carry() { return carry; }

        List<PhysicalGridRow> copyRows(int first, int count) {
            if (first < 0 || count < 0 || first + count > rows.size()) {
                throw new IllegalArgumentException(
                        "Physical grid row slice is outside derived rows: first=" + first
                                + ", count=" + count + ", derived=" + rows.size());
            }
            return new ArrayList<PhysicalGridRow>(rows.subList(first, first + count));
        }

        void append(Geometry geometry) {
            if (!geometry.draft.solid) {
                carry = GridCarry.empty();
                return;
            }
            if (!geometry.connected) {
                carry = GridCarry.active(geometry.nearLeft, geometry.nearRight, 0.0);
            } else if (!carry.active) {
                // The terrain may be connected to a pre-existing segment whose row state was not
                // supplied (for example the standalone materializer). Begin a fresh local cell.
                carry = GridCarry.active(geometry.nearLeft, geometry.nearRight, 0.0);
            }

            Vec3 leftVector = geometry.farLeft.subtract(geometry.nearLeft);
            Vec3 rightVector = geometry.farRight.subtract(geometry.nearRight);
            double leftLength = leftVector.length();
            double rightLength = rightVector.length();
            double longerLength = Math.max(leftLength, rightLength);
            if (!(longerLength > 0.0) || !Double.isFinite(longerLength)) {
                carry = GridCarry.empty();
                return;
            }
            boolean leftLonger = leftLength > rightLength;
            double distanceLonger = spacing - carry.distance;
            double boundaryTolerance = Math.max(
                    1.0e-12,
                    8.0 * Math.ulp(Math.max(longerLength, Math.abs(distanceLonger))));
            // A pathological profile must fail before any large allocation or long loop.
            double estimated = Math.ceil(Math.max(0.0, longerLength - distanceLonger) / spacing);
            if (!Double.isFinite(estimated) || estimated > maximumRows - rows.size()) {
                throw new IllegalArgumentException(
                        "Structure exceeds the physical grid row limit of " + maximumRows);
            }
            while (distanceLonger <= longerLength + boundaryTolerance) {
                double resolvedDistance = Math.abs(distanceLonger - longerLength)
                        <= boundaryTolerance ? longerLength : distanceLonger;
                if (resolvedDistance > longerLength) break;
                double leftScale = leftLonger
                        ? resolvedDistance / leftLength
                        : resolvedDistance / longerLength;
                double rightScale = leftLonger
                        ? resolvedDistance / longerLength
                        : resolvedDistance / rightLength;
                Vec3 farLeft = geometry.nearLeft.add(leftVector.multiply(leftScale));
                Vec3 farRight = geometry.nearRight.add(rightVector.multiply(rightScale));
                if (rows.size() >= maximumRows) {
                    throw new IllegalArgumentException(
                            "Structure exceeds the physical grid row limit of " + maximumRows);
                }
                rows.add(new PhysicalGridRow(
                        (int) geometry.id, carry.nearLeft, carry.nearRight,
                        farLeft, farRight));
                carry = GridCarry.active(farLeft, farRight, 0.0);
                if (resolvedDistance == longerLength) {
                    return;
                }
                distanceLonger += spacing;
            }
            double leftover = longerLength - (distanceLonger - spacing);
            if (leftover < 0.0 && leftover > -1.0e-12) leftover = 0.0;
            carry = GridCarry.active(carry.nearLeft, carry.nearRight, leftover);
        }
    }

    private static final class StructureBuild {
        final TrackProfile profile;
        Vec3 cursor;
        double yaw;
        double pitch;
        final long firstSegment;
        long nextAddon;
        final PlacementIds ids;
        final List<TileDraft> tiles;
        final List<PlacementDraft> placements;
        final Vec3 initialFarLeft, initialFarRight;
        final TerrainVertexAppearance initialFarLeftAppearance, initialFarRightAppearance;
        final boolean initialPreviousSolid;
        final ArrayList<Geometry> geometry = new ArrayList<Geometry>();
        final Map<Long, List<Addon>> byOwner = new HashMap<Long, List<Addon>>();
        final Map<String, Long> addonSources = new LinkedHashMap<String, Long>();
        final Map<String, Long> segmentSources = new LinkedHashMap<String, Long>();
        final ArrayList<TerrainSegment> segments = new ArrayList<TerrainSegment>();
        final GeometryCursor geometryCursor;
        final PhysicalGridBuilder physicalGrid;
        int geometryIndex;
        int placementIndex;
        int segmentIndex;
        BuildResult result;

        StructureBuild(TrackProfile profile, Vec3 cursor, double yaw, double pitch,
                long firstSegment,
                long nextAddon, long nextPair, List<TileDraft> tiles,
                List<PlacementDraft> placements, Vec3 initialFarLeft, Vec3 initialFarRight,
                TerrainVertexAppearance initialFarLeftAppearance,
                TerrainVertexAppearance initialFarRightAppearance,
                boolean initialPreviousSolid, GridCarry initialGridCarry) {
            this.profile=profile; this.cursor=cursor; this.yaw=yaw; this.pitch=pitch;
            this.firstSegment=firstSegment;
            this.nextAddon=nextAddon; this.ids=new PlacementIds(nextPair);
            this.tiles=tiles;
            this.placements=new ArrayList<PlacementDraft>(placements);
            Collections.sort(this.placements, new Comparator<PlacementDraft>() {
                @Override public int compare(PlacementDraft a, PlacementDraft b) {
                    int owner = Integer.compare(a.tileIndex, b.tileIndex);
                    return owner != 0 ? owner : Integer.compare(a.declaration, b.declaration);
                }
            });
            this.initialFarLeft=initialFarLeft; this.initialFarRight=initialFarRight;
            this.initialFarLeftAppearance=initialFarLeftAppearance;
            this.initialFarRightAppearance=initialFarRightAppearance;
            this.initialPreviousSolid=initialPreviousSolid;
            this.geometryCursor = new GeometryCursor(
                    profile, cursor, yaw, pitch,
                    initialFarLeft, initialFarRight,
                    initialFarLeftAppearance, initialFarRightAppearance,
                    initialPreviousSolid);
            this.physicalGrid = new PhysicalGridBuilder(
                    profile.rowSpacing, initialGridCarry, MAX_PHYSICAL_GRID_ROWS);
        }

        int commandCount() {
            // Geometry, addon placement, immutable segment freezing, and one final seal.
            long count = (long) tiles.size() * 2L + placements.size() + 1L;
            if (count > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Structure command count is too large");
            }
            return (int) count;
        }

        void advanceOne() {
            if (result != null) {
                throw new IllegalStateException("Structure build is already complete");
            }
            if (geometryIndex < tiles.size()) {
                buildNextGeometry();
            } else if (placementIndex < placements.size()) {
                placeNextAddon();
            } else if (segmentIndex < geometry.size()) {
                freezeNextSegment();
            } else {
                finish();
            }
        }

        BuildResult build() {
            while (result == null) {
                advanceOne();
            }
            return result;
        }

        private void buildNextGeometry() {
            int index = geometryIndex++;
            Geometry emitted = geometryCursor.emit(index, tiles.get(index));
            geometry.add(new Geometry(firstSegment + index, emitted.draft,
                    emitted.nearLeft, emitted.nearRight,
                    emitted.farLeft, emitted.farRight, emitted.connected,
                    emitted.poseCenter, emitted.direction, emitted.right,
                    emitted.normal, emitted.horizontalForward,
                    emitted.nearLeftAppearance, emitted.nearRightAppearance,
                    emitted.farLeftAppearance, emitted.farRightAppearance));
            physicalGrid.append(emitted);
            cursor = geometryCursor.cursor;
            yaw = geometryCursor.yaw;
            pitch = geometryCursor.pitch;
        }

        private void placeNextAddon() {
            PlacementDraft placement = placements.get(placementIndex++);
            if (placement.tileIndex < 0 || placement.tileIndex >= geometry.size())
                throw new IllegalArgumentException("Addon placement has no owner segment");
            if (placement.tileEndIndex < placement.tileIndex
                    || placement.tileEndIndex >= geometry.size()) {
                throw new IllegalArgumentException("Addon placement extends past terrain");
            }
            Geometry owner = geometry.get(placement.tileIndex);
            if (!owner.draft.solid)
                throw new IllegalArgumentException("Cannot place addon on gap");
            for (int tile = placement.tileIndex + 1;
                    tile <= placement.tileEndIndex; tile++) {
                if (!geometry.get(tile).draft.solid) {
                    throw new IllegalArgumentException("Cannot span addon across a gap");
                }
            }
            PlacementFootprint resolved = footprint(
                    owner, geometry.get(placement.tileEndIndex), placement);
            long addonId = nextAddon++;
            Addon addon = placement.addon.place(addonId, owner.id, resolved, ids);
            List<Addon> list = byOwner.get(owner.id);
            if (list == null) {
                list = new ArrayList<Addon>();
                byOwner.put(owner.id, list);
            }
            list.add(addon);
            if (addonSources.put(placement.addon.sourceId, addonId) != null) {
                throw new IllegalArgumentException(
                        "Duplicate addon source ID " + placement.addon.sourceId);
            }
        }

        private void freezeNextSegment() {
            Geometry g = geometry.get(segmentIndex++);
            if (segmentSources.put(g.draft.sourceId, g.id) != null) {
                throw new IllegalArgumentException(
                        "Duplicate tile source ID " + g.draft.sourceId);
            }
            List<Addon> addons = byOwner.get(g.id);
            segments.add(new TerrainSegment(g.id, g.nearLeft, g.nearRight,
                    g.farLeft, g.farRight, g.draft.solid, g.connected,
                    g.draft.surface, g.nearLeftAppearance, g.nearRightAppearance,
                    g.farLeftAppearance, g.farRightAppearance,
                    addons == null ? Collections.<Addon>emptyList() : addons));
        }

        private void finish() {
            MaterializedStructure materialized = new MaterializedStructure(
                    segments, segmentSources, addonSources, physicalGrid.size());
            Geometry last = geometry.isEmpty() ? null : geometry.get(geometry.size() - 1);
            result = new BuildResult(materialized, cursor, yaw, pitch,
                    firstSegment + tiles.size(), nextAddon, ids.nextPair,
                    last == null ? initialFarLeft : last.farLeft,
                    last == null ? initialFarRight : last.farRight,
                    last == null ? initialFarLeftAppearance : last.farLeftAppearance,
                    last == null ? initialFarRightAppearance : last.farRightAppearance,
                    last == null ? initialPreviousSolid : last.draft.solid,
                    physicalGrid.carry());
        }
        PlacementFootprint footprint(
                Geometry startGeometry, Geometry endGeometry, PlacementDraft p) {
            if(p.poseAligned) {
                Vec3 center=startGeometry.poseCenter.add(
                        startGeometry.right.multiply(p.acrossStart*profile.width));
                Vec3 across=startGeometry.right.multiply(p.acrossEnd);
                Vec3 along=startGeometry.direction.multiply(p.alongEnd);
                return new PlacementFootprint(center.subtract(across).subtract(along),
                        center.add(across).subtract(along),center.subtract(across).add(along),
                        center.add(across).add(along),center,startGeometry.normal,
                        startGeometry.direction, startGeometry.horizontalForward,p.declaration);
            }
            if (p.gridPlacement) {
                int firstRow = p.gridRowStart;
                int lastRow = p.gridRowEnd;
                if (firstRow < 0 || lastRow < firstRow
                        || lastRow >= physicalGrid.rows.size()) {
                    throw new IllegalArgumentException(
                            "Grid placement rows are unavailable during materialization: ["
                                    + p.gridRowStart + ", " + p.gridRowEnd
                                    + "], derived=" + physicalGrid.rows.size());
                }
                PhysicalGridRow startRow = physicalGrid.rows.get(firstRow);
                PhysicalGridRow endRow = physicalGrid.rows.get(lastRow);
                Vec3 nl = lerpEdge(startRow.nearLeft, startRow.nearRight, p.acrossStart);
                Vec3 nr = lerpEdge(startRow.nearLeft, startRow.nearRight, p.acrossEnd);
                Vec3 fl = lerpEdge(endRow.nearLeft, endRow.nearRight, p.acrossStart);
                Vec3 fr = lerpEdge(endRow.nearLeft, endRow.nearRight, p.acrossEnd);
                // GRID rows are already exact physical cells; along fractions interpolate within
                // the first/last selected row boundaries.
                Vec3 startFarLeft = lerpEdge(
                        startRow.farLeft, startRow.farRight, p.acrossStart);
                Vec3 startFarRight = lerpEdge(
                        startRow.farLeft, startRow.farRight, p.acrossEnd);
                Vec3 endFarLeft = lerpEdge(endRow.farLeft, endRow.farRight, p.acrossStart);
                Vec3 endFarRight = lerpEdge(endRow.farLeft, endRow.farRight, p.acrossEnd);
                nl = Vec3.lerp(nl, startFarLeft, p.alongStart);
                nr = Vec3.lerp(nr, startFarRight, p.alongStart);
                fl = Vec3.lerp(fl, endFarLeft, p.alongEnd);
                fr = Vec3.lerp(fr, endFarRight, p.alongEnd);
                Vec3 center=nl.add(nr).add(fl).add(fr).multiply(0.25);
                Vec3 forward=fl.add(fr).subtract(nl).subtract(nr).normalized();
                Vec3 normal=nr.subtract(nl).cross(fl.subtract(nl)).normalized();
                if (normal.y<0.0) normal=normal.multiply(-1.0);
                return new PlacementFootprint(nl,nr,fl,fr,center,normal,forward,
                        startGeometry.horizontalForward,p.declaration);
            }
            Vec3 nl=bilinear(startGeometry,p.acrossStart,p.alongStart);
            Vec3 nr=bilinear(startGeometry,p.acrossEnd,p.alongStart);
            Vec3 fl=bilinear(endGeometry,p.acrossStart,p.alongEnd);
            Vec3 fr=bilinear(endGeometry,p.acrossEnd,p.alongEnd);
            Vec3 center=nl.add(nr).add(fl).add(fr).multiply(0.25);
            Vec3 forward=fl.add(fr).subtract(nl).subtract(nr).normalized();
            Vec3 normal=nr.subtract(nl).cross(fl.subtract(nl)).normalized();
            if (normal.y<0.0) normal=normal.multiply(-1.0);
            return new PlacementFootprint(nl,nr,fl,fr,center,normal,forward,
                    startGeometry.horizontalForward,p.declaration);
        }
        Vec3 bilinear(Geometry g,double across,double along) {
            return Vec3.lerp(Vec3.lerp(g.nearLeft,g.nearRight,across),
                    Vec3.lerp(g.farLeft,g.farRight,across),along);
        }
        Vec3 lerpEdge(Vec3 left, Vec3 right, double across) {
            return Vec3.lerp(left, right, across);
        }
    }

    private static final class BuildResult {
        final MaterializedStructure materialized; final Vec3 cursor; final double yaw,pitch;
        final long nextSegmentId,nextAddonId,nextPairId;
        final Vec3 farLeft,farRight;
        final TerrainVertexAppearance farLeftAppearance,farRightAppearance;
        final boolean previousSolid;
        final GridCarry gridCarry;
        BuildResult(MaterializedStructure materialized,Vec3 cursor,double yaw,double pitch,
                long nextSegmentId,long nextAddonId,long nextPairId,
                Vec3 farLeft,Vec3 farRight,TerrainVertexAppearance farLeftAppearance,
                TerrainVertexAppearance farRightAppearance,boolean previousSolid,
                GridCarry gridCarry) {
            this.materialized=materialized; this.cursor=cursor; this.yaw=yaw;
            this.pitch=pitch;
            this.nextSegmentId=nextSegmentId; this.nextAddonId=nextAddonId; this.nextPairId=nextPairId;
            this.farLeft=farLeft;this.farRight=farRight;
            this.farLeftAppearance=farLeftAppearance;this.farRightAppearance=farRightAppearance;
            this.previousSolid=previousSolid;
            this.gridCarry=gridCarry;
        }
    }

    private static final class PendingBuild {
        final List<TileDraft> tiles;
        final List<PlacementDraft> placements;
        final QueuedStructure ticket;
        StructureBuild build;
        int remainingCommands;

        PendingBuild(
                List<TileDraft> tiles, List<PlacementDraft> placements,
                QueuedStructure ticket) {
            this.tiles = tiles;
            this.placements = placements;
            this.ticket = ticket;
        }

        void startIfNeeded(StructureBuild value) {
            if (build != null) {
                return;
            }
            build = value;
            remainingCommands = value.commandCount();
        }

        int advance(int limit) {
            int consumed = Math.min(limit, remainingCommands);
            for (int i = 0; i < consumed; i++) {
                build.advanceOne();
            }
            remainingCommands -= consumed;
            return consumed;
        }

        boolean ready() {
            return remainingCommands == 0L;
        }

        BuildResult result() {
            if (!ready() || build == null || build.result == null) {
                throw new IllegalStateException("Structure build is not complete");
            }
            return build.result;
        }
    }

    private static final class PendingPublication {
        final List<TerrainSegment> segments; int index;
        PendingPublication(List<TerrainSegment> segments){this.segments=segments;}
        void copyNext(List<TerrainSegment> target,int limit){
            int count=Math.min(limit,segments.size()-index);
            int end=index+count;
            target.addAll(segments.subList(index,end));
            index=end;
        }
        boolean complete(){return index>=segments.size();}
        int remaining(){return segments.size()-index;}
    }

    private static long mix64(long value) {
        long mixed=value; mixed^=mixed>>>30; mixed*=0xbf58476d1ce4e5b9L;
        mixed^=mixed>>>27; mixed*=0x94d049bb133111ebL; return mixed^(mixed>>>31);
    }
}
