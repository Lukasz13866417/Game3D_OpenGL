package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos.red_black.RbNodePool;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos.treap.TreapNodePool;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_length.segtree_implementation.NodePool;

public final class PartialSegmentHandlerResourcePack {
    private static final int SEGTREE_NODE_POOL_CAPACITY = 512;
    private static final int ORDERED_SEGMENT_NODE_POOL_CAPACITY = 64;
    private static final int GRID_BUILD_SCRATCH_CAPACITY = 128;

    private final NodePool segtreeNodePool;
    private final TreapNodePool treapNodePool;
    private final RbNodePool rbNodePool;
    private final GridBuildScratch gridBuildScratch;

    private PartialSegmentHandlerResourcePack(
            NodePool segtreeNodePool,
            TreapNodePool treapNodePool,
            RbNodePool rbNodePool,
            GridBuildScratch gridBuildScratch
    ) {
        this.segtreeNodePool = segtreeNodePool;
        this.treapNodePool = treapNodePool;
        this.rbNodePool = rbNodePool;
        this.gridBuildScratch = gridBuildScratch;
    }

    public NodePool segtreeNodePool() {
        return segtreeNodePool;
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
            NodePool segtreeNodePool = new NodePool(SEGTREE_NODE_POOL_CAPACITY);
            TreapNodePool treapNodePool = new TreapNodePool(ORDERED_SEGMENT_NODE_POOL_CAPACITY);
            RbNodePool rbNodePool = new RbNodePool(ORDERED_SEGMENT_NODE_POOL_CAPACITY);
            GridBuildScratch gridBuildScratch = new GridBuildScratch(GRID_BUILD_SCRATCH_CAPACITY);
            return new PartialSegmentHandlerResourcePack(
                    segtreeNodePool,
                    treapNodePool,
                    rbNodePool,
                    gridBuildScratch
            );
        }
    }
}
