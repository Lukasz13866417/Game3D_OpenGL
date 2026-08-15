package com.example.game3d.core.terrain;

import com.example.game3d.core.math.Aabb;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.addon.Addon;
import com.example.game3d.core.terrain.addon.DeathSpike;
import com.example.game3d.core.terrain.addon.Potion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, deterministic collision representation shared by simulation and rendering adapters. */
public final class TerrainWorld implements CollisionTerrain {
    private static final double GRID_CELL_SIZE = 4.0;
    private final List<TerrainPatch> patches;
    private final List<TerrainTriangle> triangles;
    private final List<Addon> addons;
    private final Map<Long, List<TerrainTriangle>> triangleGrid;
    private final Map<Long, List<Addon>> addonGrid;
    private final Map<Long, TerrainTriangle> trianglesById;
    private final Map<Long, Long> triangleOwnerSegmentIds;
    private final Map<Long, Integer> collisionBoundaryMasks;
    private final Map<Long, Set<Long>> sharedEdgeNeighbors;

    public TerrainWorld(List<TerrainPatch> patches) {
        this.patches = Collections.unmodifiableList(new ArrayList<TerrainPatch>(patches));
        ArrayList<TerrainTriangle> allTriangles = new ArrayList<TerrainTriangle>();
        ArrayList<Addon> allAddons = new ArrayList<Addon>();
        Set<Long> triangleIds = new HashSet<Long>();
        Set<Long> addonIds = new HashSet<Long>();
        for (TerrainPatch patch : patches) {
            for (TerrainTriangle triangle : patch.triangles) {
                if (!triangleIds.add(triangle.id)) {
                    throw new IllegalArgumentException("Duplicate triangle id " + triangle.id);
                }
                allTriangles.add(triangle);
            }
            for (Addon addon : patch.addons) {
                if (!addonIds.add(addon.id())) {
                    throw new IllegalArgumentException("Duplicate addon id " + addon.id());
                }
                allAddons.add(addon);
            }
        }
        Comparator<TerrainTriangle> triangleOrder =
                new Comparator<TerrainTriangle>() {
                    @Override
                    public int compare(TerrainTriangle left, TerrainTriangle right) {
                        return Long.compare(left.id, right.id);
                    }
                };
        Comparator<Addon> addonOrder =
                new Comparator<Addon>() {
                    @Override
                    public int compare(Addon left, Addon right) {
                        return Long.compare(left.id(), right.id());
                    }
                };
        Collections.sort(allTriangles, triangleOrder);
        Collections.sort(allAddons, addonOrder);
        triangles = Collections.unmodifiableList(allTriangles);
        addons = Collections.unmodifiableList(allAddons);
        LinkedHashMap<Long, TerrainTriangle> byId =
                new LinkedHashMap<Long, TerrainTriangle>();
        LinkedHashMap<Long, Long> owners =
                new LinkedHashMap<Long, Long>();
        for (TerrainPatch patch : patches) {
            for (TerrainTriangle triangle : patch.triangles) {
                byId.put(triangle.id, triangle);
                owners.put(triangle.id,
                        triangle.ownerSegmentId >= 0L
                                ? triangle.ownerSegmentId : patch.id);
            }
        }
        trianglesById = Collections.unmodifiableMap(byId);
        triangleOwnerSegmentIds = Collections.unmodifiableMap(owners);
        triangleGrid = buildTriangleGrid(allTriangles);
        addonGrid = buildAddonGrid(allAddons);
        collisionBoundaryMasks = buildCollisionBoundaryMasks(allTriangles);
        sharedEdgeNeighbors = buildSharedEdgeNeighbors(allTriangles);
    }

    public List<TerrainPatch> patches() {
        return patches;
    }

    public List<TerrainTriangle> triangles() {
        return triangles;
    }

    public List<Addon> addons() {
        return addons;
    }

    public boolean containsTriangle(long triangleId) {
        return trianglesById.containsKey(triangleId);
    }

    @Override
    public long segmentIdForTriangle(long triangleId) {
        Long owner = triangleOwnerSegmentIds.get(triangleId);
        return owner == null ? -1L : owner.longValue();
    }

    /**
     * Returns one bit per triangle edge (AB, BC, CA). Coplanar edges shared by another triangle
     * are omitted so the narrow phase does not turn an internal mesh diagonal into a ridge.
     */
    public int collisionBoundaryMask(long triangleId) {
        Integer mask = collisionBoundaryMasks.get(triangleId);
        return mask == null ? 0b111 : mask.intValue();
    }

