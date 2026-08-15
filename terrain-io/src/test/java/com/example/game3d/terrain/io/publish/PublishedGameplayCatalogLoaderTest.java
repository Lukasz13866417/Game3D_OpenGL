package com.example.game3d.terrain.io.publish;

import com.example.game3d.authoring.BaseTerrainStructure;
import com.example.game3d.authoring.GameplayLevelCatalog;
import com.example.game3d.authoring.GameplayLevelProvider;
import com.example.game3d.authoring.TerrainMaterializer;
import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;
import com.example.game3d.terrain.io.store.ContentDigests;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.StringReader;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PublishedGameplayCatalogLoaderTest {
    @Test public void builtInsStayFirstAndJsonProvidersFollowArtifactOrder() throws Exception {
        JsonArray entries = builtinEntries();
        entries.add(envelope("extra-a", level("level-a", "30000000-0000-0000-0000-000000000001")));
        entries.add(envelope("extra-b", level("level-b", "30000000-0000-0000-0000-000000000002")));

        GameplayLevelCatalog catalog = new PublishedGameplayCatalogLoader().load(
                new StringReader(runtime(entries)));
        assertEquals(8, catalog.entries().size());
        assertEquals("stairs_curve_line", catalog.entries().get(0).stableId());
        assertEquals("rect_curve_sprint", catalog.entries().get(5).stableId());
        assertEquals("extra-a", catalog.entries().get(6).stableId());
        assertEquals("extra-b", catalog.entries().get(7).stableId());
    }

    @Test public void jsonProviderReturnsFreshOneShotStructureEveryTime() throws Exception {
        JsonArray entries = builtinEntries();
        entries.add(envelope("extra", level("level", "40000000-0000-0000-0000-000000000001")));
        GameplayLevelProvider provider = new PublishedGameplayCatalogLoader().load(
                new StringReader(runtime(entries))).entries().get(6);
        BaseTerrainStructure<?> first = provider.create(10);
        BaseTerrainStructure<?> second = provider.create(10);
        assertNotSame(first, second);
        TerrainMaterializer.materialize(first, TrackProfile.gameplayDefault(), Vec3.ZERO, 4L);
        TerrainMaterializer.materialize(second, TrackProfile.gameplayDefault(), Vec3.ZERO, 4L);
    }

    @Test public void duplicateUnknownOrCorruptContentFallsBackAtomically() {
        JsonArray entries = builtinEntries();
        entries.add(entries.get(5).deepCopy());
        GameplayLevelCatalog fallback = new PublishedGameplayCatalogLoader().loadOrBuiltIns(
                new StringReader(runtime(entries)));
        assertEquals(6, fallback.entries().size());

        try {
            new PublishedGameplayCatalogLoader().load(new StringReader(runtime(entries)));
            fail("Expected strict load failure");
        } catch (PublishedCatalogException expected) {
            assertTrue(expected.getMessage().contains("invalid"));
        }
    }

    @Test public void missingBuiltinProviderFallsBackAtomically() {
        JsonArray entries = builtinEntries();
        entries.remove(5);
        GameplayLevelCatalog fallback = new PublishedGameplayCatalogLoader().loadOrBuiltIns(
                new StringReader(runtime(entries)));
        assertEquals(6, fallback.entries().size());
    }

    @Test public void unknownProviderMarkerFallsBackAtomically() {
        JsonArray entries = builtinEntries();
        JsonObject marker = new JsonObject();
        marker.addProperty("contentType", "JAVA_PROVIDER");
        marker.addProperty("formatVersion", 1);
        marker.addProperty("providerId", "unknown-provider");
        entries.add(envelope("unknown-provider", marker));
        GameplayLevelCatalog fallback = new PublishedGameplayCatalogLoader().loadOrBuiltIns(
                new StringReader(runtime(entries)));
        assertEquals(6, fallback.entries().size());
    }

    @Test public void contentThatCannotMaterializeFallsBackAtomically() {
        JsonArray entries = builtinEntries();
        entries.add(envelope("invalid-surface",
                levelWithSurface("invalid-level",
                        "60000000-0000-0000-0000-000000000001",
                        "NOT_A_SURFACE")));

        GameplayLevelCatalog fallback = new PublishedGameplayCatalogLoader().loadOrBuiltIns(
                new StringReader(runtime(entries)));
        assertEquals(6, fallback.entries().size());

        try {
            new PublishedGameplayCatalogLoader().load(
                    new StringReader(runtime(entries)));
            fail("Expected strict load to reject unmaterializable terrain");
        } catch (PublishedCatalogException expected) {
            assertTrue(expected.getMessage().contains("invalid"));
        }
    }

    private static JsonArray builtinEntries() {
        JsonArray result = new JsonArray();
        for (GameplayLevelProvider provider : GameplayLevelCatalog.builtIns().entries()) {
            JsonObject marker = new JsonObject();
            marker.addProperty("contentType", "JAVA_PROVIDER");
            marker.addProperty("formatVersion", 1);
            marker.addProperty("providerId", provider.stableId());
            result.add(envelope(provider.stableId(), marker));
        }
        return result;
    }

    private static JsonObject level(String id, String tileId) {
        return levelWithSurface(id, tileId, "NORMAL");
    }

    private static JsonObject levelWithSurface(
            String id, String tileId, String surfaceKind) {
        StructureDocument structure = new StructureDocument(1, id + "-structure", GridMode.ADVANCED,
                Collections.singletonList(new TileRecord(tileId, true, 0, 0, 0,
                        surfaceKind, 1, 1)), Collections.emptyList());
        LevelDocument level = new LevelDocument(1, id, TrackProfile.GAMEPLAY_PROFILE_ID,
                Collections.singletonList(LevelEntry.inline(
                        "50000000-0000-0000-0000-000000000001", structure)));
        return JsonParser.parseString(new TerrainJsonCodec().encode(level)).getAsJsonObject();
    }

    private static JsonObject envelope(String id, JsonElement content) {
        JsonObject result = new JsonObject();
        result.addProperty("id", id);
        result.addProperty("digest", ContentDigests.sha256(content.toString()));
        result.add("content", content);
        return result;
    }

    private static String runtime(JsonArray entries) {
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", 1);
        root.addProperty("sourceCatalogId", "test");
        root.add("entries", entries);
        return root.toString();
    }
}
