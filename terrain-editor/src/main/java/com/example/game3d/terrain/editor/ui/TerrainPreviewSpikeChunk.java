package com.example.game3d.terrain.editor.ui;

import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One bounded, reusable DeathSpike batch with face-to-addon picking metadata. */
final class TerrainPreviewSpikeChunk {
    static final int SPIKES_PER_CHUNK = PreviewSpikeChunkData.SPIKES_PER_CHUNK;
    private static final PhongMaterial MATERIAL = new PhongMaterial(Color.CRIMSON);

    private final int startIndex;
    private final int spikeCount;
    private final long geometryDigest;
    private final MeshView node;
    private final long[] faceAddonIds;
    private Map<Long, String> sources;
    private Consumer<String> picked;
    private BooleanSupplier pickingEnabled;

    private TerrainPreviewSpikeChunk(
            PreviewSpikeChunkData data,
            Map<Long, String> sources,
            Consumer<String> picked,
            BooleanSupplier pickingEnabled) {
        startIndex = data.startIndex();
        spikeCount = data.spikeCount();
        geometryDigest = data.geometryDigest();
        faceAddonIds = data.mesh().faceSourceIds();
        this.sources = sources;
        this.picked = picked;
        this.pickingEnabled = pickingEnabled;
        TriangleMesh mesh = new TriangleMesh();
        mesh.getTexCoords().setAll(0, 0);
        mesh.getPoints().setAll(data.mesh().points());
        mesh.getFaces().setAll(data.mesh().faces());
        node = new MeshView(mesh);
        node.setCullFace(CullFace.NONE);
        node.setMaterial(MATERIAL);
        node.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY
                    || !this.pickingEnabled.getAsBoolean()) return;
            int face = event.getPickResult().getIntersectedFace();
            if (face < 0 || face >= faceAddonIds.length) return;
            String source = this.sources.get(faceAddonIds[face]);
            if (source != null) this.picked.accept(source);
            event.consume();
        });
    }

    static TerrainPreviewSpikeChunk attach(
            PreviewSpikeChunkData data,
            Map<Long, String> sources,
            Consumer<String> picked,
            BooleanSupplier pickingEnabled) {
        return new TerrainPreviewSpikeChunk(data, sources, picked, pickingEnabled);
    }

    boolean matches(PreviewSpikeChunkData data) {
        return startIndex == data.startIndex()
                && spikeCount == data.spikeCount()
                && geometryDigest == data.geometryDigest();
    }

    void updateInteraction(Map<Long, String> value, Consumer<String> newPicked,
                           BooleanSupplier newPickingEnabled) {
        sources = value;
        picked = newPicked;
        pickingEnabled = newPickingEnabled;
    }

    MeshView node() {
        return node;
    }

    String sourceForFace(int faceIndex) {
        return faceIndex < 0 || faceIndex >= faceAddonIds.length
                ? null : sources.get(faceAddonIds[faceIndex]);
    }
}
