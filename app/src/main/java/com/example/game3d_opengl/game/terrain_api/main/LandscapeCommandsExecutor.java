package com.example.game3d_opengl.game.terrain_api.main;

import com.example.game3d_opengl.game.terrain_api.grid.symbolic.advanced.AdvancedGridCreator;
import com.example.game3d_opengl.game.terrain_api.grid.symbolic.GridCreatorWrapper;
import com.example.game3d_opengl.game.terrain_api.grid.symbolic.basic.BasicGridCreator;
import com.example.game3d_opengl.game.terrain_api.terrainutil.execbuffer.CommandExecutor;

public class LandscapeCommandsExecutor implements CommandExecutor {
    // User-callable commands
    public static final int CMD_LANDSCAPE_USER_FIRST = 1;
    public static final int CMD_SET_H_ANG = 1;
    public static final int CMD_SET_V_ANG = 2;
    public static final int CMD_ADD_H_ANG = 3;
    public static final int CMD_ADD_V_ANG = 4;
    public static final int CMD_ADD_SEG = 5;
    public static final int CMD_ADD_EMPTY_SEG = 6;
    public static final int CMD_LIFT_UP = 7;
    public static final int CMD_START_STRUCTURE_LANDSCAPE = 8;
    public static final int CMD_SET_ALPHAS = 9;
    public static final int CMD_LANDSCAPE_USER_LAST = 9;

    // Internal commands
    public static final int CMD_FINISH_STRUCTURE_LANDSCAPE = 10;

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
                terrain.tileManager.setHorizontalAngle(angleH);
                break;
            case CMD_SET_V_ANG:
                float angleV = buffer[offset + 2];
                terrain.tileManager.setVerticalAngle(angleV);
                break;
            case CMD_ADD_H_ANG:
                float deltaH = buffer[offset + 2];
                terrain.tileManager.addHorizontalAngle(deltaH);
                break;
            case CMD_ADD_V_ANG:
                float deltaV = buffer[offset + 2];
                terrain.tileManager.addVerticalAngle(deltaV);
                break;
            case CMD_ADD_SEG:
                terrain.tileManager.addSegment(false);
                break;
            case CMD_ADD_EMPTY_SEG:
                terrain.tileManager.addSegment(true);
                break;
            case CMD_LIFT_UP:
                float dy = buffer[offset + 2];
                terrain.tileManager.liftUp(dy);
                break;
            case CMD_START_STRUCTURE_LANDSCAPE:
                boolean isChild = (int) (buffer[offset + 2]) != 0;
                BaseTerrainStructure<?> what;
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
                terrain.rowCountStack.push(terrain.tileManager.getCurrRowCount());
                break;
            case CMD_FINISH_STRUCTURE_LANDSCAPE:
                BaseTerrainStructure<?> thatStructure = terrain.structureStack.pop();
                int startRowCount = terrain.rowCountStack.pop();
                GridCreatorWrapper myGridCreatorWrapper = terrain.gridCreatorWrapperStack.pop();
                GridCreatorWrapper parentGridCreatorWrapper = terrain.gridCreatorWrapperStack.peek();
                int nRowsAdded = terrain.tileManager.getCurrRowCount() - startRowCount;
                if(thatStructure instanceof AdvancedTerrainStructure) {
                    myGridCreatorWrapper.content = new AdvancedGridCreator(
                            nRowsAdded, terrain.nCols, parentGridCreatorWrapper,
                            startRowCount
                    );
                }else{
                    myGridCreatorWrapper.content = new BasicGridCreator(
                            nRowsAdded, terrain.nCols, parentGridCreatorWrapper,
                            startRowCount
                    );
                }
                terrain.gridCreatorWrapperQueue.enqueue(myGridCreatorWrapper);
                terrain.rowOffsetQueue.enqueue(startRowCount);
                thatStructure.generateAddons(terrain, nRowsAdded, terrain.nCols);
                terrain.commandBuffer.addCommand(AddonsCommandsExecutor.CMD_FINISH_STRUCTURE_ADDONS);
                break;
            case CMD_SET_ALPHAS:
                float alphaL = buffer[offset + 2], alphaR = buffer[offset + 3];
                terrain.tileManager.setUpcomingAlphas(alphaL, alphaR);
                break;
            default:
                throw new IllegalArgumentException("Unknown command code: " + code);
        }
    }

    @Override
    public boolean canHandle(float v) {
        int command = (int) v;
        return command >= CMD_LANDSCAPE_USER_FIRST && command <= CMD_FINISH_STRUCTURE_LANDSCAPE;
    }
}