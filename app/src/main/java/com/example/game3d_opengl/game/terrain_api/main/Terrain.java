package com.example.game3d_opengl.game.terrain_api.main;

import static com.example.game3d_opengl.game.terrain_api.main.LandscapeCommandsExecutor.CMD_FINISH_STRUCTURE_LANDSCAPE;
import static com.example.game3d_opengl.game.terrain_api.main.AddonsCommandsExecutor.CMD_RESERVE_HORIZONTAL;
import static com.example.game3d_opengl.game.terrain_api.main.AddonsCommandsExecutor.CMD_RESERVE_RANDOM_HORIZONTAL;
import static com.example.game3d_opengl.game.terrain_api.main.AddonsCommandsExecutor.CMD_RESERVE_RANDOM_VERTICAL;
import static com.example.game3d_opengl.game.terrain_api.main.AddonsCommandsExecutor.CMD_RESERVE_VERTICAL;
import static com.example.game3d_opengl.game.terrain_api.main.LandscapeCommandsExecutor.CMD_ADD_H_ANG;
import static com.example.game3d_opengl.game.terrain_api.main.LandscapeCommandsExecutor.CMD_ADD_SEG;
import static com.example.game3d_opengl.game.terrain_api.main.LandscapeCommandsExecutor.CMD_ADD_EMPTY_SEG;
import static com.example.game3d_opengl.game.terrain_api.main.LandscapeCommandsExecutor.CMD_ADD_V_ANG;
import static com.example.game3d_opengl.game.terrain_api.main.LandscapeCommandsExecutor.CMD_LIFT_UP;
import static com.example.game3d_opengl.game.terrain_api.main.LandscapeCommandsExecutor.CMD_SET_H_ANG;
import static com.example.game3d_opengl.game.terrain_api.main.LandscapeCommandsExecutor.CMD_SET_V_ANG;
import static com.example.game3d_opengl.game.terrain_api.main.LandscapeCommandsExecutor.CMD_START_STRUCTURE_LANDSCAPE;

import com.example.game3d_opengl.game.terrain_api.Tile;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain_api.grid.symbolic.GridCreatorWrapper;
import com.example.game3d_opengl.game.terrain_api.terrainutil.ArrayQueue;
import com.example.game3d_opengl.game.terrain_api.terrainutil.ArrayStack;
import com.example.game3d_opengl.game.terrain_api.terrainutil.IntArrayQueue;
import com.example.game3d_opengl.game.terrain_api.terrainutil.IntArrayStack;
import com.example.game3d_opengl.game.terrain_api.terrainutil.FixedMaxSizeDeque;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain_api.terrainutil.execbuffer.CommandExecutor;
import com.example.game3d_opengl.game.terrain_api.terrainutil.execbuffer.PreallocatedCommandBuffer;


/**
 * Terrain with a fixed-size deque of tiles. We keep a `lastTile` pointer
 * to always build from the newest tile, even as older ones remain in the deque.
 * All terrain is LAZILY generated - every generation "request" is quickly translated into commands.
 * These commands are at some point (which is chosen by user) "interpreted" by CommandExecutors.
 * This gives the user control over what part of the generation process should be actually completed
 * in a given frame (single generateChunks(cnt) call).
 */
public class Terrain {

    public void cleanupGPUResources() {
        commandBuffer.free();
        tileBuilder.cleanup();
        gridCreatorWrapperQueue.clear();
        gridCreatorWrapperStack.clear();
        rowOffsetQueue.clear();
        rowCountStack.clear();
        structureStack.clear();
        waitingStructuresQueue.clear();
        childStructuresQueue.clear();
        for (Addon addon : addons) {
            addon.cleanupGPUResources();
        }
        addons.clear();
    }

    public final TileBuilder tileBuilder;

    public int getTileCount() {
        return tileBuilder.getTileCount();
    }

    public Tile getTile(int i) {
        return tileBuilder.getTile(i);
    }

    public void resetGPUResources() {
        for (int i = 0; i < tileBuilder.getTileCount(); ++i) {
            tileBuilder.getTile(i).resetGPUResources();
        }
        for (Addon addon : addons) {
            addon.resetGPUResources();
        }
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

        public void addEmptySegment() {
            // Just store the command code, no arg
            commandBuffer.addCommand(CMD_ADD_EMPTY_SEG);
        }

        public void liftUp(float dy) {
            commandBuffer.addCommand(CMD_LIFT_UP, dy);
        }

        public void addChild(BaseTerrainStructure child) {
            childStructuresQueue.enqueue(child);
            commandBuffer.addCommand(CMD_START_STRUCTURE_LANDSCAPE, 1);
            child.generateTiles(this);
            commandBuffer.addCommand(CMD_FINISH_STRUCTURE_LANDSCAPE);
        }
    }

