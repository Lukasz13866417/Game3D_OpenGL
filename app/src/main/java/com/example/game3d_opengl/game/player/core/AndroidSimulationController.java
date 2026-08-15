package com.example.game3d_opengl.game.player.core;

import com.example.game3d.core.input.PlayerInputEvent;
import com.example.game3d.core.input.TimestampedInputQueue;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.simulation.FixedStepAccumulator;
import com.example.game3d.core.simulation.PhysicsConfig;
import com.example.game3d.core.simulation.PlayerSnapshot;
import com.example.game3d.core.simulation.SimulationEngine;
import com.example.game3d.core.simulation.StepObserver;
import com.example.game3d.core.simulation.StepResult;
import com.example.game3d.core.simulation.SimulationFrameSnapshot;
import com.example.game3d.core.terrain.TerrainCommit;
import com.example.game3d.core.terrain.TerrainCollisionIndex;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.core.terrain.TerrainWorld;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Android-facing fixed-clock/input adapter for the shared simulation. It deliberately does not
 * reference OpenGL: callers interpolate snapshots only when drawing.
 */
public final class AndroidSimulationController {
    public interface TickListener {
        void onPhysicsTick(StepResult result);

        void onSimulationOverrun(long retainedNanos);
    }

    private static final int MAX_CATCH_UP_TICKS = 8;

    private final PhysicsConfig config;
    private final SimulationEngine engine;
    private final FixedStepAccumulator clock =
            new FixedStepAccumulator(MAX_CATCH_UP_TICKS);
    private final TimestampedInputQueue inputQueue = new TimestampedInputQueue();
    private final AtomicLong nextInputSequence = new AtomicLong();
    private final long inputEpochNanos;
    private final double screenHeight;
    private final TickListener listener;
    private final ArrayList<PauseInterval> completedPauses =
            new ArrayList<PauseInterval>();
    private long pauseStartedNanos = -1L;
    private PlayerSnapshot previousSnapshot;
    private PlayerSnapshot currentSnapshot;
    private SimulationFrameSnapshot currentFrameSnapshot;

    public AndroidSimulationController(
            TerrainWorld terrain, Vec3 initialPosition, int initialAirJumpCharges,
            int screenHeight, long inputEpochNanos, TickListener listener) {
        if (screenHeight <= 0) {
            throw new IllegalArgumentException("screenHeight must be positive");
        }
        this.config = new PhysicsConfig();
        this.engine = new SimulationEngine(terrain, config, initialPosition,
                initialAirJumpCharges, StepObserver.NONE);
        this.inputEpochNanos = inputEpochNanos;
        this.screenHeight = screenHeight;
        this.listener = listener;
        this.currentSnapshot = engine.snapshot();
        this.previousSnapshot = currentSnapshot;
        this.currentFrameSnapshot = engine.frameSnapshot(currentSnapshot);
    }

    public AndroidSimulationController(
            TerrainSnapshot terrain, Vec3 initialPosition, int initialAirJumpCharges,
            int screenHeight, long inputEpochNanos, TickListener listener) {
        if (screenHeight <= 0) {
            throw new IllegalArgumentException("screenHeight must be positive");
        }
        this.config = new PhysicsConfig();
        this.engine = new SimulationEngine(terrain, config, initialPosition,
                initialAirJumpCharges, StepObserver.NONE);
        this.inputEpochNanos = inputEpochNanos;
        this.screenHeight = screenHeight;
        this.listener = listener;
        this.currentSnapshot = engine.snapshot();
        this.previousSnapshot = currentSnapshot;
        this.currentFrameSnapshot = engine.frameSnapshot(currentSnapshot);
    }

    public AndroidSimulationController(
            TerrainCollisionIndex terrain,
            Vec3 initialPosition,
            int initialAirJumpCharges,
            int screenHeight,
            long inputEpochNanos,
            TickListener listener) {
        if (screenHeight <= 0) {
            throw new IllegalArgumentException("screenHeight must be positive");
        }
        if (terrain == null) {
            throw new IllegalArgumentException("terrain == null");
        }
        this.config = new PhysicsConfig();
        this.engine = new SimulationEngine(
                terrain, config, initialPosition,
                initialAirJumpCharges, StepObserver.NONE);
        this.inputEpochNanos = inputEpochNanos;
        this.screenHeight = screenHeight;
        this.listener = listener;
        this.currentSnapshot = engine.snapshot();
        this.previousSnapshot = currentSnapshot;
        this.currentFrameSnapshot = engine.frameSnapshot(currentSnapshot);
    }

