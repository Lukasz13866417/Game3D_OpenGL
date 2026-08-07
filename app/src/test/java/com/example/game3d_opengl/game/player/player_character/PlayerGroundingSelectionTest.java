package com.example.game3d_opengl.game.player.player_character;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.game3d_opengl.game.player.player_logic.FrameStartPlayerState;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Tile;
import com.example.game3d_opengl.game.terrain.terrain_api.main.TileProfile;
import com.example.game3d_opengl.rendering.object3d.UnbatchedObject3DWithOutline;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class PlayerGroundingSelectionTest {
    private static final float EPS = 1e-6f;

    private static void assertTriangleMatches(Tile tile, int triangleIndex, Vector3D a, Vector3D b, Vector3D c) {
        assertTrue(triangleIndex >= 0);
        assertSame(a, tile.getTriangleVertex(triangleIndex, 0));
        assertSame(b, tile.getTriangleVertex(triangleIndex, 1));
        assertSame(c, tile.getTriangleVertex(triangleIndex, 2));
    }

    @Test
    public void closest_tile_keeps_footing_when_farther_tile_is_probed_later() throws Exception {
        Player player = createPlayerForTest();
        placePlayer(player, 0f, -0.5f, -0.5f);
        FrameStartPlayerState frameState = getFrameState(player);
        Tile closerTile = createFlatTile(11L, -0.80f);
        Tile fartherTile = createFlatTile(12L, -0.90f);

        player.beginFrame(16f);
        player.beginTileInteractionSweep();
        player.interactWith(closerTile);
        player.interactWith(fartherTile);
        player.finishTileInteractionSweep();

        assertSame(closerTile, frameState.getTileBelow());
        assertTrue(frameState.getCollisionTriangleIndex() >= 0);
        assertEquals(0.30f, frameState.getNearestGroundDistance(), EPS);
    }

    @Test
    public void direct_tile_interaction_still_commits_footing_without_explicit_sweep() throws Exception {
        Player player = createPlayerForTest();
        placePlayer(player, 0f, -0.5f, -0.5f);
        FrameStartPlayerState frameState = getFrameState(player);
        Tile tile = createFlatTile(21L, -0.80f);

        player.beginFrame(16f);
        player.interactWith(tile);

        assertSame(tile, frameState.getTileBelow());
        assertTrue(frameState.getCollisionTriangleIndex() >= 0);
    }

    @Test
    public void sloped_tile_selects_expected_triangle_for_ground_projection() throws Exception {
        Player player = createPlayerForTest();
        placePlayer(player, -0.35f, -0.65f, -0.80f);
        FrameStartPlayerState frameState = getFrameState(player);
        Tile tile = new Tile(
                new Vector3D(-0.5f, -0.80f, 0f),
                new Vector3D(0.5f, -0.55f, 0f),
                new Vector3D(-0.5f, -1.10f, -1f),
                new Vector3D(0.5f, -0.70f, -1f),
                31L,
                false,
                TileProfile.NORMAL
        );

        player.beginFrame(16f);
        player.interactWith(tile);

        assertSame(tile, frameState.getTileBelow());
        assertTriangleMatches(
                tile,
                frameState.getCollisionTriangleIndex(),
                tile.nearLeft,
                tile.farRight,
                tile.farLeft
        );
    }

    @Test
    public void moving_away_from_ground_triangle_does_not_commit_footing() throws Exception {
        Player player = createPlayerForTest();
        placePlayer(player, 0f, -0.5f, -0.5f);
        FrameStartPlayerState frameState = getFrameState(player);
        Tile tile = createFlatTile(41L, -0.80f);

        player.beginFrame(16f);
        frameState.setLastMove(new Vector3D(0f, 0.01f, 0f));
        player.interactWith(tile);

        assertNull(frameState.getTileBelow());
        assertEquals(-1, frameState.getCollisionTriangleIndex());
        assertEquals(0.30f, frameState.getNearestGroundDistance(), EPS);
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

    private static UnbatchedObject3DWithOutline getObject(Player player) throws Exception {
        Field field = Player.class.getDeclaredField("object3D");
        field.setAccessible(true);
        return (UnbatchedObject3DWithOutline) field.get(player);
    }

    private static void placePlayer(Player player, float x, float y, float z) throws Exception {
        UnbatchedObject3DWithOutline object = getObject(player);
        object.objX = x;
        object.objY = y;
        object.objZ = z;
    }

    private static Tile createFlatTile(long id, float surfaceY) {
        return new Tile(
                new Vector3D(-0.5f, surfaceY, 0f),
                new Vector3D(0.5f, surfaceY, 0f),
                new Vector3D(-0.5f, surfaceY, -1f),
                new Vector3D(0.5f, surfaceY, -1f),
                id,
                false,
                TileProfile.NORMAL
        );
    }
}
