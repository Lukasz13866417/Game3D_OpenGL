package com.example.game3d.terrain.io.cli;

import com.example.game3d.authoring.GameplayLevelCatalog;
import com.example.game3d.authoring.GameplayLevelProvider;
import com.example.game3d.authoring.TerrainMaterializer;
import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.CatalogDocument;
import com.example.game3d.terrain.io.model.CatalogEntry;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;
import com.example.game3d.terrain.io.publish.PublishedGameplayCatalogLoader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TerrainContentCliTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void publishesAndStrictlyValidatesRuntimeArtifact() throws Exception {
        Path sourceRoot = temporary.newFolder("content").toPath();
        Path catalogPath = sourceRoot.resolve("catalog.json");
        Path output = temporary.getRoot().toPath().resolve("runtime.json");
        List<CatalogEntry> entries = new ArrayList<CatalogEntry>();
        for (GameplayLevelProvider provider : GameplayLevelCatalog.builtIns().entries()) {
            entries.add(new CatalogEntry(provider.stableId(), CatalogEntry.Kind.JAVA_PROVIDER,
                    provider.stableId(), true));
        }
        CatalogDocument catalog = new CatalogDocument(1, "source", entries);
        Files.write(catalogPath, new TerrainJsonCodec().encode(catalog)
                .getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream outputText = new ByteArrayOutputStream();
        ByteArrayOutputStream errorText = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outputText);
        PrintStream error = new PrintStream(errorText);

        assertEquals(0, TerrainContentCli.run(new String[] { "publish",
                catalogPath.toString(), sourceRoot.toString(), output.toString() }, out, error));
        assertTrue(Files.isRegularFile(output));
        assertEquals(0, TerrainContentCli.run(new String[] { "validate", output.toString() },
                out, error));
        assertTrue(outputText.toString("UTF-8").contains("6 gameplay providers"));
        assertEquals("", errorText.toString("UTF-8"));
    }

    @Test public void invalidPublishedArtifactReturnsFailure() throws Exception {
        Path invalid = temporary.newFile("invalid.json").toPath();
        Files.write(invalid, "{}".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, TerrainContentCli.run(new String[] { "validate", invalid.toString() },
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream())));
    }

    @Test public void customJsonLevelPublishesLoadsAndMaterializesEndToEnd() throws Exception {
        Path sourceRoot = temporary.newFolder("custom-content").toPath();
        Path structures = Files.createDirectories(sourceRoot.resolve("structures"));
        Path levels = Files.createDirectories(sourceRoot.resolve("levels"));
        Path catalogPath = sourceRoot.resolve("catalog.json");
        Path output = temporary.getRoot().toPath().resolve("custom-runtime.json");
        TerrainJsonCodec codec = new TerrainJsonCodec();
        StructureDocument structure = new StructureDocument(
                1, "custom-structure", GridMode.ADVANCED,
                java.util.Collections.singletonList(new TileRecord(
                        "81000000-0000-0000-0000-000000000001", true,
                        4.0, 2.0, 0.0, "NORMAL", 1.0, 1.0)),
                java.util.Collections.emptyList());
        LevelDocument level = new LevelDocument(
                1, "custom-level", TrackProfile.GAMEPLAY_PROFILE_ID,
                java.util.Collections.singletonList(LevelEntry.reference(
                        "82000000-0000-0000-0000-000000000001",
                        "structures/custom-structure")));
        Files.write(structures.resolve("custom-structure.json"),
                codec.encode(structure).getBytes(StandardCharsets.UTF_8));
        Files.write(levels.resolve("custom-level.json"),
                codec.encode(level).getBytes(StandardCharsets.UTF_8));
        List<CatalogEntry> entries = builtinCatalogEntries();
        entries.add(new CatalogEntry("custom-level", CatalogEntry.Kind.JSON_LEVEL,
                "levels/custom-level", true));
        Files.write(catalogPath, codec.encode(new CatalogDocument(
                1, "with-custom", entries)).getBytes(StandardCharsets.UTF_8));

        assertEquals(0, TerrainContentCli.run(new String[] { "publish",
                        catalogPath.toString(), sourceRoot.toString(), output.toString() },
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream())));
        try (Reader reader = Files.newBufferedReader(output, StandardCharsets.UTF_8)) {
            GameplayLevelCatalog catalog =
                    new PublishedGameplayCatalogLoader().load(reader);
            assertEquals(7, catalog.entries().size());
            GameplayLevelProvider custom = catalog.entries().get(6);
            assertEquals("custom-level", custom.stableId());
            assertEquals(1, TerrainMaterializer.materialize(
                    custom.create(123L), TrackProfile.gameplayDefault(),
                    Vec3.ZERO, 9L).segments.size());
        }
    }

    @Test public void invalidSourceCatalogLeavesPreviousRuntimeArtifactUntouched() throws Exception {
        Path sourceRoot = temporary.newFolder("invalid-source").toPath();
        Path catalogPath = sourceRoot.resolve("catalog.json");
        Path output = temporary.getRoot().toPath().resolve("previous-runtime.json");
        Files.write(output, "previous-good".getBytes(StandardCharsets.UTF_8));
        List<CatalogEntry> reordered = new ArrayList<CatalogEntry>();
        List<GameplayLevelProvider> providers = GameplayLevelCatalog.builtIns().entries();
        for (int i = providers.size() - 1; i >= 0; i--) {
            GameplayLevelProvider provider = providers.get(i);
            reordered.add(new CatalogEntry(provider.stableId(),
                    CatalogEntry.Kind.JAVA_PROVIDER, provider.stableId(), true));
        }
        Files.write(catalogPath, new TerrainJsonCodec().encode(
                new CatalogDocument(1, "invalid-order", reordered)).getBytes(
                StandardCharsets.UTF_8));

        assertEquals(1, TerrainContentCli.run(new String[] { "publish",
                        catalogPath.toString(), sourceRoot.toString(), output.toString() },
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream())));
        assertEquals("previous-good", new String(
                Files.readAllBytes(output), StandardCharsets.UTF_8));
    }

    private static List<CatalogEntry> builtinCatalogEntries() {
        List<CatalogEntry> entries = new ArrayList<CatalogEntry>();
        for (GameplayLevelProvider provider : GameplayLevelCatalog.builtIns().entries()) {
            entries.add(new CatalogEntry(provider.stableId(),
                    CatalogEntry.Kind.JAVA_PROVIDER, provider.stableId(), true));
        }
        return entries;
    }
}
