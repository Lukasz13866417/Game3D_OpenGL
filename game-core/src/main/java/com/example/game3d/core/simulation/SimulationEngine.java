package com.example.game3d.core.simulation;

import com.example.game3d.core.input.FixedStepInput;
import com.example.game3d.core.input.PlayerInputEvent;
import com.example.game3d.core.math.Aabb;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.CollisionTerrain;
import com.example.game3d.core.terrain.SurfaceProperties;
import com.example.game3d.core.terrain.TerrainCollisionIndex;
import com.example.game3d.core.terrain.TerrainCommit;
import com.example.game3d.core.terrain.addon.Addon;
import com.example.game3d.core.terrain.addon.AddonContactContext;
import com.example.game3d.core.terrain.addon.AddonEffectSink;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.core.terrain.TerrainTriangle;
import com.example.game3d.core.terrain.TerrainWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Authoritative deterministic player simulation. Rendering and Android lifecycle are deliberately
 * absent from this class.
 */
public strictfp final class SimulationEngine {
    private CollisionTerrain terrain;
    private final PhysicsConfig config;
    private final StepObserver observer;
    private final PlayerBodyState state;
    // These buffers are single-active-query scratch. Never nest another terrain query while
    // iterating one of them; SimulationEngine is deliberately single-threaded and non-reentrant.
    private final ArrayList<TerrainTriangle> triangleQueryScratch =
            new ArrayList<TerrainTriangle>(16);
    /*
     * Broad-phase "fat AABB" cache. A fixed tick performs several overlapping sweep and support
     * queries. Keeping the nearby triangles avoids repeatedly walking the terrain grid while the
     * exact-bounds filter below preserves query semantics, including folded or vertically stacked
     * track sections.
     */
    private final ArrayList<TerrainTriangle> triangleNeighborhoodScratch =
            new ArrayList<TerrainTriangle>(16);
    private Aabb triangleNeighborhoodBounds;
    private final ArrayList<Addon> addonQueryScratch =
            new ArrayList<Addon>(8);
    private final ArrayList<JumpDecision> forecastJumpScratch =
            new ArrayList<JumpDecision>();
    private final ArrayList<SimulationEvent> forecastEventScratch =
            new ArrayList<SimulationEvent>();
    private SafeSupportForecastCache safeSupportForecastCache;
    private double commandedCruisingSpeed;
    private AddonActivitySnapshot cachedAddonActivity =
            new AddonActivitySnapshot(Collections.<Long>emptySet());
    private boolean addonActivityDirty;

    public SimulationEngine(TerrainWorld terrain, PhysicsConfig config,
                            Vec3 initialPosition, int initialAirJumpCharges,
                            StepObserver observer) {
        this(terrain, config, initialPosition, Vec3.ZERO, initialAirJumpCharges, observer);
    }

    public SimulationEngine(TerrainWorld terrain, PhysicsConfig config,
                            Vec3 initialPosition, Vec3 initialVelocity,
                            int initialAirJumpCharges, StepObserver observer) {
        this(terrain, config, initialPosition, initialVelocity, 0.0,
                initialAirJumpCharges, observer);
    }

    public SimulationEngine(TerrainWorld terrain, PhysicsConfig config,
                            Vec3 initialPosition, Vec3 initialVelocity,
                            double initialAngularVelocity,
                            int initialAirJumpCharges, StepObserver observer) {
        if (initialAirJumpCharges < 0) {
            throw new IllegalArgumentException("Initial air jump charges cannot be negative");
        }
        if (!Double.isFinite(initialAngularVelocity)) {
            throw new IllegalArgumentException("Initial angular velocity must be finite");
        }
        this.terrain = terrain;
        this.config = config;
        this.observer = observer == null ? StepObserver.NONE : observer;
        this.state = new PlayerBodyState(
                initialPosition, initialVelocity, initialAngularVelocity,
                initialAirJumpCharges);
        this.commandedCruisingSpeed = config.cruisingSpeed;
    }

    public SimulationEngine(TerrainWorld terrain, Vec3 initialPosition) {
        this(terrain, new PhysicsConfig(), initialPosition, 0, StepObserver.NONE);
    }

    public SimulationEngine(
            TerrainSnapshot terrain,
            PhysicsConfig config,
            Vec3 initialPosition,
            Vec3 initialVelocity,
            double initialAngularVelocity,
            int initialAirJumpCharges,
            StepObserver observer) {
        this(new TerrainCollisionIndex(terrain), config, initialPosition,
                initialVelocity, initialAngularVelocity, initialAirJumpCharges, observer);
    }

    public SimulationEngine(
            TerrainSnapshot terrain,
            PhysicsConfig config,
            Vec3 initialPosition,
            int initialAirJumpCharges,
            StepObserver observer) {
        this(terrain, config, initialPosition, Vec3.ZERO, 0.0,
                initialAirJumpCharges, observer);
    }

    /**
     * Takes ownership of an already-built canonical collision projection.
     *
     * <p>This is useful when a future gameplay session prepares its immutable bootstrap index
     * off the render thread, then transfers it to the single-threaded simulation.</p>
     */
    public SimulationEngine(
            TerrainCollisionIndex terrain,
            PhysicsConfig config,
            Vec3 initialPosition,
            int initialAirJumpCharges,
            StepObserver observer) {
        this((CollisionTerrain) terrain, config, initialPosition,
                Vec3.ZERO, 0.0, initialAirJumpCharges, observer);
    }

    private SimulationEngine(
            CollisionTerrain terrain,
            PhysicsConfig config,
            Vec3 initialPosition,
            Vec3 initialVelocity,
            double initialAngularVelocity,
            int initialAirJumpCharges,
            StepObserver observer) {
        if (terrain == null) {
            throw new IllegalArgumentException("terrain == null");
        }
        if (initialAirJumpCharges < 0) {
            throw new IllegalArgumentException("Initial air jump charges cannot be negative");
        }
        if (!Double.isFinite(initialAngularVelocity)) {
            throw new IllegalArgumentException("Initial angular velocity must be finite");
        }
        this.terrain = terrain;
        this.config = config;
        this.observer = observer == null ? StepObserver.NONE : observer;
        this.state = new PlayerBodyState(
                initialPosition, initialVelocity, initialAngularVelocity,
                initialAirJumpCharges);
        this.commandedCruisingSpeed = config.cruisingSpeed;
    }

    public StepResult step(FixedStepInput input) {
        if (input == null) {
            input = FixedStepInput.EMPTY;
        }
        if (!input.events.isEmpty()) {
            invalidateSafeSupportForecast();
        }
        boolean captureDiagnostics = observer != StepObserver.NONE;
        PlayerSnapshot before = captureDiagnostics ? snapshot() : null;
        Vec3 tickStartPosition = state.position;
        ArrayList<TerrainTriangle> queried = captureDiagnostics
                ? new ArrayList<TerrainTriangle>() : null;
        ArrayList<ContactSnapshot> contacts = captureDiagnostics
                ? new ArrayList<ContactSnapshot>() : null;
        ArrayList<MotionSegment> motionSegments = captureDiagnostics
                ? new ArrayList<MotionSegment>() : null;
        ArrayList<SpinSegment> spinSegments = captureDiagnostics
                ? new ArrayList<SpinSegment>() : null;
        ArrayList<JumpDecision> jumpEvaluations = new ArrayList<JumpDecision>();
        ArrayList<SimulationEvent> events = new ArrayList<SimulationEvent>();
        decayHeldGestureCharge();
        InputOutcome inputOutcome = processInput(input.events);
        updateReleasedCharge();

        JumpDecision winningDecision = evaluatePrePhysicsJump(
                inputOutcome.groundedChargedRelease, jumpEvaluations, events);
        if (winningDecision.action == JumpDecision.Action.JUMP_NOW) {
            executeJump(winningDecision, events);
        }

        boolean wasGrounded = state.grounded;
        PhysicsOutcome outcome = advancePhysics(state, !state.dead, queried, contacts,
                motionSegments, spinSegments, jumpEvaluations, events);
        enforceFallingOnlyLandingJumpArm();
        // A turn can arrive one tick after landing because touch events keep their timestamps.
        // Once grounded, movement should follow the new camera-facing direction immediately.
        if (!state.dead && state.grounded && inputOutcome.facingChanged) {
            snapHorizontalVelocity(state);
        }
        completeMotionPath(motionSegments, tickStartPosition, state.position);
        if (outcome.encounteredSupport || outcome.landed || state.grounded) {
            invalidateSafeSupportForecast();
        }
        if (state.grounded) {
            state.supportSegmentId =
                    terrain.segmentIdForTriangle(state.supportTriangleId);
            if (state.supportSegmentId >= 0L) {
                state.lastSupportedSegmentId = state.supportSegmentId;
            }
            state.hasSupportedAnchor = true;
            state.lastSupportedY = state.position.y;
        } else {
            state.supportSegmentId = -1L;
        }

        if (outcome.landingPosition != null && state.grounded
                && !wasGrounded && !outcome.landingJumped) {
            events.add(new SimulationEvent(SimulationEvent.Type.LAND,
                    outcome.landingTriangleId, "first supported tick",
                    eventTimeNanos(state, outcome.landingTickFraction),
                    outcome.landingPosition, outcome.landingTickFraction));
        }
        if (outcome.spikeId >= 0L && !state.dead) {
            state.dead = true;
            invalidateSafeSupportForecast();
            events.add(new SimulationEvent(SimulationEvent.Type.SPIKE_HIT,
                    outcome.spikeId, "analytic cylinder overlapped spike volume",
                    eventTimeNanos(state, outcome.spikeTickFraction),
                    outcome.spikePosition, outcome.spikeTickFraction));
            events.add(new SimulationEvent(SimulationEvent.Type.PLAYER_DIED,
                    outcome.spikeId, "spike",
                    eventTimeNanos(state, outcome.spikeTickFraction),
                    outcome.spikePosition, outcome.spikeTickFraction));
        }
        if (state.position.y < deathFloor(state) && !state.dead) {
            state.dead = true;
            invalidateSafeSupportForecast();
            events.add(new SimulationEvent(SimulationEvent.Type.PLAYER_DIED, -1L,
                    "fell below recoverable world bound",
                    eventTimeNanos(state, 1.0), state.position, 1.0));
        }
        if (!state.dead) {
            collectPickups(events);
        }
        enforceFallingOnlyLandingJumpArm();
        sortEvents(events);

        state.tick++;
        state.timeNanos += PhysicsConfig.FIXED_DT_NANOS;
        updateRenderOrigin();
        validate(events);
        PlayerSnapshot after = snapshot();
        if (captureDiagnostics) {
            StepRecord record = new StepRecord(before, after, input.events, queried, contacts,
                    motionSegments, spinSegments, jumpEvaluations, events);
            observer.onStep(record);
        }

        JumpDecision reported = lastDecision(jumpEvaluations, winningDecision);
        return new StepResult(after, events, reported);
    }

    public PlayerSnapshot snapshot() {
        Vec3 renderLocal = state.position.subtract(state.worldOrigin);
        Vec3 heading = state.heading();
        Vec3 axis = state.axis();
        return new PlayerSnapshot(
                state.tick,
                state.timeNanos,
                renderLocal,
                state.position,
                state.worldOrigin,
                state.velocity,
                heading,
                axis,
                state.yawRadians,
                state.axleRadians,
                state.axleDeltaRadians,
                state.angularVelocity,
                state.driveAngularVelocity * config.cylinderRadius,
                state.gestureCharge,
                state.gestureChargePotential,
                state.gestureRawDeltaX,
                state.gestureRawUpwardDistance,
                state.gestureMaxAbsRawDeltaX,
                jumpChargePathEligible(state),
                state.airJumpCharges,
                state.grounded,
                state.supportTriangleId,
                state.supportSegmentId,
                state.lastSupportedSegmentId,
                state.supportNormal,
                state.touchHeld,
                state.landingJumpArmed,
                jumpCooldownRemainingNanosAt(state, state.timeNanos),
                state.impactBrakeArmed,
                state.dead,
                stateHash());
    }

    public PhysicsConfig config() {
        return config;
    }

    public SimulationFrameSnapshot frameSnapshot() {
        return frameSnapshot(snapshot());
    }

    /**
     * Builds the presentation frame around an already captured authoritative player snapshot.
     *
     * <p>The Android clock already receives the snapshot from {@link #step}; accepting it here
     * avoids taking a duplicate body snapshot merely to attach terrain/addon presentation
     * state.</p>
     */
    public SimulationFrameSnapshot frameSnapshot(PlayerSnapshot playerSnapshot) {
        if (playerSnapshot == null || playerSnapshot.tick != state.tick
                || playerSnapshot.timeNanos != state.timeNanos) {
            throw new IllegalArgumentException(
                    "playerSnapshot must describe the engine's current tick");
        }
        if (addonActivityDirty) {
            cachedAddonActivity =
                    new AddonActivitySnapshot(state.inactiveAddonIds);
            addonActivityDirty = false;
        }
        return new SimulationFrameSnapshot(
                playerSnapshot, terrainRevision(), cachedAddonActivity);
    }

    public long terrainRevision() {
        return terrain instanceof TerrainCollisionIndex
                ? ((TerrainCollisionIndex) terrain).revision() : 0L;
    }

    public long terrainDigest() {
        if (terrain instanceof TerrainCollisionIndex) {
            return ((TerrainCollisionIndex) terrain).deterministicDigest();
        }
        return terrain instanceof TerrainWorld
                ? ((TerrainWorld) terrain).deterministicDigest() : 0L;
    }

    /**
     * Applies a complete terrain update atomically between fixed ticks.
     *
     * <p>Existing static-world constructors remain supported, but commit application requires a
     * canonical snapshot-backed engine.</p>
     */
    public void applyTerrainCommit(TerrainCommit commit) {
        if (!(terrain instanceof TerrainCollisionIndex)) {
            throw new IllegalStateException(
                    "Terrain commits require a canonical TerrainSnapshot constructor");
        }
        TerrainCollisionIndex index = (TerrainCollisionIndex) terrain;
        long supportedId = state.supportTriangleId;
        long oldFingerprint = supportedId < 0L
                ? Long.MIN_VALUE : index.collisionFingerprint(supportedId);
        index.apply(commit);
        invalidateSafeSupportForecast();
        invalidateTriangleNeighborhood();
        addonQueryScratch.clear();
        purgeRetiredInactiveAddons(index);
        if (supportedId >= 0L
                && oldFingerprint != index.collisionFingerprint(supportedId)) {
            clearSupport();
        }
    }

    /**
     * Atomically swaps the immutable terrain snapshot between fixed ticks. Body state, collected
     * addon IDs, and support identity are retained when the referenced triangle still exists.
     */
    public void replaceTerrain(TerrainWorld replacement) {
        if (replacement == null) {
            throw new IllegalArgumentException("replacement == null");
        }
        terrain = replacement;
        invalidateSafeSupportForecast();
        invalidateTriangleNeighborhood();
        addonQueryScratch.clear();
        if (state.supportTriangleId >= 0L
                && !replacement.containsTriangle(state.supportTriangleId)) {
            clearSupport();
        }
    }

    /** Sets the deterministic motor target used by subsequent fixed ticks. */
    public void setCruisingSpeed(double unitsPerSecond) {
        if (!(unitsPerSecond > 0.0) || !Double.isFinite(unitsPerSecond)) {
            throw new IllegalArgumentException("Cruising speed must be finite and positive");
        }
        invalidateSafeSupportForecast();
        commandedCruisingSpeed = unitsPerSecond;
    }

    public double cruisingSpeed() {
        return commandedCruisingSpeed;
    }

    private InputOutcome processInput(List<PlayerInputEvent> inputEvents) {
        InputOutcome outcome = new InputOutcome();
        if (state.dead) {
            return outcome;
        }
        for (PlayerInputEvent event : inputEvents) {
            if (event.type == PlayerInputEvent.Type.CANCEL_GESTURE) {
                state.touchHeld = false;
                state.gestureConsumed = false;
                clearGestureRequest();
            } else if (event.type == PlayerInputEvent.Type.TOUCH_DOWN) {
                if (!state.touchHeld) {
                    clearGestureRequest();
                    state.gestureConsumed = false;
                    state.touchHeld = true;
                }
            } else if (event.type == PlayerInputEvent.Type.TOUCH_UP) {
                if (!state.touchHeld) {
                    continue;
                }
                state.touchHeld = false;
                resetGestureClassifier(state);
                // The impact brake is explicitly a hold-through-impact action.
                state.impactBrakeArmed = false;
                if (state.gestureConsumed) {
                    clearGestureRequest();
                    continue;
                }
                if (state.grounded) {
                    if (charged()) {
                        outcome.groundedChargedRelease = true;
                        break;
                    }
                    clearGestureRequest();
                } else if (charged()) {
                    state.bufferedAirborneRequest = true;
                    state.airborneReleaseNanos = state.timeNanos;
                    state.airborneReleaseCharge = state.gestureCharge;
                    break;
                } else {
                    clearGestureRequest();
                }
            } else {
                if (event.deltaXScreenHeights != 0.0) {
                    outcome.facingChanged = true;
                }
                applySwipe(event);
            }
        }
        return outcome;
    }

    private void applySwipe(PlayerInputEvent event) {
        state.yawRadians = wrapRadians(state.yawRadians
                + event.deltaXScreenHeights * config.facingRadiansPerScreenHeight);
        if (!state.touchHeld || state.gestureConsumed) {
            return;
        }

        // Raw motion decides whether this individual packet is vertical enough. Earlier
        // steering never poisons a later upward packet; sensitivity-scaled Y decides how much
        // charge that accepted packet contributes.
        state.gestureRawDeltaX += event.rawDeltaXScreenHeights;
        state.gestureMaxAbsRawDeltaX = Math.max(
                state.gestureMaxAbsRawDeltaX,
                Math.abs(state.gestureRawDeltaX));

        if (event.rawDeltaYScreenHeights < 0.0) {
            state.gestureRawUpwardDistance -= event.rawDeltaYScreenHeights;
            state.impactBrakeArmed = false;
            double contribution = Math.max(0.0,
                    -event.deltaYScreenHeights * config.swipeChargePerScreenHeight);
            state.gestureLastSwipeChargeEligible = contribution > 0.0
                    && isUpwardChargeSwipe(event);
            if (state.gestureLastSwipeChargeEligible) {
                state.gestureCharge = clamp01(state.gestureCharge + contribution);
                state.gestureChargePotential = state.gestureCharge;
                state.heldChargeLastContributionNanos = state.timeNanos;
            }
        } else {
            state.gestureLastSwipeChargeEligible = false;
            applyImpactBrakeSwipe(event);
        }
    }

    private boolean isUpwardChargeSwipe(PlayerInputEvent event) {
        return event.rawDeltaYScreenHeights < 0.0
                && Math.abs(event.rawDeltaXScreenHeights) <=
                -event.rawDeltaYScreenHeights * config.maxJumpChargeXToYRatio
                        + 1.0e-12;
    }

    private void decayHeldGestureCharge() {
        if (!state.touchHeld || state.gestureConsumed
                || state.heldChargeLastContributionNanos < 0L) {
            return;
        }
        long graceEnd = state.heldChargeLastContributionNanos
                + config.heldGestureChargeGraceNanos;
        // State time is this tick's start. Age charge only through that boundary so a release
        // is never evaluated using charge from the future portion of its physics tick.
        long tickEnd = state.timeNanos;
        long tickStart = Math.max(0L, tickEnd - PhysicsConfig.FIXED_DT_NANOS);
        long decayStart = Math.max(tickStart, graceEnd);
        long decayNanos = Math.max(0L, tickEnd - decayStart);
        if (decayNanos == 0L) {
            return;
        }
        double decay = (double) decayNanos
                / (double) config.heldGestureChargeDecayNanos;
        state.gestureCharge = clamp01(state.gestureCharge - decay);
        state.gestureChargePotential = state.gestureCharge;
        if (!charged()) {
            state.landingJumpArmed = false;
        }
    }

    private void applyImpactBrakeSwipe(PlayerInputEvent event) {
        if (!isVerticallyDominantDownSwipe(event)) {
            return;
        }
        if (!state.grounded && state.velocity.y < 0.0) {
            state.impactBrakeArmed = true;
            state.landingJumpArmed = false;
        }
        double cancellation =
                event.deltaYScreenHeights * config.swipeCancelPerScreenHeight;
        state.gestureCharge = clamp01(state.gestureCharge - cancellation);
        state.gestureChargePotential = state.gestureCharge;
    }

    private boolean isVerticallyDominantDownSwipe(PlayerInputEvent event) {
        // Preserve the established impact-brake gesture. The new raw/cumulative limits apply
        // only to adding jump charge.
        return event.deltaYScreenHeights > 0.0
                && Math.abs(event.deltaYScreenHeights) + 1.0e-12
                >= Math.abs(event.deltaXScreenHeights)
                * config.swipeVerticalDominance;
    }

    private boolean jumpChargePathEligible(PlayerBodyState body) {
        return body.gestureLastSwipeChargeEligible;
    }

    private void updateReleasedCharge() {
        if (!state.bufferedAirborneRequest || state.airborneReleaseNanos < 0L) {
            return;
        }
        if (state.landingJumpArmed) {
            // Once the bounded safe-impact forecast arms a landing jump, the visible bar stays
            // exactly where the player released it. The forecast is revalidated every tick, so
            // this cannot retain charge indefinitely after the landing ceases to be imminent.
            state.gestureCharge = state.airborneReleaseCharge;
            return;
        }
        long elapsed = Math.max(0L, state.timeNanos - state.airborneReleaseNanos);
        if (elapsed <= config.airborneChargeFreezeNanos) {
            state.gestureCharge = state.airborneReleaseCharge;
            return;
        }
        long decayElapsed = elapsed - config.airborneChargeFreezeNanos;
        double remaining = 1.0
                - (double) decayElapsed / (double) config.airborneChargeDecayNanos;
        state.gestureCharge = clamp01(state.airborneReleaseCharge * remaining);
        if (remaining <= 0.0) {
            clearGestureRequest();
        }
    }

    private JumpDecision evaluatePrePhysicsJump(
            boolean releaseThisTick, List<JumpDecision> evaluations,
            List<SimulationEvent> events) {
        if (state.dead) {
            return addDecision(evaluations, JumpRuleId.TERMINAL_REJECT,
                    JumpDecision.Action.REJECT, false, "player is terminal");
        }
        if (state.grounded && charged()
                && (releaseThisTick
                || state.bufferedAirborneRequest
                || state.landingJumpArmed)) {
            if (jumpCooldownReadyAt(state, state.timeNanos)) {
                JumpRuleId rule = releaseThisTick
                        ? JumpRuleId.GROUNDED_RELEASED
                        : JumpRuleId.GROUNDED_BUFFERED;
                return addDecision(evaluations, rule,
                        JumpDecision.Action.JUMP_NOW, false,
                        releaseThisTick
                                ? "grounded charged gesture released this tick"
                                : "retained jump became eligible after landing/cooldown");
            }
            if (releaseThisTick && !state.bufferedAirborneRequest) {
                retainReleasedJumpRequest(state);
            }
            return addDecision(evaluations, JumpRuleId.JUMP_COOLDOWN_DEFER,
                    JumpDecision.Action.DEFER, false,
                    cooldownReason(state, state.timeNanos));
        }
        if (!state.grounded && charged()) {
            boolean falling = state.velocity.y < 0.0;
            ForecastResult forecast = falling && !state.impactBrakeArmed
                    ? forecastWithoutJump() : null;
            if (forecast == ForecastResult.SAFE_SUPPORT_FIRST) {
                if (!state.landingJumpArmed) {
                    state.landingJumpArmed = true;
                    long horizonMillis = Math.round(
                            config.landingJumpBufferTicks * PhysicsConfig.FIXED_DT_NANOS
                                    / 1_000_000.0);
                    events.add(new SimulationEvent(
                            SimulationEvent.Type.LANDING_JUMP_ARMED,
                            -1L,
                            "falling; spike-safe support within "
                                    + horizonMillis + " ms",
                            eventTimeNanos(state, 0.0), state.position, 0.0));
                }
                JumpRuleId deferRule = state.airJumpCharges <= 0
                        ? JumpRuleId.AIRBORNE_NO_CHARGE_DEFER
                        : JumpRuleId.AIRBORNE_SAFE_SUPPORT_DEFER;
                return addDecision(evaluations, deferRule,
                        JumpDecision.Action.DEFER, false,
                        "landing jump armed for imminent spike-safe support");
            }

            // A cached/previously armed landing is no longer safe or close enough.
            state.landingJumpArmed = false;
            if (state.bufferedAirborneRequest) {
                if (state.airJumpCharges <= 0) {
                    JumpDecision rejected = addDecision(
                            evaluations, JumpRuleId.AIRBORNE_NO_CHARGE_REJECT,
                            JumpDecision.Action.REJECT, false,
                            falling
                                    ? "no spike-safe support inside landing-buffer horizon"
                                    : "landing buffer requires downward motion");
                    clearGestureRequest();
                    return rejected;
                }
                if (!jumpCooldownReadyAt(state, state.timeNanos)) {
                    return addDecision(evaluations, JumpRuleId.JUMP_COOLDOWN_DEFER,
                            JumpDecision.Action.DEFER, false,
                            cooldownReason(state, state.timeNanos));
                }
                if (forecast == ForecastResult.SPIKE_FIRST) {
                    return addDecision(evaluations, JumpRuleId.AIRBORNE_SPIKE_FIRST,
                            JumpDecision.Action.JUMP_NOW, true,
                            "no-jump forecast reaches a spike before safe support");
                }
                if (!falling) {
                    return addDecision(evaluations, JumpRuleId.AIRBORNE_RELEASED,
                            JumpDecision.Action.JUMP_NOW, true,
                            "released airborne jump while not falling");
                }
                if (forecast == ForecastResult.NO_RECOVERABLE_SUPPORT) {
                    return addDecision(evaluations, JumpRuleId.AIRBORNE_UNRECOVERABLE,
                            JumpDecision.Action.JUMP_NOW, true,
                            "no spike-safe support inside landing-buffer horizon");
                }
                throw new IllegalStateException("Unhandled forecast result " + forecast);
            }
        }
        if (state.bufferedAirborneRequest && !charged()) {
            return addDecision(evaluations, JumpRuleId.CHARGE_EXPIRED,
                    JumpDecision.Action.REJECT, false, "gesture charge expired");
        }
        return addDecision(evaluations, JumpRuleId.NO_REQUEST,
                JumpDecision.Action.REJECT, false, "no applicable jump request");
    }

    private PhysicsOutcome advancePhysics(
            PlayerBodyState body, boolean allowLandingRules,
            List<TerrainTriangle> queried, List<ContactSnapshot> contacts,
            List<MotionSegment> motionSegments,
            List<SpinSegment> spinSegments,
            List<JumpDecision> jumpEvaluations, List<SimulationEvent> events) {
        double dt = PhysicsConfig.FIXED_DT_SECONDS;
        Vec3 heading = body.heading();
        body.axleDeltaRadians = 0.0;
        double driveTargetOmega = commandedCruisingSpeed / config.cylinderRadius;
        double driveAngularAcceleration = (body.grounded
                ? config.motorAcceleration / config.cylinderRadius
                : config.airAngularAcceleration);
        body.driveAngularVelocity = approach(
                body.driveAngularVelocity, driveTargetOmega,
                driveAngularAcceleration * dt);
        body.velocity = body.velocity.add(new Vec3(0.0, -config.gravity * dt, 0.0));

        double expectedTravel = body.velocity.length() * dt;
        int substeps = Math.max(1, Math.min(256,
                (int) Math.ceil(expectedTravel / Math.max(0.025, config.cylinderRadius * 0.30))));
        double subDt = dt / substeps;
        boolean startedGrounded = body.grounded;
        boolean carriedSupport = startedGrounded;
        long carriedSupportId = body.supportTriangleId;
        Vec3 carriedSupportNormal = body.supportNormal;
        body.grounded = false;
        PhysicsOutcome outcome = new PhysicsOutcome();
        int impactsThisTick = 0;
        boolean postImpactMotion = false;

        for (int substep = 0; substep < substeps; substep++) {
            Vec3 substepStartPosition = body.position;
            boolean motionSupported = carriedSupport;
            boolean supportThisSubstep = false;
            long activeSupportId = carriedSupportId;
            Vec3 activeSupportNormal = carriedSupportNormal;
            boolean tractionApplied = false;
            double localElapsed = 0.0;
            long ignoredTouchingTriangleId = -1L;
            while (localElapsed < 1.0 - 1.0e-12) {
                double remainingDt = subDt * (1.0 - localElapsed);
                Vec3 start = body.position;
                Vec3 attempted = start.add(body.velocity.multiply(remainingDt));
                SweepSelection selection = earliestSweep(
                        start, attempted, body.axis(), queried,
                        ignoredTouchingTriangleId);
                if (selection == null) {
                    double startFraction = (substep + localElapsed) / substeps;
                    double endFraction = (substep + 1.0) / substeps;
                    body.position = attempted;
                    MotionSegment.Phase phase = (motionSupported || supportThisSubstep)
                            ? MotionSegment.Phase.SUPPORTED
                            : (postImpactMotion
                            ? MotionSegment.Phase.POST_IMPACT
                            : MotionSegment.Phase.FREE_FLIGHT);
                    recordResolvedMotion(
                            body, outcome, motionSegments,
                            spinSegments,
                            startFraction, endFraction, start, attempted, phase,
                            activeSupportId, activeSupportNormal);
                    localElapsed = 1.0;
                    break;
                }
                if (selection.failed) {
                    double failureFraction = (substep + localElapsed) / substeps;
                    events.add(new SimulationEvent(
                            SimulationEvent.Type.INVARIANT_FAILURE,
                            selection.triangle.id,
                            "cylinder-triangle shape cast exhausted its iteration budget",
                            eventTimeNanos(body, failureFraction),
                            body.position, failureFraction));
                    outcome.aborted = true;
                    break;
                }
                if (++impactsThisTick > 8) {
                    double failureFraction = (substep + localElapsed) / substeps;
                    events.add(new SimulationEvent(
                            SimulationEvent.Type.INVARIANT_FAILURE,
                            selection.triangle.id,
                            "more than eight terrain impacts in one fixed tick",
                            eventTimeNanos(body, failureFraction),
                            body.position, failureFraction));
                    outcome.aborted = true;
                    break;
                }

                CylinderTriangleCast.SweepHit hit = selection.hit;
                double hitLocalFraction = localElapsed
                        + (1.0 - localElapsed) * hit.fraction;
                double startFraction = (substep + localElapsed) / substeps;
                double hitTickFraction = (substep + hitLocalFraction) / substeps;
                body.position = hit.centerAtImpact;
                MotionSegment.Phase approachPhase = (motionSupported || supportThisSubstep)
                        ? MotionSegment.Phase.SUPPORTED
                        : (postImpactMotion
                        ? MotionSegment.Phase.POST_IMPACT
                        : MotionSegment.Phase.FREE_FLIGHT);
                recordResolvedMotion(
                        body, outcome, motionSegments,
                        spinSegments,
                        startFraction, hitTickFraction, start, body.position,
                        approachPhase, activeSupportId, activeSupportNormal);

                Vec3 preVelocity = body.velocity;
                double preAngularVelocity = body.angularVelocity;
                boolean supports = hit.normal.y >= config.supportSlopeCosine;
                if (supports) {
                    outcome.supportTriangleId = selection.triangle.id;
                }
                boolean firstSupportEncounter = supports
                        && !startedGrounded && !outcome.encounteredSupport;
                if (firstSupportEncounter) {
                    outcome.encounteredSupport = true;
                }
                double normalVelocity = body.velocity.dot(hit.normal);
                boolean impactBrake = firstSupportEncounter
                        && body.impactBrakeArmed
                        && normalVelocity < 0.0;
                long hitTimeNanos = eventTimeNanos(body, hitTickFraction);
                boolean landingJumpRequested = firstSupportEncounter
                        && allowLandingRules
                        && body.landingJumpArmed
                        && charged(body);
                boolean landingJumpCooldownReady =
                        jumpCooldownReadyAt(body, hitTimeNanos);

                if (!impactBrake && landingJumpRequested
                        && !landingJumpCooldownReady) {
                    addDecision(jumpEvaluations, JumpRuleId.JUMP_COOLDOWN_DEFER,
                            JumpDecision.Action.DEFER, false,
                            cooldownReason(body, hitTimeNanos));
                }

                if (!impactBrake && landingJumpRequested
                        && landingJumpCooldownReady) {
                    JumpDecision landing = addDecision(jumpEvaluations,
                            JumpRuleId.LANDING_CHARGED,
                            JumpDecision.Action.JUMP_NOW, false,
                            "first supported impact with sufficient charge");
                    executeJump(body, landing, events, hitTickFraction);
                    outcome.landingJumped = true;
                    motionSupported = false;
                    supportThisSubstep = false;
                    activeSupportId = -1L;
                    activeSupportNormal = Vec3.ZERO;
                    postImpactMotion = true;
                    ignoredTouchingTriangleId = -1L;
                } else {
                    double impulse = 0.0;
                    double outgoing = 0.0;
                    boolean connectedWalkableTransition = supports
                            && body.supportTriangleId >= 0L
                            && body.supportTriangleId != selection.triangle.id
                            && terrain.isWalkableTransition(
                            body.supportTriangleId, selection.triangle.id,
                            config.supportSlopeCosine);
                    if (normalVelocity < 0.0) {
                        double impactSpeed = -normalVelocity;
                        boolean wouldBounce = !connectedWalkableTransition
                                && impactSpeed >= config.bounceSpeedThreshold
                                && config.restitution > 0.0;
                        outgoing = wouldBounce && !impactBrake
                                ? impactSpeed * config.restitution : 0.0;
                        double deltaVelocity = outgoing - normalVelocity;
                        body.velocity = body.velocity.add(
                                hit.normal.multiply(deltaVelocity));
                        impulse = deltaVelocity * config.mass;
                        if (impactBrake) {
                            clearGestureRequest(body);
                            // The downward intent has been consumed, but the physical touch has
                            // not. Begin a fresh held-charge accumulator so reversing into an
                            // upward swipe works without requiring TOUCH_UP + TOUCH_DOWN first.
                            body.gestureConsumed = false;
                            if (wouldBounce && allowLandingRules) {
                                events.add(new SimulationEvent(
                                        SimulationEvent.Type.BOUNCE_SUPPRESSED,
                                        selection.triangle.id,
                                        "held downward swipe absorbed impact speed="
                                                + impactSpeed,
                                        eventTimeNanos(body, hitTickFraction),
                                        body.position, hitTickFraction));
                            }
                        } else if (outgoing > 0.0) {
                            snapHorizontalVelocity(body);
                            if (allowLandingRules) {
                                events.add(new SimulationEvent(
                                        SimulationEvent.Type.BOUNCE,
                                        selection.triangle.id,
                                        "impact speed=" + impactSpeed,
                                        eventTimeNanos(body, hitTickFraction),
                                        body.position, hitTickFraction));
                            }
                        }
                    }

                    boolean restingSupport = supports && outgoing == 0.0
                            && body.velocity.dot(hit.normal) <= 1.0e-9;
                    boolean firstStableLanding = restingSupport
                            && !startedGrounded && outcome.landingPosition == null;
                    if (restingSupport) {
                        if (firstStableLanding) {
                            snapHorizontalVelocity(body);
                        }
                        motionSupported = true;
                        supportThisSubstep = true;
                        activeSupportId = selection.triangle.id;
                        activeSupportNormal = hit.normal;
                        body.supportTriangleId = selection.triangle.id;
                        body.supportNormal = hit.normal;
                        ignoredTouchingTriangleId = selection.triangle.id;
                        outcome.landed = true;
                        double tractionDt = subDt * (1.0 - hitLocalFraction);
                        if (tractionDt > 0.0) {
                            applyRollingTraction(body, heading, hit.normal,
                                    selection.triangle.surface, tractionDt);
                            tractionApplied = true;
                        }
                        syncRollRateToSupport(
                                body, hit.normal, spinSegments,
                                hitTickFraction, selection.triangle.id,
                                firstStableLanding);
                    } else if (outgoing > 0.0) {
                        motionSupported = false;
                        supportThisSubstep = false;
                        activeSupportId = -1L;
                        activeSupportNormal = Vec3.ZERO;
                        body.grounded = false;
                        postImpactMotion = true;
                        ignoredTouchingTriangleId = -1L;
                    }
                    if (firstStableLanding) {
                        outcome.landingTriangleId = selection.triangle.id;
                        outcome.landingPosition = body.position;
                        outcome.landingTickFraction = hitTickFraction;
                    }
                    if (contacts != null) {
                        contacts.add(new ContactSnapshot(
                                selection.triangle.id, hit.terrainPoint, hit.normal,
                                0.0, impulse, attempted, body.position,
                                hit.signedSeparation, hitTickFraction,
                                preVelocity, body.velocity,
                                preAngularVelocity, body.angularVelocity,
                                hit.feature.name(),
                                hit.iterations, ContactSnapshot.TimingQuality.SWEPT_TOI));
                    }
                }

                if (outcome.landingJumped && contacts != null) {
                    contacts.add(new ContactSnapshot(
                            selection.triangle.id, hit.terrainPoint, hit.normal,
                            0.0, 0.0, attempted, body.position,
                            hit.signedSeparation, hitTickFraction,
                            preVelocity, body.velocity,
                            preAngularVelocity, body.angularVelocity,
                            hit.feature.name(),
                            hit.iterations, ContactSnapshot.TimingQuality.SWEPT_TOI));
                }
                localElapsed = hitLocalFraction;
                if (hit.fraction <= 1.0e-12
                        && body.velocity.subtract(preVelocity).lengthSquared()
                        <= 1.0e-24) {
                    // A touching, non-blocking contact cannot consume time. The endpoint support
                    // query below will retain it without spinning this impact loop.
                    body.position = attempted;
                    MotionSegment.Phase phase = (motionSupported || supportThisSubstep)
                            ? MotionSegment.Phase.SUPPORTED
                            : MotionSegment.Phase.POST_IMPACT;
                    recordResolvedMotion(
                            body, outcome, motionSegments,
                            spinSegments,
                            hitTickFraction, (substep + 1.0) / substeps,
                            hit.centerAtImpact, attempted, phase,
                            activeSupportId, activeSupportNormal);
                    localElapsed = 1.0;
                }
            }

            if (outcome.aborted) {
                break;
            }

            SupportSelection support = findRestingSupport(
                    body, substepStartPosition, queried);
            if (!outcome.landingJumped && support != null) {
                if (support.contact.penetration > 0.0) {
                    body.position = body.position.add(
                            support.contact.normal.multiply(support.contact.penetration));
                }
                motionSupported = true;
                supportThisSubstep = true;
                activeSupportId = support.triangle.id;
                activeSupportNormal = support.contact.normal;
                body.supportTriangleId = support.triangle.id;
                body.supportNormal = support.contact.normal;
                outcome.landed = true;
                outcome.supportTriangleId = support.triangle.id;
                if (!tractionApplied) {
                    applyRollingTraction(body, heading, support.contact.normal,
                            support.triangle.surface, subDt);
                }
                boolean firstEndpointLanding =
                        !startedGrounded && outcome.landingPosition == null;
                if (firstEndpointLanding) {
                    snapHorizontalVelocity(body);
                    outcome.landingTriangleId = support.triangle.id;
                    outcome.landingPosition = body.position;
                    outcome.landingTickFraction = (substep + 1.0) / substeps;
                }
                syncRollRateToSupport(
                        body, support.contact.normal, spinSegments,
                        (substep + 1.0) / substeps, support.triangle.id,
                        firstEndpointLanding);
            }
            body.grounded = !outcome.landingJumped && supportThisSubstep;
            if (!body.grounded) {
                body.supportTriangleId = -1L;
                body.supportNormal = Vec3.ZERO;
                carriedSupport = false;
                carriedSupportId = -1L;
                carriedSupportNormal = Vec3.ZERO;
            } else {
                carriedSupport = true;
                carriedSupportId = activeSupportId;
                carriedSupportNormal = activeSupportNormal;
            }

            long spikeId = findOverlappingSpike(body);
            if (spikeId >= 0L) {
                outcome.spikeId = spikeId;
                outcome.spikePosition = body.position;
                outcome.spikeTickFraction = (substep + 1.0) / substeps;
                break;
            }
        }
        finishRollTick(body, outcome, spinSegments);
        return outcome;
    }

    private void applyRollingTraction(PlayerBodyState body, Vec3 heading, Vec3 normal,
                                      SurfaceProperties surface, double dt) {
        Vec3 alongSurface = heading.subtract(normal.multiply(heading.dot(normal))).normalized();
        double surfaceSpeed = body.driveAngularVelocity * config.cylinderRadius
                * surface.motorSpeedMultiplier;
        Vec3 desiredTangent = alongSurface.multiply(surfaceSpeed);
        double normalComponent = body.velocity.dot(normal);
        Vec3 tangent = body.velocity.subtract(normal.multiply(normalComponent));
        Vec3 correction = desiredTangent.subtract(tangent);
        double maxCorrection = config.groundFrictionAcceleration * dt;
        if (correction.length() > maxCorrection) {
            correction = correction.normalized().multiply(maxCorrection);
        }
        body.velocity = body.velocity.add(correction);
    }

    private SweepSelection earliestSweep(
            Vec3 start, Vec3 attempted, Vec3 axis,
            List<TerrainTriangle> queried, long ignoredTriangleId) {
        Aabb sweptBounds = CylinderCollider.bounds(
                start, axis, config.cylinderHalfLength, config.cylinderRadius)
                .union(CylinderCollider.bounds(
                        attempted, axis, config.cylinderHalfLength, config.cylinderRadius))
                .expanded(config.contactOffset * 2.0);
        queryNearbyTriangles(sweptBounds, triangleQueryScratch);
        List<TerrainTriangle> candidates = triangleQueryScratch;
        appendUnique(queried, candidates);
        Vec3 translation = attempted.subtract(start);
        SweepSelection best = null;
        for (int i = 0; i < candidates.size(); i++) {
            TerrainTriangle triangle = candidates.get(i);
            if (triangle.id == ignoredTriangleId) {
                continue;
            }
            CylinderTriangleCast.SweepHit hit = CylinderTriangleCast.cast(
                    start, translation, axis,
                    config.cylinderHalfLength, config.cylinderRadius,
                    triangle, terrain.collisionBoundaryMask(triangle.id),
                    config.toiTolerance);
            if (hit.status == CylinderTriangleCast.Status.FAILED) {
                return new SweepSelection(triangle, hit, true);
            }
            if (hit.status != CylinderTriangleCast.Status.HIT
                    && hit.status != CylinderTriangleCast.Status.START_OVERLAPPED) {
                continue;
            }
            if (best == null
                    || hit.fraction < best.hit.fraction - 1.0e-10
                    || (Math.abs(hit.fraction - best.hit.fraction) <= 1.0e-10
                    && triangle.id < best.triangle.id)) {
                best = new SweepSelection(triangle, hit, false);
            }
        }
        return best;
    }

    private SupportSelection findRestingSupport(
            PlayerBodyState body, Vec3 previousPosition,
            List<TerrainTriangle> queried) {
        queryNearbyTriangles(
                CylinderCollider.bounds(body.position, body.axis(),
                        config.cylinderHalfLength, config.cylinderRadius)
                        .expanded(config.contactOffset * 2.0),
                triangleQueryScratch);
        List<TerrainTriangle> candidates = triangleQueryScratch;
        appendUnique(queried, candidates);
        SupportSelection best = null;
        for (int i = 0; i < candidates.size(); i++) {
            TerrainTriangle triangle = candidates.get(i);
            CylinderCollider.ContactCandidate contact = CylinderCollider.contact(
                    previousPosition, body.position, body.axis(), config, triangle,
                    terrain.collisionBoundaryMask(triangle.id));
            if (contact == null
                    || contact.normal.y < config.supportSlopeCosine
                    || body.velocity.dot(contact.normal) > 1.0e-9) {
                continue;
            }
            if (best == null
                    || contact.normal.y > best.contact.normal.y + 1.0e-12
                    || (Math.abs(contact.normal.y - best.contact.normal.y) <= 1.0e-12
                    && triangle.id < best.triangle.id)) {
                best = new SupportSelection(triangle, contact);
            }
        }
        return best;
    }

    private void queryNearbyTriangles(
            Aabb exactBounds, List<TerrainTriangle> destination) {
        if (triangleNeighborhoodBounds == null
                || !triangleNeighborhoodBounds.contains(exactBounds)) {
            /*
             * Four ordinary 120 Hz cruising steps are roughly one world unit. This normally keeps
             * the support segment and its immediate neighbors resident while remaining small
             * enough that the exact filter below tests only a few triangles.
             */
            double travelPadding = commandedCruisingSpeed
                    * PhysicsConfig.FIXED_DT_SECONDS * 4.0;
            double shapePadding = config.cylinderRadius * 4.0;
            triangleNeighborhoodBounds = exactBounds.expanded(
                    Math.max(travelPadding, shapePadding));
            terrain.queryTriangles(
                    triangleNeighborhoodBounds, triangleNeighborhoodScratch);
        }

        destination.clear();
        for (int i = 0; i < triangleNeighborhoodScratch.size(); i++) {
            TerrainTriangle triangle = triangleNeighborhoodScratch.get(i);
            if (triangle.bounds.intersects(exactBounds)) {
                destination.add(triangle);
            }
        }
    }

    private void invalidateTriangleNeighborhood() {
        triangleNeighborhoodBounds = null;
        triangleNeighborhoodScratch.clear();
        triangleQueryScratch.clear();
    }

    private void recordResolvedMotion(
            PlayerBodyState body, PhysicsOutcome outcome,
            List<MotionSegment> motionSegments,
            List<SpinSegment> spinSegments,
            double startFraction, double endFraction,
            Vec3 start, Vec3 end, MotionSegment.Phase phase,
            long supportTriangleId, Vec3 supportNormal) {
        addMotionSegment(
                motionSegments, startFraction, endFraction, start, end, phase);

        double clampedStart = clamp01(startFraction);
        double clampedEnd = Math.max(clampedStart, clamp01(endFraction));
        if (clampedStart > outcome.rollCoveredFraction + 1.0e-12) {
            double gapStartOmega = body.angularVelocity;
            double gapStartDelta = outcome.axleDeltaRadians;
            advanceAirborneRoll(
                    body, outcome,
                    (clampedStart - outcome.rollCoveredFraction)
                            * PhysicsConfig.FIXED_DT_SECONDS);
            addSpinSegment(
                    spinSegments,
                    outcome.rollCoveredFraction, clampedStart,
                    SpinSegment.Mode.AIR_MOTOR,
                    outcome.axleDeltaRadians - gapStartDelta,
                    gapStartOmega, body.angularVelocity,
                    0.0, -1L, Vec3.ZERO);
        }
        if (clampedEnd <= clampedStart + 1.0e-15) {
            outcome.rollCoveredFraction =
                    Math.max(outcome.rollCoveredFraction, clampedEnd);
            return;
        }

        double startOmega = body.angularVelocity;
        double startDelta = outcome.axleDeltaRadians;
        double signedDistance = 0.0;
        SpinSegment.Mode spinMode = SpinSegment.Mode.AIR_MOTOR;
        if (phase == MotionSegment.Phase.SUPPORTED
                && supportTriangleId >= 0L
                && supportNormal != null
                && supportNormal.lengthSquared() > 1.0e-18) {
            Vec3 tangent = rollingTangent(body, supportNormal);
            signedDistance = end.subtract(start).dot(tangent);
            outcome.axleDeltaRadians -= signedDistance / config.cylinderRadius;
            body.angularVelocity =
                    -body.velocity.dot(tangent) / config.cylinderRadius;
            spinMode = SpinSegment.Mode.SUPPORTED_ROLL;
        } else {
            advanceAirborneRoll(
                    body, outcome,
                    (clampedEnd - clampedStart) * PhysicsConfig.FIXED_DT_SECONDS);
        }
        addSpinSegment(
                spinSegments, clampedStart, clampedEnd, spinMode,
                outcome.axleDeltaRadians - startDelta,
                startOmega, body.angularVelocity, signedDistance,
                spinMode == SpinSegment.Mode.SUPPORTED_ROLL
                        ? supportTriangleId : -1L,
                spinMode == SpinSegment.Mode.SUPPORTED_ROLL
                        ? supportNormal : Vec3.ZERO);
        outcome.rollCoveredFraction =
                Math.max(outcome.rollCoveredFraction, clampedEnd);
    }

    /**
     * Applies the intentionally motor-driven airborne spin rule with exact integration for the
     * constant angular acceleration used while approaching the commanded rate.
     */
    private void advanceAirborneRoll(
            PlayerBodyState body, PhysicsOutcome outcome, double durationSeconds) {
        if (durationSeconds <= 0.0) {
            return;
        }
        double startOmega = body.angularVelocity;
        double targetOmega = -commandedCruisingSpeed / config.cylinderRadius;
        double difference = targetOmega - startOmega;
        double acceleration = config.airAngularAcceleration;
        double accelerationDirection = Math.signum(difference);
        double timeToTarget = Math.abs(difference) / acceleration;
        double acceleratingSeconds = Math.min(durationSeconds, timeToTarget);
        double reachedOmega = startOmega
                + accelerationDirection * acceleration * acceleratingSeconds;
        outcome.axleDeltaRadians +=
                (startOmega + reachedOmega) * 0.5 * acceleratingSeconds;
        if (durationSeconds > acceleratingSeconds) {
            outcome.axleDeltaRadians +=
                    targetOmega * (durationSeconds - acceleratingSeconds);
            reachedOmega = targetOmega;
        }
        body.angularVelocity = reachedOmega;
    }

    private void syncRollRateToSupport(PlayerBodyState body, Vec3 supportNormal) {
        syncRollRateToSupport(
                body, supportNormal, null, 1.0,
                body.supportTriangleId, false);
    }

    private void syncRollRateToSupport(
            PlayerBodyState body, Vec3 supportNormal,
            List<SpinSegment> spinSegments, double tickFraction,
            long supportTriangleId, boolean recordLandingSnap) {
        if (supportNormal == null || supportNormal.lengthSquared() <= 1.0e-18) {
            return;
        }
        double previousAngularVelocity = body.angularVelocity;
        Vec3 tangent = rollingTangent(body, supportNormal);
        body.angularVelocity =
                -body.velocity.dot(tangent) / config.cylinderRadius;
        if (recordLandingSnap) {
            addSpinSegment(
                    spinSegments, tickFraction, tickFraction,
                    SpinSegment.Mode.LANDING_SNAP, 0.0,
                    previousAngularVelocity, body.angularVelocity,
                    0.0, supportTriangleId, supportNormal);
        }
    }

    private Vec3 rollingTangent(PlayerBodyState body, Vec3 supportNormal) {
        Vec3 tangent = supportNormal.cross(body.axis()).normalized();
        if (tangent.dot(body.heading()) < 0.0) {
            tangent = tangent.multiply(-1.0);
        }
        return tangent;
    }

    private void finishRollTick(
            PlayerBodyState body, PhysicsOutcome outcome,
            List<SpinSegment> spinSegments) {
        if (outcome.rollCoveredFraction < 1.0 - 1.0e-12) {
            double startFraction = outcome.rollCoveredFraction;
            double remainingSeconds =
                    (1.0 - startFraction)
                            * PhysicsConfig.FIXED_DT_SECONDS;
            if (!body.grounded) {
                double startOmega = body.angularVelocity;
                double startDelta = outcome.axleDeltaRadians;
                advanceAirborneRoll(body, outcome, remainingSeconds);
                addSpinSegment(
                        spinSegments, startFraction, 1.0,
                        SpinSegment.Mode.AIR_MOTOR,
                        outcome.axleDeltaRadians - startDelta,
                        startOmega, body.angularVelocity,
                        0.0, -1L, Vec3.ZERO);
            }
        }
        if (body.grounded) {
            syncRollRateToSupport(body, body.supportNormal);
        }
        body.axleDeltaRadians = outcome.axleDeltaRadians;
        body.axleRadians =
                wrapRadians(body.axleRadians + body.axleDeltaRadians);
    }

    private static void addSpinSegment(
            List<SpinSegment> segments,
            double startFraction, double endFraction,
            SpinSegment.Mode mode, double deltaRadians,
            double startAngularVelocity, double endAngularVelocity,
            double signedDistance,
            long supportTriangleId, Vec3 supportNormal) {
        if (segments == null) {
            return;
        }
        segments.add(new SpinSegment(
                clamp01(startFraction), clamp01(endFraction), mode,
                deltaRadians, startAngularVelocity, endAngularVelocity,
                signedDistance, supportTriangleId, supportNormal));
    }

    private static void addMotionSegment(
            List<MotionSegment> segments,
            double startFraction, double endFraction,
            Vec3 start, Vec3 end, MotionSegment.Phase phase) {
        if (segments == null) {
            return;
        }
        double clampedStart = clamp01(startFraction);
        double clampedEnd = clamp01(endFraction);
        if (clampedEnd < clampedStart) {
            clampedEnd = clampedStart;
        }
        if (clampedEnd == clampedStart
                && start.subtract(end).lengthSquared() <= 1.0e-24) {
            return;
        }
        segments.add(new MotionSegment(
                clampedStart, clampedEnd, start, end, phase));
    }

    private static void completeMotionPath(
            List<MotionSegment> segments, Vec3 tickStart, Vec3 tickEnd) {
        if (segments == null) {
            return;
        }
        if (segments.isEmpty()) {
            segments.add(new MotionSegment(0.0, 1.0,
                    tickStart, tickEnd, MotionSegment.Phase.FREE_FLIGHT));
            return;
        }
        MotionSegment first = segments.get(0);
        if (first.startFraction > 0.0) {
            segments.add(0, new MotionSegment(0.0, first.startFraction,
                    tickStart, first.startPosition, first.phase));
        }
        MotionSegment last = segments.get(segments.size() - 1);
        if (last.endFraction < 1.0
                || last.endPosition.subtract(tickEnd).lengthSquared() > 1.0e-24) {
            segments.add(new MotionSegment(last.endFraction, 1.0,
                    last.endPosition, tickEnd, last.phase));
        }
    }

    private ForecastResult forecastWithoutJump() {
        if (safeSupportForecastCache != null) {
            if (safeSupportForecastCache.matchesNext(state)) {
                safeSupportForecastCache.advance();
                return ForecastResult.SAFE_SUPPORT_FIRST;
            }
            invalidateSafeSupportForecast();
        }
        PlayerBodyState forecast = state.copy();
        forecast.bufferedAirborneRequest = false;
        forecast.landingJumpArmed = false;
        forecast.impactBrakeArmed = false;
        forecastJumpScratch.clear();
        forecastEventScratch.clear();
        ArrayList<NoJumpTrajectoryCheckpoint> safeTrajectory =
                new ArrayList<NoJumpTrajectoryCheckpoint>();
        for (int i = 0; i < config.landingJumpBufferTicks; i++) {
            PhysicsOutcome outcome = advancePhysics(forecast, false,
                    null, null, null, null,
                    forecastJumpScratch, forecastEventScratch);
            if (outcome.spikeId >= 0L) {
                return ForecastResult.SPIKE_FIRST;
            }
            // A walkable impact is the relevant landing-jump opportunity even when its speed
            // would normally produce restitution instead of immediately settling as support.
            if (outcome.encounteredSupport || outcome.landed || forecast.grounded) {
                if (!safeTrajectory.isEmpty()) {
                    safeSupportForecastCache =
                            new SafeSupportForecastCache(safeTrajectory);
                }
                return ForecastResult.SAFE_SUPPORT_FIRST;
            }
            if (forecast.position.y < deathFloor(forecast)) {
                return ForecastResult.NO_RECOVERABLE_SUPPORT;
            }
            safeTrajectory.add(new NoJumpTrajectoryCheckpoint(forecast));
        }
        return ForecastResult.NO_RECOVERABLE_SUPPORT;
    }

    private void invalidateSafeSupportForecast() {
        safeSupportForecastCache = null;
    }

    private double deathFloor(PlayerBodyState body) {
        return body.hasSupportedAnchor
                ? body.lastSupportedY + config.deathY
                : config.deathY;
    }

    private long findOverlappingSpike(PlayerBodyState body) {
        Aabb bodyBounds = CylinderCollider.bounds(body.position, body.axis(),
                config.cylinderHalfLength, config.cylinderRadius);
        terrain.queryAddons(bodyBounds, addonQueryScratch);
        AddonContactContext context = new AddonContactContext(
                body.position, body.axis(), config.cylinderRadius,
                config.cylinderHalfLength, bodyBounds);
        ContactProbeSink sink = new ContactProbeSink();
        for (int i = 0; i < addonQueryScratch.size(); i++) {
            Addon addon = addonQueryScratch.get(i);
            if (addon.contactPhase() != Addon.ContactPhase.HAZARD) {
                continue;
            }
            sink.clear();
            addon.evaluateContact(context, sink);
            if (sink.hazardId >= 0L) {
                return sink.hazardId;
            }
        }
        return -1L;
    }

    private void collectPickups(List<SimulationEvent> events) {
        Aabb bodyBounds = CylinderCollider.bounds(state.position, state.axis(),
                config.cylinderHalfLength, config.cylinderRadius);
        terrain.queryAddons(
                bodyBounds.expanded(config.cylinderRadius), addonQueryScratch);
        AddonContactContext context = new AddonContactContext(
                state.position, state.axis(), config.cylinderRadius,
                config.cylinderHalfLength, bodyBounds);
        ContactProbeSink sink = new ContactProbeSink();
        for (int i = 0; i < addonQueryScratch.size(); i++) {
            Addon addon = addonQueryScratch.get(i);
            if (addon.contactPhase() != Addon.ContactPhase.PICKUP
                    || state.inactiveAddonIds.contains(addon.id())) {
                continue;
            }
            sink.clear();
            addon.evaluateContact(context, sink);
            if (sink.pickupId >= 0L) {
                state.inactiveAddonIds.add(sink.pickupId);
                addonActivityDirty = true;
                state.airJumpCharges += sink.airJumpCharges;
                invalidateSafeSupportForecast();
                events.add(new SimulationEvent(SimulationEvent.Type.FEATHER_COLLECTED,
                        sink.pickupId, "air jump charges=" + state.airJumpCharges,
                        eventTimeNanos(state, 1.0), state.position, 1.0));
            }
        }
    }

    private void executeJump(JumpDecision decision, List<SimulationEvent> events) {
        executeJump(state, decision, events, 0.0);
    }

    private void executeJump(PlayerBodyState body, JumpDecision decision,
                             List<SimulationEvent> events, double tickFraction) {
        if (body == state) {
            invalidateSafeSupportForecast();
        }
        if (decision.consumesAirCharge) {
            if (body.airJumpCharges <= 0) {
                throw new IllegalStateException("Air jump selected without a charge");
            }
            body.airJumpCharges--;
        }
        if (usesFacingLaunchDirection(body, decision)) {
            snapHorizontalVelocity(body);
        }
        Vec3 heading = body.heading();
        double forwardBoost = config.jumpForwardBoostSpeed;
        body.velocity = new Vec3(
                body.velocity.x + heading.x * forwardBoost,
                config.jumpSpeed,
                body.velocity.z + heading.z * forwardBoost);
        body.grounded = false;
        body.supportTriangleId = -1L;
        body.supportNormal = Vec3.ZERO;
        body.gestureCharge = 0.0;
        resetGestureClassifier(body);
        body.gestureConsumed = true;
        body.bufferedAirborneRequest = false;
        body.landingJumpArmed = false;
        body.impactBrakeArmed = false;
        body.airborneReleaseNanos = -1L;
        body.airborneReleaseCharge = 0.0;
        long jumpTimeNanos = eventTimeNanos(body, tickFraction);
        body.jumpCooldownUntilNanos = jumpTimeNanos + config.jumpCooldownNanos;
        events.add(new SimulationEvent(SimulationEvent.Type.JUMP, -1L,
                decision.rule.name(), jumpTimeNanos,
                body.position, tickFraction));
    }

    private static boolean usesFacingLaunchDirection(
            PlayerBodyState body, JumpDecision decision) {
        return !body.grounded
                || decision.rule == JumpRuleId.LANDING_CHARGED;
    }

    private void clearGestureRequest() {
        clearGestureRequest(state);
    }

    private void clearGestureRequest(PlayerBodyState body) {
        if (body == state) {
            invalidateSafeSupportForecast();
        }
        body.gestureCharge = 0.0;
        resetGestureClassifier(body);
        body.bufferedAirborneRequest = false;
        body.landingJumpArmed = false;
        body.impactBrakeArmed = false;
        body.airborneReleaseNanos = -1L;
        body.airborneReleaseCharge = 0.0;
    }

    /**
     * Physics runs after jump-rule evaluation, so a later rebound must not expose one stale
     * rising/grounded "armed" frame to gameplay or the HUD. Landing buffers are descending-only.
     */
    private void enforceFallingOnlyLandingJumpArm() {
        if (!state.landingJumpArmed) {
            return;
        }
        boolean valid = !state.grounded
                && state.velocity.y < 0.0
                && !state.impactBrakeArmed
                && !state.dead
                && charged();
        if (valid) {
            return;
        }
        state.landingJumpArmed = false;
        // Without an air-jump charge there is no valid action to retain during a rebound.
        // Clearing now avoids a one-frame flash of the released charge before next tick rejects it.
        if (!state.grounded && state.velocity.y >= 0.0
                && state.airJumpCharges <= 0) {
            clearGestureRequest();
        }
    }

    private static void retainReleasedJumpRequest(PlayerBodyState body) {
        body.bufferedAirborneRequest = true;
        body.airborneReleaseNanos = body.timeNanos;
        body.airborneReleaseCharge = body.gestureCharge;
    }

    private static boolean jumpCooldownReadyAt(PlayerBodyState body, long timeNanos) {
        return timeNanos >= body.jumpCooldownUntilNanos;
    }

    private static long jumpCooldownRemainingNanosAt(
            PlayerBodyState body, long timeNanos) {
        return Math.max(0L, body.jumpCooldownUntilNanos - timeNanos);
    }

    private static String cooldownReason(PlayerBodyState body, long timeNanos) {
        double remainingMillis = jumpCooldownRemainingNanosAt(body, timeNanos)
                / 1_000_000.0;
        return "post-jump cooldown has " + remainingMillis + " ms remaining";
    }

    private static void resetGestureClassifier(PlayerBodyState body) {
        body.gestureChargePotential = 0.0;
        body.gestureRawDeltaX = 0.0;
        body.gestureRawUpwardDistance = 0.0;
        body.gestureMaxAbsRawDeltaX = 0.0;
        body.gestureLastSwipeChargeEligible = false;
        body.heldChargeLastContributionNanos = -1L;
    }

    private boolean charged() {
        return charged(state);
    }

    private boolean charged(PlayerBodyState body) {
        return body.gestureCharge + 1.0e-12 >= config.jumpChargeThreshold;
    }

    private void snapHorizontalVelocity(PlayerBodyState body) {
        double horizontalSpeed = Math.sqrt(
                body.velocity.x * body.velocity.x + body.velocity.z * body.velocity.z);
        Vec3 heading = body.heading();
        body.velocity = new Vec3(heading.x * horizontalSpeed,
                body.velocity.y, heading.z * horizontalSpeed);
    }

    private void updateRenderOrigin() {
        Vec3 local = state.position.subtract(state.worldOrigin);
        if (Math.abs(local.x) > 500.0
                || Math.abs(local.y) > 500.0
                || Math.abs(local.z) > 500.0) {
            state.worldOrigin = new Vec3(
                    Math.abs(local.x) > 500.0
                            ? state.position.x : state.worldOrigin.x,
                    Math.abs(local.y) > 500.0
                            ? state.position.y : state.worldOrigin.y,
                    Math.abs(local.z) > 500.0
                            ? state.position.z : state.worldOrigin.z);
        }
    }

    private void validate(List<SimulationEvent> events) {
        boolean valid = finite(state.position) && finite(state.velocity)
                && finite(state.supportNormal)
                && Double.isFinite(state.axleRadians)
                && Double.isFinite(state.axleDeltaRadians)
                && Double.isFinite(state.angularVelocity)
                && Double.isFinite(state.driveAngularVelocity)
                && state.gestureCharge >= -1.0e-9 && state.gestureCharge <= 1.0 + 1.0e-9
                && state.gestureChargePotential >= -1.0e-9
                && state.gestureChargePotential <= 1.0 + 1.0e-9
                && Double.isFinite(state.gestureRawDeltaX)
                && Double.isFinite(state.gestureRawUpwardDistance)
                && state.gestureRawUpwardDistance >= -1.0e-12
                && Double.isFinite(state.gestureMaxAbsRawDeltaX)
                && state.gestureMaxAbsRawDeltaX >= -1.0e-12
                && state.heldChargeLastContributionNanos >= -1L
                && state.airJumpCharges >= 0
                && (!state.landingJumpArmed
                || (!state.grounded
                && state.velocity.y < 0.0
                && !state.impactBrakeArmed
                && !state.dead
                && charged()));
        if (!valid) {
            events.add(new SimulationEvent(SimulationEvent.Type.INVARIANT_FAILURE, -1L,
                    "non-finite state or invalid charge"));
            throw new IllegalStateException("Simulation invariant failed at tick " + state.tick);
        }
    }

    private long stateHash() {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, state.tick);
        hash = mix(hash, state.timeNanos);
        hash = mix(hash, quantize(state.position.x));
        hash = mix(hash, quantize(state.position.y));
        hash = mix(hash, quantize(state.position.z));
        hash = mix(hash, quantize(state.worldOrigin.x));
        hash = mix(hash, quantize(state.worldOrigin.y));
        hash = mix(hash, quantize(state.worldOrigin.z));
        hash = mix(hash, quantize(state.velocity.x));
        hash = mix(hash, quantize(state.velocity.y));
        hash = mix(hash, quantize(state.velocity.z));
        hash = mix(hash, quantize(state.yawRadians));
        hash = mix(hash, quantize(state.axleRadians));
        hash = mix(hash, quantize(state.axleDeltaRadians));
        hash = mix(hash, quantize(state.angularVelocity));
        hash = mix(hash, quantize(state.driveAngularVelocity));
        hash = mix(hash, quantize(state.gestureCharge));
        hash = mix(hash, quantize(state.gestureChargePotential));
        hash = mix(hash, quantize(state.gestureRawDeltaX));
        hash = mix(hash, quantize(state.gestureRawUpwardDistance));
        hash = mix(hash, quantize(state.gestureMaxAbsRawDeltaX));
        hash = mix(hash, state.gestureLastSwipeChargeEligible ? 1L : 0L);
        hash = mix(hash, state.heldChargeLastContributionNanos);
        hash = mix(hash, state.airJumpCharges);
        hash = mix(hash, state.grounded ? 1L : 0L);
        hash = mix(hash, state.supportTriangleId);
        hash = mix(hash, state.supportSegmentId);
        hash = mix(hash, state.lastSupportedSegmentId);
        hash = mix(hash, quantize(state.supportNormal.x));
        hash = mix(hash, quantize(state.supportNormal.y));
        hash = mix(hash, quantize(state.supportNormal.z));
        hash = mix(hash, state.hasSupportedAnchor ? 1L : 0L);
        hash = mix(hash, quantize(state.lastSupportedY));
        hash = mix(hash, quantize(commandedCruisingSpeed));
        hash = mix(hash, state.touchHeld ? 1L : 0L);
        hash = mix(hash, state.gestureConsumed ? 1L : 0L);
        hash = mix(hash, state.dead ? 1L : 0L);
        hash = mix(hash, state.bufferedAirborneRequest ? 1L : 0L);
        hash = mix(hash, state.landingJumpArmed ? 1L : 0L);
        hash = mix(hash, state.impactBrakeArmed ? 1L : 0L);
        hash = mix(hash, state.airborneReleaseNanos);
        hash = mix(hash, quantize(state.airborneReleaseCharge));
        hash = mix(hash, state.jumpCooldownUntilNanos);
        ArrayList<Long> inactiveAddonIds =
                new ArrayList<Long>(state.inactiveAddonIds);
        Collections.sort(inactiveAddonIds);
        hash = mix(hash, inactiveAddonIds.size());
        for (Long addonId : inactiveAddonIds) {
            hash = mix(hash, addonId.longValue());
        }
        return hash;
    }

    private void purgeRetiredInactiveAddons(TerrainCollisionIndex index) {
        ArrayList<Long> removed = new ArrayList<Long>();
        for (Long addonId : state.inactiveAddonIds) {
            if (!index.containsAddon(addonId.longValue())) {
                removed.add(addonId);
            }
        }
        if (state.inactiveAddonIds.removeAll(removed)) {
            addonActivityDirty = true;
        }
    }

    private static final class ContactProbeSink implements AddonEffectSink {
        long hazardId = -1L;
        long pickupId = -1L;
        int airJumpCharges;

        void clear() {
            hazardId = -1L;
            pickupId = -1L;
            airJumpCharges = 0;
        }

        @Override
        public void hitHazard(long addonId) {
            hazardId = addonId;
        }

        @Override
        public void grantAirJump(long addonId, int charges) {
            pickupId = addonId;
            airJumpCharges = charges;
        }
    }

    private void clearSupport() {
        state.grounded = false;
        state.supportTriangleId = -1L;
        state.supportSegmentId = -1L;
        state.supportNormal = Vec3.ZERO;
    }

    private static long mix(long hash, long value) {
        hash ^= value;
        return hash * 0x100000001b3L;
    }

    private static long quantize(double value) {
        return Math.round(value * 1_000_000_000.0);
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static double approach(double value, double target, double maxDelta) {
        if (value < target) {
            return Math.min(target, value + maxDelta);
        }
        return Math.max(target, value - maxDelta);
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long eventTimeNanos(PlayerBodyState body, double tickFraction) {
        return body.timeNanos
                + Math.round(PhysicsConfig.FIXED_DT_NANOS * tickFraction);
    }

    private static double wrapRadians(double value) {
        double twoPi = Math.PI * 2.0;
        value %= twoPi;
        if (value > Math.PI) {
            value -= twoPi;
        } else if (value < -Math.PI) {
            value += twoPi;
        }
        return value;
    }

    private static JumpDecision addDecision(
            List<JumpDecision> evaluations, JumpRuleId rule,
            JumpDecision.Action action, boolean consumesCharge, String reason) {
        JumpDecision decision = new JumpDecision(rule, action, consumesCharge, reason);
        evaluations.add(decision);
        return decision;
    }

    private static JumpDecision lastDecision(
            List<JumpDecision> decisions, JumpDecision fallback) {
        return decisions.isEmpty() ? fallback : decisions.get(decisions.size() - 1);
    }

    private static void appendUnique(List<TerrainTriangle> destination,
                                     List<TerrainTriangle> candidates) {
        if (destination == null) {
            return;
        }
        for (int candidateIndex = 0; candidateIndex < candidates.size();
             candidateIndex++) {
            TerrainTriangle candidate = candidates.get(candidateIndex);
            boolean present = false;
            for (int existingIndex = 0; existingIndex < destination.size();
                 existingIndex++) {
                if (destination.get(existingIndex).id == candidate.id) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                destination.add(candidate);
            }
        }
    }

    private static void sortEvents(List<SimulationEvent> events) {
        Collections.sort(events, new java.util.Comparator<SimulationEvent>() {
            @Override
            public int compare(SimulationEvent left, SimulationEvent right) {
                double leftFraction = Double.isNaN(left.tickFraction)
                        ? Double.POSITIVE_INFINITY : left.tickFraction;
                double rightFraction = Double.isNaN(right.tickFraction)
                        ? Double.POSITIVE_INFINITY : right.tickFraction;
                return Double.compare(leftFraction, rightFraction);
            }
        });
    }

    private enum ForecastResult {
        SPIKE_FIRST,
        SAFE_SUPPORT_FIRST,
        NO_RECOVERABLE_SUPPORT
    }

    /**
     * A SAFE_SUPPORT_FIRST result is reusable only while the authoritative body visits the exact
     * no-jump states that produced it. External trajectory changes clear the cache eagerly; these
     * checkpoints are an additional bit-exact guard against accidentally reusing a stale suffix.
     */
    private static final class SafeSupportForecastCache {
        final List<NoJumpTrajectoryCheckpoint> trajectory;
        int nextIndex;

        SafeSupportForecastCache(List<NoJumpTrajectoryCheckpoint> trajectory) {
            this.trajectory = trajectory;
        }

        boolean matchesNext(PlayerBodyState body) {
            return nextIndex < trajectory.size()
                    && trajectory.get(nextIndex).matches(body);
        }

        void advance() {
            nextIndex++;
        }
    }

    private static final class NoJumpTrajectoryCheckpoint {
        final Vec3 position;
        final Vec3 velocity;
        final double yawRadians;
        final double axleRadians;
        final double axleDeltaRadians;
        final double driveAngularVelocity;
        final double angularVelocity;
        final int airJumpCharges;
        final boolean grounded;
        final long supportTriangleId;
        final Vec3 supportNormal;
        final boolean hasSupportedAnchor;
        final double lastSupportedY;
        final boolean dead;
        final long jumpCooldownUntilNanos;

        NoJumpTrajectoryCheckpoint(PlayerBodyState body) {
            position = body.position;
            velocity = body.velocity;
            yawRadians = body.yawRadians;
            axleRadians = body.axleRadians;
            axleDeltaRadians = body.axleDeltaRadians;
            driveAngularVelocity = body.driveAngularVelocity;
            angularVelocity = body.angularVelocity;
            airJumpCharges = body.airJumpCharges;
            grounded = body.grounded;
            supportTriangleId = body.supportTriangleId;
            supportNormal = body.supportNormal;
            hasSupportedAnchor = body.hasSupportedAnchor;
            lastSupportedY = body.lastSupportedY;
            dead = body.dead;
            jumpCooldownUntilNanos = body.jumpCooldownUntilNanos;
        }

        boolean matches(PlayerBodyState body) {
            return same(position, body.position)
                    && same(velocity, body.velocity)
                    && same(yawRadians, body.yawRadians)
                    && same(axleRadians, body.axleRadians)
                    && same(axleDeltaRadians, body.axleDeltaRadians)
                    && same(driveAngularVelocity, body.driveAngularVelocity)
                    && same(angularVelocity, body.angularVelocity)
                    && airJumpCharges == body.airJumpCharges
                    && grounded == body.grounded
                    && supportTriangleId == body.supportTriangleId
                    && same(supportNormal, body.supportNormal)
                    && hasSupportedAnchor == body.hasSupportedAnchor
                    && same(lastSupportedY, body.lastSupportedY)
                    && dead == body.dead
                    && jumpCooldownUntilNanos == body.jumpCooldownUntilNanos;
        }

        private static boolean same(Vec3 left, Vec3 right) {
            return same(left.x, right.x)
                    && same(left.y, right.y)
                    && same(left.z, right.z);
        }

        private static boolean same(double left, double right) {
            return Double.doubleToLongBits(left) == Double.doubleToLongBits(right);
        }
    }

    private static final class PhysicsOutcome {
        boolean encounteredSupport;
        boolean landed;
        boolean landingJumped;
        boolean aborted;
        long supportTriangleId = -1L;
        long landingTriangleId = -1L;
        Vec3 landingPosition;
        double landingTickFraction = Double.NaN;
        long spikeId = -1L;
        Vec3 spikePosition;
        double spikeTickFraction = Double.NaN;
        double axleDeltaRadians;
        double rollCoveredFraction;
    }

    private static final class InputOutcome {
        boolean groundedChargedRelease;
        boolean facingChanged;
    }

    private static final class SweepSelection {
        final TerrainTriangle triangle;
        final CylinderTriangleCast.SweepHit hit;
        final boolean failed;

        SweepSelection(TerrainTriangle triangle,
                       CylinderTriangleCast.SweepHit hit, boolean failed) {
            this.triangle = triangle;
            this.hit = hit;
            this.failed = failed;
        }
    }

    private static final class SupportSelection {
        final TerrainTriangle triangle;
        final CylinderCollider.ContactCandidate contact;

        SupportSelection(TerrainTriangle triangle,
                         CylinderCollider.ContactCandidate contact) {
            this.triangle = triangle;
            this.contact = contact;
        }
    }
}
