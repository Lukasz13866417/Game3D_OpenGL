package com.example.game3d_opengl.rendering.util3d;

import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.add;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.div;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.dotProduct;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.sub;
import static java.lang.Math.sqrt;

import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;


public class GameMath {

    public static final float EPSILON = 1e-6f;
    public static final float PI = 3.1415926535f;
    public static final float INF = Float.POSITIVE_INFINITY, NINF = Float.NEGATIVE_INFINITY;

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
        // translate so axisPoint is at the origin
        Vector3D translated = pointToRotate.sub(axisPoint);

        // unit‐length axis vector
        Vector3D k = axisDirection.normalized();

        float cosθ = (float) Math.cos(angleRadians);
        float sinθ = (float) Math.sin(angleRadians);

        // Rodrigues' rotation formula
        Vector3D term1 = translated.mult(cosθ);
        Vector3D term2 = k.crossProduct(translated).mult(sinθ);
        Vector3D term3 = k.mult(k.dotProduct(translated) * (1 - cosθ));

        Vector3D rotated = term1.add(term2).add(term3);

        // translate back
        return rotated.add(axisPoint);
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

        // triangle edges
        Vector3D edge1 = vertex1.sub(vertex0);
        Vector3D edge2 = vertex2.sub(vertex0);

        // begin calculating determinant
        Vector3D pVec = rayDirection.crossProduct(edge2);
        float det = edge1.dotProduct(pVec);

        // if det is near zero, no intersection (ray parallel or back-facing)
        if (det > -EPSILON && det < EPSILON) {
            return Float.POSITIVE_INFINITY;
        }
        float invDet = 1.0f / det;

        // calculate u parameter and test bounds
        Vector3D tVec = rayOrigin.sub(vertex0);
        float u = tVec.dotProduct(pVec) * invDet;
        if (u < 0.0f || u > 1.0f) {
            return Float.POSITIVE_INFINITY;
        }

        // calculate v parameter and test bounds
        Vector3D qVec = tVec.crossProduct(edge1);
        float v = rayDirection.dotProduct(qVec) * invDet;
        if (v < 0.0f || u + v > 1.0f) {
            return Float.POSITIVE_INFINITY;
        }

        // calculate t (distance along the ray)
        float t = edge2.dotProduct(qVec) * invDet;
        return (t > EPSILON) ? t : Float.POSITIVE_INFINITY;
    }

}
