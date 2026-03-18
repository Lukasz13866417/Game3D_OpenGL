package com.example.game3d_opengl.game.terrain.track_elements.spike;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;

import com.example.game3d_opengl.game.terrain.track_elements.portal.PortalLightingEnvironment;
import com.example.game3d_opengl.rendering.mesh.AbstractMesh3D;
import com.example.game3d_opengl.rendering.infill.InfillShaderArgs;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Infill-only spike mesh using canonical spike vertices (weights + t) and per-instance uniforms.
 * Shares a pre-created VBO id (provided by builder), and generates its own fill IBO.
 */
public final class SpikeInfillMesh3D extends AbstractMesh3D<SpikeInfillDrawArgs, SpikeInfillShaderPair> {

    private final FColor color;

    private SpikeInfillMesh3D(Builder b) {
        super(b);
        this.color = b.color != null ? b.color : CLR(0, 0, 0, 1);
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
        FColor theme = PortalLightingEnvironment.getColorTheme();
        final float themeDarken = 0.8f; // slightly darker than theme
        fs.color = theme != null
                ? FColor.CLR(theme.r() * themeDarken, theme.g() * themeDarken, theme.b() * themeDarken, 1f)
                : color;

        Vector3D lightPos = PortalLightingEnvironment.getLightPos();
        Vector3D cameraPos = PortalLightingEnvironment.getCameraPos();
        FColor lightColor = PortalLightingEnvironment.getLightColor();
        if (lightPos != null) {
            fs.lightX = lightPos.x; fs.lightY = lightPos.y; fs.lightZ = lightPos.z;
        }
        if (cameraPos != null) {
            fs.cameraX = cameraPos.x; fs.cameraY = cameraPos.y; fs.cameraZ = cameraPos.z;
        }
        fs.lightColor = lightColor != null ? lightColor : FColor.CLR(1f, 1f, 1f, 1f);
        fs.specular = 0.9f;
        fs.shininess = 5f;

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
            // Canonical spike vertices:
            // 0=NL, 1=NR, 2=FR, 3=FL, 4=Apex
            final float[][] canonicalW = new float[][]{
                    {1f, 0f, 0f, 0f},
                    {0f, 1f, 0f, 0f},
                    {0f, 0f, 1f, 0f},
                    {0f, 0f, 0f, 1f},
                    {0f, 0f, 0f, 0f}
            };
            final float[] canonicalT = new float[]{0f, 0f, 0f, 0f, 1f};
            final int[][] faceCanon = new int[][]{
                    {0, 1, 4},
                    {1, 2, 4},
                    {2, 3, 4},
                    {3, 0, 4}
            };

            // Duplicate vertices per face so each face can carry its own anchor
            // (constant light/view vectors for true flat look).
            final int faceCount = faceCanon.length;
            final int vertsPerFace = 3;
            final int totalVerts = faceCount * vertsPerFace;
            final int stride = 10; // w(4) + t(1) + faceAnchorW(4) + faceAnchorT(1)
            float[] data = new float[totalVerts * stride];

            int w = 0;
            for (int fi = 0; fi < faceCount; fi++) {
                int i0 = faceCanon[fi][0];
                int i1 = faceCanon[fi][1];
                int i2 = faceCanon[fi][2];

                float aw0 = (canonicalW[i0][0] + canonicalW[i1][0] + canonicalW[i2][0]) / 3f;
                float aw1 = (canonicalW[i0][1] + canonicalW[i1][1] + canonicalW[i2][1]) / 3f;
                float aw2 = (canonicalW[i0][2] + canonicalW[i1][2] + canonicalW[i2][2]) / 3f;
                float aw3 = (canonicalW[i0][3] + canonicalW[i1][3] + canonicalW[i2][3]) / 3f;
                float at = (canonicalT[i0] + canonicalT[i1] + canonicalT[i2]) / 3f;

                int[] tri = new int[]{i0, i1, i2};
                for (int k = 0; k < 3; k++) {
                    int ci = tri[k];
                    data[w++] = canonicalW[ci][0];
                    data[w++] = canonicalW[ci][1];
                    data[w++] = canonicalW[ci][2];
                    data[w++] = canonicalW[ci][3];
                    data[w++] = canonicalT[ci];
                    data[w++] = aw0;
                    data[w++] = aw1;
                    data[w++] = aw2;
                    data[w++] = aw3;
                    data[w++] = at;
                }
            }

            this.faces = new int[][]{
                    {0, 1, 2},
                    {3, 4, 5},
                    {6, 7, 8},
                    {9, 10, 11}
            };
            this.verts = new Vector3D[]{
                    new Vector3D(0,0,0), new Vector3D(0,0,0), new Vector3D(0,0,0),
                    new Vector3D(0,0,0), new Vector3D(0,0,0), new Vector3D(0,0,0),
                    new Vector3D(0,0,0), new Vector3D(0,0,0), new Vector3D(0,0,0),
                    new Vector3D(0,0,0), new Vector3D(0,0,0), new Vector3D(0,0,0)
            };
            return data;
        }
    }
}
