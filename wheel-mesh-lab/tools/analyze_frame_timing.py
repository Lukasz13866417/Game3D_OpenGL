#!/usr/bin/env python3
"""Analyze live wheel preview timing without confusing submission with display.

The input is produced by wheel_mesh_lab --frame-timing-trace.  The report keeps
four questions separate: did swap-return cadence hitch, did the rendered pose
advance continuously, did the anti-alias representation pulse, and where was
CPU time spent.  GLFW exposes queue back-pressure here, not a confirmed scanout
timestamp.  This is intentionally not an FPS-average test.
"""

from __future__ import annotations

import argparse
from collections import Counter
import csv
import json
import math
from pathlib import Path
from typing import Mapping, Sequence

import numpy as np


REQUIRED_COLUMNS = {
    "frame",
    "loop_start_ms",
    "loop_delta_ms",
    "render_ms",
    "setup_ms",
    "scene_ms",
    "bloom_ms",
    "swap_wait_ms",
    "swap_return_ms",
    "swap_interval_ms",
    "phase_degrees",
    "nominal_hz",
    "spin_rps",
    "alias_envelope_cycles",
    "groove_contrast",
}

POSE_DELTA_COLUMNS = ("physical_pose_delta_degrees", "planned_delta_degrees")
FILTER_DELTA_COLUMNS = ("filter_delta_degrees", "planned_delta_degrees")
OPTIONAL_NUMERIC_COLUMNS = {
    "screenshot_ms",
    "poll_ms",
    "physical_pose_delta_degrees",
    "planned_delta_degrees",
    "filter_delta_degrees",
    "groove_cycles_per_frame",
    "temporal_blend",
    "cadence_title_update",
    "scheduled_phase_clock",
    "band_blend",
    "motion_band_energy_weight",
    "bloom_correction_blend",
    "core_intensity",
    "temporal_sample_count",
    "emission_draw_count",
    "gpu_disjoint_epoch",
    "gpu_query_latency_frames",
    "gpu_setup_ms",
    "gpu_scene_ms",
    "gpu_bloom_ms",
    "gpu_frame_ms",
    "scanout_valid",
    "scanout_counter_before",
    "scanout_counter_after",
    "scanout_counter_delta",
    "scanout_query_before_ms",
    "scanout_query_after_ms",
    "presentation_completion_events",
    "presentation_exact_mapping",
}
CATEGORICAL_COLUMNS = {
    "diagnostic_run_id",
    "gpu_timer_status",
    # Keep absolute nanosecond timestamps as decimal strings until they are
    # parsed as Python integers. Converting them through float64 loses low bits.
    "gpu_start_timestamp_ns",
    "gpu_end_timestamp_ns",
    "scanout_source",
    "presentation_completion_source",
}
GPU_TRACE_COLUMNS = {
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
SCANOUT_TRACE_COLUMNS = {
    "scanout_source",
    "scanout_valid",
    "scanout_counter_before",
    "scanout_counter_after",
    "scanout_counter_delta",
    "scanout_query_before_ms",
    "scanout_query_after_ms",
}
PRESENTATION_TRACE_COLUMNS = {
    "presentation_completion_source",
    "presentation_completion_events",
    "presentation_exact_mapping",
}
INSTRUMENTED_TRACE_COLUMNS = (
    GPU_TRACE_COLUMNS | SCANOUT_TRACE_COLUMNS | PRESENTATION_TRACE_COLUMNS
    | {"diagnostic_run_id"}
)
GPU_TIMER_STATUSES = {
    "not_requested",
    "pending",
    "ok",
    "ring_full",
    "disjoint",
    "invalid_timestamps",
    "pending_at_shutdown",
}
MINIMUM_VALID_INSTRUMENTATION_FRACTION = 0.90
MINIMUM_DIAGNOSTIC_SECONDS = 3.0
SEVERE_INTERVAL_NOMINAL_SLOTS = 4.0

TraceAnnotations = Mapping[str, Sequence[str]]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument("trace", type=Path)
    parser.add_argument(
        "--output-dir",
        type=Path,
        help="default: <trace stem>-analysis beside the trace",
    )
    return parser.parse_args()


def read_trace_details(
    path: Path,
) -> tuple[list[str], np.ndarray, dict[str, tuple[str, ...]]]:
    with path.open(newline="", encoding="utf-8") as source:
        reader = csv.DictReader(source, delimiter="\t")
        if reader.fieldnames is None:
            raise ValueError("timing trace has no header")
        if len(set(reader.fieldnames)) != len(reader.fieldnames):
            raise ValueError("timing trace contains duplicate column names")
        missing = REQUIRED_COLUMNS.difference(reader.fieldnames)
        if missing:
            raise ValueError(
                "timing trace is missing columns: " + ", ".join(sorted(missing))
            )
        if not any(name in reader.fieldnames for name in POSE_DELTA_COLUMNS):
            raise ValueError(
                "timing trace needs physical_pose_delta_degrees "
                "(or legacy planned_delta_degrees)"
            )
        rows = list(reader)
        header = set(reader.fieldnames)
        present_instrumented = header.intersection(INSTRUMENTED_TRACE_COLUMNS)
        if present_instrumented and present_instrumented != INSTRUMENTED_TRACE_COLUMNS:
            missing_instrumented = sorted(
                INSTRUMENTED_TRACE_COLUMNS.difference(header)
            )
            raise ValueError(
                "instrumented timing trace is missing columns: "
                + ", ".join(missing_instrumented)
            )
        fields = [
            name
            for name in reader.fieldnames
            if name in REQUIRED_COLUMNS or name in OPTIONAL_NUMERIC_COLUMNS
        ]
        annotations = {
            name: tuple(row[name] for row in rows)
            for name in reader.fieldnames
            if name in CATEGORICAL_COLUMNS
        }
    if len(rows) < 3:
        raise ValueError("timing trace needs at least three frames")
    try:
        values = np.asarray(
            [[float(row[field]) for field in fields] for row in rows],
            dtype=np.float64,
        )
    except (KeyError, ValueError) as error:
        raise ValueError("timing trace contains a non-numeric value") from error
    if not np.all(np.isfinite(values)):
        raise ValueError("timing trace contains a non-finite value")
    validate_trace(fields, values)
    validate_instrumentation(fields, values, annotations)
    return fields, values, annotations


def read_trace(path: Path) -> tuple[list[str], np.ndarray]:
    """Read legacy numeric data while accepting the additive trace schema."""
    fields, values, _ = read_trace_details(path)
    return fields, values


def validate_trace(fields: list[str], values: np.ndarray) -> None:
    if values.ndim != 2 or values.shape[0] < 3:
        raise ValueError("timing trace needs at least three frames")
    if not np.all(np.isfinite(values)):
        raise ValueError("timing trace contains a non-finite value")

    def get(name: str) -> np.ndarray:
        return values[:, fields.index(name)]

    frames = get("frame")
    if np.any(frames != np.floor(frames)) or np.any(np.diff(frames) != 1.0):
        raise ValueError("timing trace frame numbers must be consecutive integers")
    if np.any(get("nominal_hz") <= 0.0):
        raise ValueError("timing trace nominal_hz must be positive")
    nonnegative = [
        "loop_delta_ms",
        "render_ms",
        "setup_ms",
        "scene_ms",
        "bloom_ms",
        "swap_wait_ms",
        "swap_interval_ms",
    ]
    for name in nonnegative:
        if np.any(get(name) < 0.0):
            raise ValueError(f"timing trace {name} must not be negative")
    swap_return = get("swap_return_ms")
    if np.any(np.diff(swap_return) < 0.0):
        raise ValueError("timing trace swap_return_ms must be monotonic")
    observed_intervals = np.diff(swap_return)
    recorded_intervals = get("swap_interval_ms")[1:]
    tolerance = np.maximum(0.01, np.abs(observed_intervals) * 1.0e-5)
    if np.any(np.abs(observed_intervals - recorded_intervals) > tolerance):
        raise ValueError(
            "timing trace swap_interval_ms disagrees with swap_return_ms"
        )


def _annotation_values(
    annotations: TraceAnnotations | None,
    name: str,
    row_count: int,
) -> tuple[str, ...] | None:
    if annotations is None or name not in annotations:
        return None
    result = tuple(str(value) for value in annotations[name])
    if len(result) != row_count:
        raise ValueError(
            f"timing trace annotation {name} has {len(result)} rows; "
            f"expected {row_count}"
        )
    return result


def _parse_exact_nonnegative_integers(
    values: Sequence[str],
    field: str,
) -> tuple[int, ...]:
    result: list[int] = []
    for index, text in enumerate(values, start=2):
        if not text or not text.isascii() or not text.isdecimal():
            raise ValueError(
                f"timing trace {field} must contain exact non-negative "
                f"integers (line {index})"
            )
        result.append(int(text, 10))
    return tuple(result)


def _constant_annotation(
    annotations: TraceAnnotations | None,
    name: str,
    row_count: int,
) -> str | None:
    values = _annotation_values(annotations, name, row_count)
    if values is None:
        return None
    unique = set(values)
    if len(unique) != 1:
        raise ValueError(f"timing trace {name} must be constant across the run")
    return values[0]


def validate_instrumentation(
    fields: list[str],
    values: np.ndarray,
    annotations: TraceAnnotations | None,
) -> None:
    """Validate optional diagnostics without weakening the legacy schema."""
    row_count = values.shape[0]
    statuses = _annotation_values(annotations, "gpu_timer_status", row_count)
    if statuses is None:
        # Direct callers of analyze() and all historical traces remain valid.
        return

    required_numeric = (
        GPU_TRACE_COLUMNS | SCANOUT_TRACE_COLUMNS | PRESENTATION_TRACE_COLUMNS
    ).difference(CATEGORICAL_COLUMNS)
    missing_numeric = sorted(required_numeric.difference(fields))
    missing_annotations = sorted(
        CATEGORICAL_COLUMNS.difference(annotations or {})
    )
    if missing_numeric or missing_annotations:
        missing = missing_numeric + missing_annotations
        raise ValueError(
            "instrumented timing trace is missing columns: " + ", ".join(missing)
        )

    unknown_statuses = sorted(set(statuses).difference(GPU_TIMER_STATUSES))
    if unknown_statuses:
        raise ValueError(
            "timing trace has unknown gpu_timer_status values: "
            + ", ".join(unknown_statuses)
        )
    _constant_annotation(annotations, "diagnostic_run_id", row_count)
    scanout_source = _constant_annotation(
        annotations, "scanout_source", row_count
    )
    completion_source = _constant_annotation(
        annotations, "presentation_completion_source", row_count
    )
    if not scanout_source or not completion_source:
        raise ValueError("instrumentation source names must not be empty")

    starts = _parse_exact_nonnegative_integers(
        _annotation_values(
            annotations, "gpu_start_timestamp_ns", row_count
        ) or (),
        "gpu_start_timestamp_ns",
    )
    ends = _parse_exact_nonnegative_integers(
        _annotation_values(
            annotations, "gpu_end_timestamp_ns", row_count
        ) or (),
        "gpu_end_timestamp_ns",
    )

    def get(name: str) -> np.ndarray:
        return values[:, fields.index(name)]

    integer_fields = (
        "gpu_disjoint_epoch",
        "gpu_query_latency_frames",
        "scanout_valid",
        "scanout_counter_before",
        "scanout_counter_after",
        "scanout_counter_delta",
        "presentation_completion_events",
        "presentation_exact_mapping",
    )
    for name in integer_fields:
        data = get(name)
        if np.any(data != np.floor(data)):
            raise ValueError(f"timing trace {name} must contain integers")
    if np.any(get("gpu_disjoint_epoch") < 0.0):
        raise ValueError("timing trace gpu_disjoint_epoch must not be negative")
    if np.any(np.diff(get("gpu_disjoint_epoch")) < 0.0):
        raise ValueError("timing trace gpu_disjoint_epoch must be monotonic")
    if np.any(get("gpu_query_latency_frames") < -1.0):
        raise ValueError(
            "timing trace gpu_query_latency_frames must be at least -1"
        )

    gpu_stages = (
        "gpu_setup_ms",
        "gpu_scene_ms",
        "gpu_bloom_ms",
        "gpu_frame_ms",
    )
    for name in gpu_stages:
        if np.any(get(name) < -1.0):
            raise ValueError(f"timing trace {name} must be at least -1")

    for index, status in enumerate(statuses):
        stage_values = [float(get(name)[index]) for name in gpu_stages]
        if status != "ok":
            continue
        if starts[index] <= 0 or ends[index] < starts[index]:
            raise ValueError(
                f"timing trace has invalid ok GPU timestamps at frame {index}"
            )
        if get("gpu_query_latency_frames")[index] < 0.0:
            raise ValueError(
                f"timing trace ok GPU result lacks query latency at frame {index}"
            )
        if any(stage < 0.0 for stage in stage_values):
            raise ValueError(
                f"timing trace ok GPU result has unavailable stage at frame {index}"
            )
        stage_sum = sum(stage_values[:3])
        frame_ms = stage_values[3]
        tolerance = max(1.0e-6, frame_ms * 1.0e-6)
        if abs(stage_sum - frame_ms) > tolerance:
            raise ValueError(
                f"timing trace GPU stage sum disagrees with gpu_frame_ms at "
                f"frame {index}"
            )
        timestamp_ms = (ends[index] - starts[index]) * 1.0e-6
        if abs(timestamp_ms - frame_ms) > tolerance:
            raise ValueError(
                f"timing trace GPU timestamps disagree with gpu_frame_ms at "
                f"frame {index}"
            )
        if (index > 0 and statuses[index - 1] == "ok"
                and get("gpu_disjoint_epoch")[index]
                    == get("gpu_disjoint_epoch")[index - 1]
                and starts[index] <= starts[index - 1]):
            raise ValueError(
                "timing trace ok GPU start timestamps must increase within "
                f"one disjoint epoch (frame {index})"
            )

    scanout_valid = get("scanout_valid")
    if np.any((scanout_valid != 0.0) & (scanout_valid != 1.0)):
        raise ValueError("timing trace scanout_valid must be exactly 0 or 1")
    exact_mapping = get("presentation_exact_mapping")
    if np.any((exact_mapping != 0.0) & (exact_mapping != 1.0)):
        raise ValueError(
            "timing trace presentation_exact_mapping must be exactly 0 or 1"
        )
    if np.ptp(get("presentation_completion_events")) != 0.0:
        raise ValueError(
            "timing trace presentation_completion_events must be constant"
        )
    if np.ptp(exact_mapping) != 0.0:
        raise ValueError(
            "timing trace presentation_exact_mapping must be constant"
        )
    if np.any(get("presentation_completion_events") < 0.0):
        raise ValueError(
            "timing trace presentation_completion_events must not be negative"
        )
    if exact_mapping[0] == 1.0:
        event_count = int(get("presentation_completion_events")[0])
        if completion_source == "unavailable" or event_count != row_count:
            raise ValueError(
                "timing trace claims exact presentation mapping without one "
                "completion event per submitted frame"
            )

    for name in ("scanout_query_before_ms", "scanout_query_after_ms"):
        if np.any(get(name) < 0.0):
            raise ValueError(f"timing trace {name} must not be negative")
    counters_before = get("scanout_counter_before")
    counters_after = get("scanout_counter_after")
    counter_delta = get("scanout_counter_delta")
    if np.any(counters_before < 0.0) or np.any(counters_after < 0.0):
        raise ValueError("timing trace scanout counters must not be negative")
    if np.any(counter_delta < 0.0):
        raise ValueError("timing trace scanout_counter_delta must not be negative")
    valid_indices = np.flatnonzero(scanout_valid == 1.0)
    for index in valid_indices:
        expected = (
            int(counters_after[index]) - int(counters_before[index])
        ) & 0xFFFFFFFF
        if expected != int(counter_delta[index]):
            raise ValueError(
                "timing trace scanout_counter_delta disagrees with counters at "
                f"frame {index}"
            )


def column(fields: list[str], values: np.ndarray, name: str) -> np.ndarray:
    return values[:, fields.index(name)]


def safe_percentile(values: np.ndarray, percentile: float) -> float:
    return float(np.percentile(values, percentile)) if values.size else 0.0


def correlation(left: np.ndarray, right: np.ndarray) -> float:
    if left.size < 3 or np.std(left) <= 1e-12 or np.std(right) <= 1e-12:
        return 0.0
    return float(np.corrcoef(left, right)[0, 1])


def pose_swap_lag(
    pose_delta: np.ndarray,
    swap_proxy_delta: np.ndarray,
) -> tuple[int, dict[str, float]]:
    scores: dict[str, float] = {}
    for lag in range(-2, 3):
        if lag < 0:
            left = pose_delta[:lag]
            right = swap_proxy_delta[-lag:]
        elif lag > 0:
            left = pose_delta[lag:]
            right = swap_proxy_delta[:-lag]
        else:
            left = pose_delta
            right = swap_proxy_delta
        scores[str(lag)] = correlation(left, right)
    best = max(
        range(-2, 3),
        key=lambda item: (scores[str(item)], -abs(item)),
    )
    return best, scores


def dominant_modulation(
    values: np.ndarray,
    timestamps_seconds: np.ndarray,
    nominal_sample_rate: float,
) -> tuple[float, float]:
    if (values.size < 16 or timestamps_seconds.size != values.size
            or nominal_sample_rate <= 0.0):
        return 0.0, 0.0
    unique_times, unique_indices = np.unique(timestamps_seconds, return_index=True)
    if unique_times.size < 16 or unique_times[-1] <= unique_times[0]:
        return 0.0, 0.0
    sample_count = max(
        16,
        int(math.floor(
            (unique_times[-1] - unique_times[0]) * nominal_sample_rate
        )) + 1,
    )
    regular_times = np.linspace(unique_times[0], unique_times[-1], sample_count)
    signal = np.interp(regular_times, unique_times, values[unique_indices])
    signal = np.clip(signal, 0.0, 2.0)
    signal = signal - np.median(signal)
    window = np.hanning(signal.size)
    spectrum = np.abs(np.fft.rfft(signal * window)) ** 2
    frequencies = np.fft.rfftfreq(signal.size, 1.0 / nominal_sample_rate)
    eligible = (
        (frequencies >= 0.5)
        & (frequencies <= min(30.0, nominal_sample_rate / 2.0))
    )
    if not np.any(eligible):
        return 0.0, 0.0
    eligible_indices = np.flatnonzero(eligible)
    index = int(eligible_indices[np.argmax(spectrum[eligible])])
    noise = float(np.median(spectrum[eligible]))
    snr = float(spectrum[index] / max(noise, 1e-20))
    return float(frequencies[index]), snr


def distribution(values: np.ndarray) -> dict[str, float | None]:
    if values.size == 0:
        return {"median": None, "p99": None, "maximum": None}
    return {
        "median": float(np.median(values)),
        "p99": safe_percentile(values, 99.0),
        "maximum": float(np.max(values)),
    }


def analyze_instrumentation(
    fields: list[str],
    values: np.ndarray,
    annotations: TraceAnnotations | None,
    nominal_interval: np.ndarray,
    long_swap_event: np.ndarray,
    duration_seconds: float,
) -> tuple[dict, dict, dict, dict[str, str], dict[str, Sequence]]:
    sample_count = values.shape[0] - 1
    empty_metric: dict[str, Sequence] = {
        "gpu_timer_status": ["legacy_unavailable"] * sample_count,
        "gpu_frame_ms": np.full(sample_count, -1.0),
        "gpu_setup_ms": np.full(sample_count, -1.0),
        "gpu_scene_ms": np.full(sample_count, -1.0),
        "gpu_bloom_ms": np.full(sample_count, -1.0),
        "gpu_over_budget": np.zeros(sample_count, dtype=bool),
        "scanout_counter_delta": np.zeros(sample_count),
        "scanout_retrace_long_event": np.zeros(sample_count, dtype=bool),
    }
    statuses_all = _annotation_values(
        annotations, "gpu_timer_status", values.shape[0]
    )
    if statuses_all is None:
        gpu = {
            "available": False,
            "status_counts": {},
            "valid_frames": 0,
            "valid_fraction": 0.0,
            "stages_ms": {},
            "gpu_start_interval_ms": distribution(np.asarray([])),
            "over_0_8_nominal_budget": 0,
            "over_nominal_budget": 0,
            "long_swap_with_gpu_over_budget": 0,
            "long_swap_with_valid_gpu_but_not_over_budget": 0,
        }
        scanout = {
            "available": False,
            "source": None,
            "valid_frames": 0,
            "valid_fraction": 0.0,
            "counter_interval_delta": distribution(np.asarray([])),
            "long_retrace_intervals": 0,
            "measurement_scope": "unavailable",
        }
        presentation = {
            "source": None,
            "completion_events": 0,
            "exact_mapping": False,
            "status": "UNAVAILABLE",
            "cadence_from_per_event_timestamps_available": False,
        }
        return (
            gpu,
            scanout,
            presentation,
            {
                "gpu_render_budget": "UNKNOWN",
                "scanout_retrace": "UNKNOWN",
                "presentation_completion_cadence": "UNKNOWN",
            },
            empty_metric,
        )

    validate_instrumentation(fields, values, annotations)
    sample = values[1:]

    def get(name: str) -> np.ndarray:
        return sample[:, fields.index(name)]

    statuses = tuple(statuses_all[1:])
    status_counts = dict(sorted(Counter(statuses).items()))
    gpu_valid = np.asarray([status == "ok" for status in statuses])
    gpu_valid_count = int(np.count_nonzero(gpu_valid))
    gpu_valid_fraction = gpu_valid_count / max(sample_count, 1)
    gpu_frame = get("gpu_frame_ms")
    gpu_stages = {
        "setup": get("gpu_setup_ms"),
        "scene": get("gpu_scene_ms"),
        "bloom": get("gpu_bloom_ms"),
        "frame": gpu_frame,
    }
    stage_stats = {
        name: distribution(stage[gpu_valid])
        for name, stage in gpu_stages.items()
    }
    gpu_near_budget = gpu_valid & (gpu_frame > nominal_interval * 0.8)
    gpu_over_budget = gpu_valid & (gpu_frame > nominal_interval)

    starts_all = _parse_exact_nonnegative_integers(
        _annotation_values(
            annotations, "gpu_start_timestamp_ns", values.shape[0]
        ) or (),
        "gpu_start_timestamp_ns",
    )
    starts = np.asarray(starts_all[1:], dtype=object)
    epochs = get("gpu_disjoint_epoch").astype(np.int64)
    start_intervals: list[float] = []
    for index in range(1, sample_count):
        if (gpu_valid[index] and gpu_valid[index - 1]
                and epochs[index] == epochs[index - 1]):
            difference = int(starts[index]) - int(starts[index - 1])
            if difference >= 0:
                start_intervals.append(difference * 1.0e-6)
    start_interval_array = np.asarray(start_intervals, dtype=np.float64)
    query_latency = get("gpu_query_latency_frames")[gpu_valid]
    largest_stage = None
    valid_stage_names = ("setup", "scene", "bloom")
    if gpu_valid_count:
        largest_stage = max(
            valid_stage_names,
            key=lambda name: float(stage_stats[name]["maximum"] or 0.0),
        )
    gpu_requested = any(status != "not_requested" for status in statuses)
    if not gpu_requested:
        gpu_verdict = "UNKNOWN"
    elif (duration_seconds < MINIMUM_DIAGNOSTIC_SECONDS
            or gpu_valid_fraction < MINIMUM_VALID_INSTRUMENTATION_FRACTION):
        gpu_verdict = "INCONCLUSIVE"
    elif (safe_percentile(gpu_frame[gpu_valid], 99.0)
            <= float(np.median(nominal_interval)) * 0.5
            and not np.any(gpu_over_budget)):
        gpu_verdict = "PASS"
    else:
        gpu_verdict = "FAIL"
    gpu = {
        "available": gpu_requested,
        "status_counts": status_counts,
        "valid_frames": gpu_valid_count,
        "valid_fraction": gpu_valid_fraction,
        "minimum_valid_fraction": MINIMUM_VALID_INSTRUMENTATION_FRACTION,
        "stages_ms": stage_stats,
        "largest_maximum_stage": largest_stage,
        "gpu_start_interval_ms": distribution(start_interval_array),
        "query_latency_frames": distribution(query_latency),
        "over_0_8_nominal_budget": int(np.count_nonzero(gpu_near_budget)),
        "over_nominal_budget": int(np.count_nonzero(gpu_over_budget)),
        "long_swap_with_gpu_over_budget": int(
            np.count_nonzero(long_swap_event & gpu_over_budget)
        ),
        "long_swap_with_valid_gpu_but_not_over_budget": int(
            np.count_nonzero(long_swap_event & gpu_valid & ~gpu_over_budget)
        ),
        "clock_domain_note": (
            "GPU timestamp differences are valid within one disjoint epoch; "
            "they are not subtracted from CPU steady-clock timestamps."
        ),
    }

    scanout_source = _constant_annotation(
        annotations, "scanout_source", values.shape[0]
    )
    scanout_valid = get("scanout_valid") == 1.0
    scanout_valid_count = int(np.count_nonzero(scanout_valid))
    scanout_fraction = scanout_valid_count / max(sample_count, 1)
    scanout_after = get("scanout_counter_after").astype(np.uint64)
    scanout_per_swap = get("scanout_counter_delta")
    retrace_interval_values: list[int] = []
    retrace_interval_indices: list[int] = []
    for index in range(1, sample_count):
        if scanout_valid[index] and scanout_valid[index - 1]:
            retrace_interval_values.append(
                (int(scanout_after[index]) - int(scanout_after[index - 1]))
                & 0xFFFFFFFF
            )
            retrace_interval_indices.append(index)
    retrace_intervals = np.asarray(retrace_interval_values, dtype=np.float64)
    positive_retrace = retrace_intervals[retrace_intervals > 0.0]
    nominal_retrace_delta = (
        max(1, int(round(float(np.median(positive_retrace)))))
        if positive_retrace.size else 1
    )
    long_retrace_values = retrace_intervals > nominal_retrace_delta * 1.5
    long_retrace_event = np.zeros(sample_count, dtype=bool)
    for index, is_long in zip(
        retrace_interval_indices, long_retrace_values, strict=True
    ):
        long_retrace_event[index] = bool(is_long)
    scanout_available = scanout_source not in (None, "unavailable")
    if not scanout_available or scanout_valid_count == 0:
        scanout_verdict = "UNKNOWN"
    elif (duration_seconds < MINIMUM_DIAGNOSTIC_SECONDS
            or scanout_fraction < MINIMUM_VALID_INSTRUMENTATION_FRACTION):
        scanout_verdict = "INCONCLUSIVE"
    elif np.any(long_retrace_values):
        scanout_verdict = "FAIL"
    else:
        scanout_verdict = "PASS"
    scanout = {
        "available": scanout_available,
        "source": scanout_source,
        "valid_frames": scanout_valid_count,
        "valid_fraction": scanout_fraction,
        "minimum_valid_fraction": MINIMUM_VALID_INSTRUMENTATION_FRACTION,
        "nominal_counter_delta": nominal_retrace_delta,
        "counter_interval_delta": distribution(retrace_intervals),
        "per_swap_counter_delta": distribution(scanout_per_swap[scanout_valid]),
        "long_swap_retrace_delta": distribution(
            scanout_per_swap[scanout_valid & long_swap_event]
        ),
        "long_retrace_intervals": int(np.count_nonzero(long_retrace_values)),
        "zero_retrace_intervals": int(
            np.count_nonzero(retrace_intervals == 0.0)
        ),
        "query_before_ms": distribution(
            get("scanout_query_before_ms")[scanout_valid]
        ),
        "query_after_ms": distribution(
            get("scanout_query_after_ms")[scanout_valid]
        ),
        "measurement_scope": (
            "physical display-pipe retrace counter only; it does not identify "
            "which window buffer was scanned out"
        ),
    }

    completion_source = _constant_annotation(
        annotations, "presentation_completion_source", values.shape[0]
    )
    completion_events = int(get("presentation_completion_events")[0])
    exact_mapping = bool(get("presentation_exact_mapping")[0])
    if exact_mapping:
        presentation_status = "SUPPORTED_EXACT_MAPPING"
        presentation_verdict = "INCONCLUSIVE"
    elif completion_source not in (None, "unavailable"):
        presentation_status = "NO_EXACT_MAPPING"
        presentation_verdict = "UNKNOWN"
    else:
        presentation_status = "UNAVAILABLE"
        presentation_verdict = "UNKNOWN"
    presentation = {
        "source": completion_source,
        "completion_events": completion_events,
        "exact_mapping": exact_mapping,
        "status": presentation_status,
        "cadence_from_per_event_timestamps_available": False,
        "measurement_scope": (
            "Aggregate completion evidence cannot establish presentation "
            "cadence without the mapped per-event timestamp trace."
        ),
    }

    metrics = {
        "gpu_timer_status": statuses,
        "gpu_frame_ms": gpu_frame,
        "gpu_setup_ms": get("gpu_setup_ms"),
        "gpu_scene_ms": get("gpu_scene_ms"),
        "gpu_bloom_ms": get("gpu_bloom_ms"),
        "gpu_over_budget": gpu_over_budget,
        "scanout_counter_delta": scanout_per_swap,
        "scanout_retrace_long_event": long_retrace_event,
    }
    return (
        gpu,
        scanout,
        presentation,
        {
            "gpu_render_budget": gpu_verdict,
            "scanout_retrace": scanout_verdict,
            "presentation_completion_cadence": presentation_verdict,
        },
        metrics,
    )


def analyze(
    fields: list[str],
    values: np.ndarray,
    annotations: TraceAnnotations | None = None,
) -> tuple[dict, list[dict]]:
    validate_trace(fields, values)
    validate_instrumentation(fields, values, annotations)
    # Frame zero has no preceding swap-return sample.
    sample = values[1:]
    get = lambda name: sample[:, fields.index(name)]
    optional = lambda name, default=0.0: (
        get(name) if name in fields else np.full(sample.shape[0], default)
    )
    swap_interval = get("swap_interval_ms")
    loop_delta = get("loop_delta_ms")
    nominal_hz = get("nominal_hz")
    spin_rps = get("spin_rps")
    pose_delta_field = next(
        name for name in POSE_DELTA_COLUMNS if name in fields
    )
    filter_delta_field = next(
        (name for name in FILTER_DELTA_COLUMNS if name in fields),
        pose_delta_field,
    )
    pose_delta = get(pose_delta_field)
    filter_delta = get(filter_delta_field)
    nominal_interval = 1000.0 / nominal_hz
    swap_proxy_delta = spin_rps * 360.0 * swap_interval / 1000.0
    nominal_pose_delta = spin_rps * 360.0 / nominal_hz
    moving = np.abs(swap_proxy_delta) > 1e-8
    speed_ratio = np.ones_like(swap_proxy_delta)
    speed_ratio[moving] = pose_delta[moving] / swap_proxy_delta[moving]
    nominally_moving = np.abs(nominal_pose_delta) > 1e-8
    pose_step_ratio = np.ones_like(pose_delta)
    pose_step_ratio[nominally_moving] = (
        pose_delta[nominally_moving]
        / nominal_pose_delta[nominally_moving]
    )
    filter_step_ratio = np.ones_like(filter_delta)
    filter_step_ratio[nominally_moving] = (
        filter_delta[nominally_moving]
        / nominal_pose_delta[nominally_moving]
    )
    cumulative_slots = np.cumsum(swap_interval / nominal_interval)
    slot_boundaries = np.floor(
        np.concatenate(([0.0], cumulative_slots)) + 0.5
    ).astype(np.int64)
    cadence_slots = np.diff(slot_boundaries)
    late_slots = np.maximum(0, cadence_slots - 1)
    skipped_submission_slots = cadence_slots == 0
    net_unsubmitted_slots = max(
        0,
        int(slot_boundaries[-1]) - int(cadence_slots.size),
    )
    long_event = swap_interval > nominal_interval * 1.5
    severe_event = swap_interval > (
        nominal_interval * SEVERE_INTERVAL_NOMINAL_SLOTS
    )
    best_lag, lag_scores = pose_swap_lag(pose_delta, swap_proxy_delta)
    best_lag_correlation = lag_scores[str(best_lag)]
    meaningful_best_lag = best_lag if best_lag_correlation >= 0.5 else None
    swap_return = get("swap_return_ms")
    beat_hz, beat_snr = dominant_modulation(
        speed_ratio[moving],
        swap_return[moving] / 1000.0,
        float(np.median(nominal_hz)),
    )

    contrast = get("groove_contrast")
    alias_cycles = get("alias_envelope_cycles")
    temporal_blend = optional("temporal_blend")
    band_blend = optional("band_blend")
    motion_band_energy = optional("motion_band_energy_weight")
    bloom_correction = optional("bloom_correction_blend")
    core_intensity = optional("core_intensity", 1.0)
    temporal_sample_count = optional("temporal_sample_count", 1.0)
    emission_draw_count = optional("emission_draw_count", 1.0)
    contrast_step = np.abs(np.diff(contrast, prepend=contrast[0]))
    alias_step = np.abs(np.diff(alias_cycles, prepend=alias_cycles[0]))
    event_times = swap_return[long_event] / 1000.0
    event_spacing = np.diff(event_times)
    severe_event_times = swap_return[severe_event] / 1000.0
    severe_event_spacing = np.diff(severe_event_times)
    render_ms = get("render_ms")
    render_near_budget_event = render_ms > nominal_interval * 0.8
    render_over_budget_event = render_ms > nominal_interval
    duration_seconds = float((swap_return[-1] - swap_return[0]) / 1000.0)
    spin_scale = max(float(np.max(np.abs(spin_rps))), 1.0)
    stable_motion = (
        float(np.ptp(spin_rps)) <= spin_scale * 1.0e-5
        and float(np.ptp(nominal_hz))
            <= max(float(np.median(nominal_hz)), 1.0) * 1.0e-5
    )

    def variation(values_to_measure: np.ndarray) -> tuple[float, float]:
        return (
            float(np.ptp(values_to_measure)),
            float(np.max(np.abs(np.diff(
                values_to_measure, prepend=values_to_measure[0]
            )))),
        )

    temporal_blend_range, temporal_blend_step = variation(temporal_blend)
    band_blend_range, band_blend_step = variation(band_blend)
    motion_energy_range, motion_energy_step = variation(motion_band_energy)
    bloom_range, bloom_step = variation(bloom_correction)
    core_range, core_step = variation(core_intensity)
    sample_count_changes = int(np.count_nonzero(np.diff(temporal_sample_count)))
    emission_draw_changes = int(np.count_nonzero(np.diff(emission_draw_count)))

    (
        gpu_timing,
        scanout_retrace,
        presentation_completion,
        instrumentation_verdicts,
        instrumentation_metrics,
    ) = analyze_instrumentation(
        fields,
        values,
        annotations,
        nominal_interval,
        long_event,
        duration_seconds,
    )

    summary = {
        "frames_analyzed": int(sample.shape[0]),
        "duration_seconds": duration_seconds,
        "measurement_support": {
            "physical_pose_delta_available": (
                "physical_pose_delta_degrees" in fields
            ),
            "confirmed_display_timestamps_available": False,
            "presentation_exact_mapping_available": bool(
                presentation_completion["exact_mapping"]
            ),
            "gpu_timer_valid_fraction": gpu_timing["valid_fraction"],
            "scanout_retrace_valid_fraction": scanout_retrace["valid_fraction"],
            "constant_motion_and_nominal_cadence": stable_motion,
            "minimum_diagnostic_seconds": MINIMUM_DIAGNOSTIC_SECONDS,
        },
        "nominal_hz": float(np.median(nominal_hz)),
        "swap_interval_ms": {
            "median": float(np.median(swap_interval)),
            "p99": safe_percentile(swap_interval, 99.0),
            "maximum": float(np.max(swap_interval)),
        },
        "render_ms": {
            "median": float(np.median(render_ms)),
            "p99": safe_percentile(render_ms, 99.0),
            "maximum": float(np.max(render_ms)),
            "over_0_8_nominal_budget": int(
                np.count_nonzero(render_near_budget_event)
            ),
            "over_nominal_budget": int(np.count_nonzero(render_over_budget_event)),
            "setup_maximum": float(np.max(get("setup_ms"))),
            "scene_maximum": float(np.max(get("scene_ms"))),
            "bloom_maximum": float(np.max(get("bloom_ms"))),
        },
        "swap_return_cadence_proxy": {
            "long_interval_events": int(np.count_nonzero(long_event)),
            "severe_interval_threshold_nominal_slots": (
                SEVERE_INTERVAL_NOMINAL_SLOTS
            ),
            "severe_interval_events": int(np.count_nonzero(severe_event)),
            "late_slots_before_queued_submissions": int(np.sum(late_slots)),
            "zero_slot_submissions": int(
                np.count_nonzero(skipped_submission_slots)
            ),
            "net_refresh_slots_without_submission": net_unsubmitted_slots,
            "event_spacing_median_seconds": (
                float(np.median(event_spacing)) if event_spacing.size else None
            ),
            "severe_event_spacing_median_seconds": (
                float(np.median(severe_event_spacing))
                if severe_event_spacing.size else None
            ),
        },
        "gpu_timing": gpu_timing,
        "scanout_retrace": scanout_retrace,
        "presentation_completion": presentation_completion,
        "pose_timing": {
            "pose_step_ratio_p01": safe_percentile(
                pose_step_ratio[nominally_moving], 1.0
            ),
            "pose_step_ratio_median": safe_percentile(
                pose_step_ratio[nominally_moving], 50.0
            ),
            "pose_step_ratio_p99": safe_percentile(
                pose_step_ratio[nominally_moving], 99.0
            ),
            "slow_pose_steps_below_0_8": int(
                np.count_nonzero(pose_step_ratio[nominally_moving] < 0.8)
            ),
            "catch_up_pose_steps_above_1_2": int(
                np.count_nonzero(pose_step_ratio[nominally_moving] > 1.2)
            ),
            "filter_step_ratio_p01": safe_percentile(
                filter_step_ratio[nominally_moving], 1.0
            ),
            "filter_step_ratio_p99": safe_percentile(
                filter_step_ratio[nominally_moving], 99.0
            ),
            "swap_proxy_speed_ratio_p01": safe_percentile(
                speed_ratio[moving], 1.0
            ),
            "swap_proxy_speed_ratio_median": safe_percentile(
                speed_ratio[moving], 50.0
            ),
            "swap_proxy_speed_ratio_p99": safe_percentile(
                speed_ratio[moving], 99.0
            ),
            "swap_proxy_slow_frames_below_0_55": int(
                np.count_nonzero(speed_ratio[moving] < 0.55)
            ),
            "swap_proxy_catch_up_frames_above_1_8": int(
                np.count_nonzero(speed_ratio[moving] > 1.8)
            ),
            "best_swap_return_lag_frames": (
                int(meaningful_best_lag)
                if meaningful_best_lag is not None else None
            ),
            "best_swap_return_lag_correlation": best_lag_correlation,
            "lag_correlations": lag_scores,
            "dominant_modulation_hz": beat_hz,
            "dominant_modulation_snr": beat_snr,
        },
        "representation": {
            "groove_contrast_range": float(np.ptp(contrast)),
            "maximum_contrast_step": float(np.max(contrast_step)),
            "alias_envelope_range": float(np.ptp(alias_cycles)),
            "maximum_alias_step": float(np.max(alias_step)),
            "temporal_blend_range": temporal_blend_range,
            "maximum_temporal_blend_step": temporal_blend_step,
            "band_blend_range": band_blend_range,
            "maximum_band_blend_step": band_blend_step,
            "motion_band_energy_range": motion_energy_range,
            "maximum_motion_band_energy_step": motion_energy_step,
            "bloom_correction_range": bloom_range,
            "maximum_bloom_correction_step": bloom_step,
            "core_intensity_range": core_range,
            "maximum_core_intensity_step": core_step,
            "temporal_sample_count_changes": sample_count_changes,
            "emission_draw_count_changes": emission_draw_changes,
        },
    }
    representation_stable = (
        summary["representation"]["groove_contrast_range"] <= 0.05
        and summary["representation"]["alias_envelope_range"] <= 0.05
        and temporal_blend_range <= 0.05
        and band_blend_range <= 0.05
        and motion_energy_range <= 0.05
        and bloom_range <= 0.05
        and core_range <= 0.05
        and sample_count_changes == 0
        and emission_draw_changes == 0
    )
    summary["verdicts"] = {
        "swap_return_cadence_proxy": (
            "INCONCLUSIVE"
            if duration_seconds < MINIMUM_DIAGNOSTIC_SECONDS
            else (
                "PASS"
                if summary["swap_return_cadence_proxy"]["long_interval_events"] == 0
                else "FAIL"
            )
        ),
        "physical_display_cadence": "UNKNOWN",
        "cpu_render_budget": (
            "PASS"
            if summary["render_ms"]["p99"]
                <= float(np.median(nominal_interval)) * 0.5
            and summary["render_ms"]["over_nominal_budget"] == 0
            else "FAIL"
        ),
        "pose_step_continuity": (
            "UNKNOWN"
            if "physical_pose_delta_degrees" not in fields
            else (
                "PASS"
                if summary["pose_timing"]["slow_pose_steps_below_0_8"] == 0
                and summary["pose_timing"]["catch_up_pose_steps_above_1_2"] == 0
                else "FAIL"
            )
        ),
        "representation_stability": (
            "INCONCLUSIVE"
            if not stable_motion
            else ("PASS" if representation_stable else "FAIL")
        ),
        **instrumentation_verdicts,
    }
    if (summary["verdicts"]["gpu_render_budget"] == "FAIL"
            and summary["verdicts"]["cpu_render_budget"] == "PASS"):
        summary["diagnosis"] = "measured_gpu_execution_can_exhaust_frame_budget"
    elif (summary["verdicts"]["swap_return_cadence_proxy"] == "FAIL"
            and summary["verdicts"]["cpu_render_budget"] == "PASS"
            and summary["verdicts"]["gpu_render_budget"] == "PASS"):
        summary["diagnosis"] = (
            "queue_back_pressure_outside_measured_cpu_and_gpu_render"
        )
    elif (summary["verdicts"]["swap_return_cadence_proxy"] == "FAIL"
            and summary["verdicts"]["cpu_render_budget"] == "PASS"):
        summary["diagnosis"] = "queue_back_pressure_outside_measured_cpu_render"
    elif summary["verdicts"]["cpu_render_budget"] == "FAIL":
        summary["diagnosis"] = "measured_cpu_render_can_exhaust_frame_budget"
    else:
        summary["diagnosis"] = "no_measured_live_timing_fault"

    metric_rows: list[dict] = []
    frame = get("frame")
    for index in range(sample.shape[0]):
        metric_rows.append({
            "frame": int(frame[index]),
            "swap_return_ms": float(swap_return[index]),
            "swap_interval_ms": float(swap_interval[index]),
            "loop_delta_ms": float(loop_delta[index]),
            "nominal_interval_ms": float(nominal_interval[index]),
            "cadence_slots": int(cadence_slots[index]),
            "late_slots": int(late_slots[index]),
            "zero_slot_submission": int(skipped_submission_slots[index]),
            "physical_pose_delta_degrees": float(pose_delta[index]),
            "filter_delta_degrees": float(filter_delta[index]),
            "swap_proxy_delta_degrees": float(swap_proxy_delta[index]),
            "pose_step_ratio": float(pose_step_ratio[index]),
            "swap_proxy_speed_ratio": float(speed_ratio[index]),
            "groove_contrast": float(contrast[index]),
            "alias_envelope_cycles": float(alias_cycles[index]),
            "temporal_blend": float(temporal_blend[index]),
            "band_blend": float(band_blend[index]),
            "motion_band_energy_weight": float(motion_band_energy[index]),
            "bloom_correction_blend": float(bloom_correction[index]),
            "core_intensity": float(core_intensity[index]),
            "temporal_sample_count": int(temporal_sample_count[index]),
            "emission_draw_count": int(emission_draw_count[index]),
            "long_interval_event": int(long_event[index]),
            "severe_interval_event": int(severe_event[index]),
            "gpu_timer_status": instrumentation_metrics[
                "gpu_timer_status"
            ][index],
            "gpu_setup_ms": float(
                instrumentation_metrics["gpu_setup_ms"][index]
            ),
            "gpu_scene_ms": float(
                instrumentation_metrics["gpu_scene_ms"][index]
            ),
            "gpu_bloom_ms": float(
                instrumentation_metrics["gpu_bloom_ms"][index]
            ),
            "gpu_frame_ms": float(
                instrumentation_metrics["gpu_frame_ms"][index]
            ),
            "gpu_over_budget": int(
                instrumentation_metrics["gpu_over_budget"][index]
            ),
            "scanout_counter_delta": int(
                instrumentation_metrics["scanout_counter_delta"][index]
            ),
            "scanout_retrace_long_event": int(
                instrumentation_metrics["scanout_retrace_long_event"][index]
            ),
        })
    return summary, metric_rows


def write_metrics(path: Path, rows: list[dict]) -> None:
    with path.open("w", newline="", encoding="utf-8") as destination:
        writer = csv.DictWriter(destination, fieldnames=list(rows[0]), delimiter="\t")
        writer.writeheader()
        writer.writerows(rows)


def svg_polyline(
    x: np.ndarray,
    y: np.ndarray,
    left: float,
    top: float,
    width: float,
    height: float,
    y_min: float,
    y_max: float,
    color: str,
) -> str:
    if x.size > 5000:
        indices = np.linspace(0, x.size - 1, 5000).astype(int)
        x = x[indices]
        y = y[indices]
    x_span = max(float(x[-1] - x[0]), 1e-9)
    y_span = max(y_max - y_min, 1e-9)
    points = " ".join(
        f"{left + (vx - x[0]) / x_span * width:.2f},"
        f"{top + (1.0 - (np.clip(vy, y_min, y_max) - y_min) / y_span) * height:.2f}"
        for vx, vy in zip(x, y, strict=True)
    )
    return f'<polyline fill="none" stroke="{color}" stroke-width="1.5" points="{points}"/>'


def write_svg(path: Path, rows: list[dict], summary: dict) -> None:
    width, height = 1200, 760
    left, plot_width = 82.0, 1080.0
    panel_height = 180.0
    times = np.asarray([row["swap_return_ms"] for row in rows]) / 1000.0
    swap = np.asarray([row["swap_interval_ms"] for row in rows])
    ratio = np.asarray([row["pose_step_ratio"] for row in rows])
    contrast = np.asarray([row["groove_contrast"] for row in rows])
    alias = np.asarray([row["alias_envelope_cycles"] for row in rows])
    nominal_ms = 1000.0 / summary["nominal_hz"]
    events = [row for row in rows if row["long_interval_event"]]

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#0d141d"/>',
        '<style>text{font-family:monospace;fill:#d9e5f2}.muted{fill:#8fa3b8}</style>',
        '<text x="36" y="34" font-size="22">Wheel live timing (swap-return proxy)</text>',
        f'<text x="36" y="58" class="muted" font-size="14">cadence-proxy={summary["verdicts"]["swap_return_cadence_proxy"]}  CPU={summary["verdicts"]["cpu_render_budget"]}  pose={summary["verdicts"]["pose_step_continuity"]}  representation={summary["verdicts"]["representation_stability"]}</text>',
    ]
    panels = [
        (92.0, "swap interval (ms)", swap, 0.0, max(nominal_ms * 2.0, safe_percentile(swap, 99.0) * 1.1), "#6ee7d0"),
        (322.0, "pose step / nominal pose step", ratio, 0.0, 2.0, "#ffd166"),
        (552.0, "groove contrast (green) and alias cycles (purple)", contrast, 0.0, 1.0, "#60df8a"),
    ]
    for top, label, series, y_min, y_max, color in panels:
        parts.extend([
            f'<rect x="{left}" y="{top}" width="{plot_width}" height="{panel_height}" fill="#101c28" stroke="#263747"/>',
            f'<text x="{left}" y="{top - 12}" font-size="15">{label}</text>',
            svg_polyline(times, series, left, top, plot_width, panel_height, y_min, y_max, color),
        ])
        for event in events:
            event_x = left + (event["swap_return_ms"] / 1000.0 - times[0]) / max(times[-1] - times[0], 1e-9) * plot_width
            parts.append(
                f'<line x1="{event_x:.2f}" y1="{top}" x2="{event_x:.2f}" y2="{top + panel_height}" stroke="#ff5d73" stroke-width="1" opacity="0.75"/>'
            )
    parts.append(svg_polyline(times, np.minimum(alias, 1.0), left, 552.0, plot_width, panel_height, 0.0, 1.0, "#b98cff"))
    parts.append('</svg>')
    path.write_text("\n".join(parts) + "\n", encoding="utf-8")


