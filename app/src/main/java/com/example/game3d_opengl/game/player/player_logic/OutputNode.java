package com.example.game3d_opengl.game.player.player_logic;

import com.example.game3d_opengl.game.logic_abstraction.StateInfoNode;
import com.example.game3d_opengl.game.player.player_logic.jump.JumpLogicNode;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Converts the legacy player-logic graph's movement and jump decisions into one frame of output.
 */
public final class OutputNode extends StateInfoNode<OutputNode.Data> {
    private static final float LEGACY_JUMP_INITIAL_SPEED = 0.015f;
    private static final float LEGACY_BOUNCE_FALL_SPEED_THRESHOLD = 0.0015f;
    private static final float LEGACY_BOUNCE_SPEED_FACTOR = 1f;
    private static final float LEGACY_BOUNCE_ONE_TIME_OFFSET = 0.003f;

    private static final Vector3D ZERO_OFFSET = new Vector3D(0f, 0f, 0f);
    private static final Vector3D JUMP_OFFSET = new Vector3D(0f, 0.2f, 0f);

    public static final class Data {
        public Vector3D oneTimePosOffset;

        public Vector3D move;
        public float nextFallSpeed;
    }

    private final MoveNode.Data moveData;
    private final JumpLogicNode.Data jumpLogicData;
    private final InputNode input;
    private Data data = new Data();

    public OutputNode(
            MoveNode.Data moveData,
            JumpLogicNode.Data jumpLogicData,
            InputNode input
    ) {
        this.moveData = moveData;
        this.jumpLogicData = jumpLogicData;
        this.input = input;
    }


    @Override
    public Data getData() {
        return data;
    }

    @Override
    public void calc() {
        FrameStartPlayerState in = input != null ? input.getData() : null;
        boolean hasFooting = in != null && in.getTileBelow() != null;
        float fallSpeed = in != null ? in.getFallSpeed() : 0f;
        float impactSpeed = Math.max(0f, fallSpeed);
        float bounceFactor = Math.max(
                0f, Math.min(LEGACY_BOUNCE_SPEED_FACTOR, 0.999f));
        JumpLogicNode.JumpDecision jumpDecision =
                (jumpLogicData != null && jumpLogicData.decision != null)
                        ? jumpLogicData.decision
                        : JumpLogicNode.JumpDecision.NONE;
        if (jumpDecision == JumpLogicNode.JumpDecision.JUMP_NOW) {
            data.oneTimePosOffset = JUMP_OFFSET;
            data.move = moveData.move.setY(LEGACY_JUMP_INITIAL_SPEED);
            data.nextFallSpeed = 0;
        } else if (hasFooting
                && impactSpeed > LEGACY_BOUNCE_FALL_SPEED_THRESHOLD) {
            data.oneTimePosOffset = ZERO_OFFSET;
            data.move = moveData.move.setY(
                    impactSpeed * bounceFactor
                            + LEGACY_BOUNCE_ONE_TIME_OFFSET);
            data.nextFallSpeed = 0;
        } else {
            data.move = moveData.move;
            data.nextFallSpeed = moveData.nextFallSpeed;
            data.oneTimePosOffset = ZERO_OFFSET;
        }
    }
}

