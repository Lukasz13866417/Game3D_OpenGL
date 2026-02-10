package com.example.game3d_opengl.game.progress_bar;

import com.example.game3d_opengl.rendering.mesh.AbstractMesh3D;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public final class ProgressBarFillMesh3D
        extends AbstractMesh3D<ProgressBarFillDrawArgs, ProgressBarFillShaderPair> {

    private final FColor color;

    private ProgressBarFillMesh3D(Builder b) {
        super(b);
        this.color = b.color;
    }

    @Override
    protected void setVariableArgsValues(ProgressBarFillDrawArgs args, ProgressBarFillShaderPair s) {
        ProgressBarFillShaderArgs.VS vs = new ProgressBarFillShaderArgs.VS();
        vs.mvp = args.vp;
        ProgressBarFillShaderArgs.FS fs = new ProgressBarFillShaderArgs.FS();
        fs.color = color;
        fs.progress = args.progress;
        s.setArgs(vs, fs);
    }

    public static final class Builder
            extends AbstractMesh3D.BaseBuilder<ProgressBarFillMesh3D, Builder, ProgressBarFillShaderPair> {
        private FColor color = FColor.CLR(1f, 1f, 1f, 1f);

        public Builder color(FColor c) {
            if (c != null) this.color = c;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        protected ProgressBarFillMesh3D create() {
            return new ProgressBarFillMesh3D(this);
        }

        @Override
        public void checkValid() {
            shader(ProgressBarFillShaderPair.getSharedShader());
            super.checkValid();
        }

        @Override
        protected float[] setVertexData() {
            final int n = verts.length;
            float[] out = new float[n * 3];
            for (int i = 0; i < n; i++) {
                Vector3D v = verts[i];
                int o = i * 3;
                out[o] = (float) v.x;
                out[o + 1] = (float) v.y;
                out[o + 2] = (float) v.z;
            }
            return out;
        }
    }
}
