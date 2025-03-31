package com.example.game3d_opengl.game.terrain_structures;

import com.example.game3d_opengl.game.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain_api.main.TerrainStructure;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain_api.main.Terrain.GridBrush;
import com.example.game3d_opengl.game.terrain_api.main.Terrain.TileBrush;

import java.util.function.Function;

/**
 * A terrain structure that follows the graph of a user-supplied function.
 * The function is sampled across the number of tiles, and its values are
 * used to set (or increment) the vertical angle of each segment.
 */
public class TerrainFunction extends TerrainStructure {

    private final Function<Float, Float> function;
    private final float xStart;
    private final float xEnd;

    /**
     * @param tilesToMake number of tiles (segments) to generate
     * @param function    the function f(x) whose values will define the vertical shape
     * @param xStart      starting x-value for sampling the function
     * @param xEnd        ending x-value for sampling the function
     */
    public TerrainFunction(int tilesToMake,
                           Function<Float, Float> function,
                           float xStart,
                           float xEnd) {
        super(tilesToMake);
        this.function = function;
        this.xStart = xStart;
        this.xEnd = xEnd;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        // We'll sample the function from xStart to xEnd, across tilesToMake points.
        float dx = (xEnd - xStart) / (tilesToMake - 1);

        // Evaluate the function at the first point and set the vertical angle:
        float prevValue = function.apply(xStart);
        brush.setVerticalAng(prevValue);
        brush.addSegment(); // First tile

        // For subsequent tiles, increment the vertical angle by the difference:
        for (int i = 1; i < tilesToMake; i++) {
            float x = xStart + i * dx;
            float currValue = function.apply(x);
            float slope = (currValue - prevValue) / dx;
            float deltaAngle = (float) Math.atan(slope);
            brush.setVerticalAng(deltaAngle);
            brush.addSegment();

            prevValue = currValue;
        }
    }

    @Override
    protected void generateAddons(GridBrush brush, int nRows, int nCols) {
       
    }
}
