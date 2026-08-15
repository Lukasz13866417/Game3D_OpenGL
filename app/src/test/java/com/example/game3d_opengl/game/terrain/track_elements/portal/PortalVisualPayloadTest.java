package com.example.game3d_opengl.game.terrain.track_elements.portal;

import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Pure-CPU checks for the core-portal values copied into GL draw payloads. */
public final class PortalVisualPayloadTest {
    @Test
    public void forwardAndUpProduceThePortalBasis() {
        float[] basis = new float[9];

        PortalVisual.writeBaseRotation(
                new Vector3D(1f, 0f, 0f),
                new Vector3D(1f, 0f, 1f),
                basis);

        // Column-major local axes: X is across, Y is core forward, Z is authored up.
        assertEquals(0f, basis[0], 1e-6f);
        assertEquals(-1f, basis[1], 1e-6f);
        assertEquals(0f, basis[2], 1e-6f);
        assertEquals(1f, basis[3], 1e-6f);
        assertEquals(0f, basis[4], 1e-6f);
        assertEquals(0f, basis[5], 1e-6f);
        assertEquals(0f, basis[6], 1e-6f);
        assertEquals(0f, basis[7], 1e-6f);
        assertEquals(1f, basis[8], 1e-6f);
    }

    @Test
    public void widthAndHeightRemainIndependentInShaderScale() {
        float[] scale = new float[3];

        PortalVisual.writeVisualScale(4f, 2f, 1f, scale);

        assertEquals(2f, scale[0], 0f);
        assertEquals(1f, scale[1], 0f);
        assertEquals(1f, scale[2], 0f);
    }

    @Test
    public void canonicalStylePreservesParityAndOtherStylesReachThePayload() {
        assertEquals(0f, PortalVisual.styleAccent("BEACON"), 0f);
        assertTrue(PortalVisual.styleAccent("CUSTOM_BEACON") > 0f);
    }
}
