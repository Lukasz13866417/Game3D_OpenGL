package com.example.game3d_opengl.game.logic_abstraction;

import android.util.Pair;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public abstract class StateInfoGraph
                               <InputDataType, OutputDataType> {
    private final ArrayList<StateInfoNode<?>> nodes;
    private final ArrayList<List<? extends StateInfoNode<?>>> transposeGraphAdjacencyList;

    private boolean isOrderingSet;

    private int[] ordering;
    private final int nodeCnt;

    private final StateInfoNode<InputDataType> inputNode;
    private final StateInfoNode<OutputDataType> outputNode;

    public StateInfoGraph(){
        nodes = new ArrayList<>();
        transposeGraphAdjacencyList = new ArrayList<>();
        this.isOrderingSet = false;
        this.setupAPI = new GraphSetupAPI();
        Pair<StateInfoNode<InputDataType>, StateInfoNode<OutputDataType>>
                                                    inputAndOutputNodes = setupNodes(this.setupAPI);
        this.inputNode = inputAndOutputNodes.first;
        this.outputNode = inputAndOutputNodes.second;
        this.nodeCnt = nodes.size(); // Bug fix: nodeCnt must be set after setup.
    }

    // The purpose of this is to only allow subclasses to add nodes.
    // That way, they'll have to provide an input node & output node.
    public class GraphSetupAPI {
        public void addNode(StateInfoNode<?> node, List<? extends StateInfoNode<?>> dependencies) { // Renamed for clarity
            node.indInOriginalOrdering = nodes.size(); // Bug fix: set original index
            nodes.add(node);
            transposeGraphAdjacencyList.add(dependencies);
        }
    }
    GraphSetupAPI setupAPI;
    protected abstract Pair<StateInfoNode<InputDataType>, StateInfoNode<OutputDataType>>
                                                            setupNodes(GraphSetupAPI graphSetupAPI);

    private void setOrdering(){
        assert !isOrderingSet : "Ordering is already set. Can't be set again.";
        this.ordering = new int[nodeCnt];

        // Reset nReadyUsers before sorting to ensure correctness
        for(StateInfoNode<?> node : nodes) {
            node.nReadyUsers = 0;
        }

        ArrayDeque<StateInfoNode<?>> queue = new ArrayDeque<>();
        for(StateInfoNode<?> currNode : nodes){
            // nUsers is the out-degree of the original graph, which is the
            // in-degree for the transposed graph. Start with nodes of in-degree 0.
            if(currNode.nUsers == 0){
                queue.add(currNode);
            }
        }

        int orderingCurrSize = 0;
        while(!queue.isEmpty()){
            StateInfoNode<?> currNode = queue.removeFirst();
            int indInOriginal = currNode.indInOriginalOrdering;
            ordering[orderingCurrSize++] = indInOriginal;

            // In the transposed graph, the neighbors of currNode are its dependencies in the original graph.
            List<? extends StateInfoNode<?>> users = transposeGraphAdjacencyList.get(indInOriginal);
            for(StateInfoNode<?> dep : users){
                // We have processed a node that uses `dep`, so we decrement its in-degree counter.
                // When the counter reaches the total number of users, `dep` is ready to be processed.
                if(++dep.nReadyUsers == dep.nUsers){ // Bug fix: Use pre-increment
                    queue.add(dep);
                }
            }
        }

        assert orderingCurrSize == nodeCnt : "Cycle detected in graph or logic error in sort";

        // Reverse the reverse topological order to get the final topological order.
        for (int i = 0, j = ordering.length - 1; i < j; i++, j--) {
            int tmp = ordering[i];
            ordering[i] = ordering[j];
            ordering[j] = tmp;
        }
        this.isOrderingSet = true;
    }

    public OutputDataType runLogic(InputDataType input){
        if(!isOrderingSet){
            setOrdering();
        }
        this.inputNode.setData(input);
        for(int i=0;i<nodeCnt;++i){
            nodes.get(ordering[i]).calc();
        }
        return this.outputNode.getData();
    }
}
