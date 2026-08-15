package com.example.game3d_opengl.architecture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Locks the canonical Android terrain boundary while the compatibility implementation is retired. */
public final class TerrainArchitectureBoundaryTest {
    private static final String JAVA_ROOT = "src/main/java";

    private static final List<String> FORBIDDEN_IMPORTS = Arrays.asList(
            "com.example.game3d_opengl.game.terrain.terrain_api.",
            "com.example.game3d_opengl.game.terrain.terrain_structures.",
            "com.example.game3d_opengl.game.terrain.track_elements.potion.Potion;",
            "com.example.game3d_opengl.game.terrain.track_elements.spike.DeathSpike;",
            "com.example.game3d_opengl.game.terrain.track_elements.portal.Portal;",
            "com.example.game3d_opengl.game.terrain.track_elements.portal.ExitPortal;",
            "com.example.game3d_opengl.game.stage.stages.test."
    );

    @Test
    public void productionTerrainSessionAndPresentationCannotImportLegacyTerrain() throws Exception {
        Path sourceRoot = findAppProject().resolve(JAVA_ROOT);
        List<Path> productionSources = new ArrayList<Path>();
        productionSources.add(sourceRoot.resolve("com/example/game3d_opengl/MyGLRenderer.java"));
        productionSources.add(sourceRoot.resolve(
                "com/example/game3d_opengl/game/player/player_character/Player.java"));
        productionSources.addAll(javaFiles(sourceRoot.resolve(
                "com/example/game3d_opengl/game/player/player_logic")));
        productionSources.addAll(javaFiles(sourceRoot.resolve(
                "com/example/game3d_opengl/game/stage/stages/main")));
        productionSources.addAll(javaFiles(sourceRoot.resolve(
                "com/example/game3d_opengl/game/terrain/presentation")));

        productionSources.add(sourceRoot.resolve(
                "com/example/game3d_opengl/game/terrain/track_elements/GameplayElementBatchRenderers.java"));
        productionSources.add(sourceRoot.resolve(
                "com/example/game3d_opengl/game/terrain/track_elements/potion/PotionBatchRenderer.java"));
        productionSources.add(sourceRoot.resolve(
                "com/example/game3d_opengl/game/terrain/track_elements/potion/PotionRenderResources.java"));
        productionSources.add(sourceRoot.resolve(
                "com/example/game3d_opengl/game/terrain/track_elements/spike/SpikeBatchRenderer.java"));
        productionSources.add(sourceRoot.resolve(
                "com/example/game3d_opengl/game/terrain/track_elements/portal/PortalRenderer.java"));

        List<String> violations = new ArrayList<String>();
        for (Path source : productionSources) {
            String relative = sourceRoot.relativize(source).toString();
            for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("import ")
                        && !trimmed.startsWith("import static ")) {
                    continue;
                }
                for (String forbidden : FORBIDDEN_IMPORTS) {
                    if (trimmed.contains(forbidden)) {
                        violations.add(relative + ": " + trimmed);
                    }
                }
            }
        }

        if (!violations.isEmpty()) {
            fail("Canonical Android terrain imported the compatibility implementation:\n"
                    + joinLines(violations));
        }
    }

    @Test
    public void canonicalPathIsExplicitlyWiredToSharedTerrainRecords() throws Exception {
        Path sourceRoot = findAppProject().resolve(JAVA_ROOT);
        String prepared = read(sourceRoot.resolve(
                "com/example/game3d_opengl/game/stage/stages/main/PreparedGameplaySession.java"));
        String gameplay = read(sourceRoot.resolve(
                "com/example/game3d_opengl/game/stage/stages/main/GameplayStage.java"));
        String presentation = read(sourceRoot.resolve(
                "com/example/game3d_opengl/game/terrain/presentation/TerrainPresentation.java"));
        String batches = read(sourceRoot.resolve(
                "com/example/game3d_opengl/game/terrain/track_elements/GameplayElementBatchRenderers.java"));
        String terrainShader = read(sourceRoot.resolve(
                "com/example/game3d_opengl/game/terrain/presentation/TerrainRibbonShaderPair.java"));
        String terrainMesh = read(sourceRoot.resolve(
                "com/example/game3d_opengl/game/terrain/presentation/CanonicalTerrainMeshRenderer.java"));
        String portalRenderer = read(sourceRoot.resolve(
                "com/example/game3d_opengl/game/terrain/track_elements/portal/PortalRenderer.java"));
        String portalSphereShader = read(sourceRoot.resolve(
                "com/example/game3d_opengl/game/terrain/track_elements/portal/rendering/PortalSphereShaderPair.java"));
        String portalWireframeShader = read(sourceRoot.resolve(
                "com/example/game3d_opengl/game/terrain/track_elements/portal/rendering/PortalWireframeShaderPair.java"));
        String potionRenderer = read(sourceRoot.resolve(
                "com/example/game3d_opengl/game/terrain/presentation/PotionRenderer.java"));
        String spikeRenderer = read(sourceRoot.resolve(
                "com/example/game3d_opengl/game/terrain/presentation/DeathSpikeRenderer.java"));

        assertTrue(prepared.contains("com.example.game3d.authoring.GameplayTerrainStream"));
        assertTrue(prepared.contains("com.example.game3d.authoring.GameplayLevelCatalog"));
        assertTrue(gameplay.contains("game.terrain.presentation.TerrainPresentation"));
        assertTrue(presentation.contains("com.example.game3d.core.terrain.addon.Addon"));
        assertTrue(presentation.contains("com.example.game3d.core.terrain.TerrainSnapshot"));

        assertFalse("Renderer registry must not install itself into mutable legacy addons",
                batches.contains("installDefaultBatchRenderer"));
        assertFalse("Renderer registry must consume presentation instances, not legacy addons",
                batches.contains("terrain_api.addon.Addon"));
        assertFalse("Production batch renderers must not expose a process-wide default",
                batches.contains("defaultRenderers"));
        assertFalse("TerrainPresentation must receive its renderer registry explicitly",
                presentation.contains("getDefaultOrNull"));
        assertFalse("Canonical terrain shader must be owned by the GL-context registry",
                terrainShader.contains("sharedShader"));
        assertFalse("Canonical terrain mesh must not reach a process-wide shader",
                terrainMesh.contains("sharedShader"));
        assertTrue(terrainShader.contains("deleteOwnedProgram()"));
        assertTrue(portalSphereShader.contains("deleteOwnedProgram()"));
        assertTrue(portalWireframeShader.contains("deleteOwnedProgram()"));
        assertTrue(portalRenderer.contains("addon.width"));
        assertTrue(portalRenderer.contains("addon.height"));
        assertTrue(portalRenderer.contains("addon.center"));
        assertTrue(portalRenderer.contains("addon.forward"));
        assertTrue(portalRenderer.contains("addon.up"));
        assertTrue(portalRenderer.contains("addon.visualStyleId"));
        assertTrue(potionRenderer.contains("addon.center"));
        assertTrue(potionRenderer.contains("addon.visualStyleId"));
        assertTrue(spikeRenderer.contains("addon.nearLeft"));
        assertTrue(spikeRenderer.contains("addon.nearRight"));
        assertTrue(spikeRenderer.contains("addon.farLeft"));
        assertTrue(spikeRenderer.contains("addon.farRight"));
        assertTrue(spikeRenderer.contains("addon.apex"));
        assertTrue(spikeRenderer.contains("addon.outwardNormal"));
        assertTrue(spikeRenderer.contains("addon.baseOffset"));
    }

    @Test
    public void legacyTerrainImportsCannotEscapeTheirQuarantine() throws Exception {
        Path sourceRoot = findAppProject().resolve(JAVA_ROOT);
        List<String> violations = new ArrayList<String>();
        for (Path source : javaFiles(sourceRoot)) {
            String relative = sourceRoot.relativize(source)
                    .toString().replace('\\', '/');
            if (isLegacyQuarantine(relative)) {
                continue;
            }
            for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("import ")
                        && trimmed.contains(
                        "com.example.game3d_opengl.game.terrain.terrain_api.")) {
                    violations.add(relative + ": " + trimmed);
                }
            }
        }
        if (!violations.isEmpty()) {
            fail("Legacy terrain dependency escaped its compatibility quarantine:\n"
                    + joinLines(violations));
        }
    }

    private static Path findAppProject() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve(JAVA_ROOT))) {
                return current;
            }
            if (Files.isDirectory(current.resolve("app").resolve(JAVA_ROOT))) {
                return current.resolve("app");
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate the app project from user.dir");
    }

    private static List<Path> javaFiles(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static String read(Path source) throws IOException {
        return new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
    }

    private static boolean isLegacyQuarantine(String relative) {
        return relative.startsWith(
                "com/example/game3d_opengl/game/terrain/terrain_api/")
                || relative.startsWith(
                "com/example/game3d_opengl/game/terrain/terrain_structures/")
                || relative.startsWith(
                "com/example/game3d_opengl/game/stage/stages/test/")
                || relative.equals(
                "com/example/game3d_opengl/game/terrain/track_elements/potion/Potion.java")
                || relative.equals(
                "com/example/game3d_opengl/game/terrain/track_elements/spike/DeathSpike.java")
                || relative.equals(
                "com/example/game3d_opengl/game/terrain/track_elements/portal/Portal.java")
                || relative.equals(
                "com/example/game3d_opengl/game/terrain/track_elements/portal/ExitPortal.java");
    }

    private static String joinLines(List<String> lines) {
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            if (result.length() > 0) {
                result.append('\n');
            }
            result.append(line);
        }
        return result.toString();
    }
}
