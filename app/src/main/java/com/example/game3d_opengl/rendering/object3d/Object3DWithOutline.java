package com.example.game3d_opengl.rendering.object3d;

import com.example.game3d_opengl.rendering.object3d.infill.Mesh3DInfill;
import com.example.game3d_opengl.rendering.object3d.wireframe.Mesh3DWireframe;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Holds two meshes: infill (Mesh3DInfill) and edges (Mesh3DWireframe),
 * and draws both in order. No transform wrapper yet.
 */
public final class Object3DWithOutline extends Object3D {
    private final Mesh3DInfill fillMesh;
    private final Mesh3DWireframe edgeMesh;

    private Object3DWithOutline(Mesh3DInfill fillMesh, Mesh3DWireframe edgeMesh) {
        this.fillMesh = fillMesh;
        this.edgeMesh = edgeMesh;
    }

    @Override
    protected void drawUnderlying(float[] model, float[] vp) {
        if (fillMesh != null) fillMesh.draw(model, vp);
        if (edgeMesh != null) edgeMesh.draw(model, vp);
    }

    @Override
    public void reloadOwnedGPUResources() {
        if (fillMesh != null) fillMesh.reloadOwnedGPUResources();
        if (edgeMesh != null) edgeMesh.reloadOwnedGPUResources();
    }

    @Override
    public void cleanupOwnedGPUResources() {
        if (fillMesh != null) fillMesh.cleanupOwnedGPUResources();
        if (edgeMesh != null) edgeMesh.cleanupOwnedGPUResources();
    }

    public static Object3DWithOutline wrap(Mesh3DInfill fillMesh, Mesh3DWireframe edgeMesh){
        return new Object3DWithOutline(fillMesh, edgeMesh);
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

        public Object3DWithOutline build(){
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

            return new Object3DWithOutline(fill, wire);
        }
    }
}


