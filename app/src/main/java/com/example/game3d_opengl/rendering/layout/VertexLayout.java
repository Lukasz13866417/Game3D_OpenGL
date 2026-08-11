package com.example.game3d_opengl.rendering.layout;

import android.opengl.GLES20;

public interface VertexLayout {
    int strideBytes();

    final class AttributeSpec {
        private final int componentCount;
        private final int glType;
        private final boolean normalized;
        private final int byteOffset;

        public AttributeSpec(int componentCount, int glType, boolean normalized, int byteOffset) {
            this.componentCount = componentCount;
            this.glType = glType;
            this.normalized = normalized;
            this.byteOffset = byteOffset;
        }

        public void enableAndPoint(int location, int strideBytes) {
            GLES20.glEnableVertexAttribArray(location);
            GLES20.glVertexAttribPointer(
                    location,
                    componentCount,
                    glType,
                    normalized,
                    strideBytes,
                    byteOffset
            );
        }
    }

    interface HasPosition extends VertexLayout {
        AttributeSpec position();
    }

    interface HasNormals extends VertexLayout {
        AttributeSpec normals();
    }

    interface HasTexCoords extends VertexLayout {
        AttributeSpec texCoords();
    }

    interface HasFaceGroups extends VertexLayout {
        AttributeSpec faceGroups();
    }

    interface HasPositionA extends VertexLayout {
        AttributeSpec positionA();
    }

    interface HasPositionB extends VertexLayout {
        AttributeSpec positionB();
    }

    interface HasEdgeEnd extends VertexLayout {
        AttributeSpec edgeEnd();
    }

    interface HasEdgeSide extends VertexLayout {
        AttributeSpec edgeSide();
    }

    interface HasWeights extends VertexLayout {
        AttributeSpec weights();
    }

    interface HasT extends VertexLayout {
        AttributeSpec t();
    }

    interface HasWeightsA extends VertexLayout {
        AttributeSpec weightsA();
    }

    interface HasTA extends VertexLayout {
        AttributeSpec tA();
    }

    interface HasWeightsB extends VertexLayout {
        AttributeSpec weightsB();
    }

    interface HasTB extends VertexLayout {
        AttributeSpec tB();
    }

    interface HasFaceAnchorWeights extends VertexLayout {
        AttributeSpec faceAnchorWeights();
    }

    interface HasFaceAnchorT extends VertexLayout {
        AttributeSpec faceAnchorT();
    }

    interface HasFaceBaseAWeights extends VertexLayout {
        AttributeSpec faceBaseAWeights();
    }

    interface HasFaceBaseBWeights extends VertexLayout {
        AttributeSpec faceBaseBWeights();
    }

    final class PositionLayout implements HasPosition {
        public static final PositionLayout INSTANCE = new PositionLayout();

        private static final int STRIDE_BYTES = 3 * 4;
        private static final AttributeSpec POSITION =
                new AttributeSpec(3, GLES20.GL_FLOAT, false, 0);

        private PositionLayout() {}

        @Override
        public int strideBytes() {
            return STRIDE_BYTES;
        }

        @Override
        public AttributeSpec position() {
            return POSITION;
        }
    }

    final class PositionNormalLayout implements HasPosition, HasNormals {
        public static final PositionNormalLayout INSTANCE = new PositionNormalLayout();

        private static final int STRIDE_BYTES = 6 * 4;
        private static final AttributeSpec POSITION =
                new AttributeSpec(3, GLES20.GL_FLOAT, false, 0);
        private static final AttributeSpec NORMALS =
                new AttributeSpec(3, GLES20.GL_FLOAT, false, 3 * 4);

        private PositionNormalLayout() {}

        @Override
        public int strideBytes() {
            return STRIDE_BYTES;
        }

        @Override
        public AttributeSpec position() {
            return POSITION;
        }

        @Override
        public AttributeSpec normals() {
            return NORMALS;
        }
    }

    final class PositionUvLayout implements HasPosition, HasTexCoords {
        public static final PositionUvLayout INSTANCE = new PositionUvLayout();

        private static final int STRIDE_BYTES = 5 * 4;
        private static final AttributeSpec POSITION =
                new AttributeSpec(3, GLES20.GL_FLOAT, false, 0);
        private static final AttributeSpec TEX_COORDS =
                new AttributeSpec(2, GLES20.GL_FLOAT, false, 3 * 4);

        private PositionUvLayout() {}

        @Override
        public int strideBytes() {
            return STRIDE_BYTES;
        }

        @Override
        public AttributeSpec position() {
            return POSITION;
        }

        @Override
        public AttributeSpec texCoords() {
            return TEX_COORDS;
        }
    }

