package com.example.game3d.core.terrain;

import com.example.game3d.core.math.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CPU-only streaming terrain producer used by both desktop tests and Android gameplay.
 *
 * <p>Recipes author immutable segments ahead of the committed frontier. {@link #generateChunks}
 * publishes bounded atomic commits, so consumers never see half-authored segment records.</p>
 */
public final class StreamingTerrainGenerator implements TerrainOutput {
    public static final int DEFAULT_INTERACTION_WINDOW_AHEAD = 64;
    private static final int RETAIN_BEHIND_SEGMENTS = 50;

    private final double width;
    private final double segmentLength;
    private final int interactionWindowAhead;
    private final TerrainState state = new TerrainState();
    private final ArrayDeque<AuthoringCommand> authoringCommands =
            new ArrayDeque<AuthoringCommand>();
    private final ArrayList<TerrainCommit> pendingCommits =
            new ArrayList<TerrainCommit>();

    private Vec3 cursor;
    private double yawRadians;
    private double pitchRadians;
    private Vec3 previousFarLeft;
    private Vec3 previousFarRight;
    private TerrainVertexAppearance previousFarLeftAppearance;
    private TerrainVertexAppearance previousFarRightAppearance;
    private long nextSegmentId;
    private long nextFeatureId = 1L;
    private long nextPortalPairId = 1L;
    private boolean previousAuthoredSolid;
    private boolean closed;

    private interface AuthoringCommand {
        TerrainSegment emitNext(StreamingTerrainGenerator generator);

        boolean isComplete();
    }

    private enum StraightKind {
        PLAIN,
        INTRO_HAZARDS,
        OBSTACLE
    }

    private static final class StraightCommand implements AuthoringCommand {
        private final int count;
        private final SurfaceProperties surface;
        private final StraightKind kind;
        private final boolean portal;
        private final long portalPairId;
        private int index;

        StraightCommand(
                int count,
                SurfaceProperties surface,
                StraightKind kind,
                boolean portal,
                long portalPairId) {
            this.count = count;
            this.surface = surface;
            this.kind = kind;
            this.portal = portal;
            this.portalPairId = portalPairId;
        }

        @Override
        public TerrainSegment emitNext(StreamingTerrainGenerator generator) {
            List<TerrainFeatureSpec> features =
                    Collections.<TerrainFeatureSpec>emptyList();
            boolean introSpike = kind == StraightKind.INTRO_HAZARDS
                    && (index == 20 || index == 40 || index == 60);
            boolean obstacleFeature = kind == StraightKind.OBSTACLE
                    && (index == count / 4
                    || index == count / 2
                    || index == (count * 3) / 4
                    || index == count / 3
                    || index == (count * 2) / 3
                    || (portal && (index == 3 || index == count - 4)));
            if (introSpike || obstacleFeature) {
                ArrayList<TerrainFeatureSpec> authoredFeatures =
                        new ArrayList<TerrainFeatureSpec>();
                if (introSpike) {
                    generator.addSpike(authoredFeatures, index, count);
                } else if (kind == StraightKind.OBSTACLE) {
                    if (index == count / 4 || index == count / 2
                            || index == (count * 3) / 4) {
                        generator.addCollectible(authoredFeatures);
                    }
                    if (index == count / 3
                            || index == (count * 2) / 3) {
                        generator.addSpike(authoredFeatures, index, count);
                    }
                    if (portal && index == 3) {
                        generator.addPortal(
                                authoredFeatures,
                                portalPairId,
                                TerrainFeatureSpec.Portal.Role.EXIT);
                    } else if (portal && index == count - 4) {
                        generator.addPortal(
                                authoredFeatures,
                                portalPairId,
                                TerrainFeatureSpec.Portal.Role.ENTRANCE);
                    }
                }
                features = authoredFeatures;
            }
            index++;
            return generator.createSegment(
                    true,
                    surface,
                    1f,
                    brightness(surface),
                    features);
        }

        @Override
        public boolean isComplete() {
            return index >= count;
        }
    }

    private static final class CurveCommand implements AuthoringCommand {
        private final int curveSegments;
        private final double yawStep;
        private final double pitchDelta;
        private final double pitchStep;
        private final int pitchFadeSegments;
        private int index;

        CurveCommand(
                int curveSegments,
                double yawDelta,
                double pitchDelta,
                int pitchFadeSegments) {
            this.curveSegments = curveSegments;
            this.yawStep = yawDelta / curveSegments;
            this.pitchDelta = pitchDelta;
            this.pitchStep = pitchDelta / curveSegments;
            this.pitchFadeSegments =
                    Math.abs(pitchDelta) > 1.0e-12
                            ? pitchFadeSegments : 0;
        }

        @Override
        public TerrainSegment emitNext(StreamingTerrainGenerator generator) {
            if (index < curveSegments) {
                generator.yawRadians += yawStep;
                generator.pitchRadians += pitchStep;
                List<TerrainFeatureSpec> features =
                        Collections.<TerrainFeatureSpec>emptyList();
                if (index == curveSegments / 3
                        || index == (curveSegments * 2) / 3) {
                    ArrayList<TerrainFeatureSpec> spike =
                            new ArrayList<TerrainFeatureSpec>(1);
                    generator.addSpike(spike, index, curveSegments);
                    features = spike;
                }
                TerrainSegment result = generator.createSegment(
                        true, SurfaceProperties.NORMAL, 1f, 1f, features);
                index++;
                if (index == curveSegments
                        && pitchFadeSegments == 0
                        && Math.abs(pitchDelta) > 1.0e-12) {
                    generator.pitchRadians -= pitchDelta;
                }
                return result;
            }
            generator.pitchRadians -= pitchDelta / pitchFadeSegments;
            index++;
            return generator.createSegment(
                    true,
                    SurfaceProperties.NORMAL,
                    1f,
                    1f,
                    Collections.<TerrainFeatureSpec>emptyList());
        }

        @Override
        public boolean isComplete() {
            return index >= curveSegments + pitchFadeSegments;
        }
    }

    private static final class StairsCommand implements AuthoringCommand {
        private final int segmentsPerStair;
        private final int stairCount;
        private final int gapSegments;
        private final double yawStep;
        private final double stepHeight;
        private int stair;
        private int solidIndex;
        private int gapIndex;
        private boolean initialized;

        StairsCommand(
                int segmentsPerStair,
                int stairCount,
                int gapSegments,
                double yawDelta,
                double stepHeight) {
            this.segmentsPerStair = segmentsPerStair;
            this.stairCount = stairCount;
            this.gapSegments = gapSegments;
            this.yawStep = yawDelta / (segmentsPerStair * stairCount);
            this.stepHeight = stepHeight;
        }

        @Override
        public TerrainSegment emitNext(StreamingTerrainGenerator generator) {
            if (!initialized) {
                generator.cursor =
                        generator.cursor.add(Vec3.UP.multiply(stepHeight));
                generator.previousAuthoredSolid = false;
                initialized = true;
            }
            if (solidIndex < segmentsPerStair) {
                generator.yawRadians += yawStep;
                List<TerrainFeatureSpec> features =
                        Collections.<TerrainFeatureSpec>emptyList();
                if (solidIndex == segmentsPerStair / 2) {
                    ArrayList<TerrainFeatureSpec> spike =
                            new ArrayList<TerrainFeatureSpec>(1);
                    generator.addSpike(
                            spike,
                            stair + solidIndex,
                            segmentsPerStair * stairCount);
                    features = spike;
                }
                TerrainSegment result = generator.createSegment(
                        true,
                        SurfaceProperties.NORMAL,
                        0.5f,
                        1f,
                        features);
                solidIndex++;
                if (solidIndex == segmentsPerStair
                        && (stair == stairCount - 1 || gapSegments == 0)) {
                    finishStair(generator);
                }
                return result;
            }
            TerrainSegment result = generator.createSegment(
                    false,
                    SurfaceProperties.NORMAL,
                    0.5f,
                    1f,
                    Collections.<TerrainFeatureSpec>emptyList());
            gapIndex++;
            if (gapIndex >= gapSegments) {
                finishStair(generator);
            }
            return result;
        }

        @Override
        public boolean isComplete() {
            return stair >= stairCount;
        }

        private void finishStair(StreamingTerrainGenerator generator) {
            generator.cursor =
                    generator.cursor.add(Vec3.UP.multiply(stepHeight));
            generator.previousAuthoredSolid = false;
            stair++;
            solidIndex = 0;
            gapIndex = 0;
        }
    }

    private static final class BoostRampCommand
            implements AuthoringCommand {
        private final int rampSegments;
        private final int gapSegments;
        private final int landingSegments;
        private final double pitchStep;
        private int index;

        BoostRampCommand(
                int rampSegments,
                int gapSegments,
                int landingSegments,
                double launchPitch) {
            this.rampSegments = rampSegments;
            this.gapSegments = gapSegments;
            this.landingSegments = landingSegments;
            this.pitchStep = launchPitch / rampSegments;
        }

        @Override
        public TerrainSegment emitNext(StreamingTerrainGenerator generator) {
            if (index < rampSegments) {
                generator.pitchRadians += pitchStep;
                SurfaceProperties surface =
                        index == rampSegments - 1
                                ? SurfaceProperties.BOOST_RAMP_LAUNCH
                                : SurfaceProperties.BOOST_RAMP;
                float t = rampSegments <= 1
                        ? 1f
                        : (float) index / (float) (rampSegments - 1);
                TerrainSegment result = generator.createSegment(
                        true,
                        surface,
                        1f,
                        1f + 0.35f * t,
                        Collections.<TerrainFeatureSpec>emptyList());
                index++;
                if (index == rampSegments) {
                    generator.pitchRadians = 0.0;
                }
                return result;
            }
            if (index < rampSegments + gapSegments) {
                index++;
                return generator.createSegment(
                        false,
                        SurfaceProperties.NORMAL,
                        1f,
                        1f,
                        Collections.<TerrainFeatureSpec>emptyList());
            }
            index++;
            return generator.createSegment(
                    true,
                    SurfaceProperties.NORMAL,
                    1f,
                    1f,
                    Collections.<TerrainFeatureSpec>emptyList());
        }

        @Override
        public boolean isComplete() {
            return index
                    >= rampSegments + gapSegments + landingSegments;
        }
    }

    public StreamingTerrainGenerator(
            double width, double segmentLength, Vec3 startCenter) {
        this(width, segmentLength, startCenter,
                DEFAULT_INTERACTION_WINDOW_AHEAD);
    }

    public StreamingTerrainGenerator(
            double width,
            double segmentLength,
            Vec3 startCenter,
            int interactionWindowAhead) {
        if (!(width > 0.0) || !(segmentLength > 0.0)
                || startCenter == null || interactionWindowAhead < 0) {
            throw new IllegalArgumentException(
                    "Invalid streaming terrain dimensions");
        }
        this.width = width;
        this.segmentLength = segmentLength;
        this.cursor = startCenter;
        this.interactionWindowAhead = interactionWindowAhead;
    }

    /** Safe opening followed by sparse, avoidable hazards. */
    public synchronized void enqueueIntroSegments() {
        requireOpen();
        enqueueStraight(80, SurfaceProperties.NORMAL);
        authoringCommands.addLast(new StraightCommand(
                80,
                SurfaceProperties.NORMAL,
                StraightKind.INTRO_HAZARDS,
                false,
                -1L));
    }

    /**
     * Enqueues one deterministic rich gameplay level.
     *
     * <p>The index selects the template and all feature placement; generation is reproducible
     * without Android globals or renderer state.</p>
     */
    public synchronized void enqueueGameplayLevel(int levelIndex) {
        requireOpen();
        int safeIndex = Math.max(0, levelIndex);
        long mixed = mix64(safeIndex + 0x9e3779b97f4a7c15L);
        int template = (int) Math.floorMod(mixed, 6L);
        boolean portal = safeIndex >= 4 && ((mixed >>> 8) & 3L) == 0L;
        boolean portalFirst = ((mixed >>> 12) & 1L) == 0L;
        switch (template) {
            case 0:
                enqueueStraight(20, SurfaceProperties.NORMAL);
                enqueueStairs(30, 5, 2, Math.PI / 8.0, -0.9);
                enqueueStraight(12, SurfaceProperties.NORMAL);
                enqueueCurve(12, -Math.PI / 8.0, 0.0, 5);
                enqueueObstacleStraight(34, portal && portalFirst);
                enqueueCurve(42, 0.0, Math.PI / 14.0, 5);
                enqueueStraight(16, SurfaceProperties.NORMAL);
                enqueueObstacleStraight(40, portal && !portalFirst);
                enqueueStraight(34, SurfaceProperties.NORMAL);
                break;
            case 1:
                enqueueStraight(50, SurfaceProperties.NORMAL);
                enqueueStairs(42, 7, 2, Math.PI / 9.0, -0.8);
                enqueueStraight(12, SurfaceProperties.NORMAL);
                enqueueCurve(14, -Math.PI / 9.0, 0.0, 5);
                enqueueStraight(50, SurfaceProperties.NORMAL);
                break;
            case 2:
                enqueueStraight(24, SurfaceProperties.NORMAL);
                enqueueObstacleStraight(28, portal);
                enqueueBoostRamp(8, 0, 40, Math.PI / 15.0);
                enqueueStraight(40, SurfaceProperties.NORMAL);
                break;
            case 3:
                enqueueStraight(20, SurfaceProperties.NORMAL);
                enqueueCurve(18, Math.PI / 16.0, 0.0, 5);
                enqueueStraight(30, SurfaceProperties.NORMAL);
                enqueueBoostRamp(7, 0, 18, Math.PI / 7.0);
                enqueueStraight(22, SurfaceProperties.NORMAL);
                enqueueObstacleStraight(30, portal);
                enqueueStraight(30, SurfaceProperties.NORMAL);
                break;
            case 4:
                enqueueStraight(50, SurfaceProperties.NORMAL);
                enqueueStairs(35, 5, 2, Math.PI / 10.0, -0.85);
                enqueueStraight(20, SurfaceProperties.NORMAL);
                enqueueCurve(12, -Math.PI / 10.0, 0.0, 5);
                enqueueObstacleStraight(50, portal);
                enqueueCurve(
                        30, Math.PI / 20.0, Math.PI / 22.0, 5);
                enqueueStraight(18, SurfaceProperties.NORMAL);
                break;
            default:
                enqueueStraight(30, SurfaceProperties.NORMAL);
                enqueueObstacleStraight(42, portal && portalFirst);
                enqueueCurve(36, 0.0, Math.PI / 16.0, 5);
                enqueueStraight(18, SurfaceProperties.NORMAL);
                enqueueObstacleStraight(32, portal && !portalFirst);
                enqueueStairs(18, 4, 2, Math.PI / 7.0, -1.0);
                enqueueStraight(12, SurfaceProperties.NORMAL);
                enqueueCurve(20, -Math.PI / 7.0, 0.0, 5);
                enqueueStraight(34, SurfaceProperties.NORMAL);
                break;
        }
    }

    public synchronized void enqueueStraight(
            int segmentCount, SurfaceProperties surface) {
        requireNonNegative(segmentCount, "segmentCount");
        SurfaceProperties safe =
                surface == null ? SurfaceProperties.NORMAL : surface;
        if (segmentCount > 0) {
            authoringCommands.addLast(new StraightCommand(
                    segmentCount,
                    safe,
                    StraightKind.PLAIN,
                    false,
                    -1L));
        }
    }

    public synchronized int generateChunks(int segmentBudget) {
        requireOpen();
        if (segmentBudget == 0 || authoringCommands.isEmpty()) {
            return 0;
        }
        int limit = segmentBudget < 0
                ? Integer.MAX_VALUE : segmentBudget;
        ArrayList<TerrainSegment> upserts =
                new ArrayList<TerrainSegment>(
                        segmentBudget < 0 ? 64 : segmentBudget);
        while (upserts.size() < limit && !authoringCommands.isEmpty()) {
            AuthoringCommand command = authoringCommands.peekFirst();
            if (command.isComplete()) {
                authoringCommands.removeFirst();
                continue;
            }
            upserts.add(command.emitNext(this));
            if (command.isComplete()) {
                authoringCommands.removeFirst();
            }
        }
        if (upserts.isEmpty()) {
            return 0;
        }
        long frontier = upserts.get(upserts.size() - 1).id;
        publish(new TerrainCommit(
                state.revision(),
                state.revision() + 1L,
                frontier,
                state.retireBeforeSegmentId(),
                upserts));
        return limit;
    }

    public synchronized void removeOldTerrainElements(
            long referenceSegmentId) {
        requireOpen();
        long desired = Math.max(
                0L, referenceSegmentId - RETAIN_BEHIND_SEGMENTS);
        desired = Math.min(desired, state.committedThroughSegmentId() + 1L);
        if (desired <= state.retireBeforeSegmentId()) {
            return;
        }
        publish(new TerrainCommit(
                state.revision(),
                state.revision() + 1L,
                state.committedThroughSegmentId(),
                desired,
                Collections.<TerrainSegment>emptyList()));
    }

    public synchronized boolean hasPendingGenerationWork() {
        return !authoringCommands.isEmpty();
    }

    public synchronized int getSegmentCount() {
        int count = 0;
        for (TerrainSegment ignored : state.segments()) {
            count++;
        }
        return count;
    }

    public double getSegmentLength() {
        return segmentLength;
    }

    public int getInteractionWindowAhead() {
        return interactionWindowAhead;
    }

    public synchronized int getCommittedLeadAheadOf(
            long referenceSegmentId) {
        if (state.committedThroughSegmentId() < 0L) {
            return 0;
        }
        long reference = Math.max(
                state.retireBeforeSegmentId(), referenceSegmentId);
        long lead = state.committedThroughSegmentId() - reference;
        return lead <= 0L
                ? 0 : (int) Math.min(Integer.MAX_VALUE, lead);
    }

    /**
     * Picks the retained segment that best matches the player's current world position.
     *
     * <p>Gameplay lead tracking must not freeze on {@code lastSupportedSegmentId} while the
     * player is airborne. That frozen id makes the frontier look healthy even after the body has
     * already flown past the last authored ribbon.</p>
     */
    public synchronized long resolveStreamingReferenceSegmentId(
            long lastSupportedSegmentId,
            Vec3 absolutePosition) {
        requireOpen();
        if (absolutePosition == null) {
            throw new IllegalArgumentException("absolutePosition == null");
        }
        long bestId = -1L;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (TerrainSegment segment : state.segments()) {
            Vec3 center = segment.nearLeft
                    .add(segment.nearRight)
                    .add(segment.farLeft)
                    .add(segment.farRight)
                    .multiply(0.25);
            double dx = center.x - absolutePosition.x;
            double dy = center.y - absolutePosition.y;
            double dz = center.z - absolutePosition.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < bestDistSq
                    || (distSq == bestDistSq && segment.id > bestId)) {
                bestDistSq = distSq;
                bestId = segment.id;
            }
        }
        if (bestId < 0L) {
            return lastSupportedSegmentId;
        }
        return Math.max(lastSupportedSegmentId, bestId);
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
        ArrayList<TerrainCommit> drained =
                new ArrayList<TerrainCommit>(pendingCommits);
        pendingCommits.clear();
        return Collections.unmodifiableList(drained);
    }

    @Override
    public synchronized long revision() {
        return state.revision();
    }

    @Override
    public synchronized void close() {
        authoringCommands.clear();
        pendingCommits.clear();
        closed = true;
    }

    private void enqueueObstacleStraight(int count, boolean portal) {
        long portalPair = portal ? nextPortalPairId++ : -1L;
        if (count > 0) {
            authoringCommands.addLast(new StraightCommand(
                    count,
                    SurfaceProperties.NORMAL,
                    StraightKind.OBSTACLE,
                    portal,
                    portalPair));
        }
    }

    private void enqueueCurve(
            int curveSegments,
            double yawDelta,
            double pitchDelta,
            int pitchFadeSegments) {
        if (curveSegments <= 0 || pitchFadeSegments < 0) {
            throw new IllegalArgumentException(
                    "Curve and fade segment counts are invalid");
        }
        authoringCommands.addLast(new CurveCommand(
                curveSegments, yawDelta, pitchDelta, pitchFadeSegments));
    }

    private void enqueueStairs(
            int segmentsPerStair,
            int stairCount,
            int gapSegments,
            double yawDelta,
            double stepHeight) {
        if (segmentsPerStair <= 0 || stairCount <= 0
                || gapSegments < 0) {
            throw new IllegalArgumentException("Invalid stair dimensions");
        }
        authoringCommands.addLast(new StairsCommand(
                segmentsPerStair,
                stairCount,
                gapSegments,
                yawDelta,
                stepHeight));
    }

    private void enqueueBoostRamp(
            int rampSegments,
            int gapSegments,
            int landingSegments,
            double launchPitch) {
        if (rampSegments <= 0 || gapSegments < 0
                || landingSegments < 0) {
            throw new IllegalArgumentException("Invalid boost-ramp dimensions");
        }
        authoringCommands.addLast(new BoostRampCommand(
                rampSegments,
                gapSegments,
                landingSegments,
                launchPitch));
    }

    private TerrainSegment createSegment(
            boolean solid,
            SurfaceProperties surface,
            float alpha,
            float brightness,
            List<TerrainFeatureSpec> features) {
        Vec3 direction = direction();
        Vec3 right = right();
        Vec3 nearLeft = cursor.subtract(right.multiply(width * 0.5));
        Vec3 nearRight = cursor.add(right.multiply(width * 0.5));
        Vec3 next = cursor.add(direction.multiply(segmentLength));
        Vec3 farLeft = next.subtract(right.multiply(width * 0.5));
        Vec3 farRight = next.add(right.multiply(width * 0.5));
        TerrainVertexAppearance targetAppearance =
                new TerrainVertexAppearance(alpha, brightness);
        TerrainVertexAppearance nearLeftAppearance = targetAppearance;
        TerrainVertexAppearance nearRightAppearance = targetAppearance;
        // A connected canonical seam is one shared edge, not two independently
        // calculated approximations. Reusing its geometry and appearance also
        // keeps the invariant intact when generation pauses at a chunk boundary.
        if (solid && previousAuthoredSolid) {
            if (previousFarLeft == null || previousFarRight == null
                    || previousFarLeftAppearance == null
                    || previousFarRightAppearance == null) {
                throw new IllegalStateException(
                        "Connected terrain is missing its previous far edge");
            }
            nearLeft = previousFarLeft;
            nearRight = previousFarRight;
            nearLeftAppearance = previousFarLeftAppearance;
            nearRightAppearance = previousFarRightAppearance;
        }
        TerrainSegment result = new TerrainSegment(
                nextSegmentId++,
                nearLeft,
                nearRight,
                farLeft,
                farRight,
                solid,
                solid && previousAuthoredSolid,
                surface,
                nearLeftAppearance,
                nearRightAppearance,
                targetAppearance,
                targetAppearance,
                features);
        cursor = next;
        previousFarLeft = farLeft;
        previousFarRight = farRight;
        previousFarLeftAppearance = targetAppearance;
        previousFarRightAppearance = targetAppearance;
        previousAuthoredSolid = solid;
        return result;
    }

    private void addSpike(
            List<TerrainFeatureSpec> destination,
            int positionIndex,
            int sectionCount) {
        long owner = nextSegmentId;
        Vec3 center = cursor.add(direction().multiply(segmentLength * 0.5));
        double lateralSign =
                ((positionIndex + sectionCount) & 1) == 0 ? -1.0 : 1.0;
        center = center.add(right().multiply(lateralSign * width * 0.27));
        Vec3 normal = surfaceNormal();
        double radius = Math.min(width * 0.10, segmentLength * 0.25);
        double height = 0.42 + 0.12
                * unitHash(owner * 31L + positionIndex);
        Vec3 across = right().multiply(radius);
        Vec3 along = direction().normalized().multiply(radius);
        long id = nextFeatureId++;
        destination.add(new TerrainFeatureSpec.Spike(
                id,
                owner,
                center.subtract(across).subtract(along),
                center.add(across).subtract(along),
                center.subtract(across).add(along),
                center.add(across).add(along),
                center.add(normal.multiply(height)),
                normal,
                0.025,
                center,
                radius,
                height));
    }

    private void addCollectible(
            List<TerrainFeatureSpec> destination) {
        long owner = nextSegmentId;
        Vec3 center = cursor.add(direction().multiply(segmentLength * 0.5))
                .add(surfaceNormal().multiply(0.56));
        destination.add(new TerrainFeatureSpec.AirJumpCollectible(
                nextFeatureId++,
                owner,
                center,
                0.22,
                "POTION_FEATHER"));
    }

    private void addPortal(
            List<TerrainFeatureSpec> destination,
            long pairId,
            TerrainFeatureSpec.Portal.Role role) {
        long owner = nextSegmentId;
        Vec3 forward = new Vec3(
                Math.sin(yawRadians), 0.0, -Math.cos(yawRadians));
        Vec3 center = cursor.add(direction().multiply(segmentLength * 0.5))
                .add(Vec3.UP.multiply(2.22));
        destination.add(new TerrainFeatureSpec.Portal(
                nextFeatureId++,
                owner,
                pairId,
                role,
                center,
                forward,
                Vec3.UP,
                1.0,
                1.0,
                "BEACON"));
    }

    private Vec3 direction() {
        double cosPitch = Math.cos(pitchRadians);
        return new Vec3(
                Math.sin(yawRadians) * cosPitch,
                Math.sin(pitchRadians),
                -Math.cos(yawRadians) * cosPitch).normalized();
    }

    private Vec3 right() {
        return new Vec3(
                Math.cos(yawRadians), 0.0, Math.sin(yawRadians));
    }

    private Vec3 surfaceNormal() {
        Vec3 normal = right().cross(direction()).normalized();
        return normal.y < 0.0 ? normal.multiply(-1.0) : normal;
    }

    private void publish(TerrainCommit commit) {
        state.apply(commit);
        pendingCommits.add(commit);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Terrain generator is closed");
        }
    }

    private static float brightness(SurfaceProperties surface) {
        return surface.kind == SurfaceProperties.Kind.NORMAL ? 1f : 1.25f;
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
    }

    private static double unitHash(long value) {
        long mixed = mix64(value);
        return (mixed >>> 11) * 0x1.0p-53;
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }
}
