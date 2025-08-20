package com.example.game3d_opengl.game.terrain_api.grid.symbolic.advanced.segments.by_length.segtree_implementation;


import com.example.game3d_opengl.game.terrain_api.grid.symbolic.GridSegment;

public class PreallocatedHashedSegmentsByLengthNodes extends HashedSegmentsByLength {

    private final int LEAF_CNT;

    private static final NodePool NODE_POOL = new NodePool(100000);

    private final int rootInd;
    private final Node root;

    public PreallocatedHashedSegmentsByLengthNodes(int totalRows, int nCols, boolean areSegmentsVertical) {
        super(totalRows, nCols, areSegmentsVertical);
        int maxElements = totalRows * nCols * (Math.max(totalRows, nCols) + 1);
        this.LEAF_CNT = nextPowerOfTwo(maxElements);
        this.rootInd = NODE_POOL.newNode();
        makeRoot();
        this.root = NODE_POOL.at(rootInd);
    }

    private void makeRoot() {
        NODE_POOL.at(rootInd).clear();
        NODE_POOL.at(rootInd).lo = 0;
        NODE_POOL.at(rootInd).hi = LEAF_CNT - 1;
        }

    private Node appendNode(int parent, boolean isLeftChild) {
        int id = NODE_POOL.newNode();
        NODE_POOL.at(id).clear();
        NODE_POOL.at(id).parent = parent;
        if(isLeftChild) {
            NODE_POOL.at(parent).left = id;
        }else{
            NODE_POOL.at(parent).right = id;
        }
        NODE_POOL.at(id).isLeftChild = isLeftChild;
        return NODE_POOL.at(id);
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

    /**
     * Frees up this instance’s slot so it can be reused.
     * Leftover data is cleared.
     */
    @Override
    public void destroy() {
        destroyRec(root);
    }

    private void destroyRec(Node node){
        if(node.left != 0){
            destroyRec(NODE_POOL.at(node.left));
        }
        if(node.right != 0){
            destroyRec(NODE_POOL.at(node.right));
        }
        NODE_POOL.freeNode(node.id);
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
        --row;
        --col;
        int hash = segHash(row, col, length);

        deleteRec(root.id, hash);
    }

    @Override
    public int countFittingSpaces(int spaceSize) {
        int minHash = segHash(0, 0, spaceSize);
        int res = 0;
        int v = rootInd;
        while (v != 0 && !NODE_POOL.at(v).isLeaf()) {
            int mid = (NODE_POOL.at(v).lo + NODE_POOL.at(v).hi) / 2;
            if(minHash <= mid){
                res += countSpacesInSubtree(NODE_POOL.at(v).right,spaceSize);
                v = NODE_POOL.at(v).left;
            }else{
                v = NODE_POOL.at(v).right;
            }
        }
        if(NODE_POOL.at(v).isLeaf() && NODE_POOL.at(v).lo >= minHash){
            res += countSpacesInSubtree(v,spaceSize);
        }
        return res;
    }

    @Override
    public GridSegment getKthFittingSpace(int spaceSize, int k) {
        int minHash = segHash(0,0,spaceSize);
        int v = lowerBound(minHash);
        if (NODE_POOL.at(v).subtreeTotalLen >= spaceSize) {
            int spacesHere = NODE_POOL.at(v).subtreeTotalLen - spaceSize + 1;
            if (k <= spacesHere) {
                return kthSpaceInSegment(NODE_POOL.at(v).lo, spaceSize, k);
            } else {
                k -= spacesHere;
            }
        }
        boolean comingFromLeftChild = NODE_POOL.at(v).isLeftChild;
        v = NODE_POOL.at(v).parent;
        // Go up to LCA(our leaf with min hash , leaf with hash of segment containing k-th fitting space)
        while (v != rootInd && !(comingFromLeftChild && countSpacesInSubtree(NODE_POOL.at(v).right, spaceSize) >= k)) {
            if(comingFromLeftChild) {
                k -= countSpacesInSubtree(NODE_POOL.at(v).right, spaceSize);
            }
            comingFromLeftChild = NODE_POOL.at(v).isLeftChild;
            v = NODE_POOL.at(v).parent;
        }
        // Go down from LCA to the leaf with hash of segment containing k-th fitting space.
        // First step must be handled separately because v's left subtree is on the path we've already travelled.
        v = NODE_POOL.at(v).right;
        while(!NODE_POOL.at(v).isLeaf()){
            // Every segment in subtree of v is large enough.
            int spacesInLeft = countSpacesInSubtree(NODE_POOL.at(v).left, spaceSize);
            if(spacesInLeft >= k){
                v = NODE_POOL.at(v).left;
            }else{
                k -= spacesInLeft;
                v = NODE_POOL.at(v).right;
            }
        }
        // Now we are at the leaf with hash of segment containing k-th fitting space. The hash is this leaf's index.
        int hash = NODE_POOL.at(v).lo;
        return kthSpaceInSegment(hash, spaceSize, k);
    }


    // --------------------------
    //         Helper methods
    // --------------------------

    /**
     * Recursive helper for insert:
     *  1) If is leaf, store (size=1, totalLen=length, subtreeMax=length)
     *  2) Otherwise, descend left/right, then post-order update
     */
    private void insertRec(int nodeId, int hash, int length) {
        Node node = NODE_POOL.at(nodeId);
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
        node.subtreeSize = NODE_POOL.at(node.left).subtreeSize
                + NODE_POOL.at(node.right).subtreeSize;

        node.subtreeTotalLen = NODE_POOL.at(node.left).subtreeTotalLen
                + NODE_POOL.at(node.right).subtreeTotalLen;

        node.subtreeMax = Math.max(NODE_POOL.at(node.left).subtreeMax, NODE_POOL.at(node.right).subtreeMax);
    }

    /**
     * Recursive helper for delete:
     *  1) If leaf, simply clear out the node (size=0, totalLen=0, max=0)
     *  2) Otherwise, descend left/right, then post-order update
     */
    private void deleteRec(int nodeId, int hash) {
        Node node = NODE_POOL.at(nodeId);
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
        node.subtreeSize = NODE_POOL.at(node.left).subtreeSize
                + NODE_POOL.at(node.right).subtreeSize;

        node.subtreeTotalLen = NODE_POOL.at(node.left).subtreeTotalLen
                + NODE_POOL.at(node.right).subtreeTotalLen;

        node.subtreeMax = Math.max(NODE_POOL.at(node.left).subtreeMax, NODE_POOL.at(node.right).subtreeMax);
    }

    private int lowerBound(int hash){
        int v = rootInd;
        while(!NODE_POOL.at(v).isLeaf()){
            int mid = (NODE_POOL.at(v).lo + NODE_POOL.at(v).hi) / 2;
            if(hash <= mid && NODE_POOL.at(NODE_POOL.at(v).left).subtreeMax >= hash){
                v = NODE_POOL.at(v).left;
            }else{
                v = NODE_POOL.at(v).right;
            }
        }
        return v;
    }

    private int countSpacesInSubtree(int v, int spaceSize) { // assumes every segment there is large enough
        // if v=0 (nonexistent node), it returns 0, because all fields in NODE_POOL.at(0) are 0 / false.
        return NODE_POOL.at(v).subtreeTotalLen - (spaceSize - 1) * NODE_POOL.at(v).subtreeSize;
    }


}
