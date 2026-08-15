package com.example.game3d_opengl.game.terrain.track_elements.portal;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.addon.Portal;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * OpenGL presentation of a renderer-neutral core portal addon.
 *
 * <p>The underlying meshes remain shared, while center/orientation/animation are owned by this
 * presentation instance.</p>
 */
public final class PortalRenderer {
    private final Portal addon;
    private final PortalVisual visual;

    public PortalRenderer(
            Portal addon, Vec3 renderOrigin, PortalRenderResources resources) {
        if (addon == null || resources == null) {
            throw new IllegalArgumentException("addon/resources cannot be null");
        }
        this.addon = addon;
        this.visual = new PortalVisual(resources);
        visual.setOuterDimensions((float) addon.width, (float) addon.height);
        visual.setLookDirection(vector(addon.forward));
        visual.setUpDirection(vector(addon.up));
        visual.setVisualStyle(addon.visualStyleId);
        setRenderOrigin(renderOrigin);
    }

    public long addonId() {
        return addon.id();
    }

    public void setRenderOrigin(Vec3 renderOrigin) {
        Vec3 origin = renderOrigin == null ? Vec3.ZERO : renderOrigin;
        visual.setCenter(new Vector3D(
                (float) (addon.center.x - origin.x),
                (float) (addon.center.y - origin.y),
                (float) (addon.center.z - origin.z)));
    }

    public void update(float dtMillis) {
        visual.update(dtMillis);
    }

    public void draw(float[] vp) {
        visual.draw(vp);
    }

    private static Vector3D vector(Vec3 value) {
        return new Vector3D((float) value.x, (float) value.y, (float) value.z);
    }
}
