package com.example.game3d.terrain.editor.compile;

import com.example.game3d.authoring.BaseTerrainStructure;
import com.example.game3d.authoring.GenerationBudget;
import com.example.game3d.authoring.MaterializedStructure;
import com.example.game3d.authoring.QueuedStructure;
import com.example.game3d.authoring.Terrain;
import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.terrain.io.authoring.DataBackedStructureFactory;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TerrainSourceDocument;
import com.example.game3d.terrain.io.resolve.ResolvedLevel;
import com.example.game3d.terrain.io.resolve.ResolvedStructureOccurrence;
import com.example.game3d.terrain.io.resolve.ResolutionException;
import com.example.game3d.terrain.io.resolve.StructureOccurrenceNamespacer;
import com.example.game3d.terrain.io.resolve.TerrainDocumentRepository;
import com.example.game3d.terrain.io.resolve.TerrainReferenceResolver;
import com.example.game3d.terrain.io.validation.TerrainContentLimits;
import com.example.game3d.terrain.io.validation.TerrainValidator;
import com.example.game3d.terrain.io.validation.ValidationProblem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Exact editor preview compiler using the shared structure interpreter. */
public final class AuthoringDocumentCompiler implements DocumentCompiler {
    private static final TerrainDocumentRepository EMPTY_REPOSITORY =
            new TerrainDocumentRepository() {
                @Override public StructureDocument findStructure(String id) { return null; }
                @Override public LevelDocument findLevel(String id) { return null; }
            };

    private final TerrainDocumentRepository compatibilityRepository;
    private final TerrainValidator validator = new TerrainValidator();
    private final TerrainReferenceResolver resolver = new TerrainReferenceResolver();

    public AuthoringDocumentCompiler() {
        this.compatibilityRepository = EMPTY_REPOSITORY;
    }

    /** Compatibility constructor for direct unit-test compilation. Production passes snapshots. */
    public AuthoringDocumentCompiler(TerrainDocumentRepository repository) {
        this.compatibilityRepository = repository;
    }

