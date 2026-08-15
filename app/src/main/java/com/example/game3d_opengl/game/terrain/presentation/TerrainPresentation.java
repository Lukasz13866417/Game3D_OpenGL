package com.example.game3d_opengl.game.terrain.presentation;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.simulation.AddonActivitySnapshot;
import com.example.game3d.core.simulation.SimulationFrameSnapshot;
import com.example.game3d.core.terrain.TerrainCommit;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.core.terrain.TerrainState;
import com.example.game3d.core.terrain.addon.Addon;
import com.example.game3d.core.terrain.addon.DeathSpike;
import com.example.game3d.core.terrain.addon.Portal;
import com.example.game3d.core.terrain.addon.Potion;
import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.game.terrain.track_elements.GameplayElementBatchRenderers;
import com.example.game3d_opengl.game.terrain.track_elements.portal.PortalRenderer;
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
public final class TerrainPresentation implements GPUResourceOwner {
    private final TerrainState terrain;
    private final TerrainRendererRegistry rendererRegistry;
    private final CanonicalTerrainMeshRenderer mesh;
    private final TreeMap<Long, DeathSpikeRenderer> spikes =
            new TreeMap<Long, DeathSpikeRenderer>();
    private final TreeMap<Long, PotionRenderer> potions =
            new TreeMap<Long, PotionRenderer>();
    private final TreeMap<Long, PortalRenderer> portals =
            new TreeMap<Long, PortalRenderer>();
    private final TreeMap<Long, Long> portalDigests =
            new TreeMap<Long, Long>();
    private final TreeMap<Long, ArrayList<Long>> addonIdsBySegment =
            new TreeMap<Long, ArrayList<Long>>();
    private Vec3 renderOrigin = Vec3.ZERO;

    public TerrainPresentation(
            TerrainSnapshot initialTerrain,
            TerrainRendererRegistry rendererRegistry) {
        if (initialTerrain == null || rendererRegistry == null) {
            throw new IllegalArgumentException("initialTerrain/rendererRegistry cannot be null");
        }
        this.rendererRegistry = rendererRegistry;
        mesh = new CanonicalTerrainMeshRenderer(
                rendererRegistry.terrainShaderOrNull());
        terrain = new TerrainState(initialTerrain);
        mesh.rebuild(terrain.segments(), renderOrigin);
        initializeAddonCaches();
    }

