package com.example.game3d.authoring.grid.symbolic.advanced.segments.by_end_pos.treap;

import com.example.game3d.authoring.grid.symbolic.GridSegment;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.GridBuildScratch;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.GridSegmentSink;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.by_end_pos.OrderedSegmentSet;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.by_end_pos.treap.TreapNodePool.TreapNode;

import java.util.Arrays;

public final class PooledTreapOrderedSegmentSet implements OrderedSegmentSet {
    private final boolean vertical;
    private final TreapNodePool pool;
    private int root = 0;
    private int size = 0;
    private int rngState = 0x9E3779B9;

    public PooledTreapOrderedSegmentSet(boolean vertical, int nRows, int nCols, TreapNodePool pool) {
        this.vertical = vertical;
        this.pool = pool;
        rngState ^= (vertical ? 0x7f4a7c15 : 0x3c6ef372);
        rngState ^= (nRows * 0x1f123bb5);
        rngState ^= (nCols * 0x5bd1e995);
    }

    public static PooledTreapOrderedSegmentSet fromSortedSegments(
            boolean vertical, int nRows, int nCols, TreapNodePool pool, GridSegment[] sortedSegments
    ) {
        PooledTreapOrderedSegmentSet out = new PooledTreapOrderedSegmentSet(vertical, nRows, nCols, pool);
        if (sortedSegments == null || sortedSegments.length == 0) {
            return out;
        }
        out.buildFromSortedSegments(sortedSegments);
        return out;
    }

    public static PooledTreapOrderedSegmentSet fromScratchSorted(
            boolean vertical, int nRows, int nCols, TreapNodePool pool, GridBuildScratch scratch
    ) {
        PooledTreapOrderedSegmentSet out = new PooledTreapOrderedSegmentSet(vertical, nRows, nCols, pool);
        if (scratch == null || scratch.size() == 0) {
            return out;
        }
        out.buildFromScratchSorted(scratch);
        return out;
    }

    @Override
    public void add(GridSegment seg) {
        add(seg.row, seg.col, seg.length);
    }

    @Override
    public void add(int row, int col, int length) {
        int z = pool.newNode();
        TreapNode zn = pool.at(z);
        zn.row = row;
        zn.col = col;
        zn.length = length;
        zn.priority = nextPriority();

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

        while (zn.parent != 0) {
            TreapNode p = pool.at(zn.parent);
            if (p.priority <= zn.priority) {
                break;
            }
            if (p.left == z) {
                rotateRight(zn.parent);
            } else {
                rotateLeft(zn.parent);
            }
        }
        size += 1;
    }

    @Override
    public boolean remove(GridSegment seg) {
        int node = findNode(seg);
        if (node == 0) {
            return false;
        }
        deleteNode(node);
        size -= 1;
        return true;
    }

    @Override
    public GridSegment ceiling(GridSegment key) {
        int curr = root;
        int best = 0;
        while (curr != 0) {
            int cmp = compare(key, curr);
            if (cmp <= 0) {
                best = curr;
                curr = pool.at(curr).left;
            } else {
                curr = pool.at(curr).right;
            }
        }
        return best == 0 ? null : toSegment(best);
    }

    @Override
    public GridSegment pollFirst() {
        if (root == 0) {
            return null;
        }
        int min = minimum(root);
        GridSegment out = toSegment(min);
        deleteNode(min);
        size -= 1;
        return out;
    }

    @Override
    public boolean isEmpty() {
        return root == 0;
    }

    @Override
    public GridSegment[] toSortedArray() {
        GridSegment[] out = new GridSegment[size];
        if (size == 0) {
            return out;
        }
        int[] stack = new int[Math.max(8, size)];
        int top = 0;
        int curr = root;
        int outIdx = 0;
        while (curr != 0 || top > 0) {
            while (curr != 0) {
                if (top == stack.length) {
                    stack = Arrays.copyOf(stack, stack.length * 2);
                }
                stack[top++] = curr;
                curr = pool.at(curr).left;
            }
            curr = stack[--top];
            out[outIdx++] = toSegment(curr);
            curr = pool.at(curr).right;
        }
        return out;
    }

    @Override
    public void forEachSorted(GridSegmentSink sink) {
        if (size == 0) {
            return;
        }
        int[] stack = new int[Math.max(8, size)];
        int top = 0;
        int curr = root;
        while (curr != 0 || top > 0) {
            while (curr != 0) {
                if (top == stack.length) {
                    stack = Arrays.copyOf(stack, stack.length * 2);
                }
                stack[top++] = curr;
                curr = pool.at(curr).left;
            }
            curr = stack[--top];
            TreapNode node = pool.at(curr);
            sink.accept(node.row, node.col, node.length);
            curr = node.right;
        }
    }

    private void buildFromSortedSegments(GridSegment[] sortedSegments) {
        int[] stack = new int[Math.max(8, sortedSegments.length)];
        int top = 0;

        for (GridSegment seg : sortedSegments) {
            if (seg == null) {
                continue;
            }
            int nodeIdx = pool.newNode();
            TreapNode node = pool.at(nodeIdx);
            node.row = seg.row;
            node.col = seg.col;
            node.length = seg.length;
            node.priority = nextPriority();

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
    }

    private void buildFromScratchSorted(GridBuildScratch scratch) {
        int[] stack = new int[Math.max(8, scratch.size())];
        int top = 0;

        for (int i = 0; i < scratch.size(); ++i) {
            int nodeIdx = pool.newNode();
            TreapNode node = pool.at(nodeIdx);
            node.row = scratch.rowAt(i);
            node.col = scratch.colAt(i);
            node.length = scratch.lengthAt(i);
            node.priority = nextPriority();

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
    }

    private int nextPriority() {
        int x = rngState;
        x ^= (x << 13);
        x ^= (x >>> 17);
        x ^= (x << 5);
        rngState = x;
        return x & Integer.MAX_VALUE;
    }

    private int compare(GridSegment seg, int nodeIdx) {
        return compare(seg.row, seg.col, seg.length, nodeIdx);
    }

    private int compare(int row, int col, int length, int nodeIdx) {
        TreapNode n = pool.at(nodeIdx);
        int aPrimary = vertical ? col : row;
        int bPrimary = vertical ? n.col : n.row;
        if (aPrimary != bPrimary) {
            return Integer.compare(aPrimary, bPrimary);
        }
        int aEnd = vertical ? row + length : col + length;
        int bEnd = vertical ? n.row + n.length : n.col + n.length;
        return Integer.compare(aEnd, bEnd);
    }

    private int findNode(GridSegment seg) {
        int curr = root;
        while (curr != 0) {
            int cmp = compare(seg, curr);
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

    private int minimum(int node) {
        int curr = node;
        while (pool.at(curr).left != 0) {
            curr = pool.at(curr).left;
        }
        return curr;
    }

    private GridSegment toSegment(int idx) {
        TreapNode n = pool.at(idx);
        return new GridSegment(n.row, n.col, n.length);
    }

    private void rotateLeft(int xIdx) {
        TreapNode x = pool.at(xIdx);
        int yIdx = x.right;
        TreapNode y = pool.at(yIdx);
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
    }

    private void rotateRight(int yIdx) {
        TreapNode y = pool.at(yIdx);
        int xIdx = y.left;
        TreapNode x = pool.at(xIdx);
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
    }
}