    @Override public CompileResult compile(CompileRequest request) throws Exception {
        TerrainSourceDocument document = request.document();
        TerrainDocumentRepository repository = request.references();
        List<ValidationProblem> problems = new ArrayList<>(request.sessionProblems());
        problems.addAll(validator.validate(document).problems());
        for (ValidationProblem problem : problems)
            if (problem.severity() == ValidationProblem.Severity.ERROR)
                return result(request, null, Map.of(), Map.of(), problems);

        List<PreviewOccurrence> structures = new ArrayList<>();
        String profileId;
        if (document instanceof StructureDocument structure) {
            structures.add(new PreviewOccurrence(null, structure));
            profileId = TrackProfile.GAMEPLAY_PROFILE_ID;
        } else if (document instanceof LevelDocument level) {
            profileId = level.sessionProfileId();
            if (!supportedProfile(profileId)) {
                problems.add(error("$.sessionProfileId",
                        "Unknown track profile '" + profileId + "'"));
            }
            collectReferenceProblems(level, repository, profileId, "$",
                    new HashSet<>(), 0, new int[] {0}, problems);
            if (!hasErrors(problems)) {
                final ResolvedLevel resolved;
                try {
                    resolved = resolver.resolve(level, repository);
                } catch (ResolutionException invalidReferences) {
                    // Resolution limits and any future expected reference rejection are authored
                    // document errors, not failures of the compiler implementation.
                    problems.add(error("$", invalidReferences.getMessage()));
                    return result(request, null, Map.of(), Map.of(), problems);
                }
                for (ResolvedStructureOccurrence occurrence : resolved.occurrences()) {
                    structures.add(new PreviewOccurrence(occurrence,
                            StructureOccurrenceNamespacer.namespace(occurrence)));
                }
            }
        } else {
            throw new IllegalArgumentException("Catalogs are published, not previewed");
        }
        for (ValidationProblem problem : problems)
            if (problem.severity() == ValidationProblem.Severity.ERROR)
                return result(request, null, Map.of(), Map.of(), problems);
        if (!supportedProfile(profileId)) {
            return result(request, null, Map.of(), Map.of(), problems);
        }

        Terrain terrain = new Terrain(TrackProfile.gameplayDefault(), Vec3.ZERO, 0L);
        try {
            Map<String, Long> segmentIds = new LinkedHashMap<>();
            Map<String, Long> addonIds = new LinkedHashMap<>();
            List<QueuedPreview> queued = new ArrayList<>();
            for (PreviewOccurrence structure : structures) {
                BaseTerrainStructure<?> authored =
                        DataBackedStructureFactory.create(structure.materializedDocument);
                queued.add(new QueuedPreview(
                        structure, terrain.enqueueStructure(authored)));
            }
            while (terrain.hasPendingGenerationWork()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Preview compilation cancelled");
                }
                terrain.generate(GenerationBudget.UNLIMITED);
            }
            for (QueuedPreview queuedStructure : queued) {
                MaterializedStructure result = queuedStructure.ticket.materialized();
                if (queuedStructure.occurrence.resolved == null) {
                    segmentIds.putAll(result.sourceSegmentIds);
                    addonIds.putAll(result.sourceAddonIds);
                    continue;
                }
                ResolvedStructureOccurrence occurrence = queuedStructure.occurrence.resolved;
                for (com.example.game3d.terrain.io.model.TileRecord tile
                        : occurrence.structure().tiles()) {
                    Long id = result.sourceSegmentIds.get(
                            occurrence.namespacedSourceId(tile.sourceId()));
                    if (id == null) throw new IllegalStateException(
                            "Missing materialized source segment " + tile.sourceId());
                    segmentIds.put(occurrence.sourceKey(tile.sourceId()), id);
                }
                for (com.example.game3d.terrain.io.model.AddonReservation addon
                        : occurrence.structure().addons()) {
                    Long id = result.sourceAddonIds.get(
                            occurrence.namespacedSourceId(addon.sourceId()));
                    if (id == null) throw new IllegalStateException(
                            "Missing materialized source addon " + addon.sourceId());
                    addonIds.put(occurrence.sourceKey(addon.sourceId()), id);
                }
            }
            TerrainSnapshot snapshot = terrain.snapshot();
            return result(request, snapshot, segmentIds, addonIds, problems);
        } finally {
            terrain.close();
        }
    }

    /** Direct deterministic compilation retained for importer and compiler unit tests. */
    public CompileResult compile(long revision, TerrainSourceDocument document) throws Exception {
        CompileTicket ticket = new CompileTicket(new UUID(0L, 0L), revision);
        return compile(new CompileRequest(ticket, revision, document,
                compatibilityRepository));
    }

    private static CompileResult result(
            CompileRequest request,
            TerrainSnapshot snapshot,
            Map<String, Long> segmentIds,
            Map<String, Long> addonIds,
            List<ValidationProblem> problems) {
        return new CompileResult(request.ticket(), request.documentRevision(), snapshot,
                segmentIds, addonIds, problems);
    }

    /**
     * Validates the complete reference graph while retaining a path anchored in the root level.
     * Nested paths deliberately remain below the root entry that introduces the occurrence, so
     * the Problems panel can always select that actionable sequence item.
     */
    private void collectReferenceProblems(
            LevelDocument level,
            TerrainDocumentRepository repository,
            String rootProfileId,
            String levelPath,
            Set<String> activeLevelIds,
            int depth,
            int[] resolvedLevelCount,
            List<ValidationProblem> problems) {
        if (depth >= TerrainContentLimits.MAX_REFERENCE_DEPTH) {
            problems.add(error(levelPath, "Level reference depth exceeds "
                    + TerrainContentLimits.MAX_REFERENCE_DEPTH));
            return;
        }
        resolvedLevelCount[0]++;
        if (resolvedLevelCount[0] > TerrainContentLimits.MAX_RESOLVED_LEVELS) {
            problems.add(error(levelPath, "Resolved level expansion exceeds "
                    + TerrainContentLimits.MAX_RESOLVED_LEVELS + " levels"));
            return;
        }
        if (!activeLevelIds.add(level.id())) {
            problems.add(error(levelPath,
                    "Level reference cycle reaches '" + level.id() + "'"));
            return;
        }
        try {
            for (int index = 0; index < level.entries().size(); index++) {
                com.example.game3d.terrain.io.model.LevelEntry entry =
                        level.entries().get(index);
                String entryPath = levelPath + ".entries[" + index + "]";
                if (entry.kind()
                        == com.example.game3d.terrain.io.model.LevelEntry.Kind.STRUCTURE_REFERENCE) {
                    StructureDocument structure = repository.findStructure(entry.structureRef());
                    if (structure == null) {
                        problems.add(error(entryPath + ".structureRef",
                                "Missing structure '" + entry.structureRef() + "'"));
                        continue;
                    }
                    addRebasedProblems(problems,
                            validator.validate(structure).problems(),
                            entryPath + ".resolvedStructure",
                            "Referenced structure '" + structure.id() + "': ");
                } else if (entry.kind()
                        == com.example.game3d.terrain.io.model.LevelEntry.Kind.LEVEL_REFERENCE) {
                    LevelDocument child = repository.findLevel(entry.levelRef());
                    if (child == null) {
                        problems.add(error(entryPath + ".levelRef",
                                "Missing level '" + entry.levelRef() + "'"));
                        continue;
                    }
                    String childPath = entryPath + ".resolvedLevel";
                    addRebasedProblems(problems, validator.validate(child).problems(),
                            childPath, "Referenced level '" + child.id() + "': ");
                    if (!rootProfileId.equals(child.sessionProfileId())) {
                        problems.add(error(childPath + ".sessionProfileId",
                                "Referenced level '" + child.id()
                                        + "' uses profile '" + child.sessionProfileId()
                                        + "' instead of '" + rootProfileId + "'"));
                    }
                    collectReferenceProblems(child, repository, rootProfileId,
                            childPath, activeLevelIds, depth + 1,
                            resolvedLevelCount, problems);
                }
            }
        } finally {
            activeLevelIds.remove(level.id());
        }
    }

    private static void addRebasedProblems(
            List<ValidationProblem> destination,
            List<ValidationProblem> source,
            String basePath,
            String messagePrefix) {
        for (ValidationProblem problem : source) {
            String suffix = problem.path().equals("$")
                    ? "" : problem.path().startsWith("$.")
                    ? problem.path().substring(1) : "." + problem.path();
            destination.add(new ValidationProblem(problem.severity(),
                    basePath + suffix, messagePrefix + problem.message()));
        }
    }

    private static boolean supportedProfile(String profileId) {
        return TrackProfile.GAMEPLAY_PROFILE_ID.equals(profileId)
                || "gameplay-default".equals(profileId);
    }

    private static boolean hasErrors(List<ValidationProblem> problems) {
        return problems.stream().anyMatch(problem ->
                problem.severity() == ValidationProblem.Severity.ERROR);
    }

    private static ValidationProblem error(String path, String message) {
        return new ValidationProblem(ValidationProblem.Severity.ERROR, path, message);
    }

    private static final class PreviewOccurrence {
        final ResolvedStructureOccurrence resolved;
        final StructureDocument materializedDocument;

        PreviewOccurrence(
                ResolvedStructureOccurrence resolved,
                StructureDocument materializedDocument) {
            this.resolved = resolved;
            this.materializedDocument = materializedDocument;
        }
    }

    private static final class QueuedPreview {
        final PreviewOccurrence occurrence;
        final QueuedStructure ticket;

        QueuedPreview(PreviewOccurrence occurrence, QueuedStructure ticket) {
            this.occurrence = occurrence;
            this.ticket = ticket;
        }
    }
}
