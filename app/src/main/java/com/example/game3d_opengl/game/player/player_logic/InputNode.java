package com.example.game3d_opengl.game.player.player_logic;

import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import androidx.annotation.NonNull;

import com.example.game3d_opengl.game.logic_abstraction.LogicInputNode;
import com.example.game3d_opengl.game.logic_abstraction.StateInfoNode;
import com.example.game3d_opengl.game.player.player_character.PlayerConfig;

public final class InputNode extends LogicInputNode<FrameStartPlayerState> {

    private FrameStartPlayerState frameStartPlayerState;

    public InputNode() {
    }

    @Override
    public void setData(FrameStartPlayerState what) {
        assert what != null;
        this.frameStartPlayerState = what;
    }

    @Override
    public FrameStartPlayerState getData() {
        assert frameStartPlayerState != null;
        return frameStartPlayerState;
    }

    @Override
    public void calc() {
        // Input node is a passive data source for now.
    }


}

