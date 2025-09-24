package com.example.game3d_opengl.rendering.infill;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;

import com.example.game3d_opengl.rendering.mesh.AbstractMesh3D;
import com.example.game3d_opengl.rendering.mesh.MeshDrawArgs;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public class Mesh3DInfill extends AbstractMesh3D<MeshDrawArgs, InfillShaderPair> {

    public Mesh3DInfill(Builder builder){
        super(builder);
        this.fillColor = builder.fillColor;
    }

    private final FColor fillColor;

    @Override
    protected void setVariableArgsValues(MeshDrawArgs args, InfillShaderPair s) {
        InfillShaderArgs.VS vs = new InfillShaderArgs.VS();
        vs.mvp = args.vp;
        InfillShaderArgs.FS fs = new InfillShaderArgs.FS();
        fs.color = fillColor != null ? fillColor : CLR(1,1,1,1);
        s.setArgs(vs, fs);
    }

    public static class Builder extends BaseBuilder<Mesh3DInfill, Builder, InfillShaderPair> {
        private FColor fillColor = CLR(1,1,1,1);

        public Builder fillColor(FColor c){ this.fillColor = c; return this; }

        @Override
        public void checkValid() {
            shader(InfillShaderPair.getSharedShader());
            super.checkValid();
            assert fillColor != null;
            // Sanity check: faces reference existing vertices
            for (int[] f : faces) {
                for (int idx : f) {
                    if (idx < 0 || idx >= verts.length) {
                        throw new IllegalStateException("Face index out of range: " + idx + " (verts=" + verts.length + ")");
                    }
                }
            }
        }

        @Override
        protected float[] setVertexData() {
            // Pack positions only: [x0,y0,z0, x1,y1,z1, ...]
            final int n = verts.length;
            float[] out = new float[n * 3];
            for (int i = 0; i < n; ++i) {
                Vector3D v = verts[i];
                int o = i * 3;
                out[o    ] = (float) v.x;
                out[o + 1] = (float) v.y;
                out[o + 2] = (float) v.z;
            }
            // Faces remain as provided; AbstractMesh3D.BaseBuilder.prepareGPUResources()
            // will fan-triangulate them into the IBO.
            return out;
        }

        @Override
        public Builder self() {
            return this;
        }

        @Override
        public Mesh3DInfill create() {
            // Ensure the shared shader is set so draw() has everything it needs.
            return new Mesh3DInfill(this);
        }

    }
}


