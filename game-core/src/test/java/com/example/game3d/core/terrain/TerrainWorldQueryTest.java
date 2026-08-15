package com.example.game3d.core.terrain;

import com.example.game3d.core.math.Aabb;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.addon.Addon;
import com.example.game3d.core.terrain.addon.AddonFootprint;
import com.example.game3d.core.terrain.addon.DeathSpike;
import com.example.game3d.core.terrain.addon.Potion;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TerrainWorldQueryTest {
    @Test
    public void spatialQueryReturnsLargeTriangleOnlyOnceAndSortsByStableId() {
        TerrainTriangle laterId = triangle(20L, -20.0);
        TerrainTriangle earlierId = triangle(10L, 0.0);
        TerrainWorld world = new TerrainWorld(Arrays.asList(
                patch(2L, laterId), patch(1L, earlierId)));

        List<TerrainTriangle> result = world.queryTriangles(new Aabb(
                new Vec3(-5.0, -1.0, -30.0),
                new Vec3(5.0, 1.0, 5.0)));

        assertEquals(2, result.size());
        assertEquals(10L, result.get(0).id);
        assertEquals(20L, result.get(1).id);
    }

    @Test
    public void featureQueryDeduplicatesAcrossGridCellsAndSortsById() {
        Addon later = spike(
                9L, new Vec3(0.0, 0.0, 0.0), 6.0, 1.0);
        Addon earlier = potion(
                3L, new Vec3(1.0, 1.0, 1.0), 5.0);
        TerrainWorld world = new TerrainWorld(Arrays.asList(
                addonPatch(2L, later), addonPatch(1L, earlier)));

        List<Addon> result = world.queryAddons(new Aabb(
                new Vec3(-10.0, -2.0, -10.0),
                new Vec3(10.0, 4.0, 10.0)));

        assertEquals(2, result.size());
        assertEquals(3L, result.get(0).id());
        assertEquals(9L, result.get(1).id());
    }

    @Test
    public void callerOwnedTriangleScratchIsClearedDeduplicatedAndReusable() {
        TerrainTriangle laterId = triangle(20L, -20.0);
        TerrainTriangle earlierId = triangle(10L, 0.0);
        TerrainWorld world = new TerrainWorld(Arrays.asList(
                patch(2L, laterId), patch(1L, earlierId)));
        ArrayList<TerrainTriangle> scratch = new ArrayList<TerrainTriangle>();
        scratch.add(triangle(999L, 100.0));

        world.queryTriangles(new Aabb(
                new Vec3(-5.0, -1.0, -30.0),
                new Vec3(5.0, 1.0, 5.0)), scratch);

        assertEquals(2, scratch.size());
        assertEquals(10L, scratch.get(0).id);
        assertEquals(20L, scratch.get(1).id);

        world.queryTriangles(Aabb.around(
                new Vec3(1000.0, 1000.0, 1000.0), 1.0, 1.0, 1.0), scratch);
        assertEquals("a later empty query must not expose stale candidates",
                0, scratch.size());
    }

    @Test
    public void callerOwnedFeatureScratchIsClearedDeduplicatedAndReusable() {
        Addon later = spike(
                9L, new Vec3(0.0, 0.0, 0.0), 6.0, 1.0);
        Addon earlier = potion(
                3L, new Vec3(1.0, 1.0, 1.0), 5.0);
        TerrainWorld world = new TerrainWorld(Arrays.asList(
                addonPatch(2L, later), addonPatch(1L, earlier)));
        ArrayList<Addon> scratch = new ArrayList<Addon>();
        scratch.add(potion(
                999L, new Vec3(100.0, 100.0, 100.0), 0.1));

        world.queryAddons(new Aabb(
                new Vec3(-10.0, -2.0, -10.0),
                new Vec3(10.0, 4.0, 10.0)), scratch);

        assertEquals(2, scratch.size());
        assertEquals(3L, scratch.get(0).id());
        assertEquals(9L, scratch.get(1).id());

        world.queryAddons(Aabb.around(
                new Vec3(1000.0, 1000.0, 1000.0), 1.0, 1.0, 1.0), scratch);
        assertEquals("a later empty query must not expose stale candidates",
                0, scratch.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateTriangleIdsAreRejected() {
        new TerrainWorld(Arrays.asList(
                patch(1L, triangle(7L, 0.0)),
                patch(2L, triangle(7L, -4.0))));
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateFeatureIdsAreRejected() {
        new TerrainWorld(Arrays.asList(
                addonPatch(1L, potion(
                        4L, new Vec3(0.0, 1.0, 0.0), 0.2)),
                addonPatch(2L, spike(
                        4L, new Vec3(2.0, 0.0, 0.0), 0.4, 1.0))));
    }

    @Test
    public void deterministicDigestIncludesFeatureShapeAndIsPatchOrderIndependent() {
        TerrainPatch trianglePatch = patch(1L, triangle(1L, 0.0));
        TerrainPatch smallSpike = addonPatch(2L, spike(
                2L, new Vec3(0.0, 0.0, -2.0), 0.2, 1.0));
        TerrainPatch largeSpike = addonPatch(2L, spike(
                2L, new Vec3(0.0, 0.0, -2.0), 0.8, 2.0));

        TerrainWorld ordered = new TerrainWorld(Arrays.asList(trianglePatch, smallSpike));
        TerrainWorld reversed = new TerrainWorld(Arrays.asList(smallSpike, trianglePatch));
        TerrainWorld changedShape = new TerrainWorld(Arrays.asList(trianglePatch, largeSpike));

        assertEquals(ordered.deterministicDigest(), reversed.deterministicDigest());
        assertNotEquals(ordered.deterministicDigest(), changedShape.deterministicDigest());
    }

    private static TerrainTriangle triangle(long id, double zOffset) {
        return new TerrainTriangle(id,
                new Vec3(-8.0, 0.0, zOffset + 2.0),
                new Vec3(8.0, 0.0, zOffset + 2.0),
                new Vec3(0.0, 0.0, zOffset - 10.0),
                SurfaceMaterial.NORMAL);
    }

    private static TerrainPatch patch(long patchId, TerrainTriangle triangle) {
        return new TerrainPatch(patchId, Collections.singletonList(triangle),
                Collections.<Addon>emptyList());
    }

    private static TerrainPatch addonPatch(long patchId, Addon addon) {
        return new TerrainPatch(patchId, Collections.<TerrainTriangle>emptyList(),
                Collections.singletonList(addon));
    }

    private static Addon potion(long id, Vec3 center, double triggerRadius) {
        Potion potion = new Potion(center, triggerRadius, "TEST");
        potion.place(id, 0L, AddonFootprint.around(
                center, triggerRadius, triggerRadius, triggerRadius));
        return potion;
    }

    private static Addon spike(long id, Vec3 center, double radius, double height) {
        Vec3 nearLeft = new Vec3(center.x - radius, center.y, center.z + radius);
        Vec3 nearRight = new Vec3(center.x + radius, center.y, center.z + radius);
        Vec3 farLeft = new Vec3(center.x - radius, center.y, center.z - radius);
        Vec3 farRight = new Vec3(center.x + radius, center.y, center.z - radius);
        DeathSpike spike = new DeathSpike(
                nearLeft, nearRight, farLeft, farRight,
                center.add(Vec3.UP.multiply(height)), Vec3.UP, 0.0,
                center, radius, height);
        spike.place(id, 0L, AddonFootprint.quadrilateral(
                nearLeft, nearRight, farLeft, farRight));
        return spike;
    }
}
