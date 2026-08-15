package com.example.game3d.simulator;

import com.example.game3d.core.input.PlayerInputEvent;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.simulation.PhysicsConfig;
import com.example.game3d.authoring.GameplayTerrainStream;
import com.example.game3d.authoring.GameplayLevelCatalog;
import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.core.terrain.SurfaceMaterial;
import com.example.game3d.core.terrain.TerrainCommit;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.core.terrain.TerrainWorld;
import com.example.game3d.core.terrain.TrackBuilder;
import com.example.game3d.terrain.io.publish.PublishedGameplayCatalogLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic scenarios built from the same canonical terrain contracts as gameplay. */
public final class ScenarioRegistry {
    private static final String TERRAIN_CATALOG_PROPERTY = "game3d.terrainCatalog";
    static final String PACKAGED_TERRAIN_CATALOG = "/terrain/runtime-catalog.json";
    private final Map<String, Scenario> scenarios = new LinkedHashMap<String, Scenario>();
    private final PhysicsConfig config = new PhysicsConfig();
    private final GameplayLevelCatalog gameplayCatalog;

    public ScenarioRegistry() {
        this(loadPublishedCatalogOrBuiltIns());
    }

    ScenarioRegistry(GameplayLevelCatalog gameplayCatalog) {
        if (gameplayCatalog == null) {
            throw new IllegalArgumentException("gameplayCatalog == null");
        }
        this.gameplayCatalog = gameplayCatalog;
        register(flatRest());
        register(groundJump());
        register(jumpChargeXBoundaryAccept());
        register(jumpChargeXRatioReject());
        register(jumpChargeLargeXAccept());
        register(slopeAndBoost());
        register(gapRecovery());
        register(spikeAvoidance());
        register(downHoldNoBounce());
        register(downHoldThenCharge());
        register(downReleaseBounces());
        register(landingBufferNearSafe());
        register(landingBufferTooEarly());
        register(landingBufferRising());
        register(landingBufferRisingBounce());
        register(landingBufferRisingRamp());
        register(featherCollection());
        register(airborneRedirect());
        register(openLift());
        register(streamingCommit());
        register(generatedGameplayStream());
        register(publishedCatalogLevel());
    }

    public Scenario require(String name) {
        Scenario scenario = scenarios.get(name);
        if (scenario == null) {
            throw new IllegalArgumentException("Unknown scenario '" + name
                    + "'. Use 'list' to see available scenarios.");
        }
        return scenario;
    }

    public List<Scenario> all() {
        return Collections.unmodifiableList(new ArrayList<Scenario>(scenarios.values()));
    }

    private void register(Scenario scenario) {
        scenarios.put(scenario.name, scenario);
    }

    private Vec3 restingStart() {
        return new Vec3(0.0, config.cylinderRadius + 0.002, 1.0);
    }

    private Scenario flatRest() {
        TrackBuilder terrain = new TrackBuilder(6.0).straight(240.0);
        return scenario("flat_rest", "Motorized cylinder settles and rolls on a flat track",
                terrain, restingStart(), 0, 600);
    }

    private Scenario groundJump() {
        TrackBuilder terrain = new TrackBuilder(6.0).straight(160.0);
        List<PlayerInputEvent> inputs = new ArrayList<PlayerInputEvent>();
        inputs.add(PlayerInputEvent.down(Scenario.atMillis(250), 1));
        inputs.add(PlayerInputEvent.swipe(Scenario.atMillis(270), 2, 0.0, -0.30));
        inputs.add(PlayerInputEvent.up(Scenario.atMillis(290), 3));
        return scenario("ground_jump", "Ground release jump and subsequent landing",
                terrain, restingStart(), 0, 500, inputs);
    }

    private Scenario jumpChargeXBoundaryAccept() {
        return jumpChargeMovementScenario(
                "jump_charge_x_boundary_accept",
                "A near-boundary movement is vertical enough to contribute jump charge",
                0.059,
                -0.050);
    }

