
package com.example.game3d_opengl.game.terrain_api;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;

import com.example.game3d_opengl.rendering.object3d.Polygon3D;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class Tile implements TerrainElement {
    private long id = -1L;

    public long getID() {
        return id;
    }

    public void setID(long id) {
        this.id = id;
    }

    /**
     * The slope of this tile. Computed once from its geometry.
     */
    public final float slope;

    /**
     * All four corners of this tile:
     * nearLeft, nearRight = "close edge"
     * farLeft,  farRight  = "far edge"
     * These vertices are already de-facto in world space.
     * The terrain doesn't move, the player does. And the camera follows him around.
     */
    public final Vector3D nearLeft, nearRight, farLeft, farRight;
    public final Vector3D[][] triangles;

    /**
     * Polygon wrapper that handles the vertex buffer, shaders, and rendering for this tile.
     */
    private Polygon3D polygon3D;
    // Reusable color instance to avoid per-frame allocations
    private final FColor cachedColor = CLR(1,1,1,1);

    /**
     * Constructs a Tile using 4 corners plus slope.
     * Then builds a Polygon3D to render that quad with a fill + outline color.
     */
    public Tile(Vector3D nl, Vector3D nr, Vector3D fl, Vector3D fr, float slope, long l) {
        this.nearLeft = nl;
        this.nearRight = nr;
        this.farLeft = fl;
        this.farRight = fr;
        this.slope = slope;

        this.triangles = new Vector3D[][]{
            new Vector3D[]{this.nearLeft,this.nearRight,this.farRight},
            new Vector3D[]{this.nearLeft,this.farLeft,this.farRight}
        };

        // Build an array of perimeter coords: nearLeft -> nearRight -> farRight -> farLeft
        // so that Polygon3D will form a closed shape (triangle fan).
        float[] perimeterCoords = new float[]{
                nl.x, nl.y, nl.z,
                nr.x, nr.y, nr.z,
                fr.x, fr.y, fr.z,
                fl.x, fl.y, fl.z
        };

        polygon3D = new Polygon3D(perimeterCoords, cachedColor, cachedColor);
    }

    /**
     * Computes a slope-based brightness color from the given colorTheme,
     * or a special "guardian" color if isGuardian == true,
     * then applies it to the Polygon3D.
     */
    public void setTileColor(FColor colorTheme) {
        // reuse cachedColor to avoid new allocations
        double brightnessMul = 1.0 + 3.0f * slope * slope;

        float baseR = colorTheme.r();
        float baseG = colorTheme.g();
        float baseB = colorTheme.b();

        float finalR = (float) Math.min(1.0, baseR * brightnessMul);
        float finalG = (float) Math.min(1.0, baseG * brightnessMul);
        float finalB = (float) Math.min(1.0, baseB * brightnessMul);

        cachedColor.rgba[0] = finalR;
        cachedColor.rgba[1] = finalG;
        cachedColor.rgba[2] = finalB;
        cachedColor.rgba[3] = 1.0f;

        polygon3D.setFillAndOutline(cachedColor, cachedColor);
    }

    /**
     * Delegate the draw call to polygon3D.
     *
     * @param mvpMatrix The combined Model-View-Projection matrix.
     */
    @Override
    public void draw(float[] mvpMatrix) {
        polygon3D.draw(mvpMatrix);
    }

    @Override
    public String toString() {
        return "TILE["
                + "NEAR L=" + nearLeft + ", R=" + nearRight+"\n"
                + "FAR  L=" + farLeft  + ", R=" + farRight+"\n"
                //+ ", slope=" + String.format(Locale.ROOT,"{%5f}",slope)
                + "]";
    }

    @Override
    public void updateBeforeDraw(float dt) {

    }

    @Override
    public void updateAfterDraw(float dt) {

    }

    @Override
    public void cleanupOnDeath() {
        polygon3D.cleanup();
    }

    @Override
    public boolean isGoneBy(long playerID) {
        return playerID - getID() > 50;
    }

}