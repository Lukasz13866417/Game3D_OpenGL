package com.example.game3d.terrain.io.publish;

import com.example.game3d.authoring.BaseTerrainStructure;
import com.example.game3d.authoring.GameplayLevelCatalog;
import com.example.game3d.authoring.GameplayLevelProvider;
import com.example.game3d.authoring.MaterializedStructure;
import com.example.game3d.authoring.TerrainLevelSequence;
import com.example.game3d.authoring.TerrainMaterializer;
import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.terrain.io.CodecException;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.authoring.DataBackedStructureFactory;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.publish.RuntimeCatalogEntry;
import com.example.game3d.terrain.io.validation.TerrainValidator;
import com.example.game3d.terrain.io.validation.TerrainContentLimits;
import com.example.game3d.terrain.io.validation.ValidationResult;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Converts the normalized published artifact into executable authoring providers.
 * No partially valid artifact is observable: every failure returns the six Java
 * built-ins as one immutable catalog.
 */
public final class PublishedGameplayCatalogLoader {
    private final TerrainJsonCodec codec;
    private final TerrainValidator validator;
    private final RuntimeCatalogLoader runtimeLoader;

    public PublishedGameplayCatalogLoader() {
        this(new TerrainJsonCodec(), new TerrainValidator(), new RuntimeCatalogLoader());
    }

    PublishedGameplayCatalogLoader(TerrainJsonCodec codec, TerrainValidator validator,
                                   RuntimeCatalogLoader runtimeLoader) {
        this.codec = codec;
        this.validator = validator;
        this.runtimeLoader = runtimeLoader;
    }

    /** Strict load for validation, tooling, and actionable session-preparation diagnostics. */
    public GameplayLevelCatalog load(Reader published) throws PublishedCatalogException {
        try {
            RuntimeCatalog runtime = runtimeLoader.load(published);
            return compile(runtime, GameplayLevelCatalog.builtIns());
        } catch (RuntimeCatalogException invalid) {
            throw new PublishedCatalogException("Published terrain catalog is invalid", invalid);
        } catch (CodecException invalid) {
            throw new PublishedCatalogException("Published terrain content is invalid", invalid);
        } catch (RuntimeException invalid) {
            throw new PublishedCatalogException("Published terrain content is invalid", invalid);
        }
    }

    public GameplayLevelCatalog loadOrBuiltIns(Reader published) {
        try {
            return load(published);
        } catch (PublishedCatalogException invalid) {
            return GameplayLevelCatalog.builtIns();
        }
    }

