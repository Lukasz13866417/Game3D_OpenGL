package com.example.game3d_opengl.game.terrain.terrain_api.main;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.BaseGridCreator;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridCreatorWrapper;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.AdvancedGridCreator;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridSegment;
import com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.execbuffer.CommandExecutor;

public class AddonsCommandsExecutor implements CommandExecutor {
    // User-callable commands
    public static final int CMD_ADDONS_USER_FIRST = 33;
    public static final int CMD_RESERVE_VERTICAL = 33;
    public static final int CMD_RESERVE_HORIZONTAL = 34;
    public static final int CMD_RESERVE_RANDOM_VERTICAL = 35;
    public static final int CMD_RESERVE_RANDOM_HORIZONTAL = 36;
    public static final int CMD_START_STRUCTURE_ADDONS = 37;
    public static final int CMD_RESERVE_K_RANDOM_FIELDS = 38;
    public static final int CMD_RESERVE_HORIZONTAL_REGION = 40;
    public static final int CMD_RESERVE_RANDOM_HORIZONTAL_REGION = 41;
    public static final int CMD_ADDONS_USER_LAST = 41;

    // Internal commands
    public static final int CMD_FINISH_STRUCTURE_ADDONS = 42;

    private final Terrain terrain;


    public AddonsCommandsExecutor(Terrain terrain) {
        this.terrain = terrain;
    }

    @Override
    public void execute(float[] buffer, int offset, int length) {
        int code = (int) buffer[offset];
        switch (code) {
            case CMD_RESERVE_VERTICAL:
                handleReserveVertical(buffer, offset);
                break;
            case CMD_RESERVE_HORIZONTAL:
                handleReserveHorizontal(buffer, offset);
                break;
            case CMD_RESERVE_RANDOM_VERTICAL:
                handleReserveRandomVertical(buffer, offset);
                break;
            case CMD_RESERVE_RANDOM_HORIZONTAL:
                handleReserveRandomHorizontal(buffer, offset);
                break;
            case CMD_START_STRUCTURE_ADDONS:
                handleStartStructureAddons();
                break;
            case CMD_RESERVE_K_RANDOM_FIELDS:
                handleReserveKRandomFields(buffer, offset);
                break;
            case CMD_RESERVE_HORIZONTAL_REGION:
                handleReserveHorizontalRegion(buffer, offset);
                break;
            case CMD_RESERVE_RANDOM_HORIZONTAL_REGION:
                handleReserveRandomHorizontalRegion(buffer, offset);
                break;
            case CMD_FINISH_STRUCTURE_ADDONS:
                terrain.gridCreatorWrapperQueue.dequeue().finishAddonPhase();
                terrain.rowOffsetQueue.dequeue();
                break;
            default:
                throw new IllegalArgumentException("Unknown command code: " + code);
        }
    }

    private void handleReserveVertical(float[] buffer, int offset) {
        int row = (int) buffer[offset + 2];
        int col = (int) buffer[offset + 3];
        int segLength = (int) buffer[offset + 4];
        BaseGridCreator latest = requireActiveCreator();
        latest.reserveVertical(row, col, segLength);
        processAddons(row, col, segLength, false);
    }

    private void processAddons(int baseRow, int baseCol, int length,  boolean horizontal) {
        int rOffset = terrain.rowOffsetQueue.peek();
        //System.out.println("<> PROCESSING ADDONS: "+baseRow+","+baseCol+","+length+" OFF: "+rOffset);
        for (int i = 0; i < length; ++i) {
            Addon addon = terrain.addonQueue.dequeue();
            int row = horizontal ? baseRow : baseRow + i;
            row += rOffset;
            long tileId = terrain.tileManager.getTileIdForRow(row);
            addon.setTileId(tileId);
            int col = horizontal ? baseCol + i : baseCol;
            TerrainGridField field = terrain.tileManager.getField(row, col);
            addon.place(field.nearLeft, field.nearRight, field.farLeft, field.farRight);
            terrain.addons.pushBack(addon);
        }
    }

