#!/usr/bin/env python3
"""Encode existing PPM sequences side by side at a verified native cadence."""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

from video_cadence import (
    cfr_output_arguments,
    contiguous_ppm_frame_count,
    encode_verified_video,
    ffmpeg_rate,
    require_finite_positive_fps,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--input",
        type=Path,
        action="append",
        required=True,
        dest="inputs",
        help="frame-00000.ppm sequence directory; specify exactly two or four",
    )
    parser.add_argument("--fps", type=float, required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def stack_filter(input_count: int) -> str:
    if input_count == 2:
        layout = "0_0|w0_0"
    elif input_count == 4:
        layout = "0_0|w0_0|0_h0|w0_h0"
    else:
        raise ValueError("exactly two or four input sequences are required")
    inputs = "".join(f"[{index}:v]" for index in range(input_count))
    return f"{inputs}xstack=inputs={input_count}:layout={layout}[v]"


def main() -> None:
    args = parse_args()
    try:
        require_finite_positive_fps(args.fps)
        frame_counts = [contiguous_ppm_frame_count(path) for path in args.inputs]
        filter_graph = stack_filter(len(args.inputs))
    except ValueError as error:
        raise SystemExit(str(error)) from error
    if len(set(frame_counts)) != 1:
        raise SystemExit(
            "all input sequences must contain the same number of contiguous frames: "
            + ", ".join(
                f"{path}={count}" for path, count in zip(args.inputs, frame_counts)
            )
        )
    ffmpeg = shutil.which("ffmpeg")
    ffprobe = shutil.which("ffprobe")
    if ffmpeg is None or ffprobe is None:
        raise SystemExit("verified video export requires both ffmpeg and ffprobe")

    command = [ffmpeg, "-hide_banner", "-loglevel", "error", "-y"]
    for sequence_dir in args.inputs:
        command.extend(
            (
                "-framerate", ffmpeg_rate(args.fps),
                "-i", str(sequence_dir / "frame-%05d.ppm"),
            )
        )
    command.extend(
        (
            "-filter_complex", filter_graph,
            "-map", "[v]",
            "-c:v", "libx264", "-crf", "16", "-preset", "slow",
            "-pix_fmt", "yuv420p",
            *cfr_output_arguments(args.fps),
        )
    )
    metadata = encode_verified_video(
        command,
        ffprobe,
        args.output,
        args.fps,
        frame_counts[0],
    )
    print(
        f"Comparison video: {args.output} "
        f"({float(metadata.average_fps):g} fps, {metadata.frame_count} frames)"
    )


if __name__ == "__main__":
    main()
