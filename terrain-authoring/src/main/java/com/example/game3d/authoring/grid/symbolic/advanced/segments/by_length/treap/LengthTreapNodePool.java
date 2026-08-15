package com.example.game3d.authoring.grid.symbolic.advanced.segments.by_length.treap;

import com.example.game3d.authoring.grid.symbolic.advanced.segments.by_length.segtree_implementation.PreallocatedResizableArrayStack;

public final class LengthTreapNodePool {
    private LengthTreapNode[] nodes;
    private final PreallocatedResizableArrayStack freeIndices;

    public LengthTreapNodePool(int initialCapacity) {
        int cap = Math.max(2, initialCapacity);
        nodes = new LengthTreapNode[cap];
        for (int i = 0; i < cap; ++i) {
            nodes[i] = new LengthTreapNode(i);
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

    public LengthTreapNode at(int idx) {
        return nodes[idx];
    }

    public void freeNode(int idx) {
        nodes[idx].clear();
        freeIndices.pushBack(idx);
    }

    private void expand() {
        int oldCap = nodes.length;
        int newCap = oldCap * 2;
        LengthTreapNode[] newNodes = new LengthTreapNode[newCap];
        System.arraycopy(nodes, 0, newNodes, 0, oldCap);
        for (int i = oldCap; i < newCap; ++i) {
            newNodes[i] = new LengthTreapNode(i);
        }
        for (int i = newCap - 1; i >= oldCap; --i) {
            freeIndices.pushBack(i);
        }
        nodes = newNodes;
    }

    public static final class LengthTreapNode {
        public int row;
        public int col;
        public int length;
        public int priority;
        public int left;
        public int right;
        public int parent;
        public int subtreeSize;
        public int subtreeTotalLen;
        public final int id;

        LengthTreapNode(int id) {
            this.id = id;
        }

        void clear() {
            row = 0;
            col = 0;
            length = 0;
            priority = 0;
            left = 0;
            right = 0;
            parent = 0;
            subtreeSize = 0;
            subtreeTotalLen = 0;
        }
    }
}
