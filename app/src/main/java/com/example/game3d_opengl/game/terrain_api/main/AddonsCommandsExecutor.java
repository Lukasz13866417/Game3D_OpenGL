package com.example.game3d_opengl.game.terrain_api.main;

import android.util.Pair;

import com.example.game3d_opengl.game.terrain_api.Tile;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain_api.grid.symbolic.GridCreator;
import com.example.game3d_opengl.game.terrain_api.grid.symbolic.GridSegment;
import com.example.game3d_opengl.game.terrain_api.terrainutil.execbuffer.CommandExecutor;

public class AddonsCommandsExecutor implements CommandExecutor {
    // User-callable commands
    public static final int CMD_ADDONS_USER_FIRST = 33;
    public static final int CMD_RESERVE_VERTICAL = 33;
    public static final int CMD_RESERVE_HORIZONTAL = 34;
    public static final int CMD_RESERVE_RANDOM_VERTICAL = 35;
    public static final int CMD_RESERVE_RANDOM_HORIZONTAL = 36;
    public static final int CMD_START_STRUCTURE_ADDONS = 37;
    public static final int CMD_ADDONS_USER_LAST = 37;

    // Internal commands
    public static final int CMD_FINISH_STRUCTURE_ADDONS = 38;

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
            case CMD_FINISH_STRUCTURE_ADDONS:
                terrain.gridCreatorWrapperQueue.dequeue().content.destroy();
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
        GridCreator latest = terrain.gridCreatorWrapperQueue.peek().content;
        latest.reserveVertical(row, col, segLength);
        processAddons(row, col, segLength, latest, false);
    }

    private void processAddons(int baseRow, int baseCol, int length, GridCreator creator, boolean horizontal) {
        int rOffset = terrain.rowOffsetQueue.peek();
        for (int i = 0; i < length; ++i) {
            Addon addon = terrain.addonQueue.dequeue();
             
            
            int row = horizontal ? baseRow : baseRow + i;
            row += rOffset;
            long tileId = terrain.tileBuilder.getTileIdForRow(row);
            addon.setTileId(tileId);
            int col = horizontal ? baseCol + i : baseCol;
            Vector3D[] field = terrain.tileBuilder.getField(row,col);
            addon.place(field[0], field[1], field[2], field[3]);
            terrain.addons.pushBack(addon);
        }
    }

    private void handleReserveHorizontal(float[] buffer, int offset) {
        int row = (int) buffer[offset + 2];
        int col = (int) buffer[offset + 3];
        int segLength = (int) buffer[offset + 4];
        GridCreator latest = terrain.gridCreatorWrapperQueue.peek().content;
        latest.reserveHorizontal(row, col, segLength);
        processAddons(row, col, segLength, latest, true);
    }

    private void handleReserveRandomVertical(float[] buffer, int offset) {
        int segLength = (int) buffer[offset + 2];
        GridCreator latest = terrain.gridCreatorWrapperQueue.peek().content;
        GridSegment found = latest.reserveRandomFittingVertical(segLength);
        processAddons(found.row, found.col, segLength, latest, false);
    }

    private void handleReserveRandomHorizontal(float[] buffer, int offset) {
        int segLength = (int) buffer[offset + 2];
        GridCreator latest = terrain.gridCreatorWrapperQueue.peek().content;
        GridSegment found = latest.reserveRandomFittingHorizontal(segLength);
        processAddons(found.row, found.col, segLength, latest, true);
    }

    @Override
    public boolean canHandle(float v) {
        int command = (int) v;
        return command >= CMD_ADDONS_USER_FIRST && command <= CMD_FINISH_STRUCTURE_ADDONS;
    }
}