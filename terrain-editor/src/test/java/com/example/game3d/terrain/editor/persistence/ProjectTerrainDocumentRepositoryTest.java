package com.example.game3d.terrain.editor.persistence;

import com.example.game3d.terrain.editor.edit.RepeatSpec;
import com.example.game3d.terrain.editor.edit.TileEdits;
import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.io.CodecException;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.resolve.TerrainProjectContentIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
        assertEquals(structures.resolve("shared.json").toAbsolutePath().normalize(),
                repository.contentIndex().structuresById().get("shared.structure")
                        .sourcePath());
        assertEquals(levels.resolve("course.json").toAbsolutePath().normalize(),
                repository.contentIndex().levelsById().get("shared.level")
                        .sourcePath());
        assertEquals(repository.snapshotView().contentIndex().levelsById(),
                repository.contentIndex().levelsById(),
                "the wrapper must expose the exact current immutable generation");
        TerrainProjectContentIndex firstIndex = repository.contentIndex();

        StructureDocument oneTile = (StructureDocument) TileEdits.repeat(
                0, true, "NORMAL",
                new RepeatSpec(1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0),
                () -> "00000000-0000-0000-0000-000000000001").apply(blank);
        Files.writeString(structures.resolve("shared.json"), codec.encode(oneTile));
        assertEquals(0, repository.findStructure("shared.structure").tiles().size(),
                "external changes must not silently alter the loaded snapshot");

        repository.reload(projectRoot);
        assertEquals(1, repository.findStructure("shared.structure").tiles().size());
        assertNotSame(firstIndex, repository.contentIndex());
        assertEquals(0, firstIndex.structuresById().get("shared.structure")
                .document().tiles().size(), "an earlier index remains frozen");
        assertEquals(1, repository.contentIndex().structuresById()
                .get("shared.structure").document().tiles().size());
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

    @Test void loadedCandidateIsAdoptedAsOneGeneration() throws Exception {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        Path firstRoot = Files.createDirectories(projectRoot.resolve("first"));
        Path secondRoot = Files.createDirectories(projectRoot.resolve("second"));
        Path firstStructures = Files.createDirectories(
                firstRoot.resolve("terrain-content/structures"));
        Path secondStructures = Files.createDirectories(
                secondRoot.resolve("terrain-content/structures"));
        Files.writeString(firstStructures.resolve("first.json"), codec.encode(
                DocumentFactories.blankStructure("first", GridMode.ADVANCED)));
        Files.writeString(secondStructures.resolve("second.json"), codec.encode(
                DocumentFactories.blankStructure("second", GridMode.ADVANCED)));
        ProjectTerrainDocumentRepository current =
                new ProjectTerrainDocumentRepository(codec);
        ProjectTerrainDocumentRepository candidate =
                new ProjectTerrainDocumentRepository(codec);
        current.reload(firstRoot);
        candidate.reload(secondRoot);

        current.replaceWith(candidate);

        assertEquals(null, current.findStructure("first"));
        assertNotNull(current.findStructure("second"));
        assertEquals(secondRoot.resolve("terrain-content").toAbsolutePath(),
                current.contentRoot());
        assertEquals(candidate.contentIndex(), current.contentIndex());
    }
}
