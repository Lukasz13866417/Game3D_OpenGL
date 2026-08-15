package com.example.game3d.authoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable read-only capture consumed by editor import/export tooling. */
public final class CapturedStructureCommands {
    public final List<CapturedTileCommand> tiles;
    public final List<CapturedAddonPlacement> addonPlacements;

    CapturedStructureCommands(
            List<CapturedTileCommand> tiles,
            List<CapturedAddonPlacement> addonPlacements) {
        this.tiles = Collections.unmodifiableList(
                new ArrayList<CapturedTileCommand>(tiles));
        this.addonPlacements = Collections.unmodifiableList(
                new ArrayList<CapturedAddonPlacement>(addonPlacements));
    }
}
