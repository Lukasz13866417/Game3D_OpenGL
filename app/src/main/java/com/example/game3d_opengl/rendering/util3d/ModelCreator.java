package com.example.game3d_opengl.rendering.util3d;


import static com.example.game3d_opengl.rendering.util3d.GameMath.rotX;
import static com.example.game3d_opengl.rendering.util3d.GameMath.rotY;
import static com.example.game3d_opengl.rendering.util3d.GameMath.rotZ;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.res.AssetManager;


import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModelCreator {

    private final AssetManager assetManager;
    private Vector3D[] verts;
    private int[][] faces;
    private boolean somethingWasLoaded;

    public ModelCreator(AssetManager assetManager) {
        this.assetManager = assetManager;
        this.somethingWasLoaded = false;
    }

    public void load(String filename) throws IOException {
        List<Vector3D> vertsList = new ArrayList<>();
        List<int[]> facesList = new ArrayList<>();

        InputStream inputStream = assetManager.open(filename);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("v ")) {
                String[] parts = line.split("\\s+");
                float x = Float.parseFloat(parts[1]);
                float y = Float.parseFloat(parts[2]);
                float z = Float.parseFloat(parts[3]);
                vertsList.add(V3(x, y, z));
            } else if (line.startsWith("f ")) {
                String[] parts = line.split("\\s+");
                int[] face = new int[parts.length - 1];
                for (int i = 1; i < parts.length; i++) {
                    face[i - 1] = Integer.parseInt(parts[i].split("/")[0]) - 1;
                }
                facesList.add(face);
            }
        }

        reader.close();

        verts = vertsList.toArray(new Vector3D[0]);
        faces = facesList.toArray(new int[0][]);
        somethingWasLoaded = true;
    }

    public void setModel(Vector3D[] verts, int[][] faces) {
        this.verts = verts;
        this.faces = faces;
        somethingWasLoaded = true;
    }

    public void scaleX(float targetSizeX) {
        assert (somethingWasLoaded);
        float minx = Arrays.stream(verts).map(v -> v.x).min(Float::compare).orElse(Float.MAX_VALUE);
        float maxx = Arrays.stream(verts).map(v -> v.x).max(Float::compare).orElse(Float.MIN_VALUE);
        float scale = targetSizeX / (maxx - minx);
        verts = Arrays.stream(verts).map(v -> v.multX(scale)).toArray(Vector3D[]::new);
    }

    public void scaleY(float targetSizeY) {
        assert (somethingWasLoaded);
        float miny = Arrays.stream(verts).map(v -> v.y).min(Float::compare).orElse(Float.MAX_VALUE);
        float maxy = Arrays.stream(verts).map(v -> v.y).max(Float::compare).orElse(Float.MIN_VALUE);
        float scale = targetSizeY / (maxy - miny);
        verts = Arrays.stream(verts).map(v -> v.multY(scale)).toArray(Vector3D[]::new);
    }

    public void scaleZ(float targetSizeZ) {
        assert (somethingWasLoaded);
        float minz = Arrays.stream(verts).map(v -> v.z).min(Float::compare).orElse(Float.MAX_VALUE);
        float maxz = Arrays.stream(verts).map(v -> v.z).max(Float::compare).orElse(Float.MIN_VALUE);
        float scale = targetSizeZ / (maxz - minz);
        verts = Arrays.stream(verts).map(v -> v.multZ(scale)).toArray(Vector3D[]::new);
    }

    public void scaleBy(float k) {
        verts = Arrays.stream(verts).map(v -> v.multX(k).multY(k).multZ(k)).toArray(Vector3D[]::new);
    }

    public void rotateX(float angle) {
        assert (somethingWasLoaded);
        verts = Arrays.stream(verts).map(v -> rotX(v, angle)).toArray(Vector3D[]::new);
    }

    public void rotateY(float angle) {
        assert (somethingWasLoaded);
        verts = Arrays.stream(verts).map(v -> rotY(v, angle)).toArray(Vector3D[]::new);
    }

    public void rotateZ(float angle) {
        assert (somethingWasLoaded);
        verts = Arrays.stream(verts).map(v -> rotZ(v, angle)).toArray(Vector3D[]::new);
    }

    public Vector3D[] getVerts() {
        assert (somethingWasLoaded);
        return verts.clone();
    }

    public int[][] getFaces() {
        assert (somethingWasLoaded);
        return faces.clone();
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