
package com.example.game3d_opengl.game.terrain_api;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;

import com.example.game3d_opengl.rendering.object3d.Polygon3D;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public class Tile implements TerrainElement {
    private final long id;

    public long getID() {
        return id;
    }

    /**
     * The slope of this tile. Computed once from its geometry.
     */
    public final float slope;

    public boolean isEmptySegment() {
        return isEmptySegment;
    }

    private final boolean isEmptySegment;

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
     * The Polygon3D is created separately via factory method.
     */
    private Tile(Vector3D nl, Vector3D nr, Vector3D fl, Vector3D fr, float slope, long l, Polygon3D polygon3D, boolean isEmptySegment) {
        this.nearLeft = nl;
        this.nearRight = nr;
        this.farLeft = fl;
        this.farRight = fr;
        this.slope = slope;
        this.id = l;
        this.polygon3D = polygon3D;
        this.isEmptySegment = isEmptySegment;

        this.triangles = new Vector3D[][]{
            new Vector3D[]{this.nearLeft,this.nearRight,this.farRight},
            new Vector3D[]{this.nearLeft,this.farLeft,this.farRight}
        };
    }

    public static Polygon3D makePolygon3D(Vector3D nl, Vector3D nr, Vector3D fl, Vector3D fr) {
        // Build an array of perimeter coords: nearLeft -> nearRight -> farRight -> farLeft
        // The center will be computed automatically by Polygon3D
        float[] perimeterCoords = new float[]{
                nl.x, nl.y, nl.z,
                nr.x, nr.y, nr.z,
                fr.x, fr.y, fr.z,
                fl.x, fl.y, fl.z
        };

        // Use default colors that will be updated later via setTileColor
        FColor defaultColor = CLR(1,1,1,1);
        return Polygon3D.createWithVertexData(perimeterCoords,
                false,
                defaultColor,
                defaultColor);
    }
    
    public static Tile createTile(Vector3D nl, Vector3D nr, Vector3D fl, Vector3D fr, float slope, long l, boolean isFake) {
        Polygon3D polygon = makePolygon3D(nl, nr, fl, fr);
        return new Tile(nl, nr, fl, fr, slope, l, polygon, isFake);
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

        polygon3D.setFillAndOutline(cachedColor, new FColor(0,1,0));
    }

    /**
     * Delegate the draw call to polygon3D.
     *
     * @param mvpMatrix The combined Model-View-Projection matrix.
     */
    @Override
    public void draw(float[] mvpMatrix) {
        if(!isEmptySegment) {
            polygon3D.draw(mvpMatrix);
        }
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
    public void cleanupGPUResources() {
        polygon3D.cleanup();
    }

    @Override
    public void resetGPUResources() {
        polygon3D.reload();
    }

    @Override
    public boolean isGoneBy(long playerID) {
        return playerID - getID() > 50;
    }

}