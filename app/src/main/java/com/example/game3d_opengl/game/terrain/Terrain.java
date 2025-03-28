package com.example.game3d_opengl.game.terrain;

import static com.example.game3d_opengl.engine.util3d.GameMath.PI;
import static com.example.game3d_opengl.engine.util3d.GameMath.rotateAroundAxis;
import static com.example.game3d_opengl.engine.util3d.GameMath.rotateAroundTwoPoints;
import static com.example.game3d_opengl.engine.util3d.vector.Vector3D.V3;
import static java.lang.Math.abs;
import static java.lang.Math.atan;

import com.example.game3d_opengl.game.terrain.addon.Addon;
import com.example.game3d_opengl.game.terrain.grid.symbolic.GridCreator;
import com.example.game3d_opengl.game.terrain.grid.symbolic.GridSegment;
import com.example.game3d_opengl.game.terrain.terrainutil.ArrayQueue;
import com.example.game3d_opengl.game.terrain.terrainutil.ArrayStack;
import com.example.game3d_opengl.game.terrain.terrainutil.FixedMaxSizeDeque;
import com.example.game3d_opengl.engine.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain.terrainutil.PreallocatedCoordinateBuffer;
import com.example.game3d_opengl.game.terrain.terrainutil.execbuffer.CommandExecutor;
import com.example.game3d_opengl.game.terrain.terrainutil.execbuffer.PreallocatedCommandBuffer;


/**
 * Terrain with a fixed-size deque of tiles. We keep a `lastTile` pointer
 * to always build from the newest tile, even as older ones remain in the deque.
 * All terrain is LAZILY generated - every generation "request" is quickly translated into commands.
 * These commands are at some point (chosen by user) "interpreted" by CommandExecutors.
 * This gives the user control over what part of the generation process should be actually completed
 * in a given frame (single generateChunks(cnt) call).
 */
