package com.example.game3d_opengl.game.terrain.terrain_structures.levels.late;

import static com.example.game3d_opengl.game.util.GameMath.PI;

import com.example.game3d_opengl.game.terrain.terrain_structures.Terrain2DCurve;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainLine;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainLineWithSpikeRect;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainStairs;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.TerrainLevelSequence;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.initial.IntroEmptyStraight;

public final class LongStairArcLevel extends TerrainLevelSequence {
    public LongStairArcLevel() {
        this(true, true);
    }

    public LongStairArcLevel(boolean enableFirstPortalSection, boolean enableSecondPortalSection) {
        super(
                "long_stair_arc_level",
                new TerrainLine(50),
                TerrainStairs.builder()
                        .tilesPerStair(35)
                        .stairCount(5)
                        .emptyBetween(2)
                        .horizontalAngleDelta(PI / 10f)
                        .jump(-0.85f)
                        .build(),
                new IntroEmptyStraight(20),
                Terrain2DCurve.builder()
                        .tilesToMake(12)
                        .horizontalAngleDelta(-PI / 10f)
                        .verticalAngleDelta(0f)
                        .resetVerticalAngle(true)
                        .verticalAngleFadeoutTiles(5)
                        .build(),
                new TerrainLineWithSpikeRect(50, enableFirstPortalSection),
                Terrain2DCurve.builder()
                        .tilesToMake(30)
                        .horizontalAngleDelta(PI / 20f)
                        .verticalAngleDelta(PI / 22f)
                        .resetVerticalAngle(true)
                        .verticalAngleFadeoutTiles(5)
                        .build(),
                new TerrainLine(18)
        );
    }
}
