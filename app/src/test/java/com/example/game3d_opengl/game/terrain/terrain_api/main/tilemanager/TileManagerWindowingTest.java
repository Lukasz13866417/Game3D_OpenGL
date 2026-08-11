package com.example.game3d_opengl.game.terrain.terrain_api.main.tilemanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.game3d_opengl.game.terrain.terrain_api.main.TerrainGridField;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import org.junit.Test;

public class TileManagerWindowingTest {
    private static void assertVectorEquals(Vector3D expected, Vector3D actual) {
        assertEquals(expected.x, actual.x, 1e-6f);
        assertEquals(expected.y, actual.y, 1e-6f);
        assertEquals(expected.z, actual.z, 1e-6f);
    }

    private static void assertFieldMatches(float[] corners, TerrainGridField field) {
        assertEquals(field.nearLeft.x, corners[0], 1e-6f);
        assertEquals(field.nearLeft.y, corners[1], 1e-6f);
        assertEquals(field.nearLeft.z, corners[2], 1e-6f);
        assertEquals(field.nearRight.x, corners[3], 1e-6f);
        assertEquals(field.nearRight.y, corners[4], 1e-6f);
        assertEquals(field.nearRight.z, corners[5], 1e-6f);
        assertEquals(field.farLeft.x, corners[6], 1e-6f);
        assertEquals(field.farLeft.y, corners[7], 1e-6f);
        assertEquals(field.farLeft.z, corners[8], 1e-6f);
        assertEquals(field.farRight.x, corners[9], 1e-6f);
        assertEquals(field.farRight.y, corners[10], 1e-6f);
        assertEquals(field.farRight.z, corners[11], 1e-6f);
    }

    @Test
    public void findLastTileIndexAtOrBefore_handles_rewritten_non_contiguous_ids() {
        TileManager tileManager = new TileManager(
                32,
                6,
                new Vector3D(0f, 0f, 0f),
                3.2f,
                1.4f,
                1f
        );
        try {
            for (int i = 0; i < 6; ++i) {
                tileManager.addSegment(false);
            }

            int tileCount = tileManager.getTileCount();
            long[] ids = new long[tileCount];
            for (int i = 0; i < tileCount; ++i) {
                ids[i] = tileManager.getTile(i).getID();
                assertEquals(i, tileManager.findLastTileIndexAtOrBefore(ids[i]));
                if (i > 0) {
                    assertTrue(ids[i] > ids[i - 1]);
                }
            }

            assertEquals(-1, tileManager.findLastTileIndexAtOrBefore(ids[0] - 1));
            assertEquals(tileCount - 1, tileManager.findLastTileIndexAtOrBefore(ids[tileCount - 1] + 100));

            for (int i = 0; i < tileCount - 1; ++i) {
                if (ids[i + 1] - ids[i] > 1) {
                    long missingId = ids[i] + 1;
                    assertEquals(i, tileManager.findLastTileIndexAtOrBefore(missingId));
                    return;
                }
            }
            throw new AssertionError("Expected at least one gap in rewritten tile IDs.");
        } finally {
            tileManager.cleanupGPUResourcesRecursively();
        }
    }

    @Test
    public void absolute_tile_indices_remain_stable_after_old_tiles_are_removed() {
        TileManager tileManager = new TileManager(
                32,
                6,
                new Vector3D(0f, 0f, 0f),
                3.2f,
                1.4f,
                1f
        );
        try {
            for (int i = 0; i < 8; ++i) {
                tileManager.addSegment(false);
            }

            int tileCount = tileManager.getTileCount();
            long[] ids = new long[tileCount];
            int[] absoluteIndices = new int[tileCount];
            for (int i = 0; i < tileCount; ++i) {
                ids[i] = tileManager.getTile(i).getID();
                absoluteIndices[i] = tileManager.getAbsoluteTileIndexForVisibleIndex(i);
            }

            tileManager.removeOldTiles(ids[4]);

            for (int i = 4; i < tileCount; ++i) {
                assertEquals(absoluteIndices[i], tileManager.getAbsoluteTileIndexAtOrBefore(ids[i]));
            }
        } finally {
            tileManager.cleanupGPUResourcesRecursively();
        }
    }

    @Test
    public void raw_corner_writers_match_allocating_field_views() {
        TileManager tileManager = new TileManager(
                32,
                6,
                new Vector3D(0f, 0f, 0f),
                3.2f,
                1.4f,
                1f
        );
        try {
            tileManager.addSegment(false);
            tileManager.addHorizontalAngle(0.23f);
            tileManager.addSegment(false);
            tileManager.addVerticalAngle(-0.18f);
            tileManager.addSegment(false);
            tileManager.addHorizontalAngle(-0.11f);
            tileManager.addVerticalAngle(0.09f);
            tileManager.addSegment(false);

            float[] corners = new float[12];

            TerrainGridField singleField = tileManager.getField(2, 3);
            tileManager.writeFieldCorners(2, 3, corners);
            assertFieldMatches(corners, singleField);

            TerrainGridField regionField = tileManager.getHorizontalRegionField(3, 2, 3);
            tileManager.writeHorizontalRegionFieldCorners(3, 2, 3, corners);
            assertFieldMatches(corners, regionField);
        } finally {
            tileManager.cleanupGPUResourcesRecursively();
        }
    }

    @Test
    public void add_segment_rewrites_previous_tile_far_edge_to_match_new_tile_near_edge() {
        TileManager tileManager = new TileManager(
                32,
                6,
                new Vector3D(0f, 0f, 0f),
                3.2f,
                1.4f,
                1f
        );
        try {
            tileManager.addSegment(false);
            Vector3D farLeftBeforeRewrite = tileManager.getTile(1).farLeft;
            Vector3D farRightBeforeRewrite = tileManager.getTile(1).farRight;

            tileManager.addHorizontalAngle(0.27f);
            tileManager.addVerticalAngle(-0.12f);
            tileManager.addSegment(false);

            assertEquals(3, tileManager.getTileCount());
            Vector3D farLeftAfterRewrite = tileManager.getTile(1).farLeft;
            Vector3D farRightAfterRewrite = tileManager.getTile(1).farRight;
            Vector3D nextNearLeft = tileManager.getTile(2).nearLeft;
            Vector3D nextNearRight = tileManager.getTile(2).nearRight;

            assertTrue(
                    !Vector3D.approxEq(farLeftBeforeRewrite, farLeftAfterRewrite, 1e-6f)
                            || !Vector3D.approxEq(farRightBeforeRewrite, farRightAfterRewrite, 1e-6f)
            );
            assertVectorEquals(nextNearLeft, farLeftAfterRewrite);
            assertVectorEquals(nextNearRight, farRightAfterRewrite);
        } finally {
            tileManager.cleanupGPUResourcesRecursively();
        }
    }
}
