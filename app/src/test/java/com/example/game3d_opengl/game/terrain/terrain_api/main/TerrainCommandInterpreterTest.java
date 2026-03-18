package com.example.game3d_opengl.game.terrain.terrain_api.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.AdvancedGridCreator;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

public class TerrainCommandInterpreterTest {

    private static final Object PRINT_CAPTURE_LOCK = new Object();
    private final ArrayList<Terrain> terrainsToCleanup = new ArrayList<>();

    private static String[] capturePrintedGridLines(AdvancedGridCreator creator) {
        synchronized (PRINT_CAPTURE_LOCK) {
            PrintStream originalOut = System.out;
            ByteArrayOutputStream capturedBytes = new ByteArrayOutputStream();
            PrintStream capture = new PrintStream(capturedBytes);
            try {
                System.setOut(capture);
                creator.printGrid();
            } finally {
                System.setOut(originalOut);
                capture.close();
            }

            String text = new String(capturedBytes.toByteArray(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            while (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            return text.split("\n");
        }
    }

    private static void assertPrintedGrid(
            AdvancedGridCreator creator, int expectedRows, int expectedCols, String... expectedGridRows
    ) {
        String[] lines = capturePrintedGridLines(creator);
        assertEquals(expectedGridRows.length + 2, lines.length);
        assertTrue(lines[0].startsWith("Horizontal hashCode(): "));
        assertTrue(lines[1].startsWith("Rows: " + expectedRows + " | Cols: " + expectedCols + " "));
        assertTrue(lines[1].endsWith(",false"));
        for (int i = 0; i < expectedGridRows.length; ++i) {
            assertEquals(expectedGridRows[i], lines[i + 2]);
        }
    }

    private static final class NoOpAddon extends Addon {
        @Override
        protected void onPlace(
                Vector3D fieldNearLeft,
                Vector3D fieldNearRight,
                Vector3D fieldFarLeft,
                Vector3D fieldFarRight
        ) {}

        @Override
        public void accept(Player player) {}

        @Override
        public void updateBeforeDraw(float dt) {}

        @Override
        public void updateAfterDraw(float dt) {}

        @Override
        public void cleanupGPUResourcesRecursively() {}

        @Override
        public void reloadGPUResourcesRecursivelyOnContextLoss() {}

        @Override
        public void draw(float[] mvpMatrix) {}

        @Override
        public void rebasePosition(Vector3D delta) {}
    }

    private static Addon[] noOpAddons(int count) {
        Addon[] addons = new Addon[count];
        for (int i = 0; i < count; ++i) {
            addons[i] = new NoOpAddon();
        }
        return addons;
    }

    private static final class RecordingStructure extends AdvancedTerrainStructure {
        private final String id;
        private final List<RecordingStructure> children = new ArrayList<>();
        private final List<String> orderSink;
        private final List<String> rowsSink;

        RecordingStructure(int ownSegments, String id, List<String> orderSink, List<String> rowsSink) {
            super(ownSegments);
            this.id = id;
            this.orderSink = orderSink;
            this.rowsSink = rowsSink;
        }

        void addChildNode(RecordingStructure child) {
            children.add(child);
        }

        @Override
        protected void generateTiles(Terrain.TileBrush brush) {
            for (RecordingStructure child : children) {
                addChild(child, brush);
            }
            for (int i = 0; i < tilesToMake; ++i) {
                brush.addSegment();
            }
        }

        @Override
        protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
            orderSink.add(id);
            rowsSink.add(id + ":" + nRows + "x" + nCols);
        }
    }

    private static final class EmptyAddonStructure extends AdvancedTerrainStructure {
        int seenRows = -1;

        EmptyAddonStructure(int ownSegments) {
            super(ownSegments);
        }

        @Override
        protected void generateTiles(Terrain.TileBrush brush) {
            for (int i = 0; i < tilesToMake; ++i) {
                brush.addSegment();
            }
        }

        @Override
        protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
            seenRows = nRows;
        }
    }

    private static final class BlockingChildStructure extends AdvancedTerrainStructure {
        int seenRows = -1;

        BlockingChildStructure(int ownSegments) {
            super(ownSegments);
        }

        @Override
        protected void generateTiles(Terrain.TileBrush brush) {
            for (int i = 0; i < tilesToMake; ++i) {
                brush.addSegment();
            }
        }

        @Override
        protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
            seenRows = nRows;
        }

        @Override
        protected int[] getParentBlockedRowsRange(int nRows, int nCols) {
            return new int[]{1, nRows};
        }

        @Override
        protected boolean shouldPropagateReservationsToParent() {
            return false;
        }
    }

