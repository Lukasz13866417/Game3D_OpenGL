package com.example.game3d.authoring;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.addon.Addon;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Behavioral coverage for the shared replacement of the legacy symbolic reservation grids. */
public final class GridReservationContractTest {
    private static final TrackProfile PROFILE = TrackProfile.gameplayDefault();

    @Test
    public void fixedHorizontalAndVerticalReservationsUseExpectedRows() {
        MaterializedStructure result = materialize(new AdvancedTerrainStructure(4) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                addStraightTiles(brush, 4);
            }

            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                brush.reserveHorizontal(1, 1, 3, addons("h", 3));
                brush.reserveVertical(2, columns, 3, addons("v", 3));
            }
        }, 1L);

        assertEquals(0L, addon(result, "h0").ownerSegmentId());
        assertEquals(0L, addon(result, "h1").ownerSegmentId());
        assertEquals(0L, addon(result, "h2").ownerSegmentId());
        assertEquals(1L, addon(result, "v0").ownerSegmentId());
        assertEquals(2L, addon(result, "v1").ownerSegmentId());
        assertEquals(2L, addon(result, "v2").ownerSegmentId());
    }

    @Test
    public void randomFittingHorizontalAndVerticalRespectOccupiedCells() {
        MaterializedStructure horizontal = materialize(
                constrainedRandomHorizontal(), 18L);
        assertEquals(1L, addon(horizontal, "random-h0").ownerSegmentId());
        assertEquals(1L, addon(horizontal, "random-h1").ownerSegmentId());

        MaterializedStructure vertical = materialize(
                constrainedRandomVertical(), 18L);
        assertEquals(1L, addon(vertical, "random-v0").ownerSegmentId());
        assertEquals(2L, addon(vertical, "random-v1").ownerSegmentId());
        assertEquals(
                center(addon(vertical, "random-v0").footprint()).x,
                center(addon(vertical, "random-v1").footprint()).x,
                1.0e-12);
    }

    @Test
    public void kRandomFieldsAreSortedUniqueAndSeedDeterministic() {
        MaterializedStructure first = materialize(randomFields(), 77L);
        MaterializedStructure same = materialize(randomFields(), 77L);
        MaterializedStructure other = materialize(randomFields(), 78L);
        assertEquals(digest(first), digest(same));
        assertNotEquals(digest(first), digest(other));

        Set<String> centers = new HashSet<String>();
        long previousOwner = -1L;
        for (int i = 0; i < 8; i++) {
            Addon addon = addon(first, "field" + i);
            Vec3 center = center(addon.footprint());
            assertTrue(centers.add(center.x + ":" + center.y + ":" + center.z));
            assertTrue("K random fields must be emitted in row-major owner order",
                    addon.ownerSegmentId() >= previousOwner);
            previousOwner = addon.ownerSegmentId();
        }
    }

    @Test
    public void fixedAndRandomHorizontalRegionsProduceOneSpanningAddon() {
        MaterializedStructure fixed = materialize(new AdvancedTerrainStructure(2) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                addStraightTiles(brush, 2);
            }

            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                brush.reserveHorizontalRegion(
                        1, 2, 3, AddonBlueprint.airJumpPotion("fixed-region"));
            }
        }, 2L);
        Addon fixedRegion = addon(fixed, "fixed-region");
        assertEquals(0L, fixedRegion.ownerSegmentId());
        assertEquals(PROFILE.width * 3.0 / PROFILE.gridColumns,
                fixedRegion.footprint().nearRight
                        .subtract(fixedRegion.footprint().nearLeft).length(),
                1.0e-12);

        MaterializedStructure random = materialize(randomHorizontalRegion(), 9L);
        MaterializedStructure same = materialize(randomHorizontalRegion(), 9L);
        assertEquals(digest(random), digest(same));
        assertEquals(1, random.sourceAddonIds.size());
        assertEquals(PROFILE.width * 4.0 / PROFILE.gridColumns,
                addon(random, "random-region").footprint().nearRight
                        .subtract(addon(random, "random-region").footprint().nearLeft).length(),
                1.0e-12);
    }

    @Test
    public void advancedReservationsRejectCellAndRegionOverlapAtomically() {
        assertThrows(IllegalStateException.class, () -> materialize(
                new AdvancedTerrainStructure(3) {
                    @Override protected void generateTiles(Terrain.TileBrush brush) {
                        addStraightTiles(brush, 3);
                    }

                    @Override protected void generateAddons(
                            Terrain.AdvancedGridBrush brush, int rows, int columns) {
                        brush.reserveVertical(1, 2, 3, addons("vertical", 3));
                        brush.reserveHorizontal(2, 1, 3, addons("overlap", 3));
                    }
                }, 1L));

        assertThrows(IllegalStateException.class, () -> materialize(
                new AdvancedTerrainStructure(2) {
                    @Override protected void generateTiles(Terrain.TileBrush brush) {
                        addStraightTiles(brush, 2);
                    }

                    @Override protected void generateAddons(
                            Terrain.AdvancedGridBrush brush, int rows, int columns) {
                        brush.reserveHorizontal(1, 3, 1, addons("cell", 1));
                        brush.reserveHorizontalRegion(
                                1, 2, 3,
                                AddonBlueprint.airJumpPotion("overlap-region"));
                    }
                }, 1L));
    }

    @Test
    public void basicReservationsRemainNonExclusiveButPropagateToAdvancedParent() {
        final BasicTerrainStructure child = new BasicTerrainStructure(2) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                addStraightTiles(brush, 2);
            }

            @Override protected void generateAddons(
                    Terrain.BasicGridBrush brush, int rows, int columns) {
                brush.reserveHorizontal(1, 1, 2, addons("basic-a", 2));
                brush.reserveVertical(1, 1, 2, addons("basic-b", 2));
            }
        };
        assertThrows(IllegalStateException.class, () -> materialize(
                new AdvancedTerrainStructure(1) {
                    @Override protected void generateTiles(Terrain.TileBrush brush) {
                        addChild(child, brush);
                        brush.addSegment("parent-extra");
                    }

                    @Override protected void generateAddons(
                            Terrain.AdvancedGridBrush brush, int rows, int columns) {
                        brush.reserveHorizontal(
                                1, 1, 1, addons("parent-overlap", 1));
                    }
                }, 1L));
    }

    @Test
    public void nonPropagatingMiddleLayerStopsDescendantOccupancy() {
        final AdvancedTerrainStructure leaf = reservingChild("leaf");
        final BasicTerrainStructure middle = new BasicTerrainStructure(1) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                addChild(leaf, brush);
                brush.addSegment("middle-extra");
            }

            @Override protected void generateAddons(
                    Terrain.BasicGridBrush brush, int rows, int columns) {
            }

            @Override protected boolean shouldPropagateReservationsToParent() {
                return false;
            }
        };
        MaterializedStructure result = materialize(new AdvancedTerrainStructure(1) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                addChild(middle, brush);
                brush.addSegment("root-extra");
            }

            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                brush.reserveHorizontal(1, 2, 2, addons("root", 2));
            }
        }, 1L);
        assertEquals(4, result.sourceAddonIds.size());
    }

    @Test
    public void blockedChildRowsPropagateThroughBasicMiddleLayer() {
        final AdvancedTerrainStructure leaf = new AdvancedTerrainStructure(2) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                addStraightTiles(brush, 2);
            }

            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                brush.reserveHorizontal(1, 1, 1, addons("leaf", 1));
            }

            @Override protected boolean shouldPropagateReservationsToParent() {
                return false;
            }

            @Override protected int[] getParentBlockedRowsRange(int rows, int columns) {
                return new int[] {1, rows};
            }
        };
        final BasicTerrainStructure middle = new BasicTerrainStructure(1) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                addChild(leaf, brush);
                brush.addSegment("middle-extra");
            }

            @Override protected void generateAddons(
                    Terrain.BasicGridBrush brush, int rows, int columns) {
            }
        };
        assertThrows(IllegalStateException.class, () -> materialize(
                new AdvancedTerrainStructure(1) {
                    @Override protected void generateTiles(Terrain.TileBrush brush) {
                        addChild(middle, brush);
                        brush.addSegment("root-extra");
                    }

                    @Override protected void generateAddons(
                            Terrain.AdvancedGridBrush brush, int rows, int columns) {
                        brush.reserveHorizontal(1, 1, 1, addons("blocked", 1));
                    }
                }, 1L));
    }

    @Test
    public void allRandomModesUseOnlyTheInjectedSeed() {
        long first = digest(materialize(allRandomModes(), 991L));
        long same = digest(materialize(allRandomModes(), 991L));
        long other = digest(materialize(allRandomModes(), 992L));
        assertEquals(first, same);
        assertNotEquals(first, other);
        assertEquals("seeded physical-grid layout changed", 278971582129715L, first);
    }

    @Test
    public void terrainInstancesMaterializeConcurrentlyWithoutSharedState() throws Exception {
        final long expectedA = terrainDigest(341L);
        final long expectedB = terrainDigest(917L);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Long> first = executor.submit(new Callable<Long>() {
                @Override public Long call() {
                    return terrainDigest(341L);
                }
            });
            Future<Long> second = executor.submit(new Callable<Long>() {
                @Override public Long call() {
                    return terrainDigest(917L);
                }
            });
            assertEquals(expectedA, first.get().longValue());
            assertEquals(expectedB, second.get().longValue());
        } finally {
            executor.shutdownNow();
        }
    }

    private static AdvancedTerrainStructure constrainedRandomHorizontal() {
        return new AdvancedTerrainStructure(2) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                addStraightTiles(brush, 2);
            }

            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                brush.reserveHorizontal(1, 1, columns, addons("block-h", columns));
                brush.reserveRandomFittingHorizontal(2, addons("random-h", 2));
            }
        };
    }

    private static AdvancedTerrainStructure constrainedRandomVertical() {
        return new AdvancedTerrainStructure(4) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                addStraightTiles(brush, 4);
            }

            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                brush.reserveHorizontal(1, 1, columns, addons("block-top", columns));
                brush.reserveHorizontal(4, 1, columns, addons("block-bottom", columns));
                brush.reserveRandomFittingVertical(2, addons("random-v", 2));
            }
        };
    }

    private static AdvancedTerrainStructure randomFields() {
        return new AdvancedTerrainStructure(5) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                addStraightTiles(brush, 5);
            }

            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                brush.reserveKRandomFields(addons("field", 8));
            }
        };
    }

    private static AdvancedTerrainStructure randomHorizontalRegion() {
        return new AdvancedTerrainStructure(3) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                addStraightTiles(brush, 3);
            }

            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                brush.reserveRandomHorizontalRegion(
                        4, AddonBlueprint.airJumpPotion("random-region"));
            }
        };
    }

    private static AdvancedTerrainStructure reservingChild(final String prefix) {
        return new AdvancedTerrainStructure(2) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                addStraightTiles(brush, 2);
            }

            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                brush.reserveHorizontal(1, 2, 2, addons(prefix, 2));
            }
        };
    }

    private static AdvancedTerrainStructure allRandomModes() {
        return new AdvancedTerrainStructure(10) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                addStraightTiles(brush, 10);
            }

            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                brush.reserveRandomFittingHorizontal(2, addons("rh", 2));
                brush.reserveRandomFittingVertical(2, addons("rv", 2));
                brush.reserveKRandomFields(addons("rk", 3));
                brush.reserveRandomHorizontalRegion(
                        2, AddonBlueprint.airJumpPotion("rr"));
            }
        };
    }

    private static long terrainDigest(long seed) {
        Terrain terrain = new Terrain(PROFILE, Vec3.ZERO, seed);
        terrain.enqueueStructure(allRandomModes());
        terrain.generate(GenerationBudget.UNLIMITED);
        return terrain.snapshot().deterministicDigest;
    }

    private static MaterializedStructure materialize(
            BaseTerrainStructure<?> structure, long seed) {
        return TerrainMaterializer.materialize(
                structure, PROFILE, Vec3.ZERO, seed);
    }

    private static void addStraightTiles(Terrain.TileBrush brush, int count) {
        for (int i = 0; i < count; i++) {
            brush.addSegment("tile-" + i);
        }
    }

    private static AddonBlueprint[] addons(String prefix, int count) {
        AddonBlueprint[] result = new AddonBlueprint[count];
        for (int i = 0; i < count; i++) {
            result[i] = AddonBlueprint.airJumpPotion(prefix + i);
        }
        return result;
    }

    private static Addon addon(MaterializedStructure structure, String sourceId) {
        Long id = structure.sourceAddonIds.get(sourceId);
        if (id == null) {
            throw new AssertionError("Missing addon source " + sourceId);
        }
        for (TerrainSegment segment : structure.segments) {
            for (Addon addon : segment.addons) {
                if (addon.id() == id.longValue()) {
                    return addon;
                }
            }
        }
        throw new AssertionError("Missing placed addon " + sourceId);
    }

    private static Vec3 center(com.example.game3d.core.terrain.addon.AddonFootprint footprint) {
        return footprint.nearLeft.add(footprint.nearRight)
                .add(footprint.farLeft).add(footprint.farRight).multiply(0.25);
    }

    private static long digest(MaterializedStructure structure) {
        long digest = 1L;
        for (TerrainSegment segment : structure.segments) {
            digest = 31L * digest + segment.deterministicDigest();
        }
        return digest;
    }
}
