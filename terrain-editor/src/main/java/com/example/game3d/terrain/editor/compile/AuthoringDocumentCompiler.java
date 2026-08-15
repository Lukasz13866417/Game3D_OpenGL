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
import com.example.game3d.terrain.io.resolve.StructureOccurrenceNamespacer;
import com.example.game3d.terrain.io.resolve.TerrainDocumentRepository;
import com.example.game3d.terrain.io.resolve.TerrainReferenceResolver;
import com.example.game3d.terrain.io.validation.TerrainValidator;
import com.example.game3d.terrain.io.validation.ValidationProblem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact editor preview compiler using the shared structure interpreter. */
public final class AuthoringDocumentCompiler implements DocumentCompiler {
    private final TerrainDocumentRepository repository;
    private final TerrainValidator validator = new TerrainValidator();
    private final TerrainReferenceResolver resolver = new TerrainReferenceResolver();

    public AuthoringDocumentCompiler(TerrainDocumentRepository repository) {
        this.repository = repository;
    }

    @Override public CompileResult compile(long revision, TerrainSourceDocument document) throws Exception {
        List<ValidationProblem> problems = new ArrayList<>(validator.validate(document).problems());
        for (ValidationProblem problem : problems)
            if (problem.severity() == ValidationProblem.Severity.ERROR)
                return new CompileResult(revision, null, Map.of(), Map.of(), problems);

        List<PreviewOccurrence> structures = new ArrayList<>();
        String profileId;
        if (document instanceof StructureDocument structure) {
            structures.add(new PreviewOccurrence(null, structure));
            profileId = TrackProfile.GAMEPLAY_PROFILE_ID;
        } else if (document instanceof LevelDocument level) {
            ResolvedLevel resolved = resolver.resolve(level, repository);
            profileId = level.sessionProfileId();
            for (LevelDocument resolvedLevel : resolved.levels()) {
                problems.addAll(validator.validate(resolvedLevel).problems());
                if (!profileId.equals(resolvedLevel.sessionProfileId())) {
                    problems.add(new ValidationProblem(
                            ValidationProblem.Severity.ERROR,
                            "$.sessionProfileId",
                            "referenced level '" + resolvedLevel.id()
                                    + "' uses a different session profile"));
                }
            }
            for (ResolvedStructureOccurrence occurrence : resolved.occurrences()) {
                problems.addAll(validator.validate(occurrence.structure()).problems());
                structures.add(new PreviewOccurrence(occurrence,
                        StructureOccurrenceNamespacer.namespace(occurrence)));
            }
        } else {
            throw new IllegalArgumentException("Catalogs are published, not previewed");
        }
        for (ValidationProblem problem : problems)
            if (problem.severity() == ValidationProblem.Severity.ERROR)
                return new CompileResult(revision, null, Map.of(), Map.of(), problems);
        if (!TrackProfile.GAMEPLAY_PROFILE_ID.equals(profileId) && !"gameplay-default".equals(profileId))
            throw new IllegalArgumentException("Unknown track profile " + profileId);

        Terrain terrain = new Terrain(TrackProfile.gameplayDefault(), Vec3.ZERO, 0L);
        Map<String, Long> segmentIds = new LinkedHashMap<>();
        Map<String, Long> addonIds = new LinkedHashMap<>();
        List<QueuedPreview> queued = new ArrayList<>();
        for (PreviewOccurrence structure : structures) {
            BaseTerrainStructure<?> authored =
                    DataBackedStructureFactory.create(structure.materializedDocument);
            queued.add(new QueuedPreview(
                    structure, terrain.enqueueStructure(authored)));
        }
        while (terrain.hasPendingGenerationWork()) terrain.generate(GenerationBudget.UNLIMITED);
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
        terrain.close();
        return new CompileResult(revision, snapshot, segmentIds, addonIds, problems);
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
