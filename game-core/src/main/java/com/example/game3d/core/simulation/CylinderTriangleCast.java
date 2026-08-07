package com.example.game3d.core.simulation;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainTriangle;

/**
 * Deterministic, translation-only continuous collision detection between a finite cylinder and
 * one one-sided terrain triangle.
 *
 * <p>The triangle is decomposed into its face, active boundary edges, and active boundary
 * vertices. Each feature is solved analytically over the normalized frame interval. This avoids
 * both conservative-advancement iteration and GJK simplex allocation in the simulation hot path.
 */
public strictfp final class CylinderTriangleCast {
    private static final double DIRECTION_EPSILON = 1.0e-12;
    private static final double MIN_ADVANCE = 1.0e-12;
    private static final double TIME_EPSILON = 1.0e-10;
    private static final double PARAMETER_EPSILON = 1.0e-9;
    private static final double ONE_SIDED_EPSILON = 1.0e-10;
    private static final double FEATURE_TIE_EPSILON = 1.0e-9;

    private CylinderTriangleCast() {
    }

    public enum Status {
        HIT,
        MISS,
        START_OVERLAPPED,
        /** Retained for API compatibility. The analytic solver does not iterate to failure. */
        FAILED
    }

    public enum Feature {
        FACE,
        EDGE_AB,
        EDGE_BC,
        EDGE_CA,
        VERTEX_A,
        VERTEX_B,
        VERTEX_C,
        UNKNOWN
    }

    public static final class SweepHit {
        public final Status status;
        public final double fraction;
        public final Vec3 centerAtImpact;
        public final Vec3 cylinderPoint;
        public final Vec3 terrainPoint;
        /** Contact normal pointing from terrain toward the cylinder. */
        public final Vec3 normal;
        public final double signedSeparation;
        public final long triangleId;
        public final Feature feature;
        /** Number of analytic feature families evaluated; never an iteration count. */
        public final int iterations;

        private SweepHit(
                Status status,
                double fraction,
                Vec3 centerAtImpact,
                Vec3 cylinderPoint,
                Vec3 terrainPoint,
                Vec3 normal,
                double signedSeparation,
                long triangleId,
                Feature feature,
                int iterations) {
            this.status = status;
            this.fraction = fraction;
            this.centerAtImpact = centerAtImpact;
            this.cylinderPoint = cylinderPoint;
            this.terrainPoint = terrainPoint;
            this.normal = normal;
            this.signedSeparation = signedSeparation;
            this.triangleId = triangleId;
            this.feature = feature;
            this.iterations = iterations;
        }

        private static SweepHit miss(TerrainTriangle triangle, int evaluations) {
            return new SweepHit(
                    Status.MISS, Double.NaN, null, null, null, null,
                    Double.NaN, triangle.id, Feature.UNKNOWN, evaluations);
        }
    }

    public static SweepHit cast(
            Vec3 startCenter,
            Vec3 translation,
            Vec3 axis,
            double halfLength,
            double radius,
            TerrainTriangle triangle,
            int collisionBoundaryMask) {
        return cast(
                startCenter, translation, axis, halfLength, radius,
                triangle, collisionBoundaryMask,
                Math.max(1.0e-9, radius * 1.0e-7));
    }

    public static SweepHit cast(
            Vec3 startCenter,
            Vec3 translation,
            Vec3 axis,
            double halfLength,
            double radius,
            TerrainTriangle triangle,
            int collisionBoundaryMask,
            double targetDistance) {
        if (startCenter == null || translation == null || axis == null || triangle == null) {
            throw new IllegalArgumentException("Cylinder cast inputs must not be null");
        }
        if (!(halfLength >= 0.0) || !Double.isFinite(halfLength)) {
            throw new IllegalArgumentException("Cylinder half-length must be finite and non-negative");
        }
        if (!(radius > 0.0) || !Double.isFinite(radius)) {
            throw new IllegalArgumentException("Cylinder radius must be finite and positive");
        }
        if (!(targetDistance > 0.0) || !Double.isFinite(targetDistance)) {
            throw new IllegalArgumentException("TOI tolerance must be finite and positive");
        }
        double axisLengthSquared = axis.x * axis.x + axis.y * axis.y + axis.z * axis.z;
        if (!(axisLengthSquared > DIRECTION_EPSILON * DIRECTION_EPSILON)) {
            throw new IllegalArgumentException("Cylinder axis must be non-zero");
        }

        Query query = new Query(
                startCenter, translation, axis, Math.sqrt(axisLengthSquared),
                halfLength, radius, triangle,
                collisionBoundaryMask & 0b111, targetDistance);
        return query.solve();
    }

    private static final class Query {
        private final TerrainTriangle triangle;
        private final int boundaryMask;
        private final double halfLength;
        private final double radius;
        private final double targetDistance;
        private final double geometryTolerance;

        private final double cx;
        private final double cy;
        private final double cz;
        private final double vx;
        private final double vy;
        private final double vz;
        private final double ax;
        private final double ay;
        private final double az;

        private final double nx;
        private final double ny;
        private final double nz;

        private final Interval interval = new Interval();

        private int evaluations;
        private double bestTime = Double.POSITIVE_INFINITY;
        private int bestPriority = Integer.MAX_VALUE;
        private Feature bestFeature = Feature.UNKNOWN;
        private Status bestStatus = Status.MISS;
        private double bestTerrainX;
        private double bestTerrainY;
        private double bestTerrainZ;
        private double bestCylinderX;
        private double bestCylinderY;
        private double bestCylinderZ;
        private double bestNormalX;
        private double bestNormalY;
        private double bestNormalZ;
        private double bestSeparation;

        /** Scratch output for face-witness and closest-point calculations. */
        private double scratchX;
        private double scratchY;
        private double scratchZ;
        private double scratchTerrainX;
        private double scratchTerrainY;
        private double scratchTerrainZ;

        Query(
                Vec3 startCenter,
                Vec3 translation,
                Vec3 axis,
                double axisLength,
                double halfLength,
                double radius,
                TerrainTriangle triangle,
                int boundaryMask,
                double targetDistance) {
            this.triangle = triangle;
            this.boundaryMask = boundaryMask;
            this.halfLength = halfLength;
            this.radius = radius;
            this.targetDistance = targetDistance;
            this.geometryTolerance =
                    1.0e-9 * Math.max(1.0, Math.max(halfLength, radius));

            this.cx = startCenter.x;
            this.cy = startCenter.y;
            this.cz = startCenter.z;
            this.vx = translation.x;
            this.vy = translation.y;
            this.vz = translation.z;
            this.ax = axis.x / axisLength;
            this.ay = axis.y / axisLength;
            this.az = axis.z / axisLength;

            double normalSign = triangle.normal.y < -0.001 ? -1.0 : 1.0;
            this.nx = triangle.normal.x * normalSign;
            this.ny = triangle.normal.y * normalSign;
            this.nz = triangle.normal.z * normalSign;
        }

        SweepHit solve() {
            double centerSide =
                    (cx - triangle.a.x) * nx
                            + (cy - triangle.a.y) * ny
                            + (cz - triangle.a.z) * nz;
            // Terrain is authored as a one-sided surface. A center already behind it is not
            // teleported back through the surface.
            if (centerSide < 0.0) {
                return SweepHit.miss(triangle, 0);
            }

            considerFace();

            if ((boundaryMask & 0b001) != 0) {
                considerEdge(triangle.a, triangle.b, Feature.EDGE_AB);
            }
            if ((boundaryMask & 0b010) != 0) {
                considerEdge(triangle.b, triangle.c, Feature.EDGE_BC);
            }
            if ((boundaryMask & 0b100) != 0) {
                considerEdge(triangle.c, triangle.a, Feature.EDGE_CA);
            }

            if ((boundaryMask & 0b101) != 0) {
                considerVertex(triangle.a, Feature.VERTEX_A);
            }
            if ((boundaryMask & 0b011) != 0) {
                considerVertex(triangle.b, Feature.VERTEX_B);
            }
            if ((boundaryMask & 0b110) != 0) {
                considerVertex(triangle.c, Feature.VERTEX_C);
            }

            if (bestFeature == Feature.UNKNOWN) {
                return SweepHit.miss(triangle, evaluations);
            }
            double impactX = cx + vx * bestTime;
            double impactY = cy + vy * bestTime;
            double impactZ = cz + vz * bestTime;
            return new SweepHit(
                    bestStatus,
                    bestTime,
                    new Vec3(impactX, impactY, impactZ),
                    new Vec3(bestCylinderX, bestCylinderY, bestCylinderZ),
                    new Vec3(bestTerrainX, bestTerrainY, bestTerrainZ),
                    new Vec3(bestNormalX, bestNormalY, bestNormalZ),
                    bestSeparation,
                    triangle.id,
                    bestFeature,
                    evaluations);
        }

        private void considerFace() {
            evaluations++;
            double axisDotNormal = ax * nx + ay * ny + az * nz;
            double radialNormalSquared = Math.max(
                    0.0, 1.0 - axisDotNormal * axisDotNormal);
            double extent =
                    halfLength * Math.abs(axisDotNormal)
                            + radius * Math.sqrt(radialNormalSquared);
            double startGap =
                    (cx - triangle.a.x) * nx
                            + (cy - triangle.a.y) * ny
                            + (cz - triangle.a.z) * nz
                            - extent;
            double closingSpeed = -(vx * nx + vy * ny + vz * nz);
            if (closingSpeed <= MIN_ADVANCE) {
                return;
            }

            double time;
            if (startGap <= targetDistance) {
                time = 0.0;
            } else {
                time = (startGap - targetDistance) / closingSpeed;
                if (time < -TIME_EPSILON || time > 1.0 + TIME_EPSILON) {
                    return;
                }
                time = clamp01(time);
            }

            double centerX = cx + vx * time;
            double centerY = cy + vy * time;
            double centerZ = cz + vz * time;
            if (!findFaceWitness(
                    centerX, centerY, centerZ,
                    axisDotNormal, radialNormalSquared)) {
                return;
            }

            double separation =
                    (scratchX - triangle.a.x) * nx
                            + (scratchY - triangle.a.y) * ny
                            + (scratchZ - triangle.a.z) * nz;
            commit(
                    time,
                    Feature.FACE,
                    0,
                    scratchTerrainX, scratchTerrainY, scratchTerrainZ,
                    scratchX, scratchY, scratchZ,
                    nx, ny, nz,
                    separation,
                    time <= TIME_EPSILON && startGap < -targetDistance);
        }

        /**
         * Finds a point in the cylinder's support set opposite the face normal whose projection
         * lies in the finite triangle.
         */
        private boolean findFaceWitness(
                double centerX,
                double centerY,
                double centerZ,
                double axisDotNormal,
                double radialNormalSquared) {
            double radialNormalLength = Math.sqrt(radialNormalSquared);

            if (Math.abs(axisDotNormal) <= 1.0e-10) {
                // The support set is a full generator segment parallel to the cylinder axis.
                double baseX = centerX - radius * nx;
                double baseY = centerY - radius * ny;
                double baseZ = centerZ - radius * nz;
                double planeDistance =
                        (baseX - triangle.a.x) * nx
                                + (baseY - triangle.a.y) * ny
                                + (baseZ - triangle.a.z) * nz;
                double projectedX = baseX - planeDistance * nx;
                double projectedY = baseY - planeDistance * ny;
                double projectedZ = baseZ - planeDistance * nz;

                if (!clipAxialGeneratorToTriangle(
                        projectedX, projectedY, projectedZ)) {
                    return false;
                }
                double lambda = scratchX;
                scratchX = baseX + lambda * ax;
                scratchY = baseY + lambda * ay;
                scratchZ = baseZ + lambda * az;
                scratchTerrainX = scratchX - planeDistance * nx;
                scratchTerrainY = scratchY - planeDistance * ny;
                scratchTerrainZ = scratchZ - planeDistance * nz;
                return true;
            }

            if (radialNormalLength <= 1.0e-10) {
                // The support set is a cap disk. Its center need not project into the triangle:
                // the disk may still overlap a triangle edge or corner.
                double capSign = axisDotNormal > 0.0 ? -1.0 : 1.0;
                double capCenterX = centerX + capSign * halfLength * ax;
                double capCenterY = centerY + capSign * halfLength * ay;
                double capCenterZ = centerZ + capSign * halfLength * az;
                double planeDistance =
                        (capCenterX - triangle.a.x) * nx
                                + (capCenterY - triangle.a.y) * ny
                                + (capCenterZ - triangle.a.z) * nz;
                double projectedX = capCenterX - planeDistance * nx;
                double projectedY = capCenterY - planeDistance * ny;
                double projectedZ = capCenterZ - planeDistance * nz;
                closestPointOnTriangle(projectedX, projectedY, projectedZ);
                double dx = scratchX - projectedX;
                double dy = scratchY - projectedY;
                double dz = scratchZ - projectedZ;
                if (dx * dx + dy * dy + dz * dz
                        > square(radius + geometryTolerance)) {
                    return false;
                }
                scratchTerrainX = scratchX;
                scratchTerrainY = scratchY;
                scratchTerrainZ = scratchZ;
                scratchX += planeDistance * nx;
                scratchY += planeDistance * ny;
                scratchZ += planeDistance * nz;
                return true;
            }

            double radialX = nx - axisDotNormal * ax;
            double radialY = ny - axisDotNormal * ay;
            double radialZ = nz - axisDotNormal * az;
            double capSign = axisDotNormal > 0.0 ? -1.0 : 1.0;
            double pointX =
                    centerX + capSign * halfLength * ax
                            - radius * radialX / radialNormalLength;
            double pointY =
                    centerY + capSign * halfLength * ay
                            - radius * radialY / radialNormalLength;
            double pointZ =
                    centerZ + capSign * halfLength * az
                            - radius * radialZ / radialNormalLength;
            double planeDistance =
                    (pointX - triangle.a.x) * nx
                            + (pointY - triangle.a.y) * ny
                            + (pointZ - triangle.a.z) * nz;
            double projectedX = pointX - planeDistance * nx;
            double projectedY = pointY - planeDistance * ny;
            double projectedZ = pointZ - planeDistance * nz;
            if (!containsProjectedPoint(
                    projectedX, projectedY, projectedZ, 1.0e-8)) {
                return false;
            }
            scratchX = pointX;
            scratchY = pointY;
            scratchZ = pointZ;
            scratchTerrainX = projectedX;
            scratchTerrainY = projectedY;
            scratchTerrainZ = projectedZ;
            return true;
        }

        /**
         * Clips the cylinder's axial support generator against all three barycentric half-spaces.
         * On success {@code scratchX} contains the chosen axial coordinate.
         */
        private boolean clipAxialGeneratorToTriangle(
                double pointX, double pointY, double pointZ) {
            double v0x = triangle.b.x - triangle.a.x;
            double v0y = triangle.b.y - triangle.a.y;
            double v0z = triangle.b.z - triangle.a.z;
            double v1x = triangle.c.x - triangle.a.x;
            double v1y = triangle.c.y - triangle.a.y;
            double v1z = triangle.c.z - triangle.a.z;
            double relativeX = pointX - triangle.a.x;
            double relativeY = pointY - triangle.a.y;
            double relativeZ = pointZ - triangle.a.z;
            double d00 = v0x * v0x + v0y * v0y + v0z * v0z;
            double d01 = v0x * v1x + v0y * v1y + v0z * v1z;
            double d11 = v1x * v1x + v1y * v1y + v1z * v1z;
            double denominator = d00 * d11 - d01 * d01;
            if (!(denominator > 0.0) || !Double.isFinite(denominator)) {
                return false;
            }

            double d20 =
                    relativeX * v0x + relativeY * v0y + relativeZ * v0z;
            double d21 =
                    relativeX * v1x + relativeY * v1y + relativeZ * v1z;
            double along20 = ax * v0x + ay * v0y + az * v0z;
            double along21 = ax * v1x + ay * v1y + az * v1z;
            double atPointB = (d11 * d20 - d01 * d21) / denominator;
            double atPointC = (d00 * d21 - d01 * d20) / denominator;
            double slopeB = (d11 * along20 - d01 * along21) / denominator;
            double slopeC = (d00 * along21 - d01 * along20) / denominator;
            double atPointA = 1.0 - atPointB - atPointC;
            double slopeA = -slopeB - slopeC;

            interval.reset(-halfLength, halfLength);
            interval.clipLinearLower(atPointA, slopeA, -1.0e-8);
            interval.clipLinearLower(atPointB, slopeB, -1.0e-8);
            interval.clipLinearLower(atPointC, slopeC, -1.0e-8);
            if (!interval.valid) {
                return false;
            }
            scratchX = clamp(0.0, interval.low, interval.high);
            return true;
        }

        private void considerEdge(Vec3 start, Vec3 end, Feature feature) {
            evaluations++;
            double ex = end.x - start.x;
            double ey = end.y - start.y;
            double ez = end.z - start.z;
            double dx = start.x - cx;
            double dy = start.y - cy;
            double dz = start.z - cz;

            double z0 = dx * ax + dy * ay + dz * az;
            double zs = ex * ax + ey * ay + ez * az;
            double zv = vx * ax + vy * ay + vz * az;

            double q0x = dx - z0 * ax;
            double q0y = dy - z0 * ay;
            double q0z = dz - z0 * az;
            double qsx = ex - zs * ax;
            double qsy = ey - zs * ay;
            double qsz = ez - zs * az;
            double qvx = vx - zv * ax;
            double qvy = vy - zv * ay;
            double qvz = vz - zv * az;

            double qsSquared = qsx * qsx + qsy * qsy + qsz * qsz;
            if (qsSquared > DIRECTION_EPSILON * DIRECTION_EPSILON) {
                // Interior side contact: the edge point is the radial closest point for each t.
                double alpha =
                        -(q0x * qsx + q0y * qsy + q0z * qsz) / qsSquared;
                double beta =
                        (qvx * qsx + qvy * qsy + qvz * qsz) / qsSquared;
                double radial0X = q0x + alpha * qsx;
                double radial0Y = q0y + alpha * qsy;
                double radial0Z = q0z + alpha * qsz;
                double radialSlopeX = beta * qsx - qvx;
                double radialSlopeY = beta * qsy - qvy;
                double radialSlopeZ = beta * qsz - qvz;
                double axial0 = z0 + alpha * zs;
                double axialSlope = beta * zs - zv;

                interval.reset(0.0, 1.0);
                interval.clipLinearRange(alpha, beta, 0.0, 1.0);
                interval.clipLinearRange(
                        axial0, axialSlope, -halfLength, halfLength);
                interval.clipSquaredVector(
                        radial0X, radial0Y, radial0Z,
                        radialSlopeX, radialSlopeY, radialSlopeZ,
                        radius + targetDistance + geometryTolerance);
                if (interval.valid) {
                    double time = interval.low;
                    considerEdgePoint(
                            time, alpha + beta * time,
                            start, ex, ey, ez, feature);
                }
            } else {
                // The segment is parallel to the cylinder axis in radial projection. All of its
                // points have the same radial distance; only axial interval overlap remains.
                interval.reset(0.0, 1.0);
                double minimumAxialDelta = Math.min(0.0, zs);
                double maximumAxialDelta = Math.max(0.0, zs);
                interval.clipLinearRange(
                        z0, -zv,
                        -halfLength - maximumAxialDelta,
                        halfLength - minimumAxialDelta);
                interval.clipSquaredVector(
                        q0x, q0y, q0z,
                        -qvx, -qvy, -qvz,
                        radius + targetDistance + geometryTolerance);
                if (interval.valid) {
                    double time = interval.low;
                    double axialBase = z0 - zv * time;
                    double parameter = Math.abs(zs) <= DIRECTION_EPSILON
                            ? 0.5
                            : clamp(-axialBase / zs, 0.0, 1.0);
                    considerEdgePoint(
                            time, parameter,
                            start, ex, ey, ez, feature);
                }
            }

            // Cap contacts are the other interior KKT cases: z(s,t) is fixed at either cap and
            // the resulting moving point enters the cap disk.
            considerEdgeCap(
                    start, ex, ey, ez, feature, -1.0,
                    z0, zs, zv,
                    q0x, q0y, q0z, qsx, qsy, qsz, qvx, qvy, qvz, qsSquared);
            considerEdgeCap(
                    start, ex, ey, ez, feature, 1.0,
                    z0, zs, zv,
                    q0x, q0y, q0z, qsx, qsy, qsz, qvx, qvy, qvz, qsSquared);
        }

        private void considerEdgeCap(
                Vec3 start,
                double ex,
                double ey,
                double ez,
                Feature feature,
                double capSign,
                double z0,
                double zs,
                double zv,
                double q0x,
                double q0y,
                double q0z,
                double qsx,
                double qsy,
                double qsz,
                double qvx,
                double qvy,
                double qvz,
                double qsSquared) {
            double cap = capSign * halfLength;
            if (Math.abs(zs) > DIRECTION_EPSILON) {
                double alpha = (cap - z0) / zs;
                double beta = zv / zs;
                double radial0X = q0x + alpha * qsx;
                double radial0Y = q0y + alpha * qsy;
                double radial0Z = q0z + alpha * qsz;
                double radialSlopeX = beta * qsx - qvx;
                double radialSlopeY = beta * qsy - qvy;
                double radialSlopeZ = beta * qsz - qvz;

                interval.reset(0.0, 1.0);
                interval.clipLinearRange(alpha, beta, 0.0, 1.0);
                interval.clipSquaredVector(
                        radial0X, radial0Y, radial0Z,
                        radialSlopeX, radialSlopeY, radialSlopeZ,
                        radius + geometryTolerance);
                if (interval.valid) {
                    double time = interval.low;
                    considerEdgePoint(
                            time, alpha + beta * time,
                            start, ex, ey, ez, feature);
                }
                return;
            }

            if (Math.abs(zv) <= DIRECTION_EPSILON) {
                // If the entire motion already lies on this cap, radial entry is covered by the
                // side case above.
                return;
            }
            double time = (z0 - cap) / zv;
            if (time < -TIME_EPSILON || time > 1.0 + TIME_EPSILON) {
                return;
            }
            time = clamp01(time);
            double movingQx = q0x - qvx * time;
            double movingQy = q0y - qvy * time;
            double movingQz = q0z - qvz * time;
            double parameter = qsSquared <= DIRECTION_EPSILON * DIRECTION_EPSILON
                    ? 0.5
                    : clamp(
                            -(movingQx * qsx + movingQy * qsy + movingQz * qsz)
                                    / qsSquared,
                            0.0, 1.0);
            double radialX = movingQx + parameter * qsx;
            double radialY = movingQy + parameter * qsy;
            double radialZ = movingQz + parameter * qsz;
            if (radialX * radialX + radialY * radialY + radialZ * radialZ
                    <= square(radius + geometryTolerance)) {
                considerEdgePoint(
                        time, parameter,
                        start, ex, ey, ez, feature);
            }
        }

        private void considerVertex(Vec3 point, Feature feature) {
            evaluations++;
            double dx = point.x - cx;
            double dy = point.y - cy;
            double dz = point.z - cz;
            double zv = vx * ax + vy * ay + vz * az;
            double z0 = dx * ax + dy * ay + dz * az;
            double q0x = dx - z0 * ax;
            double q0y = dy - z0 * ay;
            double q0z = dz - z0 * az;
            double qvx = vx - zv * ax;
            double qvy = vy - zv * ay;
            double qvz = vz - zv * az;

            interval.reset(0.0, 1.0);
            interval.clipLinearRange(z0, -zv, -halfLength, halfLength);
            interval.clipSquaredVector(
                    q0x, q0y, q0z,
                    -qvx, -qvy, -qvz,
                    radius + targetDistance + geometryTolerance);
            if (interval.valid) {
                considerBoundaryPoint(
                        interval.low, point.x, point.y, point.z, feature, 1);
            }
        }

        private void considerEdgePoint(
                double time,
                double parameter,
                Vec3 start,
                double ex,
                double ey,
                double ez,
                Feature edgeFeature) {
            if (parameter <= PARAMETER_EPSILON
                    || parameter >= 1.0 - PARAMETER_EPSILON) {
                // Endpoints are solved independently as vertex trajectories. Keeping their
                // classification exact is important for diagnostics and tie handling.
                return;
            }
            considerBoundaryPoint(
                    time,
                    start.x + parameter * ex,
                    start.y + parameter * ey,
                    start.z + parameter * ez,
                    edgeFeature,
                    2);
        }

        private void considerBoundaryPoint(
                double time,
                double terrainX,
                double terrainY,
                double terrainZ,
                Feature feature,
                int priority) {
            if (time < -TIME_EPSILON || time > 1.0 + TIME_EPSILON) {
                return;
            }
            time = clamp01(time);
            double centerX = cx + vx * time;
            double centerY = cy + vy * time;
            double centerZ = cz + vz * time;
            double dx = terrainX - centerX;
            double dy = terrainY - centerY;
            double dz = terrainZ - centerZ;
            double axial = dx * ax + dy * ay + dz * az;
            double radialX = dx - axial * ax;
            double radialY = dy - axial * ay;
            double radialZ = dz - axial * az;
            double radialLengthSquared =
                    radialX * radialX + radialY * radialY + radialZ * radialZ;
            double radialLength = Math.sqrt(Math.max(0.0, radialLengthSquared));
            // The interval roots are computed against an inflated boundary. Allow a few ulps
            // around that root when reconstructing the 3-D witness.
            double reconstructionTolerance = geometryTolerance * 8.0;
            if (Math.abs(axial) > halfLength + reconstructionTolerance
                    || radialLength
                    > radius + targetDistance + reconstructionTolerance) {
                return;
            }

            boolean radialBoundary =
                    Math.abs(radialLength - (radius + targetDistance))
                            <= reconstructionTolerance;
            boolean capBoundary =
                    Math.abs(Math.abs(axial) - halfLength)
                            <= reconstructionTolerance;
            boolean strictlyInside =
                    radialLength < radius - geometryTolerance
                            && Math.abs(axial) < halfLength - geometryTolerance;
            boolean startsInsideSideTolerance =
                    time <= TIME_EPSILON
                            && radialLength
                            <= radius + targetDistance + reconstructionTolerance
                            && Math.abs(axial)
                            <= halfLength + reconstructionTolerance;
            if (time > TIME_EPSILON && !radialBoundary && !capBoundary) {
                // An entering trajectory must touch at least one cylinder boundary. A candidate
                // inside both constraints came from another interval becoming active and is
                // handled by the corresponding endpoint/cap case.
                return;
            }

            double sideNormalX = 0.0;
            double sideNormalY = 0.0;
            double sideNormalZ = 0.0;
            boolean hasSide = radialLength > DIRECTION_EPSILON;
            if (hasSide) {
                sideNormalX = -radialX / radialLength;
                sideNormalY = -radialY / radialLength;
                sideNormalZ = -radialZ / radialLength;
            }
            double axialSign = axial < 0.0 ? -1.0 : 1.0;
            double capNormalX = -axialSign * ax;
            double capNormalY = -axialSign * ay;
            double capNormalZ = -axialSign * az;

            boolean allowSide = hasSide
                    && (radialBoundary || strictlyInside || startsInsideSideTolerance);
            boolean allowCap = capBoundary || strictlyInside;
            double sideClosing = allowSide
                    ? -(vx * sideNormalX + vy * sideNormalY + vz * sideNormalZ)
                    : Double.NEGATIVE_INFINITY;
            double capClosing = allowCap
                    ? -(vx * capNormalX + vy * capNormalY + vz * capNormalZ)
                    : Double.NEGATIVE_INFINITY;
            double sideFacing = allowSide
                    ? sideNormalX * nx + sideNormalY * ny + sideNormalZ * nz
                    : Double.NEGATIVE_INFINITY;
            double capFacing = allowCap
                    ? capNormalX * nx + capNormalY * ny + capNormalZ * nz
                    : Double.NEGATIVE_INFINITY;
            boolean sideAccepts =
                    allowSide
                            && sideClosing > MIN_ADVANCE
                            && sideFacing > ONE_SIDED_EPSILON;
            boolean capAccepts =
                    allowCap
                            && capClosing > MIN_ADVANCE
                            && capFacing > ONE_SIDED_EPSILON;
            if (!sideAccepts && !capAccepts) {
                return;
            }

            int normalKind;
            double normalX;
            double normalY;
            double normalZ;
            if (sideAccepts && capAccepts && radialBoundary && capBoundary) {
                // At a rim, select a member of the normal cone that opposes the motion. This is
                // stable at oblique vertex impacts and reduces to the radial normal when axial
                // motion is absent.
                double sideWeight = Math.max(0.0, sideClosing);
                double capWeight = Math.max(0.0, capClosing);
                normalX = sideWeight * sideNormalX + capWeight * capNormalX;
                normalY = sideWeight * sideNormalY + capWeight * capNormalY;
                normalZ = sideWeight * sideNormalZ + capWeight * capNormalZ;
                double normalLength =
                        Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
                if (normalLength <= DIRECTION_EPSILON) {
                    return;
                }
                normalX /= normalLength;
                normalY /= normalLength;
                normalZ /= normalLength;
                normalKind = 3;
            } else if (sideAccepts && capAccepts) {
                double radialClearance = radius - radialLength;
                double axialClearance = halfLength - Math.abs(axial);
                if (radialClearance <= axialClearance) {
                    normalX = sideNormalX;
                    normalY = sideNormalY;
                    normalZ = sideNormalZ;
                    normalKind = 1;
                } else {
                    normalX = capNormalX;
                    normalY = capNormalY;
                    normalZ = capNormalZ;
                    normalKind = 2;
                }
            } else if (sideAccepts) {
                normalX = sideNormalX;
                normalY = sideNormalY;
                normalZ = sideNormalZ;
                normalKind = 1;
            } else {
                normalX = capNormalX;
                normalY = capNormalY;
                normalZ = capNormalZ;
                normalKind = 2;
            }

            double cylinderX;
            double cylinderY;
            double cylinderZ;
            double separation;
            if (normalKind == 1) {
                cylinderX = centerX + axial * ax + radialX * radius / radialLength;
                cylinderY = centerY + axial * ay + radialY * radius / radialLength;
                cylinderZ = centerZ + axial * az + radialZ * radius / radialLength;
                separation = radialLength - radius;
            } else if (normalKind == 2) {
                cylinderX =
                        centerX + axialSign * halfLength * ax + radialX;
                cylinderY =
                        centerY + axialSign * halfLength * ay + radialY;
                cylinderZ =
                        centerZ + axialSign * halfLength * az + radialZ;
                separation = Math.abs(axial) - halfLength;
            } else {
                cylinderX =
                        centerX + axialSign * halfLength * ax
                                + radialX * radius / radialLength;
                cylinderY =
                        centerY + axialSign * halfLength * ay
                                + radialY * radius / radialLength;
                cylinderZ =
                        centerZ + axialSign * halfLength * az
                                + radialZ * radius / radialLength;
                double radialSeparation = radialLength - radius;
                double axialSeparation = Math.abs(axial) - halfLength;
                separation = radialSeparation > 0.0 && axialSeparation > 0.0
                        ? Math.sqrt(
                                radialSeparation * radialSeparation
                                        + axialSeparation * axialSeparation)
                        : Math.max(radialSeparation, axialSeparation);
            }

            commit(
                    time,
                    feature,
                    priority,
                    terrainX, terrainY, terrainZ,
                    cylinderX, cylinderY, cylinderZ,
                    normalX, normalY, normalZ,
                    separation,
                    time <= TIME_EPSILON && strictlyInside);
        }

        private void commit(
                double time,
                Feature feature,
                int priority,
                double terrainX,
                double terrainY,
                double terrainZ,
                double cylinderX,
                double cylinderY,
                double cylinderZ,
                double normalX,
                double normalY,
                double normalZ,
                double separation,
                boolean startOverlapped) {
            double normalLengthSquared =
                    normalX * normalX + normalY * normalY + normalZ * normalZ;
            if (!(normalLengthSquared > DIRECTION_EPSILON * DIRECTION_EPSILON)) {
                return;
            }
            double inverseNormalLength = 1.0 / Math.sqrt(normalLengthSquared);
            normalX *= inverseNormalLength;
            normalY *= inverseNormalLength;
            normalZ *= inverseNormalLength;
            if (normalX * nx + normalY * ny + normalZ * nz
                    <= ONE_SIDED_EPSILON) {
                return;
            }
            if (-(vx * normalX + vy * normalY + vz * normalZ)
                    <= MIN_ADVANCE) {
                return;
            }

            if (time > bestTime + FEATURE_TIE_EPSILON) {
                return;
            }
            if (Math.abs(time - bestTime) <= FEATURE_TIE_EPSILON
                    && priority >= bestPriority) {
                return;
            }

            bestTime = clamp01(time);
            bestPriority = priority;
            bestFeature = feature;
            bestStatus = startOverlapped ? Status.START_OVERLAPPED : Status.HIT;
            bestTerrainX = terrainX;
            bestTerrainY = terrainY;
            bestTerrainZ = terrainZ;
            bestCylinderX = cylinderX;
            bestCylinderY = cylinderY;
            bestCylinderZ = cylinderZ;
            bestNormalX = normalX;
            bestNormalY = normalY;
            bestNormalZ = normalZ;
            bestSeparation = separation;
        }

        private boolean containsProjectedPoint(
                double px, double py, double pz, double tolerance) {
            double v0x = triangle.b.x - triangle.a.x;
            double v0y = triangle.b.y - triangle.a.y;
            double v0z = triangle.b.z - triangle.a.z;
            double v1x = triangle.c.x - triangle.a.x;
            double v1y = triangle.c.y - triangle.a.y;
            double v1z = triangle.c.z - triangle.a.z;
            double v2x = px - triangle.a.x;
            double v2y = py - triangle.a.y;
            double v2z = pz - triangle.a.z;
            double d00 = v0x * v0x + v0y * v0y + v0z * v0z;
            double d01 = v0x * v1x + v0y * v1y + v0z * v1z;
            double d11 = v1x * v1x + v1y * v1y + v1z * v1z;
            double d20 = v2x * v0x + v2y * v0y + v2z * v0z;
            double d21 = v2x * v1x + v2y * v1y + v2z * v1z;
            double denominator = d00 * d11 - d01 * d01;
            if (!(denominator > 0.0) || !Double.isFinite(denominator)) {
                return false;
            }
            double second = (d11 * d20 - d01 * d21) / denominator;
            double third = (d00 * d21 - d01 * d20) / denominator;
            double first = 1.0 - second - third;
            return first >= -tolerance
                    && second >= -tolerance
                    && third >= -tolerance;
        }

        /** Writes the closest point to {@code scratchX/Y/Z}. */
        private void closestPointOnTriangle(double px, double py, double pz) {
            double abx = triangle.b.x - triangle.a.x;
            double aby = triangle.b.y - triangle.a.y;
            double abz = triangle.b.z - triangle.a.z;
            double acx = triangle.c.x - triangle.a.x;
            double acy = triangle.c.y - triangle.a.y;
            double acz = triangle.c.z - triangle.a.z;
            double apx = px - triangle.a.x;
            double apy = py - triangle.a.y;
            double apz = pz - triangle.a.z;
            double d1 = abx * apx + aby * apy + abz * apz;
            double d2 = acx * apx + acy * apy + acz * apz;
            if (d1 <= 0.0 && d2 <= 0.0) {
                setScratch(triangle.a.x, triangle.a.y, triangle.a.z);
                return;
            }

            double bpx = px - triangle.b.x;
            double bpy = py - triangle.b.y;
            double bpz = pz - triangle.b.z;
            double d3 = abx * bpx + aby * bpy + abz * bpz;
            double d4 = acx * bpx + acy * bpy + acz * bpz;
            if (d3 >= 0.0 && d4 <= d3) {
                setScratch(triangle.b.x, triangle.b.y, triangle.b.z);
                return;
            }

            double vc = d1 * d4 - d3 * d2;
            if (vc <= 0.0 && d1 >= 0.0 && d3 <= 0.0) {
                double parameter = d1 / (d1 - d3);
                setScratch(
                        triangle.a.x + parameter * abx,
                        triangle.a.y + parameter * aby,
                        triangle.a.z + parameter * abz);
                return;
            }

            double cpx = px - triangle.c.x;
            double cpy = py - triangle.c.y;
            double cpz = pz - triangle.c.z;
            double d5 = abx * cpx + aby * cpy + abz * cpz;
            double d6 = acx * cpx + acy * cpy + acz * cpz;
            if (d6 >= 0.0 && d5 <= d6) {
                setScratch(triangle.c.x, triangle.c.y, triangle.c.z);
                return;
            }

            double vb = d5 * d2 - d1 * d6;
            if (vb <= 0.0 && d2 >= 0.0 && d6 <= 0.0) {
                double parameter = d2 / (d2 - d6);
                setScratch(
                        triangle.a.x + parameter * acx,
                        triangle.a.y + parameter * acy,
                        triangle.a.z + parameter * acz);
                return;
            }

            double va = d3 * d6 - d5 * d4;
            if (va <= 0.0 && d4 - d3 >= 0.0 && d5 - d6 >= 0.0) {
                double parameter =
                        (d4 - d3) / ((d4 - d3) + (d5 - d6));
                setScratch(
                        triangle.b.x + parameter * (triangle.c.x - triangle.b.x),
                        triangle.b.y + parameter * (triangle.c.y - triangle.b.y),
                        triangle.b.z + parameter * (triangle.c.z - triangle.b.z));
                return;
            }

            double inverse = 1.0 / (va + vb + vc);
            double v = vb * inverse;
            double w = vc * inverse;
            setScratch(
                    triangle.a.x + v * abx + w * acx,
                    triangle.a.y + v * aby + w * acy,
                    triangle.a.z + v * abz + w * acz);
        }

        private void setScratch(double x, double y, double z) {
            scratchX = x;
            scratchY = y;
            scratchZ = z;
        }
    }

    /** Mutable interval used to intersect the analytic feature constraints without allocation. */
    private static final class Interval {
        double low;
        double high;
        boolean valid;

        void reset(double low, double high) {
            this.low = low;
            this.high = high;
            this.valid = low <= high;
        }

        void clipLinearRange(
                double valueAtZero,
                double slope,
                double minimum,
                double maximum) {
            if (!valid) {
                return;
            }
            if (Math.abs(slope) <= DIRECTION_EPSILON) {
                if (valueAtZero < minimum || valueAtZero > maximum) {
                    valid = false;
                }
                return;
            }
            double first = (minimum - valueAtZero) / slope;
            double second = (maximum - valueAtZero) / slope;
            clip(Math.min(first, second), Math.max(first, second));
        }

        void clipLinearLower(double valueAtZero, double slope, double minimum) {
            if (!valid) {
                return;
            }
            if (Math.abs(slope) <= DIRECTION_EPSILON) {
                if (valueAtZero < minimum) {
                    valid = false;
                }
                return;
            }
            double crossing = (minimum - valueAtZero) / slope;
            if (slope > 0.0) {
                clip(crossing, Double.POSITIVE_INFINITY);
            } else {
                clip(Double.NEGATIVE_INFINITY, crossing);
            }
        }

        void clipSquaredVector(
                double x0,
                double y0,
                double z0,
                double xs,
                double ys,
                double zs,
                double maximumLength) {
            if (!valid) {
                return;
            }
            double quadratic = xs * xs + ys * ys + zs * zs;
            double linear = 2.0 * (x0 * xs + y0 * ys + z0 * zs);
            double constant =
                    x0 * x0 + y0 * y0 + z0 * z0
                            - maximumLength * maximumLength;
            if (quadratic <= DIRECTION_EPSILON * DIRECTION_EPSILON) {
                if (Math.abs(linear) <= DIRECTION_EPSILON) {
                    if (constant > 0.0) {
                        valid = false;
                    }
                    return;
                }
                double crossing = -constant / linear;
                if (linear > 0.0) {
                    clip(Double.NEGATIVE_INFINITY, crossing);
                } else {
                    clip(crossing, Double.POSITIVE_INFINITY);
                }
                return;
            }

            double discriminant = linear * linear - 4.0 * quadratic * constant;
            double discriminantTolerance =
                    1.0e-14 * Math.max(
                            1.0,
                            linear * linear
                                    + Math.abs(4.0 * quadratic * constant));
            if (discriminant < -discriminantTolerance) {
                valid = false;
                return;
            }
            double root = Math.sqrt(Math.max(0.0, discriminant));
            double first;
            double second;
            if (root == 0.0) {
                first = second = -linear / (2.0 * quadratic);
            } else {
                double stable = -0.5 * (linear + Math.copySign(root, linear));
                first = stable / quadratic;
                second = constant / stable;
                if (first > second) {
                    double swap = first;
                    first = second;
                    second = swap;
                }
            }
            clip(first, second);
        }

        private void clip(double minimum, double maximum) {
            low = Math.max(low, minimum);
            high = Math.min(high, maximum);
            if (low > high + TIME_EPSILON) {
                valid = false;
            } else if (low > high) {
                low = high;
            }
        }
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double square(double value) {
        return value * value;
    }
}
