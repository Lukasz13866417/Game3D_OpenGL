package com.example.game3d_opengl.rendering.shader;

import com.example.game3d_opengl.rendering.layout.VertexLayout;

public abstract class MeshShaderPair<
        VS extends ShaderArgValues,
        FS extends ShaderArgValues,
        L extends VertexLayout>
        extends ShaderPair<VS, FS> {

    private L boundLayout;

    protected MeshShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }

    public final void bindLayout(L layout) {
        if (layout == null) {
            throw new IllegalArgumentException("layout == null");
        }
        this.boundLayout = layout;
    }

    protected final L requireBoundLayout() {
        if (boundLayout == null) {
            throw new IllegalStateException("No vertex layout bound before attribute setup.");
        }
        return boundLayout;
    }

    @Override
    public final void enableAndPointVertexAttribs() {
        enableAndPointVertexAttribs(requireBoundLayout());
    }

    protected abstract void enableAndPointVertexAttribs(L layout);
}
