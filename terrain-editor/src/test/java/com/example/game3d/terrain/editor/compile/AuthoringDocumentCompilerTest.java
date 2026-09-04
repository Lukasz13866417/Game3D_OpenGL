package com.example.game3d.terrain.editor.compile;

import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;
import com.example.game3d.terrain.io.resolve.TerrainDocumentRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthoringDocumentCompilerTest {
    @Test void earlyTileEditPropagatesToDownstreamCanonicalGeometry() throws Exception {
        TileRecord first = tile("00000000-0000-0000-0000-000000000001", 0);
        TileRecord second = tile("00000000-0000-0000-0000-000000000002", 0);
        StructureDocument straight = new StructureDocument(1, "straight", GridMode.ADVANCED,
                Arrays.asList(first, second), java.util.Collections.emptyList());
        StructureDocument turned = straight.withTiles(Arrays.asList(
                first.withValues(20, 0, 0, 1, 1), second));
        AuthoringDocumentCompiler compiler = new AuthoringDocumentCompiler(emptyRepository());

        CompileResult a = compiler.compile(1, straight);
        CompileResult b = compiler.compile(2, turned);

        assertTrue(a.successful()); assertTrue(b.successful());
        assertNotEquals(a.snapshot().segments.get(1).farLeft,
                b.snapshot().segments.get(1).farLeft);
    }

    @Test void repeatedReferencesHaveCollisionFreeOccurrenceAwarePickingMaps()
            throws Exception {
        StructureDocument repeated = new StructureDocument(1, "repeated", GridMode.ADVANCED,
                Collections.singletonList(tile(
                        "10000000-0000-0000-0000-000000000001", 0)),
                Collections.emptyList());
        String firstEntry = "20000000-0000-0000-0000-000000000001";
        String secondEntry = "20000000-0000-0000-0000-000000000002";
        LevelDocument level = new LevelDocument(1, "level",
                TrackProfile.GAMEPLAY_PROFILE_ID, Arrays.asList(
                LevelEntry.reference(firstEntry, repeated.id()),
                LevelEntry.reference(secondEntry, repeated.id())));
        TerrainDocumentRepository repository = new TerrainDocumentRepository() {
            @Override public StructureDocument findStructure(String id) {
                return repeated.id().equals(id) ? repeated : null;
            }
            @Override public LevelDocument findLevel(String id) { return null; }
        };

        CompileResult result = new AuthoringDocumentCompiler(repository).compile(1, level);

        assertTrue(result.successful());
        assertEquals(2, result.snapshot().segments.size());
        assertEquals(2, result.sourceSegmentIds().size());
        assertTrue(result.sourceSegmentIds().keySet().stream()
                .anyMatch(key -> key.startsWith(firstEntry + "/")));
        assertTrue(result.sourceSegmentIds().keySet().stream()
                .anyMatch(key -> key.startsWith(secondEntry + "/")));
    }

    @Test void missingReferenceIsAnActionableInvalidDraftNotACompilerFailure()
            throws Exception {
        LevelDocument level = new LevelDocument(1, "missing-level",
                TrackProfile.GAMEPLAY_PROFILE_ID, List.of(LevelEntry.reference(
                "30000000-0000-0000-0000-000000000001", "missing-structure")));

        CompileResult result = new AuthoringDocumentCompiler(emptyRepository())
                .compile(3, level);

        assertFalse(result.successful());
        assertFalse(result.compilerFailed());
        assertTrue(result.problems().stream().anyMatch(problem ->
                problem.path().equals("$.entries[0].structureRef")
                        && problem.message().contains("missing-structure")));
    }

    @Test void cycleAndNestedProblemsRetainTheirRootOccurrencePath() throws Exception {
        LevelDocument root = new LevelDocument(1, "cycle-root",
                TrackProfile.GAMEPLAY_PROFILE_ID, List.of(LevelEntry.levelReference(
                "40000000-0000-0000-0000-000000000001", "cycle-child")));
        LevelDocument child = new LevelDocument(1, "cycle-child",
                TrackProfile.GAMEPLAY_PROFILE_ID, List.of(LevelEntry.levelReference(
                "40000000-0000-0000-0000-000000000002", "cycle-root")));
        TerrainDocumentRepository repository = new TerrainDocumentRepository() {
            @Override public StructureDocument findStructure(String id) { return null; }
            @Override public LevelDocument findLevel(String id) {
                if (root.id().equals(id)) return root;
                return child.id().equals(id) ? child : null;
            }
        };

        CompileResult result = new AuthoringDocumentCompiler(repository).compile(4, root);

        assertFalse(result.successful());
        assertFalse(result.compilerFailed());
        assertTrue(result.problems().stream().anyMatch(problem ->
                problem.path().startsWith("$.entries[0].resolvedLevel.entries[0]")
                        && problem.message().contains("cycle")));
    }

    @Test void unknownProfileAndReferencedValidationErrorsAreInvalidWithUsefulPaths()
            throws Exception {
        LevelDocument unknownProfile = new LevelDocument(1, "profile-level",
                "unknown-profile", List.of());
        CompileResult profileResult = new AuthoringDocumentCompiler(emptyRepository())
                .compile(5, unknownProfile);
        assertFalse(profileResult.compilerFailed());
        assertTrue(profileResult.problems().stream().anyMatch(problem ->
                problem.path().equals("$.sessionProfileId")
                        && problem.message().contains("Unknown track profile")));

        StructureDocument invalid = new StructureDocument(1, "invalid-child",
                GridMode.ADVANCED, List.of(new TileRecord(
                "50000000-0000-0000-0000-000000000001", true,
                0, 0, 0, "NORMAL", 2, 1)), List.of());
        LevelDocument parent = new LevelDocument(1, "validation-parent",
                TrackProfile.GAMEPLAY_PROFILE_ID, List.of(LevelEntry.reference(
                "50000000-0000-0000-0000-000000000002", invalid.id())));
        TerrainDocumentRepository repository = new TerrainDocumentRepository() {
            @Override public StructureDocument findStructure(String id) {
                return invalid.id().equals(id) ? invalid : null;
            }
            @Override public LevelDocument findLevel(String id) { return null; }
        };

        CompileResult invalidResult = new AuthoringDocumentCompiler(repository)
                .compile(6, parent);
        assertFalse(invalidResult.compilerFailed());
        assertTrue(invalidResult.problems().stream().anyMatch(problem ->
                problem.path().equals(
                        "$.entries[0].resolvedStructure.tiles[0].alpha")));
    }

    private static TileRecord tile(String id, double turn) {
        return new TileRecord(id, true, turn, 0, 0, "NORMAL", 1, 1);
    }

    private static TerrainDocumentRepository emptyRepository() {
        return new TerrainDocumentRepository() {
            @Override public StructureDocument findStructure(String id) { return null; }
            @Override public LevelDocument findLevel(String id) { return null; }
        };
    }
}
