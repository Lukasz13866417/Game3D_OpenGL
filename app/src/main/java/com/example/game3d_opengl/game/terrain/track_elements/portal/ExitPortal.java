package com.example.game3d_opengl.game.terrain.track_elements.portal;

import static com.example.game3d_opengl.game.util.GameMath.getNormal;

import android.opengl.Matrix;

import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.rendering.mesh.MVPDrawArgs;
import com.example.game3d_opengl.rendering.infill.Mesh3DInfill;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.rendering.wireframe.Mesh3DWireframe;

public final class ExitPortal extends Addon {
    private static final FColor DEFAULT_FILL = FColor.CLR(0.1f, 0.4f, 0.9f, 1f);
    private static final FColor DEFAULT_EDGE = FColor.CLR(1f, 1f, 1f, 1f);
    private static final float DEFAULT_EDGE_PX = 2f;
    private static final float DEFAULT_WIDTH = 1.2f;
    private static final float DEFAULT_HEIGHT = 2.0f;
    private static final float FRAME_THICKNESS_FRACTION = 0.06f;
    private static final FColor FRAME_COLOR = FColor.CLR(0.95f, 0.95f, 1f, 1f);

    private Vector3D center;
    private Vector3D normal;
    private Vector3D up;
    private final float width;
    private final float height;
    private boolean placed = false;

    private final Mesh3DInfill fillMesh;
    private final Mesh3DWireframe edgeMesh;
    private final Mesh3DInfill frameMesh;
    private final MVPDrawArgs drawArgs = new MVPDrawArgs(new float[16]);
    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    public static ExitPortal createExitPortal() {
        return new ExitPortal(DEFAULT_WIDTH, DEFAULT_HEIGHT);
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
        Vector3D[] verts = buildLocalVerts();
        int[][] faces = new int[][]{
                new int[]{0, 1, 2},
                new int[]{0, 2, 3}
        };
        this.fillMesh = new Mesh3DInfill.Builder()
                .verts(verts)
                .faces(faces)
                .fillColor(DEFAULT_FILL)
                .buildObject();
        this.edgeMesh = new Mesh3DWireframe.Builder()
                .verts(verts)
                .faces(faces)
                .edgeColor(DEFAULT_EDGE)
                .pixelWidth(DEFAULT_EDGE_PX)
                .buildObject();
        float thickness = Math.max(0.03f, Math.min(width, height) * FRAME_THICKNESS_FRACTION);
        this.frameMesh = buildFrameMesh(
                width + 2f * thickness, height + 2f * thickness,
                width, height,
                FRAME_COLOR
        );
    }

    private Vector3D[] buildLocalVerts() {
        float halfW = width * 0.5f;
        float halfH = height * 0.5f;
        return new Vector3D[]{
                new Vector3D(-halfW, -halfH, 0f),
                new Vector3D(halfW, -halfH, 0f),
                new Vector3D(halfW, halfH, 0f),
                new Vector3D(-halfW, halfH, 0f)
        };
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

    public void setTransform(Vector3D center, Vector3D normal, Vector3D up) {
        if (center != null) this.center = center;
        if (normal != null) this.normal = normal;
        if (up != null) this.up = up;
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

    @Override
    public void updateBeforeDraw(float dt) {
    }

    @Override
    public void updateAfterDraw(float dt) {
    }

    @Override
    public void draw(float[] vp) {
        updateModelMatrix();
        Matrix.multiplyMM(mvpMatrix, 0, vp, 0, modelMatrix, 0);
        drawArgs.setMvp(mvpMatrix);
        if (fillMesh != null) fillMesh.draw(drawArgs);
        if (edgeMesh != null) edgeMesh.draw(drawArgs);
        if (frameMesh != null) frameMesh.draw(drawArgs);
    }

    private void updateModelMatrix() {
        Vector3D fwd = normal.normalized();
        Vector3D upN = up.normalized();
        Vector3D right = fwd.crossProduct(upN).normalized();
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
        modelMatrix[12] = center.x;
        modelMatrix[13] = center.y;
        modelMatrix[14] = center.z;
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
        Vector3D normal = trackDir;
        Vector3D up = terrainNormal;
        setTransform(center, normal, up);
        placed = true;
    }

    @Override
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {
        if (fillMesh != null) fillMesh.cleanupGPUResourcesRecursivelyOnContextLoss();
        if (edgeMesh != null) edgeMesh.cleanupGPUResourcesRecursivelyOnContextLoss();
        if (frameMesh != null) frameMesh.cleanupGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        if (fillMesh != null) fillMesh.reloadGPUResourcesRecursivelyOnContextLoss();
        if (edgeMesh != null) edgeMesh.reloadGPUResourcesRecursivelyOnContextLoss();
        if (frameMesh != null) frameMesh.reloadGPUResourcesRecursivelyOnContextLoss();
    }
}
