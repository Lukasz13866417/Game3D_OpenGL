package com.example.game3d_opengl.rendering.object3d;

import com.example.game3d_opengl.rendering.infill.Mesh3DInfill;
import com.example.game3d_opengl.rendering.mesh.MVPDrawArgs;
import com.example.game3d_opengl.rendering.wireframe.Mesh3DWireframe;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Holds two meshes: infill (Mesh3DInfill) and edges (Mesh3DWireframe),
 * and draws both in order. No transform wrapper yet.
 */
// TODO figure out what to do with this class.
public final class UnbatchedObject3DWithOutline extends UnbatchedObject3D {
    private final Mesh3DInfill fillMesh;
    private final Mesh3DWireframe edgeMesh;

    private UnbatchedObject3DWithOutline(Mesh3DInfill fillMesh, Mesh3DWireframe edgeMesh) {
        this.fillMesh = fillMesh;
        this.edgeMesh = edgeMesh;
    }

    @Override
    protected void drawUnderlying(float[] model, float[] vp) {
        // Compose MVP and pass via draw args (UnbatchedObject3D already multiplies model*vp before)
        float[] mvp = new float[16];
        android.opengl.Matrix.multiplyMM(mvp, 0, vp, 0, model, 0);
        if (fillMesh != null) fillMesh.draw(new MVPDrawArgs(mvp));
        if (edgeMesh != null) edgeMesh.draw(new MVPDrawArgs(mvp));
    }

    @Override
    public void reloadGPUResourcesRecursively() {
        if (fillMesh != null) fillMesh.reloadGPUResourcesRecursively();
        if (edgeMesh != null) edgeMesh.reloadGPUResourcesRecursively();
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
        private int[][] faces;
        private FColor fillColor = FColor.CLR(1,1,1,1);
        private FColor edgeColor = FColor.CLR(1,1,1,1);
        private float edgePixels = 2f;

        public Builder verts(Vector3D[] v){ this.verts = v; return this; }
        public Builder faces(int[][] f){ this.faces = f; return this; }
        public Builder fillColor(FColor c){ this.fillColor = c; return this; }
        public Builder edgeColor(FColor c){ this.edgeColor = c; return this; }
        public Builder edgePixels(float px){ this.edgePixels = px; return this; }

        public UnbatchedObject3DWithOutline build(){
            Mesh3DInfill fill = new Mesh3DInfill.Builder()
                    .verts(verts)
                    .faces(faces)
                    .fillColor(fillColor)
                    .buildObject();

            Mesh3DWireframe wire = new Mesh3DWireframe.Builder()
                    .verts(verts)
                    .faces(faces)
                    .edgeColor(edgeColor)
                    .pixelWidth(edgePixels)
                    .buildObject();

            return new UnbatchedObject3DWithOutline(fill, wire);
        }
    }
}


