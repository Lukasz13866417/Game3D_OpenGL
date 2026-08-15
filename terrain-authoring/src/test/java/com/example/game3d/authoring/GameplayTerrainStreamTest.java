package com.example.game3d.authoring;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.StreamingTerrainGenerator;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class GameplayTerrainStreamTest {
    @Test
    public void builtInOnlyCatalogPreservesSelectionAndOutput() {
        Vec3 start = new Vec3(0.0, -3.5, -0.5);
        GameplayTerrainStream actual = GameplayTerrainStream.builtIns(start);
        StreamingTerrainGenerator expected =
                new StreamingTerrainGenerator(3.2, 1.4, start);
        actual.enqueueIntroSegments();
        expected.enqueueIntroSegments();
        for (int ordinal = 0; ordinal < 24; ordinal++) {
            actual.enqueueGameplayLevel(ordinal);
            expected.enqueueGameplayLevel(ordinal);
        }
        actual.generateChunks(-1);
        expected.generateChunks(-1);
        assertEquals(expected.snapshot().deterministicDigest,
                actual.snapshot().deterministicDigest);
        assertEquals(expected.snapshot().segments.size(),
                actual.snapshot().segments.size());
    }

    @Test
    public void builtInProviderKeepsItsIdentityWhenCustomEntriesExpandPool() {
        GameplayLevelProvider custom = new GameplayLevelProvider() {
            @Override public String stableId() { return "custom"; }
            @Override public BaseTerrainStructure<?> create(long levelOrdinal) {
                return GameplayTerrainFactory.gameplayTemplate(0, (int) levelOrdinal);
            }
        };
        GameplayLevelCatalog catalog = GameplayLevelCatalog.builtIns()
                .withAdditionalEntries(Collections.singletonList(custom));

        for (int ordinal = 0; ordinal < 200; ordinal++) {
            GameplayLevelProvider selected = catalog.select(ordinal);
            int selectedBuiltin = builtinIndex(selected.stableId());
            if (selectedBuiltin >= 0) {
                MaterializedStructure actual = TerrainMaterializer.materialize(
                        selected.create(ordinal), TrackProfile.gameplayDefault(),
                        Vec3.ZERO, 123L);
                MaterializedStructure expected = TerrainMaterializer.materialize(
                        GameplayTerrainFactory.gameplayTemplate(selectedBuiltin, ordinal),
                        TrackProfile.gameplayDefault(), Vec3.ZERO, 123L);
                assertEquals(expected.segments.size(), actual.segments.size());
                for (int i = 0; i < expected.segments.size(); i++) {
                    assertEquals(expected.segments.get(i).deterministicDigest(),
                            actual.segments.get(i).deterministicDigest());
                }
            }
        }
    }

    private static int builtinIndex(String id) {
        java.util.List<GameplayLevelProvider> builtins =
                GameplayLevelCatalog.builtIns().entries();
        for (int i = 0; i < builtins.size(); i++) {
            if (builtins.get(i).stableId().equals(id)) return i;
        }
        return -1;
    }
}
