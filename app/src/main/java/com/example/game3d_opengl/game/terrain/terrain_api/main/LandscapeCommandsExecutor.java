package com.example.game3d_opengl.game.terrain.terrain_api.main;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos.EndPosTreeKind;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridCreatorWrapper;
import com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.execbuffer.CommandExecutor;

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
    public static final int CMD_SET_TILE_PROFILE = 10;
    public static final int CMD_SET_TILE_BRIGHTNESS = 11;
    public static final int CMD_LANDSCAPE_USER_LAST = 11;

    // Internal commands
    public static final int CMD_FINISH_STRUCTURE_LANDSCAPE = 12;

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
                if (!isChild) {
                    what = terrain.waitingStructuresQueue.dequeue();
                } else {
                    what = terrain.childStructuresQueue.dequeue();
                }
                terrain.structureStack.push(what);
                terrain.gridCreatorWrapperStack.push(
                        new GridCreatorWrapper(terrain.gridResourcePack.partialSegmentHandlerResourcePack())
                );
                terrain.recordStructureLandscapeStart();
                if (!isChild) {
                    what.generateTiles(terrain.tileBrush);
                    terrain.commandBuffer.addCommand(CMD_FINISH_STRUCTURE_LANDSCAPE);
                }
                terrain.rowCountStack.push(terrain.tileManager.getCurrRowCount());
                break;
            case CMD_FINISH_STRUCTURE_LANDSCAPE:
                BaseTerrainStructure<?> thatStructure = terrain.structureStack.pop();
                int startRowCount = terrain.rowCountStack.pop();
                int parentStartRowCount = terrain.rowCountStack.isEmpty()
                                                                 ? 0 : terrain.rowCountStack.peek();
                int ourRowOffsetFromParent = startRowCount - parentStartRowCount;
                GridCreatorWrapper myGridCreatorWrapper = terrain.gridCreatorWrapperStack.pop();
                GridCreatorWrapper parentGridCreatorWrapper = terrain.gridCreatorWrapperStack.peek();
                int nRowsAdded = terrain.tileManager.getCurrRowCount() - startRowCount;
                boolean propagateToParent = thatStructure.shouldPropagateReservationsToParent();
                int[][] blockedRowsForThisCreator = myGridCreatorWrapper.consumePendingBlockedRowsRanges();
                boolean isAdvanced = thatStructure instanceof AdvancedTerrainStructure;
                myGridCreatorWrapper.configureStructure(
                        isAdvanced,
                        nRowsAdded,
                        terrain.nCols,
                        parentGridCreatorWrapper,
                        ourRowOffsetFromParent,
                        EndPosTreeKind.POOLED_TREAP,
                        propagateToParent,
                        blockedRowsForThisCreator
                );
                myGridCreatorWrapper.setDebugName(thatStructure.name);
                if (parentGridCreatorWrapper != null) {
                    parentGridCreatorWrapper.addChildWrapper(myGridCreatorWrapper, ourRowOffsetFromParent);
                }
                if (propagateToParent && parentGridCreatorWrapper != null) {
                    for (int[] range : blockedRowsForThisCreator) {
                        parentGridCreatorWrapper.addPendingBlockedRowsRange(
                                range[0] + ourRowOffsetFromParent,
                                range[1] + ourRowOffsetFromParent
                        );
                    }
                }
                int[] blockedRows = thatStructure.getParentBlockedRowsRange(nRowsAdded, terrain.nCols);
                if (blockedRows != null && blockedRows.length == 2 && parentGridCreatorWrapper != null) {
                    int localStart = blockedRows[0];
                    int localEnd = blockedRows[1];
                    if (localStart <= localEnd) {
                        int absStart = ourRowOffsetFromParent + localStart;
                        int absEnd = ourRowOffsetFromParent + localEnd;
                        parentGridCreatorWrapper.addPendingBlockedRowsRange(absStart, absEnd);
                    }
                }
                terrain.recordStructureLandscapeFinish();
                terrain.gridCreatorWrapperQueue.enqueue(myGridCreatorWrapper);
                terrain.rowOffsetQueue.enqueue(startRowCount);
                terrain.enqueueDeferredAddonPhase(thatStructure, nRowsAdded, terrain.nCols);
                terrain.commandBuffer.addCommand(AddonsCommandsExecutor.CMD_BEGIN_STRUCTURE_ADDONS);
                if (isAdvanced) {
                    terrain.commandBuffer.addCommand(AddonsCommandsExecutor.CMD_BUILD_AGC_HORIZONTAL);
                    terrain.commandBuffer.addCommand(AddonsCommandsExecutor.CMD_BUILD_AGC_VERTICAL);
                    terrain.commandBuffer.addCommand(AddonsCommandsExecutor.CMD_FINALIZE_AGC);
                }
                terrain.commandBuffer.addCommand(AddonsCommandsExecutor.CMD_EMIT_STRUCTURE_ADDONS);
                terrain.commandBuffer.addCommand(AddonsCommandsExecutor.CMD_FINISH_STRUCTURE_ADDONS);
                break;
            case CMD_SET_ALPHAS:
                float alphaL = buffer[offset + 2], alphaR = buffer[offset + 3];
                terrain.tileManager.setUpcomingAlphas(alphaL, alphaR);
                break;
            case CMD_SET_TILE_PROFILE:
                terrain.tileManager.setUpcomingTileProfile(
                        TileProfile.fromCommandId((int) buffer[offset + 2])
                );
                break;
            case CMD_SET_TILE_BRIGHTNESS:
                terrain.tileManager.setUpcomingBrightnessMultiplier(buffer[offset + 2]);
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
