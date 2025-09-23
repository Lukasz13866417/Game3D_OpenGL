package com.example.game3d_opengl.game.track_elements.spike;

import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.rendering.object3d.AbstractMesh3D;
import com.example.game3d_opengl.rendering.object3d.infill.InfillShaderArgs;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Wireframe renderer for spike edges using weights+t vertex layout.
 * Generates an edge list from the canonical 5-vertex spike and renders as lines (GL_LINES-style via shader).
 * Uses SpikeWireframeShaderPair which computes screen-space thickness in the vertex shader.
 */
public final class SpikeWireframeMesh3D extends AbstractMesh3D<SpikeWireframeShaderPair> {

    private final float[] uNL, uNR, uFR, uFL, uApex, uNormal;
    private final float uBaseOffset;
    private final FColor color;
    private final float pixelWidth;

    public SpikeWireframeMesh3D(Builder b) {
        super(b);
        this.uNL = b.uNL; this.uNR = b.uNR; this.uFR = b.uFR; this.uFL = b.uFL;
        this.uApex = b.uApex; this.uNormal = b.uNormal; this.uBaseOffset = b.uBaseOffset;
        this.color = b.color; this.pixelWidth = b.pixelWidth;
    }

    @Override
    protected void setVariableArgsValues(float[] mvp, SpikeWireframeShaderPair s) {
        InfillShaderArgs.VS vs = new InfillShaderArgs.VS();
        vs.mvp = mvp;
        vs.uNL = uNL; vs.uNR = uNR; vs.uFR = uFR; vs.uFL = uFL;
        vs.uApex = uApex; vs.uNormal = uNormal; vs.uBaseOffset = uBaseOffset;

        InfillShaderArgs.FS fs = new InfillShaderArgs.FS();
        fs.color = color;
        s.setArgs(vs, fs);
    }

    public static final class Builder extends AbstractMesh3D.BaseBuilder<SpikeWireframeMesh3D, Builder, SpikeWireframeShaderPair> {
        private float[] uNL, uNR, uFR, uFL, uApex, uNormal;
        private float uBaseOffset;
        private FColor color;
        private float pixelWidth;

        @Override protected Builder self() { return this; }
        @Override protected SpikeWireframeMesh3D create() { return new SpikeWireframeMesh3D(this); }

        public Builder shader(SpikeWireframeShaderPair s) { super.shader(s); return this; }
        public Builder instanceUniforms(float[] nl, float[] nr, float[] fr, float[] fl,
                                        float[] apex, float[] normal, float baseOffset) {
            this.uNL = nl; this.uNR = nr; this.uFR = fr; this.uFL = fl;
            this.uApex = apex; this.uNormal = normal; this.uBaseOffset = baseOffset; return this;
        }
        public Builder color(FColor c) { this.color = c; return this; }
        public Builder pixelWidth(float px) { this.pixelWidth = px; return this; }

        @Override
        protected float[] setVertexData() {
            // Canonical five vertices with weights+t, matching SpikeInfillMesh3D
            float[] data = new float[]{
                    1,0,0,0, 0,
                    0,1,0,0, 0,
                    0,0,1,0, 0,
                    0,0,0,1, 0,
                    0,0,0,0, 1
            };
            // Provide dummy positions to satisfy builder contract
            this.verts = new Vector3D[]{
                    new Vector3D(0,0,0), new Vector3D(0,0,0), new Vector3D(0,0,0),
                    new Vector3D(0,0,0), new Vector3D(0,0,0)
            };
            // Edges of the spike pyramid: base square perimeter + 4 sides to apex
            this.faces = new int[][]{
                    new int[]{0,1}, new int[]{1,2}, new int[]{2,3}, new int[]{3,0},
                    new int[]{0,4}, new int[]{1,4}, new int[]{2,4}, new int[]{3,4}
            };
            return data;
        }
    }
}


