package com.example.game3d.simulator;

import com.example.game3d.core.input.PlayerInputEvent;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.simulation.PhysicsConfig;
import com.example.game3d.core.simulation.SimulationEngine;
import com.example.game3d.core.simulation.StepResult;
import com.example.game3d.core.terrain.TerrainCommit;
import com.example.game3d.core.terrain.TerrainWorld;
import com.example.game3d.core.terrain.TrackBuilder;

import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class NdjsonTraceWriterTest {
    private final PhysicsConfig config = new PhysicsConfig();

    @Test
    public void summaryTraceIsByteStableAndHasOneVersionedRecordPerTick() {
        Scenario scenario = new ScenarioRegistry().require("feather_collection");

        TraceCapture first = capture(scenario, TraceLevel.SUMMARY, 60);
        TraceCapture second = capture(scenario, TraceLevel.SUMMARY, 60);

        assertEquals(first.contents, second.contents);
        assertEquals(61, first.lines.length);
        String header = first.lines[0];
        assertTrue(header.startsWith("{\"type\":\"header\",\"schema\":10,"));
        assertTrue(header.endsWith("}"));
        assertTrue(header.contains("\"scenario\":\"feather_collection\""));
        assertTrue(header.contains("\"fixedHz\":120"));
        assertTrue(header.contains("\"dtNanos\":" + PhysicsConfig.FIXED_DT_NANOS));
        assertTrue(header.contains("\"landingBufferTicks\":"
                + PhysicsConfig.FIXED_HZ / 6));
        assertTrue(header.contains("\"landingBufferMillis\":166.666660000"));
        assertTrue(header.contains("\"jumpCooldownMillis\":100.000000000"));
        assertTrue(header.contains("\"maxJumpChargeXToYRatio\":1.200000000"));
        assertTrue(header.contains("\"heldGestureChargeGraceMillis\":100.000000000"));
        assertTrue(header.contains("\"heldGestureChargeDecayMillis\":400.000000000"));
        assertTrue(header.contains("\"cylinderRadius\":"));
        assertTrue(header.contains("\"spinConvention\":\"right-hand"));
        assertTrue(header.contains("\"triangleCount\":4"));
        assertTrue(header.contains("\"featureCount\":1"));
        assertTrue(header.contains("\"terrainDigest\":\""));
        assertTrue(header.contains("\"terrainRevision\":0"));
        assertTrue(header.contains("\"traceLevel\":\"SUMMARY\""));
        assertTrue(header.contains("\"terrainSegments\":["));
        assertTrue(header.contains("\"terrainTriangles\":["));
        assertTrue(header.contains("\"terrainFeatures\":["));

        int featherEvents = 0;
        for (int i = 1; i < first.lines.length; i++) {
            String tick = first.lines[i];
            assertTrue(tick.startsWith("{\"type\":\"tick\",\"tick\":" + i + ","));
            assertTrue(tick.contains("\"timeNanos\":"
                    + i * PhysicsConfig.FIXED_DT_NANOS));
            assertTrue(tick.contains("\"terrainRevision\":0"));
            assertTrue(tick.contains("\"appliedTerrainCommits\":[]"));
            assertTrue(tick.contains("\"before\":{"));
            assertTrue(tick.contains("\"after\":{"));
            assertTrue(tick.contains("\"inputs\":["));
            assertTrue(tick.contains("\"jumpRules\":["));
            assertTrue(tick.contains("\"events\":["));
            assertTrue(tick.contains("\"motionSegments\":["));
            assertTrue(tick.contains("\"spinSegments\":["));
            assertTrue(tick.contains("\"axleDeltaRadians\":"));
            assertTrue(tick.contains("\"supportNormal\":["));
            assertTrue(tick.contains("\"landingJumpArmed\":"));
            assertTrue(tick.contains("\"impactBrakeArmed\":"));
            assertTrue(tick.contains("\"gestureChargePotential\":"));
            assertTrue(tick.contains("\"gestureMaxAbsRawDeltaX\":"));
            assertTrue(tick.contains("\"jumpChargePathEligible\":"));
            assertTrue(tick.contains("\"lastSwipeChargeEligible\":"));
            assertFalse(tick.contains("\"queriedTriangles\":"));
            assertFalse(tick.contains("\"contacts\":"));
            assertFalse(tick.contains("\"cylinder\":"));
            assertFalse(tick.contains("NaN"));
            assertFalse(tick.contains("Infinity"));
            featherEvents += occurrences(tick, "\"type\":\"FEATHER_COLLECTED\"");
        }
        assertEquals(1, featherEvents);

        String featherLine =
                first.onlyLineContaining("\"type\":\"FEATHER_COLLECTED\"");
        String after = section(featherLine, "\"after\":", ",\"inputs\":");
        String featherEvent = eventObject(featherLine, "FEATHER_COLLECTED");
        assertEquals(1.0, doubleField(featherEvent, "tickFraction"), 0.0);
        assertEquals(longField(after, "timeNanos"),
                longField(featherEvent, "timeNanos"));
        assertVecEquals(vecField(after, "absolutePosition"),
                vecField(featherEvent, "position"), 1.0e-9);
    }

    @Test
    public void schemaTenInputsRetainRawPhysicalGestureDeltas() {
        TraceCapture trace = capture(
                new ScenarioRegistry().require("ground_jump"),
                TraceLevel.SUMMARY,
                40);

        String swipeTick = trace.onlyLineContaining("\"type\":\"SWIPE\"");
        assertTrue(swipeTick.contains("\"dxScreenHeights\":0.000000000"));
        assertTrue(swipeTick.contains("\"dyScreenHeights\":-0.300000000"));
        assertTrue(swipeTick.contains("\"rawDxScreenHeights\":0.000000000"));
        assertTrue(swipeTick.contains("\"rawDyScreenHeights\":-0.300000000"));
    }

    @Test
    public void streamingCommitIsRecordedOnTheTickThatFirstUsesIt() {
        Scenario scenario = new ScenarioRegistry().require("streaming_commit");

        TraceCapture trace = capture(scenario, TraceLevel.SUMMARY, 42);

        assertTrue(trace.lines[0].contains("\"triangleCount\":2"));
        assertTrue(trace.lines[0].contains("\"terrainSegments\":["));
        String commitTick = trace.lines[41];
        assertTrue(commitTick.startsWith("{\"type\":\"tick\",\"tick\":41,"));
        assertTrue(commitTick.contains("\"terrainRevision\":1"));
        assertTrue(commitTick.contains("\"terrainCommitsAppliedBeforeTick\":40"));
        assertTrue(commitTick.contains("\"appliedTerrainCommits\":[{"));
        assertTrue(commitTick.contains("\"baseRevision\":0"));
        assertTrue(commitTick.contains("\"revision\":1"));
        assertTrue(commitTick.contains("\"segmentUpserts\":[{\"id\":1,"));
        assertTrue(trace.lines[40].contains("\"terrainRevision\":0"));
        assertTrue(trace.lines[42].contains("\"terrainRevision\":1"));
        assertTrue(trace.lines[42].contains("\"appliedTerrainCommits\":[]"));
    }

    @Test
    public void traceLevelsAddDiagnosticsWithoutChangingBaseSchema() {
        Scenario scenario = new ScenarioRegistry().require("flat_rest");
        String summaryTick = capture(scenario, TraceLevel.SUMMARY, 1).lines[1];
        TraceCapture contacts = capture(scenario, TraceLevel.CONTACTS, 1);
        TraceCapture full = capture(scenario, TraceLevel.FULL, 1);
        String contactsTick = contacts.lines[1];
        String fullTick = full.lines[1];

        assertFalse(summaryTick.contains("\"queriedTriangles\":"));
        assertTrue(contactsTick.contains("\"queriedTriangles\":["));
        assertTrue(contactsTick.contains("\"contacts\":["));
        assertTrue(contactsTick.contains("\"timingQuality\":\""));
        assertTrue(contactsTick.contains("\"resolvedCenter\":["));
        assertFalse(contactsTick.contains("\"cylinder\":"));
        assertTrue(fullTick.contains("\"queriedTriangles\":["));
        assertTrue(fullTick.contains("\"contacts\":["));
        assertTrue(fullTick.contains("\"cylinder\":{"));
        assertTrue(fullTick.contains("\"rimSamples\":["));
        assertTrue(fullTick.contains("\"visualVertices\":[]"));
        assertTrue(full.lines[0].contains("\"traceLevel\":\"FULL\""));
        assertTrue(full.lines[0].contains("\"visualVertexCount\":0"));
    }

    @Test
    public void groundedJumpEventSerializesTheTakeoffPointBeforeMotion() {
        Scenario scenario = new ScenarioRegistry().require("ground_jump");
        TraceCapture trace = capture(scenario, TraceLevel.SUMMARY, 50);
        String jumpLine = trace.onlyLineContaining("\"type\":\"JUMP\"");
        String before = section(jumpLine, "\"before\":", ",\"after\":");
        String after = section(jumpLine, "\"after\":", ",\"inputs\":");
        String event = eventObject(jumpLine, "JUMP");

        long beforeTime = longField(before, "timeNanos");
        long eventTime = longField(event, "timeNanos");
        double tickFraction = doubleField(event, "tickFraction");
        Vec3 beforePosition = vecField(before, "absolutePosition");
        Vec3 eventPosition = vecField(event, "position");
        Vec3 afterPosition = vecField(after, "absolutePosition");

        assertTrue(jumpLine.contains("\"id\":\"GROUNDED_RELEASED\","
                + "\"action\":\"JUMP_NOW\""));
        assertEquals(0.0, tickFraction, 0.0);
        assertEquals(beforeTime, eventTime);
        assertVecEquals(beforePosition, eventPosition, 1.0e-9);
        assertTrue("the first post-jump sample must be above the event marker",
                afterPosition.y > eventPosition.y);
    }

    @Test
    public void landingJumpSerializesItsExactFractionWithinTheContactTick() {
        TerrainWorld terrain = new TrackBuilder(8.0).straight(100.0).build();
        Scenario scenario = new Scenario(
                "landing_jump_trace",
                "charged held input jumps at the exact landing contact",
                terrain,
                new Vec3(0.0, 3.0, 1.0),
                Vec3.ZERO,
                0,
                120,
                Arrays.asList(
                        PlayerInputEvent.down(0L, 1L),
                        PlayerInputEvent.swipe(0L, 2L, 0.0, -0.30)));

        TraceCapture first = capture(scenario, TraceLevel.SUMMARY, 100);
        TraceCapture second = capture(scenario, TraceLevel.SUMMARY, 100);
        assertEquals(first.contents, second.contents);

        String jumpLine = first.onlyLineContaining("\"type\":\"JUMP\"");
        String before = section(jumpLine, "\"before\":", ",\"after\":");
        String after = section(jumpLine, "\"after\":", ",\"inputs\":");
        String event = eventObject(jumpLine, "JUMP");
        long beforeTime = longField(before, "timeNanos");
        long afterTime = longField(after, "timeNanos");
        long eventTime = longField(event, "timeNanos");
        double tickFraction = doubleField(event, "tickFraction");
        long expectedEventTime = beforeTime
                + Math.round(PhysicsConfig.FIXED_DT_NANOS * tickFraction);

        assertTrue(jumpLine.contains("\"id\":\"LANDING_CHARGED\","
                + "\"action\":\"JUMP_NOW\""));
        assertTrue(tickFraction > 0.0);
        assertTrue(tickFraction <= 1.0);
        assertEquals(expectedEventTime, eventTime);
        assertTrue(eventTime > beforeTime);
        assertTrue(eventTime <= afterTime);
        assertNotNull(vecField(event, "position"));
    }

    private TraceCapture capture(Scenario scenario, TraceLevel level, int ticks) {
        StringWriter buffer = new StringWriter();
        PrintWriter output = new PrintWriter(buffer);
        NdjsonTraceWriter writer = new NdjsonTraceWriter(
                output, level, scenario, config, VisualVertexCloud.empty());
        SimulationEngine engine = scenario.usesCanonicalTerrain()
                ? new SimulationEngine(
                        scenario.terrainSnapshot, config, scenario.initialPosition,
                        scenario.initialVelocity, scenario.initialAngularVelocity,
                        scenario.initialAirJumpCharges, writer)
                : new SimulationEngine(
                        scenario.terrain, config, scenario.initialPosition,
                        scenario.initialVelocity, scenario.initialAngularVelocity,
                        scenario.initialAirJumpCharges, writer);
        for (int tick = 0; tick < ticks; tick++) {
            List<TerrainCommit> commits = scenario.commitsForTick(tick);
            for (TerrainCommit commit : commits) {
                engine.applyTerrainCommit(commit);
            }
            if (!commits.isEmpty()) {
                writer.onTerrainCommits(
                        tick, commits, engine.terrainRevision(), engine.terrainDigest());
            }
            StepResult result = engine.step(scenario.inputForTick(tick));
            if (result.snapshot.dead) {
                break;
            }
        }
        output.flush();
        return new TraceCapture(buffer.toString());
    }

    private static String section(String json, String startMarker, String endMarker) {
        int start = json.indexOf(startMarker);
        assertTrue("missing " + startMarker, start >= 0);
        start += startMarker.length();
        int end = json.indexOf(endMarker, start);
        assertTrue("missing " + endMarker, end >= start);
        return json.substring(start, end);
    }

    private static String eventObject(String tickJson, String eventType) {
        String marker = "{\"type\":\"" + eventType + "\"";
        int start = tickJson.indexOf(marker);
        assertTrue("missing event " + eventType, start >= 0);
        int end = tickJson.indexOf('}', start);
        assertTrue("unterminated event " + eventType, end > start);
        return tickJson.substring(start, end + 1);
    }

    private static long longField(String json, String name) {
        return Long.parseLong(numberField(json, name));
    }

    private static double doubleField(String json, String name) {
        return Double.parseDouble(numberField(json, name));
    }

    private static String numberField(String json, String name) {
        String marker = "\"" + name + "\":";
        int start = json.indexOf(marker);
        assertTrue("missing number field " + name, start >= 0);
        start += marker.length();
        int end = start;
        while (end < json.length()) {
            char value = json.charAt(end);
            if (!(value == '-' || value == '+' || value == '.'
                    || value == 'e' || value == 'E'
                    || (value >= '0' && value <= '9'))) {
                break;
            }
            end++;
        }
        return json.substring(start, end);
    }

    private static Vec3 vecField(String json, String name) {
        String marker = "\"" + name + "\":[";
        int start = json.indexOf(marker);
        assertTrue("missing vector field " + name, start >= 0);
        start += marker.length();
        int end = json.indexOf(']', start);
        assertTrue("unterminated vector field " + name, end > start);
        String[] parts = json.substring(start, end).split(",");
        assertEquals(3, parts.length);
        return new Vec3(Double.parseDouble(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]));
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual, double epsilon) {
        assertEquals(expected.x, actual.x, epsilon);
        assertEquals(expected.y, actual.y, epsilon);
        assertEquals(expected.z, actual.z, epsilon);
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static final class TraceCapture {
        final String contents;
        final String[] lines;

        TraceCapture(String contents) {
            this.contents = contents;
            this.lines = contents.split("\\r?\\n");
        }

        String onlyLineContaining(String needle) {
            String found = null;
            for (String line : lines) {
                if (!line.contains(needle)) {
                    continue;
                }
                assertTrue("multiple lines contain " + needle, found == null);
                found = line;
            }
            assertNotNull("no line contains " + needle, found);
            return found;
        }
    }
}
