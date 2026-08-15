package com.example.game3d.terrain.io.resolve;

import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.validation.TerrainContentLimits;
import org.junit.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TerrainReferenceResolverTest {
    @Test public void resolvesFreshSnapshotInDeclaredOrder() throws Exception {
        StructureDocument a = structure("a");
        StructureDocument b = structure("b");
        LevelDocument level = new LevelDocument(1, "level", "gameplay-default", Arrays.asList(
                LevelEntry.reference(uuid(1), "b"), LevelEntry.reference(uuid(2), "a")));
        InMemoryTerrainDocumentRepository repository = new InMemoryTerrainDocumentRepository(
                Arrays.asList(a, b), Collections.singletonList(level));

        ResolvedLevel result = new TerrainReferenceResolver().resolve(level, repository);

        assertEquals("b", result.structures().get(0).id());
        assertEquals("a", result.structures().get(1).id());
    }

    @Test public void rejectsCycles() throws Exception {
        LevelDocument a = new LevelDocument(1, "a", "profile",
                Collections.singletonList(LevelEntry.levelReference(uuid(1), "b")));
        LevelDocument b = new LevelDocument(1, "b", "profile",
                Collections.singletonList(LevelEntry.levelReference(uuid(2), "a")));
        InMemoryTerrainDocumentRepository repository = new InMemoryTerrainDocumentRepository(
                Collections.<StructureDocument>emptyList(), Arrays.asList(a, b));
        try {
            new TerrainReferenceResolver().resolve(a, repository);
            org.junit.Assert.fail("Expected cycle rejection");
        } catch (ResolutionException expected) {
            assertTrue(expected.getMessage().contains("a -> b -> a"));
        }
    }

    @Test public void repeatedStructureReferencesRetainDistinctOccurrencePaths() throws Exception {
        StructureDocument repeated = structure("repeated");
        LevelDocument level = new LevelDocument(1, "level", "profile", Arrays.asList(
                LevelEntry.reference(uuid(1), repeated.id()),
                LevelEntry.reference(uuid(2), repeated.id())));
        ResolvedLevel resolved = new TerrainReferenceResolver().resolve(level,
                new InMemoryTerrainDocumentRepository(
                        Collections.singletonList(repeated),
                        Collections.singletonList(level)));

        assertEquals(2, resolved.occurrences().size());
        assertEquals(uuid(1), resolved.occurrences().get(0).occurrenceKey());
        assertEquals(uuid(2), resolved.occurrences().get(1).occurrenceKey());
        assertNotEquals(
                resolved.occurrences().get(0).namespacedSourceId(uuid(10)),
                resolved.occurrences().get(1).namespacedSourceId(uuid(10)));
    }

    @Test(expected = ResolutionException.class)
    public void rejectsMissingReferences() throws Exception {
        LevelDocument level = new LevelDocument(1, "level", "profile",
                Collections.singletonList(LevelEntry.reference(uuid(1), "missing")));
        new TerrainReferenceResolver().resolve(level, new InMemoryTerrainDocumentRepository(
                Collections.<StructureDocument>emptyList(), Collections.singletonList(level)));
    }

    @Test public void rejectsReferenceExpansionBeyondSynchronousCaptureLimit() throws Exception {
        StructureDocument repeated = structure("repeated");
        java.util.List<LevelEntry> entries = new ArrayList<LevelEntry>();
        for (int i = 0; i <= TerrainContentLimits.MAX_RESOLVED_STRUCTURES; i++) {
            entries.add(LevelEntry.reference(uuid(i + 1), "repeated"));
        }
        LevelDocument level = new LevelDocument(1, "too-large", "profile", entries);
        try {
            new TerrainReferenceResolver().resolve(level,
                    new InMemoryTerrainDocumentRepository(
                            Collections.singletonList(repeated),
                            Collections.singletonList(level)));
            org.junit.Assert.fail("Expected resolved-content limit rejection");
        } catch (ResolutionException expected) {
            assertTrue(expected.getMessage().contains("limits"));
        }
    }

    @Test public void rejectsEmptyLevelFanOutBeyondExpansionLimit() throws Exception {
        LevelDocument empty = new LevelDocument(1, "empty", TrackProfile.GAMEPLAY_PROFILE_ID,
                Collections.<LevelEntry>emptyList());
        List<LevelEntry> references = new ArrayList<LevelEntry>();
        for (int i = 0; i < TerrainContentLimits.MAX_RESOLVED_LEVELS; i++) {
            references.add(LevelEntry.levelReference(
                    String.format("40000000-0000-0000-0000-%012d", i), empty.id()));
        }
        LevelDocument root = new LevelDocument(1, "root", TrackProfile.GAMEPLAY_PROFILE_ID,
                references);
        InMemoryTerrainDocumentRepository repository =
                new InMemoryTerrainDocumentRepository(
                        Collections.<StructureDocument>emptyList(),
                        Collections.singletonList(empty));

        try {
            new TerrainReferenceResolver().resolve(root, repository);
            fail("Expected expanded level count rejection");
        } catch (ResolutionException expected) {
            assertTrue(expected.getMessage().contains("limits"));
        }
    }

    private static StructureDocument structure(String id) {
        return new StructureDocument(1, id, GridMode.ADVANCED,
                Collections.emptyList(), Collections.emptyList());
    }

    private static String uuid(int value) {
        return String.format("00000000-0000-0000-0000-%012d", value);
    }
}
