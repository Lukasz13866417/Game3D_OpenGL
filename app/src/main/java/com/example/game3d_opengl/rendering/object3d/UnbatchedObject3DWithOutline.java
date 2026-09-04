package com.example.game3d_opengl.rendering.object3d;

import com.example.game3d_opengl.rendering.infill.Mesh3DInfill;
import com.example.game3d_opengl.rendering.mesh.MVPDrawArgs;
import com.example.game3d_opengl.rendering.wireframe.Mesh3DWireframe;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Holds one or more infill meshes under one transform and an optional wireframe mesh.
 * The Builder creates one infill mesh; use wrap() for multipart or legacy/test objects.
 */
public final class UnbatchedObject3DWithOutline extends UnbatchedObject3D {
    private final Mesh3DInfill[] fillMeshes;
    private final Mesh3DWireframe edgeMesh;
    private final MVPDrawArgs fillDrawArgs = new MVPDrawArgs(new float[16], new float[16]);
    private final float[] edgeMvp = new float[16];
    private final MVPDrawArgs edgeDrawArgs = new MVPDrawArgs(edgeMvp);

    private UnbatchedObject3DWithOutline(
            Mesh3DInfill[] fillMeshes,
            Mesh3DWireframe edgeMesh
    ) {
        this.fillMeshes = fillMeshes != null ? fillMeshes.clone() : new Mesh3DInfill[0];
        this.edgeMesh = edgeMesh;
    }

    @Override
    protected void drawUnderlying(float[] model, float[] vp) {
        drawFillMeshes(model, vp, null, false);
        drawEdgeMesh(model, vp);
    }

    /**
     * Draws this object except for one material mesh. This is used by the wheel exposure path to
     * establish an emitter-free body/occluder depth before the sharp emissive core is drawn.
     */
    public void drawExcludingFillMesh(
            float[] vp,
            Mesh3DInfill excludedMesh) {
        float[] model = currentModelMatrix();
        drawFillMeshes(model, vp, excludedMesh, false);
        drawEdgeMesh(model, vp);
    }

    /**
     * Draws all material meshes except a stable exclusion group and up to two optional meshes.
     * Identity comparison keeps this allocation-free for multipart presentation effects.
     */
    public void drawExcludingFillMeshes(
            float[] vp,
            Mesh3DInfill[] excludedGroup,
            Mesh3DInfill firstExcludedMesh,
            Mesh3DInfill secondExcludedMesh) {
        float[] model = currentModelMatrix();
        fillDrawArgs.model = model;
        fillDrawArgs.vp = vp;
        for (Mesh3DInfill fillMesh : fillMeshes) {
            if (fillMesh != null
                    && fillMesh != firstExcludedMesh
                    && fillMesh != secondExcludedMesh
                    && !containsIdentity(excludedGroup, fillMesh)) {
                fillMesh.draw(fillDrawArgs);
            }
        }
        drawEdgeMesh(model, vp);
    }

    /** Draws one owned material mesh under this object's current transform. */
    public void drawOnlyFillMesh(float[] vp, Mesh3DInfill includedMesh) {
        if (includedMesh == null) {
            return;
        }
        float[] model = currentModelMatrix();
        drawFillMeshes(model, vp, includedMesh, true);
    }

    private void drawFillMeshes(
            float[] model,
            float[] vp,
            Mesh3DInfill selectedMesh,
            boolean includeOnlySelected) {
        fillDrawArgs.model = model;
        fillDrawArgs.vp = vp;
        for (Mesh3DInfill fillMesh : fillMeshes) {
            boolean selected = fillMesh == selectedMesh;
            if (fillMesh != null
                    && (includeOnlySelected ? selected : !selected)) {
                fillMesh.draw(fillDrawArgs);
            }
        }
    }

    private void drawEdgeMesh(float[] model, float[] vp) {
        if (edgeMesh != null) {
            android.opengl.Matrix.multiplyMM(edgeMvp, 0, vp, 0, model, 0);
            edgeMesh.draw(edgeDrawArgs);
        }
    }

    private static boolean containsIdentity(
            Mesh3DInfill[] meshes,
            Mesh3DInfill candidate) {
        if (meshes == null) {
            return false;
        }
        for (Mesh3DInfill mesh : meshes) {
            if (mesh == candidate) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        for (Mesh3DInfill fillMesh : fillMeshes) {
            if (fillMesh != null) fillMesh.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (edgeMesh != null) edgeMesh.reloadGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        for (Mesh3DInfill fillMesh : fillMeshes) {
            if (fillMesh != null) fillMesh.cleanupGPUResourcesRecursively();
        }
        if (edgeMesh != null) edgeMesh.cleanupGPUResourcesRecursively();
    }

    public static UnbatchedObject3DWithOutline wrap(Mesh3DInfill fillMesh, Mesh3DWireframe edgeMesh){
        return new UnbatchedObject3DWithOutline(
                fillMesh == null ? null : new Mesh3DInfill[]{fillMesh},
                edgeMesh);
    }

    public static UnbatchedObject3DWithOutline wrapMultipart(
            Mesh3DInfill[] fillMeshes,
            Mesh3DWireframe edgeMesh
    ) {
        return new UnbatchedObject3DWithOutline(fillMeshes, edgeMesh);
    }

    /**
     * Builds the common single-material form of this transformed object.
     */
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

            return new UnbatchedObject3DWithOutline(
                    new Mesh3DInfill[]{fill}, null);
        }
    }
}
