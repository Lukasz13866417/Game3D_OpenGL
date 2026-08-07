package com.example.game3d_opengl.rendering.util3d;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import org.junit.Test;

import java.io.StringReader;
import java.util.Map;

/**
 * Verifies that multipart OBJ geometry keeps material groups and shared model bounds.
 */
public class ObjMaterialGroupLoaderTest {
    private static final float EPSILON = 1.0e-6f;

    @Test
    public void parseSeparatesMaterialsAndScalesWholeModel() throws Exception {
        String obj = ""
                + "v -1 -2 -4\n"
                + "v 1 -2 -4\n"
                + "v -1 2 -4\n"
                + "v 1 2 4\n"
                + "vn 1 1 1\n"
                + "usemtl dark\n"
                + "f 1//1 2//1 3//1\n"
                + "usemtl glow\n"
                + "f 2//1 3//1 4//1\n";

        Map<String, PreparedModelData> result = ObjMaterialGroupLoader.parse(
                new StringReader(obj), 4f, 6f, 8f);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("dark"));
        assertTrue(result.containsKey("glow"));
        assertModelBounds(result, 4f, 6f, 8f);

        Vector3D normal = result.get("dark").normals()[0];
        assertEquals(1f, Math.sqrt(normal.sqlen()), EPSILON);
    }

    private static void assertModelBounds(
            Map<String, PreparedModelData> parts,
            float expectedX,
            float expectedY,
            float expectedZ
    ) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (PreparedModelData model : parts.values()) {
            for (Vector3D vertex : model.verts()) {
                minX = Math.min(minX, vertex.x);
                minY = Math.min(minY, vertex.y);
                minZ = Math.min(minZ, vertex.z);
                maxX = Math.max(maxX, vertex.x);
                maxY = Math.max(maxY, vertex.y);
                maxZ = Math.max(maxZ, vertex.z);
            }
        }

        assertEquals(expectedX, maxX - minX, EPSILON);
        assertEquals(expectedY, maxY - minY, EPSILON);
        assertEquals(expectedZ, maxZ - minZ, EPSILON);
    }
}
