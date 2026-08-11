package com.example.game3d_opengl.game.terrain.track_elements.spike;

import com.example.game3d_opengl.game.terrain.track_elements.portal.PortalLightingEnvironment;
import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.batching.InstancedFamilyRenderer;
import com.example.game3d_opengl.rendering.batching.ObjectBlockEncoder;
import com.example.game3d_opengl.rendering.batching.ScenePassBlockEncoder;
import com.example.game3d_opengl.rendering.batching.ScenePassData;
import com.example.game3d_opengl.rendering.batching.SpikeCanonicalGeometryBuilder;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public final class SpikeBatchRenderer implements GPUResourceOwner {
    private static final class SpikeObjectEncoder
            implements ObjectBlockEncoder<SpikeBatchInstance> {
        @Override
        public int floatCountPerObject() {
            return 24;
        }

        @Override
        public void encode(SpikeBatchInstance spike, FloatBuffer target) {
            SpikeInfillDrawArgs args = spike.spikeBatchArgs();
            putVec4(target, args.uNL, 1f);
            putVec4(target, args.uNR, 1f);
            putVec4(target, args.uFR, 1f);
            putVec4(target, args.uFL, 1f);
            putVec4(target, args.uApex, 1f);
            target.put(args.uNormal[0]).put(args.uNormal[1]).put(args.uNormal[2]).put(args.uBaseOffset);
        }

        private static void putVec4(FloatBuffer target, float[] xyz, float w) {
            target.put(xyz[0]).put(xyz[1]).put(xyz[2]).put(w);
        }
    }

    private final InstancedFamilyRenderer<ScenePassData, SpikeBatchInstance> renderer;
    private final List<SpikeBatchInstance> visibleSpikes = new ArrayList<>();
    private final ScenePassData passScratch = new ScenePassData();
    private int lastBatchDrawCalls = 0;
    private int lastBatchInstanceCount = 0;

    public SpikeBatchRenderer() {
        this.renderer = new InstancedFamilyRenderer<>(
                SpikeCanonicalGeometryBuilder.buildFillGeometry(),
                new SpikeBatchShaderProgram(),
                ScenePassBlockEncoder.INSTANCE,
                new SpikeObjectEncoder()
        );
    }

    public void beginFrame() {
        visibleSpikes.clear();
        lastBatchDrawCalls = 0;
        lastBatchInstanceCount = 0;
    }

    public void add(DeathSpike spike) {
        if (spike != null && spike.hasBatchData()) {
            visibleSpikes.add(spike);
        }
    }

    public void add(SpikeBatchInstance spike) {
        if (spike != null && spike.spikeBatchArgs() != null) {
            visibleSpikes.add(spike);
        }
    }

    public void render(float[] vpMatrix) {
        if (visibleSpikes.isEmpty()) {
            return;
        }
        buildPass(vpMatrix);
        lastBatchDrawCalls = 1;
        lastBatchInstanceCount = visibleSpikes.size();
        renderer.render(passScratch, visibleSpikes);
        visibleSpikes.clear();
    }

    public void drawSingle(float[] vpMatrix, DeathSpike spike) {
        if (spike == null || !spike.hasBatchData()) {
            return;
        }
        buildPass(vpMatrix);
        renderer.renderSingle(passScratch, spike);
    }

    private void buildPass(float[] vpMatrix) {
        passScratch.setVp(vpMatrix);
        Vector3D lightPos = PortalLightingEnvironment.getLightPos();
        Vector3D cameraPos = PortalLightingEnvironment.getCameraPos();
        FColor lightColor = PortalLightingEnvironment.getLightColor();
        FColor theme = PortalLightingEnvironment.getColorTheme();
        if (lightPos != null) {
            passScratch.lightX = lightPos.x;
            passScratch.lightY = lightPos.y;
            passScratch.lightZ = lightPos.z;
        }
        if (cameraPos != null) {
            passScratch.cameraX = cameraPos.x;
            passScratch.cameraY = cameraPos.y;
            passScratch.cameraZ = cameraPos.z;
        }
        if (lightColor != null) {
            passScratch.lightColorR = lightColor.r();
            passScratch.lightColorG = lightColor.g();
            passScratch.lightColorB = lightColor.b();
            passScratch.lightColorA = lightColor.a();
        }
        if (theme != null) {
            passScratch.themeR = theme.r();
            passScratch.themeG = theme.g();
            passScratch.themeB = theme.b();
            passScratch.themeA = theme.a();
        }
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        renderer.reloadGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        renderer.cleanupGPUResourcesRecursively();
    }

    public int getLastBatchDrawCalls() {
        return lastBatchDrawCalls;
    }

    public int getLastBatchInstanceCount() {
        return lastBatchInstanceCount;
    }
}
