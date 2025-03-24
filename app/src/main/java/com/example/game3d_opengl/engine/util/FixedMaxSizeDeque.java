package com.example.game3d.engine3d;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class FixedMaxSizeDeque<T> implements Iterable<T> {
    private int front = -1, curr_size = 0;
    private final T[] elements;
    private final int max_size;

    public FixedMaxSizeDeque(int max_size) {
        this.max_size = max_size;
        this.elements = (T[]) (new Object[max_size]);
    }

    public int getMaxSize() {
        return max_size;
    }

    public T getFirst() {
        if (curr_size == 0) {
            throw new IllegalStateException("Deque is empty");
        }
        int ind = front + 1;
        return ind < max_size ? elements[ind] : elements[ind - max_size];
    }

    public T getLast() {
        if (curr_size == 0) {
            throw new IllegalStateException("Deque is empty");
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

    public T popFirst(){
        T res = getFirst();
        removeFirst();
        return res;
    }

    public T popLast(){
        T res = getLast();
        removeLast();
        return res;
    }

    public int size() {
        return curr_size;
    }

    public boolean isEmpty(){
        return size() == 0;
    }

    public void clear(){
        while(size() > 0){
            removeFirst();
        }
    }

    public void pushBack(T val) {
        if (curr_size == max_size) {
            throw new IllegalStateException("Size exceeded "+curr_size+" out of "+max_size);
        }
        ++curr_size;
        int ind = front + curr_size;
        if (ind >= max_size) {
            ind -= max_size;
        }
        elements[ind] = val;
    }

    public T get(int ind) {
        if (ind < 0 || ind >= curr_size) {
            throw new IllegalStateException("Index " + ind + " out of bounds (curr size "+curr_size+")");
        }
        ind = front + ind + 1;
        if (ind >= max_size) {
            ind -= max_size;
        }
        return elements[ind];
    }

    @Override
    public Iterator<T> iterator() {
        return new FixedMaxSizeDequeIterator();
    }

    private class FixedMaxSizeDequeIterator implements Iterator<T> {
        private int count = 0;
        private int index = front + 1;

        @Override
        public boolean hasNext() {
            return count < curr_size;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            T element = elements[index];
            index = (index + 1) % max_size;
            count++;
            return element;
        }
    }
}
