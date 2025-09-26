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

import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain_api.grid.symbolic.GridCreatorWrapper;
import com.example.game3d_opengl.game.terrain_api.terrainutil.ArrayQueue;
import com.example.game3d_opengl.game.terrain_api.terrainutil.ArrayStack;
import com.example.game3d_opengl.game.terrain_api.terrainutil.IntArrayQueue;
import com.example.game3d_opengl.game.terrain_api.terrainutil.IntArrayStack;
import com.example.game3d_opengl.game.terrain_api.terrainutil.FixedMaxSizeDeque;
import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.util3d.FColor;
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
 * The terrain system uses a command-based architecture where:
 * 1. Terrain structures generate commands (e.g., "add segment", "set angle")
 * 2. Commands are stored in a buffer
 * 3. Commands are executed in chunks to control frame time
 * 4. This allows for complex terrain generation without blocking the main thread
 */
public class Terrain implements GPUResourceOwner {

    private static final int DEFAULT_QUEUE_CAPACITY = 100_000;

    // Error messages
    private static final String ERROR_INVALID_TILE_INDEX = "Invalid tile index: ";
    private static final String ERROR_INVALID_ADDON_INDEX = "Invalid addon index: ";


    /**
     * The tile builder responsible for creating and managing individual tiles.
     * Handles the geometry generation and GPU resource management for tiles.
     */
    public final TileManager tileManager;

    /**
     * Gets the total number of tiles currently in the terrain.
     * 
     * @return the number of tiles
     */
    public int getTileCount() {
        return tileManager.getTileCount();
    }

    /**
     * Gets a tile at the specified index.
     * 
     * @param i the tile index
     * @return the tile at the specified index
     * @throws IndexOutOfBoundsException if the index is invalid
     */
    public Tile getTile(int i) {
        if (i < 0 || i >= tileManager.getTileCount()) {
            throw new IndexOutOfBoundsException(ERROR_INVALID_TILE_INDEX + i);
        }
        return tileManager.getTile(i);
    }

    /**
     * Cleans up all GPU resources used by the terrain system.
     * This includes VBOs, IBOs, and other OpenGL objects.
     * Should be called when the OpenGL context is being destroyed.
     */
    @Override
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {

        // TODO this should only do GPU stuff. Make separate method for command buffers etc

        commandBuffer.free();
        tileManager.cleanupGPUResourcesRecursivelyOnContextLoss();

        gridCreatorWrapperQueue.clear();
        gridCreatorWrapperStack.clear();

        rowOffsetQueue.clear();
        rowCountStack.clear();

        structureStack.clear();
        waitingStructuresQueue.clear();
        childStructuresQueue.clear();

        for (Addon addon : addons) {
            addon.cleanupGPUResourcesRecursivelyOnContextLoss();
        }
        addons.clear();
    }


