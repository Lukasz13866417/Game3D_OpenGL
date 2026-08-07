package com.example.game3d.core.terrain;

import java.util.List;

/**
 * The sole output contract of a streaming terrain producer.
 *
 * <p>Consumers bootstrap from one snapshot, then apply ordered commits between fixed simulation
 * ticks. Rendering, HUD, and physics may build independent caches from these immutable records.</p>
 */
public interface TerrainOutput extends AutoCloseable {
    TerrainSnapshot snapshot();

    List<TerrainCommit> drainPendingCommits();

    long revision();

    @Override
    void close();
}