    private void handleReserveHorizontal(float[] buffer, int offset) {
        int row = (int) buffer[offset + 2];
        int col = (int) buffer[offset + 3];
        int segLength = (int) buffer[offset + 4];
        BaseGridCreator latest = requireActiveCreator();
        latest.reserveHorizontal(row, col, segLength);
        processAddons(row, col, segLength, true);
    }

    private void handleReserveRandomVertical(float[] buffer, int offset) {
        int segLength = (int) buffer[offset + 2];
        BaseGridCreator creator = requireActiveCreator();
        assert creator instanceof AdvancedGridCreator;
        AdvancedGridCreator latest = (AdvancedGridCreator) creator;
        GridSegment found = latest.reserveRandomFittingVertical(segLength);
        processAddons(found.row, found.col, segLength, false);
    }

    private void handleReserveRandomHorizontal(float[] buffer, int offset) {
        int segLength = (int) buffer[offset + 2];
        BaseGridCreator creator = requireActiveCreator();
        assert creator instanceof AdvancedGridCreator;
        AdvancedGridCreator latest = (AdvancedGridCreator) creator;
        GridSegment found = latest.reserveRandomFittingHorizontal(segLength);
        processAddons(found.row, found.col, segLength, true);
    }

    private void handleReserveKRandomFields(float[] buffer, int offset) {
        int k = (int) buffer[offset + 2];
        BaseGridCreator creator = requireActiveCreator();
        assert creator instanceof AdvancedGridCreator;
        AdvancedGridCreator latest = (AdvancedGridCreator) creator;
        GridSegment[] found = latest.reserveKRandomFields(k);
        for (GridSegment seg : found) {
            processAddons(seg.row, seg.col, 1, false);
        }
    }

    private void handleReserveHorizontalRegion(float[] buffer, int offset) {
        int row = (int) buffer[offset + 2];
        int col = (int) buffer[offset + 3];
        int segLength = (int) buffer[offset + 4];
        BaseGridCreator latest = requireActiveCreator();
        latest.reserveHorizontal(row, col, segLength);
        Addon addon = terrain.addonQueue.dequeue();
        processAddonOnHorizontalRegion(new GridSegment(row, col, segLength), addon);
    }

    private void handleReserveRandomHorizontalRegion(float[] buffer, int offset) {
        int segLength = (int) buffer[offset + 2];
        BaseGridCreator creator = requireActiveCreator();
        assert creator instanceof AdvancedGridCreator;
        AdvancedGridCreator latest = (AdvancedGridCreator) creator;
        GridSegment found = latest.reserveRandomFittingHorizontal(segLength);
        Addon addon = terrain.addonQueue.dequeue();
        processAddonOnHorizontalRegion(found, addon);
    }

    private void handleStartStructureAddons() {
        GridCreatorWrapper wrapper = terrain.gridCreatorWrapperQueue.peek();
        if (wrapper == null) {
            throw new IllegalStateException("No grid creator wrapper available for addon phase start.");
        }
        wrapper.materializeIfNeeded();
    }

    private BaseGridCreator requireActiveCreator() {
        GridCreatorWrapper wrapper = terrain.gridCreatorWrapperQueue.peek();
        if (wrapper == null) {
            throw new IllegalStateException("No active grid creator wrapper.");
        }
        BaseGridCreator creator = wrapper.getContent();
        if (creator == null) {
            throw new IllegalStateException("Grid creator was not materialized before addon command.");
        }
        return creator;
    }

    private void processAddonOnHorizontalRegion(GridSegment seg, Addon addon) {
        int rOffset = terrain.rowOffsetQueue.peek();
        int row = seg.row + rOffset;
        int col = seg.col;
        int length = seg.length;
        long tileId = terrain.tileManager.getTileIdForRow(row);
        addon.setTileId(tileId);
        TerrainGridField field = terrain.tileManager.getHorizontalRegionField(row, col, length);
        addon.place(field.nearLeft, field.nearRight, field.farLeft, field.farRight);
        terrain.addons.pushBack(addon);
    }

    @Override
    public boolean canHandle(float v) {
        int command = (int) v;
        return command >= CMD_ADDONS_USER_FIRST && command <= CMD_FINISH_STRUCTURE_ADDONS;
    }
}