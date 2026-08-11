package com.example.game3d.core.terrain;

import com.example.game3d.core.math.Aabb;
import com.example.game3d.core.math.Vec3;

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
        TerrainFeature.Spike later = new TerrainFeature.Spike(
                9L, new Vec3(0.0, 0.0, 0.0), 6.0, 1.0);
        TerrainFeature.Feather earlier = new TerrainFeature.Feather(
                3L, new Vec3(1.0, 1.0, 1.0), 5.0);
        TerrainWorld world = new TerrainWorld(Arrays.asList(
                featurePatch(2L, later), featurePatch(1L, earlier)));

        List<TerrainFeature> result = world.queryFeatures(new Aabb(
                new Vec3(-10.0, -2.0, -10.0),
                new Vec3(10.0, 4.0, 10.0)));

        assertEquals(2, result.size());
        assertEquals(3L, result.get(0).id);
        assertEquals(9L, result.get(1).id);
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
        TerrainFeature.Spike later = new TerrainFeature.Spike(
                9L, new Vec3(0.0, 0.0, 0.0), 6.0, 1.0);
        TerrainFeature.Feather earlier = new TerrainFeature.Feather(
                3L, new Vec3(1.0, 1.0, 1.0), 5.0);
        TerrainWorld world = new TerrainWorld(Arrays.asList(
                featurePatch(2L, later), featurePatch(1L, earlier)));
        ArrayList<TerrainFeature> scratch = new ArrayList<TerrainFeature>();
        scratch.add(new TerrainFeature.Feather(
                999L, new Vec3(100.0, 100.0, 100.0), 0.1));

        world.queryFeatures(new Aabb(
                new Vec3(-10.0, -2.0, -10.0),
                new Vec3(10.0, 4.0, 10.0)), scratch);

        assertEquals(2, scratch.size());
        assertEquals(3L, scratch.get(0).id);
        assertEquals(9L, scratch.get(1).id);

        world.queryFeatures(Aabb.around(
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
                featurePatch(1L, new TerrainFeature.Feather(
                        4L, new Vec3(0.0, 1.0, 0.0), 0.2)),
                featurePatch(2L, new TerrainFeature.Spike(
                        4L, new Vec3(2.0, 0.0, 0.0), 0.4, 1.0))));
    }

    @Test
    public void deterministicDigestIncludesFeatureShapeAndIsPatchOrderIndependent() {
        TerrainPatch trianglePatch = patch(1L, triangle(1L, 0.0));
        TerrainPatch smallSpike = featurePatch(2L, new TerrainFeature.Spike(
                2L, new Vec3(0.0, 0.0, -2.0), 0.2, 1.0));
        TerrainPatch largeSpike = featurePatch(2L, new TerrainFeature.Spike(
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
                Collections.<TerrainFeature>emptyList());
    }

    private static TerrainPatch featurePatch(long patchId, TerrainFeature feature) {
        return new TerrainPatch(patchId, Collections.<TerrainTriangle>emptyList(),
                Collections.singletonList(feature));
    }
}