    final class PositionNormalFaceGroupLayout
            implements HasPosition, HasNormals, HasFaceGroups {
        public static final PositionNormalFaceGroupLayout INSTANCE =
                new PositionNormalFaceGroupLayout();

        private static final int STRIDE_BYTES = 7 * 4;
        private static final AttributeSpec POSITION =
                new AttributeSpec(3, GLES20.GL_FLOAT, false, 0);
        private static final AttributeSpec NORMALS =
                new AttributeSpec(3, GLES20.GL_FLOAT, false, 3 * 4);
        private static final AttributeSpec FACE_GROUPS =
                new AttributeSpec(1, GLES20.GL_FLOAT, false, 6 * 4);

        private PositionNormalFaceGroupLayout() {}

        @Override
        public int strideBytes() {
            return STRIDE_BYTES;
        }

        @Override
        public AttributeSpec position() {
            return POSITION;
        }

        @Override
        public AttributeSpec normals() {
            return NORMALS;
        }

        @Override
        public AttributeSpec faceGroups() {
            return FACE_GROUPS;
        }
    }

    final class EdgeABLayout
            implements HasPositionA, HasPositionB, HasEdgeEnd, HasEdgeSide {
        public static final EdgeABLayout INSTANCE = new EdgeABLayout();

        private static final int STRIDE_BYTES = 8 * 4;
        private static final AttributeSpec POSITION_A =
                new AttributeSpec(3, GLES20.GL_FLOAT, false, 0);
        private static final AttributeSpec POSITION_B =
                new AttributeSpec(3, GLES20.GL_FLOAT, false, 3 * 4);
        private static final AttributeSpec EDGE_END =
                new AttributeSpec(1, GLES20.GL_FLOAT, false, 6 * 4);
        private static final AttributeSpec EDGE_SIDE =
                new AttributeSpec(1, GLES20.GL_FLOAT, false, 7 * 4);

        private EdgeABLayout() {}

        @Override
        public int strideBytes() {
            return STRIDE_BYTES;
        }

        @Override
        public AttributeSpec positionA() {
            return POSITION_A;
        }

        @Override
        public AttributeSpec positionB() {
            return POSITION_B;
        }

        @Override
        public AttributeSpec edgeEnd() {
            return EDGE_END;
        }

        @Override
        public AttributeSpec edgeSide() {
            return EDGE_SIDE;
        }
    }

    final class SpikeCanonicalFillLayout
            implements HasWeights, HasT, HasFaceBaseAWeights, HasFaceBaseBWeights {
        public static final SpikeCanonicalFillLayout INSTANCE =
                new SpikeCanonicalFillLayout();

        private static final int STRIDE_BYTES = 13 * 4;
        private static final AttributeSpec WEIGHTS =
                new AttributeSpec(4, GLES20.GL_FLOAT, false, 0);
        private static final AttributeSpec T =
                new AttributeSpec(1, GLES20.GL_FLOAT, false, 4 * 4);
        private static final AttributeSpec FACE_BASE_A_WEIGHTS =
                new AttributeSpec(4, GLES20.GL_FLOAT, false, 5 * 4);
        private static final AttributeSpec FACE_BASE_B_WEIGHTS =
                new AttributeSpec(4, GLES20.GL_FLOAT, false, 9 * 4);

        private SpikeCanonicalFillLayout() {}

        @Override
        public int strideBytes() {
            return STRIDE_BYTES;
        }

        @Override
        public AttributeSpec weights() {
            return WEIGHTS;
        }

        @Override
        public AttributeSpec t() {
            return T;
        }

        @Override
        public AttributeSpec faceBaseAWeights() {
            return FACE_BASE_A_WEIGHTS;
        }

        @Override
        public AttributeSpec faceBaseBWeights() {
            return FACE_BASE_B_WEIGHTS;
        }
    }

    final class SpikeCanonicalWireframeLayout
            implements HasWeightsA, HasTA, HasWeightsB, HasTB, HasEdgeEnd, HasEdgeSide {
        public static final SpikeCanonicalWireframeLayout INSTANCE =
                new SpikeCanonicalWireframeLayout();

        private static final int STRIDE_BYTES = 12 * 4;
        private static final AttributeSpec WEIGHTS_A =
                new AttributeSpec(4, GLES20.GL_FLOAT, false, 0);
        private static final AttributeSpec T_A =
                new AttributeSpec(1, GLES20.GL_FLOAT, false, 4 * 4);
        private static final AttributeSpec WEIGHTS_B =
                new AttributeSpec(4, GLES20.GL_FLOAT, false, 5 * 4);
        private static final AttributeSpec T_B =
                new AttributeSpec(1, GLES20.GL_FLOAT, false, 9 * 4);
        private static final AttributeSpec EDGE_END =
                new AttributeSpec(1, GLES20.GL_FLOAT, false, 10 * 4);
        private static final AttributeSpec EDGE_SIDE =
                new AttributeSpec(1, GLES20.GL_FLOAT, false, 11 * 4);

        private SpikeCanonicalWireframeLayout() {}

        @Override
        public int strideBytes() {
            return STRIDE_BYTES;
        }

        @Override
        public AttributeSpec weightsA() {
            return WEIGHTS_A;
        }

        @Override
        public AttributeSpec tA() {
            return T_A;
        }

        @Override
        public AttributeSpec weightsB() {
            return WEIGHTS_B;
        }

        @Override
        public AttributeSpec tB() {
            return T_B;
        }

        @Override
        public AttributeSpec edgeEnd() {
            return EDGE_END;
        }

        @Override
        public AttributeSpec edgeSide() {
            return EDGE_SIDE;
        }
    }
}
