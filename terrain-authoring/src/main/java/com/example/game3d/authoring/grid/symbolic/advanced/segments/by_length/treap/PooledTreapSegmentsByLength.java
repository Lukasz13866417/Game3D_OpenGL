package com.example.game3d.authoring.grid.symbolic.advanced.segments.by_length.treap;

import com.example.game3d.authoring.grid.symbolic.GridSegment;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.GridBuildScratch;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.PartialSegmentHandlerResourcePack;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.by_length.LengthOrderedSegments;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.by_length.treap.LengthTreapNodePool.LengthTreapNode;

import java.util.Arrays;

public final class PooledTreapSegmentsByLength extends LengthOrderedSegments {
    private final LengthTreapNodePool pool;
    private int root = 0;
    private int size = 0;
    private int rngState = 0x9E3779B9;

    public PooledTreapSegmentsByLength(int totalRows, int nCols, boolean areSegmentsVertical) {
        this(totalRows, nCols, areSegmentsVertical, new LengthTreapNodePool(64));
    }

    public PooledTreapSegmentsByLength(
            int totalRows,
            int nCols,
            boolean areSegmentsVertical,
            PartialSegmentHandlerResourcePack resourcePack
    ) {
        this(totalRows, nCols, areSegmentsVertical, resourcePack.lengthTreapNodePool());
    }

    private PooledTreapSegmentsByLength(
            int totalRows,
            int nCols,
            boolean areSegmentsVertical,
            LengthTreapNodePool pool
    ) {
        super(totalRows, nCols, areSegmentsVertical);
        this.pool = pool;
        rngState ^= (areSegmentsVertical ? 0x7f4a7c15 : 0x3c6ef372);
        rngState ^= (totalRows * 0x1f123bb5);
        rngState ^= (nCols * 0x5bd1e995);
    }

    public static PooledTreapSegmentsByLength fromFreeSegments(
            int totalRows,
            int nCols,
            boolean areSegmentsVertical,
            GridSegment[] freeSegments
    ) {
        return fromFreeSegments(
                totalRows,
                nCols,
                areSegmentsVertical,
                new LengthTreapNodePool(64),
                freeSegments
        );
    }

