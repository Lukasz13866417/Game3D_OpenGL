package com.example.game3d_opengl.rendering.icon;

import static com.example.game3d_opengl.game.util.GameMath.EPSILON;

import android.opengl.Matrix;

import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.infill.Mesh3DInfill;
import com.example.game3d_opengl.rendering.mesh.MVPDrawArgs;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.rect.Rect;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.rendering.wireframe.Mesh3DWireframe;

/**
 * Screen-space icon composed of an infill mesh + an outline (wireframe) mesh.
 * The icon is positioned by a clip-space rectangle (placementRect).
 * -  We compute an MVP that maps the object's local XY AABB exactly onto placementRect
 *    using only scale + translate and pass it directly to the meshes (camera VP is ignored).
 * -  Z is pinned to 0 in clip space (via Sz = 0, Tz = 0) so the icon draws as an overlay;
 *    the wireframe shader already applies a small NDC depth bias for edges.
 */
public class Icon implements GPUResourceOwner {

    private final Mesh3DInfill fillMesh;
    private final Mesh3DWireframe edgeMesh;

    // Precomputed model->clip transform for this icon (maps local AABB -> placementRect)
    private final MVPDrawArgs drawArgs;

    protected Icon(Builder<?,?> builder) {
        this.fillMesh = builder.fill;
        this.edgeMesh = builder.wire;
        this.drawArgs = new MVPDrawArgs(builder.toClipMvp);
    }


    public void draw() {
        // Draw in screen space: pass our precomputed MVP directly (ignores incoming model/vp).
        fillMesh.draw(drawArgs);
        edgeMesh.draw(drawArgs);
    }

    /**
     * For subclasses that need to tweak draw arguments per-frame (e.g., spinning icons).
     * The mutator may update fields like the MVP inside the shared drawArgs before we draw.
     */
    @FunctionalInterface
    protected interface ArgsMutator { void mutate(MVPDrawArgs args); }

    protected final void drawWithArgs(ArgsMutator mutator){
        mutator.mutate(this.drawArgs);
        fillMesh.draw(this.drawArgs);
        edgeMesh.draw(this.drawArgs);
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        fillMesh.reloadGPUResourcesRecursivelyOnContextLoss();
        edgeMesh.reloadGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {
        fillMesh.cleanupGPUResourcesRecursivelyOnContextLoss();
        edgeMesh.cleanupGPUResourcesRecursivelyOnContextLoss();
    }

    // ---- helpers ----

    private static float[] computeXYAabb(Vector3D[] verts) {
        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (Vector3D v : verts) {
            float x = (float) v.x;
            float y = (float) v.y;
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        return new float[]{minX, maxX, minY, maxY};
    }

    /**
     * Create a matrix M such that:
     *   [minX,maxX] × [minY,maxY]  --(M)-->  [rect.x1, rect.x2] × [rect.y1, rect.y2]
     * Using only scale and translate in XY. Z is set to constant 0 in clip space.
     */
    private static float[] buildPlacementMatrix(Rect rect,
                                                float minX, float maxX,
                                                float minY, float maxY) {

        float wObj = Math.max(EPSILON, (maxX - minX));
        float hObj = Math.max(EPSILON, (maxY - minY));

        float sx = rect.w / wObj;
        float sy = rect.h / hObj;
        float tx = rect.x1 - sx * minX;
        float ty = rect.y1 - sy * minY;

        float[] m = new float[16];
        Matrix.setIdentityM(m, 0);

        // We want M = T * S so that x' = sx*x + tx (translation unaffected by the scale).
        // Android's Matrix.* post-multiplies, so apply translate first, then scale.
        Matrix.translateM(m, 0, tx, ty, 0f);
        Matrix.scaleM(m, 0, sx, sy, 0f); // Sz=0 pins Z to 0 in clip space

        return m;
    }

    // ---- builder ----

    public static abstract class Builder<B extends Builder<B,T>, T extends Icon> {
        private Vector3D[] verts;
        private int[][] faces;
        private FColor fillColor = null;
        private FColor edgeColor = null;
        private float edgePixels = 2f;
        private Rect placementRect;

        private Mesh3DInfill fill = null;
        private Mesh3DWireframe wire = null;
        private float[] toClipMvp;

        protected void checkValid(){
            assert fillColor != null;
            assert edgeColor != null;
            assert verts != null;
            assert faces != null;
            assert placementRect != null;
        }

        public B verts(Vector3D[] v){ this.verts = v; return self(); }
        public B faces(int[][] f){ this.faces = f; return self(); }
        public B fillColor(FColor c){ this.fillColor = c; return self(); }
        public B edgeColor(FColor c){ this.edgeColor = c; return self(); }
        public B edgePixels(float px){ this.edgePixels = px; return self(); }
        public B placementRect(Rect r){ this.placementRect = r; return self(); }

        public Icon build(){

            checkValid();

            this.fill = new Mesh3DInfill.Builder()
                    .verts(verts)
                    .faces(faces)
                    .fillColor(fillColor)
                    .buildObject();

            this.wire = new Mesh3DWireframe.Builder()
                    .verts(verts)
                    .faces(faces)
                    .edgeColor(edgeColor)
                    .pixelWidth(edgePixels)
                    .buildObject();

            float[] aabb = computeXYAabb(verts);
            this.toClipMvp = buildPlacementMatrix(placementRect, aabb[0], aabb[1], aabb[2], aabb[3]);

            return createWhenReady();
        }

        protected abstract T createWhenReady();

        protected abstract B self();

    }
}