    private Scenario jumpChargeXRatioReject() {
        return jumpChargeMovementScenario(
                "jump_charge_x_ratio_reject",
                "A diagonal movement outside the vertical ratio contributes no jump charge",
                0.050,
                -0.040);
    }

    private Scenario jumpChargeLargeXAccept() {
        return jumpChargeMovementScenario(
                "jump_charge_x_large_accept",
                "A large movement contributes because its per-movement direction is vertical enough",
                0.061,
                -0.060);
    }

    private Scenario jumpChargeMovementScenario(
            String name, String description, double deltaX, double deltaY) {
        TrackBuilder terrain = new TrackBuilder(20.0).straight(80.0);
        ArrayList<PlayerInputEvent> inputs = new ArrayList<PlayerInputEvent>();
        inputs.add(PlayerInputEvent.down(Scenario.atMillis(50), 50));
        inputs.add(PlayerInputEvent.swipe(
                Scenario.atMillis(65), 51,
                deltaX, deltaY, deltaX, deltaY));
        inputs.add(PlayerInputEvent.up(Scenario.atMillis(80), 52));
        return scenario(name, description, terrain, restingStart(), 0, 90, inputs);
    }

    private Scenario slopeAndBoost() {
        TrackBuilder track = new TrackBuilder(6.0)
                .straight(18.0)
                .slope(12.0, 5.0)
                .straight(10.0)
                .material(SurfaceMaterial.BOOST)
                .slope(12.0, -3.0)
                .material(SurfaceMaterial.NORMAL)
                .straight(200.0);
        return scenario("slope_boost", "Gravity, rolling motor and boost material on slopes",
                track, restingStart(), 0, 700);
    }

    private Scenario gapRecovery() {
        TrackBuilder track = new TrackBuilder(6.0)
                .straight(12.0)
                .gap(8.0)
                .straight(220.0);
        List<PlayerInputEvent> inputs = chargedRelease(520, 0.0);
        return scenario("gap_recovery",
                "Released charge is forecast across a gap with one persistent air jump",
                track, restingStart(), 1, 650, inputs);
    }

    private Scenario spikeAvoidance() {
        TrackBuilder track = new TrackBuilder(6.0)
                .straight(3.0)
                .spike(0.0, 0.0, 0.45, 1.5)
                .straight(220.0);
        List<PlayerInputEvent> inputs = chargedRelease(0, 0.0);
        return new Scenario("spike_avoidance",
                "No-jump forecast spends one charge before a spike",
                track.build(), track.buildSnapshot(), new Vec3(0.0, 2.0, 1.0),
                new Vec3(0.0, 0.0, -16.0),
                -16.0 / config.cylinderRadius,
                1, 600, inputs,
                Collections.<Long, List<TerrainCommit>>emptyMap());
    }

    private Scenario downHoldNoBounce() {
        TrackBuilder track = new TrackBuilder(8.0).straight(200.0);
        ArrayList<PlayerInputEvent> inputs = new ArrayList<PlayerInputEvent>();
        inputs.add(PlayerInputEvent.down(Scenario.atMillis(0), 30));
        inputs.add(PlayerInputEvent.swipe(Scenario.atMillis(15), 31, 0.0, 0.12));
        // The release is intentionally well after the roughly 115 ms ground impact.
        inputs.add(PlayerInputEvent.up(Scenario.atMillis(400), 32));
        return scenario("down_hold_no_bounce",
                "A downward swipe held through hard impact absorbs restitution",
                track, new Vec3(0.0, 3.0, 1.0), new Vec3(0.0, -20.0, 0.0),
                0, 180, inputs);
    }

    private Scenario downReleaseBounces() {
        TrackBuilder track = new TrackBuilder(8.0).straight(200.0);
        ArrayList<PlayerInputEvent> inputs = new ArrayList<PlayerInputEvent>();
        inputs.add(PlayerInputEvent.down(Scenario.atMillis(0), 40));
        inputs.add(PlayerInputEvent.swipe(Scenario.atMillis(15), 41, 0.0, 0.12));
        inputs.add(PlayerInputEvent.up(Scenario.atMillis(50), 42));
        return scenario("down_release_bounces",
                "Releasing the downward swipe before hard impact restores restitution",
                track, new Vec3(0.0, 3.0, 1.0), new Vec3(0.0, -20.0, 0.0),
                0, 180, inputs);
    }

