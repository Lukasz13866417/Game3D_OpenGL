package com.example.game3d_opengl.game.terrain.track_elements.portal;

import static com.example.game3d_opengl.game.util.GameMath.getNormal;

import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.track_elements.portal.assets.PortalAsset;
import com.example.game3d_opengl.game.terrain.track_elements.portal.assets.PortalAssets;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public final class Portal extends Addon {
    private Vector3D entranceCenter;
    private float width;
    private float height;
    private boolean placed = false;
    private final PortalVisual visual;

    public static Portal createPortal(ExitPortal exitPortal) {
        float diameter = exitPortal != null
                ? Math.min(exitPortal.getWidth(), exitPortal.getHeight())
                : PortalConfig.DEFAULT_WIDTH_WORLD;
        PortalAsset asset = exitPortal != null
                ? exitPortal.getAsset()
                : PortalAssets.createPortalAsset();
        return new Portal(diameter, diameter, asset);
    }

    private Portal(float width, float height, PortalAsset asset) {
        this.width = width;
        this.height = height;
        this.entranceCenter = new Vector3D(0f, 0f, 0f);
        this.visual = new PortalVisual(asset);
        setDimensions(width, height);
        visual.setCenter(this.entranceCenter);
    }

    private void setDimensions(float width, float height) {
        this.width = Math.max(PortalConfig.MIN_REGION_WIDTH, width);
        this.height = Math.max(PortalConfig.MIN_REGION_WIDTH, height);
        float outerRadius = 0.5f * Math.min(this.width, this.height);
        visual.setBaseOuterRadius(outerRadius);
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
        visual.setCenter(entranceCenter);
        visual.draw(vp);
    }

    public void setEntrance(Vector3D center) {
        if (center != null) {
            entranceCenter = center;
            visual.setCenter(entranceCenter);
        }
        placed = true;
    }

    @Override
    public void rebasePosition(Vector3D delta) {
        if (delta == null) return;
        entranceCenter = entranceCenter.add(delta);
    }

    public boolean isPlaced() {
        return placed;
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
        visual.setLookDirection(lookDir);
        setDimensions(
                PortalConfig.DEFAULT_WIDTH_WORLD,
                PortalConfig.DEFAULT_WIDTH_WORLD
        );
        Vector3D liftedCenter = center.add(
                terrainNormal.withLen(visual.getMaxExpectedOuterRadius() + PortalConfig.BASE_CLEARANCE)
        );
        setEntrance(liftedCenter);
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
