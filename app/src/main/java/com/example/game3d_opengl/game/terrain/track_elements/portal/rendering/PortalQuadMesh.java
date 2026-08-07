package com.example.game3d_opengl.game.terrain.track_elements.portal.rendering;

import com.example.game3d_opengl.rendering.layout.VertexLayout;
import com.example.game3d_opengl.rendering.mesh.AbstractMesh3D;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public final class PortalQuadMesh extends AbstractMesh3D<
        PortalQuadDrawArgs,
        PortalShaderPair<VertexLayout.PositionUvLayout>,
        VertexLayout.PositionUvLayout> {

    private PortalQuadMesh(Builder builder) {
        super(builder);
    }

    @Override
    protected void setVariableArgsValues(
            PortalQuadDrawArgs args,
            PortalShaderPair<VertexLayout.PositionUvLayout> targetShader) {
        PortalShaderArgs.VS vs = new PortalShaderArgs.VS();
        vs.mvp = args.vp;
        PortalShaderArgs.FS fs = new PortalShaderArgs.FS();
        fs.textureUnit = args.textureUnit;
        targetShader.setArgs(vs, fs);
    }

    public static final class Builder extends BaseBuilder<
            PortalQuadMesh,
            Builder,
            PortalQuadDrawArgs,
            PortalShaderPair<VertexLayout.PositionUvLayout>,
            VertexLayout.PositionUvLayout> {
        private float[] uvs;

        public Builder uvs(float[] uvs) {
            this.uvs = uvs;
            return self();
        }

        @Override
        public void checkValid() {
            shader(PortalShaderPair.getSharedShader());
            super.checkValid();
            if (uvs == null || uvs.length != verts.length * 2) {
                throw new IllegalStateException("UVs must match verts (2 per vertex).");
            }
        }

        @Override
        protected float[] setVertexData() {
            final int n = verts.length;
            float[] out = new float[n * 5];
            for (int i = 0; i < n; i++) {
                Vector3D v = verts[i];
                int o = i * 5;
                int uo = i * 2;
                out[o    ] = v.x;
                out[o + 1] = v.y;
                out[o + 2] = v.z;
                out[o + 3] = uvs[uo];
                out[o + 4] = uvs[uo + 1];
            }
            return out;
        }

        @Override
        protected VertexLayout.PositionUvLayout createLayout() {
            return VertexLayout.PositionUvLayout.INSTANCE;
        }

        @Override
        public Builder self() {
            return this;
        }

        @Override
        public PortalQuadMesh create() {
            return new PortalQuadMesh(this);
        }
    }
}
