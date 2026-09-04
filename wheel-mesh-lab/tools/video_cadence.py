#!/usr/bin/env python3
"""Shared constant-frame-rate encoding and verification helpers."""

from __future__ import annotations

import json
import math
import os
import subprocess
from dataclasses import dataclass
from fractions import Fraction
from pathlib import Path
from typing import Sequence


MAX_SEQUENCE_FRAMES = 100_000


@dataclass(frozen=True)
class VideoMetadata:
    average_fps: Fraction
    nominal_fps: Fraction
    frame_count: int


def require_finite_positive_fps(fps: float) -> None:
    if not math.isfinite(fps) or fps <= 0.0:
        raise ValueError("video fps must be finite and positive")


def ffmpeg_rate(fps: float) -> str:
    """Return a locale-independent rate accepted by ffmpeg."""
    require_finite_positive_fps(fps)
    return format(fps, ".15g")


def cfr_output_arguments(fps: float) -> tuple[str, ...]:
    """Force the muxed stream to retain the requested presentation cadence."""
    return ("-fps_mode", "cfr", "-r", ffmpeg_rate(fps))


def temporary_video_path(output: Path) -> Path:
    """Keep ffmpeg's temporary output recognizable by its filename extension."""
    return output.with_name(f".{output.stem}.encoding{output.suffix}")


def _parse_rate(value: object, field: str) -> Fraction:
    if not isinstance(value, str):
        raise RuntimeError(f"ffprobe did not report {field}")
    try:
        rate = Fraction(value)
    except (ValueError, ZeroDivisionError) as error:
        raise RuntimeError(f"ffprobe reported invalid {field}: {value!r}") from error
    if rate <= 0:
        raise RuntimeError(f"ffprobe reported non-positive {field}: {value!r}")
    return rate


def probe_video(ffprobe: str, video: Path) -> VideoMetadata:
    result = subprocess.run(
        [
            ffprobe,
            "-v", "error",
            "-select_streams", "v:0",
            "-count_frames",
            "-show_entries",
            "stream=avg_frame_rate,r_frame_rate,nb_read_frames",
            "-of", "json",
            str(video),
        ],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    )
    try:
        payload = json.loads(result.stdout)
        streams = payload["streams"]
        stream = streams[0]
        frame_count = int(stream["nb_read_frames"])
    except (json.JSONDecodeError, KeyError, IndexError, TypeError, ValueError) as error:
        raise RuntimeError(
            f"ffprobe returned incomplete video metadata for {video}"
        ) from error
    if frame_count < 1:
        raise RuntimeError(f"ffprobe reported no frames for {video}")
    return VideoMetadata(
        average_fps=_parse_rate(stream.get("avg_frame_rate"), "avg_frame_rate"),
        nominal_fps=_parse_rate(stream.get("r_frame_rate"), "r_frame_rate"),
        frame_count=frame_count,
    )


def _rate_matches(actual: Fraction, expected: float) -> bool:
    tolerance = max(1e-6, abs(expected) * 1e-6)
    return math.isclose(float(actual), expected, rel_tol=0.0, abs_tol=tolerance)


def verify_video(
    ffprobe: str,
    video: Path,
    expected_fps: float,
    expected_frames: int,
) -> VideoMetadata:
    require_finite_positive_fps(expected_fps)
    if expected_frames < 1:
        raise ValueError("expected frame count must be positive")
    metadata = probe_video(ffprobe, video)
    if not _rate_matches(metadata.average_fps, expected_fps):
        raise RuntimeError(
            f"{video} average cadence is {float(metadata.average_fps):g} fps; "
            f"expected {expected_fps:g} fps"
        )
    if not _rate_matches(metadata.nominal_fps, expected_fps):
        raise RuntimeError(
            f"{video} nominal cadence is {float(metadata.nominal_fps):g} fps; "
            f"expected {expected_fps:g} fps"
        )
    if metadata.frame_count != expected_frames:
        raise RuntimeError(
            f"{video} contains {metadata.frame_count} frames; "
            f"expected {expected_frames}"
        )
    return metadata


def encode_verified_video(
    ffmpeg_command_without_output: Sequence[str],
    ffprobe: str,
    output: Path,
    expected_fps: float,
    expected_frames: int,
) -> VideoMetadata:
    """Encode atomically and refuse to publish cadence/frame-count mistakes."""
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = temporary_video_path(output)
    temporary.unlink(missing_ok=True)
    try:
        subprocess.run(
            [*ffmpeg_command_without_output, str(temporary)],
            check=True,
        )
        metadata = verify_video(
            ffprobe,
            temporary,
            expected_fps,
            expected_frames,
        )
        os.replace(temporary, output)
        return metadata
    finally:
        temporary.unlink(missing_ok=True)


def require_supported_sequence_frame_count(frame_count: int) -> None:
    if frame_count < 1:
        raise ValueError("sequence frame count must be positive")
    if frame_count > MAX_SEQUENCE_FRAMES:
        raise ValueError(
            f"sequence frame count cannot exceed {MAX_SEQUENCE_FRAMES}"
        )


def prepare_ppm_sequence_output(
    sequence_dir: Path,
    expected_frames: int | None = None,
) -> None:
    """Remove artifacts that could be mistaken for part of a fresh render."""
    # Validate before mkdir/unlink so an invalid new request cannot destroy a
    # previous valid capture.
    if expected_frames is not None:
        require_supported_sequence_frame_count(expected_frames)
    sequence_dir.mkdir(parents=True, exist_ok=True)
    for frame_path in sequence_dir.glob("frame-*.ppm"):
        frame_path.unlink()
    (sequence_dir / "manifest.tsv").unlink(missing_ok=True)


def contiguous_ppm_frame_count(
    sequence_dir: Path,
    expected_frames: int | None = None,
) -> int:
    """Validate an exact frame-00000.ppm... sequence and return its size."""
    if expected_frames is not None:
        require_supported_sequence_frame_count(expected_frames)
    frame_paths = sorted(sequence_dir.glob("frame-*.ppm"))
    if not frame_paths:
        raise ValueError(f"no frame-*.ppm files found in {sequence_dir}")
    frame_count = (
        expected_frames if expected_frames is not None else len(frame_paths)
    )
    expected_names = [f"frame-{index:05d}.ppm" for index in range(frame_count)]
    actual_names = [frame.name for frame in frame_paths]
    if actual_names != expected_names:
        expectation = (
            f"exactly {expected_frames} contiguous frames"
            if expected_frames is not None
            else "a contiguous sequence starting at 00000"
        )
        raise ValueError(
            f"frame sequence in {sequence_dir} has {len(frame_paths)} frame files; "
            f"expected {expectation}"
        )
    return len(frame_paths)
