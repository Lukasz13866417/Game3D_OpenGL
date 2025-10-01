package com.example.game3d_opengl.rendering.icon;

import android.opengl.Matrix;

import com.example.game3d_opengl.rendering.infill.Mesh3DInfill;
import com.example.game3d_opengl.rendering.mesh.MVPDrawArgs;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.rect.Rect;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.rendering.wireframe.Mesh3DWireframe;
import com.example.game3d_opengl.rendering.Camera;

/**
 * Icon that rotates about the Y axis in clip space but never escapes the given placement Rect.
 * Builder computes a worst-case axis-aligned bounding rectangle (in model XY) across all Y-rotations:
 *   - X extent: for each vertex, the maximum possible |x'| over θ is sqrt(x^2 + z^2). Take rMax = max over verts.
 *     This yields symmetric X bounds [-rMax, +rMax].
 *   - Y extent: rotation about Y leaves y unchanged, so bounds are [minY, maxY] from the original mesh.
 * We map that rectangle once to the placement Rect via a fixed MVP. At draw, we multiply by a Y-rotation.
 */
public class SpinningIcon extends Icon {

    private final float[] baseToClipMvp; // fixed scale+translate mapping worst-case square to Rect
    private float angleRad = 0f;         // manual rotation angle in radians (used if spinRate==0)
    private final float spinRateRadPerSec; // automatic spin rate; 0 => disabled
    private final float initialAngleRad;
    private final long startNano;

    protected SpinningIcon(SpinningBuilder builder, float[] baseToClipMvp) {
        super(builder);
        this.baseToClipMvp = baseToClipMvp;
        this.spinRateRadPerSec = builder.spinRateRadPerSec;
        this.initialAngleRad = builder.initialAngleRad;
        this.startNano = System.nanoTime();
    }

    public void setAngleRadians(float radians){ this.angleRad = radians; }
    public void setAngleDegrees(float degrees){ this.angleRad = (float)(degrees * Math.PI / 180.0); }

    @Override
    public void draw() {
        final float[] rot = new float[16];
        final float[] mvp = new float[16];
        float a = spinRateRadPerSec != 0f
                ? (initialAngleRad + (float)((System.nanoTime() - startNano) * 1e-9) * spinRateRadPerSec)
                : angleRad;
        // wrap to avoid precision loss
        a = (float)(a % (2*Math.PI));
        Matrix.setRotateM(rot, 0, (float)(a * 180.0 / Math.PI), 0f, 1f, 0f); // rotate around Y
        Matrix.multiplyMM(mvp, 0, baseToClipMvp, 0, rot, 0); // M = M_place * R_y
        drawWithArgs(args -> args.setMvp(mvp));
    }

    // Builder that computes worst-case bounding square and the fixed MVP
    public static class SpinningBuilder extends Icon.Builder<SpinningBuilder, SpinningIcon> {
        private float[] computedMvp;
        private Vector3D[] srcVerts;
        private int[][] srcFaces;
        private Rect srcRect;
        private float depthZ = Float.NaN; // view-space Z offset to keep z' > 0
        private float near = 3f, far = 160f, top = 1f; // matches Camera.setProjectionAsScreen
        private float marginPxX = 2f, marginPxY = 2f; // shrink target rect by these pixel margins per side
        private float spinRateRadPerSec = 0f;
        private float initialAngleRad = 0f;

        private static float radiusXZ(Vector3D v){ return (float)Math.hypot(v.x, v.z); }

        @Override
        public SpinningBuilder verts(Vector3D[] v){ this.srcVerts = v; return super.verts(v); }
        @Override
        public SpinningBuilder faces(int[][] f){ this.srcFaces = f; return super.faces(f); }
        @Override
        public SpinningBuilder fillColor(FColor c){ return  super.fillColor(c); }
        @Override
        public SpinningBuilder edgeColor(FColor c){ return  super.edgeColor(c); }
        @Override
        public SpinningBuilder edgePixels(float px){ return  super.edgePixels(px); }
        @Override
        public SpinningBuilder placementRect(Rect r){ this.srcRect = r; return  super.placementRect(r); }

        public SpinningBuilder depthZ(float z){ this.depthZ = z; return this; }
        public SpinningBuilder perspective(float near, float far, float top){ this.near = near; this.far = far; this.top = top; return this; }
        public SpinningBuilder marginPixels(float x, float y){ this.marginPxX = x; this.marginPxY = y; return this; }
        public SpinningBuilder spinRateDegPerSec(float degPerSec){ this.spinRateRadPerSec = (float)(degPerSec * Math.PI / 180.0); return this; }
        public SpinningBuilder spinRateRadPerSec(float radPerSec){ this.spinRateRadPerSec = radPerSec; return this; }
        public SpinningBuilder initialAngleDeg(float deg){ this.initialAngleRad = (float)(deg * Math.PI / 180.0); return this; }
        public SpinningBuilder initialAngleRad(float rad){ this.initialAngleRad = rad; return this; }

