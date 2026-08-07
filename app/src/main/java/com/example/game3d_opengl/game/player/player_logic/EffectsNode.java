package com.example.game3d_opengl.game.player.player_logic;

import com.example.game3d_opengl.game.logic_abstraction.StateInfoNode;

/**
 * Supplies temporary legacy player effects to the old movement graph.
 */
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
    public Data getData() {
        return data;
    }

    @Override
    public void calc() {
        // Placeholder: later inspect input.infos for potions/effects.
        FrameStartPlayerState in = input.getData();
        if (in != null) {
            // TODO: convert infos into effects
        }
        data.infiniteJumps = false;
        data.extraJumpCharges = 0;
    }
}

