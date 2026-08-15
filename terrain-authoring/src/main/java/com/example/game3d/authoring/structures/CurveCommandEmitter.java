package com.example.game3d.authoring.structures;

import com.example.game3d.authoring.Terrain;

/** Shared command emitter used by basic and advanced curve variants. */
final class CurveCommandEmitter {
    private CurveCommandEmitter() {}

    static void emit(
            Terrain.TileBrush brush, String sourcePrefix, int curveTiles,
            double horizontalDelta, double verticalDelta,
            boolean resetHorizontal, boolean resetVertical,
            int horizontalFadeoutTiles, int verticalFadeoutTiles) {
        double horizontalStep = horizontalDelta / curveTiles;
        double verticalStep = verticalDelta / curveTiles;
        int ordinal = 0;
        for (int i = 0; i < curveTiles; i++) {
            brush.addHorizontalAng(horizontalStep);
            brush.addVerticalAng(verticalStep);
            brush.addSegment(StructureSupport.tileId(sourcePrefix, ordinal++));
        }

        int activeHorizontalFadeout = resetHorizontal ? horizontalFadeoutTiles : 0;
        int activeVerticalFadeout = resetVertical ? verticalFadeoutTiles : 0;
        if (resetHorizontal && activeHorizontalFadeout == 0) {
            brush.addHorizontalAng(-horizontalDelta);
        }
        if (resetVertical && activeVerticalFadeout == 0) {
            brush.addVerticalAng(-verticalDelta);
        }

        int fadeoutCount = Math.max(activeHorizontalFadeout, activeVerticalFadeout);
        for (int i = 0; i < fadeoutCount; i++) {
            if (i < activeHorizontalFadeout) {
                brush.addHorizontalAng(-horizontalDelta / activeHorizontalFadeout);
            }
            if (i < activeVerticalFadeout) {
                brush.addVerticalAng(-verticalDelta / activeVerticalFadeout);
            }
            brush.addSegment(StructureSupport.tileId(sourcePrefix, ordinal++));
        }
    }

    static int totalTiles(
            int curveTiles, boolean resetHorizontal, int horizontalFadeoutTiles,
            boolean resetVertical, int verticalFadeoutTiles) {
        StructureSupport.requirePositive(curveTiles, "tilesToMake");
        StructureSupport.requireNonNegative(horizontalFadeoutTiles,
                "horizontalFadeoutTiles");
        StructureSupport.requireNonNegative(verticalFadeoutTiles,
                "verticalFadeoutTiles");
        return Math.addExact(curveTiles, Math.max(
                resetHorizontal ? horizontalFadeoutTiles : 0,
                resetVertical ? verticalFadeoutTiles : 0));
    }
}
