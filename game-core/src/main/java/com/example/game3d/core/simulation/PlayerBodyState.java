package com.example.game3d.core.simulation;

import com.example.game3d.core.math.Vec3;

import java.util.HashSet;
import java.util.Set;

/** Mutable state owned exclusively by one SimulationEngine. */
strictfp final class PlayerBodyState {
    long tick;
    long timeNanos;
    Vec3 position;
    Vec3 worldOrigin;
    Vec3 velocity;
    double yawRadians;
    double axleRadians;
    double axleDeltaRadians;
    /** Legacy motor controller state retained separately from physical tire rotation. */
    double driveAngularVelocity;
    /** Signed right-hand angular velocity about {@link #axis()}; forward rolling is negative. */
    double angularVelocity;
    double gestureCharge;
    /** Legacy held-gesture diagnostic mirroring accepted charge until classifier reset. */
    double gestureChargePotential;
    /** Signed physical X displacement accumulated during the current held gesture. */
    double gestureRawDeltaX;
    /** Total physical upward travel accumulated during the current held gesture. */
    double gestureRawUpwardDistance;
    /** Largest absolute physical X displacement reached during the current held gesture. */
    double gestureMaxAbsRawDeltaX;
    /** Whether the most recent upward packet was vertical enough to contribute charge. */
    boolean gestureLastSwipeChargeEligible;
    /** Simulation time of the most recent accepted held upward contribution. */
    long heldChargeLastContributionNanos = -1L;
    int airJumpCharges;
    boolean grounded;
    long supportTriangleId = -1L;
    long supportSegmentId = -1L;
    long lastSupportedSegmentId = -1L;
    Vec3 supportNormal = Vec3.ZERO;
    boolean hasSupportedAnchor;
    double lastSupportedY;
    boolean touchHeld;
    boolean gestureConsumed;
    boolean dead;
    boolean bufferedAirborneRequest;
    /** A safe, imminent landing was forecast while descending. */
    boolean landingJumpArmed;
    /** A downward swipe is still held and may absorb the next walkable impact. */
    boolean impactBrakeArmed;
    long airborneReleaseNanos = -1L;
    double airborneReleaseCharge;
    /** Exact simulation time at which another jump may execute. */
    long jumpCooldownUntilNanos;
    final Set<Long> inactiveAddonIds = new HashSet<Long>();

    PlayerBodyState(Vec3 position, Vec3 velocity, double initialAngularVelocity,
                    int initialAirJumpCharges) {
        this.position = position;
        this.worldOrigin = Vec3.ZERO;
        this.velocity = velocity;
        this.angularVelocity = initialAngularVelocity;
        this.airJumpCharges = initialAirJumpCharges;
    }

    PlayerBodyState copy() {
        PlayerBodyState copy = new PlayerBodyState(
                position, velocity, angularVelocity, airJumpCharges);
        copy.tick = tick;
        copy.timeNanos = timeNanos;
        copy.worldOrigin = worldOrigin;
        copy.yawRadians = yawRadians;
        copy.axleRadians = axleRadians;
        copy.axleDeltaRadians = axleDeltaRadians;
        copy.driveAngularVelocity = driveAngularVelocity;
        copy.angularVelocity = angularVelocity;
        copy.gestureCharge = gestureCharge;
        copy.gestureChargePotential = gestureChargePotential;
        copy.gestureRawDeltaX = gestureRawDeltaX;
        copy.gestureRawUpwardDistance = gestureRawUpwardDistance;
        copy.gestureMaxAbsRawDeltaX = gestureMaxAbsRawDeltaX;
        copy.gestureLastSwipeChargeEligible = gestureLastSwipeChargeEligible;
        copy.heldChargeLastContributionNanos = heldChargeLastContributionNanos;
        copy.grounded = grounded;
        copy.supportTriangleId = supportTriangleId;
        copy.supportSegmentId = supportSegmentId;
        copy.lastSupportedSegmentId = lastSupportedSegmentId;
        copy.supportNormal = supportNormal;
        copy.hasSupportedAnchor = hasSupportedAnchor;
        copy.lastSupportedY = lastSupportedY;
        copy.touchHeld = touchHeld;
        copy.gestureConsumed = gestureConsumed;
        copy.dead = dead;
        copy.bufferedAirborneRequest = bufferedAirborneRequest;
        copy.landingJumpArmed = landingJumpArmed;
        copy.impactBrakeArmed = impactBrakeArmed;
        copy.airborneReleaseNanos = airborneReleaseNanos;
        copy.airborneReleaseCharge = airborneReleaseCharge;
        copy.jumpCooldownUntilNanos = jumpCooldownUntilNanos;
        copy.inactiveAddonIds.addAll(inactiveAddonIds);
        return copy;
    }

    Vec3 heading() {
        return new Vec3(Math.sin(yawRadians), 0.0, -Math.cos(yawRadians));
    }

    Vec3 axis() {
        Vec3 heading = heading();
        return new Vec3(-heading.z, 0.0, heading.x);
    }
}
