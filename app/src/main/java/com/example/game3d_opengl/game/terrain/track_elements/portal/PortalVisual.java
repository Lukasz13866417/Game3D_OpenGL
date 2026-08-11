package com.example.game3d_opengl.game.terrain.track_elements.portal;

import android.opengl.GLES20;
import android.opengl.GLException;

import com.example.game3d_opengl.game.terrain.track_elements.portal.assets.PortalAsset;
import com.example.game3d_opengl.game.terrain.track_elements.portal.assets.PortalAssetData;
import com.example.game3d_opengl.game.terrain.track_elements.portal.assets.PortalAssets;
import com.example.game3d_opengl.game.terrain.track_elements.portal.rendering.PortalSphereDrawArgs;
import com.example.game3d_opengl.game.terrain.track_elements.portal.rendering.PortalSphereMesh3D;
import com.example.game3d_opengl.game.terrain.track_elements.portal.rendering.PortalWireframeDrawArgs;
import com.example.game3d_opengl.game.terrain.track_elements.portal.rendering.PortalWireframeMesh3D;
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
    private static PortalWireframeMesh3D sharedWireframeMesh = null;
    private static Class<?> sharedAssetClass = null;
    private static boolean sharedMeshesCleaned = false;
    private static long lastSharedReloadNanos = 0L;

    private final PortalSphereMesh3D fillMesh;
    private final PortalWireframeMesh3D wireframeMesh;
    private final PortalSphereDrawArgs shellArgs = new PortalSphereDrawArgs();
    private final PortalSphereDrawArgs coreArgs = new PortalSphereDrawArgs();
    private final PortalWireframeDrawArgs wireframeArgs = new PortalWireframeDrawArgs();

    private Vector3D center = new Vector3D(0f, 0f, 0f);
    private Vector3D lookDirection = new Vector3D(0f, 0f, -1f);
    private float baseOuterRadius = 0.5f * PortalConfig.DEFAULT_WIDTH_WORLD;
    private float animSeconds = 0f;
    private final float[] baseRotationMatrix = new float[9];
    private final float[] primarySpinRotationMatrix = new float[9];
    private final float[] secondarySpinRotationMatrix = new float[9];
    private final float[] composedSpinRotationMatrix = new float[9];
    private final float[] rotationMatrix = new float[9];

    PortalVisual(PortalAsset asset) {
        PortalAsset chosenAsset = asset != null ? asset : PortalAssets.createPortalAsset();
        this.fillMesh = acquireSharedFillMesh(chosenAsset);
        this.wireframeMesh = acquireSharedWireframeMesh(chosenAsset);
        setIdentityMat3(baseRotationMatrix);
        setIdentityMat3(primarySpinRotationMatrix);
        setIdentityMat3(secondarySpinRotationMatrix);
        setIdentityMat3(composedSpinRotationMatrix);
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
        configureShellArgs(vp);
        configureCoreArgs(vp);
        configureWireframeArgs(vp);

        boolean blendWas = isBlendEnabled();
        boolean cullWas = isCullEnabled();
        boolean depthWas = isDepthTestEnabled();

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDepthMask(false);
        fillMesh.draw(shellArgs);

        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glDisable(GLES20.GL_BLEND);
        fillMesh.draw(coreArgs);
        if (wireframeMesh != null) {
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            wireframeMesh.draw(wireframeArgs);
        }

        GLES20.glDepthMask(true);
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

        setYAxisRotation(
                PortalConfig.PRIMARY_ROTATION_SPEED_RAD_PER_SEC * animSeconds,
                primarySpinRotationMatrix
        );
        setZAxisRotation(
                PortalConfig.SECONDARY_ROTATION_SPEED_RAD_PER_SEC * animSeconds,
                secondarySpinRotationMatrix
        );
        multiplyMat3(primarySpinRotationMatrix, secondarySpinRotationMatrix, composedSpinRotationMatrix);
        multiplyMat3(baseRotationMatrix, composedSpinRotationMatrix, rotationMatrix);
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

    private void configureShellArgs(float[] vp) {
        configureCommonFillArgs(shellArgs, vp, baseOuterRadius);
        FColor theme = PortalLightingEnvironment.getColorTheme();
        if (theme == null) theme = FColor.CLR(0.8f, 0f, 0f, 1f);
        FColor shellBase = mixWithWhite(theme, PortalConfig.SHELL_WHITE_MIX, PortalConfig.SHELL_ALPHA);
        shellArgs.colorA = shellBase;
        shellArgs.colorB = scaleColor(shellBase, PortalConfig.SHELL_DARK_FACE_FACTOR, PortalConfig.SHELL_ALPHA);
        shellArgs.ambient = PortalConfig.SHELL_AMBIENT;
        shellArgs.diffuse = PortalConfig.SHELL_DIFFUSE;
        shellArgs.specular = PortalConfig.SHELL_SPECULAR;
        shellArgs.shininess = PortalConfig.SHELL_SHININESS;
    }

    private void configureCoreArgs(float[] vp) {
        configureCommonFillArgs(coreArgs, vp, baseOuterRadius * PortalConfig.CORE_RADIUS_FACTOR);
        FColor theme = PortalLightingEnvironment.getColorTheme();
        if (theme == null) theme = FColor.CLR(0.8f, 0f, 0f, 1f);
        FColor coreBase = scaleColor(
                mixWithWhite(theme, PortalConfig.CORE_WHITE_MIX, 1f),
                PortalConfig.CORE_BRIGHTNESS,
                1f
        );
        coreArgs.colorA = coreBase;
        coreArgs.colorB = scaleColor(coreBase, 0.9f, 1f);
        coreArgs.ambient = PortalConfig.CORE_AMBIENT;
        coreArgs.diffuse = PortalConfig.CORE_DIFFUSE;
        coreArgs.specular = PortalConfig.CORE_SPECULAR;
        coreArgs.shininess = PortalConfig.CORE_SHININESS;
    }

    private void configureWireframeArgs(float[] vp) {
        wireframeArgs.vp = vp;
        wireframeArgs.centerX = center.x;
        wireframeArgs.centerY = center.y;
        wireframeArgs.centerZ = center.z;
        wireframeArgs.radius = baseOuterRadius;
        wireframeArgs.rotation = rotationMatrix;
        FColor theme = PortalLightingEnvironment.getColorTheme();
        if (theme == null) theme = FColor.CLR(0.8f, 0f, 0f, 1f);
        wireframeArgs.color = scaleColor(
                mixWithWhite(theme, PortalConfig.WIREFRAME_WHITE_MIX, 1f),
                PortalConfig.WIREFRAME_BRIGHTNESS,
                1f
        );
    }

    private void configureCommonFillArgs(PortalSphereDrawArgs args, float[] vp, float radius) {
        args.vp = vp;
        args.centerX = center.x;
        args.centerY = center.y;
        args.centerZ = center.z;
        args.radius = radius;
        args.rotation = rotationMatrix;

        Vector3D lightPos = PortalLightingEnvironment.getLightPos();
        Vector3D cameraPos = PortalLightingEnvironment.getCameraPos();
        FColor lightColor = PortalLightingEnvironment.getLightColor();
        if (lightPos == null) lightPos = new Vector3D(0f, 10f, 0f);
        if (cameraPos == null) cameraPos = new Vector3D(0f, 0f, 3f);
        if (lightColor == null) lightColor = DEFAULT_LIGHT_COLOR;

        args.lightX = lightPos.x;
        args.lightY = lightPos.y;
        args.lightZ = lightPos.z;
        args.lightColor = lightColor;
        args.cameraX = cameraPos.x;
        args.cameraY = cameraPos.y;
        args.cameraZ = cameraPos.z;
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
        cleanupSharedGpuResources();
    }

    static void cleanupSharedGpuResources() {
        synchronized (SHARED_MESH_LOCK) {
            if (sharedMeshesCleaned) return;
            if (sharedFillMesh != null) {
                sharedFillMesh.cleanupGPUResourcesRecursively();
            }
            if (sharedWireframeMesh != null) {
                sharedWireframeMesh.cleanupGPUResourcesRecursively();
            }
            sharedMeshesCleaned = true;
        }
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        reloadSharedGpuResources();
    }

    static void reloadSharedGpuResources() {
        synchronized (SHARED_MESH_LOCK) {
            if (sharedFillMesh == null && sharedWireframeMesh == null) return;
            long now = System.nanoTime();
            if (!sharedMeshesCleaned && (now - lastSharedReloadNanos) < RELOAD_DEBOUNCE_NANOS) return;
            if (sharedFillMesh != null) {
                sharedFillMesh.reloadGPUResourcesRecursivelyOnContextLoss();
            }
            if (sharedWireframeMesh != null) {
                sharedWireframeMesh.reloadGPUResourcesRecursivelyOnContextLoss();
            }
            sharedMeshesCleaned = false;
            lastSharedReloadNanos = now;
        }
    }

    static void warmUpSharedGpuResources() {
        PortalAsset asset = PortalAssets.createPortalAsset();
        acquireSharedFillMesh(asset);
        acquireSharedWireframeMesh(asset);
    }

    static boolean sharedGpuResourcesReady() {
        synchronized (SHARED_MESH_LOCK) {
            return sharedFillMesh != null && !sharedMeshesCleaned;
        }
    }

    static void markSharedGpuResourcesDirty() {
        synchronized (SHARED_MESH_LOCK) {
            if (sharedFillMesh != null) {
                sharedMeshesCleaned = true;
            }
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
                sharedWireframeMesh = meshData.edges.length == 0
                        ? null
                        : new PortalWireframeMesh3D.Builder()
                        .verts(meshData.verts)
                        .edges(deepCopyFaces(meshData.edges))
                        .halfPx(0.5f * PortalConfig.WIREFRAME_PIXEL_WIDTH)
                        .buildObject();
                sharedAssetClass = assetClass;
                sharedMeshesCleaned = false;
            } else if (sharedMeshesCleaned) {
                sharedFillMesh.reloadGPUResourcesRecursivelyOnContextLoss();
                if (sharedWireframeMesh != null) {
                    sharedWireframeMesh.reloadGPUResourcesRecursivelyOnContextLoss();
                }
                sharedMeshesCleaned = false;
                lastSharedReloadNanos = System.nanoTime();
            }
            return sharedFillMesh;
        }
    }

    private static PortalWireframeMesh3D acquireSharedWireframeMesh(PortalAsset asset) {
        acquireSharedFillMesh(asset);
        synchronized (SHARED_MESH_LOCK) {
            return sharedWireframeMesh;
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

    private static void setYAxisRotation(float angle, float[] out) {
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        out[0] = c;  out[1] = 0f; out[2] = -s;
        out[3] = 0f; out[4] = 1f; out[5] = 0f;
        out[6] = s;  out[7] = 0f; out[8] = c;
    }

    private static void setZAxisRotation(float angle, float[] out) {
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        out[0] = c;  out[1] = s;  out[2] = 0f;
        out[3] = -s; out[4] = c;  out[5] = 0f;
        out[6] = 0f; out[7] = 0f; out[8] = 1f;
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

    private static FColor mixWithWhite(FColor color, float whiteMix, float alpha) {
        float t = clamp01(whiteMix);
        return FColor.CLR(
                color.r() * (1f - t) + t,
                color.g() * (1f - t) + t,
                color.b() * (1f - t) + t,
                alpha
        );
    }

    private static FColor scaleColor(FColor color, float scale, float alpha) {
        return FColor.CLR(
                color.r() * scale,
                color.g() * scale,
                color.b() * scale,
                alpha
        );
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