    private Scenario downHoldThenCharge() {
        TrackBuilder track = new TrackBuilder(8.0).straight(200.0);
        ArrayList<PlayerInputEvent> inputs = new ArrayList<PlayerInputEvent>();
        inputs.add(PlayerInputEvent.down(Scenario.atMillis(0), 35));
        inputs.add(PlayerInputEvent.swipe(Scenario.atMillis(15), 36, 0.0, 0.12));
        // Keep the same physical touch held through the roughly 115 ms impact, then reverse it.
        inputs.add(PlayerInputEvent.swipe(Scenario.atMillis(160), 37, 0.0, -0.12));
        inputs.add(PlayerInputEvent.up(Scenario.atMillis(220), 38));
        return scenario("down_hold_then_charge",
                "A held brake becomes a fresh upward jump charge after landing",
                track, new Vec3(0.0, 3.0, 1.0), new Vec3(0.0, -20.0, 0.0),
                0, 180, inputs);
    }

    private Scenario landingBufferNearSafe() {
        TrackBuilder track = new TrackBuilder(8.0).straight(200.0);
        return scenario("landing_buffer_near_safe",
                "A falling release near spike-safe support arms a charge-preserving landing jump",
                track, new Vec3(0.0, 1.4, 1.0), new Vec3(0.0, -1.0, 0.0),
                1, 260, chargedRelease(0, 0.0));
    }

    private Scenario landingBufferTooEarly() {
        TrackBuilder track = new TrackBuilder(8.0).straight(240.0);
        return scenario("landing_buffer_too_early",
                "Safe support beyond the roughly 167 ms window does not retain an airborne release",
                track, new Vec3(0.0, 5.0, 1.0), new Vec3(0.0, -1.0, 0.0),
                1, 180, chargedRelease(0, 0.0));
    }

    private Scenario landingBufferRising() {
        TrackBuilder track = new TrackBuilder(8.0).straight(200.0);
        return scenario("landing_buffer_rising",
                "An upward-moving release uses its air charge instead of arming a landing jump",
                track, new Vec3(0.0, 2.0, 1.0), new Vec3(0.0, 5.0, 0.0),
                1, 220, chargedRelease(0, 0.0));
    }

    private Scenario landingBufferRisingBounce() {
        TrackBuilder track = new TrackBuilder(8.0).straight(200.0);
        return scenario("landing_buffer_rising_bounce",
                "A release during a rising rebound spends its air charge instead of sticking",
                track,
                new Vec3(0.0, config.cylinderRadius + 0.020, 1.0),
                new Vec3(0.0, -10.8, 0.0),
                1, 80, chargedRelease(50, 0.0));
    }

    private Scenario landingBufferRisingRamp() {
        TrackBuilder track = new TrackBuilder(8.0)
                .slope(4.0, 2.0)
                .gap(0.5)
                .straight(100.0);
        double angle = Math.atan2(2.0, 4.0);
        Vec3 tangent = new Vec3(0.0, Math.sin(angle), -Math.cos(angle));
        Vec3 normal = new Vec3(0.0, Math.cos(angle), Math.sin(angle));
        Vec3 surfacePoint = new Vec3(0.0, 1.75, -1.5);
        Vec3 start = surfacePoint.add(normal.multiply(config.cylinderRadius));
        return scenario("landing_buffer_rising_ramp",
                "A rising ramp takeoff with no air charge rejects the release instead of sticking",
                track, start, tangent.multiply(12.0),
                0, 60, chargedRelease(85, 0.0));
    }

    private Scenario featherCollection() {
        TrackBuilder track = new TrackBuilder(6.0)
                .straight(8.0)
                .feather(0.0, 0.0, config.cylinderRadius, 0.22)
                .straight(100.0);
        return scenario("feather_collection",
                "A collectible feather adds exactly one persistent air-jump charge",
                track, restingStart(), 0, 400);
    }

