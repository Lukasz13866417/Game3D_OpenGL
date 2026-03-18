package com.example.game3d_opengl.game.terrain.track_elements.portal;

import android.opengl.GLES20;
import android.opengl.GLException;

import com.example.game3d_opengl.game.terrain.track_elements.portal.assets.PortalAsset;
import com.example.game3d_opengl.game.terrain.track_elements.portal.assets.PortalAssetData;
import com.example.game3d_opengl.game.terrain.track_elements.portal.assets.PortalAssets;
import com.example.game3d_opengl.game.terrain.track_elements.portal.rendering.PortalSphereDrawArgs;
import com.example.game3d_opengl.game.terrain.track_elements.portal.rendering.PortalSphereMesh3D;
import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Procedural visual for portals driven by a selected PortalAsset mesh.
 */
final class PortalVisual implements GPUResourceOwner {
    private static final FColor DEFAULT_LIGHT_COLOR = FColor.CLR(1f, 1f, 1f, 1f);
    private static final Vector3D WORLD_UP = new Vector3D(0f, 1f, 0f);
    private static final Object SHARED_MESH_LOCK = new Object();
    private static final long RELOAD_DEBOUNCE_NANOS = 100_000_000L;

    private static PortalSphereMesh3D sharedFillMesh = null;
    private static Class<?> sharedAssetClass = null;
    private static boolean sharedMeshCleaned = false;
    private static long lastSharedReloadNanos = 0L;

    private final PortalSphereMesh3D fillMesh;
    private final PortalSphereDrawArgs fillArgs = new PortalSphereDrawArgs();

    private Vector3D center = new Vector3D(0f, 0f, 0f);
    private Vector3D lookDirection = new Vector3D(0f, 0f, -1f);
    private float baseOuterRadius = 0.5f * PortalConfig.DEFAULT_WIDTH_WORLD;
    private float animSeconds = 0f;
    private final float[] baseRotationMatrix = new float[9];
    private final float[] spinRotationMatrix = new float[9];
    private final float[] rotationMatrix = new float[9];

    PortalVisual(PortalAsset asset) {
        PortalAsset chosenAsset = asset != null ? asset : PortalAssets.createPortalAsset();
        this.fillMesh = acquireSharedFillMesh(chosenAsset);
        setIdentityMat3(baseRotationMatrix);
        setIdentityMat3(spinRotationMatrix);
        setIdentityMat3(rotationMatrix);
    }

    void setCenter(Vector3D center) {
        if (center != null) this.center = center;
    }

    void setBaseOuterRadius(float radius) {
        this.baseOuterRadius = Math.max(0.02f, radius);
    }

    void setLookDirection(Vector3D lookDir) {
        if (lookDir == null) {
            return;
        }
        Vector3D h = new Vector3D(lookDir.x, 0f, lookDir.z);
        if (h.sqlen() < 1e-8f) {
            return;
        }
        this.lookDirection = h.withLen(1f);
    }

    float getMaxExpectedOuterRadius() {
        return baseOuterRadius;
    }

    void update(float dtMillis) {
        if (dtMillis > 0f) {
            animSeconds += dtMillis * 0.001f;
        }
    }

