package com.example.game3d_opengl.game.util;

import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.add;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.div;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.dotProduct;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.sub;
import static java.lang.Math.sqrt;

import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;


public class GameMath {

    public static final class MutableVec3 {
        public float x;
        public float y;
        public float z;

        public MutableVec3 set(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }
    }

    public static final float EPSILON = 1e-6f;
    public static final float PI = 3.1415926535f;
    public static final float INF = Float.POSITIVE_INFINITY, NINF = Float.NEGATIVE_INFINITY;
    public static boolean testParallel(Vector3D a, Vector3D b) {
        // Degenerate case: zero-length vectors are not directions
        if (a.sqlen() == 0 || b.sqlen() == 0) return false;

        // If cross product magnitude is ~0, vectors are parallel
        Vector3D cross = a.crossProduct(b);

        // Tolerance squared (adjust if needed)
        final double EPS = 1e-8;

        return cross.sqlen() < EPS;
    }

    public static float roundToDecimals(float value, int decimals) {
        if (decimals < 0) {
            throw new IllegalArgumentException("decimals must be >= 0");
        }

        double factor = Math.pow(10.0, decimals);
        return (float) (Math.round(value * factor) / factor);
    }

    public static float tan(float alpha){
        return (float)(Math.tan(alpha));
    }
    public static float sin(float alpha){
        return (float)(Math.sin(alpha));
    }
    public static float cos(float alpha){
        return (float)(Math.cos(alpha));
    }

    public static int pointAndPlanePosition(Vector3D A, Vector3D B, Vector3D C, Vector3D P) {
        Vector3D AB = B.sub(A);
        Vector3D AC = C.sub(A);
        Vector3D AP = P.sub(A);

        Vector3D normal = AB.crossProduct(AC);
        double dotProduct = normal.dotProduct(AP);

        if (dotProduct > 0) {
            return 1;
        } else if (dotProduct < 0) {
            return -1;
        } else {
            return 0;
        }
    }

    public static boolean isPointInTriangle(Vector3D a, Vector3D b, Vector3D c, Vector3D p) {
        Vector3D v0 = sub(b, a);
        Vector3D v1 = sub(c, a);
        Vector3D v2 = sub(p, a);

        float dot00 = dotProduct(v0, v0);
        float dot01 = dotProduct(v0, v1);
        float dot02 = dotProduct(v0, v2);
        float dot11 = dotProduct(v1, v1);
        float dot12 = dotProduct(v1, v2);

        float invDenom = 1 / (dot00 * dot11 - dot01 * dot01);
        float u = (dot11 * dot02 - dot01 * dot12) * invDenom;
        float v = (dot00 * dot12 - dot01 * dot02) * invDenom;
        return (u >= 0) && (v >= 0) && (u + v < 1);
    }

    public static Vector3D getNormal(Vector3D... points) {
        return _getNormal(points[0],points[1],points[2]);
    }

    public static void getUnitNormalTo(MutableVec3 out, Vector3D point1, Vector3D point2, Vector3D point3) {
        float edge1x = point2.x - point1.x;
        float edge1y = point2.y - point1.y;
        float edge1z = point2.z - point1.z;
        float edge2x = point3.x - point1.x;
        float edge2y = point3.y - point1.y;
        float edge2z = point3.z - point1.z;

        float normalX = edge1y * edge2z - edge1z * edge2y;
        float normalY = edge1z * edge2x - edge1x * edge2z;
        float normalZ = edge1x * edge2y - edge1y * edge2x;
        float normalLen = (float) sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (normalLen <= EPSILON) {
            out.set(0f, 0f, 0f);
            return;
        }
        float invLen = 1f / normalLen;
        out.set(normalX * invLen, normalY * invLen, normalZ * invLen);
    }

    private static Vector3D _getNormal(Vector3D point1, Vector3D point2, Vector3D point3) {
        Vector3D edge1 = sub(point2, point1);
        Vector3D edge2 = sub(point3, point1);
        float normalX = edge1.y * edge2.z - edge1.z * edge2.y;
        float normalY = edge1.z * edge2.x - edge1.x * edge2.z;
        float normalZ = edge1.x * edge2.y - edge1.y * edge2.x;
        Vector3D norm = new Vector3D(normalX, normalY, normalZ);
        double d = norm.sqlen();
        return div(norm, (float) sqrt(d));
    }

    public static Vector3D rotZ(Vector3D u, Vector3D o, float ang){
        Vector3D u2 = sub(u,o);
        float x2 = (u2.x*cos(ang) - u2.y*sin(ang));
        float y2 = (u2.x*sin(ang) + u2.y*cos(ang));
        Vector3D u3 = new Vector3D(x2,y2,u2.z);
        return add(u3,o);
    }

