package com.example.game3d_opengl.game.terrain.track_elements.portal.rendering;

import com.example.game3d_opengl.rendering.layout.VertexLayout;
import com.example.game3d_opengl.rendering.mesh.AbstractMesh3D;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public final class PortalSphereMesh3D
        extends AbstractMesh3D<
                PortalSphereDrawArgs,
                PortalSphereShaderPair<VertexLayout.PositionNormalFaceGroupLayout>,
                VertexLayout.PositionNormalFaceGroupLayout> {

    private final PortalSphereShaderArgs.VS vs = new PortalSphereShaderArgs.VS();
    private final PortalSphereShaderArgs.FS fs = new PortalSphereShaderArgs.FS();

    private PortalSphereMesh3D(Builder builder) {
        super(builder);
    }

    @Override
    protected void setVariableArgsValues(
            PortalSphereDrawArgs args,
            PortalSphereShaderPair<VertexLayout.PositionNormalFaceGroupLayout> targetShader) {
        vs.vp = args.vp;
        vs.centerX = args.centerX;
        vs.centerY = args.centerY;
        vs.centerZ = args.centerZ;
        vs.radius = args.radius;
        vs.rotation = args.rotation;

        fs.colorA = args.colorA;
        fs.colorB = args.colorB;
        fs.lightX = args.lightX;
        fs.lightY = args.lightY;
        fs.lightZ = args.lightZ;
        fs.lightColor = args.lightColor;
        fs.cameraX = args.cameraX;
        fs.cameraY = args.cameraY;
        fs.cameraZ = args.cameraZ;
        fs.ambient = args.ambient;
        fs.diffuse = args.diffuse;
        fs.specular = args.specular;
        fs.shininess = args.shininess;

        targetShader.setArgs(vs, fs);
    }

    public static final class Builder
            extends BaseBuilder<
                    PortalSphereMesh3D,
                    Builder,
                    PortalSphereDrawArgs,
                    PortalSphereShaderPair<VertexLayout.PositionNormalFaceGroupLayout>,
                    VertexLayout.PositionNormalFaceGroupLayout> {

        private float[] normals;
        private float[] faceGroups;

        public Builder normals(float[] normals) {
            this.normals = normals;
            return this;
        }

        public Builder faceGroups(float[] faceGroups) {
            this.faceGroups = faceGroups;
            return this;
        }

        @Override
        public void checkValid() {
            shader(PortalSphereShaderPair.getSharedShader());
            super.checkValid();
        }

        @Override
        protected float[] setVertexData() {
            final int n = verts.length;
            float[] out = new float[n * 7];
            for (int i = 0; i < n; ++i) {
                Vector3D v = verts[i];
                int o = i * 7;
                out[o]     = v.x;
                out[o + 1] = v.y;
                out[o + 2] = v.z;
                out[o + 3] = normals != null ? normals[i * 3]     : 0f;
                out[o + 4] = normals != null ? normals[i * 3 + 1] : 0f;
                out[o + 5] = normals != null ? normals[i * 3 + 2] : 0f;
                out[o + 6] = faceGroups != null ? faceGroups[i] : 0f;
            }
            return out;
        }

        @Override
        protected VertexLayout.PositionNormalFaceGroupLayout createLayout() {
            return VertexLayout.PositionNormalFaceGroupLayout.INSTANCE;
        }

        @Override
        protected Builder self() { return this; }

        @Override
        protected PortalSphereMesh3D create() { return new PortalSphereMesh3D(this); }
    }
}
