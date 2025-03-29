package com.example.game3d_opengl.game.terrain.grid.symbolic.segments;

import com.example.game3d_opengl.game.terrain.grid.symbolic.GridSegment;

class PreallocatedHashedSegmentsByLengthNodes extends HashedSegmentsByLength {

    protected final int LEAF_CNT;


    private static final int POOL_SIZE = 5;
    private static final int MAX_STRUCTURE_SIZE = 2000 * 24 + 1;
    private static final boolean[] slotInUse = new boolean[POOL_SIZE];

    private static final Node[][] nodes = new Node[POOL_SIZE][];

    private int poolSlotIndex = -1;

    static {
        for (int i = 0; i < POOL_SIZE; i++) {
            nodes[i] = new Node[MAX_STRUCTURE_SIZE];
            for (int j = 0; j < MAX_STRUCTURE_SIZE; ++j) {
                nodes[i][j] = new Node();
            }
            slotInUse[i] = false;
        }
    }


    private static class Node {
        int id, subtreeSize, subtreeTotalLen, subtreeMax, left, right, parent, lo, hi;
        boolean isLeftChild;

        public void clear() {
            id = 0;
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

        public boolean isLeaf() {
            return lo == hi;
        }

        public Node() {
            clear();
        }
    }

    private int cntNodes;
    private final Node root;
    private final Node[] treeNodes;

    public PreallocatedHashedSegmentsByLengthNodes(int totalRows, int nCols, boolean areSegmentsVertical) {
        super(totalRows, nCols, areSegmentsVertical);
        this.poolSlotIndex = claimFreeSlot();
        // Compute LEAF_CNT based on constraints
        int maxElements = totalRows * nCols * (Math.max(totalRows, nCols) + 1);
        this.LEAF_CNT = nextPowerOfTwo(maxElements);
        this.cntNodes = 0;
        this.treeNodes = nodes[poolSlotIndex];
        this.root = makeRoot(); // now, cntNodes will be 1
    }

    private Node makeRoot() {
        treeNodes[1].clear();
        treeNodes[1].lo = 0;
        treeNodes[1].hi = LEAF_CNT - 1;
        treeNodes[1].id = 1;
        cntNodes = 1;
        return treeNodes[1];
    }

    private Node appendNode(int parent, boolean isLeftChild) {
        int id = cntNodes + 1;
        treeNodes[id].clear();
        treeNodes[id].parent = parent;
        if(isLeftChild) {
            treeNodes[parent].left = id;
        }else{
            treeNodes[parent].right = id;
        }
        treeNodes[id].isLeftChild = isLeftChild;
        treeNodes[id].id = id;
        ++cntNodes;
        return treeNodes[cntNodes];
    }

    private void addLeft(Node from) {
        Node child = appendNode(from.id, true);
        child.lo = from.lo;
        child.hi = (from.lo + from.hi) / 2;
    }

    private void addRight(Node from) {
        Node child = appendNode(from.id, false);
        child.lo = ((from.lo + from.hi) / 2) + 1;
        child.hi = from.hi;
    }

    // Grab the first free slot
    private int claimFreeSlot() {
        for (int i = 0; i < POOL_SIZE; i++) {
            if (!slotInUse[i]) {
                slotInUse[i] = true;
                return i;
            }
        }
        throw new IllegalStateException("No free pool slots");
    }

    // ---------------------------------------
    //  4) Cleanup / Release
    // ---------------------------------------

    /**
     * Frees up this instance’s slot so it can be reused.
     * Optionally zero out arrays if you want to be absolutely sure
     * leftover data is cleared.
     */
    @Override
    public void freeArraysIfCleanedUp() {
        if (poolSlotIndex == -1) return;  // Already cleaned up or never assigned
        slotInUse[poolSlotIndex] = false;
        poolSlotIndex = -1;
    }

    @Override
    public void insert(int row, int col, int length) {
        // Adjust row, col, then compute hash
        --row;
        --col;
        int hash = segHash(row, col, length);

        insertRec(root.id, hash, length);
    }

    @Override
    public void delete(int row, int col, int length) {
        // Adjust row, col, then compute hash
        --row;
        --col;
        int hash = segHash(row, col, length);

        deleteRec(root.id, hash);
    }

    @Override
    public int countFittingSpaces(int spaceSize) {
        int minHash = segHash(0, 0, spaceSize);
        int res = 0;
        int v = 1;
        while (v != 0 && !treeNodes[v].isLeaf()) {
            int mid = (treeNodes[v].lo + treeNodes[v].hi) / 2;
            if(minHash <= mid){
                res += countSpacesInSubtree(treeNodes[v].right,spaceSize);
                v = treeNodes[v].left;
            }else{
                v = treeNodes[v].right;
            }
        }
        if(treeNodes[v].isLeaf() && treeNodes[v].lo >= minHash){
            res += countSpacesInSubtree(v,spaceSize);
        }
        return res;
    }