    public static PooledTreapSegmentsByLength fromFreeSegments(
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
                resourcePack.lengthTreapNodePool(),
                freeSegments
        );
    }

    private static PooledTreapSegmentsByLength fromFreeSegments(
            int totalRows,
            int nCols,
            boolean areSegmentsVertical,
            LengthTreapNodePool pool,
            GridSegment[] freeSegments
    ) {
        PooledTreapSegmentsByLength out =
                new PooledTreapSegmentsByLength(totalRows, nCols, areSegmentsVertical, pool);
        if (freeSegments == null || freeSegments.length == 0) {
            return out;
        }
        GridSegment[] sorted = freeSegments.clone();
        Arrays.sort(sorted, PooledTreapSegmentsByLength::compareSegments);
        out.buildFromSortedSegments(sorted);
        return out;
    }

    public static PooledTreapSegmentsByLength fromLengthSortedScratch(
            int totalRows,
            int nCols,
            boolean areSegmentsVertical,
            PartialSegmentHandlerResourcePack resourcePack,
            GridBuildScratch scratch
    ) {
        PooledTreapSegmentsByLength out =
                new PooledTreapSegmentsByLength(totalRows, nCols, areSegmentsVertical, resourcePack);
        if (scratch == null || scratch.size() == 0) {
            return out;
        }
        out.buildFromScratchSorted(scratch);
        return out;
    }

    @Override
    public void insert(int row, int col, int length) {
        if (length <= 0) {
            return;
        }
        int z = pool.newNode();
        LengthTreapNode zn = pool.at(z);
        zn.row = row;
        zn.col = col;
        zn.length = length;
        zn.priority = nextPriority();
        zn.subtreeSize = 1;
        zn.subtreeTotalLen = length;

        int parent = 0;
        int curr = root;
        while (curr != 0) {
            parent = curr;
            int cmp = compare(row, col, length, curr);
            if (cmp < 0) {
                curr = pool.at(curr).left;
            } else if (cmp > 0) {
                curr = pool.at(curr).right;
            } else {
                pool.freeNode(z);
                return;
            }
        }

        zn.parent = parent;
        if (parent == 0) {
            root = z;
        } else if (compare(row, col, length, parent) < 0) {
            pool.at(parent).left = z;
        } else {
            pool.at(parent).right = z;
        }

        refreshUpwards(parent);

        while (zn.parent != 0) {
            LengthTreapNode p = pool.at(zn.parent);
            if (p.priority <= zn.priority) {
                break;
            }
            if (p.left == z) {
                rotateRight(zn.parent);
            } else {
                rotateLeft(zn.parent);
            }
        }
        refreshUpwards(z);
        size += 1;
    }

    @Override
    public void delete(int row, int col, int length) {
        int node = findNode(row, col, length);
        if (node == 0) {
            return;
        }
        deleteNode(node);
        size -= 1;
    }

    @Override
    public int countFittingSpaces(int spaceSize) {
        if (spaceSize <= 0) {
            throw new IllegalArgumentException("spaceSize must be > 0");
        }
        Split split = split(root, spaceSize, 1, 1);
        int qualifiedRoot = split.right;
        int result = spacesInQualifiedSubtree(qualifiedRoot, spaceSize);
        root = merge(split.left, qualifiedRoot);
        if (root != 0) {
            pool.at(root).parent = 0;
        }
        return result;
    }

    @Override
    public GridSegment getKthFittingSpace(int spaceSize, int k) {
        if (spaceSize <= 0) {
            throw new IllegalArgumentException("spaceSize must be > 0");
        }
        if (k <= 0) {
            throw new IllegalArgumentException("k must be > 0");
        }

        Split split = split(root, spaceSize, 1, 1);
        int qualifiedRoot = split.right;
        int totalSpaces = spacesInQualifiedSubtree(qualifiedRoot, spaceSize);
        if (k > totalSpaces) {
            root = merge(split.left, qualifiedRoot);
            if (root != 0) {
                pool.at(root).parent = 0;
            }
            throw new IllegalArgumentException("k exceeds number of fitting spaces");
        }
        GridSegment result = kthSpaceInQualifiedSubtree(qualifiedRoot, spaceSize, k);
        root = merge(split.left, qualifiedRoot);
        if (root != 0) {
            pool.at(root).parent = 0;
        }
        return result;
    }

    @Override
    public void destroy() {
        destroyRec(root);
        root = 0;
        size = 0;
    }

    private void buildFromSortedSegments(GridSegment[] sortedSegments) {
        int[] stack = new int[Math.max(8, sortedSegments.length)];
        int top = 0;

        for (GridSegment seg : sortedSegments) {
            if (seg == null || seg.length <= 0) {
                continue;
            }
            int nodeIdx = pool.newNode();
            LengthTreapNode node = pool.at(nodeIdx);
            node.row = seg.row;
            node.col = seg.col;
            node.length = seg.length;
            node.priority = nextPriority();
            node.subtreeSize = 1;
            node.subtreeTotalLen = seg.length;

            int lastPopped = 0;
            while (top > 0 && pool.at(stack[top - 1]).priority > node.priority) {
                lastPopped = stack[--top];
            }

            if (top == 0) {
                root = nodeIdx;
            } else {
                int parentIdx = stack[top - 1];
                pool.at(parentIdx).right = nodeIdx;
                node.parent = parentIdx;
            }

            if (lastPopped != 0) {
                node.left = lastPopped;
                pool.at(lastPopped).parent = nodeIdx;
            }

            if (top == stack.length) {
                stack = Arrays.copyOf(stack, stack.length * 2);
            }
            stack[top++] = nodeIdx;
            size += 1;
        }

        recomputeSubtree(root);
    }

    private void buildFromScratchSorted(GridBuildScratch scratch) {
        int[] stack = new int[Math.max(8, scratch.size())];
        int top = 0;

        for (int i = 0; i < scratch.size(); ++i) {
            int length = scratch.lengthAt(i);
            if (length <= 0) {
                continue;
            }
            int nodeIdx = pool.newNode();
            LengthTreapNode node = pool.at(nodeIdx);
            node.row = scratch.rowAt(i);
            node.col = scratch.colAt(i);
            node.length = length;
            node.priority = nextPriority();
            node.subtreeSize = 1;
            node.subtreeTotalLen = length;

            int lastPopped = 0;
            while (top > 0 && pool.at(stack[top - 1]).priority > node.priority) {
                lastPopped = stack[--top];
            }

            if (top == 0) {
                root = nodeIdx;
            } else {
                int parentIdx = stack[top - 1];
                pool.at(parentIdx).right = nodeIdx;
                node.parent = parentIdx;
            }

            if (lastPopped != 0) {
                node.left = lastPopped;
                pool.at(lastPopped).parent = nodeIdx;
            }

            if (top == stack.length) {
                stack = Arrays.copyOf(stack, stack.length * 2);
            }
            stack[top++] = nodeIdx;
            size += 1;
        }

        recomputeSubtree(root);
    }

    private int recomputeSubtree(int nodeIdx) {
        if (nodeIdx == 0) {
            return 0;
        }
        LengthTreapNode node = pool.at(nodeIdx);
        recomputeSubtree(node.left);
        recomputeSubtree(node.right);
        refreshNode(nodeIdx);
        return node.subtreeSize;
    }

    private int nextPriority() {
        int x = rngState;
        x ^= (x << 13);
        x ^= (x >>> 17);
        x ^= (x << 5);
        rngState = x;
        return x & Integer.MAX_VALUE;
    }

    private static int compareSegments(GridSegment a, GridSegment b) {
        return compareKey(a.length, a.row, a.col, b.length, b.row, b.col);
    }

    private int compare(int row, int col, int length, int nodeIdx) {
        LengthTreapNode node = pool.at(nodeIdx);
        return compareKey(length, row, col, node.length, node.row, node.col);
    }

    private int findNode(int row, int col, int length) {
        int curr = root;
        while (curr != 0) {
            int cmp = compare(row, col, length, curr);
            if (cmp < 0) {
                curr = pool.at(curr).left;
            } else if (cmp > 0) {
                curr = pool.at(curr).right;
            } else {
                return curr;
            }
        }
        return 0;
    }

    private void refreshNode(int nodeIdx) {
        if (nodeIdx == 0) {
            return;
        }
        LengthTreapNode node = pool.at(nodeIdx);
        node.subtreeSize = 1 + subtreeSize(node.left) + subtreeSize(node.right);
        node.subtreeTotalLen = node.length + subtreeTotalLen(node.left) + subtreeTotalLen(node.right);
    }

    private void refreshUpwards(int nodeIdx) {
        int curr = nodeIdx;
        while (curr != 0) {
            refreshNode(curr);
            curr = pool.at(curr).parent;
        }
    }

    private int subtreeSize(int nodeIdx) {
        return nodeIdx == 0 ? 0 : pool.at(nodeIdx).subtreeSize;
    }

    private int subtreeTotalLen(int nodeIdx) {
        return nodeIdx == 0 ? 0 : pool.at(nodeIdx).subtreeTotalLen;
    }

    private int spacesInQualifiedSubtree(int nodeIdx, int spaceSize) {
        if (nodeIdx == 0) {
            return 0;
        }
        LengthTreapNode node = pool.at(nodeIdx);
        return node.subtreeTotalLen - (spaceSize - 1) * node.subtreeSize;
    }

    private GridSegment kthSpaceInQualifiedSubtree(int nodeIdx, int spaceSize, int k) {
        int curr = nodeIdx;
        while (curr != 0) {
            LengthTreapNode node = pool.at(curr);
            int leftSpaces = spacesInQualifiedSubtree(node.left, spaceSize);
            if (k <= leftSpaces) {
                curr = node.left;
                continue;
            }

            int nodeSpaces = node.length - spaceSize + 1;
            if (k <= leftSpaces + nodeSpaces) {
                return kthSpaceInSegment(node.row, node.col, spaceSize, k - leftSpaces);
            }

            k -= leftSpaces + nodeSpaces;
            curr = node.right;
        }

        throw new IllegalArgumentException("k exceeds number of fitting spaces");
    }

    private Split split(int nodeIdx, int length, int row, int col) {
        if (nodeIdx == 0) {
            return new Split(0, 0);
        }

        LengthTreapNode node = pool.at(nodeIdx);
        if (compareKey(node.length, node.row, node.col, length, row, col) < 0) {
            Split split = split(node.right, length, row, col);
            node.right = split.left;
            if (node.right != 0) {
                pool.at(node.right).parent = nodeIdx;
            }
            refreshNode(nodeIdx);
            if (split.right != 0) {
                pool.at(split.right).parent = 0;
            }
            node.parent = 0;
            return new Split(nodeIdx, split.right);
        }

        Split split = split(node.left, length, row, col);
        node.left = split.right;
        if (node.left != 0) {
            pool.at(node.left).parent = nodeIdx;
        }
        refreshNode(nodeIdx);
        if (split.left != 0) {
            pool.at(split.left).parent = 0;
        }
        node.parent = 0;
        return new Split(split.left, nodeIdx);
    }

    private int merge(int leftRoot, int rightRoot) {
        if (leftRoot == 0) {
            return rightRoot;
        }
        if (rightRoot == 0) {
            return leftRoot;
        }

        LengthTreapNode left = pool.at(leftRoot);
        LengthTreapNode right = pool.at(rightRoot);
        if (left.priority <= right.priority) {
            int merged = merge(left.right, rightRoot);
            left.right = merged;
            if (merged != 0) {
                pool.at(merged).parent = leftRoot;
            }
            refreshNode(leftRoot);
            left.parent = 0;
            return leftRoot;
        }

        int merged = merge(leftRoot, right.left);
        right.left = merged;
        if (merged != 0) {
            pool.at(merged).parent = rightRoot;
        }
        refreshNode(rightRoot);
        right.parent = 0;
        return rightRoot;
    }

    private void rotateLeft(int xIdx) {
        LengthTreapNode x = pool.at(xIdx);
        int yIdx = x.right;
        LengthTreapNode y = pool.at(yIdx);
        int parent = x.parent;

        x.right = y.left;
        if (y.left != 0) {
            pool.at(y.left).parent = xIdx;
        }

        y.parent = parent;
        if (parent == 0) {
            root = yIdx;
        } else if (pool.at(parent).left == xIdx) {
            pool.at(parent).left = yIdx;
        } else {
            pool.at(parent).right = yIdx;
        }

        y.left = xIdx;
        x.parent = yIdx;

        refreshNode(xIdx);
        refreshNode(yIdx);
    }

    private void rotateRight(int yIdx) {
        LengthTreapNode y = pool.at(yIdx);
        int xIdx = y.left;
        LengthTreapNode x = pool.at(xIdx);
        int parent = y.parent;

        y.left = x.right;
        if (x.right != 0) {
            pool.at(x.right).parent = yIdx;
        }

        x.parent = parent;
        if (parent == 0) {
            root = xIdx;
        } else if (pool.at(parent).right == yIdx) {
            pool.at(parent).right = xIdx;
        } else {
            pool.at(parent).left = xIdx;
        }

        x.right = yIdx;
        y.parent = xIdx;

        refreshNode(yIdx);
        refreshNode(xIdx);
    }

    private void deleteNode(int nodeIdx) {
        int z = nodeIdx;
        while (pool.at(z).left != 0 && pool.at(z).right != 0) {
            int left = pool.at(z).left;
            int right = pool.at(z).right;
            if (pool.at(left).priority <= pool.at(right).priority) {
                rotateRight(z);
            } else {
                rotateLeft(z);
            }
        }

        int child = pool.at(z).left != 0 ? pool.at(z).left : pool.at(z).right;
        int parent = pool.at(z).parent;
        if (child != 0) {
            pool.at(child).parent = parent;
        }
        if (parent == 0) {
            root = child;
        } else if (pool.at(parent).left == z) {
            pool.at(parent).left = child;
        } else {
            pool.at(parent).right = child;
        }
        pool.freeNode(z);
        refreshUpwards(parent);
    }

    private void destroyRec(int nodeIdx) {
        if (nodeIdx == 0) {
            return;
        }
        LengthTreapNode node = pool.at(nodeIdx);
        destroyRec(node.left);
        destroyRec(node.right);
        pool.freeNode(nodeIdx);
    }

    private static final class Split {
        final int left;
        final int right;

        Split(int left, int right) {
            this.left = left;
            this.right = right;
        }
    }
}
