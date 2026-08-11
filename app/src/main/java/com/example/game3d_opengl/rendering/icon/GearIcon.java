package com.example.game3d_opengl.rendering.icon;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.ScreenInfo;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.rect.Rect;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.util.ArrayList;
import java.util.List;

public final class GearIcon extends Icon {
    private static final int DEFAULT_TOOTH_COUNT = 6;
    private static final int DEFAULT_INNER_SEGMENTS = 20;
    private static final int DEFAULT_GAP_ARC_SEGMENTS = 3;
    private static final float ROOT_RADIUS = 0.72f;
    private static final float TOOTH_RADIUS = 1.0f;
    private static final float INNER_RADIUS = 0.4f;
    private static final float TOOTH_HALF_WIDTH_FACTOR = 0.40f;
    private static final float TOOTH_CENTER_OFFSET_FACTOR = 0.5f;

    private GearIcon(Builder builder) {
        super(builder);
    }

    public static GearIcon createPx(Rect rectPx, FColor edgeColor, float edgePixels) {
        if (rectPx == null) {
            throw new IllegalArgumentException("rectPx == null");
        }
        if (edgeColor == null) {
            throw new IllegalArgumentException("edgeColor == null");
        }
        GearMeshData meshData = buildGearMeshData(DEFAULT_TOOTH_COUNT, DEFAULT_INNER_SEGMENTS);
        return new Builder()
                .verts(meshData.verts)
                .faces(meshData.faces)
                .fillColor(FColor.CLR(0f, 0f, 0f, 1f))
                .edgeColor(edgeColor)
                .edgePixels(edgePixels)
                .placementRect(pxRectToClip(rectPx))
                .build();
    }

    @Override
    public void draw() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        super.draw();
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    private static GearMeshData buildGearMeshData(int toothCount, int innerSegments) {
        List<Vector3D> verts = new ArrayList<>();
        List<int[]> faces = new ArrayList<>();

        float step = (float) (2.0 * Math.PI / toothCount);
        float toothHalfWidth = step * TOOTH_HALF_WIDTH_FACTOR;
        float gapSpan = step - 2f * toothHalfWidth;
        float phase = (float) (-Math.PI * 0.0f + step * TOOTH_CENTER_OFFSET_FACTOR);
        List<Integer> outerFace = new ArrayList<>();

        float firstToothStartAngle = phase - toothHalfWidth;
        outerFace.add(verts.size());
        verts.add(polar(ROOT_RADIUS, firstToothStartAngle));

        for (int i = 0; i < toothCount; ++i) {
            float centerAngle = phase + i * step;
            float toothStartAngle = centerAngle - toothHalfWidth;
            float toothEndAngle = centerAngle + toothHalfWidth;
            Vector3D baseA = polar(ROOT_RADIUS, toothStartAngle);
            Vector3D baseB = polar(ROOT_RADIUS, toothEndAngle);
            Vector3D toothNormal = polar(1f, centerAngle);
            float chordMidRadius = ROOT_RADIUS * (float) Math.cos(toothHalfWidth);
            float toothLift = TOOTH_RADIUS - chordMidRadius;
            Vector3D topA = baseA.add(toothNormal.mult(toothLift));
            Vector3D topB = baseB.add(toothNormal.mult(toothLift));

            outerFace.add(verts.size());
            verts.add(topA);
            outerFace.add(verts.size());
            verts.add(topB);
            outerFace.add(verts.size());
            verts.add(baseB);

            int gapPointCount = (i == toothCount - 1)
                    ? DEFAULT_GAP_ARC_SEGMENTS
                    : DEFAULT_GAP_ARC_SEGMENTS + 1;
            for (int j = 1; j <= gapPointCount; ++j) {
                float angle = toothEndAngle + gapSpan * j / (DEFAULT_GAP_ARC_SEGMENTS + 1);
                outerFace.add(verts.size());
                verts.add(polar(ROOT_RADIUS, angle));
            }
        }
        faces.add(toIntArray(outerFace));

        int[] innerFace = new int[innerSegments];
        for (int i = 0; i < innerSegments; ++i) {
            float angle = phase + i * (float) (2.0 * Math.PI / innerSegments);
            innerFace[i] = verts.size();
            verts.add(polar(INNER_RADIUS, angle));
        }
        faces.add(innerFace);

        return new GearMeshData(
                verts.toArray(new Vector3D[0]),
                faces.toArray(new int[0][])
        );
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < values.size(); ++i) {
            out[i] = values.get(i);
        }
        return out;
    }

    private static Vector3D polar(float radius, float angle) {
        return new Vector3D(
                radius * (float) Math.cos(angle),
                radius * (float) Math.sin(angle),
                0f
        );
    }

    private static Rect pxRectToClip(Rect rectPx) {
        float screenW = Math.max(1f, (float) ScreenInfo.getScreenW());
        float screenH = Math.max(1f, (float) ScreenInfo.getScreenH());
        float x1 = (rectPx.x1 / screenW) * 2f - 1f;
        float x2 = (rectPx.x2 / screenW) * 2f - 1f;
        float yTop = 1f - (rectPx.y1 / screenH) * 2f;
        float yBottom = 1f - (rectPx.y2 / screenH) * 2f;
        return new Rect(x1, yBottom, x2, yTop);
    }

    private static final class GearMeshData {
        final Vector3D[] verts;
        final int[][] faces;

        GearMeshData(Vector3D[] verts, int[][] faces) {
            this.verts = verts;
            this.faces = faces;
        }
    }

    public static final class Builder extends Icon.Builder<Builder, GearIcon> {
        @Override
        protected GearIcon createWhenReady() {
            return new GearIcon(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
