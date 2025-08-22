package com.example.game3d_opengl.rendering.object3d;


import android.opengl.GLES20;

/**
 * Wrapper around a glProgram - a pair of vertex and fragment shader.
 * The purpose is to separate this concern from the Polygon3D class.
 */
public abstract class ShaderPair<VertexShaderArgValues extends ShaderArgValues,
                                FragmentShaderArgValues extends ShaderArgValues> {


    private static final int SHADER_PROGRAM_INITIAL_VALUE = 0;

    private int programHandle;
    private final String vsSource;
    private final String fsSource;
    protected ShaderPair(int programHandle, String vsSource, String fsSource) {
        this.programHandle = programHandle;
        this.vsSource = vsSource;
        this.fsSource = fsSource;
    }

    // ===== Builder base class =====
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
                    : createPrograms(vsSource, fsSource);
            S pair = create(handle, vsSource, fsSource);
            pair.setupAttribLocations();
            return pair;
        }
    }

    // ===== factory hook =====
    @FunctionalInterface
    public interface Creator<T extends ShaderPair<?,?>> {
        T create(int programHandle, String vs, String fs);
    }

    public static <T extends ShaderPair<?,?>> T create(
            String vs, String fs, Creator<T> maker) {
        int handle = createProgram(vs, fs);      // compile + link here
        return maker.create(handle, vs, fs);     // subclass ctor receives handle
    }

    private static int createProgram(String vs, String fs) {
        int v = loadShader(GLES20.GL_VERTEX_SHADER, vs);
        int f = loadShader(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(p);
            GLES20.glDeleteProgram(p);
            GLES20.glDeleteShader(v);
            GLES20.glDeleteShader(f);
            throw new RuntimeException("Program link error:\n" + log);
        }
        GLES20.glDeleteShader(v);
        GLES20.glDeleteShader(f);
        return p;
    }

    /**
     * Resets the shader program when GL context is recreated.
     * This is a package-private method that allows Object3D to reset the program
     * after context recreation.
     */
    public void resetProgram() {
        if (programHandle != SHADER_PROGRAM_INITIAL_VALUE) {
            if (android.opengl.GLES20.glIsProgram(programHandle)) {
                android.opengl.GLES20.glDeleteProgram(programHandle);
            }
            programHandle = SHADER_PROGRAM_INITIAL_VALUE;
        }
    }

    protected abstract void setupAttribLocations();


    private VertexShaderArgValues va = null;
    private FragmentShaderArgValues fa = null;

    public void setArgValues(VertexShaderArgValues va, FragmentShaderArgValues fa){
        this.va = va;
        this.fa = fa;
    }


    protected int getProgramHandle(){
        return programHandle;
    }

    public final void transferArgsToGPU(){
        transferArgsToGPU(va,fa);
    }

    protected abstract void transferArgsToGPU(VertexShaderArgValues va, FragmentShaderArgValues fa);

    protected abstract void cleanupAfterDraw();

    /**
     * Enable and set up all vertex attribute pointers from the currently bound VBOs.
     * Called by draw paths before issuing draw calls.
     */
    public abstract void enableAndPointVertexAttribs();

    public final void setAsCurrentProgram(){
        GLES20.glUseProgram(programHandle);
    }

    // I want the user to keep track of the program state, instead of constantly checking.
    public void reloadProgram(){
        // optional: clean previous if still valid
        if (getProgramHandle() != 0 && GLES20.glIsProgram(getProgramHandle())) {
            // safe even if context was lost; glIsProgram will be false
            GLES20.glDeleteProgram(getProgramHandle());
        }
        programHandle = createPrograms(vsSource, fsSource);
        setupAttribLocations(); // subclasses refresh locations
        assert programHandle != 0;
    }

    private static int createPrograms(String vertexShaderCode, String fragmentShaderCode) {
        // Compile & link
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        int programId = GLES20.glCreateProgram();
        GLES20.glAttachShader(programId, vertexShader);
        GLES20.glAttachShader(programId, fragmentShader);
        GLES20.glLinkProgram(programId);

        // Optional: check link status
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            String errorMsg = GLES20.glGetProgramInfoLog(programId);
            GLES20.glDeleteProgram(programId);
            throw new RuntimeException("Program link error:\n" + errorMsg);
        }
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