    /**
     * API for terrain structures for putting addons on the landscape, using a grid.
     */
    public abstract static class BaseGridBrush {
        public abstract void reserveVertical(int row, int col, int length, Addon[] addons);

        public abstract void reserveHorizontal(int row, int col, int length, Addon[] addons);
    }

    /**
     * Basic version of the API.
     * It doesn't check for situations where multiple addons occupy the same grid square.
     */
    public class BasicGridBrush extends BaseGridBrush {
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

    }

    /**
     * Slower but more powerful version.
     * It checks for situations where multiple addons occupy the same grid square.
     * Use this as the "root" terrain structure to prevent such situations.
     * It also provides randomized queries (reserveRandomFittingHorizontal/Vertical).
     */
    public class AdvancedGridBrush extends BaseGridBrush {
        public void reserveVertical(int row, int col, int length, Addon[] addons) {
            assert addons.length == length : "Addon count doesn't match segment length";
            System.out.println("Enqueue RV "+row+","+col+","+length);
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

    final IntArrayQueue rowOffsetQueue;


    final ArrayQueue<Addon> addonQueue;

    final IntArrayStack rowCountStack;


    // structures waiting for command interpretation
    final ArrayStack<BaseTerrainStructure<?>> structureStack;

    // structures waiting for command generation
    final ArrayQueue<BaseTerrainStructure<?>> waitingStructuresQueue;
    final ArrayQueue<BaseTerrainStructure<?>> childStructuresQueue;


    final TileBrush tileBrush;
    final AdvancedGridBrush advancedGridBrush;
    final BasicGridBrush basicGridBrush;

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

        this.rowOffsetQueue = new IntArrayQueue(100_000);
        this.rowCountStack = new IntArrayStack(100_000);
        this.gridCreatorWrapperStack = new ArrayStack<>();
        this.gridCreatorWrapperQueue = new ArrayQueue<>();
        this.addonQueue = new ArrayQueue<>();
        this.structureStack = new ArrayStack<>();
        this.waitingStructuresQueue = new ArrayQueue<>();
        this.childStructuresQueue = new ArrayQueue<>();

        this.advancedGridBrush = new AdvancedGridBrush();
        this.basicGridBrush = new BasicGridBrush();

        this.tileBrush = new TileBrush();

    }

    private void removeOldAddons(long playerTileId) {
        while (!addons.isEmpty() && addons.getFirst().isGoneBy(playerTileId)) {
            addons.popFirst().cleanupGPUResources();
        }
    }

    public void removeOldTerrainElements(long playerTileId) {

        tileBuilder.removeOldTiles(playerTileId);
        removeOldAddons(playerTileId);
    }

    public int getAddonCount() {
        return addons.size();
    }

    public Addon getAddon(int i) {
        return addons.get(i);
    }

    public void enqueueStructure(BaseTerrainStructure what) {
        waitingStructuresQueue.enqueue(what);
    }

    public void generateChunks(int nChunks) {
        while (nChunks != 0) {
            if (!commandBuffer.hasAnyCommands()) {
                if (!waitingStructuresQueue.isEmpty()) {
                    // Only now generate commands of structures waiting for generation of commands.
                    // This is because there are no "fresh" commands to execute.
                    commandBuffer.addCommand(CMD_START_STRUCTURE_LANDSCAPE, 0);
                } else {
                    // No commands waiting to execute AND no structures with ungenerated commands.
                    break;
                }
            }
            commandBuffer.executeFirstCommand(generalExecutor);
            --nChunks;
        }
    }

    final PreallocatedCommandBuffer commandBuffer;


    /**
     * Based on command code, tells one of the executors to execute a command.
     * It simply dispatches commands to appropriate executors.
     */
    private class GeneralExecutor implements CommandExecutor {
        @Override
        public void execute(float[] buffer, int offset, int length) {
            int code = (int) buffer[offset];
            if (landscapeCommandExecutor.canHandle(code)) {
                landscapeCommandExecutor.execute(buffer, offset, length);
            } else if (addonsCommandExecutor.canHandle(code)) {
                addonsCommandExecutor.execute(buffer, offset, length);
            } else {
                throw new IllegalArgumentException("Unhandled command code in GeneralExecutor: " + code);
            }
        }

        @Override
        public boolean canHandle(float v) {
            return landscapeCommandExecutor.canHandle(v) || addonsCommandExecutor.canHandle(v);
        }
    }
}
