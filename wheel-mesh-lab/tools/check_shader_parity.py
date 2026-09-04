#!/usr/bin/env python3
"""Fail when the mesh lab's gameplay shader copies drift from Android sources."""

from __future__ import annotations

import json
import math
import re
import difflib
from pathlib import Path


LAB_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = LAB_ROOT.parent


def extract_java_string(path: Path, variable: str) -> str:
    source = path.read_text(encoding="utf-8")
    declaration = re.search(
        rf"(?:private\s+static\s+final\s+)?String\s+{re.escape(variable)}\s*=",
        source,
    )
    if declaration is None:
        raise RuntimeError(f"could not find Java string {variable} in {path}")
    start = declaration.end()
    in_string = False
    escaped = False
    end = None
    for index in range(start, len(source)):
        character = source[index]
        if in_string:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == '"':
                in_string = False
        elif character == '"':
            in_string = True
        elif character == ";":
            end = index
            break
    if end is None:
        raise RuntimeError(f"unterminated Java string assignment {variable} in {path}")
    tokens = re.findall(r'"(?:\\.|[^"\\])*"', source[start:end])
    if not tokens:
        raise RuntimeError(f"Java string {variable} in {path} has no literals")
    return "".join(json.loads(token) for token in tokens)


def canonical_shader(source: str) -> str:
    # A source file conventionally ends in LF while two embedded Java shaders do
    # not. Preserve every meaningful line and ignore only trailing file whitespace.
    return "\n".join(line.rstrip() for line in source.rstrip().splitlines())


def assert_shader_matches(java_path: Path, variable: str, shader_path: Path) -> None:
    android_source = canonical_shader(extract_java_string(java_path, variable))
    lab_source = canonical_shader(shader_path.read_text(encoding="utf-8"))
    if android_source != lab_source:
        difference = "\n".join(difflib.unified_diff(
            android_source.splitlines(),
            lab_source.splitlines(),
            fromfile=f"{java_path.name}::{variable}",
            tofile=shader_path.name,
            lineterm="",
        ))
        raise RuntimeError(
            f"shader drift: {shader_path.relative_to(REPO_ROOT)} no longer matches "
            f"{java_path.relative_to(REPO_ROOT)}::{variable}\n{difference}"
        )
    print(f"[ok] {shader_path.name} == {java_path.name}::{variable}")


def extract_numeric_constant(source: str, name: str) -> float:
    match = re.search(
        rf"\b{re.escape(name)}\s*=\s*"
        rf"([0-9]+(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)(?:[fF])?\s*;",
        source,
    )
    if match is None:
        raise RuntimeError(f"could not find numeric constant {name}")
    return float(match.group(1))


def extract_first_numeric_constant(
    source: str,
    names: str | tuple[str, ...],
) -> tuple[str, float]:
    candidates = (names,) if isinstance(names, str) else names
    for name in candidates:
        try:
            return name, extract_numeric_constant(source, name)
        except RuntimeError:
            pass
    raise RuntimeError(
        "could not find any numeric constant from: " + ", ".join(candidates)
    )


def extract_product_constant(source: str, name: str) -> float:
    match = re.search(rf"\b{re.escape(name)}\s*=\s*([^;]+);", source)
    if match is None:
        raise RuntimeError(f"could not find arithmetic constant {name}")
    expression = match.group(1).replace("f", "").replace("F", "")
    if re.fullmatch(r"[0-9.\s*]+", expression) is None:
        raise RuntimeError(f"unsupported arithmetic expression for {name}: {expression!r}")
    factors = [float(value) for value in expression.split("*")]
    return math.prod(factors)


