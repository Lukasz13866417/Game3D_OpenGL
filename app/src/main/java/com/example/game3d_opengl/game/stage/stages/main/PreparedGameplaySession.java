package com.example.game3d_opengl.game.stage.stages.main;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;

import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.game.player.player_character.PlayerConfig;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainCollisionIndex;
import com.example.game3d.core.terrain.TerrainOutput;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.authoring.GameplayLevelCatalog;
import com.example.game3d.authoring.GameplayTerrainStream;
import com.example.game3d_opengl.rendering.GPUResourceOwner;

public final class PreparedGameplaySession implements GPUResourceOwner {
    static final int TERRAIN_PREPARATION_COMMAND_BUDGET = 256;
    private static final int INITIAL_LEVEL_COUNT = 4;

    private Player player;
    private final GameplayTerrainStream terrain;
    private TerrainOutput terrainOutput;
    private final LightSource lightSource;
    private final int nextRandomLevelIndex;
    private volatile TerrainCollisionIndex preparedCollisionIndex;
    private volatile RuntimeException runtimePreparationFailure;
    private boolean collisionPreparationStarted;
    private long runtimePreparationGeneration;
    private long preparedCollisionRevision = -1L;

    private PreparedGameplaySession(
            Player player,
            GameplayTerrainStream terrain,
            TerrainOutput terrainOutput,
            LightSource lightSource,
            int nextRandomLevelIndex
    ) {
        if (terrain == null) {
            throw new IllegalArgumentException("terrain == null");
        }
        if (lightSource == null) {
            throw new IllegalArgumentException("lightSource == null");
        }
        if (terrainOutput == null) {
            throw new IllegalArgumentException("terrainOutput == null");
        }
        this.player = player;
        this.terrain = terrain;
        this.terrainOutput = terrainOutput;
        this.lightSource = lightSource;
        this.nextRandomLevelIndex = nextRandomLevelIndex;
    }

    public static PreparedGameplaySession createInitialSession() {
        return createInitialSession(GameplayLevelCatalog.builtIns());
    }