public class Terrain {

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
    }

    /**
     * API for terrain structures for putting addons on the landscape, using a grid
     */
    public class GridBrush {
        public void reserveVertical(int row, int col, int length, Addon[] addons) {
            assert addons.length == length : "Addon count doesn't match segment length";
            commandBuffer.addCommand(CMD_RESERVE_VERTICAL);
            commandBuffer.addCommand(row);
            commandBuffer.addCommand(col);
            commandBuffer.addCommand(length);
            for (Addon addon : addons) {
                addonStack.push(addon);
            }
        }

        public void reserveHorizontal(int row, int col, int length, Addon[] addons) {
            assert addons.length == length : "Addon count doesn't match segment length";
            commandBuffer.addCommand(CMD_RESERVE_HORIZONTAL);
            commandBuffer.addCommand(row);
            commandBuffer.addCommand(col);
            commandBuffer.addCommand(length);
            for (Addon addon : addons) {
                addonStack.push(addon);
            }
        }

        public void reserveRandomFittingHorizontal(int length, Addon[] addons) {
            assert addons.length == length : "Addon count doesn't match segment length";
            commandBuffer.addCommand(CMD_RESERVE_RANDOM_HORIZONTAL);
            commandBuffer.addCommand(length);
            for (Addon addon : addons) {
                addonStack.push(addon);
            }
        }

        public void reserveRandomFittingVertical(int length, Addon[] addons) {
            assert addons.length == length : "Addon count doesn't match segment length";
            commandBuffer.addCommand(CMD_RESERVE_RANDOM_VERTICAL);
            commandBuffer.addCommand(length);
            for (Addon addon : addons) {
                addonStack.push(addon);
            }
        }

    }

    private final ArrayStack<GridCreator> structureGridStack;
    private final ArrayStack<Addon> addonStack;

    // structures waiting for command interpretation
    private final ArrayStack<TerrainStructure> structureStack;

    // structures waiting for command generation
    private final ArrayQueue<TerrainStructure> waitingStructuresQueue;


    private final TileBrush tileBrush;
    private final GridBrush gridBrush;


    private final float segWidth, segLength, rowSpacing;
    private float dHorizontalAng, dVerticalAng;
    private float currVerticalAng = 0.0f, currHorizontalAng = 0.0f;

    private int lastStructureStartRowCount = 0, currRowCount = 0;

    private final PreallocatedCoordinateBuffer leftSideBuffer, rightSideBuffer;

    /**
     * Deque to hold all tiles (including the guardian).
     */
    private final FixedMaxSizeDeque<Tile> tiles;
    private final FixedMaxSizeDeque<Addon> addons;

    /**
     * Points to the newest tile added.
     */
    private Tile lastTile;

    private Vector3D lastLeftRow, lastRightRow;

    private float lengthTillNextRow;

    private final int nCols = 6;

    public Terrain(int maxSegments, Vector3D startMid, float segWidth, float segLength) {
        this.segWidth = segWidth;
        this.segLength = segLength;
        this.rowSpacing = 0.75f;
        this.lengthTillNextRow = this.rowSpacing;

        // Initialize the fixed-size deque with capacity maxSegments + 1
        this.tiles = new FixedMaxSizeDeque<>(maxSegments + 1);
        this.addons = new FixedMaxSizeDeque<>(maxSegments + 1);

        // Create the "guardian" tile as the first tile
        Vector3D startLeft = V3(startMid.sub(segWidth / 2, 0, 0));
        Vector3D startRight = V3(startMid.add(segWidth / 2, 0, 0));

        // Guardian tile: store near and far edges the same
        Tile guardian = new Tile(startLeft,                      // nearLeft
                startRight,                     // nearRight
                startLeft.sub(0, 0, 0.01f),      // farLeft
                startRight.sub(0, 0, 0.01f),     // farRight
                0.0f                            // slope
        );
        this.tiles.pushBack(guardian);
        this.lastTile = guardian;

        // Initialize angles and offsets.
        this.dHorizontalAng = 0.0f;
        this.dVerticalAng = 0.0f;

        this.commandBuffer = new PreallocatedCommandBuffer();

        // General Executor decides which of two other executors should execute some command.
        this.generalExecutor = new GeneralExecutor();
        // For landscape commands
        this.landscapeCommandExecutor = new LandscapeBuildingCommandsExecutor();
        // For addon grid commands
        this.addonsCommandExecutor = new AddonsBuildingCommandsExecutor();

        this.structureGridStack = new ArrayStack<>();
        this.addonStack = new ArrayStack<>();
        this.structureStack = new ArrayStack<>();
        this.waitingStructuresQueue = new ArrayQueue<>();

        this.gridBrush = new GridBrush();
        this.tileBrush = new TileBrush();

        this.leftSideBuffer = new PreallocatedCoordinateBuffer();
        this.rightSideBuffer = new PreallocatedCoordinateBuffer();

        lastLeftRow = new Vector3D(startLeft.x,startLeft.y,startLeft.z);
        lastRightRow = new Vector3D(startRight.x,startRight.y,startRight.z);

        leftSideBuffer.addPos(lastLeftRow.x,lastLeftRow.y,lastLeftRow.z);
        rightSideBuffer.addPos(lastRightRow.x,lastRightRow.y,lastRightRow.z);

    }

    /**
     * Helper: builds a new tile and adds it to the deque.
     */
    private void addTile(Vector3D nl, Vector3D nr, Vector3D fl, Vector3D fr) {
        float slopeVal = (float) atan((fl.y - nl.y) / Math.sqrt((fl.x - nl.x) * (fl.x - nl.x) + (fl.z - nl.z) * (fl.z - nl.z)));
        Tile t = new Tile(nl, nr, fl, fr, slopeVal);
        this.tiles.pushBack(t);
        lastTile = t;
        Vector3D edgeLeft = fl.sub(nl), edgeRight = fr.sub(nr);
        float len = (float) Math.sqrt(edgeLeft.sqlen());
        while(len >= lengthTillNextRow){
            float dist = len - lengthTillNextRow;

            Vector3D leftSide = nl.add(edgeLeft.withLen(dist));
            Vector3D rightSide = nr.add(edgeRight.withLen(dist));
            leftSideBuffer.addPos(leftSide.x,leftSide.y,leftSide.z);
            rightSideBuffer.addPos(rightSide.x,rightSide.y,rightSide.z);

            len -= lengthTillNextRow;
            lengthTillNextRow = rowSpacing;

            currRowCount += 1;
        }
        lengthTillNextRow -= len;
    }

    /**
     * Creates one additional tile, continuing from lastTile’s far edge.
     */
    public void addSegment() {
        if (tiles.size() == tiles.getMaxSize()) {
            throw new IllegalStateException("Already at capacity");
        }

        Vector3D l1 = lastTile.farLeft;
        Vector3D r1 = lastTile.farRight;

        Vector3D axis;
        if (tiles.size() == 1) {
            axis = V3(0, -1, 0);
        } else {
            // Get the previous tile (second-to-last in the deque)
            Tile prevTile = tiles.get(tiles.size() - 2);
            Vector3D r0 = prevTile.farRight;
            Vector3D l0 = prevTile.farLeft;
            Vector3D e1 = l0.sub(r0);
            Vector3D e2 = r1.sub(r0);
            axis = e1.crossProduct(e2).mult(1);
        }

        Vector3D mid = l1.add(r1).div(2);
        Vector3D newL1 = rotateAroundAxis(mid, axis, l1, dHorizontalAng);
        Vector3D newR1 = rotateAroundAxis(mid, axis, r1, dHorizontalAng);

        dHorizontalAng = 0.0f;

        Vector3D dir = rotateAroundTwoPoints(axis, V3(0, 0, 0), newR1.sub(newL1), PI / 2).withLen(segLength);

        Vector3D l2 = newL1.add(dir);
        Vector3D r2 = newR1.add(dir);

        l2 = rotateAroundTwoPoints(newR1, newL1, l2, -dVerticalAng);
        r2 = rotateAroundTwoPoints(newR1, newL1, r2, -dVerticalAng);

        dVerticalAng = 0.0f;

        // Update the last tile's far edge.
        // Remove the old last tile, update it, and push it back.
        Tile oldLast = tiles.popLast();
        Tile updatedLast = new Tile(oldLast.nearLeft, oldLast.nearRight, newL1, newR1, oldLast.slope);
        tiles.pushBack(updatedLast);
        lastTile = updatedLast;

        // Add the new tile segment.
        addTile(newL1, newR1, l2, r2);
    }

    public void removeOldTiles(float playerZ) {
        while (!tiles.isEmpty() && tiles.getFirst().farLeft.z > playerZ + 10f) {
            tiles.removeFirst();
        }
    }

    private void addVerticalAngle(float angle) {
        this.dVerticalAng = angle;
    }

    private void addHorizontalAngle(float angle) {
        //assert abs(angle) < PI / 5 : "Too drastic angle change";
        this.dHorizontalAng = angle;
    }

    private void setHorizontalAngle(float angle) {
       // assert abs(angle - currHorizontalAng) < PI / 5 : "Too drastic angle change";
        this.dHorizontalAng = angle - currHorizontalAng;
        currHorizontalAng += dHorizontalAng;
    }

    private void setVerticalAngle(float angle) {
        this.dVerticalAng = angle - currVerticalAng;
        currVerticalAng += dVerticalAng;
    }

    public int getTileCount() {
        return tiles.size();
    }

    public Tile getTile(int i) {
        return tiles.get(i);
    }

    public int getAddonCount() {
        return addons.size();
    }

    public Addon getAddon(int i) {
        return addons.get(i);
    }

    public void enqueueStructure(TerrainStructure what) {
        waitingStructuresQueue.enqueue(what);
        commandBuffer.addCommand(CMD_START_STRUCTURE_LANDSCAPE);
    }

    private final PreallocatedCommandBuffer commandBuffer;

    public void generateChunks(int nChunks) {
        while (nChunks != 0 && commandBuffer.hasAnyCommands()) {
            commandBuffer.executeFirstCommand(generalExecutor);
            --nChunks;
        }
        if(!commandBuffer.hasAnyCommands()){
            leftSideBuffer.clear();
            rightSideBuffer.clear();
            leftSideBuffer.addPos(lastLeftRow.x,lastLeftRow.y,lastLeftRow.z);
            rightSideBuffer.addPos(lastRightRow.x,lastRightRow.y,lastRightRow.z);
        }
    }

    private static final float MIN_CMD_GRID = 32;

    /**
     * Based on command code, tells one of the executors to execute a command.
     */
    private class GeneralExecutor implements CommandExecutor {
        @Override
        public void execute(float[] buffer, int offset, int length) {
            int code =(int)(buffer[offset]);
            //printCommand(buffer, offset, length);
            if (code < MIN_CMD_GRID) {
                landscapeCommandExecutor.execute(buffer, offset, length);
            } else {
                addonsCommandExecutor.execute(buffer, offset, length);
            }
        }
    }

    private void printCommand(float[] buffer, int offset, int length) {
        int code = (int)(buffer[offset]);
        if (code == CMD_SET_H_ANG) {
            System.out.println("SET H ANG " + buffer[offset + 1]);
        } else if (code == CMD_SET_V_ANG) {
            System.out.println("SET V ANG " + buffer[offset + 1]);
        } else if (code == CMD_ADD_V_ANG) {
            System.out.println("ADD V ANG " + buffer[offset + 1]);
        } else if (code == CMD_ADD_H_ANG) {
            System.out.println("ADD H ANG " + buffer[offset + 1]);
        } else if (code == CMD_ADD_SEG) {
            System.out.println("ADD SEG");
        } else if (code == CMD_START_STRUCTURE_LANDSCAPE) {
            System.out.println("START STRUCTURE LANDSCAPE");
        } else if (code == CMD_FINISH_STRUCTURE_LANDSCAPE) {
            System.out.println("FINISH STRUCTURE LANDSCAPE");
        } else if (code == CMD_RESERVE_VERTICAL) {
            int row = (int) buffer[offset + 1];
            int col = (int) buffer[offset + 2];
            int segLength = (int) buffer[offset + 3];
            System.out.println("RESERVE VERTICAL" + row + "," + col + "," + segLength);
        } else if (code == CMD_RESERVE_HORIZONTAL) {
            int row = (int) buffer[offset + 1];
            int col = (int) buffer[offset + 2];
            int segLength = (int) buffer[offset + 3];
            System.out.println("RESERVE HORIZONTAL" + row + "," + col + "," + segLength);
        } else if (code == CMD_RESERVE_RANDOM_VERTICAL) {
            System.out.println("RESERVE RANDOM VERTICAL" + buffer[offset + 1]);
        } else if (code == CMD_RESERVE_RANDOM_HORIZONTAL) {
            System.out.println("RESERVE RANDOM HORIZONTAL" + buffer[offset + 1]);
        } else if (code == CMD_FINISH_STRUCTURE_ADDONS) {
            System.out.println("FINISH STRUCTURE ADDONS");
        } else if (code == CMD_START_STRUCTURE_ADDONS) {
            System.out.println("START STRUCTURE ADDONS");
        } else {
            throw new IllegalArgumentException("Unknown command code: " + code);
        }
    }


    private final GeneralExecutor generalExecutor;

    private static final int CMD_SET_H_ANG = 1;
    private static final int CMD_SET_V_ANG = 2;
    private static final int CMD_ADD_H_ANG = 3;
    private static final int CMD_ADD_V_ANG = 4;
    private static final int CMD_ADD_SEG = 5;
    private static final int CMD_FINISH_STRUCTURE_LANDSCAPE = 6;
    private static final int CMD_START_STRUCTURE_LANDSCAPE = 7;

    private Vector3D[] getField(int row, int col){
        Vector3D nearLeftOnSide = V3(
                leftSideBuffer.getX(row-1),
                leftSideBuffer.getY(row-1),
                leftSideBuffer.getZ(row-1)
        );
        Vector3D nearRightOnSide = V3(
                rightSideBuffer.getX(row-1),
                rightSideBuffer.getY(row-1),
                rightSideBuffer.getZ(row-1)
        );
        Vector3D farLeftOnSide = V3(
                leftSideBuffer.getX(row),
                leftSideBuffer.getY(row),
                leftSideBuffer.getZ(row)
        );
        Vector3D farRightOnSide = V3(
                rightSideBuffer.getX(row),
                rightSideBuffer.getY(row),
                rightSideBuffer.getZ(row)
        );
        Vector3D nearEdge = nearRightOnSide.sub(nearLeftOnSide);
        Vector3D farEdge = farRightOnSide.sub(farLeftOnSide);
        Vector3D nearLeft = nearLeftOnSide.add(nearEdge.mult(col-1).div(nCols+1));
        Vector3D farLeft = nearLeftOnSide.add(nearEdge.mult(col).div(nCols+1));
        Vector3D nearRight = nearRightOnSide.add(farEdge.mult(col-1).div(nCols+1));
        Vector3D farRight = nearRightOnSide.add(farEdge.mult(col).div(nCols+1));
        return new Vector3D[]{
                nearLeft,
                nearRight,
                farLeft,
                farRight
        };
    }


    private class LandscapeBuildingCommandsExecutor implements CommandExecutor {
        @Override
        public void execute(float[] buffer, int offset, int length) {
            float code = buffer[offset];
            if (code == CMD_SET_H_ANG) {
                float angle = buffer[offset + 2];
                setHorizontalAngle(angle);

            } else if (code == CMD_SET_V_ANG) {
                float angle = buffer[offset + 2];
                setVerticalAngle(angle);

            } else if (code == CMD_ADD_V_ANG) {
                float delta = buffer[offset + 2];
                addVerticalAngle(delta);

            } else if (code == CMD_ADD_H_ANG) {
                float delta = buffer[offset + 2];
                addHorizontalAngle(delta);

            } else if (code == CMD_ADD_SEG) {
                addSegment();
            } else if (code == CMD_START_STRUCTURE_LANDSCAPE) {
                TerrainStructure what = waitingStructuresQueue.dequeue();
                structureStack.push(what);
                what.generateTiles(tileBrush); // generates commands, not tiles.
                commandBuffer.addCommand(CMD_FINISH_STRUCTURE_LANDSCAPE);
                lastStructureStartRowCount = currRowCount;
            } else if (code == CMD_FINISH_STRUCTURE_LANDSCAPE) {

                TerrainStructure thatStructure = structureStack.pop();
                int nRowsAdded = currRowCount - lastStructureStartRowCount;
                System.out.println("ROWS ADDED: "+nRowsAdded);
                assert thatStructure != null;
                commandBuffer.addCommand(CMD_START_STRUCTURE_ADDONS, nRowsAdded);
                thatStructure.generateAddons(gridBrush, nRowsAdded, nCols);
                commandBuffer.addCommand(CMD_FINISH_STRUCTURE_ADDONS);

            } else {
                throw new IllegalArgumentException("Unknown command code: " + code);
            }
        }
    }

    private final LandscapeBuildingCommandsExecutor landscapeCommandExecutor;

    private static final int CMD_RESERVE_VERTICAL = 1 + 32;
    private static final int CMD_RESERVE_HORIZONTAL = 2 + 32;
    private static final int CMD_RESERVE_RANDOM_VERTICAL = 3 + 32;
    private static final int CMD_RESERVE_RANDOM_HORIZONTAL = 4 + 32;
    private static final int CMD_FINISH_STRUCTURE_ADDONS = 5 + 32;
    private static final int CMD_START_STRUCTURE_ADDONS = 6 + 32;


    private class AddonsBuildingCommandsExecutor implements CommandExecutor {
        @Override
        public void execute(float[] buffer, int offset, int length) {
            float code = buffer[offset];
            GridCreator latest = structureGridStack.peek();
            if (code == CMD_RESERVE_VERTICAL) {
                int row = (int) buffer[offset + 2];
                int col = (int) buffer[offset + 3];
                int segLength = (int) buffer[offset + 4];
                latest.reserveVertical(row, col, segLength);
                for (int i = 0; i < segLength; ++i) {
                    Addon addon = addonStack.pop();
                    Vector3D[] field = getField(row + i, col);
                    addon.place(field[0],field[1],field[2],field[3]);
                    addons.pushBack(addon);
                }
            } else if (code == CMD_RESERVE_HORIZONTAL) {
                int row = (int) buffer[offset + 2];
                int col = (int) buffer[offset + 3];
                int segLength = (int) buffer[offset + 4];
                latest.reserveHorizontal(row, col, segLength);
                for (int i = 0; i < segLength; ++i) {
                    Addon addon = addonStack.pop();
                    Vector3D[] field = getField(row, col+i);
                    addon.place(field[0],field[1],field[2],field[3]);
                    addons.pushBack(addon);
                }
            } else if (code == CMD_RESERVE_RANDOM_VERTICAL) {
                int segLength = (int) buffer[offset + 2];
                GridSegment found = latest.reserveRandomFittingVertical(segLength);
                for (int i = 0; i < segLength; ++i) {
                    Addon addon = addonStack.pop();
                    Vector3D[] field = getField(found.row + i, found.col);
                    addon.place(field[0],field[1],field[2],field[3]);
                    addons.pushBack(addon);
                }
            } else if (code == CMD_RESERVE_RANDOM_HORIZONTAL) {
                int segLength = (int) buffer[offset + 2];
                System.out.println("Got command to reserve random hor. of len: "+buffer[offset + 2]);
                GridSegment found = latest.reserveRandomFittingHorizontal(segLength);
                for (int i = 0; i < segLength; ++i) {
                    Addon addon = addonStack.pop();
                    Vector3D[] field = getField(found.row, found.col + i);
                    addon.place(field[0],field[1],field[2],field[3]);
                    addons.pushBack(addon);
                }
            } else if (code == CMD_FINISH_STRUCTURE_ADDONS) {
                structureGridStack.pop().destroy();
            } else if (code == CMD_START_STRUCTURE_ADDONS) {
                int nRowsAdded = (int)(buffer[offset+2]);
                GridCreator parent = structureGridStack.peek();
                System.out.println("GRID CREATOR CREATED WITH: "+nRowsAdded+" ROWS AND "+nCols+" COLS");
                structureGridStack.push(new GridCreator(nRowsAdded, nCols, parent, lastStructureStartRowCount));
            } else {
                throw new IllegalArgumentException("Unknown command code: " + code);
            }
        }
    }

    private final AddonsBuildingCommandsExecutor addonsCommandExecutor;


}
