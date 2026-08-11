package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos.red_black;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_length.segtree_implementation.PreallocatedResizableArrayStack;

public final class RbNodePool {
    private RbNode[] nodes;
    private final PreallocatedResizableArrayStack freeIndices;

    public RbNodePool(int initialCapacity) {
        int cap = Math.max(2, initialCapacity);
        nodes = new RbNode[cap];
        for (int i = 0; i < cap; ++i) {
            nodes[i] = new RbNode(i);
        }
        freeIndices = new PreallocatedResizableArrayStack(cap);
        for (int i = cap - 1; i >= 1; --i) {
            freeIndices.pushBack(i);
        }
    }

    public int newNode() {
        if (freeIndices.isEmpty()) {
            expand();
        }
        int idx = freeIndices.popLast();
        nodes[idx].clear();
        return idx;
    }

    public RbNode at(int idx) {
        return nodes[idx];
    }

    public void freeNode(int idx) {
        nodes[idx].clear();
        freeIndices.pushBack(idx);
    }

    private void expand() {
        int oldCap = nodes.length;
        int newCap = oldCap * 2;
        RbNode[] newNodes = new RbNode[newCap];
        System.arraycopy(nodes, 0, newNodes, 0, oldCap);
        for (int i = oldCap; i < newCap; ++i) {
            newNodes[i] = new RbNode(i);
        }
        for (int i = newCap - 1; i >= oldCap; --i) {
            freeIndices.pushBack(i);
        }
        nodes = newNodes;
    }

    public static final class RbNode {
        public int row;
        public int col;
        public int length;
        public int left;
        public int right;
        public int parent;
        public boolean red = false;
        public final int id;

        RbNode(int id) {
            this.id = id;
        }

        void clear() {
            row = 0;
            col = 0;
            length = 0;
            left = 0;
            right = 0;
            parent = 0;
            red = false;
        }
    }
}
