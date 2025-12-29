package com.example.game3d_opengl.game.player.player_character;

import static com.example.game3d_opengl.game.util.GameMath.PI;
import static com.example.game3d_opengl.game.util.GameMath.cos;
import static com.example.game3d_opengl.game.util.GameMath.getNormal;
import static com.example.game3d_opengl.game.util.GameMath.rayTriangleDistance;
import static com.example.game3d_opengl.game.util.GameMath.rotY;
import static com.example.game3d_opengl.game.util.GameMath.sin;
import static com.example.game3d_opengl.game.util.GameMiscUtil.maxAll;
import static com.example.game3d_opengl.game.util.GameMiscUtil.minAll;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import static java.lang.Float.max;
import static java.lang.Math.abs;
import static java.lang.Math.min;
import static java.lang.Math.signum;

import com.example.game3d_opengl.game.WorldActor;
import com.example.game3d_opengl.game.player.player_state.infos.PlayerAffectingInfo;
import com.example.game3d_opengl.game.player.player_state.infos.PlayerAllInfoVisitor;
import com.example.game3d_opengl.game.player.player_state.infos.jump.PlayerJumpInfo;
import com.example.game3d_opengl.game.player.player_state.infos.jump.PlayerAllJumpLogicImplementation;
import com.example.game3d_opengl.rendering.object3d.UnbatchedObject3DWithOutline;
import com.example.game3d_opengl.rendering.util3d.vector.Vector2D;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Tile;

import static com.example.game3d_opengl.game.player.player_character.PlayerConfig.*;
import static com.example.game3d_opengl.game.player.player_character.PlayerAssets.*;


/**
 * Represents the player character in the game world.
 * Handles movement, collision detection, physics, and rendering.
 * The player moves along the terrain and can interact with various game elements.
 */
public class Player implements WorldActor , PlayerAllInfoVisitor {


    // Instance fields
    private final UnbatchedObject3DWithOutline object3D;
    private Vector3D dir;
    private Vector3D move;

    // Rotation state
    private float stickyRotationTime = 0.0f;
    private float stickyRotationAng = 0.0f;

    // Physics state
    private Tile tileBelow;
    private long nearestTileId = -1;

    private final PlayerAllJumpLogicImplementation jumpLogicImplementation;

    /**
     * Private constructor to enforce factory pattern.
     * Initializes the player with default movement and rotation values.
     * 
     * @param object3D the 3D object representation of the player
     */
    private Player(UnbatchedObject3DWithOutline object3D) {
        this.object3D = object3D;
        this.dir = new Vector3D(INITIAL_DIRECTION_X, INITIAL_DIRECTION_Y, INITIAL_DIRECTION_Z);
        this.move = new Vector3D(0, 0, 0);
        this.jumpLogicImplementation = new PlayerAllJumpLogicImplementation();
        this.interactableAPI = new InteractableAPI(this);
    }
    
    private static UnbatchedObject3DWithOutline getObject3D() {
        if (PLAYER_OBJECT == null) {
            throw new IllegalStateException(ERROR_ASSETS_NOT_LOADED);
        }
        return PLAYER_OBJECT;
    }

    /**
     * Factory method to create a new Player instance.
     * 
     * @return a new Player instance
     * @throws IllegalStateException if assets haven't been loaded
     */
    public static Player createPlayer() {
        return new Player(getObject3D());
    }

    /**
     * Gets the current direction vector of the player.
     * 
     * @return the normalized direction vector
     */
    public Vector3D getDir() {
        return dir;
    }

    /**
     * Calculates the determinant of the 3x3 matrix formed by three vectors.
     * Used for solving the surface projection equation.
     * 
     * @param n normal vector
     * @param u first tangent vector
     * @param w second tangent vector
     * @return the determinant value
     */
    private float calculateDeterminant(Vector3D n, Vector3D u, Vector3D w) {
        return n.x * u.y * w.z - n.x * u.z * w.y
                - n.y * u.x * w.z + n.y * u.z * w.x
                + n.z * u.x * w.y - n.z * u.y * w.x;
    }

