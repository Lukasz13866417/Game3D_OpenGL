package com.example.game3d.terrain.editor.session;

import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.terrain.editor.compile.EditorReferenceSnapshot;
import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.resolve.FileTerrainDocumentRepository;
import com.example.game3d.terrain.io.resolve.TerrainDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorSessionIndexTest {
    @TempDir Path directory;
    @Test void changedStructureInvalidatesTransitiveLevelDependents() {
        StructureDocument structure = DocumentFactories.blankStructure("shared", GridMode.ADVANCED);
        LevelDocument first = level("first", LevelEntry.reference(uuid(1), "shared"));
        LevelDocument second = level("second", LevelEntry.levelReference(uuid(2), "first"));
        UUID structureWorkspace = UUID.randomUUID();
        UUID firstWorkspace = UUID.randomUUID();
        UUID secondWorkspace = UUID.randomUUID();

        EditorSessionIndex index = new EditorSessionIndex(emptyRepository(), List.of(
                open(structureWorkspace, structure),
                open(firstWorkspace, first),
                open(secondWorkspace, second)));

        assertEquals(Set.of(structureWorkspace, firstWorkspace, secondWorkspace),
                index.affectedBy(List.of("shared")));
    }

    @Test void changedOpenStructureInvalidatesOpenLevelThroughSavedIntermediateLevel()
            throws Exception {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        Path structures = Files.createDirectories(directory.resolve("structures"));
        Path levels = Files.createDirectories(directory.resolve("levels"));
        Path structureSource = structures.resolve("shared.json");
        StructureDocument savedStructure = DocumentFactories.blankStructure(
                "shared", GridMode.ADVANCED);
        Files.writeString(structureSource, codec.encode(savedStructure));
        Files.writeString(levels.resolve("middle.json"), codec.encode(level(
                "middle", LevelEntry.reference(uuid(1), "structures/shared"))));
        TerrainDocumentRepository repository = FileTerrainDocumentRepository.load(
                directory, codec);

        StructureDocument editedStructure = DocumentFactories.blankStructure(
                "shared", GridMode.BASIC);
        LevelDocument outer = level(
                "outer", LevelEntry.levelReference(uuid(2), "levels/middle"));
        UUID structureWorkspace = UUID.randomUUID();
        UUID outerWorkspace = UUID.randomUUID();
        EditorSessionIndex index = new EditorSessionIndex(repository, List.of(
                new EditorReferenceSnapshot.OpenDocument(
                        structureWorkspace, structureSource, editedStructure),
                open(outerWorkspace, outer)));

        assertEquals(Set.of(structureWorkspace, outerWorkspace),
                index.affectedBy(List.of("shared")));
    }

    @Test void referenceSnapshotDoesNotObserveLaterCollectionMutation() {
        StructureDocument structure = DocumentFactories.blankStructure("shared", GridMode.ADVANCED);
        ArrayList<EditorReferenceSnapshot.OpenDocument> open = new ArrayList<>();
        open.add(open(UUID.randomUUID(), structure));
        EditorSessionIndex index = new EditorSessionIndex(emptyRepository(), open);
        open.clear();

        assertSame(structure, index.references().findStructure("shared"));
    }

    @Test void duplicateOpenIdsProduceAnErrorAndNoArbitraryOpenWinner() {
        StructureDocument first = DocumentFactories.blankStructure("duplicate", GridMode.ADVANCED);
        StructureDocument second = DocumentFactories.blankStructure("duplicate", GridMode.BASIC);
        EditorSessionIndex index = new EditorSessionIndex(emptyRepository(), List.of(
                open(UUID.randomUUID(), first), open(UUID.randomUUID(), second)));

        assertFalse(index.references().problems().isEmpty());
        assertEquals(null, index.references().findStructure("duplicate"));
    }

    @Test void openDocumentOverridesOnlyItsOwnCanonicalSavedSource() throws Exception {
        StructureDocument saved = DocumentFactories.blankStructure(
                "shared", GridMode.ADVANCED);
        Path source = directory.resolve("shared.terrain-structure.json");
        Files.writeString(source, new TerrainJsonCodec().encode(saved));
        TerrainDocumentRepository repository = FileTerrainDocumentRepository.load(
                directory, new TerrainJsonCodec());
        StructureDocument edited = DocumentFactories.blankStructure(
                "shared", GridMode.BASIC);

        EditorSessionIndex sameSource = new EditorSessionIndex(repository, List.of(
                new EditorReferenceSnapshot.OpenDocument(
                        UUID.randomUUID(), source, edited)));
        assertSame(edited, sameSource.references().findStructure("shared"));
        assertSame(edited, sameSource.references().findStructure(
                "shared.terrain-structure.json"));
        assertSame(edited, sameSource.references().findStructure(
                "shared.terrain-structure"));
        assertTrue(sameSource.references().problems().isEmpty());

        EditorSessionIndex otherSource = new EditorSessionIndex(repository, List.of(
                new EditorReferenceSnapshot.OpenDocument(
                        UUID.randomUUID(), directory.resolve("other.json"), edited)));
        assertEquals(null, otherSource.references().findStructure("shared"));
        assertFalse(otherSource.references().problems().isEmpty());
    }

    @Test void openLevelAlsoShadowsEverySavedPathAlias() throws Exception {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        Path levels = Files.createDirectories(directory.resolve("levels"));
        Path source = levels.resolve("course.json");
        LevelDocument saved = level(
                "course", LevelEntry.reference(uuid(1), "saved-structure"));
        LevelDocument edited = level(
                "course", LevelEntry.reference(uuid(2), "edited-structure"));
        Files.writeString(source, codec.encode(saved));
        TerrainDocumentRepository repository = FileTerrainDocumentRepository.load(
                directory, codec);

        EditorSessionIndex index = new EditorSessionIndex(repository, List.of(
                new EditorReferenceSnapshot.OpenDocument(
                        UUID.randomUUID(), source, edited)));

        assertSame(edited, index.references().findLevel("course"));
        assertSame(edited, index.references().findLevel("levels/course.json"));
        assertSame(edited, index.references().findLevel("levels/course"));
    }

    @Test void renamingOpenDocumentHidesStaleSavedIdFromSameFile() throws Exception {
        StructureDocument saved = DocumentFactories.blankStructure(
                "old-id", GridMode.ADVANCED);
        Path source = directory.resolve("old.terrain-structure.json");
        Files.writeString(source, new TerrainJsonCodec().encode(saved));
        TerrainDocumentRepository repository = FileTerrainDocumentRepository.load(
                directory, new TerrainJsonCodec());
        StructureDocument renamed = DocumentFactories.blankStructure(
                "new-id", GridMode.ADVANCED);

        EditorSessionIndex index = new EditorSessionIndex(repository, List.of(
                new EditorReferenceSnapshot.OpenDocument(
                        UUID.randomUUID(), source, renamed)));

        assertEquals(null, index.references().findStructure("old-id"));
        assertSame(renamed, index.references().findStructure(
                "old.terrain-structure.json"));
        assertSame(renamed, index.references().findStructure(
                "old.terrain-structure"));
        assertSame(renamed, index.references().findStructure("new-id"));
    }

    private static EditorReferenceSnapshot.OpenDocument open(
            UUID workspaceId, com.example.game3d.terrain.io.model.TerrainSourceDocument document) {
        return new EditorReferenceSnapshot.OpenDocument(workspaceId, null, document);
    }

    private static LevelDocument level(String id, LevelEntry entry) {
        return new LevelDocument(1, id, TrackProfile.GAMEPLAY_PROFILE_ID, List.of(entry));
    }

    private static String uuid(int value) {
        return String.format("00000000-0000-0000-0000-%012d", value);
    }

    private static TerrainDocumentRepository emptyRepository() {
        return new TerrainDocumentRepository() {
            @Override public StructureDocument findStructure(String id) { return null; }
            @Override public LevelDocument findLevel(String id) { return null; }
        };
    }
}
