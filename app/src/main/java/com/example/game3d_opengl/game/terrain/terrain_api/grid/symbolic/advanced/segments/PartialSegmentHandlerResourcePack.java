package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos.red_black.RbNodePool;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos.treap.TreapNodePool;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_length.treap.LengthTreapNodePool;

public final class PartialSegmentHandlerResourcePack {
    private static final int LENGTH_TREAP_NODE_POOL_CAPACITY = 512;
    private static final int ORDERED_SEGMENT_NODE_POOL_CAPACITY = 64;
    private static final int GRID_BUILD_SCRATCH_CAPACITY = 128;

    private final LengthTreapNodePool lengthTreapNodePool;
    private final TreapNodePool treapNodePool;
    private final RbNodePool rbNodePool;
    private final GridBuildScratch gridBuildScratch;

    private PartialSegmentHandlerResourcePack(
            LengthTreapNodePool lengthTreapNodePool,
            TreapNodePool treapNodePool,
            RbNodePool rbNodePool,
            GridBuildScratch gridBuildScratch
    ) {
        this.lengthTreapNodePool = lengthTreapNodePool;
        this.treapNodePool = treapNodePool;
        this.rbNodePool = rbNodePool;
        this.gridBuildScratch = gridBuildScratch;
    }

    public LengthTreapNodePool lengthTreapNodePool() {
        return lengthTreapNodePool;
    }

    public TreapNodePool treapNodePool() {
        return treapNodePool;
    }

    public RbNodePool rbNodePool() {
        return rbNodePool;
    }

    public GridBuildScratch gridBuildScratch() {
        return gridBuildScratch;
    }

    public static final class Factory {
        public PartialSegmentHandlerResourcePack create() {
            LengthTreapNodePool lengthTreapNodePool =
                    new LengthTreapNodePool(LENGTH_TREAP_NODE_POOL_CAPACITY);
            TreapNodePool treapNodePool = new TreapNodePool(ORDERED_SEGMENT_NODE_POOL_CAPACITY);
            RbNodePool rbNodePool = new RbNodePool(ORDERED_SEGMENT_NODE_POOL_CAPACITY);
            GridBuildScratch gridBuildScratch = new GridBuildScratch(GRID_BUILD_SCRATCH_CAPACITY);
            return new PartialSegmentHandlerResourcePack(
                    lengthTreapNodePool,
                    treapNodePool,
                    rbNodePool,
                    gridBuildScratch
            );
        }
    }
}
