#!/usr/bin/env python3
"""Exercise deterministic live-timing replay with a seeded cadence fault."""

from __future__ import annotations

import argparse
import csv
import hashlib
from pathlib import Path
import subprocess
import tempfile


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--binary", type=Path, required=True)
    return parser.parse_args()


def write_fault_trace(path: Path) -> None:
    hz = 180.0
    nominal_ms = 1000.0 / hz
    intervals = [0.0] + [nominal_ms] * 31
    intervals[10] = nominal_ms * 8.0
    intervals[11] = 0.20
    swap_return = 0.0
    rows = []
    for frame, interval in enumerate(intervals):
        swap_return += interval
        loop_delta = 0.0 if frame == 0 else intervals[frame - 1]
        rows.append({
            "frame": frame,
            "loop_delta_ms": loop_delta,
            "swap_return_ms": swap_return,
            "swap_interval_ms": interval,
            "nominal_hz": hz,
        })
    with path.open("w", newline="", encoding="utf-8") as destination:
        writer = csv.DictWriter(destination, fieldnames=list(rows[0]), delimiter="\t")
        writer.writeheader()
        writer.writerows(rows)


def run_replay(binary: Path, trace: Path, output: Path, clock: str) -> None:
    result = subprocess.run(
        [
            str(binary),
            "--frame-timing-replay", str(trace),
            "--sequence-dir", str(output),
            "--phase-clock", clock,
            "--model", "mint",
            "--preset", "tread",
            "--temporal-mode", "adaptive",
            "--spin-rps", "4",
            "--width", "96",
            "--height", "72",
            "--no-bloom",
        ],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"{clock} timing replay failed ({result.returncode}):\n{result.stdout}"
        )


def read_rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as source:
        return list(csv.DictReader(source, delimiter="\t"))


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    args = parse_args()
    with tempfile.TemporaryDirectory(prefix="wheel-timing-replay-") as directory:
        root = Path(directory)
        trace = root / "fault.tsv"
        write_fault_trace(trace)
        scheduled_a = root / "scheduled-a"
        scheduled_b = root / "scheduled-b"
        previous = root / "previous"
        run_replay(args.binary, trace, scheduled_a, "scheduled")
        run_replay(args.binary, trace, scheduled_b, "scheduled")
        run_replay(args.binary, trace, previous, "previous-delta")

        scheduled_rows = read_rows(scheduled_a / "submissions.tsv")
        previous_rows = read_rows(previous / "submissions.tsv")
        scheduled_delta = [
            abs(float(row["physical_pose_delta_degrees"]))
            for row in scheduled_rows[1:]
        ]
        previous_delta = [
            abs(float(row["physical_pose_delta_degrees"]))
            for row in previous_rows[1:]
        ]
        nominal_delta = 4.0 * 360.0 / 180.0
        if min(scheduled_delta) < nominal_delta * 0.75 - 1e-6:
            raise RuntimeError("scheduled replay produced a slow pose step")
        if max(scheduled_delta) > nominal_delta * 1.5 + 1e-6:
            raise RuntimeError("scheduled replay produced a catch-up pose step")
        if min(previous_delta) >= nominal_delta * 0.8:
            raise RuntimeError("seeded previous-delta replay did not produce a slow step")
        if max(previous_delta) <= nominal_delta * 1.2:
            raise RuntimeError("seeded previous-delta replay did not catch up")

        presentation = read_rows(scheduled_a / "manifest.tsv")
        if not presentation or any(
            row.get("timing_source") != "swap_return_proxy_replay"
            for row in presentation
        ):
            raise RuntimeError("replay manifest lost its timing-source caveat")
        if any(float(row["fps"]) != 180.0 or float(row["rps"]) != 4.0
               for row in presentation):
            raise RuntimeError("replay manifest lost nominal FPS/RPS telemetry")
        source_counts: dict[int, int] = {}
        for row in presentation:
            source = int(row["source_submission"])
            source_counts[source] = source_counts.get(source, 0) + 1
        if max(source_counts.values(), default=0) < 8:
            raise RuntimeError("seeded long swap did not hold its preceding source")

        deterministic_files = [
            "manifest.tsv",
            "submissions.tsv",
            "qa-timing.svg",
            "sources/source-00009.ppm",
        ]
        for relative in deterministic_files:
            if digest(scheduled_a / relative) != digest(scheduled_b / relative):
                raise RuntimeError(f"timing replay is not deterministic: {relative}")

        bad_trace = root / "bad.tsv"
        text = trace.read_text(encoding="utf-8")
        bad_trace.write_text(text.replace("180.0", "nan", 1), encoding="utf-8")
        failure = subprocess.run(
            [
                str(args.binary),
                "--frame-timing-replay", str(bad_trace),
                "--sequence-dir", str(root / "bad-output"),
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )
        if failure.returncode == 0 or "invalid nominal_hz" not in failure.stdout:
            raise RuntimeError("non-finite timing replay was not rejected")

    print("[ok] timing replay reproduces hold/catch-up, scheduled containment, "
          "deterministic pixels/manifests, and rejects non-finite input")


if __name__ == "__main__":
    main()
