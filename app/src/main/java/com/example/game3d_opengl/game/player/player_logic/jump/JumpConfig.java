package com.example.game3d_opengl.game.player.player_logic.jump;

public class JumpConfig {
    // Swipe thresholds/config
    static final float JUMP_CONTRIBUTING_SWIPE_THRESHOLD_PX = 4f;
    static final float JUMP_CANCELLING_SWIPE_THRESHOLD_PX = 8f;
    static final float JUMP_SWIPE_THRESHOLD_PX = 25f;
    static final float JUMP_MAX_SWIPE = 150f;
    static final float JUMP_SWIPE_CUMULATION_RATE = 0.4f;
    static final float JUMP_SWIPE_CANCELLATION_RATE = 0.3f;
    static final int JUMP_AIR_RELEASE_BUFFER_FRAMES = 60;

    public final float jumpContributingSwipeThresholdPx;
    public final float jumpCancellingSwipeThresholdPx;
    public final float jumpSwipeThresholdPx;
    public final float jumpMaxSwipe;
    public final float jumpSwipeCumulationRate;
    public final float jumpSwipeCancellationRate;
    public final int jumpAirReleaseBufferFrames;

    public JumpConfig() {
        this.jumpContributingSwipeThresholdPx = JUMP_CONTRIBUTING_SWIPE_THRESHOLD_PX;
        this.jumpCancellingSwipeThresholdPx = JUMP_CANCELLING_SWIPE_THRESHOLD_PX;
        this.jumpSwipeThresholdPx = JUMP_SWIPE_THRESHOLD_PX;
        this.jumpMaxSwipe = JUMP_MAX_SWIPE;
        this.jumpSwipeCumulationRate = JUMP_SWIPE_CUMULATION_RATE;
        this.jumpSwipeCancellationRate = JUMP_SWIPE_CANCELLATION_RATE;
        this.jumpAirReleaseBufferFrames = JUMP_AIR_RELEASE_BUFFER_FRAMES;
    }
}