    private Scenario airborneRedirect() {
        TrackBuilder terrain = new TrackBuilder(200.0).straight(240.0);
        List<PlayerInputEvent> inputs = new ArrayList<PlayerInputEvent>();
        inputs.addAll(chargedRelease(250, 0.0));
        inputs.add(PlayerInputEvent.swipe(Scenario.atMillis(380), 20, 0.22, 0.0));
        return scenario("airborne_redirect",
                "Air swipe changes facing only; first ground contact redirects velocity",
                terrain, restingStart(), 0, 550, inputs);
    }

    private Scenario openLift() {
        TrackBuilder terrain = new TrackBuilder(6.0)
                .straight(12.0)
                .gap(3.0)
                .lift(1.2)
                .straight(100.0);
        return scenario("open_lift", "Disconnected elevated top has no hidden vertical riser",
                terrain, restingStart(), 0, 500);
    }

    private Scenario streamingCommit() {
        TrackBuilder complete = new TrackBuilder(6.0)
                .straight(12.0)
                .straight(220.0);
        TerrainSnapshot completeSnapshot = complete.buildSnapshot();
        TerrainSegment first = completeSnapshot.segments.get(0);
        TerrainSegment second = completeSnapshot.segments.get(1);
        TerrainSnapshot initial = new TerrainSnapshot(
                0L, first.id, 0L, Collections.singletonList(first));
        TerrainCommit extension = new TerrainCommit(
                0L, 1L, second.id, 0L, Collections.singletonList(second));
        LinkedHashMap<Long, List<TerrainCommit>> commits =
                new LinkedHashMap<Long, List<TerrainCommit>>();
        commits.put(40L, Collections.singletonList(extension));
        return new Scenario(
                "streaming_commit",
                "A future track segment is committed atomically before fixed tick 40",
                complete.build(),
                initial,
                restingStart(),
                Vec3.ZERO,
                0.0,
                0,
                500,
                Collections.<PlayerInputEvent>emptyList(),
                commits);
    }

    /**
     * Exercises the concrete producer used by Android, including its real chunk commits.
     */
    private Scenario generatedGameplayStream() {
        Vec3 terrainStart = new Vec3(0.0, -3.5, -0.5);
        GameplayTerrainStream generator =
                new GameplayTerrainStream(
                        TrackProfile.gameplayDefault(), terrainStart, 0L,
                        gameplayCatalog);
        generator.enqueueIntroSegments();
        generator.generateChunks(48);
        TerrainSnapshot initial = generator.snapshot();
        generator.drainPendingCommits();
        generator.generateChunks(-1);
        List<TerrainCommit> extension = generator.drainPendingCommits();
        generator.close();

        LinkedHashMap<Long, List<TerrainCommit>> commits =
                new LinkedHashMap<Long, List<TerrainCommit>>();
        commits.put(80L, extension);
        TerrainWorld compatibilityWorld = new TrackBuilder(3.2)
                .lift(-3.5)
                .straight(224.0)
                .build();
        return new Scenario(
                "generated_gameplay_stream",
                "Android gameplay's CPU terrain generator streams its real intro recipe",
                compatibilityWorld,
                initial,
                new Vec3(0.0, -0.5, -0.5),
                Vec3.ZERO,
                0.0,
                0,
                500,
                Collections.<PlayerInputEvent>emptyList(),
                commits);
    }

