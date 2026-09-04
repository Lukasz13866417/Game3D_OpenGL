package com.example.game3d_opengl.game.player.player_character;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.example.game3d_opengl.game.terrain.track_elements.portal.rendering.PortalRenderTarget;
import com.example.game3d_opengl.rendering.BloomConfig;
import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.RenderTarget;
import com.example.game3d_opengl.rendering.object3d.UnbatchedObject3DWithOutline;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.PreparedModelData;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * Wheel-local temporal integration for the mint wheel's moving emissive grooves.
 *
 * <p>The expensive work is restricted to a projected wheel ROI. Each exposure pose occupies an
 * independent atlas cell with its own groove depth. A single resolve compares those depths with
 * the already-rendered emitter-free scene depth, producing one normalized local exposure image.
 * The geometry atlas is rendered once. It is resolved first against player-body depth so the
 * premultiplied luminous core can be inserted before translucent terrain, then resolved again
 * against final scene depth. That second resolve adds only the per-pixel energy missing from the
 * ordinary bloom prefilter. Side emission remains sharp.</p>
 */
final class WheelMotionGlowRenderer implements GPUResourceOwner {
    private static final String TAG = "WheelMotionGlow";
    private static final int ATLAS_COLUMNS = 4;
    private static final int ATLAS_ROWS = 3;
    private static final int MAX_SAMPLES =
            WheelTemporalSamplingPlanner.MAX_TEMPORAL_SAMPLES;
    private static final int DEFAULT_CELL_CAPACITY_PIXELS = 192;
    private static final int CELL_GROWTH_ALIGNMENT_PIXELS = 32;
    private static final float ROI_RADIUS_SCALE = 1.18f;
    private static final float ROI_GUARD_PIXELS = 4f;
    private static final float CLIP_EPSILON = 1.0e-5f;

    // RGBA8 is universally renderable on the required ES 3.1 devices. Keeping the atlas below
    // one avoids asymmetric clipping during accumulation; the composite pass restores energy.
    private static final float ATLAS_ENCODE_SCALE = 0.84f;

    private static final float[] QUAD_VERTICES = new float[]{
            -1f, -1f, 0f, 0f, 0f,
             1f, -1f, 0f, 1f, 0f,
             1f,  1f, 0f, 1f, 1f,
            -1f,  1f, 0f, 0f, 1f
    };
    private static final short[] QUAD_INDICES = new short[]{0, 1, 2, 0, 2, 3};

    private final float[] resolverWeights = new float[MAX_SAMPLES];
    private final float[] grooveModel = new float[16];
    private final float[] cropMatrix = new float[16];
    private final float[] croppedViewProjection = new float[16];
    private final float[] projectedCenter = new float[2];
    private final float[] projectedPoint = new float[2];

    private PortalRenderTarget atlasTarget;
    private PortalRenderTarget resolvedTarget;
    private int atlasCellCapacityWidth;
    private int atlasCellCapacityHeight;
    private int requestedBloomWidth;
    private int requestedBloomHeight;
    private int roiX;
    private int roiY;
    private int roiWidth;
    private int roiHeight;
    private boolean supported = true;
    private boolean atlasFrameAvailable;

    private int resolverProgram;
    private int quadVbo;
    private int quadIbo;
    private int resolverPosition;
    private int resolverUv;
    private int resolverAtlas;
    private int resolverAtlasDepth;
    private int resolverSceneDepth;
    private int resolverSampleCount;
    private int resolverWeightsLocation;
    private int resolverAtlasSize;
    private int resolverCellStride;
    private int resolverRoiSize;
    private int resolverSceneUvOrigin;
    private int resolverSceneUvScale;
    private int resolverSceneTexelStep;

    private int compositeProgram;
    private int compositePosition;
    private int compositeUv;
    private int compositeSource;
    private int compositeUvOrigin;
    private int compositeUvScale;
    private int compositeIntensity;
    private int compositeAlphaIntensity;
    private int compositeScene;
    private int compositeSceneUvOrigin;
    private int compositeSceneUvScale;
    private int compositeSceneTexelStep;
    private int compositeBloomThreshold;
    private int compositeEmissionBrightFactor;
    private int compositeBloomCorrection;
    private int compositeBloomCorrectionBlend;

    private PreparedModelData uploadedGrooveGeometry;
    private PreparedModelData uploadedMotionBandGeometry;
    private int grooveProgram;
    private int grooveVbo;
    private int grooveIbo;
    private int grooveIndexCount;
    private int grooveIndexType;
    private int motionBandVbo;
    private int motionBandIbo;
    private int motionBandIndexCount;
    private int motionBandIndexType;
    private int groovePosition;
    private int grooveViewProjection;
    private int grooveModelLocation;
    private int grooveColor;

    boolean isSupported() {
        return supported;
    }

    /** Invalidates any atlas left by an earlier frame without touching its reusable storage. */
    void discardPreparedFrame() {
        atlasFrameAvailable = false;
    }

    /** Ensures target growth succeeds before the scene chooses the temporal groove path. */
    boolean prepareFrameCapacity(
            int bloomWidth,
            int bloomHeight,
            float projectedRadius) {
        PreparedModelData geometry = PlayerAssets.mintGrooveTemporalGeometry();
        if (!supported || geometry == null || !(projectedRadius > 0f)
                || bloomWidth <= 0 || bloomHeight <= 0) {
            return false;
        }
        // ceil(center + h) - floor(center - h) can be one pixel wider than ceil(2h).
        // Reserve that guard pixel here so the active render cannot unexpectedly grow targets
        // after the player body has already been drawn without its sharp groove mesh.
        double fullExtent = 1.0 + Math.ceil(
                2.0 * (projectedRadius * ROI_RADIUS_SCALE
                        + ROI_GUARD_PIXELS));
        int requiredWidth = (int) Math.max(
                1.0, Math.min((double) bloomWidth, fullExtent));
        int requiredHeight = (int) Math.max(
                1.0, Math.min((double) bloomHeight, fullExtent));
        try {
            ensureResources(
                    bloomWidth,
                    bloomHeight,
                    geometry,
                    PlayerAssets.mintMotionBandTemporalGeometry(),
                    requiredWidth,
                    requiredHeight);
            return supported;
        } catch (RuntimeException failure) {
            disableAfterFailure(failure);
            return false;
        }
    }

    void preload(int bloomWidth, int bloomHeight) {
        PreparedModelData geometry = PlayerAssets.mintGrooveTemporalGeometry();
        if (geometry == null || bloomWidth <= 0 || bloomHeight <= 0) {
            return;
        }
        try {
            ensureResources(
                    bloomWidth,
                    bloomHeight,
                    geometry,
                    PlayerAssets.mintMotionBandTemporalGeometry(),
                    Math.min(DEFAULT_CELL_CAPACITY_PIXELS, bloomWidth),
                    Math.min(DEFAULT_CELL_CAPACITY_PIXELS, bloomHeight));
        } catch (RuntimeException failure) {
            disableAfterFailure(failure);
        }
    }

