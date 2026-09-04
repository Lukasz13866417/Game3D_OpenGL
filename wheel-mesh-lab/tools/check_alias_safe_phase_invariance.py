#!/usr/bin/env python3
"""Verify every clean wheel mode's high-speed phase-invariant endpoint."""

from __future__ import annotations

import argparse
import math
import subprocess
import tempfile
from contextlib import nullcontext
from pathlib import Path


LAB_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_BINARY = LAB_ROOT / "build" / "wheel_mesh_lab"
DEFAULT_PHASES = (0.0, 3.25, 7.75, 13.0)
CLEAN_MODES = ("alias-safe", "adaptive", "split")
RAW_CONTROL_BY_MODE = {
    "adaptive": "adaptive-raw",
    "split": "split-raw",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--binary", type=Path, default=DEFAULT_BINARY)
    parser.add_argument(
        "--output-dir",
        type=Path,
        help="keep diagnostic captures here instead of using a temporary directory",
    )
    parser.add_argument("--cadences", type=float, nargs="+", default=(120.0, 180.0))
    parser.add_argument("--rps", type=float, default=22.3)
    parser.add_argument(
        "--modes",
        nargs="+",
        choices=CLEAN_MODES,
        default=list(CLEAN_MODES),
        help="clean entry points that must share the alias-safe endpoint",
    )
    parser.add_argument("--phases", type=float, nargs="+", default=DEFAULT_PHASES)
    parser.add_argument("--width", type=int, default=320)
    parser.add_argument("--height", type=int, default=240)
    parser.add_argument(
        "--max-channel-delta",
        type=int,
        default=0,
        help="maximum allowed 8-bit RGB difference at the high-speed endpoint",
    )
    return parser.parse_args()


def read_ppm(path: Path) -> tuple[int, int, bytes]:
    encoded = path.read_bytes()
    offset = 0

    def token() -> bytes:
        nonlocal offset
        while offset < len(encoded):
            if encoded[offset:offset + 1] == b"#":
                line_end = encoded.find(b"\n", offset)
                offset = len(encoded) if line_end < 0 else line_end + 1
            elif encoded[offset:offset + 1].isspace():
                offset += 1
            else:
                break
        start = offset
        while offset < len(encoded) and not encoded[offset:offset + 1].isspace():
            offset += 1
        if start == offset:
            raise RuntimeError(f"truncated PPM header in {path}")
        return encoded[start:offset]

    if token() != b"P6":
        raise RuntimeError(f"expected binary P6 PPM: {path}")
    width = int(token())
    height = int(token())
    maximum = int(token())
    if maximum != 255:
        raise RuntimeError(f"expected 8-bit PPM, found max={maximum}: {path}")
    if offset >= len(encoded) or not encoded[offset:offset + 1].isspace():
        raise RuntimeError(f"missing PPM pixel-data separator: {path}")
    offset += 2 if encoded[offset:offset + 2] == b"\r\n" else 1
    pixels = encoded[offset:]
    expected = width * height * 3
    if len(pixels) != expected:
        raise RuntimeError(
            f"expected {expected} PPM pixel bytes, found {len(pixels)}: {path}"
        )
    return width, height, pixels


def render(
    args: argparse.Namespace,
    output: Path,
    mode: str,
    fps: float,
    rps: float,
    phase_degrees: float,
) -> tuple[int, int, bytes]:
    command = [
        str(args.binary),
        "--smoke-test",
        "--model", "mint",
        "--preset", "tread",
        "--temporal-mode", mode,
        "--spin-rps", str(rps),
        "--spin-phase-degrees", str(phase_degrees),
        "--fps", str(fps),
        "--width", str(args.width),
        "--height", str(args.height),
        "--screenshot", str(output),
    ]
    subprocess.run(command, check=True)
    return read_ppm(output)


def pixel_difference(
    expected: tuple[int, int, bytes],
    actual: tuple[int, int, bytes],
) -> tuple[int, int]:
    if actual[:2] != expected[:2]:
        raise RuntimeError(
            f"capture dimensions differ: expected {expected[:2]}, found {actual[:2]}"
        )
    max_delta = 0
    changed_pixels = 0
    for offset in range(0, len(expected[2]), 3):
        pixel_changed = False
        for channel in range(3):
            delta = abs(expected[2][offset + channel] - actual[2][offset + channel])
            max_delta = max(max_delta, delta)
            pixel_changed = pixel_changed or delta != 0
        changed_pixels += int(pixel_changed)
    return max_delta, changed_pixels


def verify_cadence(
    args: argparse.Namespace,
    directory: Path,
    mode: str,
    fps: float,
) -> None:
    cadence = f"{fps:g}".replace(".", "p") + "hz"
    cadence_dir = directory / mode / cadence
    cadence_dir.mkdir(parents=True, exist_ok=True)
    reference = render(
        args,
        cadence_dir / f"high-phase-{args.phases[0]:g}.ppm",
        mode,
        fps,
        args.rps,
        args.phases[0],
    )
    for phase in args.phases[1:]:
        candidate = render(
            args,
            cadence_dir / f"high-phase-{phase:g}.ppm",
            mode,
            fps,
            args.rps,
            phase,
        )
        max_delta, changed_pixels = pixel_difference(reference, candidate)
        print(
            f"[ok] {mode} at {fps:g} Hz, high-speed phase {phase:g}°: "
            f"max RGB delta={max_delta}, changed pixels={changed_pixels}"
        )
        if max_delta > args.max_channel_delta:
            raise RuntimeError(
                f"{mode} alias-safe endpoint depends on phase at {fps:g} Hz: "
                f"phase {phase:g} has max RGB delta {max_delta} "
                f"(allowed {args.max_channel_delta})"
            )

    raw_mode = RAW_CONTROL_BY_MODE.get(mode)
    if raw_mode is not None:
        raw = render(
            args,
            cadence_dir / f"{raw_mode}-control.ppm",
            raw_mode,
            fps,
            args.rps,
            args.phases[0],
        )
        raw_max_delta, raw_changed_pixels = pixel_difference(reference, raw)
        if raw_changed_pixels == 0:
            raise RuntimeError(
                f"{raw_mode} no longer differs from protected {mode} at {fps:g} Hz"
            )
        print(
            f"[ok] {raw_mode} remains a distinct alias diagnostic at {fps:g} Hz: "
            f"max RGB delta={raw_max_delta}, changed pixels={raw_changed_pixels}"
        )

    # A phase-invariant result at every speed could hide the physical grooves
    # entirely. Confirm that the same mode still presents their real phase when
    # the displayed delta is zero and the continuous-band LOD is inactive.
    low_a = render(args, cadence_dir / "low-phase-0.ppm", mode, fps, 0.0, 0.0)
    low_b = render(args, cadence_dir / "low-phase-5.ppm", mode, fps, 0.0, 5.0)
    low_max_delta, low_changed_pixels = pixel_difference(low_a, low_b)
    if low_changed_pixels == 0:
        raise RuntimeError(
            f"{mode} low-speed physical grooves did not change with phase at {fps:g} Hz"
        )
    print(
        f"[ok] {mode} at {fps:g} Hz, low-speed physical phase remains visible: "
        f"max RGB delta={low_max_delta}, changed pixels={low_changed_pixels}"
    )


def main() -> None:
    args = parse_args()
    if not args.binary.is_file():
        raise SystemExit(
            f"missing {args.binary}; run cmake --build wheel-mesh-lab/build first"
        )
    if args.width < 1 or args.height < 1:
        raise SystemExit("--width and --height must be positive")
    if args.max_channel_delta < 0 or args.max_channel_delta > 255:
        raise SystemExit("--max-channel-delta must be between 0 and 255")
    if not math.isfinite(args.rps):
        raise SystemExit("--rps must be finite")
    if len(args.phases) < 2 or any(not math.isfinite(p) for p in args.phases):
        raise SystemExit("--phases requires at least two finite values")
    if not args.cadences or any(
        not math.isfinite(fps) or fps <= 0.0 for fps in args.cadences
    ):
        raise SystemExit("--cadences values must be finite and positive")
    if len(set(args.cadences)) != len(args.cadences):
        raise SystemExit("--cadences must not contain duplicates")

    if args.output_dir is None:
        context = tempfile.TemporaryDirectory(prefix="wheel-alias-safe-")
    else:
        args.output_dir.mkdir(parents=True, exist_ok=True)
        context = nullcontext(str(args.output_dir))
    with context as raw_directory:
        directory = Path(raw_directory)
        for mode in args.modes:
            for fps in args.cadences:
                verify_cadence(args, directory, mode, fps)
        if args.output_dir is not None:
            print(f"Diagnostic captures: {directory}")

    print(
        "Alias-safe phase invariance verified for "
        + ", ".join(args.modes)
        + " at "
        + ", ".join(f"{fps:g} Hz" for fps in args.cadences)
    )


if __name__ == "__main__":
    main()
