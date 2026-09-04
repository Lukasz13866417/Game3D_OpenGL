package com.example.game3d.terrain.io.catalog;

import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.terrain.io.model.CatalogDocument;
import com.example.game3d.terrain.io.model.CatalogEntry;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.resolve.InMemoryTerrainDocumentRepository;
import com.example.game3d.terrain.io.validation.ValidationProblem;
import com.example.game3d.terrain.io.validation.ValidationResult;
import org.junit.Test;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameplayCatalogPolicyTest {
    @Test public void acceptsLockedBuiltinsAndResolvableCustomLevel() {
        LevelDocument level = new LevelDocument(1, "custom-level",
                TrackProfile.GAMEPLAY_PROFILE_ID, Collections.emptyList());
        CatalogDocument catalog = CatalogDocumentEdits.addJsonLevel(
                CatalogDocumentEdits.newGameplayCatalog("main"),
                "custom-provider", level.id(), true);

        ValidationResult result = GameplayCatalogPolicy.validate(catalog,
                new InMemoryTerrainDocumentRepository(Collections.emptyList(),
                        Collections.singletonList(level)));

        assertTrue(result.problems().toString(), result.isValid());
    }

    @Test public void rejectsChangedBuiltinPrefixBeforePublishing() {
        CatalogDocument catalog = CatalogDocumentEdits.newGameplayCatalog("main");
        java.util.List<com.example.game3d.terrain.io.model.CatalogEntry> entries =
                new java.util.ArrayList<com.example.game3d.terrain.io.model.CatalogEntry>(
                        catalog.entries());
        java.util.Collections.swap(entries, 0, 1);

        ValidationResult result = GameplayCatalogPolicy.validate(
                new CatalogDocument(1, "main", entries),
                new InMemoryTerrainDocumentRepository(
                        Collections.emptyList(), Collections.emptyList()));

        assertFalse(result.isValid());
        assertTrue(result.problems().get(0).message().contains("built-in"));
    }

    @Test public void missingDisabledCustomLevelStillBlocksPublishing() {
        CatalogDocument catalog = CatalogDocumentEdits.addJsonLevel(
                CatalogDocumentEdits.newGameplayCatalog("main"),
                "parked", "missing", false);

        ValidationResult result = GameplayCatalogPolicy.validate(catalog,
                new InMemoryTerrainDocumentRepository(
                        Collections.emptyList(), Collections.emptyList()));

        assertFalse(result.isValid());
        assertEquals(ValidationProblem.Severity.ERROR,
                result.problems().get(0).severity());
    }

    @Test public void requiredBuiltinIdCannotReappearInCustomSuffix() {
        String reserved = GameplayCatalogPolicy.requiredBuiltinIds().get(0);
        LevelDocument level = new LevelDocument(1, "ordinary-level",
                TrackProfile.GAMEPLAY_PROFILE_ID, Collections.emptyList());
        List<CatalogEntry> entries =
                new ArrayList<CatalogEntry>(
                        CatalogDocumentEdits.newGameplayCatalog("main").entries());
        entries.add(new CatalogEntry(
                reserved, CatalogEntry.Kind.JSON_LEVEL,
                level.id(), true));

        ValidationResult result = GameplayCatalogPolicy.validate(
                new CatalogDocument(1, "main", entries),
                new InMemoryTerrainDocumentRepository(Collections.emptyList(),
                        Collections.singletonList(level)));

        assertFalse(result.isValid());
        assertTrue(result.problems().toString(), result.problems().stream()
                .anyMatch(problem -> problem.message().contains(
                        "reserved for the locked prefix")));
    }

    @Test public void customSuffixRejectsJavaProviderAndBuiltinLevelLocation() {
        String reserved = GameplayCatalogPolicy.requiredBuiltinIds().get(0);
        LevelDocument reservedLevel = new LevelDocument(1, reserved,
                TrackProfile.GAMEPLAY_PROFILE_ID, Collections.emptyList());
        List<CatalogEntry> entries =
                new ArrayList<CatalogEntry>(
                        CatalogDocumentEdits.newGameplayCatalog("main").entries());
        entries.add(new CatalogEntry(
                "rogue-java", CatalogEntry.Kind.JAVA_PROVIDER,
                "rogue-java", true));
        entries.add(new CatalogEntry(
                "reserved-location", CatalogEntry.Kind.JSON_LEVEL,
                reserved, true));

        ValidationResult result = GameplayCatalogPolicy.validate(
                new CatalogDocument(1, "main", entries),
                new InMemoryTerrainDocumentRepository(Collections.emptyList(),
                        Collections.singletonList(reservedLevel)));

        assertFalse(result.isValid());
        assertTrue(result.problems().toString(), result.problems().stream()
                .anyMatch(problem -> problem.message().contains(
                        "additional Java providers are not supported")));
        assertTrue(result.problems().toString(), result.problems().stream()
                .anyMatch(problem -> problem.message().contains(
                        "reserved built-in provider ID")));
    }
}
