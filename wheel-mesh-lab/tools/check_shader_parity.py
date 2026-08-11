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
        rf"\b{re.escape(name)}\s*=\s*([0-9]+(?:\.[0-9]+)?)(?:[fF])?\s*;", source
    )
    if match is None:
        raise RuntimeError(f"could not find numeric constant {name}")
    return float(match.group(1))


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
    lab_main = (LAB_ROOT / "src/main.cpp").read_text(encoding="utf-8")
    constant_pairs = [
        ("BLOOM_THRESHOLD", "kBloomThreshold"),
        ("BLOOM_INTENSITY", "kBloomIntensity"),
        ("BLUR_ITERATIONS", "kBloomIterations"),
        ("DOWNSAMPLE", "kBloomDownsample"),
        ("BLUR_TEXEL_STEP_SCALE", "kBloomTexelStepScale"),
    ]
    for android_name, lab_name in constant_pairs:
        android_value = extract_numeric_constant(android_bloom, android_name)
        lab_value = extract_numeric_constant(lab_main, lab_name)
        if not math.isclose(android_value, lab_value, rel_tol=0.0, abs_tol=1e-7):
            raise RuntimeError(
                f"bloom constant drift: {lab_name}={lab_value}, "
                f"Android {android_name}={android_value}"
            )
        print(f"[ok] {lab_name} == {android_name} == {android_value:g}")

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
