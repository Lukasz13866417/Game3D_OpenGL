#!/usr/bin/env python3
"""Run a control-vs-wheel live cadence diagnostic and compare the results.

The control is wheel_cadence_probe: it creates the same kind of native window and
does only clear, swap, and event polling.  The wheel runs use sharp, adaptive,
and split temporal modes.  This tool deliberately calls the existing timing
analyzer instead of duplicating its wheel-specific analysis.

Swap-return timing is a queue/back-pressure proxy.  Native captures also sample
the GLX_SGI_video_sync retrace counter and probe X Present completion events.
The retrace counter is display-pipe evidence, not a per-application-frame
presentation timestamp; physical presentation therefore remains UNKNOWN unless
the completion probe establishes an exact frame mapping.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import shlex
import statistics
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Sequence


TOOLS_DIR = Path(__file__).resolve().parent
LAB_ROOT = TOOLS_DIR.parent
DEFAULT_BUILD_DIR = LAB_ROOT / "build"
MINIMUM_CONCLUSIVE_SECONDS = 3.0
SEVERE_INTERVAL_NOMINAL_SLOTS = 4.0
WHEEL_MODES = ("sharp", "adaptive", "split")
TEMPORAL_ACTIVATION_EPSILON = 1.0e-6
MODEL_SLUGS = {"mint": "mint-wheel", "violet": "violet-wheel"}
EXPECTED_TEMPORAL_SOURCES = {
    "sharp": "sharp_mesh",
    "adaptive": "harmonic_shell",
    "split": "harmonic_shell",
}
CANONICAL_MINT_GLOW_COUNT = 18
CONTROL_COLUMNS = (
    "frame",
    "loop_start_ms",
    "loop_delta_ms",
    "clear_ms",
    "swap_wait_ms",
    "swap_return_ms",
    "swap_return_interval_ms",
    "poll_ms",
    "nominal_hz",
)
CONTROL_SCANOUT_COLUMNS = (
    "scanout_source",
    "scanout_valid",
    "scanout_counter_before",
    "scanout_counter_after",
    "scanout_counter_delta",
    "scanout_query_before_ms",
    "scanout_query_after_ms",
)
WHEEL_INSTRUMENTATION_COLUMNS = {
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
    "scanout_source",
    "scanout_valid",
    "scanout_counter_before",
    "scanout_counter_after",
    "scanout_counter_delta",
    "scanout_query_before_ms",
    "scanout_query_after_ms",
    "presentation_completion_source",
    "presentation_completion_events",
    "presentation_exact_mapping",
}
PRESENTATION_EVENT_COLUMNS = (
    "diagnostic_run_id",
    "event_index",
    "source",
    "validity",
    "mapped_submission_frame",
    "local_submission_ms",
    "local_arrival_ms",
    "ust_raw",
    "ust_units",
    "msc",
    "serial",
    "mode",
    "ust_delta_ms",
    "msc_delta",
    "window",
)
SCANOUT_COUNTER_SEMANTICS = (
    "physical display-pipe retrace counter; not an application-frame "
    "presentation timestamp"
)


@dataclass(frozen=True)
class CommandResult:
    label: str
    argv: tuple[str, ...]
    log_path: Path


def positive_float(value: str) -> float:
    parsed = float(value)
    if not math.isfinite(parsed) or parsed <= 0.0:
        raise argparse.ArgumentTypeError("must be a finite number greater than zero")
    return parsed


def nonnegative_float(value: str) -> float:
    parsed = float(value)
    if not math.isfinite(parsed) or parsed < 0.0:
        raise argparse.ArgumentTypeError("must be a finite non-negative number")
    return parsed


def positive_int(value: str) -> int:
    parsed = int(value)
    if parsed <= 0:
        raise argparse.ArgumentTypeError("must be greater than zero")
    return parsed


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Run a minimal clear/swap control and sharp/adaptive/split wheel "
            "captures, analyze them, and write comparison.json plus report.md."
        ),
        allow_abbrev=False,
    )
    parser.add_argument(
        "--probe-binary",
        type=Path,
        default=DEFAULT_BUILD_DIR / "wheel_cadence_probe",
        help="Path to wheel_cadence_probe.",
    )
    parser.add_argument(
        "--wheel-binary",
        type=Path,
        default=DEFAULT_BUILD_DIR / "wheel_mesh_lab",
        help="Path to wheel_mesh_lab.",
    )
    parser.add_argument(
        "--analyzer",
        type=Path,
        default=TOOLS_DIR / "analyze_frame_timing.py",
        help="Path to the existing wheel timing analyzer.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=DEFAULT_BUILD_DIR / "live-stutter-diagnostic",
        help="Directory for traces, per-run analysis, and comparison outputs.",
    )
    parser.add_argument(
        "--duration-seconds",
        type=positive_float,
        default=5.0,
        help=(
            "Capture duration for each run (default: 5). Runs shorter than "
            f"{MINIMUM_CONCLUSIVE_SECONDS:g}s are accepted as smoke tests but "
            "classified INCONCLUSIVE_SHORT_RUN."
        ),
    )
    parser.add_argument(
        "--swap-interval",
        type=int,
        choices=range(0, 5),
        default=1,
        metavar="0..4",
        help="Requested swap interval for every run (default: 1).",
    )
    parser.add_argument("--width", type=positive_int, default=960)
    parser.add_argument("--height", type=positive_int, default=540)
    parser.add_argument("--spin-rps", type=nonnegative_float, default=3.5)
    parser.add_argument(
        "--nominal-hz",
        type=positive_float,
        default=None,
        help=(
            "Override the effective nominal cadence recorded by the control "
            "and wheel traces. Use this for a nested/virtual display that "
            "reports the wrong refresh rate."
        ),
    )
    parser.add_argument(
        "--model",
        choices=("mint", "violet"),
        default="mint",
        help=(
            "Wheel model used by every rendered run (default: mint). The protected "
            "adaptive/split paths currently require mint groove geometry; selecting "
            "violet will therefore fail the effective-mode validation."
        ),
    )
    parser.add_argument(
        "--preset",
        choices=("side", "tread", "three-quarter", "gameplay"),
        default="tread",
    )
    parser.add_argument(
        "--phase-clock",
        choices=("scheduled", "previous-delta"),
        default="scheduled",
        help="Wheel pose clock used by all three wheel runs (default: scheduled).",
    )
    parser.add_argument(
        "--egl-window-context",
        action="store_true",
        help="Use the EGL window path in both the control and wheel captures.",
    )
    parser.add_argument(
        "--no-bloom",
        action="store_true",
        help="Disable bloom in all wheel captures.",
    )
    return parser.parse_args(argv)


def require_file(path: Path, label: str, executable: bool = False) -> Path:
    resolved = path.expanduser().resolve()
    if not resolved.is_file():
        raise RuntimeError(f"{label} does not exist or is not a file: {resolved}")
    if executable and not resolved.stat().st_mode & 0o111:
        raise RuntimeError(f"{label} is not executable: {resolved}")
    return resolved


def run_and_tee(label: str, argv: Sequence[str], log_path: Path) -> CommandResult:
    log_path.parent.mkdir(parents=True, exist_ok=True)
    command = tuple(str(value) for value in argv)
    print(f"\n[{label}] {shlex.join(command)}", flush=True)
    with log_path.open("w", encoding="utf-8") as log:
        log.write(f"$ {shlex.join(command)}\n")
        log.flush()
        process = subprocess.Popen(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
        )
        assert process.stdout is not None
        for line in process.stdout:
            print(f"[{label}] {line}", end="", flush=True)
            log.write(line)
        return_code = process.wait()
        log.write(f"\n[exit_code] {return_code}\n")
    if return_code != 0:
        raise RuntimeError(
            f"{label} failed with exit code {return_code}; see {log_path}"
        )
    return CommandResult(label=label, argv=command, log_path=log_path)


def percentile(values: Iterable[float], fraction: float) -> float:
    ordered = sorted(values)
    if not ordered:
        return 0.0
    position = fraction * (len(ordered) - 1)
    lower = int(math.floor(position))
    upper = int(math.ceil(position))
    if lower == upper:
        return ordered[lower]
    blend = position - lower
    return ordered[lower] * (1.0 - blend) + ordered[upper] * blend


def finite_number(row: dict[str, str], column: str, line_number: int) -> float:
    try:
        value = float(row[column])
    except (KeyError, ValueError) as error:
        raise RuntimeError(
            f"Invalid control trace value at line {line_number}, column {column}"
        ) from error
    if not math.isfinite(value):
        raise RuntimeError(
            f"Non-finite control trace value at line {line_number}, column {column}"
        )
    return value


def integral_number(
    row: dict[str, str],
    column: str,
    line_number: int,
    *,
    minimum: int = 0,
) -> int:
    value = finite_number(row, column, line_number)
    parsed = int(value)
    if value != parsed or parsed < minimum:
        raise RuntimeError(
            f"Invalid integral value at line {line_number}, column {column}"
        )
    return parsed


def exact_decimal_integer(
    row: dict[str, str],
    column: str,
    line_number: int,
) -> int:
    text = row.get(column, "")
    if not text or not text.isascii() or not text.isdecimal():
        raise RuntimeError(
            f"Invalid exact integer at line {line_number}, column {column}"
        )
    return int(text, 10)


def metric_summary(values: Sequence[float]) -> dict[str, float]:
    return {
        "median": statistics.median(values) if values else 0.0,
        "p99": percentile(values, 0.99),
        "maximum": max(values) if values else 0.0,
    }


def scanout_retrace_summary(
    rows: Sequence[dict[str, Any]],
    long_rows: Sequence[dict[str, Any]],
    *,
    collected: bool,
) -> dict[str, Any]:
    if not collected:
        return {
            "status": "NOT_COLLECTED",
            "source": "unavailable",
            "semantics": SCANOUT_COUNTER_SEMANTICS,
            "valid_frames": 0,
            "valid_fraction": 0.0,
            "retrace_delta": metric_summary([]),
            "long_swap_retrace_delta": metric_summary([]),
            "query_overhead_ms": metric_summary([]),
            "proves_per_frame_presentation": False,
        }

    valid_rows = [row for row in rows if row["scanout_valid"] == 1]
    valid_long_rows = [row for row in long_rows if row["scanout_valid"] == 1]
    sources = sorted({str(row["scanout_source"]) for row in rows})
    return {
        "status": "AVAILABLE" if valid_rows else "UNAVAILABLE",
        "source": (
            "glx_sgi_video_sync"
            if any(source == "glx_sgi_video_sync" for source in sources)
            else "unavailable"
        ),
        "observed_sources": sources,
        "semantics": SCANOUT_COUNTER_SEMANTICS,
        "valid_frames": len(valid_rows),
        "valid_fraction": len(valid_rows) / len(rows) if rows else 0.0,
        "retrace_delta": metric_summary(
            [float(row["scanout_counter_delta"]) for row in valid_rows]
        ),
        "long_swap_retrace_delta": metric_summary(
            [float(row["scanout_counter_delta"]) for row in valid_long_rows]
        ),
        "query_overhead_ms": metric_summary(
            [
                float(row["scanout_query_before_ms"])
                + float(row["scanout_query_after_ms"])
                for row in rows
            ]
        ),
        "proves_per_frame_presentation": False,
    }


def analyze_control_trace(trace_path: Path) -> dict[str, Any]:
    with trace_path.open("r", encoding="utf-8", newline="") as source:
        reader = csv.DictReader(source, delimiter="\t")
        fieldnames = set(reader.fieldnames or ())
        missing = sorted(set(CONTROL_COLUMNS) - fieldnames)
        if missing:
            raise RuntimeError(
                "Cadence-probe trace is missing required columns "
                f"{missing}; got {reader.fieldnames}"
            )
        present_scanout = fieldnames.intersection(CONTROL_SCANOUT_COLUMNS)
        if present_scanout and present_scanout != set(CONTROL_SCANOUT_COLUMNS):
            missing_scanout = sorted(
                set(CONTROL_SCANOUT_COLUMNS) - present_scanout
            )
            raise RuntimeError(
                "Cadence-probe trace has incomplete scanout instrumentation; "
                f"missing {missing_scanout}"
            )
        has_scanout = present_scanout == set(CONTROL_SCANOUT_COLUMNS)
        rows = list(reader)
    if len(rows) < 2:
        raise RuntimeError(f"Control trace has fewer than two frames: {trace_path}")

    parsed: list[dict[str, Any]] = []
    prior_frame: int | None = None
    prior_return: float | None = None
    for line_number, row in enumerate(rows, start=2):
        values: dict[str, Any] = {
            column: finite_number(row, column, line_number)
            for column in CONTROL_COLUMNS
        }
        frame = int(values["frame"])
        if values["frame"] != frame:
            raise RuntimeError(f"Non-integral frame at control trace line {line_number}")
        if prior_frame is not None and frame != prior_frame + 1:
            raise RuntimeError(f"Non-consecutive frame at control trace line {line_number}")
        if prior_return is not None and values["swap_return_ms"] < prior_return:
            raise RuntimeError(f"Non-monotonic swap return at control trace line {line_number}")
        prior_frame = frame
        prior_return = values["swap_return_ms"]
        if has_scanout:
            source_name = row["scanout_source"].strip()
            if not source_name:
                raise RuntimeError(
                    f"Empty scanout_source at control trace line {line_number}"
                )
            valid = integral_number(row, "scanout_valid", line_number)
            if valid not in (0, 1):
                raise RuntimeError(
                    f"scanout_valid must be 0 or 1 at line {line_number}"
                )
            before = integral_number(
                row, "scanout_counter_before", line_number
            )
            after = integral_number(row, "scanout_counter_after", line_number)
            delta = integral_number(row, "scanout_counter_delta", line_number)
            if before > 0xFFFFFFFF or after > 0xFFFFFFFF or delta > 0xFFFFFFFF:
                raise RuntimeError(
                    f"Scanout counter exceeds uint32 at line {line_number}"
                )
            if valid and source_name != "glx_sgi_video_sync":
                raise RuntimeError(
                    "A valid retrace sample must identify glx_sgi_video_sync "
                    f"at line {line_number}"
                )
            if valid and delta != ((after - before) & 0xFFFFFFFF):
                raise RuntimeError(
                    f"Scanout counter delta mismatch at line {line_number}"
                )
            query_before = finite_number(
                row, "scanout_query_before_ms", line_number
            )
            query_after = finite_number(
                row, "scanout_query_after_ms", line_number
            )
            if query_before < 0.0 or query_after < 0.0:
                raise RuntimeError(
                    f"Negative scanout query overhead at line {line_number}"
                )
            values.update(
                {
                    "scanout_source": source_name,
                    "scanout_valid": valid,
                    "scanout_counter_before": before,
                    "scanout_counter_after": after,
                    "scanout_counter_delta": delta,
                    "scanout_query_before_ms": query_before,
                    "scanout_query_after_ms": query_after,
                }
            )
        parsed.append(values)

    # Frame zero has no preceding return interval, matching the wheel analyzer.
    samples = parsed[1:]
    intervals = [row["swap_return_interval_ms"] for row in samples]
    clears = [row["clear_ms"] for row in samples]
    swaps = [row["swap_wait_ms"] for row in samples]
    polls = [row["poll_ms"] for row in samples]
    nominal_hz_values = [row["nominal_hz"] for row in samples]
    nominal_hz = statistics.median(nominal_hz_values)
    if nominal_hz <= 0.0:
        raise RuntimeError("Control trace reports non-positive nominal_hz")
    nominal_interval_ms = 1000.0 / nominal_hz
    long_rows = [
        row
        for row in samples
        if row["swap_return_interval_ms"] > 1.5 * (1000.0 / row["nominal_hz"])
    ]
    severe_rows = [
        row
        for row in samples
        if row["swap_return_interval_ms"]
        > SEVERE_INTERVAL_NOMINAL_SLOTS
        * (1000.0 / row["nominal_hz"])
    ]
    event_times = [row["swap_return_ms"] / 1000.0 for row in long_rows]
    event_spacings = [right - left for left, right in zip(event_times, event_times[1:])]
    severe_event_times = [
        row["swap_return_ms"] / 1000.0 for row in severe_rows
    ]
    severe_event_spacings = [
        right - left
        for left, right in zip(severe_event_times, severe_event_times[1:])
    ]
    duration_seconds = (
        parsed[-1]["swap_return_ms"] - parsed[0]["swap_return_ms"]
    ) / 1000.0
    long_count = len(long_rows)
    severe_count = len(severe_rows)
    return {
        "frames_analyzed": len(samples),
        "duration_seconds": duration_seconds,
        "nominal_hz": nominal_hz,
        "nominal_interval_ms": nominal_interval_ms,
        "swap_interval_ms": {
            "median": statistics.median(intervals),
            "p99": percentile(intervals, 0.99),
            "maximum": max(intervals),
        },
        "clear_ms": {
            "median": statistics.median(clears),
            "p99": percentile(clears, 0.99),
            "maximum": max(clears),
        },
        "swap_wait_ms": {
            "median": statistics.median(swaps),
            "p99": percentile(swaps, 0.99),
            "maximum": max(swaps),
        },
        "poll_ms": {
            "median": statistics.median(polls),
            "p99": percentile(polls, 0.99),
            "maximum": max(polls),
        },
        "long_interval_events": long_count,
        "long_interval_event_rate_hz": (
            long_count / duration_seconds if duration_seconds > 0.0 else 0.0
        ),
        "severe_interval_threshold_nominal_slots": (
            SEVERE_INTERVAL_NOMINAL_SLOTS
        ),
        "severe_interval_events": severe_count,
        "severe_interval_event_rate_hz": (
            severe_count / duration_seconds if duration_seconds > 0.0 else 0.0
        ),
        "event_spacing_median_seconds": (
            statistics.median(event_spacings) if event_spacings else None
        ),
        "severe_event_spacing_median_seconds": (
            statistics.median(severe_event_spacings)
            if severe_event_spacings else None
        ),
        "scanout_retrace": scanout_retrace_summary(
            samples, long_rows, collected=has_scanout
        ),
        "physical_display_cadence": "UNKNOWN",
    }


def load_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise RuntimeError(f"Could not load analyzer output {path}: {error}") from error
    if not isinstance(value, dict):
        raise RuntimeError(f"Analyzer output is not a JSON object: {path}")
    return value


def analyze_presentation_event_trace(
    path: Path,
    *,
    expected_run_id: str | None = None,
    expected_event_count: int | None = None,
    expected_exact_mapping: bool | None = None,
    expected_submission_count: int | None = None,
) -> dict[str, Any]:
    with path.open("r", encoding="utf-8", newline="") as source:
        reader = csv.DictReader(source, delimiter="\t")
        if reader.fieldnames != list(PRESENTATION_EVENT_COLUMNS):
            raise RuntimeError(
                "Unexpected presentation event trace schema. Expected "
                f"{list(PRESENTATION_EVENT_COLUMNS)}, got {reader.fieldnames}"
            )
        rows = list(reader)

    run_ids = {row["diagnostic_run_id"] for row in rows}
    if expected_run_id is not None and any(
        run_id != expected_run_id for run_id in run_ids
    ):
        raise RuntimeError(
            f"Presentation event trace run ID does not match {expected_run_id!r}"
        )
    if expected_event_count is not None and len(rows) != expected_event_count:
        raise RuntimeError(
            "Presentation event trace count disagrees with the frame trace: "
            f"{len(rows)} != {expected_event_count}"
        )

    mapped_frames: list[int] = []
    prior_ust: int | None = None
    prior_msc: int | None = None
    all_mapped = bool(rows)
    all_timestamped = bool(rows)
    for line_number, row in enumerate(rows, start=2):
        event_index = integral_number(row, "event_index", line_number)
        if event_index != line_number - 2:
            raise RuntimeError(
                f"Non-consecutive presentation event index at line {line_number}"
            )
        if row["source"] != "xpresent_pixmap":
            raise RuntimeError(
                f"Unexpected presentation event source at line {line_number}"
            )
        mapped = row["validity"] == "mapped"
        if row["validity"] not in ("mapped", "unmatched"):
            raise RuntimeError(
                f"Unexpected presentation validity at line {line_number}"
            )
        all_mapped = all_mapped and mapped
        if mapped:
            frame = integral_number(
                row, "mapped_submission_frame", line_number
            )
            mapped_frames.append(frame)
        elif row["mapped_submission_frame"]:
            raise RuntimeError(
                f"Unmatched event has a mapped frame at line {line_number}"
            )
        ust = exact_decimal_integer(row, "ust_raw", line_number)
        msc = exact_decimal_integer(row, "msc", line_number)
        ust_units = row["ust_units"]
        if ust_units not in ("microseconds", "unsupported"):
            raise RuntimeError(
                f"Unexpected presentation UST units at line {line_number}"
            )
        timestamped = ust_units == "microseconds" and ust > 0 and msc > 0
        all_timestamped = all_timestamped and timestamped
        if (timestamped and prior_ust is not None
                and (ust <= prior_ust or msc <= prior_msc)):
            raise RuntimeError(
                f"Non-monotonic presentation UST/MSC at line {line_number}"
            )
        if timestamped:
            prior_ust = ust
            prior_msc = msc

    exact_mapping = (
        all_mapped
        and all_timestamped
        and mapped_frames == list(range(len(mapped_frames)))
        and expected_submission_count is not None
        and len(rows) == expected_submission_count
    )
    if expected_exact_mapping is not None and exact_mapping != expected_exact_mapping:
        raise RuntimeError(
            "Presentation event trace exact-mapping state disagrees with the "
            "frame trace"
        )
    return {
        "source": (
            "xpresent_pixmap_exact"
            if exact_mapping
            else ("xpresent_pixmap_unmapped" if rows else "no_pixmap_events")
        ),
        "completion_events": len(rows),
        "expected_submissions": expected_submission_count,
        "exact_frame_mapping": exact_mapping,
        "physical_display_cadence": (
            "TIMESTAMPS_AVAILABLE_NOT_GRADED" if exact_mapping else "UNKNOWN"
        ),
    }


def validate_wheel_instrumentation(
    rows: Sequence[dict[str, str]],
    fieldnames: set[str],
    trace_path: Path,
    *,
    require_native_scanout: bool,
) -> dict[str, Any]:
    missing = sorted(WHEEL_INSTRUMENTATION_COLUMNS - fieldnames)
    if missing:
        raise RuntimeError(
            f"Wheel trace lacks instrumentation columns {missing}: {trace_path}"
        )

    run_ids: set[str] = set()
    completion_sources: set[str] = set()
    completion_counts: set[int] = set()
    exact_mapping_values: set[int] = set()
    scanout_sources: set[str] = set()
    valid_scanout = 0
    gpu_setup: list[float] = []
    gpu_scene: list[float] = []
    gpu_bloom: list[float] = []
    gpu_frame: list[float] = []
    scanout_deltas: list[float] = []
    scanout_query_overheads: list[float] = []
    for line_number, row in enumerate(rows, start=2):
        run_id = row["diagnostic_run_id"].strip()
        if not run_id:
            raise RuntimeError(
                f"Empty diagnostic_run_id at {trace_path}:{line_number}"
            )
        run_ids.add(run_id)
        status = row["gpu_timer_status"].strip()
        if status != "ok":
            raise RuntimeError(
                "Strict diagnostic requires a resolved GPU timer result for "
                f"every frame; got {status!r} at {trace_path}:{line_number}"
            )
        integral_number(row, "gpu_disjoint_epoch", line_number)
        integral_number(row, "gpu_query_latency_frames", line_number)
        start_ns = exact_decimal_integer(
            row, "gpu_start_timestamp_ns", line_number
        )
        end_ns = exact_decimal_integer(row, "gpu_end_timestamp_ns", line_number)
        if start_ns <= 0 or end_ns < start_ns:
            raise RuntimeError(
                f"Invalid GPU timestamp range at {trace_path}:{line_number}"
            )
        stages = [
            finite_number(row, column, line_number)
            for column in (
                "gpu_setup_ms",
                "gpu_scene_ms",
                "gpu_bloom_ms",
                "gpu_frame_ms",
            )
        ]
        if any(value < 0.0 for value in stages):
            raise RuntimeError(
                f"Unavailable GPU stage timing at {trace_path}:{line_number}"
            )
        tolerance = max(1.0e-5, stages[3] * 1.0e-5)
        if abs(sum(stages[:3]) - stages[3]) > tolerance:
            raise RuntimeError(
                f"GPU stage sum mismatch at {trace_path}:{line_number}"
            )
        timestamp_ms = (end_ns - start_ns) * 1.0e-6
        if abs(timestamp_ms - stages[3]) > tolerance:
            raise RuntimeError(
                f"GPU timestamp duration mismatch at {trace_path}:{line_number}"
            )
        gpu_setup.append(stages[0])
        gpu_scene.append(stages[1])
        gpu_bloom.append(stages[2])
        gpu_frame.append(stages[3])

        scanout_source = row["scanout_source"].strip()
        if not scanout_source:
            raise RuntimeError(
                f"Empty scanout_source at {trace_path}:{line_number}"
            )
        scanout_sources.add(scanout_source)
        scanout_valid = integral_number(row, "scanout_valid", line_number)
        if scanout_valid not in (0, 1):
            raise RuntimeError(
                f"scanout_valid must be 0 or 1 at {trace_path}:{line_number}"
            )
        before = integral_number(row, "scanout_counter_before", line_number)
        after = integral_number(row, "scanout_counter_after", line_number)
        delta = integral_number(row, "scanout_counter_delta", line_number)
        if before > 0xFFFFFFFF or after > 0xFFFFFFFF or delta > 0xFFFFFFFF:
            raise RuntimeError(
                f"Scanout counter exceeds uint32 at {trace_path}:{line_number}"
            )
        if scanout_valid:
            valid_scanout += 1
            if scanout_source != "glx_sgi_video_sync":
                raise RuntimeError(
                    "Valid scanout samples must identify glx_sgi_video_sync "
                    f"at {trace_path}:{line_number}"
                )
            if delta != ((after - before) & 0xFFFFFFFF):
                raise RuntimeError(
                    f"Scanout delta mismatch at {trace_path}:{line_number}"
                )
            scanout_deltas.append(float(delta))
        before_query = finite_number(
            row, "scanout_query_before_ms", line_number
        )
        after_query = finite_number(row, "scanout_query_after_ms", line_number)
        if before_query < 0.0 or after_query < 0.0:
            raise RuntimeError(
                f"Negative scanout query overhead at {trace_path}:{line_number}"
            )
        scanout_query_overheads.append(before_query + after_query)

        completion_source = row["presentation_completion_source"].strip()
        if not completion_source:
            raise RuntimeError(
                f"Empty presentation source at {trace_path}:{line_number}"
            )
        completion_sources.add(completion_source)
        completion_counts.add(
            integral_number(row, "presentation_completion_events", line_number)
        )
        exact_mapping = integral_number(
            row, "presentation_exact_mapping", line_number
        )
        if exact_mapping not in (0, 1):
            raise RuntimeError(
                "presentation_exact_mapping must be 0 or 1 at "
                f"{trace_path}:{line_number}"
            )
        exact_mapping_values.add(exact_mapping)

    for name, values in (
        ("diagnostic_run_id", run_ids),
        ("scanout_source", scanout_sources),
        ("presentation_completion_source", completion_sources),
        ("presentation_completion_events", completion_counts),
        ("presentation_exact_mapping", exact_mapping_values),
    ):
        if len(values) != 1:
            raise RuntimeError(
                f"Wheel trace changes {name} between frames: {trace_path}"
            )

    valid_fraction = valid_scanout / len(rows)
    if require_native_scanout and valid_fraction < 0.95:
        raise RuntimeError(
            "Native diagnostic requires at least 95% valid GLX_SGI retrace "
            f"samples; got {valid_fraction:.1%} in {trace_path}"
        )
    event_count = next(iter(completion_counts))
    exact_mapping = bool(next(iter(exact_mapping_values)))
    completion_source = next(iter(completion_sources))
    if exact_mapping and event_count != len(rows):
        raise RuntimeError(
            "Trace claims exact presentation mapping without one event per frame"
        )
    if exact_mapping != (completion_source == "xpresent_pixmap_exact"):
        raise RuntimeError(
            "Presentation completion source and exact-mapping flag disagree"
        )
    if completion_source == "xpresent_no_pixmap_events" and event_count != 0:
        raise RuntimeError(
            "No-pixmap-events source reports nonzero completion events"
        )
    return {
        "diagnostic_run_id": next(iter(run_ids)),
        "frame_count": len(rows),
        "gpu_timing": {
            "status": "AVAILABLE",
            "valid_fraction": 1.0,
            "setup_ms": metric_summary(gpu_setup),
            "scene_ms": metric_summary(gpu_scene),
            "bloom_ms": metric_summary(gpu_bloom),
            "frame_ms": metric_summary(gpu_frame),
        },
        "scanout_retrace": {
            "status": "AVAILABLE" if valid_scanout else "UNAVAILABLE",
            "source": next(iter(scanout_sources)),
            "semantics": SCANOUT_COUNTER_SEMANTICS,
            "valid_frames": valid_scanout,
            "valid_fraction": valid_fraction,
            "retrace_delta": metric_summary(scanout_deltas),
            "query_overhead_ms": metric_summary(scanout_query_overheads),
            "proves_per_frame_presentation": False,
        },
        "presentation_timing": {
            "source": completion_source,
            "completion_events": event_count,
            "exact_frame_mapping": exact_mapping,
            "physical_display_cadence": (
                "TIMESTAMPS_AVAILABLE_NOT_GRADED"
                if exact_mapping
                else "UNKNOWN"
            ),
        },
    }


def inspect_wheel_trace_identity(
    trace_path: Path,
    requested_model: str,
    requested_mode: str,
    requested_spin_rps: float,
    *,
    require_instrumentation: bool = False,
    require_native_scanout: bool = False,
) -> dict[str, Any]:
    """Strictly attest model and temporal identity from every renderer row."""
    with trace_path.open("r", encoding="utf-8", newline="") as source:
        reader = csv.DictReader(source, delimiter="\t")
        fieldnames = set(reader.fieldnames or ())
        required = {
            "frame",
            "groove_cycles_per_frame",
            "alias_envelope_cycles",
            "temporal_blend",
            "model_slug",
            "requested_temporal_mode",
            "effective_temporal_mode",
            "temporal_source",
            "temporal_grooves_available",
            "mint_glow_count",
        }
        missing = sorted(required - fieldnames)
        if missing:
            raise RuntimeError(
                f"Wheel trace lacks identity evidence columns {missing}: {trace_path}"
            )
        rows = list(reader)
    if not rows:
        raise RuntimeError(f"Wheel trace contains no frames: {trace_path}")

    temporal_blends: list[float] = []
    groove_cycles: list[float] = []
    alias_cycles: list[float] = []
    model_slugs: set[str] = set()
    trace_requested_modes: set[str] = set()
    effective_modes: set[str] = set()
    temporal_sources: set[str] = set()
    groove_availability: set[int] = set()
    mint_glow_counts: set[int] = set()
    for line_number, row in enumerate(rows, start=2):
        temporal_blends.append(
            finite_number(row, "temporal_blend", line_number)
        )
        groove_cycles.append(
            finite_number(row, "groove_cycles_per_frame", line_number)
        )
        alias_cycles.append(
            finite_number(row, "alias_envelope_cycles", line_number)
        )
        model_slugs.add(row["model_slug"].strip())
        trace_requested_modes.add(row["requested_temporal_mode"].strip())
        effective_modes.add(row["effective_temporal_mode"].strip())
        temporal_sources.add(row["temporal_source"].strip())
        available = finite_number(
            row, "temporal_grooves_available", line_number
        )
        glow_count = finite_number(row, "mint_glow_count", line_number)
        if available not in (0.0, 1.0):
            raise RuntimeError(
                "temporal_grooves_available must be exactly 0 or 1 at "
                f"{trace_path}:{line_number}"
            )
        if glow_count != int(glow_count):
            raise RuntimeError(
                f"mint_glow_count is not integral at {trace_path}:{line_number}"
            )
        groove_availability.add(int(available))
        mint_glow_counts.add(int(glow_count))

    exact_fields: tuple[tuple[str, set[object]], ...] = (
        ("model_slug", model_slugs),
        ("requested_temporal_mode", trace_requested_modes),
        ("effective_temporal_mode", effective_modes),
        ("temporal_source", temporal_sources),
        ("temporal_grooves_available", groove_availability),
        ("mint_glow_count", mint_glow_counts),
    )
    for field, values in exact_fields:
        if len(values) != 1:
            raise RuntimeError(
                f"Wheel trace changes {field} between frames: "
                f"{sorted(values, key=str)} in {trace_path}"
            )

    model_slug = next(iter(model_slugs))
    trace_requested_mode = next(iter(trace_requested_modes))
    effective_mode = next(iter(effective_modes))
    temporal_source = next(iter(temporal_sources))
    temporal_grooves_available = bool(next(iter(groove_availability)))
    mint_glow_count = next(iter(mint_glow_counts))

    expected_slug = MODEL_SLUGS[requested_model]
    if model_slug != expected_slug:
        raise RuntimeError(
            f"Requested model {requested_model}, but trace reports model_slug="
            f"{model_slug!r} instead of {expected_slug!r}."
        )
    if trace_requested_mode != requested_mode:
        raise RuntimeError(
            f"Requested temporal mode {requested_mode}, but trace records "
            f"requested_temporal_mode={trace_requested_mode!r}."
        )
    if effective_mode != requested_mode:
        raise RuntimeError(
            f"Requested temporal mode {requested_mode} for model {requested_model}, "
            f"but renderer resolved effective_temporal_mode={effective_mode!r}."
        )
    expected_source = EXPECTED_TEMPORAL_SOURCES[effective_mode]
    if temporal_source != expected_source:
        raise RuntimeError(
            f"Effective mode {effective_mode} must use temporal_source="
            f"{expected_source!r}, but trace reports {temporal_source!r}."
        )
    expected_grooves_available = requested_model == "mint"
    if temporal_grooves_available != expected_grooves_available:
        raise RuntimeError(
            f"Model {requested_model} expected temporal_grooves_available="
            f"{int(expected_grooves_available)}, but trace reports "
            f"{int(temporal_grooves_available)}."
        )
    if effective_mode != "sharp" and not temporal_grooves_available:
        raise RuntimeError(
            f"Effective mode {effective_mode} requires temporal groove geometry."
        )
    if mint_glow_count != CANONICAL_MINT_GLOW_COUNT:
        raise RuntimeError(
            "Live comparison requires the canonical all-real-groove mint asset: "
            f"expected mint_glow_count={CANONICAL_MINT_GLOW_COUNT}, got "
            f"{mint_glow_count}."
        )

    temporal_path_active = any(
        value > TEMPORAL_ACTIVATION_EPSILON for value in temporal_blends
    )
    groove_planning_active = any(
        value > TEMPORAL_ACTIVATION_EPSILON for value in groove_cycles
    )
    alias_planning_active = any(
        value > TEMPORAL_ACTIVATION_EPSILON for value in alias_cycles
    )
    requested_non_sharp = effective_mode != "sharp"
    if requested_non_sharp and not temporal_path_active:
        raise RuntimeError(
            f"Trace identifies effective mode {effective_mode}, but reports "
            f"temporal_blend=0 for every frame: {trace_path}"
        )
    if (
        requested_non_sharp
        and requested_spin_rps > TEMPORAL_ACTIVATION_EPSILON
        and not (groove_planning_active and alias_planning_active)
    ):
        raise RuntimeError(
            f"Requested moving {requested_mode} for model {requested_model}, but "
            f"{trace_path} contains no active groove/alias planning signal."
        )
    result = {
        "requested_model": requested_model,
        "resolved_model": requested_model,
        "model_slug": model_slug,
        "resolved_model_evidence": "exact model_slug recorded in every trace row",
        "requested_mode": requested_mode,
        "trace_requested_mode": trace_requested_mode,
        "effective_temporal_mode": effective_mode,
        "effective_temporal_path": temporal_source,
        "temporal_source": temporal_source,
        "temporal_grooves_available": temporal_grooves_available,
        "mint_glow_count": mint_glow_count,
        "exact_effective_mode_encoded_by_trace": True,
        "temporal_path_active": temporal_path_active,
        "groove_planning_active": groove_planning_active,
        "alias_planning_active": alias_planning_active,
    }
    if require_instrumentation:
        result["instrumentation"] = validate_wheel_instrumentation(
            rows,
            fieldnames,
            trace_path,
            require_native_scanout=require_native_scanout,
        )
    return result


def wheel_comparison_view(
    mode: str,
    summary: dict[str, Any],
    identity: dict[str, Any],
) -> dict[str, Any]:
    swap = summary["swap_interval_ms"]
    cadence = summary["swap_return_cadence_proxy"]
    render = summary["render_ms"]
    verdicts = summary["verdicts"]
    duration = float(summary["duration_seconds"])
    long_count = int(cadence["long_interval_events"])
    severe_count = int(cadence.get("severe_interval_events", 0))
    result = {
        "mode": mode,
        "identity": identity,
        "frames_analyzed": int(summary["frames_analyzed"]),
        "duration_seconds": duration,
        "nominal_hz": float(summary["nominal_hz"]),
        "swap_interval_ms": {
            "median": float(swap["median"]),
            "p99": float(swap["p99"]),
            "maximum": float(swap["maximum"]),
        },
        "long_interval_events": long_count,
        "long_interval_event_rate_hz": long_count / duration if duration > 0.0 else 0.0,
        "event_spacing_median_seconds": cadence.get("event_spacing_median_seconds"),
        "severe_interval_threshold_nominal_slots": cadence.get(
            "severe_interval_threshold_nominal_slots",
            SEVERE_INTERVAL_NOMINAL_SLOTS,
        ),
        "severe_interval_events": severe_count,
        "severe_interval_event_rate_hz": (
            severe_count / duration if duration > 0.0 else 0.0
        ),
        "severe_event_spacing_median_seconds": cadence.get(
            "severe_event_spacing_median_seconds"
        ),
        "render_ms": {
            "median": float(render["median"]),
            "p99": float(render["p99"]),
            "maximum": float(render["maximum"]),
        },
        "verdicts": {
            "swap_return_cadence_proxy": verdicts["swap_return_cadence_proxy"],
            "cpu_render_budget": verdicts["cpu_render_budget"],
            "pose_step_continuity": verdicts["pose_step_continuity"],
            "representation_stability": verdicts["representation_stability"],
            "physical_display_cadence": "UNKNOWN",
        },
    }
    instrumentation = identity.get("instrumentation", {})
    for key in ("gpu_timing", "scanout_retrace", "presentation_timing"):
        combined: dict[str, Any] = {}
        if isinstance(instrumentation.get(key), dict):
            combined.update(instrumentation[key])
        analyzer_key = key
        if key == "presentation_timing" and "presentation_completion" in summary:
            analyzer_key = "presentation_completion"
        if analyzer_key in summary:
            if not isinstance(summary[analyzer_key], dict):
                raise RuntimeError(f"Analyzer {key} summary must be an object")
            combined.update(summary[analyzer_key])
        if "exact_mapping" in combined:
            combined["exact_frame_mapping"] = bool(combined["exact_mapping"])
        if combined:
            result[key] = combined
    if "presentation_completion" in summary:
        result["presentation_completion"] = summary["presentation_completion"]
    return result


def ratio_or_none(numerator: float | None, denominator: float | None) -> float | None:
    if numerator is None or denominator is None or denominator <= 0.0:
        return None
    return numerator / denominator


def compare_to_control(control: dict[str, Any], wheel: dict[str, Any]) -> dict[str, Any]:
    use_severe = (
        int(control.get("severe_interval_events", 0)) > 0
        or int(wheel.get("severe_interval_events", 0)) > 0
    )
    prefix = "severe_interval" if use_severe else "long_interval"
    spacing_key = (
        "severe_event_spacing_median_seconds"
        if use_severe else "event_spacing_median_seconds"
    )
    control_count = int(control[f"{prefix}_events"])
    wheel_count = int(wheel[f"{prefix}_events"])
    rate_ratio = ratio_or_none(
        float(wheel[f"{prefix}_event_rate_hz"]),
        float(control[f"{prefix}_event_rate_hz"]),
    )
    max_ratio = ratio_or_none(
        float(wheel["swap_interval_ms"]["maximum"]),
        float(control["swap_interval_ms"]["maximum"]),
    )
    spacing_ratio = ratio_or_none(
        wheel.get(spacing_key),
        control.get(spacing_key),
    )
    both_stall = control_count > 0 and wheel_count > 0
    similar_rate = rate_ratio is not None and 0.45 <= rate_ratio <= 2.2
    similar_maximum = max_ratio is not None and 0.5 <= max_ratio <= 2.0
    similar_spacing = spacing_ratio is None or 0.6 <= spacing_ratio <= 1.67
    return {
        "comparison_event_class": (
            "severe_over_4_nominal_slots" if use_severe else "long_over_1_5_slots"
        ),
        "both_have_long_intervals": both_stall,
        "long_interval_event_rate_ratio_wheel_over_control": rate_ratio,
        "maximum_swap_interval_ratio_wheel_over_control": max_ratio,
        "event_spacing_ratio_wheel_over_control": spacing_ratio,
        "similar_to_control": both_stall and similar_rate and similar_maximum and similar_spacing,
        "similarity_thresholds": {
            "event_rate_ratio": [0.45, 2.2],
            "maximum_interval_ratio": [0.5, 2.0],
            "event_spacing_ratio_when_available": [0.6, 1.67],
        },
    }


def classify(
    control: dict[str, Any],
    wheels: dict[str, dict[str, Any]],
    comparisons: dict[str, dict[str, Any]],
) -> tuple[str, str]:
    durations = [float(control["duration_seconds"])] + [
        float(wheel["duration_seconds"]) for wheel in wheels.values()
    ]
    if min(durations) < MINIMUM_CONCLUSIVE_SECONDS:
        return (
            "INCONCLUSIVE_SHORT_RUN",
            f"At least one capture is shorter than {MINIMUM_CONCLUSIVE_SECONDS:g}s; "
            "the files are useful for plumbing validation, not a stall attribution.",
        )

    control_stalls = int(control["long_interval_events"]) > 0
    stalled_modes = [
        mode for mode, wheel in wheels.items() if int(wheel["long_interval_events"]) > 0
    ]
    similar_modes = [
        mode for mode, comparison in comparisons.items() if comparison["similar_to_control"]
    ]
    cpu_failed_modes = [
        mode
        for mode, wheel in wheels.items()
        if wheel["verdicts"]["cpu_render_budget"] == "FAIL"
    ]

    if control_stalls and len(similar_modes) == len(wheels):
        if cpu_failed_modes:
            return (
                "COMMON_BASELINE_PLUS_WHEEL_RENDER_PRESSURE",
                "All wheel modes reproduce a control-like swap-return stall pattern, "
                f"and CPU render budget also fails in {', '.join(cpu_failed_modes)}.",
            )
        return (
            "COMMON_WINDOW_SYSTEM_OR_QUEUE_BACKPRESSURE",
            "The minimal clear/swap control and all wheel modes have quantitatively "
            "similar long swap-return intervals. The common cause is below the wheel's "
            "temporal representation, such as scheduling, the window system, driver "
            "queueing, or compositor back-pressure.",
        )
    if control_stalls and stalled_modes:
        return (
            "MIXED_CONTROL_AND_WHEEL_SWAP_RETURN_STALLS",
            "Long swap-return intervals occur in the control and in "
            f"{', '.join(stalled_modes)}, but the rates/timing are not consistently "
            "control-like across all wheel modes. Repeat a longer run while keeping "
            "desktop load stable before attributing the difference to a mode.",
        )
    if control_stalls:
        return (
            "CONTROL_ONLY_SWAP_RETURN_STALLS",
            "The minimal control stalled but none of the wheel captures did. This does "
            "not implicate wheel rendering; repeat because sequential live captures can "
            "sample different scheduler/compositor conditions.",
        )
    if not stalled_modes:
        return (
            "NO_LONG_SWAP_RETURN_STALLS_REPRODUCED",
            "No capture contained a swap-return interval above 1.5 nominal periods.",
        )
    if len(stalled_modes) < len(wheels):
        return (
            "MODE_SPECIFIC_OR_NONSTATIONARY_WHEEL_STALLS",
            "The clear/swap control did not stall, while only "
            f"{', '.join(stalled_modes)} did. This may be mode-specific, or the live "
            "system load may have changed between sequential captures; repeat and "
            "rotate run order before drawing a causal conclusion.",
        )
    if cpu_failed_modes:
        return (
            "WHEEL_RENDER_WORKLOAD_STALLS",
            "All wheel modes stalled while the control did not, and CPU render budget "
            f"failed in {', '.join(cpu_failed_modes)}. Investigate wheel/GPU workload.",
        )
    return (
        "WHEEL_OR_DRIVER_QUEUE_STALLS_NOT_IN_CONTROL",
        "All wheel modes have long swap-return intervals absent from the clear/swap "
        "control, while measured CPU render work remains within budget. The additional "
        "scene/GPU/driver queue path is implicated, but physical scanout is unmeasured.",
    )


def fmt_number(value: Any, digits: int = 3) -> str:
    if value is None:
        return "—"
    return f"{float(value):.{digits}f}"


def relative_link(target: Path, base: Path) -> str:
    try:
        return str(target.resolve().relative_to(base.resolve()))
    except ValueError:
        return str(target.resolve())


def evidence_metric(
    evidence: dict[str, Any],
    group_names: Sequence[str],
    metric: str,
) -> Any:
    for group_name in group_names:
        group = evidence.get(group_name)
        if isinstance(group, dict) and metric in group:
            return group[metric]
    return None


def write_report(
    path: Path,
    requested_model: str,
    classification: str,
    interpretation: str,
    control: dict[str, Any],
    wheels: dict[str, dict[str, Any]],
    comparisons: dict[str, dict[str, Any]],
    presentation_event_probe_enabled: bool,
) -> None:
    rows = [
        (
            "control",
            control,
            "n/a",
            "n/a",
        )
    ] + [
        (
            mode,
            wheels[mode],
            wheels[mode]["verdicts"]["cpu_render_budget"],
            "yes" if comparisons[mode]["similar_to_control"] else "no",
        )
        for mode in WHEEL_MODES
    ]
    table_lines = []
    for name, result, cpu_verdict, similar in rows:
        table_lines.append(
            "| {name} | {frames} | {duration} | {hz} | {median} | {p99} | "
            "{maximum} | {events} | {severe} | {rate} | {cpu} | {similar} |".format(
                name=name,
                frames=result["frames_analyzed"],
                duration=fmt_number(result["duration_seconds"]),
                hz=fmt_number(result["nominal_hz"], 2),
                median=fmt_number(result["swap_interval_ms"]["median"]),
                p99=fmt_number(result["swap_interval_ms"]["p99"]),
                maximum=fmt_number(result["swap_interval_ms"]["maximum"]),
                events=result["long_interval_events"],
                severe=result.get("severe_interval_events", 0),
                rate=fmt_number(result.get("severe_interval_event_rate_hz", 0.0)),
                cpu=cpu_verdict,
                similar=similar,
            )
        )
    identity_lines = []
    for mode in WHEEL_MODES:
        identity = wheels[mode]["identity"]
        identity_lines.append(
            "| {run} | `{slug}` | `{requested}` | `{effective}` | `{source}` | "
            "{grooves} | {glow_count} |".format(
                run=mode,
                slug=identity["model_slug"],
                requested=identity["trace_requested_mode"],
                effective=identity["effective_temporal_mode"],
                source=identity["temporal_source"],
                grooves="yes" if identity["temporal_grooves_available"] else "no",
                glow_count=identity["mint_glow_count"],
            )
        )

    retrace_lines = []
    for name, result, _cpu_verdict, _similar in rows:
        evidence = result.get("scanout_retrace", {})
        retrace_delta = evidence_metric(
            evidence,
            ("per_swap_counter_delta", "retrace_delta", "counter_delta"),
            "maximum",
        )
        long_delta = evidence_metric(
            evidence,
            ("long_swap_retrace_delta", "long_interval_counter_delta"),
            "maximum",
        )
        retrace_lines.append(
            "| {name} | `{status}` | `{source}` | {coverage} | {maximum} | "
            "{long_max} |".format(
                name=name,
                status=evidence.get("status", "NOT_COLLECTED"),
                source=evidence.get("source", "unavailable"),
                coverage=fmt_number(evidence.get("valid_fraction"), 3),
                maximum=fmt_number(retrace_delta, 0),
                long_max=fmt_number(long_delta, 0),
            )
        )

    gpu_lines = []
    for mode in WHEEL_MODES:
        evidence = wheels[mode].get("gpu_timing", {})
        stages = evidence.get("stages_ms", {})
        frame_group = stages.get("frame") if isinstance(stages, dict) else None
        if not isinstance(frame_group, dict):
            frame_group = evidence.get("frame_ms")
        if not isinstance(frame_group, dict):
            frame_group = evidence.get("gpu_frame_ms", {})
        gpu_lines.append(
            "| {mode} | `{status}` | {coverage} | {median} | {p99} | "
            "{maximum} |".format(
                mode=mode,
                status=evidence.get(
                    "status",
                    "AVAILABLE" if evidence.get("available") else "NOT_COLLECTED",
                ),
                coverage=fmt_number(evidence.get("valid_fraction"), 3),
                median=fmt_number(frame_group.get("median")),
                p99=fmt_number(frame_group.get("p99")),
                maximum=fmt_number(frame_group.get("maximum")),
            )
        )

    exact_mappings = [
        bool(
            result.get("presentation_timing", {}).get(
                "exact_frame_mapping",
                result.get("presentation_timing", {}).get("exact_mapping", False),
            )
        )
        for _name, result, _cpu_verdict, _similar in rows
    ]
    any_exact_mapping = any(exact_mappings)
    physical_display_status = (
        "TIMESTAMPS_AVAILABLE_NOT_GRADED"
        if any_exact_mapping
        else "UNKNOWN"
    )

    control_artifacts = (
        "[Control trace](control/trace.tsv) and [run log](control/run.log)"
    )
    if presentation_event_probe_enabled:
        control_artifacts += ", [presentation events](control/presentation-events.tsv)"
    links = [f"- {control_artifacts}"]
    for mode in WHEEL_MODES:
        presentation_link = (
            f", [presentation events]({mode}/presentation-events.tsv)"
            if presentation_event_probe_enabled
            else ""
        )
        links.append(
            f"- {mode}: [trace]({mode}/trace.tsv), "
            f"[analyzer report]({mode}/analysis/report.md), "
            f"[timing plot]({mode}/analysis/timing.svg), "
            f"[run log]({mode}/run.log){presentation_link}"
        )

    text = f"""# Live stutter diagnostic