    private float calculateBeta(Vector3D n, Vector3D tangent2, Vector3D dir, float det) {
        return (n.x * dir.y * tangent2.z - n.x * dir.z * tangent2.y
                - n.y * dir.x * tangent2.z + n.y * dir.z * tangent2.x
                + n.z * dir.x * tangent2.y - n.z * dir.y * tangent2.x) / det;
    }

    /**
     * Calculates the gamma coefficient for surface projection.
     */
    private float calculateGamma(Vector3D n, Vector3D tangent1, Vector3D dir, float det) {
        return (n.x * tangent1.y * dir.z - n.x * tangent1.z * dir.y
                - n.y * tangent1.x * dir.z + n.y * tangent1.z * dir.x
                + n.z * tangent1.x * dir.y - n.z * tangent1.y * dir.x) / det;
    }

    // Physics state
    private float fallSpeed = 0f;

    /**
     * Handles movement when the player is falling.
     * Applies gravity and maintains horizontal movement.
     */
    private void handleFallingMovement() {
        // Maintain horizontal movement while falling
        Vector3D dwl = dir.withLen(PLAYER_SPEED);
        move = V3(dwl.x, move.y, dwl.z);

        // Apply gravity
        move = V3(move.x, move.y - fallSpeed, move.z);
        fallSpeed += FALL_ACCELERATION;
    }

    @Override
    public void visit(PlayerJumpInfo.PlayerHasFooting info){
        // Record hitTri tile (for bookkeeping like nearestTileId)
        this.tileBelow = info.tile;
        assert info.tile.getID() >= nearestTileId;
        this.nearestTileId = info.tile.getID();

        // Compute ground sliding move using the provided contact triangles
        // Choose the closest triangle under the player

        Vector3D[] hitTri = findCollisionTriangle(info.tile);
        assert hitTri != null;
        fallSpeed = 0f;

        Vector3D u = hitTri[1].sub(hitTri[0]);
        Vector3D w = hitTri[2].sub(hitTri[0]);
        Vector3D n = u.crossProduct(w);

        float det = calculateDeterminant(n, u, w);
        if (Math.abs(det) > 1e-6f) {
            float beta = calculateBeta(n,  w, dir, det);
            float gamma = calculateGamma(n, u, dir, det);
            move = u.mult(beta).add(w.mult(gamma)).withLen(PLAYER_SPEED);
        } else {
            move = dir.withLen(PLAYER_SPEED);
        }

        // Forward jump-related decisions to jump logic
        info.accept(jumpLogicImplementation);
    }

    @Override
    public void visit(PlayerJumpInfo.PlayerWantsJump info) {
        info.accept(jumpLogicImplementation);
    }

    @Override
    public void visit(PlayerJumpInfo.PlayerHitsGroundSoon info) {
        info.accept(jumpLogicImplementation);
    }

    @Override
    public void visit(PlayerJumpInfo.PlayerHitsSpikeSoon info) {
        info.accept(jumpLogicImplementation);
    }

    @Override
    public void visit(PlayerJumpInfo.PlayerHasJumpCharges info) {
        info.accept(jumpLogicImplementation);
    }


    /**
     * Updates the player's physics and movement before rendering.
     * Handles gravity, collision response, and movement calculations.
     *
     * @param dtMillis time delta in milliseconds
     */
    @Override
    public void updateBeforeDraw(float dtMillis) {
        // Update sticky rotation (gradual rotation decay)
        stickyRotationTime = max(0f, stickyRotationTime - dtMillis);
        if (stickyRotationTime == 0 && stickyRotationAng != 0) {
            float dYaw = minByAbs(signum(stickyRotationAng) * STICKY_ROTATION_ANGLE_DECAY_RATE * dtMillis, stickyRotationAng);
            object3D.objYaw -= dYaw;
            stickyRotationAng -= dYaw;
        }


        // When this method is called, all infos should have already be handled.
        // So basically, here we just check if player jumps or falls.
        // If yes, override the move determined by the tile below, and make it look like jump/fall.
        // If no, the move vector should be kept as is.

        if(jumpLogicImplementation.shouldJump()){
            System.exit(0); // TODO actually make a jump
        } else if (tileBelow == null) {
            handleFallingMovement();
        }



        // Apply movement to position
        object3D.objX += move.x * dtMillis;
        object3D.objY += move.y * dtMillis;
        object3D.objZ += move.z * dtMillis;

        // Update visual rotation based on movement
        object3D.objPitch -= dtMillis * PLAYER_SPEED / (PI * PLAYER_HEIGHT) * 2 * PI;

    }


