package com.example.game3d_opengl.game.terrain.track_elements.spike;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.game.util.GameMath.getNormal;
import static com.example.game3d_opengl.rendering.util3d.RenderingUtils.ID_NOT_SET;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3S;

import android.opengl.GLES20;

import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.game.util.GameRandom;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.rendering.util3d.FColor;

public class DeathSpike extends Addon {

    private final float height;
    private final float baseOffset = 0.025f;

    private static int SPIKE_FILL_VBO_ID = ID_NOT_SET;
    private static int SPIKE_WIRE_VBO_ID = ID_NOT_SET;

    // Shared canonical meshes (geometry/VBO/IBO)
    private static SpikeInfillMesh3D SHARED_FILL_MESH;
    private static SpikeWireframeMesh3D SHARED_WIRE_MESH;

    // Per-instance draw args (uniforms)
    private SpikeInfillDrawArgs infillArgs;
    private SpikeWireframeDrawArgs wireArgs;

    public static void LOAD_DEATHSPIKE_ASSETS(){
        SpikeInfillShaderPair.LOAD_SHADER_CODE();
        SpikeWireframeShaderPair.LOAD_SHADER_CODE();
        assert SPIKE_FILL_VBO_ID == ID_NOT_SET;
        assert SPIKE_WIRE_VBO_ID == ID_NOT_SET;
        int[] ids = new int[2];
        GLES20.glGenBuffers(2, ids,0);
        SPIKE_FILL_VBO_ID = ids[0];
        SPIKE_WIRE_VBO_ID = ids[1];

        // Build shared fill mesh (uploads canonical data into SPIKE_FILL_VBO_ID)
        SpikeInfillShaderPair fillShader = SpikeInfillShaderPair.getSharedShader();
        SHARED_FILL_MESH = new SpikeInfillMesh3D.Builder()
                .shader(fillShader)
                .vboId(SPIKE_FILL_VBO_ID)
                .color(FColor.CLR(0,0,0,1))
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

        // Build shared wireframe mesh (uploads canonical expanded edge data into SPIKE_WIRE_VBO_ID)
        SpikeWireframeShaderPair wireShader = SpikeWireframeShaderPair.getSharedShader();
        SHARED_WIRE_MESH = new SpikeWireframeMesh3D.Builder()
                .shader(wireShader)
                .vboId(SPIKE_WIRE_VBO_ID)
                .color(CLR(1,1,1,1))
                .pixelWidth(1.5f)
                // dummy verts/faces to satisfy builder contract (overridden internally)
                .verts(new Vector3D[]{ new Vector3D(0,0,0) })
                .faces(new int[][]{ new int[]{0,0,0} })
                .buildObject();
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
        Vector3D myNL = fieldNearLeft;//fieldMid.add(fieldNearLeft.sub(fieldMid).mult(0.8f));
        Vector3D myNR = fieldNearRight;//fieldMid.add(fieldNearRight.sub(fieldMid).mult(0.8f));
        Vector3D myFL = fieldFarLeft;//fieldMid.add(fieldFarLeft.sub(fieldMid).mult(0.8f));
        Vector3D myFR = fieldFarRight;//fieldMid.add(fieldFarRight.sub(fieldMid).mult(0.8f));
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
        Vector3D apex = fieldMid.sub(unitNormal.withLen(height));
        Vector3D baseNormal = unitNormal.mult(-1f);

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
        uNormal[0]=baseNormal.x; uNormal[1]=baseNormal.y; uNormal[2]=baseNormal.z;

        // Per-instance args only (shared geometry)
        this.infillArgs = new SpikeInfillDrawArgs(uNL, uNR, uFR, uFL, uApex, uNormal, baseOffset);
        this.wireArgs = new SpikeWireframeDrawArgs(1.5f, -2e-4f, uNL, uNR, uFR, uFL, uApex, uNormal, baseOffset, CLR(1,1,1,1));

    }

    @Override
    public void draw(float[] vpMatrix) {
        if (infillArgs != null) { infillArgs.vp = vpMatrix; SHARED_FILL_MESH.draw(infillArgs); }
        if (wireArgs != null) { wireArgs.vp = vpMatrix; SHARED_WIRE_MESH.draw(wireArgs); }
    }

    @Override
    public void updateBeforeDraw(float dt) {

    }

    @Override
    public void updateAfterDraw(float dt) {

    }

    @Override
    public void rebasePosition(Vector3D delta) {
        if (delta == null) return;
        float[] uNL = infillArgs != null ? infillArgs.uNL : (wireArgs != null ? wireArgs.uNL : null);
        float[] uNR = infillArgs != null ? infillArgs.uNR : (wireArgs != null ? wireArgs.uNR : null);
        float[] uFR = infillArgs != null ? infillArgs.uFR : (wireArgs != null ? wireArgs.uFR : null);
        float[] uFL = infillArgs != null ? infillArgs.uFL : (wireArgs != null ? wireArgs.uFL : null);
        float[] uApex = infillArgs != null ? infillArgs.uApex : (wireArgs != null ? wireArgs.uApex : null);
        if (uNL == null || uNR == null || uFR == null || uFL == null || uApex == null) return;
        addOffset(uNL, delta);
        addOffset(uNR, delta);
        addOffset(uFR, delta);
        addOffset(uFL, delta);
        addOffset(uApex, delta);
    }

    public boolean writeHazardPoint(float[] out) {
        if (out == null || out.length < 3) return false;
        float[] apex = infillArgs != null ? infillArgs.uApex : (wireArgs != null ? wireArgs.uApex : null);
        if (apex == null) return false;
        out[0] = apex[0];
        out[1] = apex[1];
        out[2] = apex[2];
        return true;
    }

    private static void addOffset(float[] v, Vector3D delta) {
        if (v == null) return;
        v[0] += delta.x;
        v[1] += delta.y;
        v[2] += delta.z;
    }

    @Override
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {
        SHARED_FILL_MESH.cleanupGPUResourcesRecursivelyOnContextLoss();
        SHARED_WIRE_MESH.cleanupGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        SHARED_FILL_MESH.reloadGPUResourcesRecursivelyOnContextLoss();
        SHARED_WIRE_MESH.reloadGPUResourcesRecursivelyOnContextLoss();
    }


    @Override
    public void accept(Player player) {
        player.interactWith(this);
    }
}
