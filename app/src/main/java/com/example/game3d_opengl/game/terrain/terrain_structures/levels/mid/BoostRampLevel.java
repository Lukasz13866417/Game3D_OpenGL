package com.example.game3d_opengl.game.terrain.terrain_structures.levels.mid;

import static com.example.game3d_opengl.game.util.GameMath.PI;

import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainBoostRamp;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainLine;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainLineWithSpikeRect;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.TerrainLevelSequence;

public final class BoostRampLevel extends TerrainLevelSequence {
    public BoostRampLevel() {
        this(true, true);
    }

    public BoostRampLevel(boolean enableFirstPortalSection, boolean enableSecondPortalSection) {
        super(
                "boost_ramp_level",
                new TerrainLine(24),
                new TerrainLineWithSpikeRect(28, enableFirstPortalSection),
                TerrainBoostRamp.builder()
                        .rampTiles(8)
                        .gapTiles(0)
                        .landingTiles(40)
                        .launchAngleDelta(PI / 15f)
                        .build(),
                new TerrainLine(40)
        );
    }
}
