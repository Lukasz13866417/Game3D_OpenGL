package com.example.game3d_opengl.rendering.infill;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;

import android.opengl.Matrix;

import com.example.game3d_opengl.game.terrain.track_elements.portal.PortalLightingEnvironment;
import com.example.game3d_opengl.rendering.RenderConfig;
import com.example.game3d_opengl.rendering.layout.VertexLayout;
import com.example.game3d_opengl.rendering.mesh.AbstractMesh3D;
import com.example.game3d_opengl.rendering.mesh.BaseMeshDrawArgs;
import com.example.game3d_opengl.rendering.shader.MeshShaderPair;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public class Mesh3DInfill extends AbstractMesh3D<
        BaseMeshDrawArgs,
        MeshShaderPair<InfillShaderArgs.VS, InfillShaderArgs.FS, VertexLayout.PositionNormalLayout>,
        VertexLayout.PositionNormalLayout> {

    public Mesh3DInfill(Builder builder){
        super(builder);
        this.fillColor = builder.fillColor;
        this.ambient = builder.ambient;
        this.diffuse = builder.diffuse;
        this.specular = builder.specular;
        this.shininess = builder.shininess;
    }

    private static final FColor WHITE = CLR(1f, 1f, 1f, 1f);

    private FColor fillColor;
    private final float ambient, diffuse, specular, shininess;

    private final float[] tmpMvp = new float[16];
    private final float[] identityMatrix = new float[16];
    private final InfillShaderArgs.VS vs = new InfillShaderArgs.VS();
    private final InfillShaderArgs.FS fs = new InfillShaderArgs.FS();

    public void setFillColor(FColor fillColor) {
        if (fillColor != null) {
            this.fillColor = fillColor;
        }
    }

    @Override
    protected void setVariableArgsValues(
            BaseMeshDrawArgs args,
            MeshShaderPair<InfillShaderArgs.VS, InfillShaderArgs.FS, VertexLayout.PositionNormalLayout> s) {
        fs.color = fillColor != null ? fillColor : WHITE;
        fs.lightX = 0f;
        fs.lightY = 0f;
        fs.lightZ = 0f;
        fs.cameraX = 0f;
        fs.cameraY = 0f;
        fs.cameraZ = 0f;
        fs.isDepthPass = 0;

        if (args.model != null) {
            Matrix.multiplyMM(tmpMvp, 0, args.vp, 0, args.model, 0);
            vs.mvp = tmpMvp;
            vs.modelMatrix = args.model;

            Vector3D lightPos = PortalLightingEnvironment.getLightPos();
            Vector3D cameraPos = PortalLightingEnvironment.getCameraPos();
            FColor lightColor = PortalLightingEnvironment.getLightColor();
            if (lightPos != null) {
                fs.lightX = lightPos.x; fs.lightY = lightPos.y; fs.lightZ = lightPos.z;
            }
            if (cameraPos != null) {
                fs.cameraX = cameraPos.x; fs.cameraY = cameraPos.y; fs.cameraZ = cameraPos.z;
            }
            fs.lightColor = lightColor != null ? lightColor : WHITE;
            fs.ambient = ambient;
            fs.diffuse = diffuse;
            fs.specular = specular;
            fs.shininess = shininess;
        } else {
            vs.mvp = args.vp;
            Matrix.setIdentityM(identityMatrix, 0);
            vs.modelMatrix = identityMatrix;
            fs.lightColor = WHITE;
            fs.ambient = 1f;
            fs.diffuse = 0f;
            fs.specular = 0f;
            fs.shininess = 1f;
        }

        s.setArgs(vs, fs);
    }

    public static class Builder extends BaseBuilder<
            Mesh3DInfill,
            Builder,
            BaseMeshDrawArgs,
            MeshShaderPair<InfillShaderArgs.VS, InfillShaderArgs.FS, VertexLayout.PositionNormalLayout>,
            VertexLayout.PositionNormalLayout> {
        private FColor fillColor = CLR(1,1,1,1);
        private Vector3D[] suppliedNormals;
        private float ambient = 0.4f;
        private float diffuse = 0.8f;
        private float specular = 0.6f;
        private float shininess = 20f;

        public Builder fillColor(FColor c){ this.fillColor = c; return this; }

        public Builder normals(Vector3D[] n){ this.suppliedNormals = n; return this; }

        public Builder ambient(float v){ this.ambient = v; return this; }
        public Builder diffuse(float v){ this.diffuse = v; return this; }
        public Builder specular(float v){ this.specular = v; return this; }
        public Builder shininess(float v){ this.shininess = v; return this; }

        @Override
        public void checkValid() {
            if (RenderConfig.FLAT_SHADING) {
                shader(FlatLitShaderPair.getSharedShader());
            } else {
                shader(InfillShaderPair.getSharedShader());
            }
            super.checkValid();
            assert fillColor != null;
            for (int[] f : faces) {
                for (int idx : f) {
                    if (idx < 0 || idx >= verts.length) {
                        throw new IllegalStateException("Face index out of range: "
                                                           + idx + " (verts=" + verts.length + ")");
                    }
                }
            }
            if (suppliedNormals != null && suppliedNormals.length != verts.length) {
                throw new IllegalStateException("normals.length (" + suppliedNormals.length
                        + ") != verts.length (" + verts.length + ")");
            }
        }

        @Override
        protected float[] setVertexData() {
            if (RenderConfig.FLAT_SHADING) {
                return buildFlatShadedData();
            } else {
                return buildSmoothShadedData();
            }
        }

        private float[] buildFlatShadedData() {
            int totalTris = 0;
            for (int[] face : faces) {
                if (face != null && face.length >= 3) totalTris += (face.length - 2);
            }
            int totalVerts = totalTris * 3;
            float[] out = new float[totalVerts * 6];
            int[][] newFaces = new int[totalTris][3];
            int vi = 0;
            int fi = 0;
            for (int[] face : faces) {
                if (face == null || face.length < 3) continue;
                Vector3D a = verts[face[0]];
                Vector3D b = verts[face[1]];
                Vector3D c = verts[face[2]];
                float e1x = b.x - a.x, e1y = b.y - a.y, e1z = b.z - a.z;
                float e2x = c.x - a.x, e2y = c.y - a.y, e2z = c.z - a.z;
                float nx = e1y * e2z - e1z * e2y;
                float ny = e1z * e2x - e1x * e2z;
                float nz = e1x * e2y - e1y * e2x;
                float nLen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (nLen > 1e-8f) { nx /= nLen; ny /= nLen; nz /= nLen; }

                for (int t = 1; t < face.length - 1; t++) {
                    Vector3D v0 = verts[face[0]];
                    Vector3D v1 = verts[face[t]];
                    Vector3D v2 = verts[face[t + 1]];
                    int o = vi * 6;
                    out[o]   = v0.x; out[o+1] = v0.y; out[o+2] = v0.z;
                    out[o+3] = nx;   out[o+4] = ny;   out[o+5] = nz;
                    o += 6;
                    out[o]   = v1.x; out[o+1] = v1.y; out[o+2] = v1.z;
                    out[o+3] = nx;   out[o+4] = ny;   out[o+5] = nz;
                    o += 6;
                    out[o]   = v2.x; out[o+1] = v2.y; out[o+2] = v2.z;
                    out[o+3] = nx;   out[o+4] = ny;   out[o+5] = nz;
                    newFaces[fi] = new int[]{vi, vi + 1, vi + 2};
                    vi += 3;
                    fi++;
                }
            }
            faces = newFaces;
            verts = new Vector3D[totalVerts];
            for (int i = 0; i < totalVerts; i++) {
                int o = i * 6;
                verts[i] = new Vector3D(out[o], out[o + 1], out[o + 2]);
            }
            suppliedNormals = null;
            return out;
        }

        private float[] buildSmoothShadedData() {
            final int n = verts.length;
            float[] norms;
            if (suppliedNormals != null) {
                norms = new float[n * 3];
                for (int i = 0; i < n; i++) {
                    Vector3D sn = suppliedNormals[i];
                    norms[i * 3]     = sn.x;
                    norms[i * 3 + 1] = sn.y;
                    norms[i * 3 + 2] = sn.z;
                }
            } else {
                norms = computePerVertexNormals(verts, faces);
            }
            float[] out = new float[n * 6];
            for (int i = 0; i < n; ++i) {
                Vector3D v = verts[i];
                int o = i * 6;
                out[o    ] = v.x;
                out[o + 1] = v.y;
                out[o + 2] = v.z;
                out[o + 3] = norms[i * 3];
                out[o + 4] = norms[i * 3 + 1];
                out[o + 5] = norms[i * 3 + 2];
            }
            return out;
        }

        private static float[] computePerVertexNormals(Vector3D[] verts, int[][] faces) {
            float[] normals = new float[verts.length * 3];
            for (int[] face : faces) {
                if (face == null || face.length < 3) continue;
                Vector3D a = verts[face[0]];
                Vector3D b = verts[face[1]];
                Vector3D c = verts[face[2]];
                float e1x = b.x - a.x, e1y = b.y - a.y, e1z = b.z - a.z;
                float e2x = c.x - a.x, e2y = c.y - a.y, e2z = c.z - a.z;
                float nx = e1y * e2z - e1z * e2y;
                float ny = e1z * e2x - e1x * e2z;
                float nz = e1x * e2y - e1y * e2x;
                for (int idx : face) {
                    normals[idx * 3]     += nx;
                    normals[idx * 3 + 1] += ny;
                    normals[idx * 3 + 2] += nz;
                }
            }
            for (int i = 0; i < verts.length; ++i) {
                float x = normals[i * 3], y = normals[i * 3 + 1], z = normals[i * 3 + 2];
                float len = (float) Math.sqrt(x * x + y * y + z * z);
                if (len > 1e-8f) {
                    normals[i * 3]     /= len;
                    normals[i * 3 + 1] /= len;
                    normals[i * 3 + 2] /= len;
                }
            }
            return normals;
        }

        @Override
        protected VertexLayout.PositionNormalLayout createLayout() {
            return VertexLayout.PositionNormalLayout.INSTANCE;
        }

        @Override
        public Builder self() {
            return this;
        }

        @Override
        public Mesh3DInfill create() {
            return new Mesh3DInfill(this);
        }

    }
}
