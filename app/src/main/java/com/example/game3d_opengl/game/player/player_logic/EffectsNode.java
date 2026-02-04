package com.example.game3d_opengl.game.player.player_logic;

import com.example.game3d_opengl.game.logic_abstraction.StateInfoNode;

public final class EffectsNode extends StateInfoNode<EffectsNode.Data> {
    public static final class Data {
        public boolean infiniteJumps;
        public int extraJumpCharges;
    }

    private final InputNode input;
    private Data data = new Data();

    public EffectsNode(InputNode input) {
        this.input = input;
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
        // Placeholder: later inspect input.infos for potions/effects.
        InputNode.Data in = input.getData();
        if (in != null) {
            // TODO: convert infos into effects
        }
        data.infiniteJumps = false;
        data.extraJumpCharges = 0;
    }
}

