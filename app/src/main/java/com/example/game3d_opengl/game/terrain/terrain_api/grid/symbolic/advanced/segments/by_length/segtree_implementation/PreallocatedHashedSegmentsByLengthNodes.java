package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_length.segtree_implementation;


import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridSegment;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.GridBuildScratch;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.PartialSegmentHandlerResourcePack;

import java.util.Arrays;

public class PreallocatedHashedSegmentsByLengthNodes extends HashedSegmentsByLength {
    private static final int DEFAULT_NODE_POOL_CAPACITY = 512;

    private final int LEAF_CNT;
    private final NodePool nodePool;

    private final int rootInd;
    private final Node root;

    public PreallocatedHashedSegmentsByLengthNodes(int totalRows, int nCols, boolean areSegmentsVertical) {
        this(totalRows, nCols, areSegmentsVertical, new NodePool(DEFAULT_NODE_POOL_CAPACITY));
    }

    public PreallocatedHashedSegmentsByLengthNodes(
            int totalRows,
            int nCols,
            boolean areSegmentsVertical,
            PartialSegmentHandlerResourcePack resourcePack
    ) {
        this(totalRows, nCols, areSegmentsVertical, resourcePack.segtreeNodePool());
    }

    private PreallocatedHashedSegmentsByLengthNodes(
            int totalRows, int nCols, boolean areSegmentsVertical, NodePool nodePool
    ) {
        super(totalRows, nCols, areSegmentsVertical);
        this.nodePool = nodePool;
        int maxElements = totalRows * nCols * (Math.max(totalRows, nCols) + 1);
        this.LEAF_CNT = nextPowerOfTwo(maxElements);
        this.rootInd = nodePool.newNode();
        makeRoot();
        this.root = nodePool.at(rootInd);
    }

    public static PreallocatedHashedSegmentsByLengthNodes fromFreeSegments(
            int totalRows, int nCols, boolean areSegmentsVertical, GridSegment[] freeSegments
    ) {
        return fromFreeSegments(
                totalRows,
                nCols,
                areSegmentsVertical,
                new NodePool(DEFAULT_NODE_POOL_CAPACITY),
                freeSegments
        );
    }

    public static PreallocatedHashedSegmentsByLengthNodes fromFreeSegments(
            int totalRows,
            int nCols,
            boolean areSegmentsVertical,
            PartialSegmentHandlerResourcePack resourcePack,
            GridSegment[] freeSegments
    ) {
        return fromFreeSegments(
                totalRows,
                nCols,
                areSegmentsVertical,
                resourcePack.segtreeNodePool(),
                freeSegments
        );
    }

    private static PreallocatedHashedSegmentsByLengthNodes fromFreeSegments(
            int totalRows,
            int nCols,
            boolean areSegmentsVertical,
            NodePool nodePool,
            GridSegment[] freeSegments
    ) {
        PreallocatedHashedSegmentsByLengthNodes out =
                new PreallocatedHashedSegmentsByLengthNodes(totalRows, nCols, areSegmentsVertical, nodePool);
        if (freeSegments == null || freeSegments.length == 0) {
            return out;
        }
        GridSegment[] sortedSegments = freeSegments.clone();
        Arrays.sort(sortedSegments, (a, b) -> {
            int hashA = out.segHash(a.row - 1, a.col - 1, a.length);
            int hashB = out.segHash(b.row - 1, b.col - 1, b.length);
            return Integer.compare(hashA, hashB);
        });
        int[] hashes = new int[sortedSegments.length];
        int[] lengths = new int[sortedSegments.length];
        for (int i = 0; i < sortedSegments.length; ++i) {
            GridSegment seg = sortedSegments[i];
            hashes[i] = out.segHash(seg.row - 1, seg.col - 1, seg.length);
            lengths[i] = seg.length;
        }
        out.buildRootFromSortedHashes(hashes, lengths);
        return out;
    }

    public static PreallocatedHashedSegmentsByLengthNodes fromScratch(
            int totalRows,
            int nCols,
            boolean areSegmentsVertical,
            PartialSegmentHandlerResourcePack resourcePack,
            GridBuildScratch scratch
    ) {
        PreallocatedHashedSegmentsByLengthNodes out =
                new PreallocatedHashedSegmentsByLengthNodes(
                        totalRows,
                        nCols,
                        areSegmentsVertical,
                        resourcePack.segtreeNodePool()
                );
        if (scratch == null || scratch.size() == 0) {
            return out;
        }
        scratch.sortByHash(totalRows, nCols);
        out.buildRootFromScratchSortedHashes(scratch);
        return out;
    }