    /** Returns the projected radius in the temporal target's pixels. */
    float projectedRadiusPixels(
            float[] viewProjection,
            UnbatchedObject3DWithOutline wheel,
            float worldRadius,
            int viewportWidth,
            int viewportHeight) {
        if (viewProjection == null || viewProjection.length < 16
                || wheel == null || !(worldRadius > 0f)
                || viewportWidth <= 0 || viewportHeight <= 0) {
            return 0f;
        }
        if (!project(
                viewProjection,
                wheel.objX,
                wheel.objY,
                wheel.objZ,
                viewportWidth,
                viewportHeight,
                projectedCenter)) {
            return 0f;
        }

        float maxRadius = 0f;
        if (project(
                viewProjection,
                wheel.objX,
                wheel.objY + worldRadius,
                wheel.objZ,
                viewportWidth,
                viewportHeight,
                projectedPoint)) {
            maxRadius = pixelDistance(projectedCenter, projectedPoint);
        }
        if (project(
                viewProjection,
                wheel.objX,
                wheel.objY - worldRadius,
                wheel.objZ,
                viewportWidth,
                viewportHeight,
                projectedPoint)) {
            maxRadius = Math.max(
                    maxRadius,
                    pixelDistance(projectedCenter, projectedPoint));
        }

        double yawRadians = Math.toRadians(wheel.objYaw);
        float radialX = (float) Math.sin(yawRadians) * worldRadius;
        float radialZ = (float) Math.cos(yawRadians) * worldRadius;
        if (project(
                viewProjection,
                wheel.objX + radialX,
                wheel.objY,
                wheel.objZ + radialZ,
                viewportWidth,
                viewportHeight,
                projectedPoint)) {
            maxRadius = Math.max(
                    maxRadius,
                    pixelDistance(projectedCenter, projectedPoint));
        }
        if (project(
                viewProjection,
                wheel.objX - radialX,
                wheel.objY,
                wheel.objZ - radialZ,
                viewportWidth,
                viewportHeight,
                projectedPoint)) {
            maxRadius = Math.max(
                    maxRadius,
                    pixelDistance(projectedCenter, projectedPoint));
        }

        // Grooves span the cylinder axis too. Radius is a conservative substitute for their
        // slightly smaller half-length and covers perspective expansion at either end.
        float axialX = (float) Math.cos(yawRadians) * worldRadius;
        float axialZ = (float) -Math.sin(yawRadians) * worldRadius;
        if (project(
                viewProjection,
                wheel.objX + axialX,
                wheel.objY,
                wheel.objZ + axialZ,
                viewportWidth,
                viewportHeight,
                projectedPoint)) {
            maxRadius = Math.max(
                    maxRadius,
                    pixelDistance(projectedCenter, projectedPoint));
        }
        if (project(
                viewProjection,
                wheel.objX - axialX,
                wheel.objY,
                wheel.objZ - axialZ,
                viewportWidth,
                viewportHeight,
                projectedPoint)) {
            maxRadius = Math.max(
                    maxRadius,
                    pixelDistance(projectedCenter, projectedPoint));
        }
        return Float.isFinite(maxRadius) ? maxRadius : 0f;
    }

    /**
     * Renders one exposure atlas and inserts its premultiplied core into the scene. Call this
     * after the emitter-free player body and before terrain. The atlas remains valid for the
     * later bloom-only resolve.
     */
    boolean renderSceneCore(
            RenderTarget sceneSource,
            int bloomWidth,
            int bloomHeight,
            float[] viewProjection,
            UnbatchedObject3DWithOutline wheel,
            WheelTemporalSamplingPlanner.Plan plan,
            float coreIntensity) {
        try {
            return renderSceneCoreUnchecked(
                    sceneSource,
                    bloomWidth,
                    bloomHeight,
                    viewProjection,
                    wheel,
                    plan,
                    coreIntensity);
        } catch (RuntimeException failure) {
            disableAfterFailure(failure);
            restoreSceneDestination(sceneSource);
            return false;
        }
    }

    private boolean renderSceneCoreUnchecked(
            RenderTarget sceneSource,
            int bloomWidth,
            int bloomHeight,
            float[] viewProjection,
            UnbatchedObject3DWithOutline wheel,
            WheelTemporalSamplingPlanner.Plan plan,
            float coreIntensity) {
        atlasFrameAvailable = false;
        if (!supported || sceneSource == null
                || sceneSource.getDepthTextureId() == 0
                || viewProjection == null || wheel == null || plan == null
                || !(coreIntensity > 0f)
                || !computeRoi(
                        viewProjection,
                        wheel,
                        plan.projectedRadiusPixels(),
                        bloomWidth,
                        bloomHeight)) {
            restoreSceneDestination(sceneSource);
            return false;
        }

        PreparedModelData grooveGeometry =
                PlayerAssets.mintGrooveTemporalGeometry();
        if (grooveGeometry == null) {
            restoreSceneDestination(sceneSource);
            return false;
        }

        ensureResources(
                bloomWidth,
                bloomHeight,
                grooveGeometry,
                PlayerAssets.mintMotionBandTemporalGeometry(),
                roiWidth,
                roiHeight);
        if (!supported || atlasTarget == null || resolvedTarget == null
                || atlasTarget.getDepthTextureId() == 0) {
            restoreSceneDestination(sceneSource);
            return false;
        }

        float originalPitch = wheel.objPitch;
        boolean rendered = false;
        try {
            buildCroppedViewProjection(
                    viewProjection,
                    bloomWidth,
                    bloomHeight);
            renderExposureAtlas(wheel, plan, originalPitch);
            resolveToLocalEmission(sceneSource, plan, bloomWidth, bloomHeight);
            compositeResolved(
                    sceneSource,
                    scaledLeft(roiX, bloomWidth, sceneSource.getWidth()),
                    scaledLeft(roiY, bloomHeight, sceneSource.getHeight()),
                    scaledRight(roiX + roiWidth, bloomWidth,
                            sceneSource.getWidth()),
                    scaledRight(roiY + roiHeight, bloomHeight,
                            sceneSource.getHeight()),
                    coreIntensity,
                    true);
            atlasFrameAvailable = true;
            rendered = true;
        } finally {
            wheel.objPitch = originalPitch;
            if (!rendered) {
                atlasFrameAvailable = false;
            }
            restoreSceneDestination(sceneSource);
        }
        return true;
    }

    /** Re-resolves the atlas and adds only bloom energy absent from the ordinary bright pass. */
    void contributeBloom(
            RenderTarget bloomDestination,
            RenderTarget finalScene,
            int bloomWidth,
            int bloomHeight,
            WheelTemporalSamplingPlanner.Plan plan,
            float bloomCorrectionBlend) {
        try {
            contributeBloomUnchecked(
                    bloomDestination,
                    finalScene,
                    bloomWidth,
                    bloomHeight,
                    plan,
                    bloomCorrectionBlend);
        } catch (RuntimeException failure) {
            // The temporal core is already part of scene color. Losing only this optional direct
            // bloom share is a safe same-frame degradation; following frames return to sharp.
            disableAfterFailure(failure);
            restoreBloomDestination(
                    bloomDestination, bloomWidth, bloomHeight);
        }
    }

