package com.example.game3d_opengl.game.player.player_logic;

import static org.junit.Assert.assertEquals;

import com.example.game3d_opengl.game.player.player_character.PlayerConfig;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import org.junit.Test;

public class MoveNodeBoostSpeedTest {
    private static final float EPS = 1e-6f;

    @Test
    public void airborne_motion_uses_active_horizontal_speed() {
        PlayerConfig config = new PlayerConfig();
        FrameStartPlayerState state = new FrameStartPlayerState(config);
        InputNode inputNode = new InputNode();
        inputNode.setData(state);
        MoveNode moveNode = new MoveNode(inputNode, config);
        float boostedSpeed = config.playerSpeed * 1.5f;

        state.setMoveDir(new Vector3D(0f, 0f, -1f));
        state.setLastMove(new Vector3D(0f, 0.012f, 0f));
        state.setFallSpeed(0f);
        state.setActiveHorizontalSpeed(boostedSpeed);

        moveNode.calc();

        Vector3D move = moveNode.getData().move;
        float horizontalSpeed = (float) Math.sqrt(move.x * move.x + move.z * move.z);
        assertEquals(boostedSpeed, horizontalSpeed, EPS);
        assertEquals(0.012f, move.y, EPS);
    }
}
