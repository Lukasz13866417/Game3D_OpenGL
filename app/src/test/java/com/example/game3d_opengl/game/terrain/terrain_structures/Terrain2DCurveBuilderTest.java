package com.example.game3d_opengl.game.terrain.terrain_structures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.junit.Test;

public class Terrain2DCurveBuilderTest {
    @Test
    public void advanced_curve_builder_defaults_to_horizontal_reset_off_and_vertical_reset_on()
            throws Exception {
        Terrain2DCurve curve = Terrain2DCurve.builder()
                .tilesToMake(8)
                .horizontalAngleDelta(0.5f)
                .verticalAngleDelta(0.25f)
                .build();

        assertFalse(readBooleanField(curve, "resetHorizontalAngle"));
        assertTrue(readBooleanField(curve, "resetVerticalAngle"));
        assertEquals(0, readIntField(curve, "horizontalFadeoutTiles"));
        assertEquals(0, readIntField(curve, "verticalFadeoutTiles"));
        assertEquals(8, readTotalTilesToMake(curve));
    }

    @Test
    public void basic_curve_builder_defaults_to_horizontal_reset_off_and_vertical_reset_on()
            throws Exception {
        BasicTerrain2DCurve curve = BasicTerrain2DCurve.builder()
                .tilesToMake(8)
                .horizontalAngleDelta(0.5f)
                .verticalAngleDelta(0.25f)
                .build();

        assertFalse(readBooleanField(curve, "resetHorizontalAngle"));
        assertTrue(readBooleanField(curve, "resetVerticalAngle"));
        assertEquals(0, readIntField(curve, "horizontalFadeoutTiles"));
        assertEquals(0, readIntField(curve, "verticalFadeoutTiles"));
        assertEquals(8, readTotalTilesToMake(curve));
    }

    @Test
    public void advanced_curve_builder_allows_overriding_reset_flags() throws Exception {
        Terrain2DCurve curve = Terrain2DCurve.builder()
                .tilesToMake(8)
                .horizontalAngleDelta(0.5f)
                .verticalAngleDelta(0.25f)
                .resetHorizontalAngle(true)
                .resetVerticalAngle(false)
                .horizontalAngleFadeoutTiles(3)
                .verticalAngleFadeoutTiles(5)
                .build();

        assertTrue(readBooleanField(curve, "resetHorizontalAngle"));
        assertFalse(readBooleanField(curve, "resetVerticalAngle"));
        assertEquals(3, readIntField(curve, "horizontalFadeoutTiles"));
        assertEquals(5, readIntField(curve, "verticalFadeoutTiles"));
        assertEquals(11, readTotalTilesToMake(curve));
    }

    @Test
    public void basic_curve_builder_allows_overriding_reset_flags() throws Exception {
        BasicTerrain2DCurve curve = BasicTerrain2DCurve.builder()
                .tilesToMake(8)
                .horizontalAngleDelta(0.5f)
                .verticalAngleDelta(0.25f)
                .resetHorizontalAngle(true)
                .resetVerticalAngle(false)
                .horizontalAngleFadeoutTiles(3)
                .verticalAngleFadeoutTiles(5)
                .build();

        assertTrue(readBooleanField(curve, "resetHorizontalAngle"));
        assertFalse(readBooleanField(curve, "resetVerticalAngle"));
        assertEquals(3, readIntField(curve, "horizontalFadeoutTiles"));
        assertEquals(5, readIntField(curve, "verticalFadeoutTiles"));
        assertEquals(11, readTotalTilesToMake(curve));
    }

    private static boolean readBooleanField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static int readIntField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static int readTotalTilesToMake(Object target) throws Exception {
        Class<?> cls = target.getClass();
        while (cls != null && !"BaseTerrainStructure".equals(cls.getSimpleName())) {
            cls = cls.getSuperclass();
        }
        if (cls == null) {
            throw new AssertionError("BaseTerrainStructure superclass not found");
        }
        Field field = cls.getDeclaredField("tilesToMake");
        field.setAccessible(true);
        return field.getInt(target);
    }
}
