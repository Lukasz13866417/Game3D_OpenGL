package com.example.game3d.terrain.io.publish;

import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.CatalogEntry;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;
import com.example.game3d.terrain.io.resolve.ResolvedLevel;
import com.example.game3d.terrain.io.resolve.InMemoryTerrainDocumentRepository;
import com.example.game3d.terrain.io.resolve.TerrainReferenceResolver;
import com.example.game3d.terrain.io.store.ContentDigests;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuthoringTerrainContentCompilerTest {
    @Test public void compilesKnownJavaProviderAsStableMarker() {
        CompiledTerrainContent result = new AuthoringTerrainContentCompiler().compileJavaProvider(
                new CatalogEntry("stairs_curve_line", CatalogEntry.Kind.JAVA_PROVIDER,
                        "stairs_curve_line", true));
        JsonObject marker = JsonParser.parseString(result.normalizedJson()).getAsJsonObject();
        assertEquals("JAVA_PROVIDER", marker.get("contentType").getAsString());
        assertEquals("stairs_curve_line", marker.get("providerId").getAsString());
        assertEquals(ContentDigests.sha256(marker.toString()), result.digest());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownJavaProvider() {
        new AuthoringTerrainContentCompiler().compileJavaProvider(
                new CatalogEntry("unknown", CatalogEntry.Kind.JAVA_PROVIDER, "unknown", true));
    }

    @Test public void resolvesLevelToSelfContainedInlineStructures() throws Exception {
        StructureDocument first = structure("first", "10000000-0000-0000-0000-000000000001");
        StructureDocument second = structure("second", "10000000-0000-0000-0000-000000000002");
        LevelDocument source = new LevelDocument(1, "level", TrackProfile.GAMEPLAY_PROFILE_ID,
                Arrays.asList(LevelEntry.reference("20000000-0000-0000-0000-000000000001", "first"),
                        LevelEntry.reference("20000000-0000-0000-0000-000000000002", "second")));
        CompiledTerrainContent result = new AuthoringTerrainContentCompiler().compileJsonLevel(
                new CatalogEntry("extra", CatalogEntry.Kind.JSON_LEVEL, "level", true),
                new ResolvedLevel(source, Arrays.asList(first, second)));

        LevelDocument decoded = new TerrainJsonCodec().decodeLevel(result.normalizedJson());
        assertEquals(2, decoded.entries().size());
        assertEquals(LevelEntry.Kind.INLINE_STRUCTURE, decoded.entries().get(0).kind());
        assertEquals("first", decoded.entries().get(0).inlineStructure().id());
        assertFalse(result.normalizedJson().contains("structureRef"));
    }

    @Test public void repeatedStructureReferencesReceiveDistinctRuntimeSourceIds()
            throws Exception {
        StructureDocument repeated = structure(
                "repeated", "10000000-0000-0000-0000-000000000001");
        LevelDocument source = new LevelDocument(1, "level",
                TrackProfile.GAMEPLAY_PROFILE_ID, Arrays.asList(
                LevelEntry.reference("20000000-0000-0000-0000-000000000001",
                        repeated.id()),
                LevelEntry.reference("20000000-0000-0000-0000-000000000002",
                        repeated.id())));
        ResolvedLevel resolved = new TerrainReferenceResolver().resolve(source,
                new InMemoryTerrainDocumentRepository(
                        Collections.singletonList(repeated),
                        Collections.singletonList(source)));

        CompiledTerrainContent result = new AuthoringTerrainContentCompiler().compileJsonLevel(
                new CatalogEntry("extra", CatalogEntry.Kind.JSON_LEVEL, source.id(), true),
                resolved);
        LevelDocument decoded = new TerrainJsonCodec().decodeLevel(result.normalizedJson());

        assertEquals(2, decoded.entries().size());
        String firstTile = decoded.entries().get(0).inlineStructure()
                .tiles().get(0).sourceId();
        String secondTile = decoded.entries().get(1).inlineStructure()
                .tiles().get(0).sourceId();
        assertFalse(firstTile.equals(secondTile));
    }

    private static StructureDocument structure(String id, String sourceId) {
        return new StructureDocument(1, id, GridMode.ADVANCED,
                Collections.singletonList(new TileRecord(sourceId, true, 0, 0, 0,
                        "NORMAL", 1, 1)), Collections.emptyList());
    }
}