    @Override
    public GridSegment getKthFittingSpace(int spaceSize, int k) {
        int minHash = segHash(0,0,spaceSize);
        int v = lowerBound(minHash);
        if (treeNodes[v].subtreeTotalLen >= spaceSize) {
            int spacesHere = treeNodes[v].subtreeTotalLen - spaceSize + 1;
            if (k <= spacesHere) {
                return kthSpaceInSegment(treeNodes[v].lo, spaceSize, k);
            } else {
                k -= spacesHere;
            }
        }
        boolean comingFromLeftChild = treeNodes[v].isLeftChild;
        v = treeNodes[v].parent;
        // Go up to LCA(our leaf with min hash , leaf with hash of segment containing k-th fitting space)
        while (v >= 1 && !(comingFromLeftChild && countSpacesInSubtree(treeNodes[v].right, spaceSize) >= k)) {
            if(comingFromLeftChild) {
                k -= countSpacesInSubtree(treeNodes[v].right, spaceSize);
            }
            comingFromLeftChild = treeNodes[v].isLeftChild;
            v = treeNodes[v].parent;
        }
        // Go down from LCA to the leaf with hash of segment containing k-th fitting space.
        // First step must be handled separately because v's left subtree is on the path we've already travelled.
        v = treeNodes[v].right;
        while(!treeNodes[v].isLeaf()){
            // Every segment in subtree of v is large enough.
            int spacesInLeft = countSpacesInSubtree(treeNodes[v].left, spaceSize);
            if(spacesInLeft >= k){
                v = treeNodes[v].left;
            }else{
                k -= spacesInLeft;
                v = treeNodes[v].right;
            }
        }
        // Now we are at the leaf with hash of segment containing k-th fitting space. The hash is this leaf's index.
        int hash = treeNodes[v].lo;
        return kthSpaceInSegment(hash, spaceSize, k);
    }


    // --------------------------
    //         Helper methods
    // --------------------------

    /**
     * Recursive helper for insert:
     *  1) If leaf, store (size=1, totalLen=length, subtreeMax=length)
     *  2) Otherwise, descend left/right, then post-order update
     */
    private void insertRec(int nodeId, int hash, int length) {
        Node node = treeNodes[nodeId];
        if (node.isLeaf()) {
            // This leaf corresponds uniquely to 'hash'
            node.subtreeSize = 1;
            node.subtreeTotalLen = length;
            node.subtreeMax = hash;
            return;
        }

        int mid = (node.lo + node.hi) / 2;
        // Go to correct child, creating if needed
        if (hash <= mid) {
            if (node.left == 0) {
                addLeft(node);
            }
            insertRec(node.left, hash, length);
        } else {
            if (node.right == 0) {
                addRight(node);
            }
            insertRec(node.right, hash, length);
        }

        // Post-order update of this node's aggregations
        node.subtreeSize = treeNodes[node.left].subtreeSize
                + treeNodes[node.right].subtreeSize;

        node.subtreeTotalLen = treeNodes[node.left].subtreeTotalLen
                + treeNodes[node.right].subtreeTotalLen;

        node.subtreeMax = Math.max(treeNodes[node.left].subtreeMax, treeNodes[node.right].subtreeMax);
    }

    /**
     * Recursive helper for delete:
     *  1) If leaf, simply clear out the node (size=0, totalLen=0, max=0)
     *  2) Otherwise, descend left/right, then post-order update
     */
    private void deleteRec(int nodeId, int hash) {
        Node node = treeNodes[nodeId];
        if (node.isLeaf()) {
            // This leaf corresponds uniquely to 'hash'
            node.subtreeSize = 0;
            node.subtreeTotalLen = 0;
            node.subtreeMax = -1;
            return;
        }

        int mid = (node.lo + node.hi) / 2;
        // Descend into the correct child
        if (hash <= mid) {
            assert (node.left != 0);
            deleteRec(node.left, hash);
        } else {
            assert (node.right != 0);
            deleteRec(node.right, hash);
        }

        // Post-order update
        node.subtreeSize = treeNodes[node.left].subtreeSize
                + treeNodes[node.right].subtreeSize;

        node.subtreeTotalLen = treeNodes[node.left].subtreeTotalLen
                + treeNodes[node.right].subtreeTotalLen;

        node.subtreeMax = Math.max(treeNodes[node.left].subtreeMax, treeNodes[node.right].subtreeMax);
    }

    private int lowerBound(int hash){
        int v = 1;
        while(!treeNodes[v].isLeaf()){
            int mid = (treeNodes[v].lo + treeNodes[v].hi) / 2;
            if(hash <= mid && treeNodes[treeNodes[v].left].subtreeMax >= hash){
                v = treeNodes[v].left;
            }else{
                v = treeNodes[v].right;
            }
        }
        return v;
    }

    private int countSpacesInSubtree(int v, int spaceSize) { // assumes every segment there is large enough
        // if v=0 (nonexistent node), it returns 0, because all fields in treeNodes[0] are 0 / false.
        return treeNodes[v].subtreeTotalLen - (spaceSize - 1) * treeNodes[v].subtreeSize;
    }


}
