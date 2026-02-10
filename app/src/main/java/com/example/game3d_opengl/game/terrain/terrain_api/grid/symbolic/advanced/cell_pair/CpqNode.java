package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.cell_pair;

class CpqNode {
    int cnt;       // number of free cells in [lo, hi]
    int lo, hi;    // interval bounds (code space)
    int left;      // index of left child in pool (0 = absent)
    int right;     // index of right child in pool (0 = absent)
    int parent;    // index of parent in pool
    final int id;  // this node's index in pool
    boolean isLeftChild;

    CpqNode(int id) {
        this.id = id;
    }

    void clear() {
        cnt = 0;
        lo = 0;
        hi = 0;
        left = 0;
        right = 0;
        parent = 0;
        isLeftChild = false;
    }

    boolean isLeaf() {
        return lo == hi;
    }
}
