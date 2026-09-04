#!/usr/bin/env python3
"""Fault-injection tests for the live wheel timing analyzer."""

from __future__ import annotations

import csv
import importlib.util
import math
from pathlib import Path
import tempfile
import unittest

import numpy as np


MODULE_PATH = Path(__file__).with_name("analyze_frame_timing.py")
SPEC = importlib.util.spec_from_file_location("analyze_frame_timing", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"cannot import {MODULE_PATH}")
ANALYZER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ANALYZER)


FIELDS = [
    "frame",
    "loop_start_ms",
    "loop_delta_ms",
    "render_ms",
    "setup_ms",
    "scene_ms",
    "bloom_ms",
    "screenshot_ms",
    "swap_wait_ms",
    "swap_return_ms",
    "swap_interval_ms",
    "poll_ms",
    "phase_degrees",
    "physical_pose_delta_degrees",
    "filter_delta_degrees",
    "nominal_hz",
    "spin_rps",
    "groove_cycles_per_frame",
    "alias_envelope_cycles",
    "temporal_blend",
    "groove_contrast",
    "cadence_title_update",
    "scheduled_phase_clock",
]


def synthetic_trace(frame_count: int = 720) -> tuple[list[str], np.ndarray]:
    hz = 180.0
    rps = 4.0
    interval_ms = 1000.0 / hz
    pose_step = rps * 360.0 / hz
    rows: list[list[float]] = []
    swap_return_ms = 0.0
    phase_degrees = 0.0
    for frame in range(frame_count):
        current_interval = 0.0 if frame == 0 else interval_ms
        swap_return_ms += current_interval
        phase_degrees += 0.0 if frame == 0 else pose_step
        row = {
            "frame": float(frame),
            "loop_start_ms": float(frame) * interval_ms,
            "loop_delta_ms": current_interval,
            "render_ms": 0.20,
            "setup_ms": 0.02,
            "scene_ms": 0.08,
            "bloom_ms": 0.10,
            "screenshot_ms": 0.0,
            "swap_wait_ms": max(current_interval - 0.20, 0.0),
            "swap_return_ms": swap_return_ms,
            "swap_interval_ms": current_interval,
            "poll_ms": 0.01,
            "phase_degrees": phase_degrees,
            "physical_pose_delta_degrees": 0.0 if frame == 0 else pose_step,
            "filter_delta_degrees": 0.0 if frame == 0 else pose_step,
            "nominal_hz": hz,
            "spin_rps": rps,
            "groove_cycles_per_frame": pose_step / 20.0,
            "alias_envelope_cycles": 0.4,
            "temporal_blend": 1.0,
            "groove_contrast": 0.2,
            "cadence_title_update": 0.0,
            "scheduled_phase_clock": 1.0,
        }
        rows.append([row[name] for name in FIELDS])
    return list(FIELDS), np.asarray(rows, dtype=np.float64)


def instrumented_trace(
    frame_count: int = 720,
) -> tuple[list[str], np.ndarray, dict[str, tuple[str, ...]]]:
    fields, values = synthetic_trace(frame_count)
    hz = values[0, fields.index("nominal_hz")]
    interval_ns = int(round(1.0e9 / hz))
    gpu_setup_ms = 0.04
    gpu_scene_ms = 0.10
    gpu_bloom_ms = 0.12
    gpu_frame_ms = gpu_setup_ms + gpu_scene_ms + gpu_bloom_ms
    numeric = {
        "gpu_disjoint_epoch": np.zeros(frame_count),
        "gpu_query_latency_frames": np.full(frame_count, 2.0),
        "gpu_setup_ms": np.full(frame_count, gpu_setup_ms),
        "gpu_scene_ms": np.full(frame_count, gpu_scene_ms),
        "gpu_bloom_ms": np.full(frame_count, gpu_bloom_ms),
        "gpu_frame_ms": np.full(frame_count, gpu_frame_ms),
        "scanout_valid": np.ones(frame_count),
        "scanout_counter_before": np.arange(frame_count, dtype=np.float64),
        "scanout_counter_after": np.arange(1, frame_count + 1, dtype=np.float64),
        "scanout_counter_delta": np.ones(frame_count),
        "scanout_query_before_ms": np.full(frame_count, 0.002),
        "scanout_query_after_ms": np.full(frame_count, 0.002),
        "presentation_completion_events": np.zeros(frame_count),
        "presentation_exact_mapping": np.zeros(frame_count),
    }
    for name, column in numeric.items():
        fields.append(name)
        values = np.column_stack((values, column))
    starts = [1_000_000_000_000_000 + frame * interval_ns for frame in range(frame_count)]
    duration_ns = int(round(gpu_frame_ms * 1.0e6))
    annotations = {
        "diagnostic_run_id": tuple("test-run" for _ in range(frame_count)),
        "gpu_timer_status": tuple("ok" for _ in range(frame_count)),
        "gpu_start_timestamp_ns": tuple(str(value) for value in starts),
        "gpu_end_timestamp_ns": tuple(
            str(value + duration_ns) for value in starts
        ),
        "scanout_source": tuple(
            "glx_sgi_video_sync" for _ in range(frame_count)
        ),
        "presentation_completion_source": tuple(
            "unavailable" for _ in range(frame_count)
        ),
    }
    return fields, values, annotations