    /**
     * True when two supportable terrain faces are topologically connected by an authored shared
     * edge. A supported player may transfer between these faces without treating the tessellation
     * crease as an airborne restitution impact.
     */
    public boolean isWalkableTransition(
            long fromTriangleId, long toTriangleId, double supportSlopeCosine) {
        if (fromTriangleId < 0L || toTriangleId < 0L) {
            return false;
        }
        Set<Long> neighbors = sharedEdgeNeighbors.get(fromTriangleId);
        if (neighbors == null || !neighbors.contains(toTriangleId)) {
            return false;
        }
        TerrainTriangle from = trianglesById.get(fromTriangleId);
        TerrainTriangle to = trianglesById.get(toTriangleId);
        return from != null && to != null
                && frontNormal(from).y >= supportSlopeCosine
                && frontNormal(to).y >= supportSlopeCosine;
    }

    public long deterministicDigest() {
        long hash = 0xcbf29ce484222325L;
        for (TerrainTriangle triangle : triangles) {
            hash = mix(hash, triangle.id);
            hash = mix(hash, Double.doubleToLongBits(triangle.a.x));
            hash = mix(hash, Double.doubleToLongBits(triangle.a.y));
            hash = mix(hash, Double.doubleToLongBits(triangle.a.z));
            hash = mix(hash, Double.doubleToLongBits(triangle.b.x));
            hash = mix(hash, Double.doubleToLongBits(triangle.b.y));
            hash = mix(hash, Double.doubleToLongBits(triangle.b.z));
            hash = mix(hash, Double.doubleToLongBits(triangle.c.x));
            hash = mix(hash, Double.doubleToLongBits(triangle.c.y));
            hash = mix(hash, Double.doubleToLongBits(triangle.c.z));
            hash = mix(hash, triangle.surface.kind.stableCode);
            hash = mix(hash,
                    Double.doubleToLongBits(triangle.surface.motorSpeedMultiplier));
        }
        for (Addon addon : addons) {
            hash = mix(hash, addon.id());
            hash = mix(hash, addon.kind.stableCode);
            Vec3 center;
            if (addon instanceof DeathSpike) {
                DeathSpike spike = (DeathSpike) addon;
                center = spike.collisionBaseCenter;
                hash = mixVec(hash, center);
                hash = mix(hash, Double.doubleToLongBits(spike.collisionRadius));
                hash = mix(hash, Double.doubleToLongBits(spike.collisionHeight));
            } else if (addon instanceof Potion) {
                Potion potion = (Potion) addon;
                center = potion.center;
                hash = mixVec(hash, center);
                hash = mix(hash, Double.doubleToLongBits(potion.triggerRadius));
            } else {
                hash = mix(hash, addon.deterministicDigest());
            }
        }
        return hash;
    }

    public List<TerrainTriangle> queryTriangles(Aabb bounds) {
        ArrayList<TerrainTriangle> result = new ArrayList<TerrainTriangle>();
        queryTriangles(bounds, result);
        return result;
    }

