package com.example.game3d_opengl.game.terrain.main;

import com.example.game3d_opengl.engine.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain.addon.Addon;
import com.example.game3d_opengl.game.terrain.grid.symbolic.GridCreator;
import com.example.game3d_opengl.game.terrain.grid.symbolic.GridSegment;
import com.example.game3d_opengl.game.terrain.terrainutil.ArrayStack;
import com.example.game3d_opengl.game.terrain.terrainutil.execbuffer.CommandExecutor;

public class AddonsCommandsExecutor implements CommandExecutor {
    public static final int CMD_RESERVE_VERTICAL = 33;
    public static final int CMD_RESERVE_HORIZONTAL = 34;
    public static final int CMD_RESERVE_RANDOM_VERTICAL = 35;
    public static final int CMD_RESERVE_RANDOM_HORIZONTAL = 36;
    public static final int CMD_FINISH_STRUCTURE_ADDONS = 37;
    public static final int CMD_START_STRUCTURE_ADDONS = 38;

    private final Terrain terrain;

    private final ArrayStack<Integer> rowOffsetStack = new ArrayStack<>();

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
                terrain.structureGridStack.pop().destroy();
                rowOffsetStack.pop();
                break;
            case CMD_START_STRUCTURE_ADDONS:
                handleStartStructureAddons(buffer, offset);
                break;
            default:
                throw new IllegalArgumentException("Unknown command code: " + code);
        }
    }

    private void handleReserveVertical(float[] buffer, int offset) {
        int row = (int) buffer[offset + 2];
        int col = (int) buffer[offset + 3];
        int segLength = (int) buffer[offset + 4];

        assert terrain.structureGridStack.size() == 1;
        GridCreator latest = terrain.structureGridStack.peek();
        latest.reserveVertical(row, col, segLength);
        processAddons(row, col, segLength, latest, false);
    }

    private void processAddons(int baseRow, int baseCol, int length, GridCreator creator, boolean horizontal) {
        assert terrain.structureGridStack.size() == 1;
        int rOffset = rowOffsetStack.peek();
        for (int i = 0; i < length; ++i) {
            Addon addon = terrain.addonStack.pop();
            int row = horizontal ? baseRow : baseRow + i;
            row += rOffset;
            int col = horizontal ? baseCol + i : baseCol;
            Vector3D[] field = terrain.tileBuilder.getField(row,col);
            addon.place(field[0], field[1], field[2], field[3]);
            terrain.addons.pushBack(addon);
        }
    }

    private void handleReserveHorizontal(float[] buffer, int offset) {
        assert terrain.structureGridStack.size() == 1;
        int row = (int) buffer[offset + 2];
        int col = (int) buffer[offset + 3];
        int segLength = (int) buffer[offset + 4];
        GridCreator latest = terrain.structureGridStack.peek();
        latest.reserveHorizontal(row, col, segLength);
        processAddons(row, col, segLength, latest, true);
    }

    private void handleReserveRandomVertical(float[] buffer, int offset) {
        assert terrain.structureGridStack.size() == 1;
        int segLength = (int) buffer[offset + 2];
        GridCreator latest = terrain.structureGridStack.peek();
        GridSegment found = latest.reserveRandomFittingVertical(segLength);
        processAddons(found.row, found.col, segLength, latest, false);
    }

    private void handleReserveRandomHorizontal(float[] buffer, int offset) {
        assert terrain.structureGridStack.size() == 1;
        int segLength = (int) buffer[offset + 2];
        GridCreator latest = terrain.structureGridStack.peek();
        GridSegment found = latest.reserveRandomFittingHorizontal(segLength);
        processAddons(found.row, found.col, segLength, latest, true);
    }

    private void handleStartStructureAddons(float[] buffer, int offset) {
        int nRowsAdded = (int) buffer[offset + 2];
        GridCreator parent = terrain.structureGridStack.peek();
        GridCreator ng = new GridCreator(nRowsAdded, terrain.nCols,
                parent, terrain.lastStructureStartRowCount
        );
        rowOffsetStack.push((int)(buffer[offset + 3]));
        terrain.structureGridStack.push(ng);
        assert terrain.structureGridStack.size() == 1;
    }

    @Override
    public boolean canHandle(float v) {
        return (int)v >= CMD_RESERVE_VERTICAL && (int)v <= CMD_START_STRUCTURE_ADDONS;
    }
}