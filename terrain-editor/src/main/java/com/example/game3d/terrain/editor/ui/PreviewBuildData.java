package com.example.game3d.terrain.editor.ui;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.core.terrain.addon.Addon;
import com.example.game3d.core.terrain.addon.AddonFootprint;
import com.example.game3d.core.terrain.addon.DeathSpike;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * JavaFX-free preview payload prepared away from the application thread.
 *
 * <p>The primitive arrays are private to this pipeline and are treated as immutable after
 * construction. JavaFX only copies them into scene-graph meshes during the short attach phase.</p>
 */
final class PreviewBuildData {
    final TerrainSnapshot snapshot;
    final PreviewBounds bounds;
    final Map<Long, String> segmentSources;
    final Map<Long, String> addonSources;
    final Map<String, PreviewBounds> sourceBounds;
    final List<PreviewTerrainChunkData> terrainChunks;
    final List<PreviewSpikeChunkData> spikeChunks;
    final List<Addon> sparseAddons;

    private PreviewBuildData(
            TerrainSnapshot snapshot,
            PreviewBounds bounds,
            Map<Long, String> segmentSources,
            Map<Long, String> addonSources,
            Map<String, PreviewBounds> sourceBounds,
            List<PreviewTerrainChunkData> terrainChunks,
            List<PreviewSpikeChunkData> spikeChunks,
            List<Addon> sparseAddons) {
        this.snapshot = snapshot;
        this.bounds = bounds;
        this.segmentSources = Map.copyOf(segmentSources);
        this.addonSources = Map.copyOf(addonSources);
        this.sourceBounds = Collections.unmodifiableMap(
                new LinkedHashMap<>(sourceBounds));
        this.terrainChunks = List.copyOf(terrainChunks);
        this.spikeChunks = List.copyOf(spikeChunks);
        this.sparseAddons = List.copyOf(sparseAddons);
    }

    static PreviewBuildData build(
            TerrainSnapshot snapshot,
            Map<String, Long> sourceSegments,
            Map<String, Long> sourceAddons) {
        if (snapshot == null || sourceSegments == null || sourceAddons == null) {
            throw new IllegalArgumentException("Preview build arguments are required");
        }
        Map<Long, String> segmentSources = reverse(sourceSegments);
        Map<Long, String> addonSources = reverse(sourceAddons);
        Map<String, PreviewBounds> sourceBounds = new LinkedHashMap<>();
        PreviewBounds.Builder completeBounds = new PreviewBounds.Builder();
        List<DeathSpike> spikes = new ArrayList<>();
        List<Addon> sparse = new ArrayList<>();

        for (TerrainSegment segment : snapshot.segments) {
            checkCancelled();
            completeBounds.includeWorld(segment.nearLeft);
            completeBounds.includeWorld(segment.nearRight);
            completeBounds.includeWorld(segment.farLeft);
            completeBounds.includeWorld(segment.farRight);
            String segmentSource = segmentSources.get(segment.id);
            if (segmentSource != null) {
                mergeSourceAliases(sourceBounds, segmentSource,
                        PreviewBounds.aroundWorld(segment.nearLeft, segment.nearRight,
                                segment.farRight, segment.farLeft));
            }
            for (Addon addon : segment.addons) {
                AddonFootprint footprint = addon.footprint();
                completeBounds.includeWorld(footprint.nearLeft);
                completeBounds.includeWorld(footprint.nearRight);
                completeBounds.includeWorld(footprint.farLeft);
                completeBounds.includeWorld(footprint.farRight);
                completeBounds.includeWorldAabb(addon.broadPhaseBounds().min,
                        addon.broadPhaseBounds().max);
                String addonSource = addonSources.get(addon.id());
                if (addonSource != null) {
                    mergeSourceAliases(sourceBounds, addonSource,
                            PreviewBounds.aroundWorld(addon.broadPhaseBounds().min,
                                    addon.broadPhaseBounds().max));
                }
                if (addon instanceof DeathSpike spike) spikes.add(spike);
                else sparse.add(addon);
            }
        }

        List<PreviewTerrainChunkData> terrainChunks = new ArrayList<>();
        for (int start = 0; start < snapshot.segments.size();
             start += PreviewTerrainChunkData.SEGMENTS_PER_CHUNK) {
            checkCancelled();
            int end = Math.min(snapshot.segments.size(),
                    start + PreviewTerrainChunkData.SEGMENTS_PER_CHUNK);
            terrainChunks.add(PreviewTerrainChunkData.build(
                    snapshot.segments, start, end));
        }

        List<PreviewSpikeChunkData> spikeChunks = new ArrayList<>();
        for (int start = 0; start < spikes.size();
             start += PreviewSpikeChunkData.SPIKES_PER_CHUNK) {
            checkCancelled();
            int end = Math.min(spikes.size(),
                    start + PreviewSpikeChunkData.SPIKES_PER_CHUNK);
            spikeChunks.add(PreviewSpikeChunkData.build(spikes, start, end));
        }
        checkCancelled();
        return new PreviewBuildData(snapshot, completeBounds.build(),
                segmentSources, addonSources, sourceBounds,
                terrainChunks, spikeChunks, sparse);
    }

    private static Map<Long, String> reverse(Map<String, Long> source) {
        Map<Long, String> out = new HashMap<>();
        source.forEach((key, value) -> {
            if (key != null && value != null) out.put(value, key);
        });
        return out;
    }

    /** Exact source plus the top-level occurrence; never aliases a local trailing ID. */
    private static void mergeSourceAliases(
            Map<String, PreviewBounds> destination,
            String source,
            PreviewBounds bounds) {
        destination.merge(source, bounds, PreviewBounds::union);
        int first = source.indexOf('/');
        if (first > 0) {
            destination.merge(source.substring(0, first),
                    bounds, PreviewBounds::union);
        }
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Preview build superseded");
        }
    }
}

