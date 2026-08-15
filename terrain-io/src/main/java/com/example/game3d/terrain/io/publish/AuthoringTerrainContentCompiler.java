package com.example.game3d.terrain.io.publish;

import com.example.game3d.authoring.GameplayLevelCatalog;
import com.example.game3d.authoring.GameplayLevelProvider;
import com.example.game3d.authoring.BaseTerrainStructure;
import com.example.game3d.authoring.TerrainLevelSequence;
import com.example.game3d.authoring.TerrainMaterializer;
import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.authoring.DataBackedStructureFactory;
import com.example.game3d.terrain.io.model.CatalogEntry;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.resolve.ResolvedLevel;
import com.example.game3d.terrain.io.resolve.ResolvedStructureOccurrence;
import com.example.game3d.terrain.io.resolve.StructureOccurrenceNamespacer;
import com.example.game3d.terrain.io.store.ContentDigests;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Canonical publisher compiler. Java content is represented by a stable provider
 * marker; JSON levels are flattened to inline structures so runtime loading never
 * performs file or catalog reference resolution.
 */
public final class AuthoringTerrainContentCompiler implements TerrainContentCompiler {
    public static final String JAVA_PROVIDER_CONTENT_TYPE = "JAVA_PROVIDER";

    private final TerrainJsonCodec codec;
    private final Set<String> knownJavaProviders;

    public AuthoringTerrainContentCompiler() {
        this(new TerrainJsonCodec(), GameplayLevelCatalog.builtIns());
    }

    AuthoringTerrainContentCompiler(TerrainJsonCodec codec, GameplayLevelCatalog builtIns) {
        if (codec == null || builtIns == null) {
            throw new IllegalArgumentException("codec and builtIns are required");
        }
        this.codec = codec;
        this.knownJavaProviders = new HashSet<String>();
        for (GameplayLevelProvider provider : builtIns.entries()) {
            knownJavaProviders.add(provider.stableId());
        }
    }

    @Override
    public CompiledTerrainContent compileJavaProvider(CatalogEntry entry) {
        requireKind(entry, CatalogEntry.Kind.JAVA_PROVIDER);
        String providerId = entry.location();
        if (!knownJavaProviders.contains(providerId)) {
            throw new IllegalArgumentException("Unknown Java terrain provider '" + providerId + "'");
        }
        if (!providerId.equals(entry.id())) {
            throw new IllegalArgumentException("Java provider catalog ID must be '" + providerId + "'");
        }
        JsonObject marker = new JsonObject();
        marker.addProperty("contentType", JAVA_PROVIDER_CONTENT_TYPE);
        marker.addProperty("formatVersion", 1);
        marker.addProperty("providerId", providerId);
        return compiled(marker.toString());
    }

    @Override
    public CompiledTerrainContent compileJsonLevel(CatalogEntry entry, ResolvedLevel resolved) {
        requireKind(entry, CatalogEntry.Kind.JSON_LEVEL);
        if (resolved == null) {
            throw new IllegalArgumentException("resolved level == null");
        }
        LevelDocument source = resolved.source();
        if (!TrackProfile.GAMEPLAY_PROFILE_ID.equals(source.sessionProfileId())) {
            throw new IllegalArgumentException("Unsupported session profile '"
                    + source.sessionProfileId() + "'");
        }
        if (resolved.structures().isEmpty()) {
            throw new IllegalArgumentException("A published gameplay level cannot be empty");
        }
        List<LevelEntry> inline = new ArrayList<LevelEntry>(resolved.occurrences().size());
        BaseTerrainStructure<?>[] structures =
                new BaseTerrainStructure<?>[resolved.occurrences().size()];
        for (int i = 0; i < resolved.occurrences().size(); i++) {
            ResolvedStructureOccurrence occurrence = resolved.occurrences().get(i);
            StructureDocument structure = StructureOccurrenceNamespacer.namespace(occurrence);
            inline.add(LevelEntry.inline(stableSourceId(source.id(), structure.id(), i), structure));
            structures[i] = DataBackedStructureFactory.create(structure);
        }
        if (TerrainMaterializer.materialize(
                new TerrainLevelSequence(source.id(), structures),
                TrackProfile.gameplayDefault(), Vec3.ZERO,
                stableSeed(entry.id())).segments.isEmpty()) {
            throw new IllegalArgumentException("A published gameplay level must emit a tile");
        }
        LevelDocument flattened = new LevelDocument(source.formatVersion(), source.id(),
                source.sessionProfileId(), inline);
        // Parsing the codec output gives the compact, stable representation used by
        // both the published digest and RuntimeCatalogLoader verification.
        String normalized = JsonParser.parseString(codec.encode(flattened)).toString();
        return compiled(normalized);
    }

    private static void requireKind(CatalogEntry entry, CatalogEntry.Kind expected) {
        if (entry == null || entry.kind() != expected) {
            throw new IllegalArgumentException("Expected catalog entry kind " + expected);
        }
    }

    private static String stableSourceId(String levelId, String structureId, int index) {
        String key = "terrain-level-entry:" + levelId + ":" + index + ":" + structureId;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static long stableSeed(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static CompiledTerrainContent compiled(String normalized) {
        return new CompiledTerrainContent(normalized, ContentDigests.sha256(normalized));
    }
}
