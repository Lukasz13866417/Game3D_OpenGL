package com.example.game3d_opengl.game.player.player_character;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;

import android.content.res.AssetManager;
import android.util.Log;

import com.example.game3d_opengl.game.settings.PlayerAppearanceSettings;
import com.example.game3d_opengl.game.settings.PlayerAppearanceSettings.WheelStyle;
import com.example.game3d_opengl.rendering.infill.Mesh3DInfill;
import com.example.game3d_opengl.rendering.object3d.UnbatchedObject3DWithOutline;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.ObjMaterialGroupLoader;
import com.example.game3d_opengl.rendering.util3d.PreparedModelData;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import static com.example.game3d_opengl.game.player.player_character.PlayerConfig.*;

/**
 * Loads and owns the shared multipart player mesh used by every gameplay session.
 * Disk parsing can happen in the background, while mesh creation stays on the GL thread.
 */
public class PlayerAssets {
    private static final float NEON_DARK_CHANNEL = 0.04f;
    private static final float NEON_BRIGHT_CHANNEL = 0.98f;
    private static final float NEON_SATURATION_GAIN = 2.2f;

    static UnbatchedObject3DWithOutline PLAYER_OBJECT;
    private static Map<String, PreparedModelData> preparedPlayerParts;
    private static WheelStyle preparedWheelStyle;
    private static WheelStyle loadedWheelStyle;
    private static Mesh3DInfill violetCoreMesh;
    private static Mesh3DInfill violetPrimaryGrooves;
    private static Mesh3DInfill violetSecondaryGrooves;
    private static Mesh3DInfill violetDetailGrooves;
    private static Mesh3DInfill mintThemeMesh;
    private static final FColor mintThemeColor =
            CLR(0.20f, 0.94f, 0.67f, 1f);
    private static float violetPrimaryVisibility = 1f;
    private static float violetSecondaryVisibility = 1f;
    private static float violetDetailVisibility = 1f;
    private static float violetCoreGlow;
    private static boolean gpuResourcesDirty = false;


    /**
     * Creates the selected wheel's multipart render object.
     * This method must be called before creating any Player instances.
     *
     * @param assetManager the Android asset manager for loading model files
     * @throws RuntimeException if asset loading fails
     */
    public static synchronized void LOAD_PLAYER_ASSETS(AssetManager assetManager) {
        WheelStyle selectedStyle = PlayerAppearanceSettings.getWheelStyle();
        if (PLAYER_OBJECT != null && loadedWheelStyle != selectedStyle) {
            discardLoadedPlayerObject();
        }
        if (PLAYER_OBJECT != null) {
            if (gpuResourcesDirty) {
                PLAYER_OBJECT.reloadGPUResourcesRecursivelyOnContextLoss();
                gpuResourcesDirty = false;
            }
            return;
        }
        if (preparedPlayerParts != null && preparedWheelStyle != selectedStyle) {
            preparedPlayerParts = null;
            preparedWheelStyle = null;
        }
        if (preparedPlayerParts == null) {
            preparePlayerAssetsFromDisk(assetManager);
        }
        BuiltPlayerObject builtPlayer = buildPlayerObject(preparedPlayerParts);
        PLAYER_OBJECT = builtPlayer.object;
        installPlayerMeshes(builtPlayer);
        loadedWheelStyle = preparedWheelStyle;
        preparedPlayerParts = null;
        preparedWheelStyle = null;
        gpuResourcesDirty = false;
    }

