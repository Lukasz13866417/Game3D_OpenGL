package com.example.game3d_opengl.game.terrain.terrain_api.main;

import static com.example.game3d_opengl.game.terrain.terrain_api.main.LandscapeCommandsExecutor.CMD_FINISH_STRUCTURE_LANDSCAPE;
import static com.example.game3d_opengl.game.terrain.terrain_api.main.AddonsCommandsExecutor.CMD_RESERVE_HORIZONTAL;
import static com.example.game3d_opengl.game.terrain.terrain_api.main.AddonsCommandsExecutor.CMD_RESERVE_HORIZONTAL_REGION;
import static com.example.game3d_opengl.game.terrain.terrain_api.main.AddonsCommandsExecutor.CMD_RESERVE_K_RANDOM_FIELDS;
import static com.example.game3d_opengl.game.terrain.terrain_api.main.AddonsCommandsExecutor.CMD_RESERVE_RANDOM_HORIZONTAL;
import static com.example.game3d_opengl.game.terrain.terrain_api.main.AddonsCommandsExecutor.CMD_RESERVE_RANDOM_HORIZONTAL_REGION;
import static com.example.game3d_opengl.game.terrain.terrain_api.main.AddonsCommandsExecutor.CMD_RESERVE_RANDOM_VERTICAL;
import static com.example.game3d_opengl.game.terrain.terrain_api.main.AddonsCommandsExecutor.CMD_RESERVE_VERTICAL;
import static com.example.game3d_opengl.game.terrain.terrain_api.main.LandscapeCommandsExecutor.CMD_ADD_H_ANG;
import static com.example.game3d_opengl.game.terrain.terrain_api.main.LandscapeCommandsExecutor.CMD_ADD_SEG;
import static com.example.game3d_opengl.game.terrain.terrain_api.main.LandscapeCommandsExecutor.CMD_ADD_EMPTY_SEG;
import static com.example.game3d_opengl.game.terrain.terrain_api.main.LandscapeCommandsExecutor.CMD_ADD_V_ANG;
import static com.example.game3d_opengl.game.terrain.terrain_api.main.LandscapeCommandsExecutor.CMD_LIFT_UP;
import static com.example.game3d_opengl.game.terrain.terrain_api.main.LandscapeCommandsExecutor.CMD_SET_ALPHAS;
import static com.example.game3d_opengl.game.terrain.terrain_api.main.LandscapeCommandsExecutor.CMD_SET_H_ANG;
import static com.example.game3d_opengl.game.terrain.terrain_api.main.LandscapeCommandsExecutor.CMD_SET_TILE_BRIGHTNESS;
import static com.example.game3d_opengl.game.terrain.terrain_api.main.LandscapeCommandsExecutor.CMD_SET_TILE_PROFILE;
import static com.example.game3d_opengl.game.terrain.terrain_api.main.LandscapeCommandsExecutor.CMD_SET_V_ANG;
import static com.example.game3d_opengl.game.terrain.terrain_api.main.LandscapeCommandsExecutor.CMD_START_STRUCTURE_LANDSCAPE;

import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.game.pooling.PooledSlotLease;
import com.example.game3d_opengl.game.WorldActor;
import com.example.game3d_opengl.game.terrain.track_elements.GameplayElementBatchRenderers;
import com.example.game3d_opengl.game.terrain.track_elements.potion.PotionBatchInstance;
import com.example.game3d_opengl.game.terrain.track_elements.spike.SpikeBatchInstance;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridCreatorWrapper;
import com.example.game3d_opengl.game.terrain.terrain_api.main.tilemanager.TileManager;
import com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.ArrayQueue;
import com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.ArrayStack;
import com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.IntArrayQueue;
import com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.IntArrayStack;
import com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.FixedMaxSizeDeque;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.execbuffer.CommandExecutor;
import com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.execbuffer.PreallocatedCommandBuffer;


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
 *
 * @deprecated Retained only for Android diagnostic stages and legacy algorithm tests. Production
 * gameplay streams immutable core records through {@code GameplayTerrainStream}.
 */
