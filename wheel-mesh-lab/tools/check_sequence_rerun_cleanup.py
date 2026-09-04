#!/usr/bin/env python3
"""Verify direct headless sequence reruns cannot retain stale tail frames."""

from __future__ import annotations

import argparse
import subprocess
import tempfile
from pathlib import Path

from video_cadence import contiguous_ppm_frame_count


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--binary", type=Path, required=True)
    return parser.parse_args()


def render(binary: Path, sequence: Path, frames: int) -> None:
    subprocess.run(
        [
            str(binary),
            "--model", "mint",
            "--preset", "tread",
            "--temporal-mode", "adaptive",
            "--spin-rps", "2",
            "--fps", "120",
            "--width", "64",
            "--height", "64",
            "--sequence-dir", str(sequence),
            "--sequence-frames", str(frames),
        ],
        check=True,
        stdout=subprocess.DEVNULL,
    )


def main() -> None:
    args = parse_args()
    with tempfile.TemporaryDirectory(prefix="wheel-sequence-rerun-") as temporary:
        sequence = Path(temporary) / "frames"
        render(args.binary, sequence, 3)
        render(args.binary, sequence, 2)
        contiguous_ppm_frame_count(sequence, 2)
        manifest = (sequence / "manifest.tsv").read_text(encoding="utf-8").splitlines()
        if len(manifest) != 3:
            raise RuntimeError(
                f"expected header plus 2 manifest rows after rerun, found {len(manifest)} lines"
            )
    print("Direct sequence 3->2 rerun cleanup verified")


if __name__ == "__main__":
    main()
