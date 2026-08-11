package com.example.game3d.simulator;

import com.example.game3d.core.input.PlayerInputEvent;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.simulation.ContactSnapshot;
import com.example.game3d.core.simulation.CylinderCollider;
import com.example.game3d.core.simulation.JumpDecision;
import com.example.game3d.core.simulation.MotionSegment;
import com.example.game3d.core.simulation.PhysicsConfig;
import com.example.game3d.core.simulation.PlayerSnapshot;
import com.example.game3d.core.simulation.SimulationEvent;
import com.example.game3d.core.simulation.SpinSegment;
import com.example.game3d.core.simulation.StepObserver;
import com.example.game3d.core.simulation.StepRecord;
import com.example.game3d.core.terrain.TerrainTriangle;
import com.example.game3d.core.terrain.TerrainFeature;
import com.example.game3d.core.terrain.TerrainCommit;
import com.example.game3d.core.terrain.TerrainFeatureSpec;
import com.example.game3d.core.terrain.TerrainSegment;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class NdjsonTraceWriter implements StepObserver, TerrainCommitObserver {
    private final PrintWriter output;
    private final TraceLevel level;
    private final PhysicsConfig config;
    private final VisualVertexCloud vertices;
    private long terrainRevision;
    private long terrainDigest;
    private long commitsAppliedBeforeTick = -1L;
    private List<TerrainCommit> pendingTerrainCommits =
            Collections.emptyList();

    NdjsonTraceWriter(PrintWriter output, TraceLevel level, Scenario scenario,
                      PhysicsConfig config, VisualVertexCloud vertices) {
        this.output = output;
        this.level = level;
        this.config = config;
        this.vertices = vertices;
        List<TerrainTriangle> initialTriangles = scenario.usesCanonicalTerrain()
                ? collisionTriangles(scenario.terrainSnapshot.segments)
                : scenario.terrain.triangles();
        terrainRevision = scenario.usesCanonicalTerrain()
                ? scenario.terrainSnapshot.revision : 0L;
        terrainDigest = scenario.usesCanonicalTerrain()
                ? scenario.terrainSnapshot.deterministicDigest
                : scenario.terrain.deterministicDigest();
        StringBuilder header = new StringBuilder(8192);
        header.append("{\"type\":\"header\",\"schema\":9,\"scenario\":\"")
                .append(escape(scenario.name)).append("\",\"description\":\"")
                .append(escape(scenario.description)).append("\",\"fixedHz\":")
                .append(PhysicsConfig.FIXED_HZ)
                .append(",\"dtNanos\":").append(PhysicsConfig.FIXED_DT_NANOS)
                .append(",\"landingBufferTicks\":")
                .append(config.landingJumpBufferTicks)
                .append(",\"landingBufferMillis\":")
                .append(number(config.landingJumpBufferTicks
                        * PhysicsConfig.FIXED_DT_NANOS / 1_000_000.0))
                .append(",\"jumpCooldownMillis\":")
                .append(number(config.jumpCooldownNanos / 1_000_000.0))
                .append(",\"maxJumpChargeXScreenHeights\":")
                .append(number(config.maxJumpChargeXScreenHeights))
                .append(",\"maxJumpChargeXToYRatio\":")
                .append(number(config.maxJumpChargeXToYRatio))
                .append(",\"cylinderRadius\":").append(number(config.cylinderRadius))
                .append(",\"cylinderHalfLength\":")
                .append(number(config.cylinderHalfLength))
                .append(",\"spinConvention\":\"right-hand about cylinderAxis; "
                        + "forward rolling is negative\"")
                .append(",\"triangleCount\":").append(initialTriangles.size())
                .append(",\"featureCount\":").append(countFeatures(scenario))
                .append(",\"terrainDigest\":\"")
                .append(Long.toUnsignedString(terrainDigest))
                .append("\",\"terrainRevision\":").append(terrainRevision)
                .append(",\"visualVertexCount\":").append(vertices.size())
                .append(",\"traceLevel\":\"").append(level.name()).append("\"");
        if (scenario.usesCanonicalTerrain()) {
            header.append(",\"committedThroughSegmentId\":")
                    .append(scenario.terrainSnapshot.committedThroughSegmentId)
                    .append(",\"retireBeforeSegmentId\":")
                    .append(scenario.terrainSnapshot.retireBeforeSegmentId)
                    .append(",\"featureIdHighWatermark\":")
                    .append(scenario.terrainSnapshot.featureIdHighWatermark);
            appendSegmentArray(
                    header, "terrainSegments", scenario.terrainSnapshot.segments);
            appendCanonicalFeatureArray(header, scenario.terrainSnapshot.segments);
        } else {
            appendFeatureArray(header, scenario.terrain.features());
        }
        appendTriangleArray(header, "terrainTriangles", initialTriangles);
        header.append('}');
        output.println(header);
        output.flush();
    }

    @Override
    public void onTerrainCommits(
            long beforeTick,
            List<TerrainCommit> commits,
            long resultingRevision,
            long resultingDigest) {
        commitsAppliedBeforeTick = beforeTick;
        pendingTerrainCommits = Collections.unmodifiableList(
                new ArrayList<TerrainCommit>(commits));
        terrainRevision = resultingRevision;
        terrainDigest = resultingDigest;
    }

    @Override
    public void onStep(StepRecord record) {
        StringBuilder json = new StringBuilder(level == TraceLevel.FULL ? 262144 : 4096);
        json.append("{\"type\":\"tick\",\"tick\":").append(record.after.tick)
                .append(",\"timeNanos\":").append(record.after.timeNanos)
                .append(",\"terrainRevision\":").append(terrainRevision)
                .append(",\"terrainDigest\":\"")
                .append(Long.toUnsignedString(terrainDigest)).append('"');
        if (!pendingTerrainCommits.isEmpty()) {
            json.append(",\"terrainCommitsAppliedBeforeTick\":")
                    .append(commitsAppliedBeforeTick);
            appendCommitArray(json, pendingTerrainCommits);
        } else {
            json.append(",\"appliedTerrainCommits\":[]");
        }
        json
                .append(",\"before\":");
        appendPlayer(json, record.before);
        json.append(",\"after\":");
        appendPlayer(json, record.after);
        appendInputs(json, record.inputs);
        appendRules(json, record.jumpEvaluations);
        appendEvents(json, record.events);
        appendMotionSegments(json, record.motionSegments);
        appendSpinSegments(json, record.spinSegments);
        if (level != TraceLevel.SUMMARY) {
            appendTriangleArray(json, "queriedTriangles", record.queriedTriangles);
            appendContacts(json, record.contacts);
        }
        if (level == TraceLevel.FULL) {
            appendCylinderDiagnostics(json, record.after);
        }
        json.append('}');
        output.println(json);
        output.flush();
        pendingTerrainCommits = Collections.emptyList();
        commitsAppliedBeforeTick = -1L;
    }

    private void appendPlayer(StringBuilder json, PlayerSnapshot player) {
        json.append('{')
                .append("\"tick\":").append(player.tick)
                .append(",\"timeNanos\":").append(player.timeNanos)
                .append(",\"localPosition\":");
        appendVec(json, player.position);
        json.append(",\"absolutePosition\":");
        appendVec(json, player.absolutePosition);
        json.append(",\"velocity\":");
        appendVec(json, player.velocity);
        json.append(",\"heading\":");
        appendVec(json, player.heading);
        json.append(",\"cylinderAxis\":");
        appendVec(json, player.cylinderAxis);
        json.append(",\"yaw\":").append(number(player.yawRadians))
                .append(",\"axleRadians\":").append(number(player.axleRadians))
                .append(",\"axleDeltaRadians\":")
                .append(number(player.axleDeltaRadians))
                .append(",\"angularVelocity\":").append(number(player.angularVelocity))
                .append(",\"driveSurfaceSpeed\":")
                .append(number(player.driveSurfaceSpeed))
                .append(",\"gestureCharge\":").append(number(player.gestureCharge))
                .append(",\"gestureChargePotential\":")
                .append(number(player.gestureChargePotential))
                .append(",\"gestureRawDeltaX\":")
                .append(number(player.gestureRawDeltaX))
                .append(",\"gestureRawUpwardDistance\":")
                .append(number(player.gestureRawUpwardDistance))
                .append(",\"gestureMaxAbsRawDeltaX\":")
                .append(number(player.gestureMaxAbsRawDeltaX))
                .append(",\"jumpChargePathEligible\":")
                .append(player.jumpChargePathEligible)
                .append(",\"airJumpCharges\":").append(player.airJumpCharges)
                .append(",\"grounded\":").append(player.grounded)
                .append(",\"supportTriangleId\":")
                .append(player.supportTriangleId)
                .append(",\"supportSegmentId\":")
                .append(player.supportSegmentId)
                .append(",\"lastSupportedSegmentId\":")
                .append(player.lastSupportedSegmentId)
                .append(",\"supportNormal\":");
        appendVec(json, player.supportNormal);
        json
                .append(",\"touchHeld\":").append(player.touchHeld)
                .append(",\"landingJumpArmed\":").append(player.landingJumpArmed)
                .append(",\"jumpCooldownRemainingNanos\":")
                .append(player.jumpCooldownRemainingNanos)
                .append(",\"impactBrakeArmed\":").append(player.impactBrakeArmed)
                .append(",\"dead\":").append(player.dead)
                .append(",\"stateHash\":\"")
                .append(Long.toUnsignedString(player.stateHash)).append("\"}");
    }

    private static void appendInputs(StringBuilder json, List<PlayerInputEvent> inputs) {
        json.append(",\"inputs\":[");
        for (int i = 0; i < inputs.size(); i++) {
            if (i > 0) json.append(',');
            PlayerInputEvent input = inputs.get(i);
            json.append("{\"type\":\"").append(input.type.name())
                    .append("\",\"timeNanos\":").append(input.timeNanos)
                    .append(",\"sequence\":").append(input.sequence)
                    .append(",\"dxScreenHeights\":").append(number(input.deltaXScreenHeights))
                    .append(",\"dyScreenHeights\":").append(number(input.deltaYScreenHeights))
                    .append(",\"rawDxScreenHeights\":")
                    .append(number(input.rawDeltaXScreenHeights))
                    .append(",\"rawDyScreenHeights\":")
                    .append(number(input.rawDeltaYScreenHeights))
                    .append('}');
        }
        json.append(']');
    }

    private static void appendRules(StringBuilder json, List<JumpDecision> decisions) {
        json.append(",\"jumpRules\":[");
        for (int i = 0; i < decisions.size(); i++) {
            if (i > 0) json.append(',');
            JumpDecision decision = decisions.get(i);
            json.append("{\"id\":\"").append(decision.rule.name())
                    .append("\",\"action\":\"").append(decision.action.name())
                    .append("\",\"consumesAirCharge\":").append(decision.consumesAirCharge)
                    .append(",\"reason\":\"").append(escape(decision.reason)).append("\"}");
        }
        json.append(']');
    }

    private static void appendEvents(StringBuilder json, List<SimulationEvent> events) {
        json.append(",\"events\":[");
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) json.append(',');
            SimulationEvent event = events.get(i);
            json.append("{\"type\":\"").append(event.type.name())
                    .append("\",\"subjectId\":").append(event.subjectId)
                    .append(",\"detail\":\"").append(escape(event.detail)).append('"');
            if (event.timeNanos >= 0L) {
                json.append(",\"timeNanos\":").append(event.timeNanos);
            }
            if (event.position != null) {
                json.append(",\"position\":");
                appendVec(json, event.position);
            }
            if (!Double.isNaN(event.tickFraction)) {
                json.append(",\"tickFraction\":").append(number(event.tickFraction));
            }
            json.append('}');
        }
        json.append(']');
    }

    private static void appendMotionSegments(
            StringBuilder json, List<MotionSegment> segments) {
        json.append(",\"motionSegments\":[");
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) json.append(',');
            MotionSegment segment = segments.get(i);
            json.append("{\"startFraction\":")
                    .append(number(segment.startFraction))
                    .append(",\"endFraction\":")
                    .append(number(segment.endFraction))
                    .append(",\"startPosition\":");
            appendVec(json, segment.startPosition);
            json.append(",\"endPosition\":");
            appendVec(json, segment.endPosition);
            json.append(",\"phase\":\"").append(segment.phase.name()).append("\"}");
        }
        json.append(']');
    }

    private static void appendSpinSegments(
            StringBuilder json, List<SpinSegment> segments) {
        json.append(",\"spinSegments\":[");
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) json.append(',');
            SpinSegment segment = segments.get(i);
            json.append("{\"startFraction\":")
                    .append(number(segment.startFraction))
                    .append(",\"endFraction\":")
                    .append(number(segment.endFraction))
                    .append(",\"mode\":\"").append(segment.mode.name())
                    .append("\",\"deltaRadians\":")
                    .append(number(segment.deltaRadians))
                    .append(",\"startAngularVelocity\":")
                    .append(number(segment.startAngularVelocity))
                    .append(",\"endAngularVelocity\":")
                    .append(number(segment.endAngularVelocity))
                    .append(",\"signedDistance\":")
                    .append(number(segment.signedDistance))
                    .append(",\"supportTriangleId\":")
                    .append(segment.supportTriangleId)
                    .append(",\"supportNormal\":");
            appendVec(json, segment.supportNormal);
            json.append('}');
        }
        json.append(']');
    }

    private static void appendTriangleArray(StringBuilder json, String fieldName,
                                            List<TerrainTriangle> triangles) {
        json.append(",\"").append(fieldName).append("\":[");
        for (int i = 0; i < triangles.size(); i++) {
            if (i > 0) json.append(',');
            TerrainTriangle triangle = triangles.get(i);
            json.append("{\"id\":").append(triangle.id).append(",\"a\":");
            appendVec(json, triangle.a);
            json.append(",\"b\":");
            appendVec(json, triangle.b);
            json.append(",\"c\":");
            appendVec(json, triangle.c);
            json.append(",\"normal\":");
            appendVec(json, triangle.normal);
            json.append(",\"material\":\"").append(triangle.material.name())
                    .append("\",\"surfaceKind\":\"")
                    .append(triangle.surface.kind.name())
                    .append("\",\"motorSpeedMultiplier\":")
                    .append(number(triangle.surface.motorSpeedMultiplier))
                    .append(",\"ownerSegmentId\":")
                    .append(triangle.ownerSegmentId)
                    .append('}');
        }
        json.append(']');
    }

    private static void appendFeatureArray(StringBuilder json, List<TerrainFeature> features) {
        json.append(",\"terrainFeatures\":[");
        for (int i = 0; i < features.size(); i++) {
            if (i > 0) json.append(',');
            TerrainFeature feature = features.get(i);
            json.append("{\"id\":").append(feature.id)
                    .append(",\"kind\":\"").append(feature.kind.name())
                    .append("\",\"center\":");
            appendVec(json, feature.center);
            if (feature instanceof TerrainFeature.Spike) {
                TerrainFeature.Spike spike = (TerrainFeature.Spike) feature;
                json.append(",\"radius\":").append(number(spike.radius))
                        .append(",\"height\":").append(number(spike.height));
            } else if (feature instanceof TerrainFeature.Feather) {
                TerrainFeature.Feather feather = (TerrainFeature.Feather) feature;
                json.append(",\"triggerRadius\":")
                        .append(number(feather.triggerRadius));
            }
            json.append('}');
        }
        json.append(']');
    }

    private static void appendContacts(StringBuilder json, List<ContactSnapshot> contacts) {
        json.append(",\"contacts\":[");
        for (int i = 0; i < contacts.size(); i++) {
            if (i > 0) json.append(',');
            ContactSnapshot contact = contacts.get(i);
            json.append("{\"triangleId\":").append(contact.triangleId)
                    .append(",\"point\":");
            appendVec(json, contact.point);
            json.append(",\"normal\":");
            appendVec(json, contact.normal);
            json.append(",\"penetration\":").append(number(contact.penetration))
                    .append(",\"signedSeparation\":")
                    .append(number(contact.signedSeparation))
                    .append(",\"normalImpulse\":").append(number(contact.normalImpulse))
                    .append(",\"feature\":\"").append(contact.feature)
                    .append("\",\"castIterations\":").append(contact.castIterations)
                    .append(",\"timingQuality\":\"")
                    .append(contact.timingQuality.name()).append('"');
            if (contact.detectedCenter != null) {
                json.append(",\"detectedCenter\":");
                appendVec(json, contact.detectedCenter);
            }
            if (contact.resolvedCenter != null) {
                json.append(",\"resolvedCenter\":");
                appendVec(json, contact.resolvedCenter);
            }
            if (!Double.isNaN(contact.tickFraction)) {
                json.append(",\"tickFraction\":")
                        .append(number(contact.tickFraction));
            }
            if (contact.preVelocity != null) {
                json.append(",\"preVelocity\":");
                appendVec(json, contact.preVelocity);
            }
            if (contact.postVelocity != null) {
                json.append(",\"postVelocity\":");
                appendVec(json, contact.postVelocity);
            }
            if (!Double.isNaN(contact.preAngularVelocity)) {
                json.append(",\"preAngularVelocity\":")
                        .append(number(contact.preAngularVelocity));
            }
            if (!Double.isNaN(contact.postAngularVelocity)) {
                json.append(",\"postAngularVelocity\":")
                        .append(number(contact.postAngularVelocity));
            }
            json.append('}');
        }
        json.append(']');
    }

    private void appendCylinderDiagnostics(StringBuilder json, PlayerSnapshot player) {
        Vec3 capOffset = player.cylinderAxis.multiply(config.cylinderHalfLength);
        json.append(",\"cylinder\":{\"radius\":").append(number(config.cylinderRadius))
                .append(",\"halfLength\":").append(number(config.cylinderHalfLength))
                .append(",\"capA\":");
        appendVec(json, player.absolutePosition.subtract(capOffset));
        json.append(",\"capB\":");
        appendVec(json, player.absolutePosition.add(capOffset));
        json.append(",\"rimSamples\":[");
        List<Vec3> samples = CylinderCollider.rimSamples(
                player.absolutePosition, player.cylinderAxis,
                config.cylinderHalfLength, config.cylinderRadius, 24);
        for (int i = 0; i < samples.size(); i++) {
            if (i > 0) json.append(',');
            appendVec(json, samples.get(i));
        }
        json.append("]}");

        json.append(",\"visualVertices\":[");
        List<Vec3> worldVertices = vertices.worldVertices(player);
        for (int i = 0; i < worldVertices.size(); i++) {
            if (i > 0) json.append(',');
            appendVec(json, worldVertices.get(i));
        }
        json.append(']');
    }

    private static void appendVec(StringBuilder json, Vec3 value) {
        json.append('[').append(number(value.x)).append(',')
                .append(number(value.y)).append(',')
                .append(number(value.z)).append(']');
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.9f", value);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static int countFeatures(Scenario scenario) {
        if (scenario.usesCanonicalTerrain()) {
            int count = 0;
            for (TerrainSegment segment : scenario.terrainSnapshot.segments) {
                count += segment.features.size();
            }
            return count;
        }
        int count = 0;
        for (com.example.game3d.core.terrain.TerrainPatch patch : scenario.terrain.patches()) {
            count += patch.features.size();
        }
        return count;
    }

    private static List<TerrainTriangle> collisionTriangles(
            List<TerrainSegment> segments) {
        ArrayList<TerrainTriangle> result = new ArrayList<TerrainTriangle>();
        for (TerrainSegment segment : segments) {
            result.addAll(segment.collisionTriangles());
        }
        return result;
    }

    private static void appendCommitArray(
            StringBuilder json, List<TerrainCommit> commits) {
        json.append(",\"appliedTerrainCommits\":[");
        for (int i = 0; i < commits.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            TerrainCommit commit = commits.get(i);
            json.append("{\"baseRevision\":").append(commit.baseRevision)
                    .append(",\"revision\":").append(commit.revision)
                    .append(",\"committedThroughSegmentId\":")
                    .append(commit.committedThroughSegmentId)
                    .append(",\"retireBeforeSegmentId\":")
                    .append(commit.retireBeforeSegmentId);
            appendSegmentArray(json, "segmentUpserts", commit.segmentUpserts);
            json.append('}');
        }
        json.append(']');
    }

    private static void appendSegmentArray(
            StringBuilder json, String fieldName, List<TerrainSegment> segments) {
        json.append(",\"").append(fieldName).append("\":[");
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            appendSegment(json, segments.get(i));
        }
        json.append(']');
    }

    private static void appendSegment(StringBuilder json, TerrainSegment segment) {
        json.append("{\"id\":").append(segment.id)
                .append(",\"nearLeft\":");
        appendVec(json, segment.nearLeft);
        json.append(",\"nearRight\":");
        appendVec(json, segment.nearRight);
        json.append(",\"farLeft\":");
        appendVec(json, segment.farLeft);
        json.append(",\"farRight\":");
        appendVec(json, segment.farRight);
        json.append(",\"solid\":").append(segment.solid)
                .append(",\"connectedToPrevious\":")
                .append(segment.connectedToPrevious)
                .append(",\"surfaceKind\":\"")
                .append(segment.surface.kind.name())
                .append("\",\"motorSpeedMultiplier\":")
                .append(number(segment.surface.motorSpeedMultiplier))
                .append(",\"appearance\":[");
        appendAppearance(json, segment.nearLeftAppearance);
        json.append(',');
        appendAppearance(json, segment.nearRightAppearance);
        json.append(',');
        appendAppearance(json, segment.farLeftAppearance);
        json.append(',');
        appendAppearance(json, segment.farRightAppearance);
        json.append("],\"features\":[");
        for (int i = 0; i < segment.features.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            appendCanonicalFeature(json, segment.features.get(i));
        }
        json.append("]}");
    }

    private static void appendAppearance(
            StringBuilder json,
            com.example.game3d.core.terrain.TerrainVertexAppearance appearance) {
        json.append("{\"alpha\":").append(number(appearance.alpha))
                .append(",\"brightness\":")
                .append(number(appearance.brightness)).append('}');
    }

    private static void appendCanonicalFeatureArray(
            StringBuilder json, List<TerrainSegment> segments) {
        json.append(",\"terrainFeatures\":[");
        boolean first = true;
        for (TerrainSegment segment : segments) {
            for (TerrainFeatureSpec feature : segment.features) {
                if (!first) {
                    json.append(',');
                }
                appendCanonicalFeature(json, feature);
                first = false;
            }
        }
        json.append(']');
    }

    private static void appendCanonicalFeature(
            StringBuilder json, TerrainFeatureSpec feature) {
        json.append("{\"id\":").append(feature.id)
                .append(",\"ownerSegmentId\":")
                .append(feature.ownerSegmentId);
        if (feature instanceof TerrainFeatureSpec.Spike) {
            TerrainFeatureSpec.Spike spike = (TerrainFeatureSpec.Spike) feature;
            json.append(",\"kind\":\"SPIKE\",\"center\":");
            appendVec(json, spike.collisionBaseCenter);
            json.append(",\"radius\":").append(number(spike.collisionRadius))
                    .append(",\"height\":").append(number(spike.collisionHeight))
                    .append(",\"apex\":");
            appendVec(json, spike.apex);
        } else if (feature instanceof TerrainFeatureSpec.AirJumpCollectible) {
            TerrainFeatureSpec.AirJumpCollectible collectible =
                    (TerrainFeatureSpec.AirJumpCollectible) feature;
            json.append(",\"kind\":\"FEATHER\",\"center\":");
            appendVec(json, collectible.center);
            json.append(",\"triggerRadius\":")
                    .append(number(collectible.triggerRadius))
                    .append(",\"visualKind\":\"")
                    .append(escape(collectible.visualKind)).append('"');
        } else if (feature instanceof TerrainFeatureSpec.Portal) {
            TerrainFeatureSpec.Portal portal =
                    (TerrainFeatureSpec.Portal) feature;
            json.append(",\"kind\":\"PORTAL\",\"role\":\"")
                    .append(portal.role.name())
                    .append("\",\"pairId\":").append(portal.pairId)
                    .append(",\"center\":");
            appendVec(json, portal.center);
            json.append(",\"forward\":");
            appendVec(json, portal.forward);
            json.append(",\"up\":");
            appendVec(json, portal.up);
            json.append(",\"width\":").append(number(portal.width))
                    .append(",\"height\":").append(number(portal.height))
                    .append(",\"visualStyleId\":\"")
                    .append(escape(portal.visualStyleId)).append('"');
        }
        json.append('}');
    }
}
