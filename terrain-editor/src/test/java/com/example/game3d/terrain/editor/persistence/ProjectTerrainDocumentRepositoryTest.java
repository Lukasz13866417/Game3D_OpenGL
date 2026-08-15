package com.example.game3d.terrain.editor.persistence;

import com.example.game3d.terrain.editor.edit.RepeatSpec;
import com.example.game3d.terrain.editor.edit.TileEdits;
import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.io.CodecException;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.StructureDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectTerrainDocumentRepositoryTest {
    @TempDir Path projectRoot;

    @Test void savedProjectDocumentsAreAvailableByIdAndPathOnlyAfterExplicitReload()
            throws Exception {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        Path content = Files.createDirectories(projectRoot.resolve("terrain-content"));
        Path structures = Files.createDirectories(content.resolve("structures"));
        Path levels = Files.createDirectories(content.resolve("levels"));
        StructureDocument blank = DocumentFactories.blankStructure(
                "shared.structure", GridMode.ADVANCED);
        Files.writeString(structures.resolve("shared.json"), codec.encode(blank));
        Files.writeString(levels.resolve("course.json"), codec.encode(
                DocumentFactories.blankLevel("shared.level", "gameplay-default")));

        ProjectTerrainDocumentRepository repository =
                new ProjectTerrainDocumentRepository(codec);
        repository.reload(projectRoot);

        assertNotNull(repository.findStructure("shared.structure"));
        assertNotNull(repository.findStructure("structures/shared"));
        assertNotNull(repository.findLevel("shared.level"));
        assertNotNull(repository.findLevel("levels/course.json"));

        StructureDocument oneTile = (StructureDocument) TileEdits.repeat(
                0, true, "NORMAL",
                new RepeatSpec(1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0),
                () -> "00000000-0000-0000-0000-000000000001").apply(blank);
        Files.writeString(structures.resolve("shared.json"), codec.encode(oneTile));
        assertEquals(0, repository.findStructure("shared.structure").tiles().size(),
                "external changes must not silently alter the loaded snapshot");

        repository.reload(projectRoot);
        assertEquals(1, repository.findStructure("shared.structure").tiles().size());
    }

    @Test void failedReloadKeepsThePreviousUsableSnapshot() throws Exception {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        Path content = Files.createDirectories(projectRoot.resolve("terrain-content"));
        Path structures = Files.createDirectories(content.resolve("structures"));
        StructureDocument blank = DocumentFactories.blankStructure(
                "shared.structure", GridMode.ADVANCED);
        Files.writeString(structures.resolve("shared.json"), codec.encode(blank));
        ProjectTerrainDocumentRepository repository =
                new ProjectTerrainDocumentRepository(codec);
        repository.reload(projectRoot);

        Files.writeString(content.resolve("broken.json"), "{ definitely-not-json }");
        assertThrows(CodecException.class, () -> repository.reload(projectRoot));
        assertNotNull(repository.findStructure("shared.structure"));
    }
}