    public static PreparedGameplaySession createInitialSession(
            GameplayLevelCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog == null");
        }
        LightSource lightSource = new LightSource(CLR(1f, 1f, 1f, 1f));
        GameplayTerrainStream terrain =
                new GameplayTerrainStream(
                        com.example.game3d.authoring.TrackProfile.gameplayDefault(),
                        new Vec3(
                                Player.initialPositionX(),
                                Player.initialPositionY() - 3f,
                                Player.initialPositionZ()),
                        0L,
                        catalog);
        TerrainOutput terrainOutput = terrain;
        int nextRandomLevelIndex = 0;
        terrain.enqueueIntroSegments();
        for (int i = 0; i < INITIAL_LEVEL_COUNT; ++i) {
            terrain.enqueueGameplayLevel(nextRandomLevelIndex++);
        }
        return new PreparedGameplaySession(
                null, terrain, terrainOutput, lightSource,
                nextRandomLevelIndex);
    }

    public Player acquirePlayer() {
        if (player == null) {
            player = Player.createPlayer();
        }
        return player;
    }

    public GameplayTerrainStream getTerrainGenerator() {
        return terrain;
    }

    /**
     * Transfers ownership of the already-open canonical stream to gameplay.
     * Preparation commits are represented by the latest bootstrap snapshot and are discarded.
     */
    public TerrainOutput acquireTerrainOutput() {
        if (terrainOutput == null) {
            throw new IllegalStateException(
                    "Canonical terrain output was already acquired");
        }
        TerrainOutput result = terrainOutput;
        result.drainPendingCommits();
        terrainOutput = null;
        return result;
    }

    public LightSource getLightSource() {
        return lightSource;
    }

    public int getNextRandomLevelIndex() {
        return nextRandomLevelIndex;
    }

    public void generateTerrainChunks(int chunkBudget) {
        if (terrain != null) {
            terrain.generate(TERRAIN_PREPARATION_COMMAND_BUDGET, chunkBudget);
        }
    }

    public boolean isSpawnPlayableReady() {
        if (terrain == null) {
            return false;
        }
        int targetLeadTiles = computeSpawnPlayableLeadTiles(
                (float) terrain.getSegmentLength(),
                terrain.getInteractionWindowAhead()
        );
        return terrain.getCommittedLeadAheadOf(-1L) >= targetLeadTiles;
    }

    /**
     * Starts the expensive bootstrap collision-index build away from the GL thread.
     *
     * <p>Callers stop mutating this session once its spawn lead is ready, so the captured
     * revision remains the exact revision transferred into gameplay.</p>
     */
    public void beginRuntimePreparationAsync() {
        final TerrainSnapshot snapshot;
        final long generation;
        synchronized (this) {
            throwIfRuntimePreparationFailed();
            if (preparedCollisionIndex != null
                    || collisionPreparationStarted
                    || !isSpawnPlayableReady()) {
                return;
            }
            snapshot = terrain.snapshot();
            collisionPreparationStarted = true;
            generation = ++runtimePreparationGeneration;
        }

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    TerrainCollisionIndex index =
                            new TerrainCollisionIndex(snapshot);
                    synchronized (PreparedGameplaySession.this) {
                        if (generation != runtimePreparationGeneration) {
                            return;
                        }
                        if (terrain.revision() != snapshot.revision) {
                            collisionPreparationStarted = false;
                            return;
                        }
                        preparedCollisionIndex = index;
                        preparedCollisionRevision = snapshot.revision;
                    }
                } catch (Throwable failure) {
                    synchronized (PreparedGameplaySession.this) {
                        if (generation == runtimePreparationGeneration) {
                            runtimePreparationFailure =
                                    failure instanceof RuntimeException
                                            ? (RuntimeException) failure
                                            : new RuntimeException(
                                                    "Runtime preparation failed",
                                                    failure);
                        }
                    }
                }
            }
        }, "Game3D-session-collision-prep");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized boolean isRuntimePreparedReady() {
        throwIfRuntimePreparationFailed();
        return preparedCollisionIndex != null
                && preparedCollisionRevision == terrain.revision();
    }

    public synchronized TerrainCollisionIndex acquirePreparedCollisionIndex(
            long expectedRevision) {
        throwIfRuntimePreparationFailed();
        if (preparedCollisionIndex == null) {
            return null;
        }
        if (preparedCollisionRevision != expectedRevision) {
            throw new IllegalStateException(
                    "Prepared collision revision "
                            + preparedCollisionRevision
                            + " differs from terrain revision "
                            + expectedRevision);
        }
        TerrainCollisionIndex result = preparedCollisionIndex;
        preparedCollisionIndex = null;
        preparedCollisionRevision = -1L;
        return result;
    }

    public boolean isTerrainReady() {
        return terrain != null && !terrain.hasPendingGenerationWork();
    }

    static int computeSpawnPlayableLeadTiles(float segmentLength, int interactionAheadTiles) {
        return GameplayStage.computeTargetCommittedLeadTiles(
                PlayerConfig.speedForCompletedPhases(0) * 1000f,
                segmentLength,
                interactionAheadTiles
        );
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        if (player != null) {
            player.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    @Override
    public synchronized void cleanupGPUResourcesRecursively() {
        runtimePreparationGeneration++;
        collisionPreparationStarted = false;
        preparedCollisionIndex = null;
        preparedCollisionRevision = -1L;
        if (player != null) {
            player.cleanupGPUResourcesRecursively();
        }
        if (terrainOutput != null) {
            terrainOutput.close();
            terrainOutput = null;
        }
    }

    private void throwIfRuntimePreparationFailed() {
        RuntimeException failure = runtimePreparationFailure;
        if (failure != null) {
            throw new IllegalStateException(
                    "Prepared gameplay runtime failed", failure);
        }
    }
}
