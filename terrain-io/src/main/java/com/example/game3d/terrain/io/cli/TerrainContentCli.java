package com.example.game3d.terrain.io.cli;

import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.CatalogDocument;
import com.example.game3d.terrain.io.model.TerrainSourceDocument;
import com.example.game3d.terrain.io.publish.AuthoringTerrainContentCompiler;
import com.example.game3d.terrain.io.publish.PublishResult;
import com.example.game3d.terrain.io.publish.PublishedGameplayCatalogLoader;
import com.example.game3d.terrain.io.publish.TerrainPublisher;
import com.example.game3d.terrain.io.resolve.FileTerrainDocumentRepository;
import com.example.game3d.terrain.io.resolve.TerrainReferenceResolver;
import com.example.game3d.terrain.io.store.AtomicFileStore;
import com.example.game3d.terrain.io.validation.TerrainValidator;

import java.io.PrintStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Small dependency-free entry point used by Gradle and CI content tasks. */
public final class TerrainContentCli {
    private TerrainContentCli() {}

    public static void main(String[] args) {
        int result = run(args, System.out, System.err);
        if (result != 0) System.exit(result);
    }

    public static int run(String[] args, PrintStream out, PrintStream error) {
        if (args == null || args.length == 0) return usage(error);
        try {
            if ("publish".equals(args[0]) && args.length == 4) {
                return publish(Paths.get(args[1]), Paths.get(args[2]), Paths.get(args[3]), out);
            }
            if ("validate".equals(args[0]) && args.length == 2) {
                return validate(Paths.get(args[1]), out);
            }
            return usage(error);
        } catch (Exception failure) {
            error.println("Terrain content error: " + failure.getMessage());
            return 1;
        }
    }

    private static int publish(Path catalogPath, Path contentRoot, Path output, PrintStream out)
            throws Exception {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        String catalogJson = new String(Files.readAllBytes(catalogPath), StandardCharsets.UTF_8);
        TerrainSourceDocument document = codec.decode(catalogJson);
        if (!(document instanceof CatalogDocument)) {
            throw new IllegalArgumentException("Expected a catalog document at " + catalogPath);
        }
        FileTerrainDocumentRepository repository =
                FileTerrainDocumentRepository.load(contentRoot, codec);
        AtomicFileStore files = new AtomicFileStore();
        TerrainPublisher publisher = new TerrainPublisher(new TerrainValidator(),
                new TerrainReferenceResolver(), new AuthoringTerrainContentCompiler(),
                files);
        Path absoluteOutput = output.toAbsolutePath();
        Path parent = absoluteOutput.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Runtime output has no parent: " + output);
        }
        Files.createDirectories(parent);
        Path staged = Files.createTempFile(
                parent, absoluteOutput.getFileName().toString() + ".", ".validation");
        try {
            PublishResult result = publisher.publish(
                    (CatalogDocument) document, repository, staged);
            try (Reader reader = Files.newBufferedReader(staged, StandardCharsets.UTF_8)) {
                new PublishedGameplayCatalogLoader().load(reader);
            }
            String encoded = new String(Files.readAllBytes(staged), StandardCharsets.UTF_8);
            files.writeUtf8(absoluteOutput, encoded);
            out.println("Published " + result.entryCount() + " terrain entries to "
                    + absoluteOutput + " (" + result.catalogDigest() + ")");
            return 0;
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private static int validate(Path published, PrintStream out) throws Exception {
        try (Reader reader = Files.newBufferedReader(published, StandardCharsets.UTF_8)) {
            int count = new PublishedGameplayCatalogLoader().load(reader).entries().size();
            out.println("Valid terrain runtime catalog: " + count + " gameplay providers");
        }
        return 0;
    }

    private static int usage(PrintStream error) {
        error.println("Usage:");
        error.println("  TerrainContentCli publish <catalog.json> <content-root> <runtime-output.json>");
        error.println("  TerrainContentCli validate <runtime-catalog.json>");
        return 2;
    }
}
