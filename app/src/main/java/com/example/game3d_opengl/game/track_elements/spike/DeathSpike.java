package com.example.game3d_opengl.game.track_elements.spike;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.rendering.util3d.GameMath.getNormal;
import static com.example.game3d_opengl.rendering.util3d.RenderingUtils.ID_NOT_SET;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3S;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.util3d.GameRandom;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;
import com.example.game3d_opengl.rendering.util3d.FColor;

public class DeathSpike extends Addon {

    private final float height;
    private final float baseOffset = 0.025f;

    private static int SPIKE_FILL_VBO_ID = ID_NOT_SET;
    private static int SPIKE_WIRE_VBO_ID = ID_NOT_SET;
    private static final int STRIDE_BYTES = 5 * 4; // vec4 weights + float t

    // Per-instance mesh wrapper for infill draw
    private SpikeInfillMesh3D fillMesh;
    // Wireframe mesh for edges
    private SpikeWireframeMesh3D wireMesh;

    public static void LOAD_DEATHSPIKE_ASSETS(){
        assert SPIKE_FILL_VBO_ID == ID_NOT_SET;
        assert SPIKE_WIRE_VBO_ID == ID_NOT_SET;
        int[] ids = new int[2];
        GLES20.glGenBuffers(2, ids,0);
        SPIKE_FILL_VBO_ID = ids[0];
        SPIKE_WIRE_VBO_ID = ids[1];
    }

    private DeathSpike(float height) {
        super();
        this.height = height;
    }
    
    public static DeathSpike createDeathSpike() {
        float height = GameRandom.randFloat(0.225f, 0.5f, 5);
        return new DeathSpike(height); // object3D will be set in onPlace
    }

    private static Vector3D[] computeCornerTargets(Vector3D fieldNearLeft, Vector3D fieldNearRight,
                                        Vector3D fieldFarLeft, Vector3D fieldFarRight) {
        Vector3D fieldMid = fieldFarLeft.add(fieldFarRight)
                .add(fieldNearRight).add(fieldNearLeft).div(4);
        Vector3D myNL = fieldMid.add(fieldNearLeft.sub(fieldMid).mult(0.8f));
        Vector3D myNR = fieldMid.add(fieldFarLeft.sub(fieldMid).mult(0.8f));
        Vector3D myFL = fieldMid.add(fieldNearRight.sub(fieldMid).mult(0.8f));
        Vector3D myFR = fieldMid.add(fieldFarRight.sub(fieldMid).mult(0.8f));
        return V3S(myNL, myNR, myFR, myFL);
    }

    @Override
    protected void onPlace(Vector3D fieldNearLeft, Vector3D fieldNearRight,
                           Vector3D fieldFarLeft, Vector3D fieldFarRight) {
        Vector3D[] corners = computeCornerTargets(fieldNearLeft,
                                                  fieldNearRight,
                                                  fieldFarLeft,
                                                  fieldFarRight);
        Vector3D fieldMid = fieldFarLeft.add(fieldFarRight).add(fieldNearRight).add(fieldNearLeft).div(4);
        Vector3D normal = getNormal(fieldNearLeft, fieldFarLeft, fieldFarRight);
        Vector3D unitNormal = normal.withLen(1f);
        Vector3D apex = fieldMid.add(unitNormal.withLen(height));

        final float[] uNL = new float[3],
                      uNR = new float[3],
                      uFL = new float[3],
                      uFR = new float[3];
        final float[] uApex = new float[3],
                      uNormal = new float[3];
        uNL[0]=corners[0].x; uNL[1]=corners[0].y; uNL[2]=corners[0].z;
        uNR[0]=corners[1].x; uNR[1]=corners[1].y; uNR[2]=corners[1].z;
        uFR[0]=corners[2].x; uFR[1]=corners[2].y; uFR[2]=corners[2].z;
        uFL[0]=corners[3].x; uFL[1]=corners[3].y; uFL[2]=corners[3].z;
        uApex[0]=apex.x; uApex[1]=apex.y; uApex[2]=apex.z;
        uNormal[0]=unitNormal.x; uNormal[1]=unitNormal.y; uNormal[2]=unitNormal.z;

        SpikeInfillShaderPair shader = SpikeInfillShaderPair.getSharedShader();
        SpikeInfillMesh3D.Builder b = new SpikeInfillMesh3D.Builder()
                .shader(shader)
                .vboId(SPIKE_FILL_VBO_ID)
                .instanceUniforms(uNL, uNR, uFR, uFL, uApex, uNormal, baseOffset)
                .color(FColor.CLR(0,0,0,1));
        // Explicitly set faces/verts to satisfy builder contract
        b.verts(new Vector3D[]{
                new Vector3D(0,0,0), new Vector3D(0,0,0), new Vector3D(0,0,0),
                new Vector3D(0,0,0), new Vector3D(0,0,0)
        });
        b.faces(new int[][]{
                new int[]{0,1,4},
                new int[]{1,2,4},
                new int[]{2,3,4},
                new int[]{3,0,4}
        });
        fillMesh = b.buildObject();

        // Build wireframe mesh using combined spike-wireframe shader
        SpikeWireframeShaderPair wireShader = SpikeWireframeShaderPair.getSharedShader();
        wireMesh = new SpikeWireframeMesh3D.Builder()
                .shader(wireShader)
                .vboId(SPIKE_WIRE_VBO_ID)
                .instanceUniforms(uNL, uNR, uFR, uFL, uApex, uNormal, baseOffset)
                .color(CLR(1,1,1,1))
                .pixelWidth(1.5f)
                .verts(new Vector3D[]{
                        new Vector3D(0,0,0), new Vector3D(0,0,0), new Vector3D(0,0,0),
                        new Vector3D(0,0,0), new Vector3D(0,0,0)
                })
                .faces(new int[][]{
                        new int[]{0,1,4},
                        new int[]{1,2,4},
                        new int[]{2,3,4},
                        new int[]{3,0,4}
                })
                .buildObject();

    }

    @Override
    public void draw(float[] vpMatrix) {
        if (fillMesh != null) fillMesh.draw(vpMatrix);
        if (wireMesh != null) wireMesh.draw(vpMatrix);
    }

    @Override
    public void updateBeforeDraw(float dt) {

    }

    @Override
    public void updateAfterDraw(float dt) {

    }

    @Override
    public void cleanupOwnedGPUResources() {
        if (fillMesh != null) fillMesh.cleanupOwnedGPUResources();
        if (wireMesh != null) wireMesh.cleanupOwnedGPUResources();
    }

    @Override
    public void reloadOwnedGPUResources() {
        if (fillMesh != null) fillMesh.reloadOwnedGPUResources();
        if (wireMesh != null) wireMesh.reloadOwnedGPUResources();
    }


}