    public void applyTerrainCommit(TerrainCommit commit) {
        terrain.apply(commit);
        mesh.applyCommit(commit);
        applyAddonCommit(commit);
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
            applyAddonCommit(commit);
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

    public int visibleAddonCount() {
        return spikes.size() + potions.size() + portals.size();
    }

    public void setRenderOrigin(Vec3 nextOrigin) {
        Vec3 safe = nextOrigin == null ? Vec3.ZERO : nextOrigin;
        if (renderOrigin.equals(safe)) {
            return;
        }
        renderOrigin = safe;
        mesh.setRenderOrigin(renderOrigin);
        for (DeathSpikeRenderer spike : spikes.values()) {
            spike.setRenderOrigin(renderOrigin);
        }
        for (PotionRenderer potion : potions.values()) {
            potion.setRenderOrigin(renderOrigin);
        }
        for (PortalRenderer portal : portals.values()) {
            portal.setRenderOrigin(renderOrigin);
        }
    }

    public void updateBeforeDraw(
            float dtMillis, SimulationFrameSnapshot frame) {
        AddonActivitySnapshot activity =
                frame == null ? null : frame.addonActivity;
        updateAddonAnimations(dtMillis, activity);
    }

    void updateAddonAnimations(
            float dtMillis, AddonActivitySnapshot activity) {
        for (PotionRenderer potion : potions.values()) {
            if (isActive(activity, potion.addon.id())) {
                potion.update(dtMillis);
            }
        }
        for (PortalRenderer portal : portals.values()) {
            if (isActive(activity, portal.addonId())) {
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
        AddonActivitySnapshot activity =
                frame == null ? null : frame.addonActivity;
        GameplayElementBatchRenderers batches =
                rendererRegistry.batchRenderersOrNull();
        if (batches != null) {
            batches.beginFrame();
            for (PotionRenderer potion : potions.values()) {
                if (isActive(activity, potion.addon.id())) {
                    batches.submit(potion);
                }
            }
            for (DeathSpikeRenderer spike : spikes.values()) {
                if (isActive(activity, spike.addon.id())) {
                    batches.submit(spike);
                }
            }
            batches.flush(vp);
        }
        for (PortalRenderer portal : portals.values()) {
            if (isActive(activity, portal.addonId())) {
                portal.draw(vp);
            }
        }
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        mesh.reloadGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        mesh.cleanupGPUResourcesRecursively();
    }

    private void initializeAddonCaches() {
        spikes.clear();
        potions.clear();
        portals.clear();
        portalDigests.clear();
        addonIdsBySegment.clear();
        for (TerrainSegment segment : terrain.segments()) {
            upsertSegmentAddons(segment);
        }
    }

    /**
     * Mirrors only addon records touched by a canonical commit.
     *
     * <p>The previous implementation rescanned every retained segment and allocated three
     * desired-ID hash sets after every terrain chunk. At 120 Hz this made an otherwise tiny
     * one-segment commit occasionally take several milliseconds.</p>
     */
    private void applyAddonCommit(TerrainCommit commit) {
        Iterator<Map.Entry<Long, ArrayList<Long>>> retiring =
                addonIdsBySegment
                        .headMap(commit.retireBeforeSegmentId, false)
                        .entrySet()
                        .iterator();
        while (retiring.hasNext()) {
            Map.Entry<Long, ArrayList<Long>> entry = retiring.next();
            removeAddons(entry.getValue());
            retiring.remove();
        }
        for (TerrainSegment segment : commit.segmentUpserts) {
            upsertSegmentAddons(segment);
        }
    }

    private void upsertSegmentAddons(TerrainSegment segment) {
        ArrayList<Long> previous = addonIdsBySegment.get(segment.id);
        ArrayList<Long> desired =
                new ArrayList<Long>(segment.addons.size());
        for (Addon addon : segment.addons) {
            desired.add(addon.id());
            upsertAddon(addon);
        }
        if (previous != null) {
            for (Long oldId : previous) {
                if (!desired.contains(oldId)) {
                    removeAddon(oldId.longValue());
                }
            }
        }
        if (desired.isEmpty()) {
            addonIdsBySegment.remove(segment.id);
        } else {
            addonIdsBySegment.put(segment.id, desired);
        }
    }

    private void upsertAddon(Addon addon) {
        if (addon instanceof DeathSpike) {
            DeathSpike spike = (DeathSpike) addon;
            potions.remove(spike.id());
            portals.remove(spike.id());
            portalDigests.remove(spike.id());
            DeathSpikeRenderer existing = spikes.get(spike.id());
            if (existing == null
                    || existing.digest != spike.deterministicDigest()) {
                spikes.put(spike.id(),
                        new DeathSpikeRenderer(spike, renderOrigin));
            }
        } else if (addon instanceof Potion) {
            Potion potion = (Potion) addon;
            spikes.remove(potion.id());
            portals.remove(potion.id());
            portalDigests.remove(potion.id());
            PotionRenderer existing = potions.get(potion.id());
            if (existing == null
                    || existing.digest != potion.deterministicDigest()) {
                potions.put(potion.id(),
                        new PotionRenderer(potion, renderOrigin));
            }
        } else if (addon instanceof Portal) {
            Portal portal = (Portal) addon;
            spikes.remove(portal.id());
            potions.remove(portal.id());
            long digest = portal.deterministicDigest();
            Long previousDigest = portalDigests.get(portal.id());
            if (previousDigest == null
                    || previousDigest.longValue() != digest) {
                portals.put(portal.id(),
                        new PortalRenderer(portal, renderOrigin,
                                rendererRegistry.requirePortalResources()));
                portalDigests.put(portal.id(), digest);
            }
        }
    }

    private void removeAddons(List<Long> addonIds) {
        for (Long addonId : addonIds) {
            removeAddon(addonId.longValue());
        }
    }

    private void removeAddon(long addonId) {
        spikes.remove(addonId);
        potions.remove(addonId);
        portals.remove(addonId);
        portalDigests.remove(addonId);
    }

    private static boolean isActive(
            AddonActivitySnapshot activity, long addonId) {
        return activity == null || activity.isActive(addonId);
    }

    public int potionBatchDrawCalls() {
        GameplayElementBatchRenderers batches = rendererRegistry.batchRenderersOrNull();
        return batches == null ? 0 : batches.getPotionBatchDrawCalls();
    }

    public int potionBatchInstanceCount() {
        GameplayElementBatchRenderers batches = rendererRegistry.batchRenderersOrNull();
        return batches == null ? 0 : batches.getPotionBatchInstanceCount();
    }

    public int spikeBatchDrawCalls() {
        GameplayElementBatchRenderers batches = rendererRegistry.batchRenderersOrNull();
        return batches == null ? 0 : batches.getSpikeBatchDrawCalls();
    }

    public int spikeBatchInstanceCount() {
        GameplayElementBatchRenderers batches = rendererRegistry.batchRenderersOrNull();
        return batches == null ? 0 : batches.getSpikeBatchInstanceCount();
    }

    PotionRenderer cachedPotionRenderer(long addonId) {
        return potions.get(addonId);
    }

}
