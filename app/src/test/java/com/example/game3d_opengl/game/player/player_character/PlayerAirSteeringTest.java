package com.example.game3d_opengl.game.player.player_character;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.game3d_opengl.game.player.player_logic.FrameStartPlayerState;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Tile;
import com.example.game3d_opengl.game.terrain.terrain_api.main.TileProfile;
import com.example.game3d_opengl.rendering.object3d.UnbatchedObject3DWithOutline;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class PlayerAirSteeringTest {
    private static final float EPS = 1e-6f;

    @Test
    public void horizontal_swipe_rotates_facing_but_not_trajectory_while_airborne() throws Exception {
        Player player = createPlayerForTest();
        FrameStartPlayerState frameState = getFrameState(player);
        Vector3D beforeDir = frameState.getDir();
        Vector3D beforeMoveDir = frameState.getMoveDir();
        float beforeYaw = getObject(player).objYaw;

        invokeApplyInput(player, 16f, 120f, 0f);

        assertFalse(Vector3D.approxEq(beforeDir, frameState.getDir(), EPS));
        assertTrue(Vector3D.approxEq(beforeMoveDir, frameState.getMoveDir(), EPS));
        assertFalse(Math.abs(beforeYaw - getObject(player).objYaw) <= EPS);
    }

    @Test
    public void horizontal_swipe_rotates_player_while_grounded() throws Exception {
        Player player = createPlayerForTest();
        FrameStartPlayerState frameState = getFrameState(player);
        frameState.setTileBelow(createTile(TileProfile.NORMAL, false));
        Vector3D beforeDir = frameState.getDir();

        invokeApplyInput(player, 16f, 120f, 0f);

        assertFalse(Vector3D.approxEq(beforeDir, frameState.getDir(), EPS));
        assertTrue(Vector3D.approxEq(frameState.getDir(), frameState.getMoveDir(), EPS));
    }

    @Test
    public void boost_tile_speed_persists_after_liftoff() throws Exception {
        Player player = createPlayerForTest();
        FrameStartPlayerState frameState = getFrameState(player);
        PlayerConfig config = getConfig(player);

        frameState.setTileBelow(createTile(TileProfile.BOOST_RAMP_LAUNCH, false));
        invokeApplyInput(player, 16f, 0f, 0f);
        float boostedSpeed = frameState.getActiveHorizontalSpeed();

        assertEquals(
                TileProfile.BOOST_RAMP_LAUNCH.applyHorizontalSpeed(config.playerSpeed),
                boostedSpeed,
                EPS
        );

        frameState.setTileBelow(null);
        invokeApplyInput(player, 16f, 0f, 0f);

        assertEquals(boostedSpeed, frameState.getActiveHorizontalSpeed(), EPS);
    }

    private static Player createPlayerForTest() throws Exception {
        Constructor<Player> ctor = Player.class.getDeclaredConstructor(UnbatchedObject3DWithOutline.class);
        ctor.setAccessible(true);
        return ctor.newInstance(UnbatchedObject3DWithOutline.wrap(null, null));
    }

    private static FrameStartPlayerState getFrameState(Player player) throws Exception {
        Field field = Player.class.getDeclaredField("frameStartState");
        field.setAccessible(true);
        return (FrameStartPlayerState) field.get(player);
    }

    private static PlayerConfig getConfig(Player player) throws Exception {
        Field field = Player.class.getDeclaredField("config");
        field.setAccessible(true);
        return (PlayerConfig) field.get(player);
    }

    private static UnbatchedObject3DWithOutline getObject(Player player) throws Exception {
        Field field = Player.class.getDeclaredField("object3D");
        field.setAccessible(true);
        return (UnbatchedObject3DWithOutline) field.get(player);
    }

    private static void invokeApplyInput(Player player, float dtMillis, float swipeDx, float swipeDy)
            throws Exception {
        Method method = Player.class.getDeclaredMethod("applyInput", float.class, float.class, float.class);
        method.setAccessible(true);
        method.invoke(player, dtMillis, swipeDx, swipeDy);
    }

    private static Tile createTile(TileProfile profile, boolean isEmptySegment) {
        return new Tile(
                new Vector3D(-0.5f, 0f, 0f),
                new Vector3D(0.5f, 0f, 0f),
                new Vector3D(-0.5f, 0f, -1f),
                new Vector3D(0.5f, 0f, -1f),
                1L,
                isEmptySegment,
                profile
        );
    }
}
