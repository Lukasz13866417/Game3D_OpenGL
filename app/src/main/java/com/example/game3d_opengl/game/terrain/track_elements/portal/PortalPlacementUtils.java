package com.example.game3d_opengl.game.terrain.track_elements.portal;

import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

final class PortalPlacementUtils {
    private PortalPlacementUtils() {}

    static Vector3D computeHorizontalLookDirection(
            Vector3D fieldNearLeft,
            Vector3D fieldNearRight,
            Vector3D fieldFarLeft,
            Vector3D fieldFarRight
    ) {
        Vector3D nearEdge = fieldNearRight.sub(fieldNearLeft);
        Vector3D edgeH = new Vector3D(nearEdge.x, 0f, nearEdge.z);

        Vector3D nearMid = fieldNearLeft.add(fieldNearRight).div(2f);
        Vector3D farMid = fieldFarLeft.add(fieldFarRight).div(2f);
        Vector3D toFar = farMid.sub(nearMid);
        Vector3D toFarH = new Vector3D(toFar.x, 0f, toFar.z);

        // One horizontal perpendicular to near edge.
        Vector3D look = new Vector3D(-edgeH.z, 0f, edgeH.x);
        if (look.sqlen() < 1e-8f) {
            look = toFarH;
        }
        if (look.sqlen() < 1e-8f) {
            look = new Vector3D(0f, 0f, -1f);
        }

        // Pick the sign that points toward the far side of the tile.
        if (toFarH.sqlen() > 1e-8f && look.dotProduct(toFarH) < 0f) {
            look = look.mult(-1f);
        }

        Vector3D n = look.withLen(1f);
        if (n.sqlen() < 1e-8f) {
            return new Vector3D(0f, 0f, -1f);
        }
        return n;
    }
}

