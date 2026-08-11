#!/usr/bin/env python3
"""
Pretty-print and summarize AndroidX benchmark JSON outputs.

Examples:
  python3 tools/format_benchmark_json.py
  python3 tools/format_benchmark_json.py --summary
  python3 tools/format_benchmark_json.py app/build/outputs/.../com.example.game3d_opengl-benchmarkData.json --out /tmp/bench.pretty.json
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Dict, List, Optional


def find_latest_benchmark_json(repo_root: Path) -> Optional[Path]:
    base = repo_root / "app" / "build" / "outputs" / "connected_android_test_additional_output"
    if not base.exists():
        return None
    candidates = list(base.glob("**/*-benchmarkData.json"))
    if not candidates:
        return None
    candidates.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    return candidates[0]


def as_float(value: Any) -> Optional[float]:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def format_ns_to_ms(ns: Optional[float]) -> str:
    if ns is None:
        return "-"
    return f"{ns / 1_000_000.0:.3f} ms"


def summarize(data: Dict[str, Any]) -> str:
    lines: List[str] = []
    context = data.get("context", {})
    build = context.get("build", {})
    model = build.get("model", "unknown")
    sdk = build.get("version", {}).get("sdk", "unknown")
    comp_mode = context.get("compilationMode", "unknown")
    lines.append(f"Device: {model} (SDK {sdk})")
    lines.append(f"Compilation mode: {comp_mode}")
    lines.append("")
    lines.append("Benchmarks:")

    benches = data.get("benchmarks", [])
    if not benches:
        lines.append("  (none)")
        return "\n".join(lines)

    for b in benches:
        name = b.get("name", "unknown")
        metrics = b.get("metrics", {})
        time_ns = metrics.get("timeNs", {})
        alloc = metrics.get("allocationCount", {})
        median_ns = as_float(time_ns.get("median"))
        min_ns = as_float(time_ns.get("minimum"))
        max_ns = as_float(time_ns.get("maximum"))
        median_alloc = as_float(alloc.get("median"))
        repeat = b.get("repeatIterations", "?")
        warmup = b.get("warmupIterations", "?")
        throttle_sleep = b.get("thermalThrottleSleepSeconds", "?")

        lines.append(f"- {name}")
        lines.append(
            f"  time: median={format_ns_to_ms(median_ns)} "
            f"min={format_ns_to_ms(min_ns)} max={format_ns_to_ms(max_ns)}"
        )
        if median_alloc is not None:
            lines.append(f"  alloc median: {median_alloc:.2f}")
        else:
            lines.append("  alloc median: -")
        lines.append(
            f"  warmupIterations={warmup}, repeatIterations={repeat}, "
            f"thermalThrottleSleepSeconds={throttle_sleep}"
        )
    return "\n".join(lines)


def resolve_input_path(repo_root: Path, raw_path: Optional[str]) -> Path:
    if raw_path:
        candidate = Path(raw_path)
        if not candidate.is_absolute():
            candidate = (repo_root / candidate).resolve()
        if not candidate.exists():
            raise FileNotFoundError(f"Input path does not exist: {candidate}")
        return candidate

    latest = find_latest_benchmark_json(repo_root)
    if latest is None:
        raise FileNotFoundError(
            "No benchmark JSON found. Run an androidTest benchmark first."
        )
    return latest


def main() -> int:
    parser = argparse.ArgumentParser(description="Format Android benchmark JSON output.")
    parser.add_argument(
        "input",
        nargs="?",
        help="Path to benchmarkData.json (defaults to newest under app/build/outputs/connected_android_test_additional_output).",
    )
    parser.add_argument(
        "--summary",
        action="store_true",
        help="Print concise summary instead of full JSON.",
    )
    parser.add_argument(
        "--out",
        help="Output file path. If omitted, print to stdout.",
    )
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[1]
    input_path = resolve_input_path(repo_root, args.input)
    with input_path.open("r", encoding="utf-8") as f:
        data = json.load(f)

    output = summarize(data) if args.summary else json.dumps(data, indent=2)

    if args.out:
        out_path = Path(args.out)
        if not out_path.is_absolute():
            out_path = (repo_root / out_path).resolve()
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(output + "\n", encoding="utf-8")
        print(f"Wrote formatted output to: {out_path}")
    else:
        print(output)

    print(f"\nSource: {input_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
