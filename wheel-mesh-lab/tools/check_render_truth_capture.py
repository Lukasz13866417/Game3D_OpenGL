#!/usr/bin/env python3
"""Smoke-test the headless, machine-readable wheel render-truth bundle."""

from __future__ import annotations

import argparse
import csv
import json
import subprocess
import tempfile
from pathlib import Path


def fnv1a64(data: bytes) -> str:
    value = 14695981039346656037
    for byte in data:
        value ^= byte
        value = (value * 1099511628211) & 0xFFFFFFFFFFFFFFFF
    return f"{value:016x}"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--binary", type=Path, required=True)
    args = parser.parse_args()
    binary = args.binary.resolve()
    require(binary.is_file(), f"renderer binary does not exist: {binary}")

    with tempfile.TemporaryDirectory(prefix="wheel-render-truth-") as temporary:
        root = Path(temporary)
        capture = root / "capture"
        sequence = root / "sequence"
        subprocess.run(
            [
                str(binary),
                "--preset", "tread",
                "--temporal-mode", "adaptive",
                "--spin-rps", "2",
                "--fps", "120",
                "--sequence-frames", "2",
                "--sequence-dir", str(sequence),
                "--buffer-dump-dir", str(capture),
                "--width", "64",
                "--height", "48",
            ],
            check=True,
            stdout=subprocess.DEVNULL,
        )

        descriptor = json.loads((capture / "capture.json").read_text())
        require(descriptor["schema"] == "wheel-render-truth-v1", "wrong schema")
        require(descriptor["layout"] == "frame-directories-v1", "wrong layout")
        require(descriptor["frame_count"] == 2, "wrong expected frame count")
        require(descriptor["requested_model"] == "mint", "default model is not mint")
        require(descriptor["effective_model"] == "mint", "mint model fell back")
        require(descriptor["model_slug"] == "mint-wheel", "wrong model slug")
        require(descriptor["width"] == 64 and descriptor["height"] == 48,
                "wrong final dimensions")
        stages = descriptor["stages"]
        require(stages["bloom"]["width"] == 16 and stages["bloom"]["height"] == 12,
                "wrong quarter-resolution bloom dimensions")

        expected_sizes = {
            "final.rgba8": 64 * 48 * 4,
            "scene.rgba8": 64 * 48 * 4,
            "bloom.rgba8": 16 * 12 * 4,
            "emission.rgba32f": 64 * 48 * 4 * 4,
        }
        final_hashes: list[str] = []
        for frame_index in range(2):
            frame_dir = capture / f"frame-{frame_index:05d}"
            metadata = json.loads((frame_dir / "frame.json").read_text())
            require(metadata["requested_model"] == "mint", "frame requested model mismatch")
            require(metadata["effective_model"] == "mint", "frame effective model mismatch")
            require(metadata["requested_temporal_mode"] == "adaptive",
                    "frame requested mode mismatch")
            require(metadata["effective_temporal_mode"] == "adaptive",
                    "adaptive unexpectedly fell back")
            require(metadata["temporal_source"] == "harmonic_shell",
                    "adaptive frame did not use the harmonic shell")
            require(metadata["temporal_grooves_available"], "mint grooves unavailable")
            require(metadata["emission_available"], "temporal emission unavailable")
            for filename, expected_size in expected_sizes.items():
                path = frame_dir / filename
                require(path.stat().st_size == expected_size,
                        f"wrong byte count for {path}")
            for stage, filename in (
                ("final", "final.rgba8"),
                ("scene", "scene.rgba8"),
                ("bloom", "bloom.rgba8"),
                ("emission", "emission.rgba32f"),
            ):
                actual_hash = fnv1a64((frame_dir / filename).read_bytes())
                require(actual_hash == metadata["buffers"][stage]["hash_fnv1a64"],
                        f"hash mismatch for {stage} frame {frame_index}")
            final_hashes.append(metadata["buffers"]["final"]["hash_fnv1a64"])
        require(len(set(final_hashes)) == 2, "moving mint frames unexpectedly match")

        with (capture / "frames.tsv").open(newline="") as source:
            rows = list(csv.DictReader(source, delimiter="\t"))
        require(len(rows) == 2, "frames.tsv does not contain two frames")
        require(all(row["model_slug"] == "mint-wheel" for row in rows),
                "frames.tsv contains a non-mint model")
        require(all(row["effective_temporal_mode"] == "adaptive" for row in rows),
                "frames.tsv records an adaptive fallback")

        rejected = subprocess.run(
            [
                str(binary),
                "--smoke-test",
                "--no-bloom",
                "--buffer-dump-dir", str(root / "invalid"),
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
        require(rejected.returncode != 0, "--no-bloom buffer dump was accepted")
        require("requires --bloom" in rejected.stdout,
                "--no-bloom rejection did not explain the canonical-target requirement")

    print("[ok] render-truth bundle: exact stages, metadata, hashes, mint default, CLI guard")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