/** Pure terrain mesh arrays for one bounded chunk. */
record PreviewTerrainChunkData(
        int startIndex,
        int segmentCount,
        long geometryDigest,
        PreviewMeshBuffer fill,
        PreviewMeshBuffer edges) {
    static final int SEGMENTS_PER_CHUNK = 128;
    private static final int[] QUAD_TRIANGLES = {0, 1, 2, 0, 2, 3};

    static PreviewTerrainChunkData build(
            List<TerrainSegment> segments, int start, int endExclusive) {
        int solidCount = 0;
        for (int index = start; index < endExclusive; index++) {
            if (segments.get(index).solid) solidCount++;
        }
        float[] fillPoints = new float[Math.max(3, solidCount * 12)];
        int[] fillFaces = new int[solidCount * 12];
        long[] fillIds = new long[solidCount * 2];
        int allCount = endExclusive - start;
        float[] edgePoints = new float[Math.max(3, allCount * 12)];
        int[] edgeFaces = new int[allCount * 12];
        long[] edgeIds = new long[allCount * 2];
        int fillItem = 0;
        int edgeItem = 0;
        long digest = 0xcbf29ce484222325L;

        for (int index = start; index < endExclusive; index++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Preview build superseded");
            }
            TerrainSegment segment = segments.get(index);
            digest = mix(digest, segment.id);
            digest = mix(digest, segment.solid ? 1L : 0L);
            digest = mixVec(digest, segment.nearLeft);
            digest = mixVec(digest, segment.nearRight);
            digest = mixVec(digest, segment.farRight);
            digest = mixVec(digest, segment.farLeft);
            writeQuad(edgePoints, edgeFaces, edgeIds, edgeItem++, segment);
            if (segment.solid) {
                writeQuad(fillPoints, fillFaces, fillIds, fillItem++, segment);
            }
        }
        return new PreviewTerrainChunkData(start, allCount, digest,
                new PreviewMeshBuffer(fillPoints, fillFaces, fillIds),
                new PreviewMeshBuffer(edgePoints, edgeFaces, edgeIds));
    }

    private static void writeQuad(
            float[] points, int[] faces, long[] ids,
            int item, TerrainSegment segment) {
        int pointOffset = item * 12;
        writePoint(points, pointOffset, segment.nearLeft);
        writePoint(points, pointOffset + 3, segment.nearRight);
        writePoint(points, pointOffset + 6, segment.farRight);
        writePoint(points, pointOffset + 9, segment.farLeft);
        int pointBase = item * 4;
        int faceBase = item * 12;
        for (int index = 0; index < QUAD_TRIANGLES.length; index++) {
            faces[faceBase + index * 2] = pointBase + QUAD_TRIANGLES[index];
            faces[faceBase + index * 2 + 1] = 0;
        }
        ids[item * 2] = segment.id;
        ids[item * 2 + 1] = segment.id;
    }

    private static void writePoint(float[] destination, int offset, Vec3 value) {
        destination[offset] = (float) value.x;
        destination[offset + 1] = (float) -value.y;
        destination[offset + 2] = (float) value.z;
    }

    private static long mixVec(long hash, Vec3 value) {
        hash = mix(hash, Double.doubleToLongBits(value.x));
        hash = mix(hash, Double.doubleToLongBits(value.y));
        return mix(hash, Double.doubleToLongBits(value.z));
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 0x100000001b3L;
    }
}

/** Pure spike mesh arrays for one bounded chunk. */
record PreviewSpikeChunkData(
        int startIndex,
        int spikeCount,
        long geometryDigest,
        PreviewMeshBuffer mesh) {
    static final int SPIKES_PER_CHUNK = 128;
    private static final int[] SIDE_TRIANGLES = {
            0, 1, 4, 1, 2, 4, 2, 3, 4, 3, 0, 4
    };

    static PreviewSpikeChunkData build(
            List<DeathSpike> spikes, int start, int endExclusive) {
        int count = endExclusive - start;
        float[] points = new float[count * 15];
        int[] faces = new int[count * 24];
        long[] ids = new long[count * 4];
        long digest = 0xcbf29ce484222325L;
        for (int index = start; index < endExclusive; index++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Preview build superseded");
            }
            DeathSpike spike = spikes.get(index);
            int item = index - start;
            int pointOffset = item * 15;
            writePoint(points, pointOffset, spike.nearLeft);
            writePoint(points, pointOffset + 3, spike.nearRight);
            writePoint(points, pointOffset + 6, spike.farRight);
            writePoint(points, pointOffset + 9, spike.farLeft);
            writePoint(points, pointOffset + 12, spike.apex);
            int pointBase = item * 5;
            int faceBase = item * 24;
            for (int vertex = 0; vertex < SIDE_TRIANGLES.length; vertex++) {
                faces[faceBase + vertex * 2] = pointBase + SIDE_TRIANGLES[vertex];
                faces[faceBase + vertex * 2 + 1] = 0;
            }
            for (int face = 0; face < 4; face++) ids[item * 4 + face] = spike.id();
            digest = (digest ^ spike.deterministicDigest()) * 0x100000001b3L;
        }
        return new PreviewSpikeChunkData(start, count, digest,
                new PreviewMeshBuffer(points, faces, ids));
    }

    private static void writePoint(float[] destination, int offset, Vec3 value) {
        destination[offset] = (float) value.x;
        destination[offset + 1] = (float) -value.y;
        destination[offset + 2] = (float) value.z;
    }
}

/** Arrays are produced once on the worker and only read while attaching a JavaFX mesh. */
record PreviewMeshBuffer(float[] points, int[] faces, long[] faceSourceIds) {
}