class FrameTimingAnalysisTest(unittest.TestCase):
    def test_healthy_trace_passes_independent_gates(self) -> None:
        fields, values = synthetic_trace()

        summary, _ = ANALYZER.analyze(fields, values)

        self.assertEqual(
            {
                "cpu_render_budget": "PASS",
                "gpu_render_budget": "UNKNOWN",
                "physical_display_cadence": "UNKNOWN",
                "pose_step_continuity": "PASS",
                "presentation_completion_cadence": "UNKNOWN",
                "representation_stability": "PASS",
                "scanout_retrace": "UNKNOWN",
                "swap_return_cadence_proxy": "PASS",
            },
            summary["verdicts"],
        )

    def test_previous_delta_clock_fault_fails_pose_and_cadence(self) -> None:
        fields, values = synthetic_trace()
        interval_index = fields.index("swap_interval_ms")
        return_index = fields.index("swap_return_ms")
        loop_delta_index = fields.index("loop_delta_ms")
        pose_index = fields.index("physical_pose_delta_degrees")
        hz = values[1, fields.index("nominal_hz")]
        rps = values[1, fields.index("spin_rps")]
        nominal = 1000.0 / hz
        values[180, interval_index] = nominal * 8.0
        values[181, interval_index] = 0.25
        values[:, return_index] = np.cumsum(values[:, interval_index])
        values[:, loop_delta_index] = values[:, interval_index]
        # The old loop applies the preceding swap-return interval to this pose.
        values[1:, pose_index] = (
            values[:-1, interval_index] * rps * 360.0 / 1000.0
        )

        summary, _ = ANALYZER.analyze(fields, values)

        self.assertEqual("FAIL", summary["verdicts"]["swap_return_cadence_proxy"])
        self.assertEqual(
            1,
            summary["swap_return_cadence_proxy"]["severe_interval_events"],
        )
        self.assertEqual("FAIL", summary["verdicts"]["pose_step_continuity"])
        self.assertGreater(
            summary["pose_timing"]["catch_up_pose_steps_above_1_2"], 0
        )
        self.assertGreater(summary["pose_timing"]["slow_pose_steps_below_0_8"], 0)
        self.assertEqual(1, summary["pose_timing"]["best_swap_return_lag_frames"])

    def test_gradual_representation_pulse_is_not_hidden_by_smooth_pose(self) -> None:
        fields, values = synthetic_trace()
        contrast_index = fields.index("groove_contrast")
        # A slow 50-frame attack and release keeps every individual step below
        # the old 0.05 threshold, while still causing an obvious visual pulse.
        pulse = np.concatenate((
            np.linspace(0.0, 0.20, 50, endpoint=False),
            np.linspace(0.20, 0.0, 51),
        ))
        values[180:281, contrast_index] += pulse

        summary, _ = ANALYZER.analyze(fields, values)

        self.assertEqual("PASS", summary["verdicts"]["pose_step_continuity"])
        self.assertEqual("FAIL", summary["verdicts"]["representation_stability"])

    def test_render_over_budget_is_separate_from_swap_proxy(self) -> None:
        fields, values = synthetic_trace()
        values[180, fields.index("render_ms")] = 6.0

        summary, _ = ANALYZER.analyze(fields, values)

        self.assertEqual("FAIL", summary["verdicts"]["cpu_render_budget"])
        self.assertEqual("PASS", summary["verdicts"]["swap_return_cadence_proxy"])

    def test_short_trace_cannot_claim_cadence_pass(self) -> None:
        fields, values = synthetic_trace(frame_count=180)

        summary, _ = ANALYZER.analyze(fields, values)

        self.assertEqual(
            "INCONCLUSIVE", summary["verdicts"]["swap_return_cadence_proxy"]
        )

    def test_legacy_filter_surrogate_cannot_claim_pose_pass(self) -> None:
        fields, values = synthetic_trace()
        physical_index = fields.index("physical_pose_delta_degrees")
        fields[physical_index] = "planned_delta_degrees"

        summary, _ = ANALYZER.analyze(fields, values)

        self.assertEqual("UNKNOWN", summary["verdicts"]["pose_step_continuity"])

    def test_reader_rejects_inconsistent_return_intervals(self) -> None:
        fields, values = synthetic_trace(frame_count=4)
        values[2, fields.index("swap_return_ms")] += 1.0
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "inconsistent.tsv"
            with path.open("w", newline="", encoding="utf-8") as destination:
                writer = csv.writer(destination, delimiter="\t")
                writer.writerow(fields)
                writer.writerows(values)

            with self.assertRaisesRegex(ValueError, "disagrees"):
                ANALYZER.read_trace(path)

    def test_reader_rejects_non_finite_values(self) -> None:
        fields, values = synthetic_trace(frame_count=3)
        values[1, fields.index("render_ms")] = math.nan
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bad.tsv"
            with path.open("w", newline="", encoding="utf-8") as destination:
                writer = csv.writer(destination, delimiter="\t")
                writer.writerow(fields)
                writer.writerows(values)

            with self.assertRaisesRegex(ValueError, "non-finite"):
                ANALYZER.read_trace(path)

    def test_valid_gpu_and_retrace_instrumentation_pass_independent_gates(self) -> None:
        fields, values, annotations = instrumented_trace()

        summary, rows = ANALYZER.analyze(fields, values, annotations)

        self.assertEqual("PASS", summary["verdicts"]["gpu_render_budget"])
        self.assertEqual("PASS", summary["verdicts"]["scanout_retrace"])
        self.assertEqual("UNKNOWN", summary["verdicts"]["physical_display_cadence"])
        self.assertEqual(719, summary["gpu_timing"]["valid_frames"])
        self.assertAlmostEqual(
            0.26, summary["gpu_timing"]["stages_ms"]["frame"]["maximum"]
        )
        self.assertEqual("glx_sgi_video_sync", summary["scanout_retrace"]["source"])
        self.assertEqual("ok", rows[0]["gpu_timer_status"])

    def test_gpu_bloom_spike_fails_gpu_gate_while_cpu_remains_fast(self) -> None:
        fields, values, annotations = instrumented_trace()
        frame = 180
        bloom_index = fields.index("gpu_bloom_ms")
        frame_index = fields.index("gpu_frame_ms")
        values[frame, bloom_index] = 7.0
        values[frame, frame_index] = 7.14
        starts = list(annotations["gpu_start_timestamp_ns"])
        ends = list(annotations["gpu_end_timestamp_ns"])
        ends[frame] = str(int(starts[frame]) + 7_140_000)
        annotations["gpu_end_timestamp_ns"] = tuple(ends)

        summary, _ = ANALYZER.analyze(fields, values, annotations)

        self.assertEqual("PASS", summary["verdicts"]["cpu_render_budget"])
        self.assertEqual("FAIL", summary["verdicts"]["gpu_render_budget"])
        self.assertEqual("bloom", summary["gpu_timing"]["largest_maximum_stage"])
        self.assertEqual(1, summary["gpu_timing"]["over_nominal_budget"])

    def test_disjoint_results_are_not_coerced_to_fast_gpu_frames(self) -> None:
        fields, values, annotations = instrumented_trace()
        statuses = list(annotations["gpu_timer_status"])
        for index in range(1, 200):
            statuses[index] = "disjoint"
            for field in (
                "gpu_setup_ms",
                "gpu_scene_ms",
                "gpu_bloom_ms",
                "gpu_frame_ms",
            ):
                values[index, fields.index(field)] = -1.0
        annotations["gpu_timer_status"] = tuple(statuses)

        summary, _ = ANALYZER.analyze(fields, values, annotations)

        self.assertEqual("INCONCLUSIVE", summary["verdicts"]["gpu_render_budget"])
        self.assertEqual(199, summary["gpu_timing"]["status_counts"]["disjoint"])
        self.assertEqual(520, summary["gpu_timing"]["valid_frames"])
        self.assertAlmostEqual(
            0.26,
            summary["gpu_timing"]["stages_ms"]["frame"]["maximum"],
        )

    def test_gpu_stage_sum_mismatch_is_rejected(self) -> None:
        fields, values, annotations = instrumented_trace(frame_count=4)
        values[2, fields.index("gpu_bloom_ms")] += 0.1

        with self.assertRaisesRegex(ValueError, "stage sum disagrees"):
            ANALYZER.analyze(fields, values, annotations)

    def test_scanout_counter_gap_is_retrace_evidence_not_physical_present(self) -> None:
        fields, values, annotations = instrumented_trace()
        before = values[:, fields.index("scanout_counter_before")]
        after = values[:, fields.index("scanout_counter_after")]
        before[180:] += 7.0
        after[180:] += 7.0

        summary, _ = ANALYZER.analyze(fields, values, annotations)

        self.assertEqual("FAIL", summary["verdicts"]["scanout_retrace"])
        self.assertEqual(1, summary["scanout_retrace"]["long_retrace_intervals"])
        self.assertEqual("UNKNOWN", summary["verdicts"]["physical_display_cadence"])

    def test_aggregate_exact_mapping_is_supported_but_not_a_cadence_pass(self) -> None:
        fields, values, annotations = instrumented_trace()
        values[:, fields.index("presentation_completion_events")] = values.shape[0]
        values[:, fields.index("presentation_exact_mapping")] = 1.0
        annotations["presentation_completion_source"] = tuple(
            "xpresent_pixmap" for _ in range(values.shape[0])
        )

        summary, _ = ANALYZER.analyze(fields, values, annotations)

        self.assertEqual(
            "SUPPORTED_EXACT_MAPPING",
            summary["presentation_completion"]["status"],
        )
        self.assertEqual(
            "INCONCLUSIVE",
            summary["verdicts"]["presentation_completion_cadence"],
        )
        self.assertEqual("UNKNOWN", summary["verdicts"]["physical_display_cadence"])

    def test_false_exact_mapping_claim_is_rejected(self) -> None:
        fields, values, annotations = instrumented_trace(frame_count=4)
        values[:, fields.index("presentation_completion_events")] = 3.0
        values[:, fields.index("presentation_exact_mapping")] = 1.0
        annotations["presentation_completion_source"] = tuple(
            "xpresent_pixmap" for _ in range(values.shape[0])
        )

        with self.assertRaisesRegex(ValueError, "one completion event"):
            ANALYZER.analyze(fields, values, annotations)

    def test_gpu_timestamps_remain_exact_in_reader_annotations(self) -> None:
        fields, values, annotations = instrumented_trace(frame_count=4)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "instrumented.tsv"
            categorical_order = [
                "diagnostic_run_id",
                "gpu_timer_status",
                "gpu_start_timestamp_ns",
                "gpu_end_timestamp_ns",
                "scanout_source",
                "presentation_completion_source",
            ]
            with path.open("w", newline="", encoding="utf-8") as destination:
                writer = csv.writer(destination, delimiter="\t")
                writer.writerow([*fields, *categorical_order])
                for index, row in enumerate(values):
                    writer.writerow([
                        *row,
                        *(annotations[name][index] for name in categorical_order),
                    ])

            _, _, loaded = ANALYZER.read_trace_details(path)

        self.assertEqual(
            annotations["gpu_start_timestamp_ns"],
            loaded["gpu_start_timestamp_ns"],
        )

    def test_report_states_physical_presentation_limit(self) -> None:
        fields, values = synthetic_trace()
        summary, rows = ANALYZER.analyze(fields, values)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ANALYZER.write_report(root / "report.md", Path("trace.tsv"), summary)
            ANALYZER.write_svg(root / "timing.svg", rows, summary)

            report = (root / "report.md").read_text(encoding="utf-8")
            self.assertIn("not a confirmed display timestamp", report)
            self.assertTrue((root / "timing.svg").read_text().startswith("<svg"))


if __name__ == "__main__":
    unittest.main()