@Deprecated
public class Terrain implements WorldActor {
    private static final int DEFAULT_QUEUE_CAPACITY = 100_000;
    private static final int INTERACTION_TILE_WINDOW_BEHIND = 8;
    private static final int INTERACTION_TILE_WINDOW_AHEAD = 64;

    private static final class StructureFrontierSpan {
        final int sequence;
        final int startTileIndex;
        final int endTileIndex;
        boolean committed;

        StructureFrontierSpan(int sequence, int startTileIndex, int endTileIndex) {
            this.sequence = sequence;
            this.startTileIndex = startTileIndex;
            this.endTileIndex = endTileIndex;
        }
    }

    static final class DeferredAddonPhase {
        final BaseTerrainStructure<?> structure;
        final int nRows;
        final int nCols;

        DeferredAddonPhase(BaseTerrainStructure<?> structure, int nRows, int nCols) {
            this.structure = structure;
            this.nRows = nRows;
            this.nCols = nCols;
        }
    }

    private static final class DeferredAddonCommandBuffer
            implements com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.execbuffer.CommandBuffer {
        private float[] buffer = new float[128];
        private int size = 0;
        private int readPos = 0;

        @Override
        public void addCommand(float... args) {
            if (args == null || args.length == 0) {
                throw new IllegalArgumentException("At least one float (the command code) is required.");
            }
            int total = 2 + args.length - 1;
            ensureCapacity(size + total);
            buffer[size++] = args[0];
            buffer[size++] = args.length - 1;
            for (int i = 1; i < args.length; ++i) {
                buffer[size++] = args[i];
            }
        }

        @Override
        public void executeFirstCommand(CommandExecutor executor) {
            if (!hasAnyCommands()) {
                throw new IllegalStateException("No deferred addon commands to execute.");
            }
            int argCount = (int) buffer[readPos + 1];
            int total = 2 + argCount;
            executor.execute(buffer, readPos, total);
            readPos += total;
            if (readPos >= size) {
                clear();
            }
        }

        @Override
        public boolean hasAnyCommands() {
            return size - readPos >= 2;
        }

        void clear() {
            size = 0;
            readPos = 0;
        }

        private void ensureCapacity(int requiredCapacity) {
            if (requiredCapacity <= buffer.length) {
                return;
            }
            int newCapacity = buffer.length;
            while (newCapacity < requiredCapacity) {
                newCapacity *= 2;
            }
            float[] newBuffer = new float[newCapacity];
            int unread = size - readPos;
            if (unread > 0) {
                System.arraycopy(buffer, readPos, newBuffer, 0, unread);
            }
            buffer = newBuffer;
            size = unread;
            readPos = 0;
        }
    }

    // Error messages
    private static final String ERROR_INVALID_TILE_INDEX = "Invalid tile index: ";
    private static final String ERROR_INVALID_ADDON_INDEX = "Invalid addon index: ";

    /**
     * The tile builder responsible for creating and managing individual tiles.
     * Handles the geometry generation and GPU resource management for tiles.
     */
    final GridResourcePack gridResourcePack;
    public final TileManager tileManager;

