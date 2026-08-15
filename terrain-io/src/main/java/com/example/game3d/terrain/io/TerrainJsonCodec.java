package com.example.game3d.terrain.io;

import com.example.game3d.terrain.io.model.AddonKind;
import com.example.game3d.terrain.io.model.AddonReservation;
import com.example.game3d.terrain.io.model.CatalogDocument;
import com.example.game3d.terrain.io.model.CatalogEntry;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.Placement;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TerrainSourceDocument;
import com.example.game3d.terrain.io.model.TileRecord;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/** Strict, deterministic JSON codec. Semantic validation intentionally lives elsewhere. */
public final class TerrainJsonCodec {
    private final Gson gson = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    public String encode(TerrainSourceDocument document) {
        return gson.toJson(writeDocument(document)) + "\n";
    }

    public void encode(TerrainSourceDocument document, Writer writer) throws IOException {
        writer.write(encode(document));
    }

    public TerrainSourceDocument decode(String json) throws CodecException {
        final JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException error) {
            throw new CodecException("Invalid JSON", error);
        }
        JsonObject object = object(root, "$");
        String type = string(object, "documentType", "$", true);
        if ("structure".equals(type)) return readStructure(object, "$", true);
        if ("level".equals(type)) return readLevel(object, "$", true);
        if ("catalog".equals(type)) return readCatalog(object, "$", true);
        throw new CodecException("$.documentType: unsupported value " + type);
    }

    /** Reader overload used by Android AssetManager and desktop file loading. */
    public TerrainSourceDocument decode(Reader reader) throws CodecException, IOException {
        final JsonElement root;
        try {
            root = JsonParser.parseReader(reader);
        } catch (RuntimeException error) {
            throw new CodecException("Invalid JSON", error);
        }
        JsonObject object = object(root, "$");
        String type = string(object, "documentType", "$", true);
        if ("structure".equals(type)) return readStructure(object, "$", true);
        if ("level".equals(type)) return readLevel(object, "$", true);
        if ("catalog".equals(type)) return readCatalog(object, "$", true);
        throw new CodecException("$.documentType: unsupported value " + type);
    }

    public StructureDocument decodeStructure(String json) throws CodecException {
        TerrainSourceDocument value = decode(json);
        if (!(value instanceof StructureDocument)) throw new CodecException("Expected a structure document");
        return (StructureDocument) value;
    }

    public LevelDocument decodeLevel(String json) throws CodecException {
        TerrainSourceDocument value = decode(json);
        if (!(value instanceof LevelDocument)) throw new CodecException("Expected a level document");
        return (LevelDocument) value;
    }

    public CatalogDocument decodeCatalog(String json) throws CodecException {
        TerrainSourceDocument value = decode(json);
        if (!(value instanceof CatalogDocument)) throw new CodecException("Expected a catalog document");
        return (CatalogDocument) value;
    }

    private JsonObject writeDocument(TerrainSourceDocument document) {
        if (document instanceof StructureDocument) return writeStructure((StructureDocument) document, true);
        if (document instanceof LevelDocument) return writeLevel((LevelDocument) document, true);
        if (document instanceof CatalogDocument) return writeCatalog((CatalogDocument) document, true);
        throw new IllegalArgumentException("Unknown document class: " + document.getClass());
    }

    private JsonObject writeStructure(StructureDocument document, boolean includeType) {
        JsonObject out = new JsonObject();
        if (includeType) out.addProperty("documentType", "structure");
        out.addProperty("formatVersion", document.formatVersion());
        out.addProperty("id", document.id());
        out.addProperty("gridMode", document.gridMode().name());
        JsonArray tiles = new JsonArray();
        for (TileRecord tile : document.tiles()) {
            JsonObject item = new JsonObject();
            item.addProperty("sourceId", tile.sourceId());
            item.addProperty("solid", tile.solid());
            item.addProperty("turnDeltaDegrees", tile.turnDeltaDegrees());
            item.addProperty("absoluteSlopeDegrees", tile.absoluteSlopeDegrees());
            item.addProperty("liftBefore", tile.liftBefore());
            item.addProperty("surfaceKind", tile.surfaceKind());
            item.addProperty("alpha", tile.alpha());
            item.addProperty("brightness", tile.brightness());
            if (tile.resolvedTurnDeltaRadians() != null) {
                item.addProperty("resolvedTurnDeltaRadians",
                        tile.resolvedTurnDeltaRadians());
            }
            if (tile.resolvedAbsoluteSlopeRadians() != null) {
                item.addProperty("resolvedAbsoluteSlopeRadians",
                        tile.resolvedAbsoluteSlopeRadians());
            }
            tiles.add(item);
        }
        out.add("tiles", tiles);
        JsonArray addons = new JsonArray();
        for (AddonReservation addon : document.addons()) {
            JsonObject item = new JsonObject();
            item.addProperty("sourceId", addon.sourceId());
            item.addProperty("kind", addon.kind().name());
            if (addon.pairSourceId() != null) item.addProperty("pairSourceId", addon.pairSourceId());
            item.add("placement", writePlacement(addon.placement()));
            JsonObject parameters = new JsonObject();
            for (Map.Entry<String, Double> entry : new TreeMap<>(addon.parameters()).entrySet()) {
                parameters.addProperty(entry.getKey(), entry.getValue());
            }
            item.add("parameters", parameters);
            addons.add(item);
        }
        out.add("addons", addons);
        return out;
    }

    private JsonObject writePlacement(Placement placement) {
        JsonObject out = new JsonObject();
        out.addProperty("mode", placement.mode().name());
        if (placement.mode() == Placement.Mode.GRID) {
            out.addProperty("rowStart", placement.rowStart());
            out.addProperty("rowEnd", placement.rowEnd());
            out.addProperty("columnStart", placement.columnStart());
            out.addProperty("columnEnd", placement.columnEnd());
        } else {
            out.addProperty("segmentSourceId", placement.segmentSourceId());
            out.addProperty("across", placement.across());
            out.addProperty("along", placement.along());
        }
        return out;
    }

    private JsonObject writeLevel(LevelDocument document, boolean includeType) {
        JsonObject out = new JsonObject();
        if (includeType) out.addProperty("documentType", "level");
        out.addProperty("formatVersion", document.formatVersion());
        out.addProperty("id", document.id());
        out.addProperty("sessionProfileId", document.sessionProfileId());
        JsonArray entries = new JsonArray();
        for (LevelEntry entry : document.entries()) {
            JsonObject item = new JsonObject();
            item.addProperty("sourceId", entry.sourceId());
            item.addProperty("kind", entry.kind().name());
            if (entry.kind() == LevelEntry.Kind.STRUCTURE_REFERENCE) {
                item.addProperty("structureRef", entry.structureRef());
            } else if (entry.kind() == LevelEntry.Kind.LEVEL_REFERENCE) {
                item.addProperty("levelRef", entry.levelRef());
            } else {
                item.add("inlineStructure", writeStructure(entry.inlineStructure(), false));
            }
            entries.add(item);
        }
        out.add("entries", entries);
        return out;
    }

    private JsonObject writeCatalog(CatalogDocument document, boolean includeType) {
        JsonObject out = new JsonObject();
        if (includeType) out.addProperty("documentType", "catalog");
        out.addProperty("formatVersion", document.formatVersion());
        out.addProperty("id", document.id());
        JsonArray entries = new JsonArray();
        for (CatalogEntry entry : document.entries()) {
            JsonObject item = new JsonObject();
            item.addProperty("id", entry.id());
            item.addProperty("kind", entry.kind().name());
            item.addProperty("location", entry.location());
            item.addProperty("enabled", entry.enabled());
            entries.add(item);
        }
        out.add("entries", entries);
        return out;
    }

    private StructureDocument readStructure(JsonObject value, String path, boolean hasType)
            throws CodecException {
        Set<String> fields = set("formatVersion", "id", "gridMode", "tiles", "addons");
        if (hasType) fields.add("documentType");
        exactFields(value, fields, path);
        int version = integer(value, "formatVersion", path);
        String id = string(value, "id", path, true);
        GridMode grid = enumValue(GridMode.class, string(value, "gridMode", path, true), path + ".gridMode");
        JsonArray tileValues = array(value, "tiles", path);
        List<TileRecord> tiles = new ArrayList<>();
        for (int i = 0; i < tileValues.size(); i++) {
            String itemPath = path + ".tiles[" + i + "]";
            JsonObject item = object(tileValues.get(i), itemPath);
            exactFields(item, set("sourceId", "solid", "turnDeltaDegrees", "absoluteSlopeDegrees",
                    "liftBefore", "surfaceKind", "alpha", "brightness",
                    "resolvedTurnDeltaRadians", "resolvedAbsoluteSlopeRadians"), itemPath);
            tiles.add(new TileRecord(
                    string(item, "sourceId", itemPath, true), bool(item, "solid", itemPath),
                    number(item, "turnDeltaDegrees", itemPath),
                    number(item, "absoluteSlopeDegrees", itemPath),
                    number(item, "liftBefore", itemPath), string(item, "surfaceKind", itemPath, true),
                    number(item, "alpha", itemPath), number(item, "brightness", itemPath),
                    optionalNumber(item, "resolvedTurnDeltaRadians", itemPath),
                    optionalNumber(item, "resolvedAbsoluteSlopeRadians", itemPath)));
        }
        JsonArray addonValues = array(value, "addons", path);
        List<AddonReservation> addons = new ArrayList<>();
        for (int i = 0; i < addonValues.size(); i++) {
            String itemPath = path + ".addons[" + i + "]";
            JsonObject item = object(addonValues.get(i), itemPath);
            exactFields(item, set("sourceId", "kind", "pairSourceId", "placement", "parameters"), itemPath);
            String pair = optionalString(item, "pairSourceId", itemPath);
            JsonObject params = requiredObject(item, "parameters", itemPath);
            Map<String, Double> parameters = new LinkedHashMap<>();
            List<String> names = new ArrayList<>(params.keySet());
            Collections.sort(names);
            for (String name : names) parameters.put(name, primitiveNumber(params.get(name), itemPath + ".parameters." + name));
            addons.add(new AddonReservation(string(item, "sourceId", itemPath, true),
                    enumValue(AddonKind.class, string(item, "kind", itemPath, true), itemPath + ".kind"),
                    readPlacement(requiredObject(item, "placement", itemPath), itemPath + ".placement"),
                    pair, parameters));
        }
        return new StructureDocument(version, id, grid, tiles, addons);
    }

    private Placement readPlacement(JsonObject value, String path) throws CodecException {
        Placement.Mode mode = enumValue(Placement.Mode.class, string(value, "mode", path, true), path + ".mode");
        if (mode == Placement.Mode.GRID) {
            exactFields(value, set("mode", "rowStart", "rowEnd", "columnStart", "columnEnd"), path);
            return Placement.grid(integer(value, "rowStart", path), integer(value, "rowEnd", path),
                    integer(value, "columnStart", path), integer(value, "columnEnd", path));
        }
        exactFields(value, set("mode", "segmentSourceId", "across", "along"), path);
        return Placement.normalized(string(value, "segmentSourceId", path, true),
                number(value, "across", path), number(value, "along", path));
    }

    private LevelDocument readLevel(JsonObject value, String path, boolean hasType) throws CodecException {
        Set<String> fields = set("formatVersion", "id", "sessionProfileId", "entries");
        if (hasType) fields.add("documentType");
        exactFields(value, fields, path);
        JsonArray values = array(value, "entries", path);
        List<LevelEntry> entries = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String itemPath = path + ".entries[" + i + "]";
            JsonObject item = object(values.get(i), itemPath);
            LevelEntry.Kind kind = enumValue(LevelEntry.Kind.class,
                    string(item, "kind", itemPath, true), itemPath + ".kind");
            if (kind == LevelEntry.Kind.STRUCTURE_REFERENCE) {
                exactFields(item, set("sourceId", "kind", "structureRef"), itemPath);
                entries.add(LevelEntry.reference(string(item, "sourceId", itemPath, true),
                        string(item, "structureRef", itemPath, true)));
            } else if (kind == LevelEntry.Kind.LEVEL_REFERENCE) {
                exactFields(item, set("sourceId", "kind", "levelRef"), itemPath);
                entries.add(LevelEntry.levelReference(string(item, "sourceId", itemPath, true),
                        string(item, "levelRef", itemPath, true)));
            } else {
                exactFields(item, set("sourceId", "kind", "inlineStructure"), itemPath);
                entries.add(LevelEntry.inline(string(item, "sourceId", itemPath, true),
                        readStructure(requiredObject(item, "inlineStructure", itemPath),
                                itemPath + ".inlineStructure", false)));
            }
        }
        return new LevelDocument(integer(value, "formatVersion", path), string(value, "id", path, true),
                string(value, "sessionProfileId", path, true), entries);
    }

    private CatalogDocument readCatalog(JsonObject value, String path, boolean hasType) throws CodecException {
        Set<String> fields = set("formatVersion", "id", "entries");
        if (hasType) fields.add("documentType");
        exactFields(value, fields, path);
        JsonArray values = array(value, "entries", path);
        List<CatalogEntry> entries = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String itemPath = path + ".entries[" + i + "]";
            JsonObject item = object(values.get(i), itemPath);
            exactFields(item, set("id", "kind", "location", "enabled"), itemPath);
            entries.add(new CatalogEntry(string(item, "id", itemPath, true),
                    enumValue(CatalogEntry.Kind.class, string(item, "kind", itemPath, true), itemPath + ".kind"),
                    string(item, "location", itemPath, true), bool(item, "enabled", itemPath)));
        }
        return new CatalogDocument(integer(value, "formatVersion", path), string(value, "id", path, true), entries);
    }

    private static JsonObject object(JsonElement value, String path) throws CodecException {
        if (value == null || !value.isJsonObject()) throw new CodecException(path + ": expected object");
        return value.getAsJsonObject();
    }

    private static JsonObject requiredObject(JsonObject value, String key, String path) throws CodecException {
        return object(required(value, key, path), path + "." + key);
    }

    private static JsonArray array(JsonObject value, String key, String path) throws CodecException {
        JsonElement element = required(value, key, path);
        if (!element.isJsonArray()) throw new CodecException(path + "." + key + ": expected array");
        return element.getAsJsonArray();
    }

    private static String string(JsonObject value, String key, String path, boolean nonEmpty) throws CodecException {
        JsonElement element = required(value, key, path);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
            throw new CodecException(path + "." + key + ": expected string");
        String text = element.getAsString();
        if (nonEmpty && text.trim().isEmpty()) throw new CodecException(path + "." + key + ": must not be blank");
        return text;
    }

    private static String optionalString(JsonObject value, String key, String path) throws CodecException {
        if (!value.has(key)) return null;
        return string(value, key, path, true);
    }

    private static boolean bool(JsonObject value, String key, String path) throws CodecException {
        JsonElement element = required(value, key, path);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean())
            throw new CodecException(path + "." + key + ": expected boolean");
        return element.getAsBoolean();
    }

    private static int integer(JsonObject value, String key, String path) throws CodecException {
        double number = number(value, key, path);
        if (number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)
            throw new CodecException(path + "." + key + ": expected 32-bit integer");
        return (int) number;
    }

    private static double number(JsonObject value, String key, String path) throws CodecException {
        return primitiveNumber(required(value, key, path), path + "." + key);
    }

    private static Double optionalNumber(JsonObject value, String key, String path)
            throws CodecException {
        return value.has(key) ? primitiveNumber(value.get(key), path + "." + key) : null;
    }

    private static double primitiveNumber(JsonElement element, String path) throws CodecException {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
            throw new CodecException(path + ": expected number");
        try { return element.getAsDouble(); }
        catch (RuntimeException error) { throw new CodecException(path + ": invalid number", error); }
    }

    private static JsonElement required(JsonObject value, String key, String path) throws CodecException {
        if (!value.has(key) || value.get(key).isJsonNull()) throw new CodecException(path + "." + key + ": required");
        return value.get(key);
    }

    private static void exactFields(JsonObject value, Set<String> allowed, String path) throws CodecException {
        for (String key : value.keySet()) if (!allowed.contains(key))
            throw new CodecException(path + ": unknown field '" + key + "'");
        for (String key : allowed) if (!value.has(key)
                && !"pairSourceId".equals(key)
                && !"resolvedTurnDeltaRadians".equals(key)
                && !"resolvedAbsoluteSlopeRadians".equals(key))
            throw new CodecException(path + "." + key + ": required");
    }

    private static Set<String> set(String... names) {
        return new LinkedHashSet<>(Arrays.asList(names));
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String path)
            throws CodecException {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException error) { throw new CodecException(path + ": unsupported value " + value); }
    }
}
