#!/usr/bin/env python3
"""Analyze machine-readable wheel-render intermediates.

The v1 capture format deliberately records buffers that already exist in the
renderer instead of trying to infer every failure from final RGB pixels:

Each submitted frame lives in ``frame-%05d/`` and can contain:

* ``emission.rgba32f`` -- linear, premultiplied temporal emission;
* ``scene.rgba8`` -- scene after the emission core, before bloom;
* ``bloom.rgba8`` -- the quarter-resolution blurred bloom contribution;
* ``final.rgba8`` -- the final composite.

``capture.json`` describes the fixed dimensions and requested/effective model
and temporal mode. ``frames.tsv`` supplies at least ``frame`` and
``phase_degrees``. The analyzer fails before reading pixels if a requested
model or temporal mode silently fell back, which guards against accidentally
testing the violet wheel while believing the mint temporal renderer is active.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Iterable

try:
    import numpy as np
    from PIL import Image, ImageDraw
except ImportError as exc:  # pragma: no cover - environment diagnostic
    raise SystemExit(
        "analyze_render_truth.py requires NumPy and Pillow "
        "(python3-numpy/python3-pil)"
    ) from exc


SCHEMA = "wheel-render-truth-v1"
TRANSITIONAL_SCHEMA = "wheel-mesh-lab-buffer-capture"
GROOVE_PITCH_DEGREES = 20.0
EPSILON = 1.0e-12
STAGES = ("emission", "scene", "bloom", "final")


@dataclass(frozen=True)
class StageSpec:
    dtype: str
    width: int
    height: int


@dataclass(frozen=True)
class Thresholds:
    emission_energy_cv: float = 0.02
    emission_alpha_cv: float = 0.02
    emission_support_cv: float = 0.05
    bloom_energy_cv: float = 0.05
    bloom_support_cv: float = 0.08
    bloom_expansion_cv: float = 0.08
    phase_contrast_floor: float = 0.03
    minimum_phase_change_degrees: float = 1.0e-4
    significant_stage_change: float = 1.0e-4
    minimum_pace_comparisons: int = 16
    maximum_emission_pace_cv: float = 0.10
    minimum_emission_pace_p01_over_median: float = 0.65
    maximum_emission_pace_p99_over_median: float = 1.45


@dataclass
class Capture:
    directory: Path
    metadata: dict[str, Any]
    rows: list[dict[str, str]]
    layout: str
    stage_specs: dict[str, StageSpec]
    frame_count: int
    requested_model: str
    effective_model: str
    requested_mode: str
    effective_mode: str

    @property
    def width(self) -> int:
        return self.stage_specs["final"].width

    @property
    def height(self) -> int:
        return self.stage_specs["final"].height


@dataclass
class AnalysisResult:
    capture: Capture
    metrics: list[dict[str, float | int | bool]]
    summary: dict[str, Any]


def _positive_integer(value: Any, label: str) -> int:
    if isinstance(value, bool):
        raise ValueError(f"{label} must be a positive integer")
    try:
        parsed = int(value)
    except (TypeError, ValueError) as error:
        raise ValueError(f"{label} must be a positive integer") from error
    if parsed <= 0 or str(value).strip() not in (str(parsed), f"{parsed}.0"):
        raise ValueError(f"{label} must be a positive integer")
    return parsed


def _finite_float(value: str | float | int | None, label: str) -> float:
    if value is None or value == "":
        raise ValueError(f"missing {label}")
    try:
        parsed = float(value)
    except (TypeError, ValueError) as error:
        raise ValueError(f"invalid {label}: {value!r}") from error
    if not math.isfinite(parsed):
        raise ValueError(f"non-finite {label}: {value!r}")
    return parsed


def _bool_value(value: Any, label: str) -> bool:
    if isinstance(value, bool):
        return value
    normalized = str(value).strip().lower()
    if normalized in ("1", "true", "yes", "on"):
        return True
    if normalized in ("0", "false", "no", "off"):
        return False
    raise ValueError(f"invalid boolean {label}: {value!r}")


def _normalize_model(value: Any) -> str:
    normalized = str(value).strip().lower().replace("_", "-")
    aliases = {
        "mint-wheel": "mint",
        "green": "mint",
        "green-wheel": "mint",
        "violet-wheel": "violet",
        "purple": "violet",
        "purple-wheel": "violet",
    }
    return aliases.get(normalized, normalized)


def _normalize_mode(value: Any) -> str:
    normalized = str(value).strip().lower().replace("_", "-")
    aliases = {
        "frame-split": "split",
        "framesplit": "split",
        "frame-split-raw": "split-raw",
        "framesplit-raw": "split-raw",
        "band-limited": "band",
    }
    return aliases.get(normalized, normalized)


def _uniform_row_value(rows: list[dict[str, str]], field: str) -> str | None:
    values = {
        row[field].strip()
        for row in rows
        if field in row and row[field].strip() != ""
    }
    if len(values) > 1:
        raise ValueError(f"frames.tsv changes {field} within one capture")
    return next(iter(values), None)


def _metadata_or_rows(
    metadata: dict[str, Any],
    rows: list[dict[str, str]],
    keys: Iterable[str],
    label: str,
) -> Any:
    for key in keys:
        if key in metadata and metadata[key] not in (None, ""):
            metadata_value = metadata[key]
            row_value = _uniform_row_value(rows, key)
            if row_value is not None and str(metadata_value) != row_value:
                raise ValueError(
                    f"capture.json and frames.tsv disagree about {label}: "
                    f"{metadata_value!r} versus {row_value!r}"
                )
            return metadata_value
    for key in keys:
        row_value = _uniform_row_value(rows, key)
        if row_value is not None:
            return row_value
    raise ValueError(f"capture does not declare {label}")


def _capture_layout(metadata: dict[str, Any]) -> str:
    declaration = metadata.get("layout", "per-frame-directories")
    if isinstance(declaration, dict):
        kind = declaration.get("kind", declaration.get("type", ""))
        pattern = declaration.get("pattern", "frame-%05d")
    else:
        kind = declaration
        pattern = metadata.get("frame_directory_pattern", "frame-%05d")
    normalized = str(kind).strip().lower().replace("_", "-")
    aliases = {
        "frame-directories": "per-frame-directories",
        "frame-directories-v1": "per-frame-directories",
        "per-frame-directory": "per-frame-directories",
        "frame-%05d": "per-frame-directories",
    }
    normalized = aliases.get(normalized, normalized)
    if normalized != "per-frame-directories":
        raise ValueError(f"unsupported render-truth layout: {kind!r}")
    if pattern not in ("frame-%05d", "frame-{frame:05d}"):
        raise ValueError(f"unsupported frame-directory pattern: {pattern!r}")
    return normalized


def _stage_specs(metadata: dict[str, Any]) -> dict[str, StageSpec]:
    stage_root = metadata.get("stages")
    if stage_root is not None and not isinstance(stage_root, dict):
        raise ValueError("capture stages declaration must be an object")
    result: dict[str, StageSpec] = {}
    expected_dtypes = {
        "emission": "float32",
        "scene": "uint8",
        "bloom": "uint8",
        "final": "uint8",
    }
    for stage in STAGES:
        descriptor = (stage_root or {}).get(stage, metadata.get(stage))
        if not isinstance(descriptor, dict):
            raise ValueError(f"capture does not declare {stage} stage dimensions")
        dtype = str(descriptor.get("dtype", "")).strip().lower()
        aliases = {"rgba32f": "float32", "f32": "float32", "u8": "uint8"}
        dtype = aliases.get(dtype, dtype)
        if dtype != expected_dtypes[stage]:
            raise ValueError(
                f"{stage} dtype must be {expected_dtypes[stage]}, found {dtype!r}"
            )
        suffix = "rgba32f" if stage == "emission" else "rgba8"
        expected_pattern = f"frame-%05d/{stage}.{suffix}"
        pattern = descriptor.get("file_pattern", expected_pattern)
        if pattern != expected_pattern:
            raise ValueError(
                f"unsupported {stage} file pattern: {pattern!r}; "
                f"expected {expected_pattern!r}"
            )
        result[stage] = StageSpec(
            dtype=dtype,
            width=_positive_integer(descriptor.get("width"), f"{stage} width"),
            height=_positive_integer(descriptor.get("height"), f"{stage} height"),
        )
    if result["scene"] != result["final"]:
        raise ValueError("scene and final stage dimensions/dtypes must match")
    if (result["emission"].width, result["emission"].height) != (
        result["final"].width, result["final"].height
    ):
        raise ValueError("emission and final stage dimensions must match")
    if result["bloom"].width > result["final"].width \
            or result["bloom"].height > result["final"].height:
        raise ValueError("bloom stage cannot exceed the final stage dimensions")
    return result


def _read_rows(path: Path) -> list[dict[str, str]]:
    if not path.is_file():
        raise ValueError(f"missing frames manifest: {path}")
    with path.open(newline="", encoding="utf-8") as source:
        reader = csv.DictReader(source, delimiter="\t")
        if reader.fieldnames is None:
            raise ValueError(f"empty frames manifest: {path}")
        rows = list(reader)
    if not rows:
        raise ValueError(f"empty frames manifest: {path}")
    for required in ("frame", "phase_degrees"):
        if required not in rows[0]:
            raise ValueError(f"frames.tsv has no {required} column")
    for index, row in enumerate(rows):
        frame = _positive_integer(row["frame"], f"frames.tsv row {index} frame") \
            if row["frame"] != "0" else 0
        if frame != index:
            raise ValueError(
                f"frames.tsv must contain contiguous zero-based frames; "
                f"row {index} names frame {frame}"
            )
        _finite_float(row["phase_degrees"], f"frame {index} phase_degrees")
    return rows


def load_capture(directory: Path) -> Capture:
    directory = directory.expanduser().resolve()
    metadata_path = directory / "capture.json"
    if not metadata_path.is_file():
        raise ValueError(f"missing capture metadata: {metadata_path}")
    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"invalid capture metadata: {metadata_path}: {error}") from error
    if not isinstance(metadata, dict):
        raise ValueError("capture.json root must be an object")
    schema = metadata.get("schema", metadata.get("schema_id"))
    if schema not in (SCHEMA, TRANSITIONAL_SCHEMA):
        raise ValueError(f"unsupported render-truth schema: {schema!r}")
    if schema == TRANSITIONAL_SCHEMA and metadata.get("schema_version") != 1:
        raise ValueError(
            f"unsupported transitional render-truth schema version: "
            f"{metadata.get('schema_version')!r}"
        )

    layout = _capture_layout(metadata)
    stage_specs = _stage_specs(metadata)
    rows = _read_rows(directory / "frames.tsv")
    frame_count = len(rows)
    if "frame_count" in metadata and _positive_integer(
            metadata["frame_count"], "frame_count") != frame_count:
        raise ValueError(
            f"capture declares {metadata['frame_count']} frames but frames.tsv "
            f"contains {frame_count}"
        )

    requested_model = _normalize_model(_metadata_or_rows(
        metadata, rows, ("requested_model", "model_slug"), "requested model"
    ))
    effective_model = _normalize_model(_metadata_or_rows(
        metadata, rows, ("effective_model", "model_slug"), "effective model"
    ))
    requested_mode = _normalize_mode(_metadata_or_rows(
        metadata, rows, ("requested_temporal_mode", "requested_mode"),
        "requested temporal mode",
    ))
    effective_mode = _normalize_mode(_metadata_or_rows(
        metadata, rows, ("effective_temporal_mode", "effective_mode"),
        "effective temporal mode",
    ))

    if requested_model != effective_model:
        raise ValueError(
            "requested/effective model mismatch: "
            f"requested {requested_model!r}, rendered {effective_model!r}"
        )
    row_model_slug = _uniform_row_value(rows, "model_slug")
    if row_model_slug is not None \
            and _normalize_model(row_model_slug) != effective_model:
        raise ValueError(
            "capture.json and frames.tsv disagree about effective model: "
            f"{effective_model!r} versus {_normalize_model(row_model_slug)!r}"
        )
    if requested_mode != effective_mode:
        raise ValueError(
            "requested/effective temporal mode mismatch: "
            f"requested {requested_mode!r}, rendered {effective_mode!r}"
        )
    temporal_modes = {
        "reference", "adaptive", "adaptive-raw", "band", "split",
        "split-raw", "alias-safe",
    }
    if effective_mode in temporal_modes and effective_model != "mint":
        raise ValueError(
            f"temporal mode {effective_mode!r} requires the mint wheel, "
            f"but the capture rendered {effective_model!r}"
        )
    availability = metadata.get("temporal_grooves_available")
    if availability is None:
        availability = _uniform_row_value(rows, "temporal_grooves_available")
    if effective_mode in temporal_modes and availability is not None \
            and not _bool_value(availability, "temporal_grooves_available"):
        raise ValueError(
            f"capture claims effective mode {effective_mode!r} without temporal grooves"
        )

    capture = Capture(
        directory=directory,
        metadata=metadata,
        rows=rows,
        layout=layout,
        stage_specs=stage_specs,
        frame_count=frame_count,
        requested_model=requested_model,
        effective_model=effective_model,
        requested_mode=requested_mode,
        effective_mode=effective_mode,
    )
    _validate_raw_files(capture)
    return capture


def _stage_path(capture: Capture, stage: str, frame: int) -> Path:
    suffix = "rgba32f" if stage == "emission" else "rgba8"
    return capture.directory / f"frame-{frame:05d}" / f"{stage}.{suffix}"


def _emission_available(capture: Capture, frame: int) -> bool:
    value = capture.rows[frame].get("emission_available", "").strip()
    if value:
        available = _bool_value(value, f"frame {frame} emission_available")
        active_value = capture.rows[frame].get("temporal_active", "").strip()
        if active_value and _bool_value(
                active_value, f"frame {frame} temporal_active") != available:
            raise ValueError(
                f"frame {frame} temporal_active and emission_available disagree"
            )
        return available
    # Canonical captures declare availability explicitly. This inference keeps
    # early v1 bundles readable without making a missing declared buffer pass.
    return _stage_path(capture, "emission", frame).is_file()


def _validate_raw_files(capture: Capture) -> None:
    for frame in range(capture.frame_count):
        frame_directory = capture.directory / f"frame-{frame:05d}"
        if not frame_directory.is_dir():
            raise ValueError(f"missing render-truth frame directory: {frame_directory}")
        expected_names = {"frame.json"}
        for stage in STAGES:
            path = _stage_path(capture, stage, frame)
            expected_names.add(path.name)
            available = stage != "emission" or _emission_available(capture, frame)
            if not available:
                if path.exists():
                    raise ValueError(
                        f"{path} exists although frames.tsv declares emission unavailable"
                    )
                continue
            if not path.is_file():
                raise ValueError(f"missing render-truth buffer: {path}")
            spec = capture.stage_specs[stage]
            bytes_per_channel = 4 if spec.dtype == "float32" else 1
            expected_size = spec.width * spec.height * 4 * bytes_per_channel
            actual_size = path.stat().st_size
            if actual_size != expected_size:
                raise ValueError(
                    f"wrong size for {path.name}: expected {expected_size} bytes, "
                    f"found {actual_size}"
                )
        stale = sorted(
            path.name
            for pattern in ("*.rgba32f", "*.rgba8")
            for path in frame_directory.glob(pattern)
            if path.name not in expected_names
        )
        if stale:
            raise ValueError(
                f"{frame_directory.name} contains stale raw buffers: "
                + ", ".join(stale[:6])
            )
    expected_directories = {
        f"frame-{frame:05d}" for frame in range(capture.frame_count)
    }
    stale_directories = sorted(
        path.name
        for path in capture.directory.glob("frame-*")
        if path.is_dir() and path.name not in expected_directories
    )
    if stale_directories:
        raise ValueError(
            "capture contains stale/non-contiguous frame directories: "
            + ", ".join(stale_directories[:6])
        )


def _read_stage(capture: Capture, stage: str, frame: int) -> np.ndarray:
    if stage == "emission" and not _emission_available(capture, frame):
        raise ValueError(f"frame {frame} has no temporal emission buffer")
    path = _stage_path(capture, stage, frame)
    dtype = np.dtype("<f4") if stage == "emission" else np.dtype(np.uint8)
    data = np.fromfile(path, dtype=dtype)
    spec = capture.stage_specs[stage]
    result = data.reshape(spec.height, spec.width, 4)
    if stage == "emission":
        if not bool(np.all(np.isfinite(result))):
            raise ValueError(f"non-finite value in {path.name}")
        minimum = float(np.min(result))
        alpha_minimum = float(np.min(result[..., 3]))
        alpha_maximum = float(np.max(result[..., 3]))
        if minimum < -1.0e-5:
            raise ValueError(f"negative premultiplied emission in {path.name}")
        if alpha_minimum < -1.0e-5 or alpha_maximum > 1.0001:
            raise ValueError(
                f"emission alpha outside [0,1] in {path.name}: "
                f"[{alpha_minimum:g}, {alpha_maximum:g}]"
            )
    return result


def _luminance(rgb: np.ndarray) -> np.ndarray:
    values = rgb.astype(np.float64, copy=False)
    if rgb.dtype == np.uint8:
        values = values / 255.0
    return (
        values[..., 0] * 0.2126
        + values[..., 1] * 0.7152
        + values[..., 2] * 0.0722
    )


def _hash(array: np.ndarray) -> str:
    return hashlib.sha256(array.tobytes(order="C")).hexdigest()


def _normalized_change(current: np.ndarray, previous: np.ndarray) -> float:
    current_f = current.astype(np.float64, copy=False)
    previous_f = previous.astype(np.float64, copy=False)
    if current.dtype == np.uint8:
        current_f = current_f / 255.0
        previous_f = previous_f / 255.0
    scale = max(
        float(np.mean(np.abs(current_f))),
        float(np.mean(np.abs(previous_f))),
        1.0 / 255.0,
    )
    return float(np.mean(np.abs(current_f - previous_f)) / scale)


def _cyclic_phase_change(current: float, previous: float) -> float:
    delta = math.remainder(current - previous, GROOVE_PITCH_DEGREES)
    return abs(delta)


def _phase_dependent(row: dict[str, str], mode: str, thresholds: Thresholds) -> bool:
    explicit = row.get("phase_dependent_expected", "").strip()
    if explicit:
        return _bool_value(explicit, "phase_dependent_expected")
    contrast = row.get("groove_contrast", "").strip()
    if contrast:
        return _finite_float(contrast, "groove_contrast") \
            > thresholds.phase_contrast_floor
    cycles = row.get("groove_cycles_per_frame", "").strip()
    if cycles and mode in ("adaptive", "split", "alias-safe"):
        return abs(_finite_float(cycles, "groove_cycles_per_frame")) < 0.49
    return mode not in ("alias-safe",)


def _series_statistics(values: np.ndarray) -> dict[str, float | int | None]:
    finite = values[np.isfinite(values)]
    if finite.size == 0:
        return {
            "available_count": 0,
            "minimum": None,
            "p01": None,
            "mean": None,
            "median": None,
            "p99": None,
            "maximum": None,
            "cv": None,
        }
    mean = float(np.mean(finite))
    return {
        "available_count": int(finite.size),
        "minimum": float(np.min(finite)),
        "p01": float(np.percentile(finite, 1.0)),
        "mean": mean,
        "median": float(np.median(finite)),
        "p99": float(np.percentile(finite, 99.0)),
        "maximum": float(np.max(finite)),
        "cv": float(np.std(finite) / max(abs(mean), EPSILON)),
    }


def analyze_capture(
    capture: Capture,
    thresholds: Thresholds = Thresholds(),
) -> AnalysisResult:
    metrics: list[dict[str, float | int | bool]] = []
    previous_arrays: dict[str, np.ndarray] = {}
    previous_hashes: dict[str, str] = {}
    previous_phase: float | None = None
    expected_motion_comparisons = 0
    duplicate_counts = {stage: 0 for stage in STAGES}
    downstream_holds = {stage: 0 for stage in ("scene", "bloom", "final")}

    for frame, row in enumerate(capture.rows):
        phase = _finite_float(row["phase_degrees"], f"frame {frame} phase")
        emission_available = _emission_available(capture, frame)
        arrays = {
            stage: _read_stage(capture, stage, frame)
            for stage in STAGES
            if stage != "emission" or emission_available
        }
        hashes = {stage: _hash(array) for stage, array in arrays.items()}
        stage_luminance = {
            stage: _luminance(array[..., :3]) for stage, array in arrays.items()
        }
        stage_energy = {
            stage: float(np.sum(values))
            for stage, values in stage_luminance.items()
        }
        stage_peak = {
            stage: float(np.max(array[..., :3]))
                    / (255.0 if array.dtype == np.uint8 else 1.0)
            for stage, array in arrays.items()
        }
        stage_support: dict[str, int] = {
            stage: int(np.count_nonzero(values > 0.5 / 255.0))
            for stage, values in stage_luminance.items()
        }
        if emission_available:
            emission = arrays["emission"]
            emission_alpha = emission[..., 3].astype(np.float64)
            stage_support["emission"] = int(np.count_nonzero(
                (emission_alpha > 1.0e-5)
                | (stage_luminance["emission"] > 1.0e-5)
            ))
            emission_alpha_energy = float(np.sum(emission_alpha))
        else:
            emission_alpha_energy = math.nan

        metric: dict[str, float | int | bool] = {
            "frame": frame,
            "phase_degrees": phase,
            "emission_available": emission_available,
            "phase_dependent_expected": _phase_dependent(
                row, capture.effective_mode, thresholds
            ),
            "emission_alpha_energy": emission_alpha_energy,
        }
        for stage in STAGES:
            available = stage in arrays
            metric[f"{stage}_luminance_energy"] = (
                stage_energy[stage] if available else math.nan
            )
            metric[f"{stage}_peak_value"] = (
                stage_peak[stage] if available else math.nan
            )
            metric[f"{stage}_support_pixels"] = (
                stage_support[stage] if available else math.nan
            )
            spec = capture.stage_specs[stage]
            metric[f"{stage}_support_fraction"] = (
                stage_support[stage] / float(spec.width * spec.height)
                if available else math.nan
            )
            metric[f"{stage}_exact_duplicate"] = False
            metric[f"{stage}_normalized_change"] = math.nan

        motion_expected = False
        if previous_phase is not None:
            phase_change = _cyclic_phase_change(phase, previous_phase)
            motion_expected = (
                phase_change > thresholds.minimum_phase_change_degrees
                and bool(metric["phase_dependent_expected"])
            )
            if motion_expected:
                expected_motion_comparisons += 1
            metric["cyclic_phase_change_degrees"] = phase_change
            metric["motion_expected"] = motion_expected
            for stage in STAGES:
                if stage not in arrays or stage not in previous_arrays:
                    continue
                exact_duplicate = hashes[stage] == previous_hashes[stage]
                change = _normalized_change(arrays[stage], previous_arrays[stage])
                metric[f"{stage}_exact_duplicate"] = exact_duplicate
                metric[f"{stage}_normalized_change"] = change
                if motion_expected and exact_duplicate:
                    duplicate_counts[stage] += 1
            emission_change = float(metric["emission_normalized_change"])
            emission_changed = math.isfinite(emission_change) \
                and emission_change > thresholds.significant_stage_change
            if motion_expected and emission_changed \
                    and bool(metric["final_exact_duplicate"]):
                # Attribute only an actual final-output hold. A quarter-size
                # RGBA8 bloom buffer can legitimately quantize to an identical
                # byte image while the scene/final continues moving.
                for stage in ("scene", "bloom", "final"):
                    if bool(metric[f"{stage}_exact_duplicate"]):
                        downstream_holds[stage] += 1
                        break
        else:
            metric["cyclic_phase_change_degrees"] = math.nan
            metric["motion_expected"] = False

        emission_support_fraction = float(metric["emission_support_fraction"])
        metric["bloom_to_emission_support_ratio"] = (
            float(metric["bloom_support_fraction"])
            / max(emission_support_fraction, EPSILON)
            if math.isfinite(emission_support_fraction) else math.nan
        )
        metrics.append(metric)
        previous_arrays = arrays
        previous_hashes = hashes
        previous_phase = phase

    def series(name: str) -> np.ndarray:
        return np.asarray([float(metric[name]) for metric in metrics], dtype=np.float64)

    statistics = {
        "emission_luminance_energy": _series_statistics(
            series("emission_luminance_energy")
        ),
        "emission_alpha_energy": _series_statistics(series("emission_alpha_energy")),
        "emission_support_pixels": _series_statistics(series("emission_support_pixels")),
        "scene_luminance_energy": _series_statistics(series("scene_luminance_energy")),
        "bloom_luminance_energy": _series_statistics(series("bloom_luminance_energy")),
        "bloom_peak_value": _series_statistics(series("bloom_peak_value")),
        "bloom_support_pixels": _series_statistics(series("bloom_support_pixels")),
        "final_luminance_energy": _series_statistics(series("final_luminance_energy")),
        "bloom_to_emission_support_ratio": _series_statistics(
            series("bloom_to_emission_support_ratio")
        ),
    }

    # Exact duplicates catch complete holds. A visible slowdown can be subtler:
    # one submitted frame advances only partway and the next catches up. Measure
    # raw-emission change per commanded phase degree before compositing/bloom,
    # where downstream quantization cannot create or hide that event.
    emission_pace_values = np.asarray([
        float(metric["emission_normalized_change"])
        / max(float(metric["cyclic_phase_change_degrees"]), EPSILON)
        for metric in metrics
        if bool(metric["motion_expected"])
        and math.isfinite(float(metric["emission_normalized_change"]))
        and math.isfinite(float(metric["cyclic_phase_change_degrees"]))
    ], dtype=np.float64)
    emission_pace_statistics = _series_statistics(emission_pace_values)
    statistics["emission_change_per_phase_degree"] = emission_pace_statistics

    pace_reasons: list[str] = []
    if emission_pace_values.size < thresholds.minimum_pace_comparisons:
        pace_verdict = "NOT_EVALUATED"
        pace_reasons.append(
            f"only {emission_pace_values.size} usable phase-dependent transitions; "
            f"need {thresholds.minimum_pace_comparisons}"
        )
    else:
        pace_median = float(emission_pace_statistics["median"])
        pace_p01_ratio = float(emission_pace_statistics["p01"]) \
            / max(pace_median, EPSILON)
        pace_p99_ratio = float(emission_pace_statistics["p99"]) \
            / max(pace_median, EPSILON)
        pace_cv = float(emission_pace_statistics["cv"])
        if pace_cv > thresholds.maximum_emission_pace_cv:
            pace_reasons.append(
                f"raw-emission change/degree CV {pace_cv:.6g} exceeds "
                f"{thresholds.maximum_emission_pace_cv:.6g}"
            )
        if pace_p01_ratio < thresholds.minimum_emission_pace_p01_over_median:
            pace_reasons.append(
                f"raw-emission p01 pace is {pace_p01_ratio:.3f}x median, below "
                f"{thresholds.minimum_emission_pace_p01_over_median:.3f}x"
            )
        if pace_p99_ratio > thresholds.maximum_emission_pace_p99_over_median:
            pace_reasons.append(
                f"raw-emission p99 pace is {pace_p99_ratio:.3f}x median, above "
                f"{thresholds.maximum_emission_pace_p99_over_median:.3f}x"
            )
        pace_verdict = "FAIL" if pace_reasons else "PASS"
        if not pace_reasons:
            pace_reasons.append(
                "raw-emission change per commanded phase degree remains stable"
            )

    continuity_reasons: list[str] = []
    if duplicate_counts["emission"]:
        continuity_reasons.append(
            f"raw temporal emission repeated during expected motion on "
            f"{duplicate_counts['emission']} frame transitions"
        )
    for stage in ("scene", "bloom", "final"):
        if downstream_holds[stage]:
            continuity_reasons.append(
                f"{stage} repeated despite changed upstream emission on "
                f"{downstream_holds[stage]} frame transitions"
            )
    if duplicate_counts["final"] and not any(downstream_holds.values()):
        continuity_reasons.append(
            f"final composite repeated during expected motion on "
            f"{duplicate_counts['final']} frame transitions"
        )
    if continuity_reasons:
        continuity_verdict = "FAIL"
    elif expected_motion_comparisons == 0:
        continuity_verdict = "INDETERMINATE"
        continuity_reasons.append(
            "capture has no phase-dependent, non-period-equivalent motion transitions"
        )
    else:
        continuity_verdict = "PASS"
        continuity_reasons.append(
            f"no exact holds across {expected_motion_comparisons} expected-motion transitions"
        )

    emission_stability_checks = (
        ("emission luminance energy", "emission_luminance_energy",
         thresholds.emission_energy_cv),
        ("emission alpha energy", "emission_alpha_energy",
         thresholds.emission_alpha_cv),
        ("emission support", "emission_support_pixels",
         thresholds.emission_support_cv),
    )
    bloom_stability_checks = (
        ("bloom luminance energy", "bloom_luminance_energy",
         thresholds.bloom_energy_cv),
        ("bloom support", "bloom_support_pixels",
         thresholds.bloom_support_cv),
        ("bloom/emission support expansion", "bloom_to_emission_support_ratio",
         thresholds.bloom_expansion_cv),
    )
    stability_reasons: list[str] = []

    def failed_checks(
        checks: tuple[tuple[str, str, float], ...]
    ) -> list[str]:
        failures: list[str] = []
        for label, key, maximum_cv in checks:
            measured_cv = statistics[key]["cv"]
            if measured_cv is not None and measured_cv > maximum_cv:
                failures.append(
                    f"{label} CV {measured_cv:.6g} exceeds {maximum_cv:.6g}"
                )
        return failures

    emission_count = int(statistics["emission_alpha_energy"]["available_count"])
    emission_failures = failed_checks(emission_stability_checks)
    bloom_failures = failed_checks(bloom_stability_checks)
    if emission_count == 0:
        emission_stability_verdict = "NOT_EVALUATED"
        stability_reasons.append(
            "raw temporal emission is unavailable on every frame; emission "
            "stability was not evaluated"
        )
    elif emission_count == 1:
        emission_stability_verdict = "INDETERMINATE"
        stability_reasons.append(
            "only one raw temporal-emission frame is available"
        )
    elif emission_failures:
        emission_stability_verdict = "FAIL"
        stability_reasons.extend(emission_failures)
    else:
        emission_stability_verdict = "PASS"

    bloom_peak = statistics["bloom_peak_value"]["maximum"]
    bloom_at_quantization_floor = (
        bloom_peak is not None and bloom_peak <= 1.5 / 255.0
    )
    if bloom_at_quantization_floor:
        bloom_stability_verdict = "NOT_EVALUATED"
        stability_reasons.append(
            "RGBA8 bloom never exceeds one quantization step; bloom energy/support "
            "CV is not statistically meaningful"
        )
    elif bloom_failures:
        bloom_stability_verdict = "FAIL"
        stability_reasons.extend(bloom_failures)
    else:
        bloom_stability_verdict = "PASS"

    if "FAIL" in (emission_stability_verdict, bloom_stability_verdict):
        stability_verdict = "FAIL"
    elif emission_stability_verdict == "INDETERMINATE":
        stability_verdict = "INDETERMINATE"
    else:
        # NOT_EVALUATED is correct for sharp frames and does not invalidate the
        # independent bloom/final-stage checks.
        stability_verdict = "PASS"
    if not stability_reasons:
        stability_reasons.append("emission energy and bloom support remain stable")

    if (continuity_verdict == "FAIL" or stability_verdict == "FAIL"
            or pace_verdict == "FAIL"):
        overall_verdict = "FAIL"
    elif continuity_verdict == "INDETERMINATE" \
            or stability_verdict == "INDETERMINATE":
        overall_verdict = "INDETERMINATE"
    else:
        overall_verdict = "PASS"

    first_failure_stage: str | None = None
    if duplicate_counts["emission"] > 0:
        first_failure_stage = "emission"
    else:
        for stage in ("scene", "bloom", "final"):
            if downstream_holds[stage] > 0:
                first_failure_stage = stage
                break
        if first_failure_stage is None and duplicate_counts["final"] > 0:
            first_failure_stage = "final"
    if first_failure_stage is None and pace_verdict == "FAIL":
        first_failure_stage = "emission"

    summary: dict[str, Any] = {
        "schema": "wheel-render-truth-analysis-v1",
        "capture_schema": capture.metadata.get("schema"),
        "overall_verdict": overall_verdict,
        "continuity_verdict": continuity_verdict,
        "motion_pace_verdict": pace_verdict,
        "stability_verdict": stability_verdict,
        "emission_stability_verdict": emission_stability_verdict,
        "bloom_stability_verdict": bloom_stability_verdict,
        "requested_model": capture.requested_model,
        "effective_model": capture.effective_model,
        "requested_temporal_mode": capture.requested_mode,
        "effective_temporal_mode": capture.effective_mode,
        "frame_count": capture.frame_count,
        "layout": capture.layout,
        "stage_dimensions": {
            stage: {
                "width": spec.width,
                "height": spec.height,
                "dtype": spec.dtype,
            }
            for stage, spec in capture.stage_specs.items()
        },
        "emission_available_frames": sum(
            int(bool(metric["emission_available"])) for metric in metrics
        ),
        "expected_motion_comparisons": expected_motion_comparisons,
        "exact_duplicate_counts_during_expected_motion": duplicate_counts,
        "downstream_hold_counts": downstream_holds,
        "first_failure_stage": first_failure_stage,
        "continuity_reasons": continuity_reasons,
        "motion_pace_reasons": pace_reasons,
        "stability_reasons": stability_reasons,
        "statistics": statistics,
        "thresholds": asdict(thresholds),
        "measurement_limits": {
            "physical_scanout": "NOT_MEASURED",
            "reason": (
                "These buffers describe submitted renderer output. They can localize "
                "a renderer-stage hold but cannot prove which image reached physical scanout."
            ),
        },
    }
    return AnalysisResult(capture=capture, metrics=metrics, summary=summary)


def _write_metrics(path: Path, result: AnalysisResult) -> None:
    fieldnames = list(result.metrics[0].keys())
    with path.open("w", newline="", encoding="utf-8") as destination:
        writer = csv.DictWriter(destination, fieldnames=fieldnames, delimiter="\t")
        writer.writeheader()
        for metric in result.metrics:
            writer.writerow(metric)


def _stage_preview(array: np.ndarray, stage: str) -> Image.Image:
    if stage == "emission":
        luminance = _luminance(array[..., :3])
        alpha = array[..., 3].astype(np.float64)
        peak = max(float(np.percentile(luminance, 99.5)), 1.0e-6)
        green = np.clip(luminance / peak, 0.0, 1.0)
        cyan = np.clip(alpha, 0.0, 1.0)
        rgb = np.stack((green * 0.12, green, green * 0.55 + cyan * 0.35), axis=2)
        encoded = np.clip(np.rint(rgb * 255.0), 0.0, 255.0).astype(np.uint8)
    else:
        encoded = array[..., :3].astype(np.uint8)
    return Image.fromarray(np.flipud(encoded), mode="RGB")


def _write_overview(path: Path, result: AnalysisResult) -> None:
    interesting = [0, result.capture.frame_count - 1]
    for metric in result.metrics[1:]:
        if any(bool(metric[f"{stage}_exact_duplicate"]) for stage in STAGES):
            interesting.append(int(metric["frame"]))
            break
    alpha_energy = np.asarray(
        [float(metric["emission_alpha_energy"]) for metric in result.metrics]
    )
    finite_alpha = np.flatnonzero(np.isfinite(alpha_energy))
    if finite_alpha.size:
        median_alpha = float(np.nanmedian(alpha_energy))
        interesting.append(int(np.nanargmax(np.abs(alpha_energy - median_alpha))))
    frames = list(dict.fromkeys(interesting))[:4]

    scale = min(2, max(1, 320 // max(result.capture.width, 1)))
    tile_width = result.capture.width * scale
    tile_height = result.capture.height * scale
    label_height = 22
    canvas = Image.new(
        "RGB",
        (tile_width * len(STAGES), (tile_height + label_height) * len(frames)),
        (12, 17, 24),
    )
    draw = ImageDraw.Draw(canvas)
    for row_index, frame in enumerate(frames):
        y = row_index * (tile_height + label_height)
        for column, stage in enumerate(STAGES):
            if stage == "emission" and not _emission_available(result.capture, frame):
                preview = Image.new("RGB", (tile_width, tile_height), (18, 24, 32))
                ImageDraw.Draw(preview).text(
                    (8, 8), "emission unavailable", fill=(175, 185, 198)
                )
            else:
                preview = _stage_preview(
                    _read_stage(result.capture, stage, frame), stage
                ).resize((tile_width, tile_height), Image.Resampling.NEAREST)
            x = column * tile_width
            canvas.paste(preview, (x, y))
            draw.text(
                (x + 4, y + tile_height + 4),
                f"{stage} f={frame}",
                fill=(225, 235, 245),
            )
    canvas.save(path)


def write_artifacts(output_dir: Path, result: AnalysisResult) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / "summary.json").write_text(
        json.dumps(result.summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    _write_metrics(output_dir / "metrics.tsv", result)
    _write_overview(output_dir / "overview.png", result)
    reasons = [
        *(f"- {reason}" for reason in result.summary["continuity_reasons"]),
        *(f"- {reason}" for reason in result.summary["motion_pace_reasons"]),
        *(f"- {reason}" for reason in result.summary["stability_reasons"]),
    ]
    report = f"""# Render-truth analysis

