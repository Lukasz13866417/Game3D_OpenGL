package com.example.game3d_opengl.rendering.mesh;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.layout.VertexLayout;
import com.example.game3d_opengl.rendering.shader.MeshShaderPair;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public abstract class AbstractMesh3D<
        A extends BaseMeshDrawArgs,
        S extends MeshShaderPair<?, ?, L>,
        L extends VertexLayout>
        implements GPUResourceOwner {

    protected final S shader;
    protected final L layout;
    protected final AbstractRenderer<A, S, L> renderer;

    private final int indexCount;
    private final int indexByteOffset;
    private final int indexType;

    protected AbstractMesh3D(BaseBuilder<?, ?, A, S, L> builder) {
        this.shader = builder.shader;
        this.layout = builder.layout;
        this.renderer = builder.renderer;
        this.indexCount = builder.indexCount;
        this.indexByteOffset = builder.indexByteOffset;
        this.indexType = builder.indexType;
    }

    protected abstract void setVariableArgsValues(A meshDrawArgs, S targetShader);

    public void draw(A args) {
        renderer.render(this, args);
    }

    protected final boolean canUpdateVertexData(int floatCount) {
        return renderer.canUpdateVertexData(floatCount);
    }

    protected final void updateVertexData(float[] vertexData) {
        renderer.updateVertexData(vertexData);
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        renderer.reloadGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        renderer.cleanupGPUResourcesRecursively();
    }

    final AbstractRenderer<A, S, L> renderer() {
        return renderer;
    }

    final void issueDraw(A args) {
        setVariableArgsValues(args, shader);
        shader.transferUniformArgsToGPU();
        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                indexCount,
                indexType,
                indexByteOffset);
    }

    protected static abstract class BaseBuilder<
            T extends AbstractMesh3D<A, S, L>,
            B extends BaseBuilder<T, B, A, S, L>,
            A extends BaseMeshDrawArgs,
            S extends MeshShaderPair<?, ?, L>,
            L extends VertexLayout> {

        protected Vector3D[] verts;
        protected int[][] faces;
        protected S shader;
        protected L layout;
        protected AbstractRenderer<A, S, L> renderer;
        protected int indexCount;
        protected int indexByteOffset;
        protected int indexType;

        protected abstract B self();

        protected abstract T create();

        protected abstract L createLayout();

        protected abstract float[] setVertexData();

        public B verts(Vector3D[] verts) {
            this.verts = verts;
            return self();
        }

        public B faces(int[][] faces) {
            this.faces = faces;
            return self();
        }

        public B shader(S what) {
            this.shader = what;
            return self();
        }

        protected void checkValid() {
            if (faces == null) {
                throw new IllegalStateException("faces == null");
            }
            if (verts == null) {
                throw new IllegalStateException("verts == null");
            }
            if (shader == null) {
                throw new IllegalStateException("shader == null");
            }
        }

        public final T buildObject() {
            checkValid();
            layout = createLayout();
            float[] vertexData = setVertexData();
            renderer = new AbstractRenderer<>(shader, layout, vertexData, faces);
            indexCount = renderer.getIndexCount();
            indexByteOffset = 0;
            indexType = renderer.getIndexType();
            return create();
        }
    }
}
