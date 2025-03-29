package com.example.game3d_opengl.game.terrain.terrainutil;

public class PreallocatedCoordinateBuffer {
    private final PreallocatedFloatBuffer floatBuffer;

    public PreallocatedCoordinateBuffer() {
        this.floatBuffer = new PreallocatedFloatBuffer();
    }

    /**
     * Adds one 3D coordinate (x, y, z) to the buffer.
     * Internally, this consumes three floats in the floatBuffer.
     */
    public void addPos(float x, float y, float z) {
        floatBuffer.add(x);
        floatBuffer.add(y);
        floatBuffer.add(z);
    }

    /**
     * Returns the X component of the i-th coordinate.
     */
    public float getX(int i) {
        return floatBuffer.get(i * 3);
    }

    /**
     * Returns the Y component of the i-th coordinate.
     */
    public float getY(int i) {
        return floatBuffer.get(i * 3 + 1);
    }

    /**
     * Returns the Z component of the i-th coordinate.
     */
    public float getZ(int i) {
        return floatBuffer.get(i * 3 + 2);
    }

    /**
     * Returns the number of coordinates (not floats) currently in this buffer.
     * Since each coordinate is 3 floats, we divide the floatBuffer size by 3.
     */
    public int size() {
        return floatBuffer.size() / 3;
    }

    /**
     * Clears the buffer.
     */
    public void clear() {
        floatBuffer.clear();
    }

    public float[] pop(){
        if(size() == 0){
            throw new IllegalStateException("Empty buffer");
        }
        return new float[]{
                floatBuffer.pop(),
                floatBuffer.pop(),
                floatBuffer.pop()
        };
    }
}
