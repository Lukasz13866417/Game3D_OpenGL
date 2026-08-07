package com.example.game3d_opengl.game.terrain.track_elements.portal;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainFeatureSpec;
import com.example.game3d_opengl.game.terrain.track_elements.portal.assets.PortalAssets;
import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * OpenGL presentation of a renderer-neutral canonical portal specification.
 *
 * <p>The underlying meshes remain shared, while center/orientation/animation are owned by this
 * presentation instance.</p>
 */
public final class CanonicalPortalVisual implements GPUResourceOwner {
    private final TerrainFeatureSpec.Portal spec;
    private final PortalVisual visual;

    public CanonicalPortalVisual(TerrainFeatureSpec.Portal spec, Vec3 renderOrigin) {
        if (spec == null) {
            throw new IllegalArgumentException("spec == null");
        }
        this.spec = spec;
        this.visual = new PortalVisual(PortalAssets.createPortalAsset());
        visual.setBaseOuterRadius(
                (float) (0.5 * Math.min(spec.width, spec.height)));
        visual.setLookDirection(vector(spec.forward));
        setRenderOrigin(renderOrigin);
    }

    public long featureId() {
        return spec.id;
    }

    public static void warmUpSharedGpuAssets() {
        PortalVisual.warmUpSharedGpuResources();
    }

    public static boolean areSharedGpuAssetsReady() {
        return PortalVisual.sharedGpuResourcesReady();
    }

    public static void reloadSharedGpuAssets() {
        PortalVisual.reloadSharedGpuResources();
    }

    public static void cleanupSharedGpuAssets() {
        PortalVisual.cleanupSharedGpuResources();
    }

    public static void markSharedGpuAssetsDirty() {
        PortalVisual.markSharedGpuResourcesDirty();
    }

    public void setRenderOrigin(Vec3 renderOrigin) {
        Vec3 origin = renderOrigin == null ? Vec3.ZERO : renderOrigin;
        visual.setCenter(new Vector3D(
                (float) (spec.center.x - origin.x),
                (float) (spec.center.y - origin.y),
                (float) (spec.center.z - origin.z)));
    }

    public void update(float dtMillis) {
        visual.update(dtMillis);
    }

    public void draw(float[] vp) {
        visual.draw(vp);
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        reloadSharedGpuAssets();
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        cleanupSharedGpuAssets();
    }

    private static Vector3D vector(Vec3 value) {
        return new Vector3D((float) value.x, (float) value.y, (float) value.z);
    }
}
