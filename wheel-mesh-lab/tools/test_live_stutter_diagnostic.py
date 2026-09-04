#!/usr/bin/env python3
"""Unit tests for live stutter diagnostic model/effective-mode validation."""

from __future__ import annotations

import csv
import tempfile
import unittest
from pathlib import Path

import run_live_stutter_diagnostic as diagnostic


TRACE_COLUMNS = (
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
)
INSTRUMENTATION_COLUMNS = (
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
)


def write_trace(path: Path, rows: list[tuple[object, ...]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as destination:
        writer = csv.writer(destination, delimiter="\t", lineterminator="\n")
        writer.writerow(TRACE_COLUMNS)
        writer.writerows(rows)


def mint_row(
    frame: int,
    requested_mode: str,
    effective_mode: str,
    temporal_source: str,
    temporal_blend: float,
) -> tuple[object, ...]:
    planning = 0.0 if effective_mode == "sharp" else 0.35
    return (
        frame,
        planning,
        planning,
        temporal_blend,
        "mint-wheel",
        requested_mode,
        effective_mode,
        temporal_source,
        1,
        18,
    )


def write_instrumented_trace(
    path: Path,
    *,
    gpu_status: str = "ok",
    scanout_delta: int = 1,
) -> None:
    with path.open("w", encoding="utf-8", newline="") as destination:
        writer = csv.writer(destination, delimiter="\t", lineterminator="\n")
        writer.writerow((*TRACE_COLUMNS, *INSTRUMENTATION_COLUMNS))
        for frame in range(2):
            writer.writerow(
                (
                    *mint_row(
                        frame,
                        "adaptive",
                        "adaptive",
                        "harmonic_shell",
                        1,
                    ),
                    "test-run",
                    gpu_status,
                    0,
                    2,
                    1_000_000 + frame * 100_000,
                    1_060_000 + frame * 100_000,
                    0.01,
                    0.02,
                    0.03,
                    0.06,
                    "glx_sgi_video_sync",
                    1,
                    100 + frame,
                    101 + frame,
                    scanout_delta,
                    0.001,
                    0.001,
                    "xpresent_no_pixmap_events",
                    0,
                    0,
                )
            )


def write_control_trace(path: Path, *, appended: bool, bad_delta: bool = False) -> None:
    columns = list(diagnostic.CONTROL_COLUMNS)
    if appended:
        columns.extend(diagnostic.CONTROL_SCANOUT_COLUMNS)
    with path.open("w", encoding="utf-8", newline="") as destination:
        writer = csv.DictWriter(
            destination, fieldnames=columns, delimiter="\t", lineterminator="\n"
        )
        writer.writeheader()
        for frame, interval, before, after in (
            (0, 0.0, 10, 11),
            (1, 20.0, 11, 13),
        ):
            row: dict[str, object] = {
                "frame": frame,
                "loop_start_ms": frame * 20.0,
                "loop_delta_ms": interval,
                "clear_ms": 0.01,
                "swap_wait_ms": interval,
                "swap_return_ms": frame * 20.0,
                "swap_return_interval_ms": interval,
                "poll_ms": 0.01,
                "nominal_hz": 100.0,
            }
            if appended:
                row.update(
                    {
                        "scanout_source": "glx_sgi_video_sync",
                        "scanout_valid": 1,
                        "scanout_counter_before": before,
                        "scanout_counter_after": after,
                        "scanout_counter_delta": (
                            99 if bad_delta and frame == 1 else after - before
                        ),
                        "scanout_query_before_ms": 0.001,
                        "scanout_query_after_ms": 0.002,
                    }
                )
            writer.writerow(row)


def write_presentation_events(
    path: Path,
    *,
    count: int,
    mapped: bool = True,
    ust_units: str = "microseconds",
) -> None:
    with path.open("w", encoding="utf-8", newline="") as destination:
        writer = csv.DictWriter(
            destination,
            fieldnames=diagnostic.PRESENTATION_EVENT_COLUMNS,
            delimiter="\t",
            lineterminator="\n",
        )
        writer.writeheader()
        for index in range(count):
            writer.writerow(
                {
                    "diagnostic_run_id": "test-run",
                    "event_index": index,
                    "source": "xpresent_pixmap",
                    "validity": "mapped" if mapped else "unmatched",
                    "mapped_submission_frame": index if mapped else "",
                    "local_submission_ms": index * 5.0,
                    "local_arrival_ms": index * 5.0 + 1.0,
                    "ust_raw": 1_000_000 + index * 5_000,
                    "ust_units": ust_units,
                    "msc": 10_000 + index,
                    "serial": index,
                    "mode": "flip",
                    "ust_delta_ms": "" if index == 0 else 5.0,
                    "msc_delta": "" if index == 0 else 1,
                    "window": 123,
                }
            )


class LiveStutterDiagnosticTests(unittest.TestCase):
    def test_cli_defaults_to_mint(self) -> None:
        args = diagnostic.parse_args([])
        self.assertEqual(args.model, "mint")
        self.assertIsNone(args.nominal_hz)

    def test_nominal_hz_override_is_parsed(self) -> None:
        args = diagnostic.parse_args(["--nominal-hz", "180"])
        self.assertEqual(args.nominal_hz, 180.0)

    def test_explicit_violet_is_parsed_for_clear_failure_reporting(self) -> None:
        args = diagnostic.parse_args(["--model", "violet"])
        self.assertEqual(args.model, "violet")

    def test_active_non_sharp_trace_confirms_mint_temporal_path(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            trace = Path(directory) / "active.tsv"
            write_trace(
                trace,
                [
                    mint_row(0, "adaptive", "adaptive", "harmonic_shell", 1),
                    mint_row(1, "adaptive", "adaptive", "harmonic_shell", 1),
                ],
            )
            identity = diagnostic.inspect_wheel_trace_identity(
                trace, "mint", "adaptive", 3.5
            )
        self.assertEqual(identity["resolved_model"], "mint")
        self.assertEqual(
            identity["effective_temporal_path"], "harmonic_shell"
        )
        self.assertEqual(identity["effective_temporal_mode"], "adaptive")
        self.assertTrue(identity["exact_effective_mode_encoded_by_trace"])
        self.assertTrue(identity["temporal_path_active"])

    def test_non_sharp_exact_sharp_fallback_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            trace = Path(directory) / "fallback.tsv"
            write_trace(
                trace,
                [
                    (
                        0,
                        0,
                        0,
                        0,
                        "violet-wheel",
                        "split",
                        "sharp",
                        "sharp_mesh",
                        0,
                        18,
                    )
                ],
            )
            with self.assertRaisesRegex(
                RuntimeError, "resolved effective_temporal_mode='sharp'"
            ):
                diagnostic.inspect_wheel_trace_identity(
                    trace, "violet", "split", 3.5
                )

    def test_sharp_records_exact_identity(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            trace = Path(directory) / "sharp.tsv"
            write_trace(
                trace,
                [mint_row(0, "sharp", "sharp", "sharp_mesh", 0)],
            )
            identity = diagnostic.inspect_wheel_trace_identity(
                trace, "mint", "sharp", 3.5
            )
        self.assertEqual(identity["resolved_model"], "mint")
        self.assertEqual(identity["model_slug"], "mint-wheel")
        self.assertEqual(identity["effective_temporal_path"], "sharp_mesh")
        self.assertEqual(identity["mint_glow_count"], 18)
        self.assertTrue(identity["exact_effective_mode_encoded_by_trace"])

    def test_model_slug_mismatch_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            trace = Path(directory) / "model-mismatch.tsv"
            row = list(mint_row(0, "sharp", "sharp", "sharp_mesh", 0))
            row[4] = "violet-wheel"
            row[8] = 0
            write_trace(trace, [tuple(row)])
            with self.assertRaisesRegex(RuntimeError, "trace reports model_slug"):
                diagnostic.inspect_wheel_trace_identity(
                    trace, "mint", "sharp", 3.5
                )

    def test_requested_mode_mismatch_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            trace = Path(directory) / "requested-mode-mismatch.tsv"
            write_trace(
                trace,
                [mint_row(0, "split", "adaptive", "harmonic_shell", 1)],
            )
            with self.assertRaisesRegex(RuntimeError, "trace records requested"):
                diagnostic.inspect_wheel_trace_identity(
                    trace, "mint", "adaptive", 3.5
                )

    def test_temporal_source_mismatch_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            trace = Path(directory) / "source-mismatch.tsv"
            write_trace(
                trace,
                [mint_row(0, "adaptive", "adaptive", "sharp_mesh", 1)],
            )
            with self.assertRaisesRegex(RuntimeError, "must use temporal_source"):
                diagnostic.inspect_wheel_trace_identity(
                    trace, "mint", "adaptive", 3.5
                )

    def test_identity_change_between_rows_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            trace = Path(directory) / "changing.tsv"
            write_trace(
                trace,
                [
                    mint_row(0, "adaptive", "adaptive", "harmonic_shell", 1),
                    mint_row(1, "adaptive", "sharp", "sharp_mesh", 0),
                ],
            )
            with self.assertRaisesRegex(
                RuntimeError, "changes effective_temporal_mode"
            ):
                diagnostic.inspect_wheel_trace_identity(
                    trace, "mint", "adaptive", 3.5
                )

    def test_legacy_control_trace_remains_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            trace = Path(directory) / "control.tsv"
            write_control_trace(trace, appended=False)
            summary = diagnostic.analyze_control_trace(trace)
        self.assertEqual(summary["scanout_retrace"]["status"], "NOT_COLLECTED")
        self.assertEqual(summary["physical_display_cadence"], "UNKNOWN")

    def test_appended_control_retrace_columns_are_validated_and_reported(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            trace = Path(directory) / "control.tsv"
            write_control_trace(trace, appended=True)
            summary = diagnostic.analyze_control_trace(trace)
        retrace = summary["scanout_retrace"]
        self.assertEqual(retrace["status"], "AVAILABLE")
        self.assertEqual(retrace["source"], "glx_sgi_video_sync")
        self.assertEqual(retrace["valid_fraction"], 1.0)
        self.assertEqual(retrace["long_swap_retrace_delta"]["maximum"], 2.0)
        self.assertFalse(retrace["proves_per_frame_presentation"])
        self.assertEqual(summary["physical_display_cadence"], "UNKNOWN")

    def test_bad_control_retrace_delta_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            trace = Path(directory) / "control.tsv"
            write_control_trace(trace, appended=True, bad_delta=True)
            with self.assertRaisesRegex(RuntimeError, "delta mismatch"):
                diagnostic.analyze_control_trace(trace)

    def test_strict_instrumented_wheel_trace_is_attested(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            trace = Path(directory) / "wheel.tsv"
            write_instrumented_trace(trace)
            identity = diagnostic.inspect_wheel_trace_identity(
                trace,
                "mint",
                "adaptive",
                3.5,
                require_instrumentation=True,
                require_native_scanout=True,
            )
        instrumentation = identity["instrumentation"]
        self.assertEqual(instrumentation["diagnostic_run_id"], "test-run")
        self.assertEqual(instrumentation["gpu_timing"]["status"], "AVAILABLE")
        self.assertEqual(
            instrumentation["scanout_retrace"]["source"],
            "glx_sgi_video_sync",
        )
        self.assertFalse(
            instrumentation["presentation_timing"]["exact_frame_mapping"]
        )

    def test_strict_wheel_trace_rejects_unresolved_gpu_timer(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            trace = Path(directory) / "wheel.tsv"
            write_instrumented_trace(trace, gpu_status="pending_at_shutdown")
            with self.assertRaisesRegex(RuntimeError, "resolved GPU timer"):
                diagnostic.inspect_wheel_trace_identity(
                    trace,
                    "mint",
                    "adaptive",
                    3.5,
                    require_instrumentation=True,
                    require_native_scanout=True,
                )

    def test_empty_presentation_probe_is_explicitly_unknown(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            trace = Path(directory) / "presentation-events.tsv"
            with trace.open("w", encoding="utf-8", newline="") as destination:
                writer = csv.writer(
                    destination, delimiter="\t", lineterminator="\n"
                )
                writer.writerow(diagnostic.PRESENTATION_EVENT_COLUMNS)
            summary = diagnostic.analyze_presentation_event_trace(
                trace,
                expected_event_count=0,
                expected_exact_mapping=False,
            )
        self.assertEqual(summary["completion_events"], 0)
        self.assertFalse(summary["exact_frame_mapping"])
        self.assertEqual(summary["physical_display_cadence"], "UNKNOWN")

    def test_presentation_mapping_requires_every_submission_and_real_ust(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            trace = Path(directory) / "presentation-events.tsv"
            write_presentation_events(trace, count=2)
            exact = diagnostic.analyze_presentation_event_trace(
                trace,
                expected_run_id="test-run",
                expected_event_count=2,
                expected_exact_mapping=True,
                expected_submission_count=2,
            )
            partial = diagnostic.analyze_presentation_event_trace(
                trace,
                expected_submission_count=3,
            )
        self.assertTrue(exact["exact_frame_mapping"])
        self.assertFalse(partial["exact_frame_mapping"])
        self.assertEqual(partial["physical_display_cadence"], "UNKNOWN")

    def test_unsupported_presentation_ust_is_not_exact(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            trace = Path(directory) / "presentation-events.tsv"
            write_presentation_events(trace, count=2, ust_units="unsupported")
            summary = diagnostic.analyze_presentation_event_trace(
                trace,
                expected_submission_count=2,
                expected_exact_mapping=False,
            )
        self.assertFalse(summary["exact_frame_mapping"])

    def test_wheel_comparison_copies_new_analyzer_evidence(self) -> None:
        summary = {
            "swap_interval_ms": {"median": 5.5, "p99": 6.0, "maximum": 7.0},
            "swap_return_cadence_proxy": {
                "long_interval_events": 0,
                "event_spacing_median_seconds": None,
            },
            "render_ms": {"median": 0.1, "p99": 0.2, "maximum": 0.3},
            "verdicts": {
                "swap_return_cadence_proxy": "PASS",
                "cpu_render_budget": "PASS",
                "pose_step_continuity": "PASS",
                "representation_stability": "PASS",
            },
            "frames_analyzed": 10,
            "duration_seconds": 3.0,
            "nominal_hz": 180.0,
            "gpu_timing": {"status": "AVAILABLE", "marker": "gpu"},
            "scanout_retrace": {"status": "AVAILABLE", "marker": "scanout"},
            "presentation_completion": {
                "exact_mapping": False,
                "marker": "presentation",
            },
        }
        view = diagnostic.wheel_comparison_view("adaptive", summary, {})
        self.assertEqual(view["gpu_timing"]["marker"], "gpu")
        self.assertEqual(view["scanout_retrace"]["marker"], "scanout")
        self.assertEqual(
            view["presentation_timing"]["marker"], "presentation"
        )
        self.assertFalse(view["presentation_timing"]["exact_frame_mapping"])
        self.assertEqual(
            view["presentation_completion"]["marker"], "presentation"
        )

    def test_control_comparison_uses_severe_events_over_minor_jitter(self) -> None:
        control = {
            "long_interval_events": 16,
            "long_interval_event_rate_hz": 2.0,
            "event_spacing_median_seconds": 0.4,
            "severe_interval_events": 7,
            "severe_interval_event_rate_hz": 0.875,
            "severe_event_spacing_median_seconds": 1.2,
            "swap_interval_ms": {"maximum": 55.0},
        }
        wheel = {
            "long_interval_events": 7,
            "long_interval_event_rate_hz": 0.875,
            "event_spacing_median_seconds": 1.2,
            "severe_interval_events": 7,
            "severe_interval_event_rate_hz": 0.875,
            "severe_event_spacing_median_seconds": 1.2,
            "swap_interval_ms": {"maximum": 50.0},
        }
        comparison = diagnostic.compare_to_control(control, wheel)
        self.assertEqual(
            "severe_over_4_nominal_slots",
            comparison["comparison_event_class"],
        )
        self.assertTrue(comparison["similar_to_control"])


if __name__ == "__main__":
    unittest.main()
