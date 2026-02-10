package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.cell_pair;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridSegment;

/**
 * Sparse segment tree over a linearized (row-major) grid of cells.
 * Supports two operations:
 * <ol>
 *   <li>{@link #reserve(int, int)} — mark a cell as occupied.</li>
 *   <li>{@link #findKthPairAndReserve(int, int)} — find (and reserve) the k-th valid
 *       pair of free cells whose "distance" is at least {@code d}, where distance between
 *       two cells is the number of free cells whose codes lie strictly between them.</li>
 * </ol>
 *
 * <p>Cells are linearized as {@code code(r,c) = (r-1)*nCols + (c-1)} (0-based).
 * Pairs {@code (x, y)} with {@code x < y} are ordered by {@code y} first, then {@code x}.
 *
 * <p>Each node stores the count of free cells in its interval. The tree starts fully free
 * and nodes are created lazily (implicit segment tree with a pre-allocated node pool).
 */
public class CellPairQuerySegtree {

    private final int nRows, nCols;
    private final int N; // nRows * nCols

    private static final CpqNodePool NODE_POOL = new CpqNodePool(100000);

    private final int rootId;

    public CellPairQuerySegtree(int nRows, int nCols) {
        this.nRows = nRows;
        this.nCols = nCols;
        this.N = nRows * nCols;
        this.rootId = NODE_POOL.newNode();
        CpqNode root = NODE_POOL.at(rootId);
        root.lo = 0;
        root.hi = N - 1;
        root.cnt = N;
    }

    private int code(int row, int col) {
        return (row - 1) * nCols + (col - 1);
    }

    private GridSegment fromCode(int code) {
        return GridSegment.GS(code / nCols + 1, code % nCols + 1, 1);
    }

    // -----------------------------------------------
    //       Count valid pairs — O(1)
    // -----------------------------------------------

    /**
     * Returns the total number of valid pairs with distance &ge; {@code d}.
     * <p>
     * Each free cell y with rank R (among all free cells) contributes
     * {@code max(0, R - d - 1)} valid entrance partners. Summing over all y:
     * <pre>total = (F - d - 1)(F - d) / 2</pre>
     * where F is the total number of free cells.
     */
    public long countGoodPairs(int d) {
        long F = NODE_POOL.at(rootId).cnt;
        long gap = F - d - 1;
        if (gap <= 0) return 0;
        return gap * (gap + 1) / 2;
    }

    // -----------------------------------------------
    //       Reserve a single cell — O(log N)
    // -----------------------------------------------

    /**
     * Marks cell (row, col) as occupied. Row and col are 1-indexed.
     */
    public void reserve(int row, int col) {
        assert row >= 1 && row <= nRows;
        assert col >= 1 && col <= nCols;
        reserveByCode(code(row, col));
    }

    private void reserveByCode(int code) {
        assert code >= 0 && code < N;
        reserveRec(rootId, code);
    }

    private void reserveRec(int nodeId, int code) {
        CpqNode node = NODE_POOL.at(nodeId);
        node.cnt--;
        if (node.isLeaf()) {
            return;
        }
        int mid = (node.lo + node.hi) / 2;
        if (code <= mid) {
            ensureLeft(node);
            reserveRec(node.left, code);
        } else {
            ensureRight(node);
            reserveRec(node.right, code);
        }
    }

    // -----------------------------------------------
    //       Find k-th pair and reserve — O(log N)
    // -----------------------------------------------

