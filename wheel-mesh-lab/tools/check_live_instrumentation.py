#!/usr/bin/env python3
"""Exercise and validate the live GPU/retrace diagnostic on a real X display."""

from __future__ import annotations

import argparse
import csv
import math
from pathlib import Path
import subprocess
import sys


GPU_FIELDS = {
    "diagnostic_run_id",
    "gpu_timer_status",
    "gpu_disjoint_epoch",
    "gpu_query_latency_frames",
    "gpu_start_timestamp_ns",
    "gpu_end_timestamp_ns",
    "gpu_setup_ms",
    "gpu_scene_ms",
    "gpu_bloom_ms",
    "gpu_frame_ms",
}
SCANOUT_FIELDS = {
    "scanout_source",
    "scanout_valid",
    "scanout_counter_before",
    "scanout_counter_after",
    "scanout_counter_delta",
    "scanout_query_before_ms",
    "scanout_query_after_ms",
}
PRESENTATION_FIELDS = {
    "presentation_completion_source",
    "presentation_completion_events",
    "presentation_exact_mapping",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument("--wheel-binary", type=Path, required=True)
    parser.add_argument("--probe-binary", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    return parser.parse_args()


def run(command: list[str], label: str) -> None:
    result = subprocess.run(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if result.returncode != 0:
        if "requires GL_EXT_disjoint_timer_query" in result.stdout:
            print("SKIP: this OpenGL ES driver has no GPU timestamp queries")
            raise SystemExit(77)
        raise RuntimeError(
            f"{label} failed with exit code {result.returncode}:\n{result.stdout}"
        )


def read_trace(
    path: Path,
    *,
    minimum_rows: int = 3,
) -> tuple[list[str], list[dict[str, str]]]:
    with path.open(newline="", encoding="utf-8") as source:
        reader = csv.DictReader(source, delimiter="\t")
        fields = list(reader.fieldnames or ())
        rows = list(reader)
    if len(rows) < minimum_rows:
        raise RuntimeError(
            f"{path} contains fewer than {minimum_rows} frames"
        )
    return fields, rows


def exact_nonnegative_integer(text: str, field: str) -> int:
    if not text.isascii() or not text.isdecimal():
        raise RuntimeError(f"{field} is not an exact non-negative integer: {text!r}")
    return int(text, 10)


def finite(row: dict[str, str], field: str) -> float:
    try:
        value = float(row[field])
    except (KeyError, ValueError) as error:
        raise RuntimeError(f"invalid {field}: {row.get(field)!r}") from error
    if not math.isfinite(value):
        raise RuntimeError(f"non-finite {field}: {value}")
    return value


def validate_scanout(rows: list[dict[str, str]]) -> None:
    sources = {row["scanout_source"] for row in rows}
    if len(sources) != 1 or "" in sources:
        raise RuntimeError(f"scanout source changed or is empty: {sorted(sources)}")
    for row in rows:
        valid = exact_nonnegative_integer(row["scanout_valid"], "scanout_valid")
        if valid not in (0, 1):
            raise RuntimeError("scanout_valid must be zero or one")
        before = exact_nonnegative_integer(
            row["scanout_counter_before"], "scanout_counter_before"
        )
        after = exact_nonnegative_integer(
            row["scanout_counter_after"], "scanout_counter_after"
        )
        delta = exact_nonnegative_integer(
            row["scanout_counter_delta"], "scanout_counter_delta"
        )
        if valid and ((after - before) & 0xFFFFFFFF) != delta:
            raise RuntimeError("scanout counter delta does not match its endpoints")
        if finite(row, "scanout_query_before_ms") < 0.0:
            raise RuntimeError("negative before-swap scanout query duration")
        if finite(row, "scanout_query_after_ms") < 0.0:
            raise RuntimeError("negative after-swap scanout query duration")


def validate_gpu(rows: list[dict[str, str]]) -> None:
    if len({row["diagnostic_run_id"] for row in rows}) != 1:
        raise RuntimeError("diagnostic_run_id must be constant")
    statuses = {row["gpu_timer_status"] for row in rows}
    if statuses != {"ok"}:
        raise RuntimeError(f"GPU timing did not resolve every frame: {sorted(statuses)}")
    for row in rows:
        start = exact_nonnegative_integer(
            row["gpu_start_timestamp_ns"], "gpu_start_timestamp_ns"
        )
        end = exact_nonnegative_integer(
            row["gpu_end_timestamp_ns"], "gpu_end_timestamp_ns"
        )
        if start <= 0 or end < start:
            raise RuntimeError("invalid GPU timestamp ordering")
        setup = finite(row, "gpu_setup_ms")
        scene = finite(row, "gpu_scene_ms")
        bloom = finite(row, "gpu_bloom_ms")
        total = finite(row, "gpu_frame_ms")
        if min(setup, scene, bloom, total) < 0.0:
            raise RuntimeError("resolved GPU stages must be non-negative")
        tolerance = max(1.0e-6, total * 1.0e-6)
        if abs(setup + scene + bloom - total) > tolerance:
            raise RuntimeError("GPU stage durations do not sum to gpu_frame_ms")
        if abs((end - start) * 1.0e-6 - total) > tolerance:
            raise RuntimeError("GPU timestamp span disagrees with gpu_frame_ms")


def validate_presentation(rows: list[dict[str, str]], event_path: Path) -> None:
    if not event_path.is_file():
        raise RuntimeError(f"missing presentation event trace: {event_path}")
    sources = {row["presentation_completion_source"] for row in rows}
    counts = {
        exact_nonnegative_integer(
            row["presentation_completion_events"],
            "presentation_completion_events",
        )
        for row in rows
    }
    mappings = {
        exact_nonnegative_integer(
            row["presentation_exact_mapping"], "presentation_exact_mapping"
        )
        for row in rows
    }
    if len(sources) != 1 or len(counts) != 1 or len(mappings) != 1:
        raise RuntimeError("presentation capability/result changed between trace rows")
    exact = next(iter(mappings))
    if exact not in (0, 1):
        raise RuntimeError("presentation_exact_mapping must be zero or one")
    if exact and next(iter(counts)) != len(rows):
        raise RuntimeError("exact presentation mapping lacks one event per frame")


def main() -> int:
    args = parse_args()
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    control_trace = output / "control.tsv"
    control_events = output / "control-presentation.tsv"
    wheel_trace = output / "wheel.tsv"
    wheel_events = output / "wheel-presentation.tsv"

    run(
        [
            str(args.probe_binary.resolve()),
            # A cold NVIDIA/X11 first swap can occasionally block for close to
            # one second while the presentation path initializes. Keep this a
            # real multi-frame instrumentation test instead of making startup
            # latency look like a schema failure.
            "--duration-seconds", "1.5",
            "--swap-interval", "1",
            "--width", "64",
            "--height", "64",
            "--trace", str(control_trace),
            "--presentation-events", str(control_events),
        ],
        "control instrumentation smoke",
    )
    control_fields, control_rows = read_trace(control_trace, minimum_rows=1)
    missing = SCANOUT_FIELDS.difference(control_fields)
    if missing:
        raise RuntimeError(f"control trace lacks fields: {sorted(missing)}")
    # DISPLAY can remain set while every physical connector is asleep or
    # unplugged. NVIDIA then returns one swap per second, exposes no monitor
    # refresh, and cannot provide GLX_SGI retrace samples. That is not a live
    # presentation environment, so treat it like the headless CMake case.
    inactive_display = all(
        finite(row, "nominal_hz") <= 0.0
        and exact_nonnegative_integer(row["scanout_valid"], "scanout_valid") == 0
        for row in control_rows
    )
    if inactive_display:
        print("SKIP: DISPLAY has no active monitor/retrace clock")
        return 77
    if len(control_rows) < 3:
        raise RuntimeError(f"{control_trace} contains fewer than three frames")
    validate_scanout(control_rows)

    run(
        [
            str(args.wheel_binary.resolve()),
            "--model", "mint",
            "--auto-roll",
            "--spin-rps", "3.5",
            "--temporal-mode", "adaptive",
            "--phase-clock", "scheduled",
            "--preset", "tread",
            "--frame-timing-trace", str(wheel_trace),
            "--presentation-events", str(wheel_events),
            "--gpu-timing",
            "--diagnostic-seconds", "1.5",
            "--swap-interval", "1",
            "--width", "64",
            "--height", "64",
            "--bloom",
        ],
        "wheel instrumentation smoke",
    )
    wheel_fields, wheel_rows = read_trace(wheel_trace)
    missing = (GPU_FIELDS | SCANOUT_FIELDS | PRESENTATION_FIELDS).difference(
        wheel_fields
    )
    if missing:
        raise RuntimeError(f"wheel trace lacks fields: {sorted(missing)}")
    validate_gpu(wheel_rows)
    validate_scanout(wheel_rows)
    validate_presentation(wheel_rows, wheel_events)
    print(
        "Validated asynchronous GPU timestamps, retrace counters, and "
        "presentation-feedback capability reporting."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError) as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(1) from error