def write_report(path: Path, trace: Path, summary: dict) -> None:
    cadence = summary["swap_return_cadence_proxy"]
    pose = summary["pose_timing"]
    representation = summary["representation"]
    lag = pose["best_swap_return_lag_frames"]
    lag_text = "not significant" if lag is None else f"{lag:+d} frame(s)"
    gpu = summary["gpu_timing"]
    scanout = summary["scanout_retrace"]
    presentation = summary["presentation_completion"]
    gpu_frame = gpu.get("stages_ms", {}).get("frame", {})
    if not gpu["available"]:
        gpu_line = "- GPU timer: unavailable in this trace."
    elif gpu["valid_frames"] == 0:
        gpu_line = (
            "- GPU timer was requested, but it produced no valid frame results; "
            f"statuses: {gpu['status_counts']}."
        )
    else:
        gpu_line = (
            f"- GPU timer: {gpu['valid_frames']} valid frames "
            f"({gpu['valid_fraction']:.1%}); frame p99 "
            f"{float(gpu_frame['p99']):.3f} ms, maximum "
            f"{float(gpu_frame['maximum']):.3f} ms; largest maximum stage: "
            f"{gpu['largest_maximum_stage']}."
        )
    scanout_line = (
        "- Physical retrace clock: unavailable in this trace."
        if not scanout["available"]
        else (
            f"- Physical retrace clock `{scanout['source']}`: "
            f"{scanout['long_retrace_intervals']} long counter intervals; "
            "this is display-pipe timing, not window-buffer presentation."
        )
    )
    presentation_line = (
        f"- Presentation completion `{presentation['source'] or 'unavailable'}`: "
        f"{presentation['completion_events']} events; status "
        f"`{presentation['status']}`."
    )
    lines = [
        "# Wheel live timing analysis",
        "",
        f"Source: `{trace}`",
        "",
        "| Gate | Result |",
        "|---|---|",
        *[
            f"| {name.replace('_', ' ')} | **{value}** |"
            for name, value in summary["verdicts"].items()
        ],
        "",
        f"- Analyzed {summary['frames_analyzed']} frames over {summary['duration_seconds']:.3f} s at nominal {summary['nominal_hz']:.3f} Hz.",
        f"- Long swap-return intervals: {cadence['long_interval_events']}; gross late slots before queued returns: {cadence['late_slots_before_queued_submissions']}.",
        f"- Zero-slot/queued submissions: {cadence['zero_slot_submissions']}; net nominal slots without a submission: {cadence['net_refresh_slots_without_submission']}.",
        f"- Worst swap interval: {summary['swap_interval_ms']['maximum']:.3f} ms; p99: {summary['swap_interval_ms']['p99']:.3f} ms.",
        f"- Slow pose steps (<0.8× nominal): {pose['slow_pose_steps_below_0_8']}; catch-up steps (>1.2×): {pose['catch_up_pose_steps_above_1_2']}.",
        f"- Strongest meaningful pose/swap-return lag: {lag_text}.",
        f"- Groove-contrast range: {representation['groove_contrast_range']:.6f}; maximum one-frame step: {representation['maximum_contrast_step']:.6f}.",
        gpu_line,
        scanout_line,
        presentation_line,
        f"- Diagnostic classification: `{summary['diagnosis']}`.",
        "",
        "**Measurement limit:** GLFW swap return is queue back-pressure, not a confirmed display timestamp. SGI video-sync is only a physical retrace counter; neither identifies which window buffer was scanned out. Aggregate completion counts cannot produce a physical-present cadence verdict without the validated per-event timestamp trace.",
        "",
        "Red vertical lines in `timing.svg` mark swap-return intervals longer than 1.5 nominal refresh slots.",
    ]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    args = parse_args()
    output = args.output_dir or args.trace.with_name(args.trace.stem + "-analysis")
    output.mkdir(parents=True, exist_ok=True)
    try:
        fields, values, annotations = read_trace_details(args.trace)
        summary, rows = analyze(fields, values, annotations)
    except ValueError as error:
        raise SystemExit(str(error)) from error
    (output / "summary.json").write_text(
        json.dumps(summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    write_metrics(output / "metrics.tsv", rows)
    write_svg(output / "timing.svg", rows, summary)
    write_report(output / "report.md", args.trace, summary)
    print(json.dumps(summary["verdicts"], sort_keys=True))
    print(f"Report: {output / 'report.md'}")


if __name__ == "__main__":
    main()
