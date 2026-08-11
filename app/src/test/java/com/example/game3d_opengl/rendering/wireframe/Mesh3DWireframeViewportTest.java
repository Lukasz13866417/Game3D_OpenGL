package com.example.game3d_opengl.rendering.wireframe;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class Mesh3DWireframeViewportTest {
    @Test
    public void preservesViewportOriginForFragmentSpaceLineClipping() {
        WireframeShaderArgs.VS args = new WireframeShaderArgs.VS();

        Mesh3DWireframe.applyViewport(new int[]{7, 93, 1080, 2097}, args);

        assertEquals(7, args.viewportX);
        assertEquals(93, args.viewportY);
        assertEquals(1080, args.viewportW);
        assertEquals(2097, args.viewportH);
    }

    @Test
    public void clampsOnlyViewportDimensionsNotItsOrigin() {
        WireframeShaderArgs.VS args = new WireframeShaderArgs.VS();

        Mesh3DWireframe.applyViewport(new int[]{-4, -9, 0, -2}, args);

        assertEquals(-4, args.viewportX);
        assertEquals(-9, args.viewportY);
        assertEquals(1, args.viewportW);
        assertEquals(1, args.viewportH);
    }
}
