package com.example.game3d.terrain.editor;

import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.editor.state.EditorState;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.catalog.CatalogDocumentEdits;
import com.example.game3d.terrain.io.model.CatalogDocument;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.resolve.FileTerrainDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainEditorAppCatalogMembershipTest {
    @TempDir Path directory;

    @Test
    void registrationRequiresTheCanonicalSavedProjectLevelNotOnlyItsId() throws Exception {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        Path contentRoot = Files.createDirectories(directory.resolve("terrain-content"));
        Path levels = Files.createDirectories(contentRoot.resolve("levels"));
        LevelDocument level = DocumentFactories.blankLevel(
                "level.alpha", "gameplay-default");
        Path savedSource = levels.resolve("alpha.terrain-level.json");
        Files.writeString(savedSource, codec.encode(level));
        FileTerrainDocumentRepository savedProject =
                FileTerrainDocumentRepository.load(contentRoot, codec);
        CatalogDocument catalog = CatalogDocumentEdits.addJsonLevel(
                CatalogDocumentEdits.newGameplayCatalog("main"),
                "play.alpha", level.id(), true);

        EditorState projectTab = state(level, savedSource);
        assertTrue(TerrainEditorApp.representsRegisteredProjectLevel(
                projectTab, catalog, savedProject));
        Path sameFileAlias = directory.resolve("saved-level-alias.json");
        Files.createLink(sameFileAlias, savedSource);
        assertTrue(TerrainEditorApp.representsRegisteredProjectLevel(
                state(level, sameFileAlias), catalog, savedProject),
                "a hard-link alias still represents the canonical saved source");

        Path standaloneCopy = directory.resolve("standalone-level.json");
        Files.writeString(standaloneCopy, codec.encode(level));
        assertFalse(TerrainEditorApp.representsRegisteredProjectLevel(
                state(level, standaloneCopy), catalog, savedProject),
                "a different file with the same document ID is not the catalog source");
        assertFalse(TerrainEditorApp.representsRegisteredProjectLevel(
                EditorState.unsaved(level), catalog, savedProject));

        CatalogDocument notRegistered =
                CatalogDocumentEdits.newGameplayCatalog("main");
        assertFalse(TerrainEditorApp.representsRegisteredProjectLevel(
                projectTab, notRegistered, savedProject));
    }

    private static EditorState state(LevelDocument level, Path source) {
        return new EditorState(level, source, null, Set.of(), 0L, List.of());
    }
}
