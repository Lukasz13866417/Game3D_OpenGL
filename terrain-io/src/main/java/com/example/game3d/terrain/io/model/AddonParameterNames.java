package com.example.game3d.terrain.io.model;

/** Stable parameter keys shared by JSON authoring frontends and the structure interpreter. */
public final class AddonParameterNames {
    /** Half-width of a segment-normalized footprint, expressed as a fraction of track width. */
    public static final String FOOTPRINT_HALF_ACROSS = "footprintHalfAcross";
    /** Half-length of a segment-normalized footprint, expressed as a fraction of tile length. */
    public static final String FOOTPRINT_HALF_ALONG = "footprintHalfAlong";
    /** Nonzero when a placement is aligned to the segment pose instead of bilinear corners. */
    public static final String FOOTPRINT_POSE_ALIGNED = "footprintPoseAligned";
    /** Exact pose-aligned lateral fraction retained by Java-provider imports. */
    public static final String POSE_LATERAL_FRACTION = "poseLateralFraction";
    /** Exact pose-aligned half-width in world units retained by Java-provider imports. */
    public static final String POSE_HALF_ACROSS_WORLD = "poseHalfAcrossWorld";
    /** Exact pose-aligned half-length in world units retained by Java-provider imports. */
    public static final String POSE_HALF_ALONG_WORLD = "poseHalfAlongWorld";

    private AddonParameterNames() {
    }
}
