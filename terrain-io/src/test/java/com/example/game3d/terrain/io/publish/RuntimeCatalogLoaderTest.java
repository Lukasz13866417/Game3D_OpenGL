package com.example.game3d.terrain.io.publish;

import com.example.game3d.terrain.io.store.ContentDigests;
import com.example.game3d.terrain.io.validation.TerrainContentLimits;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.StringReader;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RuntimeCatalogLoaderTest {
    @Test public void returnsTypedImmutableDefinitions() {
        String content = "{\"segments\":[]}";
        String json = "{\"formatVersion\":1,\"sourceCatalogId\":\"published\",\"entries\":[{"
                + "\"id\":\"level\",\"digest\":\"" + ContentDigests.sha256(content)
                + "\",\"content\":" + content + "}]}";
        RuntimeCatalog fallback = fallback();
        RuntimeCatalog result = new RuntimeCatalogLoader().loadOrFallback(new StringReader(json), fallback);
        assertEquals("published", result.sourceCatalogId());
        assertEquals("level", result.entries().get(0).id());
        assertEquals(0, result.entries().get(0).compiledDefinition().getAsJsonObject()
                .getAsJsonArray("segments").size());
    }

    @Test public void digestCorruptionFallsBackAtomically() {
        String json = "{\"formatVersion\":1,\"sourceCatalogId\":\"bad\",\"entries\":[{"
                + "\"id\":\"level\",\"digest\":\"wrong\",\"content\":{}}]}";
        RuntimeCatalog fallback = fallback();
        assertSame(fallback, new RuntimeCatalogLoader().loadOrFallback(new StringReader(json), fallback));
    }

    @Test public void legacyJsonApiAlsoVerifiesDigestBeforeReturningAnyEntry() {
        String json = "{\"formatVersion\":1,\"sourceCatalogId\":\"bad\",\"entries\":[{"
                + "\"id\":\"level\",\"digest\":\"wrong\",\"content\":{}}]}";
        JsonObject builtin = new JsonObject();
        builtin.addProperty("id", "builtin");
        List<JsonObject> fallback = Collections.singletonList(builtin);
        List<JsonObject> result = new RuntimeCatalogLoader().loadOrFallback(json, fallback);
        assertEquals(1, result.size());
        assertEquals("builtin", result.get(0).get("id").getAsString());
    }

    @Test public void strictLoaderRejectsDuplicateIds() {
        String content = "{}";
        String entry = "{\"id\":\"same\",\"digest\":\""
                + ContentDigests.sha256(content) + "\",\"content\":" + content + "}";
        String json = "{\"formatVersion\":1,\"sourceCatalogId\":\"bad\",\"entries\":["
                + entry + "," + entry + "]}";
        try {
            new RuntimeCatalogLoader().load(new StringReader(json));
            fail("Expected strict failure");
        } catch (RuntimeCatalogException expected) {
            assertTrue(expected.getMessage().contains("Invalid"));
        }
    }

    @Test public void strictLoaderRejectsCatalogBeyondRuntimeEntryLimit() {
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", 1);
        root.addProperty("sourceCatalogId", "oversized");
        JsonArray entries = new JsonArray();
        for (int i = 0; i <= TerrainContentLimits.MAX_CATALOG_ENTRIES; i++) {
            entries.add(new JsonObject());
        }
        root.add("entries", entries);
        try {
            new RuntimeCatalogLoader().load(new StringReader(root.toString()));
            fail("Expected runtime catalog limit rejection");
        } catch (RuntimeCatalogException expected) {
            assertTrue(expected.getMessage().contains("Invalid"));
        }
    }

    private static RuntimeCatalog fallback() {
        return new RuntimeCatalog(1, "builtins", Collections.singletonList(
                new RuntimeCatalogEntry("builtin", ContentDigests.sha256("{}"),
                        JsonParser.parseString("{}"))));
    }
}
