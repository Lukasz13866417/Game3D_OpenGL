package com.example.game3d_opengl.rendering.util3d;


import static com.example.game3d_opengl.game.util.GameMath.rotX;
import static com.example.game3d_opengl.game.util.GameMath.rotY;
import static com.example.game3d_opengl.game.util.GameMath.rotZ;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.res.AssetManager;


import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelCreator {

    private final AssetManager assetManager;
    private Vector3D[] verts;
    private Vector3D[] normals;
    private int[][] faces;
    private boolean somethingWasLoaded;
    private boolean hasNormals;

    public ModelCreator(AssetManager assetManager) {
        this.assetManager = assetManager;
        this.somethingWasLoaded = false;
        this.hasNormals = false;
    }

    public void load(String filename) throws IOException {
        List<Vector3D> posList = new ArrayList<>();
        List<Vector3D> normalPool = new ArrayList<>();
        List<int[]> rawFacePos = new ArrayList<>();
        List<int[]> rawFaceNorm = new ArrayList<>();
        boolean fileHasNormals = false;

        InputStream inputStream = assetManager.open(filename);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("vn ")) {
                String[] parts = line.split("\\s+");
                normalPool.add(V3(
                        Float.parseFloat(parts[1]),
                        Float.parseFloat(parts[2]),
                        Float.parseFloat(parts[3])));
            } else if (line.startsWith("v ")) {
                String[] parts = line.split("\\s+");
                posList.add(V3(
                        Float.parseFloat(parts[1]),
                        Float.parseFloat(parts[2]),
                        Float.parseFloat(parts[3])));
            } else if (line.startsWith("f ")) {
                String[] parts = line.split("\\s+");
                int n = parts.length - 1;
                int[] fPos = new int[n];
                int[] fNorm = new int[n];
                boolean faceHasNormals = false;
                for (int i = 0; i < n; i++) {
                    String[] comp = parts[i + 1].split("/");
                    fPos[i] = Integer.parseInt(comp[0]) - 1;
                    if (comp.length >= 3 && !comp[2].isEmpty()) {
                        fNorm[i] = Integer.parseInt(comp[2]) - 1;
                        faceHasNormals = true;
                    } else {
                        fNorm[i] = -1;
                    }
                }
                rawFacePos.add(fPos);
                rawFaceNorm.add(fNorm);
                if (faceHasNormals) fileHasNormals = true;
            }
        }
        reader.close();

        if (fileHasNormals && !normalPool.isEmpty()) {
            buildWithNormals(posList, normalPool, rawFacePos, rawFaceNorm);
        } else {
            verts = posList.toArray(new Vector3D[0]);
            faces = rawFacePos.toArray(new int[0][]);
            normals = null;
            hasNormals = false;
        }
        somethingWasLoaded = true;
    }

    private void buildWithNormals(List<Vector3D> posList, List<Vector3D> normalPool,
                                  List<int[]> rawFacePos, List<int[]> rawFaceNorm) {
        Map<Long, Integer> pairToIndex = new HashMap<>();
        List<Vector3D> outVerts = new ArrayList<>();
        List<Vector3D> outNormals = new ArrayList<>();
        int[][] outFaces = new int[rawFacePos.size()][];

        for (int fi = 0; fi < rawFacePos.size(); fi++) {
            int[] fPos = rawFacePos.get(fi);
            int[] fNorm = rawFaceNorm.get(fi);
            int[] outFace = new int[fPos.length];
            for (int vi = 0; vi < fPos.length; vi++) {
                int pi = fPos[vi];
                int ni = fNorm[vi];
                if (ni < 0) ni = 0;
                long key = ((long) pi << 32) | (ni & 0xFFFFFFFFL);
                Integer existing = pairToIndex.get(key);
                if (existing != null) {
                    outFace[vi] = existing;
                } else {
                    int idx = outVerts.size();
                    outVerts.add(posList.get(pi));
                    outNormals.add(normalPool.get(ni));
                    pairToIndex.put(key, idx);
                    outFace[vi] = idx;
                }
            }
            outFaces[fi] = outFace;
        }

        verts = outVerts.toArray(new Vector3D[0]);
        normals = outNormals.toArray(new Vector3D[0]);
        faces = outFaces;
        hasNormals = true;
    }

    public void setModel(Vector3D[] verts, int[][] faces) {
        this.verts = verts;
        this.faces = faces;
        this.normals = null;
        this.hasNormals = false;
        somethingWasLoaded = true;
    }

    public void scaleX(float targetSizeX) {
        assert (somethingWasLoaded);
        float minx = Arrays.stream(verts).map(v -> v.x).min(Float::compare).orElse(Float.MAX_VALUE);
        float maxx = Arrays.stream(verts).map(v -> v.x).max(Float::compare).orElse(Float.MIN_VALUE);
        float scale = targetSizeX / (maxx - minx);
        verts = Arrays.stream(verts).map(v -> v.multX(scale)).toArray(Vector3D[]::new);
        if (hasNormals) rescaleNormals(1f / scale, 1f, 1f);
    }

    public void scaleY(float targetSizeY) {
        assert (somethingWasLoaded);
        float miny = Arrays.stream(verts).map(v -> v.y).min(Float::compare).orElse(Float.MAX_VALUE);
        float maxy = Arrays.stream(verts).map(v -> v.y).max(Float::compare).orElse(Float.MIN_VALUE);
        float scale = targetSizeY / (maxy - miny);
        verts = Arrays.stream(verts).map(v -> v.multY(scale)).toArray(Vector3D[]::new);
        if (hasNormals) rescaleNormals(1f, 1f / scale, 1f);
    }

    public void scaleZ(float targetSizeZ) {
        assert (somethingWasLoaded);
        float minz = Arrays.stream(verts).map(v -> v.z).min(Float::compare).orElse(Float.MAX_VALUE);
        float maxz = Arrays.stream(verts).map(v -> v.z).max(Float::compare).orElse(Float.MIN_VALUE);
        float scale = targetSizeZ / (maxz - minz);
        verts = Arrays.stream(verts).map(v -> v.multZ(scale)).toArray(Vector3D[]::new);
        if (hasNormals) rescaleNormals(1f, 1f, 1f / scale);
    }

    public void scaleBy(float k) {
        verts = Arrays.stream(verts).map(v -> v.multX(k).multY(k).multZ(k)).toArray(Vector3D[]::new);
    }

    private void rescaleNormals(float sx, float sy, float sz) {
        for (int i = 0; i < normals.length; i++) {
            Vector3D n = normals[i];
            float nx = n.x * sx, ny = n.y * sy, nz = n.z * sz;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1e-8f) { nx /= len; ny /= len; nz /= len; }
            normals[i] = V3(nx, ny, nz);
        }
    }

    public void rotateX(float angle) {
        assert (somethingWasLoaded);
        verts = Arrays.stream(verts).map(v -> rotX(v, angle)).toArray(Vector3D[]::new);
        if (hasNormals) {
            normals = Arrays.stream(normals).map(n -> rotX(n, angle)).toArray(Vector3D[]::new);
        }
    }

    public void rotateY(float angle) {
        assert (somethingWasLoaded);
        verts = Arrays.stream(verts).map(v -> rotY(v, angle)).toArray(Vector3D[]::new);
        if (hasNormals) {
            normals = Arrays.stream(normals).map(n -> rotY(n, angle)).toArray(Vector3D[]::new);
        }
    }

    public void rotateZ(float angle) {
        assert (somethingWasLoaded);
        verts = Arrays.stream(verts).map(v -> rotZ(v, angle)).toArray(Vector3D[]::new);
        if (hasNormals) {
            normals = Arrays.stream(normals).map(n -> rotZ(n, angle)).toArray(Vector3D[]::new);
        }
    }

    public Vector3D[] getVerts() {
        assert (somethingWasLoaded);
        return verts.clone();
    }

    public int[][] getFaces() {
        assert (somethingWasLoaded);
        return faces.clone();
    }

    public boolean hasNormals() {
        return hasNormals;
    }

    /**
     * Returns per-vertex normals loaded from the OBJ file, or null if the file
     * had no normals. The array is parallel to getVerts() -- normals[i] is the
     * normal for verts[i].
     */
    public Vector3D[] getNormals() {
        if (!hasNormals) return null;
        return normals.clone();
    }

    public void centerVerts() {
        assert (somethingWasLoaded);
        float minx = Arrays.stream(verts).map(v -> v.x).min(Float::compare).orElse(Float.MAX_VALUE);
        float maxx = Arrays.stream(verts).map(v -> v.x).max(Float::compare).orElse(Float.MIN_VALUE);
        float miny = Arrays.stream(verts).map(v -> v.y).min(Float::compare).orElse(Float.MAX_VALUE);
        float maxy = Arrays.stream(verts).map(v -> v.y).max(Float::compare).orElse(Float.MIN_VALUE);
        float minz = Arrays.stream(verts).map(v -> v.z).min(Float::compare).orElse(Float.MAX_VALUE);
        float maxz = Arrays.stream(verts).map(v -> v.z).max(Float::compare).orElse(Float.MIN_VALUE);
        Vector3D cent = V3((minx + maxx) / 2, (miny + maxy) / 2, (minz + maxz) / 2);
        for (int i = 0; i < verts.length; ++i) {
            verts[i] = verts[i].sub(cent);
        }
    }
}