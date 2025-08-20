package com.example.game3d_opengl.game.terrain_api.grid.symbolic.advanced.segments.by_length.segtree_implementation;

class Node {
    int subtreeSize; // number of segments (not nodes)
    int subtreeTotalLen;
    int subtreeMax;
    int left;    // Index of left child in NodePool
    int right;   // Index of right child in NodePool
    int parent;  // Index of parent in NodePool
    int lo;
    int hi;
    final int id;
    boolean isLeftChild;

    public Node(int id){
        this.id = id;
    }

    void clear() {
        subtreeSize = 0;
        subtreeTotalLen = 0;
        subtreeMax = -1;
        left = 0;
        right = 0;
        parent = 0;
        lo = 0;
        hi = 0;
        isLeftChild = false;
    }

    boolean isLeaf() {
        return lo == hi;
    }
}
