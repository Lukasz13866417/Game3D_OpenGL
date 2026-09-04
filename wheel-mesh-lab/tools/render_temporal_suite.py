#!/usr/bin/env python3
"""Render deterministic wheel-motion sequences and optional comparison videos."""

from __future__ import annotations

import argparse
import math
import shutil
import subprocess
from pathlib import Path

from video_cadence import (
    MAX_SEQUENCE_FRAMES,
    cfr_output_arguments,
    contiguous_ppm_frame_count,
    encode_verified_video,
    ffmpeg_rate,
    prepare_ppm_sequence_output,
    require_supported_sequence_frame_count,
)


LAB_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_BINARY = LAB_ROOT / "build" / "wheel_mesh_lab"
MODES = (
    "sharp",
    "reference",
    "adaptive",
    "adaptive-raw",
    "band",
    "split",
    "split-raw",
    "split-oracle",
    "alias-safe",
)
# The clean adaptive and split choices analytically filter each periodic tread
# harmonic. Their old geometry-multipass forms remain explicitly selectable as
# adaptive-raw and split-raw for diagnosis.
DEFAULT_COMPARISON_MODES = ("adaptive-raw", "adaptive", "split-raw", "split")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--binary", type=Path, default=DEFAULT_BINARY)
    parser.add_argument("--output-dir", type=Path,
                        default=LAB_ROOT / "build" / "temporal-suite")
    parser.add_argument(
        "--modes",
        nargs="+",
        choices=MODES,
        default=list(DEFAULT_COMPARISON_MODES),
    )
    parser.add_argument("--fps", type=float, default=120.0)
    parser.add_argument(
        "--cadences",
        nargs="+",
        type=float,
        help=(
            "render the complete comparison at each listed display cadence; "
            "for the intended cross-cadence check use '--cadences 120 180'"
        ),
    )
    parser.add_argument("--rps", type=float, default=22.3)
    parser.add_argument("--frames", type=int, default=120)
    parser.add_argument(
        "--seconds",
        type=float,
        help=(
            "render this duration at every cadence (overrides --frames); "
            "useful with '--cadences 120 180'"
        ),
    )
    parser.add_argument("--width", type=int, default=640)
    parser.add_argument("--height", type=int, default=480)
    parser.add_argument(
        "--max-roll-step-deg",
        type=float,
        default=1.5,
        help=(
            "maximum interval angle for split and split-raw modes "
            "(default: 1.5 degrees)"
        ),
    )
    parser.add_argument(
        "--split-oracle-samples",
        type=int,
        default=128,
        help="trailing-box samples used by the split-oracle alias",
    )
    parser.add_argument(
        "--preset",
        choices=("side", "tread", "three-quarter", "gameplay"),
        default="tread",
    )
    parser.add_argument("--no-video", action="store_true")
    return parser.parse_args()


def render_mode(
    args: argparse.Namespace,
    mode: str,
    fps: float,
    frames: int,
    output_dir: Path,
) -> Path:
    sequence_dir = output_dir / mode
    prepare_ppm_sequence_output(sequence_dir, frames)
    command = [
        str(args.binary),
        "--model", "mint",
        "--preset", args.preset,
        "--temporal-mode", "split-raw" if mode == "split-oracle" else mode,
        "--spin-rps", str(args.rps),
        "--spin-phase-degrees", "0",
        "--fps", str(fps),
        "--width", str(args.width),
        "--height", str(args.height),
        "--sequence-dir", str(sequence_dir),
        "--sequence-frames", str(frames),
    ]
    if mode in ("split", "split-raw"):
        command.extend(("--max-roll-step-deg", str(args.max_roll_step_deg)))
    elif mode == "split-oracle":
        degrees_per_frame = abs(args.rps) * 360.0 / fps
        # Choosing D/N makes ceil(D/maxStep)==N. Inflate by a tiny relative
        # epsilon so floating-point parsing cannot accidentally request N+1.
        oracle_step = (
            degrees_per_frame / args.split_oracle_samples * (1.0 + 1e-7)
            if degrees_per_frame > 0.0
            else args.max_roll_step_deg
        )
        command.extend(("--max-roll-step-deg", str(oracle_step)))
    print("+", " ".join(command), flush=True)
    subprocess.run(command, check=True)
    contiguous_ppm_frame_count(sequence_dir, frames)
    return sequence_dir


