package com.example.game3d_opengl.rendering.infill;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** Compiles the instanced spin shaders against a real mobile OpenGL ES 3 driver. */
@RunWith(AndroidJUnit4.class)
public class PlayerSpinBlurShaderAndroidTest {
    private static final int EGL_OPENGL_ES3_BIT_KHR = 0x0040;

    @Test
    public void playerFillShadersCompileAndLinkOnDevice() {
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        assertNotEquals(EGL14.EGL_NO_DISPLAY, display);
        int[] versions = new int[2];
        assertTrue(EGL14.eglInitialize(display, versions, 0, versions, 1));

        EGLContext context = EGL14.EGL_NO_CONTEXT;
        EGLSurface surface = EGL14.EGL_NO_SURFACE;
        try {
            EGLConfig[] configs = new EGLConfig[1];
            int[] configCount = new int[1];
            int[] configAttributes = new int[]{
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_DEPTH_SIZE, 16,
                    EGL14.EGL_NONE
            };
            assertTrue(EGL14.eglChooseConfig(
                    display,
                    configAttributes,
                    0,
                    configs,
                    0,
                    configs.length,
                    configCount,
                    0));
            assertTrue(configCount[0] > 0);

            int[] contextAttributes = new int[]{
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                    EGL14.EGL_NONE
            };
            context = EGL14.eglCreateContext(
                    display,
                    configs[0],
                    EGL14.EGL_NO_CONTEXT,
                    contextAttributes,
                    0);
            assertNotEquals(EGL14.EGL_NO_CONTEXT, context);

            int[] surfaceAttributes = new int[]{
                    EGL14.EGL_WIDTH, 1,
                    EGL14.EGL_HEIGHT, 1,
                    EGL14.EGL_NONE
            };
            surface = EGL14.eglCreatePbufferSurface(
                    display,
                    configs[0],
                    surfaceAttributes,
                    0);
            assertNotEquals(EGL14.EGL_NO_SURFACE, surface);
            assertTrue(EGL14.eglMakeCurrent(
                    display, surface, surface, context));

            Context appContext = ApplicationProvider.getApplicationContext();
            FlatLitShaderPair.LOAD_SHADER_CODE();
            InfillShaderPair.LOAD_SHADER_CODE(appContext.getAssets());
            assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError());
        } finally {
            EGL14.eglMakeCurrent(
                    display,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT);
            if (!EGL14.EGL_NO_SURFACE.equals(surface)) {
                EGL14.eglDestroySurface(display, surface);
            }
            if (!EGL14.EGL_NO_CONTEXT.equals(context)) {
                EGL14.eglDestroyContext(display, context);
            }
            EGL14.eglTerminate(display);
        }
    }
}
