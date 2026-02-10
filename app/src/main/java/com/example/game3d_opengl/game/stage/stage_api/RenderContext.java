package com.example.game3d_opengl.game.stage.stage_api;

import com.example.game3d_opengl.rendering.RenderTarget;

public final class RenderContext {
    public static final int FLAG_SKIP_PORTALS = 1;

    public float[] vp;
    public RenderTarget target; // null means default framebuffer
    public int viewportW;
    public int viewportH;
    public int flags;
    public boolean clear = true;

    public boolean shouldSkipPortals() {
        return (flags & FLAG_SKIP_PORTALS) != 0;
    }
}