## Outcome

**Classification:** `{classification}`

**Requested wheel model:** `{requested_model}`. Each rendered command receives
an explicit `--model {requested_model}` argument. Every trace row must attest the
expected model slug, requested and effective modes, temporal source, groove
availability, and canonical eighteen-groove asset before the run is analyzed.

{interpretation}

**Physical presentation cadence: `{physical_display_status}`.**
{("An exact X Present frame mapping was captured, but this report does not grade it as a cadence PASS." if any_exact_mapping else "No exact per-frame X Present completion mapping was captured, so physical presentation remains UNKNOWN.")}
The GLX_SGI_video_sync values below count physical display-pipe retraces around a
swap call. They show that the display clock advanced, but cannot identify which
application frame was scanned out. A swap return can describe compositor or
driver queue back-pressure without proving what was physically displayed.

## Renderer identity

| run | model slug | requested mode | effective mode | temporal source | grooves available | mint glow count |
|---|---|---|---|---|---|---:|
{chr(10).join(identity_lines)}

## Comparison

Long intervals exceed 1.5 nominal refresh periods; severe intervals exceed
{SEVERE_INTERVAL_NOMINAL_SLOTS:g} periods. Control similarity uses the severe
class whenever either compared run contains one, so minor one-slot scheduling
jitter cannot hide a shared large periodic stall.
Runs are sequential, so a mode difference should be reproduced with longer captures
and varied run order before it is treated as causal.

