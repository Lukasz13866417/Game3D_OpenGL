package com.example.game3d_opengl.game.player.player_character;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.game3d.core.simulation.PhysicsConfig;
import com.example.game3d_opengl.rendering.object3d.UnbatchedObject3DWithOutline;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class PlayerTurnVisualCompositionTest {
    private static final float FRAME_120_HZ_MILLIS = 1_000f / 120f;
    private static final float EPSILON = 1e-5f;

    @Test
    public void facingDeltaChangesOnlyTheComposedModelYaw()
            throws Exception {
        Player player = createPlayerForTest();
        UnbatchedObject3DWithOutline object = getObject(player);
        player.enableAuthoritativeSimulation();
        player.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);
        player.beginTurnVisualHold();
        Vector3D authoritativeDirection = player.getDir();

        setAuthoritativeYaw(player, Math.toRadians(2.0));
        player.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);

        assertTrue(object.objYaw < -2f);
        assertTrue(Vector3D.approxEq(
                authoritativeDirection,
                player.getDir(),
                EPSILON
        ));
    }

    @Test
    public void cosmeticStrengthIsIndependentOfForwardSpeed()
            throws Exception {
        Player slowPlayer = createPlayerForTest();
        Player fastPlayer = createPlayerForTest();
        slowPlayer.setMoveSpeed(16f);
        fastPlayer.setMoveSpeed(96f);
        slowPlayer.enableAuthoritativeSimulation();
        fastPlayer.enableAuthoritativeSimulation();
        slowPlayer.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);
        fastPlayer.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);
        slowPlayer.beginTurnVisualHold();
        fastPlayer.beginTurnVisualHold();

        setAuthoritativeYaw(slowPlayer, Math.toRadians(2.0));
        setAuthoritativeYaw(fastPlayer, Math.toRadians(2.0));
        slowPlayer.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);
        fastPlayer.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);

        assertEquals(
                getObject(slowPlayer).objYaw,
                getObject(fastPlayer).objYaw,
                EPSILON
        );
    }

    @Test
    public void recompositionUsesAuthoritativeBaseWithoutTransformDrift()
            throws Exception {
        Player player = createPlayerForTest();
        UnbatchedObject3DWithOutline object = getObject(player);
        player.enableAuthoritativeSimulation();
        player.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);
        player.beginTurnVisualHold();
        setAuthoritativeYaw(player, Math.toRadians(2.0));
        player.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);
        float firstOffset = object.objYaw - (-2f);

        player.updateTurnVisualAfterCamera(0f);

        assertEquals(-2f + firstOffset, object.objYaw, EPSILON);
    }

    @Test
    public void inactivityReturnsModelToAuthoritativeYaw()
            throws Exception {
        Player player = createPlayerForTest();
        UnbatchedObject3DWithOutline object = getObject(player);
        object.objYaw = 37f;
        player.enableAuthoritativeSimulation();
        player.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);
        player.beginTurnVisualHold();
        setAuthoritativeYaw(player, Math.toRadians(-35.0));
        player.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);
        assertTrue(object.objYaw < 35f);

        for (int tick = 0; tick < 7; tick++) {
            setAuthoritativeYaw(player, Math.toRadians(-35.0));
            player.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);
        }
        player.updateTurnVisualAfterCamera(
                PlayerTurnVisualEffect.RETURN_DELAY_MILLIS + 1_000f
        );

        assertEquals(35f, object.objYaw, EPSILON);
    }

    @Test
    public void resetImmediatelyRestoresAuthoritativeYaw()
            throws Exception {
        Player player = createPlayerForTest();
        UnbatchedObject3DWithOutline object = getObject(player);
        object.objYaw = -23f;
        player.enableAuthoritativeSimulation();
        player.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);
        player.beginTurnVisualHold();
        setAuthoritativeYaw(player, Math.toRadians(21.0));
        player.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);
        assertTrue(object.objYaw > -21f);

        player.resetTurnVisual();

        assertEquals(-21f, object.objYaw, EPSILON);
    }

    @Test
    public void lifecycleResetIsConsumedByNextGlThreadComposition()
            throws Exception {
        Player player = createPlayerForTest();
        UnbatchedObject3DWithOutline object = getObject(player);
        object.objYaw = 11f;
        player.enableAuthoritativeSimulation();
        player.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);
        player.beginTurnVisualHold();
        setAuthoritativeYaw(player, Math.toRadians(-13.0));
        player.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);
        float accentedYaw = object.objYaw;
        assertTrue(accentedYaw > 13f);

        player.requestTurnVisualReset();
        // The lifecycle thread does not mutate render state directly.
        assertEquals(accentedYaw, object.objYaw, EPSILON);

        player.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);
        assertEquals(13f, object.objYaw, EPSILON);
    }

    @Test
    public void touchReleaseAnimatesBackToAuthoritativeYawWithoutDelay()
            throws Exception {
        Player player = createPlayerForTest();
        UnbatchedObject3DWithOutline object = getObject(player);
        player.enableAuthoritativeSimulation();
        player.beginTurnVisualHold();
        setAuthoritativeYaw(player, Math.toRadians(2.0));
        player.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);
        assertTrue(object.objYaw < -2f);
        float accentedYaw = object.objYaw;

        player.endTurnVisualHold();

        assertEquals(accentedYaw, object.objYaw, EPSILON);
        player.updateTurnVisualAfterCamera(80f);
        assertTrue(Math.abs(object.objYaw - -2f)
                < Math.abs(accentedYaw - -2f));
        assertTrue(Math.abs(object.objYaw - -2f) > 0f);
        setAuthoritativeYaw(player, Math.toRadians(4.0));
        player.updateTurnVisualAfterCamera(1_000f);
        assertEquals(-4f, object.objYaw, EPSILON);
    }

    @Test
    public void renderInterpolationTailCannotRetargetCosmeticYaw()
            throws Exception {
        Player player = createPlayerForTest();
        UnbatchedObject3DWithOutline object = getObject(player);
        player.enableAuthoritativeSimulation();
        player.beginTurnVisualHold();
        for (int yawDegrees = 2; yawDegrees <= 16; yawDegrees += 2) {
            setAuthoritativeYaw(player, Math.toRadians(yawDegrees));
            player.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);
        }

        setAuthoritativeYawComponents(
                player,
                Math.toRadians(16.5),
                Math.toRadians(18.0)
        );
        player.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);
        float offsetBeforeRenderTail = object.objYaw - -16.5f;

        setAuthoritativeYawComponents(
                player,
                Math.toRadians(17.5),
                Math.toRadians(18.0)
        );
        player.updateTurnVisualAfterCamera(FRAME_120_HZ_MILLIS);
        float offsetAfterRenderTail = object.objYaw - -17.5f;

        assertTrue(Math.abs(offsetAfterRenderTail)
                >= Math.abs(offsetBeforeRenderTail));
    }

    private static Player createPlayerForTest() throws Exception {
        Constructor<Player> constructor =
                Player.class.getDeclaredConstructor(
                        UnbatchedObject3DWithOutline.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                UnbatchedObject3DWithOutline.wrap(null, null));
    }

    private static UnbatchedObject3DWithOutline getObject(
            Player player) throws Exception {
        Field field = Player.class.getDeclaredField("object3D");
        field.setAccessible(true);
        return (UnbatchedObject3DWithOutline) field.get(player);
    }

    private static void setAuthoritativeYaw(
            Player player,
            double logicalYawRadians) throws Exception {
        setAuthoritativeYawComponents(
                player,
                logicalYawRadians,
                logicalYawRadians
        );
    }

    private static void setAuthoritativeYawComponents(
            Player player,
            double renderedLogicalYawRadians,
            double canonicalLogicalYawRadians) throws Exception {
        Field renderedYaw = Player.class.getDeclaredField(
                "authoritativeRenderYawRadians");
        renderedYaw.setAccessible(true);
        renderedYaw.setDouble(player, renderedLogicalYawRadians);

        Field turnVisualYaw = Player.class.getDeclaredField(
                "authoritativeTurnVisualYawRadians");
        turnVisualYaw.setAccessible(true);
        turnVisualYaw.setDouble(player, canonicalLogicalYawRadians);

        Field turnVisualTime = Player.class.getDeclaredField(
                "authoritativeTurnVisualTimeNanos");
        turnVisualTime.setAccessible(true);
        turnVisualTime.setLong(
                player,
                turnVisualTime.getLong(player) + PhysicsConfig.FIXED_DT_NANOS
        );

        Field modelYaw = Player.class.getDeclaredField(
                "authoritativeModelYawDegrees");
        modelYaw.setAccessible(true);
        modelYaw.setFloat(
                player,
                (float) -Math.toDegrees(renderedLogicalYawRadians)
        );
    }
}
