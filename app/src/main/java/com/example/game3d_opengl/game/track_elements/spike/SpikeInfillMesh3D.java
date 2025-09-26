package com.example.game3d_opengl.game.track_elements.spike;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;

import com.example.game3d_opengl.rendering.mesh.AbstractMesh3D;
import com.example.game3d_opengl.rendering.infill.InfillShaderArgs;
import com.example.game3d_opengl.rendering.util3d.FColor;

/**
 * Infill-only spike mesh using canonical spike vertices (weights + t) and per-instance uniforms.
 * Shares a pre-created VBO id (provided by builder), and generates its own fill IBO.
 */
public final class SpikeInfillMesh3D extends AbstractMesh3D<SpikeInfillDrawArgs, SpikeInfillShaderPair> {

    private final FColor color;

    private SpikeInfillMesh3D(Builder b) {
        super(b);
        this.color = b.color != null ? b.color : CLR(0,0,0,1);
    }

    @Override
    protected void setVariableArgsValues(SpikeInfillDrawArgs args, SpikeInfillShaderPair s) {
        InfillShaderArgs.VS vs = new InfillShaderArgs.VS();
        vs.mvp = args.vp;
        vs.uNL = args.uNL;
        vs.uNR = args.uNR;
        vs.uFR = args.uFR;
        vs.uFL = args.uFL;
        vs.uApex = args.uApex;
        vs.uNormal = args.uNormal;
        vs.uBaseOffset = args.uBaseOffset;

        InfillShaderArgs.FS fs = new InfillShaderArgs.FS();
        fs.color = color;

        s.setArgs(vs, fs);
    }

    public static final class Builder extends AbstractMesh3D.BaseBuilder<SpikeInfillMesh3D, Builder, SpikeInfillShaderPair> {
        private FColor color;

        @Override
        protected Builder self() { return this; }

        @Override
        protected SpikeInfillMesh3D create() { return new SpikeInfillMesh3D(this); }

        public Builder shader(SpikeInfillShaderPair shader) { super.shader(shader); return this; }

        public Builder color(FColor c) { this.color = c; return this; }

        @Override
        protected float[] setVertexData() {
            // Canonical spike vertices: 5 verts, each has vec4 weights + float t
            // Indexing: 0:NL, 1:NR, 2:FR, 3:FL, 4:Apex (t=1)
            float[] data = new float[]{
                    1,0,0,0, 0,
                    0,1,0,0, 0,
                    0,0,1,0, 0,
                    0,0,0,1, 0,
                    0,0,0,0, 1
            };
            return data;
        }
    }
}


