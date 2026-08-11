package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments;

import java.util.Arrays;

/**
 * Reusable primitive workspace for parent grid-summary construction.
 * Stores segments as parallel arrays to avoid transient GridSegment allocations.
 */
public final class GridBuildScratch {
    private static final int DEFAULT_CAPACITY = 128;

    private int[] rows;
    private int[] cols;
    private int[] lengths;
    private int size = 0;

    public GridBuildScratch() {
        this(DEFAULT_CAPACITY);
    }

    public GridBuildScratch(int initialCapacity) {
        int capacity = Math.max(1, initialCapacity);
        this.rows = new int[capacity];
        this.cols = new int[capacity];
        this.lengths = new int[capacity];
    }

    public void clear() {
        size = 0;
    }

    public int size() {
        return size;
    }

    public int rowAt(int index) {
        return rows[index];
    }

    public int colAt(int index) {
        return cols[index];
    }

    public int lengthAt(int index) {
        return lengths[index];
    }

    public void add(int row, int col, int length) {
        if (length <= 0) {
            return;
        }
        ensureCapacity(size + 1);
        rows[size] = row;
        cols[size] = col;
        lengths[size] = length;
        size++;
    }

    public void sortByLengthThenPosition() {
        sort();
    }

    private void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity <= rows.length) {
            return;
        }
        int newCapacity = rows.length;
        while (newCapacity < requiredCapacity) {
            newCapacity *= 2;
        }
        rows = Arrays.copyOf(rows, newCapacity);
        cols = Arrays.copyOf(cols, newCapacity);
        lengths = Arrays.copyOf(lengths, newCapacity);
    }

    private void sort() {
        if (size <= 1) {
            return;
        }
        quickSort(0, size - 1);
    }

    private void quickSort(int lo, int hi) {
        int i = lo;
        int j = hi;
        int pivot = lo + ((hi - lo) >>> 1);
        while (i <= j) {
            while (compare(i, pivot) < 0) {
                i++;
            }
            while (compare(j, pivot) > 0) {
                j--;
            }
            if (i <= j) {
                swap(i, j);
                if (pivot == i) {
                    pivot = j;
                } else if (pivot == j) {
                    pivot = i;
                }
                i++;
                j--;
            }
        }
        if (lo < j) {
            quickSort(lo, j);
        }
        if (i < hi) {
            quickSort(i, hi);
        }
    }

    private int compare(int first, int second) {
        if (lengths[first] != lengths[second]) {
            return Integer.compare(lengths[first], lengths[second]);
        }
        if (rows[first] != rows[second]) {
            return Integer.compare(rows[first], rows[second]);
        }
        return Integer.compare(cols[first], cols[second]);
    }

    private void swap(int first, int second) {
        if (first == second) {
            return;
        }
        int tmp = rows[first];
        rows[first] = rows[second];
        rows[second] = tmp;

        tmp = cols[first];
        cols[first] = cols[second];
        cols[second] = tmp;

        tmp = lengths[first];
        lengths[first] = lengths[second];
        lengths[second] = tmp;
    }
}
