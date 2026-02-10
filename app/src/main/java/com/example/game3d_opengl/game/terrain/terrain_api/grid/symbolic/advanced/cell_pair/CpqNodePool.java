package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.cell_pair;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_length.segtree_implementation.PreallocatedResizableArrayStack;

class CpqNodePool {
    private CpqNode[] nodes;
    private final PreallocatedResizableArrayStack freeIndices;

    CpqNodePool(int initialCapacity) {
        nodes = new CpqNode[initialCapacity];
        for (int i = 0; i < initialCapacity; i++) {
            nodes[i] = new CpqNode(i);
        }
        freeIndices = new PreallocatedResizableArrayStack(initialCapacity);
        for (int i = initialCapacity - 1; i >= 1; i--) {
            freeIndices.pushBack(i);
        }
    }

    int newNode() {
        if (freeIndices.isEmpty()) {
            expandPool();
        }
        int index = freeIndices.popLast();
        nodes[index].clear();
        return index;
    }

    private void expandPool() {
        int oldCapacity = nodes.length;
        int newCapacity = oldCapacity * 2;
        CpqNode[] newNodes = new CpqNode[newCapacity];
        System.arraycopy(nodes, 0, newNodes, 0, oldCapacity);
        for (int i = oldCapacity; i < newCapacity; i++) {
            newNodes[i] = new CpqNode(i);
        }
        for (int i = newCapacity - 1; i >= oldCapacity; i--) {
            freeIndices.pushBack(i);
        }
        nodes = newNodes;
    }

    CpqNode at(int index) {
        return nodes[index];
    }

    void freeNode(int index) {
        nodes[index].clear();
        freeIndices.pushBack(index);
    }
}
