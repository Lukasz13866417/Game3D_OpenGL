package com.example.game3d.simulator;

import com.example.game3d.core.simulation.PhysicsConfig;
import com.example.game3d.core.simulation.PlayerSnapshot;
import com.example.game3d.core.simulation.SimulationEngine;
import com.example.game3d.core.simulation.SimulationEvent;
import com.example.game3d.core.simulation.StepObserver;
import com.example.game3d.core.simulation.StepResult;
import com.example.game3d.core.terrain.TerrainCommit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

public final class SimulatorMain {
    private SimulatorMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "help".equals(args[0]) || "--help".equals(args[0])) {
            printHelp();
            return;
        }
        if ("list".equals(args[0])) {
            for (Scenario scenario : new ScenarioRegistry().all()) {
                System.out.println(scenario.name + " - " + scenario.description);
            }
            return;
        }
        if ("diff".equals(args[0])) {
            if (args.length != 3) {
                throw new IllegalArgumentException("diff requires two NDJSON files");
            }
            int exitCode = diff(Paths.get(args[1]), Paths.get(args[2]));
            if (exitCode != 0) {
                System.exit(exitCode);
            }
            return;
        }
        if (!"run".equals(args[0])) {
            throw new IllegalArgumentException("Expected 'list' or 'run'");
        }
        if (args.length < 2) {
            throw new IllegalArgumentException("run requires a scenario name");
        }

        CatalogSelection catalogSelection = CatalogSelection.parse(args);
        if (catalogSelection.catalogPath != null
                && catalogSelection.entryId == null) {
            throw new IllegalArgumentException(
                    "--catalog requires --catalog-entry for exact published-level selection");
        }
        if (catalogSelection.entryId != null
                && !"published_catalog_level".equals(args[1])) {
            throw new IllegalArgumentException(
                    "--catalog-entry is only valid for published_catalog_level");
        }
        ScenarioRegistry registry;
        if (catalogSelection.catalogPath != null) {
            registry = ScenarioRegistry.fromPublishedCatalog(
                    catalogSelection.catalogPath, catalogSelection.entryId);
        } else if (catalogSelection.entryId != null) {
            registry = ScenarioRegistry.fromPackagedCatalog(
                    catalogSelection.entryId);
        } else {
            registry = new ScenarioRegistry();
        }
        Scenario scenario = registry.require(args[1]);
        Options options = Options.parse(args, scenario.defaultTicks);
        PhysicsConfig config = new PhysicsConfig();
        VisualVertexCloud visualVertices = loadVertices(options, config);
        PrintWriter output = openOutput(options.output);
        try {
            NdjsonTraceWriter trace = new NdjsonTraceWriter(
                    output, options.traceLevel, scenario, config, visualVertices);
            if (options.interactive) {
                interactive(scenario, config, trace);
            } else {
                runBatch(scenario, config, trace, options.ticks);
            }
        } finally {
            if (options.output != null) {
                output.close();
            }
        }
    }

    private static void runBatch(Scenario scenario, PhysicsConfig config,
                                 StepObserver observer, int ticks) {
        SimulationEngine engine = createEngine(scenario, config, observer);
        int eventCount = 0;
        for (int tick = 0; tick < ticks; tick++) {
            applyScheduledCommits(engine, scenario, tick, observer);
            StepResult result = engine.step(scenario.inputForTick(tick));
            eventCount += result.events.size();
            if (result.snapshot.dead) {
                break;
            }
        }
        PlayerSnapshot finalState = engine.snapshot();
        System.err.println("scenario=" + scenario.name
                + " ticks=" + finalState.tick
                + " position=" + finalState.absolutePosition
                + " velocity=" + finalState.velocity
                + " omega=" + finalState.angularVelocity
                + " axle=" + finalState.axleRadians
                + " grounded=" + finalState.grounded
                + " terrainRevision=" + engine.terrainRevision()
                + " charges=" + finalState.airJumpCharges
                + " dead=" + finalState.dead
                + " events=" + eventCount
                + " hash=" + Long.toUnsignedString(finalState.stateHash));
    }

    private static void interactive(Scenario scenario, PhysicsConfig config,
                                    StepObserver observer) throws IOException {
        Session session = new Session(scenario, config, observer);
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in,
                StandardCharsets.UTF_8));
        System.err.println("Interactive commands: next, run N, until EVENT, inspect, rewind N, quit");
        while (true) {
            System.err.print("sim[" + session.engine.snapshot().tick + "]> ");
            System.err.flush();
            String line = input.readLine();
            if (line == null) return;
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 0 || parts[0].isEmpty()) continue;
            if ("quit".equals(parts[0]) || "exit".equals(parts[0])) return;
            if ("next".equals(parts[0])) {
                session.step();
            } else if ("run".equals(parts[0])) {
                session.run(parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
            } else if ("until".equals(parts[0]) && parts.length > 1) {
                session.until(parts);
            } else if ("rewind".equals(parts[0]) && parts.length > 1) {
                session.rewind(Integer.parseInt(parts[1]));
            } else if ("inspect".equals(parts[0])) {
                inspect(session.engine.snapshot(), session.last, config);
            } else {
                System.err.println("Unknown command");
            }
        }
    }

    private static void inspect(
            PlayerSnapshot snapshot, StepResult last, PhysicsConfig config) {
        System.err.println("tick=" + snapshot.tick
                + " timeMs=" + snapshot.timeNanos / 1_000_000.0
                + " local=" + snapshot.position
                + " absolute=" + snapshot.absolutePosition
                + " velocity=" + snapshot.velocity
                + " heading=" + snapshot.heading
                + " axis=" + snapshot.cylinderAxis
                + " axle=" + snapshot.axleRadians
                + " axleDelta=" + snapshot.axleDeltaRadians
                + " omega=" + snapshot.angularVelocity
                + " rimSpeed=" + (-snapshot.angularVelocity * config.cylinderRadius)
                + " support=" + snapshot.supportTriangleId
                + " supportSegment=" + snapshot.supportSegmentId
                + " lastSupportSegment=" + snapshot.lastSupportedSegmentId
                + " supportNormal=" + snapshot.supportNormal
                + " charge=" + snapshot.gestureCharge
                + " airCharges=" + snapshot.airJumpCharges
                + " grounded=" + snapshot.grounded
                + " dead=" + snapshot.dead);
        if (last != null) {
            System.err.println("lastRule=" + last.jumpDecision.rule + "/"
                    + last.jumpDecision.action + " events=" + last.events.size());
        }
    }

    private static VisualVertexCloud loadVertices(Options options, PhysicsConfig config)
            throws IOException {
        if (options.traceLevel != TraceLevel.FULL) {
            return VisualVertexCloud.empty();
        }
        Path path = options.objPath != null
                ? options.objPath
                : Paths.get("app/src/main/assets/tire_main.obj");
        if (!Files.exists(path)) {
            System.err.println("Visual OBJ not found at " + path
                    + "; FULL trace will contain analytic cylinder samples only.");
            return VisualVertexCloud.empty();
        }
        return VisualVertexCloud.load(path, config);
    }

    private static PrintWriter openOutput(Path output) throws IOException {
        if (output == null) {
            return new PrintWriter(System.out, false);
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return new PrintWriter(Files.newBufferedWriter(output, StandardCharsets.UTF_8));
    }

    private static void printHelp() {
        System.out.println("Game3D deterministic desktop simulator");
        System.out.println("  list");
        System.out.println("  diff <left.ndjson> <right.ndjson>");
        System.out.println("  run <scenario> [--ticks N | --duration-ms N]");
        System.out.println("       [--trace summary|contacts|full] [--out FILE]");
        System.out.println("       [--obj FILE] [--interactive]");
        System.out.println("       [--catalog-entry STABLE_ID [--catalog FILE]]");
    }

    static int diff(Path leftPath, Path rightPath) throws IOException {
        BufferedReader left = Files.newBufferedReader(leftPath, StandardCharsets.UTF_8);
        BufferedReader right = Files.newBufferedReader(rightPath, StandardCharsets.UTF_8);
        try {
            long lineNumber = 1L;
            while (true) {
                String leftLine = left.readLine();
                String rightLine = right.readLine();
                if (leftLine == null && rightLine == null) {
                    System.out.println("Traces are identical.");
                    return 0;
                }
                if (leftLine == null || rightLine == null || !leftLine.equals(rightLine)) {
                    System.out.println("First divergence at NDJSON line " + lineNumber
                            + " (tick " + Math.max(0L, lineNumber - 1L) + ")");
                    System.out.println("left : " + summarizeLine(leftLine));
                    System.out.println("right: " + summarizeLine(rightLine));
                    return 1;
                }
                lineNumber++;
            }
        } finally {
            left.close();
            right.close();
        }
    }

    private static String summarizeLine(String line) {
        if (line == null) return "<end-of-file>";
        int max = 500;
        return line.length() <= max ? line : line.substring(0, max) + "...";
    }

    private static final class Session {
        final Scenario scenario;
        final PhysicsConfig config;
        final StepObserver observer;
        SimulationEngine engine;
        StepResult last;

        Session(Scenario scenario, PhysicsConfig config, StepObserver observer) {
            this.scenario = scenario;
            this.config = config;
            this.observer = observer;
            reset();
        }

        void reset() {
            engine = createEngine(scenario, config, observer);
            last = null;
        }

        void step() {
            long tick = engine.snapshot().tick;
            applyScheduledCommits(engine, scenario, tick, observer);
            last = engine.step(scenario.inputForTick(tick));
            inspect(engine.snapshot(), last, config);
        }

        void run(int count) {
            for (int i = 0; i < count && !engine.snapshot().dead; i++) {
                step();
            }
        }

        void until(String[] command) {
            String kind = command.length >= 3
                    ? command[1].toLowerCase(Locale.ROOT) : "event";
            String expected = command.length >= 3 ? command[2] : command[1];
            if ("tick".equals(kind)) {
                long target = Long.parseLong(expected);
                if (target < engine.snapshot().tick) {
                    System.err.println("Target tick is behind the current state; use rewind.");
                    return;
                }
                while (engine.snapshot().tick < target && !engine.snapshot().dead) {
                    long tick = engine.snapshot().tick;
                    applyScheduledCommits(engine, scenario, tick, observer);
                    last = engine.step(scenario.inputForTick(tick));
                }
                inspect(engine.snapshot(), last, config);
                return;
            }
            expected = expected.toUpperCase(Locale.ROOT);
            for (int i = 0; i < config.forecastTicks * 4; i++) {
                long tick = engine.snapshot().tick;
                applyScheduledCommits(engine, scenario, tick, observer);
                last = engine.step(scenario.inputForTick(tick));
                if ("rule".equals(kind)) {
                    if (last.jumpDecision.rule.name().equals(expected)) {
                        inspect(engine.snapshot(), last, config);
                        return;
                    }
                } else {
                    for (SimulationEvent event : last.events) {
                        if (event.type.name().equals(expected)) {
                            inspect(engine.snapshot(), last, config);
                            return;
                        }
                    }
                }
                if (engine.snapshot().dead) break;
            }
            System.err.println(kind + " not reached");
        }

        void rewind(int count) {
            long target = Math.max(0L, engine.snapshot().tick - count);
            reset();
            while (engine.snapshot().tick < target) {
                long tick = engine.snapshot().tick;
                applyScheduledCommits(engine, scenario, tick, observer);
                last = engine.step(scenario.inputForTick(tick));
            }
            inspect(engine.snapshot(), last, config);
        }
    }

    private static SimulationEngine createEngine(
            Scenario scenario, PhysicsConfig config, StepObserver observer) {
        if (scenario.usesCanonicalTerrain()) {
            return new SimulationEngine(
                    scenario.terrainSnapshot,
                    config,
                    scenario.initialPosition,
                    scenario.initialVelocity,
                    scenario.initialAngularVelocity,
                    scenario.initialAirJumpCharges,
                    observer);
        }
        return new SimulationEngine(
                scenario.terrain,
                config,
                scenario.initialPosition,
                scenario.initialVelocity,
                scenario.initialAngularVelocity,
                scenario.initialAirJumpCharges,
                observer);
    }

    private static void applyScheduledCommits(
            SimulationEngine engine,
            Scenario scenario,
            long tick,
            StepObserver observer) {
        List<TerrainCommit> commits = scenario.commitsForTick(tick);
        for (TerrainCommit commit : commits) {
            engine.applyTerrainCommit(commit);
        }
        if (!commits.isEmpty() && observer instanceof TerrainCommitObserver) {
            ((TerrainCommitObserver) observer).onTerrainCommits(
                    tick, commits, engine.terrainRevision(), engine.terrainDigest());
        }
    }

    private static final class Options {
        int ticks;
        TraceLevel traceLevel = TraceLevel.SUMMARY;
        Path output;
        Path objPath;
        boolean interactive;

        static Options parse(String[] args, int defaultTicks) {
            Options options = new Options();
            options.ticks = defaultTicks;
            for (int i = 2; i < args.length; i++) {
                String arg = args[i];
                if ("--ticks".equals(arg)) {
                    options.ticks = Integer.parseInt(requireValue(args, ++i, arg));
                } else if ("--duration-ms".equals(arg)) {
                    double millis = Double.parseDouble(requireValue(args, ++i, arg));
                    options.ticks = (int) Math.ceil(
                            millis * PhysicsConfig.FIXED_HZ / 1000.0);
                } else if ("--trace".equals(arg)) {
                    options.traceLevel = TraceLevel.parse(requireValue(args, ++i, arg));
                } else if ("--out".equals(arg)) {
                    options.output = Paths.get(requireValue(args, ++i, arg));
                } else if ("--obj".equals(arg)) {
                    options.objPath = Paths.get(requireValue(args, ++i, arg));
                } else if ("--interactive".equals(arg)) {
                    options.interactive = true;
                } else if ("--catalog".equals(arg)
                        || "--catalog-entry".equals(arg)) {
                    requireValue(args, ++i, arg);
                } else {
                    throw new IllegalArgumentException("Unknown option " + arg);
                }
            }
            if (options.ticks < 1) {
                throw new IllegalArgumentException("ticks must be positive");
            }
            return options;
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return args[index];
        }
    }

    private static final class CatalogSelection {
        Path catalogPath;
        String entryId;

        static CatalogSelection parse(String[] args) {
            CatalogSelection selection = new CatalogSelection();
            for (int i = 2; i < args.length; i++) {
                String argument = args[i];
                if ("--catalog".equals(argument)) {
                    if (selection.catalogPath != null) {
                        throw new IllegalArgumentException("--catalog was supplied more than once");
                    }
                    selection.catalogPath = Paths.get(requireValue(args, ++i, argument));
                } else if ("--catalog-entry".equals(argument)) {
                    if (selection.entryId != null) {
                        throw new IllegalArgumentException(
                                "--catalog-entry was supplied more than once");
                    }
                    selection.entryId = requireValue(args, ++i, argument);
                    if (selection.entryId.trim().isEmpty()) {
                        throw new IllegalArgumentException("--catalog-entry must not be blank");
                    }
                } else if (requiresValue(argument)) {
                    requireValue(args, ++i, argument);
                }
            }
            return selection;
        }

        private static boolean requiresValue(String argument) {
            return "--ticks".equals(argument)
                    || "--duration-ms".equals(argument)
                    || "--trace".equals(argument)
                    || "--out".equals(argument)
                    || "--obj".equals(argument);
        }

        private static String requireValue(
                String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " requires a value");
            }
            return args[index];
        }
    }
}
