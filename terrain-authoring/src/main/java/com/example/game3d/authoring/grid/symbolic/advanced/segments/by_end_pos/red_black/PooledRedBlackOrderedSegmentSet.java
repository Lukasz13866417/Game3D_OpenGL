package com.example.game3d.authoring.grid.symbolic.advanced.segments.by_end_pos.red_black;

import com.example.game3d.authoring.grid.symbolic.GridSegment;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.GridSegmentSink;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.by_end_pos.OrderedSegmentSet;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.by_end_pos.red_black.RbNodePool.RbNode;

import java.util.Arrays;

public final class PooledRedBlackOrderedSegmentSet implements OrderedSegmentSet {
    private static final boolean RED = true;
    private static final boolean BLACK = false;

    private final boolean vertical;
    private final RbNodePool pool;
    private int root = 0;
    private int size = 0;

    public PooledRedBlackOrderedSegmentSet(boolean vertical, int nRows, int nCols, RbNodePool pool) {
        this.vertical = vertical;
        this.pool = pool;
    }

    @Override
    public void add(GridSegment seg) {
        add(seg.row, seg.col, seg.length);
    }

    @Override
    public void add(int row, int col, int length) {
        int z = pool.newNode();
        RbNode zn = pool.at(z);
        zn.row = row;
        zn.col = col;
        zn.length = length;
        zn.red = RED;

        int y = 0;
        int x = root;
        while (x != 0) {
            y = x;
            int cmp = compare(row, col, length, x);
            if (cmp < 0) {
                x = pool.at(x).left;
            } else if (cmp > 0) {
                x = pool.at(x).right;
            } else {
                pool.freeNode(z);
                return;
            }
        }

        zn.parent = y;
        if (y == 0) {
            root = z;
        } else if (compare(row, col, length, y) < 0) {
            pool.at(y).left = z;
        } else {
            pool.at(y).right = z;
        }
        insertFixup(z);
        size += 1;
    }

