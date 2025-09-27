package com.example.game3d_opengl.game.terrain.terrain_api.terrainutil;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.NoSuchElementException;

public class ArrayQueue<T> {
    private final ArrayDeque<T> deque = new ArrayDeque<>();

    public void enqueue(T item) {
        deque.addLast(item); // Add to the tail
    }

    public T dequeue() {
        if (deque.isEmpty()) {
            throw new NoSuchElementException("Queue is empty");
        }
        return deque.removeFirst(); // Remove from the head
    }

    public T peek() {
        return deque.peekFirst(); // View head
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

    @NonNull
    @Override
    public String toString() {
        return deque.toString();
    }
}
