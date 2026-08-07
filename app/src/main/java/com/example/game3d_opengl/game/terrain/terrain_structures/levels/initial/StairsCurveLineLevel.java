package com.example.game3d_opengl.game.terrain.terrain_structures.levels.initial;

import static com.example.game3d_opengl.game.util.GameMath.PI;

import com.example.game3d_opengl.game.terrain.terrain_structures.Terrain2DCurve;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainLine;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainLineWithSpikeRect;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainStairs;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.TerrainLevelSequence;

public final class StairsCurveLineLevel extends TerrainLevelSequence {
    public StairsCurveLineLevel() {
        this(true, true);
    }

    public StairsCurveLineLevel(boolean enableFirstPortalSection, boolean enableSecondPortalSection) {
        super(
                "stairs_curve_line_level",
                new TerrainLine(20),
                TerrainStairs.builder()
                        .tilesPerStair(30)
                        .stairCount(5)
                        .emptyBetween(2)
                        .horizontalAngleDelta(PI / 8f)
                        .jump(-0.9f)
                        .build(),
                new IntroEmptyStraight(12),
                Terrain2DCurve.builder()
                        .tilesToMake(12)
                        .horizontalAngleDelta(-PI / 8f)
                        .verticalAngleDelta(0f)
                        .verticalAngleFadeoutTiles(5)
                        .build(),
                new TerrainLineWithSpikeRect(34, enableFirstPortalSection),
                Terrain2DCurve.builder()
                        .tilesToMake(42)
                        .horizontalAngleDelta(0f)
                        .verticalAngleDelta(PI / 14f)
                        .verticalAngleFadeoutTiles(5)
                        .build(),
                new TerrainLine(16),
                new TerrainLineWithSpikeRect(40, enableSecondPortalSection),
                new TerrainLine(34)
        );
    }
}
