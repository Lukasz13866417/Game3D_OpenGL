package com.example.game3d.core.terrain;

import com.example.game3d.core.math.Aabb;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.addon.Addon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Incremental deterministic collision projection of canonical terrain.
 *
 * <p>The class is single-threaded. Callers apply complete commits only between fixed simulation
 * ticks.</p>
 */
public final class TerrainCollisionIndex implements CollisionTerrain {
    private static final double GRID_CELL_SIZE = 4.0;
    private static final long MAX_CELLS_PER_ITEM = 1_000_000L;

    private final TerrainState state;
    private final HashMap<Long, TerrainTriangle> trianglesById =
            new HashMap<Long, TerrainTriangle>();
    private final HashMap<Long, Addon> addonsById =
            new HashMap<Long, Addon>();
    private final HashMap<Long, ArrayList<TerrainTriangle>> triangleGrid =
            new HashMap<Long, ArrayList<TerrainTriangle>>();
    private final HashMap<Long, ArrayList<Addon>> addonGrid =
            new HashMap<Long, ArrayList<Addon>>();
    private final HashMap<Long, long[]> triangleCells =
            new HashMap<Long, long[]>();
    private final HashMap<Long, long[]> addonCells =
            new HashMap<Long, long[]>();
    private final HashMap<Long, EdgeKey[]> triangleEdges =
            new HashMap<Long, EdgeKey[]>();
    private final HashMap<EdgeKey, ArrayList<EdgeRef>> edgeOwners =
            new HashMap<EdgeKey, ArrayList<EdgeRef>>();
    private final HashMap<Long, Integer> collisionBoundaryMasks =
            new HashMap<Long, Integer>();
    private final HashMap<Long, HashSet<Long>> sharedEdgeNeighbors =
            new HashMap<Long, HashSet<Long>>();

    public TerrainCollisionIndex() {
        this(TerrainSnapshot.empty());
    }

    public TerrainCollisionIndex(TerrainSnapshot snapshot) {
        state = new TerrainState(snapshot);
        for (TerrainSegment segment : snapshot.segments) {
            addPrepared(prepare(segment));
        }
        recomputeAllTopology();
    }

    public void apply(TerrainCommit commit) {
        state.validate(commit);

        ArrayList<TerrainSegment> changed =
                new ArrayList<TerrainSegment>(commit.segmentUpserts.size());
        for (TerrainSegment existing :
                state.segmentsBefore(commit.retireBeforeSegmentId)) {
            changed.add(existing);
        }
        for (TerrainSegment upsert : commit.segmentUpserts) {
            TerrainSegment old = state.segment(upsert.id);
            if (old != null) {
                changed.add(old);
            }
        }

        ArrayList<PreparedSegment> prepared =
                new ArrayList<PreparedSegment>(commit.segmentUpserts.size());
        for (TerrainSegment segment : commit.segmentUpserts) {
            prepared.add(prepare(segment));
        }

        HashSet<EdgeKey> affectedEdges = new HashSet<EdgeKey>();
        HashSet<Long> affectedTriangles = new HashSet<Long>();
        for (TerrainSegment old : changed) {
            collectTopologyImpact(old, affectedEdges, affectedTriangles);
        }

        for (TerrainSegment old : changed) {
            removeSegment(old);
        }
        for (PreparedSegment addition : prepared) {
            addPrepared(addition);
            for (TerrainTriangle triangle : addition.triangles) {
                EdgeKey[] edges = triangleEdges.get(triangle.id);
                Collections.addAll(affectedEdges, edges);
                affectedTriangles.add(triangle.id);
            }
        }
        for (EdgeKey edge : affectedEdges) {
            List<EdgeRef> owners = edgeOwners.get(edge);
            if (owners != null) {
                for (EdgeRef owner : owners) {
                    affectedTriangles.add(owner.triangleId);
                }
            }
        }
        recomputeTopology(affectedTriangles);
        state.applyValidated(commit);
    }

    public TerrainSnapshot snapshot() {
        return state.snapshot();
    }

    public long revision() {
        return state.revision();
    }

    public long deterministicDigest() {
        return state.snapshot().deterministicDigest;
    }

    public long collisionFingerprint(long triangleId) {
        TerrainTriangle triangle = trianglesById.get(triangleId);
        return triangle == null ? Long.MIN_VALUE : triangle.collisionFingerprint();
    }

    public boolean containsAddon(long addonId) {
        return addonsById.containsKey(addonId);
    }

    @Override
    public boolean containsTriangle(long triangleId) {
        return trianglesById.containsKey(triangleId);
    }

    @Override
    public long segmentIdForTriangle(long triangleId) {
        TerrainTriangle triangle = trianglesById.get(triangleId);
        return triangle == null ? -1L : triangle.ownerSegmentId;
    }