    private GameplayLevelCatalog compile(RuntimeCatalog runtime, GameplayLevelCatalog builtIns)
            throws CodecException {
        List<GameplayLevelProvider> builtinProviders = builtIns.entries();
        List<RuntimeCatalogEntry> entries = runtime.entries();
        if (entries.size() < builtinProviders.size()) {
            throw new IllegalArgumentException("Published catalog omits built-in providers");
        }

        Set<String> envelopeIds = new HashSet<String>();
        Set<String> providerIds = new HashSet<String>();
        for (int i = 0; i < entries.size(); i++) {
            RuntimeCatalogEntry entry = entries.get(i);
            if (!envelopeIds.add(entry.id())) {
                throw new IllegalArgumentException("Duplicate runtime entry ID " + entry.id());
            }
            JsonObject content = object(entry.compiledDefinition());
            if (i < builtinProviders.size()) {
                String expected = builtinProviders.get(i).stableId();
                String actual = javaProviderId(content);
                if (!expected.equals(actual) || !expected.equals(entry.id())
                        || !providerIds.add(actual)) {
                    throw new IllegalArgumentException("Built-in provider order mismatch");
                }
            } else if (isJavaProvider(content)) {
                // The production Java set is deliberately closed. Extra Java marker
                // entries would otherwise duplicate selection weight unpredictably.
                throw new IllegalArgumentException("Unexpected Java provider marker");
            }
        }

        ArrayList<GameplayLevelProvider> additions = new ArrayList<GameplayLevelProvider>();
        Set<String> stableIds = new HashSet<String>();
        long customTiles = 0L;
        long customAddons = 0L;
        for (GameplayLevelProvider provider : builtinProviders) stableIds.add(provider.stableId());
        for (int i = builtinProviders.size(); i < entries.size(); i++) {
            RuntimeCatalogEntry entry = entries.get(i);
            if (!stableIds.add(entry.id())) {
                throw new IllegalArgumentException("Duplicate gameplay provider ID " + entry.id());
            }
            LevelDocument level = codec.decodeLevel(entry.compiledDefinition().toString());
            requireSelfContained(level);
            ValidationResult validation = validator.validate(level);
            if (!validation.isValid()) {
                throw new IllegalArgumentException("Invalid published level " + entry.id());
            }
            for (LevelEntry levelEntry : level.entries()) {
                customTiles += levelEntry.inlineStructure().tiles().size();
                customAddons += levelEntry.inlineStructure().addons().size();
            }
            if (customTiles > TerrainContentLimits.MAX_PUBLISHED_CUSTOM_TILES
                    || customAddons
                    > TerrainContentLimits.MAX_PUBLISHED_CUSTOM_ADDONS) {
                throw new IllegalArgumentException(
                        "Published catalog exceeds custom terrain content limits");
            }
            InlineLevelProvider provider = new InlineLevelProvider(entry.id(), level);
            MaterializedStructure materialized = TerrainMaterializer.materialize(
                    provider.create(0L), TrackProfile.gameplayDefault(), Vec3.ZERO, 0L);
            if (materialized.segments.isEmpty()) {
                throw new IllegalArgumentException(
                        "Published level emits no terrain " + entry.id());
            }
            additions.add(provider);
        }
        return builtIns.withAdditionalEntries(additions);
    }

    private static void requireSelfContained(LevelDocument level) {
        if (!TrackProfile.GAMEPLAY_PROFILE_ID.equals(level.sessionProfileId())
                || level.entries().isEmpty()) {
            throw new IllegalArgumentException("Unsupported or empty gameplay level");
        }
        for (LevelEntry entry : level.entries()) {
            if (entry.kind() != LevelEntry.Kind.INLINE_STRUCTURE) {
                throw new IllegalArgumentException("Published levels must be self-contained");
            }
        }
    }

    private static JsonObject object(JsonElement content) {
        if (content == null || !content.isJsonObject()) {
            throw new IllegalArgumentException("Runtime content is not an object");
        }
        return content.getAsJsonObject();
    }

    private static boolean isJavaProvider(JsonObject content) {
        return content.has("contentType")
                && AuthoringTerrainContentCompiler.JAVA_PROVIDER_CONTENT_TYPE.equals(
                content.get("contentType").getAsString());
    }

    private static String javaProviderId(JsonObject content) {
        if (!isJavaProvider(content)
                || content.size() != 3
                || !content.has("formatVersion")
                || content.get("formatVersion").getAsInt() != 1
                || !content.has("providerId")) {
            throw new IllegalArgumentException("Malformed Java provider marker");
        }
        return content.get("providerId").getAsString();
    }

    private static final class InlineLevelProvider implements GameplayLevelProvider {
        private final String stableId;
        private final LevelDocument level;

        InlineLevelProvider(String stableId, LevelDocument level) {
            this.stableId = stableId;
            this.level = level;
        }

        @Override public String stableId() {
            return stableId;
        }

        @Override public BaseTerrainStructure<?> create(long levelOrdinal) {
            BaseTerrainStructure<?>[] structures =
                    new BaseTerrainStructure<?>[level.entries().size()];
            for (int i = 0; i < level.entries().size(); i++) {
                structures[i] = DataBackedStructureFactory.create(
                        level.entries().get(i).inlineStructure());
            }
            return new TerrainLevelSequence(stableId, structures);
        }
    }
}
