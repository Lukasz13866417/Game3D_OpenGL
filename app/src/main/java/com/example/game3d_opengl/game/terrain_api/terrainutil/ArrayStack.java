package com.example.game3d_opengl.game.terrain_api.terrainutil;

import java.util.ArrayDeque;
import java.util.EmptyStackException;

public class ArrayStack<T> {
    private final ArrayDeque<T> deque = new ArrayDeque<>();
    
    public void push(T item) {
        deque.push(item);
    }

    public T pop() {
        if (deque.isEmpty()) throw new EmptyStackException();
        return deque.pop();
    }

    public T peek() {
        return deque.peek();
    }

    public boolean isEmpty() {
        return deque.isEmpty();
    }

    public int size() {
        return deque.size();
    }

    public void clear() {
        deque.clear();
    }

    @Override
    public String toString() {
        return deque.toString();
    }
}
