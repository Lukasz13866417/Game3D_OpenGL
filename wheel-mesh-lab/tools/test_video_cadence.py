#!/usr/bin/env python3
"""Regression tests for native-cadence wheel-lab video exports."""

from __future__ import annotations

import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

import render_rpm_sweep
import render_temporal_suite
from video_cadence import (
    MAX_SEQUENCE_FRAMES,
    cfr_output_arguments,
    contiguous_ppm_frame_count,
    encode_verified_video,
    ffmpeg_rate,
    prepare_ppm_sequence_output,
    probe_video,
    require_supported_sequence_frame_count,
    verify_video,
)


FFMPEG = shutil.which("ffmpeg")
FFPROBE = shutil.which("ffprobe")


def write_sequence(directory: Path, frames: int) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    for index in range(frames):
        red = (index * 43) % 256
        pixels = bytes((red, 80, 160)) * 4
        (directory / f"frame-{index:05d}.ppm").write_bytes(
            b"P6\n2 2\n255\n" + pixels
        )


class VideoCadenceUnitTest(unittest.TestCase):
    def test_cfr_arguments_retain_requested_rate(self) -> None:
        self.assertEqual(
            cfr_output_arguments(120.0),
            ("-fps_mode", "cfr", "-r", "120"),
        )
        self.assertEqual(ffmpeg_rate(60000 / 1001), "59.9400599400599")

    def test_frame_sequence_must_be_contiguous(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            sequence = Path(temporary)
            (sequence / "frame-00000.ppm").touch()
            (sequence / "frame-00002.ppm").touch()
            with self.assertRaisesRegex(ValueError, "contiguous"):
                contiguous_ppm_frame_count(sequence)

    def test_expected_frame_count_rejects_a_stale_tail(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            sequence = Path(temporary)
            write_sequence(sequence, 3)
            with self.assertRaisesRegex(ValueError, "exactly 2 contiguous frames"):
                contiguous_ppm_frame_count(sequence, 2)

    def test_temporal_suite_three_to_two_frame_rerun_is_clean(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            args = SimpleNamespace(
                binary=Path("fake-wheel-mesh-lab"),
                preset="tread",
                rps=1.0,
                width=2,
                height=2,
                max_roll_step_deg=1.5,
                split_oracle_samples=128,
            )

            def fake_renderer(command: list[str], *, check: bool) -> None:
                self.assertTrue(check)
                sequence = Path(command[command.index("--sequence-dir") + 1])
                frames = int(command[command.index("--sequence-frames") + 1])
                self.assertFalse((sequence / "manifest.tsv").exists())
                write_sequence(sequence, frames)
                (sequence / "manifest.tsv").write_text(
                    "frame\n" + "".join(f"{index}\n" for index in range(frames)),
                    encoding="utf-8",
                )

            with mock.patch.object(
                render_temporal_suite.subprocess,
                "run",
                side_effect=fake_renderer,
            ):
                sequence = render_temporal_suite.render_mode(
                    args, "sharp", 120.0, 3, output
                )
                render_temporal_suite.render_mode(
                    args, "sharp", 120.0, 2, output
                )

            self.assertEqual(contiguous_ppm_frame_count(sequence, 2), 2)
            self.assertEqual(
                (sequence / "manifest.tsv").read_text(encoding="utf-8"),
                "frame\n0\n1\n",
            )

    def test_temporal_encoder_rejects_frames_beyond_expected_count(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            sequence = Path(temporary) / "sharp"
            write_sequence(sequence, 3)
            with mock.patch.object(
                render_temporal_suite,
                "encode_verified_video",
            ) as encode:
                with self.assertRaisesRegex(
                    ValueError, "exactly 2 contiguous frames"
                ):
                    render_temporal_suite.encode_video(
                        "ffmpeg", "ffprobe", sequence, 120.0, 2
                    )
            encode.assert_not_called()

    def test_sequence_preparation_removes_only_prior_sequence_artifacts(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            sequence = Path(temporary)
            write_sequence(sequence, 3)
            (sequence / "manifest.tsv").write_text("stale\n", encoding="utf-8")
            retained = sequence / "notes.txt"
            retained.write_text("keep", encoding="utf-8")
            prepare_ppm_sequence_output(sequence)
            self.assertEqual(list(sequence.glob("frame-*.ppm")), [])
            self.assertFalse((sequence / "manifest.tsv").exists())
            self.assertEqual(retained.read_text(encoding="utf-8"), "keep")

    def test_oversized_sequence_rejection_preserves_previous_capture(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            sequence = Path(temporary)
            prior_frame = sequence / "frame-00000.ppm"
            prior_manifest = sequence / "manifest.tsv"
            prior_frame.write_bytes(b"prior frame")
            prior_manifest.write_text("prior manifest\n", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "cannot exceed 100000"):
                prepare_ppm_sequence_output(
                    sequence, MAX_SEQUENCE_FRAMES + 1
                )

            self.assertEqual(prior_frame.read_bytes(), b"prior frame")
            self.assertEqual(
                prior_manifest.read_text(encoding="utf-8"), "prior manifest\n"
            )

    def test_temporal_duration_frame_limit_is_checked_per_cadence(self) -> None:
        args = SimpleNamespace(seconds=1000.0, frames=1)
        self.assertEqual(
            render_temporal_suite.frame_count_for_cadence(args, 100.0),
            MAX_SEQUENCE_FRAMES,
        )
        with self.assertRaisesRegex(ValueError, "cannot exceed 100000"):
            render_temporal_suite.frame_count_for_cadence(args, 180.0)

    def test_shared_sequence_frame_limit(self) -> None:
        require_supported_sequence_frame_count(MAX_SEQUENCE_FRAMES)
        with self.assertRaisesRegex(ValueError, "cannot exceed 100000"):
            require_supported_sequence_frame_count(MAX_SEQUENCE_FRAMES + 1)

    def test_rpm_sweep_video_defaults_to_capture_cadence(self) -> None:
        defaults = render_rpm_sweep.parse_args([])
        self.assertEqual(defaults.video_fps, 120.0)
        self.assertEqual(defaults.output_dir.name, "rpm-sweep-band-120hz")

        native_180 = render_rpm_sweep.parse_args(["--fps", "180"])
        self.assertEqual(native_180.video_fps, 180.0)
        self.assertEqual(native_180.output_dir.name, "rpm-sweep-band-180hz")

        slow_motion = render_rpm_sweep.parse_args(
            ["--fps", "120", "--video-fps", "60"]
        )
        self.assertEqual(slow_motion.video_fps, 60.0)

    def test_rpm_reuse_derives_native_cadence_from_manifest(self) -> None:
        args = render_rpm_sweep.parse_args([
            "--reuse", "--output-dir", "/tmp/rpm-reuse-test"
        ])
        rows = [{"fps": "180"}, {"fps": "180.0"}]
        self.assertEqual(
            render_rpm_sweep.resolve_reuse_video_fps(args, rows),
            180.0,
        )
        self.assertEqual(args.fps, 180.0)

    def test_rpm_reuse_rejects_explicit_cadence_mismatch(self) -> None:
        args = render_rpm_sweep.parse_args([
            "--reuse", "--fps", "120",
            "--output-dir", "/tmp/rpm-reuse-test",
        ])
        with self.assertRaisesRegex(ValueError, "does not match manifest"):
            render_rpm_sweep.resolve_reuse_video_fps(
                args, [{"fps": "180"}]
            )

    def test_rpm_reuse_allows_explicit_retiming_only_via_video_fps(self) -> None:
        args = render_rpm_sweep.parse_args([
            "--reuse", "--video-fps", "60",
            "--output-dir", "/tmp/rpm-reuse-test",
        ])
        self.assertEqual(
            render_rpm_sweep.resolve_reuse_video_fps(
                args, [{"fps": "180"}]
            ),
            60.0,
        )

    def test_rpm_sweep_rejects_abbreviated_cadence_options(self) -> None:
        for abbreviated in ("--fp=120", "--video-f=60"):
            with self.subTest(option=abbreviated):
                with self.assertRaises(SystemExit):
                    render_rpm_sweep.parse_args(["--reuse", abbreviated])

    @unittest.skipUnless(FFMPEG and FFPROBE, "ffmpeg and ffprobe are required")
    def test_verified_export_is_120_fps_with_exact_frame_count(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            sequence = root / "frames"
            output = root / "native-120hz.mp4"
            write_sequence(sequence, 7)
            command = [
                FFMPEG,
                "-hide_banner", "-loglevel", "error", "-y",
                "-framerate", ffmpeg_rate(120.0),
                "-i", str(sequence / "frame-%05d.ppm"),
                "-c:v", "libx264", "-pix_fmt", "yuv420p",
                *cfr_output_arguments(120.0),
            ]
            metadata = encode_verified_video(
                command,
                FFPROBE,
                output,
                120.0,
                7,
            )
            self.assertEqual(float(metadata.average_fps), 120.0)
            self.assertEqual(metadata.frame_count, 7)
            self.assertEqual(probe_video(FFPROBE, output).frame_count, 7)

    @unittest.skipUnless(FFMPEG and FFPROBE, "ffmpeg and ffprobe are required")
    def test_verified_export_is_180_fps_with_exact_frame_count(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            sequence = root / "frames"
            output = root / "native-180hz.mp4"
            write_sequence(sequence, 9)
            command = [
                FFMPEG,
                "-hide_banner", "-loglevel", "error", "-y",
                "-framerate", ffmpeg_rate(180.0),
                "-i", str(sequence / "frame-%05d.ppm"),
                "-c:v", "libx264", "-pix_fmt", "yuv420p",
                *cfr_output_arguments(180.0),
            ]
            metadata = encode_verified_video(
                command,
                FFPROBE,
                output,
                180.0,
                9,
            )
            self.assertEqual(float(metadata.nominal_fps), 180.0)
            self.assertEqual(metadata.frame_count, 9)

    @unittest.skipUnless(FFMPEG and FFPROBE, "ffmpeg and ffprobe are required")
    def test_ffmpeg_image_default_is_rejected_as_120_fps(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            sequence = root / "frames"
            output = root / "accidental-default.mp4"
            write_sequence(sequence, 4)
            # Deliberately omit -framerate: image2's default is the regression
            # that created 25-fps files with 120hz/180hz names.
            subprocess.run(
                [
                    FFMPEG,
                    "-hide_banner", "-loglevel", "error", "-y",
                    "-i", str(sequence / "frame-%05d.ppm"),
                    "-c:v", "libx264", "-pix_fmt", "yuv420p",
                    str(output),
                ],
                check=True,
            )
            self.assertEqual(float(probe_video(FFPROBE, output).average_fps), 25.0)
            with self.assertRaisesRegex(RuntimeError, "expected 120 fps"):
                verify_video(FFPROBE, output, 120.0, 4)


if __name__ == "__main__":
    unittest.main()
