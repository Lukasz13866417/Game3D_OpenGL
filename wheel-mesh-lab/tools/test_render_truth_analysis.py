#!/usr/bin/env python3
"""Seeded regression tests for machine-readable render-truth analysis."""

from __future__ import annotations

import csv
import json
import tempfile
import unittest
from pathlib import Path

import numpy as np

from analyze_render_truth import (
    SCHEMA,
    analyze_capture,
    load_capture,
    write_artifacts,
)


WIDTH = 40
HEIGHT = 24
BLOOM_WIDTH = 10
BLOOM_HEIGHT = 6
FRAME_COUNT = 48


def _emission_frame(
    frame: int,
    energy: float = 1.0,
    width_scale: int = 1,
    center_override: int | None = None,
) -> np.ndarray:
    result = np.zeros((HEIGHT, WIDTH, 4), dtype=np.float32)
    # Align the clean fixture to quarter-resolution bloom texels so its support
    # is invariant; dedicated tests below introduce support changes explicitly.
    center = (frame * 4 if center_override is None else center_override) % WIDTH
    y0, y1 = 7, 17
    for offset in range(-width_scale, width_scale + 1):
        x = (center + offset) % WIDTH
        weight = energy / float(2 * width_scale + 1)
        result[y0:y1, x, 0] = 0.16 * weight
        result[y0:y1, x, 1] = 0.94 * weight
        result[y0:y1, x, 2] = 0.62 * weight
        result[y0:y1, x, 3] = weight
    return result


def _rgba8_stages(emission: np.ndarray) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    rgb = np.clip(emission[..., :3], 0.0, 1.0)
    scene_rgb = np.clip(0.025 + rgb * 0.78, 0.0, 1.0)
    bloom_full = (
        rgb * 0.36
        + np.roll(rgb, 1, axis=1) * 0.20
        + np.roll(rgb, -1, axis=1) * 0.20
        + np.roll(rgb, 2, axis=1) * 0.12
        + np.roll(rgb, -2, axis=1) * 0.12
    )
    bloom_rgb = bloom_full.reshape(
        BLOOM_HEIGHT, HEIGHT // BLOOM_HEIGHT,
        BLOOM_WIDTH, WIDTH // BLOOM_WIDTH, 3,
    ).mean(axis=(1, 3))
    final_rgb = np.clip(scene_rgb + bloom_full * 0.55, 0.0, 1.0)

    def rgba(values: np.ndarray) -> np.ndarray:
        alpha = np.ones((*values.shape[:2], 1), dtype=np.float64)
        return np.clip(
            np.rint(np.concatenate((values, alpha), axis=2) * 255.0),
            0.0,
            255.0,
        ).astype(np.uint8)

    return rgba(scene_rgb), rgba(bloom_rgb), rgba(final_rgb)