    /**
     * Replaces {@code destination} with the triangles intersecting {@code bounds}, ordered by
     * stable triangle ID. The caller retains ownership of the list and may reuse it as query
     * scratch to avoid per-query collection allocation. The destination must be mutable and
     * should provide efficient indexed access and insertion (normally an {@link ArrayList}); its
     * contents remain valid only until that list is reused.
     */
    public void queryTriangles(Aabb bounds, List<TerrainTriangle> destination) {
        destination.clear();
        int minCellX = cell(bounds.min.x);
        int maxCellX = cell(bounds.max.x);
        int minCellZ = cell(bounds.min.z);
        int maxCellZ = cell(bounds.max.z);
        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                List<TerrainTriangle> bucket = triangleGrid.get(key(cellX, cellZ));
                if (bucket == null) {
                    continue;
                }
                for (int i = 0; i < bucket.size(); i++) {
                    TerrainTriangle triangle = bucket.get(i);
                    if (triangle.bounds.intersects(bounds)) {
                        int index = findTriangle(destination, triangle.id);
                        if (index < 0) {
                            destination.add(-index - 1, triangle);
                        }
                    }
                }
            }
        }
    }

    public List<Addon> queryAddons(Aabb bounds) {
        ArrayList<Addon> result = new ArrayList<Addon>();
        queryAddons(bounds, result);
        return result;
    }

    /**
     * Replaces {@code destination} with the addons intersecting {@code bounds}, ordered by
     * stable addon ID. The caller retains ownership of the list and may reuse it as query
     * scratch to avoid per-query collection allocation. The destination must be mutable and
     * should provide efficient indexed access and insertion (normally an {@link ArrayList}); its
     * contents remain valid only until that list is reused.
     */
    public void queryAddons(Aabb bounds, List<Addon> destination) {
        destination.clear();
        int minCellX = cell(bounds.min.x);
        int maxCellX = cell(bounds.max.x);
        int minCellZ = cell(bounds.min.z);
        int maxCellZ = cell(bounds.max.z);
        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                List<Addon> bucket = addonGrid.get(key(cellX, cellZ));
                if (bucket == null) {
                    continue;
                }
                for (int i = 0; i < bucket.size(); i++) {
                    Addon addon = bucket.get(i);
                    if (addon.broadPhaseBounds().intersects(bounds)) {
                        int index = findAddon(destination, addon.id());
                        if (index < 0) {
                            destination.add(-index - 1, addon);
                        }
                    }
                }
            }
        }
    }

    private static int findTriangle(List<TerrainTriangle> sorted, long id) {
        int low = 0;
        int high = sorted.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            long middleId = sorted.get(middle).id;
            if (middleId < id) {
                low = middle + 1;
            } else if (middleId > id) {
                high = middle - 1;
            } else {
                return middle;
            }
        }
        return -low - 1;
    }

    private static int findAddon(List<Addon> sorted, long id) {
        int low = 0;
        int high = sorted.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            long middleId = sorted.get(middle).id();
            if (middleId < id) {
                low = middle + 1;
            } else if (middleId > id) {
                high = middle - 1;
            } else {
                return middle;
            }
        }
        return -low - 1;
    }

    private static Map<Long, List<TerrainTriangle>> buildTriangleGrid(
            List<TerrainTriangle> source) {
        HashMap<Long, List<TerrainTriangle>> grid =
                new HashMap<Long, List<TerrainTriangle>>();
        for (TerrainTriangle triangle : source) {
            for (int x = cell(triangle.bounds.min.x);
                 x <= cell(triangle.bounds.max.x); x++) {
                for (int z = cell(triangle.bounds.min.z);
                     z <= cell(triangle.bounds.max.z); z++) {
                    long key = key(x, z);
                    List<TerrainTriangle> bucket = grid.get(key);
                    if (bucket == null) {
                        bucket = new ArrayList<TerrainTriangle>();
                        grid.put(key, bucket);
                    }
                    bucket.add(triangle);
                }
            }
        }
        for (Map.Entry<Long, List<TerrainTriangle>> entry : grid.entrySet()) {
            Collections.sort(entry.getValue(), new Comparator<TerrainTriangle>() {
                @Override
                public int compare(TerrainTriangle left, TerrainTriangle right) {
                    return Long.compare(left.id, right.id);
                }
            });
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(grid);
    }

    private static Map<Long, List<Addon>> buildAddonGrid(
            List<Addon> source) {
        HashMap<Long, List<Addon>> grid =
                new HashMap<Long, List<Addon>>();
        for (Addon addon : source) {
            if (addon.contactPhase() == Addon.ContactPhase.NONE) {
                continue;
            }
            Aabb bounds = addon.broadPhaseBounds();
            for (int x = cell(bounds.min.x); x <= cell(bounds.max.x); x++) {
                for (int z = cell(bounds.min.z); z <= cell(bounds.max.z); z++) {
                    long key = key(x, z);
                    List<Addon> bucket = grid.get(key);
                    if (bucket == null) {
                        bucket = new ArrayList<Addon>();
                        grid.put(key, bucket);
                    }
                    bucket.add(addon);
                }
            }
        }
        for (Map.Entry<Long, List<Addon>> entry : grid.entrySet()) {
            Collections.sort(entry.getValue(), new Comparator<Addon>() {
                @Override
                public int compare(Addon left, Addon right) {
                    return Long.compare(left.id(), right.id());
                }
            });
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(grid);
    }

    private static Map<Long, Integer> buildCollisionBoundaryMasks(
            List<TerrainTriangle> triangles) {
        HashMap<Long, Integer> masks = new HashMap<Long, Integer>();
        HashMap<EdgeKey, List<EdgeRef>> edgeOwners =
                new HashMap<EdgeKey, List<EdgeRef>>();
        for (TerrainTriangle triangle : triangles) {
            masks.put(triangle.id, 0b111);
            addEdgeOwner(edgeOwners, triangle, 0, triangle.a, triangle.b);
            addEdgeOwner(edgeOwners, triangle, 1, triangle.b, triangle.c);
            addEdgeOwner(edgeOwners, triangle, 2, triangle.c, triangle.a);
        }
        for (List<EdgeRef> owners : edgeOwners.values()) {
            for (int left = 0; left < owners.size(); left++) {
                for (int right = left + 1; right < owners.size(); right++) {
                    EdgeRef a = owners.get(left);
                    EdgeRef b = owners.get(right);
                    if (Math.abs(a.triangle.normal.dot(b.triangle.normal))
                            < 1.0 - 1.0e-10) {
                        continue;
                    }
                    masks.put(a.triangle.id,
                            masks.get(a.triangle.id) & ~(1 << a.edgeIndex));
                    masks.put(b.triangle.id,
                            masks.get(b.triangle.id) & ~(1 << b.edgeIndex));
                }
            }
        }
        return Collections.unmodifiableMap(masks);
    }

    private static Map<Long, Set<Long>> buildSharedEdgeNeighbors(
            List<TerrainTriangle> triangles) {
        HashMap<EdgeKey, List<EdgeRef>> edgeOwners =
                new HashMap<EdgeKey, List<EdgeRef>>();
        for (TerrainTriangle triangle : triangles) {
            addEdgeOwner(edgeOwners, triangle, 0, triangle.a, triangle.b);
            addEdgeOwner(edgeOwners, triangle, 1, triangle.b, triangle.c);
            addEdgeOwner(edgeOwners, triangle, 2, triangle.c, triangle.a);
        }
        HashMap<Long, Set<Long>> neighbors = new HashMap<Long, Set<Long>>();
        for (TerrainTriangle triangle : triangles) {
            neighbors.put(triangle.id, new HashSet<Long>());
        }
        for (List<EdgeRef> owners : edgeOwners.values()) {
            for (int left = 0; left < owners.size(); left++) {
                for (int right = left + 1; right < owners.size(); right++) {
                    long leftId = owners.get(left).triangle.id;
                    long rightId = owners.get(right).triangle.id;
                    neighbors.get(leftId).add(rightId);
                    neighbors.get(rightId).add(leftId);
                }
            }
        }
        HashMap<Long, Set<Long>> immutable = new HashMap<Long, Set<Long>>();
        for (Map.Entry<Long, Set<Long>> entry : neighbors.entrySet()) {
            immutable.put(entry.getKey(),
                    Collections.unmodifiableSet(new HashSet<Long>(entry.getValue())));
        }
        return Collections.unmodifiableMap(immutable);
    }

    private static Vec3 frontNormal(TerrainTriangle triangle) {
        return triangle.normal.dot(Vec3.UP) < -0.001
                ? triangle.normal.multiply(-1.0) : triangle.normal;
    }

    private static void addEdgeOwner(
            Map<EdgeKey, List<EdgeRef>> owners, TerrainTriangle triangle,
            int edgeIndex, Vec3 start, Vec3 end) {
        EdgeKey key = new EdgeKey(start, end);
        List<EdgeRef> edgeRefs = owners.get(key);
        if (edgeRefs == null) {
            edgeRefs = new ArrayList<EdgeRef>();
            owners.put(key, edgeRefs);
        }
        edgeRefs.add(new EdgeRef(triangle, edgeIndex));
    }

    private static int cell(double coordinate) {
        return (int) Math.floor(coordinate / GRID_CELL_SIZE);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static long mix(long hash, long value) {
        hash ^= value;
        return hash * 0x100000001b3L;
    }

    private static long mixVec(long hash, Vec3 value) {
        hash = mix(hash, Double.doubleToLongBits(value.x));
        hash = mix(hash, Double.doubleToLongBits(value.y));
        return mix(hash, Double.doubleToLongBits(value.z));
    }

    private static final class EdgeRef {
        final TerrainTriangle triangle;
        final int edgeIndex;

        EdgeRef(TerrainTriangle triangle, int edgeIndex) {
            this.triangle = triangle;
            this.edgeIndex = edgeIndex;
        }
    }

    private static final class EdgeKey {
        final PointKey first;
        final PointKey second;

        EdgeKey(Vec3 start, Vec3 end) {
            PointKey startKey = new PointKey(start);
            PointKey endKey = new PointKey(end);
            if (startKey.compareTo(endKey) <= 0) {
                first = startKey;
                second = endKey;
            } else {
                first = endKey;
                second = startKey;
            }
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof EdgeKey)) {
                return false;
            }
            EdgeKey key = (EdgeKey) other;
            return first.equals(key.first) && second.equals(key.second);
        }

        @Override
        public int hashCode() {
            return first.hashCode() * 31 + second.hashCode();
        }
    }

    private static final class PointKey implements Comparable<PointKey> {
        final long x;
        final long y;
        final long z;

        PointKey(Vec3 point) {
            x = bits(point.x);
            y = bits(point.y);
            z = bits(point.z);
        }

        @Override
        public int compareTo(PointKey other) {
            int xOrder = Long.compare(x, other.x);
            if (xOrder != 0) {
                return xOrder;
            }
            int yOrder = Long.compare(y, other.y);
            return yOrder != 0 ? yOrder : Long.compare(z, other.z);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof PointKey)) {
                return false;
            }
            PointKey point = (PointKey) other;
            return x == point.x && y == point.y && z == point.z;
        }

        @Override
        public int hashCode() {
            long hash = x;
            hash = hash * 31L + y;
            hash = hash * 31L + z;
            return (int) (hash ^ (hash >>> 32));
        }

        private static long bits(double value) {
            return Double.doubleToLongBits(value == 0.0 ? 0.0 : value);
        }
    }
}