        @Override
        protected void checkValid(){
            super.checkValid();
            // Worst-case X across Y-rotation (perspective), and exact Y bounds
            float rMax = 0f;
            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            for (Vector3D v : this.srcVerts) {
                float r = radiusXZ(v);
                if (r > rMax) rMax = r;
                if (v.y < minY) minY = v.y;
                if (v.y > maxY) maxY = v.y;
            }
            if (rMax < 1e-6f) rMax = 1e-6f;

            // Choose a safe positive depth magnitude. We'll place geometry at z_eye = -zAbs.
            float zAbs = Float.isNaN(depthZ) ? Math.max(near * 4f, rMax * 3.0f + near) : Math.abs(depthZ);

            // Projection params
            float aspect = Camera.SCREEN_HEIGHT == 0 ? 1f : ((float)Camera.SCREEN_WIDTH / (float)Camera.SCREEN_HEIGHT);
            float cotHalfFovY = near / top; // because top = near * tan(fovY/2) => cot = near/top
            float A = cotHalfFovY / aspect;  // for X
            float k = cotHalfFovY;           // for Y

            // Desired NDC half extents from placement rect
            float cx = (srcRect.x1 + srcRect.x2) * 0.5f;
            float cy = (srcRect.y1 + srcRect.y2) * 0.5f;
            float hx = srcRect.w * 0.5f;
            float hy = srcRect.h * 0.5f;

            // Apply pixel margins by converting pixels -> NDC
            float pxToNdcX = Camera.SCREEN_WIDTH  > 0 ? (2f / Camera.SCREEN_WIDTH)  : 0f;
            float pxToNdcY = Camera.SCREEN_HEIGHT > 0 ? (2f / Camera.SCREEN_HEIGHT) : 0f;
            float hxEff = Math.max(1e-5f, hx - marginPxX * pxToNdcX);
            float hyEff = Math.max(1e-5f, hy - marginPxY * pxToNdcY);

            // Compute non-uniform scales: sx (applies to X and Z), and sy (applies to Y)
            float sx = Float.POSITIVE_INFINITY;
            for (Vector3D v : this.srcVerts) {
                float r = radiusXZ(v);
                if (r > 1e-8f) {
                    float bound = (hxEff * zAbs) / (r * (A + hxEff));
                    if (bound < sx) sx = bound;
                }
            }
            if (!(sx > 0 && Float.isFinite(sx))) sx = 0.1f;
            // Ensure near-plane margin for all rotations
            if (zAbs - sx * rMax <= near) {
                sx = (zAbs - near - 1f) / Math.max(rMax, 1e-6f);
                if (sx <= 0f) sx = 0.05f;
            }

            float sy = Float.POSITIVE_INFINITY;
            for (Vector3D v : this.srcVerts) {
                float r = radiusXZ(v);
                float yAbs = Math.abs(v.y);
                if (yAbs <= 1e-8f) continue; // no constraint from a zero-height vertex
                float zMin = zAbs - sx * r; // minimum |z_eye| across rotation with XZ scale sx
                if (zMin <= 1e-6f) continue; // degenerate; guarded by near-plane condition above
                // Perspective-correct NDC bound: k*sy*|y| / zMin <= hyEff  => sy <= hyEff*zMin / (k*|y|)
                float bound = (hyEff * zMin) / (k * yAbs);
                if (bound < sy) sy = bound;
            }
            if (!(sy > 0 && Float.isFinite(sy))) sy = sx; // fallback

            // Build M_view = T(Xc,Yc,-zAbs) * S(sMax)
            float[] M = new float[16];
            Matrix.setIdentityM(M, 0);
            // Center translation in view-space so NDC center approx (cx,cy): Xc = cx * zAbs / A, Yc = cy * zAbs / k
            float tx_view = (A > 1e-6f) ? (cx * zAbs / A) : 0f;
            float ty_view = (k > 1e-6f) ? (cy * zAbs / k) : 0f;
            Matrix.translateM(M, 0, tx_view, ty_view, -zAbs);
            Matrix.scaleM(M, 0, sx, sy, sx); // scale X and Z by sx (rotation in XZ), Y by sy

            // Build P
            float ratio = aspect;
            float left = -ratio, right = ratio, bottom = -top, topv = top;
            float[] P = new float[16];
            Matrix.frustumM(P, 0, left, right, bottom, topv, near, far);

            // Final MVP
            float[] out = new float[16];
            Matrix.multiplyMM(out, 0, P, 0, M, 0);
            this.computedMvp = out;
        }

        // Allow subclasses (e.g., PotionIconBuilder) to access the computed MVP
        protected float[] getComputedMvp(){
            return computedMvp;
        }

        @Override
        protected SpinningIcon createWhenReady() {
            return new SpinningIcon(this, computedMvp);
        }

        @Override
        protected SpinningBuilder self() { return this; }
    }
}


