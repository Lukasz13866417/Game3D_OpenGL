package com.example.game3d_opengl.game.terrain.track_elements.portal;

import com.example.game3d_opengl.game.terrain.track_elements.portal.assets.PortalAsset;
import com.example.game3d_opengl.game.terrain.track_elements.portal.assets.PortalAssetData;
import com.example.game3d_opengl.game.terrain.track_elements.portal.assets.PortalAssets;
import com.example.game3d_opengl.game.terrain.track_elements.portal.rendering.PortalSphereMesh3D;
import com.example.game3d_opengl.game.terrain.track_elements.portal.rendering.PortalSphereShaderPair;
import com.example.game3d_opengl.game.terrain.track_elements.portal.rendering.PortalWireframeMesh3D;
import com.example.game3d_opengl.game.terrain.track_elements.portal.rendering.PortalWireframeShaderPair;
import com.example.game3d_opengl.rendering.GPUResourceOwner;

/** Portal meshes and shader programs owned by one GL-context renderer registry. */
public final class PortalRenderResources implements GPUResourceOwner {
    private final PortalSphereMesh3D fillMesh;
    private final PortalWireframeMesh3D wireframeMesh;

    public PortalRenderResources() {
        this(PortalAssets.createPortalAsset());
    }

    PortalRenderResources(PortalAsset asset) {
        PortalAsset chosen = asset == null ? PortalAssets.createPortalAsset() : asset;
        PortalAssetData meshData = chosen.buildMeshData();
        fillMesh = new PortalSphereMesh3D.Builder()
                .verts(meshData.verts)
                .normals(meshData.normals)
                .faceGroups(meshData.faceGroups)
                .faces(deepCopyFaces(meshData.faces))
                .shader(PortalSphereShaderPair.createContextShader())
                .buildObject();
        wireframeMesh = meshData.edges.length == 0
                ? null
                : new PortalWireframeMesh3D.Builder()
                .verts(meshData.verts)
                .edges(deepCopyFaces(meshData.edges))
                .halfPx(0.5f * PortalConfig.WIREFRAME_PIXEL_WIDTH)
                .shader(PortalWireframeShaderPair.createContextShader())
                .buildObject();
    }

    PortalSphereMesh3D fillMesh() {
        return fillMesh;
    }

    PortalWireframeMesh3D wireframeMesh() {
        return wireframeMesh;
    }

    @Override public void reloadGPUResourcesRecursivelyOnContextLoss() {
        fillMesh.reloadGPUResourcesRecursivelyOnContextLoss();
        if (wireframeMesh != null) {
            wireframeMesh.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    @Override public void cleanupGPUResourcesRecursively() {
        fillMesh.cleanupGPUResourcesRecursively();
        if (wireframeMesh != null) {
            wireframeMesh.cleanupGPUResourcesRecursively();
        }
    }

    private static int[][] deepCopyFaces(int[][] source) {
        int[][] copy = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }
}
