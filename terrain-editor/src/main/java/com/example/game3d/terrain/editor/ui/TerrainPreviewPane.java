package com.example.game3d.terrain.editor.ui;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.core.terrain.addon.Addon;
import com.example.game3d.core.terrain.addon.Portal;
import com.example.game3d.core.terrain.addon.Potion;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AmbientLight;
import javafx.scene.Camera;
import javafx.scene.DepthTest;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SubScene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Shape3D;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Affine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Interactive geometry preview of the exact canonical terrain snapshot.
 *
 * <p>The materials intentionally remain a JavaFX approximation. Geometry, addon placement,
 * source picking, framing bounds, and navigation all consume canonical world-space output.</p>
 */
public final class TerrainPreviewPane extends StackPane {
    public enum PreviewState {
        EMPTY,
        COMPILING,
        CURRENT,
        STALE_INVALID,
        FAILED
    }

    enum NavigationMode { ORBIT, WALK }

    private static final double WALK_STEP = .8;
    private static final double WALK_TURN_DEGREES = 5.0;
    private static final double WALK_HEIGHT_STEP = .35;
    private static final AtomicInteger BUILD_THREAD_IDS = new AtomicInteger();
    private static final ExecutorService BUILD_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() - 1)),
            task -> {
                Thread thread = new Thread(task, "terrain-preview-builder-"
                        + BUILD_THREAD_IDS.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });

    private final Group terrainRoot = new Group();
    private final Group world = new Group();
    private final SubScene scene;
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final WalkCameraState walkCamera = new WalkCameraState();
    private final OrbitCameraState orbitCamera = new OrbitCameraState();
    private final Label navigationHelp = new Label();
    private final Label stateOverlay = new Label();
    private final Label geometryLabel = new Label("GEOMETRY PREVIEW · JavaFX materials");
    private final ToggleButton orbitButton = new ToggleButton("Orbit");
    private final ToggleButton walkButton = new ToggleButton("Walk");
    private final List<TerrainPreviewMeshChunk> chunks = new ArrayList<>();
    private final List<TerrainPreviewSpikeChunk> spikeChunks = new ArrayList<>();
    private final Map<Long, CachedAddon> addonCache = new HashMap<>();
    private final Set<String> selectedSources = new HashSet<>();

    private TerrainSnapshot latestSnapshot = TerrainSnapshot.empty();
    private PreviewBounds latestBounds = PreviewBounds.empty();
    private Map<String, PreviewBounds> sourceBounds = Map.of();
    private NavigationMode navigationMode = NavigationMode.ORBIT;
    private PreviewState previewState = PreviewState.EMPTY;
    private boolean pickingRequested = true;
    private boolean edgesVisible = true;
    private boolean pendingInitialFit;
    private boolean hasCurrentGeometry;
    private Consumer<PreviewState> stateListener = ignored -> { };
    private volatile long requestedBuildTicket;
    private volatile long completedBuildTicket;
    private volatile boolean lastBufferBuildRanOnFxThread;
    private Future<?> pendingBuild;
    private double anchorX;
    private double anchorY;

    public TerrainPreviewPane() {
        world.setDepthTest(DepthTest.ENABLE);
        world.getChildren().addAll(terrainRoot,
                new AmbientLight(Color.color(.82, .82, .86)));
        scene = new SubScene(world, 800, 600, true,
                javafx.scene.SceneAntialiasing.BALANCED);
        scene.setId("preview-viewport");
        scene.setFill(Color.rgb(28, 31, 38));
        camera.setVerticalFieldOfView(true);
        scene.setCamera(camera);

        configureOverlay(stateOverlay, Pos.CENTER, new Insets(45));
        stateOverlay.setId("preview-state-overlay");
        stateOverlay.setWrapText(true);
        stateOverlay.setMaxWidth(440);
        stateOverlay.setMouseTransparent(true);
        stateOverlay.setStyle("-fx-background-color: rgba(20,23,29,.88);"
                + " -fx-background-radius: 6; -fx-padding: 12;"
                + " -fx-text-fill: white; -fx-font-weight: bold;");

        configureOverlay(geometryLabel, Pos.BOTTOM_RIGHT, new Insets(10));
        geometryLabel.setId("preview-geometry-label");
        geometryLabel.setMouseTransparent(true);
        geometryLabel.setStyle("-fx-background-color: rgba(20,23,29,.76);"
                + " -fx-background-radius: 4; -fx-padding: 4 7 4 7;"
                + " -fx-text-fill: #c8d2cd; -fx-font-size: 10px;");

        getChildren().addAll(scene, navigationControls(), stateOverlay, geometryLabel);
        setFocusTraversable(true);
        scene.setFocusTraversable(true);
        scene.widthProperty().bind(widthProperty());
        scene.heightProperty().bind(heightProperty());
        widthProperty().addListener((observable, oldValue, value) -> completePendingFit());
        heightProperty().addListener((observable, oldValue, value) -> completePendingFit());
        installNavigationHandlers();
        updatePreviewState(PreviewState.EMPTY, "No preview geometry");
        applyOrbitCamera();
    }

    /**
     * Requests an asynchronous preview replacement. The last good scene remains attached until
     * this request's worker-prepared buffers pass the monotonic ticket check and attach.
     */
    public void show(TerrainSnapshot snapshot, Map<String, Long> sourceSegments,
                     Map<String, Long> sourceAddons, Consumer<String> picked) {
        if (snapshot == null || sourceSegments == null || sourceAddons == null
                || picked == null) {
            throw new IllegalArgumentException("Preview arguments are required");
        }
        long ticket = invalidatePendingBuild();
        updatePreviewState(PreviewState.COMPILING, "Building preview geometry…");
        pendingBuild = BUILD_EXECUTOR.submit(() -> {
            lastBufferBuildRanOnFxThread = Platform.isFxApplicationThread();
            final PreviewBuildData built;
            try {
                built = PreviewBuildData.build(snapshot, sourceSegments, sourceAddons);
            } catch (CancellationException superseded) {
                return;
            } catch (RuntimeException failure) {
                Platform.runLater(() -> previewBuildFailed(ticket, failure));
                return;
            }
            Platform.runLater(() -> attachPreview(ticket, built, picked));
        });
    }

    /** Marks retained geometry as in-progress and prevents stale source picking. */
    public void showCompiling(String detail) {
        invalidatePendingBuild();
        updatePreviewState(PreviewState.COMPILING,
                detail == null || detail.isBlank() ? "Updating preview…" : detail);
    }

    /** Keeps the last good geometry visibly stale while current validation errors are shown. */
    public void showInvalid(String detail) {
        invalidatePendingBuild();
        updatePreviewState(PreviewState.STALE_INVALID,
                detail == null || detail.isBlank()
                        ? hasCurrentGeometry
                        ? "Current draft is invalid · showing the last good preview"
                        : "Current draft is invalid · no preview is available"
                        : detail);
    }

    /** Keeps the last good geometry visibly stale after an unexpected compiler failure. */
    public void showFailure(String detail) {
        invalidatePendingBuild();
        updatePreviewState(PreviewState.FAILED,
                detail == null || detail.isBlank()
                        ? hasCurrentGeometry
                        ? "Preview compilation failed · showing the last good preview"
                        : "Preview compilation failed · no preview is available"
                        : detail);
    }

    public PreviewState previewState() {
        return previewState;
    }

    public void setPreviewStateListener(Consumer<PreviewState> listener) {
        stateListener = listener == null ? ignored -> { } : listener;
        stateListener.accept(previewState);
    }

    /** Whether stale-state messaging may truthfully refer to retained preview geometry. */
    public boolean hasCurrentGeometry() {
        return hasCurrentGeometry;
    }

    /** Invalidates queued work when the owning workspace is permanently closed. */
    public void dispose() {
        invalidatePendingBuild();
    }

    public void setPickingEnabled(boolean enabled) {
        pickingRequested = enabled;
    }

    public boolean isPickingEnabled() {
        return pickingAllowed();
    }

    public void setSelectedSourceIds(Set<String> sourceIds) {
        selectedSources.clear();
        if (sourceIds != null) selectedSources.addAll(sourceIds);
    }

    public void setEdgesVisible(boolean visible) {
        edgesVisible = visible;
        for (TerrainPreviewMeshChunk chunk : chunks) {
            chunk.setEdgesVisible(visible);
        }
    }

    public void frameAll() {
        frameAllInternal(true);
    }

    public void frameSelection() {
        PreviewBounds selected = PreviewBounds.empty();
        for (String source : selectedSources) {
            selected = selected.union(sourceBounds.get(source));
        }
        if (selected.isEmpty()) {
            frameAll();
            return;
        }
        setNavigationMode(NavigationMode.ORBIT);
        orbitCamera.frame(selected, viewportWidth(), viewportHeight(), camera.getFieldOfView());
        pendingInitialFit = false;
        applyOrbitCamera();
    }

    public void resetView() {
        if (navigationMode == NavigationMode.WALK) {
            walkCamera.reset(latestSnapshot);
            applyWalkCamera();
        } else {
            orbitCamera.resetAndFrame(latestBounds, viewportWidth(), viewportHeight(),
                    camera.getFieldOfView());
            pendingInitialFit = false;
            applyOrbitCamera();
        }
        requestFocus();
    }

    private long invalidatePendingBuild() {
        requestedBuildTicket++;
        if (pendingBuild != null) pendingBuild.cancel(true);
        pendingBuild = null;
        return requestedBuildTicket;
    }

    private void previewBuildFailed(long ticket, RuntimeException failure) {
        if (ticket != requestedBuildTicket) return;
        pendingBuild = null;
        updatePreviewState(PreviewState.FAILED,
                "Preview geometry build failed: " + safeMessage(failure)
                        + (hasCurrentGeometry
                        ? " · showing the last good preview"
                        : " · no preview is available"));
    }

    private void attachPreview(
            long ticket, PreviewBuildData built, Consumer<String> picked) {
        if (ticket != requestedBuildTicket) return;
        try {
            List<Node> nodes = new ArrayList<>();
            rebuildTerrainChunks(built.terrainChunks,
                    built.segmentSources, picked, nodes);
            rebuildSpikeChunks(built.spikeChunks,
                    built.addonSources, picked, nodes);
            rebuildSparseAddons(built.sparseAddons,
                    built.addonSources, picked, nodes);
            patchChildren(terrainRoot.getChildren(), nodes);
        } catch (RuntimeException failure) {
            previewBuildFailed(ticket, failure);
            return;
        }

        pendingBuild = null;
        latestSnapshot = built.snapshot;
        latestBounds = built.bounds;
        sourceBounds = built.sourceBounds;
        hasCurrentGeometry = !built.snapshot.segments.isEmpty();
        if (!hasCurrentGeometry) {
            updatePreviewState(PreviewState.EMPTY, "No preview geometry");
            completedBuildTicket = ticket;
            return;
        }
        updatePreviewState(PreviewState.CURRENT, null);
        if (!orbitCamera.isInitialized()
                || !latestBounds.contains(orbitCamera.target(), 0.0)) {
            pendingInitialFit = true;
            frameAllInternal(false);
        } else {
            orbitCamera.updateSceneRadius(latestBounds.radius());
            if (navigationMode == NavigationMode.ORBIT) applyOrbitCamera();
        }
        if (navigationMode == NavigationMode.WALK
                && walkCamera.initializeIfNeeded(built.snapshot)) {
            applyWalkCamera();
        }
        completedBuildTicket = ticket;
    }

    private void rebuildTerrainChunks(
            List<PreviewTerrainChunkData> prepared,
            Map<Long, String> sources,
            Consumer<String> picked,
            List<Node> nodes) {
        List<TerrainPreviewMeshChunk> previous = new ArrayList<>(chunks);
        List<TerrainPreviewMeshChunk> next = new ArrayList<>();
        for (int chunkIndex = 0; chunkIndex < prepared.size(); chunkIndex++) {
            PreviewTerrainChunkData data = prepared.get(chunkIndex);
            TerrainPreviewMeshChunk chunk = chunkIndex < previous.size()
                    && previous.get(chunkIndex).matches(data)
                    ? previous.get(chunkIndex)
                    : TerrainPreviewMeshChunk.attach(data,
                    sources, picked, this::pickingAllowed);
            chunk.updateInteraction(sources, picked, this::pickingAllowed);
            chunk.setEdgesVisible(edgesVisible);
            next.add(chunk);
            nodes.add(chunk.node());
        }
        chunks.clear();
        chunks.addAll(next);
    }

    private void rebuildSpikeChunks(
            List<PreviewSpikeChunkData> prepared,
            Map<Long, String> sources,
            Consumer<String> picked,
            List<Node> nodes) {
        List<TerrainPreviewSpikeChunk> previous = new ArrayList<>(spikeChunks);
        List<TerrainPreviewSpikeChunk> next = new ArrayList<>();
        for (int chunkIndex = 0; chunkIndex < prepared.size(); chunkIndex++) {
            PreviewSpikeChunkData data = prepared.get(chunkIndex);
            TerrainPreviewSpikeChunk chunk = chunkIndex < previous.size()
                    && previous.get(chunkIndex).matches(data)
                    ? previous.get(chunkIndex)
                    : TerrainPreviewSpikeChunk.attach(data,
                    sources, picked, this::pickingAllowed);
            chunk.updateInteraction(sources, picked, this::pickingAllowed);
            next.add(chunk);
            nodes.add(chunk.node());
        }
        spikeChunks.clear();
        spikeChunks.addAll(next);
    }

    private void rebuildSparseAddons(
                               List<Addon> addons,
                               Map<Long, String> sources,
                               Consumer<String> picked,
                               List<Node> nodes) {
        Map<Long, CachedAddon> nextCache = new HashMap<>();
        for (Addon addon : addons) {
            long digest = addon.deterministicDigest();
            CachedAddon cached = addonCache.get(addon.id());
            Shape3D shape = cached != null && cached.digest == digest
                    ? cached.shape : addonShape(addon);
            shape.setUserData(sources.get(addon.id()));
            shape.setOnMouseClicked(event -> {
                if (event.getButton() != MouseButton.PRIMARY
                        || !pickingAllowed()) return;
                Object source = shape.getUserData();
                if (source instanceof String value) picked.accept(value);
                event.consume();
            });
            nextCache.put(addon.id(), new CachedAddon(digest, shape));
            nodes.add(shape);
        }
        addonCache.clear();
        addonCache.putAll(nextCache);
    }

    private static void patchChildren(
            ObservableList<Node> attached, List<Node> desired) {
        Map<Node, Integer> attachedIndices = new IdentityHashMap<>();
        for (int index = 0; index < attached.size(); index++) {
            attachedIndices.put(attached.get(index), index);
        }
        for (int index = 0; index < desired.size(); index++) {
            Integer oldIndex = attachedIndices.get(desired.get(index));
            if (oldIndex != null && oldIndex != index) {
                attached.setAll(desired);
                return;
            }
        }
        int shared = Math.min(attached.size(), desired.size());
        for (int index = 0; index < shared; index++) {
            Node node = desired.get(index);
            if (attached.get(index) != node) attached.set(index, node);
        }
        if (attached.size() > desired.size()) {
            attached.remove(desired.size(), attached.size());
        } else if (desired.size() > attached.size()) {
            attached.addAll(desired.subList(attached.size(), desired.size()));
        }
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName() : message;
    }

    private Shape3D addonShape(Addon addon) {
        if (addon instanceof Potion potion) {
            Sphere sphere = new Sphere(Math.max(.08, potion.triggerRadius * .42), 16);
            position(sphere, potion.center);
            sphere.setMaterial(new PhongMaterial(Color.CORNFLOWERBLUE));
            return sphere;
        }
        if (addon instanceof Portal portal) {
            Box box = new Box(portal.width, portal.height, .08);
            PortalPreviewBasis basis = PortalPreviewBasis.from(portal.forward, portal.up);
            box.getTransforms().setAll(new Affine(
                    basis.right.x, basis.up.x, basis.forward.x, portal.center.x,
                    basis.right.y, basis.up.y, basis.forward.y, -portal.center.y,
                    basis.right.z, basis.up.z, basis.forward.z, portal.center.z));
            box.setMaterial(new PhongMaterial(portal.role == Portal.Role.ENTRANCE
                    ? Color.MEDIUMPURPLE : Color.GOLD));
            return box;
        }
        throw new IllegalArgumentException("Unsupported sparse addon " + addon.kind);
    }

    private FlowPane navigationControls() {
        orbitButton.setId("preview-orbit-mode");
        walkButton.setId("preview-walk-mode");
        ToggleGroup modes = new ToggleGroup();
        orbitButton.setToggleGroup(modes);
        walkButton.setToggleGroup(modes);
        modes.selectToggle(orbitButton);
        orbitButton.setOnAction(event -> {
            modes.selectToggle(orbitButton);
            setNavigationMode(NavigationMode.ORBIT);
        });
        walkButton.setOnAction(event -> {
            modes.selectToggle(walkButton);
            setNavigationMode(NavigationMode.WALK);
        });
        Button reset = new Button("Reset");
        reset.setId("preview-reset-view");
        reset.setOnAction(event -> resetView());

        navigationHelp.setId("preview-navigation-help");
        navigationHelp.setWrapText(true);
        navigationHelp.setMaxWidth(460);
        updateNavigationHelp();
        FlowPane controls = new FlowPane(6, 4,
                orbitButton, walkButton, reset, navigationHelp);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(7));
        controls.setMaxWidth(Double.MAX_VALUE);
        controls.setStyle("-fx-background-color: rgba(20, 23, 29, .88);"
                + " -fx-background-radius: 5;");
        StackPane.setAlignment(controls, Pos.TOP_LEFT);
        StackPane.setMargin(controls, new Insets(9));
        return controls;
    }

    private void installNavigationHandlers() {
        scene.setOnScroll(event -> {
            if (navigationMode != NavigationMode.ORBIT) return;
            orbitCamera.zoom(event.getDeltaY());
            applyOrbitCamera();
            event.consume();
        });
        scene.setOnMousePressed(event -> {
            requestFocus();
            anchorX = event.getSceneX();
            anchorY = event.getSceneY();
        });
        scene.setOnMouseDragged(event -> {
            double deltaX = event.getSceneX() - anchorX;
            double deltaY = event.getSceneY() - anchorY;
            anchorX = event.getSceneX();
            anchorY = event.getSceneY();
            if (navigationMode == NavigationMode.ORBIT) {
                if (event.isPrimaryButtonDown()) {
                    orbitCamera.orbit(deltaX, deltaY);
                } else if (event.isMiddleButtonDown() || event.isSecondaryButtonDown()) {
                    orbitCamera.pan(deltaX, deltaY, viewportHeight(), camera.getFieldOfView());
                } else {
                    return;
                }
                applyOrbitCamera();
                event.consume();
            } else if (navigationMode == NavigationMode.WALK
                    && event.isPrimaryButtonDown() && walkCamera.isInitialized()) {
                walkCamera.mouseLook(deltaX, deltaY);
                applyWalkCamera();
                event.consume();
            }
        });
        addEventFilter(KeyEvent.KEY_PRESSED, this::handleNavigationKey);
    }

    private void setNavigationMode(NavigationMode mode) {
        navigationMode = mode;
        if (mode == NavigationMode.WALK && walkCamera.initializeIfNeeded(latestSnapshot)) {
            walkButton.getToggleGroup().selectToggle(walkButton);
            applyWalkCamera();
        } else if (mode == NavigationMode.ORBIT) {
            orbitButton.getToggleGroup().selectToggle(orbitButton);
            if (!orbitCamera.isInitialized()) frameAllInternal(false);
            applyOrbitCamera();
        }
        updateNavigationHelp();
        requestFocus();
    }

    private void handleNavigationKey(KeyEvent event) {
        KeyCode code = event.getCode();
        if (code == KeyCode.F) {
            if (event.isShiftDown()) frameSelection();
            else frameAll();
            event.consume();
            return;
        }
        if (code == KeyCode.R) {
            resetView();
            event.consume();
            return;
        }
        if (navigationMode != NavigationMode.WALK || !walkCamera.isInitialized()) return;
        if (code == KeyCode.W) {
            walkCamera.move(WALK_STEP);
        } else if (code == KeyCode.S) {
            walkCamera.move(-WALK_STEP);
        } else if (code == KeyCode.A) {
            walkCamera.turn(WALK_TURN_DEGREES);
        } else if (code == KeyCode.D) {
            walkCamera.turn(-WALK_TURN_DEGREES);
        } else if (code == KeyCode.Q) {
            walkCamera.elevate(WALK_HEIGHT_STEP);
        } else if (code == KeyCode.E) {
            walkCamera.elevate(-WALK_HEIGHT_STEP);
        } else {
            return;
        }
        applyWalkCamera();
        event.consume();
    }

    private void frameAllInternal(boolean selectOrbit) {
        if (latestBounds.isEmpty()) return;
        if (selectOrbit) setNavigationModeWithoutApplying(NavigationMode.ORBIT);
        orbitCamera.frame(latestBounds, viewportWidth(), viewportHeight(),
                camera.getFieldOfView());
        pendingInitialFit = widthProperty().get() < 20 || heightProperty().get() < 20;
        applyOrbitCamera();
        requestFocus();
    }

    private void setNavigationModeWithoutApplying(NavigationMode mode) {
        navigationMode = mode;
        if (mode == NavigationMode.ORBIT) {
            orbitButton.getToggleGroup().selectToggle(orbitButton);
        } else {
            walkButton.getToggleGroup().selectToggle(walkButton);
        }
        updateNavigationHelp();
    }

    private void completePendingFit() {
        if (!pendingInitialFit || latestBounds.isEmpty()
                || getWidth() < 20 || getHeight() < 20) return;
        pendingInitialFit = false;
        orbitCamera.frame(latestBounds, viewportWidth(), viewportHeight(),
                camera.getFieldOfView());
        if (navigationMode == NavigationMode.ORBIT) applyOrbitCamera();
    }

    private void applyOrbitCamera() {
        if (!orbitCamera.isInitialized()) return;
        PreviewCameraTransforms.apply(camera, orbitCamera.position(),
                orbitCamera.forward(), orbitCamera.sceneRadius(), orbitCamera.distance());
    }

    private void applyWalkCamera() {
        if (!walkCamera.isInitialized()) return;
        Vec3 position = PreviewBounds.toFx(walkCamera.position());
        double yaw = Math.toRadians(walkCamera.yawDegrees());
        double pitch = Math.toRadians(walkCamera.pitchDegrees());
        double horizontal = Math.cos(pitch);
        Vec3 forward = new Vec3(Math.sin(yaw) * horizontal,
                Math.sin(pitch), Math.cos(yaw) * horizontal);
        PreviewCameraTransforms.apply(camera, position, forward,
                latestBounds.radius(), latestBounds.radius());
    }

    private void updateNavigationHelp() {
        navigationHelp.setText(navigationMode == NavigationMode.ORBIT
                ? "Drag orbit · middle/right drag pan · scroll zoom · F frame · ⇧F selection"
                : "Drag mouse-look · W/S move · A/D turn · Q up · E down · R reset");
        navigationHelp.setTextFill(Color.WHITE);
    }

    private void updatePreviewState(PreviewState state, String detail) {
        previewState = state;
        boolean hasGeometry = !terrainRoot.getChildren().isEmpty();
        switch (state) {
            case CURRENT -> {
                terrainRoot.setOpacity(1.0);
                stateOverlay.setVisible(false);
            }
            case COMPILING -> {
                terrainRoot.setOpacity(hasGeometry ? .72 : 1.0);
                showStateOverlay(detail);
            }
            case STALE_INVALID, FAILED -> {
                terrainRoot.setOpacity(hasGeometry ? .42 : 1.0);
                showStateOverlay(detail);
            }
            case EMPTY -> {
                terrainRoot.setOpacity(1.0);
                showStateOverlay(detail);
            }
        }
        stateListener.accept(state);
    }

    private void showStateOverlay(String detail) {
        stateOverlay.setText(detail == null ? "" : detail);
        stateOverlay.setVisible(true);
    }

    private boolean pickingAllowed() {
        return pickingRequested && previewState == PreviewState.CURRENT;
    }

    private double viewportWidth() {
        return Math.max(1.0, getWidth() > 1.0 ? getWidth() : scene.getWidth());
    }

    private double viewportHeight() {
        return Math.max(1.0, getHeight() > 1.0 ? getHeight() : scene.getHeight());
    }

    NavigationMode navigationModeForTesting() { return navigationMode; }
    Vec3 walkPositionForTesting() { return walkCamera.position(); }
    double walkYawForTesting() { return walkCamera.yawDegrees(); }
    double walkPitchForTesting() { return walkCamera.pitchDegrees(); }
    Camera cameraForTesting() { return camera; }
    Vec3 orbitTargetForTesting() { return orbitCamera.target(); }
    double orbitDistanceForTesting() { return orbitCamera.distance(); }
    int terrainChunkCountForTesting() { return chunks.size(); }
    int terrainMeshViewCountForTesting() { return chunks.size() * 2; }
    int spikeChunkCountForTesting() { return spikeChunks.size(); }
    long requestedBuildTicketForTesting() { return requestedBuildTicket; }
    long completedBuildTicketForTesting() { return completedBuildTicket; }
    boolean lastBufferBuildRanOnFxThreadForTesting() {
        return lastBufferBuildRanOnFxThread;
    }
    List<Object> chunkIdentitiesForTesting() {
        return chunks.stream().map(value -> (Object) value.node()).toList();
    }
    List<Object> spikeChunkIdentitiesForTesting() {
        return spikeChunks.stream().map(value -> (Object) value.node()).toList();
    }
    String sourceForFaceForTesting(int chunk, boolean fill, int face) {
        return chunks.get(chunk).sourceForFace(fill, face);
    }
    String spikeSourceForFaceForTesting(int chunk, int face) {
        return spikeChunks.get(chunk).sourceForFace(face);
    }

    private static void configureOverlay(Label label, Pos alignment, Insets margin) {
        StackPane.setAlignment(label, alignment);
        StackPane.setMargin(label, margin);
    }

    private static void position(Shape3D shape, Vec3 value) {
        shape.setTranslateX(value.x);
        shape.setTranslateY(-value.y);
        shape.setTranslateZ(value.z);
    }

    private record CachedAddon(long digest, Shape3D shape) {
    }
}