    public static Vector3D rotZ(Vector3D u, float ang){
        float x2 = (u.x*cos(ang) - u.y*sin(ang));
        float y2 = (u.x*sin(ang) + u.y*cos(ang));
        return V3(x2,y2, u.z);
    }
    public static void rotZ(Vector3D[] verts, Vector3D o, float ang){
        for(int i=0;i<verts.length;++i){
            verts[i] = rotZ(verts[i],o,ang);
        }
    }
    public static Vector3D rotX(Vector3D u, Vector3D o, float ang){
        Vector3D u2 = sub(u,o);
        float y2 = u2.y*cos(ang) - u2.z*sin(ang);
        float z2 = u2.y*sin(ang) + u2.z*cos(ang);
        Vector3D u3 = new Vector3D(u2.x,y2,z2);
        return add(u3,o);
    }
    public static Vector3D rotX(Vector3D u, float ang){
        float y2 = u.y*cos(ang) - u.z*sin(ang);
        float z2 = u.y*sin(ang) + u.z*cos(ang);
        return V3(u.x,y2,z2);
    }
    public static Vector3D rotY(Vector3D u, Vector3D o, float ang){
        Vector3D u2 = sub(u,o);
        float x2 = u2.x*cos(ang) - u2.z*sin(ang);
        float z2 = u2.x*sin(ang) + u2.z*cos(ang);
        Vector3D u3 = new Vector3D(x2,u2.y,z2);
        return add(u3,o);
    }
    public static Vector3D rotY(Vector3D u, float ang){
        float x2 = u.x*cos(ang) - u.z*sin(ang);
        float z2 = u.x*sin(ang) + u.z*cos(ang);
        return V3(x2, u.y,z2);
    }

    public static Vector3D getCentroid(Vector3D ... verts){
        Vector3D res = V3(0.0f,0.0f,0.0f);
        for(Vector3D v : verts){
            res = res.add(v);
        }
        return res.div(verts.length);
    }

    public static Vector3D rotateAroundAxis(
            Vector3D axisPoint,        // the point about which to rotate
            Vector3D axisDirection,    // direction vector of the rotation axis (need not be unit length)
            Vector3D pointToRotate,    // the point you want to rotate
            float angleRadians         // rotation angle in radians
    ) {
        float axisPx = axisPoint.x;
        float axisPy = axisPoint.y;
        float axisPz = axisPoint.z;
        float axisDx = axisDirection.x;
        float axisDy = axisDirection.y;
        float axisDz = axisDirection.z;
        float pointX = pointToRotate.x;
        float pointY = pointToRotate.y;
        float pointZ = pointToRotate.z;

        float translatedX = pointX - axisPx;
        float translatedY = pointY - axisPy;
        float translatedZ = pointZ - axisPz;

        float axisLen = (float) Math.sqrt(axisDx * axisDx + axisDy * axisDy + axisDz * axisDz);
        float kx = axisDx / axisLen;
        float ky = axisDy / axisLen;
        float kz = axisDz / axisLen;

        float cosTheta = (float) Math.cos(angleRadians);
        float sinTheta = (float) Math.sin(angleRadians);

        float crossX = ky * translatedZ - kz * translatedY;
        float crossY = kz * translatedX - kx * translatedZ;
        float crossZ = kx * translatedY - ky * translatedX;
        float dot = kx * translatedX + ky * translatedY + kz * translatedZ;
        float oneMinusCos = 1f - cosTheta;

        float rotatedX = translatedX * cosTheta + crossX * sinTheta + kx * dot * oneMinusCos;
        float rotatedY = translatedY * cosTheta + crossY * sinTheta + ky * dot * oneMinusCos;
        float rotatedZ = translatedZ * cosTheta + crossZ * sinTheta + kz * dot * oneMinusCos;

        return V3(rotatedX + axisPx, rotatedY + axisPy, rotatedZ + axisPz);
    }

    public static void rotateAroundAxisTo(
            MutableVec3 out,
            Vector3D axisPoint,
            Vector3D axisDirection,
            Vector3D pointToRotate,
            float angleRadians
    ) {
        rotateAroundAxisTo(
                out,
                axisPoint.x, axisPoint.y, axisPoint.z,
                axisDirection.x, axisDirection.y, axisDirection.z,
                pointToRotate.x, pointToRotate.y, pointToRotate.z,
                angleRadians
        );
    }