Overall: **{result.summary['overall_verdict']}**

- Model: requested `{result.capture.requested_model}`, effective `{result.capture.effective_model}`
- Temporal mode: requested `{result.capture.requested_mode}`, effective `{result.capture.effective_mode}`
- Continuity: **{result.summary['continuity_verdict']}**
- Partial-slowdown/catch-up pace: **{result.summary['motion_pace_verdict']}**
- Energy/support stability: **{result.summary['stability_verdict']}**
- First failing stage: `{result.summary['first_failure_stage']}`

## Findings

{chr(10).join(reasons)}

## Measurement boundary

This report reads submitted GPU intermediates. It can identify the first renderer
stage that held or changed energy, but it does not contain a confirmed physical
scanout timestamp or a camera observation of the monitor.
"""
    (output_dir / "report.md").write_text(report, encoding="utf-8")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument("capture_dir", type=Path)
    parser.add_argument("--output-dir", type=Path, required=True)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        capture = load_capture(args.capture_dir)
        result = analyze_capture(capture)
        write_artifacts(args.output_dir, result)
    except (OSError, ValueError) as error:
        print(f"render-truth analysis failed: {error}", file=sys.stderr)
        return 2
    print(
        f"Render truth: {result.summary['overall_verdict']} "
        f"(continuity={result.summary['continuity_verdict']}, "
        f"pace={result.summary['motion_pace_verdict']}, "
        f"stability={result.summary['stability_verdict']})"
    )
    print(f"Report: {args.output_dir / 'report.md'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
