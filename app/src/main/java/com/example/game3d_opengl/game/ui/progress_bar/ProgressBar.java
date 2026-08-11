package com.example.game3d_opengl.game.progress_bar;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.ScreenInfo;
import com.example.game3d_opengl.rendering.mesh.MVPDrawArgs;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.rect.Rect;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.rendering.wireframe.Mesh3DWireframe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ProgressBar implements GPUResourceOwner {
    private float minVal;
    private float maxVal;
    private final float widthPx;
    private final float heightPx;
    private final float xPx;
    private final float yPx;
    private final float dentWidthPx;
    private final float dentDepthPx;
    private final float edgePx;
    private FColor fillColor;
    private final FColor outlineColor;
    private float[] milestones;
    private final Rect outlineRectClip;

    private ProgressBarFillMesh3D fillMesh;
    private ProgressBarFillDrawArgs fillDrawArgs;
    private Mesh3DWireframe outlineMesh;
    private MVPDrawArgs outlineDrawArgs;

    private ProgressBar(Builder b) {
        this.minVal = b.minVal;
        this.maxVal = b.maxVal;
        this.widthPx = b.widthPx;
        this.heightPx = b.heightPx;
        this.xPx = b.xPx;
        this.yPx = b.yPx;
        this.dentWidthPx = b.dentWidthPx;
        this.dentDepthPx = b.dentDepthPx;
        this.edgePx = b.edgePx;
        this.fillColor = b.fillColor;
        this.outlineColor = b.outlineColor;
        this.milestones = b.milestones != null ? Arrays.copyOf(b.milestones, b.milestones.length) : new float[0];
        this.outlineRectClip = b.outlineRectClip;
        rebuildMeshes();
    }

    public void draw(float currProgress) {
        float ratio = 0f;
        float denom = maxVal - minVal;
        if (denom > 1e-6f) {
            ratio = (currProgress - minVal) / denom;
        }
        ratio = clamp01(ratio);

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        fillDrawArgs.progress = ratio;
        fillMesh.draw(fillDrawArgs);

        outlineMesh.draw(outlineDrawArgs);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    public void setRange(float minVal, float maxVal) {
        if (maxVal <= minVal) return;
        if (this.minVal == minVal && this.maxVal == maxVal) return;
        this.minVal = minVal;
        this.maxVal = maxVal;
        rebuildMeshes();
    }

    public void setMilestones(float[] milestones) {
        setMilestones(milestones, milestones != null ? milestones.length : 0);
    }

    public void setMilestones(float[] source, int count) {
        int safeCount = source != null
                ? Math.max(0, Math.min(count, source.length))
                : 0;
        if (sameValues(this.milestones, source, safeCount)) return;
        this.milestones = safeCount > 0
                ? Arrays.copyOf(source, safeCount)
                : new float[0];
        rebuildMeshes();
    }

    /** Changes only the fill uniform; no geometry or GPU buffer is rebuilt. */
    public void setFillColor(FColor color) {
        if (color == null) {
            return;
        }
        fillColor = color;
        if (fillMesh != null) {
            fillMesh.setColor(color);
        }
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        if (fillMesh != null) fillMesh.reloadGPUResourcesRecursivelyOnContextLoss();
        if (outlineMesh != null) outlineMesh.reloadGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        if (fillMesh != null) fillMesh.cleanupGPUResourcesRecursively();
        if (outlineMesh != null) outlineMesh.cleanupGPUResourcesRecursively();
    }

    private void rebuildMeshes() {
        Builder.FillMeshData fillData = buildFillMeshData();
        Vector3D[] outlineVerts = buildOutlineVerts();
        int[][] outlineFace = new int[][]{ buildSequentialFace(outlineVerts.length) };

        if (fillMesh != null
                && outlineMesh != null
                && fillMesh.canUpdateGeometry(fillData.verts)
                && outlineMesh.canUpdateGeometry(outlineVerts, outlineFace)) {
            fillMesh.updateGeometry(fillData.verts);
            outlineMesh.updateGeometry(outlineVerts, outlineFace);
            return;
        }

        if (fillMesh != null) fillMesh.cleanupGPUResourcesRecursively();
        if (outlineMesh != null) outlineMesh.cleanupGPUResourcesRecursively();

        fillMesh = new ProgressBarFillMesh3D.Builder()
                .verts(fillData.verts)
                .faces(fillData.faces)
                .color(fillColor)
                .buildObject();
        fillDrawArgs = new ProgressBarFillDrawArgs();
        fillDrawArgs.vp = buildPlacementMatrix(outlineRectClip, 0f, 1f, 0f, 1f);

        outlineMesh = new Mesh3DWireframe.Builder()
                .verts(outlineVerts)
                .faces(outlineFace)
                .edgeColor(outlineColor)
                .pixelWidth(edgePx)
                // Avoid rounded segment-end overlap at notch corners.
                .capPixels(0f)
                .buildObject();
        outlineDrawArgs = new MVPDrawArgs(
                buildPlacementMatrix(outlineRectClip, 0f, 1f, 0f, 1f));
    }

    private static boolean sameValues(float[] current, float[] source, int count) {
        if (current == null || current.length != count) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            if (Float.compare(current[i], source[i]) != 0) {
                return false;
            }
        }
        return true;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

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

    private static Rect rectPxToClip(float xPx, float yPx, float widthPx, float heightPx) {
        float screenW = Math.max(1f, (float) ScreenInfo.getScreenW());
        float screenH = Math.max(1f, (float) ScreenInfo.getScreenH());

        float x1 = (xPx / screenW) * 2f - 1f;
        float x2 = ((xPx + widthPx) / screenW) * 2f - 1f;
        float yTop = 1f - (yPx / screenH) * 2f;
        float yBottom = 1f - ((yPx + heightPx) / screenH) * 2f;
        return new Rect(x1, yBottom, x2, yTop);
    }

    public static final class Builder {
        private static final float DEFAULT_EDGE_PX = 2f;
        private static final float DEFAULT_DENT_WIDTH_PX = 8f;
        private static final float DEFAULT_DENT_DEPTH_PX = 5f;

        private float minVal;
        private float maxVal;
        private float widthPx;
        private float heightPx;
        private float xPx = 0f;
        private float yPx = 0f;
        private FColor fillColor = FColor.CLR(0.2f, 0.7f, 0.2f, 1f);
        private FColor outlineColor = FColor.CLR(1f, 1f, 1f, 1f);
        private float edgePx = DEFAULT_EDGE_PX;
        private float dentWidthPx = DEFAULT_DENT_WIDTH_PX;
        private float dentDepthPx = DEFAULT_DENT_DEPTH_PX;
        private float[] milestones = new float[0];

        private Rect outlineRectClip;

        public Builder range(float minVal, float maxVal) {
            this.minVal = minVal;
            this.maxVal = maxVal;
            return this;
        }

        public Builder bboxPx(float xPx, float yPx, float widthPx, float heightPx) {
            this.xPx = xPx;
            this.yPx = yPx;
            this.widthPx = widthPx;
            this.heightPx = heightPx;
            return this;
        }

        public Builder fillColor(FColor color) {
            if (color != null) this.fillColor = color;
            return this;
        }

        public Builder outlineColor(FColor color) {
            if (color != null) this.outlineColor = color;
            return this;
        }

        public Builder outlinePixels(float px) {
            this.edgePx = px;
            return this;
        }

        public Builder dentSizePx(float widthPx, float depthPx) {
            this.dentWidthPx = widthPx;
            this.dentDepthPx = depthPx;
            return this;
        }

        public Builder milestones(float[] milestones) {
            this.milestones = milestones != null ? milestones : new float[0];
            return this;
        }

        public ProgressBar build() {
            if (maxVal <= minVal) {
                throw new IllegalStateException("ProgressBar range must have maxVal > minVal");
            }
            if (widthPx <= 0f || heightPx <= 0f) {
                throw new IllegalStateException("ProgressBar bbox must have positive size");
            }

            outlineRectClip = rectPxToClip(xPx, yPx, widthPx, heightPx);
            return new ProgressBar(this);
        }

        private static final class FillMeshData {
            final Vector3D[] verts;
            final int[][] faces;

            FillMeshData(Vector3D[] verts, int[][] faces) {
                this.verts = verts;
                this.faces = faces;
            }
        }

        private FillMeshData buildFillMeshData() {
            float depth = dentDepthPx / Math.max(1f, heightPx);
            depth = Math.min(0.45f, clamp01(depth));
            float halfW = (dentWidthPx * 0.5f) / Math.max(1f, widthPx);

            float[] centers = buildDentCenters();
            float[] xBreaks = buildXBreakpoints(centers, halfW);

            List<Vector3D> verts = new ArrayList<>();
            for (float x : xBreaks) {
                float dentAmt = dentAmountAt(x, centers, halfW, depth);
                float yBottom = dentAmt;
                float yTop = 1f - dentAmt;
                verts.add(new Vector3D(x, yBottom, 0f));
                verts.add(new Vector3D(x, yTop, 0f));
            }

            int segs = Math.max(0, xBreaks.length - 1);
            int[][] faces = new int[segs][];
            for (int i = 0; i < segs; i++) {
                int b0 = i * 2;
                int t0 = b0 + 1;
                int b1 = b0 + 2;
                int t1 = b0 + 3;
                faces[i] = new int[]{ b0, t0, t1, b1 };
            }

            return new FillMeshData(verts.toArray(new Vector3D[0]), faces);
        }

        private float[] buildDentCenters() {
            float[] ms = milestones != null ? milestones : new float[0];
            float[] sorted = Arrays.copyOf(ms, ms.length);
            Arrays.sort(sorted);

            List<Float> dentCenters = new ArrayList<>();
            for (float m : sorted) {
                float t = (m - minVal) / (maxVal - minVal);
                if (t < 0f || t >= 1f) continue;
                dentCenters.add(t);
            }
            float[] out = new float[dentCenters.size()];
            for (int i = 0; i < dentCenters.size(); i++) {
                out[i] = dentCenters.get(i);
            }
            return out;
        }

        private float[] buildXBreakpoints(float[] centers, float halfW) {
            List<Float> xs = new ArrayList<>();
            xs.add(0f);
            xs.add(1f);
            if (centers != null && halfW > 0f) {
                for (float t : centers) {
                    float left = Math.max(0f, t - halfW);
                    float right = Math.min(1f, t + halfW);
                    xs.add(left);
                    xs.add(t);
                    xs.add(right);
                }
            }
            float[] out = new float[xs.size()];
            for (int i = 0; i < xs.size(); i++) {
                out[i] = xs.get(i);
            }
            Arrays.sort(out);

            List<Float> unique = new ArrayList<>();
            float last = Float.NaN;
            for (float v : out) {
                if (unique.isEmpty() || Math.abs(v - last) > 1e-4f) {
                    unique.add(v);
                    last = v;
                }
            }
            float[] uniqArr = new float[unique.size()];
            for (int i = 0; i < unique.size(); i++) {
                uniqArr[i] = unique.get(i);
            }
            return uniqArr;
        }

        private float dentAmountAt(float x, float[] centers, float halfW, float depth) {
            if (centers == null || centers.length == 0 || halfW <= 0f || depth <= 0f) return 0f;
            float maxAmt = 0f;
            for (float t : centers) {
                float d = Math.abs(x - t);
                if (d >= halfW) continue;
                float amt = depth * (1f - (d / halfW));
                if (amt > maxAmt) maxAmt = amt;
            }
            return maxAmt;
        }

        private Vector3D[] buildOutlineVerts() {
            float depth = clamp01(dentDepthPx / heightPx);
            float halfW = Math.max(0f, dentWidthPx * 0.5f / widthPx);

            float[] ms = milestones != null ? milestones : new float[0];
            float[] sorted = Arrays.copyOf(ms, ms.length);
            Arrays.sort(sorted);

            List<Float> dentCenters = new ArrayList<>();
            for (float m : sorted) {
                float t = (m - minVal) / (maxVal - minVal);
                if (t <= 0f || t >= 1f) continue;
                dentCenters.add(t);
            }

            List<Vector3D> verts = new ArrayList<>();
            // Bottom edge left -> right with dents pointing up
            verts.add(new Vector3D(0f, 0f, 0f));
            for (float t : dentCenters) {
                float left = Math.max(0f, t - halfW);
                float right = Math.min(1f, t + halfW);
                if (right <= left) continue;
                verts.add(new Vector3D(left, 0f, 0f));
                verts.add(new Vector3D(t, depth, 0f));
                verts.add(new Vector3D(right, 0f, 0f));
            }
            verts.add(new Vector3D(1f, 0f, 0f));
            // Right edge up
            verts.add(new Vector3D(1f, 1f, 0f));
            // Top edge right -> left with dents pointing down
            for (int i = dentCenters.size() - 1; i >= 0; i--) {
                float t = dentCenters.get(i);
                float left = Math.max(0f, t - halfW);
                float right = Math.min(1f, t + halfW);
                if (right <= left) continue;
                verts.add(new Vector3D(right, 1f, 0f));
                verts.add(new Vector3D(t, 1f - depth, 0f));
                verts.add(new Vector3D(left, 1f, 0f));
            }
            verts.add(new Vector3D(0f, 1f, 0f));
            // Left edge down (closing edge handled by face)

            return verts.toArray(new Vector3D[0]);
        }

        private int[] buildSequentialFace(int count) {
            int[] face = new int[count];
            for (int i = 0; i < count; i++) face[i] = i;
            return face;
        }
    }

    private Builder.FillMeshData buildFillMeshData() {
        float depth = dentDepthPx / Math.max(1f, heightPx);
        depth = Math.min(0.45f, clamp01(depth));
        float halfW = (dentWidthPx * 0.5f) / Math.max(1f, widthPx);

        float[] centers = buildDentCenters();
        float[] xBreaks = buildXBreakpoints(centers, halfW);

        List<Vector3D> verts = new ArrayList<>();
        for (float x : xBreaks) {
            float dentAmt = dentAmountAt(x, centers, halfW, depth);
            float yBottom = dentAmt;
            float yTop = 1f - dentAmt;
            verts.add(new Vector3D(x, yBottom, 0f));
            verts.add(new Vector3D(x, yTop, 0f));
        }

        int segs = Math.max(0, xBreaks.length - 1);
        int[][] faces = new int[segs][];
        for (int i = 0; i < segs; i++) {
            int b0 = i * 2;
            int t0 = b0 + 1;
            int b1 = b0 + 2;
            int t1 = b0 + 3;
            faces[i] = new int[]{ b0, t0, t1, b1 };
        }

        return new Builder.FillMeshData(verts.toArray(new Vector3D[0]), faces);
    }

    private float[] buildDentCenters() {
        float[] sorted = Arrays.copyOf(milestones, milestones.length);
        Arrays.sort(sorted);
        List<Float> dentCenters = new ArrayList<>();
        for (float m : sorted) {
            float t = (m - minVal) / (maxVal - minVal);
            if (t < 0f || t >= 1f) continue;
            dentCenters.add(t);
        }
        float[] out = new float[dentCenters.size()];
        for (int i = 0; i < dentCenters.size(); i++) out[i] = dentCenters.get(i);
        return out;
    }

    private float[] buildXBreakpoints(float[] centers, float halfW) {
        List<Float> xs = new ArrayList<>();
        xs.add(0f);
        xs.add(1f);
        if (centers != null && halfW > 0f) {
            for (float t : centers) {
                float left = Math.max(0f, t - halfW);
                float right = Math.min(1f, t + halfW);
                xs.add(left);
                xs.add(t);
                xs.add(right);
            }
        }
        float[] out = new float[xs.size()];
        for (int i = 0; i < xs.size(); i++) out[i] = xs.get(i);
        Arrays.sort(out);

        List<Float> unique = new ArrayList<>();
        float last = Float.NaN;
        for (float v : out) {
            if (unique.isEmpty() || Math.abs(v - last) > 1e-4f) {
                unique.add(v);
                last = v;
            }
        }
        float[] uniqArr = new float[unique.size()];
        for (int i = 0; i < unique.size(); i++) uniqArr[i] = unique.get(i);
        return uniqArr;
    }

    private float dentAmountAt(float x, float[] centers, float halfW, float depth) {
        if (centers == null || centers.length == 0 || halfW <= 0f || depth <= 0f) return 0f;
        float maxAmt = 0f;
        for (float t : centers) {
            float d = Math.abs(x - t);
            if (d >= halfW) continue;
            float amt = depth * (1f - (d / halfW));
            if (amt > maxAmt) maxAmt = amt;
        }
        return maxAmt;
    }

    private Vector3D[] buildOutlineVerts() {
        float depth = clamp01(dentDepthPx / heightPx);
        float halfW = Math.max(0f, dentWidthPx * 0.5f / widthPx);

        float[] sorted = Arrays.copyOf(milestones, milestones.length);
        Arrays.sort(sorted);
        List<Float> dentCenters = new ArrayList<>();
        for (float m : sorted) {
            float t = (m - minVal) / (maxVal - minVal);
            if (t <= 0f || t >= 1f) continue;
            dentCenters.add(t);
        }

        List<Vector3D> verts = new ArrayList<>();
        verts.add(new Vector3D(0f, 0f, 0f));
        for (float t : dentCenters) {
            float left = Math.max(0f, t - halfW);
            float right = Math.min(1f, t + halfW);
            if (right <= left) continue;
            verts.add(new Vector3D(left, 0f, 0f));
            verts.add(new Vector3D(t, depth, 0f));
            verts.add(new Vector3D(right, 0f, 0f));
        }
        verts.add(new Vector3D(1f, 0f, 0f));
        verts.add(new Vector3D(1f, 1f, 0f));
        for (int i = dentCenters.size() - 1; i >= 0; i--) {
            float t = dentCenters.get(i);
            float left = Math.max(0f, t - halfW);
            float right = Math.min(1f, t + halfW);
            if (right <= left) continue;
            verts.add(new Vector3D(right, 1f, 0f));
            verts.add(new Vector3D(t, 1f - depth, 0f));
            verts.add(new Vector3D(left, 1f, 0f));
        }
        verts.add(new Vector3D(0f, 1f, 0f));
        return verts.toArray(new Vector3D[0]);
    }

    private int[] buildSequentialFace(int count) {
        int[] face = new int[count];
        for (int i = 0; i < count; i++) face[i] = i;
        return face;
    }
}