    @Override
    public void draw(float[] mvpMatrix) {
        if (object3D != null) {
            object3D.draw(mvpMatrix);
        }
    }

    @Override
    public void updateAfterDraw(float dt) {
        // Reset tile below after physics update
        tileBelow = null;
        jumpLogicImplementation.resetFrame();
    }

    @Override
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {
        if (object3D != null) {
            object3D.cleanupGPUResourcesRecursivelyOnContextLoss();
        }
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        if (object3D != null) {
            object3D.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    /**
     * Returns the minimum value by absolute magnitude.
     * 
     * @param a first value
     * @param b second value
     * @return the value with smaller absolute magnitude
     */
    private float minByAbs(float a, float b) {
        return abs(a) < abs(b) ? a : b;
    }

    /**
     * Gets the ID of the nearest tile to the player.
     * Used for terrain management and cleanup.
     * 
     * @return the nearest tile ID, or -1 if no tile is nearby
     */
    public long getNearestTileId() {
        return nearestTileId;
    }

    /**
     * Handles touch input for player rotation.
     * Applies rotation based on horizontal swipe distance and updates
     * both the movement direction and visual rotation.
     * 
     * @param dx horizontal swipe distance in pixels
     */
    public void rotDirOnTouch(float dx) {
        // Calculate rotation angle based on swipe distance
        float dYaw = dx * ROTATION_SWIPE_SENSITIVITY; // in radians
        
        // Update movement direction
        dir = rotY(dir, dYaw);
        
        // Update visual rotation (convert radians to degrees for OpenGL)
        object3D.objYaw -= dYaw * 180.0f / PI;

        // Apply sticky rotation effect for smoother movement
        stickyRotationAng = stickyRotationAng - dx * STICKY_ROTATION_COEFFICIENT;
        stickyRotationTime = STICKY_ROTATION_LASTING_TIME;
        object3D.objYaw -= dx * STICKY_ROTATION_COEFFICIENT;
    }

    private Vector3D[] findCollisionTriangle(Tile tile){
        Vector3D origin = V3(object3D.objX, object3D.objY, object3D.objZ);
        for (Vector3D[] tri : tile.triangles) {
            Vector3D triNormal = getNormal(tri);
            float d = rayTriangleDistance(
                    origin,
                    triNormal.mult(-1),
                    tri[0], tri[1], tri[2]
            );
            if (!Float.isInfinite(d) && d < (PLAYER_HEIGHT + fallSpeed) * FALL_COLLISION_SAFETY_MULTIPLIER && d > PLAYER_HEIGHT/2) {
                return tri;
            }
        }
        return null;
    }
    private boolean collidesTile(Tile tile){
        return findCollisionTriangle(tile) != null;
    }


    public float getX() { return object3D.objX; }
    public float getY() { return object3D.objY; }
    public float getZ() { return object3D.objZ; }

    // InteractableAPI instance
    private final InteractableAPI interactableAPI;

    /**
     * Returns an API for interactables (like Potions, Addons) to interact with the player.
     * Allows adding PlayerAffectingInfo and accessing safe player state.
     */
    public InteractableAPI getInteractableAPI() {
        return interactableAPI;
    }

    /**
     * API class for interactables to interact with the player.
     */
    public static class InteractableAPI {
        private final Player player;

        public InteractableAPI(Player player) {
            this.player = player;
        }

        /**
         * Adds a PlayerAffectingInfo to the player.
         */
        public void addInfo(PlayerAffectingInfo<? super PlayerAllInfoVisitor> info) {
            info.accept(player);
        }

        /**
         * Gets the player's X position.
         */
        public float getPlayerX() {
            return player.getX();
        }

        /**
         * Gets the player's Y position.
         */
        public float getPlayerY() {
            return player.getY();
        }

        /**
         * Gets the player's Z position.
         */
        public float getPlayerZ() {
            return player.getZ();
        }

        public boolean collidesTile(Tile tile){
            return player.collidesTile(tile);
        }

        // Add other getters as needed for interactables
    }


}