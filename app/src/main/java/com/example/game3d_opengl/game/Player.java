package com.example.game3d_opengl.game;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.rendering.util3d.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.GameMath.getNormal;
import static com.example.game3d_opengl.rendering.util3d.GameMath.isPointInTriangle;
import static com.example.game3d_opengl.rendering.util3d.GameMath.pointAndPlanePosition;
import static com.example.game3d_opengl.rendering.util3d.GameMath.rayTriangleDistance;
import static com.example.game3d_opengl.rendering.util3d.GameMath.rotY;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import static java.lang.Float.max;
import static java.lang.Math.abs;
import static java.lang.Math.signum;

import android.content.res.AssetManager;

import com.example.game3d_opengl.rendering.object3d.ModelCreator;
import com.example.game3d_opengl.rendering.object3d.Object3D;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain_api.Tile;

import java.io.IOException;

public class Player extends Object3D implements WorldActor {

    public static final float PLAYER_WIDTH = 0.132f;
    public static final float PLAYER_HEIGHT = PLAYER_WIDTH * 3.54f;

    private static Object3D.Builder playerBuilder;

    private Vector3D dir;
    private Vector3D move;

    private final float stickyRotationLastingTime = 42f;
    private float stickyRotationTime = 0.0f;
    private float stickyRotationAng = 0.0f;
    private final float stickyRotationAngDecayRate = 0.0575f;
    private final float playerSpeed = 0.04f;

    private final float stickyRotationCoeff = 0.0085f;

    private final float rotationSwipeSensitivity = 0.00052f;

    private Tile tileBelow;

