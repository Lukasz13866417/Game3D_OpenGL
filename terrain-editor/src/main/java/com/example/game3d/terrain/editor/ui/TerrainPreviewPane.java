package com.example.game3d.terrain.editor.ui;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.core.terrain.addon.Addon;
import com.example.game3d.core.terrain.addon.DeathSpike;
import com.example.game3d.core.terrain.addon.Portal;
import com.example.game3d.core.terrain.addon.Potion;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AmbientLight;
import javafx.scene.Camera;
import javafx.scene.DepthTest;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SubScene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Shape3D;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Affine;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** JavaFX-material approximation of the exact canonical snapshot geometry. */
public final class TerrainPreviewPane extends StackPane {
    enum NavigationMode { ORBIT, WALK }

    private static final double WALK_STEP = .8;
    private static final double WALK_TURN_DEGREES = 5.0;
    private static final double WALK_HEIGHT_STEP = .35;
    private static final double WALK_LOOK_DOWN_DEGREES = -8.0;

    private final Group world = new Group();
    private final SubScene scene;
    private final PerspectiveCamera camera = new PerspectiveCamera(true);
    private final WalkCameraState walkCamera = new WalkCameraState();
    private final Label navigationHelp = new Label();
    private TerrainSnapshot latestSnapshot = TerrainSnapshot.empty();
    private NavigationMode navigationMode = NavigationMode.ORBIT;
    private double anchorX;
    private double anchorY;
    private double orbitYaw = -18;
    private double orbitPitch = -24;
    private double orbitX;
    private double orbitY;
    private double orbitZ = -14;

    public TerrainPreviewPane() {
        world.setDepthTest(DepthTest.ENABLE);
        world.getChildren().add(new AmbientLight(Color.color(.82, .82, .86)));
        scene = new SubScene(world, 800, 600, true, javafx.scene.SceneAntialiasing.BALANCED);
        scene.setFill(Color.rgb(28, 31, 38));
        camera.setNearClip(.05);
        camera.setFarClip(5000);
        scene.setCamera(camera);
        getChildren().addAll(scene, navigationControls());
        setFocusTraversable(true);
        scene.setFocusTraversable(true);
        scene.widthProperty().bind(widthProperty());
        scene.heightProperty().bind(heightProperty());
        applyOrbitCamera();
        scene.setOnScroll(event -> {
            if (navigationMode != NavigationMode.ORBIT) return;
            orbitZ = clamp(orbitZ + event.getDeltaY() * .04, -120, -5);
            applyOrbitCamera();
        });
        scene.setOnMousePressed(event -> {
            requestFocus();
            anchorX = event.getSceneX();
            anchorY = event.getSceneY();
        });
        scene.setOnMouseDragged(event -> {
            if (navigationMode != NavigationMode.ORBIT
                    || event.getButton() != MouseButton.PRIMARY) return;
            orbitYaw += (event.getSceneX() - anchorX) * .3;
            orbitPitch -= (event.getSceneY() - anchorY) * .3;
            anchorX = event.getSceneX(); anchorY = event.getSceneY();
            applyOrbitCamera();
        });
        addEventFilter(KeyEvent.KEY_PRESSED, this::handleNavigationKey);
    }

    public void show(TerrainSnapshot snapshot, Map<String, Long> sourceSegments,
                     Map<String, Long> sourceAddons, Consumer<String> picked) {
        latestSnapshot = snapshot;
        world.getChildren().removeIf(node -> !(node instanceof AmbientLight));
        Map<Long, String> segmentSources = reverse(sourceSegments);
        Map<Long, String> addonSources = reverse(sourceAddons);
        for (TerrainSegment segment : snapshot.segments) {
            MeshView solid = segmentMesh(segment, DrawMode.FILL, false);
            attachPicking(solid, segmentSources.get(segment.id), picked);
            world.getChildren().add(solid);
            MeshView wire = segmentMesh(segment, DrawMode.LINE, true);
            attachPicking(wire, segmentSources.get(segment.id), picked);
            world.getChildren().add(wire);
            for (Addon addon : segment.addons) {
                Shape3D shape = addonShape(addon);
                attachPicking(shape, addonSources.get(addon.id()), picked);
                world.getChildren().add(shape);
            }
        }
        if (navigationMode == NavigationMode.ORBIT) {
            frameOrbit(snapshot);
        } else if (walkCamera.initializeIfNeeded(snapshot)) {
            applyWalkCamera();
        }
    }

