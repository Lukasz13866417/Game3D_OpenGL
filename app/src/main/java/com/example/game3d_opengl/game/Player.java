package com.example.game3d_opengl.game;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.rendering.util3d.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.GameMath.getNormal;
import static com.example.game3d_opengl.rendering.util3d.GameMath.rayTriangleDistance;
import static com.example.game3d_opengl.rendering.util3d.GameMath.rotY;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import static java.lang.Float.max;
import static java.lang.Math.abs;
import static java.lang.Math.signum;

import android.content.res.AssetManager;
import android.util.Log;

import com.example.game3d_opengl.rendering.object3d.ModelCreator;
import com.example.game3d_opengl.rendering.object3d.Object3D;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain_api.Tile;

import java.io.IOException;

/**
 * Represents the player character in the game world.
 * Handles movement, collision detection, physics, and rendering.
 * The player moves along the terrain and can interact with various game elements.
 */
public class Player implements WorldActor {

    // Constants for magic numbers
    public static final float PLAYER_WIDTH = 0.132f;
    public static final float PLAYER_HEIGHT = PLAYER_WIDTH * 3.54f;
    
    // Physics constants
    private static final float FALL_ACCELERATION = 1e-6f;
    private static final float COLLISION_THRESHOLD_MULTIPLIER = 1.05f;
    private static final float PLAYER_SPEED = 0.04f;
    
    // Rotation constants
    private static final float STICKY_ROTATION_LASTING_TIME = 42f;
    private static final float STICKY_ROTATION_ANGLE_DECAY_RATE = 0.0575f;
    private static final float STICKY_ROTATION_COEFFICIENT = 0.0085f;
    private static final float ROTATION_SWIPE_SENSITIVITY = 0.00052f;
    
    // Movement constants
    private static final float INITIAL_DIRECTION_X = 0f;
    private static final float INITIAL_DIRECTION_Y = 0f;
    private static final float INITIAL_DIRECTION_Z = -1f;
    private static final float INITIAL_POSITION_X = 0f;
    private static final float INITIAL_POSITION_Y = -0.5f;
    private static final float INITIAL_POSITION_Z = -0.5f;
    
    // Asset loading constants
    private static final String PLAYER_MODEL_FILENAME = "tire.obj";
    private static final float MODEL_ROTATION_X = PI / 2;
    private static final float MODEL_ROTATION_Y = PI / 2;
    
    // Error messages
    private static final String ERROR_ASSETS_NOT_LOADED = "Player assets not loaded. Call LOAD_PLAYER_ASSETS first.";
    private static final String ERROR_ASSET_LOADING = "Failed to load player assets: ";
    private static final String TAG = "Player";