def main() -> None:
    infill_dir = REPO_ROOT / (
        "app/src/main/java/com/example/game3d_opengl/rendering/infill"
    )
    bloom_java = REPO_ROOT / (
        "app/src/main/java/com/example/game3d_opengl/game/stage/stages/main/"
        "BloomPostProcessor.java"
    )
    shaders = LAB_ROOT / "shaders"

    mappings = [
        (infill_dir / "FlatLitShaderPair.java", "vs", shaders / "flat_lit.vert"),
        (infill_dir / "FlatLitShaderPair.java", "fs", shaders / "flat_lit.frag"),
        (infill_dir / "InfillShaderPair.java", "vs", shaders / "smooth_lit.vert"),
        (infill_dir / "InfillShaderPair.java", "fs", shaders / "smooth_lit.frag"),
        (bloom_java, "VS_FULLSCREEN", shaders / "fullscreen.vert"),
        (bloom_java, "FS_PREFILTER", shaders / "bloom_prefilter.frag"),
        (bloom_java, "FS_BLUR", shaders / "bloom_blur.frag"),
        (bloom_java, "FS_COMPOSITE", shaders / "bloom_composite.frag"),
    ]
    for java_path, variable, shader_path in mappings:
        assert_shader_matches(java_path, variable, shader_path)

    android_bloom = bloom_java.read_text(encoding="utf-8")
    bloom_config_java = REPO_ROOT / (
        "app/src/main/java/com/example/game3d_opengl/rendering/BloomConfig.java"
    )
    android_bloom_config = bloom_config_java.read_text(encoding="utf-8")
    player_java = REPO_ROOT / (
        "app/src/main/java/com/example/game3d_opengl/game/player/player_character/"
        "Player.java"
    )
    sampling_java = player_java.with_name("WheelTemporalSamplingPlanner.java")
    player_assets_java = player_java.with_name("PlayerAssets.java")
    android_player = player_java.read_text(encoding="utf-8")
    android_sampling = sampling_java.read_text(encoding="utf-8")
    android_player_assets = player_assets_java.read_text(encoding="utf-8")
    lab_main = (LAB_ROOT / "src/main.cpp").read_text(encoding="utf-8")
    lab_models = (LAB_ROOT / "src/wheel_models.hpp").read_text(encoding="utf-8")
    constant_pairs = [
        (android_bloom_config, "BRIGHT_THRESHOLD", "kBloomThreshold"),
        (android_bloom_config, "COMPOSITE_INTENSITY", "kBloomIntensity"),
        (android_bloom, "BLUR_ITERATIONS", "kBloomIterations"),
        (android_bloom, "DOWNSAMPLE", "kBloomDownsample"),
        (android_bloom, "BLUR_TEXEL_STEP_SCALE", "kBloomTexelStepScale"),
        (android_player, "TEMPORAL_SHUTTER_FRAME_FRACTION",
         "kTemporalShutterFrameFraction"),
        (android_player, "TEMPORAL_BLEND_START_PIXELS",
         "kTemporalBlendStartPixels"),
        (android_player, "TEMPORAL_BLEND_FULL_PIXELS",
         "kTemporalBlendFullPixels"),
        (android_player, "TEMPORAL_ACTIVATION_EPSILON",
         "kTemporalActivationEpsilon"),
        (android_sampling, "MAX_TEMPORAL_SAMPLES", "kMaxTemporalSamples"),
        (android_sampling, "TARGET_SAMPLE_SPACING_PIXELS",
         "kTargetSampleSpacingPixels"),
        (
            android_sampling,
            (
                "BAND_BLEND_START_GROOVE_CYCLES_PER_FRAME",
                "ALIAS_SAFE_BAND_START_GROOVE_CYCLES_PER_FRAME",
                "BAND_BLEND_START_CYCLES_PER_FRAME",
            ),
            (
                "kAliasSafeBandStartGrooveCyclesPerFrame",
                "kBandStartGrooveCyclesPerFrame",
            ),
        ),
        (
            android_sampling,
            (
                "BAND_BLEND_END_GROOVE_CYCLES_PER_FRAME",
                "ALIAS_SAFE_BAND_END_GROOVE_CYCLES_PER_FRAME",
                "BAND_BLEND_END_CYCLES_PER_FRAME",
            ),
            (
                "kAliasSafeBandEndGrooveCyclesPerFrame",
                "kBandEndGrooveCyclesPerFrame",
            ),
        ),
        (android_sampling, "GROOVE_COUNT", "kMintChevronCount", lab_models),
        (android_player_assets, "NEON_BRIGHT_CHANNEL", "kNeonBrightChannel"),
        (android_player_assets, "NEON_DARK_CHANNEL", "kNeonDarkChannel"),
        (android_player_assets, "NEON_SATURATION_GAIN", "kNeonSaturationGain"),
        (
            android_player_assets,
            "MINT_MOTION_BAND_DUTY_CYCLE",
            "kMintMotionBandCanonicalDutyCycle",
            lab_models,
        ),
    ]
    for pair in constant_pairs:
        android_source, android_names, lab_names, *lab_source_override = pair
        lab_source = lab_source_override[0] if lab_source_override else lab_main
        android_name, android_value = extract_first_numeric_constant(
            android_source, android_names
        )
        lab_name, lab_value = extract_first_numeric_constant(lab_source, lab_names)
        if not math.isclose(android_value, lab_value, rel_tol=0.0, abs_tol=1e-7):
            raise RuntimeError(
                f"renderer constant drift: {lab_name}={lab_value}, "
                f"Android {android_name}={android_value}"
            )
        print(f"[ok] {lab_name} == {android_name} == {android_value:g}")

    motion_java = player_java.with_name("WheelMotionGlowRenderer.java")
    android_motion = motion_java.read_text(encoding="utf-8")
    android_resolve = extract_java_string(
        motion_java, "RESOLVE_FRAGMENT_SHADER"
    )
    android_composite = extract_java_string(
        motion_java, "COMPOSITE_FRAGMENT_SHADER"
    )
    lab_accumulate = (shaders / "temporal_accumulate.frag").read_text(
        encoding="utf-8"
    )
    lab_direct = (shaders / "direct_emission.frag").read_text(encoding="utf-8")
    lab_residual = (shaders / "temporal_bloom_residual.frag").read_text(
        encoding="utf-8"
    )

    semantic_checks = [
        (android_player, "Android temporal activation", [
            "wheelTemporalBlend(plan) > TEMPORAL_ACTIVATION_EPSILON",
            "shouldUseTemporalExposure(plan)",
            "preparedMintSharpScale = 0f;",
            "preparedWheelCoreIntensity = 1f;",
            "preparedWheelBloomCorrectionBlend = (float) wheelBloomCorrectionBlend(plan);",
            "return Math.max( wheelBloomCorrectionBlend(plan), plan.continuousBandBlend());",
            "return smoothStep( TEMPORAL_BLEND_START_PIXELS, TEMPORAL_BLEND_FULL_PIXELS, plan.projectedSweepPixels());",
        ]),
        (android_motion, "Android premultiplied source-over state", [
            "GLES20.GL_ONE_MINUS_SRC_ALPHA",
            "alphaComposite ? clamp01(intensity) : 0f",
        ]),
        (android_resolve, "Android normalized weighted exposure", [
            "exposure += texture(uAtlas, atlasUV) * uWeights[i] * visible;",
            "fragColor = exposure;",
        ]),
        (android_composite, "Android coverage/residual composite shader", [
            "exposure.rgb * uIntensity",
            "exposure.a * uAlphaIntensity",
            "ordinary *= 0.25;",
            "decodedExposure += texture(uSource",
            "decodedExposure *= 0.25 * uIntensity;",
            "vec3 target = decodedExposure * uEmissionBrightFactor;",
            "vec3 residual = max(target - ordinary, vec3(0.0));",
            "residual * uBloomCorrectionBlend",
        ]),
        (lab_main, "desktop sharp-groove omission/source-over", [
            "plan_.coreIntensity = 1.0F;",
            "drawSceneAtPhase(plan.centerPhaseRadians, 0.0F);",
            "plan.mode == TemporalMode::Reference",
            "aliasSafe ? 0.0F : plan.centerPhaseRadians",
            "temporal_.accumulateSample(plan.motionBandEnergyWeight);",
            "GL_ONE_MINUS_SRC_ALPHA",
            "plan_.bloomCorrectionBlend = smoothStep(",
            "plan_.temporalBlend = std::max( plan_.bloomCorrectionBlend, plan_.bandBlend);",
            "lastTemporalPlan_.bloomCorrectionBlend",
        ]),
        (lab_accumulate, "desktop normalized weighted exposure", [
            "texture(uSampleTex, vUV) * uSampleWeight",
        ]),
        (lab_direct, "desktop premultiplied intensity", [
            "texture(uEmissionTex, vUV) * uIntensity",
        ]),
        (lab_residual, "desktop per-pixel bloom residual", [
            "ordinary *= 0.25;",
            "decodedExposure += texture(uEmissionTex",
            "decodedExposure *= 0.25;",
            "vec3 target = decodedExposure * uEmissionBrightFactor;",
            "vec3 residual = max(target - ordinary, vec3(0.0));",
            "residual * uBloomCorrectionBlend",
        ]),
    ]
    for source, label, tokens in semantic_checks:
        normalized_source = re.sub(r"\s+", " ", source)
        missing = [
            token for token in tokens
            if re.sub(r"\s+", " ", token) not in normalized_source
        ]
        if missing:
            raise RuntimeError(f"{label} drift; missing tokens: {missing}")
        print(f"[ok] {label}")

    correction_method = re.search(
        r"static double wheelBloomCorrectionBlend\([^)]*\)\s*\{(.*?)\n\s*\}",
        android_player,
        re.DOTALL,
    )
    if correction_method is None:
        raise RuntimeError("could not locate Android wheelBloomCorrectionBlend")
    if "continuousBandBlend" in correction_method.group(1):
        raise RuntimeError(
            "Android bloom correction incorrectly depends on the band blend"
        )
    print("[ok] bloom correction is projected-only (band blend excluded)")

    forbidden_global_compensation = [
        "notionalSharpScale",
        "temporalBloomIntensityForSharpScale",
    ]
    for token in forbidden_global_compensation:
        if token in android_player or token in lab_main:
            raise RuntimeError(
                f"obsolete global temporal-bloom compensation returned: {token}"
            )
    print("[ok] no notional-sharp/global temporal-bloom scalar")

    physics_java = REPO_ROOT / (
        "game-core/src/main/java/com/example/game3d/core/simulation/PhysicsConfig.java"
    )
    android_physics = physics_java.read_text(encoding="utf-8")
    physics_pairs = [
        ("DEFAULT_CYLINDER_RADIUS", "kGameplayCylinderRadius"),
        ("DEFAULT_CYLINDER_HALF_LENGTH", "kGameplayCylinderHalfLength"),
    ]
    for android_name, lab_name in physics_pairs:
        android_value = extract_product_constant(android_physics, android_name)
        lab_value = extract_numeric_constant(lab_main, lab_name)
        if not math.isclose(android_value, lab_value, rel_tol=0.0, abs_tol=1e-7):
            raise RuntimeError(
                f"physics constant drift: {lab_name}={lab_value}, "
                f"game-core {android_name}={android_value}"
            )
        print(f"[ok] {lab_name} == {android_name} == {android_value:g}")

if __name__ == "__main__":
    main()
