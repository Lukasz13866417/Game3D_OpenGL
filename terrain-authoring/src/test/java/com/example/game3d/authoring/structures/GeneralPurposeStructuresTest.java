package com.example.game3d.authoring.structures;

import com.example.game3d.authoring.BaseTerrainStructure;
import com.example.game3d.authoring.MaterializedStructure;
import com.example.game3d.authoring.TerrainLevelSequence;
import com.example.game3d.authoring.TerrainMaterializer;
import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.SurfaceProperties;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.addon.Addon;
import com.example.game3d.core.terrain.addon.Portal;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class GeneralPurposeStructuresTest {
    private static final TrackProfile PROFILE = TrackProfile.gameplayDefault();

    @Test
    public void representativeStructuresMaterializeAsPureSharedJava() {
        List<StructureFactory> factories = Arrays.asList(
                new StructureFactory() {
                    @Override public BaseTerrainStructure<?> create() {
                        return new TerrainLine("test:line", 12);
                    }
                },
                new StructureFactory() {
                    @Override public BaseTerrainStructure<?> create() {
                        return new BasicTerrainLine("test:basic-line", 8,
                                BasicTerrainLine.Layout.MIDDLE_HORIZONTAL_BAND);
                    }
                },
                new StructureFactory() {
                    @Override public BaseTerrainStructure<?> create() {
                        return new TerrainCurve("test:curve", 12, 0.45f);
                    }
                },
                new StructureFactory() {
                    @Override public BaseTerrainStructure<?> create() {
                        return new BasicTerrainCurve("test:basic-curve", 8, -0.3f,
                                BasicTerrainCurve.Layout.END_POTION);
                    }
                },
                new StructureFactory() {
                    @Override public BaseTerrainStructure<?> create() {
                        return Terrain2DCurve.builder().sourcePrefix("test:2d")
                                .tilesToMake(8).horizontalAngleDelta(0.4f)
                                .verticalAngleDelta(0.22f)
                                .resetHorizontalAngle(true).resetVerticalAngle(true)
                                .horizontalAngleFadeoutTiles(2)
                                .verticalAngleFadeoutTiles(3).build();
                    }
                },
                new StructureFactory() {
                    @Override public BaseTerrainStructure<?> create() {
                        return BasicTerrain2DCurve.builder()
                                .sourcePrefix("test:basic-2d").tilesToMake(8)
                                .horizontalAngleDelta(-0.25f).verticalAngleDelta(0.18f)
                                .layout(BasicTerrain2DCurve.Layout.LEFT_RIGHT).build();
                    }
                },
                new StructureFactory() {
                    @Override public BaseTerrainStructure<?> create() {
                        return TerrainStairs.builder().sourcePrefix("test:stairs")
                                .tilesPerStair(4).stairCount(3).emptyBetween(1)
                                .horizontalAngleDelta(0.3f).jump(-0.8f).build();
                    }
                },
                new StructureFactory() {
                    @Override public BaseTerrainStructure<?> create() {
                        return BasicTerrainStairs.builder()
                                .sourcePrefix("test:basic-stairs")
                                .tilesPerStair(4).stairCount(3).emptyBetween(1)
                                .horizontalAngleDelta(0.3f).jump(-0.8f)
                                .layout(BasicTerrainStairs.Layout.TOP_POTION).build();
                    }
                },
                new StructureFactory() {
                    @Override public BaseTerrainStructure<?> create() {
                        return new TerrainBoostRamp("test:ramp", 3, 2, 4, 0.35f);
                    }
                },
                new StructureFactory() {
                    @Override public BaseTerrainStructure<?> create() {
                        return new TerrainEmptySegments("test:gaps", 4);
                    }
                },
                new StructureFactory() {
                    @Override public BaseTerrainStructure<?> create() {
                        return new EmptySegmentTestStructure("test:gap-test", 12);
                    }
                },
                new StructureFactory() {
                    @Override public BaseTerrainStructure<?> create() {
                        return new TerrainSpiral("test:spiral", 14, 0.8f, 0.16f);
                    }
                },
                new StructureFactory() {
                    @Override public BaseTerrainStructure<?> create() {
                        return new TerrainFunction("test:function", 6,
                                new Function<Float, Float>() {
                                    @Override public Float apply(Float value) {
                                        return value * value * 0.05f;
                                    }
                                }, -1f, 1f);
                    }
                },
                new StructureFactory() {
                    @Override public BaseTerrainStructure<?> create() {
                        return new TerrainLineWithSpikeRect("test:portal", 24, true);
                    }
                });

        for (StructureFactory factory : factories) {
            MaterializedStructure first = materialize(factory.create(), 445L);
            MaterializedStructure second = materialize(factory.create(), 445L);
            assertFalse(factory.create().getClass().getSimpleName(),
                    first.segments.isEmpty());
            assertEquals(factory.create().getClass().getSimpleName(),
                    digest(first), digest(second));
        }
    }

    @Test
    public void randomizedReservationsUseOnlyTheMaterializationSeed() {
        long first = digest(materialize(
                new TerrainSpiral("seeded:spiral", 18, 0.7f, 0.12f), 901L));
        long same = digest(materialize(
                new TerrainSpiral("seeded:spiral", 18, 0.7f, 0.12f), 901L));
        assertEquals(first, same);
        assertEquals("fixed-seed handwritten layout changed",
                1521027957089636915L, first);

        Set<Long> layouts = new HashSet<Long>();
        for (long seed = 901L; seed < 917L; seed++) {
            layouts.add(digest(materialize(
                    new TerrainSpiral("seeded:spiral", 18, 0.7f, 0.12f), seed)));
        }
        assertTrue("the materialization seed must influence randomized reservations",
                layouts.size() > 1);
    }

    @Test
    public void twoDimensionalFadeoutReturnsToLevelAndHeading() {
        MaterializedStructure result = materialize(Terrain2DCurve.builder()
                .sourcePrefix("fade").tilesToMake(4)
                .horizontalAngleDelta(0.4f).verticalAngleDelta(0.2f)
                .resetHorizontalAngle(true).resetVerticalAngle(true)
                .horizontalAngleFadeoutTiles(2).verticalAngleFadeoutTiles(2)
                .build(), 1L);

        assertEquals(6, result.segments.size());
        TerrainSegment last = result.segments.get(result.segments.size() - 1);
        Vec3 near = last.nearLeft.add(last.nearRight).multiply(0.5);
        Vec3 far = last.farLeft.add(last.farRight).multiply(0.5);
        Vec3 direction = far.subtract(near).normalized();
        assertEquals(0.0, direction.x, 2.0e-8);
        assertEquals(0.0, direction.y, 2.0e-8);
        assertEquals(-1.0, direction.z, 2.0e-8);
    }

    @Test
    public void boostRampPublishesCanonicalSurfacesAndGapConnectivity() {
        MaterializedStructure result = materialize(
                new TerrainBoostRamp("ramp", 3, 2, 4, 0.3f), 1L);
        assertEquals(9, result.segments.size());
        assertEquals(SurfaceProperties.BOOST_RAMP, result.segments.get(0).surface);
        assertEquals(SurfaceProperties.BOOST_RAMP, result.segments.get(1).surface);
        assertEquals(SurfaceProperties.BOOST_RAMP_LAUNCH,
                result.segments.get(2).surface);
        assertFalse(result.segments.get(3).solid);
        assertFalse(result.segments.get(4).solid);
        assertEquals(SurfaceProperties.NORMAL, result.segments.get(5).surface);
        assertFalse(result.segments.get(5).connectedToPrevious);
        assertTrue(result.segments.get(6).connectedToPrevious);
    }

    @Test
    public void stairsKeepGapsAddonFreeAndPlaceOneSeededSpikePerFlight() {
        MaterializedStructure result = materialize(new TerrainStairs(
                "stairs", 4, 3, 1, 0.25f, -0.75f), 33L);
        assertEquals(14, result.segments.size());
        assertEquals(3, addonCount(result));
        int gaps = 0;
        for (TerrainSegment segment : result.segments) {
            if (!segment.solid) {
                gaps++;
                assertTrue(segment.addons.isEmpty());
            }
        }
        assertEquals(2, gaps);
    }

    @Test
    public void portalHelperProducesTwoRolesWithOnePairId() {
        MaterializedStructure result = materialize(
                new TerrainLineWithSpikeRect("portal", 24, true), 72L);
        Portal entrance = null;
        Portal exit = null;
        for (TerrainSegment segment : result.segments) {
            for (Addon addon : segment.addons) {
                if (addon instanceof Portal) {
                    Portal portal = (Portal) addon;
                    if (portal.role == Portal.Role.ENTRANCE) {
                        entrance = portal;
                    } else {
                        exit = portal;
                    }
                }
            }
        }
        assertTrue(entrance != null);
        assertTrue(exit != null);
        assertEquals(exit.pairId, entrance.pairId);
        assertNotEquals(exit.id(), entrance.id());
    }

    @Test
    public void explicitPrefixesAllowRepeatedStructureTypesInOneLevel() {
        MaterializedStructure result = materialize(new TerrainLevelSequence(
                "repeated-lines",
                new TerrainLine("repeated:first", 8),
                new TerrainLine("repeated:second", 8)), 19L);
        assertEquals(16, result.segments.size());
        assertEquals(result.sourceAddonIds.size(), addonCount(result));
    }

    private interface StructureFactory {
        BaseTerrainStructure<?> create();
    }

    private static MaterializedStructure materialize(
            BaseTerrainStructure<?> structure, long seed) {
        return TerrainMaterializer.materialize(
                structure, PROFILE, Vec3.ZERO, seed);
    }

    private static int addonCount(MaterializedStructure structure) {
        int result = 0;
        for (TerrainSegment segment : structure.segments) {
            result += segment.addons.size();
        }
        return result;
    }

    private static long digest(MaterializedStructure structure) {
        long result = 1L;
        for (TerrainSegment segment : structure.segments) {
            result = 31L * result + segment.deterministicDigest();
        }
        return result;
    }
}