def encode_video(
    ffmpeg: str,
    ffprobe: str,
    sequence_dir: Path,
    fps: float,
    frames: int,
) -> Path:
    contiguous_ppm_frame_count(sequence_dir, frames)
    output = sequence_dir.with_suffix(".mp4")
    metadata = encode_verified_video(
        [
            ffmpeg,
            "-hide_banner", "-loglevel", "error", "-y",
            "-framerate", ffmpeg_rate(fps),
            "-i", str(sequence_dir / "frame-%05d.ppm"),
            "-c:v", "libx264", "-crf", "16", "-preset", "slow",
            "-pix_fmt", "yuv420p",
            *cfr_output_arguments(fps),
        ],
        ffprobe,
        output,
        fps,
        frames,
    )
    print(
        f"Video: {output} "
        f"({float(metadata.average_fps):g} fps, {metadata.frame_count} frames)"
    )
    return output


def mode_label(args: argparse.Namespace, mode: str) -> str:
    if mode == "split":
        return "SPLIT analytic one-frame box"
    if mode == "split-raw":
        return f"SPLIT RAW {args.max_roll_step_deg:g}deg (aliases)"
    if mode == "split-oracle":
        return f"SPLIT RAW ORACLE {args.split_oracle_samples} samples"
    if mode == "reference":
        return "REFERENCE centered 0.75-frame Hann"
    if mode == "band":
        return "LEGACY one-pitch morph"
    if mode == "alias-safe":
        return "ALIAS-SAFE continuous band"
    if mode == "adaptive":
        return "ADAPTIVE analytic 0.75-frame Hann"
    if mode == "adaptive-raw":
        return "ADAPTIVE RAW (alias diagnostic)"
    if mode == "sharp":
        return "SHARP current pose"
    return mode.upper()


def labelled_stack_filter(args: argparse.Namespace, modes: list[str]) -> str:
    labelled = []
    for index, mode in enumerate(modes):
        # Labels make the protected modes and their deliberately alias-prone
        # diagnostic controls impossible to confuse in generated evidence.
        label = mode_label(args, mode).replace("'", r"\'")
        labelled.append(
            f"[{index}:v]drawtext=text='{label}':fontcolor=white:fontsize=20:"
            "box=1:boxcolor=black@0.70:boxborderw=7:x=10:y=10"
            f"[label{index}]"
        )
    inputs = "".join(f"[label{index}]" for index in range(len(modes)))
    if len(modes) == 2:
        layout = "0_0|w0_0"
    elif len(modes) == 4:
        layout = "0_0|w0_0|0_h0|w0_h0"
    else:
        raise ValueError("comparison stacking supports exactly two or four modes")
    labelled.append(
        f"{inputs}xstack=inputs={len(modes)}:layout={layout}[v]"
    )
    return ";".join(labelled)


def encode_comparison(
    ffmpeg: str,
    ffprobe: str,
    args: argparse.Namespace,
    modes: list[str],
    videos: list[Path],
    output_dir: Path,
    fps: float,
    frames: int,
) -> Path | None:
    if len(videos) not in (2, 4):
        return None
    output = output_dir / "comparison.mp4"
    command = [ffmpeg, "-hide_banner", "-loglevel", "error", "-y"]
    for video in videos:
        command.extend(("-i", str(video)))
    command.extend(
        (
            "-filter_complex",
            labelled_stack_filter(args, modes),
            "-map", "[v]",
            "-c:v", "libx264", "-crf", "16", "-preset", "slow",
            "-pix_fmt", "yuv420p",
            *cfr_output_arguments(fps),
        )
    )
    metadata = encode_verified_video(
        command,
        ffprobe,
        output,
        fps,
        frames,
    )
    print(
        f"Comparison video: {output} "
        f"({float(metadata.average_fps):g} fps, {metadata.frame_count} frames)"
    )
    return output