    private void contributeBloomUnchecked(
            RenderTarget bloomDestination,
            RenderTarget finalScene,
            int bloomWidth,
            int bloomHeight,
            WheelTemporalSamplingPlanner.Plan plan,
            float bloomCorrectionBlend) {
        if (!atlasFrameAvailable || !supported || bloomDestination == null
                || finalScene == null || finalScene.getDepthTextureId() == 0
                || plan == null || !(bloomCorrectionBlend > 0f)) {
            atlasFrameAvailable = false;
            restoreBloomDestination(
                    bloomDestination, bloomWidth, bloomHeight);
            return;
        }
        try {
            // Opaque terrain drawn after the scene core now participates in visibility. The
            // exposure geometry is deliberately reused; only this wheel-local resolve repeats.
            resolveToLocalEmission(
                    finalScene, plan, bloomWidth, bloomHeight);
            compositeResolved(
                    bloomDestination,
                    roiX,
                    roiY,
                    roiX + roiWidth,
                    roiY + roiHeight,
                    1f,
                    false,
                    finalScene,
                    bloomWidth,
                    bloomHeight,
                    bloomCorrectionBlend);
        } finally {
            atlasFrameAvailable = false;
            restoreBloomDestination(
                    bloomDestination, bloomWidth, bloomHeight);
        }
    }

    private boolean computeRoi(
            float[] viewProjection,
            UnbatchedObject3DWithOutline wheel,
            double projectedRadius,
            int width,
            int height) {
        if (width <= 0 || height <= 0 || !Double.isFinite(projectedRadius)
                || !(projectedRadius > 0.0)
                || !project(
                        viewProjection,
                        wheel.objX,
                        wheel.objY,
                        wheel.objZ,
                        width,
                        height,
                        projectedCenter)) {
            return false;
        }

        double halfExtent = Math.min(
                Math.max(width, height) * 2.0,
                projectedRadius * ROI_RADIUS_SCALE + ROI_GUARD_PIXELS);
        int left = (int) Math.max(0.0,
                Math.floor(projectedCenter[0] - halfExtent));
        int bottom = (int) Math.max(0.0,
                Math.floor(projectedCenter[1] - halfExtent));
        int right = (int) Math.min(width,
                Math.ceil(projectedCenter[0] + halfExtent));
        int top = (int) Math.min(height,
                Math.ceil(projectedCenter[1] + halfExtent));
        if (right <= left || top <= bottom) {
            return false;
        }
        roiX = left;
        roiY = bottom;
        roiWidth = right - left;
        roiHeight = top - bottom;
        return true;
    }

    private void buildCroppedViewProjection(
            float[] viewProjection,
            int fullWidth,
            int fullHeight) {
        float scaleX = fullWidth / (float) roiWidth;
        float scaleY = fullHeight / (float) roiHeight;
        float offsetX = (fullWidth - 2f * roiX - roiWidth)
                / (float) roiWidth;
        float offsetY = (fullHeight - 2f * roiY - roiHeight)
                / (float) roiHeight;
        Matrix.setIdentityM(cropMatrix, 0);
        cropMatrix[0] = scaleX;
        cropMatrix[5] = scaleY;
        cropMatrix[12] = offsetX;
        cropMatrix[13] = offsetY;
        Matrix.multiplyMM(
                croppedViewProjection,
                0,
                cropMatrix,
                0,
                viewProjection,
                0);
    }

