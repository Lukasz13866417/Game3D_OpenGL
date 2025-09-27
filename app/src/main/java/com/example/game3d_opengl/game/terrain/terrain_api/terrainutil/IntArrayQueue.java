package com.example.game3d_opengl.game.terrain.terrain_api.terrainutil;

import java.util.NoSuchElementException;

/**
 * Fixed-capacity, pre-allocated ring buffer queue for primitive ints.
 */
public class IntArrayQueue {

	private final int[] data;
	private final int capacity;
	private int head = 0; // index of front element
	private int size = 0;

	public IntArrayQueue(int capacity) {
		if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
		this.capacity = capacity;
		this.data = new int[capacity];
	}

	public void enqueue(int value) {
		if (size == capacity) throw new IllegalStateException("IntArrayQueue full");
		int tail = (head + size) % capacity;
		data[tail] = value;
		size++;
	}

	public void dequeue() {
		if (size == 0) throw new NoSuchElementException("Queue is empty");
		head = (head + 1) % capacity;
		size--;
	}

	public int peek() {
		if (size == 0) throw new NoSuchElementException("Queue is empty");
		return data[head];
	}

	public boolean isEmpty() {
		return size == 0;
	}

	public int size() {
		return size;
	}

	public void clear() {
		head = 0;
		size = 0;
	}
}