    private void makeRoot() {
        nodePool.at(rootInd).clear();
        nodePool.at(rootInd).lo = 0;
        nodePool.at(rootInd).hi = LEAF_CNT - 1;
    }

    private Node appendNode(int parent, boolean isLeftChild) {
        int id = nodePool.newNode();
        nodePool.at(id).clear();
        nodePool.at(id).parent = parent;
        if(isLeftChild) {
            nodePool.at(parent).left = id;
        }else{
            nodePool.at(parent).right = id;
        }
        nodePool.at(id).isLeftChild = isLeftChild;
        return nodePool.at(id);
    }

    private void buildRootFromSortedHashes(int[] hashes, int[] lengths) {
        makeRoot();
        if (hashes.length == 0) {
            return;
        }
        buildRec(rootInd, hashes, lengths, 0, hashes.length);
    }

    private void buildRootFromScratchSortedHashes(GridBuildScratch scratch) {
        makeRoot();
        if (scratch.size() == 0) {
            return;
        }
        buildRec(rootInd, scratch, 0, scratch.size());
    }

    private void buildRec(int nodeId, int[] hashes, int[] lengths, int from, int toExclusive) {
        if (from >= toExclusive) {
            return;
        }
        Node node = nodePool.at(nodeId);
        if (node.lo == node.hi) {
            if (toExclusive != from + 1 || hashes[from] != node.lo) {
                throw new IllegalArgumentException("Invalid sorted hashes for bulk segtree construction.");
            }
            node.subtreeSize = 1;
            node.subtreeTotalLen = lengths[from];
            node.subtreeMax = hashes[from];
            return;
        }

        int mid = (node.lo + node.hi) / 2;
        int split = upperBound(hashes, from, toExclusive, mid);
        if (from < split) {
            addLeft(node);
            buildRec(node.left, hashes, lengths, from, split);
        }
        if (split < toExclusive) {
            addRight(node);
            buildRec(node.right, hashes, lengths, split, toExclusive);
        }
        node.subtreeSize = nodePool.at(node.left).subtreeSize + nodePool.at(node.right).subtreeSize;
        node.subtreeTotalLen =
                nodePool.at(node.left).subtreeTotalLen + nodePool.at(node.right).subtreeTotalLen;
        node.subtreeMax = Math.max(nodePool.at(node.left).subtreeMax, nodePool.at(node.right).subtreeMax);
    }

    private void buildRec(int nodeId, GridBuildScratch scratch, int from, int toExclusive) {
        if (from >= toExclusive) {
            return;
        }
        Node node = nodePool.at(nodeId);
        if (node.lo == node.hi) {
            int hash = hashAt(scratch, from);
            if (toExclusive != from + 1 || hash != node.lo) {
                throw new IllegalArgumentException("Invalid sorted hashes for bulk segtree construction.");
            }
            node.subtreeSize = 1;
            node.subtreeTotalLen = scratch.lengthAt(from);
            node.subtreeMax = hash;
            return;
        }

        int mid = (node.lo + node.hi) / 2;
        int split = upperBound(scratch, from, toExclusive, mid);
        if (from < split) {
            addLeft(node);
            buildRec(node.left, scratch, from, split);
        }
        if (split < toExclusive) {
            addRight(node);
            buildRec(node.right, scratch, split, toExclusive);
        }
        node.subtreeSize = nodePool.at(node.left).subtreeSize + nodePool.at(node.right).subtreeSize;
        node.subtreeTotalLen =
                nodePool.at(node.left).subtreeTotalLen + nodePool.at(node.right).subtreeTotalLen;
        node.subtreeMax = Math.max(nodePool.at(node.left).subtreeMax, nodePool.at(node.right).subtreeMax);
    }

