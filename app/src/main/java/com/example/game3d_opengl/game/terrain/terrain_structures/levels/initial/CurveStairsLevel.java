package com.example.game3d_opengl.game.terrain.terrain_structures.levels.initial;

import static com.example.game3d_opengl.game.util.GameMath.PI;

import com.example.game3d_opengl.game.terrain.terrain_structures.Terrain2DCurve;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainLine;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainStairs;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.TerrainLevelSequence;

public final class CurveStairsLevel extends TerrainLevelSequence {
    public CurveStairsLevel() {
        this(true, true);
    }

    public CurveStairsLevel(boolean enableFirstPortalSection, boolean enableSecondPortalSection) {
        super(
                "curve_stairs_level",
                new TerrainLine(50),
                TerrainStairs.builder()
                        .tilesPerStair(42)
                        .stairCount(7)
                        .emptyBetween(2)
                        .horizontalAngleDelta(PI / 9f)
                        .jump(-0.8f)
                        .build(),
                new IntroEmptyStraight(12),
                Terrain2DCurve.builder()
                        .tilesToMake(14)
                        .horizontalAngleDelta(-PI / 9f)
                        .verticalAngleDelta(0f)
                        .verticalAngleFadeoutTiles(5)
                        .build(),
                new TerrainLine(50)
        );
    }
}
