package com.example.game3d_opengl.rendering.object3d;

import static com.example.game3d_opengl.rendering.object3d.BasicShaderPair.sharedShader;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.util3d.FColor;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Polygon with the simplest shader (uMVPMatrix + vColor).
 */
public final class BasicPolygon3D extends Polygon3D<BasicShaderArgs.VS, BasicShaderArgs.FS, BasicShaderPair> {

    private final BasicShaderArgs.VS vsArgs = new BasicShaderArgs.VS();
    private final BasicShaderArgs.FS fsArgs = new BasicShaderArgs.FS();

    private FColor fillColor, edgeColor;


    private BasicPolygon3D(Builder b) {
        super(b, sharedShader);
        this.fillColor = b.fillColor;
        this.edgeColor = b.edgeColor;
    }

    public static final class Builder extends Polygon3D.BaseBuilder<BasicPolygon3D, Builder> {

        private FColor fillColor = new FColor(1, 1, 1, 1),
                edgeColor = new FColor(1, 1, 1, 1);

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        protected BasicPolygon3D create() {
            return new BasicPolygon3D(this);
        }

        public Builder fillColor(FColor what) {
            this.fillColor = what;
            return this;
        }

        public Builder edgeColor(FColor what) {
            this.edgeColor = what;
            return this;
        }

    }

    /**
     * Updates the fill and outline colors for this polygon.
     * Changes take effect on the next draw call.
     *
     * @param newFillColor    the new interior color
     * @param newOutlineColor the new outline color
     */
    public void setFillAndOutline(FColor newFillColor, FColor newOutlineColor) {
        if (newFillColor == null || newOutlineColor == null) {
            throw new IllegalArgumentException("Colors cannot be null");
        }
        this.fillColor = newFillColor;
        this.edgeColor = newOutlineColor;
    }

    public FColor getFillColor(){
        return fillColor;
    }

    public FColor getEdgeColor(){
        return edgeColor;
    }

    @Override
    protected android.util.Pair<BasicShaderArgs.VS, BasicShaderArgs.FS> setupShaderArgs(float[] mvpMatrix, boolean isFill) {
        vsArgs.mvp = mvpMatrix;
        fsArgs.color = isFill ? getFillColor() : getEdgeColor();
        return android.util.Pair.create(vsArgs, fsArgs);
    }
}


