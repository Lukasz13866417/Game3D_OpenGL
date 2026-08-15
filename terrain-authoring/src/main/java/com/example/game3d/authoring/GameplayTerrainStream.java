package com.example.game3d.authoring;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainCommit;
import com.example.game3d.core.terrain.TerrainOutput;
import com.example.game3d.core.terrain.TerrainSnapshot;

import java.util.List;

/** Structure-backed production terrain stream and the sole gameplay authoring facade. */
public final class GameplayTerrainStream implements TerrainOutput {
    private final Terrain terrain;
    private final GameplayLevelCatalog catalog;

    public GameplayTerrainStream(
            TrackProfile profile,
            Vec3 startCenter,
            long seed,
            GameplayLevelCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog == null");
        }
        this.terrain = new Terrain(profile, startCenter, seed);
        this.catalog = catalog;
    }

    public static GameplayTerrainStream builtIns(Vec3 startCenter) {
        return new GameplayTerrainStream(
                TrackProfile.gameplayDefault(), startCenter, 0L,
                GameplayLevelCatalog.builtIns());
    }

    public void enqueueIntroSegments() {
        terrain.enqueueStructure(GameplayTerrainFactory.intro());
    }

    public void enqueueGameplayLevel(int levelOrdinal) {
        GameplayLevelProvider provider = catalog.select(levelOrdinal);
        terrain.enqueueStructure(provider.create(levelOrdinal));
    }

    public int generateChunks(int segmentBudget) {
        return terrain.generateChunks(segmentBudget);
    }

    /** Advances private authoring work and public segment commits under independent limits. */
    public int generate(int commandBudget, int segmentBudget) {
        return terrain.generate(new GenerationBudget(commandBudget, segmentBudget));
    }

    public void removeOldTerrainElements(long referenceSegmentId) {
        terrain.removeOldTerrainElements(referenceSegmentId);
    }

    public boolean hasPendingGenerationWork() {
        return terrain.hasPendingGenerationWork();
    }

    public int getSegmentCount() {
        return terrain.getSegmentCount();
    }

    public int getPlannedSegmentCount() {
        return terrain.getPlannedSegmentCount();
    }

    public double getSegmentLength() {
        return terrain.getSegmentLength();
    }

    public int getInteractionWindowAhead() {
        return terrain.getInteractionWindowAhead();
    }

    public int getCommittedLeadAheadOf(long referenceSegmentId) {
        return terrain.getCommittedLeadAheadOf(referenceSegmentId);
    }

    public long resolveStreamingReferenceSegmentId(
            long lastSupportedSegmentId, Vec3 absolutePosition) {
        return terrain.resolveStreamingReferenceSegmentId(
                lastSupportedSegmentId, absolutePosition);
    }

    public GameplayLevelCatalog catalog() {
        return catalog;
    }

    @Override
    public TerrainSnapshot snapshot() {
        return terrain.snapshot();
    }

    @Override
    public List<TerrainCommit> drainPendingCommits() {
        return terrain.drainPendingCommits();
    }

    @Override
    public long revision() {
        return terrain.revision();
    }

    @Override
    public void close() {
        terrain.close();
    }
}
