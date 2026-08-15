package com.example.game3d_opengl.game.terrain.presentation;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.simulation.AddonActivitySnapshot;
import com.example.game3d.core.terrain.TerrainCommit;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.core.terrain.TrackBuilder;
import com.example.game3d.core.terrain.addon.AddonFootprint;
import com.example.game3d.core.terrain.addon.DeathSpike;
import com.example.game3d.core.terrain.addon.Potion;
import com.example.game3d_opengl.game.terrain.track_elements.spike.SpikeInfillDrawArgs;
import com.example.game3d_opengl.game.terrain.track_elements.potion.PotionRenderResources;
import com.example.game3d_opengl.rendering.util3d.FColor;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TerrainPresentationTest {
    @Test
    public void appliesTheSameAppendAndRetirementRevisionsAsSimulation() {
        TerrainSnapshot complete = new TrackBuilder(6.0)
                .straight(10.0)
                .feather(0.0, 0.0, 0.25, 0.22)
                .straight(10.0)
                .buildSnapshot();
        TerrainSegment first = complete.segments.get(0);
        TerrainSegment second = complete.segments.get(1);
        TerrainPresentation presentation =
                new TerrainPresentation(new TerrainSnapshot(
                        0L, 0L, 0L, Collections.singletonList(first)),
                        new TerrainRendererRegistry());

        assertEquals(0L, presentation.terrainRevision());
        assertEquals(1, presentation.visibleSegmentCount());
        assertEquals(1, presentation.visibleAddonCount());

        presentation.applyTerrainCommit(new TerrainCommit(
                0L, 1L, 1L, 0L, Collections.singletonList(second)));

        assertEquals(1L, presentation.terrainRevision());
        assertEquals(2, presentation.visibleSegmentCount());
        assertEquals(1, presentation.visibleAddonCount());

        presentation.applyTerrainCommit(new TerrainCommit(
                1L, 2L, 1L, 1L,
                Collections.<TerrainSegment>emptyList()));

        assertEquals(2L, presentation.terrainRevision());
        assertEquals(1, presentation.visibleSegmentCount());
        assertEquals(0, presentation.visibleAddonCount());
    }

    @Test
    public void appliesCommitBurstInOrderAndExposesFinalRevision() {
        TerrainSnapshot complete = new TrackBuilder(6.0)
                .straight(4.0)
                .straight(4.0)
                .straight(4.0)
                .buildSnapshot();
        TerrainPresentation presentation =
                new TerrainPresentation(new TerrainSnapshot(
                        0L, 0L, 0L,
                        Collections.singletonList(complete.segments.get(0))),
                        new TerrainRendererRegistry());

        presentation.applyTerrainCommits(Arrays.asList(
                new TerrainCommit(
                        0L, 1L, 1L, 0L,
                        Collections.singletonList(complete.segments.get(1))),
                new TerrainCommit(
                        1L, 2L, 2L, 0L,
                        Collections.singletonList(complete.segments.get(2)))));

        assertEquals(2L, presentation.terrainRevision());
        assertEquals(3, presentation.visibleSegmentCount());
    }

    @Test
    public void potionRendererAppliesFloatingOriginAndKeepsAnimationLocal() {
        Potion potion = new Potion(new Vec3(12.0, 5.0, -7.0), 0.3, "FEATHER");
        potion.place(1L, 0L, AddonFootprint.around(
                potion.center, potion.triggerRadius, potion.triggerRadius,
                potion.triggerRadius));
        PotionRenderer renderer = new PotionRenderer(
                potion, new Vec3(10.0, 2.0, -10.0));

        float[] model = new float[16];
        renderer.writePotionModelMatrix(model);
        assertEquals(2.0f, model[12], 0.0f);
        assertEquals(3.0f, model[13], 0.0f);
        assertEquals(3.0f, model[14], 0.0f);

        renderer.update(625.0f);
        renderer.writePotionModelMatrix(model);
        assertEquals((float) Math.cos(Math.toRadians(100.0)), model[0], 1.0e-6f);
        assertEquals((float) -Math.sin(Math.toRadians(100.0)), model[2], 1.0e-6f);
        assertEquals(2.0f, model[12], 0.0f);

        renderer.setRenderOrigin(new Vec3(11.0, 4.0, -8.0));
        renderer.writePotionModelMatrix(model);
        assertEquals(1.0f, model[12], 0.0f);
        assertEquals(1.0f, model[13], 0.0f);
        assertEquals(1.0f, model[14], 0.0f);
    }

    @Test
    public void potionVisualStyleReachesTheBatchColorPayload() {
        Potion canonical = new Potion(Vec3.ZERO, 0.3, "POTION_FEATHER");
        canonical.place(1L, 0L, AddonFootprint.around(
                canonical.center, 0.3, 0.3, 0.3));
        Potion custom = new Potion(Vec3.ZERO, 0.3, "CUSTOM_STYLE");
        custom.place(2L, 0L, AddonFootprint.around(
                custom.center, 0.3, 0.3, 0.3));

        FColor canonicalColor = new PotionRenderer(canonical, Vec3.ZERO)
                .potionFillColor();
        FColor customColor = new PotionRenderer(custom, Vec3.ZERO)
                .potionFillColor();

        assertSame(PotionRenderResources.FILL_COLOR, canonicalColor);
        assertTrue(canonicalColor.r() != customColor.r()
                || canonicalColor.g() != customColor.g()
                || canonicalColor.b() != customColor.b());
    }

    @Test
    public void spikeRendererBuildsBatchPayloadFromCoreAddonCoordinates() {
        Vec3 nearLeft = new Vec3(4.0, 1.0, 6.0);
        Vec3 nearRight = new Vec3(6.0, 1.0, 6.0);
        Vec3 farLeft = new Vec3(4.0, 1.0, 4.0);
        Vec3 farRight = new Vec3(6.0, 1.0, 4.0);
        DeathSpike spike = new DeathSpike(
                nearLeft, nearRight, farLeft, farRight,
                new Vec3(5.0, 3.0, 5.0), Vec3.UP, 0.15,
                new Vec3(5.0, 1.0, 5.0), 1.0, 2.0);
        spike.place(1L, 0L, AddonFootprint.quadrilateral(
                nearLeft, nearRight, farLeft, farRight));
        DeathSpikeRenderer renderer = new DeathSpikeRenderer(
                spike, new Vec3(1.0, 0.5, 2.0));

        SpikeInfillDrawArgs args = renderer.spikeBatchArgs();
        assertEquals(3.0f, args.uNL[0], 0.0f);
        assertEquals(0.5f, args.uNL[1], 0.0f);
        assertEquals(4.0f, args.uNL[2], 0.0f);
        assertEquals(4.0f, args.uApex[0], 0.0f);
        assertEquals(2.5f, args.uApex[1], 0.0f);
        assertEquals(3.0f, args.uApex[2], 0.0f);
        assertEquals(1.0f, args.uNormal[1], 0.0f);
        assertEquals(0.15f, args.uBaseOffset, 0.0f);
    }

    @Test
    public void sameIdCacheEntryRefreshesOnlyWhenAddonDigestChanges() {
        TerrainSnapshot initial = new TrackBuilder(6.0)
                .straight(10.0)
                .feather(0.0, 0.0, 0.25, 0.22)
                .buildSnapshot();
        TerrainSegment oldSegment = initial.segments.get(0);
        Potion oldPotion = (Potion) oldSegment.addons.get(0);
        TerrainPresentation presentation = new TerrainPresentation(
                initial, new TerrainRendererRegistry());
        PotionRenderer first = presentation.cachedPotionRenderer(oldPotion.id());

        presentation.applyTerrainCommit(new TerrainCommit(
                0L, 1L, 0L, 0L, Collections.singletonList(oldSegment)));
        assertSame(first, presentation.cachedPotionRenderer(oldPotion.id()));

        Potion replacement = new Potion(
                oldPotion.center.add(new Vec3(0.5, 0.0, 0.0)),
                oldPotion.triggerRadius, oldPotion.visualStyleId);
        replacement.place(oldPotion.id(), oldSegment.id,
                AddonFootprint.around(replacement.center,
                        replacement.triggerRadius, replacement.triggerRadius,
                        replacement.triggerRadius));
        TerrainSegment changed = withAddon(oldSegment, replacement);
        presentation.applyTerrainCommit(new TerrainCommit(
                1L, 2L, 0L, 0L, Collections.singletonList(changed)));

        assertNotSame(first, presentation.cachedPotionRenderer(oldPotion.id()));
        assertEquals(replacement.deterministicDigest(),
                presentation.cachedPotionRenderer(oldPotion.id()).digest);
    }

    @Test
    public void inactivePotionDoesNotAdvanceRendererAnimation() {
        TerrainSnapshot terrain = new TrackBuilder(6.0)
                .straight(10.0)
                .feather(0.0, 0.0, 0.25, 0.22)
                .buildSnapshot();
        Potion potion = (Potion) terrain.segments.get(0).addons.get(0);
        TerrainPresentation presentation = new TerrainPresentation(
                terrain, new TerrainRendererRegistry());
        PotionRenderer renderer = presentation.cachedPotionRenderer(potion.id());
        float[] model = new float[16];

        presentation.updateAddonAnimations(625f,
                AddonActivitySnapshot.ofInactiveAddonIds(
                        Collections.singletonList(potion.id())));
        renderer.writePotionModelMatrix(model);
        assertEquals(1.0f, model[0], 0.0f);

        presentation.updateAddonAnimations(625f,
                AddonActivitySnapshot.ofInactiveAddonIds(Collections.<Long>emptyList()));
        renderer.writePotionModelMatrix(model);
        assertEquals((float) Math.cos(Math.toRadians(100.0)), model[0], 1.0e-6f);
    }

    private static TerrainSegment withAddon(
            TerrainSegment source, Potion potion) {
        return new TerrainSegment(
                source.id,
                source.nearLeft, source.nearRight,
                source.farLeft, source.farRight,
                source.solid, source.connectedToPrevious,
                source.surface,
                source.nearLeftAppearance, source.nearRightAppearance,
                source.farLeftAppearance, source.farRightAppearance,
                Collections.singletonList(potion));
    }
}