    private static int upperBound(int[] hashes, int from, int toExclusive, int value) {
        int lo = from;
        int hi = toExclusive;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (hashes[mid] <= value) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private int upperBound(GridBuildScratch scratch, int from, int toExclusive, int value) {
        int lo = from;
        int hi = toExclusive;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (hashAt(scratch, mid) <= value) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private int hashAt(GridBuildScratch scratch, int index) {
        return segHash(
                scratch.rowAt(index) - 1,
                scratch.colAt(index) - 1,
                scratch.lengthAt(index)
        );
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
            destroyRec(nodePool.at(node.left));
        }
        if(node.right != 0){
            destroyRec(nodePool.at(node.right));
        }
        nodePool.freeNode(node.id);
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
        while (v != 0 && !nodePool.at(v).isLeaf()) {
            int mid = (nodePool.at(v).lo + nodePool.at(v).hi) / 2;
            if(minHash <= mid){
                res += countSpacesInSubtree(nodePool.at(v).right,spaceSize);
                v = nodePool.at(v).left;
            }else{
                v = nodePool.at(v).right;
            }
        }
        if(nodePool.at(v).isLeaf() && nodePool.at(v).lo >= minHash){
            res += countSpacesInSubtree(v,spaceSize);
        }
        return res;
    }

    @Override
    public GridSegment getKthFittingSpace(int spaceSize, int k) {
        int minHash = segHash(0,0,spaceSize);
        int v = lowerBound(minHash);
        if (nodePool.at(v).subtreeTotalLen >= spaceSize) {
            int spacesHere = nodePool.at(v).subtreeTotalLen - spaceSize + 1;
            if (k <= spacesHere) {
                return kthSpaceInSegment(nodePool.at(v).lo, spaceSize, k);
            } else {
                k -= spacesHere;
            }
        }
        boolean comingFromLeftChild = nodePool.at(v).isLeftChild;
        v = nodePool.at(v).parent;
        // Go up to LCA(our leaf with min hash , leaf with hash of segment containing k-th fitting space)
        while (v != rootInd && !(comingFromLeftChild && countSpacesInSubtree(nodePool.at(v).right, spaceSize) >= k)) {
            if(comingFromLeftChild) {
                k -= countSpacesInSubtree(nodePool.at(v).right, spaceSize);
            }
            comingFromLeftChild = nodePool.at(v).isLeftChild;
            v = nodePool.at(v).parent;
        }
        // Go down from LCA to the leaf with hash of segment containing k-th fitting space.
        // First step must be handled separately because v's left subtree is on the path we've already travelled.
        v = nodePool.at(v).right;
        while(!nodePool.at(v).isLeaf()){
            // Every segment in subtree of v is large enough.
            int spacesInLeft = countSpacesInSubtree(nodePool.at(v).left, spaceSize);
            if(spacesInLeft >= k){
                v = nodePool.at(v).left;
            }else{
                k -= spacesInLeft;
                v = nodePool.at(v).right;
            }
        }
        // Now we are at the leaf with hash of segment containing k-th fitting space. The hash is this leaf's index.
        int hash = nodePool.at(v).lo;
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
        Node node = nodePool.at(nodeId);
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
        node.subtreeSize = nodePool.at(node.left).subtreeSize
                + nodePool.at(node.right).subtreeSize;

        node.subtreeTotalLen = nodePool.at(node.left).subtreeTotalLen
                + nodePool.at(node.right).subtreeTotalLen;

        node.subtreeMax = Math.max(nodePool.at(node.left).subtreeMax, nodePool.at(node.right).subtreeMax);
    }

    /**
     * Recursive helper for delete:
     *  1) If leaf, simply clear out the node (size=0, totalLen=0, max=0)
     *  2) Otherwise, descend left/right, then post-order update
     */
    private void deleteRec(int nodeId, int hash) {
        Node node = nodePool.at(nodeId);
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
        node.subtreeSize = nodePool.at(node.left).subtreeSize
                + nodePool.at(node.right).subtreeSize;

        node.subtreeTotalLen = nodePool.at(node.left).subtreeTotalLen
                + nodePool.at(node.right).subtreeTotalLen;

        node.subtreeMax = Math.max(nodePool.at(node.left).subtreeMax, nodePool.at(node.right).subtreeMax);
    }

    private int lowerBound(int hash){
        int v = rootInd;
        while(!nodePool.at(v).isLeaf()){
            int mid = (nodePool.at(v).lo + nodePool.at(v).hi) / 2;
            if(hash <= mid && nodePool.at(nodePool.at(v).left).subtreeMax >= hash){
                v = nodePool.at(v).left;
            }else{
                v = nodePool.at(v).right;
            }
        }
        return v;
    }

    private int countSpacesInSubtree(int v, int spaceSize) { // assumes every segment there is large enough
        // if v=0 (nonexistent node), it returns 0, because all fields in nodePool.at(0) are 0 / false.
        return nodePool.at(v).subtreeTotalLen - (spaceSize - 1) * nodePool.at(v).subtreeSize;
    }


}
