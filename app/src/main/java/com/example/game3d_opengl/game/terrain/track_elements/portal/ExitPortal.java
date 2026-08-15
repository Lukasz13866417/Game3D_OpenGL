package com.example.game3d_opengl.game.terrain.track_elements.portal;

import static com.example.game3d_opengl.game.util.GameMath.getNormal;

import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.track_elements.portal.assets.PortalAsset;
import com.example.game3d_opengl.game.terrain.track_elements.portal.assets.PortalAssets;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/** @deprecated Mutable compatibility addon for the legacy terrain diagnostics. */
@Deprecated
public final class ExitPortal extends Addon {
    private static final Vector3D WORLD_UP = new Vector3D(0f, 1f, 0f);

    private Vector3D center;
    private Vector3D normal;
    private Vector3D up;
    private float width;
    private float height;
    private boolean placed = false;
    private final PortalAsset asset;
    private PortalVisual visual;

    public static ExitPortal createExitPortal() {
        float defaultDiameter = PortalConfig.DEFAULT_WIDTH_WORLD;
        return new ExitPortal(defaultDiameter, defaultDiameter);
    }

    public static ExitPortal createExitPortal(float width, float height) {
        return new ExitPortal(width, height);
    }

    private ExitPortal(float width, float height) {
        this.center = new Vector3D(0f, 0f, 0f);
        this.normal = new Vector3D(0f, 0f, 1f);
        this.up = new Vector3D(0f, 1f, 0f);
        this.width = width;
        this.height = height;
        this.asset = PortalAssets.createPortalAsset();
        setDimensions(width, height);
    }

    private void setDimensions(float width, float height) {
        this.width = Math.max(PortalConfig.MIN_REGION_WIDTH, width);
        this.height = Math.max(PortalConfig.MIN_REGION_WIDTH, height);
        float outerRadius = 0.5f * Math.min(this.width, this.height);
        if (visual != null) {
            visual.setBaseOuterRadius(outerRadius);
        }
    }

    public void setTransform(Vector3D center, Vector3D normal, Vector3D up) {
        if (center != null) this.center = center;
        if (normal != null) this.normal = normal;
        if (up != null) this.up = up;
        if (visual != null) {
            visual.setCenter(this.center);
            visual.setLookDirection(this.normal);
        }
        placed = true;
    }

    public Vector3D getCenter() {
        return center;
    }

    public Vector3D getPortalNormal() {
        return normal;
    }

    public Vector3D getUp() {
        return up;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public boolean isPlaced() {
        return placed;
    }

    public PortalAsset getAsset() {
        return asset;
    }

    @Override
    public void updateBeforeDraw(float dt) {
        if (visual != null) {
            visual.update(dt);
        }
    }

    @Override
    public void updateAfterDraw(float dt) {
    }

    @Override
    public void draw(float[] vp) {
        if (!placed) {
            return;
        }
        ensureVisual();
        visual.setCenter(center);
        visual.draw(vp);
    }

    @Override
    public void rebasePosition(Vector3D delta) {
        if (delta == null) return;
        center = center.add(delta);
    }

    @Override
    public void accept(Player player) {
        // Legacy portals were visual-only.
    }

    @Override
    protected void onPlace(Vector3D fieldNearLeft,
                           Vector3D fieldNearRight,
                           Vector3D fieldFarLeft,
                           Vector3D fieldFarRight) {
        onPlace(
                fieldNearLeft.x, fieldNearLeft.y, fieldNearLeft.z,
                fieldNearRight.x, fieldNearRight.y, fieldNearRight.z,
                fieldFarLeft.x, fieldFarLeft.y, fieldFarLeft.z,
                fieldFarRight.x, fieldFarRight.y, fieldFarRight.z
        );
    }

    @Override
    protected void onPlace(float nearLeftX, float nearLeftY, float nearLeftZ,
                           float nearRightX, float nearRightY, float nearRightZ,
                           float farLeftX, float farLeftY, float farLeftZ,
                           float farRightX, float farRightY, float farRightZ) {
        float centerX = 0.25f * (farLeftX + farRightX + nearLeftX + nearRightX);
        float centerY = 0.25f * (farLeftY + farRightY + nearLeftY + nearRightY);
        float centerZ = 0.25f * (farLeftZ + farRightZ + nearLeftZ + nearRightZ);

        float[] lookDir = new float[3];
        PortalPlacementUtils.computeHorizontalLookDirection(
                nearLeftX, nearLeftY, nearLeftZ,
                nearRightX, nearRightY, nearRightZ,
                farLeftX, farLeftY, farLeftZ,
                farRightX, farRightY, farRightZ,
                lookDir
        );

        setDimensions(
                PortalConfig.DEFAULT_WIDTH_WORLD,
                PortalConfig.DEFAULT_WIDTH_WORLD
        );
        float lift = 0.5f * Math.min(width, height)
                + PortalConfig.BASE_CLEARANCE;
        Vector3D liftedCenter = new Vector3D(centerX, centerY + lift, centerZ);
        Vector3D normal = new Vector3D(lookDir[0], lookDir[1], lookDir[2]);
        setTransform(liftedCenter, normal, WORLD_UP);
        placed = true;
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        if (visual != null) {
            visual.cleanupGPUResourcesRecursively();
        }
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        if (visual != null) {
            visual.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    private void ensureVisual() {
        if (visual != null) {
            return;
        }
        visual = new PortalVisual(asset);
        visual.setBaseOuterRadius(0.5f * Math.min(width, height));
        visual.setCenter(center);
        visual.setLookDirection(normal);
    }
}
