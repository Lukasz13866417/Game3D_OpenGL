package com.example.game3d.terrain.io.publish;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.example.game3d.terrain.io.store.ContentDigests;
import com.example.game3d.terrain.io.validation.TerrainContentLimits;

/** All-or-nothing runtime decoding with an explicit built-in fallback. */
public final class RuntimeCatalogLoader {
    /** Strict all-or-nothing decode used when callers need diagnostics. */
    public RuntimeCatalog load(Reader published) throws RuntimeCatalogException {
        if (published == null) throw new RuntimeCatalogException("Published catalog reader is null");
        try {
            JsonElement parsed = JsonParser.parseReader(published);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("root");
            JsonObject root = parsed.getAsJsonObject();
            requireExactFields(root, "formatVersion", "sourceCatalogId", "entries");
            if (root.get("formatVersion").getAsInt() != 1) throw new IllegalArgumentException("version");
            String sourceId = nonEmpty(root.get("sourceCatalogId").getAsString(), "sourceCatalogId");
            JsonArray array = root.getAsJsonArray("entries");
            if (array == null || array.size() == 0
                    || array.size() > TerrainContentLimits.MAX_CATALOG_ENTRIES) {
                throw new IllegalArgumentException("entries");
            }
            Set<String> ids = new HashSet<String>();
            List<RuntimeCatalogEntry> loaded = new ArrayList<RuntimeCatalogEntry>();
            for (JsonElement value : array) {
                JsonObject item = value.getAsJsonObject();
                requireExactFields(item, "id", "digest", "content");
                String id = nonEmpty(item.get("id").getAsString(), "entry id");
                if (!ids.add(id)) throw new IllegalArgumentException("duplicate entry id");
                String digest = nonEmpty(item.get("digest").getAsString(), "digest");
                JsonElement content = item.get("content");
                if (content == null || content.isJsonNull()) throw new IllegalArgumentException("content");
                String expected = ContentDigests.sha256(content.toString());
                if (!expected.equals(digest)) throw new IllegalArgumentException("digest");
                loaded.add(new RuntimeCatalogEntry(id, digest, content));
            }
            return new RuntimeCatalog(1, sourceId, loaded);
        } catch (RuntimeException invalid) {
            throw new RuntimeCatalogException("Invalid published terrain catalog", invalid);
        }
    }

    public RuntimeCatalog loadOrFallback(Reader published, RuntimeCatalog builtins) {
        try {
            return load(published);
        } catch (RuntimeCatalogException invalid) {
            return builtins;
        }
    }

    public List<JsonObject> loadOrFallback(String publishedJson, List<JsonObject> builtins) {
        try {
            RuntimeCatalog catalog = load(new StringReader(publishedJson));
            List<JsonObject> loaded = new ArrayList<JsonObject>();
            for (RuntimeCatalogEntry entry : catalog.entries()) {
                JsonObject item = new JsonObject();
                item.addProperty("id", entry.id());
                item.addProperty("digest", entry.digest());
                item.add("content", entry.compiledDefinition());
                loaded.add(item);
            }
            return Collections.unmodifiableList(loaded);
        } catch (RuntimeCatalogException invalid) {
            List<JsonObject> copy = new ArrayList<JsonObject>();
            for (JsonObject builtin : builtins) copy.add(builtin.deepCopy());
            return Collections.unmodifiableList(copy);
        }
    }

    private static String nonEmpty(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name);
        return value;
    }

    private static void requireExactFields(JsonObject object, String... names) {
        Set<String> expected = new HashSet<String>();
        Collections.addAll(expected, names);
        if (!object.keySet().equals(expected)) throw new IllegalArgumentException("fields");
    }
}
