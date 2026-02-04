package com.example.game3d_opengl.game.player.player_logic;

import com.example.game3d_opengl.game.logic_abstraction.StateInfoNode;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public final class OutputNode extends StateInfoNode<OutputNode.Data> {
    public static final class Data {
        public Vector3D move;
        public float nextFallSpeed;
    }

    private final MoveNode moveNode;
    private Data data = new Data();

    public OutputNode(MoveNode moveNode) {
        this.moveNode = moveNode;
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
        MoveNode.Data move = moveNode.getData();
        data.move = move.move;
        data.nextFallSpeed = move.nextFallSpeed;
    }
}