    private LightSource lightSource = null;
    private FColor colorTheme = null;


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
    public void cleanupGPUResourcesRecursively() {

        // TODO this should only do GPU stuff. Make separate method for command buffers etc

        tileManager.cleanupGPUResourcesRecursively();
        commandBuffer.release();

        gridCreatorWrapperQueue.clear();
        gridCreatorWrapperStack.clear();

        rowOffsetQueue.clear();
        rowCountStack.clear();
        tileStartIndexStack.clear();

        structureStack.clear();
        waitingStructuresQueue.clear();
        childStructuresQueue.clear();
        deferredAddonPhaseQueue.clear();
        pendingStructureFrontierSpans.clear();
        deferredAddonCommandBuffer.clear();
        stagedAddonQueue.clear();
        activeAddonCommandBuffer = commandBuffer;
        activeAddonQueue = addonQueue;

        for (Addon addon : addons) {
            addon.cleanupGPUResourcesRecursively();
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
        LegacyGameplayElementRenderers.reloadOnContextLoss();
    }

    /**
     * API for terrain structures to create landscape (tiles).
     * The TileBrush provides methods to build terrain geometry by adding
     * commands to the command buffer. These commands are executed later
     * to generate the actual terrain.
     */
    public class TileBrush {
        // Each command is stored as [commandCode, argCount, arg_1, arg_2, ..., arg_argCount].
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
         * Sets alpha values for every new tile.
         * If not called, defaults to 1,1,1,1
         */
        public void setCornerAlphas(float alphaL, float alphaR) {
            commandBuffer.addCommand(CMD_SET_ALPHAS, alphaL, alphaR);
        }

        public void setUpcomingTileProfile(TileProfile profile) {
            TileProfile safeProfile = profile != null ? profile : TileProfile.NORMAL;
            commandBuffer.addCommand(CMD_SET_TILE_PROFILE, safeProfile.getCommandId());
        }

        public void setUpcomingBrightnessMultiplier(float brightnessMultiplier) {
            commandBuffer.addCommand(CMD_SET_TILE_BRIGHTNESS, brightnessMultiplier);
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

    private void enqueueAddonCommand(float... args) {
        activeAddonCommandBuffer.addCommand(args);
    }

    private void enqueueAddon(Addon addon) {
        activeAddonQueue.enqueue(addon);
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
            assert row > 0;
            assert col > 0;
            assert col <= nCols;
            assert length > 0;
            assert addons.length == length : "Addon count doesn't match segment length";
            enqueueAddonCommand(CMD_RESERVE_VERTICAL, row, col, length);
            for (Addon addon : addons) {
                enqueueAddon(addon);
            }
        }

        /**
         * Reserves a horizontal strip without collision checking.
         */
        public void reserveHorizontal(int row, int col, int length, Addon[] addons) {
            assert row > 0;
            assert col > 0;
            assert col <= nCols;
            assert length > 0;
            assert addons.length == length : "Addon count doesn't match segment length";
            enqueueAddonCommand(CMD_RESERVE_HORIZONTAL, row, col, length);
            for (Addon addon : addons) {
                enqueueAddon(addon);
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
            assert row > 0;
            assert col > 0;
            assert col <= nCols;
            assert length > 0;
            enqueueAddonCommand(CMD_RESERVE_VERTICAL, row, col, length);
            for (Addon addon : addons) {
                enqueueAddon(addon);
            }
        }

        /**
         * Reserves a horizontal strip with collision checking.
         */
        public void reserveHorizontal(int row, int col, int length, Addon[] addons) {
            assert addons.length == length : "Addon count doesn't match segment length";
            assert row > 0;
            assert col > 0;
            assert col <= nCols;
            assert length > 0;
            enqueueAddonCommand(CMD_RESERVE_HORIZONTAL, row, col, length);
            for (Addon addon : addons) {
                enqueueAddon(addon);
            }
        }

        /**
         * Reserves a random horizontal strip that fits the specified length.
         * The system will find an available location automatically.
         */
        public void reserveRandomFittingHorizontal(int length, Addon[] addons) {
            assert addons.length == length : "Addon count doesn't match segment length";
            assert length > 0;
            enqueueAddonCommand(CMD_RESERVE_RANDOM_HORIZONTAL, length);
            for (Addon addon : addons) {
                enqueueAddon(addon);
            }
        }

        /**
         * Reserves a random vertical strip that fits the specified length.
         * The system will find an available location automatically.
         */
        public void reserveRandomFittingVertical(int length, Addon[] addons) {
            assert addons.length == length : "Addon count doesn't match segment length";
            assert length > 0;
            enqueueAddonCommand(CMD_RESERVE_RANDOM_VERTICAL, length);
            for (Addon addon : addons) {
                enqueueAddon(addon);
            }
        }

        /**
         * Reserves k random single-cell fields and places addons in sorted order of
         * selected fields (row, then col).
         */
        public void reserveKRandomFields(Addon[] addons) {
            assert addons != null;
            assert addons.length > 0 : "Addon count must be > 0";
            enqueueAddonCommand(CMD_RESERVE_K_RANDOM_FIELDS, addons.length);
            for (Addon addon : addons) {
                enqueueAddon(addon);
            }
        }

        /**
         * Reserves a specific horizontal region and places one addon over the full region.
         */
        public void reserveHorizontalRegion(int row, int col, int length, Addon addon) {
            assert addon != null;
            assert row > 0;
            assert col > 0;
            assert col <= nCols;
            assert length > 0;
            enqueueAddonCommand(CMD_RESERVE_HORIZONTAL_REGION, row, col, length);
            enqueueAddon(addon);
        }

        /**
         * Reserves a random horizontal region and places one addon over the full region.
         */
        public void reserveRandomHorizontalRegion(int length, Addon addon) {
            assert addon != null;
            assert length > 0;
            enqueueAddonCommand(CMD_RESERVE_RANDOM_HORIZONTAL_REGION, length);
            enqueueAddon(addon);
        }
    }

    // Data structures for managing terrain generation state
    final ArrayStack<GridCreatorWrapper> gridCreatorWrapperStack;
    final ArrayQueue<GridCreatorWrapper> gridCreatorWrapperQueue;
    final IntArrayQueue rowOffsetQueue;
    final ArrayQueue<Addon> addonQueue;
    final IntArrayStack rowCountStack;
    final IntArrayStack tileStartIndexStack;

    // Structures waiting for command interpretation
    final ArrayStack<BaseTerrainStructure<?>> structureStack;

    // Structures waiting for command generation
    final ArrayQueue<BaseTerrainStructure<?>> waitingStructuresQueue;
    final ArrayQueue<BaseTerrainStructure<?>> childStructuresQueue;
    final ArrayQueue<DeferredAddonPhase> deferredAddonPhaseQueue;
    final ArrayQueue<StructureFrontierSpan> pendingStructureFrontierSpans;

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
    private final DeferredAddonCommandBuffer deferredAddonCommandBuffer;
    private CommandExecutor activeAddonCommandExecutor = null;
    private com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.execbuffer.CommandBuffer
            activeAddonCommandBuffer;
    private ArrayQueue<Addon> activeAddonQueue;
    private final ArrayQueue<Addon> stagedAddonQueue;

    private static final FColor DEFAULT_COLOR_THEME = FColor.CLR(0.8f,0,0);
    private int nextStructureFrontierSequence = 0;
    private int committedFrontierTileIndex = -1;

    public Terrain(int maxSegments, int nCols, Vector3D startMid, float segWidth, float segLength, float rowSpacing, LightSource lightSource) {
        this(maxSegments, nCols, startMid, segWidth, segLength, rowSpacing, lightSource,
                TerrainResourcePack.defaultInstance());
    }

    private Terrain(int maxSegments, int nCols, Vector3D startMid, float segWidth, float segLength,
                    float rowSpacing, LightSource lightSource, TerrainResourcePack resourcePack) {
        TileManager createdTileManager = null;
        PreallocatedCommandBuffer createdCommandBuffer = null;
        try {
            this.nCols = nCols;
            this.gridResourcePack = resourcePack.gridResourcePack();

            PooledSlotLease<float[]> commandBufferLease = resourcePack.commandBufferPool().acquire();
            createdCommandBuffer = new PreallocatedCommandBuffer(commandBufferLease);
            createdCommandBuffer.resetAfterAcquire();
            this.commandBuffer = createdCommandBuffer;
            this.deferredAddonCommandBuffer = new DeferredAddonCommandBuffer();

            // Initialize the tile builder with the specified parameters
            createdTileManager = new TileManager(
                    maxSegments,
                    nCols,
                    startMid,
                    segWidth,
                    segLength,
                    rowSpacing,
                    resourcePack.tileManagerResourcePack()
            );
            this.tileManager = createdTileManager;

            // Initialize the addons collection
            this.addons = new FixedMaxSizeDeque<>(maxSegments + 1);

            // Initialize the command execution system
            this.generalExecutor = new GeneralExecutor();
            this.landscapeCommandExecutor = new LandscapeCommandsExecutor(this);
            this.addonsCommandExecutor = new AddonsCommandsExecutor(this);

            // Initialize data structures with appropriate capacities
            this.rowOffsetQueue = new IntArrayQueue(DEFAULT_QUEUE_CAPACITY);
            this.rowCountStack = new IntArrayStack(DEFAULT_QUEUE_CAPACITY);
            this.tileStartIndexStack = new IntArrayStack(DEFAULT_QUEUE_CAPACITY);
            this.gridCreatorWrapperStack = new ArrayStack<>();
            this.gridCreatorWrapperQueue = new ArrayQueue<>();
            this.addonQueue = new ArrayQueue<>();
            this.structureStack = new ArrayStack<>();
            this.waitingStructuresQueue = new ArrayQueue<>();
            this.childStructuresQueue = new ArrayQueue<>();
            this.deferredAddonPhaseQueue = new ArrayQueue<>();
            this.pendingStructureFrontierSpans = new ArrayQueue<>();
            this.stagedAddonQueue = new ArrayQueue<>();
            this.activeAddonCommandBuffer = this.commandBuffer;
            this.activeAddonQueue = this.addonQueue;

            // Initialize the grid brushes
            this.advancedGridBrush = new AdvancedGridBrush();
            this.basicGridBrush = new BasicGridBrush();

            // Initialize the tile brush
            this.tileBrush = new TileBrush();

            this.lightSource = lightSource;
            this.colorTheme = DEFAULT_COLOR_THEME;
            this.committedFrontierTileIndex = createdTileManager.getLastGeneratedTileIndex();
        } catch (Throwable t) {
            if (createdTileManager != null) {
                createdTileManager.cleanupGPUResourcesRecursively();
            }
            if (createdCommandBuffer != null) {
                createdCommandBuffer.release();
            }
            throw t;
        }
    }

    public void updateBeforeDraw(float dt) {
        for (int i = 0; i < getAddonCount(); ++i) {
            getAddon(i).updateBeforeDraw(dt);
        }
        tileManager.updateBeforeDraw(dt);
    }

    public void setLightSource(LightSource lightSource){
        this.lightSource = lightSource;
    }

    public void setColorTheme(FColor colorTheme){
        this.colorTheme = colorTheme;
    }

    public void draw( float[] vp) {
        draw(vp, true);
    }

    public void draw(float[] vp, boolean includeAddons) {
        tileManager.draw(colorTheme, vp, lightSource);
        if (includeAddons) {
            GameplayElementBatchRenderers addonBatchRenderers =
                    LegacyGameplayElementRenderers.getOrNull();
            if (addonBatchRenderers != null) {
                addonBatchRenderers.beginFrame();
            }
            for (int i = 0; i < getAddonCount(); ++i) {
                Addon addon = getAddon(i);
                if (addonBatchRenderers != null) {
                    if (addon instanceof PotionBatchInstance) {
                        addonBatchRenderers.submit((PotionBatchInstance) addon);
                        continue;
                    }
                    if (addon instanceof SpikeBatchInstance) {
                        addonBatchRenderers.submit((SpikeBatchInstance) addon);
                        continue;
                    }
                }
                addon.draw(vp);
            }
            if (addonBatchRenderers != null) {
                addonBatchRenderers.flush(vp);
            }
        }
    }

    @Override
    public void updateAfterDraw(float dt) {
        for (int i = 0; i < getAddonCount(); ++i) {
            getAddon(i).updateAfterDraw(dt);
        }
        tileManager.updateAfterDraw(dt);
    }

    @Override
    public void rebasePosition(Vector3D delta) {
        if (delta == null) return;
        tileManager.rebasePosition(delta);
        for (int i = 0; i < getAddonCount(); ++i) {
            getAddon(i).rebasePosition(delta);
        }
        if (lightSource != null && lightSource.position != null) {
            lightSource.setPosition(lightSource.position.add(delta));
        }
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
        tileManager.removeOldTiles(playerTileId - 50L);
        removeOldAddons(playerTileId);
    }

    public int getAddonCount() {
        return addons.size();
    }

    public int getCommittedFrontierTileIndex() {
        return committedFrontierTileIndex;
    }

    public int getLastGeneratedTileIndex() {
        return tileManager.getLastGeneratedTileIndex();
    }

    public float getSegmentLength() {
        return tileManager.getSegmentLength();
    }

    public int getInteractionTileWindowAhead() {
        return INTERACTION_TILE_WINDOW_AHEAD;
    }

    public boolean hasPendingGenerationWork() {
        return commandBuffer.hasAnyCommands()
                || !waitingStructuresQueue.isEmpty()
                || !pendingStructureFrontierSpans.isEmpty();
    }

    public int getCommittedLeadAheadOf(long referenceTileId) {
        int referenceTileIndex = tileManager.getAbsoluteTileIndexAtOrBefore(referenceTileId);
        if (referenceTileIndex < 0 || committedFrontierTileIndex < 0) {
            return 0;
        }
        return committedFrontierTileIndex - referenceTileIndex;
    }

    public Addon getAddon(int i) {
        if (i < 0 || i >= addons.size()) {
            throw new IndexOutOfBoundsException(ERROR_INVALID_ADDON_INDEX + i);
        }
        return addons.get(i);
    }

    public int getFirstVisibleTileAbsoluteIndex() {
        return tileManager.getFirstVisibleTileAbsoluteIndex();
    }

    public int getAbsoluteTileIndexForVisibleIndex(int visibleTileIndex) {
        return tileManager.getAbsoluteTileIndexForVisibleIndex(visibleTileIndex);
    }

    void addPlacedAddon(Addon addon) {
        if (addon == null) {
            throw new IllegalArgumentException("addon == null");
        }
        addons.pushBack(addon);
    }

    void enqueueDeferredAddonPhase(BaseTerrainStructure<?> structure, int nRows, int nCols) {
        deferredAddonPhaseQueue.enqueue(new DeferredAddonPhase(structure, nRows, nCols));
    }

    DeferredAddonPhase peekDeferredAddonPhase() {
        return deferredAddonPhaseQueue.peek();
    }

    DeferredAddonPhase dequeueDeferredAddonPhase() {
        return deferredAddonPhaseQueue.dequeue();
    }

    Addon dequeuePendingAddon() {
        return activeAddonQueue.dequeue();
    }

    void beginDeferredAddonEmission(CommandExecutor executor) {
        if (activeAddonCommandBuffer != commandBuffer || activeAddonQueue != addonQueue) {
            throw new IllegalStateException("Deferred addon emission is already active.");
        }
        deferredAddonCommandBuffer.clear();
        stagedAddonQueue.clear();
        activeAddonCommandExecutor = executor;
        activeAddonCommandBuffer = deferredAddonCommandBuffer;
        activeAddonQueue = stagedAddonQueue;
    }

    void finishDeferredAddonEmission() {
        deferredAddonCommandBuffer.clear();
        stagedAddonQueue.clear();
        activeAddonCommandExecutor = null;
        activeAddonCommandBuffer = commandBuffer;
        activeAddonQueue = addonQueue;
    }

    void executeDeferredAddonCommands() {
        if (activeAddonCommandExecutor == null || activeAddonCommandBuffer != deferredAddonCommandBuffer) {
            throw new IllegalStateException("Deferred addon emission is not active.");
        }
        while (deferredAddonCommandBuffer.hasAnyCommands()) {
            deferredAddonCommandBuffer.executeFirstCommand(activeAddonCommandExecutor);
        }
    }

    void recordStructureLandscapeStart() {
        tileStartIndexStack.push(tileManager.getLastGeneratedTileIndex());
    }

    void recordStructureLandscapeFinish() {
        int startTileIndex = tileStartIndexStack.pop();
        int endTileIndex = tileManager.getLastGeneratedTileIndex();
        pendingStructureFrontierSpans.enqueue(
                new StructureFrontierSpan(nextStructureFrontierSequence++, startTileIndex, endTileIndex)
        );
    }

    void recordStructureAddonFinish() {
        StructureFrontierSpan span = pendingStructureFrontierSpans.peek();
        if (span == null) {
            throw new IllegalStateException("No pending structure frontier span to commit.");
        }
        span.committed = true;
        advanceCommittedFrontierIfPossible();
    }

    int getPendingStructureFrontierCountForTesting() {
        return pendingStructureFrontierSpans.size();
    }

    private void advanceCommittedFrontierIfPossible() {
        while (!pendingStructureFrontierSpans.isEmpty()) {
            StructureFrontierSpan span = pendingStructureFrontierSpans.peek();
            if (span == null || !span.committed) {
                return;
            }
            committedFrontierTileIndex = Math.max(committedFrontierTileIndex, span.endTileIndex);
            pendingStructureFrontierSpans.dequeue();
        }
    }

    public void interactNearby(Player player, long referenceTileId) {
        if (player == null || tileManager.getTileCount() <= 0) {
            return;
        }

        int referenceTileIdx = referenceTileId >= 0
                ? tileManager.findLastTileIndexAtOrBefore(referenceTileId)
                : -1;
        if (referenceTileIdx < 0) {
            referenceTileIdx = 0;
        }

        int tileStartIdx = Math.max(0, referenceTileIdx - INTERACTION_TILE_WINDOW_BEHIND);
        int tileEndIdx = Math.min(
                tileManager.getTileCount() - 1,
                referenceTileIdx + INTERACTION_TILE_WINDOW_AHEAD
        );

        player.beginTileInteractionSweep();
        try {
            for (int i = tileStartIdx; i <= tileEndIdx; ++i) {
                tileManager.getTile(i).accept(player);
            }
        } finally {
            player.finishTileInteractionSweep();
        }

        if (addons.isEmpty()) {
            return;
        }

        long addonMinTileId = tileManager.getTile(tileStartIdx).getID();
        long addonMaxTileId = tileManager.getTile(tileEndIdx).getID();
        int addonStartIdx = findFirstAddonIndexAtOrAfter(addonMinTileId);
        if (addonStartIdx < 0) {
            return;
        }
        int addonEndIdx = findLastAddonIndexAtOrBefore(addonMaxTileId);
        if (addonEndIdx < addonStartIdx) {
            return;
        }
        for (int i = addonStartIdx; i <= addonEndIdx; ++i) {
            addons.get(i).accept(player);
        }
    }

    private int findFirstAddonIndexAtOrAfter(long tileId) {
        int low = 0;
        int high = addons.size() - 1;
        int best = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midId = addons.get(mid).getTileId();
            if (midId >= tileId) {
                best = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return best;
    }

    private int findLastAddonIndexAtOrBefore(long tileId) {
        int low = 0;
        int high = addons.size() - 1;
        int best = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midId = addons.get(mid).getTileId();
            if (midId <= tileId) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
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
                    // 0 = is not child structure of another structure, but rather a new structure.
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
            //Util.printCommand(buffer,offset);
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
