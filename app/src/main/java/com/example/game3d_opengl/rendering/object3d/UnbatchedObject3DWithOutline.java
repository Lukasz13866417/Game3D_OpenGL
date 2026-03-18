package com.example.game3d_opengl.rendering.object3d;

import com.example.game3d_opengl.rendering.infill.Mesh3DInfill;
import com.example.game3d_opengl.rendering.mesh.MVPDrawArgs;
import com.example.game3d_opengl.rendering.wireframe.Mesh3DWireframe;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Holds an infill mesh and an optional wireframe mesh.
 * The Builder no longer creates wireframe; use wrap() for legacy/test code.
 */
public final class UnbatchedObject3DWithOutline extends UnbatchedObject3D {
    private final Mesh3DInfill fillMesh;
    private final Mesh3DWireframe edgeMesh;

    private UnbatchedObject3DWithOutline(Mesh3DInfill fillMesh, Mesh3DWireframe edgeMesh) {
        this.fillMesh = fillMesh;
        this.edgeMesh = edgeMesh;
    }

    @Override
    protected void drawUnderlying(float[] model, float[] vp) {
        if (fillMesh != null) fillMesh.draw(new MVPDrawArgs(model, vp));
        if (edgeMesh != null) {
            float[] mvp = new float[16];
            android.opengl.Matrix.multiplyMM(mvp, 0, vp, 0, model, 0);
            edgeMesh.draw(new MVPDrawArgs(mvp));
        }
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        if (fillMesh != null) fillMesh.reloadGPUResourcesRecursivelyOnContextLoss();
        if (edgeMesh != null) edgeMesh.reloadGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        if (fillMesh != null) fillMesh.cleanupGPUResourcesRecursively();
        if (edgeMesh != null) edgeMesh.cleanupGPUResourcesRecursively();
    }

    public static UnbatchedObject3DWithOutline wrap(Mesh3DInfill fillMesh, Mesh3DWireframe edgeMesh){
        return new UnbatchedObject3DWithOutline(fillMesh, edgeMesh);
    }

    public static class Builder {
        private Vector3D[] verts;
        private Vector3D[] normals;
        private int[][] faces;
        private FColor fillColor = FColor.CLR(1,1,1,1);
        private Float ambient, diffuse, specular, shininess;

        public Builder verts(Vector3D[] v){ this.verts = v; return this; }
        public Builder normals(Vector3D[] n){ this.normals = n; return this; }
        public Builder faces(int[][] f){ this.faces = f; return this; }
        public Builder fillColor(FColor c){ this.fillColor = c; return this; }
        public Builder ambient(float v){ this.ambient = v; return this; }
        public Builder diffuse(float v){ this.diffuse = v; return this; }
        public Builder specular(float v){ this.specular = v; return this; }
        public Builder shininess(float v){ this.shininess = v; return this; }

        public UnbatchedObject3DWithOutline build(){
            Mesh3DInfill.Builder fillBuilder = new Mesh3DInfill.Builder()
                    .verts(verts)
                    .faces(faces)
                    .fillColor(fillColor);
            if (normals != null) fillBuilder.normals(normals);
            if (ambient != null) fillBuilder.ambient(ambient);
            if (diffuse != null) fillBuilder.diffuse(diffuse);
            if (specular != null) fillBuilder.specular(specular);
            if (shininess != null) fillBuilder.shininess(shininess);
            Mesh3DInfill fill = fillBuilder.buildObject();

            return new UnbatchedObject3DWithOutline(fill, null);
        }
    }
}
