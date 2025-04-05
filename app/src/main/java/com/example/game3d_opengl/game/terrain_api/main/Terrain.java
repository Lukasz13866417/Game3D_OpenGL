package com.example.game3d_opengl.game.terrain_api.main;

import static com.example.game3d_opengl.game.terrain_api.main.LandscapeCommandsExecutor.CMD_FINISH_STRUCTURE_LANDSCAPE;
import static com.example.game3d_opengl.game.terrain_api.main.Util.printCommand;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;
import static com.example.game3d_opengl.game.terrain_api.main.AddonsCommandsExecutor.CMD_RESERVE_HORIZONTAL;
import static com.example.game3d_opengl.game.terrain_api.main.AddonsCommandsExecutor.CMD_RESERVE_RANDOM_HORIZONTAL;
import static com.example.game3d_opengl.game.terrain_api.main.AddonsCommandsExecutor.CMD_RESERVE_RANDOM_VERTICAL;
import static com.example.game3d_opengl.game.terrain_api.main.AddonsCommandsExecutor.CMD_RESERVE_VERTICAL;
import static com.example.game3d_opengl.game.terrain_api.main.LandscapeCommandsExecutor.CMD_ADD_H_ANG;
import static com.example.game3d_opengl.game.terrain_api.main.LandscapeCommandsExecutor.CMD_ADD_SEG;
import static com.example.game3d_opengl.game.terrain_api.main.LandscapeCommandsExecutor.CMD_ADD_V_ANG;
import static com.example.game3d_opengl.game.terrain_api.main.LandscapeCommandsExecutor.CMD_SET_H_ANG;
import static com.example.game3d_opengl.game.terrain_api.main.LandscapeCommandsExecutor.CMD_SET_V_ANG;
import static com.example.game3d_opengl.game.terrain_api.main.LandscapeCommandsExecutor.CMD_START_STRUCTURE_LANDSCAPE;
import static java.lang.Math.abs;

import com.example.game3d_opengl.game.terrain_api.Tile;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain_api.grid.symbolic.GridCreatorWrapper;
import com.example.game3d_opengl.game.terrain_api.terrainutil.ArrayQueue;
import com.example.game3d_opengl.game.terrain_api.terrainutil.ArrayStack;
import com.example.game3d_opengl.game.terrain_api.terrainutil.FixedMaxSizeDeque;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain_api.terrainutil.execbuffer.CommandExecutor;
import com.example.game3d_opengl.game.terrain_api.terrainutil.execbuffer.PreallocatedCommandBuffer;


/**
 * Terrain with a fixed-size deque of tiles. We keep a `lastTile` pointer
 * to always build from the newest tile, even as older ones remain in the deque.
 * All terrain is LAZILY generated - every generation "request" is quickly translated into commands.
 * These commands are at some point (chosen by user) "interpreted" by CommandExecutors.
 * This gives the user control over what part of the generation process should be actually completed
 * in a given frame (single generateChunks(cnt) call).
 */
public class Terrain {

    final TileBuilder tileBuilder;

    public int getTileCount() {
        return tileBuilder.getTileCount();
    }

    public Tile getTile(int i) {
        return tileBuilder.getTile(i);
    }

    /**
     * API for terrain structures for creating landscape (tiles).
     **/
    public class TileBrush {
        // Each command is stored as [commandCode, arg].
        // For commands with no arg (e.g. addSegment), we only store the code.
        public void setHorizontalAng(float ang) {
            commandBuffer.addCommand(CMD_SET_H_ANG, ang);
        }

        public void setVerticalAng(float ang) {
            commandBuffer.addCommand(CMD_SET_V_ANG, ang);
        }

        public void addVerticalAng(float ang) {
            commandBuffer.addCommand(CMD_ADD_V_ANG, ang);
        }

        public void addHorizontalAng(float ang) {
            commandBuffer.addCommand(CMD_ADD_H_ANG, ang);
        }

        public void addSegment() {
            // Just store the command code, no arg
            commandBuffer.addCommand(CMD_ADD_SEG);
        }

        public void addChild(TerrainStructure child) {
            childStructuresQueue.enqueue(child);
            commandBuffer.addCommand(CMD_START_STRUCTURE_LANDSCAPE, 1);
            child.generateTiles(this);
            commandBuffer.addCommand(CMD_FINISH_STRUCTURE_LANDSCAPE);
        }
    }

    /**
     * API for terrain structures for putting addons on the landscape, using a grid
     */
    public class GridBrush {
        public void reserveVertical(int row, int col, int length, Addon[] addons) {
            assert addons.length == length : "Addon count doesn't match segment length";
            commandBuffer.addCommand(CMD_RESERVE_VERTICAL, row, col, length);
            for (Addon addon : addons) {
                addonQueue.enqueue(addon);
            }
        }

