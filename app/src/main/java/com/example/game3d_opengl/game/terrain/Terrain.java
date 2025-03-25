package com.example.game3d_opengl.game.terrain;

import static com.example.game3d_opengl.engine.util3d.GameMath.PI;
import static com.example.game3d_opengl.engine.util3d.GameMath.rotateAroundAxis;
import static com.example.game3d_opengl.engine.util3d.GameMath.rotateAroundTwoPoints;
import static com.example.game3d_opengl.engine.util3d.vector.Vector3D.V3;
import static java.lang.Math.abs;
import static java.lang.Math.atan;

import com.example.game3d_opengl.game.terrain.terrainutil.FixedMaxSizeDeque;
import com.example.game3d_opengl.engine.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain.terrainutil.execbuffer.CommandExecutor;
import com.example.game3d_opengl.game.terrain.terrainutil.execbuffer.PreallocatedCommandBuffer;

/**
 * Terrain with a fixed-size deque of tiles. We keep a `lastTile` pointer
 * to always build from the newest tile, even as older ones remain in the deque.
 */
public class Terrain {

    // API for terrain structures.
    public class TerrainBrush {
        // Each command is stored as [commandCode, optionalArg].
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

    private final TerrainBrush brush = new TerrainBrush();

    private final float segWidth, segLength;
    private float dHorizontalAng, dVerticalAng;
    private float currVerticalAng = 0.0f, currHorizontalAng = 0.0f;

    /**
     * Deque to hold all tiles (including the guardian).
     */
    private final FixedMaxSizeDeque<Tile> tiles;

    /**
     * Points to the newest tile added.
     */
    private Tile lastTile;

    private final int nCols = 6;

    public Terrain(int maxSegments, Vector3D startMid, float segWidth, float segLength) {
        this.segWidth = segWidth;
        this.segLength = segLength;

        // Initialize the fixed-size deque with capacity maxSegments + 1
        this.tiles = new FixedMaxSizeDeque<>(maxSegments + 1);

        // Create the "guardian" tile as the first tile
        Vector3D startLeft = V3(startMid.sub(segWidth / 2, 0, 0));
        Vector3D startRight = V3(startMid.add(segWidth / 2, 0, 0));

        // Guardian tile: store near and far edges the same
        Tile guardian = new Tile(
                startLeft,                      // nearLeft
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
        this.commandExecutor = new TerrainBuildingCommandsExecutor();
    }

    /**
     * Helper: builds a new tile and adds it to the deque.
     */
    private void addTile(Vector3D nl, Vector3D nr, Vector3D fl, Vector3D fr) {
        float slopeVal = (float) atan(
                (fl.y - nl.y) / Math.sqrt(
                        (fl.x - nl.x) * (fl.x - nl.x) + (fl.z - nl.z) * (fl.z - nl.z)
                )
        );
        Tile t = new Tile(nl, nr, fl, fr, slopeVal);
        this.tiles.pushBack(t);
        lastTile = t;
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

        Vector3D dir = rotateAroundTwoPoints(
                axis,
                V3(0, 0, 0),
                newR1.sub(newL1),
                PI / 2
        ).withLen(segLength);

        Vector3D l2 = newL1.add(dir);
        Vector3D r2 = newR1.add(dir);

        l2 = rotateAroundTwoPoints(newR1, newL1, l2, -dVerticalAng);
        r2 = rotateAroundTwoPoints(newR1, newL1, r2, -dVerticalAng);

        dVerticalAng = 0.0f;

        // Update the last tile's far edge.
        // Remove the old last tile, update it, and push it back.
        Tile oldLast = tiles.popLast();
        Tile updatedLast = new Tile(
                oldLast.nearLeft,
                oldLast.nearRight,
                newL1,
                newR1,
                oldLast.slope
        );
        tiles.pushBack(updatedLast);
        lastTile = updatedLast;

        // Add the new tile segment.
        addTile(newL1, newR1, l2, r2);
    }

    public void removeOldTiles(float playerZ){
        while(!tiles.isEmpty() && tiles.getFirst().farLeft.z > playerZ + 10f){
            tiles.removeFirst();
        }
    }

    private void addVerticalAngle(float angle){
        this.dVerticalAng = angle;
    }

    private void addHorizontalAngle(float angle){
        assert abs(angle) < PI / 5 : "Too drastic angle change";
        this.dHorizontalAng = angle;
    }

    private void setHorizontalAngle(float angle) {
        assert abs(angle - currHorizontalAng) < PI / 5 : "Too drastic angle change";
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

    public void enqueueStructure(TerrainStructure what){
        what.generateTiles(brush); // actually doesnt generate. (Because of how brush works)
    }

    private final PreallocatedCommandBuffer commandBuffer;

    public void generateChunks(int nChunks){
        while(nChunks != 0 && commandBuffer.hasAnyCommands()){
            commandBuffer.executeFirstCommand(commandExecutor);
            --nChunks;
        }
    }


    private static final float CMD_SET_H_ANG = 0.00001f;
    private static final float CMD_SET_V_ANG = 0.00011f;
    private static final float CMD_ADD_H_ANG = 0.00005f;
    private static final float CMD_ADD_V_ANG = 0.00051f;
    private static final float CMD_ADD_SEG = 0.00511f;


    private class TerrainBuildingCommandsExecutor implements CommandExecutor {
        @Override
        public void execute(float[] buffer, int offset, int length) {
            float code = buffer[offset];

            if (code == CMD_SET_H_ANG) {
                float angle = buffer[offset + 1];
                setHorizontalAngle(angle);

            } else if (code == CMD_SET_V_ANG) {
                float angle = buffer[offset + 1];
                setVerticalAngle(angle);

            } else if (code == CMD_ADD_V_ANG) {
                float delta = buffer[offset + 1];
                addVerticalAngle(delta);

            } else if (code == CMD_ADD_H_ANG) {
                float delta = buffer[offset + 1];
                addHorizontalAngle(delta);

            } else if (code == CMD_ADD_SEG) {
                addSegment();

            } else {
                throw new IllegalArgumentException("Unknown command code: " + code);
            }
        }
    }


    private final TerrainBuildingCommandsExecutor commandExecutor;
}
