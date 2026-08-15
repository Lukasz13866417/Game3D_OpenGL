package com.example.game3d.authoring.grid.symbolic.advanced.segments;

import com.example.game3d.authoring.DeterministicRandom;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.by_end_pos.red_black.RbNodePool;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.by_end_pos.treap.TreapNodePool;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.by_length.treap.LengthTreapNodePool;

public final class PartialSegmentHandlerResourcePack {
    private static final int LENGTH_TREAP_NODE_POOL_CAPACITY = 512;
    private static final int ORDERED_SEGMENT_NODE_POOL_CAPACITY = 64;
    private static final int GRID_BUILD_SCRATCH_CAPACITY = 128;

    private final LengthTreapNodePool lengthTreapNodePool;
    private final TreapNodePool treapNodePool;
    private final RbNodePool rbNodePool;
    private final GridBuildScratch gridBuildScratch;
    private final DeterministicRandom random;

    private PartialSegmentHandlerResourcePack(
            LengthTreapNodePool lengthTreapNodePool,
            TreapNodePool treapNodePool,
            RbNodePool rbNodePool,
            GridBuildScratch gridBuildScratch,
            DeterministicRandom random
    ) {
        this.lengthTreapNodePool = lengthTreapNodePool;
        this.treapNodePool = treapNodePool;
        this.rbNodePool = rbNodePool;
        this.gridBuildScratch = gridBuildScratch;
        this.random = random;
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

    /** Random source owned by this one symbolic-grid materialization/session. */
    public DeterministicRandom random() {
        return random;
    }

    /**
     * Creates a fresh, deterministic resource set for convenience constructors.
     * No nodes, scratch buffers, or random state are shared with another grid.
     */
    public static PartialSegmentHandlerResourcePack createDefault() {
        return seeded(0L);
    }

    public static PartialSegmentHandlerResourcePack seeded(long seed) {
        return using(new DeterministicRandom(seed));
    }

    public static PartialSegmentHandlerResourcePack using(DeterministicRandom random) {
        if (random == null) {
            throw new IllegalArgumentException("random == null");
        }
        return new Factory(random).create();
    }

    public static final class Factory {
        private final DeterministicRandom random;

        public Factory() {
            this(new DeterministicRandom(0L));
        }

        public Factory(long seed) {
            this(new DeterministicRandom(seed));
        }

        public Factory(DeterministicRandom random) {
            if (random == null) {
                throw new IllegalArgumentException("random == null");
            }
            this.random = random;
        }

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
                    gridBuildScratch,
                    random
            );
        }
    }
}
