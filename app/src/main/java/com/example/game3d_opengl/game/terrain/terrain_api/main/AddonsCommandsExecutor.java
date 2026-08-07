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
    public static final int CMD_RESERVE_K_RANDOM_FIELDS = 38;
    public static final int CMD_RESERVE_HORIZONTAL_REGION = 40;
    public static final int CMD_RESERVE_RANDOM_HORIZONTAL_REGION = 41;
    public static final int CMD_ADDONS_USER_LAST = 41;

    // Internal commands
    public static final int CMD_BEGIN_STRUCTURE_ADDONS = 42;
    public static final int CMD_BUILD_AGC_HORIZONTAL = 43;
    public static final int CMD_BUILD_AGC_VERTICAL = 44;
    public static final int CMD_FINALIZE_AGC = 45;
    public static final int CMD_EMIT_STRUCTURE_ADDONS = 46;
    public static final int CMD_FINISH_STRUCTURE_ADDONS = 47;

    private final Terrain terrain;
    private final float[] fieldCornersScratch = new float[12];


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
            case CMD_BEGIN_STRUCTURE_ADDONS:
                handleBeginStructureAddons();
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
            case CMD_BUILD_AGC_HORIZONTAL:
                requireActiveWrapper().buildAdvancedHorizontalIfNeeded();
                break;
            case CMD_BUILD_AGC_VERTICAL:
                requireActiveWrapper().buildAdvancedVerticalIfNeeded();
                break;
            case CMD_FINALIZE_AGC:
                requireActiveWrapper().finalizeAdvancedMaterialization();
                break;
            case CMD_EMIT_STRUCTURE_ADDONS:
                handleEmitStructureAddons();
                break;
            case CMD_FINISH_STRUCTURE_ADDONS:
                handleFinishStructureAddons();
                break;
            default:
                throw new IllegalArgumentException("Unknown command code: " + code);
        }
    }

    private void handleReserveVertical(float[] buffer, int offset) {
        int row = (int) buffer[offset + 2];
        int col = (int) buffer[offset + 3];
        int segLength = (int) buffer[offset + 4];
        GridCreatorWrapper wrapper = requireActiveWrapper();
        BaseGridCreator latest = requireActiveCreator(wrapper);
        try {
            latest.reserveVertical(row, col, segLength);
        } catch (RuntimeException e) {
            throw wrapReservationFailure("reserveVertical", wrapper, row, col, segLength, e);
        }
        processAddons(row, col, segLength, false);
    }

    private void processAddons(int baseRow, int baseCol, int length,  boolean horizontal) {
        int rOffset = terrain.rowOffsetQueue.peek();
        //System.out.println("<> PROCESSING ADDONS: "+baseRow+","+baseCol+","+length+" OFF: "+rOffset);
        for (int i = 0; i < length; ++i) {
            Addon addon = terrain.dequeuePendingAddon();
            int row = horizontal ? baseRow : baseRow + i;
            row += rOffset;
            long tileId = terrain.tileManager.getTileIdForRow(row);
            addon.setTileId(tileId);
            int col = horizontal ? baseCol + i : baseCol;
            terrain.tileManager.writeFieldCorners(row, col, fieldCornersScratch);
            addon.place(
                    fieldCornersScratch[0], fieldCornersScratch[1], fieldCornersScratch[2],
                    fieldCornersScratch[3], fieldCornersScratch[4], fieldCornersScratch[5],
                    fieldCornersScratch[6], fieldCornersScratch[7], fieldCornersScratch[8],
                    fieldCornersScratch[9], fieldCornersScratch[10], fieldCornersScratch[11]
            );
            terrain.addPlacedAddon(addon);
        }
    }

    private void handleReserveHorizontal(float[] buffer, int offset) {
        int row = (int) buffer[offset + 2];
        int col = (int) buffer[offset + 3];
        int segLength = (int) buffer[offset + 4];
        GridCreatorWrapper wrapper = requireActiveWrapper();
        BaseGridCreator latest = requireActiveCreator(wrapper);
        try {
            latest.reserveHorizontal(row, col, segLength);
        } catch (RuntimeException e) {
            throw wrapReservationFailure("reserveHorizontal", wrapper, row, col, segLength, e);
        }
        processAddons(row, col, segLength, true);
    }

    private void handleReserveRandomVertical(float[] buffer, int offset) {
        int segLength = (int) buffer[offset + 2];
        GridCreatorWrapper wrapper = requireActiveWrapper();
        BaseGridCreator creator = requireActiveCreator(wrapper);
        assert creator instanceof AdvancedGridCreator;
        AdvancedGridCreator latest = (AdvancedGridCreator) creator;
        GridSegment found;
        try {
            found = latest.reserveRandomFittingVertical(segLength);
        } catch (RuntimeException e) {
            throw wrapReservationFailure("reserveRandomVertical", wrapper, -1, -1, segLength, e);
        }
        processAddons(found.row, found.col, segLength, false);
    }

    private void handleReserveRandomHorizontal(float[] buffer, int offset) {
        int segLength = (int) buffer[offset + 2];
        GridCreatorWrapper wrapper = requireActiveWrapper();
        BaseGridCreator creator = requireActiveCreator(wrapper);
        assert creator instanceof AdvancedGridCreator;
        AdvancedGridCreator latest = (AdvancedGridCreator) creator;
        GridSegment found;
        try {
            found = latest.reserveRandomFittingHorizontal(segLength);
        } catch (RuntimeException e) {
            throw wrapReservationFailure("reserveRandomHorizontal", wrapper, -1, -1, segLength, e);
        }
        processAddons(found.row, found.col, segLength, true);
    }

    private void handleReserveKRandomFields(float[] buffer, int offset) {
        int k = (int) buffer[offset + 2];
        GridCreatorWrapper wrapper = requireActiveWrapper();
        BaseGridCreator creator = requireActiveCreator(wrapper);
        assert creator instanceof AdvancedGridCreator;
        AdvancedGridCreator latest = (AdvancedGridCreator) creator;
        GridSegment[] found;
        try {
            found = latest.reserveKRandomFields(k);
        } catch (RuntimeException e) {
            throw wrapReservationFailure("reserveKRandomFields", wrapper, -1, -1, k, e);
        }
        for (GridSegment seg : found) {
            processAddons(seg.row, seg.col, 1, false);
        }
    }

    private void handleReserveHorizontalRegion(float[] buffer, int offset) {
        int row = (int) buffer[offset + 2];
        int col = (int) buffer[offset + 3];
        int segLength = (int) buffer[offset + 4];
        GridCreatorWrapper wrapper = requireActiveWrapper();
        BaseGridCreator latest = requireActiveCreator(wrapper);
        try {
            latest.reserveHorizontal(row, col, segLength);
        } catch (RuntimeException e) {
            throw wrapReservationFailure("reserveHorizontalRegion", wrapper, row, col, segLength, e);
        }
        Addon addon = terrain.dequeuePendingAddon();
        processAddonOnHorizontalRegion(new GridSegment(row, col, segLength), addon);
    }

    private void handleReserveRandomHorizontalRegion(float[] buffer, int offset) {
        int segLength = (int) buffer[offset + 2];
        GridCreatorWrapper wrapper = requireActiveWrapper();
        BaseGridCreator creator = requireActiveCreator(wrapper);
        assert creator instanceof AdvancedGridCreator;
        AdvancedGridCreator latest = (AdvancedGridCreator) creator;
        GridSegment found;
        try {
            found = latest.reserveRandomFittingHorizontal(segLength);
        } catch (RuntimeException e) {
            throw wrapReservationFailure("reserveRandomHorizontalRegion", wrapper, -1, -1, segLength, e);
        }
        Addon addon = terrain.dequeuePendingAddon();
        processAddonOnHorizontalRegion(found, addon);
    }

    private void handleBeginStructureAddons() {
        GridCreatorWrapper wrapper = requireActiveWrapper();
        if (wrapper.isAdvancedStructure()) {
            wrapper.beginAdvancedMaterialization();
            return;
        }
        wrapper.materializeBasicIfNeeded();
    }

    private void handleEmitStructureAddons() {
        Terrain.DeferredAddonPhase phase = requireActiveDeferredAddonPhase();
        terrain.beginDeferredAddonEmission(this);
        try {
            phase.structure.generateAddons(terrain, phase.nRows, phase.nCols);
            terrain.executeDeferredAddonCommands();
        } finally {
            terrain.finishDeferredAddonEmission();
        }
    }

    private void handleFinishStructureAddons() {
        terrain.gridCreatorWrapperQueue.dequeue().finishAddonPhase();
        terrain.rowOffsetQueue.dequeue();
        terrain.dequeueDeferredAddonPhase();
        terrain.recordStructureAddonFinish();
    }

    private GridCreatorWrapper requireActiveWrapper() {
        GridCreatorWrapper wrapper = terrain.gridCreatorWrapperQueue.peek();
        if (wrapper == null) {
            throw new IllegalStateException("No active grid creator wrapper.");
        }
        return wrapper;
    }

    private Terrain.DeferredAddonPhase requireActiveDeferredAddonPhase() {
        Terrain.DeferredAddonPhase phase = terrain.peekDeferredAddonPhase();
        if (phase == null) {
            throw new IllegalStateException("No deferred addon phase is active.");
        }
        return phase;
    }

    private BaseGridCreator requireActiveCreator(GridCreatorWrapper wrapper) {
        BaseGridCreator creator = wrapper.getContent();
        if (creator == null) {
            throw new IllegalStateException("Grid creator was not materialized before addon command.");
        }
        return creator;
    }

    private RuntimeException wrapReservationFailure(
            String operation,
            GridCreatorWrapper wrapper,
            int row,
            int col,
            int length,
            RuntimeException cause
    ) {
        String message = "Addon reservation failed: op=" + operation
                + " row=" + row
                + " col=" + col
                + " len=" + length
                + " structure=" + wrapper.describeForDebug();
        System.out.println("<> " + message);
        BaseGridCreator creator = wrapper.getContent();
        if (creator != null) {
            creator.printMetaData();
            creator.printGrid();
        }
        if (cause instanceof IllegalArgumentException) {
            return new IllegalArgumentException(message, cause);
        }
        return new RuntimeException(message, cause);
    }

    private void processAddonOnHorizontalRegion(GridSegment seg, Addon addon) {
        int rOffset = terrain.rowOffsetQueue.peek();
        int row = seg.row + rOffset;
        int col = seg.col;
        int length = seg.length;
        long tileId = terrain.tileManager.getTileIdForRow(row);
        addon.setTileId(tileId);
        terrain.tileManager.writeHorizontalRegionFieldCorners(row, col, length, fieldCornersScratch);
        addon.place(
                fieldCornersScratch[0], fieldCornersScratch[1], fieldCornersScratch[2],
                fieldCornersScratch[3], fieldCornersScratch[4], fieldCornersScratch[5],
                fieldCornersScratch[6], fieldCornersScratch[7], fieldCornersScratch[8],
                fieldCornersScratch[9], fieldCornersScratch[10], fieldCornersScratch[11]
        );
        terrain.addPlacedAddon(addon);
    }

    @Override
    public boolean canHandle(float v) {
        int command = (int) v;
        return command >= CMD_ADDONS_USER_FIRST && command <= CMD_FINISH_STRUCTURE_ADDONS;
    }
}
