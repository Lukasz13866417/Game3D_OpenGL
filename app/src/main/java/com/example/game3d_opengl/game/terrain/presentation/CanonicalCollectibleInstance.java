package com.example.game3d_opengl.game.terrain.presentation;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainFeatureSpec;
import com.example.game3d_opengl.game.terrain.track_elements.potion.PotionBatchInstance;

final class CanonicalCollectibleInstance implements PotionBatchInstance {
    final TerrainFeatureSpec.AirJumpCollectible spec;
    final long digest;
    private float x;
    private float y;
    private float z;
    private float yawDegrees;

    CanonicalCollectibleInstance(
            TerrainFeatureSpec.AirJumpCollectible spec, Vec3 renderOrigin) {
        this.spec = spec;
        this.digest = spec.deterministicDigest();
        setRenderOrigin(renderOrigin);
    }

    void setRenderOrigin(Vec3 renderOrigin) {
        Vec3 origin = renderOrigin == null ? Vec3.ZERO : renderOrigin;
        x = (float) (spec.center.x - origin.x);
        y = (float) (spec.center.y - origin.y);
        z = (float) (spec.center.z - origin.z);
    }

    void update(float dtMillis) {
        yawDegrees = wrapDegrees(yawDegrees + Math.max(0f, dtMillis) * 0.16f);
    }

    @Override
    public void writePotionModelMatrix(float[] out) {
        if (out == null || out.length < 16) {
            throw new IllegalArgumentException("out must contain 16 floats");
        }
        float radians = (float) Math.toRadians(yawDegrees);
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        out[0] = c;   out[1] = 0f; out[2] = -s;  out[3] = 0f;
        out[4] = 0f;  out[5] = 1f; out[6] = 0f;  out[7] = 0f;
        out[8] = s;   out[9] = 0f; out[10] = c;  out[11] = 0f;
        out[12] = x;  out[13] = y; out[14] = z;  out[15] = 1f;
    }

    private static float wrapDegrees(float value) {
        float wrapped = value % 360f;
        return wrapped < 0f ? wrapped + 360f : wrapped;
    }
}
