package com.example.game3d.terrain.io.publish;

import com.example.game3d.authoring.GameplayLevelCatalog;
import com.example.game3d.authoring.GameplayLevelProvider;
import com.example.game3d.authoring.GenerationBudget;
import com.example.game3d.authoring.MaterializedStructure;
import com.example.game3d.authoring.Terrain;
import com.example.game3d.authoring.TerrainMaterializer;
import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.terrain.io.authoring.DataBackedStructureFactory;
import com.example.game3d.terrain.io.model.AddonKind;
import com.example.game3d.terrain.io.model.AddonReservation;
import com.example.game3d.terrain.io.model.CatalogDocument;
import com.example.game3d.terrain.io.model.CatalogEntry;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.Placement;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;
import com.example.game3d.terrain.io.resolve.InMemoryTerrainDocumentRepository;
import com.example.game3d.terrain.io.resolve.ResolvedLevel;
import com.example.game3d.terrain.io.resolve.TerrainReferenceResolver;
import com.example.game3d.terrain.io.store.AtomicFileStore;
import com.example.game3d.terrain.io.store.ContentDigests;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TerrainPublisherTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void preservesCatalogOrderAndPublishesAtomically() throws Exception {
        Path output = temporary.newFile("runtime.json").toPath();
        CatalogDocument catalog = new CatalogDocument(1, "main", Arrays.asList(
                new CatalogEntry("builtin.0", CatalogEntry.Kind.JAVA_PROVIDER, "provider.0", true),
                new CatalogEntry("disabled", CatalogEntry.Kind.JAVA_PROVIDER, "disabled", false),
                new CatalogEntry("builtin.1", CatalogEntry.Kind.JAVA_PROVIDER, "provider.1", true)));
        TerrainContentCompiler compiler = compiler(false);

        PublishResult result = publisher(compiler).publish(catalog, emptyRepository(), output);
        String text = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);

        assertEquals(2, result.entryCount());
        assertTrue(text.indexOf("builtin.0") < text.indexOf("builtin.1"));
        assertTrue(!text.contains("disabled\""));
    }

    @Test public void compilerFailureLeavesPreviousGoodAssetUntouched() throws Exception {
        Path output = temporary.newFile("runtime.json").toPath();
        Files.write(output, "previous-good".getBytes(StandardCharsets.UTF_8));
        CatalogDocument catalog = new CatalogDocument(1, "main", Collections.singletonList(
                new CatalogEntry("builtin", CatalogEntry.Kind.JAVA_PROVIDER, "provider", true)));

        try {
            publisher(compiler(true)).publish(catalog, emptyRepository(), output);
            org.junit.Assert.fail("Expected digest mismatch");
        } catch (PublishException expected) {
            assertTrue(expected.getMessage().contains("digest mismatch"));
        }
        assertEquals("previous-good", new String(Files.readAllBytes(output), StandardCharsets.UTF_8));
    }

    @Test public void publishedJsonMaterializesIdenticallyAtRuntime() throws Exception {
        Path output = temporary.newFile("runtime-parity.json").toPath();
        StructureDocument structure = new StructureDocument(1, "custom-structure",
                GridMode.ADVANCED, Arrays.asList(
                new TileRecord("10000000-0000-0000-0000-000000000001", true,
                        7.5, 4.0, 0.0, "NORMAL", 1.0, 1.0),
                new TileRecord("10000000-0000-0000-0000-000000000002", true,
                        -2.0, 0.0, 0.0, "BOOST_RAMP", 0.8, 1.2)),
                Collections.singletonList(new AddonReservation(
                        "20000000-0000-0000-0000-000000000001",
                        AddonKind.AIR_JUMP_POTION,
                        Placement.normalized(
                                "10000000-0000-0000-0000-000000000002", 0.5, 0.5),
                        null, Collections.<String, Double>emptyMap())));
        LevelDocument level = new LevelDocument(1, "custom-level",
                TrackProfile.GAMEPLAY_PROFILE_ID,
                Collections.singletonList(LevelEntry.reference(
                        "30000000-0000-0000-0000-000000000001", structure.id())));
        List<CatalogEntry> entries = new ArrayList<CatalogEntry>();
        for (GameplayLevelProvider provider : GameplayLevelCatalog.builtIns().entries()) {
            entries.add(new CatalogEntry(provider.stableId(),
                    CatalogEntry.Kind.JAVA_PROVIDER, provider.stableId(), true));
        }
        entries.add(new CatalogEntry("custom-provider", CatalogEntry.Kind.JSON_LEVEL,
                level.id(), true));
        CatalogDocument catalog = new CatalogDocument(1, "main", entries);
        InMemoryTerrainDocumentRepository repository =
                new InMemoryTerrainDocumentRepository(
                        Collections.singletonList(structure), Collections.singletonList(level));

        publisher(new AuthoringTerrainContentCompiler()).publish(catalog, repository, output);
        GameplayLevelProvider runtimeProvider;
        try (java.io.Reader reader = Files.newBufferedReader(output, StandardCharsets.UTF_8)) {
            runtimeProvider = new PublishedGameplayCatalogLoader().load(reader).entries().get(6);
        }
        MaterializedStructure source = TerrainMaterializer.materialize(
                DataBackedStructureFactory.create(structure),
                TrackProfile.gameplayDefault(), Vec3.ZERO, 77L);
        MaterializedStructure runtime = TerrainMaterializer.materialize(
                runtimeProvider.create(123L),
                TrackProfile.gameplayDefault(), Vec3.ZERO, 77L);

        assertEquals(source.segments.size(), runtime.segments.size());
        for (int i = 0; i < source.segments.size(); i++) {
            assertEquals(source.segments.get(i).deterministicDigest(),
                    runtime.segments.get(i).deterministicDigest());
        }
    }

    @Test public void rejectsInvalidReferencedLevelBeforeReplacingArtifact() throws Exception {
        Path output = temporary.newFile("invalid-child.json").toPath();
        Files.write(output, "previous-good".getBytes(StandardCharsets.UTF_8));
        StructureDocument structure = new StructureDocument(1, "child-structure",
                GridMode.ADVANCED, Collections.singletonList(new TileRecord(
                "40000000-0000-0000-0000-000000000001", true,
                0, 0, 0, "NORMAL", 1, 1)), Collections.emptyList());
        LevelDocument child = new LevelDocument(2, "child",
                TrackProfile.GAMEPLAY_PROFILE_ID,
                Collections.singletonList(LevelEntry.inline(
                        "40000000-0000-0000-0000-000000000002", structure)));
        LevelDocument root = new LevelDocument(1, "root",
                TrackProfile.GAMEPLAY_PROFILE_ID,
                Collections.singletonList(LevelEntry.levelReference(
                        "40000000-0000-0000-0000-000000000003", child.id())));
        CatalogDocument catalog = new CatalogDocument(1, "main",
                Collections.singletonList(new CatalogEntry("custom",
                        CatalogEntry.Kind.JSON_LEVEL, root.id(), true)));
        InMemoryTerrainDocumentRepository repository =
                new InMemoryTerrainDocumentRepository(
                        Collections.<StructureDocument>emptyList(), Arrays.asList(root, child));

        try {
            publisher(new AuthoringTerrainContentCompiler()).publish(
                    catalog, repository, output);
            org.junit.Assert.fail("Expected referenced level validation failure");
        } catch (PublishException expected) {
            assertTrue(expected.getMessage().contains("validation"));
        }
        assertEquals("previous-good",
                new String(Files.readAllBytes(output), StandardCharsets.UTF_8));
    }

    @Test public void repeatedSavedStructurePublishesWithRuntimeParityAndUniqueMappings()
            throws Exception {
        Path output = temporary.newFile("repeated-runtime.json").toPath();
        StructureDocument repeated = new StructureDocument(1, "repeated",
                GridMode.ADVANCED, Collections.singletonList(new TileRecord(
                "60000000-0000-0000-0000-000000000001", true,
                3.0, 1.0, 0.0, "NORMAL", 1.0, 1.0)),
                Collections.singletonList(new AddonReservation(
                        "60000000-0000-0000-0000-000000000004",
                        AddonKind.AIR_JUMP_POTION,
                        Placement.normalized(
                                "60000000-0000-0000-0000-000000000001", 0.5, 0.5),
                        null, Collections.<String, Double>emptyMap())));
        LevelDocument level = new LevelDocument(1, "repeated-level",
                TrackProfile.GAMEPLAY_PROFILE_ID, Arrays.asList(
                LevelEntry.reference("60000000-0000-0000-0000-000000000002",
                        repeated.id()),
                LevelEntry.reference("60000000-0000-0000-0000-000000000003",
                        repeated.id())));
        List<CatalogEntry> catalogEntries = new ArrayList<CatalogEntry>();
        for (GameplayLevelProvider provider : GameplayLevelCatalog.builtIns().entries()) {
            catalogEntries.add(new CatalogEntry(provider.stableId(),
                    CatalogEntry.Kind.JAVA_PROVIDER, provider.stableId(), true));
        }
        catalogEntries.add(new CatalogEntry("repeated-provider",
                CatalogEntry.Kind.JSON_LEVEL, level.id(), true));
        publisher(new AuthoringTerrainContentCompiler()).publish(
                new CatalogDocument(1, "main", catalogEntries),
                new InMemoryTerrainDocumentRepository(
                        Collections.singletonList(repeated), Collections.singletonList(level)),
                output);

        GameplayLevelProvider runtimeProvider;
        try (java.io.Reader reader = Files.newBufferedReader(output, StandardCharsets.UTF_8)) {
            runtimeProvider = new PublishedGameplayCatalogLoader().load(reader).entries().get(6);
        }
        MaterializedStructure runtime = TerrainMaterializer.materialize(
                runtimeProvider.create(0L), TrackProfile.gameplayDefault(), Vec3.ZERO, 0L);
        Terrain source = new Terrain(TrackProfile.gameplayDefault(), Vec3.ZERO, 0L);
        source.enqueueStructure(DataBackedStructureFactory.create(repeated));
        source.enqueueStructure(DataBackedStructureFactory.create(repeated));
        source.generate(GenerationBudget.UNLIMITED);

        assertEquals(2, runtime.segments.size());
        assertEquals(2, runtime.sourceSegmentIds.size());
        assertEquals(2, runtime.sourceAddonIds.size());
        assertEquals(source.snapshot().segments.size(), runtime.segments.size());
        for (int i = 0; i < runtime.segments.size(); i++) {
            assertEquals(source.snapshot().segments.get(i).deterministicDigest(),
                    runtime.segments.get(i).deterministicDigest());
        }
    }

    @Test public void rejectsReferencedLevelWithDifferentSessionProfile() throws Exception {
        Path output = temporary.newFile("profile-mismatch.json").toPath();
        StructureDocument structure = new StructureDocument(1, "child-structure",
                GridMode.ADVANCED, Collections.singletonList(new TileRecord(
                "50000000-0000-0000-0000-000000000001", true,
                0, 0, 0, "NORMAL", 1, 1)), Collections.emptyList());
        LevelDocument child = new LevelDocument(1, "child", "another-profile",
                Collections.singletonList(LevelEntry.inline(
                        "50000000-0000-0000-0000-000000000002", structure)));
        LevelDocument root = new LevelDocument(1, "root",
                TrackProfile.GAMEPLAY_PROFILE_ID,
                Collections.singletonList(LevelEntry.levelReference(
                        "50000000-0000-0000-0000-000000000003", child.id())));
        CatalogDocument catalog = new CatalogDocument(1, "main",
                Collections.singletonList(new CatalogEntry("custom",
                        CatalogEntry.Kind.JSON_LEVEL, root.id(), true)));

        try {
            publisher(new AuthoringTerrainContentCompiler()).publish(catalog,
                    new InMemoryTerrainDocumentRepository(
                            Collections.<StructureDocument>emptyList(),
                            Arrays.asList(root, child)), output);
            org.junit.Assert.fail("Expected profile mismatch rejection");
        } catch (PublishException expected) {
            assertTrue(expected.getMessage().contains("profile mismatch"));
        }
    }

    private static TerrainPublisher publisher(TerrainContentCompiler compiler) {
        return new TerrainPublisher(new TerrainValidator(), new TerrainReferenceResolver(), compiler,
                new AtomicFileStore());
    }

    private static TerrainContentCompiler compiler(final boolean badDigest) {
        return new TerrainContentCompiler() {
            @Override public CompiledTerrainContent compileJavaProvider(CatalogEntry entry) {
                String json = "{\"provider\":\"" + entry.location() + "\"}";
                return new CompiledTerrainContent(json,
                        badDigest ? "wrong" : ContentDigests.sha256(json));
            }
            @Override public CompiledTerrainContent compileJsonLevel(CatalogEntry entry, ResolvedLevel level) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static InMemoryTerrainDocumentRepository emptyRepository() {
        return new InMemoryTerrainDocumentRepository(Collections.emptyList(), Collections.emptyList());
    }
}
