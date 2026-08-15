package com.example.game3d.core.terrain.addon;

/** Receives deterministic effects produced by addon contact evaluation. */
public interface AddonEffectSink {
    void hitHazard(long addonId);

    void grantAirJump(long addonId, int charges);
}
