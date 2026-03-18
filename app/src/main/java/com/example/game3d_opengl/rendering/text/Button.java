package com.example.game3d_opengl.rendering.text;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.ScreenInfo;
import com.example.game3d_opengl.rendering.icon.RectOverlay;
import com.example.game3d_opengl.rendering.infill.Mesh3DInfill;
import com.example.game3d_opengl.rendering.mesh.MVPDrawArgs;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.rect.Rect;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public final class Button implements GPUResourceOwner {
    private static final Vector3D[] UNIT_QUAD = new Vector3D[]{
            new Vector3D(0, 0, 0),
            new Vector3D(1, 0, 0),
            new Vector3D(1, 1, 0),
            new Vector3D(0, 1, 0)
    };
    private static final int[][] UNIT_FACES = new int[][]{
            new int[]{0, 1, 2, 3}
    };

    private final Rect rectPx;
    private final Mesh3DInfill fillMesh;
    private final MVPDrawArgs fillDrawArgs;
    private final RectOverlay outline;
    private final TextRenderer.TextLabel label;

    private Button(Builder builder) {
        this.rectPx = builder.rectPx;
        this.fillMesh = builder.fillMesh;
        this.fillDrawArgs = builder.fillDrawArgs;
        this.outline = builder.outline;
        this.label = builder.label;
    }

    public void draw() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        if (fillMesh != null) {
            fillMesh.draw(fillDrawArgs);
        }
        if (outline != null) {
            outline.draw();
        }
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    public Rect getRectPx() {
        return rectPx;
    }

    public Button setText(String text) {
        if (label != null) {
            label.setText(text);
        }
        return this;
    }

    public Button setTextColor(FColor color) {
        if (label != null && color != null) {
            label.setColor(color);
        }
        return this;
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        if (fillMesh != null) fillMesh.reloadGPUResourcesRecursivelyOnContextLoss();
        if (outline != null) outline.reloadGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        if (fillMesh != null) fillMesh.cleanupGPUResourcesRecursively();
        if (outline != null) outline.cleanupGPUResourcesRecursively();
    }

    public static final class Builder {
        private static final float DEFAULT_EDGE_PX = 2f;

        private Rect rectPx;
        private Rect rectClip;
        private String text;
        private FColor fillColor = FColor.CLR(0.15f, 0.15f, 0.15f, 1f);
        private FColor outlineColor = FColor.CLR(1f, 1f, 1f, 1f);
        private FColor textColor = FColor.CLR(1f, 1f, 1f, 1f);
        private float edgePx = DEFAULT_EDGE_PX;
        private float textScale = 1f;
        private TextRenderer textRenderer;

        private Mesh3DInfill fillMesh;
        private MVPDrawArgs fillDrawArgs;
        private RectOverlay outline;
        private TextRenderer.TextLabel label;

        public Builder bboxPx(float x, float y, float w, float h) {
            this.rectPx = new Rect(x, y, x + w, y + h);
            return this;
        }

        public Builder bboxPx(Rect rectPx) {
            this.rectPx = rectPx;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
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

        public Builder textColor(FColor color) {
            if (color != null) this.textColor = color;
            return this;
        }

        public Builder edgePixels(float px) {
            this.edgePx = px;
            return this;
        }

        public Builder textRenderer(TextRenderer textRenderer) {
            this.textRenderer = textRenderer;
            return this;
        }

        public Builder textScale(float scale) {
            this.textScale = scale;
            return this;
        }

        public Button build() {
            if (rectPx == null) {
                throw new IllegalStateException("Button requires a bounding box");
            }
            if (textRenderer == null) {
                throw new IllegalStateException("Button requires a TextRenderer");
            }
            if (text == null) {
                text = "";
            }

            float screenW = Math.max(1f, (float) ScreenInfo.getScreenW());
            float screenH = Math.max(1f, (float) ScreenInfo.getScreenH());

            float x1 = (rectPx.x1 / screenW) * 2f - 1f;
            float x2 = (rectPx.x2 / screenW) * 2f - 1f;
            float yTop = 1f - (rectPx.y1 / screenH) * 2f;
            float yBottom = 1f - (rectPx.y2 / screenH) * 2f;
            rectClip = new Rect(x1, yBottom, x2, yTop);

            fillMesh = new Mesh3DInfill.Builder()
                    .verts(UNIT_QUAD)
                    .faces(UNIT_FACES)
                    .fillColor(fillColor)
                    .buildObject();

            float[] placement = buildPlacementMatrix(rectClip, 0f, 1f, 0f, 1f);
            fillDrawArgs = new MVPDrawArgs(placement);

            outline = new RectOverlay.Builder()
                    .placementRect(rectClip)
                    .edgeColor(outlineColor)
                    .edgePixels(edgePx)
                    .build();

            float cx = (rectPx.x1 + rectPx.x2) * 0.5f;
            float cy = (rectPx.y1 + rectPx.y2) * 0.5f;
            label = textRenderer.createLabel(text, cx, cy, textColor)
                    .setAnchor(TextRenderer.Anchor.CENTER)
                    .setScale(textScale);

            return new Button(this);
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
    }
}
