package com.example.game3d_opengl.game.player.player_logic;

import android.util.Pair;

import com.example.game3d_opengl.game.logic_abstraction.LogicInputNode;
import com.example.game3d_opengl.game.logic_abstraction.StateInfoGraph;
import com.example.game3d_opengl.game.logic_abstraction.StateInfoNode;
import com.example.game3d_opengl.game.player.player_character.PlayerConfig;
import com.example.game3d_opengl.game.player.player_logic.jump.JumpConfig;
import com.example.game3d_opengl.game.player.player_logic.jump.JumpLogicNode;

import java.util.List;

/**
 * Player logic organized as a DAG of StateInfoNodes.
 */
public final class PlayerLogic extends StateInfoGraph<FrameStartPlayerState,
                                                      OutputNode.Data> {

    private static final ThreadLocal<PlayerConfig> CONSTRUCTION_CONFIG = new ThreadLocal<>();

    private final PlayerConfig config;
    private final JumpConfig jumpConfig;
    private final InputNode input ;
    private final EffectsNode effects ;
    private final JumpLogicNode jumpLogic ;
    private final MoveNode move ;
    private final OutputNode output ;

    public PlayerLogic(PlayerConfig config, JumpConfig jumpConfig) {
        this.config = config;
        this.jumpConfig = jumpConfig;
        this.input = new InputNode();
        this.effects = new EffectsNode(input, config);
        this.jumpLogic = new JumpLogicNode(input, effects.getData(), config, jumpConfig);
        this.move = new MoveNode(input, config);
        this.output = new OutputNode(move.getData(), jumpLogic.getData(), input, config);
    }

    public float getCumulativeSwipeDy(){
        return jumpLogic.getCumulativeSwipeDy();
    }


    @Override
    protected Pair<LogicInputNode<FrameStartPlayerState>, StateInfoNode<OutputNode.Data>> setupNodes(GraphSetupAPI graphSetupAPI) {

        assert input != null;
        assert effects != null;
        assert jumpLogic != null;
        assert move != null;
        assert output != null;

        graphSetupAPI.addNode(input, list());
        graphSetupAPI.addNode(effects, list(input));
        graphSetupAPI.addNode(jumpLogic, list(input, effects));
        graphSetupAPI.addNode(move, list(input));
        graphSetupAPI.addNode(output, list(move, jumpLogic, input));

        return new Pair<>(input, output);
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
