package com.example.game3d.authoring.structures;

import com.example.game3d.authoring.AdvancedTerrainStructure;
import com.example.game3d.authoring.Terrain;

import java.util.function.Function;

/** Samples a function and emits the legacy absolute-pitch terrain command sequence. */
public class TerrainFunction extends AdvancedTerrainStructure {
    private final String sourcePrefix;
    private final Function<Float, Float> function;
    private final float xStart;
    private final float xEnd;

    public static Builder builder() {
        return new Builder();
    }

    public TerrainFunction(
            int tilesToMake, Function<Float, Float> function,
            float xStart, float xEnd) {
        this("handwritten:terrain-function", tilesToMake, function, xStart, xEnd);
    }

    public TerrainFunction(
            String sourcePrefix, int tilesToMake, Function<Float, Float> function,
            float xStart, float xEnd) {
        super(StructureSupport.requirePositive(tilesToMake, "tilesToMake"));
        this.sourcePrefix = StructureSupport.requirePrefix(sourcePrefix);
        if (function == null) {
            throw new IllegalArgumentException("function == null");
        }
        this.function = function;
        this.xStart = StructureSupport.requireFinite(xStart, "xStart");
        this.xEnd = StructureSupport.requireFinite(xEnd, "xEnd");
        if (tilesToMake > 1 && xStart == xEnd) {
            throw new IllegalArgumentException("x range has zero width");
        }
        this.name = sourcePrefix;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        float firstValue = valueAt(xStart);
        brush.setVerticalAng(firstValue);
        brush.addSegment(StructureSupport.tileId(sourcePrefix, 0));
        if (tilesToMake == 1) {
            return;
        }

        float dx = (xEnd - xStart) / (tilesToMake - 1);
        float previousValue = firstValue;
        for (int i = 1; i < tilesToMake; i++) {
            float x = xStart + i * dx;
            float currentValue = valueAt(x);
            float slope = (currentValue - previousValue) / dx;
            brush.setVerticalAng(Math.atan(slope));
            brush.addSegment(StructureSupport.tileId(sourcePrefix, i));
            previousValue = currentValue;
        }
    }

    private float valueAt(float x) {
        Float value = function.apply(x);
        if (value == null || !Float.isFinite(value)) {
            throw new IllegalArgumentException("function returned a non-finite value at " + x);
        }
        return value;
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int rows, int columns) {
    }

    public static final class Builder {
        private String sourcePrefix = "handwritten:terrain-function";
        private Integer tilesToMake;
        private Function<Float, Float> function;
        private Float xStart;
        private Float xEnd;

        public Builder sourcePrefix(String value) { sourcePrefix = value; return this; }
        public Builder tilesToMake(int value) { tilesToMake = value; return this; }
        public Builder function(Function<Float, Float> value) {
            function = value; return this;
        }
        public Builder xRange(float start, float end) {
            xStart = start; xEnd = end; return this;
        }

        public TerrainFunction build() {
            if (tilesToMake == null || function == null
                    || xStart == null || xEnd == null) {
                throw new IllegalStateException(
                        "tilesToMake, function and xRange must be set");
            }
            return new TerrainFunction(sourcePrefix, tilesToMake,
                    function, xStart, xEnd);
        }
    }
}
