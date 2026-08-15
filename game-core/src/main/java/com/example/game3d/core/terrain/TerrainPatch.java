package com.example.game3d.core.terrain;

import com.example.game3d.core.terrain.addon.Addon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TerrainPatch {
    public final long id;
    public final List<TerrainTriangle> triangles;
    public final List<Addon> addons;

    public TerrainPatch(long id, List<TerrainTriangle> triangles, List<Addon> addons) {
        this.id = id;
        this.triangles = Collections.unmodifiableList(new ArrayList<TerrainTriangle>(triangles));
        this.addons = Collections.unmodifiableList(new ArrayList<Addon>(addons));
    }
}
