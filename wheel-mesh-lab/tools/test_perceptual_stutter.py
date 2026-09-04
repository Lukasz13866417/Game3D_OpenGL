#!/usr/bin/env python3
"""Seeded-fault regression tests for analyze_perceptual_stutter.py.

These tests intentionally do not merely check that the analyzer runs.  Each
fault changes a different perceptual failure channel and must be rejected:

* a duplicated visible pose (stall followed by catch-up),
* phase integrated with the previous presentation interval,
* a periodic glow-energy pulse,
* a still-visible repeated pattern above temporal Nyquist.

Clean constant-cadence and irregular-cadence controls must pass.  A protected
high-speed continuous band must be classified as alias-safe while explicitly
reporting that the repeating tread no longer communicates motion.
"""

from __future__ import annotations

import csv
import math
import tempfile
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

from analyze_perceptual_stutter import (
    AnalysisResult,
    Thresholds,
    analyze_capture,
    load_sequence,
    write_artifacts,
)


FPS = 120.0
GROOVE_COUNT = 18
WIDTH = 96
HEIGHT = 72
FRAME_COUNT = 360
TEMPLATE_COUNT = 90


def linear_to_srgb(values: np.ndarray) -> np.ndarray:
    encoded = np.where(
        values <= 0.0031308,
        values * 12.92,
        1.055 * np.power(np.maximum(values, 0.0), 1.0 / 2.4) - 0.055,
    )
    return np.clip(np.rint(encoded * 255.0), 0.0, 255.0).astype(np.uint8)


def synthetic_wheel(
    physical_phase_degrees: float,
    *,
    pattern_contrast: float = 1.0,
    energy_scale: float = 1.0,
) -> np.ndarray:
    x = np.linspace(-1.0, 1.0, WIDTH, dtype=np.float64)[None, :]
    y = np.linspace(-1.0, 1.0, HEIGHT, dtype=np.float64)[:, None]
    tread = (x / 0.62) ** 2 + (y / 0.92) ** 2 <= 1.0
    edge = np.clip(
        1.0 - ((x / 0.68) ** 2 + (y / 0.98) ** 2), 0.0, 1.0
    )
    groove_phase = math.radians(physical_phase_degrees * GROOVE_COUNT)
    # A chevron-like projected coordinate.  The raised-cosine fifth power has
    # exactly the first five harmonics expected by the learned phase model.
    projected_coordinate = 2.0 * math.pi * (2.7 * y + 0.72 * np.abs(x))
    raised = (0.5 + 0.5 * np.cos(projected_coordinate - groove_phase)) ** 5
    # Keep authored emission energy independent of phase.  This makes the
    # clean control exercise motion estimation rather than accidentally
    # seeding an intensity fault through finite elliptical coverage.
    raised *= 0.24609375 / max(float(np.mean(raised[tread])), 1.0e-12)
    groove_profile = 0.24609375 + pattern_contrast * (
        raised - 0.24609375
    )
    groove = tread * groove_profile
    static_side = (
        (np.abs(x) > 0.64)
        & (np.abs(x) < 0.72)
        & (np.abs(y) < 0.68)
    )
    body = tread * (0.035 + 0.055 * edge)
    emission = energy_scale * (0.72 * groove + 0.24 * static_side)
    green = np.clip(body + emission, 0.0, 1.0)
    red = np.clip(body * 0.20 + emission * 0.025, 0.0, 1.0)
    blue = np.clip(body * 0.28 + emission * 0.10, 0.0, 1.0)
    rgb = np.stack(np.broadcast_arrays(red, green, blue), axis=2)
    return linear_to_srgb(rgb)


