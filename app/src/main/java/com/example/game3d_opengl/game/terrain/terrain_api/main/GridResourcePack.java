package com.example.game3d_opengl.game.terrain.terrain_api.main;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.PartialSegmentHandlerResourcePack;

/**
 * Holder for grid-related pooled resources.
 * Concrete grid pools can be added here as grid internals migrate to this pack.
 */
public final class GridResourcePack {
    private static final GridResourcePack DEFAULT_INSTANCE = new Factory().create();

    private final PartialSegmentHandlerResourcePack partialSegmentHandlerResourcePack;

    private GridResourcePack() {
        this.partialSegmentHandlerResourcePack = new PartialSegmentHandlerResourcePack.Factory().create();
    }

    public static GridResourcePack defaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public PartialSegmentHandlerResourcePack partialSegmentHandlerResourcePack() {
        return partialSegmentHandlerResourcePack;
    }

    public static final class Factory implements ResourcePackFactory<GridResourcePack> {
        @Override
        public GridResourcePack create() {
            return new GridResourcePack();
        }
    }
}
