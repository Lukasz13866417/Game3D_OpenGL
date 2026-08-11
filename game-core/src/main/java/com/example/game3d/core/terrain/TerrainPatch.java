package com.example.game3d.core.terrain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TerrainPatch {
    public final long id;
    public final List<TerrainTriangle> triangles;
    public final List<TerrainFeature> features;

    public TerrainPatch(long id, List<TerrainTriangle> triangles, List<TerrainFeature> features) {
        this.id = id;
        this.triangles = Collections.unmodifiableList(new ArrayList<TerrainTriangle>(triangles));
        this.features = Collections.unmodifiableList(new ArrayList<TerrainFeature>(features));
    }
}
