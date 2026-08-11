package com.example.game3d.core.simulation;

import com.example.game3d.core.math.Vec3;

public final class PlayerSnapshot {
    public final long tick;
    public final long timeNanos;
    public final Vec3 position;
    public final Vec3 absolutePosition;
    /** Exact origin subtracted from {@link #absolutePosition} to produce {@link #position}. */
    public final Vec3 renderOrigin;
    public final Vec3 velocity;
    public final Vec3 heading;
    public final Vec3 cylinderAxis;
    public final double yawRadians;
    /** Wrapped right-hand rotation phase about {@link #cylinderAxis}. */
    public final double axleRadians;
    /** Exact signed rotation performed since the preceding fixed tick. */
    public final double axleDeltaRadians;
    /** Signed right-hand angular velocity; forward rolling is negative. */
    public final double angularVelocity;
    /** Internal motor controller rim speed retained for gameplay-trajectory compatibility. */
    public final double driveSurfaceSpeed;
    public final double gestureCharge;
    /** Charge accumulated by upward motion before applying physical path-direction guards. */
    public final double gestureChargePotential;
    /** Current physical finger displacement from this held gesture phase's X origin. */
    public final double gestureRawDeltaX;
    /** Cumulative physical upward finger travel during the held gesture phase. */
    public final double gestureRawUpwardDistance;
    /** Peak physical sideways excursion during the held gesture phase. */
    public final double gestureMaxAbsRawDeltaX;
    /** Whether both absolute-X and X-to-upward-Y guards currently permit visible charge. */
    public final boolean jumpChargePathEligible;
    public final int airJumpCharges;
    public final boolean grounded;
    public final long supportTriangleId;
    /** Current canonical support segment, or -1 while airborne. */
    public final long supportSegmentId;
    /** Last valid support segment. It remains stable while airborne. */
    public final long lastSupportedSegmentId;
    /** Selected support normal, or {@link Vec3#ZERO} while airborne. */
    public final Vec3 supportNormal;
    public final boolean touchHeld;
    /** True only while descending toward a sufficiently close, spike-safe impact. */
    public final boolean landingJumpArmed;
    /** Remaining post-jump cooldown at this snapshot's exact simulation time. */
    public final long jumpCooldownRemainingNanos;
    /** True while a held downward swipe can absorb the next walkable hard impact. */
    public final boolean impactBrakeArmed;
    public final boolean dead;
    public final long stateHash;

    PlayerSnapshot(long tick, long timeNanos, Vec3 position, Vec3 absolutePosition,
                   Vec3 renderOrigin,
                   Vec3 velocity, Vec3 heading, Vec3 cylinderAxis,
                   double yawRadians, double axleRadians, double axleDeltaRadians,
                   double angularVelocity, double driveSurfaceSpeed,
                   double gestureCharge, double gestureChargePotential,
                   double gestureRawDeltaX, double gestureRawUpwardDistance,
                   double gestureMaxAbsRawDeltaX, boolean jumpChargePathEligible,
                   int airJumpCharges, boolean grounded,
                   long supportTriangleId, long supportSegmentId,
                   long lastSupportedSegmentId, Vec3 supportNormal,
                   boolean touchHeld, boolean landingJumpArmed,
                   long jumpCooldownRemainingNanos,
                   boolean impactBrakeArmed, boolean dead, long stateHash) {
        this.tick = tick;
        this.timeNanos = timeNanos;
        this.position = position;
        this.absolutePosition = absolutePosition;
        this.renderOrigin = renderOrigin;
        this.velocity = velocity;
        this.heading = heading;
        this.cylinderAxis = cylinderAxis;
        this.yawRadians = yawRadians;
        this.axleRadians = axleRadians;
        this.axleDeltaRadians = axleDeltaRadians;
        this.angularVelocity = angularVelocity;
        this.driveSurfaceSpeed = driveSurfaceSpeed;
        this.gestureCharge = gestureCharge;
        this.gestureChargePotential = gestureChargePotential;
        this.gestureRawDeltaX = gestureRawDeltaX;
        this.gestureRawUpwardDistance = gestureRawUpwardDistance;
        this.gestureMaxAbsRawDeltaX = gestureMaxAbsRawDeltaX;
        this.jumpChargePathEligible = jumpChargePathEligible;
        this.airJumpCharges = airJumpCharges;
        this.grounded = grounded;
        this.supportTriangleId = supportTriangleId;
        this.supportSegmentId = supportSegmentId;
        this.lastSupportedSegmentId = lastSupportedSegmentId;
        this.supportNormal = supportNormal;
        this.touchHeld = touchHeld;
        this.landingJumpArmed = landingJumpArmed;
        this.jumpCooldownRemainingNanos = jumpCooldownRemainingNanos;
        this.impactBrakeArmed = impactBrakeArmed;
        this.dead = dead;
        this.stateHash = stateHash;
    }
}
