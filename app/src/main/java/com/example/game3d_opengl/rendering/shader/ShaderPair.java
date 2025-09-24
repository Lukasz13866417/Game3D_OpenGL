package com.example.game3d_opengl.rendering.shader;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.GPUResourceOwner;

/**
 * Wrapper around a glProgram - a pair of vertex and fragment shader.
 * The purpose is to separate this concern from the Polygon3D class.
 */
public abstract class ShaderPair<VS extends ShaderArgValues, FS extends ShaderArgValues> implements GPUResourceOwner {

    // 1) Constants and static fields

    // 2) Instance fields
    private int programHandle;
    private final String vsSource;
    private final String fsSource;
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

        protected abstract B self();
        protected abstract S create(int programHandle, String vs, String fs);

        public B fromSource(String vs, String fs) {
            this.vsSource = vs;
            this.fsSource = fs;
            return self();
        }

        public final S build() {
            int handle = createProgram(vsSource, fsSource);
            S pair = create(handle, vsSource, fsSource);
            pair.setupAttribLocations();
            return pair;
        }
    }


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


    private VS vsArgs = null;
    private FS fsArgs = null;

    public void setArgs(VS vs, FS fs){
        this.vsArgs = vs;
        this.fsArgs = fs;
    }

    protected abstract void transferArgsToGPU(VS vs, FS fs);

    public void transferArgsToGPU(){
        transferArgsToGPU(vsArgs, fsArgs);
    }


    protected final int getProgramHandle(){
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

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        reloadProgram();
    }

    @Override
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {
        // Programs are managed by GL; nothing to cleanup explicitly here
    }
}
