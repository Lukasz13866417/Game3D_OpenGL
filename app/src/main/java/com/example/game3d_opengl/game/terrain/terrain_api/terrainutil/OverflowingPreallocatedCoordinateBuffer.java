package com.example.game3d_opengl.game.terrain.terrain_api.terrainutil;

import com.example.game3d_opengl.game.pooling.PooledResourcesOwner;

public class OverflowingPreallocatedCoordinateBuffer extends PooledResourcesOwner {
    private final OverflowingPreallocatedFloatBuffer floatBuffer;

    public void free(){
        releasePooledResourcesRecursively();
    }

    private OverflowingPreallocatedCoordinateBuffer(OverflowingPreallocatedFloatBuffer floatBuffer) {
        super(null);
        this.floatBuffer = floatBuffer;
    }

    public static OverflowingPreallocatedCoordinateBuffer acquire() {
        return new OverflowingPreallocatedCoordinateBuffer(OverflowingPreallocatedFloatBuffer.acquire());
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
        assert  i < size();
        return floatBuffer.get(i * 3);
    }

    /**
     * Returns the Y component of the i-th coordinate.
     */
    public float getY(int i) {
        assert  i < size();
        return floatBuffer.get(i * 3 + 1);
    }

    /**
     * Returns the Z component of the i-th coordinate.
     */
    public float getZ(int i) {
        assert  i < size();
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

    @Override
    public void releasePooledResourcesRecursively() {
        clear();
        floatBuffer.releasePooledResourcesRecursively();
    }
}
