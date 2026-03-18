package com.example.game3d_opengl.game.terrain.terrain_api.main.tilemanager;

import com.example.game3d_opengl.game.pooling.FixedPool;
import com.example.game3d_opengl.game.terrain.terrain_api.main.ResourcePackFactory;

/**
 * Holder for tile-manager-related fixed pools.
 */
public final class TileManagerResourcePack {
    private static final TileManagerResourcePack DEFAULT_INSTANCE =
            new Factory().create();

    private static final int MAX_BUFFER_COUNT = 2;
    private static final int ROW_INFO_MAX_SIZE = 100_000;
    private static final int SEGMENT_HISTORY_MAX_SIZE = 100_000;

    private final FixedPool<TileManager.GridRowInfo[]> rowInfoPool;
    private final FixedPool<TileManager.SegmentHistory[]> segmentHistoryPool;

    private TileManagerResourcePack() {
        this.rowInfoPool = new FixedPool<>(
                MAX_BUFFER_COUNT,
                TileManagerResourcePack::createRowInfoBuffer,
                "No more available preallocated row-info buffers."
        );
        this.segmentHistoryPool = new FixedPool<>(
                MAX_BUFFER_COUNT,
                TileManagerResourcePack::createSegmentHistoryBuffer,
                "No more available preallocated segment-history buffers."
        );
    }

    private static TileManager.GridRowInfo[] createRowInfoBuffer() {
        TileManager.GridRowInfo[] buffer = new TileManager.GridRowInfo[ROW_INFO_MAX_SIZE];
        for (int i = 0; i < ROW_INFO_MAX_SIZE; ++i) {
            buffer[i] = new TileManager.GridRowInfo();
        }
        return buffer;
    }

    private static TileManager.SegmentHistory[] createSegmentHistoryBuffer() {
        TileManager.SegmentHistory[] buffer = new TileManager.SegmentHistory[SEGMENT_HISTORY_MAX_SIZE];
        for (int i = 0; i < SEGMENT_HISTORY_MAX_SIZE; ++i) {
            buffer[i] = new TileManager.SegmentHistory();
        }
        return buffer;
    }

    public static TileManagerResourcePack defaultInstance() {
        return DEFAULT_INSTANCE;
    }

    public FixedPool<TileManager.GridRowInfo[]> rowInfoPool() {
        return rowInfoPool;
    }

    public FixedPool<TileManager.SegmentHistory[]> segmentHistoryPool() {
        return segmentHistoryPool;
    }

    public static final class Factory implements ResourcePackFactory<TileManagerResourcePack> {
        @Override
        public TileManagerResourcePack create() {
            return new TileManagerResourcePack();
        }
    }
}
