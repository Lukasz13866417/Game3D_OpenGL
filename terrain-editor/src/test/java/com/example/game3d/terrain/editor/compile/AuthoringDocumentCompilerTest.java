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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
