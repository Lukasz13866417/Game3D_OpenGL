package com.example.game3d.authoring;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.addon.Addon;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Contract tests for geometry-derived, distance-spaced addon GRID rows. */
public final class PhysicalGridRowsTest {
    private static final TrackProfile PROFILE = TrackProfile.gameplayDefault();

    @Test
    public void rowsCarryAcrossConnectedSegmentsAndTerminalPartialCellIsNotEmitted() {
        MaterializedStructure result = TerrainMaterializer.materialize(
                fiveStraightWithLastRowAddon(), PROFILE, Vec3.ZERO, 1L);

        // 5 * 1.4m = 7m, so the exact terminal boundary closes the seventh full cell.
        assertEquals(7, result.physicalGridRowCount);
        Addon addon = addon(result, "last-row");
        assertEquals(4L, addon.ownerSegmentId());
        assertTrue(addon.footprint().nearLeft
                .subtract(addon.footprint().farLeft).length() > 0.0);
    }

    @Test
    public void turnAndSlopeRowsUseCompletedCanonicalEdges() {
        MaterializedStructure result = TerrainMaterializer.materialize(
                new AdvancedTerrainStructure(3) {
                    @Override protected void generateTiles(Terrain.TileBrush brush) {
                        brush.addSegment("a");
                        brush.addHorizontalAng(Math.PI / 5.0);
                        brush.setVerticalAng(Math.PI / 9.0);
                        brush.addSegment("b");
                        brush.addSegment("c");
                    }

                    @Override protected void generateAddons(
                            Terrain.AdvancedGridBrush brush, int rows, int columns) {
                        brush.placeGridRegion(rows, rows, 1, columns,
                                AddonBlueprint.airJumpPotion("curved-row"));
                    }
                }, PROFILE, Vec3.ZERO, 2L);

        Addon addon = addon(result, "curved-row");
        TerrainSegment owner = result.segments.get((int) addon.ownerSegmentId());
        assertTrue(addon.footprint().nearLeft.subtract(owner.nearLeft).length()
                <= owner.farLeft.subtract(owner.nearLeft).length());
        assertNotEquals(0.0, addon.footprint().nearLeft.y, 1.0e-12);
        assertNotEquals(addon.footprint().nearLeft.z,
                addon.footprint().nearRight.z, 1.0e-12);
    }

    @Test
    public void gapAndLiftResetUnfinishedCarry() {
        MaterializedStructure result = TerrainMaterializer.materialize(
                new AdvancedTerrainStructure(5) {
                    @Override protected void generateTiles(Terrain.TileBrush brush) {
                        brush.addSegment("one");
                        brush.addEmptySegment("gap");
                        brush.addSegment("after-gap");
                        brush.liftUp(1.0);
                        brush.addSegment("lifted");
                        brush.addSegment("after-lift");
                    }

                    @Override protected void generateAddons(
                            Terrain.AdvancedGridBrush brush, int rows, int columns) {
                        assertEquals(4, rows);
                        brush.reserveVertical(1, 1, rows, potions("reset", rows));
                    }
                }, PROFILE, Vec3.ZERO, 3L);

        assertEquals(4, result.physicalGridRowCount);
        assertEquals(0L, addon(result, "reset-0").ownerSegmentId());
        assertEquals(2L, addon(result, "reset-1").ownerSegmentId());
        assertEquals(3L, addon(result, "reset-2").ownerSegmentId());
        assertEquals(4L, addon(result, "reset-3").ownerSegmentId());
    }

    @Test
    public void sequentialChildrenUseTheirOwnLocalPhysicalRows() {
        final AdvancedTerrainStructure first = child("first");
        final AdvancedTerrainStructure second = child("second");
        MaterializedStructure result = TerrainMaterializer.materialize(
                new AdvancedTerrainStructure(4) {
                    @Override protected void generateTiles(Terrain.TileBrush brush) {
                        addChild(first, brush);
                        addChild(second, brush);
                    }

                    @Override protected void generateAddons(
                            Terrain.AdvancedGridBrush brush, int rows, int columns) {
                    }
                }, PROFILE, Vec3.ZERO, 4L);

        Addon a = addon(result, "first-addon");
        Addon b = addon(result, "second-addon");
        assertTrue(a.ownerSegmentId() < b.ownerSegmentId());
        assertTrue(center(a).z > center(b).z);
    }

    @Test
    public void publicCountAndOutOfRangeDiagnosticUsePhysicalRows() {
        assertEquals(7, TerrainMaterializer.derivePhysicalGridRowCount(
                straight(5), PROFILE, Vec3.ZERO, 5L));
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TerrainMaterializer.materialize(
                        new AdvancedTerrainStructure(5) {
                            @Override protected void generateTiles(Terrain.TileBrush brush) {
                                for (int i = 0; i < 5; i++) brush.addSegment("o-" + i);
                            }

                            @Override protected void generateAddons(
                                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                                brush.placeGridRegion(8, 8, 1, 1,
                                        AddonBlueprint.airJumpPotion("too-far"));
                            }
                        }, PROFILE, Vec3.ZERO, 5L));
        assertTrue(error.getMessage().contains(
                "Grid row range [8, 8] exceeds derived physical row count 7"));
    }

    @Test
    public void pathologicalRowSpacingIsRejectedBeforeAllocation() {
        TrackProfile tiny = new TrackProfile("tiny", 3.2, 1.4, 6, 1.0e-300);
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TerrainMaterializer.materialize(
                        straight(1), tiny, Vec3.ZERO, 6L));
        assertTrue(error.getMessage().contains("physical grid row limit"));
    }

    private static AdvancedTerrainStructure fiveStraightWithLastRowAddon() {
        return new AdvancedTerrainStructure(5) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                for (int i = 0; i < 5; i++) brush.addSegment("s-" + i);
            }

            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                assertEquals(7, rows);
                brush.placeGridRegion(rows, rows, 1, columns,
                        AddonBlueprint.airJumpPotion("last-row"));
            }
        };
    }

    private static AdvancedTerrainStructure child(final String prefix) {
        return new AdvancedTerrainStructure(2) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                brush.addSegment(prefix + "-0");
                brush.addSegment(prefix + "-1");
            }

            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                brush.placeGridRegion(1, 1, 1, 1,
                        AddonBlueprint.airJumpPotion(prefix + "-addon"));
            }
        };
    }

    private static AdvancedTerrainStructure straight(final int count) {
        return new AdvancedTerrainStructure(count) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                for (int i = 0; i < count; i++) brush.addSegment("q-" + i);
            }

            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
            }
        };
    }

    private static AddonBlueprint[] potions(String prefix, int count) {
        AddonBlueprint[] result = new AddonBlueprint[count];
        for (int i = 0; i < count; i++) {
            result[i] = AddonBlueprint.airJumpPotion(prefix + "-" + i);
        }
        return result;
    }

    private static Addon addon(MaterializedStructure result, String sourceId) {
        long id = result.sourceAddonIds.get(sourceId);
        for (TerrainSegment segment : result.segments) {
            for (Addon addon : segment.addons) {
                if (addon.id() == id) return addon;
            }
        }
        throw new AssertionError("Missing addon " + sourceId);
    }

    private static Vec3 center(Addon addon) {
        return addon.footprint().nearLeft.add(addon.footprint().nearRight)
                .add(addon.footprint().farLeft).add(addon.footprint().farRight)
                .multiply(0.25);
    }
}
