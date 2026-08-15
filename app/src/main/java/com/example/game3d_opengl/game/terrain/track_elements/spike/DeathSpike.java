package com.example.game3d_opengl.game.terrain.track_elements.spike;

import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.game.util.GameRandom;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;

/** @deprecated Mutable compatibility addon for the legacy terrain diagnostics. */
@Deprecated
public class DeathSpike extends Addon
        implements SpikeBatchInstance {
    private static final float MIN_RANDOM_HEIGHT = 0.225f * 1.25f;
    private static final float MAX_RANDOM_HEIGHT = 0.5f * 1.25f;

    private final float height;
    private final float baseOffset = 0.025f;

    private SpikeInfillDrawArgs infillArgs;

    private DeathSpike(float height) {
        super();
        this.height = height;
    }
    
    public static DeathSpike createDeathSpike() {
        float height = GameRandom.randFloat(MIN_RANDOM_HEIGHT, MAX_RANDOM_HEIGHT, 5);
        return new DeathSpike(height); // object3D will be set in onPlace
    }

    @Override
    protected void onPlace(float nearLeftX, float nearLeftY, float nearLeftZ,
                           float nearRightX, float nearRightY, float nearRightZ,
                           float farLeftX, float farLeftY, float farLeftZ,
                           float farRightX, float farRightY, float farRightZ) {
        float fieldMidX = 0.25f * (farLeftX + farRightX + nearRightX + nearLeftX);
        float fieldMidY = 0.25f * (farLeftY + farRightY + nearRightY + nearLeftY);
        float fieldMidZ = 0.25f * (farLeftZ + farRightZ + nearRightZ + nearLeftZ);

        float edge1X = farLeftX - nearLeftX;
        float edge1Y = farLeftY - nearLeftY;
        float edge1Z = farLeftZ - nearLeftZ;
        float edge2X = farRightX - nearLeftX;
        float edge2Y = farRightY - nearLeftY;
        float edge2Z = farRightZ - nearLeftZ;
        float normalX = edge1Y * edge2Z - edge1Z * edge2Y;
        float normalY = edge1Z * edge2X - edge1X * edge2Z;
        float normalZ = edge1X * edge2Y - edge1Y * edge2X;
        float normalLen = (float) Math.sqrt(
                normalX * normalX + normalY * normalY + normalZ * normalZ
        );
        if (normalLen <= 1e-8f) {
            normalX = 0f;
            normalY = 1f;
            normalZ = 0f;
        } else {
            float invLen = 1f / normalLen;
            normalX *= invLen;
            normalY *= invLen;
            normalZ *= invLen;
        }

        float apexX = fieldMidX - normalX * height;
        float apexY = fieldMidY - normalY * height;
        float apexZ = fieldMidZ - normalZ * height;
        final float[] uNL = new float[3],
                      uNR = new float[3],
                      uFL = new float[3],
                      uFR = new float[3];
        final float[] uApex = new float[3],
                      uNormal = new float[3];
        uNL[0]=nearLeftX; uNL[1]=nearLeftY; uNL[2]=nearLeftZ;
        uNR[0]=nearRightX; uNR[1]=nearRightY; uNR[2]=nearRightZ;
        uFR[0]=farRightX; uFR[1]=farRightY; uFR[2]=farRightZ;
        uFL[0]=farLeftX; uFL[1]=farLeftY; uFL[2]=farLeftZ;
        uApex[0]=apexX; uApex[1]=apexY; uApex[2]=apexZ;
        uNormal[0]=-normalX; uNormal[1]=-normalY; uNormal[2]=-normalZ;

        this.infillArgs = new SpikeInfillDrawArgs(uNL, uNR, uFR, uFL, uApex, uNormal, baseOffset);
    }

    @Override
    public void draw(float[] vpMatrix) {
        // Legacy Terrain submits this instance through SpikeBatchInstance.
    }

    @Override
    public void updateBeforeDraw(float dt) {

    }

    @Override
    public void updateAfterDraw(float dt) {

    }

    @Override
    public void rebasePosition(Vector3D delta) {
        if (delta == null || infillArgs == null) return;
        addOffset(infillArgs.uNL, delta);
        addOffset(infillArgs.uNR, delta);
        addOffset(infillArgs.uFR, delta);
        addOffset(infillArgs.uFL, delta);
        addOffset(infillArgs.uApex, delta);
    }

    public boolean writeHazardPoint(float[] out) {
        if (out == null || out.length < 3 || infillArgs == null) return false;
        out[0] = infillArgs.uApex[0];
        out[1] = infillArgs.uApex[1];
        out[2] = infillArgs.uApex[2];
        return true;
    }

    private static void addOffset(float[] v, Vector3D delta) {
        if (v == null) return;
        v[0] += delta.x;
        v[1] += delta.y;
        v[2] += delta.z;
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        // Renderer ownership lives outside the addon instance.
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        // Renderer ownership lives outside the addon instance.
    }


    @Override
    public void accept(Player player) {
        if (player != null && infillArgs != null) {
            player.recordLegacyDeathSpike(
                    infillArgs.uApex[0],
                    infillArgs.uApex[1],
                    infillArgs.uApex[2]);
        }
    }

    boolean hasBatchData() {
        return infillArgs != null;
    }

    SpikeInfillDrawArgs getBatchArgs() {
        if (infillArgs == null) {
            throw new IllegalStateException("Spike batch args are not initialized");
        }
        return infillArgs;
    }

    @Override
    public SpikeInfillDrawArgs spikeBatchArgs() {
        return getBatchArgs();
    }

}
