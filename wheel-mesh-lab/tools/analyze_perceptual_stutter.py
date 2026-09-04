#!/usr/bin/env python3
"""Measure wheel stutter from the pixels that a viewer actually receives.

The renderer manifest remains useful evidence, but it is not treated as proof
that the image moved. A separate low-speed, known-phase template sequence in
the same rendering mode teaches this tool how the visible tread changes over
one physical groove period. The analyzer
then projects every tested frame onto that image-space phase model and reports
apparent phase, apparent speed, stalls, catch-up frames, intensity modulation,
presentation cadence, and periodic beat frequencies.

The input directories must contain contiguous ``frame-00000.ppm`` files and a
tab-separated ``manifest.tsv``.  At minimum, the manifest needs
``phase_degrees``.  ``fps`` and ``rps`` make the timing checks stronger;
``presentation_time_ns`` (or ``present_ns``) permits replay of irregular live
presentation traces. ``swap_return_ns``/``swap_return_ms`` are accepted as a
useful back-pressure proxy, but reports label them as swap-return timing rather
than claiming they are confirmed scanout timestamps. Otherwise
``presentation_interval_ms`` or ``fps`` constructs a deterministic timeline.

This is deliberately an image-space QA tool rather than an optical-flow
package.  The mint wheel has eighteen equal repeating grooves, so generic flow
cannot distinguish the true rotation from a wagon-wheel alias.  The learned
complex groove basis exposes that ambiguity explicitly: below Nyquist its phase
is the apparent motion, while above Nyquist the only safe result is vanishing
phase-dependent contrast.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable, Sequence

try:
    import numpy as np
    from PIL import Image, ImageDraw, ImageFont
except ImportError as exc:  # pragma: no cover - environment diagnostic
    raise SystemExit(
        "analyze_perceptual_stutter.py requires NumPy and Pillow "
        "(python3-numpy/python3-pil)"
    ) from exc


DEFAULT_GROOVE_COUNT = 18
DEFAULT_SPATIAL_SCALES = (0, 1, 2)
EPSILON = 1.0e-12
REPRESENTATION_FLOAT_FIELDS = (
    "alias_envelope_cycles",
    "temporal_blend",
    "core_intensity",
    "bloom_correction_blend",
    "groove_contrast",
    "motion_band_energy_weight",
    "band_blend",
    "raw_band_blend",
    "emission_bright_factor",
)
REPRESENTATION_INTEGER_FIELDS = (
    "samples",
    "requested_samples",
    "sample_cap_applied",
    "emission_draws",
    "temporal_active",
)


@dataclass(frozen=True)
class Thresholds:
    """Initial conservative gates, calibrated against deterministic controls."""

    # Analytic/HDR-filtered output can retain a reliable phase at substantially
    # lower contrast than the sharp template.  The real 180 Hz transition
    # sequence remains accurately trackable at ~0.05 confidence; the protected
    # phase-invariant endpoint is orders of magnitude lower.
    min_phase_confidence: float = 0.03
    maximum_trackable_cycles: float = 0.49
    maximum_p99_phase_error_cycles: float = 0.02
    minimum_p01_speed_ratio: float = 0.80
    maximum_p99_speed_ratio: float = 1.20
    stall_speed_ratio: float = 0.55
    catchup_speed_ratio: float = 1.45
    maximum_speed_beat_amplitude: float = 0.05
    maximum_constant_speed_contrast_cv: float = 0.02
    maximum_constant_speed_energy_cv: float = 0.01
    maximum_alias_contrast: float = 0.05
    maximum_cadence_jitter_fraction: float = 0.25
    minimum_capture_duration_seconds: float = 2.5


@dataclass
class SequenceCapture:
    directory: Path
    paths: list[Path]
    rows: list[dict[str, str]]
    rgb: np.ndarray
    signal: np.ndarray
    phase_degrees: np.ndarray
    times_seconds: np.ndarray
    rps: np.ndarray
    fps: float
    timeline_kind: str
    original_size: tuple[int, int]
    analysis_size: tuple[int, int]


@dataclass
class PhaseEstimate:
    phase_radians: np.ndarray
    harmonic_amplitudes: np.ndarray
    fit_quality: np.ndarray
    confidence: np.ndarray


@dataclass
class SpectrumPeak:
    frequency_hz: float
    amplitude: float
    signal_to_noise: float


@dataclass
class ScaleAnalysis:
    radius_pixels: int
    apparent_phase_radians: np.ndarray
    contrast: np.ndarray
    confidence: np.ndarray
    fit_quality: np.ndarray
    apparent_delta_cycles: np.ndarray
    expected_delta_cycles: np.ndarray
    rendered_delta_cycles: np.ndarray
    phase_error_cycles: np.ndarray
    speed_ratio: np.ndarray
    valid_motion: np.ndarray
    stalls: np.ndarray
    catchups: np.ndarray
    continuity_verdict: str
    continuity_reasons: list[str]
    statistics: dict[str, float | int | None]


@dataclass
class AnalysisResult:
    capture: SequenceCapture
    primary: ScaleAnalysis
    scales: list[ScaleAnalysis]
    energy: np.ndarray
    novelty: np.ndarray
    dt_seconds: np.ndarray
    cadence_slots: np.ndarray
    missed_slots: np.ndarray
    cadence_jitter_seconds: np.ndarray
    cadence_verdict: str
    cadence_reasons: list[str]
    alias_verdict: str
    alias_reasons: list[str]
    motion_legibility_verdict: str
    motion_legibility_reasons: list[str]
    energy_verdict: str
    energy_reasons: list[str]
    representation_verdict: str
    representation_reasons: list[str]
    representation_series: dict[str, np.ndarray]
    representation_statistics: dict[str, dict[str, float | int]]
    overall_verdict: str
    speed_spectrum: SpectrumPeak
    energy_spectrum: SpectrumPeak
    contrast_spectrum: SpectrumPeak
    phase_timing_best_lag: int | None
    summary: dict[str, object]


def _finite_float(value: str | None, label: str) -> float:
    if value is None or value == "":
        return math.nan
    try:
        parsed = float(value)
    except ValueError as error:
        raise ValueError(f"invalid {label}: {value!r}") from error
    if not math.isfinite(parsed):
        raise ValueError(f"non-finite {label}: {value!r}")
    return parsed


def _read_manifest(path: Path) -> list[dict[str, str]]:
    if not path.is_file():
        raise ValueError(f"missing manifest: {path}")
    with path.open(newline="", encoding="utf-8") as source:
        rows = list(csv.DictReader(source, delimiter="\t"))
    if not rows:
        raise ValueError(f"empty manifest: {path}")
    if "phase_degrees" not in rows[0]:
        raise ValueError(f"manifest has no phase_degrees column: {path}")
    return rows


def _contiguous_frame_paths(directory: Path, count: int) -> list[Path]:
    paths = [directory / f"frame-{index:05d}.ppm" for index in range(count)]
    missing = [path.name for path in paths if not path.is_file()]
    if missing:
        example = ", ".join(missing[:4])
        raise ValueError(f"missing contiguous PPM frames in {directory}: {example}")
    expected_names = {path.name for path in paths}
    unexpected = sorted(
        path.name
        for path in directory.glob("frame-*.ppm")
        if path.name not in expected_names
    )
    if unexpected:
        raise ValueError(
            f"sequence has stale/non-contiguous PPM frames in {directory}: "
            + ", ".join(unexpected[:4])
        )
    return paths


def _linear_rgb(encoded: np.ndarray) -> np.ndarray:
    srgb = encoded.astype(np.float32) / 255.0
    return np.where(
        srgb <= 0.04045,
        srgb / 12.92,
        ((srgb + 0.055) / 1.055) ** 2.4,
    ).astype(np.float32)


def _signal_from_rgb(rgb: np.ndarray, channel: str) -> np.ndarray:
    if channel == "green-opponent":
        return np.maximum(
            rgb[..., 1] - 0.5 * (rgb[..., 0] + rgb[..., 2]), 0.0
        ).astype(np.float32)
    if channel == "luminance":
        return (
            rgb[..., 0] * 0.2126
            + rgb[..., 1] * 0.7152
            + rgb[..., 2] * 0.0722
        ).astype(np.float32)
    raise ValueError(f"unknown analysis channel: {channel}")


def _timeline(
    rows: list[dict[str, str]],
    fallback_fps: float | None,
) -> tuple[np.ndarray, np.ndarray, float, str]:
    count = len(rows)
    fps_values = np.array(
        [_finite_float(row.get("fps"), "fps") for row in rows], dtype=np.float64
    )
    finite_fps = fps_values[np.isfinite(fps_values) & (fps_values > 0.0)]
    if fallback_fps is not None:
        if not math.isfinite(fallback_fps) or fallback_fps <= 0.0:
            raise ValueError("fallback fps must be finite and positive")
        fps = float(fallback_fps)
    elif finite_fps.size:
        fps = float(np.median(finite_fps))
        if np.max(np.abs(finite_fps - fps)) > 1.0e-5:
            raise ValueError("manifest contains inconsistent fps values")
    else:
        raise ValueError("manifest needs fps or the analyzer needs --fps")

    timestamp_field = next((field for field in (
        "presentation_time_ns", "present_ns", "swap_return_ns",
        "swap_return_ms",
    ) if field in rows[0]), None)
    if timestamp_field is not None:
        raw = np.array(
            [
                _finite_float(row.get(timestamp_field), timestamp_field)
                for row in rows
            ],
            dtype=np.float64,
        )
        if np.any(np.diff(raw) <= 0.0):
            raise ValueError(f"{timestamp_field} must increase strictly")
        unit_seconds = 1.0e-3 if timestamp_field.endswith("_ms") else 1.0e-9
        times = (raw - raw[0]) * unit_seconds
        timeline_kind = (
            "swap_return_proxy"
            if timestamp_field.startswith("swap_return")
            else "confirmed_presentation"
        )
    elif "presentation_interval_ms" in rows[0]:
        intervals = np.array(
            [
                _finite_float(row.get("presentation_interval_ms"),
                              "presentation_interval_ms")
                for row in rows
            ],
            dtype=np.float64,
        ) * 1.0e-3
        if np.any(intervals <= 0.0):
            raise ValueError("presentation intervals must be positive")
        times = np.zeros(count, dtype=np.float64)
        if count > 1:
            times[1:] = np.cumsum(intervals[1:])
        timeline_kind = "synthetic_intervals"
    else:
        times = np.arange(count, dtype=np.float64) / fps
        timeline_kind = "fixed_cfr"

    declared_sources = {
        row.get("timing_source", "").strip() for row in rows
        if row.get("timing_source", "").strip()
    }
    if len(declared_sources) > 1:
        raise ValueError("manifest contains inconsistent timing_source values")
    if declared_sources:
        declared = next(iter(declared_sources))
        if declared == "swap_return_proxy_replay":
            timeline_kind = declared
        elif declared not in ("confirmed_presentation", "fixed_cfr"):
            raise ValueError(f"unknown manifest timing_source: {declared}")

    rps = np.array(
        [_finite_float(row.get("rps"), "rps") for row in rows],
        dtype=np.float64,
    )
    return times, rps, fps, timeline_kind


def _resize_for_analysis(
    image: Image.Image,
    analysis_width: int,
) -> tuple[Image.Image, tuple[int, int]]:
    original = image.size
    if analysis_width <= 0 or original[0] <= analysis_width:
        return image.convert("RGB"), original
    height = max(1, int(round(original[1] * analysis_width / original[0])))
    return (
        image.convert("RGB").resize(
            (analysis_width, height), Image.Resampling.LANCZOS
        ),
        original,
    )


def load_sequence(
    directory: Path,
    *,
    analysis_width: int = 192,
    channel: str = "green-opponent",
    fallback_fps: float | None = None,
) -> SequenceCapture:
    directory = directory.resolve()
    rows = _read_manifest(directory / "manifest.tsv")
    paths = _contiguous_frame_paths(directory, len(rows))
    frames: list[np.ndarray] = []
    original_size: tuple[int, int] | None = None
    analysis_size: tuple[int, int] | None = None
    for path in paths:
        with Image.open(path) as source:
            resized, current_original = _resize_for_analysis(
                source, analysis_width
            )
            if original_size is None:
                original_size = current_original
                analysis_size = resized.size
            elif current_original != original_size or resized.size != analysis_size:
                raise ValueError(f"frame dimensions change within {directory}")
            frames.append(np.asarray(resized, dtype=np.uint8))
    encoded = np.stack(frames)
    rgb = _linear_rgb(encoded)
    signal = _signal_from_rgb(rgb, channel)
    phases = np.array(
        [
            _finite_float(row.get("phase_degrees"), "phase_degrees")
            for row in rows
        ],
        dtype=np.float64,
    )
    times, rps, fps, timeline_kind = _timeline(rows, fallback_fps)
    assert original_size is not None and analysis_size is not None
    return SequenceCapture(
        directory=directory,
        paths=paths,
        rows=rows,
        rgb=rgb,
        signal=signal,
        phase_degrees=phases,
        times_seconds=times,
        rps=rps,
        fps=fps,
        timeline_kind=timeline_kind,
        original_size=original_size,
        analysis_size=analysis_size,
    )


def _box_blur_axis(values: np.ndarray, radius: int, axis: int) -> np.ndarray:
    if radius <= 0:
        return values
    padding = [(0, 0)] * values.ndim
    padding[axis] = (radius, radius)
    padded = np.pad(values, padding, mode="edge")
    cumulative = np.cumsum(padded, axis=axis, dtype=np.float64)
    zero_shape = list(cumulative.shape)
    zero_shape[axis] = 1
    cumulative = np.concatenate(
        (np.zeros(zero_shape, dtype=np.float64), cumulative), axis=axis
    )
    high = [slice(None)] * values.ndim
    low = [slice(None)] * values.ndim
    high[axis] = slice(2 * radius + 1, None)
    low[axis] = slice(None, -(2 * radius + 1))
    return (
        (cumulative[tuple(high)] - cumulative[tuple(low)])
        / float(2 * radius + 1)
    ).astype(np.float32)


def spatial_blur(values: np.ndarray, radius: int) -> np.ndarray:
    """A deterministic separable box approximation used for multi-scale QA."""

    if radius <= 0:
        return values.astype(np.float32, copy=False)
    return _box_blur_axis(
        _box_blur_axis(values, radius, axis=2), radius, axis=1
    )


class PhaseModel:
    """Linear image basis parameterized by the known periodic groove phase."""

    def __init__(
        self,
        mean_image: np.ndarray,
        spatial_basis: np.ndarray,
        spatial_decoder: np.ndarray,
        phase_mask: np.ndarray,
        energy_mask: np.ndarray,
        harmonic_count: int,
    ) -> None:
        self.mean_image = mean_image
        self.spatial_basis = spatial_basis
        self.spatial_decoder = spatial_decoder
        self.phase_mask = phase_mask
        self.energy_mask = energy_mask
        self.harmonic_count = harmonic_count

    @classmethod
    def fit(
        cls,
        template_signal: np.ndarray,
        physical_phase_degrees: np.ndarray,
        groove_count: int,
        harmonic_count: int = 5,
    ) -> "PhaseModel":
        if template_signal.ndim != 3:
            raise ValueError("template signal must have shape frames x height x width")
        if len(template_signal) != len(physical_phase_degrees):
            raise ValueError("template frames and phases differ in length")
        if len(template_signal) < 2 * harmonic_count + 1:
            raise ValueError(
                f"need at least {2 * harmonic_count + 1} template phases "
                f"for {harmonic_count} harmonics"
            )
        groove_phase = np.deg2rad(physical_phase_degrees * groove_count)
        columns = [np.ones_like(groove_phase)]
        for harmonic in range(1, harmonic_count + 1):
            columns.extend((
                np.cos(harmonic * groove_phase),
                np.sin(harmonic * groove_phase),
            ))
        design = np.stack(columns, axis=1)
        condition = float(np.linalg.cond(design))
        if not math.isfinite(condition) or condition > 1.0e6:
            raise ValueError(
                "template phases do not span one groove period well enough "
                f"(design condition {condition:.3g})"
            )

        flat = template_signal.reshape(len(template_signal), -1).astype(np.float64)
        coefficients = np.linalg.pinv(design, rcond=1.0e-8) @ flat
        mean_flat = coefficients[0]
        basis_all = coefficients[1:].T
        variance = np.std(template_signal, axis=0, dtype=np.float64)
        positive = variance > max(float(np.max(variance)) * 1.0e-4, 1.0e-7)
        if not np.any(positive):
            raise ValueError("template sequence has no phase-dependent pixels")
        cutoff = float(np.quantile(variance[positive], 0.20))
        phase_mask = variance >= cutoff

        mean_image = mean_flat.reshape(template_signal.shape[1:]).astype(np.float32)
        border = np.concatenate((
            mean_image[0], mean_image[-1], mean_image[:, 0], mean_image[:, -1]
        ))
        background = float(np.median(border))
        dynamic = max(float(np.max(mean_image) - background), 1.0e-6)
        energy_mask = mean_image > background + 0.01 * dynamic
        energy_mask |= phase_mask

        masked_basis = basis_all[phase_mask.reshape(-1)]
        gram = masked_basis.T @ masked_basis
        ridge = max(float(np.trace(gram)) / max(1, len(gram)) * 1.0e-8, 1.0e-12)
        spatial_decoder = np.linalg.solve(
            gram + np.eye(gram.shape[0]) * ridge,
            masked_basis.T,
        )
        return cls(
            mean_image=mean_image,
            spatial_basis=masked_basis.astype(np.float64),
            spatial_decoder=spatial_decoder.astype(np.float64),
            phase_mask=phase_mask,
            energy_mask=energy_mask,
            harmonic_count=harmonic_count,
        )

    def estimate(self, observed_signal: np.ndarray) -> PhaseEstimate:
        if observed_signal.shape[1:] != self.mean_image.shape:
            raise ValueError("observed and template analysis dimensions differ")
        residual = (
            observed_signal - self.mean_image[None, :, :]
        )[:, self.phase_mask].astype(np.float64)
        coefficients = residual @ self.spatial_decoder.T
        cosine = coefficients[:, 0::2]
        sine = coefficients[:, 1::2]
        amplitudes = np.sqrt(cosine * cosine + sine * sine)
        phase = np.arctan2(sine[:, 0], cosine[:, 0])
        predicted = coefficients @ self.spatial_basis.T
        residual_energy = np.sum((residual - predicted) ** 2, axis=1)
        observed_energy = np.sum(residual * residual, axis=1)
        fit_quality = np.clip(
            1.0 - residual_energy / np.maximum(observed_energy, EPSILON),
            0.0,
            1.0,
        )
        confidence = amplitudes[:, 0] * np.sqrt(fit_quality)
        return PhaseEstimate(
            phase_radians=phase,
            harmonic_amplitudes=amplitudes,
            fit_quality=fit_quality,
            confidence=confidence,
        )


def _wrap_cycles(values: np.ndarray) -> np.ndarray:
    return np.remainder(values + 0.5, 1.0) - 0.5


def _safe_percentile(values: np.ndarray, percentile: float) -> float | None:
    finite = values[np.isfinite(values)]
    return float(np.percentile(finite, percentile)) if finite.size else None


def _coefficient_of_variation(values: np.ndarray) -> float:
    finite = values[np.isfinite(values)]
    if finite.size < 2:
        return 0.0
    mean = float(np.mean(finite))
    return float(np.std(finite) / max(abs(mean), EPSILON))


def _expected_cycles(
    capture: SequenceCapture,
    groove_count: int,
) -> tuple[np.ndarray, np.ndarray]:
    count = len(capture.rows)
    expected = np.full(count, np.nan, dtype=np.float64)
    rendered = np.full(count, np.nan, dtype=np.float64)
    if count < 2:
        return expected, rendered
    rendered[1:] = np.diff(capture.phase_degrees) * groove_count / 360.0
    dt = np.diff(capture.times_seconds)
    finite_rps = np.isfinite(capture.rps)
    valid = finite_rps[1:] & finite_rps[:-1]
    expected[1:][valid] = (
        0.5 * (capture.rps[1:][valid] + capture.rps[:-1][valid])
        * groove_count
        * dt[valid]
    )
    return expected, rendered


def _constant_manifest_text(
    rows: list[dict[str, str]], field: str
) -> str | None:
    values = {row.get(field, "").strip() for row in rows}
    values.discard("")
    if len(values) > 1:
        raise ValueError(f"manifest changes {field} within one analysis sequence")
    return next(iter(values)) if values else None


def _validate_template_compatibility(
    capture: SequenceCapture,
    templates: SequenceCapture,
) -> None:
    if capture.original_size != templates.original_size:
        raise ValueError(
            "tested and template sequences must use the same source resolution: "
            f"{capture.original_size} vs {templates.original_size}"
        )
    if capture.analysis_size != templates.analysis_size:
        raise ValueError(
            "tested and template sequences have different analysis dimensions: "
            f"{capture.analysis_size} vs {templates.analysis_size}"
        )
    # Current manifests always contain mode; future live/replay schemas may add
    # the remaining context fields. Validate any metadata that both sides
    # provide instead of silently learning a different camera/shader manifold.
    for field in ("mode", "model", "preset", "camera_preset", "bloom"):
        tested = _constant_manifest_text(capture.rows, field)
        reference = _constant_manifest_text(templates.rows, field)
        if tested is not None and reference is not None and tested != reference:
            raise ValueError(
                f"tested/template {field} mismatch: {tested!r} vs {reference!r}"
            )


def _representation_series(
    rows: list[dict[str, str]],
) -> dict[str, np.ndarray]:
    series: dict[str, np.ndarray] = {}
    for field in REPRESENTATION_FLOAT_FIELDS + REPRESENTATION_INTEGER_FIELDS:
        if field not in rows[0]:
            continue
        values: list[float] = []
        missing = False
        for row in rows:
            raw = row.get(field, "").strip()
            if not raw:
                missing = True
                break
            values.append(_finite_float(raw, field))
        if missing:
            raise ValueError(
                f"representation column {field} is present but has missing values"
            )
        encoded = np.asarray(values, dtype=np.float64)
        if field in REPRESENTATION_INTEGER_FIELDS:
            rounded = np.rint(encoded)
            if np.any(np.abs(encoded - rounded) > 1.0e-9):
                raise ValueError(f"representation column {field} must be integral")
            encoded = rounded
        series[field] = encoded
    return series


def _representation_stability(
    series: dict[str, np.ndarray],
    *,
    exact_constant_conditions: bool,
) -> tuple[str, list[str], dict[str, dict[str, float | int]]]:
    if not series:
        return "NOT_AVAILABLE", [
            "manifest has no temporal-representation telemetry"
        ], {}
    reasons: list[str] = []
    statistics: dict[str, dict[str, float | int]] = {}
    for field, values in series.items():
        minimum = float(np.min(values))
        maximum = float(np.max(values))
        value_range = maximum - minimum
        unique_count = int(len(np.unique(values)))
        cv = _coefficient_of_variation(values)
        statistics[field] = {
            "minimum": minimum,
            "maximum": maximum,
            "range": value_range,
            "cv": cv,
            "unique_count": unique_count,
        }
        if not exact_constant_conditions:
            continue
        if field in REPRESENTATION_INTEGER_FIELDS:
            unstable = unique_count > 1
        else:
            tolerance = max(1.0e-6, abs(float(np.median(values))) * 1.0e-4)
            unstable = value_range > tolerance
        if unstable:
            reasons.append(
                f"constant-input representation field {field} changes "
                f"from {minimum:.6g} to {maximum:.6g}"
            )
    if not exact_constant_conditions:
        return "DIAGNOSTIC", [
            "representation telemetry recorded, but cadence/RPS varies; "
            "ranges are diagnostic rather than constant-input gates"
        ], statistics
    return ("PASS" if not reasons else "FAIL"), reasons, statistics


def _dominant_spectrum_peak(
    values: np.ndarray,
    times: np.ndarray,
    minimum_hz: float = 0.5,
    maximum_hz: float = 30.0,
) -> SpectrumPeak:
    finite = np.isfinite(values) & np.isfinite(times)
    if np.count_nonzero(finite) < 16:
        return SpectrumPeak(0.0, 0.0, 0.0)
    selected_times = times[finite]
    selected = values[finite]
    if selected_times[-1] <= selected_times[0]:
        return SpectrumPeak(0.0, 0.0, 0.0)
    dt = float(np.median(np.diff(selected_times)))
    if not math.isfinite(dt) or dt <= 0.0:
        return SpectrumPeak(0.0, 0.0, 0.0)
    uniform_times = np.arange(
        selected_times[0], selected_times[-1] + dt * 0.25, dt
    )
    uniform = np.interp(uniform_times, selected_times, selected)
    uniform -= np.mean(uniform)
    if len(uniform) < 16 or float(np.max(np.abs(uniform))) <= EPSILON:
        return SpectrumPeak(0.0, 0.0, 0.0)
    window = np.hanning(len(uniform))
    spectrum = np.fft.rfft(uniform * window)
    frequencies = np.fft.rfftfreq(len(uniform), dt)
    amplitudes = 2.0 * np.abs(spectrum) / max(float(np.sum(window)), EPSILON)
    band = (
        (frequencies >= minimum_hz)
        & (frequencies <= min(maximum_hz, 0.5 / dt))
    )
    if not np.any(band):
        return SpectrumPeak(0.0, 0.0, 0.0)
    band_indices = np.flatnonzero(band)
    peak_index = int(band_indices[np.argmax(amplitudes[band])])
    noise = float(np.median(amplitudes[band]))
    peak = float(amplitudes[peak_index])
    return SpectrumPeak(
        frequency_hz=float(frequencies[peak_index]),
        amplitude=peak,
        signal_to_noise=peak / max(noise, EPSILON),
    )


def _best_timing_lag(
    rendered_cycles: np.ndarray,
    expected_cycles: np.ndarray,
) -> int | None:
    # Lag is defined as rendered[t] matching expected[t-lag].  A correct
    # presentation path is zero; the classic previous-dt bug is +1.
    best_score: tuple[float, int] | None = None
    best_lag: int | None = None
    for lag in range(-2, 3):
        if lag < 0:
            left = rendered_cycles[1:lag]
            right = expected_cycles[1 - lag:]
        elif lag > 0:
            left = rendered_cycles[1 + lag:]
            right = expected_cycles[1:-lag]
        else:
            left = rendered_cycles[1:]
            right = expected_cycles[1:]
        finite = np.isfinite(left) & np.isfinite(right)
        if np.count_nonzero(finite) < 8:
            continue
        a = left[finite]
        b = right[finite]
        if np.std(a) <= EPSILON or np.std(b) <= EPSILON:
            continue
        correlation = float(np.corrcoef(a, b)[0, 1])
        score = (correlation, -abs(lag))
        if best_score is None or score > best_score:
            best_score = score
            best_lag = lag
    return best_lag


def _analyze_scale(
    capture: SequenceCapture,
    model: PhaseModel,
    estimate: PhaseEstimate,
    groove_count: int,
    thresholds: Thresholds,
    radius: int,
) -> ScaleAnalysis:
    count = len(capture.rows)
    apparent_delta = np.full(count, np.nan, dtype=np.float64)
    if count > 1:
        phase_delta = np.angle(
            np.exp(1j * (
                estimate.phase_radians[1:] - estimate.phase_radians[:-1]
            ))
        )
        apparent_delta[1:] = phase_delta / (2.0 * math.pi)
    expected, rendered = _expected_cycles(capture, groove_count)
    error = np.full(count, np.nan, dtype=np.float64)
    error[1:] = _wrap_cycles(apparent_delta[1:] - expected[1:])
    confidence_pair = np.full(count, np.nan, dtype=np.float64)
    if count > 1:
        confidence_pair[1:] = np.minimum(
            estimate.confidence[1:], estimate.confidence[:-1]
        )
    valid = (
        np.isfinite(expected)
        & (np.abs(expected) > 1.0e-8)
        & (np.abs(expected) <= thresholds.maximum_trackable_cycles)
        & (confidence_pair >= thresholds.min_phase_confidence)
    )
    speed_ratio = np.full(count, np.nan, dtype=np.float64)
    speed_ratio[valid] = apparent_delta[valid] / expected[valid]
    stalls = valid & (speed_ratio < thresholds.stall_speed_ratio)
    catchups = valid & (speed_ratio > thresholds.catchup_speed_ratio)

    phase_p99 = _safe_percentile(np.abs(error[valid]), 99.0)
    speed_p01 = _safe_percentile(speed_ratio[valid], 1.0)
    speed_p99 = _safe_percentile(speed_ratio[valid], 99.0)
    speed_peak = _dominant_spectrum_peak(
        speed_ratio[valid], capture.times_seconds[valid]
    )
    reasons: list[str] = []
    if np.count_nonzero(valid) < max(8, count // 10):
        verdict = "INDETERMINATE"
        reasons.append(
            "too few confident below-Nyquist frames to estimate apparent speed"
        )
    else:
        if phase_p99 is not None and (
            phase_p99 > thresholds.maximum_p99_phase_error_cycles
        ):
            reasons.append(
                f"p99 apparent phase-step error {phase_p99:.4f} cycles exceeds "
                f"{thresholds.maximum_p99_phase_error_cycles:.4f}"
            )
        if speed_p01 is not None and (
            speed_p01 < thresholds.minimum_p01_speed_ratio
        ):
            reasons.append(
                f"p01 apparent speed ratio {speed_p01:.3f} is below "
                f"{thresholds.minimum_p01_speed_ratio:.3f}"
            )
        if speed_p99 is not None and (
            speed_p99 > thresholds.maximum_p99_speed_ratio
        ):
            reasons.append(
                f"p99 apparent speed ratio {speed_p99:.3f} exceeds "
                f"{thresholds.maximum_p99_speed_ratio:.3f}"
            )
        if np.any(stalls):
            reasons.append(f"detected {int(np.count_nonzero(stalls))} visual stalls")
        if speed_peak.amplitude > thresholds.maximum_speed_beat_amplitude:
            reasons.append(
                f"periodic speed modulation is {speed_peak.amplitude:.3f} at "
                f"{speed_peak.frequency_hz:.2f} Hz"
            )
        verdict = "PASS" if not reasons else "FAIL"

    statistics: dict[str, float | int | None] = {
        "valid_motion_frames": int(np.count_nonzero(valid)),
        "stall_frames": int(np.count_nonzero(stalls)),
        "catchup_frames": int(np.count_nonzero(catchups)),
        "p99_absolute_phase_error_cycles": phase_p99,
        "p01_speed_ratio": speed_p01,
        "median_speed_ratio": _safe_percentile(speed_ratio[valid], 50.0),
        "p99_speed_ratio": speed_p99,
        "median_contrast": _safe_percentile(estimate.harmonic_amplitudes[:, 0], 50.0),
        "contrast_cv": _coefficient_of_variation(
            estimate.harmonic_amplitudes[:, 0]
        ),
        "median_confidence": _safe_percentile(estimate.confidence, 50.0),
        "speed_beat_frequency_hz": speed_peak.frequency_hz,
        "speed_beat_amplitude": speed_peak.amplitude,
        "speed_beat_snr": speed_peak.signal_to_noise,
    }
    return ScaleAnalysis(
        radius_pixels=radius,
        apparent_phase_radians=estimate.phase_radians,
        contrast=estimate.harmonic_amplitudes[:, 0],
        confidence=estimate.confidence,
        fit_quality=estimate.fit_quality,
        apparent_delta_cycles=apparent_delta,
        expected_delta_cycles=expected,
        rendered_delta_cycles=rendered,
        phase_error_cycles=error,
        speed_ratio=speed_ratio,
        valid_motion=valid,
        stalls=stalls,
        catchups=catchups,
        continuity_verdict=verdict,
        continuity_reasons=reasons,
        statistics=statistics,
    )


def analyze_capture(
    capture: SequenceCapture,
    templates: SequenceCapture,
    *,
    groove_count: int = DEFAULT_GROOVE_COUNT,
    harmonic_count: int = 5,
    spatial_scales: Sequence[int] = DEFAULT_SPATIAL_SCALES,
    thresholds: Thresholds = Thresholds(),
) -> AnalysisResult:
    if groove_count < 1:
        raise ValueError("groove count must be positive")
    if harmonic_count < 1:
        raise ValueError("harmonic count must be positive")
    _validate_template_compatibility(capture, templates)
    scales = sorted(dict.fromkeys(int(radius) for radius in spatial_scales))
    if not scales or any(radius < 0 for radius in scales):
        raise ValueError("spatial scales must contain non-negative radii")

    analyses: list[ScaleAnalysis] = []
    models: list[PhaseModel] = []
    for radius in scales:
        template_signal = spatial_blur(templates.signal, radius)
        observed_signal = spatial_blur(capture.signal, radius)
        model = PhaseModel.fit(
            template_signal,
            templates.phase_degrees,
            groove_count,
            harmonic_count,
        )
        estimate = model.estimate(observed_signal)
        models.append(model)
        analyses.append(_analyze_scale(
            capture, model, estimate, groove_count, thresholds, radius
        ))

    # Gate on the highest-fidelity requested scale. Coarser scales remain in
    # the report as valuable diagnostics, but perspective/occlusion means a
    # screen-space blur is not a pure angular convolution and can bias phase
    # near the tread silhouette. Majority voting across those biased variants
    # would reject a pixel-perfect source sequence.
    primary_index = 0
    primary = analyses[primary_index]
    primary_model = models[primary_index]
    signal = spatial_blur(capture.signal, scales[primary_index])
    energy = np.sum(signal[:, primary_model.energy_mask], axis=1, dtype=np.float64)
    novelty = np.zeros(len(capture.rows), dtype=np.float64)
    if len(capture.rows) > 1:
        differences = np.diff(signal[:, primary_model.energy_mask], axis=0)
        novelty[1:] = np.sqrt(np.mean(differences * differences, axis=1))

    dt = np.full(len(capture.rows), np.nan, dtype=np.float64)
    if len(capture.rows) > 1:
        dt[1:] = np.diff(capture.times_seconds)
    nominal_interval = 1.0 / capture.fps
    # Assign each delivered timestamp to the nearest nominal slot relative to
    # the original epoch, then difference those cumulative positions.  Rounding
    # each interval independently would accumulate fractional cadence error and
    # can both invent and hide missed slots.
    cadence_slots = np.zeros(len(capture.rows), dtype=np.int64)
    finite_dt = np.isfinite(dt) & (dt > 0.0)
    elapsed = capture.times_seconds - capture.times_seconds[0]
    cumulative_slots = np.rint(elapsed / nominal_interval).astype(np.int64)
    if len(capture.rows) > 1:
        cadence_slots[1:] = np.diff(cumulative_slots)
    missed_slots = np.maximum(cadence_slots - 1, 0)
    cadence_jitter = elapsed - cumulative_slots * nominal_interval
    cadence_jitter[0] = np.nan
    cadence_reasons: list[str] = []
    if np.any(cadence_slots[1:] <= 0):
        cadence_reasons.append(
            "trace maps multiple delivered frames to the same nominal slot"
        )
    if int(np.sum(missed_slots)) > 0:
        cadence_reasons.append(
            f"presentation trace missed {int(np.sum(missed_slots))} nominal slots"
        )
    jitter_p99 = _safe_percentile(np.abs(cadence_jitter[1:]), 99.0)
    if jitter_p99 is not None and (
        jitter_p99
        > thresholds.maximum_cadence_jitter_fraction * nominal_interval
    ):
        cadence_reasons.append(
            f"p99 cadence residual {jitter_p99 * 1000.0:.3f} ms exceeds "
            f"{thresholds.maximum_cadence_jitter_fraction:.2f} frame"
        )
    cadence_verdict = "PASS" if not cadence_reasons else "FAIL"

    constant_speed = (
        np.count_nonzero(np.isfinite(capture.rps)) >= 2
        and _coefficient_of_variation(capture.rps[np.isfinite(capture.rps)])
        < 1.0e-5
    )
    energy_cv = _coefficient_of_variation(energy)
    contrast_cv = _coefficient_of_variation(primary.contrast)
    energy_reasons: list[str] = []
    if constant_speed and energy_cv > thresholds.maximum_constant_speed_energy_cv:
        energy_reasons.append(
            f"constant-speed glow-energy CV {energy_cv:.4f} exceeds "
            f"{thresholds.maximum_constant_speed_energy_cv:.4f}"
        )
    if constant_speed and (
        contrast_cv > thresholds.maximum_constant_speed_contrast_cv
    ):
        energy_reasons.append(
            f"constant-speed pattern-contrast CV {contrast_cv:.4f} exceeds "
            f"{thresholds.maximum_constant_speed_contrast_cv:.4f}"
        )
    energy_verdict = "PASS" if not energy_reasons else "FAIL"

    representation_series = _representation_series(capture.rows)
    exact_representation_conditions = (
        constant_speed
        and np.all(cadence_slots[1:] == 1)
        and jitter_p99 is not None
        and jitter_p99 <= nominal_interval * 1.0e-4
    )
    (
        representation_verdict,
        representation_reasons,
        representation_statistics,
    ) = _representation_stability(
        representation_series,
        exact_constant_conditions=exact_representation_conditions,
    )

    expected_abs = np.abs(primary.expected_delta_cycles)
    unsafe = np.isfinite(expected_abs) & (expected_abs >= 0.5)
    alias_reasons: list[str] = []
    if np.any(unsafe):
        alias_contrast = _safe_percentile(primary.contrast[unsafe], 95.0)
        if alias_contrast is not None and (
            alias_contrast > thresholds.maximum_alias_contrast
        ):
            alias_reasons.append(
                f"above-Nyquist p95 residual groove contrast {alias_contrast:.3f} "
                f"exceeds {thresholds.maximum_alias_contrast:.3f}"
            )
        alias_verdict = "PASS" if not alias_reasons else "FAIL"
    else:
        alias_verdict = "NOT_EVALUATED"
        alias_reasons.append("sequence contains no above-Nyquist frames")

    legibility_reasons: list[str] = []
    safely_trackable = (
        np.isfinite(expected_abs)
        & (expected_abs > 1.0e-8)
        & (expected_abs <= 0.35)
    )
    if np.any(safely_trackable):
        median_trackable = _safe_percentile(
            primary.confidence[safely_trackable], 50.0
        )
        if median_trackable is not None and (
            median_trackable < thresholds.min_phase_confidence
        ):
            legibility_reasons.append(
                f"below-Nyquist median motion confidence {median_trackable:.3f} "
                f"is below {thresholds.min_phase_confidence:.3f}"
            )
    if np.any(unsafe):
        median_unsafe = _safe_percentile(primary.contrast[unsafe], 50.0)
        if median_unsafe is not None and (
            median_unsafe <= thresholds.maximum_alias_contrast
        ):
            legibility_reasons.append(
                "high-speed alias protection removes the repeating tread's "
                "recoverable motion cue"
            )
            motion_legibility_verdict = "CUE_ABSENT"
        elif alias_verdict == "FAIL":
            motion_legibility_verdict = "ALIASED_CUE"
        else:
            motion_legibility_verdict = "PASS"
    elif legibility_reasons:
        motion_legibility_verdict = "FAIL"
    else:
        motion_legibility_verdict = "PASS"

    continuity_overall = primary.continuity_verdict

    phase_lag = _best_timing_lag(
        primary.rendered_delta_cycles,
        primary.expected_delta_cycles,
    )
    if phase_lag not in (None, 0):
        continuity_overall = "FAIL"
        primary.continuity_reasons.append(
            f"rendered phase follows presentation timing at lag {phase_lag:+d}, "
            "not lag zero"
        )

    capture_duration = (
        float(capture.times_seconds[-1] - capture.times_seconds[0])
        if len(capture.rows) > 1 else 0.0
    )
    if capture_duration < thresholds.minimum_capture_duration_seconds:
        if continuity_overall != "FAIL":
            continuity_overall = "INDETERMINATE"
        primary.continuity_reasons.append(
            f"capture duration {capture_duration:.3f}s is shorter than the "
            f"{thresholds.minimum_capture_duration_seconds:.3f}s recurrence gate"
        )

    failure_components = (
        continuity_overall == "FAIL",
        cadence_verdict == "FAIL",
        alias_verdict == "FAIL",
        energy_verdict == "FAIL",
        motion_legibility_verdict == "FAIL",
        representation_verdict == "FAIL",
    )
    if any(failure_components):
        overall = "FAIL"
    elif continuity_overall == "INDETERMINATE":
        overall = "INDETERMINATE"
    else:
        overall = "PASS"

    speed_spectrum = _dominant_spectrum_peak(
        primary.speed_ratio[primary.valid_motion],
        capture.times_seconds[primary.valid_motion],
    )
    energy_normalized = energy / max(float(np.mean(energy)), EPSILON)
    contrast_normalized = primary.contrast / max(
        float(np.mean(primary.contrast)), EPSILON
    )
    energy_spectrum = _dominant_spectrum_peak(
        energy_normalized, capture.times_seconds
    )
    contrast_spectrum = _dominant_spectrum_peak(
        contrast_normalized, capture.times_seconds
    )

    summary: dict[str, object] = {
        "schema": "wheel-perceptual-stutter-v1",
        "overall_verdict": overall,
        "renderer_continuity_verdict": continuity_overall,
        "delivery_cadence_verdict": cadence_verdict,
        "alias_safety_verdict": alias_verdict,
        "motion_legibility_verdict": motion_legibility_verdict,
        "energy_stability_verdict": energy_verdict,
        "representation_stability_verdict": representation_verdict,
        "frame_count": len(capture.rows),
        "capture_fps": capture.fps,
        "timeline_kind": capture.timeline_kind,
        "capture_duration_seconds": capture_duration,
        "groove_count": groove_count,
        "analysis_size": list(capture.analysis_size),
        "spatial_scales": scales,
        "phase_timing_best_lag": phase_lag,
        "missed_presentation_slots": int(np.sum(missed_slots)),
        "p99_cadence_jitter_ms": None if jitter_p99 is None else jitter_p99 * 1000.0,
        "constant_speed_energy_cv": energy_cv,
        "constant_speed_contrast_cv": contrast_cv,
        "speed_spectrum": asdict(speed_spectrum),
        "energy_spectrum": asdict(energy_spectrum),
        "contrast_spectrum": asdict(contrast_spectrum),
        "renderer_reasons": [
            reason for analysis in analyses for reason in analysis.continuity_reasons
        ],
        "cadence_reasons": cadence_reasons,
        "alias_reasons": alias_reasons,
        "motion_legibility_reasons": legibility_reasons,
        "energy_reasons": energy_reasons,
        "representation_reasons": representation_reasons,
        "representation_statistics": representation_statistics,
        "per_scale": [
            {
                "radius_pixels": analysis.radius_pixels,
                "verdict": analysis.continuity_verdict,
                "reasons": analysis.continuity_reasons,
                **analysis.statistics,
            }
            for analysis in analyses
        ],
        "thresholds": asdict(thresholds),
    }
    return AnalysisResult(
        capture=capture,
        primary=primary,
        scales=analyses,
        energy=energy,
        novelty=novelty,
        dt_seconds=dt,
        cadence_slots=cadence_slots,
        missed_slots=missed_slots,
        cadence_jitter_seconds=cadence_jitter,
        cadence_verdict=cadence_verdict,
        cadence_reasons=cadence_reasons,
        alias_verdict=alias_verdict,
        alias_reasons=alias_reasons,
        motion_legibility_verdict=motion_legibility_verdict,
        motion_legibility_reasons=legibility_reasons,
        energy_verdict=energy_verdict,
        energy_reasons=energy_reasons,
        representation_verdict=representation_verdict,
        representation_reasons=representation_reasons,
        representation_series=representation_series,
        representation_statistics=representation_statistics,
        overall_verdict=overall,
        speed_spectrum=speed_spectrum,
        energy_spectrum=energy_spectrum,
        contrast_spectrum=contrast_spectrum,
        phase_timing_best_lag=phase_lag,
        summary=summary,
    )


def _format_number(value: float | int | None, digits: int = 4) -> str:
    if value is None:
        return "n/a"
    if isinstance(value, float) and not math.isfinite(value):
        return "n/a"
    return f"{value:.{digits}f}" if isinstance(value, float) else str(value)


def write_metrics(path: Path, result: AnalysisResult) -> None:
    fields = [
        "frame", "time_seconds", "dt_ms", "cadence_slots", "missed_slots",
        "cadence_jitter_ms", "phase_degrees", "expected_delta_cycles",
        "rendered_delta_cycles", "apparent_phase_cycles",
        "apparent_delta_cycles", "phase_error_cycles", "speed_ratio",
        "phase_confidence", "pattern_contrast", "fit_quality", "glow_energy",
        "normalized_glow_energy", "perceptual_novelty", "stall", "catchup",
    ]
    mean_energy = max(float(np.mean(result.energy)), EPSILON)
    with path.open("w", newline="", encoding="utf-8") as destination:
        writer = csv.DictWriter(destination, fieldnames=fields, delimiter="\t")
        writer.writeheader()
        for index in range(len(result.capture.rows)):
            def number(value: float) -> str:
                return "" if not math.isfinite(float(value)) else f"{float(value):.12g}"

            writer.writerow({
                "frame": index,
                "time_seconds": number(result.capture.times_seconds[index]),
                "dt_ms": number(result.dt_seconds[index] * 1000.0),
                "cadence_slots": int(result.cadence_slots[index]),
                "missed_slots": int(result.missed_slots[index]),
                "cadence_jitter_ms": number(
                    result.cadence_jitter_seconds[index] * 1000.0
                ),
                "phase_degrees": number(result.capture.phase_degrees[index]),
                "expected_delta_cycles": number(
                    result.primary.expected_delta_cycles[index]
                ),
                "rendered_delta_cycles": number(
                    result.primary.rendered_delta_cycles[index]
                ),
                "apparent_phase_cycles": number(
                    result.primary.apparent_phase_radians[index]
                    / (2.0 * math.pi)
                ),
                "apparent_delta_cycles": number(
                    result.primary.apparent_delta_cycles[index]
                ),
                "phase_error_cycles": number(
                    result.primary.phase_error_cycles[index]
                ),
                "speed_ratio": number(result.primary.speed_ratio[index]),
                "phase_confidence": number(result.primary.confidence[index]),
                "pattern_contrast": number(result.primary.contrast[index]),
                "fit_quality": number(result.primary.fit_quality[index]),
                "glow_energy": number(result.energy[index]),
                "normalized_glow_energy": number(
                    result.energy[index] / mean_energy
                ),
                "perceptual_novelty": number(result.novelty[index]),
                "stall": int(result.primary.stalls[index]),
                "catchup": int(result.primary.catchups[index]),
            })


def _svg_polyline(
    values: np.ndarray,
    x: float,
    y: float,
    width: float,
    height: float,
    color: str,
    minimum: float | None = None,
    maximum: float | None = None,
) -> tuple[str, float, float]:
    finite = np.isfinite(values)
    if not np.any(finite):
        return "", 0.0, 1.0
    low = float(np.min(values[finite])) if minimum is None else minimum
    high = float(np.max(values[finite])) if maximum is None else maximum
    if not high > low:
        padding = max(abs(low) * 0.05, 1.0e-3)
        low -= padding
        high += padding
    points: list[str] = []
    count = len(values)
    for index, value in enumerate(values):
        if not math.isfinite(float(value)):
            continue
        px = x + (0.0 if count == 1 else index / (count - 1) * width)
        py = y + (high - float(value)) / (high - low) * height
        points.append(f"{px:.2f},{py:.2f}")
    return (
        f'<polyline points="{" ".join(points)}" fill="none" '
        f'stroke="{color}" stroke-width="1.6"/>',
        low,
        high,
    )


def write_report_svg(path: Path, result: AnalysisResult) -> None:
    width, height = 1100, 1000
    left, plot_width = 82.0, 980.0
    panel_height = 145.0
    panel_tops = (145.0, 330.0, 515.0, 700.0)
    background = "#10151d"
    grid = "#293340"
    text_color = "#e8edf5"
    speed = result.primary.speed_ratio.copy()
    energy = result.energy / max(float(np.mean(result.energy)), EPSILON)
    confidence = result.primary.confidence
    contrast = result.primary.contrast
    dt_ms = result.dt_seconds * 1000.0
    panels = [
        (f"Delivery interval (ms; {result.capture.timeline_kind})",
         dt_ms, "#69b7ff", None, None),
        ("Apparent / intended speed", speed, "#ffd166", 0.0, 2.0),
        ("Phase confidence (cyan) and contrast (green)", confidence,
         "#5ce1e6", 0.0, max(1.0, float(np.nanmax(confidence)) * 1.05)),
        ("Normalized glow energy", energy, "#70e09b", None, None),
    ]
    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" '
        f'height="{height}" viewBox="0 0 {width} {height}">',
        f'<rect width="100%" height="100%" fill="{background}"/>',
        f'<text x="{left}" y="38" fill="{text_color}" font-family="sans-serif" '
        'font-size="23" font-weight="bold">Wheel perceptual-stutter report</text>',
        f'<text x="{left}" y="70" fill="{text_color}" font-family="monospace" '
        f'font-size="15">overall={result.overall_verdict}  '
        f'continuity={result.summary["renderer_continuity_verdict"]}  '
        f'cadence={result.cadence_verdict}  alias={result.alias_verdict}  '
        f'legibility={result.motion_legibility_verdict}</text>',
        f'<text x="{left}" y="96" fill="#aeb9c7" font-family="monospace" '
        f'font-size="13">frames={len(result.capture.rows)}  '
        f'capture={result.capture.fps:g} Hz  '
        f'phase-timing-lag={result.phase_timing_best_lag}  '
        f'speed beat={result.speed_spectrum.amplitude:.4f} @ '
        f'{result.speed_spectrum.frequency_hz:.2f} Hz</text>',
    ]
    for panel_index, (label, values, color, minimum, maximum) in enumerate(panels):
        top = panel_tops[panel_index]
        lines.extend((
            f'<rect x="{left}" y="{top}" width="{plot_width}" '
            f'height="{panel_height}" fill="#151d27" stroke="{grid}"/>',
            f'<text x="{left}" y="{top - 10}" fill="{text_color}" '
            f'font-family="sans-serif" font-size="14">{label}</text>',
        ))
        polyline, low, high = _svg_polyline(
            values, left, top, plot_width, panel_height, color, minimum, maximum
        )
        lines.append(polyline)
        if panel_index == 2:
            contrast_line, _, _ = _svg_polyline(
                contrast, left, top, plot_width, panel_height, "#70e09b",
                minimum, maximum
            )
            lines.append(contrast_line)
        lines.extend((
            f'<text x="{left - 8}" y="{top + 11}" text-anchor="end" '
            f'fill="#9aa7b7" font-family="monospace" font-size="11">{high:.3g}</text>',
            f'<text x="{left - 8}" y="{top + panel_height}" text-anchor="end" '
            f'fill="#9aa7b7" font-family="monospace" font-size="11">{low:.3g}</text>',
        ))
        for index in np.flatnonzero(result.primary.stalls | result.primary.catchups):
            px = left + index / max(1, len(values) - 1) * plot_width
            lines.append(
                f'<line x1="{px:.2f}" y1="{top}" x2="{px:.2f}" '
                f'y2="{top + panel_height}" stroke="#ff5d73" '
                'stroke-width="1" opacity="0.75"/>'
            )
    reasons = (
        list(result.summary["renderer_reasons"])
        + result.cadence_reasons
        + result.alias_reasons
        + result.energy_reasons
        + result.motion_legibility_reasons
    )
    reason_text = " | ".join(str(item) for item in reasons[:4]) or "No gate failures."
    reason_text = reason_text.replace("&", "&amp;").replace("<", "&lt;")
    lines.extend((
        f'<text x="{left}" y="900" fill="#c8d1dc" font-family="sans-serif" '
        f'font-size="13">{reason_text}</text>',
        f'<text x="{left}" y="965" fill="#7f8c9d" font-family="sans-serif" '
        'font-size="12">Red markers are confident apparent stalls/catch-up frames. '
        'Verdicts use source PPMs, never encoded-video playback cadence.</text>',
        '</svg>',
    ))
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def _worst_event_indices(result: AnalysisResult, limit: int = 12) -> list[int]:
    ratio = result.primary.speed_ratio
    score = np.zeros(len(ratio), dtype=np.float64)
    finite = np.isfinite(ratio)
    score[finite] = np.abs(ratio[finite] - 1.0)
    score += np.abs(result.energy / max(float(np.mean(result.energy)), EPSILON) - 1.0)
    ranked = np.argsort(score)[::-1]
    return sorted(int(index) for index in ranked[: min(limit, len(ranked))])


def write_event_contact_sheet(path: Path, result: AnalysisResult) -> None:
    indices = _worst_event_indices(result)
    columns = 4
    tile_width, image_height, label_height = 220, 165, 46
    rows = max(1, math.ceil(len(indices) / columns))
    sheet = Image.new(
        "RGB", (columns * tile_width, rows * (image_height + label_height)),
        "#10151d"
    )
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for position, index in enumerate(indices):
        with Image.open(result.capture.paths[index]) as source:
            image = source.convert("RGB")
            image.thumbnail((tile_width, image_height), Image.Resampling.LANCZOS)
        x = (position % columns) * tile_width
        y = (position // columns) * (image_height + label_height)
        sheet.paste(image, (x + (tile_width - image.width) // 2, y))
        ratio = result.primary.speed_ratio[index]
        label = (
            f"frame {index}  dt "
            f"{_format_number(result.dt_seconds[index] * 1000.0, 2)} ms\n"
            f"speed {_format_number(float(ratio), 3)}  "
            f"conf {result.primary.confidence[index]:.3f}"
        )
        draw.multiline_text(
            (x + 5, y + image_height + 4), label,
            fill="#f0f4f8", font=font, spacing=3
        )
    sheet.save(path)


def write_markdown_report(path: Path, result: AnalysisResult) -> None:
    primary = result.primary.statistics
    lines = [
        "# Wheel perceptual-stutter report",
        "",
        f"- Overall: **{result.overall_verdict}**",
        f"- Renderer continuity: **{result.summary['renderer_continuity_verdict']}**",
        f"- Delivery cadence ({result.capture.timeline_kind}): "
        f"**{result.cadence_verdict}**",
        f"- Alias safety: **{result.alias_verdict}**",
        f"- Motion legibility: **{result.motion_legibility_verdict}**",
        f"- Glow/contrast stability: **{result.energy_verdict}**",
        f"- Frames: `{len(result.capture.rows)}` at nominal `{result.capture.fps:g} Hz`",
        f"- Best rendered-phase/presentation-delta lag: `{result.phase_timing_best_lag}`",
        f"- Confident apparent stalls: `{primary['stall_frames']}`; catch-up frames: `{primary['catchup_frames']}`",
        f"- Apparent speed p01/median/p99: "
        f"`{_format_number(primary['p01_speed_ratio'])}` / "
        f"`{_format_number(primary['median_speed_ratio'])}` / "
        f"`{_format_number(primary['p99_speed_ratio'])}`",
        f"- P99 phase-step error: `{_format_number(primary['p99_absolute_phase_error_cycles'])}` groove cycles",
        f"- Dominant speed modulation: `{result.speed_spectrum.amplitude:.5f}` at "
        f"`{result.speed_spectrum.frequency_hz:.3f} Hz` "
        f"(SNR `{result.speed_spectrum.signal_to_noise:.2f}`)",
        f"- Constant-speed energy CV: `{result.summary['constant_speed_energy_cv']:.6f}`",
        f"- Constant-speed contrast CV: `{result.summary['constant_speed_contrast_cv']:.6f}`",
        "",
        "## Findings",
        "",
    ]
    if result.capture.timeline_kind in (
        "swap_return_proxy", "swap_return_proxy_replay"
    ):
        lines.extend((
            "- Timing is derived from swap-return back-pressure, not confirmed "
            "physical scanout timestamps.",
        ))
    reasons = (
        list(result.summary["renderer_reasons"])
        + result.cadence_reasons
        + result.alias_reasons
        + result.motion_legibility_reasons
        + result.energy_reasons
    )
    if reasons:
        lines.extend(f"- {reason}" for reason in reasons)
    else:
        lines.append("- No gate failures.")
    lines.extend((
        "",
        "The apparent phase is inferred from the rendered tread pixels using a "
        "low-speed, known-phase template basis from the same representation. "
        "Above half a groove cycle per "
        "presentation, repeating-groove phase is fundamentally ambiguous; the "
        "alias gate therefore expects its contrast to disappear and reports "
        "motion-cue loss separately.",
    ))
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_artifacts(output_dir: Path, result: AnalysisResult) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    write_metrics(output_dir / "metrics.tsv", result)
    (output_dir / "summary.json").write_text(
        json.dumps(result.summary, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    write_report_svg(output_dir / "report.svg", result)
    write_markdown_report(output_dir / "report.md", result)
    write_event_contact_sheet(output_dir / "worst-events.png", result)


def _parse_scales(values: Iterable[str]) -> tuple[int, ...]:
    try:
        scales = tuple(int(value) for value in values)
    except ValueError as error:
        raise argparse.ArgumentTypeError("spatial scales must be integers") from error
    if not scales or any(value < 0 for value in scales):
        raise argparse.ArgumentTypeError(
            "spatial scales must contain non-negative integers"
        )
    return scales


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sequence", type=Path, required=True)
    parser.add_argument(
        "--templates", type=Path, required=True,
        help=(
            "low-speed known-phase PPM sequence in the same mode, resolution "
            "and camera, used to learn apparent phase"
        ),
    )
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--fps", type=float,
                        help="fallback when the manifest has no fps column")
    parser.add_argument("--groove-count", type=int, default=DEFAULT_GROOVE_COUNT)
    parser.add_argument("--harmonics", type=int, default=5)
    parser.add_argument("--analysis-width", type=int, default=192)
    parser.add_argument(
        "--channel", choices=("green-opponent", "luminance"),
        default="green-opponent",
    )
    parser.add_argument(
        "--spatial-scales", nargs="+", default=["0", "1", "2"],
        metavar="RADIUS",
    )
    parser.add_argument(
        "--allow-fail", action="store_true",
        help="write the report but return success even when a QA gate fails",
    )
    args = parser.parse_args(argv)
    try:
        args.spatial_scales = _parse_scales(args.spatial_scales)
    except argparse.ArgumentTypeError as error:
        parser.error(str(error))
    if args.analysis_width < 1:
        parser.error("--analysis-width must be positive")
    if args.groove_count < 1:
        parser.error("--groove-count must be positive")
    if args.harmonics < 1:
        parser.error("--harmonics must be positive")
    return args


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        capture = load_sequence(
            args.sequence,
            analysis_width=args.analysis_width,
            channel=args.channel,
            fallback_fps=args.fps,
        )
        templates = load_sequence(
            args.templates,
            analysis_width=args.analysis_width,
            channel=args.channel,
            fallback_fps=args.fps,
        )
        result = analyze_capture(
            capture,
            templates,
            groove_count=args.groove_count,
            harmonic_count=args.harmonics,
            spatial_scales=args.spatial_scales,
        )
        write_artifacts(args.output_dir, result)
    except (OSError, ValueError) as error:
        print(f"perceptual-stutter analysis failed: {error}", file=sys.stderr)
        return 1
    print(
        f"Perceptual stutter: {result.overall_verdict}; "
        f"continuity={result.summary['renderer_continuity_verdict']}, "
        f"cadence={result.cadence_verdict}, alias={result.alias_verdict}, "
        f"legibility={result.motion_legibility_verdict}"
    )
    print(f"Report: {args.output_dir / 'report.svg'}")
    print(f"Metrics: {args.output_dir / 'metrics.tsv'}")
    if result.overall_verdict == "FAIL" and not args.allow_fail:
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
