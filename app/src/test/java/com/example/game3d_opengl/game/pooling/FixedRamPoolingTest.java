package com.example.game3d_opengl.game.pooling;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.game3d_opengl.game.terrain.terrain_api.main.GridResourcePack;
import com.example.game3d_opengl.game.terrain.terrain_api.main.ResourcePackFactory;
import com.example.game3d_opengl.game.terrain.terrain_api.main.TerrainResourcePack;
import com.example.game3d_opengl.game.terrain.terrain_api.main.tilemanager.TileManagerResourcePack;

import org.junit.Test;

public class FixedRamPoolingTest {
    private static final class TrackingOwner extends PooledResourcesOwner {
        private int releaseCount = 0;

        private TrackingOwner() {
            super(null);
        }

        @Override
        public void releasePooledResourcesRecursively() {
            releaseCount++;
        }
    }

    private static final class TrackingLease implements PooledLease {
        private int releaseCount = 0;

        @Override
        public void release() {
            releaseCount++;
        }
    }

    private static final class FailingBuilder
            extends PooledResourcesOwner.BaseBuilder<TrackingOwner, FailingBuilder> {
        private final TrackingLease lease = new TrackingLease();
        private final TrackingOwner childOwner = new TrackingOwner();

        @Override
        protected FailingBuilder self() {
            return this;
        }

        @Override
        public TrackingOwner createWhenReady() {
            take(lease);
            addChild(childOwner);
            throw new IllegalStateException("boom");
        }
    }

    private static void expectIllegalState(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    @Test
    public void fixedPool_reuses_slot_after_release() {
        FixedPool<StringBuilder> pool = new FixedPool<>(1, StringBuilder::new, "exhausted");

        PooledSlotLease<StringBuilder> firstLease = pool.acquire();
        StringBuilder firstResource = firstLease.get();

        expectIllegalState(() -> pool.acquire());

        firstLease.release();

        PooledSlotLease<StringBuilder> secondLease = pool.acquire();
        try {
            assertSame(firstResource, secondLease.get());
        } finally {
            secondLease.release();
        }
    }

    @Test
    public void builder_rolls_back_child_owners_and_leases_on_failure() {
        FailingBuilder builder = new FailingBuilder();

        expectIllegalState(() -> builder.build());

        assertTrue(builder.lease.releaseCount == 1);
        assertTrue(builder.childOwner.releaseCount == 1);
    }

    @Test
    public void tileManager_resource_pack_release_restores_fixed_slots() {
        TileManagerResourcePack resourcePack = TileManagerResourcePack.defaultInstance();
        PooledSlotLease<?> first = resourcePack.rowInfoPool().acquire();
        PooledSlotLease<?> second = resourcePack.rowInfoPool().acquire();
        PooledSlotLease<?> recycled = null;
        try {
            expectIllegalState(() -> resourcePack.rowInfoPool().acquire());
            first.release();
            recycled = resourcePack.rowInfoPool().acquire();
        } finally {
            if (recycled != null) {
                recycled.release();
            }
            second.release();
            first.release();
        }
    }

    @Test
    public void terrain_resource_pack_release_restores_command_buffer_slot() {
        TerrainResourcePack resourcePack = TerrainResourcePack.defaultInstance();
        PooledSlotLease<float[]> firstTerrainLease = null;
        PooledSlotLease<float[]> secondTerrainLease = null;
        PooledSlotLease<float[]> recycled = null;
        try {
            firstTerrainLease = resourcePack.commandBufferPool().acquire();
            secondTerrainLease = resourcePack.commandBufferPool().acquire();
            expectIllegalState(() -> resourcePack.commandBufferPool().acquire());

            secondTerrainLease.release();
            secondTerrainLease = null;
            recycled = resourcePack.commandBufferPool().acquire();
        } finally {
            if (recycled != null) {
                recycled.release();
            }
            if (secondTerrainLease != null) {
                secondTerrainLease.release();
            }
            if (firstTerrainLease != null) {
                firstTerrainLease.release();
            }
        }
    }

    @Test
    public void terrain_resource_pack_factory_propagates_dependency_factory_failure() {
        ResourcePackFactory<TileManagerResourcePack> failingTileFactory =
                new ResourcePackFactory<TileManagerResourcePack>() {
                    @Override
                    public TileManagerResourcePack create() {
                        throw new IllegalStateException("tile pack create failure");
                    }
                };
        ResourcePackFactory<GridResourcePack> gridFactory =
                new GridResourcePack.Factory();

        expectIllegalState(() -> new TerrainResourcePack.Factory(
                failingTileFactory,
                gridFactory
        ).create());
    }
}
