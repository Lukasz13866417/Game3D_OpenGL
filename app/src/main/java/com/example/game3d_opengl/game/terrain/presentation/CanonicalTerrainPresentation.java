package com.example.game3d_opengl.game.terrain.presentation;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.simulation.FeatureActivitySnapshot;
import com.example.game3d.core.simulation.SimulationFrameSnapshot;
import com.example.game3d.core.terrain.TerrainCommit;
import com.example.game3d.core.terrain.TerrainFeatureSpec;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.core.terrain.TerrainState;
import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.game.terrain.track_elements.GameplayElementBatchRenderers;
import com.example.game3d_opengl.game.terrain.track_elements.portal.CanonicalPortalVisual;
import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.util3d.FColor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Android/OpenGL caches derived from the same canonical terrain stream consumed by simulation.
 *
 * <p>This class owns no gameplay truth. Commits update its private presentation cache only after
 * they have crossed the terrain transaction boundary.</p>
 */
public final class CanonicalTerrainPresentation implements GPUResourceOwner {
    private final TerrainState terrain;
    private final CanonicalTerrainMeshRenderer mesh =
            new CanonicalTerrainMeshRenderer();
    private final TreeMap<Long, CanonicalSpikeInstance> spikes =
            new TreeMap<Long, CanonicalSpikeInstance>();
    private final TreeMap<Long, CanonicalCollectibleInstance> collectibles =
            new TreeMap<Long, CanonicalCollectibleInstance>();
    private final TreeMap<Long, CanonicalPortalVisual> portals =
            new TreeMap<Long, CanonicalPortalVisual>();
    private final TreeMap<Long, Long> portalDigests =
            new TreeMap<Long, Long>();
    private final TreeMap<Long, ArrayList<Long>> featureIdsBySegment =
            new TreeMap<Long, ArrayList<Long>>();
    private Vec3 renderOrigin = Vec3.ZERO;

    public CanonicalTerrainPresentation(TerrainSnapshot initialTerrain) {
        if (initialTerrain == null) {
            throw new IllegalArgumentException("initialTerrain == null");
        }
        terrain = new TerrainState(initialTerrain);
        mesh.rebuild(terrain.segments(), renderOrigin);
        initializeFeatureCaches();
    }

    public void applyTerrainCommit(TerrainCommit commit) {
        terrain.apply(commit);
        mesh.applyCommit(commit);
        applyFeatureCommit(commit);
    }

    /**
     * Applies an ordered commit burst and rebuilds CPU/GPU presentation caches once.
     *
     * <p>Simulation still acknowledges every revision independently; presentation only needs the
     * final state at the render boundary.</p>
     */
    public void applyTerrainCommits(List<TerrainCommit> commits) {
        if (commits == null || commits.isEmpty()) {
            return;
        }
        for (TerrainCommit commit : commits) {
            terrain.apply(commit);
            mesh.applyCommit(commit);
            applyFeatureCommit(commit);
        }
    }

    public long terrainRevision() {
        return terrain.revision();
    }

    public int visibleSegmentCount() {
        int count = 0;
        for (TerrainSegment ignored : terrain.segments()) {
            count++;
        }
        return count;
    }

    public int visibleFeatureCount() {
        return spikes.size() + collectibles.size() + portals.size();
    }

    public void setRenderOrigin(Vec3 nextOrigin) {
        Vec3 safe = nextOrigin == null ? Vec3.ZERO : nextOrigin;
        if (renderOrigin.equals(safe)) {
            return;
        }
        renderOrigin = safe;
        mesh.setRenderOrigin(renderOrigin);
        for (CanonicalSpikeInstance spike : spikes.values()) {
            spike.setRenderOrigin(renderOrigin);
        }
        for (CanonicalCollectibleInstance collectible : collectibles.values()) {
            collectible.setRenderOrigin(renderOrigin);
        }
        for (CanonicalPortalVisual portal : portals.values()) {
            portal.setRenderOrigin(renderOrigin);
        }
    }

    public void updateBeforeDraw(
            float dtMillis, SimulationFrameSnapshot frame) {
        FeatureActivitySnapshot activity =
                frame == null ? null : frame.featureActivity;
        for (CanonicalCollectibleInstance collectible : collectibles.values()) {
            if (isActive(activity, collectible.spec.id)) {
                collectible.update(dtMillis);
            }
        }
        for (CanonicalPortalVisual portal : portals.values()) {
            if (isActive(activity, portal.featureId())) {
                portal.update(dtMillis);
            }
        }
    }

