package com.example.game3d.authoring.grid.symbolic;

import com.example.game3d.authoring.DeterministicRandom;
import com.example.game3d.authoring.grid.symbolic.advanced.AdvancedGridCreator;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.PartialSegmentHandlerResourcePack;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;

public final class SymbolicGridResourceIsolationTest {
    @Test
    public void injectedSeedControlsEveryRandomReservation() {
        GridSegment[] first = randomFields(901L);
        GridSegment[] repeated = randomFields(901L);
        GridSegment[] different = randomFields(902L);

        assertArrayEquals(first, repeated);
        assertFalse(Arrays.equals(first, different));
    }

    @Test
    public void defaultResourcesOwnIndependentPoolsScratchAndRandomState() {
        PartialSegmentHandlerResourcePack first =
                PartialSegmentHandlerResourcePack.createDefault();
        PartialSegmentHandlerResourcePack second =
                PartialSegmentHandlerResourcePack.createDefault();

        assertNotSame(first.lengthTreapNodePool(), second.lengthTreapNodePool());
        assertNotSame(first.treapNodePool(), second.treapNodePool());
        assertNotSame(first.rbNodePool(), second.rbNodePool());
        assertNotSame(first.gridBuildScratch(), second.gridBuildScratch());
        assertNotSame(first.random(), second.random());
    }

    @Test
    public void destroyingOneSessionCannotReleaseAnotherSessionsNodes() {
        AdvancedGridCreator first = new AdvancedGridCreator(
                12,
                6,
                PartialSegmentHandlerResourcePack.using(new DeterministicRandom(11L)));
        AdvancedGridCreator second = new AdvancedGridCreator(
                12,
                6,
                PartialSegmentHandlerResourcePack.using(new DeterministicRandom(11L)));

        first.reserveKRandomFields(8);
        second.reserveKRandomFields(8);
        first.destroy();

        // This would expose cross-session pool ownership corruption immediately.
        second.reserveRandomFittingHorizontal(3);
        second.destroy();
    }

    private static GridSegment[] randomFields(long seed) {
        AdvancedGridCreator creator = new AdvancedGridCreator(
                12,
                6,
                PartialSegmentHandlerResourcePack.seeded(seed));
        try {
            return creator.reserveKRandomFields(12);
        } finally {
            creator.destroy();
        }
    }
}
