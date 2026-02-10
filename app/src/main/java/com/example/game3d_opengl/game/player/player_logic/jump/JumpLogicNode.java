package com.example.game3d_opengl.game.player.player_logic.jump;

import static java.lang.Math.abs;

import com.example.game3d_opengl.game.logic_abstraction.StateInfoNode;
import com.example.game3d_opengl.game.player.player_character.PlayerConfig;
import com.example.game3d_opengl.game.player.player_logic.EffectsNode;
import com.example.game3d_opengl.game.player.player_logic.FrameStartPlayerState;
import com.example.game3d_opengl.game.player.player_logic.InputNode;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public final class JumpLogicNode extends StateInfoNode<JumpLogicNode.Data> {
    public enum JumpDecision {
        NONE,
        BUFFER_JUMP,
        JUMP_NOW
    }

    public static final class Data {
        public JumpDecision decision = JumpDecision.NONE;
        public boolean shouldJump;
    }

    private final InputNode input;
    private final EffectsNode.Data effects;
    private final PlayerConfig playerConfig;
    private final JumpConfig jumpConfig;
    private Data data = new Data();

    public JumpLogicNode(InputNode input, EffectsNode.Data effects,
                         PlayerConfig playerConfig, JumpConfig jumpConfig) {
        this.input = input;
        this.effects = effects;
        this.playerConfig = playerConfig;
        this.jumpConfig = jumpConfig;
    }


    @Override
    public Data getData() {
        return data;
    }

    private float cumulativeSwipeDy = 0;
    private final int maxTouchUpTimeInterruptingSwipe = 5;
    private int currTouchUpTimeInterruptingSwipe = 0;
    private final float swipeVertThresholdFactor = 0.5f;
    private boolean wasTouchUpLastFrame = true;
    private boolean hadFootingLastFrame = false;
    private boolean hasBufferedJumpPending = false;
    private int bufferedJumpPendingFrames = 0;

    public float getCumulativeSwipeDy(){
        return -cumulativeSwipeDy;
    }

    @Override
    public void calc() {

        FrameStartPlayerState in = input.getData();

        assert in != null;
        assert effects != null;

        if(in.swipeDy <= -jumpConfig.jumpContributingSwipeThresholdPx && abs(in.swipeDy) > swipeVertThresholdFactor*abs(in.swipeDx)) {
            cumulativeSwipeDy += in.swipeDy*jumpConfig.jumpSwipeCumulationRate;
        }else if(in.swipeDy >= jumpConfig.jumpCancellingSwipeThresholdPx){
            cumulativeSwipeDy += in.swipeDy*jumpConfig.jumpSwipeCancellationRate;
        }
        cumulativeSwipeDy = clamp(cumulativeSwipeDy, -jumpConfig.jumpMaxSwipe, jumpConfig.jumpMaxSwipe);

        boolean hasFooting = in.getTileBelow() != null;
        boolean hasAirJumpCharge = effects.infiniteJumps || effects.extraJumpCharges > 0;
        boolean canGroundJump = hasFooting;
        boolean canAirJump = !hasFooting && hasAirJumpCharge;
        boolean canJump = canGroundJump || canAirJump;
        boolean swipeCharged = cumulativeSwipeDy <= -jumpConfig.jumpSwipeThresholdPx;
        boolean wantsJump = in.isTouchUp && !wasTouchUpLastFrame && swipeCharged;
        boolean justLanded = hasFooting && !hadFootingLastFrame;
        boolean wasFalling = in.getFallSpeed() > 0f;
        boolean touchHeld = !in.isTouchUp;
        boolean groundSoon = isGroundContactLikelySoon(in);
        boolean lethalLandingSoon = isLikelyLethalLandingSoon(in);
        boolean holdBackAirJump = wasFalling && groundSoon && !lethalLandingSoon;
        JumpDecision decision = JumpDecision.NONE;

        if (hasBufferedJumpPending) {
            bufferedJumpPendingFrames += 1;
            if (justLanded && canGroundJump) {
                decision = JumpDecision.JUMP_NOW;
                clearBufferedJumpState();
            } else if (canAirJump && !holdBackAirJump) {
                decision = JumpDecision.JUMP_NOW;
                clearBufferedJumpState();
            } else if (bufferedJumpPendingFrames >= jumpConfig.jumpAirReleaseBufferFrames) {
                decision = canJump ? JumpDecision.JUMP_NOW : JumpDecision.NONE;
                clearBufferedJumpState();
            } else {
                decision = JumpDecision.BUFFER_JUMP;
            }
        } else {
            if (wantsJump) {
                if (canAirJump && holdBackAirJump) {
                    hasBufferedJumpPending = true;
                    bufferedJumpPendingFrames = 0;
                    decision = JumpDecision.BUFFER_JUMP;
                } else if (canJump) {
                    decision = JumpDecision.JUMP_NOW;
                } else if (!hasFooting && groundSoon) {
                    // No air charges: keep intent briefly so landing can trigger instant jump.
                    hasBufferedJumpPending = true;
                    bufferedJumpPendingFrames = 0;
                    decision = JumpDecision.BUFFER_JUMP;
                }
            } else {
                boolean bufferedTouchHeldJump = justLanded && swipeCharged && touchHeld;
                if (canGroundJump && bufferedTouchHeldJump) {
                    decision = JumpDecision.JUMP_NOW;
                }
            }
        }

        data.decision = decision;
        data.shouldJump = (decision == JumpDecision.JUMP_NOW);

        if (data.decision == JumpDecision.BUFFER_JUMP) {
            // Keep charge while buffering so the eventual decision can still use it.
            currTouchUpTimeInterruptingSwipe = 0;
        } else if (data.shouldJump) {
            currTouchUpTimeInterruptingSwipe = 0;
            cumulativeSwipeDy = 0;
        } else if(in.isTouchUp && !hasBufferedJumpPending){
            currTouchUpTimeInterruptingSwipe += 1;
            if(currTouchUpTimeInterruptingSwipe == maxTouchUpTimeInterruptingSwipe || cumulativeSwipeDy > -jumpConfig.jumpSwipeThresholdPx){
                cumulativeSwipeDy = 0;
                currTouchUpTimeInterruptingSwipe = 0;
            }
        }else{
            currTouchUpTimeInterruptingSwipe = 0;
        }

        wasTouchUpLastFrame = in.isTouchUp;
        hadFootingLastFrame = hasFooting;
    }

    private static float clamp(float v, float minVal, float maxVal) {
        return Math.max(minVal, Math.min(maxVal, v));
    }

    private boolean isGroundContactLikelySoon(FrameStartPlayerState in) {
        if (in.getTileBelow() != null) return true;
        if (in.getFallSpeed() <= 0f) return false;
        float nearestGroundDist = in.getNearestGroundDistance();
        if (Float.isInfinite(nearestGroundDist)) return false;

        int lookAheadFrames = Math.max(1, jumpConfig.jumpAirReleaseBufferFrames);
        float predictedDownwardTravel = estimateDownwardTravel(in, lookAheadFrames);
        float reachableDist = (playerConfig.playerHeight + predictedDownwardTravel)
                * playerConfig.fallCollisionSafetyMultiplier;
        return nearestGroundDist <= reachableDist;
    }

    private float estimateDownwardTravel(FrameStartPlayerState in, int frames) {
        if (frames <= 0) return 0f;
        float dt = Math.max(0f, in.dtMillis);
        if (dt <= 0f) return 0f;

        float downwardVel = Math.max(0f, -in.getLastMove().y) + in.getFallSpeed();
        float travel = 0f;
        for (int i = 0; i < frames; ++i) {
            downwardVel += playerConfig.fallAcceleration;
            travel += downwardVel * dt;
        }
        return travel;
    }

    private boolean isLikelyLethalLandingSoon(FrameStartPlayerState in) {
        if (in.position == null) return false;
        int spikeCount = in.getNearbyDeathSpikeCount();
        if (spikeCount <= 0) return false;

        float dt = Math.max(0f, in.dtMillis);
        if (dt <= 0f) return false;
        int lookAheadFrames = Math.max(1, jumpConfig.jumpAirReleaseBufferFrames);

        Vector3D dir = in.getMoveDir();
        Vector3D horizontalVel = dir != null ? dir.withLen(playerConfig.playerSpeed) : null;
        float vx = horizontalVel != null ? horizontalVel.x : 0f;
        float vz = horizontalVel != null ? horizontalVel.z : 0f;

        float px = in.position.x;
        float py = in.position.y;
        float pz = in.position.z;
        float downwardVel = Math.max(0f, -in.getLastMove().y) + in.getFallSpeed();

        float hazardRadius = Math.max(playerConfig.playerWidth * 1.6f, 0.2f);
        float hazardRadiusSq = hazardRadius * hazardRadius;
        float hazardHeightTolerance = Math.max(playerConfig.playerHeight, 0.2f);

        for (int frame = 0; frame < lookAheadFrames; ++frame) {
            downwardVel += playerConfig.fallAcceleration;
            px += vx * dt;
            py -= downwardVel * dt;
            pz += vz * dt;
            for (int i = 0; i < spikeCount; ++i) {
                float dx = px - in.getNearbyDeathSpikeX(i);
                float dz = pz - in.getNearbyDeathSpikeZ(i);
                if (dx * dx + dz * dz > hazardRadiusSq) continue;
                float dy = abs(py - in.getNearbyDeathSpikeY(i));
                if (dy <= hazardHeightTolerance) {
                    return true;
                }
            }
        }
        return false;
    }

    private void clearBufferedJumpState() {
        hasBufferedJumpPending = false;
        bufferedJumpPendingFrames = 0;
    }
}