    @Override
    public int collisionBoundaryMask(long triangleId) {
        Integer mask = collisionBoundaryMasks.get(triangleId);
        return mask == null ? 0b111 : mask.intValue();
    }

    @Override
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

    @Override
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

    @Override
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

    private PreparedSegment prepare(TerrainSegment segment) {
        ArrayList<TerrainTriangle> triangles =
                new ArrayList<TerrainTriangle>(segment.solid ? 2 : 0);
        ArrayList<long[]> triangleCellLists =
                new ArrayList<long[]>(segment.solid ? 2 : 0);
        for (TerrainTriangle triangle : segment.collisionTriangles()) {
            triangles.add(triangle);
            triangleCellLists.add(cellsFor(triangle.bounds));
        }
        ArrayList<Addon> addons = new ArrayList<Addon>();
        ArrayList<long[]> addonCellLists = new ArrayList<long[]>();
        for (Addon addon : segment.addons) {
            if (addon.contactPhase() == Addon.ContactPhase.NONE) {
                continue;
            }
            addons.add(addon);
            addonCellLists.add(cellsFor(addon.broadPhaseBounds()));
        }
        return new PreparedSegment(
                segment, triangles, triangleCellLists, addons, addonCellLists);
    }

    private void addPrepared(PreparedSegment prepared) {
        for (int i = 0; i < prepared.triangles.size(); i++) {
            TerrainTriangle triangle = prepared.triangles.get(i);
            if (trianglesById.put(triangle.id, triangle) != null) {
                throw new IllegalStateException("Duplicate triangle id " + triangle.id);
            }
            long[] cells = prepared.triangleCells.get(i);
            triangleCells.put(triangle.id, cells);
            addTriangleToGrid(triangle, cells);
            EdgeKey[] edges = edges(triangle);
            triangleEdges.put(triangle.id, edges);
            for (int edgeIndex = 0; edgeIndex < edges.length; edgeIndex++) {
                ArrayList<EdgeRef> owners = edgeOwners.get(edges[edgeIndex]);
                if (owners == null) {
                    owners = new ArrayList<EdgeRef>();
                    edgeOwners.put(edges[edgeIndex], owners);
                }
                insertEdgeRef(owners, new EdgeRef(triangle.id, edgeIndex));
            }
            collisionBoundaryMasks.put(triangle.id, 0b111);
            sharedEdgeNeighbors.put(triangle.id, new HashSet<Long>());
        }
        for (int i = 0; i < prepared.addons.size(); i++) {
            Addon addon = prepared.addons.get(i);
            if (addonsById.put(addon.id(), addon) != null) {
                throw new IllegalStateException("Duplicate collision addon id " + addon.id());
            }
            long[] cells = prepared.addonCells.get(i);
            addonCells.put(addon.id(), cells);
            addAddonToGrid(addon, cells);
        }
    }

    private void removeSegment(TerrainSegment segment) {
        if (segment.solid) {
            removeTriangle(segment.id * 2L);
            removeTriangle(segment.id * 2L + 1L);
        }
        for (Addon addon : segment.addons) {
            removeAddon(addon.id());
        }
    }

    private void removeTriangle(long triangleId) {
        TerrainTriangle triangle = trianglesById.remove(triangleId);
        if (triangle == null) {
            return;
        }
        long[] cells = triangleCells.remove(triangleId);
        if (cells != null) {
            for (long cellKey : cells) {
                ArrayList<TerrainTriangle> bucket = triangleGrid.get(cellKey);
                removeTriangleById(bucket, triangleId);
                if (bucket != null && bucket.isEmpty()) {
                    triangleGrid.remove(cellKey);
                }
            }
        }
        EdgeKey[] edges = triangleEdges.remove(triangleId);
        if (edges != null) {
            for (EdgeKey edge : edges) {
                ArrayList<EdgeRef> owners = edgeOwners.get(edge);
                removeEdgeRef(owners, triangleId);
                if (owners != null && owners.isEmpty()) {
                    edgeOwners.remove(edge);
                }
            }
        }
        HashSet<Long> oldNeighbors = sharedEdgeNeighbors.remove(triangleId);
        if (oldNeighbors != null) {
            for (Long neighborId : oldNeighbors) {
                Set<Long> neighborSet = sharedEdgeNeighbors.get(neighborId);
                if (neighborSet != null) {
                    neighborSet.remove(triangleId);
                }
            }
        }
        collisionBoundaryMasks.remove(triangleId);
    }

