package com.example.game3d_opengl.game.terrain.track_elements.portal;

import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

final class PortalPlacementUtils {
    private PortalPlacementUtils() {}

    static void computeHorizontalLookDirection(
            float nearLeftX, float nearLeftY, float nearLeftZ,
            float nearRightX, float nearRightY, float nearRightZ,
            float farLeftX, float farLeftY, float farLeftZ,
            float farRightX, float farRightY, float farRightZ,
            float[] outDirection
    ) {
        float edgeHX = nearRightX - nearLeftX;
        float edgeHZ = nearRightZ - nearLeftZ;

        float nearMidX = 0.5f * (nearLeftX + nearRightX);
        float nearMidZ = 0.5f * (nearLeftZ + nearRightZ);
        float farMidX = 0.5f * (farLeftX + farRightX);
        float farMidZ = 0.5f * (farLeftZ + farRightZ);
        float toFarHX = farMidX - nearMidX;
        float toFarHZ = farMidZ - nearMidZ;

        float lookX = -edgeHZ;
        float lookY = 0f;
        float lookZ = edgeHX;
        float lookLenSq = lookX * lookX + lookZ * lookZ;
        if (lookLenSq < 1e-8f) {
            lookX = toFarHX;
            lookZ = toFarHZ;
            lookLenSq = lookX * lookX + lookZ * lookZ;
        }
        if (lookLenSq < 1e-8f) {
            outDirection[0] = 0f;
            outDirection[1] = 0f;
            outDirection[2] = -1f;
            return;
        }

        if ((toFarHX * toFarHX + toFarHZ * toFarHZ) > 1e-8f
                && lookX * toFarHX + lookZ * toFarHZ < 0f) {
            lookX = -lookX;
            lookZ = -lookZ;
        }

        float invLookLen = 1f / (float) Math.sqrt(lookLenSq);
        outDirection[0] = lookX * invLookLen;
        outDirection[1] = lookY;
        outDirection[2] = lookZ * invLookLen;
    }

    static Vector3D computeHorizontalLookDirection(
            Vector3D fieldNearLeft,
            Vector3D fieldNearRight,
            Vector3D fieldFarLeft,
            Vector3D fieldFarRight
    ) {
        float[] direction = new float[3];
        computeHorizontalLookDirection(
                fieldNearLeft.x, fieldNearLeft.y, fieldNearLeft.z,
                fieldNearRight.x, fieldNearRight.y, fieldNearRight.z,
                fieldFarLeft.x, fieldFarLeft.y, fieldFarLeft.z,
                fieldFarRight.x, fieldFarRight.y, fieldFarRight.z,
                direction
        );
        return new Vector3D(direction[0], direction[1], direction[2]);
    }
}

