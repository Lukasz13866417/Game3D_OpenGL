package com.example.game3d.terrain.io.catalog;

import com.example.game3d.authoring.GameplayLevelCatalog;
import com.example.game3d.authoring.GameplayLevelProvider;
import com.example.game3d.terrain.io.model.CatalogDocument;
import com.example.game3d.terrain.io.model.CatalogEntry;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.resolve.TerrainDocumentRepository;
import com.example.game3d.terrain.io.validation.ValidationProblem;
import com.example.game3d.terrain.io.validation.ValidationResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Shared source-catalog rules required by the production runtime loader. */
public final class GameplayCatalogPolicy {
    private static final List<String> REQUIRED_BUILTIN_IDS = builtInIds();

    private GameplayCatalogPolicy() {
    }

    public static List<String> requiredBuiltinIds() {
        return REQUIRED_BUILTIN_IDS;
    }

    public static int customEntryStartIndex() {
        return REQUIRED_BUILTIN_IDS.size();
    }

    public static boolean isRequiredBuiltinId(String id) {
        return REQUIRED_BUILTIN_IDS.contains(id);
    }

    /**
     * Validates the locked built-in prefix and every custom entry's runtime-facing reference.
     * Missing custom content is always an error: disabled rows remain explicit catalog state and
     * must not silently rot into an unpublishable location.
     */
    public static ValidationResult validate(
            CatalogDocument catalog,
            TerrainDocumentRepository repository) {
        if (catalog == null || repository == null) {
            throw new IllegalArgumentException("catalog and repository are required");
        }
        List<ValidationProblem> problems = new ArrayList<ValidationProblem>();
        List<CatalogEntry> entries = catalog.entries();
        Set<String> ids = new HashSet<String>();
        for (int index = 0; index < entries.size(); index++) {
            CatalogEntry entry = entries.get(index);
            if (!ids.add(entry.id())) {
                error(path(index, "id"), "duplicate gameplay catalog ID", problems);
            }
        }

        for (int index = 0; index < REQUIRED_BUILTIN_IDS.size(); index++) {
            String expected = REQUIRED_BUILTIN_IDS.get(index);
            if (index >= entries.size()) {
                error("$.entries[" + index + "]",
                        "required built-in provider '" + expected + "' is missing", problems);
                continue;
            }
            CatalogEntry actual = entries.get(index);
            if (actual.kind() != CatalogEntry.Kind.JAVA_PROVIDER
                    || !expected.equals(actual.id())
                    || !expected.equals(actual.location())
                    || !actual.enabled()) {
                error("$.entries[" + index + "]",
                        "must be enabled built-in Java provider '" + expected
                                + "' with matching ID and location", problems);
            }
        }

        for (int index = REQUIRED_BUILTIN_IDS.size(); index < entries.size(); index++) {
            CatalogEntry entry = entries.get(index);
            if (isRequiredBuiltinId(entry.id())) {
                error(path(index, "id"),
                        "required built-in provider IDs are reserved for the locked prefix: "
                                + entry.id(), problems);
            }
            if (entry.kind() != CatalogEntry.Kind.JSON_LEVEL) {
                error(path(index, "kind"),
                        "entries after the locked built-in prefix must use JSON_LEVEL; "
                                + "additional Java providers are not supported", problems);
                continue;
            }
            if (isRequiredBuiltinId(entry.location())) {
                error(path(index, "location"),
                        "a JSON level cannot use a reserved built-in provider ID as its location: "
                                + entry.location(), problems);
                continue;
            }
            LevelDocument level = repository.findLevel(entry.location());
            if (level == null) {
                problems.add(new ValidationProblem(ValidationProblem.Severity.ERROR,
                        path(index, "location"),
                        "does not resolve to a saved level document: " + entry.location()));
            } else if (isRequiredBuiltinId(level.id())) {
                error(path(index, "location"),
                        "resolves to a level whose ID is reserved for a built-in provider: "
                                + level.id(), problems);
            }
        }
        return new ValidationResult(problems);
    }

    private static String path(int index, String field) {
        return "$.entries[" + index + "]." + field;
    }

    private static void error(
            String path, String message, List<ValidationProblem> problems) {
        problems.add(new ValidationProblem(
                ValidationProblem.Severity.ERROR, path, message));
    }

    private static List<String> builtInIds() {
        List<String> ids = new ArrayList<String>();
        for (GameplayLevelProvider provider : GameplayLevelCatalog.builtIns().entries()) {
            ids.add(provider.stableId());
        }
        return Collections.unmodifiableList(ids);
    }
}