    private void removeAddon(long addonId) {
        Addon addon = addonsById.remove(addonId);
        if (addon == null) {
            return;
        }
        long[] cells = addonCells.remove(addonId);
        if (cells != null) {
            for (long cellKey : cells) {
                ArrayList<Addon> bucket = addonGrid.get(cellKey);
                removeAddonById(bucket, addonId);
                if (bucket != null && bucket.isEmpty()) {
                    addonGrid.remove(cellKey);
                }
            }
        }
    }

    private void collectTopologyImpact(
            TerrainSegment segment,
            Set<EdgeKey> affectedEdges,
            Set<Long> affectedTriangles) {
        if (!segment.solid) {
            return;
        }
        collectTriangleImpact(segment.id * 2L, affectedEdges, affectedTriangles);
        collectTriangleImpact(segment.id * 2L + 1L, affectedEdges, affectedTriangles);
    }

    private void collectTriangleImpact(
            long triangleId,
            Set<EdgeKey> affectedEdges,
            Set<Long> affectedTriangles) {
        affectedTriangles.add(triangleId);
        EdgeKey[] edges = triangleEdges.get(triangleId);
        if (edges != null) {
            Collections.addAll(affectedEdges, edges);
            for (EdgeKey edge : edges) {
                List<EdgeRef> owners = edgeOwners.get(edge);
                if (owners != null) {
                    for (EdgeRef owner : owners) {
                        affectedTriangles.add(owner.triangleId);
                    }
                }
            }
        }
        Set<Long> neighbors = sharedEdgeNeighbors.get(triangleId);
        if (neighbors != null) {
            affectedTriangles.addAll(neighbors);
        }
    }

    private void recomputeAllTopology() {
        recomputeTopology(new HashSet<Long>(trianglesById.keySet()));
    }

    private void recomputeTopology(Set<Long> affectedTriangleIds) {
        ArrayList<Long> ordered = new ArrayList<Long>(affectedTriangleIds);
        Collections.sort(ordered);
        for (Long triangleId : ordered) {
            Set<Long> oldNeighbors = sharedEdgeNeighbors.get(triangleId);
            if (oldNeighbors == null) {
                continue;
            }
            ArrayList<Long> copy = new ArrayList<Long>(oldNeighbors);
            for (Long neighborId : copy) {
                Set<Long> neighborSet = sharedEdgeNeighbors.get(neighborId);
                if (neighborSet != null) {
                    neighborSet.remove(triangleId);
                }
            }
            oldNeighbors.clear();
        }
        for (Long triangleId : ordered) {
            TerrainTriangle triangle = trianglesById.get(triangleId);
            if (triangle == null) {
                continue;
            }
            EdgeKey[] edges = triangleEdges.get(triangleId);
            int mask = 0b111;
            HashSet<Long> neighbors = sharedEdgeNeighbors.get(triangleId);
            for (int edgeIndex = 0; edgeIndex < edges.length; edgeIndex++) {
                List<EdgeRef> owners = edgeOwners.get(edges[edgeIndex]);
                if (owners == null) {
                    continue;
                }
                for (EdgeRef owner : owners) {
                    if (owner.triangleId == triangleId) {
                        continue;
                    }
                    TerrainTriangle other = trianglesById.get(owner.triangleId);
                    if (other == null) {
                        continue;
                    }
                    neighbors.add(other.id);
                    HashSet<Long> reciprocal = sharedEdgeNeighbors.get(other.id);
                    if (reciprocal != null) {
                        reciprocal.add(triangleId);
                    }
                    if (Math.abs(triangle.normal.dot(other.normal))
                            >= 1.0 - 1.0e-10) {
                        mask &= ~(1 << edgeIndex);
                    }
                }
            }
            collisionBoundaryMasks.put(triangleId, mask);
        }
        // A reciprocal neighbor outside the initial affected set can have its boundary mask
        // changed by the same touched edge. Recompute masks for every owner encountered.
        for (Long triangleId : ordered) {
            Set<Long> neighbors = sharedEdgeNeighbors.get(triangleId);
            if (neighbors == null) {
                continue;
            }
            for (Long neighborId : neighbors) {
                recomputeMask(neighborId);
            }
        }
    }

    private void recomputeMask(long triangleId) {
        TerrainTriangle triangle = trianglesById.get(triangleId);
        EdgeKey[] edges = triangleEdges.get(triangleId);
        if (triangle == null || edges == null) {
            return;
        }
        int mask = 0b111;
        for (int edgeIndex = 0; edgeIndex < edges.length; edgeIndex++) {
            List<EdgeRef> owners = edgeOwners.get(edges[edgeIndex]);
            if (owners == null) {
                continue;
            }
            for (EdgeRef owner : owners) {
                if (owner.triangleId == triangleId) {
                    continue;
                }
                TerrainTriangle other = trianglesById.get(owner.triangleId);
                if (other != null
                        && Math.abs(triangle.normal.dot(other.normal))
                        >= 1.0 - 1.0e-10) {
                    mask &= ~(1 << edgeIndex);
                    break;
                }
            }
        }
        collisionBoundaryMasks.put(triangleId, mask);
    }

