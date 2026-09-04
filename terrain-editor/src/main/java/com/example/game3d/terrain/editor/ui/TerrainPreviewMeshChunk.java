package com.example.game3d.terrain.editor.ui;

import javafx.scene.Group;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** JavaFX attachment for one worker-prepared terrain chunk. */
final class TerrainPreviewMeshChunk {
    static final int SEGMENTS_PER_CHUNK = PreviewTerrainChunkData.SEGMENTS_PER_CHUNK;
    private static final PhongMaterial FILL_MATERIAL =
            new PhongMaterial(Color.color(.25, .58, .34, .94));
    private static final PhongMaterial EDGE_MATERIAL =
            new PhongMaterial(Color.color(.82, .94, .88));

    private final int startIndex;
    private final int segmentCount;
    private final long geometryDigest;
    private final Group node = new Group();
    private final MeshView fillView;
    private final MeshView edgeView;
    private final long[] fillSegmentIds;
    private final long[] edgeSegmentIds;
    private Map<Long, String> sources;
    private Consumer<String> picked;
    private BooleanSupplier pickingEnabled;

    private TerrainPreviewMeshChunk(
            PreviewTerrainChunkData data,
            Map<Long, String> sources,
            Consumer<String> picked,
            BooleanSupplier pickingEnabled) {
        startIndex = data.startIndex();
        segmentCount = data.segmentCount();
        geometryDigest = data.geometryDigest();
        this.sources = sources;
        this.picked = picked;
        this.pickingEnabled = pickingEnabled;
        fillSegmentIds = data.fill().faceSourceIds();
        edgeSegmentIds = data.edges().faceSourceIds();
        fillView = meshView(data.fill(), DrawMode.FILL, FILL_MATERIAL);
        edgeView = meshView(data.edges(), DrawMode.LINE, EDGE_MATERIAL);
        attachPicking(fillView, fillSegmentIds);
        attachPicking(edgeView, edgeSegmentIds);
        node.getChildren().addAll(fillView, edgeView);
    }

    static TerrainPreviewMeshChunk attach(
            PreviewTerrainChunkData data,
            Map<Long, String> sources,
            Consumer<String> picked,
            BooleanSupplier pickingEnabled) {
        return new TerrainPreviewMeshChunk(data, sources, picked, pickingEnabled);
    }

    boolean matches(PreviewTerrainChunkData data) {
        return startIndex == data.startIndex()
                && segmentCount == data.segmentCount()
                && geometryDigest == data.geometryDigest();
    }

    void updateInteraction(Map<Long, String> value, Consumer<String> newPicked,
                           BooleanSupplier newPickingEnabled) {
        sources = value;
        picked = newPicked;
        pickingEnabled = newPickingEnabled;
    }

    Group node() {
        return node;
    }

    void setEdgesVisible(boolean visible) {
        edgeView.setVisible(visible);
    }

    String sourceForFace(boolean fill, int faceIndex) {
        long[] ids = fill ? fillSegmentIds : edgeSegmentIds;
        return faceIndex < 0 || faceIndex >= ids.length
                ? null : sources.get(ids[faceIndex]);
    }

    private void attachPicking(MeshView view, long[] faceIds) {
        view.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY
                    || !pickingEnabled.getAsBoolean()) return;
            int face = event.getPickResult().getIntersectedFace();
            if (face < 0 || face >= faceIds.length) return;
            String source = sources.get(faceIds[face]);
            if (source != null) picked.accept(source);
            event.consume();
        });
    }

    private static MeshView meshView(
            PreviewMeshBuffer data, DrawMode drawMode, PhongMaterial material) {
        TriangleMesh mesh = new TriangleMesh();
        mesh.getTexCoords().setAll(0, 0);
        mesh.getPoints().setAll(data.points());
        mesh.getFaces().setAll(data.faces());
        MeshView view = new MeshView(mesh);
        view.setCullFace(CullFace.NONE);
        view.setDrawMode(drawMode);
        view.setMaterial(material);
        return view;
    }
}
