package com.example.game3d.core.terrain;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class TerrainMeshDataTest {
    @Test
    public void rendererDataUsesAuthoritativeTrianglesWithoutRetriangulation() {
        TerrainWorld world = new TrackBuilder(4.0).straight(3.0).build();
        TerrainMeshData mesh = TerrainMeshData.from(world);

        assertEquals(18, mesh.positions.length);
        assertEquals(18, mesh.normals.length);
        assertArrayEquals(new long[]{1L, 2L}, mesh.triangleIds);
        TerrainTriangle first = world.triangles().get(0);
        assertEquals((float) first.a.x, mesh.positions[0], 0f);
        assertEquals((float) first.b.z, mesh.positions[5], 0f);
        assertEquals((float) first.c.y, mesh.positions[7], 0f);
    }
}