    private void addTriangleToGrid(TerrainTriangle triangle, long[] cells) {
        for (long cellKey : cells) {
            ArrayList<TerrainTriangle> bucket = triangleGrid.get(cellKey);
            if (bucket == null) {
                bucket = new ArrayList<TerrainTriangle>();
                triangleGrid.put(cellKey, bucket);
            }
            int position = findTriangle(bucket, triangle.id);
            bucket.add(position < 0 ? -position - 1 : position, triangle);
        }
    }

    private void addAddonToGrid(Addon addon, long[] cells) {
        for (long cellKey : cells) {
            ArrayList<Addon> bucket = addonGrid.get(cellKey);
            if (bucket == null) {
                bucket = new ArrayList<Addon>();
                addonGrid.put(cellKey, bucket);
            }
            int position = findAddon(bucket, addon.id());
            bucket.add(position < 0 ? -position - 1 : position, addon);
        }
    }

    private static long[] cellsFor(Aabb bounds) {
        int minX = cell(bounds.min.x);
        int maxX = cell(bounds.max.x);
        int minZ = cell(bounds.min.z);
        int maxZ = cell(bounds.max.z);
        long width = (long) maxX - minX + 1L;
        long depth = (long) maxZ - minZ + 1L;
        long count = width * depth;
        if (width <= 0L || depth <= 0L || count > MAX_CELLS_PER_ITEM) {
            throw new IllegalArgumentException("Terrain item spans too many collision cells");
        }
        long[] result = new long[(int) count];
        int cursor = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                result[cursor++] = key(x, z);
            }
        }
        return result;
    }

    private static EdgeKey[] edges(TerrainTriangle triangle) {
        return new EdgeKey[] {
                new EdgeKey(triangle.a, triangle.b),
                new EdgeKey(triangle.b, triangle.c),
                new EdgeKey(triangle.c, triangle.a)
        };
    }

    private static void insertEdgeRef(List<EdgeRef> owners, EdgeRef addition) {
        int position = 0;
        while (position < owners.size()
                && (owners.get(position).triangleId < addition.triangleId
                || (owners.get(position).triangleId == addition.triangleId
                && owners.get(position).edgeIndex < addition.edgeIndex))) {
            position++;
        }
        owners.add(position, addition);
    }

    private static void removeEdgeRef(List<EdgeRef> owners, long triangleId) {
        if (owners == null) {
            return;
        }
        for (int i = owners.size() - 1; i >= 0; i--) {
            if (owners.get(i).triangleId == triangleId) {
                owners.remove(i);
            }
        }
    }

    private static void removeTriangleById(
            List<TerrainTriangle> values, long triangleId) {
        if (values == null) {
            return;
        }
        int position = findTriangle(values, triangleId);
        if (position >= 0) {
            values.remove(position);
        }
    }

    private static void removeAddonById(
            List<Addon> values, long addonId) {
        if (values == null) {
            return;
        }
        int position = findAddon(values, addonId);
        if (position >= 0) {
            values.remove(position);
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

    private static Vec3 frontNormal(TerrainTriangle triangle) {
        return triangle.normal.dot(Vec3.UP) < -0.001
                ? triangle.normal.multiply(-1.0) : triangle.normal;
    }

    private static int cell(double coordinate) {
        double value = Math.floor(coordinate / GRID_CELL_SIZE);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Terrain coordinate is outside grid range");
        }
        return (int) value;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static final class PreparedSegment {
        final TerrainSegment segment;
        final List<TerrainTriangle> triangles;
        final List<long[]> triangleCells;
        final List<Addon> addons;
        final List<long[]> addonCells;

        PreparedSegment(
                TerrainSegment segment,
                List<TerrainTriangle> triangles,
                List<long[]> triangleCells,
                List<Addon> addons,
                List<long[]> addonCells) {
            this.segment = segment;
            this.triangles = triangles;
            this.triangleCells = triangleCells;
            this.addons = addons;
            this.addonCells = addonCells;
        }
    }

    private static final class EdgeRef {
        final long triangleId;
        final int edgeIndex;

        EdgeRef(long triangleId, int edgeIndex) {
            this.triangleId = triangleId;
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
            EdgeKey value = (EdgeKey) other;
            return first.equals(value.first) && second.equals(value.second);
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
            PointKey value = (PointKey) other;
            return x == value.x && y == value.y && z == value.z;
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