    @Override
    public boolean remove(GridSegment seg) {
        int z = findNode(seg);
        if (z == 0) {
            return false;
        }
        deleteNode(z);
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
        GridSegment res = toSegment(min);
        deleteNode(min);
        size -= 1;
        return res;
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
            RbNode node = pool.at(curr);
            sink.accept(node.row, node.col, node.length);
            curr = node.right;
        }
    }

    private GridSegment toSegment(int idx) {
        RbNode n = pool.at(idx);
        return new GridSegment(n.row, n.col, n.length);
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

    private int compare(GridSegment seg, int nodeIdx) {
        return compare(seg.row, seg.col, seg.length, nodeIdx);
    }

    private int compare(int row, int col, int length, int nodeIdx) {
        RbNode n = pool.at(nodeIdx);
        int aPrimary = vertical ? col : row;
        int bPrimary = vertical ? n.col : n.row;
        if (aPrimary != bPrimary) {
            return Integer.compare(aPrimary, bPrimary);
        }
        int aEnd = vertical ? row + length : col + length;
        int bEnd = vertical ? n.row + n.length : n.col + n.length;
        return Integer.compare(aEnd, bEnd);
    }

    private int minimum(int nodeIdx) {
        int curr = nodeIdx;
        while (pool.at(curr).left != 0) {
            curr = pool.at(curr).left;
        }
        return curr;
    }

    private int parentOf(int idx) {
        return idx == 0 ? 0 : pool.at(idx).parent;
    }

    private int leftOf(int idx) {
        return idx == 0 ? 0 : pool.at(idx).left;
    }

    private int rightOf(int idx) {
        return idx == 0 ? 0 : pool.at(idx).right;
    }

    private boolean colorOf(int idx) {
        return idx != 0 && pool.at(idx).red;
    }

    private void setColor(int idx, boolean color) {
        if (idx != 0) {
            pool.at(idx).red = color;
        }
    }

    private void leftRotate(int xIdx) {
        RbNode x = pool.at(xIdx);
        int yIdx = x.right;
        RbNode y = pool.at(yIdx);

        x.right = y.left;
        if (y.left != 0) {
            pool.at(y.left).parent = xIdx;
        }
        y.parent = x.parent;
        if (x.parent == 0) {
            root = yIdx;
        } else if (xIdx == pool.at(x.parent).left) {
            pool.at(x.parent).left = yIdx;
        } else {
            pool.at(x.parent).right = yIdx;
        }
        y.left = xIdx;
        x.parent = yIdx;
    }

    private void rightRotate(int yIdx) {
        RbNode y = pool.at(yIdx);
        int xIdx = y.left;
        RbNode x = pool.at(xIdx);

        y.left = x.right;
        if (x.right != 0) {
            pool.at(x.right).parent = yIdx;
        }
        x.parent = y.parent;
        if (y.parent == 0) {
            root = xIdx;
        } else if (yIdx == pool.at(y.parent).right) {
            pool.at(y.parent).right = xIdx;
        } else {
            pool.at(y.parent).left = xIdx;
        }
        x.right = yIdx;
        y.parent = xIdx;
    }

    private void insertFixup(int zIdx) {
        int z = zIdx;
        while (colorOf(parentOf(z)) == RED) {
            int p = parentOf(z);
            int g = parentOf(p);
            if (p == leftOf(g)) {
                int y = rightOf(g);
                if (colorOf(y) == RED) {
                    setColor(p, BLACK);
                    setColor(y, BLACK);
                    setColor(g, RED);
                    z = g;
                } else {
                    if (z == rightOf(p)) {
                        z = p;
                        leftRotate(z);
                        p = parentOf(z);
                        g = parentOf(p);
                    }
                    setColor(p, BLACK);
                    setColor(g, RED);
                    rightRotate(g);
                }
            } else {
                int y = leftOf(g);
                if (colorOf(y) == RED) {
                    setColor(p, BLACK);
                    setColor(y, BLACK);
                    setColor(g, RED);
                    z = g;
                } else {
                    if (z == leftOf(p)) {
                        z = p;
                        rightRotate(z);
                        p = parentOf(z);
                        g = parentOf(p);
                    }
                    setColor(p, BLACK);
                    setColor(g, RED);
                    leftRotate(g);
                }
            }
        }
        setColor(root, BLACK);
    }

    private void transplant(int uIdx, int vIdx) {
        int parent = pool.at(uIdx).parent;
        if (parent == 0) {
            root = vIdx;
        } else if (uIdx == pool.at(parent).left) {
            pool.at(parent).left = vIdx;
        } else {
            pool.at(parent).right = vIdx;
        }
        if (vIdx != 0) {
            pool.at(vIdx).parent = parent;
        }
    }

    private void deleteNode(int zIdx) {
        int y = zIdx;
        boolean yOriginalColor = colorOf(y);
        int x;
        int xParent;
        int zLeft = leftOf(zIdx);
        int zRight = rightOf(zIdx);

        if (zLeft == 0) {
            x = zRight;
            xParent = parentOf(zIdx);
            transplant(zIdx, zRight);
        } else if (zRight == 0) {
            x = zLeft;
            xParent = parentOf(zIdx);
            transplant(zIdx, zLeft);
        } else {
            y = minimum(zRight);
            yOriginalColor = colorOf(y);
            x = rightOf(y);
            if (parentOf(y) == zIdx) {
                xParent = y;
                if (x != 0) {
                    pool.at(x).parent = y;
                }
            } else {
                xParent = parentOf(y);
                transplant(y, rightOf(y));
                pool.at(y).right = zRight;
                if (zRight != 0) {
                    pool.at(zRight).parent = y;
                }
            }
            transplant(zIdx, y);
            pool.at(y).left = zLeft;
            if (zLeft != 0) {
                pool.at(zLeft).parent = y;
            }
            setColor(y, colorOf(zIdx));
        }

        pool.freeNode(zIdx);
        if (yOriginalColor == BLACK) {
            deleteFixup(x, xParent);
        }
    }

    private void deleteFixup(int xStart, int parentStart) {
        int x = xStart;
        int parent = parentStart;
        while (x != root && colorOf(x) == BLACK) {
            if (x == leftOf(parent)) {
                int w = rightOf(parent);
                if (colorOf(w) == RED) {
                    setColor(w, BLACK);
                    setColor(parent, RED);
                    leftRotate(parent);
                    w = rightOf(parent);
                }
                if (colorOf(leftOf(w)) == BLACK && colorOf(rightOf(w)) == BLACK) {
                    setColor(w, RED);
                    x = parent;
                    parent = parentOf(x);
                } else {
                    if (colorOf(rightOf(w)) == BLACK) {
                        setColor(leftOf(w), BLACK);
                        setColor(w, RED);
                        rightRotate(w);
                        w = rightOf(parent);
                    }
                    setColor(w, colorOf(parent));
                    setColor(parent, BLACK);
                    setColor(rightOf(w), BLACK);
                    leftRotate(parent);
                    x = root;
                    parent = 0;
                }
            } else {
                int w = leftOf(parent);
                if (colorOf(w) == RED) {
                    setColor(w, BLACK);
                    setColor(parent, RED);
                    rightRotate(parent);
                    w = leftOf(parent);
                }
                if (colorOf(rightOf(w)) == BLACK && colorOf(leftOf(w)) == BLACK) {
                    setColor(w, RED);
                    x = parent;
                    parent = parentOf(x);
                } else {
                    if (colorOf(leftOf(w)) == BLACK) {
                        setColor(rightOf(w), BLACK);
                        setColor(w, RED);
                        leftRotate(w);
                        w = leftOf(parent);
                    }
                    setColor(w, colorOf(parent));
                    setColor(parent, BLACK);
                    setColor(leftOf(w), BLACK);
                    rightRotate(parent);
                    x = root;
                    parent = 0;
                }
            }
        }
        setColor(x, BLACK);
    }
}