    /**
     * Finds the k-th valid pair (ordered by (y, x)) with distance &ge; {@code d},
     * reserves both cells, and returns them.
     *
     * @return {@code GridSegment[]{entrance, exit}} where entrance has the smaller code
     *         (earlier in terrain) and exit has the larger code (further in terrain).
     *         Both segments have length 1.
     */
    public GridSegment[] findKthPairAndReserve(int k, int d) {
        assert k >= 1 && k <= countGoodPairs(d);

        // Phase 1: walk the tree to find y (the exit portal — larger code)
        int prefix = 0;
        int v = rootId;
        while (!NODE_POOL.at(v).isLeaf()) {
            int lc = leftCnt(v);
            long leftPairs = countPairsInInterval(lc, prefix, d);
            if (k <= leftPairs) {
                ensureLeft(NODE_POOL.at(v));
                v = NODE_POOL.at(v).left;
            } else {
                k -= (int) leftPairs;
                prefix += lc;
                ensureRight(NODE_POOL.at(v));
                v = NODE_POOL.at(v).right;
            }
        }
        int yCode = NODE_POOL.at(v).lo;
        int remainingK = k; // which partner of y

        // Phase 2: walk the tree to find x (the entrance portal — the remainingK-th free cell)
        int xCode = findKthFreeCell(remainingK);

        // Reserve both cells
        reserveByCode(xCode);
        reserveByCode(yCode);

        return new GridSegment[]{fromCode(xCode), fromCode(yCode)};
    }

    /**
     * Finds the free cell with the given rank (1-indexed) among all free cells.
     */
    private int findKthFreeCell(int k) {
        int v = rootId;
        while (!NODE_POOL.at(v).isLeaf()) {
            int lc = leftCnt(v);
            if (k <= lc) {
                ensureLeft(NODE_POOL.at(v));
                v = NODE_POOL.at(v).left;
            } else {
                k -= lc;
                ensureRight(NODE_POOL.at(v));
                v = NODE_POOL.at(v).right;
            }
        }
        return NODE_POOL.at(v).lo;
    }

    // -----------------------------------------------
    //       Core formula
    // -----------------------------------------------

    /**
     * Counts the number of valid pairs (x, y) where y is a free cell in an interval
     * containing {@code cnt} free cells, given {@code prefix} free cells before the interval.
     *
     * <p>Free cell at local 0-index i has rank {@code prefix + i + 1} and contributes
     * {@code max(0, prefix + i - d)} pairs. Summing:
     * <pre>
     * t         = max(0, d - prefix)
     * effective = max(0, cnt - t)
     * base      = max(0, prefix - d)
     * result    = effective * base + effective * (effective - 1) / 2
     * </pre>
     */
    static long countPairsInInterval(int cnt, int prefix, int d) {
        if (cnt <= 0) return 0;
        int t = Math.max(0, d - prefix);
        long effective = Math.max(0, cnt - t);
        if (effective <= 0) return 0;
        long base = Math.max(0, prefix - d);
        return effective * base + effective * (effective - 1) / 2;
    }

    // -----------------------------------------------
    //       Tree helpers
    // -----------------------------------------------

    /**
     * Returns the count of free cells in the left child's interval.
     * If the left child hasn't been created yet, the interval is fully free.
     */
    private int leftCnt(int nodeId) {
        CpqNode node = NODE_POOL.at(nodeId);
        if (node.left != 0) return NODE_POOL.at(node.left).cnt;
        int mid = (node.lo + node.hi) / 2;
        return mid - node.lo + 1;
    }

    private void ensureLeft(CpqNode node) {
        if (node.left == 0) {
            int mid = (node.lo + node.hi) / 2;
            int id = NODE_POOL.newNode();
            CpqNode child = NODE_POOL.at(id);
            child.lo = node.lo;
            child.hi = mid;
            child.cnt = mid - node.lo + 1; // fully free
            child.parent = node.id;
            child.isLeftChild = true;
            node.left = id;
        }
    }

    private void ensureRight(CpqNode node) {
        if (node.right == 0) {
            int mid = (node.lo + node.hi) / 2;
            int id = NODE_POOL.newNode();
            CpqNode child = NODE_POOL.at(id);
            child.lo = mid + 1;
            child.hi = node.hi;
            child.cnt = node.hi - mid; // fully free
            child.parent = node.id;
            child.isLeftChild = false;
            node.right = id;
        }
    }

    // -----------------------------------------------
    //       Cleanup
    // -----------------------------------------------

    public void destroy() {
        destroyRec(rootId);
    }

    private void destroyRec(int nodeId) {
        CpqNode node = NODE_POOL.at(nodeId);
        if (node.left != 0) destroyRec(node.left);
        if (node.right != 0) destroyRec(node.right);
        NODE_POOL.freeNode(nodeId);
    }
}