    /**
     * @return whether the event was accepted into the authoritative input timeline
     */
    public synchronized boolean touchDown(long eventTimeNanos) {
        if (rejectPausedInput(eventTimeNanos)) {
            return false;
        }
        inputQueue.enqueue(PlayerInputEvent.down(relativeTime(eventTimeNanos),
                nextInputSequence.getAndIncrement()));
        return true;
    }

    /**
     * @return whether the event was accepted into the authoritative input timeline
     */
    public synchronized boolean touchMove(
            float previousX, float previousY, float x, float y,
            long eventTimeNanos) {
        return touchMoveDelta(
                x - previousX, y - previousY, eventTimeNanos);
    }

    /**
     * @return whether the event was accepted into the authoritative input timeline
     */
    public synchronized boolean touchMoveDelta(
            float deltaX, float deltaY, long eventTimeNanos) {
        return touchMoveDelta(
                deltaX, deltaY, deltaX, deltaY, eventTimeNanos);
    }

    /**
     * Enqueues sensitivity-scaled gameplay motion together with the unscaled physical finger
     * movement used to decide whether that packet is sufficiently vertical to charge a jump.
     */
    public synchronized boolean touchMoveDelta(
            float deltaX, float deltaY,
            float rawDeltaX, float rawDeltaY,
            long eventTimeNanos) {
        if (rejectPausedInput(eventTimeNanos)) {
            return false;
        }
        inputQueue.enqueue(PlayerInputEvent.swipe(relativeTime(eventTimeNanos),
                nextInputSequence.getAndIncrement(),
                deltaX / screenHeight,
                deltaY / screenHeight,
                rawDeltaX / screenHeight,
                rawDeltaY / screenHeight));
        return true;
    }

    /**
     * @return whether the event was accepted into the authoritative input timeline
     */
    public synchronized boolean touchUp(long eventTimeNanos) {
        if (rejectPausedInput(eventTimeNanos)) {
            return false;
        }
        inputQueue.enqueue(PlayerInputEvent.up(relativeTime(eventTimeNanos),
                nextInputSequence.getAndIncrement()));
        return true;
    }

    /**
     * @return whether the event was accepted into the authoritative input timeline
     */
    public synchronized boolean cancelGesture(long eventTimeNanos) {
        if (rejectPausedInput(eventTimeNanos)) {
            return false;
        }
        enqueueGestureCancellation(eventTimeNanos);
        return true;
    }

    private void enqueueGestureCancellation(long eventTimeNanos) {
        inputQueue.enqueue(PlayerInputEvent.cancel(relativeTime(eventTimeNanos),
                nextInputSequence.getAndIncrement()));
    }

    public synchronized FixedStepAccumulator.AdvanceResult advanceFrameMillis(
            float elapsedMillis) {
        long elapsedNanos = elapsedMillis <= 0f
                ? 0L : (long) (elapsedMillis * 1_000_000.0);
        return advanceFrameNanos(elapsedNanos);
    }

    public synchronized FixedStepAccumulator.AdvanceResult advanceFrameNanos(
            long elapsedNanos) {
        if (pauseStartedNanos >= 0L) {
            elapsedNanos = 0L;
        }
        FixedStepAccumulator.AdvanceResult result = clock.advance(
                Math.max(0L, elapsedNanos),
                (tickStart, tickEnd) -> {
                    previousSnapshot = currentSnapshot;
                    StepResult step = engine.step(inputQueue.drain(tickStart, tickEnd));
                    currentSnapshot = step.snapshot;
                    currentFrameSnapshot = engine.frameSnapshot(currentSnapshot);
                    if (listener != null) {
                        listener.onPhysicsTick(step);
                    }
                });
        if (result.overrun && listener != null) {
            listener.onSimulationOverrun(result.retainedNanos);
        }
        return result;
    }

