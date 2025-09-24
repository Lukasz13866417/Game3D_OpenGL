package com.example.game3d_opengl.game.terrain_api.terrainutil;

import java.util.EmptyStackException;

/**
 * Fixed-capacity, preallocated stack for primitive ints.
 */
public class IntArrayStack {

	private final int[] data;
	private int size = 0;

	public IntArrayStack(int capacity) {
		if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
		this.data = new int[capacity];
	}

	public void push(int value) {
		if (size == data.length) throw new IllegalStateException("IntArrayStack full");
		data[size++] = value;
	}

	public int pop() {
		if (size == 0) throw new EmptyStackException();
		return data[--size];
	}

	public boolean isEmpty() {
		return size == 0;
	}

	public int size() {
		return size;
	}

	public void clear() {
		size = 0;
	}
}


