package com.example.game3d_opengl.game.terrain.track_elements.potion;

import com.example.game3d_opengl.game.terrain.track_elements.portal.PortalLightingEnvironment;
import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.batching.InstancedFamilyRenderer;
import com.example.game3d_opengl.rendering.batching.ObjectBlockEncoder;
import com.example.game3d_opengl.rendering.batching.PositionNormalGeometryBuilder;
import com.example.game3d_opengl.rendering.batching.ScenePassBlockEncoder;
import com.example.game3d_opengl.rendering.batching.ScenePassData;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.PreparedModelData;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public final class PotionBatchRenderer implements GPUResourceOwner {
    private static final class PotionObjectEncoder
            implements ObjectBlockEncoder<PotionBatchInstance> {
        private final float[] modelScratch = new float[16];

        @Override
        public int floatCountPerObject() {
            return 20;
        }

        @Override
        public void encode(PotionBatchInstance potion, FloatBuffer target) {
            potion.writePotionModelMatrix(modelScratch);
            target.put(modelScratch);
            FColor c = Potion.POTION_FILL_COLOR;
            target.put(c.r()).put(c.g()).put(c.b()).put(c.a());
        }
    }

    private final InstancedFamilyRenderer<ScenePassData, PotionBatchInstance> renderer;
    private final List<PotionBatchInstance> visiblePotions = new ArrayList<>();
    private final ScenePassData passScratch = new ScenePassData();
    private int lastBatchDrawCalls = 0;
    private int lastBatchInstanceCount = 0;

    public PotionBatchRenderer(PreparedModelData preparedModel) {
        this.renderer = new InstancedFamilyRenderer<>(
                PositionNormalGeometryBuilder.buildSmooth(preparedModel),
                new PotionBatchShaderProgram(),
                ScenePassBlockEncoder.INSTANCE,
                new PotionObjectEncoder()
        );
    }

    public void beginFrame() {
        visiblePotions.clear();
        lastBatchDrawCalls = 0;
        lastBatchInstanceCount = 0;
    }

    public void add(Potion potion) {
        if (potion != null) {
            visiblePotions.add(potion);
        }
    }

    public void add(PotionBatchInstance potion) {
        if (potion != null) {
            visiblePotions.add(potion);
        }
    }

    public void render(float[] vpMatrix) {
        if (visiblePotions.isEmpty()) {
            return;
        }
        buildPass(vpMatrix);
        lastBatchDrawCalls = 1;
        lastBatchInstanceCount = visiblePotions.size();
        renderer.render(passScratch, visiblePotions);
        visiblePotions.clear();
    }

    public void drawSingle(float[] vpMatrix, Potion potion) {
        if (potion == null) {
            return;
        }
        buildPass(vpMatrix);
        renderer.renderSingle(passScratch, potion);
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
