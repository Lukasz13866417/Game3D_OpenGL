package com.example.game3d_opengl.game.stages.test.util;

import static com.example.game3d_opengl.rendering.shader.ShaderPair.loadShader;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.object3d.UnbatchedObject3D;
import com.example.game3d_opengl.rendering.object3d.UnbatchedObject3DWithOutline;
import com.example.game3d_opengl.rendering.infill.Mesh3DInfill;
import com.example.game3d_opengl.rendering.wireframe.Mesh3DWireframe;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class LineSet3D {

    private static final int COORDS_PER_VERTEX = 3;
    private static final float POINT_CUBE_SIZE = 0.02f;

    // shaders (same as Polygon3D)
    private static final String VERTEX_SHADER_CODE =
            "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
            "void main() {" +
            "  gl_Position = uMVPMatrix * vPosition;" +
            "}";
    private static final String FRAGMENT_SHADER_CODE =
            "precision mediump float;" +
            "uniform vec4 vColor;" +
            "void main() {" +
            "  gl_FragColor = vColor;" +
            "}";

    // GL program & handles (shared)
    private static int mProgram = 0;
    private final int positionHandle;
    private final int colorHandle;
    private final int vPMatrixHandle;

    // line data
    private final FloatBuffer lineVertexBuffer;
    private final int lineVertexCount;
    private final int lineVboId;
    private FColor lineColor;

    // points as small cubes
    private final UnbatchedObject3DWithOutline[] pointCubes;
    private FColor pointColor;

    public LineSet3D(Vector3D[] points, int[][] edges, FColor lineColor, FColor pointColor) {
        this.lineColor  = lineColor;
        this.pointColor = pointColor;

        // --- 1) Build shader program once ---
        if (mProgram == 0) {
            int vShader = loadShader(GLES20.GL_VERTEX_SHADER,   VERTEX_SHADER_CODE);
            int fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE);
            mProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(mProgram, vShader);
            GLES20.glAttachShader(mProgram, fShader);
            GLES20.glLinkProgram(mProgram);
        }
        positionHandle  = GLES20.glGetAttribLocation (mProgram, "vPosition");
        colorHandle     = GLES20.glGetUniformLocation(mProgram, "vColor");
        vPMatrixHandle  = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");

        // --- 2) Prepare line segment VBO ---
        // each edge gives two vertices
        lineVertexCount = edges.length * 2;
        float[] lineCoords = new float[lineVertexCount * COORDS_PER_VERTEX];
        for (int i = 0; i < edges.length; i++) {
            Vector3D a = points[ edges[i][0] ];
            Vector3D b = points[ edges[i][1] ];
            int base = i * 6;
            lineCoords[base]     = a.x;
            lineCoords[base + 1] = a.y;
            lineCoords[base + 2] = a.z;
            lineCoords[base + 3] = b.x;
            lineCoords[base + 4] = b.y;
            lineCoords[base + 5] = b.z;
        }
        // Upload to GL buffer
        ByteBuffer bb = ByteBuffer.allocateDirect(lineCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        lineVertexBuffer = bb.asFloatBuffer();
        lineVertexBuffer.put(lineCoords).position(0);

        int[] bufs = new int[1];
        GLES20.glGenBuffers(1, bufs, 0);
        lineVboId = bufs[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, lineVboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                lineCoords.length * 4,
                lineVertexBuffer,
                GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        // --- 3) Build a tiny cube at each point ---
        // define a unit cube centered at origin
        float s = POINT_CUBE_SIZE / 2f;
        Vector3D[] cubeVerts = new Vector3D[] {
            new Vector3D(-s,-s,-s), new Vector3D( s,-s,-s),
            new Vector3D( s, s,-s), new Vector3D(-s, s,-s),
            new Vector3D(-s,-s, s), new Vector3D( s,-s, s),
            new Vector3D( s, s, s), new Vector3D(-s, s, s)
        };
        int[][] cubeFaces = new int[][] {
            {0,1,2,3}, {4,5,6,7},  // front, back
            {0,1,5,4}, {2,3,7,6},  // bottom, top
            {0,3,7,4}, {1,2,6,5}   // left, right
        };

        pointCubes = new UnbatchedObject3DWithOutline[points.length];
        for (int i = 0; i < points.length; i++) {
            Vector3D p = points[i];
            pointCubes[i] = makePointCubeObject3D(cubeVerts, cubeFaces, p, pointColor);
        }
    }

    private static UnbatchedObject3DWithOutline makePointCubeObject3D(Vector3D[] cubeVerts, int[][] cubeFaces, Vector3D position, FColor color) {
        Mesh3DInfill fill = new Mesh3DInfill.Builder()
                .verts(cubeVerts)
                .faces(cubeFaces)
                .fillColor(color)
                .buildObject();

        Mesh3DWireframe wire = new Mesh3DWireframe.Builder()
                .verts(cubeVerts)
                .faces(cubeFaces)
                .edgeColor(FColor.CLR(1,1,1,1))
                .pixelWidth(1f)
                .buildObject();

        UnbatchedObject3DWithOutline obj = UnbatchedObject3DWithOutline.wrap(fill, wire);
        obj.objX = position.x; obj.objY = position.y; obj.objZ = position.z;
        return obj;
    }

    /* Package-private */
    public static void resetProgram() {
        if (mProgram != 0) {
            if (android.opengl.GLES20.glIsProgram(mProgram)) {
                android.opengl.GLES20.glDeleteProgram(mProgram);
            }
            mProgram = 0;
        }
    }

    /** Draws all lines, then all point-cubes. */
    public void draw(float[] vpMatrix) {
        // 1) draw lines
        GLES20.glUseProgram(mProgram);
        GLES20.glUniformMatrix4fv(vPMatrixHandle, 1, false, vpMatrix, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, lineVboId);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(
                positionHandle,
                COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                COORDS_PER_VERTEX * 4,
                0
        );

        GLES20.glUniform4fv(colorHandle, 1, lineColor.rgba, 0);
        GLES20.glLineWidth(2.0f);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, lineVertexCount);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        // 2) draw point-cubes
        for (UnbatchedObject3D cube : pointCubes) {
            cube.draw(vpMatrix);
        }
    }

}