    public static void rotateAroundAxisTo(
            MutableVec3 out,
            float axisPx, float axisPy, float axisPz,
            float axisDx, float axisDy, float axisDz,
            float pointX, float pointY, float pointZ,
            float angleRadians
    ) {
        float translatedX = pointX - axisPx;
        float translatedY = pointY - axisPy;
        float translatedZ = pointZ - axisPz;

        float axisLen = (float) Math.sqrt(axisDx * axisDx + axisDy * axisDy + axisDz * axisDz);
        float kx = axisDx / axisLen;
        float ky = axisDy / axisLen;
        float kz = axisDz / axisLen;

        float cosTheta = (float) Math.cos(angleRadians);
        float sinTheta = (float) Math.sin(angleRadians);

        float crossX = ky * translatedZ - kz * translatedY;
        float crossY = kz * translatedX - kx * translatedZ;
        float crossZ = kx * translatedY - ky * translatedX;
        float dot = kx * translatedX + ky * translatedY + kz * translatedZ;
        float oneMinusCos = 1f - cosTheta;

        out.set(
                translatedX * cosTheta + crossX * sinTheta + kx * dot * oneMinusCos + axisPx,
                translatedY * cosTheta + crossY * sinTheta + ky * dot * oneMinusCos + axisPy,
                translatedZ * cosTheta + crossZ * sinTheta + kz * dot * oneMinusCos + axisPz
        );
    }

    public static Vector3D rotateAroundTwoPoints(
            Vector3D axisStart,      // first point on the rotation axis
            Vector3D axisEnd,        // second point on the rotation axis
            Vector3D pointToRotate,  // the point you want to rotate
            float angleRadians       // rotation angle in radians
    ) {
        // build axis direction from axisStart → axisEnd
        Vector3D axisDir = axisEnd.sub(axisStart);
        return rotateAroundAxis(axisStart, axisDir, pointToRotate, angleRadians);
    }

    /**
     * Möller–Trumbore ray/triangle intersection.
     *
     * @param rayOrigin      start point of the ray
     * @param rayDirection   direction vector of the ray (doesn't need to be normalized)
     * @param vertex0        first vertex of the triangle
     * @param vertex1        second vertex of the triangle
     * @param vertex2        third vertex of the triangle
     * @return distance t along the ray (so intersectionPoint = rayOrigin + rayDirection * t),
     *         or Float.POSITIVE_INFINITY if there is no intersection or the triangle is edge-/back-facing
     */
    public static float rayTriangleDistance(
            Vector3D rayOrigin,
            Vector3D rayDirection,
            Vector3D vertex0,
            Vector3D vertex1,
            Vector3D vertex2
    ) {
        return rayTriangleDistance(
                rayOrigin.x, rayOrigin.y, rayOrigin.z,
                rayDirection.x, rayDirection.y, rayDirection.z,
                vertex0.x, vertex0.y, vertex0.z,
                vertex1.x, vertex1.y, vertex1.z,
                vertex2.x, vertex2.y, vertex2.z
        );
    }

    public static float rayTriangleDistance(
            float rayOriginX, float rayOriginY, float rayOriginZ,
            float rayDirectionX, float rayDirectionY, float rayDirectionZ,
            float vertex0X, float vertex0Y, float vertex0Z,
            float vertex1X, float vertex1Y, float vertex1Z,
            float vertex2X, float vertex2Y, float vertex2Z
    ) {
        float edge1X = vertex1X - vertex0X;
        float edge1Y = vertex1Y - vertex0Y;
        float edge1Z = vertex1Z - vertex0Z;
        float edge2X = vertex2X - vertex0X;
        float edge2Y = vertex2Y - vertex0Y;
        float edge2Z = vertex2Z - vertex0Z;

        float pVecX = rayDirectionY * edge2Z - rayDirectionZ * edge2Y;
        float pVecY = rayDirectionZ * edge2X - rayDirectionX * edge2Z;
        float pVecZ = rayDirectionX * edge2Y - rayDirectionY * edge2X;
        float det = edge1X * pVecX + edge1Y * pVecY + edge1Z * pVecZ;

        if (det > -EPSILON && det < EPSILON) {
            return Float.POSITIVE_INFINITY;
        }
        float invDet = 1.0f / det;

        float tVecX = rayOriginX - vertex0X;
        float tVecY = rayOriginY - vertex0Y;
        float tVecZ = rayOriginZ - vertex0Z;
        float u = (tVecX * pVecX + tVecY * pVecY + tVecZ * pVecZ) * invDet;
        if (u < 0.0f || u > 1.0f) {
            return Float.POSITIVE_INFINITY;
        }

        float qVecX = tVecY * edge1Z - tVecZ * edge1Y;
        float qVecY = tVecZ * edge1X - tVecX * edge1Z;
        float qVecZ = tVecX * edge1Y - tVecY * edge1X;
        float v = (rayDirectionX * qVecX + rayDirectionY * qVecY + rayDirectionZ * qVecZ) * invDet;
        if (v < 0.0f || u + v > 1.0f) {
            return Float.POSITIVE_INFINITY;
        }

        float t = (edge2X * qVecX + edge2Y * qVecY + edge2Z * qVecZ) * invDet;
        return (t > EPSILON) ? t : Float.POSITIVE_INFINITY;
    }

}