    void draw(float[] vp) {
        if (vp == null) return;

        computeRotation();
        configureFillArgs(vp);

        boolean blendWas = isBlendEnabled();
        boolean cullWas = isCullEnabled();
        boolean depthWas = isDepthTestEnabled();

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glDisable(GLES20.GL_BLEND);

        fillMesh.draw(fillArgs);

        if (!depthWas) GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        if (!cullWas) GLES20.glDisable(GLES20.GL_CULL_FACE);
        else {
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            GLES20.glCullFace(GLES20.GL_BACK);
        }
        if (blendWas) GLES20.glEnable(GLES20.GL_BLEND);
        else GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void computeRotation() {
        computeBaseRotation();

        float angle = PortalConfig.ROTATION_SPEED_RAD_PER_SEC * animSeconds;
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        // Spin around local +Y (portal look axis), then orient to world look direction.
        spinRotationMatrix[0] = c;  spinRotationMatrix[1] = 0f; spinRotationMatrix[2] = -s;
        spinRotationMatrix[3] = 0f; spinRotationMatrix[4] = 1f; spinRotationMatrix[5] = 0f;
        spinRotationMatrix[6] = s;  spinRotationMatrix[7] = 0f; spinRotationMatrix[8] = c;
        multiplyMat3(baseRotationMatrix, spinRotationMatrix, rotationMatrix);
    }

    private void computeBaseRotation() {
        Vector3D yAxis = lookDirection;
        if (yAxis.sqlen() < 1e-8f) {
            yAxis = new Vector3D(0f, 0f, -1f);
        } else {
            yAxis = yAxis.withLen(1f);
        }

        // Keep portal horizontal: world-up is fixed, so no pitch/roll toward terrain.
        Vector3D xAxis = yAxis.crossProduct(WORLD_UP);
        if (xAxis.sqlen() < 1e-8f) {
            xAxis = new Vector3D(1f, 0f, 0f);
        } else {
            xAxis = xAxis.withLen(1f);
        }
        Vector3D zAxis = xAxis.crossProduct(yAxis);
        if (zAxis.sqlen() < 1e-8f) {
            zAxis = WORLD_UP;
        } else {
            zAxis = zAxis.withLen(1f);
        }

        // Column-major basis: local X->xAxis, local Y->look direction, local Z->up-ish axis.
        baseRotationMatrix[0] = xAxis.x; baseRotationMatrix[1] = xAxis.y; baseRotationMatrix[2] = xAxis.z;
        baseRotationMatrix[3] = yAxis.x; baseRotationMatrix[4] = yAxis.y; baseRotationMatrix[5] = yAxis.z;
        baseRotationMatrix[6] = zAxis.x; baseRotationMatrix[7] = zAxis.y; baseRotationMatrix[8] = zAxis.z;
    }

    private void configureFillArgs(float[] vp) {
        fillArgs.vp = vp;
        fillArgs.centerX = center.x;
        fillArgs.centerY = center.y;
        fillArgs.centerZ = center.z;
        fillArgs.radius = baseOuterRadius;
        fillArgs.rotation = rotationMatrix;

        FColor theme = PortalLightingEnvironment.getColorTheme();
        if (theme == null) theme = FColor.CLR(0.8f, 0f, 0f, 1f);
        float dk = PortalConfig.DARK_FACE_FACTOR;
        fillArgs.colorA = theme;
        fillArgs.colorB = FColor.CLR(theme.r() * dk, theme.g() * dk, theme.b() * dk, 1f);

        Vector3D lightPos = PortalLightingEnvironment.getLightPos();
        Vector3D cameraPos = PortalLightingEnvironment.getCameraPos();
        FColor lightColor = PortalLightingEnvironment.getLightColor();
        if (lightPos == null) lightPos = new Vector3D(0f, 10f, 0f);
        if (cameraPos == null) cameraPos = new Vector3D(0f, 0f, 3f);
        if (lightColor == null) lightColor = DEFAULT_LIGHT_COLOR;

        fillArgs.lightX = lightPos.x;
        fillArgs.lightY = lightPos.y;
        fillArgs.lightZ = lightPos.z;
        fillArgs.lightColor = lightColor;
        fillArgs.cameraX = cameraPos.x;
        fillArgs.cameraY = cameraPos.y;
        fillArgs.cameraZ = cameraPos.z;
        fillArgs.ambient = PortalConfig.FILL_AMBIENT;
        fillArgs.diffuse = PortalConfig.FILL_DIFFUSE;
        fillArgs.specular = PortalConfig.FILL_SPECULAR;
        fillArgs.shininess = PortalConfig.FILL_SHININESS;
    }

    // ---- GL state helpers ----

    private static boolean isBlendEnabled() {
        try { return GLES20.glIsEnabled(GLES20.GL_BLEND); }
        catch (GLException ignored) { return false; }
    }

    private static boolean isCullEnabled() {
        try { return GLES20.glIsEnabled(GLES20.GL_CULL_FACE); }
        catch (GLException ignored) { return false; }
    }

    private static boolean isDepthTestEnabled() {
        try { return GLES20.glIsEnabled(GLES20.GL_DEPTH_TEST); }
        catch (GLException ignored) { return true; }
    }

    // ---- GPU resource management ----

    @Override
    public void cleanupGPUResourcesRecursively() {
        synchronized (SHARED_MESH_LOCK) {
            if (sharedFillMesh == null || sharedMeshCleaned) return;
            sharedFillMesh.cleanupGPUResourcesRecursively();
            sharedMeshCleaned = true;
        }
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        synchronized (SHARED_MESH_LOCK) {
            if (sharedFillMesh == null) return;
            long now = System.nanoTime();
            if (!sharedMeshCleaned && (now - lastSharedReloadNanos) < RELOAD_DEBOUNCE_NANOS) return;
            sharedFillMesh.reloadGPUResourcesRecursivelyOnContextLoss();
            sharedMeshCleaned = false;
            lastSharedReloadNanos = now;
        }
    }

    private static PortalSphereMesh3D acquireSharedFillMesh(PortalAsset asset) {
        synchronized (SHARED_MESH_LOCK) {
            Class<?> assetClass = asset.getClass();
            if (sharedFillMesh == null || sharedAssetClass != assetClass) {
                PortalAssetData meshData = asset.buildMeshData();
                sharedFillMesh = new PortalSphereMesh3D.Builder()
                        .verts(meshData.verts)
                        .normals(meshData.normals)
                        .faceGroups(meshData.faceGroups)
                        .faces(deepCopyFaces(meshData.faces))
                        .buildObject();
                sharedAssetClass = assetClass;
                sharedMeshCleaned = false;
            }
            return sharedFillMesh;
        }
    }

    private static int[][] deepCopyFaces(int[][] src) {
        int[][] out = new int[src.length][];
        for (int i = 0; i < src.length; ++i) out[i] = src[i].clone();
        return out;
    }

    private static void setIdentityMat3(float[] m) {
        m[0] = 1f; m[1] = 0f; m[2] = 0f;
        m[3] = 0f; m[4] = 1f; m[5] = 0f;
        m[6] = 0f; m[7] = 0f; m[8] = 1f;
    }

    private static void multiplyMat3(float[] a, float[] b, float[] out) {
        for (int col = 0; col < 3; ++col) {
            for (int row = 0; row < 3; ++row) {
                out[col * 3 + row] =
                        a[0 * 3 + row] * b[col * 3 + 0] +
                        a[1 * 3 + row] * b[col * 3 + 1] +
                        a[2 * 3 + row] * b[col * 3 + 2];
            }
        }
    }
}