    private static final class ParentWithBlockingChild extends AdvancedTerrainStructure {
        private final BlockingChildStructure child;
        int seenRows = -1;

        ParentWithBlockingChild(int ownSegments, BlockingChildStructure child) {
            super(ownSegments);
            this.child = child;
        }

        @Override
        protected void generateTiles(Terrain.TileBrush brush) {
            addChild(child, brush);
            for (int i = 0; i < tilesToMake; ++i) {
                brush.addSegment();
            }
        }

        @Override
        protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
            seenRows = nRows;
        }
    }

    private static final class ReservingChildStructure extends AdvancedTerrainStructure {
        ReservingChildStructure(int ownSegments) {
            super(ownSegments);
        }

        @Override
        protected void generateTiles(Terrain.TileBrush brush) {
            for (int i = 0; i < tilesToMake; ++i) {
                brush.addSegment();
            }
        }

        @Override
        protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
            assertEquals(2, nRows);
            assertEquals(4, nCols);
            brush.reserveHorizontal(2, 2, 2, noOpAddons(2));
        }
    }

    private static final class ParentWithReservingChild extends AdvancedTerrainStructure {
        private final ReservingChildStructure child;
        int seenRows = -1;

        ParentWithReservingChild(int ownSegments, ReservingChildStructure child) {
            super(ownSegments);
            this.child = child;
        }

        @Override
        protected void generateTiles(Terrain.TileBrush brush) {
            addChild(child, brush);
            for (int i = 0; i < tilesToMake; ++i) {
                brush.addSegment();
            }
        }

        @Override
        protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
            seenRows = nRows;
            assertEquals(7, nRows);
            assertEquals(4, nCols);
            brush.reserveVertical(4, 4, 2, noOpAddons(2));
        }
    }

    private Terrain createTerrain(int nCols) {
        Terrain terrain = new Terrain(
                1024,
                nCols,
                new Vector3D(0f, 0f, 0f),
                3.2f,
                1.4f,
                1f,
                new LightSource(new FColor(1f, 1f, 1f))
        );
        terrainsToCleanup.add(terrain);
        return terrain;
    }

    @After
    public void cleanupTerrains() {
        for (int i = terrainsToCleanup.size() - 1; i >= 0; --i) {
            terrainsToCleanup.get(i).cleanupGPUResourcesRecursively();
        }
        terrainsToCleanup.clear();
    }

    private static void advanceUntil(Terrain terrain, int maxSteps, BooleanSupplier condition) {
        for (int i = 0; i < maxSteps; ++i) {
            if (condition.getAsBoolean()) {
                return;
            }
            terrain.generateChunks(1);
        }
        assertTrue("Condition was not reached within " + maxSteps + " steps.", condition.getAsBoolean());
    }

    @Test
    public void interpret_tree_creates_structures_in_post_order() {
        List<String> order = new ArrayList<>();
        List<String> rows = new ArrayList<>();

        RecordingStructure root = new RecordingStructure(2, "root", order, rows);
        RecordingStructure a = new RecordingStructure(1, "a", order, rows);
        RecordingStructure b = new RecordingStructure(1, "b", order, rows);
        RecordingStructure g = new RecordingStructure(1, "g", order, rows);

        a.addChildNode(g);
        root.addChildNode(a);
        root.addChildNode(b);

        Terrain terrain = createTerrain(4);
        terrain.enqueueStructure(root);
        terrain.generateChunks(-1);

        assertEquals(Arrays.asList("g", "a", "b", "root"), order);
        assertEquals(4, rows.size());
        assertTrue(rows.get(0).startsWith("g:"));
        assertTrue(rows.get(3).startsWith("root:"));
    }

    @Test
    public void creator_is_materialized_only_when_addon_phase_starts() {
        EmptyAddonStructure structure = new EmptyAddonStructure(3);
        Terrain terrain = createTerrain(4);
        terrain.enqueueStructure(structure);

        advanceUntil(
                terrain,
                256,
                () -> structure.seenRows > 0 && terrain.gridCreatorWrapperQueue.size() == 1
        );

        assertNull(terrain.gridCreatorWrapperQueue.peek().getContent());

        terrain.generateChunks(1);
        assertNotNull(terrain.gridCreatorWrapperQueue.peek().getContent());

        terrain.generateChunks(1);
        assertTrue(terrain.gridCreatorWrapperQueue.isEmpty());
    }

    @Test
    public void child_creator_is_destroyed_before_parent_materializes() {
        BlockingChildStructure child = new BlockingChildStructure(2);
        ParentWithBlockingChild parent = new ParentWithBlockingChild(3, child);

        Terrain terrain = createTerrain(4);
        terrain.enqueueStructure(parent);

        advanceUntil(
                terrain,
                512,
                () -> parent.seenRows > 0 && terrain.gridCreatorWrapperQueue.size() == 1
        );

        assertNull(
                "Parent creator should still be lazy after child addon phase completes.",
                terrain.gridCreatorWrapperQueue.peek().getContent()
        );

        terrain.generateChunks(1);
        assertNotNull(terrain.gridCreatorWrapperQueue.peek().getContent());
    }

    @Test
    public void parent_grid_print_matches_expected_layout_after_child_summary_transfer() {
        ReservingChildStructure child = new ReservingChildStructure(2);
        ParentWithReservingChild parent = new ParentWithReservingChild(3, child);
        Terrain terrain = createTerrain(4);
        terrain.enqueueStructure(parent);

        advanceUntil(
                terrain,
                512,
                () -> parent.seenRows > 0 && terrain.gridCreatorWrapperQueue.size() == 1
        );

        assertNull(terrain.gridCreatorWrapperQueue.peek().getContent());

        terrain.generateChunks(1);
        AdvancedGridCreator parentCreator =
                (AdvancedGridCreator) terrain.gridCreatorWrapperQueue.peek().getContent();
        assertNotNull(parentCreator);

        terrain.generateChunks(1);
        assertPrintedGrid(
                parentCreator,
                7,
                4,
                "0 [., ., ., .]",
                "1 [., #, #, .]",
                "2 [., ., ., .]",
                "3 [., ., ., #]",
                "4 [., ., ., #]",
                "5 [., ., ., .]",
                "6 [., ., ., .]"
        );
    }

    @Test
    public void child_blocked_rows_are_applied_to_parent_after_summary_transfer() {
        BlockingChildStructure child = new BlockingChildStructure(2);
        ParentWithBlockingChild parent = new ParentWithBlockingChild(4, child);

        Terrain terrain = createTerrain(3);
        terrain.enqueueStructure(parent);

        advanceUntil(
                terrain,
                512,
                () -> parent.seenRows > 0
                        && terrain.gridCreatorWrapperQueue.size() == 1
                        && terrain.gridCreatorWrapperQueue.peek().getContent() != null
        );

        AdvancedGridCreator parentCreator =
                (AdvancedGridCreator) terrain.gridCreatorWrapperQueue.peek().getContent();
        assertNotNull(parentCreator);

        int availableCells = (parent.seenRows - child.seenRows) * terrain.nCols;
        for (int i = 0; i < availableCells; ++i) {
            assertTrue(
                    "Blocked child rows leaked into parent availability.",
                    parentCreator.reserveRandomFittingVertical(1).row > child.seenRows
            );
        }
    }
}
