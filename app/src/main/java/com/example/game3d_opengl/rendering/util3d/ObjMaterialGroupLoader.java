package com.example.game3d_opengl.rendering.util3d;

import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.res.AssetManager;

import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads an OBJ as separate meshes for each {@code usemtl} material name.
 * This lets one visible object use several colors without changing its shared transform.
 */
public final class ObjMaterialGroupLoader {
    private static final String DEFAULT_MATERIAL = "default";
    private static final float MIN_EXTENT = 1.0e-8f;

    private final AssetManager assetManager;

    public ObjMaterialGroupLoader(AssetManager assetManager) {
        if (assetManager == null) {
            throw new IllegalArgumentException("AssetManager cannot be null");
        }
        this.assetManager = assetManager;
    }

    public Map<String, PreparedModelData> load(
            String filename,
            float targetSizeX,
            float targetSizeY,
            float targetSizeZ
    ) throws IOException {
        try (InputStream stream = assetManager.open(filename);
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return parse(reader, targetSizeX, targetSizeY, targetSizeZ);
        }
    }

    static Map<String, PreparedModelData> parse(
            Reader source,
            float targetSizeX,
            float targetSizeY,
            float targetSizeZ
    ) throws IOException {
        if (source == null) {
            throw new IllegalArgumentException("OBJ source cannot be null");
        }
        validateTargetSize(targetSizeX, "targetSizeX");
        validateTargetSize(targetSizeY, "targetSizeY");
        validateTargetSize(targetSizeZ, "targetSizeZ");

        List<Vector3D> positions = new ArrayList<>();
        List<Vector3D> normalPool = new ArrayList<>();
        Map<String, List<int[]>> materialPositionFaces = new LinkedHashMap<>();
        Map<String, List<int[]>> materialNormalFaces = new LinkedHashMap<>();
        String activeMaterial = DEFAULT_MATERIAL;

        BufferedReader reader = source instanceof BufferedReader
                ? (BufferedReader) source
                : new BufferedReader(source);
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String value = line.trim();
            if (value.isEmpty() || value.startsWith("#")) {
                continue;
            }

            String[] parts = value.split("\\s+");
            try {
                if ("v".equals(parts[0])) {
                    requireParts(parts, 4, lineNumber);
                    positions.add(V3(
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3])));
                } else if ("vn".equals(parts[0])) {
                    requireParts(parts, 4, lineNumber);
                    normalPool.add(V3(
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3])));
                } else if ("usemtl".equals(parts[0])) {
                    requireParts(parts, 2, lineNumber);
                    activeMaterial = parts[1];
                } else if ("f".equals(parts[0])) {
                    requireParts(parts, 4, lineNumber);
                    int cornerCount = parts.length - 1;
                    int[] positionFace = new int[cornerCount];
                    int[] normalFace = new int[cornerCount];
                    for (int corner = 0; corner < cornerCount; corner++) {
                        String[] indices = parts[corner + 1].split("/", -1);
                        positionFace[corner] = parseObjIndex(
                                indices[0], positions.size(), "vertex", lineNumber);
                        normalFace[corner] = indices.length >= 3 && !indices[2].isEmpty()
                                ? parseObjIndex(
                                        indices[2], normalPool.size(), "normal", lineNumber)
                                : -1;
                    }
                    materialPositionFaces
                            .computeIfAbsent(activeMaterial, ignored -> new ArrayList<>())
                            .add(positionFace);
                    materialNormalFaces
                            .computeIfAbsent(activeMaterial, ignored -> new ArrayList<>())
                            .add(normalFace);
                }
            } catch (NumberFormatException exception) {
                throw new IOException(
                        "Invalid number in OBJ at line " + lineNumber + ": " + value,
                        exception);
            }
        }

        if (positions.isEmpty() || materialPositionFaces.isEmpty()) {
            throw new IOException("OBJ contains no drawable geometry");
        }

        float[] scales = centerAndScale(
                positions, targetSizeX, targetSizeY, targetSizeZ);
        rescaleNormals(normalPool, scales[0], scales[1], scales[2]);

        Map<String, PreparedModelData> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<int[]>> entry : materialPositionFaces.entrySet()) {
            String materialName = entry.getKey();
            result.put(materialName, buildMaterialMesh(
                    positions,
                    normalPool,
                    entry.getValue(),
                    materialNormalFaces.get(materialName)));
        }
        return result;
    }

    private static PreparedModelData buildMaterialMesh(
            List<Vector3D> positions,
            List<Vector3D> normalPool,
            List<int[]> positionFaces,
            List<int[]> normalFaces
    ) {
        boolean hasNormals = !normalPool.isEmpty();
        for (int[] face : normalFaces) {
            for (int index : face) {
                if (index < 0) {
                    hasNormals = false;
                    break;
                }
            }
            if (!hasNormals) {
                break;
            }
        }

        Map<Long, Integer> sourceToLocalIndex = new HashMap<>();
        List<Vector3D> localPositions = new ArrayList<>();
        List<Vector3D> localNormals = hasNormals ? new ArrayList<>() : null;
        int[][] localFaces = new int[positionFaces.size()][];

        for (int faceIndex = 0; faceIndex < positionFaces.size(); faceIndex++) {
            int[] positionFace = positionFaces.get(faceIndex);
            int[] normalFace = normalFaces.get(faceIndex);
            int[] localFace = new int[positionFace.length];
            for (int corner = 0; corner < positionFace.length; corner++) {
                int positionIndex = positionFace[corner];
                int normalIndex = hasNormals ? normalFace[corner] : -1;
                long key = ((long) positionIndex << 32)
                        | (normalIndex & 0xFFFFFFFFL);
                Integer localIndex = sourceToLocalIndex.get(key);
                if (localIndex == null) {
                    localIndex = localPositions.size();
                    sourceToLocalIndex.put(key, localIndex);
                    localPositions.add(positions.get(positionIndex));
                    if (hasNormals) {
                        localNormals.add(normalPool.get(normalIndex));
                    }
                }
                localFace[corner] = localIndex;
            }
            localFaces[faceIndex] = localFace;
        }

        return new PreparedModelData(
                localPositions.toArray(new Vector3D[0]),
                localFaces,
                hasNormals ? localNormals.toArray(new Vector3D[0]) : null);
    }

    private static float[] centerAndScale(
            List<Vector3D> positions,
            float targetSizeX,
            float targetSizeY,
            float targetSizeZ
    ) throws IOException {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (Vector3D position : positions) {
            minX = Math.min(minX, position.x);
            minY = Math.min(minY, position.y);
            minZ = Math.min(minZ, position.z);
            maxX = Math.max(maxX, position.x);
            maxY = Math.max(maxY, position.y);
            maxZ = Math.max(maxZ, position.z);
        }

        float extentX = maxX - minX;
        float extentY = maxY - minY;
        float extentZ = maxZ - minZ;
        if (extentX <= MIN_EXTENT || extentY <= MIN_EXTENT || extentZ <= MIN_EXTENT) {
            throw new IOException("OBJ bounds must have non-zero size on every axis");
        }

        float centerX = (minX + maxX) * 0.5f;
        float centerY = (minY + maxY) * 0.5f;
        float centerZ = (minZ + maxZ) * 0.5f;
        float scaleX = targetSizeX / extentX;
        float scaleY = targetSizeY / extentY;
        float scaleZ = targetSizeZ / extentZ;
        for (int index = 0; index < positions.size(); index++) {
            Vector3D position = positions.get(index);
            positions.set(index, V3(
                    (position.x - centerX) * scaleX,
                    (position.y - centerY) * scaleY,
                    (position.z - centerZ) * scaleZ));
        }
        return new float[]{scaleX, scaleY, scaleZ};
    }

    private static void rescaleNormals(
            List<Vector3D> normals,
            float scaleX,
            float scaleY,
            float scaleZ
    ) {
        for (int index = 0; index < normals.size(); index++) {
            Vector3D normal = normals.get(index);
            float x = normal.x / scaleX;
            float y = normal.y / scaleY;
            float z = normal.z / scaleZ;
            float length = (float) Math.sqrt(x * x + y * y + z * z);
            normals.set(index, length > MIN_EXTENT
                    ? V3(x / length, y / length, z / length)
                    : V3(0f, 0f, 0f));
        }
    }

    private static int parseObjIndex(
            String token,
            int itemCount,
            String kind,
            int lineNumber
    ) throws IOException {
        int rawIndex = Integer.parseInt(token);
        int index = rawIndex > 0 ? rawIndex - 1 : itemCount + rawIndex;
        if (rawIndex == 0 || index < 0 || index >= itemCount) {
            throw new IOException(
                    "OBJ " + kind + " index out of range at line " + lineNumber);
        }
        return index;
    }

    private static void requireParts(
            String[] parts,
            int minimum,
            int lineNumber
    ) throws IOException {
        if (parts.length < minimum) {
            throw new IOException("Incomplete OBJ statement at line " + lineNumber);
        }
    }

    private static void validateTargetSize(float value, String name) {
        if (!Float.isFinite(value) || value <= 0f) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
