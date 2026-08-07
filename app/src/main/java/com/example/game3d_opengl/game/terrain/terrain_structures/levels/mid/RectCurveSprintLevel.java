package com.example.game3d_opengl.game.terrain.terrain_structures.levels.mid;

import static com.example.game3d_opengl.game.util.GameMath.PI;

import com.example.game3d_opengl.game.terrain.terrain_structures.Terrain2DCurve;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainLine;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainLineWithSpikeRect;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainStairs;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.TerrainLevelSequence;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.initial.IntroEmptyStraight;

public final class RectCurveSprintLevel extends TerrainLevelSequence {
    public RectCurveSprintLevel() {
        this(true, true);
    }

    public RectCurveSprintLevel(boolean enableFirstPortalSection, boolean enableSecondPortalSection) {
        super(
                "rect_curve_sprint_level",
                new TerrainLine(30),
                new TerrainLineWithSpikeRect(42, enableFirstPortalSection),
                Terrain2DCurve.builder()
                        .tilesToMake(36)
                        .horizontalAngleDelta(0f)
                        .verticalAngleDelta(PI / 16f)
                        .verticalAngleFadeoutTiles(5)
                        .build(),
                new TerrainLine(18),
                new TerrainLineWithSpikeRect(32, enableSecondPortalSection),
                TerrainStairs.builder()
                        .tilesPerStair(18)
                        .stairCount(4)
                        .emptyBetween(2)
                        .horizontalAngleDelta(PI / 7f)
                        .jump(-1.0f)
                        .build(),
                new IntroEmptyStraight(12),
                Terrain2DCurve.builder()
                        .tilesToMake(20)
                        .horizontalAngleDelta(-PI / 7f)
                        .verticalAngleDelta(0f)
                        .verticalAngleFadeoutTiles(5)
                        .build(),
                new TerrainLine(34)
        );
    }
}