    /**
     * Resets all GPU resources after a context loss.
     * Recreates VBOs and IBOs for all tiles and addons.
     **/
    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        tileManager.reloadGPUResourcesRecursivelyOnContextLoss();
        for (Addon addon : addons) {
            addon.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    /**
     * API for terrain structures to create landscape (tiles).
     * The TileBrush provides methods to build terrain geometry by adding
     * commands to the command buffer. These commands are executed later
     * to generate the actual terrain.
     */
    public class TileBrush {
        // Each command is stored as [commandCode, arg].
        // For commands with no arg (e.g. addSegment), we only store the code.

        /**
         * Sets the horizontal angle for the next tile.
         * This controls the left/right orientation of the terrain.
         */
        @SuppressWarnings("unused")
        public void setHorizontalAng(float ang) {
            commandBuffer.addCommand(CMD_SET_H_ANG, ang);
        }

        /**
         * Sets the vertical angle for the next tile.
         * This controls the up/down slope of the terrain.
         */
        @SuppressWarnings("unused")
        public void setVerticalAng(float ang) {
            commandBuffer.addCommand(CMD_SET_V_ANG, ang);
        }

        /**
         * Adds to the current vertical angle.
         * This creates a gradual slope change.
         */
        @SuppressWarnings("unused")
        public void addVerticalAng(float ang) {
            commandBuffer.addCommand(CMD_ADD_V_ANG, ang);
        }

        /**
         * Adds to the current horizontal angle.
         * This creates a gradual turn in the terrain.
         */
        @SuppressWarnings("unused")
        public void addHorizontalAng(float ang) {
            commandBuffer.addCommand(CMD_ADD_H_ANG, ang);
        }

        /**
         * Adds a new terrain segment with the current angle settings.
         * This creates a tile at the current position and orientation.
         */
        public void addSegment() {
            // Just store the command code, no arg
            commandBuffer.addCommand(CMD_ADD_SEG);
        }

        /**
         * Adds an empty segment (no physical geometry).
         * Used for spacing and creating gaps in the terrain.
         */
        public void addEmptySegment() {
            // Just store the command code, no arg
            commandBuffer.addCommand(CMD_ADD_EMPTY_SEG);
        }

        /**
         * Lifts the terrain up by the specified amount.
         * This creates elevation changes in the terrain.
         */
        public void liftUp(float dy) {
            commandBuffer.addCommand(CMD_LIFT_UP, dy);
        }

        /**
         * Adds a child terrain structure to be generated after the current one.
         * Child structures are useful for creating complex terrain features
         * that depend on the parent structure's geometry.
         */
        public void addChild(BaseTerrainStructure<?> child) {
            childStructuresQueue.enqueue(child);
            commandBuffer.addCommand(CMD_START_STRUCTURE_LANDSCAPE, 1);
            child.generateTiles(this);
            commandBuffer.addCommand(CMD_FINISH_STRUCTURE_LANDSCAPE);
        }
    }

    /**
     * Base class for grid brushes used by terrain structures.
     * Grid brushes handle the placement of addons (objects) on the terrain.
     * They provide an abstraction layer for different addon placement strategies.
     */
    public abstract static class BaseGridBrush {
        /**
         * Reserves a vertical strip of grid cells for addon placement.
         */
        public abstract void reserveVertical(int row, int col, int length, Addon[] addons);

        /**
         * Reserves a horizontal strip of grid cells for addon placement.
         */
        public abstract void reserveHorizontal(int row, int col, int length, Addon[] addons);
    }

    /**
     * Basic version of the grid brush API.
     * It doesn't check for situations where multiple addons occupy the same grid square.
     * This is faster but may result in overlapping addons. (If the root grid creator is only basic)
     */
    public class BasicGridBrush extends BaseGridBrush {
        /**
         * Reserves a vertical strip without collision checking.
         */
        public void reserveVertical(int row, int col, int length, Addon[] addons) {
            assert row>0;
            assert col>0;
            assert col<=nCols;
            assert length>0;
            assert addons.length == length : "Addon count doesn't match segment length";
            commandBuffer.addCommand(CMD_RESERVE_VERTICAL, row, col, length);
            for (Addon addon : addons) {
                addonQueue.enqueue(addon);
            }
        }

        /**
         * Reserves a horizontal strip without collision checking.
         */
        public void reserveHorizontal(int row, int col, int length, Addon[] addons) {
            assert row>0;
            assert col>0;
            assert col<=nCols;
            assert length>0;
            assert addons.length == length : "Addon count doesn't match segment length";
            commandBuffer.addCommand(CMD_RESERVE_HORIZONTAL, row, col, length);
            for (Addon addon : addons) {
                addonQueue.enqueue(addon);
            }
        }
    }

    /**
     * Slower but more powerful version of the grid brush.
     * It checks for situations where multiple addons occupy the same grid square.
     * Use this as the "root" terrain structure to prevent such situations.
     * It also provides randomized queries (reserveRandomFittingHorizontal/Vertical).
     */
    public class AdvancedGridBrush extends BaseGridBrush {
        /**
         * Reserves a vertical strip with collision checking.
         */
        public void reserveVertical(int row, int col, int length, Addon[] addons) {
            assert addons.length == length : "Addon count doesn't match segment length";
            assert row>0;
            assert col>0;
            assert col<=nCols;
            assert length>0;
            commandBuffer.addCommand(CMD_RESERVE_VERTICAL, row, col, length);
            for (Addon addon : addons) {
                addonQueue.enqueue(addon);
            }
        }

        /**
         * Reserves a horizontal strip with collision checking.
         */
        public void reserveHorizontal(int row, int col, int length, Addon[] addons) {
            assert addons.length == length : "Addon count doesn't match segment length";
            assert row>0;
            assert col>0;
            assert col<=nCols;
            assert length>0;
            commandBuffer.addCommand(CMD_RESERVE_HORIZONTAL, row, col, length);
            for (Addon addon : addons) {
                addonQueue.enqueue(addon);
            }
        }

        /**
         * Reserves a random horizontal strip that fits the specified length.
         * The system will find an available location automatically.
         */
        public void reserveRandomFittingHorizontal(int length, Addon[] addons) {
            assert addons.length == length : "Addon count doesn't match segment length";
            assert length>0;
            commandBuffer.addCommand(CMD_RESERVE_RANDOM_HORIZONTAL, length);
            for (Addon addon : addons) {
                addonQueue.enqueue(addon);
            }
        }

        /**
         * Reserves a random vertical strip that fits the specified length.
         * The system will find an available location automatically.
         */
        public void reserveRandomFittingVertical(int length, Addon[] addons) {
            assert addons.length == length : "Addon count doesn't match segment length";
            assert length>0;
            commandBuffer.addCommand(CMD_RESERVE_RANDOM_VERTICAL, length);
            for (Addon addon : addons) {
                addonQueue.enqueue(addon);
            }
        }
    }

    // Data structures for managing terrain generation state
    final ArrayStack<GridCreatorWrapper> gridCreatorWrapperStack;
    final ArrayQueue<GridCreatorWrapper> gridCreatorWrapperQueue;
    final IntArrayQueue rowOffsetQueue;
    final ArrayQueue<Addon> addonQueue;
    final IntArrayStack rowCountStack;
    
    // Structures waiting for command interpretation
    final ArrayStack<BaseTerrainStructure<?>> structureStack;
    
    // Structures waiting for command generation
    final ArrayQueue<BaseTerrainStructure<?>> waitingStructuresQueue;
    final ArrayQueue<BaseTerrainStructure<?>> childStructuresQueue;

    // Core terrain components
    final TileBrush tileBrush;
    final AdvancedGridBrush advancedGridBrush;
    final BasicGridBrush basicGridBrush;

    /**
     * Deque to hold all addons (objects placed on the terrain).
     * Addons are managed separately from tiles and can be added/removed dynamically.
     */
    final FixedMaxSizeDeque<Addon> addons;

    /**
     * The number of columns in the terrain grid.
     * This determines the width of terrain segments.
     */
    final int nCols;

    // Command execution system
    private final GeneralExecutor generalExecutor;
    private final LandscapeCommandsExecutor landscapeCommandExecutor;
    private final AddonsCommandsExecutor addonsCommandExecutor;

    public Terrain(int maxSegments, int nCols, Vector3D startMid, float segWidth, float segLength, float rowSpacing) {
        this.nCols = nCols;
        
        // Initialize the tile builder with the specified parameters
        this.tileManager = new TileManager(maxSegments, nCols, startMid, segWidth, segLength, rowSpacing);

        // Initialize the addons collection
        this.addons = new FixedMaxSizeDeque<>(maxSegments + 1);

        // Initialize the command buffer for terrain generation
        this.commandBuffer = new PreallocatedCommandBuffer();

        // Initialize the command execution system
        this.generalExecutor = new GeneralExecutor();
        this.landscapeCommandExecutor = new LandscapeCommandsExecutor(this);
        this.addonsCommandExecutor = new AddonsCommandsExecutor(this);

        // Initialize data structures with appropriate capacities
        this.rowOffsetQueue = new IntArrayQueue(DEFAULT_QUEUE_CAPACITY);
        this.rowCountStack = new IntArrayStack(DEFAULT_QUEUE_CAPACITY);
        this.gridCreatorWrapperStack = new ArrayStack<>();
        this.gridCreatorWrapperQueue = new ArrayQueue<>();
        this.addonQueue = new ArrayQueue<>();
        this.structureStack = new ArrayStack<>();
        this.waitingStructuresQueue = new ArrayQueue<>();
        this.childStructuresQueue = new ArrayQueue<>();

        // Initialize the grid brushes
        this.advancedGridBrush = new AdvancedGridBrush();
        this.basicGridBrush = new BasicGridBrush();

        // Initialize the tile brush
        this.tileBrush = new TileBrush();
    }

    public void updateBeforeDraw(float dt){
        for(int i=0;i<getAddonCount();++i) {
            getAddon(i).updateBeforeDraw(dt);
        }
        tileManager.updateBeforeDraw(dt);
    }

    public void draw(FColor colorTheme, float[] vp, LightSource light){
        tileManager.draw(colorTheme, vp, light);
        for(int i=0;i<getAddonCount();++i){
            getAddon(i).draw(vp);
        }
    }

    public void updateAfterDraw(float dt) {
        for(int i=0;i<getAddonCount();++i) {
            getAddon(i).updateBeforeDraw(dt);
        }
        tileManager.updateAfterDraw(dt);
    }

    /**
     * Removes old addons that are far behind the player.
     * This helps manage memory usage and maintain performance.
     */
    private void removeOldAddons(long playerTileId) {
        while (!addons.isEmpty() && addons.getFirst().isGoneBy(playerTileId)) {
            // TODO add some cleanup of stuff that's owned per-addon.
            addons.popFirst();
        }
    }

    /**
     * Removes old terrain elements (tiles and addons) that are far behind the player.
     * This is called each frame to maintain a reasonable terrain size.
     */
    public void removeOldTerrainElements(long playerTileId) {
        tileManager.removeOldTiles(playerTileId);
        removeOldAddons(playerTileId);
    }
    public int getAddonCount() {
        return addons.size();
    }

    public Addon getAddon(int i) {
        if (i < 0 || i >= addons.size()) {
            throw new IndexOutOfBoundsException(ERROR_INVALID_ADDON_INDEX + i);
        }
        return addons.get(i);
    }

    /**
     * Adds a terrain structure to the waiting queue.
     * The structure will be processed later
     */
    public void enqueueStructure(BaseTerrainStructure<?> what) {
        waitingStructuresQueue.enqueue(what);
    }

    /**
     * Generates terrain chunks by executing pending commands.
     * The number of chunks generated is limited to control frame time.
     */
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

    /**
     * The command buffer that stores all pending terrain generation commands.
     */
    final PreallocatedCommandBuffer commandBuffer;

    /**
     * General executor that dispatches commands to appropriate specialized executors.
     * It simply routes commands based on their type to either the landscape
     * or addons command executor.
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
