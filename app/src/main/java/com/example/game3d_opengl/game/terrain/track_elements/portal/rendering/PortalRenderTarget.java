package com.example.game3d_opengl.game.terrain.track_elements.portal.rendering;

import android.opengl.GLES20;
import android.util.Log;

import com.example.game3d_opengl.rendering.RenderTarget;

public final class PortalRenderTarget implements RenderTarget {
    private static final String TAG = "PortalRenderTarget";

    private final int width;
    private final int height;

    private int fboId = 0;
    private int textureId = 0;
    private int depthRboId = 0;

    public PortalRenderTarget(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        create();
    }

    @Override
    public void bind() {
        if (fboId == 0) {
            create();
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);
    }

    @Override
    public void unbind() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getTextureId() {
        return textureId;
    }

    private void create() {
        int[] ids = new int[1];

        // Texture
        GLES20.glGenTextures(1, ids, 0);
        textureId = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                width,
                height,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                null
        );
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        // Depth renderbuffer
        GLES20.glGenRenderbuffers(1, ids, 0);
        depthRboId = ids[0];
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depthRboId);
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, width, height);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0);

        // FBO
        GLES20.glGenFramebuffers(1, ids, 0);
        fboId = ids[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);
        GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                textureId,
                0
        );
        GLES20.glFramebufferRenderbuffer(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_DEPTH_ATTACHMENT,
                GLES20.GL_RENDERBUFFER,
                depthRboId
        );
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "FBO incomplete: status=" + status);
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    private void cleanup() {
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
            textureId = 0;
        }
        if (depthRboId != 0) {
            GLES20.glDeleteRenderbuffers(1, new int[]{depthRboId}, 0);
            depthRboId = 0;
        }
        if (fboId != 0) {
            GLES20.glDeleteFramebuffers(1, new int[]{fboId}, 0);
            fboId = 0;
        }
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        cleanup();
        create();
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        cleanup();
    }
}
