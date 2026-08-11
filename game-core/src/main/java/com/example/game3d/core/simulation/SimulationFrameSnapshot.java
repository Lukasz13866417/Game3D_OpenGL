package com.example.game3d.core.simulation;

/**
 * Immutable gameplay data required by presentation at one fixed simulation tick.
 *
 * <p>Render interpolation combines two consecutive instances; no Android or OpenGL type appears
 * here.</p>
 */
public final class SimulationFrameSnapshot {
    public final PlayerSnapshot player;
    public final long terrainRevision;
    public final FeatureActivitySnapshot featureActivity;

    SimulationFrameSnapshot(
            PlayerSnapshot player,
            long terrainRevision,
            FeatureActivitySnapshot featureActivity) {
        this.player = player;
        this.terrainRevision = terrainRevision;
        this.featureActivity = featureActivity;
    }
}