    public void draw(
            float[] vp,
            FColor colorTheme,
            LightSource terrainLight,
            SimulationFrameSnapshot frame,
            float cameraEyeY) {
        if (frame != null && frame.terrainRevision != terrain.revision()) {
            throw new IllegalStateException(
                    "Presentation terrain revision " + terrain.revision()
                            + " differs from simulation revision "
                            + frame.terrainRevision);
        }
        mesh.draw(colorTheme, vp, terrainLight, cameraEyeY);
        FeatureActivitySnapshot activity =
                frame == null ? null : frame.featureActivity;
        GameplayElementBatchRenderers batches =
                GameplayElementBatchRenderers.getDefaultOrNull();
        if (batches != null) {
            batches.beginFrame();
            for (CanonicalCollectibleInstance collectible : collectibles.values()) {
                if (isActive(activity, collectible.spec.id)) {
                    batches.submit(collectible);
                }
            }
            for (CanonicalSpikeInstance spike : spikes.values()) {
                if (isActive(activity, spike.spec.id)) {
                    batches.submit(spike);
                }
            }
            batches.flush(vp);
        }
        for (CanonicalPortalVisual portal : portals.values()) {
            if (isActive(activity, portal.featureId())) {
                portal.draw(vp);
            }
        }
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        mesh.reloadGPUResourcesRecursivelyOnContextLoss();
        CanonicalPortalVisual.reloadSharedGpuAssets();
        GameplayElementBatchRenderers.reloadDefaultGPUResourcesOnContextLoss();
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        mesh.cleanupGPUResourcesRecursively();
    }

    private void initializeFeatureCaches() {
        spikes.clear();
        collectibles.clear();
        portals.clear();
        portalDigests.clear();
        featureIdsBySegment.clear();
        for (TerrainSegment segment : terrain.segments()) {
            upsertSegmentFeatures(segment);
        }
    }

    /**
     * Mirrors only feature records touched by a canonical commit.
     *
     * <p>The previous implementation rescanned every retained segment and allocated three
     * desired-ID hash sets after every terrain chunk. At 120 Hz this made an otherwise tiny
     * one-segment commit occasionally take several milliseconds.</p>
     */
    private void applyFeatureCommit(TerrainCommit commit) {
        Iterator<Map.Entry<Long, ArrayList<Long>>> retiring =
                featureIdsBySegment
                        .headMap(commit.retireBeforeSegmentId, false)
                        .entrySet()
                        .iterator();
        while (retiring.hasNext()) {
            Map.Entry<Long, ArrayList<Long>> entry = retiring.next();
            removeFeatures(entry.getValue());
            retiring.remove();
        }
        for (TerrainSegment segment : commit.segmentUpserts) {
            upsertSegmentFeatures(segment);
        }
    }

    private void upsertSegmentFeatures(TerrainSegment segment) {
        ArrayList<Long> previous = featureIdsBySegment.get(segment.id);
        ArrayList<Long> desired =
                new ArrayList<Long>(segment.features.size());
        for (TerrainFeatureSpec feature : segment.features) {
            desired.add(feature.id);
            upsertFeature(feature);
        }
        if (previous != null) {
            for (Long oldId : previous) {
                if (!desired.contains(oldId)) {
                    removeFeature(oldId.longValue());
                }
            }
        }
        if (desired.isEmpty()) {
            featureIdsBySegment.remove(segment.id);
        } else {
            featureIdsBySegment.put(segment.id, desired);
        }
    }

    private void upsertFeature(TerrainFeatureSpec feature) {
        if (feature instanceof TerrainFeatureSpec.Spike) {
            TerrainFeatureSpec.Spike spec =
                    (TerrainFeatureSpec.Spike) feature;
            collectibles.remove(spec.id);
            portals.remove(spec.id);
            portalDigests.remove(spec.id);
            CanonicalSpikeInstance existing = spikes.get(spec.id);
            if (existing == null
                    || existing.digest != spec.deterministicDigest()) {
                spikes.put(spec.id,
                        new CanonicalSpikeInstance(spec, renderOrigin));
            }
        } else if (feature
                instanceof TerrainFeatureSpec.AirJumpCollectible) {
            TerrainFeatureSpec.AirJumpCollectible spec =
                    (TerrainFeatureSpec.AirJumpCollectible) feature;
            spikes.remove(spec.id);
            portals.remove(spec.id);
            portalDigests.remove(spec.id);
            CanonicalCollectibleInstance existing =
                    collectibles.get(spec.id);
            if (existing == null
                    || existing.digest != spec.deterministicDigest()) {
                collectibles.put(spec.id,
                        new CanonicalCollectibleInstance(
                                spec, renderOrigin));
            }
        } else if (feature instanceof TerrainFeatureSpec.Portal) {
            TerrainFeatureSpec.Portal spec =
                    (TerrainFeatureSpec.Portal) feature;
            spikes.remove(spec.id);
            collectibles.remove(spec.id);
            long digest = spec.deterministicDigest();
            Long previousDigest = portalDigests.get(spec.id);
            if (previousDigest == null
                    || previousDigest.longValue() != digest) {
                portals.put(spec.id,
                        new CanonicalPortalVisual(spec, renderOrigin));
                portalDigests.put(spec.id, digest);
            }
        }
    }

    private void removeFeatures(List<Long> featureIds) {
        for (Long featureId : featureIds) {
            removeFeature(featureId.longValue());
        }
    }

    private void removeFeature(long featureId) {
        spikes.remove(featureId);
        collectibles.remove(featureId);
        portals.remove(featureId);
        portalDigests.remove(featureId);
    }

    private static boolean isActive(
            FeatureActivitySnapshot activity, long featureId) {
        return activity == null || activity.isActive(featureId);
    }

}
