package com.example.game3d.terrain.io.publish;

import com.example.game3d.terrain.io.model.CatalogDocument;
import com.example.game3d.terrain.io.model.CatalogEntry;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.resolve.ResolutionException;
import com.example.game3d.terrain.io.resolve.ResolvedLevel;
import com.example.game3d.terrain.io.resolve.TerrainDocumentRepository;
import com.example.game3d.terrain.io.resolve.TerrainReferenceResolver;
import com.example.game3d.terrain.io.store.AtomicFileStore;
import com.example.game3d.terrain.io.store.ContentDigests;
import com.example.game3d.terrain.io.validation.TerrainValidator;
import com.example.game3d.terrain.io.validation.TerrainContentLimits;
import com.example.game3d.terrain.io.validation.ValidationProblem;
import com.example.game3d.terrain.io.validation.ValidationResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Validates and compiles the full enabled catalog before atomically replacing runtime content. */
public final class TerrainPublisher {
    private final TerrainValidator validator;
    private final TerrainReferenceResolver resolver;
    private final TerrainContentCompiler compiler;
    private final AtomicFileStore files;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public TerrainPublisher(TerrainValidator validator, TerrainReferenceResolver resolver,
                            TerrainContentCompiler compiler, AtomicFileStore files) {
        this.validator = validator;
        this.resolver = resolver;
        this.compiler = compiler;
        this.files = files;
    }

    public PublishResult publish(CatalogDocument catalog, TerrainDocumentRepository repository, Path output)
            throws PublishException {
        ValidationResult catalogValidation = validator.validate(catalog);
        if (!catalogValidation.isValid()) throw new PublishException("Catalog validation failed",
                catalogValidation.problems());

        List<CompiledEntry> compiled = new ArrayList<>();
        long customTileCount = 0L;
        long customAddonCount = 0L;
        for (CatalogEntry entry : catalog.entries()) {
            if (!entry.enabled()) continue;
            try {
                CompiledTerrainContent value;
                if (entry.kind() == CatalogEntry.Kind.JAVA_PROVIDER) {
                    value = compiler.compileJavaProvider(entry);
                } else {
                    LevelDocument level = repository.findLevel(entry.location());
                    if (level == null) throw new PublishException(
                            "Missing JSON level '" + entry.location() + "' for catalog entry '" + entry.id() + "'");
                    ResolvedLevel resolved = resolver.resolve(level, repository);
                    List<ValidationProblem> problems = new ArrayList<ValidationProblem>();
                    for (LevelDocument resolvedLevel : resolved.levels()) {
                        problems.addAll(validator.validate(resolvedLevel).problems());
                        if (!level.sessionProfileId().equals(
                                resolvedLevel.sessionProfileId())) {
                            throw new PublishException("Referenced level profile mismatch for '"
                                    + resolvedLevel.id() + "'");
                        }
                    }
                    for (com.example.game3d.terrain.io.model.StructureDocument structure
                            : resolved.structures()) {
                        customTileCount += structure.tiles().size();
                        customAddonCount += structure.addons().size();
                    }
                    if (customTileCount
                            > TerrainContentLimits.MAX_PUBLISHED_CUSTOM_TILES
                            || customAddonCount
                            > TerrainContentLimits.MAX_PUBLISHED_CUSTOM_ADDONS) {
                        throw new PublishException(
                                "Enabled JSON content exceeds published terrain limits");
                    }
                    for (int i = 0; i < resolved.structures().size(); i++)
                        problems.addAll(validator.validate(resolved.structures().get(i)).problems());
                    for (ValidationProblem problem : problems) {
                        if (problem.severity() == ValidationProblem.Severity.ERROR)
                            throw new PublishException("Content validation failed for '" + entry.id() + "'", problems);
                    }
                    value = compiler.compileJsonLevel(entry, resolved);
                }
                verifyCompiled(entry, value);
                compiled.add(new CompiledEntry(entry, value));
            } catch (PublishException error) {
                throw error;
            } catch (ResolutionException error) {
                throw new PublishException("Could not resolve '" + entry.id() + "'", error);
            } catch (Exception error) {
                throw new PublishException("Could not compile '" + entry.id() + "'", error);
            }
        }

        JsonObject runtime = new JsonObject();
        runtime.addProperty("formatVersion", 1);
        runtime.addProperty("sourceCatalogId", catalog.id());
        JsonArray entries = new JsonArray();
        for (CompiledEntry item : compiled) {
            JsonObject out = new JsonObject();
            out.addProperty("id", item.source.id());
            out.addProperty("digest", item.content.digest());
            JsonElement payload = JsonParser.parseString(item.content.normalizedJson());
            out.add("content", payload);
            entries.add(out);
        }
        runtime.add("entries", entries);
        String encoded = gson.toJson(runtime) + "\n";
        try {
            files.writeUtf8(output, encoded);
        } catch (Exception error) {
            throw new PublishException("Could not replace published catalog", error);
        }
        return new PublishResult(output, compiled.size(), ContentDigests.sha256(encoded));
    }

    private void verifyCompiled(CatalogEntry entry, CompiledTerrainContent value) throws PublishException {
        final JsonElement parsed;
        try {
            parsed = JsonParser.parseString(value.normalizedJson());
        } catch (RuntimeException error) {
            throw new PublishException("Compiler returned invalid JSON for '" + entry.id() + "'", error);
        }
        String actual = ContentDigests.sha256(parsed.toString());
        if (!actual.equals(value.digest())) throw new PublishException(
                "Compiler digest mismatch for '" + entry.id() + "': expected " + actual
                        + " but got " + value.digest());
    }

    private static final class CompiledEntry {
        final CatalogEntry source;
        final CompiledTerrainContent content;
        CompiledEntry(CatalogEntry source, CompiledTerrainContent content) {
            this.source = source;
            this.content = content;
        }
    }
}
