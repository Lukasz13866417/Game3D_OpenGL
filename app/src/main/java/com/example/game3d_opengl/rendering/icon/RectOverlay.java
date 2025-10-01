package com.example.game3d_opengl.rendering.icon;

import android.opengl.Matrix;

import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.mesh.MVPDrawArgs;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.rect.Rect;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.rendering.wireframe.Mesh3DWireframe;

/**
 * Simple screen-space rectangle outline, drawn in clip space using Mesh3DWireframe.
 * The rectangle is positioned via a clip-space Rect; Z is pinned to 0 via the MVP we build.
 */
public final class RectOverlay implements GPUResourceOwner {

    private final Mesh3DWireframe edgeMesh;
    private final MVPDrawArgs drawArgs;

    private RectOverlay(Builder b) {
        this.edgeMesh = b.wire;
        this.drawArgs = new MVPDrawArgs(b.toClipMvp);
    }

    public void draw() {
        edgeMesh.draw(drawArgs);
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        edgeMesh.reloadGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {
        edgeMesh.cleanupGPUResourcesRecursivelyOnContextLoss();
    }

    public static final class Builder {
        private FColor edgeColor;
        private float edgePixels = 2f;
        private Rect placementRect;

        private Mesh3DWireframe wire;
        private float[] toClipMvp;

        public Builder edgeColor(FColor c) { this.edgeColor = c; return this; }
        public Builder edgePixels(float px) { this.edgePixels = px; return this; }
        public Builder placementRect(Rect r){ this.placementRect = r; return this; }

        private static float[] buildPlacementMatrix(Rect rect,
                                                    float minX, float maxX,
                                                    float minY, float maxY) {
            float wObj = Math.max(1e-6f, (maxX - minX));
            float hObj = Math.max(1e-6f, (maxY - minY));

            float sx = rect.w / wObj;
            float sy = rect.h / hObj;
            float tx = rect.x1 - sx * minX;
            float ty = rect.y1 - sy * minY;

            float[] m = new float[16];
            Matrix.setIdentityM(m, 0);
            Matrix.translateM(m, 0, tx, ty, 0f);
            Matrix.scaleM(m, 0, sx, sy, 0f);
            return m;
        }

        public RectOverlay build(){
            if (edgeColor == null) throw new IllegalStateException("edgeColor is null");
            if (placementRect == null) throw new IllegalStateException("placementRect is null");

            // Unit square in XY plane, z=0: (0,0)-(1,0)-(1,1)-(0,1)
            Vector3D[] verts = new Vector3D[]{
                    new Vector3D(0f, 0f, 0f),
                    new Vector3D(1f, 0f, 0f),
                    new Vector3D(1f, 1f, 0f),
                    new Vector3D(0f, 1f, 0f)
            };
            int[][] face = new int[][]{ new int[]{0,1,2,3} };

            this.wire = new Mesh3DWireframe.Builder()
                    .verts(verts)
                    .faces(face)
                    .edgeColor(edgeColor)
                    .pixelWidth(edgePixels)
                    .buildObject();

            this.toClipMvp = buildPlacementMatrix(placementRect, 0f, 1f, 0f, 1f);
            return new RectOverlay(this);
        }
    }
}