def encode_comparison_still(
    ffmpeg: str,
    args: argparse.Namespace,
    modes: list[str],
    sequence_dirs: list[Path],
    output_dir: Path,
    frame_index: int,
) -> Path | None:
    if len(sequence_dirs) not in (2, 4):
        return None
    output = output_dir / "contact-sheet.png"
    command = [ffmpeg, "-hide_banner", "-loglevel", "error", "-y"]
    filename = f"frame-{frame_index:05d}.ppm"
    for sequence_dir in sequence_dirs:
        command.extend(("-i", str(sequence_dir / filename)))
    command.extend(
        (
            "-filter_complex",
            labelled_stack_filter(args, modes),
            "-map", "[v]",
            "-frames:v", "1",
            str(output),
        )
    )
    subprocess.run(command, check=True)
    print(f"Comparison still (frame {frame_index}): {output}")
    shutil.copyfile(output, output_dir / "comparison-frame.png")
    return output


def cadence_slug(fps: float) -> str:
    return f"{fps:g}".replace(".", "p") + "hz"


def frame_count_for_cadence(args: argparse.Namespace, fps: float) -> int:
    if args.seconds is None:
        frames = args.frames
    else:
        raw_frames = args.seconds * fps
        if not math.isfinite(raw_frames):
            raise ValueError("--seconds * cadence produces a non-finite frame count")
        frames = max(1, int(round(raw_frames)))
    require_supported_sequence_frame_count(frames)
    return frames


def render_cadence(
    args: argparse.Namespace,
    fps: float,
    frames: int,
    output_dir: Path,
) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    sequence_dirs = [
        render_mode(args, mode, fps, frames, output_dir) for mode in args.modes
    ]

    if args.no_video:
        return
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        print("ffmpeg is unavailable; deterministic PPM sequences were still generated")
        return
    ffprobe = shutil.which("ffprobe")
    if ffprobe is None:
        raise RuntimeError(
            "ffprobe is required for verified video export; "
            "the deterministic PPM sequences were still generated"
        )
    videos = [
        encode_video(ffmpeg, ffprobe, sequence_dir, fps, frames)
        for sequence_dir in sequence_dirs
    ]
    if len(args.modes) in (2, 4):
        encode_comparison(
            ffmpeg,
            ffprobe,
            args,
            args.modes,
            videos,
            output_dir,
            fps,
            frames,
        )
        still_frame = min(
            frames - 1,
            max(0, int(round(fps * 0.25))),
        )
        encode_comparison_still(
            ffmpeg,
            args,
            args.modes,
            sequence_dirs,
            output_dir,
            still_frame,
        )


def main() -> None:
    args = parse_args()
    if not args.binary.is_file():
        raise SystemExit(
            f"missing {args.binary}; run cmake --build wheel-mesh-lab/build first"
        )
    if args.frames < 1:
        raise SystemExit("--frames must be positive")
    if args.seconds is not None and (
        not math.isfinite(args.seconds) or args.seconds <= 0.0
    ):
        raise SystemExit("--seconds must be finite and positive")
    cadences = args.cadences if args.cadences is not None else [args.fps]
    if not cadences or any(
        not math.isfinite(fps) or not fps > 0.0 for fps in cadences
    ):
        raise SystemExit("--fps/--cadences values must be finite and positive")
    if len(set(cadences)) != len(cadences):
        raise SystemExit("--cadences must not contain duplicates")
    if not math.isfinite(args.rps):
        raise SystemExit("--rps must be finite")
    if not math.isfinite(args.max_roll_step_deg) or not args.max_roll_step_deg > 0.0:
        raise SystemExit("--max-roll-step-deg must be finite and positive")
    if args.split_oracle_samples < 2:
        raise SystemExit("--split-oracle-samples must be at least 2")
    try:
        cadence_jobs = [
            (fps, frame_count_for_cadence(args, fps)) for fps in cadences
        ]
    except ValueError as error:
        raise SystemExit(
            f"capture exceeds the {MAX_SEQUENCE_FRAMES}-frame limit: {error}"
        ) from error
    args.output_dir.mkdir(parents=True, exist_ok=True)
    use_cadence_subdirectories = len(cadences) > 1
    for fps, frames in cadence_jobs:
        cadence_output = (
            args.output_dir / cadence_slug(fps)
            if use_cadence_subdirectories
            else args.output_dir
        )
        print(f"\n=== {fps:g} Hz ===", flush=True)
        render_cadence(args, fps, frames, cadence_output)


if __name__ == "__main__":
    main()