def write_sequence(
    directory: Path,
    phases_degrees: np.ndarray,
    times_seconds: np.ndarray,
    rps: np.ndarray,
    *,
    image_phases_degrees: np.ndarray | None = None,
    contrasts: np.ndarray | None = None,
    energy_scales: np.ndarray | None = None,
) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    count = len(phases_degrees)
    if image_phases_degrees is None:
        image_phases_degrees = phases_degrees
    if contrasts is None:
        contrasts = np.ones(count, dtype=np.float64)
    if energy_scales is None:
        energy_scales = np.ones(count, dtype=np.float64)
    with (directory / "manifest.tsv").open(
        "w", newline="", encoding="utf-8"
    ) as destination:
        writer = csv.DictWriter(
            destination,
            fieldnames=(
                "frame", "phase_degrees", "presentation_time_ns", "fps", "rps"
            ),
            delimiter="\t",
        )
        writer.writeheader()
        for index in range(count):
            Image.fromarray(synthetic_wheel(
                float(image_phases_degrees[index]),
                pattern_contrast=float(contrasts[index]),
                energy_scale=float(energy_scales[index]),
            )).save(directory / f"frame-{index:05d}.ppm")
            writer.writerow({
                "frame": index,
                "phase_degrees": f"{float(phases_degrees[index]):.12g}",
                "presentation_time_ns": int(round(times_seconds[index] * 1.0e9)),
                "fps": f"{FPS:.12g}",
                "rps": f"{float(rps[index]):.12g}",
            })


def constant_timeline(count: int) -> np.ndarray:
    # Rounding absolute timestamps mirrors a nanosecond presentation clock
    # without accumulating a fractional-nanosecond drift.
    return np.rint(np.arange(count) * (1.0e9 / FPS)).astype(np.int64) * 1.0e-9


def phases_from_cycles(cycles: np.ndarray) -> np.ndarray:
    return cycles * 360.0 / GROOVE_COUNT


class PerceptualStutterTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.temporary = tempfile.TemporaryDirectory(prefix="wheel-perceptual-test-")
        cls.root = Path(cls.temporary.name)
        cls.templates_dir = cls.root / "templates"
        template_cycles = np.arange(TEMPLATE_COUNT, dtype=np.float64) / TEMPLATE_COUNT
        template_times = constant_timeline(TEMPLATE_COUNT)
        template_rps = np.full(
            TEMPLATE_COUNT,
            (1.0 / TEMPLATE_COUNT) * FPS / GROOVE_COUNT,
            dtype=np.float64,
        )
        write_sequence(
            cls.templates_dir,
            phases_from_cycles(template_cycles),
            template_times,
            template_rps,
        )
        cls.templates = load_sequence(
            cls.templates_dir, analysis_width=WIDTH
        )

    @classmethod
    def tearDownClass(cls) -> None:
        cls.temporary.cleanup()

    def analyze(
        self,
        name: str,
        phases_cycles: np.ndarray,
        times: np.ndarray,
        rps: np.ndarray,
        *,
        image_cycles: np.ndarray | None = None,
        contrasts: np.ndarray | None = None,
        energy_scales: np.ndarray | None = None,
    ) -> AnalysisResult:
        directory = self.root / name
        write_sequence(
            directory,
            phases_from_cycles(phases_cycles),
            times,
            rps,
            image_phases_degrees=(
                None if image_cycles is None else phases_from_cycles(image_cycles)
            ),
            contrasts=contrasts,
            energy_scales=energy_scales,
        )
        capture = load_sequence(directory, analysis_width=WIDTH)
        return analyze_capture(
            capture,
            self.templates,
            groove_count=GROOVE_COUNT,
            harmonic_count=5,
            spatial_scales=(0, 1, 2),
            thresholds=Thresholds(),
        )

    def test_clean_constant_motion_passes_in_image_space(self) -> None:
        times = constant_timeline(FRAME_COUNT)
        step = 0.20
        cycles = np.arange(FRAME_COUNT, dtype=np.float64) * step
        rps = np.full(FRAME_COUNT, step * FPS / GROOVE_COUNT)
        result = self.analyze("clean", cycles, times, rps)

        self.assertEqual("PASS", result.overall_verdict)
        self.assertEqual("PASS", result.summary["renderer_continuity_verdict"])
        self.assertEqual(0, int(np.count_nonzero(result.primary.stalls)))
        self.assertAlmostEqual(
            1.0,
            float(np.nanmedian(result.primary.speed_ratio)),
            delta=0.01,
        )

    def test_clean_irregular_timeline_uses_current_interval(self) -> None:
        nominal = 1.0 / FPS
        factors = np.resize(np.array((0.88, 1.12, 0.94, 1.06)), FRAME_COUNT - 1)
        intervals = nominal * factors
        times = np.concatenate(([0.0], np.cumsum(intervals)))
        rps_value = 0.18 * FPS / GROOVE_COUNT
        rps = np.full(FRAME_COUNT, rps_value)
        cycles = np.zeros(FRAME_COUNT)
        cycles[1:] = np.cumsum(rps_value * GROOVE_COUNT * intervals)
        result = self.analyze("clean-jitter", cycles, times, rps)

        self.assertEqual("PASS", result.summary["renderer_continuity_verdict"])
        self.assertEqual("PASS", result.cadence_verdict)
        self.assertEqual(0, result.phase_timing_best_lag)

    def test_duplicate_pose_is_detected_as_stall_and_catchup(self) -> None:
        times = constant_timeline(FRAME_COUNT)
        step = 0.20
        cycles = np.arange(FRAME_COUNT, dtype=np.float64) * step
        image_cycles = cycles.copy()
        for index in range(30, FRAME_COUNT, 30):
            image_cycles[index] = image_cycles[index - 1]
        rps = np.full(FRAME_COUNT, step * FPS / GROOVE_COUNT)
        result = self.analyze(
            "duplicate-pose", cycles, times, rps, image_cycles=image_cycles
        )

        self.assertEqual("FAIL", result.overall_verdict)
        self.assertGreater(int(np.count_nonzero(result.primary.stalls)), 0)
        self.assertGreater(int(np.count_nonzero(result.primary.catchups)), 0)

    def test_previous_interval_phase_bug_is_detected_at_lag_one(self) -> None:
        nominal = 1.0 / FPS
        factors = np.resize(np.array((0.86, 1.14, 0.92, 1.08)), FRAME_COUNT - 1)
        intervals = nominal * factors
        times = np.concatenate(([0.0], np.cumsum(intervals)))
        rps_value = 0.18 * FPS / GROOVE_COUNT
        rps = np.full(FRAME_COUNT, rps_value)
        cycles = np.zeros(FRAME_COUNT)
        cycles[1] = rps_value * GROOVE_COUNT * intervals[0]
        for index in range(2, FRAME_COUNT):
            cycles[index] = (
                cycles[index - 1]
                + rps_value * GROOVE_COUNT * intervals[index - 2]
            )
        result = self.analyze("previous-dt", cycles, times, rps)

        self.assertEqual("FAIL", result.summary["renderer_continuity_verdict"])
        self.assertEqual(1, result.phase_timing_best_lag)
        self.assertEqual("PASS", result.cadence_verdict)

    def test_missed_delivery_slot_is_attributed_to_cadence_not_phase(self) -> None:
        nominal = 1.0 / FPS
        intervals = np.full(FRAME_COUNT - 1, nominal)
        intervals[149] = 2.0 * nominal
        times = np.concatenate(([0.0], np.cumsum(intervals)))
        rps_value = 0.18 * FPS / GROOVE_COUNT
        rps = np.full(FRAME_COUNT, rps_value)
        cycles = np.zeros(FRAME_COUNT)
        cycles[1:] = np.cumsum(rps_value * GROOVE_COUNT * intervals)
        result = self.analyze("missed-delivery", cycles, times, rps)

        self.assertEqual("PASS", result.summary["renderer_continuity_verdict"])
        self.assertEqual("FAIL", result.cadence_verdict)
        self.assertEqual(1, int(np.sum(result.missed_slots)))
        self.assertEqual("FAIL", result.overall_verdict)

    def test_periodic_glow_pulse_fails_energy_stability(self) -> None:
        times = constant_timeline(FRAME_COUNT)
        step = 0.20
        cycles = np.arange(FRAME_COUNT, dtype=np.float64) * step
        rps = np.full(FRAME_COUNT, step * FPS / GROOVE_COUNT)
        energy = 1.0 + 0.07 * np.sin(2.0 * math.pi * 3.0 * times)
        result = self.analyze(
            "energy-pulse", cycles, times, rps, energy_scales=energy
        )

        self.assertEqual("FAIL", result.energy_verdict)
        self.assertEqual("FAIL", result.overall_verdict)
        self.assertAlmostEqual(
            3.0, result.energy_spectrum.frequency_hz, delta=0.6
        )

    def test_visible_above_nyquist_pattern_fails_alias_safety(self) -> None:
        times = constant_timeline(FRAME_COUNT)
        step = 0.60
        cycles = np.arange(FRAME_COUNT, dtype=np.float64) * step
        rps = np.full(FRAME_COUNT, step * FPS / GROOVE_COUNT)
        result = self.analyze("unsafe-alias", cycles, times, rps)

        self.assertEqual("FAIL", result.alias_verdict)
        self.assertEqual("ALIASED_CUE", result.motion_legibility_verdict)
        self.assertEqual("FAIL", result.overall_verdict)

    def test_phase_invariant_high_speed_is_safe_but_has_no_motion_cue(self) -> None:
        times = constant_timeline(FRAME_COUNT)
        step = 0.60
        cycles = np.arange(FRAME_COUNT, dtype=np.float64) * step
        rps = np.full(FRAME_COUNT, step * FPS / GROOVE_COUNT)
        contrasts = np.zeros(FRAME_COUNT)
        result = self.analyze(
            "safe-band", cycles, times, rps, contrasts=contrasts
        )

        self.assertEqual("PASS", result.alias_verdict)
        self.assertEqual("CUE_ABSENT", result.motion_legibility_verdict)
        self.assertNotEqual("PASS", result.summary["renderer_continuity_verdict"])

    def test_reports_are_materialized_for_human_and_machine_review(self) -> None:
        times = constant_timeline(FRAME_COUNT)
        step = 0.20
        cycles = np.arange(FRAME_COUNT, dtype=np.float64) * step
        rps = np.full(FRAME_COUNT, step * FPS / GROOVE_COUNT)
        result = self.analyze("reports", cycles, times, rps)
        output = self.root / "report-output"
        write_artifacts(output, result)

        for filename in (
            "metrics.tsv", "summary.json", "report.svg", "report.md",
            "worst-events.png",
        ):
            artifact = output / filename
            self.assertTrue(artifact.is_file(), filename)
            self.assertGreater(artifact.stat().st_size, 32, filename)
        self.assertIn(
            "wheel-perceptual-stutter-v1",
            (output / "summary.json").read_text(encoding="utf-8"),
        )

    def test_short_capture_cannot_claim_periodic_smoothness(self) -> None:
        count = 90
        times = constant_timeline(count)
        step = 0.20
        cycles = np.arange(count, dtype=np.float64) * step
        rps = np.full(count, step * FPS / GROOVE_COUNT)
        result = self.analyze("too-short", cycles, times, rps)

        self.assertEqual(
            "INDETERMINATE", result.summary["renderer_continuity_verdict"]
        )
        self.assertEqual("INDETERMINATE", result.overall_verdict)
        self.assertTrue(any(
            "capture duration" in reason
            for reason in result.summary["renderer_reasons"]
        ))


if __name__ == "__main__":
    unittest.main(verbosity=2)
