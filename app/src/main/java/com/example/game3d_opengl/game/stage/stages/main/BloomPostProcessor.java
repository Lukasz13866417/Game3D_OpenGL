package com.example.game3d_opengl.game.stage.stages.main;

import android.opengl.GLES20;

import com.example.game3d_opengl.game.terrain.track_elements.portal.rendering.PortalRenderTarget;
import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.RenderTarget;

/**
 * Simple bloom pipeline:
 * 1) Render scene into sceneTarget
 * 2) Bright-pass into bloomA
 * 3) Separable blur ping-pong bloomA <-> bloomB
 * 4) Composite scene + blurred bloom to default framebuffer
 */
final class BloomPostProcessor implements GPUResourceOwner {
    private static final float BLOOM_THRESHOLD = 0.64f;
    private static final float BLOOM_INTENSITY = 0.95f;
    private static final int BLUR_ITERATIONS = 2;
    private static final float DOWNSAMPLE = 0.5f;

    private static final float[] FSQ_VERTS = new float[]{
            -1f, -1f, 0f, 0f, 0f,
            1f, -1f, 0f, 1f, 0f,
            1f, 1f, 0f, 1f, 1f,
            -1f, 1f, 0f, 0f, 1f
    };
    private static final short[] FSQ_IDX = new short[]{0, 1, 2, 0, 2, 3};

    private int surfaceW;
    private int surfaceH;
    private int bloomW;
    private int bloomH;

    private PortalRenderTarget sceneTarget;
    private PortalRenderTarget bloomTargetA;
    private PortalRenderTarget bloomTargetB;

    private int vboId = 0;
    private int iboId = 0;

    private int prefilterProgram = 0;
    private int blurProgram = 0;
    private int compositeProgram = 0;

    private int preApos = -1;
    private int preAuv = -1;
    private int preTex = -1;
    private int preThreshold = -1;

    private int blurApos = -1;
    private int blurAuv = -1;
    private int blurTex = -1;
    private int blurStep = -1;

    private int compApos = -1;
    private int compAuv = -1;
    private int compSceneTex = -1;
    private int compBloomTex = -1;
    private int compIntensity = -1;

    BloomPostProcessor(int surfaceW, int surfaceH) {
        this.surfaceW = Math.max(1, surfaceW);
        this.surfaceH = Math.max(1, surfaceH);
        initFullscreenQuadBuffers();
        initPrograms();
        createTargets();
    }

    RenderTarget getSceneTarget() {
        return sceneTarget;
    }

    void resize(int width, int height) {
        int w = Math.max(1, width);
        int h = Math.max(1, height);
        if (w == surfaceW && h == surfaceH) {
            return;
        }
        surfaceW = w;
        surfaceH = h;
        createTargets();
    }

