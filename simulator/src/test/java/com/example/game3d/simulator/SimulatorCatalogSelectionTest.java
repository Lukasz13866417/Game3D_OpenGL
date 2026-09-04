package com.example.game3d.simulator;

import com.example.game3d.authoring.GameplayLevelCatalog;
import com.example.game3d.authoring.GameplayLevelProvider;
import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.terrain.io.model.CatalogDocument;
import com.example.game3d.terrain.io.model.CatalogEntry;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;
import com.example.game3d.terrain.io.publish.AuthoringTerrainContentCompiler;
import com.example.game3d.terrain.io.publish.TerrainPublisher;
import com.example.game3d.terrain.io.resolve.InMemoryTerrainDocumentRepository;
import com.example.game3d.terrain.io.resolve.TerrainReferenceResolver;
import com.example.game3d.terrain.io.store.AtomicFileStore;
import com.example.game3d.terrain.io.validation.TerrainValidator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SimulatorCatalogSelectionTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void explicitPublishedEntryRunsWithoutOrdinalSearch() throws Exception {
        Path runtime = publishCatalog();

        Scenario scenario = ScenarioRegistry.fromPublishedCatalog(runtime, "custom-five")
                .require("published_catalog_level");

        assertEquals(5, scenario.terrainSnapshot.segments.size());
        assertTrue(scenario.description.contains("custom-five"));

        Path trace = temporary.getRoot().toPath().resolve("selected.ndjson");
        Path repeated = temporary.getRoot().toPath().resolve("selected-repeat.ndjson");
        SimulatorMain.main(new String[] {
                "run", "published_catalog_level",
                "--catalog", runtime.toString(),
                "--catalog-entry", "custom-five",
                "--ticks", "120",
                "--trace", "summary",
                "--out", trace.toString()
        });
        SimulatorMain.main(new String[] {
                "run", "published_catalog_level",
                "--catalog", runtime.toString(),
                "--catalog-entry", "custom-five",
                "--ticks", "120",
                "--trace", "summary",
                "--out", repeated.toString()
        });
        assertTrue(Files.size(trace) > 0L);
        assertArrayEquals(Files.readAllBytes(trace), Files.readAllBytes(repeated));
    }

    @Test public void explicitCatalogIsStrictAndNeverFallsBack() throws Exception {
        Path invalid = temporary.newFile("invalid-runtime.json").toPath();
        Files.write(invalid, "{}".getBytes(StandardCharsets.UTF_8));
        try {
            SimulatorMain.main(new String[] {
                    "run", "published_catalog_level",
                    "--catalog", invalid.toString(),
                    "--catalog-entry", "stairs_curve_line",
                    "--ticks", "1"
            });
            fail("Expected strict runtime-catalog rejection");
        } catch (com.example.game3d.terrain.io.publish.PublishedCatalogException expected) {
            // Expected: an explicit artifact is never replaced by the built-in fallback.
        }
    }

    @Test public void unknownExplicitEntryFailsBeforeSimulation() throws Exception {
        Path runtime = publishCatalog();
        try {
            ScenarioRegistry.fromPublishedCatalog(runtime, "missing-entry");
            fail("Expected exact entry lookup failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("missing-entry"));
        }
    }

    @Test public void explicitCatalogRequiresAnExactEntryId() throws Exception {
        try {
            SimulatorMain.main(new String[] {
                    "run", "published_catalog_level",
                    "--catalog", temporary.getRoot().toPath()
                            .resolve("unused-runtime.json").toString(),
                    "--ticks", "1"
            });
            fail("Expected explicit catalog/entry pairing rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains(
                    "--catalog requires --catalog-entry"));
        }
    }

    private Path publishCatalog() throws Exception {
        LevelDocument two = level("level-two", 2, 1000);
        LevelDocument five = level("level-five", 5, 2000);
        List<CatalogEntry> entries = new ArrayList<CatalogEntry>();
        for (GameplayLevelProvider provider : GameplayLevelCatalog.builtIns().entries()) {
            entries.add(new CatalogEntry(provider.stableId(),
                    CatalogEntry.Kind.JAVA_PROVIDER, provider.stableId(), true));
        }
        entries.add(new CatalogEntry("custom-two",
                CatalogEntry.Kind.JSON_LEVEL, two.id(), true));
        entries.add(new CatalogEntry("custom-five",
                CatalogEntry.Kind.JSON_LEVEL, five.id(), true));
        Path runtime = temporary.getRoot().toPath().resolve("runtime-catalog.json");
        new TerrainPublisher(new TerrainValidator(), new TerrainReferenceResolver(),
                new AuthoringTerrainContentCompiler(), new AtomicFileStore()).publish(
                new CatalogDocument(1, "test-catalog", entries),
                new InMemoryTerrainDocumentRepository(
                        Collections.<StructureDocument>emptyList(), Arrays.asList(two, five)),
                runtime);
        return runtime;
    }

    private static LevelDocument level(String id, int tileCount, int seed) {
        List<TileRecord> tiles = new ArrayList<TileRecord>();
        for (int index = 0; index < tileCount; index++) {
            tiles.add(new TileRecord(UUID.nameUUIDFromBytes(
                    ("tile:" + seed + ":" + index).getBytes(StandardCharsets.UTF_8)).toString(),
                    true, 0, 0, 0, "NORMAL", 1, 1));
        }
        StructureDocument inline = new StructureDocument(
                1, id + ".structure", GridMode.ADVANCED,
                tiles, Collections.emptyList());
        return new LevelDocument(1, id, TrackProfile.GAMEPLAY_PROFILE_ID,
                Collections.singletonList(LevelEntry.inline(
                        UUID.nameUUIDFromBytes(("entry:" + seed)
                                .getBytes(StandardCharsets.UTF_8)).toString(), inline)));
    }
}
