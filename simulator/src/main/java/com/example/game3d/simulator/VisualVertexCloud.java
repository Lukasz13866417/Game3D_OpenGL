package com.example.game3d.simulator;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.simulation.PhysicsConfig;
import com.example.game3d.core.simulation.PlayerSnapshot;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Optional diagnostic-only OBJ vertex cloud; never participates in collision. */
final class VisualVertexCloud {
    private final List<Vec3> localVertices;

    private VisualVertexCloud(List<Vec3> localVertices) {
        this.localVertices = localVertices;
    }

    static VisualVertexCloud empty() {
        return new VisualVertexCloud(Collections.<Vec3>emptyList());
    }

    static VisualVertexCloud load(Path path, PhysicsConfig config) throws IOException {
        ArrayList<Vec3> raw = new ArrayList<Vec3>();
        BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("v ")) {
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 4) {
                    raw.add(new Vec3(Double.parseDouble(parts[1]),
                            Double.parseDouble(parts[2]), Double.parseDouble(parts[3])));
                }
            }
        } finally {
            reader.close();
        }
        if (raw.isEmpty()) {
            return empty();
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Vec3 vertex : raw) {
            minX = Math.min(minX, vertex.x);
            maxX = Math.max(maxX, vertex.x);
            minY = Math.min(minY, vertex.y);
            maxY = Math.max(maxY, vertex.y);
            minZ = Math.min(minZ, vertex.z);
            maxZ = Math.max(maxZ, vertex.z);
        }
        Vec3 center = new Vec3((minX + maxX) * 0.5,
                (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
        ArrayList<Vec3> oriented = new ArrayList<Vec3>(raw.size());
        for (Vec3 vertex : raw) {
            Vec3 centered = vertex.subtract(center);
            // The simulator's legacy tire_main.obj has a +Z axle. Rotate it to +X
            // before applying the cylinder's independent per-axis scaling.
            oriented.add(new Vec3(-centered.z, centered.y, centered.x));
        }
        double orientedMinX = Double.POSITIVE_INFINITY;
        double orientedMaxX = Double.NEGATIVE_INFINITY;
        double orientedMinY = Double.POSITIVE_INFINITY;
        double orientedMaxY = Double.NEGATIVE_INFINITY;
        double orientedMinZ = Double.POSITIVE_INFINITY;
        double orientedMaxZ = Double.NEGATIVE_INFINITY;
        for (Vec3 vertex : oriented) {
            orientedMinX = Math.min(orientedMinX, vertex.x);
            orientedMaxX = Math.max(orientedMaxX, vertex.x);
            orientedMinY = Math.min(orientedMinY, vertex.y);
            orientedMaxY = Math.max(orientedMaxY, vertex.y);
            orientedMinZ = Math.min(orientedMinZ, vertex.z);
            orientedMaxZ = Math.max(orientedMaxZ, vertex.z);
        }
        double scaleX = config.cylinderHalfLength * 2.0
                / Math.max(1.0e-12, orientedMaxX - orientedMinX);
        double scaleY = config.cylinderRadius * 2.0
                / Math.max(1.0e-12, orientedMaxY - orientedMinY);
        double scaleZ = config.cylinderRadius * 2.0
                / Math.max(1.0e-12, orientedMaxZ - orientedMinZ);
        ArrayList<Vec3> normalized = new ArrayList<Vec3>(raw.size());
        for (Vec3 vertex : oriented) {
            normalized.add(new Vec3(
                    vertex.x * scaleX,
                    vertex.y * scaleY,
                    vertex.z * scaleZ));
        }
        return new VisualVertexCloud(Collections.unmodifiableList(normalized));
    }

    int size() {
        return localVertices.size();
    }

    List<Vec3> worldVertices(PlayerSnapshot snapshot) {
        ArrayList<Vec3> result = new ArrayList<Vec3>(localVertices.size());
        double spinCos = Math.cos(snapshot.axleRadians);
        double spinSin = Math.sin(snapshot.axleRadians);
        double yawCos = Math.cos(snapshot.yawRadians);
        double yawSin = Math.sin(snapshot.yawRadians);
        for (Vec3 vertex : localVertices) {
            double spinY = vertex.y * spinCos - vertex.z * spinSin;
            double spinZ = vertex.y * spinSin + vertex.z * spinCos;
            double worldX = vertex.x * yawCos - spinZ * yawSin;
            double worldZ = vertex.x * yawSin + spinZ * yawCos;
            result.add(snapshot.absolutePosition.add(new Vec3(worldX, spinY, worldZ)));
        }
        return result;
    }
}
