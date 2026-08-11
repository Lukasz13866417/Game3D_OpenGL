package com.example.game3d.simulator;

import com.example.game3d.core.terrain.TerrainCommit;

import java.util.List;

interface TerrainCommitObserver {
    void onTerrainCommits(
            long beforeTick,
            List<TerrainCommit> commits,
            long resultingRevision,
            long resultingDigest);
}
