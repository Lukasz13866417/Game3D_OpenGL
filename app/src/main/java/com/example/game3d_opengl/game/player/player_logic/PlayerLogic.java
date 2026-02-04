package com.example.game3d_opengl.game.player.player_logic;

import android.util.Pair;

import com.example.game3d_opengl.game.logic_abstraction.StateInfoGraph;
import com.example.game3d_opengl.game.logic_abstraction.StateInfoNode;

import java.util.List;

/**
 * Player logic organized as a DAG of StateInfoNodes.
 */
public final class PlayerLogic extends StateInfoGraph<InputNode.Data,
                                                      OutputNode.Data> {

    private InputNode inputNode;
    private MoveNode moveNode;

    @Override
    protected Pair<StateInfoNode<InputNode.Data>, StateInfoNode<OutputNode.Data>> setupNodes(GraphSetupAPI graphSetupAPI) {
        InputNode input = new InputNode();
        EffectsNode effects = new EffectsNode(input);
        JumpLogicNode jumpLogic = new JumpLogicNode(input, effects);
        MoveNode move = new MoveNode(input, effects, jumpLogic);
        OutputNode output = new OutputNode(move);

        this.inputNode = input;
        this.moveNode = move;

        graphSetupAPI.addNode(input, list());
        graphSetupAPI.addNode(effects, list(input));
        graphSetupAPI.addNode(jumpLogic, list(input, effects));
        graphSetupAPI.addNode(move, list(input, effects, jumpLogic));
        graphSetupAPI.addNode(output, list(move));

        return new Pair<>(input, output);
    }

    public MoveNode.Data getLastMoveData() {
        return moveNode == null ? null : moveNode.getData();
    }

    public InputNode getInputNode() {
        return inputNode;
    }

    @SafeVarargs
    private static List<StateInfoNode<?>> list(StateInfoNode<?>... nodes) {
        List<StateInfoNode<?>> out = new java.util.ArrayList<>();
        if (nodes != null) {
            for (StateInfoNode<?> n : nodes) {
                out.add(n);
            }
        }
        return out;
    }

}