        public void reserveHorizontal(int row, int col, int length, Addon[] addons) {
            assert addons.length == length : "Addon count doesn't match segment length";
            commandBuffer.addCommand(CMD_RESERVE_HORIZONTAL, row, col, length);
            for (Addon addon : addons) {
                addonQueue.enqueue(addon);
            }
        }

        public void reserveRandomFittingHorizontal(int length, Addon[] addons) {
            assert addons.length == length : "Addon count doesn't match segment length";
            commandBuffer.addCommand(CMD_RESERVE_RANDOM_HORIZONTAL, length);
            for (Addon addon : addons) {
                addonQueue.enqueue(addon);
            }
        }

        public void reserveRandomFittingVertical(int length, Addon[] addons) {
            assert addons.length == length : "Addon count doesn't match segment length";
            commandBuffer.addCommand(CMD_RESERVE_RANDOM_VERTICAL, length);
            for (Addon addon : addons) {
                addonQueue.enqueue(addon);
            }
        }

    }

    final ArrayStack<GridCreatorWrapper> gridCreatorWrapperStack;
    final ArrayQueue<GridCreatorWrapper> gridCreatorWrapperQueue;

    final ArrayQueue<Integer> rowOffsetQueue;

    final ArrayQueue<Addon> addonQueue;

    final ArrayStack<Integer> rowCountStack;

    // structures waiting for command interpretation
    final ArrayStack<TerrainStructure> structureStack;

    // structures waiting for command generation
    final ArrayQueue<TerrainStructure> waitingStructuresQueue;
    final ArrayQueue<TerrainStructure> childStructuresQueue;


    final TileBrush tileBrush;
    final GridBrush gridBrush;

    /**
     * Deque to hold all tiles (including the guardian).
     */
    final FixedMaxSizeDeque<Addon> addons;

    final int nCols;

    private final GeneralExecutor generalExecutor;

    private final LandscapeCommandsExecutor landscapeCommandExecutor;

    private final AddonsCommandsExecutor addonsCommandExecutor;

    public Terrain(int maxSegments, int nCols, Vector3D startMid, float segWidth, float segLength) {

        this.nCols = nCols;
        this.tileBuilder = new TileBuilder(maxSegments, nCols,
                startMid,
                segWidth, segLength, 1f
        );

        this.addons = new FixedMaxSizeDeque<>(maxSegments + 1);

        this.commandBuffer = new PreallocatedCommandBuffer();

        // General Executor decides which of two other executors should execute some command.
        this.generalExecutor = new GeneralExecutor();
        // For landscape commands
        this.landscapeCommandExecutor = new LandscapeCommandsExecutor(this);
        // For addon grid commands
        this.addonsCommandExecutor = new AddonsCommandsExecutor(this);

        this.rowOffsetQueue = new ArrayQueue<>();
        this.rowCountStack = new ArrayStack<>();
        this.gridCreatorWrapperStack = new ArrayStack<>();
        this.gridCreatorWrapperQueue = new ArrayQueue<>();
        this.addonQueue = new ArrayQueue<>();
        this.structureStack = new ArrayStack<>();
        this.waitingStructuresQueue = new ArrayQueue<>();
        this.childStructuresQueue = new ArrayQueue<>();

        this.gridBrush = new GridBrush();
        this.tileBrush = new TileBrush();

    }


    public void removeOldTiles(float playerX, float playerY, float playerZ) {
        tileBuilder.removeOldTiles(playerX, playerY, playerZ);
    }

    public int getAddonCount() {
        return addons.size();
    }

    public Addon getAddon(int i) {
        return addons.get(i);
    }

    public void enqueueStructure(TerrainStructure what) {
        waitingStructuresQueue.enqueue(what);
    }

    final PreallocatedCommandBuffer commandBuffer;

    public void generateChunks(int nChunks) {
        while (nChunks != 0) {
            if (!commandBuffer.hasAnyCommands()) {
                if (!waitingStructuresQueue.isEmpty()) {
                    commandBuffer.addCommand(CMD_START_STRUCTURE_LANDSCAPE, 0);
                } else {
                    break;
                }
            }
            commandBuffer.executeFirstCommand(generalExecutor);
            --nChunks;
        }
    }

    /**
     * Based on command code, tells one of the executors to execute a command.
     */
    private class GeneralExecutor implements CommandExecutor {
        @Override
        public void execute(float[] buffer, int offset, int length) {
            //int code = (int) (buffer[offset]);
            //printCommand(buffer, offset);
            if (landscapeCommandExecutor.canHandle(buffer[offset])) {
                landscapeCommandExecutor.execute(buffer, offset, length);
            } else {
                addonsCommandExecutor.execute(buffer, offset, length);
            }
        }

        @Override
        public boolean canHandle(float v) {
            return true;
        }
    }

}
