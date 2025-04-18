package com.example.game3d_opengl.game.terrain_api.main;

import com.example.game3d_opengl.game.terrain_api.grid.symbolic.GridCreator;
import com.example.game3d_opengl.game.terrain_api.grid.symbolic.GridCreatorWrapper;
import com.example.game3d_opengl.game.terrain_api.terrainutil.execbuffer.CommandExecutor;

public class LandscapeCommandsExecutor implements CommandExecutor {
    public static final int CMD_SET_H_ANG = 1;
    public static final int CMD_SET_V_ANG = 2;
    public static final int CMD_ADD_H_ANG = 3;
    public static final int CMD_ADD_V_ANG = 4;
    public static final int CMD_ADD_SEG = 5;
    public static final int CMD_FINISH_STRUCTURE_LANDSCAPE = 6;
    public static final int CMD_START_STRUCTURE_LANDSCAPE = 7;
    private final Terrain terrain;

    public LandscapeCommandsExecutor(Terrain terrain) {
        this.terrain = terrain;
    }

    @Override
    public void execute(float[] buffer, int offset, int length) {
        int code = (int) buffer[offset];
        switch (code) {
            case CMD_SET_H_ANG:
                float angleH = buffer[offset + 2];
                terrain.tileBuilder.setHorizontalAngle(angleH);
                break;
            case CMD_SET_V_ANG:
                float angleV = buffer[offset + 2];
                terrain.tileBuilder.setVerticalAngle(angleV);
                break;
            case CMD_ADD_H_ANG:
                float deltaH = buffer[offset + 2];
                terrain.tileBuilder.addHorizontalAngle(deltaH);
                break;
            case CMD_ADD_V_ANG:
                float deltaV = buffer[offset + 2];
                terrain.tileBuilder.addVerticalAngle(deltaV);
                break;
            case CMD_ADD_SEG:
                terrain.tileBuilder.addSegment();
                break;
            case CMD_START_STRUCTURE_LANDSCAPE:
                boolean isChild = (int) (buffer[offset + 2]) != 0;
                TerrainStructure what;
                if(!isChild){
                    what = terrain.waitingStructuresQueue.dequeue();
                }else{
                    what = terrain.childStructuresQueue.dequeue();
                }
                terrain.structureStack.push(what);
                terrain.gridCreatorWrapperStack.push(new GridCreatorWrapper());
                if (!isChild) {
                    what.generateTiles(terrain.tileBrush);
                    terrain.commandBuffer.addCommand(CMD_FINISH_STRUCTURE_LANDSCAPE);
                }
                terrain.rowCountStack.push(terrain.tileBuilder.currRowCount);
                break;
            case CMD_FINISH_STRUCTURE_LANDSCAPE:
                TerrainStructure thatStructure = terrain.structureStack.pop();
                int startRowCount = terrain.rowCountStack.pop();
                GridCreatorWrapper myGridCreatorWrapper = terrain.gridCreatorWrapperStack.pop();
                GridCreatorWrapper parentGridCreatorWrapper = terrain.gridCreatorWrapperStack.peek();
                int nRowsAdded = terrain.tileBuilder.currRowCount - startRowCount;
                myGridCreatorWrapper.content = new GridCreator(
                        nRowsAdded, terrain.nCols, parentGridCreatorWrapper,
                        startRowCount
                );
                terrain.gridCreatorWrapperQueue.enqueue(myGridCreatorWrapper);
                terrain.rowOffsetQueue.enqueue(startRowCount);
                thatStructure.generateAddons(terrain.gridBrush, nRowsAdded, terrain.nCols);
                terrain.commandBuffer.addCommand(AddonsCommandsExecutor.CMD_FINISH_STRUCTURE_ADDONS);
                break;
            default:
                throw new IllegalArgumentException("Unknown command code: " + code);
        }
    }

    @Override
    public boolean canHandle(float v) {
        return (int)v >= CMD_SET_H_ANG && (int)v <= CMD_START_STRUCTURE_LANDSCAPE;
    }
}