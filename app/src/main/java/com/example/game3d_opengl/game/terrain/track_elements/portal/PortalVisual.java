package com.example.game3d_opengl.game.terrain.track_elements.portal;

import android.opengl.GLES20;
import android.opengl.GLException;

import com.example.game3d_opengl.game.terrain.track_elements.portal.assets.PortalAsset;
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
    private final PortalRenderResources resources;
    private final boolean ownsResources;
    private final PortalSphereMesh3D fillMesh;
    private final PortalWireframeMesh3D wireframeMesh;
    private final PortalSphereDrawArgs shellArgs = new PortalSphereDrawArgs();
    private final PortalSphereDrawArgs coreArgs = new PortalSphereDrawArgs();
    private final PortalWireframeDrawArgs wireframeArgs = new PortalWireframeDrawArgs();

    private Vector3D center = new Vector3D(0f, 0f, 0f);
    private Vector3D lookDirection = new Vector3D(0f, 0f, -1f);
    private Vector3D upDirection = WORLD_UP;
    private float outerHalfWidth = 0.5f * PortalConfig.DEFAULT_WIDTH_WORLD;
    private float outerHalfHeight = 0.5f * PortalConfig.DEFAULT_WIDTH_WORLD;
    private float styleAccent;
    private float animSeconds = 0f;
    private final float[] baseRotationMatrix = new float[9];
    private final float[] primarySpinRotationMatrix = new float[9];
    private final float[] secondarySpinRotationMatrix = new float[9];
    private final float[] composedSpinRotationMatrix = new float[9];
    private final float[] rotationMatrix = new float[9];
    private final float[] scaleScratch = new float[3];

    PortalVisual(PortalAsset asset) {
        this(new PortalRenderResources(
                asset != null ? asset : PortalAssets.createPortalAsset()), true);
    }

    PortalVisual(PortalRenderResources resources) {
        this(resources, false);
    }

    private PortalVisual(PortalRenderResources resources, boolean ownsResources) {
        if (resources == null) {
            throw new IllegalArgumentException("resources == null");
        }
        this.resources = resources;
        this.ownsResources = ownsResources;
        this.fillMesh = resources.fillMesh();
        this.wireframeMesh = resources.wireframeMesh();
        setIdentityMat3(baseRotationMatrix);
        setIdentityMat3(primarySpinRotationMatrix);
        setIdentityMat3(secondarySpinRotationMatrix);
        setIdentityMat3(composedSpinRotationMatrix);
        setIdentityMat3(rotationMatrix);
    }

    void setCenter(Vector3D center) {
        if (center != null) this.center = center;
    }

    void setOuterDimensions(float width, float height) {
        outerHalfWidth = Math.max(0.02f, width * 0.5f);
        outerHalfHeight = Math.max(0.02f, height * 0.5f);
    }

    /** Compatibility hook for the quarantined legacy portal objects. */
    void setBaseOuterRadius(float radius) {
        float safe = Math.max(0.02f, radius);
        outerHalfWidth = safe;
        outerHalfHeight = safe;
    }

    void setLookDirection(Vector3D lookDir) {
        if (lookDir == null || lookDir.sqlen() < 1e-8f) {
            return;
        }
        this.lookDirection = lookDir.withLen(1f);
    }

    void setUpDirection(Vector3D upDir) {
        if (upDir != null && upDir.sqlen() >= 1e-8f) {
            upDirection = upDir.withLen(1f);
        }
    }

    void setVisualStyle(String visualStyleId) {
        // BEACON is the parity-locked production style. Unknown future style IDs retain the same
        // geometry but receive a small deterministic accent instead of being silently ignored.
        styleAccent = styleAccent(visualStyleId);
    }

    float getMaxExpectedOuterRadius() {
        return Math.max(outerHalfWidth, outerHalfHeight);
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
        writeBaseRotation(lookDirection, upDirection, baseRotationMatrix);
    }

    static void writeBaseRotation(
            Vector3D lookDirection, Vector3D upDirection, float[] out) {
        if (out == null || out.length < 9) {
            throw new IllegalArgumentException("out must contain 9 floats");
        }
        Vector3D yAxis = lookDirection;
        if (yAxis.sqlen() < 1e-8f) {
            yAxis = new Vector3D(0f, 0f, -1f);
        } else {
            yAxis = yAxis.withLen(1f);
        }

        Vector3D requestedUp = upDirection == null ? WORLD_UP : upDirection;
        Vector3D zAxis = requestedUp.sub(
                yAxis.withLen(requestedUp.dotProduct(yAxis)));
        if (zAxis.sqlen() < 1e-8f) {
            Vector3D fallback = Math.abs(yAxis.y) < 0.9f
                    ? WORLD_UP : new Vector3D(0f, 0f, 1f);
            zAxis = fallback.sub(
                    yAxis.withLen(fallback.dotProduct(yAxis)));
        }
        zAxis = zAxis.withLen(1f);
        Vector3D xAxis = yAxis.crossProduct(zAxis);
        if (xAxis.sqlen() < 1e-8f) {
            xAxis = new Vector3D(1f, 0f, 0f);
        } else {
            xAxis = xAxis.withLen(1f);
        }

        // Column-major basis: local X->xAxis, local Y->look direction, local Z->up-ish axis.
        out[0] = xAxis.x; out[1] = xAxis.y; out[2] = xAxis.z;
        out[3] = yAxis.x; out[4] = yAxis.y; out[5] = yAxis.z;
        out[6] = zAxis.x; out[7] = zAxis.y; out[8] = zAxis.z;
    }

    private void configureShellArgs(float[] vp) {
        configureCommonFillArgs(shellArgs, vp, 1f);
        FColor theme = PortalLightingEnvironment.getColorTheme();
        if (theme == null) theme = FColor.CLR(0.8f, 0f, 0f, 1f);
        FColor shellBase = mixWithWhite(theme,
                PortalConfig.SHELL_WHITE_MIX + styleAccent,
                PortalConfig.SHELL_ALPHA);
        shellArgs.colorA = shellBase;
        shellArgs.colorB = scaleColor(shellBase, PortalConfig.SHELL_DARK_FACE_FACTOR, PortalConfig.SHELL_ALPHA);
        shellArgs.ambient = PortalConfig.SHELL_AMBIENT;
        shellArgs.diffuse = PortalConfig.SHELL_DIFFUSE;
        shellArgs.specular = PortalConfig.SHELL_SPECULAR;
        shellArgs.shininess = PortalConfig.SHELL_SHININESS;
    }

    private void configureCoreArgs(float[] vp) {
        configureCommonFillArgs(coreArgs, vp, PortalConfig.CORE_RADIUS_FACTOR);
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
        setScale(wireframeArgs, 1f);
        wireframeArgs.rotation = rotationMatrix;
        FColor theme = PortalLightingEnvironment.getColorTheme();
        if (theme == null) theme = FColor.CLR(0.8f, 0f, 0f, 1f);
        wireframeArgs.color = scaleColor(
                mixWithWhite(theme, PortalConfig.WIREFRAME_WHITE_MIX, 1f),
                PortalConfig.WIREFRAME_BRIGHTNESS,
                1f
        );
    }

    private void configureCommonFillArgs(PortalSphereDrawArgs args, float[] vp, float factor) {
        args.vp = vp;
        args.centerX = center.x;
        args.centerY = center.y;
        args.centerZ = center.z;
        setScale(args, factor);
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

    private void setScale(PortalSphereDrawArgs args, float factor) {
        writeVisualScale(
                outerHalfWidth * 2f, outerHalfHeight * 2f,
                factor, scaleScratch);
        args.scaleX = scaleScratch[0];
        args.scaleY = scaleScratch[1];
        args.scaleZ = scaleScratch[2];
    }

    private void setScale(PortalWireframeDrawArgs args, float factor) {
        writeVisualScale(
                outerHalfWidth * 2f, outerHalfHeight * 2f,
                factor, scaleScratch);
        args.scaleX = scaleScratch[0];
        args.scaleY = scaleScratch[1];
        args.scaleZ = scaleScratch[2];
    }

    static void writeVisualScale(
            float width, float height, float factor, float[] out) {
        if (out == null || out.length < 3) {
            throw new IllegalArgumentException("out must contain 3 floats");
        }
        float halfWidth = Math.max(0.02f, width * 0.5f);
        float halfHeight = Math.max(0.02f, height * 0.5f);
        out[0] = halfWidth * factor;
        out[1] = Math.min(halfWidth, halfHeight) * factor;
        out[2] = halfHeight * factor;
    }

    private static float unitHash(String value) {
        int hash = value == null ? 0 : value.hashCode();
        hash ^= hash >>> 16;
        return (hash & 0xffff) / 65535f;
    }

    static float styleAccent(String visualStyleId) {
        return "BEACON".equals(visualStyleId)
                ? 0f : 0.08f * unitHash(visualStyleId);
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
        if (ownsResources) {
            resources.cleanupGPUResourcesRecursively();
        }
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        if (ownsResources) {
            resources.reloadGPUResourcesRecursivelyOnContextLoss();
        }
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