    /**
     * Loads the player's 3D model and creates the Object3D builder.
     * This method must be called before creating any Player instances.
     * 
     * @param assetManager the Android asset manager for loading model files
     * @throws RuntimeException if asset loading fails
     */
    public static void LOAD_PLAYER_ASSETS(AssetManager assetManager) {
        if (assetManager == null) {
            throw new IllegalArgumentException("AssetManager cannot be null");
        }
        
        ModelCreator playerCreator = new ModelCreator(assetManager);
        try {
            // Load and process the 3D model
            playerCreator.load(PLAYER_MODEL_FILENAME);
            playerCreator.centerVerts();
            playerCreator.rotateX(MODEL_ROTATION_X);
            playerCreator.rotateY(MODEL_ROTATION_Y);
            playerCreator.scaleX(PLAYER_WIDTH);
            playerCreator.scaleY(PLAYER_HEIGHT);
            playerCreator.scaleZ(PLAYER_HEIGHT);

            // Create the Object3D builder with the processed model
            playerBuilder = new Object3D.Builder()
                    .angles(0, 0, 0)
                    .edgeColor(CLR(1.0f, 1.0f, 1.0f, 1.0f))
                    .fillColor(CLR(0.0f, 0.0f, 0.0f, 1.0f))
                    .position(INITIAL_POSITION_X, INITIAL_POSITION_Y, INITIAL_POSITION_Z)
                    .verts(playerCreator.getVerts())
                    .faces(playerCreator.getFaces());
                    
            Log.d(TAG, "Player assets loaded successfully");
        } catch (IOException e) {
            Log.e(TAG, ERROR_ASSET_LOADING + e.getMessage(), e);
            throw new RuntimeException(ERROR_ASSET_LOADING + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error loading player assets: " + e.getMessage(), e);
            throw new RuntimeException("Unexpected error loading player assets: " + e.getMessage(), e);
        }
    }

    /**
     * Private constructor to enforce factory pattern.
     * Initializes the player with default movement and rotation values.
     * 
     * @param object3D the 3D object representation of the player
     */
    private Player(Object3D object3D) {
        this.object3D = object3D;
        this.dir = new Vector3D(INITIAL_DIRECTION_X, INITIAL_DIRECTION_Y, INITIAL_DIRECTION_Z);
        this.move = new Vector3D(0, 0, 0);
    }
    
    /**
     * Creates an Object3D instance for the player.
     * 
     * @return the Object3D representing the player's 3D model
     * @throws IllegalStateException if assets haven't been loaded
     */
    public static Object3D makeObject3D() {
        if (playerBuilder == null) {
            throw new IllegalStateException(ERROR_ASSETS_NOT_LOADED);
        }
        return playerBuilder.buildObject();
    }
    
    /**
     * Factory method to create a new Player instance.
     * 
     * @return a new Player instance
     * @throws IllegalStateException if assets haven't been loaded
     */
    public static Player createPlayer() {
        return new Player(makeObject3D());
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
     * Checks if the player collides with a given tile.
     * Uses ray-triangle intersection to determine collision with the tile's surface.
     * 
     * @param tile the tile to check collision against
     * @return true if the player is colliding with the tile
     */
    public boolean collidesTile(Tile tile) {
        if (tile == null) {
            return false;
        }
        
        if (tile.isEmptySegment()) {
            return false;
        }
        
        // Check collision against both triangles of the tile
        Vector3D n1 = getNormal(tile.triangles[0]);
        Vector3D n2 = getNormal(tile.triangles[1]);
        
        // Test collision with first triangle
        float d1 = rayTriangleDistance(
                V3(object3D.objX, object3D.objY, object3D.objZ),
                n1.mult(-signum(n1.y)),
                tile.triangles[0][0], tile.triangles[0][1], tile.triangles[0][2]
        );
        
        // If first triangle collision fails, test second triangle
        if (!(!Float.isInfinite(d1) && d1 / PLAYER_HEIGHT < COLLISION_THRESHOLD_MULTIPLIER)) {
            float d2 = rayTriangleDistance(
                    V3(object3D.objX, object3D.objY, object3D.objZ),
                    n2.mult(-signum(n2.y)),
                    tile.triangles[1][0], tile.triangles[1][1], tile.triangles[1][2]
            );
            return !Float.isInfinite(d2) && d2 / PLAYER_HEIGHT < COLLISION_THRESHOLD_MULTIPLIER;
        }
        return true;
    }

    /**
     * Sets the tile that the player is currently standing on.
     * Updates the player's footing and nearest tile ID for terrain management.
     * 
     * @param what the tile the player is standing on, or null if not on any tile
     */
    public void setFooting(Tile what) {
        this.tileBelow = what;
        if (what != null) {
            this.nearestTileId = what.getID();
        }
    }

    // Physics state
    private float fallSpeed = 0f;

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

        if (tileBelow != null) {
            // Player is on ground - handle movement along surface
            handleGroundMovement(dtMillis);
        } else {
            // Player is falling - apply gravity
            handleFallingMovement(dtMillis);
        }
        
        // Apply movement to position
        object3D.objX += move.x;
        object3D.objY += move.y;
        object3D.objZ += move.z;
        
        // Update visual rotation based on movement
        object3D.objPitch -= dtMillis * PLAYER_SPEED / (PI * PLAYER_HEIGHT) * 2 * PI;
    }

    /**
     * Handles movement when the player is on solid ground.
     * Calculates sliding movement along the tile surface.
     * 
     * @param dtMillis time delta in milliseconds
     */
    private void handleGroundMovement(float dtMillis) {
        // Find which triangle we're standing on
        Vector3D origin = V3(object3D.objX, object3D.objY, object3D.objZ);
        float bestDist = Float.POSITIVE_INFINITY;
        Vector3D[] hitTri = null;

        // Test against both triangles to find the closest one
        for (Vector3D[] tri : tileBelow.triangles) {
            Vector3D nUnit = getNormal(tri);
            float d = rayTriangleDistance(
                    origin,
                    nUnit.mult(-signum(nUnit.y)),
                    tri[0], tri[1], tri[2]
            );
            if (!Float.isInfinite(d) && d < bestDist) {
                bestDist = d;
                hitTri = tri;
            }
        }

        if (hitTri != null) {
            // We're on a triangle - no falling
            fallSpeed = 0f;

            // Calculate surface basis vectors for sliding movement
            Vector3D u = hitTri[1].sub(hitTri[0]);
            Vector3D w = hitTri[2].sub(hitTri[0]);
            Vector3D n = u.crossProduct(w);  // surface normal

            // Solve dir = β·u + γ·w + α·n -> projection = β·u + γ·w
            // This projects the player's desired direction onto the surface plane
            float det = calculateDeterminant(n, u, w);
            
            if (Math.abs(det) > 1e-6f) { // Avoid division by very small numbers
                float beta = calculateBeta(n, u, w, dir, det);
                float gamma = calculateGamma(n, u, w, dir, det);

                // Build the slide vector along the triangle plane
                move = u.mult(beta)
                        .add(w.mult(gamma))
                        .withLen(PLAYER_SPEED * dtMillis);
            } else {
                // Fallback: move in current direction
                move = dir.withLen(PLAYER_SPEED * dtMillis);
            }
        }
    }

    /**
     * Handles movement when the player is falling.
     * Applies gravity and maintains horizontal movement.
     * 
     * @param dtMillis time delta in milliseconds
     */
    private void handleFallingMovement(float dtMillis) {
        // Maintain horizontal movement while falling
        Vector3D dwl = dir.withLen(PLAYER_SPEED * dtMillis);
        move = V3(dwl.x, move.y, dwl.z);
        
        // Apply gravity
        move = V3(move.x, move.y - fallSpeed * dtMillis, move.z);
        fallSpeed += FALL_ACCELERATION * dtMillis;
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

    /**
     * Calculates the beta coefficient for surface projection.
     * 
     * @param n normal vector
     * @param u first tangent vector
     * @param w second tangent vector
     * @param dir direction vector
     * @param det determinant
     * @return the beta coefficient
     */
    private float calculateBeta(Vector3D n, Vector3D u, Vector3D w, Vector3D dir, float det) {
        return (n.x * dir.y * w.z - n.x * dir.z * w.y
                - n.y * dir.x * w.z + n.y * dir.z * w.x
                + n.z * dir.x * w.y - n.z * dir.y * w.x) / det;
    }

    /**
     * Calculates the gamma coefficient for surface projection.
     * 
     * @param n normal vector
     * @param u first tangent vector
     * @param w second tangent vector
     * @param dir direction vector
     * @param det determinant
     * @return the gamma coefficient
     */
    private float calculateGamma(Vector3D n, Vector3D u, Vector3D w, Vector3D dir, float det) {
        return (n.x * u.y * dir.z - n.x * u.z * dir.y
                - n.y * u.x * dir.z + n.y * u.z * dir.x
                + n.z * u.x * dir.y - n.z * u.y * dir.x) / det;
    }

    @Override
    public void updateAfterDraw(float dt) {
        // Reset tile below after physics update
        tileBelow = null;
    }

    @Override
    public void cleanupGPUResources() {
        if (object3D != null) {
            object3D.cleanup();
        }
    }

    @Override
    public void resetGPUResources() {
        if (object3D != null) {
            object3D.reload();
        }
    }

    /**
     * Returns the maximum value by absolute magnitude.
     * 
     * @param a first value
     * @param b second value
     * @return the value with larger absolute magnitude
     */
    private float maxByAbs(float a, float b) {
        return abs(a) > abs(b) ? a : b;
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

    @Override
    public void draw(float[] mvpMatrix) {
        if (object3D != null) {
            object3D.draw(mvpMatrix);
        }
    }

    public float getX() { return object3D != null ? object3D.objX : 0f; }
    public float getY() { return object3D != null ? object3D.objY : 0f; }
    public float getZ() { return object3D != null ? object3D.objZ : 0f; }

    // Static fields
    private static Object3D.Builder playerBuilder;
    
    // Instance fields
    private final Object3D object3D;
    private Vector3D dir;
    private Vector3D move;

    // Rotation state
    private float stickyRotationTime = 0.0f;
    private float stickyRotationAng = 0.0f;

    // Physics state
    private Tile tileBelow;
    private long nearestTileId = -1L;
}