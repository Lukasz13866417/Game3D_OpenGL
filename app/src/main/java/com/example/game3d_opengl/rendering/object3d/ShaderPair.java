package com.example.game3d_opengl.rendering.object3d;

import android.opengl.GLES20;

/**
 * Wrapper around a glProgram - a pair of vertex and fragment shader.
 * The purpose is to separate this concern from the Polygon3D class.
 */
public abstract class ShaderPair<VertexShaderArgValues extends ShaderArgValues,
                                FragmentShaderArgValues extends ShaderArgValues> {

    // 1) Constants and static fields

    // 2) Instance fields
    private int programHandle;
    private final String vsSource;
    private final String fsSource;
    private VertexShaderArgValues va = null;
    private FragmentShaderArgValues fa = null;

    // 3) Constructors
    protected ShaderPair(int programHandle, String vsSource, String fsSource) {
        this.programHandle = programHandle;
        this.vsSource = vsSource;
        this.fsSource = fsSource;
    }

    // Nested builder
    public static abstract class BaseBuilder<S extends ShaderPair<?,?>, B extends BaseBuilder<S,B>> {
        protected String vsSource;
        protected String fsSource;
        protected Integer existingProgramHandle; // optional

        protected abstract B self();
        protected abstract S create(int programHandle, String vs, String fs);

        public B fromSource(String vs, String fs) {
            this.vsSource = vs;
            this.fsSource = fs;
            this.existingProgramHandle = null;
            return self();
        }

        public B fromExistingProgram(int programHandle, String vs, String fs) {
            this.vsSource = vs;
            this.fsSource = fs;
            this.existingProgramHandle = programHandle;
            return self();
        }

        public final S build() {
            int handle = (existingProgramHandle != null)
                    ? existingProgramHandle
                    : createProgram(vsSource, fsSource);
            S pair = create(handle, vsSource, fsSource);
            pair.setupAttribLocations();
            return pair;
        }
    }

    // 4) Public methods (API)
    public final void setArgValues(VertexShaderArgValues va, FragmentShaderArgValues fa){
        this.va = va;
        this.fa = fa;
    }

    public final void transferArgsToGPU(){
        transferArgsToGPU(va, fa);
    }

    /**
     * Enable and set up all vertex attribute pointers from the currently bound VBOs.
     * Called by draw paths before issuing draw calls.
     */
    public abstract void enableAndPointVertexAttribs();

    /**
     * Disable previously enabled vertex attribute arrays. Intended to be called once after
     * a batch of draws that shared the same attribute layout.
     */
    public abstract void disableVertexAttribs();

    public final void setAsCurrentProgram(){
        GLES20.glUseProgram(programHandle);
    }

    /**
     * Recreate the program from the original sources. Call after context loss.
     */
    public void reloadProgram(){
        if (getProgramHandle() != 0 && GLES20.glIsProgram(getProgramHandle())) {
            GLES20.glDeleteProgram(getProgramHandle());
        }
        programHandle = createProgram(vsSource, fsSource);
        setupAttribLocations();
        assert programHandle != 0;
    }

    protected abstract void setupAttribLocations();

    protected abstract void transferArgsToGPU(VertexShaderArgValues va, FragmentShaderArgValues fa);

    protected int getProgramHandle(){
        return programHandle;
    }

    private static int createProgram(String vertexShaderCode, String fragmentShaderCode) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        int programId = GLES20.glCreateProgram();
        GLES20.glAttachShader(programId, vertexShader);
        GLES20.glAttachShader(programId, fragmentShader);
        GLES20.glLinkProgram(programId);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            String errorMsg = GLES20.glGetProgramInfoLog(programId);
            GLES20.glDeleteProgram(programId);
            throw new RuntimeException("Program link error:\n" + errorMsg);
        }

        // shaders can be flagged for deletion once linked
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);
        return programId;
    }

    public static int loadShader(int type, String code) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);

        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            String errorMsg = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("Shader compile error:\n" + errorMsg);
        }
        return shader;
    }
}
