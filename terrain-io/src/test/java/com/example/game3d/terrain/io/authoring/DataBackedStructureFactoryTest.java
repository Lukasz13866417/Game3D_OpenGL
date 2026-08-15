package com.example.game3d.terrain.io.authoring;

import com.example.game3d.authoring.MaterializedStructure;
import com.example.game3d.authoring.TerrainMaterializer;
import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.addon.Addon;
import com.example.game3d.terrain.io.model.AddonKind;
import com.example.game3d.terrain.io.model.AddonReservation;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.Placement;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;
import com.example.game3d.terrain.io.validation.TerrainValidator;
import com.example.game3d.terrain.io.validation.ValidationProblem;
import com.example.game3d.terrain.io.validation.ValidationResult;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DataBackedStructureFactoryTest {
    private static final String TILE_1 = "10000000-0000-0000-0000-000000000001";
    private static final String TILE_2 = "10000000-0000-0000-0000-000000000002";
    private static final String TILE_3 = "10000000-0000-0000-0000-000000000003";
    private static final String ADDON_1 = "20000000-0000-0000-0000-000000000001";
    private static final String ADDON_2 = "20000000-0000-0000-0000-000000000002";

    @Test
    public void gridRectangleSpansEveryRequestedRowAndColumnAndUsesFirstRowOwner() {
        StructureDocument document = structure(
                Arrays.asList(tile(TILE_1), tile(TILE_2), tile(TILE_3)),
                Collections.singletonList(spike(
                        ADDON_1, Placement.grid(1, 3, 2, 5))));
        assertTrue(new TerrainValidator().validate(document).isValid());

        MaterializedStructure materialized = TerrainMaterializer.materialize(
                DataBackedStructureFactory.create(document),
                TrackProfile.gameplayDefault(), Vec3.ZERO, 17L);

        TerrainSegment first = materialized.segments.get(0);
        TerrainSegment last = materialized.segments.get(2);
        assertEquals(1, first.addons.size());
        assertTrue(materialized.segments.get(1).addons.isEmpty());
        assertTrue(last.addons.isEmpty());
        Addon addon = first.addons.get(0);
        assertEquals(first.id, addon.ownerSegmentId());
        assertEquals(Long.valueOf(addon.id()), materialized.sourceAddonIds.get(ADDON_1));

        double acrossStart = 1.0 / 6.0;
        double acrossEnd = 5.0 / 6.0;
        assertVectorEquals(straightPoint(first, acrossStart, 0.25),
                addon.footprint().nearLeft);
        assertVectorEquals(straightPoint(first, acrossEnd, 0.25),
                addon.footprint().nearRight);
        assertVectorEquals(straightPoint(first, acrossStart, 2.75),
                addon.footprint().farLeft);
        assertVectorEquals(straightPoint(first, acrossEnd, 2.75),
                addon.footprint().farRight);
    }

    @Test
    public void advancedGridRectangleReservesAllCoveredCells() {
        StructureDocument document = structure(
                Arrays.asList(tile(TILE_1), tile(TILE_2), tile(TILE_3)),
                Arrays.asList(
                        spike(ADDON_1, Placement.grid(1, 2, 2, 3)),
                        spike(ADDON_2, Placement.grid(2, 3, 3, 4))));

        assertThrows(IllegalStateException.class, () -> TerrainMaterializer.materialize(
                DataBackedStructureFactory.create(document),
                TrackProfile.gameplayDefault(), Vec3.ZERO, 19L));
    }

    @Test
    public void validationDefersPhysicalRowBoundsButRejectsColumnsOutsideSessionProfile() {
        StructureDocument document = structure(
                Collections.singletonList(tile(TILE_1)),
                Arrays.asList(
                        spike(ADDON_1, Placement.grid(1, 2, 1, 1)),
                        spike(ADDON_2, Placement.grid(1, 1, 1, 7))));

        ValidationResult result = new TerrainValidator().validate(document);
        assertFalse(result.isValid());
        assertFalse(hasProblemAt(result, "$.addons[0].placement.rowEnd"));
        assertTrue(hasProblemAt(result, "$.addons[1].placement.columnEnd"));
    }

    @Test
    public void physicalGridRowsCanExceedTilesAndExactUpperBoundIsMaterialized() {
        java.util.List<TileRecord> fiveTiles = Arrays.asList(
                tile(TILE_1), tile(TILE_2), tile(TILE_3),
                tile("10000000-0000-0000-0000-000000000004"),
                tile("10000000-0000-0000-0000-000000000005"));
        StructureDocument geometryOnly = structure(fiveTiles, Collections.emptyList());
        assertEquals(7, TerrainMaterializer.derivePhysicalGridRowCount(
                DataBackedStructureFactory.create(geometryOnly),
                TrackProfile.gameplayDefault(), Vec3.ZERO, 29L));

        StructureDocument lastValidRow = structure(fiveTiles,
                Collections.singletonList(spike(
                        ADDON_1, Placement.grid(7, 7, 1, 1))));
        assertTrue(new TerrainValidator().validate(lastValidRow).isValid());
        MaterializedStructure valid = TerrainMaterializer.materialize(
                DataBackedStructureFactory.create(lastValidRow),
                TrackProfile.gameplayDefault(), Vec3.ZERO, 29L);
        assertEquals(1, valid.sourceAddonIds.size());

        StructureDocument firstInvalidRow = structure(fiveTiles,
                Collections.singletonList(spike(
                        ADDON_1, Placement.grid(8, 8, 1, 1))));
        assertTrue("static validation cannot infer a geometry-derived row count",
                new TerrainValidator().validate(firstInvalidRow).isValid());
        IllegalArgumentException invalid = assertThrows(
                IllegalArgumentException.class,
                () -> TerrainMaterializer.materialize(
                        DataBackedStructureFactory.create(firstInvalidRow),
                        TrackProfile.gameplayDefault(), Vec3.ZERO, 29L));
        assertEquals("Grid row range [8, 8] exceeds derived physical row count 7",
                invalid.getMessage());
    }

    @Test
    public void materializationRejectsRegionThatCrossesAGap() {
        StructureDocument document = structure(
                Arrays.asList(tile(TILE_1), new TileRecord(
                        TILE_2, false, 0.0, 0.0, 0.0,
                        "NORMAL", 1.0, 1.0)),
                Collections.singletonList(spike(
                        ADDON_1, Placement.grid(1, 2, 1, 2))));

        assertThrows(IllegalArgumentException.class, () -> TerrainMaterializer.materialize(
                DataBackedStructureFactory.create(document),
                TrackProfile.gameplayDefault(), Vec3.ZERO, 23L));
    }

    private static StructureDocument structure(
            java.util.List<TileRecord> tiles,
            java.util.List<AddonReservation> addons) {
        return new StructureDocument(
                1, "grid-region-test", GridMode.ADVANCED, tiles, addons);
    }

    private static TileRecord tile(String id) {
        return new TileRecord(
                id, true, 0.0, 0.0, 0.0, "NORMAL", 1.0, 1.0);
    }

    private static AddonReservation spike(String id, Placement placement) {
        return new AddonReservation(
                id, AddonKind.DEATH_SPIKE, placement, null,
                Collections.<String, Double>emptyMap());
    }

    private static Vec3 straightPoint(
            TerrainSegment first, double across, double distance) {
        return Vec3.lerp(first.nearLeft, first.nearRight, across)
                .add(new Vec3(0.0, 0.0, -distance));
    }

    private static void assertVectorEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0e-12);
        assertEquals(expected.y, actual.y, 1.0e-12);
        assertEquals(expected.z, actual.z, 1.0e-12);
    }

    private static boolean hasProblemAt(ValidationResult result, String path) {
        for (ValidationProblem problem : result.problems()) {
            if (path.equals(problem.path())) return true;
        }
        return false;
    }
}