    public static void LOAD_PLAYER_ASSETS(AssetManager assetManager) {
        ModelCreator playerCreator = new ModelCreator(assetManager);
        try {
            playerCreator.load("tire.obj");
            playerCreator.centerVerts();
            playerCreator.rotateX(PI / 2);
            playerCreator.rotateY(PI / 2);
            playerCreator.scaleX(PLAYER_WIDTH);
            playerCreator.scaleY(PLAYER_HEIGHT);
            playerCreator.scaleZ(PLAYER_HEIGHT);

            playerBuilder = new Builder()
                    .angles(0, 0, 0)
                    .edgeColor(CLR(1.0f, 1.0f, 1.0f, 1.0f))
                    .fillColor(CLR(0.0f, 0.0f, 0.0f, 1.0f))
                    .position(0.0f, -0.5f, -0.5f)
                    .verts(playerCreator.getVerts())
                    .faces(playerCreator.getFaces());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Player() {
        super(playerBuilder);
        dir = new Vector3D(0, 0, -1f);
        move = new Vector3D(0, 0, 0);
    }

    public synchronized Vector3D getDir() {
        return dir;
    }

    public boolean collidesTile(Tile tile) {
        Vector3D n1 = getNormal(tile.triangles[0]);
        Vector3D n2 = getNormal(tile.triangles[1]);
        float d1 = rayTriangleDistance(
                V3(objX,objY,objZ),
                n1.mult(-signum(n1.y)),
                tile.triangles[0][0],tile.triangles[0][1],tile.triangles[0][2]
                );
        if(!(!Float.isInfinite(d1) && d1/PLAYER_HEIGHT < 1.05f)){
            float d2 = rayTriangleDistance(
                    V3(objX,objY,objZ),
                    n2.mult(-signum(n2.y)),
                    tile.triangles[1][0],tile.triangles[1][1],tile.triangles[1][2]
            );
            return !Float.isInfinite(d2) && d2/PLAYER_HEIGHT < 1.05f;
        }
        return true;
        /*Vector3D fl2D = tile.farLeft.setY(0), nl2D = tile.nearLeft.setY(0),
                fr2D = tile.farRight.setY(0), nr2D = tile.nearRight.setY(0);

        Vector3D fl = tile.farLeft, nl = tile.nearLeft,
                fr = tile.farRight, nr = tile.nearRight;

        // pos + dir
        Vector3D ppd = V3(objX, objY, objZ).add(dir.mult(0.1f));
        float maxy = max(fl.y, max(fr.y, max(nl.y, nr.y)));
        return ((
                isPointInTriangle(fl2D, nl2D, nr2D, ppd.setY(0))
                        || isPointInTriangle(nr2D, fr2D, fl2D, ppd.setY(0))
        )
                && objY - maxy < PLAYER_HEIGHT + 0.15f
                && pointAndPlanePosition(nl, fl, fr, ppd) == -1);*/
    }

    public void setFooting(Tile what) {
        this.tileBelow = what;
    }

    private float fallSpeed = 0f;
    private final float fallAcc = 1e-6f;//1e-5f;

    @Override
    public void updateBeforeDraw(float dt) {

        stickyRotationTime = max(0f, stickyRotationTime - dt);
        if (stickyRotationTime == 0 && stickyRotationAng != 0) {
            float dYaw = minByAbs(signum(stickyRotationAng) * stickyRotationAngDecayRate * dt, stickyRotationAng);
            objYaw -= dYaw;
            stickyRotationAng -= dYaw;
        }

        if (tileBelow != null) {

            // find which of the two triangles we’re standing on
            Vector3D origin = V3(objX, objY, objZ);
            float bestDist = Float.POSITIVE_INFINITY;
            Vector3D[] hitTri = null;

            for (Vector3D[] tri : tileBelow.triangles) {
                // use the unit-normal for the “downward” test ray
                Vector3D nUnit = getNormal(tri);
                float d = rayTriangleDistance(
                        origin,
                        nUnit.mult(-signum(nUnit.y)),
                        tri[0], tri[1], tri[2]
                );
                if (!Float.isInfinite(d) && d < bestDist) {
                    bestDist = d;
                    hitTri = tri;
                }
            }

            if (hitTri != null) {
                // we’re on that triangle → no fall
                fallSpeed = 0f;

                // edges
                Vector3D u = hitTri[1].sub(hitTri[0]);
                Vector3D w = hitTri[2].sub(hitTri[0]);
                Vector3D n = u.crossProduct(w);  // raw normal

                // solve dir = β·u + γ·w + α·n  → projection = β·u + γ·w
                float det =
                        n.x * u.y * w.z - n.x * u.z * w.y
                                - n.y * u.x * w.z + n.y * u.z * w.x
                                + n.z * u.x * w.y - n.z * u.y * w.x;

                float beta = (
                        n.x * dir.y * w.z - n.x * dir.z * w.y
                                - n.y * dir.x * w.z + n.y * dir.z * w.x
                                + n.z * dir.x * w.y - n.z * dir.y * w.x
                ) / det;

                float gamma = (
                        n.x * u.y * dir.z - n.x * u.z * dir.y
                                - n.y * u.x * dir.z + n.y * u.z * dir.x
                                + n.z * u.x * dir.y - n.z * u.y * dir.x
                ) / det;

                // build the slide vector along the triangle plane
                move = u.mult(beta)
                        .add(w.mult(gamma))
                        .withLen(playerSpeed * dt);

            }

        } else {
            Vector3D dwl = dir.withLen(playerSpeed * dt);
            move = V3(dwl.x, move.y, dwl.z);
            move = V3(move.x, move.y - fallSpeed * dt, move.z);
            fallSpeed += fallAcc * dt;
        }
        objX += move.x;
        objY += move.y;
        objZ += move.z;
        objPitch -= dt * playerSpeed / (PI * PLAYER_HEIGHT) * 2 * PI;

    }

    @Override
    public void updateAfterDraw(float dt) {
        tileBelow = null;
    }

    private float maxByAbs(float a, float b) {
        return abs(a) > abs(b) ? a : b;
    }

    private float minByAbs(float a, float b) {
        return abs(a) < abs(b) ? a : b;
    }

    public void rotDirOnTouch(float dx) {
        float dYaw = dx * rotationSwipeSensitivity; // in radians
        dir = rotY(dir, dYaw);
        objYaw -= dYaw * 180.0f / PI;

        stickyRotationAng = stickyRotationAng - dx * stickyRotationCoeff;
        stickyRotationTime = stickyRotationLastingTime;
        objYaw -= dx * stickyRotationCoeff;
    }
}