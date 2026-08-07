package com.example.game3d.core.terrain;

import com.example.game3d.core.math.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Complete post-commit form of one logical track segment. */
public final class TerrainSegment {
    public final long id;
    public final Vec3 nearLeft;
    public final Vec3 nearRight;
    public final Vec3 farLeft;
    public final Vec3 farRight;
    public final boolean solid;
    public final boolean connectedToPrevious;
    public final SurfaceProperties surface;
    public final TerrainVertexAppearance nearLeftAppearance;
    public final TerrainVertexAppearance nearRightAppearance;
    public final TerrainVertexAppearance farLeftAppearance;
    public final TerrainVertexAppearance farRightAppearance;
    public final List<TerrainFeatureSpec> features;

    public TerrainSegment(
            long id,
            Vec3 nearLeft,
            Vec3 nearRight,
            Vec3 farLeft,
            Vec3 farRight,
            boolean solid,
            boolean connectedToPrevious,
            SurfaceProperties surface,
            TerrainVertexAppearance nearLeftAppearance,
            TerrainVertexAppearance nearRightAppearance,
            TerrainVertexAppearance farLeftAppearance,
            TerrainVertexAppearance farRightAppearance,
            List<TerrainFeatureSpec> features) {
        if (id < 0L || id > (Long.MAX_VALUE - 1L) / 2L) {
            throw new IllegalArgumentException("Invalid segment id " + id);
        }
        requireFinite(nearLeft, "nearLeft");
        requireFinite(nearRight, "nearRight");
        requireFinite(farLeft, "farLeft");
        requireFinite(farRight, "farRight");
        if (surface == null
                || nearLeftAppearance == null || nearRightAppearance == null
                || farLeftAppearance == null || farRightAppearance == null) {
            throw new IllegalArgumentException("Segment properties cannot be null");
        }
        if (solid) {
            validateTriangle(id * 2L, nearLeft, nearRight, farRight);
            validateTriangle(id * 2L + 1L, nearLeft, farRight, farLeft);
        }
        ArrayList<TerrainFeatureSpec> sorted = new ArrayList<TerrainFeatureSpec>(
                features == null
                        ? Collections.<TerrainFeatureSpec>emptyList() : features);
        Collections.sort(sorted, new Comparator<TerrainFeatureSpec>() {
            @Override
            public int compare(TerrainFeatureSpec left, TerrainFeatureSpec right) {
                return Long.compare(left.id, right.id);
            }
        });
        Set<Long> featureIds = new HashSet<Long>();
        for (TerrainFeatureSpec feature : sorted) {
            if (feature == null || feature.ownerSegmentId != id) {
                throw new IllegalArgumentException(
                        "Feature must be non-null and owned by segment " + id);
            }
            if (!featureIds.add(feature.id)) {
                throw new IllegalArgumentException("Duplicate feature id " + feature.id);
            }
        }
        this.id = id;
        this.nearLeft = nearLeft;
        this.nearRight = nearRight;
        this.farLeft = farLeft;
        this.farRight = farRight;
        this.solid = solid;
        this.connectedToPrevious = connectedToPrevious;
        this.surface = surface;
        this.nearLeftAppearance = nearLeftAppearance;
        this.nearRightAppearance = nearRightAppearance;
        this.farLeftAppearance = farLeftAppearance;
        this.farRightAppearance = farRightAppearance;
        this.features = Collections.unmodifiableList(sorted);
    }

    public List<TerrainTriangle> collisionTriangles() {
        if (!solid) {
            return Collections.emptyList();
        }
        ArrayList<TerrainTriangle> triangles = new ArrayList<TerrainTriangle>(2);
        triangles.add(new TerrainTriangle(
                id * 2L, id, nearLeft, nearRight, farRight, surface));
        triangles.add(new TerrainTriangle(
                id * 2L + 1L, id, nearLeft, farRight, farLeft, surface));
        return Collections.unmodifiableList(triangles);
    }

    public long collisionFingerprint() {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, solid ? 1L : 0L);
        if (solid) {
            hash = mixVec(hash, nearLeft);
            hash = mixVec(hash, nearRight);
            hash = mixVec(hash, farLeft);
            hash = mixVec(hash, farRight);
            hash = mix(hash, surface.deterministicFingerprint());
        }
        return hash;
    }

    public long deterministicDigest() {
        long hash = mix(0xcbf29ce484222325L, id);
        hash = mixVec(hash, nearLeft);
        hash = mixVec(hash, nearRight);
        hash = mixVec(hash, farLeft);
        hash = mixVec(hash, farRight);
        hash = mix(hash, solid ? 1L : 0L);
        hash = mix(hash, connectedToPrevious ? 1L : 0L);
        hash = mix(hash, surface.deterministicFingerprint());
        hash = mixAppearance(hash, nearLeftAppearance);
        hash = mixAppearance(hash, nearRightAppearance);
        hash = mixAppearance(hash, farLeftAppearance);
        hash = mixAppearance(hash, farRightAppearance);
        for (TerrainFeatureSpec feature : features) {
            hash = mix(hash, feature.deterministicDigest());
        }
        return hash;
    }

    private static long mixAppearance(long hash, TerrainVertexAppearance appearance) {
        hash = mix(hash, Float.floatToIntBits(appearance.alpha));
        return mix(hash, Float.floatToIntBits(appearance.brightness));
    }

    private static long mixVec(long hash, Vec3 value) {
        hash = mix(hash, Double.doubleToLongBits(value.x));
        hash = mix(hash, Double.doubleToLongBits(value.y));
        return mix(hash, Double.doubleToLongBits(value.z));
    }

    private static long mix(long hash, long value) {
        hash ^= value;
        return hash * 0x100000001b3L;
    }

    private static void validateTriangle(long id, Vec3 a, Vec3 b, Vec3 c) {
        if (b.subtract(a).cross(c.subtract(a)).lengthSquared() < 1.0e-16) {
            throw new IllegalArgumentException("Degenerate terrain triangle " + id);
        }
    }

    private static void requireFinite(Vec3 value, String name) {
        if (value == null
                || !Double.isFinite(value.x)
                || !Double.isFinite(value.y)
                || !Double.isFinite(value.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
