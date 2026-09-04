package com.example.game3d.terrain.editor.persistence;

import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.editor.edit.RepeatSpec;
import com.example.game3d.terrain.editor.edit.TileEdits;
import com.example.game3d.terrain.editor.state.EditorState;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.GridMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class EditorPersistenceTest {
    @TempDir Path directory;

    @Test void detectsExternalWriteTimeChangesWithoutReloading() throws Exception {
        EditorPersistence persistence = new EditorPersistence(new TerrainJsonCodec());
        Path source = directory.resolve("terrain.json");
        persistence.save(EditorState.unsaved(
                DocumentFactories.blankStructure("external", GridMode.ADVANCED)), source);
        FileTime known = Files.getLastModifiedTime(source);
        assertFalse(persistence.externallyChanged(source, known));

        Files.setLastModifiedTime(source, FileTime.fromMillis(known.toMillis() + 2000));

        assertTrue(persistence.externallyChanged(source, known));
    }

    @Test void exactRawDigestDetectsExternalChangeEvenWhenMtimeIsPreserved() throws Exception {
        EditorPersistence persistence = new EditorPersistence(new TerrainJsonCodec());
        Path source = directory.resolve("terrain.json");
        persistence.save(EditorState.unsaved(
                DocumentFactories.blankStructure("original", GridMode.ADVANCED)), source);
        EditorPersistence.LoadedDocument loaded = persistence.load(source);
        FileTime known = loaded.writeTime();
        String external = new TerrainJsonCodec().encode(
                DocumentFactories.blankStructure("external", GridMode.BASIC));
        Files.writeString(source, external);
        Files.setLastModifiedTime(source, known);
        assertTrue(persistence.externallyChanged(source, loaded.diskVersion()));

        SaveResult result = persistence.save(loaded.state(), source,
                ExpectedDiskVersion.exact(loaded.diskVersion()),
                SaveIntent.SAVE_IF_UNCHANGED);

        SaveResult.Conflict conflict = assertInstanceOf(SaveResult.Conflict.class, result);
        assertTrue(conflict.actual().isPresent());
        assertEquals(external, Files.readString(source));
    }

    @Test void conditionalSaveMarksOnlyTheBytesActuallyWrittenAsSaved() throws Exception {
        EditorPersistence persistence = new EditorPersistence(new TerrainJsonCodec());
        Path source = directory.resolve("terrain.json");
        persistence.save(EditorState.unsaved(
                DocumentFactories.blankStructure("conditional", GridMode.ADVANCED)), source);
        EditorPersistence.LoadedDocument loaded = persistence.load(source);
        EditorState edited = loaded.state().withDocument(TileEdits.repeat(
                0, true, "NORMAL", new RepeatSpec(
                        1, 0, 0, 0, 0, 0, 0, 2, 0, 1, 0),
                () -> "00000000-0000-0000-0000-000000000001")
                .apply(loaded.state().document()));

        SaveResult.Saved saved = assertInstanceOf(SaveResult.Saved.class,
                persistence.save(edited, source,
                        ExpectedDiskVersion.exact(loaded.diskVersion()),
                        SaveIntent.SAVE_IF_UNCHANGED));

        assertFalse(saved.state().isDirty(new TerrainJsonCodec()));
        assertEquals(saved.diskVersion().rawSha256(),
                persistence.diskVersion(source).rawSha256());
        // Alpha 2 is semantically invalid but finite, so draft persistence remains supported.
        assertEquals(2.0, ((com.example.game3d.terrain.io.model.StructureDocument)
                persistence.load(source).state().document()).tiles().get(0).alpha());
    }

    @Test void createNewNeverReplacesAnExistingTargetAndConfirmedOverwriteDoes() throws Exception {
        EditorPersistence persistence = new EditorPersistence(new TerrainJsonCodec());
        Path target = directory.resolve("existing.json");
        Files.writeString(target, "external bytes");
        EditorState state = EditorState.unsaved(
                DocumentFactories.blankStructure("created", GridMode.ADVANCED));

        assertInstanceOf(SaveResult.Conflict.class, persistence.save(state, target,
                ExpectedDiskVersion.absent(), SaveIntent.CREATE_NEW));
        assertEquals("external bytes", Files.readString(target));
        DiskVersion exactConflictVersion = persistence.diskVersion(target);

        SaveResult.Saved overwritten = assertInstanceOf(SaveResult.Saved.class,
                persistence.save(state, target,
                        ExpectedDiskVersion.exact(exactConflictVersion),
                        SaveIntent.OVERWRITE_CONFIRMED));
        assertEquals("created", overwritten.state().document().id());
        assertEquals("created", persistence.load(target).state().document().id());
    }

    @Test void confirmedOverwriteIsRejectedIfTheObservedConflictChangesAgain() throws Exception {
        EditorPersistence persistence = new EditorPersistence(new TerrainJsonCodec());
        Path target = directory.resolve("changing-conflict.json");
        Files.writeString(target, "external version one");
        DiskVersion confirmedVersion = persistence.diskVersion(target);
        Files.writeString(target, "external version two");
        EditorState state = EditorState.unsaved(
                DocumentFactories.blankStructure("editor-version", GridMode.ADVANCED));

        SaveResult result = persistence.save(state, target,
                ExpectedDiskVersion.exact(confirmedVersion),
                SaveIntent.OVERWRITE_CONFIRMED);

        SaveResult.Conflict conflict = assertInstanceOf(SaveResult.Conflict.class, result);
        assertTrue(conflict.actual().isPresent());
        assertFalse(confirmedVersion.sameContent(conflict.actual().orElseThrow()));
        assertEquals("external version two", Files.readString(target));
    }

    @Test void savingThroughSymbolicLinkPreservesTheLinkAndReplacesItsReferent()
            throws Exception {
        EditorPersistence persistence = new EditorPersistence(new TerrainJsonCodec());
        Path referent = directory.resolve("referent.json");
        persistence.save(EditorState.unsaved(DocumentFactories.blankStructure(
                "before.symlink.save", GridMode.ADVANCED)), referent);
        Path link = directory.resolve("linked.json");
        try {
            Files.createSymbolicLink(link, referent.getFileName());
        } catch (java.io.IOException | UnsupportedOperationException | SecurityException error) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "Symbolic links are unavailable: " + error);
            return;
        }
        EditorPersistence.LoadedDocument loaded = persistence.load(link);
        EditorState edited = loaded.state().withDocument(
                DocumentFactories.blankStructure("after.symlink.save", GridMode.BASIC));

        SaveResult.Saved saved = assertInstanceOf(SaveResult.Saved.class,
                persistence.save(edited, link,
                        ExpectedDiskVersion.exact(loaded.diskVersion()),
                        SaveIntent.SAVE_IF_UNCHANGED));

        assertTrue(Files.isSymbolicLink(link), "atomic save must not replace the symlink");
        assertTrue(Files.isSameFile(link, referent));
        assertEquals("after.symlink.save", persistence.load(referent).state().document().id());
        assertEquals(link.toAbsolutePath(), saved.state().sourcePath());
        assertEquals(link.toAbsolutePath(), saved.diskVersion().path());
    }

    @Test void atomicSaveThroughAHardLinkRebindsOnlyTheRequestedDirectoryEntry()
            throws Exception {
        EditorPersistence persistence = new EditorPersistence(new TerrainJsonCodec());
        Path originalName = directory.resolve("hardlink-original.json");
        persistence.save(EditorState.unsaved(DocumentFactories.blankStructure(
                "before.hardlink.save", GridMode.ADVANCED)), originalName);
        Path savedName = directory.resolve("hardlink-saved.json");
        Files.createLink(savedName, originalName);
        EditorPersistence.LoadedDocument loaded = persistence.load(savedName);
        EditorState edited = loaded.state().withDocument(
                DocumentFactories.blankStructure("after.hardlink.save", GridMode.BASIC));

        assertInstanceOf(SaveResult.Saved.class, persistence.save(edited, savedName,
                ExpectedDiskVersion.exact(loaded.diskVersion()),
                SaveIntent.SAVE_IF_UNCHANGED));

        assertFalse(Files.isSameFile(savedName, originalName),
                "atomic replacement intentionally gives the saved name a new inode");
        assertEquals("after.hardlink.save",
                persistence.load(savedName).state().document().id());
        assertEquals("before.hardlink.save",
                persistence.load(originalName).state().document().id());
    }

    @Test void exactSaveRejectsChangingTheTargetToAnAliasPath() throws Exception {
        EditorPersistence persistence = new EditorPersistence(new TerrainJsonCodec());
        Path source = directory.resolve("alias-source.json");
        persistence.save(EditorState.unsaved(DocumentFactories.blankStructure(
                "alias.source", GridMode.ADVANCED)), source);
        EditorPersistence.LoadedDocument loaded = persistence.load(source);
        Path hardLinkAlias = directory.resolve("alias-target.json");
        Files.createLink(hardLinkAlias, source);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> persistence.save(loaded.state(), hardLinkAlias,
                        ExpectedDiskVersion.exact(loaded.diskVersion()),
                        SaveIntent.SAVE_IF_UNCHANGED));

        assertTrue(failure.getMessage().contains("alias"));
        assertTrue(Files.isSameFile(source, hardLinkAlias));
    }
}
