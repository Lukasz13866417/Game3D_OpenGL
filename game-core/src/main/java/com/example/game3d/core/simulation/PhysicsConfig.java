package com.example.game3d.core.simulation;

/** Immutable physical units and tuning for the shared simulation. */
public strictfp final class PhysicsConfig {
    private static final double LEGACY_JUMP_FORWARD_SPEED_FACTOR = 0.125;
    public static final int FIXED_HZ = 120;
    public static final double FIXED_DT_SECONDS = 1.0 / FIXED_HZ;
    public static final long FIXED_DT_NANOS = 1_000_000_000L / FIXED_HZ;
    /** Minimum time after one jump before another jump may execute. */
    public static final long DEFAULT_JUMP_COOLDOWN_NANOS = 100_000_000L;
    public static final double DEFAULT_GRAVITY = 70.0;
    public static final double DEFAULT_JUMP_HEIGHT_MULTIPLIER = 1.15;
    public static final double DEFAULT_BOUNCE_HEIGHT_MULTIPLIER = 1.33;
    /**
     * Launch speed that raises the ideal ballistic apex by 15 percent.
     * Height is proportional to speed squared, hence the square-root conversion.
     */
    public static final double DEFAULT_JUMP_SPEED =
            20.5 * StrictMath.sqrt(DEFAULT_JUMP_HEIGHT_MULTIPLIER);
    /** Preserve the old horizontal jump impulse while changing only vertical jump height. */
    public static final double DEFAULT_JUMP_FORWARD_BOOST_SPEED =
            20.5 * LEGACY_JUMP_FORWARD_SPEED_FACTOR;
    /** Restitution that raises rebound height by 33 percent at a fixed impact speed. */
    public static final double DEFAULT_RESTITUTION =
            0.60 * StrictMath.sqrt(DEFAULT_BOUNCE_HEIGHT_MULTIPLIER);
    public static final double DEFAULT_BOUNCE_SPEED_THRESHOLD = 7.0 * 1.50;
    public static final double DEFAULT_CYLINDER_RADIUS =
            0.120 * 3.62 * 1.05 * 0.5;
    public static final double DEFAULT_CYLINDER_HALF_LENGTH =
            0.120 * 1.05 * 0.5;
    /** Gesture-charge units gained per upward swipe of one screen height. */
    public static final double DEFAULT_SWIPE_CHARGE_PER_SCREEN_HEIGHT = 6.5;
    /** Maximum physical sideways excursion of one jump-charge gesture phase. */
    public static final double DEFAULT_MAX_JUMP_CHARGE_X_SCREEN_HEIGHTS = 0.06;
    /** Maximum physical horizontal excursion per unit of upward travel. */
    public static final double DEFAULT_MAX_JUMP_CHARGE_X_TO_Y_RATIO = 1.20;
    /**
     * Minimum normalized gesture charge that makes a jump eligible.
     *
     * <p>The value sits just below one fifth so the HUD milestone remains easy to see. With the
     * default touch sensitivity, reaching it takes a little over one twentieth of the screen.</p>
     */
    public static final double DEFAULT_JUMP_CHARGE_THRESHOLD = 0.9 / 5.0;

    public final double cylinderRadius;
    public final double cylinderHalfLength;
    public final double mass;
    public final double gravity;
    public final double jumpSpeed;
    public final double jumpForwardBoostSpeed;
    public final double cruisingSpeed;
    public final double motorAcceleration;
    public final double groundFrictionAcceleration;
    public final double airAngularAcceleration;
    public final double restitution;
    public final double bounceSpeedThreshold;
    public final double supportSlopeCosine;
    /** Distance within which a non-separating contact may be retained as support. */
    public final double contactOffset;
    /** Desired visible gap between matching collision and rendered geometry. */
    public final double restOffset;
    /** Accuracy target for swept time-of-impact queries. */
    public final double toiTolerance;
    /** Legacy alias retained for the discrete start-overlap recovery path. */
    public final double collisionSlop;
    public final int contactIterations;
    /** Gesture-charge units gained per upward screen-height of normalized input. */
    public final double swipeChargePerScreenHeight;
    public final double swipeCancelPerScreenHeight;
    /** Per-packet vertical dominance retained for downward impact-brake intent. */
    public final double swipeVerticalDominance;
    /** Absolute physical horizontal-excursion limit for charging a jump. */
    public final double maxJumpChargeXScreenHeights;
    /** Physical horizontal-excursion/upward-travel limit for charging a jump. */
    public final double maxJumpChargeXToYRatio;
    /** Eligibility milestone in the normalized {@code [0, 1]} gesture-charge domain. */
    public final double jumpChargeThreshold;
    public final double facingRadiansPerScreenHeight;
    public final long airborneChargeFreezeNanos;
    public final long airborneChargeDecayNanos;
    public final long jumpCooldownNanos;
    /**
     * Maximum no-jump look-ahead used to arm a landing jump.
     *
     * <p>At 120 Hz the default 20 ticks are approximately one sixth of a second. A released
     * airborne gesture is never retained merely because support exists somewhere beyond this
     * horizon.</p>
     */
    public final int landingJumpBufferTicks;
    /** Legacy alias for {@link #landingJumpBufferTicks}. */
    public final int forecastTicks;
    public final double deathY;

    public PhysicsConfig() {
        this(
                DEFAULT_CYLINDER_RADIUS,
                DEFAULT_CYLINDER_HALF_LENGTH,
                1.0,
                DEFAULT_GRAVITY,
                DEFAULT_JUMP_SPEED,
                DEFAULT_JUMP_FORWARD_BOOST_SPEED,
                32.0,
                90.0,
                105.0,
                180.0,
                DEFAULT_RESTITUTION,
                DEFAULT_BOUNCE_SPEED_THRESHOLD,
                Math.cos(Math.toRadians(50.0)),
                0.0005,
                4,
                DEFAULT_SWIPE_CHARGE_PER_SCREEN_HEIGHT,
                4.0,
                0.5,
                DEFAULT_MAX_JUMP_CHARGE_X_SCREEN_HEIGHTS,
                DEFAULT_MAX_JUMP_CHARGE_X_TO_Y_RATIO,
                DEFAULT_JUMP_CHARGE_THRESHOLD,
                Math.toRadians(240.0),
                200_000_000L,
                400_000_000L,
                FIXED_HZ / 6,
                -20.0);
    }

    /**
     * Compatibility constructor preserving the former relationship between vertical launch
     * speed and horizontal boost for explicitly constructed non-default configurations.
     */
    public PhysicsConfig(double cylinderRadius, double cylinderHalfLength, double mass,
                         double gravity, double jumpSpeed, double cruisingSpeed,
                         double motorAcceleration, double groundFrictionAcceleration,
                         double airAngularAcceleration, double restitution,
                         double bounceSpeedThreshold, double supportSlopeCosine,
                         double collisionSlop, int contactIterations,
                         double swipeChargePerScreenHeight,
                         double swipeCancelPerScreenHeight,
                         double swipeVerticalDominance,
                         double maxJumpChargeXScreenHeights,
                         double maxJumpChargeXToYRatio,
                         double jumpChargeThreshold,
                         double facingRadiansPerScreenHeight,
                         long airborneChargeFreezeNanos,
                         long airborneChargeDecayNanos,
                         int forecastTicks, double deathY) {
        this(cylinderRadius, cylinderHalfLength, mass,
                gravity, jumpSpeed,
                jumpSpeed * LEGACY_JUMP_FORWARD_SPEED_FACTOR, cruisingSpeed,
                motorAcceleration, groundFrictionAcceleration,
                airAngularAcceleration, restitution,
                bounceSpeedThreshold, supportSlopeCosine,
                collisionSlop, contactIterations,
                swipeChargePerScreenHeight,
                swipeCancelPerScreenHeight,
                swipeVerticalDominance,
                maxJumpChargeXScreenHeights,
                maxJumpChargeXToYRatio,
                jumpChargeThreshold,
                facingRadiansPerScreenHeight,
                airborneChargeFreezeNanos,
                airborneChargeDecayNanos,
                forecastTicks, deathY);
    }

    public PhysicsConfig(double cylinderRadius, double cylinderHalfLength, double mass,
                         double gravity, double jumpSpeed, double jumpForwardBoostSpeed,
                         double cruisingSpeed,
                         double motorAcceleration, double groundFrictionAcceleration,
                         double airAngularAcceleration, double restitution,
                         double bounceSpeedThreshold, double supportSlopeCosine,
                         double collisionSlop, int contactIterations,
                         double swipeChargePerScreenHeight,
                         double swipeCancelPerScreenHeight,
                         double swipeVerticalDominance,
                         double maxJumpChargeXScreenHeights,
                         double maxJumpChargeXToYRatio,
                         double jumpChargeThreshold,
                         double facingRadiansPerScreenHeight,
                         long airborneChargeFreezeNanos,
                         long airborneChargeDecayNanos,
                         int forecastTicks, double deathY) {
        this.cylinderRadius = positive(cylinderRadius, "cylinderRadius");
        this.cylinderHalfLength = positive(cylinderHalfLength, "cylinderHalfLength");
        this.mass = positive(mass, "mass");
        this.gravity = positive(gravity, "gravity");
        this.jumpSpeed = positive(jumpSpeed, "jumpSpeed");
        this.jumpForwardBoostSpeed =
                positive(jumpForwardBoostSpeed, "jumpForwardBoostSpeed");
        this.cruisingSpeed = positive(cruisingSpeed, "cruisingSpeed");
        this.motorAcceleration = positive(motorAcceleration, "motorAcceleration");
        this.groundFrictionAcceleration =
                positive(groundFrictionAcceleration, "groundFrictionAcceleration");
        this.airAngularAcceleration = positive(airAngularAcceleration, "airAngularAcceleration");
        this.restitution = between(restitution, 0.0, 1.0, "restitution");
        this.bounceSpeedThreshold = positive(bounceSpeedThreshold, "bounceSpeedThreshold");
        this.supportSlopeCosine = between(supportSlopeCosine, 0.0, 1.0, "supportSlopeCosine");
        this.contactOffset = positive(collisionSlop, "collisionSlop");
        this.restOffset = 0.0;
        this.toiTolerance = Math.max(1.0e-9, cylinderRadius * 1.0e-7);
        this.collisionSlop = contactOffset;
        if (contactIterations < 1) {
            throw new IllegalArgumentException("contactIterations must be positive");
        }
        this.contactIterations = contactIterations;
        this.swipeChargePerScreenHeight =
                positive(swipeChargePerScreenHeight, "swipeChargePerScreenHeight");
        this.swipeCancelPerScreenHeight =
                positive(swipeCancelPerScreenHeight, "swipeCancelPerScreenHeight");
        this.swipeVerticalDominance =
                positive(swipeVerticalDominance, "swipeVerticalDominance");
        this.maxJumpChargeXScreenHeights = positive(
                maxJumpChargeXScreenHeights, "maxJumpChargeXScreenHeights");
        this.maxJumpChargeXToYRatio = positive(
                maxJumpChargeXToYRatio, "maxJumpChargeXToYRatio");
        this.jumpChargeThreshold = between(jumpChargeThreshold, 0.0, 1.0,
                "jumpChargeThreshold");
        this.facingRadiansPerScreenHeight =
                positive(facingRadiansPerScreenHeight, "facingRadiansPerScreenHeight");
        if (airborneChargeFreezeNanos < 0L || airborneChargeDecayNanos <= 0L) {
            throw new IllegalArgumentException("Charge timing must be valid");
        }
        this.airborneChargeFreezeNanos = airborneChargeFreezeNanos;
        this.airborneChargeDecayNanos = airborneChargeDecayNanos;
        this.jumpCooldownNanos = DEFAULT_JUMP_COOLDOWN_NANOS;
        if (forecastTicks < 1) {
            throw new IllegalArgumentException("forecastTicks must be positive");
        }
        this.landingJumpBufferTicks = forecastTicks;
        this.forecastTicks = landingJumpBufferTicks;
        this.deathY = deathY;
    }

    private static double positive(double value, String name) {
        if (!(value > 0.0) || Double.isInfinite(value) || Double.isNaN(value)) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
        return value;
    }

    private static double between(double value, double min, double max, String name) {
        if (value < min || value > max || Double.isNaN(value)) {
            throw new IllegalArgumentException(name + " outside valid range");
        }
        return value;
    }
}
