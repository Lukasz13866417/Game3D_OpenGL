package com.example.game3d.authoring;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.StreamingTerrainGenerator;
import com.example.game3d.core.terrain.SurfaceProperties;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.TerrainSnapshot;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TerrainAuthoringTest {
    @Test
    public void connectedTilesShareExactEdgeAndLiftBreaksConnectivity() {
        AdvancedTerrainStructure structure = new AdvancedTerrainStructure(3) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                brush.addSegment("a");
                brush.addHorizontalAng(Math.PI / 12.0);
                brush.addSegment("b");
                brush.liftUp(1.0);
                brush.addSegment("c");
            }
            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {}
        };
        MaterializedStructure result = TerrainMaterializer.materialize(
                structure, TrackProfile.gameplayDefault(), Vec3.ZERO, 11L);
        assertEquals(3, result.segments.size());
        TerrainSegment first = result.segments.get(0);
        TerrainSegment second = result.segments.get(1);
        TerrainSegment third = result.segments.get(2);
        assertTrue(second.connectedToPrevious);
        assertSame(first.farLeft, second.nearLeft);
        assertSame(first.farRight, second.nearRight);
        assertFalse(third.connectedToPrevious);
    }

    @Test
    public void structureInstancesAreOneShot() {
        AdvancedTerrainStructure structure = emptyStraight(1);
        TerrainMaterializer.materialize(
                structure, TrackProfile.gameplayDefault(), Vec3.ZERO, 1L);
        assertThrows(IllegalStateException.class, () -> TerrainMaterializer.materialize(
                structure, TrackProfile.gameplayDefault(), Vec3.ZERO, 1L));
    }

    @Test
    public void randomReservationsArePerBuildAndDeterministic() {
        long first = digest(materializeRandom(99L));
        long same = digest(materializeRandom(99L));
        long other = digest(materializeRandom(101L));
        assertEquals(first, same);
        assertNotEquals(first, other);
    }

    @Test
    public void canonicalStructuresMatchCurrentGenerator() {
        Vec3 start = new Vec3(0.0, -3.0, 0.0);
        StreamingTerrainGenerator legacy = new StreamingTerrainGenerator(3.2, 1.4, start);
        Terrain authored = new Terrain(TrackProfile.gameplayDefault(), start, 0L);
        legacy.enqueueIntroSegments();
        authored.enqueueStructure(GameplayTerrainFactory.intro());
        for (int ordinal = 0; ordinal < 24; ordinal++) {
            legacy.enqueueGameplayLevel(ordinal);
            authored.enqueueStructure(GameplayTerrainFactory.gameplayLevel(ordinal));
        }
        legacy.generateChunks(-1);
        authored.generate(GenerationBudget.UNLIMITED);
        TerrainSnapshot expected = legacy.snapshot();
        TerrainSnapshot actual = authored.snapshot();
        assertEquals(expected.segments.size(), actual.segments.size());
        assertEquals(expected.addonIdHighWatermark, actual.addonIdHighWatermark);
        for (int i = 0; i < expected.segments.size(); i++) {
            assertEquals("segment " + i + "\nexpected=" + describe(expected.segments.get(i))
                            + "\nactual=" + describe(actual.segments.get(i)),
                    expected.segments.get(i).deterministicDigest(),
                    actual.segments.get(i).deterministicDigest());
        }
        assertEquals(expected.deterministicDigest, actual.deterministicDigest);
    }

    @Test
    public void publicationChunkSizeDoesNotChangeFinalSnapshot() {
        Terrain one = new Terrain(TrackProfile.gameplayDefault(), Vec3.ZERO, 8L);
        Terrain many = new Terrain(TrackProfile.gameplayDefault(), Vec3.ZERO, 8L);
        one.enqueueStructure(GameplayTerrainFactory.gameplayLevel(7));
        many.enqueueStructure(GameplayTerrainFactory.gameplayLevel(7));
        one.generate(GenerationBudget.UNLIMITED);
        assertEquals(0, many.generateChunks(0));
        while (many.hasPendingGenerationWork()) many.generateChunks(3);
        assertEquals(one.snapshot().deterministicDigest, many.snapshot().deterministicDigest);
    }

    @Test
    public void commandAndPublicationBudgetsAdvanceIndependently() {
        Terrain terrain = new Terrain(TrackProfile.gameplayDefault(), Vec3.ZERO, 8L);
        QueuedStructure queued = terrain.enqueueStructure(emptyStraight(3));

        assertEquals(0, terrain.generate(new GenerationBudget(2, 10)));
        assertFalse(queued.isMaterialized());
        assertTrue(terrain.snapshot().segments.isEmpty());

        assertEquals(0, terrain.generate(new GenerationBudget(2, 0)));
        assertFalse(queued.isMaterialized());
        assertTrue(terrain.snapshot().segments.isEmpty());

        assertEquals(0, terrain.generate(new GenerationBudget(2, 0)));
        assertFalse(queued.isMaterialized());
        assertTrue(terrain.snapshot().segments.isEmpty());

        assertEquals(0, terrain.generate(new GenerationBudget(1, 0)));
        assertTrue(queued.isMaterialized());
        assertTrue("zero publication budget must expose nothing",
                terrain.snapshot().segments.isEmpty());

        assertEquals(2, terrain.generate(new GenerationBudget(0, 2)));
        assertEquals(2, terrain.snapshot().segments.size());
        assertEquals(1, terrain.generate(new GenerationBudget(0, 2)));
        assertEquals(3, terrain.snapshot().segments.size());
        assertEquals(3, terrain.getPlannedSegmentCount());
    }

    @Test
    public void plannedCountIncludesCapturedAndFrozenUnpublishedSegments() {
        Terrain terrain = new Terrain(TrackProfile.gameplayDefault(), Vec3.ZERO, 8L);
        terrain.enqueueStructure(emptyStraight(3));
        terrain.enqueueStructure(emptyStraight(2));
        assertEquals(5, terrain.getPlannedSegmentCount());

        terrain.generate(new GenerationBudget(4, 0));
        assertEquals(5, terrain.getPlannedSegmentCount());
        terrain.generate(GenerationBudget.UNLIMITED);
        assertEquals(5, terrain.getPlannedSegmentCount());
    }

    @Test
    public void failedPrivateMaterializationPublishesNothingAndConsumesNoIds() {
        Terrain terrain = new Terrain(TrackProfile.gameplayDefault(), Vec3.ZERO, 8L);
        QueuedStructure queued = terrain.enqueueStructure(new AdvancedTerrainStructure(1) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                brush.addEmptySegment("gap");
            }

            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                brush.placeNormalized(1, 0.4, 0.6, 0.4, 0.6,
                        AddonBlueprint.airJumpPotion("invalid-on-gap"));
            }
        });
        assertThrows(IllegalArgumentException.class,
                () -> terrain.generate(GenerationBudget.UNLIMITED));
        assertFalse(queued.isMaterialized());
        assertTrue(queued.isFailed());
        assertEquals(0L, terrain.revision());
        assertEquals(-1L, terrain.snapshot().addonIdHighWatermark);
        assertTrue(terrain.snapshot().segments.isEmpty());

        QueuedStructure valid = terrain.enqueueStructure(new AdvancedTerrainStructure(1) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                brush.addSegment("valid");
            }

            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                brush.placeNormalized(1, 0.4, 0.6, 0.4, 0.6,
                        AddonBlueprint.airJumpPotion("valid-potion"));
            }
        });

        terrain.generate(GenerationBudget.UNLIMITED);
        assertTrue(valid.isMaterialized());
        assertEquals(1, terrain.snapshot().segments.size());
        assertEquals(0L, terrain.snapshot().segments.get(0).id);
        assertEquals(1L, terrain.snapshot().segments.get(0).addons.get(0).id());
    }

    @Test
    public void failedBuildInvalidatesDependentCapturedGridBuilds() {
        Terrain terrain = new Terrain(TrackProfile.gameplayDefault(), Vec3.ZERO, 18L);
        QueuedStructure invalid = terrain.enqueueStructure(new AdvancedTerrainStructure(1) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                brush.addEmptySegment("bad-gap");
            }

            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                brush.placeNormalized(1, .4, .6, .4, .6,
                        AddonBlueprint.airJumpPotion("bad-addon"));
            }
        });
        QueuedStructure dependent = terrain.enqueueStructure(new AdvancedTerrainStructure(2) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                brush.addSegment("dependent-segment");
                brush.addSegment("dependent-segment-2");
            }

            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                brush.placeGridRegion(1, 1, 1, 1,
                        AddonBlueprint.airJumpPotion("dependent-grid-addon"));
            }
        });

        assertThrows(IllegalArgumentException.class,
                () -> terrain.generate(GenerationBudget.UNLIMITED));
        assertTrue(invalid.isFailed());
        assertTrue(dependent.isFailed());
        assertFalse(terrain.hasPendingGenerationWork());
        assertTrue(terrain.snapshot().segments.isEmpty());

        QueuedStructure fresh = terrain.enqueueStructure(new AdvancedTerrainStructure(1) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                brush.addSegment("fresh-segment");
            }

            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                brush.placeGridRegion(1, 1, 1, 1,
                        AddonBlueprint.airJumpPotion("fresh-grid-addon"));
            }
        });
        terrain.generate(GenerationBudget.UNLIMITED);
        assertTrue(fresh.isMaterialized());
        assertEquals(0L, terrain.snapshot().segments.get(0).id);
        assertEquals(1L, terrain.snapshot().segments.get(0).addons.get(0).id());
    }

    private static MaterializedStructure materializeRandom(long seed) {
        return TerrainMaterializer.materialize(new AdvancedTerrainStructure(3) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                brush.addSegment("0"); brush.addSegment("1"); brush.addSegment("2");
            }
            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                brush.reserveKRandomFields(new AddonBlueprint[] {
                        AddonBlueprint.airJumpPotion("random-potion")
                });
            }
        }, TrackProfile.gameplayDefault(), Vec3.ZERO, seed);
    }

    private static long digest(MaterializedStructure structure) {
        long result = 1L;
        for (TerrainSegment segment : structure.segments) {
            result = 31L * result + segment.deterministicDigest();
        }
        return result;
    }

    private static String describe(TerrainSegment segment) {
        StringBuilder result = new StringBuilder();
        result.append(segment.nearLeft).append('|').append(segment.nearRight)
                .append('|').append(segment.farLeft).append('|').append(segment.farRight)
                .append(" solid=").append(segment.solid)
                .append(" connected=").append(segment.connectedToPrevious)
                .append(" surface=").append(segment.surface.kind)
                .append(" appearance=").append(segment.farLeftAppearance.alpha).append('/')
                .append(segment.farLeftAppearance.brightness);
        for (com.example.game3d.core.terrain.addon.Addon addon : segment.addons) {
            result.append(" addon=").append(addon.kind).append(':')
                    .append(addon.id()).append(':').append(addon.deterministicDigest());
            if (addon instanceof com.example.game3d.core.terrain.addon.DeathSpike) {
                com.example.game3d.core.terrain.addon.DeathSpike spike =
                        (com.example.game3d.core.terrain.addon.DeathSpike) addon;
                result.append(" nl=").append(bits(spike.nearLeft))
                        .append(" apex=").append(bits(spike.apex))
                        .append(" norm=").append(bits(spike.outwardNormal))
                        .append(" center=").append(bits(spike.collisionBaseCenter))
                        .append(" radius=").append(Long.toHexString(Double.doubleToLongBits(spike.collisionRadius)))
                        .append(" height=").append(Long.toHexString(Double.doubleToLongBits(spike.collisionHeight)));
            }
        }
        return result.toString();
    }

    private static String bits(Vec3 value) {
        return Long.toHexString(Double.doubleToLongBits(value.x)) + ','
                + Long.toHexString(Double.doubleToLongBits(value.y)) + ','
                + Long.toHexString(Double.doubleToLongBits(value.z));
    }

    private static AdvancedTerrainStructure emptyStraight(final int count) {
        return new AdvancedTerrainStructure(count) {
            @Override protected void generateTiles(Terrain.TileBrush brush) {
                for (int i = 0; i < count; i++) brush.addSegment();
            }
            @Override protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {}
        };
    }
}
