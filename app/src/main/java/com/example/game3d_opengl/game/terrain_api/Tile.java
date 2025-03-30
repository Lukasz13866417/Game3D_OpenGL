
package com.example.game3d_opengl.game.terrain_api;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;

import com.example.game3d_opengl.rendering.object3d.Polygon3D;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.util.Locale;

public class Tile {

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

    /**
     * Polygon wrapper that handles the vertex buffer, shaders, and rendering for this tile.
     */
    private Polygon3D polygon3D;

    /**
     * Constructs a Tile using 4 corners plus slope.
     * Then builds a Polygon3D to render that quad with a fill + outline color.
     */
    public Tile(Vector3D nl, Vector3D nr, Vector3D fl, Vector3D fr, float slope) {
        this.nearLeft = nl;
        this.nearRight = nr;
        this.farLeft = fl;
        this.farRight = fr;
        this.slope = slope;

        // Build an array of perimeter coords: nearLeft -> nearRight -> farRight -> farLeft
        // so that Polygon3D will form a closed shape (triangle fan).
        float[] perimeterCoords = new float[]{
                nl.x, nl.y, nl.z,
                nr.x, nr.y, nr.z,
                fr.x, fr.y, fr.z,
                fl.x, fl.y, fl.z
        };

        polygon3D = new Polygon3D(perimeterCoords, CLR(1, 1, 1), CLR(1, 1, 1));
    }

    /**
     * Computes a slope-based brightness color from the given colorTheme,
     * or a special "guardian" color if isGuardian == true,
     * then applies it to the Polygon3D.
     */
    public void setTileColor(FColor colorTheme) {

        double brightnessMul = 1.0 + 3.0f*slope*slope;

        float baseR = colorTheme.r();
        float baseG = colorTheme.g();
        float baseB = colorTheme.b();

        float finalR = (float) Math.min(1.0, baseR * brightnessMul);
        float finalG = (float) Math.min(1.0, baseG * brightnessMul);
        float finalB = (float) Math.min(1.0, baseB * brightnessMul);


        polygon3D.setFillAndOutline(
                CLR(finalR, finalG, finalB, 1.0f),
                CLR(finalR, finalG, finalB, 1.0f)
        );
    }

    /**
     * Delegate the draw call to polygon3D.
     *
     * @param mvpMatrix The combined Model-View-Projection matrix.
     */
    public void draw(float[] mvpMatrix) {
        polygon3D.draw(mvpMatrix);
    }

    @Override
    public String toString() {
        return "TILE [\n"
                + "NEAR L=" + nearLeft + ", R=" + nearRight+"\n"
                + "FAR  L=" + farLeft + ", R=" + farRight+"\n"
                + ", slope=" + String.format(Locale.ROOT,"{%5f}",slope)
                + "\n]";
    }

}