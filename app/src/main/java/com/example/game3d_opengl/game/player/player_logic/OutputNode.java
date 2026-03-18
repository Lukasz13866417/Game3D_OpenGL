package com.example.game3d_opengl.game.player.player_logic;

import com.example.game3d_opengl.game.logic_abstraction.StateInfoNode;
import com.example.game3d_opengl.game.player.player_character.PlayerConfig;
import com.example.game3d_opengl.game.player.player_logic.jump.JumpLogicNode;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public final class OutputNode extends StateInfoNode<OutputNode.Data> {
    public static final class Data {
        public Vector3D oneTimePosOffset;

        public Vector3D move;
        public float nextFallSpeed;
    }

    private final MoveNode.Data moveData;
    private final JumpLogicNode.Data jumpLogicData;
    private final InputNode input;
    private final PlayerConfig config;
    private Data data = new Data();

    public OutputNode(MoveNode.Data moveData, JumpLogicNode.Data jumpLogicData, InputNode input, PlayerConfig config) {
        this.moveData = moveData;
        this.jumpLogicData = jumpLogicData;
        this.input = input;
        this.config = config;
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
        float bounceFactor = Math.max(0f, Math.min(config.bounceSpeedFactor, 0.999f));
        JumpLogicNode.JumpDecision jumpDecision =
                (jumpLogicData != null && jumpLogicData.decision != null)
                        ? jumpLogicData.decision
                        : JumpLogicNode.JumpDecision.NONE;
        if (jumpDecision == JumpLogicNode.JumpDecision.JUMP_NOW) {
            data.oneTimePosOffset = new Vector3D(0,0.2f,0);
            data.move = moveData.move.setY(config.jumpInitialSpeed);
            data.nextFallSpeed = 0;
        } else if (hasFooting && impactSpeed > config.bounceFallSpeedThreshold) {
            data.oneTimePosOffset = new Vector3D(0,0,0);
            data.move = moveData.move.setY(impactSpeed * bounceFactor + config.bounceOneTimeOffset);
            data.nextFallSpeed = 0;
        } else {
            data.move = moveData.move;
            data.nextFallSpeed = moveData.nextFallSpeed;
            data.oneTimePosOffset = new Vector3D(0,0,0);
        }
    }
}