    void compositeToScreen() {
        if (sceneTarget == null || bloomTargetA == null || bloomTargetB == null) {
            return;
        }

        // 1) Bright prefilter from scene -> bloom A
        bloomTargetA.bind();
        GLES20.glViewport(0, 0, bloomW, bloomH);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        drawPrefilter(sceneTarget.getTextureId());
        bloomTargetA.unbind();

        // 2) Blur ping-pong
        for (int i = 0; i < BLUR_ITERATIONS; ++i) {
            bloomTargetB.bind();
            GLES20.glViewport(0, 0, bloomW, bloomH);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            drawBlur(bloomTargetA.getTextureId(), 1f / Math.max(1f, bloomW), 0f);
            bloomTargetB.unbind();

            bloomTargetA.bind();
            GLES20.glViewport(0, 0, bloomW, bloomH);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            drawBlur(bloomTargetB.getTextureId(), 0f, 1f / Math.max(1f, bloomH));
            bloomTargetA.unbind();
        }

        // 3) Composite scene + bloom to screen
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, surfaceW, surfaceH);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);
        drawComposite(sceneTarget.getTextureId(), bloomTargetA.getTextureId());
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        initFullscreenQuadBuffers();
        initPrograms();
        if (sceneTarget != null) {
            sceneTarget.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (bloomTargetA != null) {
            bloomTargetA.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (bloomTargetB != null) {
            bloomTargetB.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        if (sceneTarget != null) {
            sceneTarget.cleanupGPUResourcesRecursively();
        }
        if (bloomTargetA != null) {
            bloomTargetA.cleanupGPUResourcesRecursively();
        }
        if (bloomTargetB != null) {
            bloomTargetB.cleanupGPUResourcesRecursively();
        }
        deleteBuffers();
        deletePrograms();
    }

    private void createTargets() {
        if (sceneTarget != null) {
            sceneTarget.cleanupGPUResourcesRecursively();
        }
        if (bloomTargetA != null) {
            bloomTargetA.cleanupGPUResourcesRecursively();
        }
        if (bloomTargetB != null) {
            bloomTargetB.cleanupGPUResourcesRecursively();
        }

        bloomW = Math.max(1, Math.round(surfaceW * DOWNSAMPLE));
        bloomH = Math.max(1, Math.round(surfaceH * DOWNSAMPLE));
        sceneTarget = new PortalRenderTarget(surfaceW, surfaceH);
        bloomTargetA = new PortalRenderTarget(bloomW, bloomH);
        bloomTargetB = new PortalRenderTarget(bloomW, bloomH);
    }

    private void initFullscreenQuadBuffers() {
        deleteBuffers();
        int[] ids = new int[2];
        GLES20.glGenBuffers(2, ids, 0);
        vboId = ids[0];
        iboId = ids[1];

        java.nio.FloatBuffer vb = java.nio.ByteBuffer
                .allocateDirect(FSQ_VERTS.length * 4)
                .order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer();
        vb.put(FSQ_VERTS).position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, FSQ_VERTS.length * 4, vb, GLES20.GL_STATIC_DRAW);

        java.nio.ShortBuffer ib = java.nio.ByteBuffer
                .allocateDirect(FSQ_IDX.length * 2)
                .order(java.nio.ByteOrder.nativeOrder())
                .asShortBuffer();
        ib.put(FSQ_IDX).position(0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboId);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, FSQ_IDX.length * 2, ib, GLES20.GL_STATIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private void drawPrefilter(int inputTex) {
        GLES20.glUseProgram(prefilterProgram);
        bindQuadAttributes(preApos, preAuv);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTex);
        GLES20.glUniform1i(preTex, 0);
        GLES20.glUniform1f(preThreshold, BLOOM_THRESHOLD);

        drawQuadElements();
        unbindQuadAttributes(preApos, preAuv);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    private void drawBlur(int inputTex, float texelStepX, float texelStepY) {
        GLES20.glUseProgram(blurProgram);
        bindQuadAttributes(blurApos, blurAuv);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTex);
        GLES20.glUniform1i(blurTex, 0);
        GLES20.glUniform2f(blurStep, texelStepX, texelStepY);

        drawQuadElements();
        unbindQuadAttributes(blurApos, blurAuv);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    private void drawComposite(int sceneTex, int bloomTex) {
        GLES20.glUseProgram(compositeProgram);
        bindQuadAttributes(compApos, compAuv);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTex);
        GLES20.glUniform1i(compSceneTex, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bloomTex);
        GLES20.glUniform1i(compBloomTex, 1);
        GLES20.glUniform1f(compIntensity, BLOOM_INTENSITY);

        drawQuadElements();
        unbindQuadAttributes(compApos, compAuv);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    private void bindQuadAttributes(int aPos, int aUv) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 5 * 4, 0);
        GLES20.glEnableVertexAttribArray(aUv);
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 5 * 4, 3 * 4);
    }

    private void unbindQuadAttributes(int aPos, int aUv) {
        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aUv);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private void drawQuadElements() {
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboId);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, FSQ_IDX.length, GLES20.GL_UNSIGNED_SHORT, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private void initPrograms() {
        deletePrograms();
        prefilterProgram = createProgram(VS_FULLSCREEN, FS_PREFILTER);
        blurProgram = createProgram(VS_FULLSCREEN, FS_BLUR);
        compositeProgram = createProgram(VS_FULLSCREEN, FS_COMPOSITE);

        preApos = GLES20.glGetAttribLocation(prefilterProgram, "aPosition");
        preAuv = GLES20.glGetAttribLocation(prefilterProgram, "aUV");
        preTex = GLES20.glGetUniformLocation(prefilterProgram, "uSceneTex");
        preThreshold = GLES20.glGetUniformLocation(prefilterProgram, "uThreshold");

        blurApos = GLES20.glGetAttribLocation(blurProgram, "aPosition");
        blurAuv = GLES20.glGetAttribLocation(blurProgram, "aUV");
        blurTex = GLES20.glGetUniformLocation(blurProgram, "uInputTex");
        blurStep = GLES20.glGetUniformLocation(blurProgram, "uTexelStep");

        compApos = GLES20.glGetAttribLocation(compositeProgram, "aPosition");
        compAuv = GLES20.glGetAttribLocation(compositeProgram, "aUV");
        compSceneTex = GLES20.glGetUniformLocation(compositeProgram, "uSceneTex");
        compBloomTex = GLES20.glGetUniformLocation(compositeProgram, "uBloomTex");
        compIntensity = GLES20.glGetUniformLocation(compositeProgram, "uBloomIntensity");
    }

    private void deletePrograms() {
        if (prefilterProgram != 0) {
            GLES20.glDeleteProgram(prefilterProgram);
            prefilterProgram = 0;
        }
        if (blurProgram != 0) {
            GLES20.glDeleteProgram(blurProgram);
            blurProgram = 0;
        }
        if (compositeProgram != 0) {
            GLES20.glDeleteProgram(compositeProgram);
            compositeProgram = 0;
        }
    }

    private void deleteBuffers() {
        if (vboId != 0) {
            GLES20.glDeleteBuffers(1, new int[]{vboId}, 0);
            vboId = 0;
        }
        if (iboId != 0) {
            GLES20.glDeleteBuffers(1, new int[]{iboId}, 0);
            iboId = 0;
        }
    }

    private static int createProgram(String vsCode, String fsCode) {
        int vs = compileShader(GLES20.GL_VERTEX_SHADER, vsCode);
        int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsCode);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            GLES20.glDeleteShader(vs);
            GLES20.glDeleteShader(fs);
            throw new RuntimeException("Bloom program link failed: " + log);
        }
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        return program;
    }

    private static int compileShader(int type, String code) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new RuntimeException("Bloom shader compile failed: " + log);
        }
        return shader;
    }

    private static final String VS_FULLSCREEN =
            "attribute vec3 aPosition;\n" +
            "attribute vec2 aUV;\n" +
            "varying vec2 vUV;\n" +
            "void main(){\n" +
            "  vUV = aUV;\n" +
            "  gl_Position = vec4(aPosition, 1.0);\n" +
            "}\n";

    private static final String FS_PREFILTER =
            "precision mediump float;\n" +
            "uniform sampler2D uSceneTex;\n" +
            "uniform float uThreshold;\n" +
            "varying vec2 vUV;\n" +
            "void main(){\n" +
            "  vec3 c = texture2D(uSceneTex, vUV).rgb;\n" +
            "  float br = max(max(c.r, c.g), c.b);\n" +
            "  float k = max((br - uThreshold) / max(1e-4, (1.0 - uThreshold)), 0.0);\n" +
            "  gl_FragColor = vec4(c * k, 1.0);\n" +
            "}\n";

    private static final String FS_BLUR =
            "precision mediump float;\n" +
            "uniform sampler2D uInputTex;\n" +
            "uniform vec2 uTexelStep;\n" +
            "varying vec2 vUV;\n" +
            "void main(){\n" +
            "  vec3 s = texture2D(uInputTex, vUV).rgb * 0.227027;\n" +
            "  s += texture2D(uInputTex, vUV + uTexelStep * 1.384615).rgb * 0.316216;\n" +
            "  s += texture2D(uInputTex, vUV - uTexelStep * 1.384615).rgb * 0.316216;\n" +
            "  s += texture2D(uInputTex, vUV + uTexelStep * 3.230769).rgb * 0.070270;\n" +
            "  s += texture2D(uInputTex, vUV - uTexelStep * 3.230769).rgb * 0.070270;\n" +
            "  gl_FragColor = vec4(s, 1.0);\n" +
            "}\n";

    private static final String FS_COMPOSITE =
            "precision mediump float;\n" +
            "uniform sampler2D uSceneTex;\n" +
            "uniform sampler2D uBloomTex;\n" +
            "uniform float uBloomIntensity;\n" +
            "varying vec2 vUV;\n" +
            "void main(){\n" +
            "  vec3 scene = texture2D(uSceneTex, vUV).rgb;\n" +
            "  vec3 bloom = texture2D(uBloomTex, vUV).rgb;\n" +
            "  gl_FragColor = vec4(scene + bloom * uBloomIntensity, 1.0);\n" +
            "}\n";
}
