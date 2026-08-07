package com.example.game3d_opengl.game.terrain.track_elements.spike;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;

import com.example.game3d_opengl.game.terrain.track_elements.portal.PortalLightingEnvironment;
import com.example.game3d_opengl.rendering.batching.SpikeCanonicalGeometryBuilder;
import com.example.game3d_opengl.rendering.infill.InfillShaderArgs;
import com.example.game3d_opengl.rendering.layout.VertexLayout;
import com.example.game3d_opengl.rendering.mesh.AbstractMesh3D;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Infill-only spike mesh using canonical spike vertices (weights + t) and per-instance uniforms.
 */
public final class SpikeInfillMesh3D extends AbstractMesh3D<
        SpikeInfillDrawArgs,
        SpikeInfillShaderPair<VertexLayout.SpikeCanonicalFillLayout>,
        VertexLayout.SpikeCanonicalFillLayout> {

    private final FColor color;

    private SpikeInfillMesh3D(Builder b) {
        super(b);
        this.color = b.color != null ? b.color : CLR(0, 0, 0, 1);
    }

    @Override
    protected void setVariableArgsValues(
            SpikeInfillDrawArgs args,
            SpikeInfillShaderPair<VertexLayout.SpikeCanonicalFillLayout> s) {
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
        fs.color = theme != null
                ? FColor.CLR(theme.r(), theme.g(), theme.b(), 1f)
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
        fs.specular = 0f;
        fs.shininess = 4f;

        s.setArgs(vs, fs);
    }

    public static final class Builder extends AbstractMesh3D.BaseBuilder<
            SpikeInfillMesh3D,
            Builder,
            SpikeInfillDrawArgs,
            SpikeInfillShaderPair<VertexLayout.SpikeCanonicalFillLayout>,
            VertexLayout.SpikeCanonicalFillLayout> {
        private FColor color;

        @Override
        protected Builder self() { return this; }

        @Override
        protected SpikeInfillMesh3D create() { return new SpikeInfillMesh3D(this); }

        public Builder shader(SpikeInfillShaderPair<VertexLayout.SpikeCanonicalFillLayout> shader) {
            super.shader(shader);
            return this;
        }

        public Builder color(FColor c) { this.color = c; return this; }

        @Override
        protected float[] setVertexData() {
            this.faces = SpikeCanonicalGeometryBuilder.buildFillFaces();
            this.verts = new Vector3D[SpikeCanonicalGeometryBuilder.FILL_VERTEX_COUNT];
            for (int i = 0; i < this.verts.length; i++) {
                this.verts[i] = new Vector3D(0, 0, 0);
            }
            return SpikeCanonicalGeometryBuilder.buildFillVertexData();
        }

        @Override
        protected VertexLayout.SpikeCanonicalFillLayout createLayout() {
            return VertexLayout.SpikeCanonicalFillLayout.INSTANCE;
        }
    }
}
