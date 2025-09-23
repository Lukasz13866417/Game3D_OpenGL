package com.example.game3d_opengl.game.track_elements.spike;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.rendering.util3d.RenderingUtils.ID_NOT_SET;

import com.example.game3d_opengl.rendering.object3d.AbstractMesh3D;
import com.example.game3d_opengl.rendering.object3d.infill.InfillShaderArgs;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.object3d.wireframe.WireframeShaderPair;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Infill-only spike mesh using canonical spike vertices (weights + t) and per-instance uniforms.
 * Shares a pre-created VBO id (provided by builder), and generates its own fill IBO.
 */
public final class SpikeInfillMesh3D extends AbstractMesh3D<SpikeInfillShaderPair> {

    // Per-instance uniforms
    private final float[] uNL, uNR, uFR, uFL, uApex, uNormal;
    private final float uBaseOffset;
    private final FColor color;

    private SpikeInfillMesh3D(Builder b) {
        super(b);
        this.uNL = b.uNL;
        this.uNR = b.uNR;
        this.uFR = b.uFR;
        this.uFL = b.uFL;
        this.uApex = b.uApex;
        this.uNormal = b.uNormal;
        this.uBaseOffset = b.uBaseOffset;
        this.color = b.color != null ? b.color : CLR(0,0,0,1);
    }

    @Override
    protected void setVariableArgsValues(float[] mvp, SpikeInfillShaderPair s) {
        InfillShaderArgs.VS vs = new InfillShaderArgs.VS();
        vs.mvp = mvp;
        vs.uNL = uNL;
        vs.uNR = uNR;
        vs.uFR = uFR;
        vs.uFL = uFL;
        vs.uApex = uApex;
        vs.uNormal = uNormal;
        vs.uBaseOffset = uBaseOffset;

        InfillShaderArgs.FS fs = new InfillShaderArgs.FS();
        fs.color = color;

        s.setArgs(vs, fs);
    }

    public static final class Builder extends AbstractMesh3D.BaseBuilder<SpikeInfillMesh3D, Builder, SpikeInfillShaderPair> {
        // Per-instance data to embed in the mesh instance
        private float[] uNL, uNR, uFR, uFL, uApex, uNormal;
        private float uBaseOffset;
        private FColor color;

        @Override
        protected Builder self() { return this; }

        @Override
        protected SpikeInfillMesh3D create() { return new SpikeInfillMesh3D(this); }

        public Builder shader(SpikeInfillShaderPair shader) { super.shader(shader); return this; }

        public Builder instanceUniforms(float[] nl, float[] nr, float[] fr, float[] fl,
                                        float[] apex, float[] normal, float baseOffset) {
            this.uNL = nl; this.uNR = nr; this.uFR = fr; this.uFL = fl;
            this.uApex = apex; this.uNormal = normal; this.uBaseOffset = baseOffset;
            return this;
        }

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