def write_capture(
    directory: Path,
    *,
    requested_model: str = "mint",
    effective_model: str = "mint",
    requested_mode: str = "adaptive",
    effective_mode: str = "adaptive",
    duplicate_emission_frame: int | None = None,
    duplicate_final_frame: int | None = None,
    partial_slowdown_frame: int | None = None,
    pulse_energy: bool = False,
    pulse_support: bool = False,
    corrupt_frame: int | None = None,
    emission_available: bool = True,
) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    metadata = {
        "schema": SCHEMA,
        "schema_version": 1,
        "layout": "frame-directories-v1",
        "width": WIDTH,
        "height": HEIGHT,
        "frame_count": FRAME_COUNT,
        "requested_model": requested_model,
        "effective_model": effective_model,
        "requested_temporal_mode": requested_mode,
        "effective_temporal_mode": effective_mode,
        "temporal_grooves_available": effective_model in ("mint", "mint-wheel"),
        "origin": "bottom-left",
        "stages": {
            "emission": {
                "dtype": "float32", "width": WIDTH, "height": HEIGHT,
                "file_pattern": "frame-%05d/emission.rgba32f",
            },
            "scene": {
                "dtype": "uint8", "width": WIDTH, "height": HEIGHT,
                "file_pattern": "frame-%05d/scene.rgba8",
            },
            "bloom": {
                "dtype": "uint8", "width": BLOOM_WIDTH,
                "height": BLOOM_HEIGHT,
                "file_pattern": "frame-%05d/bloom.rgba8",
            },
            "final": {
                "dtype": "uint8", "width": WIDTH, "height": HEIGHT,
                "file_pattern": "frame-%05d/final.rgba8",
            },
        },
    }
    (directory / "capture.json").write_text(
        json.dumps(metadata), encoding="utf-8"
    )
    with (directory / "frames.tsv").open(
        "w", newline="", encoding="utf-8"
    ) as destination:
        writer = csv.DictWriter(
            destination,
            fieldnames=(
                "frame", "phase_degrees", "phase_dependent_expected",
                "groove_contrast", "emission_available",
            ),
            delimiter="\t",
        )
        writer.writeheader()
        for frame in range(FRAME_COUNT):
            writer.writerow({
                "frame": frame,
                "phase_degrees": frame * 5.0,
                "phase_dependent_expected": 1,
                "groove_contrast": 1.0,
                "emission_available": int(emission_available),
            })

    emissions: list[np.ndarray] = []
    scenes: list[np.ndarray] = []
    blooms: list[np.ndarray] = []
    finals: list[np.ndarray] = []
    for frame in range(FRAME_COUNT):
        energy = 1.0
        if pulse_energy and frame % 12 in (0, 1, 2):
            energy = 1.45
        width_scale = 3 if pulse_support and frame % 12 in (0, 1, 2) else 1
        emission = _emission_frame(frame, energy=energy, width_scale=width_scale)
        scene, bloom, final = _rgba8_stages(emission)
        emissions.append(emission)
        scenes.append(scene)
        blooms.append(bloom)
        finals.append(final)

    if duplicate_emission_frame is not None:
        index = duplicate_emission_frame
        emissions[index] = emissions[index - 1].copy()
        scenes[index] = scenes[index - 1].copy()
        blooms[index] = blooms[index - 1].copy()
        finals[index] = finals[index - 1].copy()
    if duplicate_final_frame is not None:
        finals[duplicate_final_frame] = finals[duplicate_final_frame - 1].copy()
    if partial_slowdown_frame is not None:
        index = partial_slowdown_frame
        # Normal motion advances four pixels. Advance only one here, then let
        # the following frame return to its commanded position. No frame is a
        # duplicate, but this is a slowdown followed by a catch-up.
        emissions[index] = _emission_frame(
            index, center_override=index * 4 - 3
        )
        scenes[index], blooms[index], finals[index] = _rgba8_stages(
            emissions[index]
        )
    if corrupt_frame is not None:
        emissions[corrupt_frame][0, 0, 0] = np.nan

    for frame in range(FRAME_COUNT):
        frame_directory = directory / f"frame-{frame:05d}"
        frame_directory.mkdir()
        (frame_directory / "frame.json").write_text(
            json.dumps({"frame": frame}), encoding="utf-8"
        )
        if emission_available:
            emissions[frame].astype("<f4").tofile(
                frame_directory / "emission.rgba32f"
            )
        scenes[frame].tofile(frame_directory / "scene.rgba8")
        blooms[frame].tofile(frame_directory / "bloom.rgba8")
        finals[frame].tofile(frame_directory / "final.rgba8")


class RenderTruthAnalysisTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="wheel-render-truth-")
        self.root = Path(self.temporary.name)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def analyze(self, name: str, **kwargs: object):
        directory = self.root / name
        write_capture(directory, **kwargs)
        return analyze_capture(load_capture(directory))

    def test_clean_mint_capture_passes(self) -> None:
        result = self.analyze("clean")
        self.assertEqual("PASS", result.summary["overall_verdict"])
        self.assertEqual("PASS", result.summary["continuity_verdict"])
        self.assertEqual("PASS", result.summary["stability_verdict"])
        self.assertEqual(
            0,
            result.summary["exact_duplicate_counts_during_expected_motion"][
                "emission"
            ],
        )
        self.assertEqual(BLOOM_WIDTH, result.capture.stage_specs["bloom"].width)
        self.assertEqual("uint8", result.capture.stage_specs["bloom"].dtype)

    def test_sharp_capture_without_emission_is_explicitly_not_evaluated(self) -> None:
        result = self.analyze(
            "sharp-no-emission",
            requested_mode="sharp",
            effective_mode="sharp",
            emission_available=False,
        )
        self.assertEqual("PASS", result.summary["overall_verdict"])
        self.assertEqual(
            "NOT_EVALUATED", result.summary["emission_stability_verdict"]
        )
        self.assertEqual(0, result.summary["emission_available_frames"])
        output = self.root / "sharp-analysis"
        write_artifacts(output, result)
        self.assertTrue((output / "overview.png").is_file())

    def test_violet_run_cannot_masquerade_as_requested_mint(self) -> None:
        directory = self.root / "wrong-model"
        write_capture(directory, requested_model="mint", effective_model="violet")
        with self.assertRaisesRegex(ValueError, "model mismatch"):
            load_capture(directory)

    def test_temporal_mode_fallback_is_rejected_before_pixel_analysis(self) -> None:
        directory = self.root / "mode-fallback"
        write_capture(directory, requested_mode="adaptive", effective_mode="sharp")
        with self.assertRaisesRegex(ValueError, "temporal mode mismatch"):
            load_capture(directory)

    def test_temporal_mode_on_violet_is_rejected(self) -> None:
        directory = self.root / "violet-temporal"
        write_capture(
            directory,
            requested_model="violet",
            effective_model="violet",
        )
        with self.assertRaisesRegex(ValueError, "requires the mint wheel"):
            load_capture(directory)

    def test_raw_emission_hold_is_attributed_to_first_stage(self) -> None:
        result = self.analyze("emission-hold", duplicate_emission_frame=17)
        self.assertEqual("FAIL", result.summary["overall_verdict"])
        self.assertEqual("FAIL", result.summary["continuity_verdict"])
        self.assertEqual("emission", result.summary["first_failure_stage"])
        self.assertEqual(
            1,
            result.summary["exact_duplicate_counts_during_expected_motion"][
                "emission"
            ],
        )

    def test_final_only_hold_is_attributed_downstream(self) -> None:
        result = self.analyze("final-hold", duplicate_final_frame=19)
        self.assertEqual("FAIL", result.summary["continuity_verdict"])
        self.assertEqual("final", result.summary["first_failure_stage"])
        self.assertEqual(1, result.summary["downstream_hold_counts"]["final"])

    def test_partial_slowdown_and_catchup_fail_pace_without_duplicate(self) -> None:
        result = self.analyze("partial-slowdown", partial_slowdown_frame=17)
        self.assertEqual("FAIL", result.summary["overall_verdict"])
        self.assertEqual("FAIL", result.summary["motion_pace_verdict"])
        self.assertEqual("emission", result.summary["first_failure_stage"])
        self.assertEqual(
            0,
            result.summary["exact_duplicate_counts_during_expected_motion"][
                "emission"
            ],
        )

    def test_periodic_emission_energy_pulse_fails_stability(self) -> None:
        result = self.analyze("energy-pulse", pulse_energy=True)
        self.assertEqual("FAIL", result.summary["stability_verdict"])
        self.assertGreater(
            result.summary["statistics"]["emission_alpha_energy"]["cv"],
            0.02,
        )

    def test_periodic_support_growth_fails_stability(self) -> None:
        result = self.analyze("support-pulse", pulse_support=True)
        self.assertEqual("FAIL", result.summary["stability_verdict"])
        self.assertGreater(
            result.summary["statistics"]["emission_support_pixels"]["cv"],
            0.05,
        )

    def test_nonfinite_emission_is_rejected(self) -> None:
        directory = self.root / "nan"
        write_capture(directory, corrupt_frame=8)
        capture = load_capture(directory)
        with self.assertRaisesRegex(ValueError, "non-finite"):
            analyze_capture(capture)

    def test_truncated_raw_buffer_is_rejected(self) -> None:
        directory = self.root / "truncated"
        write_capture(directory)
        path = directory / "frame-00007" / "bloom.rgba8"
        path.write_bytes(path.read_bytes()[:-1])
        with self.assertRaisesRegex(ValueError, "wrong size"):
            load_capture(directory)

    def test_human_and_machine_artifacts_are_written(self) -> None:
        result = self.analyze("artifacts")
        output = self.root / "analysis"
        write_artifacts(output, result)
        for filename in ("summary.json", "metrics.tsv", "report.md", "overview.png"):
            path = output / filename
            self.assertTrue(path.is_file(), filename)
            self.assertGreater(path.stat().st_size, 32, filename)
        self.assertIn(
            "NOT_MEASURED",
            (output / "summary.json").read_text(encoding="utf-8"),
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