    /**
     * Materializes one entry from the exact immutable catalog loaded by this simulator process.
     * When a published extension is present, the first custom entry is selected deliberately so
     * desktop validation exercises more than the built-in fallback prefix.
     */
    private Scenario publishedCatalogLevel() {
        long ordinal = catalogExerciseOrdinal(gameplayCatalog);
        Vec3 terrainStart = new Vec3(0.0, -3.5, -0.5);
        GameplayTerrainStream generator = new GameplayTerrainStream(
                TrackProfile.gameplayDefault(), terrainStart, 0L, gameplayCatalog);
        generator.enqueueGameplayLevel((int) ordinal);
        generator.generateChunks(-1);
        TerrainSnapshot snapshot = generator.snapshot();
        generator.close();

        TerrainWorld compatibilityWorld = new TrackBuilder(3.2)
                .lift(-3.5)
                .straight(Math.max(14.0, snapshot.segments.size() * 1.4))
                .build();
        return new Scenario(
                "published_catalog_level",
                "A level selected from the same published catalog consumed by Android",
                compatibilityWorld,
                snapshot,
                new Vec3(0.0, -0.5, -0.5),
                Vec3.ZERO,
                0.0,
                0,
                120,
                Collections.<PlayerInputEvent>emptyList(),
                Collections.<Long, List<TerrainCommit>>emptyMap());
    }

    private static long catalogExerciseOrdinal(GameplayLevelCatalog catalog) {
        if (catalog.entries().size() <= GameplayLevelCatalog.builtIns().entries().size()) {
            return 0L;
        }
        String firstPublishedId = catalog.entries()
                .get(GameplayLevelCatalog.builtIns().entries().size()).stableId();
        for (long ordinal = 0L; ordinal < 1_000_000L; ordinal++) {
            if (firstPublishedId.equals(catalog.select(ordinal).stableId())) {
                return ordinal;
            }
        }
        throw new IllegalStateException(
                "Published catalog entry cannot be selected: " + firstPublishedId);
    }

    private static GameplayLevelCatalog loadPublishedCatalogOrBuiltIns() {
        String configuredPath = System.getProperty(TERRAIN_CATALOG_PROPERTY);
        if (configuredPath != null && !configuredPath.trim().isEmpty()) {
            Path path = Paths.get(configuredPath);
            if (!Files.isRegularFile(path)) {
                return GameplayLevelCatalog.builtIns();
            }
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return new PublishedGameplayCatalogLoader().loadOrBuiltIns(reader);
            } catch (Exception invalid) {
                return GameplayLevelCatalog.builtIns();
            }
        }

        InputStream packaged = ScenarioRegistry.class.getResourceAsStream(
                PACKAGED_TERRAIN_CATALOG);
        if (packaged == null) {
            return GameplayLevelCatalog.builtIns();
        }
        try (Reader reader = new InputStreamReader(packaged, StandardCharsets.UTF_8)) {
            return new PublishedGameplayCatalogLoader().loadOrBuiltIns(reader);
        } catch (Exception invalid) {
            return GameplayLevelCatalog.builtIns();
        }
    }

    private static List<PlayerInputEvent> chargedRelease(long startMillis, double horizontal) {
        ArrayList<PlayerInputEvent> inputs = new ArrayList<PlayerInputEvent>();
        inputs.add(PlayerInputEvent.down(Scenario.atMillis(startMillis), 10));
        inputs.add(PlayerInputEvent.swipe(Scenario.atMillis(startMillis + 15), 11,
                horizontal, -0.30));
        inputs.add(PlayerInputEvent.up(Scenario.atMillis(startMillis + 30), 12));
        return inputs;
    }

    private static Scenario scenario(String name, String description, TrackBuilder terrain,
                                     Vec3 start, int charges, int ticks) {
        return scenario(name, description, terrain, start, charges, ticks,
                Collections.<PlayerInputEvent>emptyList());
    }

    private static Scenario scenario(String name, String description, TrackBuilder terrain,
                                     Vec3 start, int charges, int ticks,
                                     List<PlayerInputEvent> inputs) {
        return scenario(name, description, terrain, start, Vec3.ZERO,
                charges, ticks, inputs);
    }

    private static Scenario scenario(String name, String description, TrackBuilder terrain,
                                     Vec3 start, Vec3 velocity, int charges, int ticks,
                                     List<PlayerInputEvent> inputs) {
        return new Scenario(
                name,
                description,
                terrain.build(),
                terrain.buildSnapshot(),
                start,
                velocity,
                0.0,
                charges,
                ticks,
                inputs,
                Collections.<Long, List<TerrainCommit>>emptyMap());
    }
}
