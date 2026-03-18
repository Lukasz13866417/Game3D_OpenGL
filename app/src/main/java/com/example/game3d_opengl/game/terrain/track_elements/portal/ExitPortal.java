package com.example.game3d_opengl.game.terrain.track_elements.portal;

import static com.example.game3d_opengl.game.util.GameMath.getNormal;

import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.track_elements.portal.assets.PortalAsset;
import com.example.game3d_opengl.game.terrain.track_elements.portal.assets.PortalAssets;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public final class ExitPortal extends Addon {
    private static final Vector3D WORLD_UP = new Vector3D(0f, 1f, 0f);

    private Vector3D center;
    private Vector3D normal;
    private Vector3D up;
    private float width;
    private float height;
    private boolean placed = false;
    private final PortalAsset asset;
    private final PortalVisual visual;

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
        this.visual = new PortalVisual(asset);
        setDimensions(width, height);
    }

    private void setDimensions(float width, float height) {
        this.width = Math.max(PortalConfig.MIN_REGION_WIDTH, width);
        this.height = Math.max(PortalConfig.MIN_REGION_WIDTH, height);
        float outerRadius = 0.5f * Math.min(this.width, this.height);
        visual.setBaseOuterRadius(outerRadius);
    }

    public void setTransform(Vector3D center, Vector3D normal, Vector3D up) {
        if (center != null) this.center = center;
        if (normal != null) this.normal = normal;
        if (up != null) this.up = up;
        visual.setCenter(this.center);
        visual.setLookDirection(this.normal);
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
        visual.update(dt);
    }

    @Override
    public void updateAfterDraw(float dt) {
    }

    @Override
    public void draw(float[] vp) {
        if (!placed) {
            return;
        }
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
        if (player != null) {
            player.interactWith(this);
        }
    }

    @Override
    protected void onPlace(Vector3D fieldNearLeft,
                           Vector3D fieldNearRight,
                           Vector3D fieldFarLeft,
                           Vector3D fieldFarRight) {
        Vector3D center = fieldFarLeft.add(fieldFarRight).add(fieldNearLeft).add(fieldNearRight).div(4);
        Vector3D lookDir = PortalPlacementUtils.computeHorizontalLookDirection(
                fieldNearLeft, fieldNearRight, fieldFarLeft, fieldFarRight
        );
        Vector3D terrainNormal = getNormal(fieldNearLeft, fieldFarLeft, fieldFarRight).normalized();
        if (terrainNormal.sqlen() < 1e-6f) {
            terrainNormal = new Vector3D(0f, 1f, 0f);
        }
        if (terrainNormal.y < 0f) {
            terrainNormal = terrainNormal.mult(-1f);
        }
        Vector3D normal = lookDir;
        Vector3D up = WORLD_UP;
        setDimensions(
                PortalConfig.DEFAULT_WIDTH_WORLD,
                PortalConfig.DEFAULT_WIDTH_WORLD
        );
        Vector3D liftedCenter = center.add(
                up.withLen(visual.getMaxExpectedOuterRadius() + PortalConfig.BASE_CLEARANCE)
        );
        setTransform(liftedCenter, normal, up);
        placed = true;
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        visual.cleanupGPUResourcesRecursively();
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        visual.reloadGPUResourcesRecursivelyOnContextLoss();
    }
}
