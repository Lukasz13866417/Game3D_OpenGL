package com.example.game3d.terrain.io.resolve;

import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.CatalogDocument;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FileTerrainDocumentRepositoryTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void recursivelyLoadsFrozenSourceDocumentsByStableId() throws Exception {
        Path root = temporary.newFolder("sources").toPath();
        Path nested = Files.createDirectories(root.resolve("nested"));
        TerrainJsonCodec codec = new TerrainJsonCodec();
        StructureDocument structure = new StructureDocument(1, "structure", GridMode.ADVANCED,
                Collections.singletonList(new TileRecord(
                        "60000000-0000-0000-0000-000000000001", true, 0, 0, 0,
                        "NORMAL", 1, 1)), Collections.emptyList());
        LevelDocument level = new LevelDocument(1, "level", TrackProfile.GAMEPLAY_PROFILE_ID,
                Collections.singletonList(LevelEntry.reference(
                        "70000000-0000-0000-0000-000000000001", "structure")));
        Files.write(root.resolve("structure.json"), codec.encode(structure).getBytes(StandardCharsets.UTF_8));
        Files.write(nested.resolve("level.json"), codec.encode(level).getBytes(StandardCharsets.UTF_8));
        CatalogDocument catalog = new CatalogDocument(1, "main", Collections.emptyList());
        Path catalogPath = root.resolve("catalog.json");
        Files.write(catalogPath, codec.encode(catalog).getBytes(StandardCharsets.UTF_8));
        Path published = Files.createDirectories(root.resolve("published/terrain"));
        Files.write(published.resolve("runtime-catalog.json"),
                "{\"formatVersion\":1,\"sourceCatalogId\":\"runtime\",\"entries\":[]}".getBytes(
                        StandardCharsets.UTF_8));

        FileTerrainDocumentRepository repository =
                FileTerrainDocumentRepository.load(root, codec);
        assertNotNull(repository.findStructure("structure"));
        assertEquals("level", repository.findLevel("level").id());
        assertEquals("level", repository.findLevel("nested/level.json").id());
        assertEquals("level", repository.findLevel("nested/level").id());
        assertEquals(root.resolve("structure.json").toAbsolutePath().normalize(),
                repository.contentIndex().structuresById().get("structure").sourcePath());
        assertEquals(nested.resolve("level.json").toAbsolutePath().normalize(),
                repository.contentIndex().levelsById().get("level").sourcePath());
        assertEquals(catalogPath.toAbsolutePath().normalize(),
                repository.contentIndex().catalogsById().get("main").sourcePath());
        try {
            repository.contentIndex().levelsById().clear();
            org.junit.Assert.fail("Expected immutable project content index");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }
}
