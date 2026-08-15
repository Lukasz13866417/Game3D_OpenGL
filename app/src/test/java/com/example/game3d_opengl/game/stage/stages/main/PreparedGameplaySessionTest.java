package com.example.game3d_opengl.game.stage.stages.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.example.game3d.core.terrain.TerrainCollisionIndex;
import com.example.game3d.core.terrain.TerrainOutput;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.authoring.GameplayLevelCatalog;
import com.example.game3d_opengl.game.player.player_character.PlayerConfig;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class PreparedGameplaySessionTest {
    @Test
    public void sessionRetainsTheExactImmutableCatalogForRestartPreparation() {
        GameplayLevelCatalog catalog = GameplayLevelCatalog.builtIns();
        PreparedGameplaySession session =
                PreparedGameplaySession.createInitialSession(catalog);
        try {
            assertSame(catalog, session.getTerrainGenerator().catalog());
        } finally {
            session.cleanupGPUResourcesRecursively();
        }
    }

    @Test
    public void terrainPreparationProducesCanonicalBootstrapWithoutGpuAssets() {
        PreparedGameplaySession session =
                PreparedGameplaySession.createInitialSession();
        TerrainOutput output = null;
        try {
            for (int guard = 0;
                 !session.isSpawnPlayableReady() && guard < 200;
                 guard++) {
                session.generateTerrainChunks(64);
            }
            assertTrue(session.isSpawnPlayableReady());

            output = session.acquireTerrainOutput();
            TerrainSnapshot snapshot = output.snapshot();

            assertFalse(snapshot.segments.isEmpty());
            assertTrue(snapshot.committedThroughSegmentId
                    >= snapshot.segments.get(snapshot.segments.size() - 1).id);
            assertTrue("preparation commits are folded into the bootstrap",
                    output.drainPendingCommits().isEmpty());
        } finally {
            if (output != null) {
                output.close();
            }
            session.cleanupGPUResourcesRecursively();
        }
    }

    @Test
    public void spawn_playable_lead_matches_initial_gameplay_guard_target() {
        int interactionAheadTiles = 64;
        float segmentLength = 1.4f;

        int expected = GameplayStage.computeTargetCommittedLeadTiles(
                PlayerConfig.speedForCompletedPhases(0) * 1000f,
                segmentLength,
                interactionAheadTiles
        );

        assertEquals(
                expected,
                PreparedGameplaySession.computeSpawnPlayableLeadTiles(
                        segmentLength,
                        interactionAheadTiles
                )
        );
    }

    @Test
    public void preparedCollisionIndexHandsOffTheExactCanonicalBootstrap()
            throws Exception {
        PreparedGameplaySession session =
                PreparedGameplaySession.createInitialSession();
        TerrainOutput output = null;
        try {
            generateSpawnPlayableLead(session);
            session.beginRuntimePreparationAsync();
            awaitRuntimePreparation(session);

            output = session.acquireTerrainOutput();
            TerrainSnapshot bootstrap = output.snapshot();

            try {
                session.acquirePreparedCollisionIndex(
                        bootstrap.revision + 1L);
                fail("Expected the handoff to reject a mismatched revision");
            } catch (IllegalStateException expected) {
                // A failed revision check must not consume the prepared index.
            }

            TerrainCollisionIndex collision =
                    session.acquirePreparedCollisionIndex(
                            bootstrap.revision);
            assertNotNull(collision);
            assertEquals(bootstrap.revision, collision.revision());
            assertEquals(
                    bootstrap.deterministicDigest,
                    collision.deterministicDigest());
            for (TerrainSegment segment : bootstrap.segments) {
                if (segment.solid) {
                    assertTrue(collision.containsTriangle(segment.id * 2L));
                    assertTrue(collision.containsTriangle(
                            segment.id * 2L + 1L));
                }
            }

            assertNull("the prepared index is a single-owner handoff",
                    session.acquirePreparedCollisionIndex(
                            bootstrap.revision));
        } finally {
            if (output != null) {
                output.close();
            }
            session.cleanupGPUResourcesRecursively();
        }
    }

    private static void generateSpawnPlayableLead(
            PreparedGameplaySession session) {
        for (int guard = 0;
             !session.isSpawnPlayableReady() && guard < 200;
             guard++) {
            session.generateTerrainChunks(64);
        }
        assertTrue(session.isSpawnPlayableReady());
    }

    private static void awaitRuntimePreparation(
            PreparedGameplaySession session) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10L);
        while (!session.isRuntimePreparedReady()
                && System.nanoTime() < deadline) {
            Thread.sleep(2L);
        }
        assertTrue("collision preparation did not finish before timeout",
                session.isRuntimePreparedReady());
    }
}
