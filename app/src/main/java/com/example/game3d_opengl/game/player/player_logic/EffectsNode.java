package com.example.game3d_opengl.game.player.player_logic;

import com.example.game3d_opengl.game.logic_abstraction.StateInfoNode;
import com.example.game3d_opengl.game.player.player_character.PlayerConfig;

public final class EffectsNode extends StateInfoNode<EffectsNode.Data> {
    public static final class Data {
        public boolean infiniteJumps;
        public int extraJumpCharges;
    }

    private final InputNode input;
    private final PlayerConfig config;
    private Data data = new Data();

    public EffectsNode(InputNode input, PlayerConfig config) {
        this.input = input;
        this.config = config;
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