    private void renderExposureAtlas(
            UnbatchedObject3DWithOutline wheel,
            WheelTemporalSamplingPlanner.Plan plan,
            float originalPitchDegrees) {
        boolean renderMotionBand = hasMotionBandContribution(plan);
        int physicalSampleCount = physicalSampleCount(plan);
        int combinedSampleCount = physicalSampleCount
                + (renderMotionBand ? 1 : 0);
        if (combinedSampleCount > MAX_SAMPLES) {
            throw new IllegalStateException(
                    "Wheel exposure exceeds fixed atlas budget: "
                            + combinedSampleCount);
        }
        atlasTarget.bind();
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(true);
        GLES20.glDepthFunc(GLES20.GL_LESS);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClearDepthf(1f);
        beginGrooveDraws(croppedViewProjection);
        try {
            bindEmissionGeometry(
                    grooveVbo,
                    grooveIbo);
            for (int sample = 0; sample < physicalSampleCount; sample++) {
                int column = sample % ATLAS_COLUMNS;
                int row = sample / ATLAS_COLUMNS;
                int x = column * atlasCellCapacityWidth;
                int y = row * atlasCellCapacityHeight;
                GLES20.glViewport(x, y, roiWidth, roiHeight);
                GLES20.glScissor(x, y, roiWidth, roiHeight);
                GLES20.glColorMask(true, true, true, true);
                GLES20.glDepthMask(true);
                GLES20.glClear(
                        GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

                wheel.objPitch = originalPitchDegrees
                        + (float) Math.toDegrees(
                        plan.resolvedSampleAngleOffsetRadians(sample));
                composeModelMatrix(grooveModel, wheel);
                drawEmissionGeometry(
                        grooveIndexCount,
                        grooveIndexType);
            }

            if (renderMotionBand) {
                int sample = physicalSampleCount;
                int column = sample % ATLAS_COLUMNS;
                int row = sample / ATLAS_COLUMNS;
                int x = column * atlasCellCapacityWidth;
                int y = row * atlasCellCapacityHeight;
                GLES20.glViewport(x, y, roiWidth, roiHeight);
                GLES20.glScissor(x, y, roiWidth, roiHeight);
                GLES20.glColorMask(true, true, true, true);
                GLES20.glDepthMask(true);
                GLES20.glClear(
                        GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

                // The shell represents an angular average, so its phase has no physical meaning.
                // Pinning it also removes the shell's own polygon-facet crawl.
                wheel.objPitch = 0f;
                composeModelMatrix(grooveModel, wheel);
                bindEmissionGeometry(
                        motionBandVbo,
                        motionBandIbo);
                drawEmissionGeometry(
                        motionBandIndexCount,
                        motionBandIndexType);
            }
        } finally {
            endGrooveDraws();
        }
        GLES20.glColorMask(true, true, true, true);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }

    private void resolveToLocalEmission(
            RenderTarget sceneSource,
            WheelTemporalSamplingPlanner.Plan plan,
            int bloomWidth,
            int bloomHeight) {
        boolean renderMotionBand = hasMotionBandContribution(plan);
        int physicalSampleCount = physicalSampleCount(plan);
        double physicalWeightScale = renderMotionBand
                ? plan.grooveContrast()
                : 1.0;
        int combinedSampleCount = physicalSampleCount
                + (renderMotionBand ? 1 : 0);
        for (int index = 0; index < MAX_SAMPLES; index++) {
            if (index < physicalSampleCount) {
                resolverWeights[index] = (float) (
                        plan.sampleWeight(index) * physicalWeightScale);
            } else if (renderMotionBand && index == physicalSampleCount) {
                resolverWeights[index] =
                        PlayerAssets.MINT_MOTION_BAND_DUTY_CYCLE
                                * (float) plan.continuousBandBlend();
            } else {
                resolverWeights[index] = 0f;
            }
        }

        resolvedTarget.bind();
        GLES20.glViewport(0, 0, roiWidth, roiHeight);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(0, 0, roiWidth, roiHeight);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);
        GLES20.glDisable(GLES20.GL_BLEND);

        GLES20.glUseProgram(resolverProgram);
        bindQuadAttributes(resolverPosition, resolverUv);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTarget.getTextureId());
        GLES20.glUniform1i(resolverAtlas, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(
                GLES20.GL_TEXTURE_2D, atlasTarget.getDepthTextureId());
        GLES20.glUniform1i(resolverAtlasDepth, 1);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(
                GLES20.GL_TEXTURE_2D, sceneSource.getDepthTextureId());
        GLES20.glUniform1i(resolverSceneDepth, 2);
        GLES20.glUniform1i(resolverSampleCount, combinedSampleCount);
        GLES20.glUniform1fv(
                resolverWeightsLocation,
                MAX_SAMPLES,
                resolverWeights,
                0);
        GLES20.glUniform2f(
                resolverAtlasSize,
                atlasTarget.getWidth(),
                atlasTarget.getHeight());
        GLES20.glUniform2f(
                resolverCellStride,
                atlasCellCapacityWidth,
                atlasCellCapacityHeight);
        GLES20.glUniform2f(resolverRoiSize, roiWidth, roiHeight);
        GLES20.glUniform2f(
                resolverSceneUvOrigin,
                roiX / (float) Math.max(1, bloomWidth),
                roiY / (float) Math.max(1, bloomHeight));
        GLES20.glUniform2f(
                resolverSceneUvScale,
                roiWidth / (float) Math.max(1, bloomWidth),
                roiHeight / (float) Math.max(1, bloomHeight));
        GLES20.glUniform2f(
                resolverSceneTexelStep,
                1f / Math.max(1, sceneSource.getWidth()),
                1f / Math.max(1, sceneSource.getHeight()));
        drawQuad();
        unbindQuadAttributes(resolverPosition, resolverUv);
        unbindTextureUnits();
    }

    private boolean hasMotionBandContribution(
            WheelTemporalSamplingPlanner.Plan plan) {
        return plan != null
                && plan.requiresMotionBand()
                && uploadedMotionBandGeometry != null
                && motionBandVbo != 0
                && motionBandIbo != 0
                && motionBandIndexCount > 0;
    }

    private int physicalSampleCount(
            WheelTemporalSamplingPlanner.Plan plan) {
        if (plan == null) {
            return 0;
        }
        // Without the optional new shell, retain the complete truthful groove exposure as a
        // compatibility fallback. With it, a full-band frame performs no physical atlas work.
        return plan.physicalAtlasSampleCount(
                hasMotionBandContribution(plan));
    }

    private void compositeResolved(
            RenderTarget destination,
            int left,
            int bottom,
            int right,
            int top,
            float intensity,
            boolean alphaComposite) {
        compositeResolved(
                destination,
                left,
                bottom,
                right,
                top,
                intensity,
                alphaComposite,
                null,
                1,
                1,
                0f);
    }

    private void compositeResolved(
            RenderTarget destination,
            int left,
            int bottom,
            int right,
            int top,
            float intensity,
            boolean alphaComposite,
            RenderTarget sceneSource,
            int bloomWidth,
            int bloomHeight,
            float bloomCorrectionBlend) {
        int clampedLeft = Math.max(0, Math.min(destination.getWidth(), left));
        int clampedBottom = Math.max(0, Math.min(destination.getHeight(), bottom));
        int clampedRight = Math.max(
                clampedLeft, Math.min(destination.getWidth(), right));
        int clampedTop = Math.max(
                clampedBottom, Math.min(destination.getHeight(), top));
        int width = clampedRight - clampedLeft;
        int height = clampedTop - clampedBottom;
        if (width <= 0 || height <= 0 || !(intensity > 0f)) {
            return;
        }

        destination.bind();
        GLES20.glViewport(clampedLeft, clampedBottom, width, height);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendEquation(GLES20.GL_FUNC_ADD);
        GLES20.glBlendFunc(
                GLES20.GL_ONE,
                alphaComposite
                        ? GLES20.GL_ONE_MINUS_SRC_ALPHA
                        : GLES20.GL_ONE);
        GLES20.glUseProgram(compositeProgram);
        bindQuadAttributes(compositePosition, compositeUv);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(
                GLES20.GL_TEXTURE_2D, resolvedTarget.getTextureId());
        GLES20.glUniform1i(compositeSource, 0);
        GLES20.glUniform2f(
                compositeUvOrigin,
                0.5f / resolvedTarget.getWidth(),
                0.5f / resolvedTarget.getHeight());
        GLES20.glUniform2f(
                compositeUvScale,
                roiWidth / (float) resolvedTarget.getWidth(),
                roiHeight / (float) resolvedTarget.getHeight());
        GLES20.glUniform1f(
                compositeIntensity,
                clamp01(intensity) / ATLAS_ENCODE_SCALE);
        GLES20.glUniform1f(
                compositeAlphaIntensity,
                alphaComposite ? clamp01(intensity) : 0f);
        GLES20.glUniform1i(
                compositeBloomCorrection,
                alphaComposite ? 0 : 1);
        GLES20.glUniform1f(
                compositeBloomCorrectionBlend,
                alphaComposite ? 0f : clamp01(bloomCorrectionBlend));
        GLES20.glUniform1f(
                compositeBloomThreshold,
                BloomConfig.BRIGHT_THRESHOLD);
        FColor emission = PlayerAssets.mintEmissionThemeColor();
        float emissionBrightFactor = WheelBloomEnergy.brightPassFactor(
                emission.r(),
                emission.g(),
                emission.b(),
                BloomConfig.BRIGHT_THRESHOLD);
        GLES20.glUniform1f(
                compositeEmissionBrightFactor,
                emissionBrightFactor);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(
                GLES20.GL_TEXTURE_2D,
                sceneSource == null ? 0 : sceneSource.getTextureId());
        GLES20.glUniform1i(compositeScene, 1);
        GLES20.glUniform2f(
                compositeSceneUvOrigin,
                roiX / (float) Math.max(1, bloomWidth),
                roiY / (float) Math.max(1, bloomHeight));
        GLES20.glUniform2f(
                compositeSceneUvScale,
                roiWidth / (float) Math.max(1, bloomWidth),
                roiHeight / (float) Math.max(1, bloomHeight));
        GLES20.glUniform2f(
                compositeSceneTexelStep,
                sceneSource == null
                        ? 1f
                        : 1f / Math.max(1, sceneSource.getWidth()),
                sceneSource == null
                        ? 1f
                        : 1f / Math.max(1, sceneSource.getHeight()));
        drawQuad();
        unbindQuadAttributes(compositePosition, compositeUv);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void ensureResources(
            int bloomWidth,
            int bloomHeight,
            PreparedModelData grooveGeometry,
            PreparedModelData motionBandGeometry,
            int requiredCellWidth,
            int requiredCellHeight) {
        requestedBloomWidth = Math.max(1, bloomWidth);
        requestedBloomHeight = Math.max(1, bloomHeight);
        if (!supported) {
            return;
        }
        if (resolverProgram == 0 || compositeProgram == 0
                || quadVbo == 0 || quadIbo == 0) {
            clearGlErrors();
            createFullscreenResources();
            validateFullscreenResources();
        }
        if (grooveProgram == 0 || uploadedGrooveGeometry != grooveGeometry
                || uploadedMotionBandGeometry != motionBandGeometry) {
            clearGlErrors();
            createGrooveResources(
                    grooveGeometry, motionBandGeometry);
            validateGrooveResources();
        }

        int minimumWidth = Math.max(
                Math.min(DEFAULT_CELL_CAPACITY_PIXELS, requestedBloomWidth),
                Math.max(1, requiredCellWidth));
        int minimumHeight = Math.max(
                Math.min(DEFAULT_CELL_CAPACITY_PIXELS, requestedBloomHeight),
                Math.max(1, requiredCellHeight));
        if (atlasTarget != null && resolvedTarget != null
                && atlasCellCapacityWidth >= minimumWidth
                && atlasCellCapacityHeight >= minimumHeight) {
            return;
        }

        int[] maximumTextureSize = new int[1];
        GLES20.glGetIntegerv(
                GLES20.GL_MAX_TEXTURE_SIZE, maximumTextureSize, 0);
        int maximum = Math.max(1, maximumTextureSize[0]);
        int maximumCellWidth = maximum / ATLAS_COLUMNS;
        int maximumCellHeight = maximum / ATLAS_ROWS;
        int nextWidth = alignedCapacity(
                Math.max(atlasCellCapacityWidth, minimumWidth));
        int nextHeight = alignedCapacity(
                Math.max(atlasCellCapacityHeight, minimumHeight));
        if (nextWidth > maximumCellWidth || nextHeight > maximumCellHeight) {
            supported = false;
            return;
        }

        deleteTargets();
        clearGlErrors();
        atlasCellCapacityWidth = nextWidth;
        atlasCellCapacityHeight = nextHeight;
        atlasTarget = new PortalRenderTarget(
                nextWidth * ATLAS_COLUMNS,
                nextHeight * ATLAS_ROWS,
                true,
                true);
        resolvedTarget = new PortalRenderTarget(nextWidth, nextHeight, false);
        if (!atlasTarget.isFramebufferComplete()
                || atlasTarget.getDepthTextureId() == 0
                || !resolvedTarget.isFramebufferComplete()) {
            supported = false;
            deleteTargets();
            return;
        }
        int glError = GLES20.glGetError();
        if (glError != GLES20.GL_NO_ERROR) {
            throw new IllegalStateException(
                    "Wheel temporal target setup failed with GL error 0x"
                            + Integer.toHexString(glError));
        }
    }

    private void validateFullscreenResources() {
        if (resolverProgram == 0 || compositeProgram == 0
                || quadVbo == 0 || quadIbo == 0
                || resolverPosition < 0 || resolverUv < 0
                || resolverAtlas < 0 || resolverAtlasDepth < 0
                || resolverSceneDepth < 0 || resolverSampleCount < 0
                || resolverWeightsLocation < 0 || resolverAtlasSize < 0
                || resolverCellStride < 0 || resolverRoiSize < 0
                || resolverSceneUvOrigin < 0 || resolverSceneUvScale < 0
                || resolverSceneTexelStep < 0
                || compositePosition < 0 || compositeUv < 0
                || compositeSource < 0 || compositeUvOrigin < 0
                || compositeUvScale < 0 || compositeIntensity < 0
                || compositeAlphaIntensity < 0 || compositeScene < 0
                || compositeSceneUvOrigin < 0
                || compositeSceneUvScale < 0
                || compositeSceneTexelStep < 0
                || compositeBloomThreshold < 0
                || compositeEmissionBrightFactor < 0
                || compositeBloomCorrection < 0
                || compositeBloomCorrectionBlend < 0) {
            throw new IllegalStateException(
                    "Wheel temporal fullscreen resources are incomplete");
        }
        int glError = GLES20.glGetError();
        if (glError != GLES20.GL_NO_ERROR) {
            throw new IllegalStateException(
                    "Wheel temporal fullscreen setup failed with GL error 0x"
                            + Integer.toHexString(glError));
        }
    }

    private void validateGrooveResources() {
        if (grooveProgram == 0 || grooveVbo == 0 || grooveIbo == 0
                || grooveIndexCount <= 0 || groovePosition < 0
                || grooveViewProjection < 0 || grooveModelLocation < 0
                || grooveColor < 0 || uploadedGrooveGeometry == null) {
            throw new IllegalStateException(
                    "Wheel temporal groove resources are incomplete");
        }
        if (uploadedMotionBandGeometry != null
                && (motionBandVbo == 0 || motionBandIbo == 0
                || motionBandIndexCount <= 0)) {
            throw new IllegalStateException(
                    "Wheel motion-band resources are incomplete");
        }
        int glError = GLES20.glGetError();
        if (glError != GLES20.GL_NO_ERROR) {
            throw new IllegalStateException(
                    "Wheel temporal groove setup failed with GL error 0x"
                            + Integer.toHexString(glError));
        }
    }

    private static void clearGlErrors() {
        for (int index = 0; index < 16; index++) {
            if (GLES20.glGetError() == GLES20.GL_NO_ERROR) {
                return;
            }
            // Drain errors from earlier, unrelated rendering before validating this setup block.
        }
    }

    private static int alignedCapacity(int value) {
        int remainder = value % CELL_GROWTH_ALIGNMENT_PIXELS;
        return remainder == 0
                ? value
                : value + CELL_GROWTH_ALIGNMENT_PIXELS - remainder;
    }

    private void createGrooveResources(
            PreparedModelData geometry,
            PreparedModelData motionBandGeometry) {
        deleteGrooveResources();
        if (geometry == null || geometry.verts() == null
                || geometry.faces() == null) {
            return;
        }

        grooveProgram = createProgram(
                GROOVE_VERTEX_SHADER, GROOVE_FRAGMENT_SHADER);
        groovePosition = GLES20.glGetAttribLocation(
                grooveProgram, "aPosition");
        grooveViewProjection = GLES20.glGetUniformLocation(
                grooveProgram, "uViewProjection");
        grooveModelLocation = GLES20.glGetUniformLocation(
                grooveProgram, "uModel");
        grooveColor = GLES20.glGetUniformLocation(
                grooveProgram, "uEmissionColor");

        uploadEmissionGeometry(geometry, false);
        if (motionBandGeometry != null) {
            uploadEmissionGeometry(motionBandGeometry, true);
        }
        uploadedGrooveGeometry = geometry;
        uploadedMotionBandGeometry = motionBandGeometry;
    }

    private void uploadEmissionGeometry(
            PreparedModelData geometry,
            boolean motionBand) {
        if (geometry == null || geometry.verts() == null
                || geometry.faces() == null) {
            return;
        }

        Vector3D[] sourceVertices = geometry.verts();
        FloatBuffer vertices = ByteBuffer
                .allocateDirect(sourceVertices.length * 3 * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        for (Vector3D vertex : sourceVertices) {
            vertices.put(vertex.x).put(vertex.y).put(vertex.z);
        }
        vertices.position(0);

        int maximumIndex = -1;
        int triangleCount = 0;
        for (int[] face : geometry.faces()) {
            if (face == null || face.length < 3) {
                continue;
            }
            triangleCount += face.length - 2;
            for (int index : face) {
                maximumIndex = Math.max(maximumIndex, index);
            }
        }
        int indexCount = triangleCount * 3;
        boolean useIntIndices = maximumIndex >= 65536
                || sourceVertices.length >= 65536;
        int indexType = useIntIndices
                ? GLES20.GL_UNSIGNED_INT
                : GLES20.GL_UNSIGNED_SHORT;
        Buffer indices;
        int indexBytes;
        if (useIntIndices) {
            IntBuffer values = ByteBuffer
                    .allocateDirect(indexCount * 4)
                    .order(ByteOrder.nativeOrder())
                    .asIntBuffer();
            appendTriangleFanIndices(geometry.faces(), values, null);
            values.position(0);
            indices = values;
            indexBytes = indexCount * 4;
        } else {
            ShortBuffer values = ByteBuffer
                    .allocateDirect(indexCount * 2)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer();
            appendTriangleFanIndices(geometry.faces(), null, values);
            values.position(0);
            indices = values;
            indexBytes = indexCount * 2;
        }

        int[] buffers = new int[2];
        GLES20.glGenBuffers(2, buffers, 0);
        int vertexBuffer = buffers[0];
        int indexBuffer = buffers[1];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuffer);
        GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER,
                sourceVertices.length * 3 * 4,
                vertices,
                GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        GLES20.glBufferData(
                GLES20.GL_ELEMENT_ARRAY_BUFFER,
                indexBytes,
                indices,
                GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        if (motionBand) {
            motionBandVbo = vertexBuffer;
            motionBandIbo = indexBuffer;
            motionBandIndexCount = indexCount;
            motionBandIndexType = indexType;
        } else {
            grooveVbo = vertexBuffer;
            grooveIbo = indexBuffer;
            grooveIndexCount = indexCount;
            grooveIndexType = indexType;
        }
    }

    private static void appendTriangleFanIndices(
            int[][] faces,
            IntBuffer intTarget,
            ShortBuffer shortTarget) {
        for (int[] face : faces) {
            if (face == null || face.length < 3) {
                continue;
            }
            for (int corner = 1; corner < face.length - 1; corner++) {
                if (intTarget != null) {
                    intTarget.put(face[0]);
                    intTarget.put(face[corner]);
                    intTarget.put(face[corner + 1]);
                } else {
                    shortTarget.put((short) face[0]);
                    shortTarget.put((short) face[corner]);
                    shortTarget.put((short) face[corner + 1]);
                }
            }
        }
    }

    private void beginGrooveDraws(float[] viewProjection) {
        FColor theme = PlayerAssets.mintEmissionThemeColor();
        GLES20.glUseProgram(grooveProgram);
        GLES20.glEnableVertexAttribArray(groovePosition);
        GLES20.glUniformMatrix4fv(
                grooveViewProjection, 1, false, viewProjection, 0);
        GLES20.glUniform3f(
                grooveColor,
                theme.r() * ATLAS_ENCODE_SCALE,
                theme.g() * ATLAS_ENCODE_SCALE,
                theme.b() * ATLAS_ENCODE_SCALE);
    }

    private void bindEmissionGeometry(
            int vertexBuffer,
            int indexBuffer) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuffer);
        GLES20.glBindBuffer(
                GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        GLES20.glVertexAttribPointer(
                groovePosition, 3, GLES20.GL_FLOAT, false, 3 * 4, 0);
    }

    private void drawEmissionGeometry(int indexCount, int indexType) {
        GLES20.glUniformMatrix4fv(
                grooveModelLocation, 1, false, grooveModel, 0);
        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                indexCount,
                indexType,
                0);
    }

    private void endGrooveDraws() {
        GLES20.glDisableVertexAttribArray(groovePosition);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private void createFullscreenResources() {
        deleteFullscreenResources();
        resolverProgram = createProgram(
                FULLSCREEN_VERTEX_SHADER, RESOLVE_FRAGMENT_SHADER);
        resolverPosition = GLES20.glGetAttribLocation(
                resolverProgram, "aPosition");
        resolverUv = GLES20.glGetAttribLocation(resolverProgram, "aUV");
        resolverAtlas = GLES20.glGetUniformLocation(
                resolverProgram, "uAtlas");
        resolverAtlasDepth = GLES20.glGetUniformLocation(
                resolverProgram, "uAtlasDepth");
        resolverSceneDepth = GLES20.glGetUniformLocation(
                resolverProgram, "uSceneDepth");
        resolverSampleCount = GLES20.glGetUniformLocation(
                resolverProgram, "uSampleCount");
        resolverWeightsLocation = GLES20.glGetUniformLocation(
                resolverProgram, "uWeights[0]");
        resolverAtlasSize = GLES20.glGetUniformLocation(
                resolverProgram, "uAtlasSize");
        resolverCellStride = GLES20.glGetUniformLocation(
                resolverProgram, "uCellStride");
        resolverRoiSize = GLES20.glGetUniformLocation(
                resolverProgram, "uRoiSize");
        resolverSceneUvOrigin = GLES20.glGetUniformLocation(
                resolverProgram, "uSceneUvOrigin");
        resolverSceneUvScale = GLES20.glGetUniformLocation(
                resolverProgram, "uSceneUvScale");
        resolverSceneTexelStep = GLES20.glGetUniformLocation(
                resolverProgram, "uSceneTexelStep");

        compositeProgram = createProgram(
                FULLSCREEN_VERTEX_SHADER, COMPOSITE_FRAGMENT_SHADER);
        compositePosition = GLES20.glGetAttribLocation(
                compositeProgram, "aPosition");
        compositeUv = GLES20.glGetAttribLocation(
                compositeProgram, "aUV");
        compositeSource = GLES20.glGetUniformLocation(
                compositeProgram, "uSource");
        compositeUvOrigin = GLES20.glGetUniformLocation(
                compositeProgram, "uUvOrigin");
        compositeUvScale = GLES20.glGetUniformLocation(
                compositeProgram, "uUvScale");
        compositeIntensity = GLES20.glGetUniformLocation(
                compositeProgram, "uIntensity");
        compositeAlphaIntensity = GLES20.glGetUniformLocation(
                compositeProgram, "uAlphaIntensity");
        compositeScene = GLES20.glGetUniformLocation(
                compositeProgram, "uScene");
        compositeSceneUvOrigin = GLES20.glGetUniformLocation(
                compositeProgram, "uSceneUvOrigin");
        compositeSceneUvScale = GLES20.glGetUniformLocation(
                compositeProgram, "uSceneUvScale");
        compositeSceneTexelStep = GLES20.glGetUniformLocation(
                compositeProgram, "uSceneTexelStep");
        compositeBloomThreshold = GLES20.glGetUniformLocation(
                compositeProgram, "uBloomThreshold");
        compositeEmissionBrightFactor = GLES20.glGetUniformLocation(
                compositeProgram, "uEmissionBrightFactor");
        compositeBloomCorrection = GLES20.glGetUniformLocation(
                compositeProgram, "uBloomCorrection");
        compositeBloomCorrectionBlend = GLES20.glGetUniformLocation(
                compositeProgram, "uBloomCorrectionBlend");

        int[] buffers = new int[2];
        GLES20.glGenBuffers(2, buffers, 0);
        quadVbo = buffers[0];
        quadIbo = buffers[1];
        FloatBuffer vertices = ByteBuffer
                .allocateDirect(QUAD_VERTICES.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertices.put(QUAD_VERTICES).position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo);
        GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER,
                QUAD_VERTICES.length * 4,
                vertices,
                GLES20.GL_STATIC_DRAW);
        ShortBuffer indices = ByteBuffer
                .allocateDirect(QUAD_INDICES.length * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
        indices.put(QUAD_INDICES).position(0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, quadIbo);
        GLES20.glBufferData(
                GLES20.GL_ELEMENT_ARRAY_BUFFER,
                QUAD_INDICES.length * 2,
                indices,
                GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private void bindQuadAttributes(int position, int uv) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, quadIbo);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glVertexAttribPointer(
                position, 3, GLES20.GL_FLOAT, false, 5 * 4, 0);
        GLES20.glEnableVertexAttribArray(uv);
        GLES20.glVertexAttribPointer(
                uv, 2, GLES20.GL_FLOAT, false, 5 * 4, 3 * 4);
    }

    private static void unbindQuadAttributes(int position, int uv) {
        GLES20.glDisableVertexAttribArray(position);
        GLES20.glDisableVertexAttribArray(uv);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private static void drawQuad() {
        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                QUAD_INDICES.length,
                GLES20.GL_UNSIGNED_SHORT,
                0);
    }

    private static void unbindTextureUnits() {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    private static void restoreBloomDestination(
            RenderTarget destination,
            int width,
            int height) {
        if (destination != null) {
            destination.bind();
        }
        GLES20.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
        GLES20.glColorMask(true, true, true, true);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);
        GLES20.glDepthFunc(GLES20.GL_LESS);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glClearColor(0f, 0f, 0f, 1f);
    }

    private static void restoreSceneDestination(RenderTarget destination) {
        if (destination != null) {
            destination.bind();
            GLES20.glViewport(
                    0, 0, destination.getWidth(), destination.getHeight());
        }
        GLES20.glColorMask(true, true, true, true);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(true);
        GLES20.glDepthFunc(GLES20.GL_LESS);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glClearColor(0f, 0f, 0f, 1f);
    }

    private void disableAfterFailure(RuntimeException failure) {
        boolean report = supported;
        supported = false;
        atlasFrameAvailable = false;
        deleteTargets();
        deleteFullscreenResources();
        deleteGrooveResources();
        if (report) {
            Log.e(TAG,
                    "Temporal wheel rendering disabled; using sharp fallback",
                    failure);
        }
    }

    private static void composeModelMatrix(
            float[] target,
            UnbatchedObject3DWithOutline wheel) {
        Matrix.setIdentityM(target, 0);
        Matrix.translateM(target, 0, wheel.objX, wheel.objY, wheel.objZ);
        Matrix.rotateM(target, 0, wheel.objYaw, 0f, 1f, 0f);
        Matrix.rotateM(target, 0, wheel.objPitch, 1f, 0f, 0f);
        Matrix.rotateM(target, 0, wheel.objRoll, 0f, 0f, 1f);
    }

    private static boolean project(
            float[] matrix,
            float x,
            float y,
            float z,
            int width,
            int height,
            float[] destination) {
        float clipX = matrix[0] * x + matrix[4] * y
                + matrix[8] * z + matrix[12];
        float clipY = matrix[1] * x + matrix[5] * y
                + matrix[9] * z + matrix[13];
        float clipW = matrix[3] * x + matrix[7] * y
                + matrix[11] * z + matrix[15];
        if (!Float.isFinite(clipX) || !Float.isFinite(clipY)
                || !Float.isFinite(clipW) || clipW <= CLIP_EPSILON) {
            return false;
        }
        destination[0] = (clipX / clipW * 0.5f + 0.5f) * width;
        destination[1] = (clipY / clipW * 0.5f + 0.5f) * height;
        return Float.isFinite(destination[0])
                && Float.isFinite(destination[1]);
    }

    private static float pixelDistance(float[] first, float[] second) {
        float dx = second[0] - first[0];
        float dy = second[1] - first[1];
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static int scaledLeft(int value, int sourceSize, int targetSize) {
        return (int) Math.floor(value * targetSize
                / (double) Math.max(1, sourceSize));
    }

    private static int scaledRight(int value, int sourceSize, int targetSize) {
        return (int) Math.ceil(value * targetSize
                / (double) Math.max(1, sourceSize));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        int previousWidth = requestedBloomWidth;
        int previousHeight = requestedBloomHeight;
        // Abandon old-context names. Deleting them in the new context can delete unrelated names
        // that the driver has already reused.
        resolverProgram = 0;
        compositeProgram = 0;
        quadVbo = 0;
        quadIbo = 0;
        grooveProgram = 0;
        grooveVbo = 0;
        grooveIbo = 0;
        uploadedGrooveGeometry = null;
        motionBandVbo = 0;
        motionBandIbo = 0;
        uploadedMotionBandGeometry = null;
        atlasTarget = null;
        resolvedTarget = null;
        atlasCellCapacityWidth = 0;
        atlasCellCapacityHeight = 0;
        atlasFrameAvailable = false;
        supported = true;
        requestedBloomWidth = 0;
        requestedBloomHeight = 0;
        preload(previousWidth, previousHeight);
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        deleteTargets();
        deleteFullscreenResources();
        deleteGrooveResources();
        requestedBloomWidth = 0;
        requestedBloomHeight = 0;
        atlasFrameAvailable = false;
        supported = true;
    }

    private void deleteTargets() {
        if (atlasTarget != null) {
            atlasTarget.cleanupGPUResourcesRecursively();
            atlasTarget = null;
        }
        if (resolvedTarget != null) {
            resolvedTarget.cleanupGPUResourcesRecursively();
            resolvedTarget = null;
        }
        atlasCellCapacityWidth = 0;
        atlasCellCapacityHeight = 0;
        atlasFrameAvailable = false;
    }

    private void deleteFullscreenResources() {
        if (resolverProgram != 0) {
            GLES20.glDeleteProgram(resolverProgram);
            resolverProgram = 0;
        }
        if (compositeProgram != 0) {
            GLES20.glDeleteProgram(compositeProgram);
            compositeProgram = 0;
        }
        if (quadVbo != 0) {
            GLES20.glDeleteBuffers(1, new int[]{quadVbo}, 0);
            quadVbo = 0;
        }
        if (quadIbo != 0) {
            GLES20.glDeleteBuffers(1, new int[]{quadIbo}, 0);
            quadIbo = 0;
        }
    }

    private void deleteGrooveResources() {
        if (grooveProgram != 0) {
            GLES20.glDeleteProgram(grooveProgram);
            grooveProgram = 0;
        }
        if (grooveVbo != 0) {
            GLES20.glDeleteBuffers(1, new int[]{grooveVbo}, 0);
            grooveVbo = 0;
        }
        if (grooveIbo != 0) {
            GLES20.glDeleteBuffers(1, new int[]{grooveIbo}, 0);
            grooveIbo = 0;
        }
        if (motionBandVbo != 0) {
            GLES20.glDeleteBuffers(1, new int[]{motionBandVbo}, 0);
            motionBandVbo = 0;
        }
        if (motionBandIbo != 0) {
            GLES20.glDeleteBuffers(1, new int[]{motionBandIbo}, 0);
            motionBandIbo = 0;
        }
        grooveIndexCount = 0;
        motionBandIndexCount = 0;
        uploadedGrooveGeometry = null;
        uploadedMotionBandGeometry = null;
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertex);
        GLES20.glAttachShader(program, fragment);
        GLES20.glLinkProgram(program);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
        if (linked[0] == 0) {
            String message = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new IllegalStateException(
                    "Wheel temporal program link failed: " + message);
        }
        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String message = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException(
                    "Wheel temporal shader compile failed: " + message);
        }
        return shader;
    }

    private static final String GROOVE_VERTEX_SHADER =
            "#version 300 es\n" +
            "uniform mat4 uViewProjection;\n" +
            "uniform mat4 uModel;\n" +
            "in vec3 aPosition;\n" +
            "void main(){\n" +
            "  gl_Position = uViewProjection * uModel * vec4(aPosition, 1.0);\n" +
            "}\n";

    private static final String GROOVE_FRAGMENT_SHADER =
            "#version 300 es\n" +
            "precision highp float;\n" +
            "uniform vec3 uEmissionColor;\n" +
            "out vec4 fragColor;\n" +
            "void main(){\n" +
            "  fragColor = vec4(uEmissionColor, 1.0);\n" +
            "}\n";

    private static final String FULLSCREEN_VERTEX_SHADER =
            "#version 300 es\n" +
            "in vec3 aPosition;\n" +
            "in vec2 aUV;\n" +
            "out vec2 vUV;\n" +
            "void main(){\n" +
            "  vUV = aUV;\n" +
            "  gl_Position = vec4(aPosition, 1.0);\n" +
            "}\n";

    private static final String RESOLVE_FRAGMENT_SHADER =
            "#version 300 es\n" +
            "precision highp float;\n" +
            "uniform sampler2D uAtlas;\n" +
            "uniform highp sampler2D uAtlasDepth;\n" +
            "uniform highp sampler2D uSceneDepth;\n" +
            "uniform int uSampleCount;\n" +
            "uniform float uWeights[12];\n" +
            "uniform vec2 uAtlasSize;\n" +
            "uniform vec2 uCellStride;\n" +
            "uniform vec2 uRoiSize;\n" +
            "uniform vec2 uSceneUvOrigin;\n" +
            "uniform vec2 uSceneUvScale;\n" +
            "uniform vec2 uSceneTexelStep;\n" +
            "in vec2 vUV;\n" +
            "out vec4 fragColor;\n" +
            "highp float conservativeSceneDepth(vec2 uv){\n" +
            "  vec2 guard = uSceneTexelStep * 1.5;\n" +
            "  vec2 lo = uSceneTexelStep * 0.5;\n" +
            "  vec2 hi = vec2(1.0) - lo;\n" +
            "  highp float d = texture(uSceneDepth, clamp(uv, lo, hi)).r;\n" +
            "  d = min(d, texture(uSceneDepth, clamp(uv + vec2( guard.x,  guard.y), lo, hi)).r);\n" +
            "  d = min(d, texture(uSceneDepth, clamp(uv + vec2(-guard.x,  guard.y), lo, hi)).r);\n" +
            "  d = min(d, texture(uSceneDepth, clamp(uv + vec2( guard.x, -guard.y), lo, hi)).r);\n" +
            "  d = min(d, texture(uSceneDepth, clamp(uv + vec2(-guard.x, -guard.y), lo, hi)).r);\n" +
            "  return d;\n" +
            "}\n" +
            "void main(){\n" +
            "  vec2 localPixel = vUV * uRoiSize;\n" +
            "  vec2 sceneUV = uSceneUvOrigin + vUV * uSceneUvScale;\n" +
            "  highp float sceneDepth = conservativeSceneDepth(sceneUV);\n" +
            "  vec4 exposure = vec4(0.0);\n" +
            "  for (int i = 0; i < 12; ++i) {\n" +
            "    if (i >= uSampleCount) { break; }\n" +
            "    int column = i - (i / 4) * 4;\n" +
            "    int row = i / 4;\n" +
            "    vec2 atlasPixel = vec2(float(column), float(row)) * uCellStride + localPixel;\n" +
            "    vec2 atlasUV = atlasPixel / uAtlasSize;\n" +
            "    highp float sampleDepth = texture(uAtlasDepth, atlasUV).r;\n" +
            "    float visible = sampleDepth <= sceneDepth + 0.00008 ? 1.0 : 0.0;\n" +
            "    exposure += texture(uAtlas, atlasUV) * uWeights[i] * visible;\n" +
            "  }\n" +
            "  fragColor = exposure;\n" +
            "}\n";

    private static final String COMPOSITE_FRAGMENT_SHADER =
            "#version 300 es\n" +
            "precision highp float;\n" +
            "uniform sampler2D uSource;\n" +
            "uniform vec2 uUvOrigin;\n" +
            "uniform vec2 uUvScale;\n" +
            "uniform float uIntensity;\n" +
            "uniform float uAlphaIntensity;\n" +
            "uniform sampler2D uScene;\n" +
            "uniform vec2 uSceneUvOrigin;\n" +
            "uniform vec2 uSceneUvScale;\n" +
            "uniform vec2 uSceneTexelStep;\n" +
            "uniform float uBloomThreshold;\n" +
            "uniform float uEmissionBrightFactor;\n" +
            "uniform int uBloomCorrection;\n" +
            "uniform float uBloomCorrectionBlend;\n" +
            "in vec2 vUV;\n" +
            "out vec4 fragColor;\n" +
            "vec3 extractBright(vec3 color){\n" +
            "  float peak = max(max(color.r, color.g), color.b);\n" +
            "  float factor = max((peak - uBloomThreshold)\n" +
            "      / max(1e-4, 1.0 - uBloomThreshold), 0.0);\n" +
            "  return color * factor;\n" +
            "}\n" +
            "void main(){\n" +
            "  vec2 sourceLo = uUvOrigin;\n" +
            "  vec2 sourceHi = uUvScale - uUvOrigin;\n" +
            "  vec2 sourceUV = clamp(vUV * uUvScale, sourceLo, sourceHi);\n" +
            "  vec4 exposure = texture(uSource, sourceUV);\n" +
            "  if (uBloomCorrection != 0) {\n" +
            "    vec2 sceneUV = uSceneUvOrigin + vUV * uSceneUvScale;\n" +
            "    vec2 d = uSceneTexelStep;\n" +
            "    vec3 ordinary = extractBright(texture(uScene, sceneUV + vec2(-d.x, -d.y)).rgb);\n" +
            "    ordinary += extractBright(texture(uScene, sceneUV + vec2( d.x, -d.y)).rgb);\n" +
            "    ordinary += extractBright(texture(uScene, sceneUV + vec2(-d.x,  d.y)).rgb);\n" +
            "    ordinary += extractBright(texture(uScene, sceneUV + vec2( d.x,  d.y)).rgb);\n" +
            "    ordinary *= 0.25;\n" +
            "    vec2 sourceD = d * uUvScale / max(uSceneUvScale, vec2(1e-6));\n" +
            "    vec3 decodedExposure = texture(uSource, clamp(sourceUV + vec2(-sourceD.x, -sourceD.y), sourceLo, sourceHi)).rgb;\n" +
            "    decodedExposure += texture(uSource, clamp(sourceUV + vec2( sourceD.x, -sourceD.y), sourceLo, sourceHi)).rgb;\n" +
            "    decodedExposure += texture(uSource, clamp(sourceUV + vec2(-sourceD.x,  sourceD.y), sourceLo, sourceHi)).rgb;\n" +
            "    decodedExposure += texture(uSource, clamp(sourceUV + vec2( sourceD.x,  sourceD.y), sourceLo, sourceHi)).rgb;\n" +
            "    decodedExposure *= 0.25 * uIntensity;\n" +
            "    vec3 target = decodedExposure * uEmissionBrightFactor;\n" +
            "    vec3 residual = max(target - ordinary, vec3(0.0));\n" +
            "    fragColor = vec4(residual * uBloomCorrectionBlend, 0.0);\n" +
            "  } else {\n" +
            "    fragColor = vec4(exposure.rgb * uIntensity,\n" +
            "                     exposure.a * uAlphaIntensity);\n" +
            "  }\n" +
            "}\n";
}
