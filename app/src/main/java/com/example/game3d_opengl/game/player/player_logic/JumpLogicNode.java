package com.example.game3d_opengl.game.player.player_logic;

import com.example.game3d_opengl.game.logic_abstraction.StateInfoNode;

public final class JumpLogicNode extends StateInfoNode<JumpLogicNode.Data> {
    public static final class Data {
        public boolean shouldJump;
    }

    private final InputNode input;
    private final EffectsNode effects;
    private Data data = new Data();

    public JumpLogicNode(InputNode input, EffectsNode effects) {
        this.input = input;
        this.effects = effects;
    }

    @Override
    public void setData(Data what) {
        this.data = what;
    }

    @Override
    public Data getData() {
        return data;
    }

    @Override
    public void calc() {
        InputNode.Data in = input.getData();
        EffectsNode.Data fx = effects.getData();
        boolean hasFooting = input.getTileBelow() != null;
        boolean canJump = hasFooting || (fx != null && (fx.infiniteJumps || fx.extraJumpCharges > 0));
        data.shouldJump = in != null && in.wantsJump && canJump;
    }
}