| run | frames | duration s | nominal Hz | swap median ms | swap p99 ms | swap max ms | >1.5-slot events | >4-slot events | >4 events/s | CPU budget | control-like |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|---|
{chr(10).join(table_lines)}

## GPU and display-clock evidence

GPU times come from asynchronous `GL_EXT_disjoint_timer_query` timestamps. They
measure submitted GPU work and do not include time spent blocked in buffer swap.

| wheel mode | GPU status | valid fraction | GPU frame median ms | GPU frame p99 ms | GPU frame max ms |
|---|---|---:|---:|---:|---:|
{chr(10).join(gpu_lines)}

The retrace counter is deliberately reported separately from presentation:

| run | retrace status | source | valid fraction | max retraces during swap | max retraces during long swap |
|---|---|---|---:|---:|---:|
{chr(10).join(retrace_lines)}

## Artifacts

{chr(10).join(links)}

Machine-readable results are in [comparison.json](comparison.json).
"""
    path.write_text(text, encoding="utf-8")


def main() -> int:
    args = parse_args()
    try:
        probe_binary = require_file(args.probe_binary, "Probe binary", executable=True)
        wheel_binary = require_file(args.wheel_binary, "Wheel binary", executable=True)
        analyzer = require_file(args.analyzer, "Timing analyzer")
        output_dir = args.output_dir.expanduser().resolve()
        output_dir.mkdir(parents=True, exist_ok=True)

        commands: list[CommandResult] = []
        common_window_args = [
            "--duration-seconds",
            f"{args.duration_seconds:.9g}",
            "--swap-interval",
            str(args.swap_interval),
            "--width",
            str(args.width),
            "--height",
            str(args.height),
        ]
        if args.egl_window_context:
            common_window_args.append("--egl-window-context")
        if args.nominal_hz is not None:
            common_window_args.extend(
                ["--nominal-hz", f"{args.nominal_hz:.9g}"]
            )

        control_dir = output_dir / "control"
        control_trace = control_dir / "trace.tsv"
        control_presentation_events = control_dir / "presentation-events.tsv"
        control_command = [
            str(probe_binary),
            *common_window_args,
            "--trace",
            str(control_trace),
        ]
        presentation_event_probe_enabled = not args.egl_window_context
        if presentation_event_probe_enabled:
            control_command.extend(
                ["--presentation-events", str(control_presentation_events)]
            )
        commands.append(
            run_and_tee("control", control_command, control_dir / "run.log")
        )
        control = analyze_control_trace(control_trace)
        if (
            presentation_event_probe_enabled
            and control["scanout_retrace"]["valid_fraction"] < 0.95
        ):
            raise RuntimeError(
                "Native control diagnostic requires at least 95% valid "
                "GLX_SGI retrace samples; got "
                f"{control['scanout_retrace']['valid_fraction']:.1%}"
            )
        if presentation_event_probe_enabled:
            control["presentation_timing"] = analyze_presentation_event_trace(
                control_presentation_events,
                expected_submission_count=control["frames_analyzed"] + 1,
            )
        else:
            control["presentation_timing"] = {
                "source": "unavailable",
                "completion_events": 0,
                "exact_frame_mapping": False,
                "physical_display_cadence": "UNKNOWN",
            }

        wheel_summaries: dict[str, dict[str, Any]] = {}
        wheels: dict[str, dict[str, Any]] = {}
        for mode in WHEEL_MODES:
            mode_dir = output_dir / mode
            trace_path = mode_dir / "trace.tsv"
            presentation_events_path = mode_dir / "presentation-events.tsv"
            wheel_command = [
                str(wheel_binary),
                "--model",
                args.model,
                "--auto-roll",
                "--spin-rps",
                f"{args.spin_rps:.9g}",
                "--temporal-mode",
                mode,
                "--phase-clock",
                args.phase_clock,
                "--preset",
                args.preset,
                "--frame-timing-trace",
                str(trace_path),
                "--diagnostic-seconds",
                f"{args.duration_seconds:.9g}",
                "--swap-interval",
                str(args.swap_interval),
                "--width",
                str(args.width),
                "--height",
                str(args.height),
                "--no-bloom" if args.no_bloom else "--bloom",
                "--gpu-timing",
                "--diagnostic-input-lock",
            ]
            if args.nominal_hz is not None:
                wheel_command.extend(["--fps", f"{args.nominal_hz:.9g}"])
            if args.egl_window_context:
                wheel_command.append("--egl-window-context")
            else:
                wheel_command.extend(
                    ["--presentation-events", str(presentation_events_path)]
                )
            commands.append(
                run_and_tee(mode, wheel_command, mode_dir / "run.log")
            )
            identity = inspect_wheel_trace_identity(
                trace_path,
                args.model,
                mode,
                args.spin_rps,
                require_instrumentation=True,
                require_native_scanout=presentation_event_probe_enabled,
            )

            analysis_dir = mode_dir / "analysis"
            analyzer_command = [
                sys.executable,
                str(analyzer),
                str(trace_path),
                "--output-dir",
                str(analysis_dir),
            ]
            commands.append(
                run_and_tee(
                    f"analyze-{mode}",
                    analyzer_command,
                    mode_dir / "analyzer.log",
                )
            )
            summary = load_json(analysis_dir / "summary.json")
            wheel_summaries[mode] = summary
            wheels[mode] = wheel_comparison_view(mode, summary, identity)
            instrumentation = identity["instrumentation"]
            if presentation_event_probe_enabled:
                presentation_event_summary = analyze_presentation_event_trace(
                    presentation_events_path,
                    expected_run_id=instrumentation["diagnostic_run_id"],
                    expected_event_count=instrumentation["presentation_timing"][
                        "completion_events"
                    ],
                    expected_exact_mapping=instrumentation[
                        "presentation_timing"
                    ]["exact_frame_mapping"],
                    expected_submission_count=instrumentation["frame_count"],
                )
                wheels[mode][
                    "presentation_event_trace"
                ] = presentation_event_summary
            else:
                wheels[mode]["presentation_event_trace"] = {
                    "source": "unavailable",
                    "completion_events": 0,
                    "exact_frame_mapping": False,
                    "physical_display_cadence": "UNKNOWN",
                }

        comparisons = {
            mode: compare_to_control(control, wheel) for mode, wheel in wheels.items()
        }
        classification, interpretation = classify(control, wheels, comparisons)
        comparison = {
            "schema_version": 2,
            "configuration": {
                "duration_seconds_per_run": args.duration_seconds,
                "swap_interval": args.swap_interval,
                "window": {"width": args.width, "height": args.height},
                "spin_rps": args.spin_rps,
                "requested_model": args.model,
                "preset": args.preset,
                "phase_clock": args.phase_clock,
                "bloom": not args.no_bloom,
                "egl_window_context": args.egl_window_context,
                "gpu_timing": True,
                "nominal_hz_override": args.nominal_hz,
                "presentation_event_probe": presentation_event_probe_enabled,
                "scanout_retrace_counter": presentation_event_probe_enabled,
                "run_order": ["control", *WHEEL_MODES],
            },
            "measurement_limits": {
                "physical_scanout_cadence": "UNKNOWN",
                "reason": (
                    "Unless X Present establishes an exact per-frame completion "
                    "mapping, GLX_SGI_video_sync is only a physical display-pipe "
                    "retrace counter. Swap-return cadence remains a queue/back-"
                    "pressure proxy and cannot identify a scanned-out app frame."
                ),
                "retrace_counter_semantics": SCANOUT_COUNTER_SEMANTICS,
            },
            "verdicts": {
                "control_vs_wheel_swap_return": classification,
                "physical_display_cadence": "UNKNOWN",
            },
            "interpretation": interpretation,
            "control": control,
            "wheel_modes": wheels,
            "control_comparisons": comparisons,
            "commands": [
                {
                    "label": command.label,
                    "argv": list(command.argv),
                    "log": relative_link(command.log_path, output_dir),
                }
                for command in commands
            ],
        }
        comparison_path = output_dir / "comparison.json"
        comparison_path.write_text(
            json.dumps(comparison, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        report_path = output_dir / "report.md"
        write_report(
            report_path,
            args.model,
            classification,
            interpretation,
            control,
            wheels,
            comparisons,
            presentation_event_probe_enabled,
        )
        print("\nDiagnostic complete", flush=True)
        print(f"  classification: {classification}", flush=True)
        print("  physical scanout: UNKNOWN", flush=True)
        print(f"  JSON: {comparison_path}", flush=True)
        print(f"  report: {report_path}", flush=True)
        return 0
    except (OSError, RuntimeError, KeyError, TypeError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
