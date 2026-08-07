package com.example.game3d_opengl.game.terrain.terrain_structures.levels.late;

import static com.example.game3d_opengl.game.util.GameMath.PI;

import com.example.game3d_opengl.game.terrain.terrain_structures.Terrain2DCurve;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainBoostRamp;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainLine;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainLineWithSpikeRect;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.TerrainLevelSequence;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.initial.IntroEmptyStraight;

public final class DoubleCurveBoostLevel extends TerrainLevelSequence {
    public DoubleCurveBoostLevel() {
        this(true, true);
    }

    public DoubleCurveBoostLevel(boolean enableFirstPortalSection, boolean enableSecondPortalSection) {
        super(
                "double_curve_boost_level",
                new TerrainLine(20),
                Terrain2DCurve.builder()
                        .tilesToMake(18)
                        .horizontalAngleDelta(PI / 16f)
                        .verticalAngleDelta(0f)
                        .verticalAngleFadeoutTiles(5)
                        .build(),
                new IntroEmptyStraight(30),
                TerrainBoostRamp.builder()
                        .rampTiles(7)
                        .gapTiles(0)
                        .landingTiles(18)
                        .launchAngleDelta(PI / 7f)
                        .build(),
                new IntroEmptyStraight(22),
                new TerrainLineWithSpikeRect(30, enableSecondPortalSection),
                new TerrainLine(30)
        );
    }
}
