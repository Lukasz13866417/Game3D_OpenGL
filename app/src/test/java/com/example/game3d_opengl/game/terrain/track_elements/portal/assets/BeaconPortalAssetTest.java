package com.example.game3d_opengl.game.terrain.track_elements.portal.assets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BeaconPortalAssetTest {
    @Test
    public void beacon_asset_contains_valid_shell_faces_and_wireframe_edges() {
        PortalAssetData data = new BeaconPortalAsset().buildMeshData();

        assertTrue("Beacon asset should provide vertices.", data.verts.length > 0);
        assertEquals("Normals should match vertex count.", data.verts.length * 3, data.normals.length);
        assertEquals("Face groups should match vertex count.", data.verts.length, data.faceGroups.length);
        assertTrue("Beacon asset should provide shell faces.", data.faces.length > 0);
        assertTrue("Beacon asset should provide wireframe edges.", data.edges.length > 0);

        for (int[] face : data.faces) {
            assertTrue("Beacon faces should have at least three corners.", face.length >= 3);
            for (int index : face) {
                assertTrue("Face index out of range: " + index, index >= 0 && index < data.verts.length);
            }
        }

        for (int[] edge : data.edges) {
            assertEquals("Each wireframe edge should have two endpoints.", 2, edge.length);
            assertTrue("Edge endpoints should be distinct.", edge[0] != edge[1]);
            assertTrue("Edge index out of range: " + edge[0], edge[0] >= 0 && edge[0] < data.verts.length);
            assertTrue("Edge index out of range: " + edge[1], edge[1] >= 0 && edge[1] < data.verts.length);
        }
    }
}
