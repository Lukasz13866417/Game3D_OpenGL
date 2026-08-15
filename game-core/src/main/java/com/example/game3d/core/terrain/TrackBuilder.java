package com.example.game3d.core.terrain;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.addon.Addon;
import com.example.game3d.core.terrain.addon.AddonFootprint;
import com.example.game3d.core.terrain.addon.DeathSpike;
import com.example.game3d.core.terrain.addon.Potion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Code-first terrain scenario builder. Gameplay generators and desktop scenarios are expected to
 * target this API rather than constructing private collision geometry.
 */
public strictfp final class TrackBuilder {
    private final double width;
    private final ArrayList<TerrainPatch> patches = new ArrayList<TerrainPatch>();
    private final ArrayList<TerrainSegment> segments = new ArrayList<TerrainSegment>();
    private Vec3 center;
    private Vec3 forward;
    private Vec3 right;
    private Vec3 previousFarLeft;
    private Vec3 previousFarRight;
    private long nextPatchId = 1;
    private long nextTriangleId = 1;
    private long nextAddonId = 1;
    private SurfaceMaterial material = SurfaceMaterial.NORMAL;
    private SurfaceProperties surface = SurfaceProperties.NORMAL;
    private boolean nextSegmentConnected;

    public TrackBuilder(double width) {
        if (width <= 0.0) {
            throw new IllegalArgumentException("Track width must be positive");
        }
        this.width = width;
        this.center = new Vec3(0.0, 0.0, 2.0);
        this.forward = new Vec3(0.0, 0.0, -1.0);
        this.right = new Vec3(1.0, 0.0, 0.0);
    }

    public TrackBuilder material(SurfaceMaterial value) {
        material = value;
        surface = value == null ? SurfaceProperties.NORMAL : value.properties();
        return this;
    }

    public TrackBuilder surface(SurfaceProperties value) {
        if (value == null) {
            throw new IllegalArgumentException("surface == null");
        }
        surface = value;
        material = value.kind == SurfaceProperties.Kind.NORMAL
                ? SurfaceMaterial.NORMAL : SurfaceMaterial.BOOST;
        return this;
    }

    public TrackBuilder straight(double length) {
        return slope(length, 0.0);
    }

    public TrackBuilder slope(double length, double rise) {
        if (length <= 0.0) {
            throw new IllegalArgumentException("Segment length must be positive");
        }
        Vec3 end = center.add(forward.multiply(length)).add(Vec3.UP.multiply(rise));
        publishQuad(center, end, material, surface, true);
        center = end;
        return this;
    }

    /** Advances without publishing collision surfaces. */
    public TrackBuilder gap(double length) {
        if (length <= 0.0) {
            throw new IllegalArgumentException("Gap length must be positive");
        }
        Vec3 end = center.add(forward.multiply(length));
        publishCanonicalSegment(center, end, surface, false);
        center = end;
        nextSegmentConnected = false;
        return this;
    }

    /** Creates an open ledge by changing elevation without adding a riser. */
    public TrackBuilder lift(double rise) {
        center = center.add(Vec3.UP.multiply(rise));
        nextSegmentConnected = false;
        return this;
    }

    public TrackBuilder turnDegrees(double degrees, double radius, int subdivisions) {
        if (radius <= 0.0 || subdivisions < 1) {
            throw new IllegalArgumentException("Turn radius and subdivisions must be positive");
        }
        double step = Math.toRadians(degrees) / subdivisions;
        double arcLength = Math.abs(Math.toRadians(degrees) * radius) / subdivisions;
        for (int i = 0; i < subdivisions; i++) {
            Vec3 previous = center;
            double cos = Math.cos(step);
            double sin = Math.sin(step);
            Vec3 nextForward = new Vec3(
                    forward.x * cos - forward.z * sin,
                    0.0,
                    forward.x * sin + forward.z * cos).normalized();
            Vec3 average = forward.add(nextForward).normalized();
            center = center.add(average.multiply(arcLength));
            forward = nextForward;
            right = new Vec3(-forward.z, 0.0, forward.x);
            publishQuad(previous, center, material, surface, true);
        }
        return this;
    }

    public TrackBuilder spike(double forwardOffset, double lateralOffset,
                              double radius, double height) {
        Vec3 position = center.add(forward.multiply(forwardOffset))
                .add(right.multiply(lateralOffset));
        double half = radius;
        Vec3 nearLeft = new Vec3(position.x - half, position.y, position.z + half);
        Vec3 nearRight = new Vec3(position.x + half, position.y, position.z + half);
        Vec3 farLeft = new Vec3(position.x - half, position.y, position.z - half);
        Vec3 farRight = new Vec3(position.x + half, position.y, position.z - half);
        DeathSpike spike = new DeathSpike(
                nearLeft, nearRight, farLeft, farRight,
                position.add(Vec3.UP.multiply(height)),
                Vec3.UP,
                0.0,
                position,
                radius,
                height);
        spike.place(nextAddonId++, lastSegmentId(),
                AddonFootprint.quadrilateral(nearLeft, nearRight, farLeft, farRight));
        addAddon(spike);
        addCanonicalAddon(spike);
        return this;
    }

    public TrackBuilder feather(double forwardOffset, double lateralOffset,
                                double height, double triggerRadius) {
        Vec3 position = center.add(forward.multiply(forwardOffset))
                .add(right.multiply(lateralOffset))
                .add(Vec3.UP.multiply(height));
        Potion potion = new Potion(position, triggerRadius, "FEATHER");
        potion.place(nextAddonId++, lastSegmentId(), AddonFootprint.around(
                position, triggerRadius, triggerRadius, triggerRadius));
        addAddon(potion);
        addCanonicalAddon(potion);
        return this;
    }

    public Vec3 cursor() {
        return center;
    }

    public TerrainWorld build() {
        return new TerrainWorld(Collections.unmodifiableList(patches));
    }

    public TerrainSnapshot buildSnapshot() {
        long committedThrough = segments.isEmpty()
                ? -1L : segments.get(segments.size() - 1).id;
        return new TerrainSnapshot(0L, committedThrough, 0L, segments);
    }

    private void publishQuad(
            Vec3 startCenter,
            Vec3 endCenter,
            SurfaceMaterial quadMaterial,
            SurfaceProperties quadSurface,
            boolean solid) {
        TerrainSegment segment = publishCanonicalSegment(
                startCenter, endCenter, quadSurface, solid);

        List<TerrainTriangle> triangles = new ArrayList<TerrainTriangle>(2);
        // Winding is chosen so a forward -Z horizontal track has +Y normals.
        triangles.add(new TerrainTriangle(
                nextTriangleId++,
                segment.nearLeft,
                segment.nearRight,
                segment.farRight,
                quadMaterial));
        triangles.add(new TerrainTriangle(
                nextTriangleId++,
                segment.nearLeft,
                segment.farRight,
                segment.farLeft,
                quadMaterial));
        patches.add(new TerrainPatch(nextPatchId++, triangles,
                Collections.<Addon>emptyList()));
    }

    private void addAddon(Addon addon) {
        patches.add(new TerrainPatch(nextPatchId++,
                Collections.<TerrainTriangle>emptyList(),
                Collections.singletonList(addon)));
    }

    private TerrainSegment publishCanonicalSegment(
            Vec3 startCenter,
            Vec3 endCenter,
            SurfaceProperties segmentSurface,
            boolean solid) {
        Vec3 startLeft = startCenter.subtract(right.multiply(width * 0.5));
        Vec3 startRight = startCenter.add(right.multiply(width * 0.5));
        Vec3 endLeft = endCenter.subtract(right.multiply(width * 0.5));
        Vec3 endRight = endCenter.add(right.multiply(width * 0.5));
        boolean connected = solid && nextSegmentConnected;
        // Keep the canonical ribbon watertight when the local right vector
        // changes at a turn. The legacy collision patch is built from this
        // same segment, below, so both projections have identical topology.
        if (connected) {
            if (previousFarLeft == null || previousFarRight == null) {
                throw new IllegalStateException(
                        "Connected terrain is missing its previous far edge");
            }
            startLeft = previousFarLeft;
            startRight = previousFarRight;
        }
        long id = segments.size();
        TerrainSegment segment = new TerrainSegment(
                id,
                startLeft,
                startRight,
                endLeft,
                endRight,
                solid,
                connected,
                segmentSurface,
                TerrainVertexAppearance.DEFAULT,
                TerrainVertexAppearance.DEFAULT,
                TerrainVertexAppearance.DEFAULT,
                TerrainVertexAppearance.DEFAULT,
                Collections.<Addon>emptyList());
        segments.add(segment);
        previousFarLeft = endLeft;
        previousFarRight = endRight;
        nextSegmentConnected = solid;
        return segment;
    }

    private void addCanonicalAddon(Addon addon) {
        if (segments.isEmpty()) {
            throw new IllegalStateException("An addon requires an owning segment");
        }
        int index = segments.size() - 1;
        TerrainSegment previous = segments.get(index);
        ArrayList<Addon> addons = new ArrayList<Addon>(previous.addons);
        addons.add(addon);
        segments.set(index, new TerrainSegment(
                previous.id,
                previous.nearLeft,
                previous.nearRight,
                previous.farLeft,
                previous.farRight,
                previous.solid,
                previous.connectedToPrevious,
                previous.surface,
                previous.nearLeftAppearance,
                previous.nearRightAppearance,
                previous.farLeftAppearance,
                previous.farRightAppearance,
                addons));
    }

    private long lastSegmentId() {
        if (segments.isEmpty()) {
            throw new IllegalStateException("An addon requires an owning segment");
        }
        return segments.get(segments.size() - 1).id;
    }
}
