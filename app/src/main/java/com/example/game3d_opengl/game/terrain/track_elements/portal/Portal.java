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
    private final PortalAsset asset;
    private PortalVisual visual;
    private final ExitPortal pairedExit;
    private Vector3D lookDirection = new Vector3D(0f, 0f, 1f);
    private Vector3D up = new Vector3D(0f, 1f, 0f);

    public static Portal createPortal(ExitPortal exitPortal) {
        float diameter = exitPortal != null
                ? Math.min(exitPortal.getWidth(), exitPortal.getHeight())
                : PortalConfig.DEFAULT_WIDTH_WORLD;
        PortalAsset asset = exitPortal != null
                ? exitPortal.getAsset()
                : PortalAssets.createPortalAsset();
        return new Portal(diameter, diameter, asset, exitPortal);
    }

    private Portal(float width, float height, PortalAsset asset, ExitPortal pairedExit) {
        this.width = width;
        this.height = height;
        this.entranceCenter = new Vector3D(0f, 0f, 0f);
        this.asset = asset;
        this.pairedExit = pairedExit;
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
        visual.setCenter(entranceCenter);
        visual.draw(vp);
    }

    public void setEntrance(Vector3D center) {
        if (center != null) {
            entranceCenter = center;
            if (visual != null) {
                visual.setCenter(entranceCenter);
            }
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

    public Vector3D getEntranceCenter() {
        return entranceCenter;
    }

    public Vector3D getLookDirection() {
        return lookDirection;
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

    public ExitPortal getPairedExit() {
        return pairedExit;
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
        if (normalLen < 1e-6f) {
            normalX = 0f;
            normalY = 1f;
            normalZ = 0f;
        } else {
            float invLen = 1f / normalLen;
            normalX *= invLen;
            normalY *= invLen;
            normalZ *= invLen;
        }
        if (normalY < 0f) {
            normalX = -normalX;
            normalY = -normalY;
            normalZ = -normalZ;
        }

        lookDirection = new Vector3D(lookDir[0], lookDir[1], lookDir[2]);
        if (visual != null) {
            visual.setLookDirection(lookDirection);
        }
        setDimensions(
                PortalConfig.DEFAULT_WIDTH_WORLD,
                PortalConfig.DEFAULT_WIDTH_WORLD
        );
        float lift = 0.5f * Math.min(width, height)
                + PortalConfig.BASE_CLEARANCE;
        Vector3D liftedCenter = new Vector3D(
                centerX + normalX * lift,
                centerY + normalY * lift,
                centerZ + normalZ * lift
        );
        setEntrance(liftedCenter);
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
        visual.setCenter(entranceCenter);
        visual.setLookDirection(lookDirection);
    }
}