    public static synchronized void preparePlayerAssetsFromDisk(AssetManager assetManager) {
        WheelStyle selectedStyle = PlayerAppearanceSettings.getWheelStyle();
        if ((PLAYER_OBJECT != null && loadedWheelStyle == selectedStyle)
                || (preparedPlayerParts != null && preparedWheelStyle == selectedStyle)) {
            return;
        }
        if (assetManager == null) {
            throw new IllegalArgumentException("AssetManager cannot be null");
        }

        try {
            ObjMaterialGroupLoader loader = new ObjMaterialGroupLoader(assetManager);
            preparedPlayerParts = loader.load(
                    selectedStyle.assetFilename(),
                    PLAYER_WIDTH,
                    PLAYER_HEIGHT,
                    PLAYER_HEIGHT);
            preparedWheelStyle = selectedStyle;

            Log.d(TAG, selectedStyle.assetFilename()
                    + " player assets loaded successfully");
        } catch (IOException e) {
            Log.e(TAG, ERROR_ASSET_LOADING + e.getMessage(), e);
            throw new RuntimeException(ERROR_ASSET_LOADING + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error loading player assets: " + e.getMessage(), e);
            throw new RuntimeException("Unexpected error loading player assets: " + e.getMessage(), e);
        }
    }

    public static synchronized boolean hasPreparedOrLoadedAssets() {
        WheelStyle selectedStyle = PlayerAppearanceSettings.getWheelStyle();
        return (PLAYER_OBJECT != null && loadedWheelStyle == selectedStyle)
                || (preparedPlayerParts != null && preparedWheelStyle == selectedStyle);
    }

    public static synchronized boolean isGpuReady() {
        return PLAYER_OBJECT != null
                && loadedWheelStyle == PlayerAppearanceSettings.getWheelStyle()
                && !gpuResourcesDirty;
    }

    public static synchronized void reloadLoadedGPUResourcesOnContextLoss() {
        if (PLAYER_OBJECT != null) {
            PLAYER_OBJECT.reloadGPUResourcesRecursivelyOnContextLoss();
            gpuResourcesDirty = false;
        }
    }

    public static synchronized void cleanupLoadedGPUResources() {
        if (PLAYER_OBJECT != null && !gpuResourcesDirty) {
            PLAYER_OBJECT.cleanupGPUResourcesRecursively();
            gpuResourcesDirty = true;
        }
    }

    public static synchronized void markLoadedGPUResourcesDirty() {
        if (PLAYER_OBJECT != null) {
            gpuResourcesDirty = true;
        }
    }

    /**
     * Builds the selected wheel immediately while gameplay is paused behind Settings.
     * Call this on the GL thread so mesh buffers are created and released safely.
     */
    public static synchronized void switchToSelectedAppearance(
            AssetManager assetManager
    ) {
        WheelStyle selectedStyle = PlayerAppearanceSettings.getWheelStyle();
        if (PLAYER_OBJECT != null
                && loadedWheelStyle == selectedStyle
                && !gpuResourcesDirty) {
            return;
        }
        if (assetManager == null) {
            throw new IllegalArgumentException("AssetManager cannot be null");
        }

        try {
            Map<String, PreparedModelData> selectedParts =
                    preparedPlayerParts != null
                            && preparedWheelStyle == selectedStyle
                            ? preparedPlayerParts
                            : new ObjMaterialGroupLoader(assetManager).load(
                                    selectedStyle.assetFilename(),
                                    PLAYER_WIDTH,
                                    PLAYER_HEIGHT,
                                    PLAYER_HEIGHT);
            BuiltPlayerObject selectedObject =
                    buildPlayerObject(selectedParts);

            discardLoadedPlayerObject();
            PLAYER_OBJECT = selectedObject.object;
            installPlayerMeshes(selectedObject);
            loadedWheelStyle = selectedStyle;
            preparedPlayerParts = null;
            preparedWheelStyle = null;
            gpuResourcesDirty = false;
        } catch (IOException exception) {
            Log.e(TAG, ERROR_ASSET_LOADING + exception.getMessage(), exception);
            throw new RuntimeException(
                    ERROR_ASSET_LOADING + exception.getMessage(),
                    exception);
        }
    }

    static synchronized void setVioletSpinAppearance(
            float primaryVisibility,
            float secondaryVisibility,
            float detailVisibility,
            float coreGlow
    ) {
        violetPrimaryVisibility = clamp01(primaryVisibility);
        violetSecondaryVisibility = clamp01(secondaryVisibility);
        violetDetailVisibility = clamp01(detailVisibility);
        violetCoreGlow = clamp01(coreGlow);
        applyVioletSpinAppearance();
    }

    /**
     * Tints the mint wheel's luminous parts from the current gameplay theme.
     * The pastel theme is converted to a bright, saturated version of the same hue.
     */
    public static synchronized void setMintThemeColor(FColor themeColor) {
        if (themeColor == null) {
            return;
        }
        writeNeonThemeColor(themeColor, mintThemeColor);
        if (mintThemeMesh != null) {
            mintThemeMesh.setFillColor(mintThemeColor);
        }
    }

    static void writeNeonThemeColor(FColor themeColor, FColor destination) {
        float maxChannel = Math.max(
                themeColor.r(),
                Math.max(themeColor.g(), themeColor.b()));
        if (maxChannel <= 1.0e-6f) {
            destination.rgba[0] = NEON_BRIGHT_CHANNEL;
            destination.rgba[1] = NEON_BRIGHT_CHANNEL;
            destination.rgba[2] = NEON_BRIGHT_CHANNEL;
        } else {
            float brightnessScale = NEON_BRIGHT_CHANNEL / maxChannel;
            destination.rgba[0] = neonChannel(
                    themeColor.r() * brightnessScale);
            destination.rgba[1] = neonChannel(
                    themeColor.g() * brightnessScale);
            destination.rgba[2] = neonChannel(
                    themeColor.b() * brightnessScale);
        }
        destination.rgba[3] = 1f;
    }

    private static float neonChannel(float brightenedChannel) {
        float saturated = NEON_BRIGHT_CHANNEL
                - (NEON_BRIGHT_CHANNEL - brightenedChannel)
                * NEON_SATURATION_GAIN;
        return Math.max(
                NEON_DARK_CHANNEL,
                Math.min(NEON_BRIGHT_CHANNEL, saturated));
    }

    private static void installPlayerMeshes(
            BuiltPlayerObject builtPlayer
    ) {
        violetCoreMesh = builtPlayer.violetCore;
        violetPrimaryGrooves = builtPlayer.violetPrimary;
        violetSecondaryGrooves = builtPlayer.violetSecondary;
        violetDetailGrooves = builtPlayer.violetDetail;
        mintThemeMesh = builtPlayer.mintTheme;
        applyVioletSpinAppearance();
        if (mintThemeMesh != null) {
            mintThemeMesh.setFillColor(mintThemeColor);
        }
    }

    private static void applyVioletSpinAppearance() {
        setVioletEnergyBlend(violetCoreMesh, violetCoreGlow);
        setVioletEnergyBlend(
                violetPrimaryGrooves, violetPrimaryVisibility);
        setVioletEnergyBlend(
                violetSecondaryGrooves, violetSecondaryVisibility);
        setVioletEnergyBlend(
                violetDetailGrooves, violetDetailVisibility);
    }

    private static void setVioletEnergyBlend(
            Mesh3DInfill mesh,
            float intensity
    ) {
        if (mesh == null) {
            return;
        }
        float t = clamp01(intensity);
        mesh.setFillColor(CLR(
                0.035f + (0.50f - 0.035f) * t,
                0.028f + (0.22f - 0.028f) * t,
                0.045f + (0.82f - 0.045f) * t,
                1f));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static void discardLoadedPlayerObject() {
        if (PLAYER_OBJECT != null && !gpuResourcesDirty) {
            PLAYER_OBJECT.cleanupGPUResourcesRecursively();
        }
        PLAYER_OBJECT = null;
        violetCoreMesh = null;
        violetPrimaryGrooves = null;
        violetSecondaryGrooves = null;
        violetDetailGrooves = null;
        mintThemeMesh = null;
        loadedWheelStyle = null;
        gpuResourcesDirty = false;
    }

    private static BuiltPlayerObject buildPlayerObject(
            Map<String, PreparedModelData> parts
    ) {
        if (parts == null || parts.isEmpty()) {
            throw new IllegalStateException("Player model data was not prepared");
        }

        Mesh3DInfill[] meshes = new Mesh3DInfill[parts.size()];
        Mesh3DInfill[] spinBlurCandidates =
                new Mesh3DInfill[parts.size()];
        int spinBlurMeshCount = 0;
        Mesh3DInfill violetCore = null;
        Mesh3DInfill violetPrimary = null;
        Mesh3DInfill violetSecondary = null;
        Mesh3DInfill violetDetail = null;
        Mesh3DInfill mintTheme = null;
        int meshIndex = 0;
        for (Map.Entry<String, PreparedModelData> entry : parts.entrySet()) {
            Mesh3DInfill mesh =
                    buildPlayerPart(entry.getKey(), entry.getValue());
            meshes[meshIndex++] = mesh;
            if (isSpinBlurMaterial(entry.getKey())) {
                spinBlurCandidates[spinBlurMeshCount++] = mesh;
            }
            if ("violet_core".equals(entry.getKey())) {
                violetCore = mesh;
            } else if ("violet_glow_primary".equals(entry.getKey())) {
                violetPrimary = mesh;
            } else if ("violet_glow_secondary".equals(entry.getKey())) {
                violetSecondary = mesh;
            } else if ("violet_glow_detail".equals(entry.getKey())) {
                violetDetail = mesh;
            } else if ("mint_light".equals(entry.getKey())) {
                mintTheme = mesh;
            }
        }

        UnbatchedObject3DWithOutline obj =
                UnbatchedObject3DWithOutline.wrapMultipart(
                        meshes,
                        null,
                        Arrays.copyOf(
                                spinBlurCandidates,
                                spinBlurMeshCount));
        obj.objX = INITIAL_POSITION_X;
        obj.objY = INITIAL_POSITION_Y;
        obj.objZ = INITIAL_POSITION_Z;
        return new BuiltPlayerObject(
                obj,
                violetCore,
                violetPrimary,
                violetSecondary,
                violetDetail,
                mintTheme);
    }

    /**
     * Only asymmetric/high-contrast rotating details need temporal sampling. Blurring the
     * mostly rotationally-symmetric carcass would add fill cost while making the silhouette
     * muddy.
     */
    static boolean isSpinBlurMaterial(String materialName) {
        return "mint_tread".equals(materialName)
                || "mint_light".equals(materialName)
                || "violet_energy".equals(materialName)
                || "violet_glow_primary".equals(materialName)
                || "violet_glow_secondary".equals(materialName)
                || "violet_glow_detail".equals(materialName);
    }

    private static Mesh3DInfill buildPlayerPart(
            String materialName,
            PreparedModelData model
    ) {
        Mesh3DInfill.Builder builder = new Mesh3DInfill.Builder()
                .verts(model.verts())
                .faces(model.faces());
        if (model.hasNormals()) {
            builder.normals(model.normals());
        }

        switch (materialName) {
            case "violet_armor":
                builder.fillColor(CLR(0.105f, 0.095f, 0.125f, 1f))
                        .ambient(0.32f)
                        .diffuse(0.70f)
                        .specular(0.07f)
                        .shininess(10f);
                break;
            case "violet_hub":
                builder.fillColor(CLR(0.125f, 0.115f, 0.145f, 1f))
                        .ambient(0.34f)
                        .diffuse(0.62f)
                        .specular(0.16f)
                        .shininess(24f);
                break;
            case "violet_core":
                builder.fillColor(CLR(0.035f, 0.028f, 0.045f, 1f))
                        .ambient(1f)
                        .diffuse(0f)
                        .specular(0f)
                        .shininess(1f);
                break;
            case "violet_energy":
            case "violet_glow_primary":
            case "violet_glow_secondary":
            case "violet_glow_detail":
                builder.fillColor(CLR(0.50f, 0.22f, 0.82f, 1f))
                        .ambient(1f)
                        .diffuse(0f)
                        .specular(0f)
                        .shininess(1f);
                break;
            case "mint_rubber":
                builder.fillColor(CLR(0.125f, 0.132f, 0.138f, 1f))
                        .ambient(0.32f)
                        .diffuse(0.70f)
                        .specular(0.10f)
                        .shininess(14f);
                break;
            case "mint_tread":
                builder.fillColor(CLR(0.025f, 0.030f, 0.032f, 1f))
                        .ambient(0.36f)
                        .diffuse(0.40f)
                        .specular(0.03f)
                        .shininess(10f);
                break;
            case "mint_hub":
                builder.fillColor(CLR(0.115f, 0.125f, 0.130f, 1f))
                        .ambient(0.34f)
                        .diffuse(0.58f)
                        .specular(0.22f)
                        .shininess(34f);
                break;
            case "mint_light":
                builder.fillColor(mintThemeColor)
                        .ambient(1f)
                        .diffuse(0f)
                        .specular(0f)
                        .shininess(1f);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported player material: " + materialName);
        }
        return builder.buildObject();
    }

    /**
     * Keeps the complete wheel and its adjustable material groups together during construction.
     */
    private static final class BuiltPlayerObject {
        private final UnbatchedObject3DWithOutline object;
        private final Mesh3DInfill violetCore;
        private final Mesh3DInfill violetPrimary;
        private final Mesh3DInfill violetSecondary;
        private final Mesh3DInfill violetDetail;
        private final Mesh3DInfill mintTheme;

        private BuiltPlayerObject(
                UnbatchedObject3DWithOutline object,
                Mesh3DInfill violetCore,
                Mesh3DInfill violetPrimary,
                Mesh3DInfill violetSecondary,
                Mesh3DInfill violetDetail,
                Mesh3DInfill mintTheme
        ) {
            this.object = object;
            this.violetCore = violetCore;
            this.violetPrimary = violetPrimary;
            this.violetSecondary = violetSecondary;
            this.violetDetail = violetDetail;
            this.mintTheme = mintTheme;
        }
    }
}
