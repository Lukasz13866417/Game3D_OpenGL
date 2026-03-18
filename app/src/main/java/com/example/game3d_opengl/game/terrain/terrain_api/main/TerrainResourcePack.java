package com.example.game3d_opengl.game.terrain.terrain_api.main;

import com.example.game3d_opengl.game.pooling.FixedPool;
import com.example.game3d_opengl.game.terrain.terrain_api.main.tilemanager.TileManagerResourcePack;

/**
 * Holder for terrain-related pooled resources and child resource packs.
 */
public final class TerrainResourcePack {
    private static final TerrainResourcePack DEFAULT_INSTANCE =
            new Factory(
                    new TileManagerResourcePack.Factory(),
                    new GridResourcePack.Factory()
            ).create(); 

    private static final int MAX_COMMAND_BUFFER_COUNT = 2;
    private static final int COMMAND_BUFFER_MAX_SIZE = 20_000;

    private final FixedPool<float[]> commandBufferPool;
    private final TileManagerResourcePack tileManagerResourcePack;
    private final GridResourcePack gridResourcePack;

    private TerrainResourcePack(TileManagerResourcePack tileManagerResourcePack,
                               GridResourcePack gridResourcePack) {
        this.commandBufferPool = new FixedPool<>(
                MAX_COMMAND_BUFFER_COUNT,
                () -> new float[COMMAND_BUFFER_MAX_SIZE],
                "No more available preallocated command buffers."
        );
        this.tileManagerResourcePack = tileManagerResourcePack;
        this.gridResourcePack = gridResourcePack;
    }

    public static TerrainResourcePack defaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public FixedPool<float[]> commandBufferPool() {
        return commandBufferPool;
    }

    public TileManagerResourcePack tileManagerResourcePack() {
        return tileManagerResourcePack;
    }

    public GridResourcePack gridResourcePack() {
        return gridResourcePack;
    }

    public static final class Factory implements ResourcePackFactory<TerrainResourcePack> {
        private final ResourcePackFactory<TileManagerResourcePack> tileManagerResourcePackFactory;
        private final ResourcePackFactory<GridResourcePack> gridResourcePackFactory;

        public Factory(
                ResourcePackFactory<TileManagerResourcePack> tileManagerResourcePackFactory,
                ResourcePackFactory<GridResourcePack> gridResourcePackFactory
        ) {
            this.tileManagerResourcePackFactory = tileManagerResourcePackFactory;
            this.gridResourcePackFactory = gridResourcePackFactory;
        }

        @Override
        public TerrainResourcePack create() {
            TileManagerResourcePack tileManagerPack = tileManagerResourcePackFactory.create();
            GridResourcePack gridPack = gridResourcePackFactory.create();
            return new TerrainResourcePack(tileManagerPack, gridPack);
        }
    }
}