    public synchronized void replaceTerrain(TerrainWorld terrain) {
        if (terrain == null) {
            throw new IllegalArgumentException("terrain == null");
        }
        boolean supportWillRemainValid =
                currentSnapshot.supportTriangleId < 0L
                        || terrain.containsTriangle(
                                currentSnapshot.supportTriangleId);
        engine.replaceTerrain(terrain);
        if (!supportWillRemainValid) {
            currentSnapshot = engine.snapshot();
            currentFrameSnapshot = engine.frameSnapshot(currentSnapshot);
        }
    }

    public synchronized void applyTerrainCommit(TerrainCommit commit) {
        engine.applyTerrainCommit(commit);
        currentSnapshot = engine.snapshot();
        currentFrameSnapshot = engine.frameSnapshot(currentSnapshot);
    }

    public synchronized long terrainRevision() {
        return engine.terrainRevision();
    }

    public synchronized SimulationFrameSnapshot currentFrameSnapshot() {
        return currentFrameSnapshot;
    }

    public synchronized void setCruisingSpeed(double unitsPerSecond) {
        engine.setCruisingSpeed(unitsPerSecond);
        currentSnapshot = engine.snapshot();
        currentFrameSnapshot = engine.frameSnapshot(currentSnapshot);
    }

    /**
     * Excludes a UI/application pause from the wall-clock-to-simulation timestamp mapping.
     * A cancellation is queued first so interruption cannot become a jump-producing release.
     */
    public synchronized void pauseAt(long wallTimeNanos) {
        if (pauseStartedNanos >= 0L) {
            return;
        }
        enqueueGestureCancellation(wallTimeNanos);
        pauseStartedNanos = wallTimeNanos;
    }

    public synchronized void resumeAt(long wallTimeNanos) {
        if (pauseStartedNanos < 0L) {
            return;
        }
        long pauseEndNanos = Math.max(pauseStartedNanos, wallTimeNanos);
        if (pauseEndNanos > pauseStartedNanos) {
            completedPauses.add(new PauseInterval(
                    pauseStartedNanos, pauseEndNanos));
        }
        pauseStartedNanos = -1L;
    }

    public synchronized boolean isPaused() {
        return pauseStartedNanos >= 0L;
    }

    public PlayerSnapshot previousSnapshot() {
        return previousSnapshot;
    }

    public PlayerSnapshot currentSnapshot() {
        return currentSnapshot;
    }

    public double renderAlpha() {
        return Math.min(1.0,
                (double) clock.retainedNanos() / PhysicsConfig.FIXED_DT_NANOS);
    }

    private long relativeTime(long eventTimeNanos) {
        long excluded = 0L;
        for (PauseInterval pause : completedPauses) {
            if (eventTimeNanos <= pause.startNanos) {
                continue;
            }
            long overlapEnd = Math.min(eventTimeNanos, pause.endNanos);
            excluded = saturatingAdd(
                    excluded, overlapEnd - pause.startNanos);
        }
        long sinceEpoch = Math.max(0L, eventTimeNanos - inputEpochNanos);
        return Math.max(0L, sinceEpoch - Math.min(sinceEpoch, excluded));
    }

    /**
     * Stage touch queues can be drained only after a lifecycle pause has ended. Rejecting both
     * currently paused input and delayed events timestamped inside a completed pause prevents
     * those stale events from being compressed to the pause boundary and reordered around the
     * cancellation queued by {@link #pauseAt(long)}.
     */
    private boolean rejectPausedInput(long eventTimeNanos) {
        if (pauseStartedNanos >= 0L) {
            return true;
        }
        for (PauseInterval pause : completedPauses) {
            if (eventTimeNanos >= pause.startNanos
                    && eventTimeNanos < pause.endNanos) {
                return true;
            }
        }
        return false;
    }

    private static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static final class PauseInterval {
        final long startNanos;
        final long endNanos;

        PauseInterval(long startNanos, long endNanos) {
            this.startNanos = startNanos;
            this.endNanos = endNanos;
        }
    }
}
