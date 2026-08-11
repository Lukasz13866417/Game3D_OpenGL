package com.example.game3d_opengl.game.player.player_character;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.game3d_opengl.rendering.object3d.UnbatchedObject3DWithOutline;
import com.example.game3d_opengl.rendering.util3d.FColor;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Verifies shared player appearance changes without requiring an OpenGL context.
 */
public class PlayerAppearanceSwapTest {
    private static final float EPSILON = 1.0e-6f;

    @Test
    public void activePlayerAdoptsSelectedWheelAndPreservesTransform() throws Exception {
        UnbatchedObject3DWithOutline originalSharedObject =
                PlayerAssets.PLAYER_OBJECT;
        try {
            UnbatchedObject3DWithOutline oldObject =
                    UnbatchedObject3DWithOutline.wrap(null, null);
            oldObject.objX = 1f;
            oldObject.objY = 2f;
            oldObject.objZ = 3f;
            oldObject.objYaw = 4f;
            oldObject.objPitch = 5f;
            oldObject.objRoll = 6f;
            Player player = createPlayerForTest(oldObject);

            UnbatchedObject3DWithOutline selectedObject =
                    UnbatchedObject3DWithOutline.wrap(null, null);
            PlayerAssets.PLAYER_OBJECT = selectedObject;

            player.beginFrame(0f);

            assertSame(selectedObject, getPlayerObject(player));
            assertEquals(1f, selectedObject.objX, EPSILON);
            assertEquals(2f, selectedObject.objY, EPSILON);
            assertEquals(3f, selectedObject.objZ, EPSILON);
            assertEquals(4f, selectedObject.objYaw, EPSILON);
            assertEquals(5f, selectedObject.objPitch, EPSILON);
            assertEquals(6f, selectedObject.objRoll, EPSILON);
        } finally {
            PlayerAssets.PLAYER_OBJECT = originalSharedObject;
        }
    }

    @Test
    public void pastelThemeBecomesASaturatedNeonWheelColor() {
        FColor destination = FColor.CLR(0f, 0f, 0f, 0f);

        PlayerAssets.writeNeonThemeColor(
                FColor.CLR(0.76f, 0.50f, 0.63f, 1f),
                destination);

        assertEquals(0.98f, destination.r(), EPSILON);
        assertTrue(destination.g() < destination.b());
        assertTrue(destination.b() < destination.r());
        assertTrue(
                destination.r() - destination.g()
                        > 0.76f - 0.50f);
        assertEquals(1f, destination.a(), EPSILON);
    }

    @Test
    public void nearlyNeutralPastelsCannotCauseAnInstantNeonHueFlip() {
        FColor first = FColor.CLR(0f, 0f, 0f, 0f);
        FColor second = FColor.CLR(0f, 0f, 0f, 0f);

        PlayerAssets.writeNeonThemeColor(
                FColor.CLR(0.6001f, 0.6000f, 0.5999f, 1f),
                first);
        PlayerAssets.writeNeonThemeColor(
                FColor.CLR(0.5999f, 0.6000f, 0.6001f, 1f),
                second);

        float totalChange = Math.abs(first.r() - second.r())
                + Math.abs(first.g() - second.g())
                + Math.abs(first.b() - second.b());
        assertTrue(totalChange < 0.01f);
    }

    private static Player createPlayerForTest(
            UnbatchedObject3DWithOutline object
    ) throws Exception {
        Constructor<Player> constructor =
                Player.class.getDeclaredConstructor(
                        UnbatchedObject3DWithOutline.class);
        constructor.setAccessible(true);
        return constructor.newInstance(object);
    }

    private static UnbatchedObject3DWithOutline getPlayerObject(
            Player player
    ) throws Exception {
        Field field = Player.class.getDeclaredField("object3D");
        field.setAccessible(true);
        return (UnbatchedObject3DWithOutline) field.get(player);
    }
}
