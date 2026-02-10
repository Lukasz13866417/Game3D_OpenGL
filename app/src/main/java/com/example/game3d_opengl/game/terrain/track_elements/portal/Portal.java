package com.example.game3d_opengl.game.terrain.track_elements.portal;

import static com.example.game3d_opengl.game.util.GameMath.getNormal;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.track_elements.portal.rendering.PortalQuadDrawArgs;
import com.example.game3d_opengl.game.terrain.track_elements.portal.rendering.PortalQuadMesh;
import com.example.game3d_opengl.game.terrain.track_elements.portal.rendering.PortalRenderTarget;
import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.rendering.ScreenInfo;
import com.example.game3d_opengl.rendering.infill.Mesh3DInfill;
import com.example.game3d_opengl.rendering.mesh.MVPDrawArgs;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public final class Portal extends Addon {
    private static final float DEFAULT_EYE_OFFSET = 0.25f;
    private static final float DEFAULT_LOOK_DISTANCE = 6.0f;
    private static final float DEFAULT_WIDTH = 1.2f;
    private static final float DEFAULT_HEIGHT = 2.0f;
    private static final float DEFAULT_TARGET_SCALE = 0.5f;
    private static final float FRAME_THICKNESS_FRACTION = 0.06f;
    private static final FColor FRAME_COLOR = FColor.CLR(0.95f, 0.95f, 1f, 1f);

    private Vector3D entranceCenter;
    private Vector3D entranceNormal;
    private Vector3D entranceUp;
    private Vector3D exitCenter;
    private Vector3D exitNormal;
    private Vector3D exitUp;
    private final float width;
    private final float height;
    private final ExitPortal exitPortal;
    private boolean placed = false;

    private final PortalRenderTarget renderTarget;
    private PortalQuadMesh quadMesh;
    private Mesh3DInfill frameMesh;
    private final PortalQuadDrawArgs drawArgs = new PortalQuadDrawArgs();
    private final MVPDrawArgs frameArgs = new MVPDrawArgs(new float[16]);
    private final Camera exitCamera = new Camera();
    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    public static Portal createPortal(ExitPortal exitPortal) {
        float w = exitPortal != null ? exitPortal.getWidth() : DEFAULT_WIDTH;
        float h = exitPortal != null ? exitPortal.getHeight() : DEFAULT_HEIGHT;
        int targetW = Math.max(1, Math.round(ScreenInfo.getScreenW() * DEFAULT_TARGET_SCALE));
        int targetH = Math.max(1, Math.round(ScreenInfo.getScreenH() * DEFAULT_TARGET_SCALE));
        return new Portal(exitPortal, w, h, targetW, targetH);
    }

    private Portal(ExitPortal exitPortal, float width, float height, int targetW, int targetH) {
        this.exitPortal = exitPortal;
        this.width = width;
        this.height = height;
        this.entranceCenter = new Vector3D(0f, 0f, 0f);
        this.entranceNormal = new Vector3D(0f, 0f, 1f);
        this.entranceUp = new Vector3D(0f, 1f, 0f);
        this.exitCenter = new Vector3D(0f, 0f, 0f);
        this.exitNormal = new Vector3D(0f, 0f, 1f);
        this.exitUp = new Vector3D(0f, 1f, 0f);
        this.renderTarget = new PortalRenderTarget(targetW, targetH);
        buildLocalQuadMesh();
        exitCamera.setProjection(targetW, targetH);
    }

    public PortalRenderTarget getRenderTarget() {
        return renderTarget;
    }

    public float[] getExitViewProjectionMatrix() {
        updateExitCamera();
        return exitCamera.getViewProjectionMatrix();
    }

    private void updateExitCamera() {
        Vector3D center = exitPortal != null ? exitPortal.getCenter() : exitCenter;
        Vector3D normal = exitPortal != null ? exitPortal.getPortalNormal() : exitNormal;
        Vector3D up = exitPortal != null ? exitPortal.getUp() : exitUp;
        if (center == null || normal == null || up == null) return;
        Vector3D forward = normal.normalized();
        Vector3D eye = center.add(forward.withLen(DEFAULT_EYE_OFFSET));
        Vector3D look = center.add(forward.withLen(DEFAULT_LOOK_DISTANCE));
        exitCamera.set(eye, look, up);
    }

    private void buildLocalQuadMesh() {
        if (quadMesh != null) {
            quadMesh.cleanupGPUResourcesRecursivelyOnContextLoss();
        }
        float halfW = width * 0.5f;
        float halfH = height * 0.5f;
        Vector3D[] verts = new Vector3D[]{
                new Vector3D(-halfW, -halfH, 0f),
                new Vector3D(halfW, -halfH, 0f),
                new Vector3D(halfW, halfH, 0f),
                new Vector3D(-halfW, halfH, 0f)
        };
        int[][] faces = new int[][]{
                new int[]{0, 1, 2},
                new int[]{0, 2, 3}
        };
        float[] uvs = new float[]{
                0f, 0f,
                1f, 0f,
                1f, 1f,
                0f, 1f
        };
        quadMesh = new PortalQuadMesh.Builder()
                .verts(verts)
                .faces(faces)
                .uvs(uvs)
                .buildObject();

        rebuildFrameMeshes();
    }

    private void rebuildFrameMeshes() {
        if (frameMesh != null) {
            frameMesh.cleanupGPUResourcesRecursivelyOnContextLoss();
        }
        float thickness = Math.max(0.03f, Math.min(width, height) * FRAME_THICKNESS_FRACTION);
        frameMesh = buildFrameMesh(
                width + 2f * thickness, height + 2f * thickness,
                width, height,
                FRAME_COLOR
        );
    }

    private Mesh3DInfill buildFrameMesh(float outerW, float outerH,
                                        float innerW, float innerH,
                                        FColor color) {
        float z = 0.002f;
        float oW = outerW * 0.5f;
        float oH = outerH * 0.5f;
        float iW = innerW * 0.5f;
        float iH = innerH * 0.5f;
        Vector3D[] verts = new Vector3D[]{
                new Vector3D(-oW, -oH, z), // 0 outer BL
                new Vector3D(oW, -oH, z),  // 1 outer BR
                new Vector3D(oW, oH, z),   // 2 outer TR
                new Vector3D(-oW, oH, z),  // 3 outer TL
                new Vector3D(-iW, -iH, z), // 4 inner BL
                new Vector3D(iW, -iH, z),  // 5 inner BR
                new Vector3D(iW, iH, z),   // 6 inner TR
                new Vector3D(-iW, iH, z)   // 7 inner TL
        };
        int[][] faces = new int[][]{
                new int[]{0, 1, 5, 4}, // bottom
                new int[]{1, 2, 6, 5}, // right
                new int[]{2, 3, 7, 6}, // top
                new int[]{3, 0, 4, 7}  // left
        };
        return new Mesh3DInfill.Builder()
                .verts(verts)
                .faces(faces)
                .fillColor(color)
                .buildObject();
    }

    @Override
    public void updateBeforeDraw(float dt) {
    }

    @Override
    public void updateAfterDraw(float dt) {
    }

    @Override
    public void draw(float[] vp) {
        if (quadMesh == null) return;
        updateModelMatrix();
        Matrix.multiplyMM(mvpMatrix, 0, vp, 0, modelMatrix, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, renderTarget.getTextureId());
        drawArgs.vp = mvpMatrix;
        drawArgs.textureUnit = 0;
        quadMesh.draw(drawArgs);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        frameArgs.setMvp(mvpMatrix);
        if (frameMesh != null) frameMesh.draw(frameArgs);
    }

    public void setEntrance(Vector3D center, Vector3D normal, Vector3D up) {
        if (center != null) entranceCenter = center;
        if (normal != null) entranceNormal = normal;
        if (up != null) entranceUp = up;
        placed = true;
    }

    public void setExit(Vector3D center, Vector3D normal, Vector3D up) {
        if (center != null) exitCenter = center;
        if (normal != null) exitNormal = normal;
        if (up != null) exitUp = up;
    }

    private void updateModelMatrix() {
        Vector3D fwd = entranceNormal.normalized();
        Vector3D up = entranceUp.normalized();
        Vector3D right = fwd.crossProduct(up).normalized();
        Vector3D orthoUp = right.crossProduct(fwd).normalized();

        Matrix.setIdentityM(modelMatrix, 0);
        modelMatrix[0] = right.x;
        modelMatrix[1] = right.y;
        modelMatrix[2] = right.z;
        modelMatrix[4] = orthoUp.x;
        modelMatrix[5] = orthoUp.y;
        modelMatrix[6] = orthoUp.z;
        modelMatrix[8] = fwd.x;
        modelMatrix[9] = fwd.y;
        modelMatrix[10] = fwd.z;
        modelMatrix[12] = entranceCenter.x;
        modelMatrix[13] = entranceCenter.y;
        modelMatrix[14] = entranceCenter.z;
    }

    @Override
    public void rebasePosition(Vector3D delta) {
        if (delta == null) return;
        entranceCenter = entranceCenter.add(delta);
        exitCenter = exitCenter.add(delta);
    }

    public boolean isPlaced() {
        return placed;
    }

    public boolean canRenderExitView() {
        if (exitPortal == null) return true;
        return exitPortal.isPlaced();
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
        Vector3D nearMid = fieldNearLeft.add(fieldNearRight).div(2);
        Vector3D farMid = fieldFarLeft.add(fieldFarRight).div(2);
        Vector3D trackDir = farMid.sub(nearMid);
        if (trackDir.sqlen() < 1e-6f) {
            trackDir = new Vector3D(0f, 0f, -1f);
        } else {
            trackDir = trackDir.withLen(1f);
        }
        Vector3D terrainNormal = getNormal(fieldNearLeft, fieldFarLeft, fieldFarRight).normalized();
        if (terrainNormal.sqlen() < 1e-6f) {
            terrainNormal = new Vector3D(0f, 1f, 0f);
        }
        Vector3D normal = trackDir.mult(-1f);
        Vector3D up = terrainNormal;
        setEntrance(center, normal, up);
        placed = true;
    }

    @Override
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {
        if (quadMesh != null) {
            quadMesh.cleanupGPUResourcesRecursivelyOnContextLoss();
        }
        if (frameMesh != null) {
            frameMesh.cleanupGPUResourcesRecursivelyOnContextLoss();
        }
        renderTarget.cleanupGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        if (quadMesh != null) {
            quadMesh.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (frameMesh != null) {
            frameMesh.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        renderTarget.reloadGPUResourcesRecursivelyOnContextLoss();
    }
}