    private MeshView segmentMesh(TerrainSegment s, DrawMode mode, boolean wire) {
        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().addAll(points(s.nearLeft, s.nearRight, s.farRight, s.farLeft));
        mesh.getTexCoords().addAll(0, 0);
        mesh.getFaces().addAll(0,0, 1,0, 2,0, 0,0, 2,0, 3,0);
        MeshView view = new MeshView(mesh);
        view.setCullFace(CullFace.NONE);
        view.setDrawMode(mode);
        Color base = s.solid ? Color.color(.25, .58, .34, s.farLeftAppearance.alpha) : Color.TRANSPARENT;
        view.setMaterial(new PhongMaterial(wire ? Color.color(.82, .94, .88) : base));
        return view;
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
            PortalPreviewBasis basis = PortalPreviewBasis.from(
                    portal.forward, portal.up);
            box.getTransforms().setAll(new Affine(
                    basis.right.x, basis.up.x, basis.forward.x, portal.center.x,
                    basis.right.y, basis.up.y, basis.forward.y, -portal.center.y,
                    basis.right.z, basis.up.z, basis.forward.z, portal.center.z));
            box.setMaterial(new PhongMaterial(portal.role == Portal.Role.ENTRANCE
                    ? Color.MEDIUMPURPLE : Color.GOLD));
            return box;
        }
        DeathSpike spike = (DeathSpike) addon;
        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().addAll(points(spike.nearLeft, spike.nearRight, spike.farRight,
                spike.farLeft, spike.apex));
        mesh.getTexCoords().addAll(0, 0);
        mesh.getFaces().addAll(0,0,1,0,4,0, 1,0,2,0,4,0, 2,0,3,0,4,0, 3,0,0,0,4,0);
        MeshView view = new MeshView(mesh);
        view.setCullFace(CullFace.NONE);
        view.setMaterial(new PhongMaterial(Color.CRIMSON));
        return view;
    }

    private void frameOrbit(TerrainSnapshot snapshot) {
        if (snapshot.segments.isEmpty()) return;
        TerrainSegment first = snapshot.segments.get(0);
        TerrainSegment last = snapshot.segments.get(snapshot.segments.size() - 1);
        Vec3 center = first.nearLeft.add(first.nearRight).add(last.farLeft).add(last.farRight).multiply(.25);
        orbitX = center.x;
        orbitY = -center.y - 3;
        orbitZ = -Math.max(14, snapshot.segments.size() * .75);
        applyOrbitCamera();
    }

    private HBox navigationControls() {
        ToggleButton orbit = new ToggleButton("Orbit");
        ToggleButton walk = new ToggleButton("Walk");
        orbit.setId("preview-orbit-mode");
        walk.setId("preview-walk-mode");
        ToggleGroup modes = new ToggleGroup();
        orbit.setToggleGroup(modes);
        walk.setToggleGroup(modes);
        modes.selectToggle(orbit);
        orbit.setOnAction(event -> {
            modes.selectToggle(orbit);
            setNavigationMode(NavigationMode.ORBIT);
        });
        walk.setOnAction(event -> {
            modes.selectToggle(walk);
            setNavigationMode(NavigationMode.WALK);
        });

        navigationHelp.setId("preview-navigation-help");
        updateNavigationHelp();
        HBox controls = new HBox(6, orbit, walk, navigationHelp);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(7));
        controls.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        controls.setStyle("-fx-background-color: rgba(20, 23, 29, .88);"
                + " -fx-background-radius: 5;");
        StackPane.setAlignment(controls, Pos.TOP_LEFT);
        StackPane.setMargin(controls, new Insets(9));
        return controls;
    }

    private void setNavigationMode(NavigationMode mode) {
        navigationMode = mode;
        if (mode == NavigationMode.WALK && walkCamera.initializeIfNeeded(latestSnapshot)) {
            applyWalkCamera();
        } else if (mode == NavigationMode.ORBIT) {
            applyOrbitCamera();
        }
        updateNavigationHelp();
        requestFocus();
    }

    private void handleNavigationKey(KeyEvent event) {
        if (navigationMode != NavigationMode.WALK || !walkCamera.isInitialized()) return;
        KeyCode code = event.getCode();
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

    private void applyOrbitCamera() {
        camera.setTranslateX(orbitX);
        camera.setTranslateY(orbitY);
        camera.setTranslateZ(orbitZ);
        camera.getTransforms().setAll(
                new Rotate(orbitYaw, Rotate.Y_AXIS),
                new Rotate(orbitPitch, Rotate.X_AXIS));
    }

    private void applyWalkCamera() {
        if (!walkCamera.isInitialized()) return;
        Vec3 position = walkCamera.position();
        camera.setTranslateX(position.x);
        camera.setTranslateY(-position.y);
        camera.setTranslateZ(position.z);
        camera.getTransforms().setAll(
                new Rotate(walkCamera.yawDegrees(), Rotate.Y_AXIS),
                new Rotate(WALK_LOOK_DOWN_DEGREES, Rotate.X_AXIS));
    }

    private void updateNavigationHelp() {
        navigationHelp.setText(navigationMode == NavigationMode.ORBIT
                ? "Drag to orbit · scroll to zoom"
                : "W/S move · A/D turn · Q up · E down");
        navigationHelp.setTextFill(Color.WHITE);
    }

    NavigationMode navigationModeForTesting() { return navigationMode; }
    Vec3 walkPositionForTesting() { return walkCamera.position(); }
    double walkYawForTesting() { return walkCamera.yawDegrees(); }
    Camera cameraForTesting() { return camera; }

    private static float[] points(Vec3... values) {
        float[] out = new float[values.length * 3];
        for (int i = 0; i < values.length; i++) {
            out[i * 3] = (float) values[i].x;
            out[i * 3 + 1] = (float) -values[i].y;
            out[i * 3 + 2] = (float) values[i].z;
        }
        return out;
    }
    private static void position(Shape3D shape, Vec3 value) {
        shape.setTranslateX(value.x); shape.setTranslateY(-value.y); shape.setTranslateZ(value.z);
    }
    private static Map<Long, String> reverse(Map<String, Long> source) {
        Map<Long, String> out = new HashMap<>();
        source.forEach((key, value) -> out.put(value, key));
        return out;
    }
    private static void attachPicking(javafx.scene.Node node, String sourceId, Consumer<String> picked) {
        if (sourceId == null) return;
        node.setUserData(sourceId);
        node.setOnMouseClicked(event -> { if (event.getButton() == MouseButton.PRIMARY) picked.accept(sourceId); });
    }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
