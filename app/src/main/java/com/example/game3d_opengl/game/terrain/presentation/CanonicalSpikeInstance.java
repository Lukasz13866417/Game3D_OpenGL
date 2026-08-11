package com.example.game3d_opengl.game.terrain.presentation;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainFeatureSpec;
import com.example.game3d_opengl.game.terrain.track_elements.spike.SpikeBatchInstance;
import com.example.game3d_opengl.game.terrain.track_elements.spike.SpikeInfillDrawArgs;

final class CanonicalSpikeInstance implements SpikeBatchInstance {
    final TerrainFeatureSpec.Spike spec;
    final long digest;
    private final float[] nearLeft = new float[3];
    private final float[] nearRight = new float[3];
    private final float[] farRight = new float[3];
    private final float[] farLeft = new float[3];
    private final float[] apex = new float[3];
    private final float[] normal = new float[3];
    private final SpikeInfillDrawArgs args;

    CanonicalSpikeInstance(TerrainFeatureSpec.Spike spec, Vec3 renderOrigin) {
        this.spec = spec;
        this.digest = spec.deterministicDigest();
        writeDirection(spec.outwardNormal, normal);
        this.args = new SpikeInfillDrawArgs(
                nearLeft, nearRight, farRight, farLeft,
                apex, normal, (float) spec.baseOffset);
        setRenderOrigin(renderOrigin);
    }

    void setRenderOrigin(Vec3 renderOrigin) {
        Vec3 origin = renderOrigin == null ? Vec3.ZERO : renderOrigin;
        writePosition(spec.nearLeft, origin, nearLeft);
        writePosition(spec.nearRight, origin, nearRight);
        writePosition(spec.farLeft, origin, farLeft);
        writePosition(spec.farRight, origin, farRight);
        writePosition(spec.apex, origin, apex);
    }

    @Override
    public SpikeInfillDrawArgs spikeBatchArgs() {
        return args;
    }

    private static void writePosition(Vec3 value, Vec3 origin, float[] out) {
        out[0] = (float) (value.x - origin.x);
        out[1] = (float) (value.y - origin.y);
        out[2] = (float) (value.z - origin.z);
    }

    private static void writeDirection(Vec3 value, float[] out) {
        out[0] = (float) value.x;
        out[1] = (float) value.y;
        out[2] = (float) value.z;
    }
}
