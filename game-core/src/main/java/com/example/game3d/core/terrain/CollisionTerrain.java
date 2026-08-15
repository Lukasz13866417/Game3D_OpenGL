package com.example.game3d.core.terrain;

import com.example.game3d.core.math.Aabb;
import com.example.game3d.core.terrain.addon.Addon;

import java.util.List;

/** Deterministic collision query surface consumed by the fixed-step simulation. */
public interface CollisionTerrain {
    boolean containsTriangle(long triangleId);

    long segmentIdForTriangle(long triangleId);

    int collisionBoundaryMask(long triangleId);

    boolean isWalkableTransition(
            long fromTriangleId, long toTriangleId, double supportSlopeCosine);

    void queryTriangles(Aabb bounds, List<TerrainTriangle> destination);

    void queryAddons(Aabb bounds, List<Addon> destination);
}
