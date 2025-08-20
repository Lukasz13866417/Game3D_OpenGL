package com.example.game3d_opengl.game.terrain_api.grid.symbolic.advanced.segments.by_length.segtree_implementation;

public class PreallocatedResizableArrayStack {

    private int front = -1, curr_size = 0;
    // Remove final so we can reassign when we grow:
    private int max_size;
    private int[] elements;

    public PreallocatedResizableArrayStack(int max_size) {
        this.max_size = max_size;
        this.elements = new int[max_size];
    }

    public int getMaxSize() {
        return max_size;
    }

    public int getFirst() {
        if (curr_size == 0) {
            throw new IllegalStateException("Deque is empty");
        }
        int ind = front + 1;
        return ind < max_size ? elements[ind] : elements[ind - max_size];
    }

    public int getLast() {
        if (curr_size == 0) {
            throw new IllegalStateException("Deque is empty");
        }
        int ind = front + curr_size;
        return ind < max_size ? elements[ind] : elements[ind - max_size];
    }

    public int peekFirst() {
        if (curr_size == 0) {
            return Integer.MIN_VALUE;
        }
        int ind = front + 1;
        return ind < max_size ? elements[ind] : elements[ind - max_size];
    }

    public int peekLast() {
        if (curr_size == 0) {
            return Integer.MIN_VALUE;
        }
        int ind = front + curr_size;
        return ind < max_size ? elements[ind] : elements[ind - max_size];
    }

    public void removeFirst() {
        if (curr_size == 0) {
            throw new IllegalStateException("Deque is empty");
        }
        --curr_size;
        front = front < max_size - 1 ? front + 1 : 0;
    }

    public void removeLast() {
        if (curr_size == 0) {
            throw new IllegalStateException("Deque is empty");
        }
        --curr_size;
    }

    public int popFirst() {
        int res = getFirst();
        removeFirst();
        return res;
    }

    public int popLast() {
        int res = getLast();
        removeLast();
        return res;
    }

    public int size() {
        return curr_size;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public void clear() {
        while (size() > 0) {
            removeFirst();
        }
    }

    /**
     * Ensures the deque has enough capacity to accommodate one more element.
     * If the current size equals max_size, we create a new array of double the size
     * and copy the existing elements in order.
     */
    private void ensureCapacity() {
        if (curr_size < max_size) {
            return;
        }
        int newMaxSize = max_size * 2;
        int[] newElements = new int[newMaxSize];

        // Copy the old data to the new array in index order:
        // We'll place the first element at index 0, second at index 1, etc.
        for (int i = 0; i < curr_size; i++) {
            newElements[i] = get(i);
        }

        // Reset front to -1 so that get(0) is now newElements[0].
        front = -1;
        elements = newElements;
        max_size = newMaxSize;
    }

    public void pushBack(int val) {
        // Now we allow expansion rather than throwing an exception
        ensureCapacity();
        ++curr_size;
        int ind = front + curr_size;
        if (ind >= max_size) {
            ind -= max_size;
        }
        elements[ind] = val;
    }

    public int get(int ind) {
        if (ind < 0 || ind >= curr_size) {
            throw new IllegalStateException(
                    "Index " + ind + " out of bounds (curr size " + curr_size + ")"
            );
        }
        ind = front + ind + 1;
        if (ind >= max_size) {
            ind -= max_size;
        }
        return elements[ind];
    }

}
