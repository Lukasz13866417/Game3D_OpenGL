#!/usr/bin/env python3
"""Render and measure a fixed-pose band-mode RPM sweep.

The wheel pose and camera remain fixed, so changes in the output are caused by
the temporal planner/compositor rather than by tread phase. A single renderer
process performs the ramp, preserving the production LOD hysteresis state.
"""

from __future__ import annotations

import argparse
import csv
import math
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from video_cadence import (
    cfr_output_arguments,
    contiguous_ppm_frame_count,
    encode_verified_video,
    ffmpeg_rate,
    prepare_ppm_sequence_output,
    require_finite_positive_fps,
    require_supported_sequence_frame_count,
)

try:
    import numpy as np
    from PIL import Image, ImageDraw, ImageFont
except ImportError as exc:  # pragma: no cover - environment diagnostic
    raise SystemExit(
        "render_rpm_sweep.py requires NumPy and Pillow (python3-numpy/python3-pil)"
    ) from exc


LAB_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_BINARY = LAB_ROOT / "build" / "wheel_mesh_lab"


def resolved_video_fps(source_fps: float, requested_fps: float | None) -> float:
    """Use native capture cadence unless deliberate retiming was requested."""
    return source_fps if requested_fps is None else requested_fps


def cadence_slug(fps: float) -> str:
    return f"{fps:g}".replace(".", "p") + "hz"


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    raw_argv = list(sys.argv[1:] if argv is None else argv)
    # Explicit cadence flags have safety semantics during --reuse.  Disallow
    # argparse's long-option abbreviations so every accepted spelling is also
    # recognized by the explicitness checks below.
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument("--binary", type=Path, default=DEFAULT_BINARY)
    parser.add_argument(
        "--output-dir",
        type=Path,
        help="artifact directory (default: rpm-sweep-band-<fps>hz)",
    )
    parser.add_argument("--fps", type=float, default=120.0)
    parser.add_argument("--start-rps", type=float, default=0.0)
    parser.add_argument("--end-rps", type=float, default=24.0)
    parser.add_argument(
        "--frames",
        type=int,
        default=481,
        help="481 gives 0.05-rps steps over the default range",
    )
    parser.add_argument("--width", type=int, default=320)
    parser.add_argument("--height", type=int, default=240)
    parser.add_argument("--phase-degrees", type=float, default=10.0)
    parser.add_argument(
        "--video-fps",
        type=float,
        help="encoded playback rate (default: match --fps)",
    )
    parser.add_argument(
        "--legacy-hump-percent",
        type=float,
        help="optional previous peak above stationary for before/after reporting",
    )
    parser.add_argument("--keep-frames", action="store_true")
    parser.add_argument(
        "--reuse",
        action="store_true",
        help="analyze an already rendered frames/manifest.tsv",
    )
    args = parser.parse_args(raw_argv)
    args.fps_explicit = any(
        token == "--fps" or token.startswith("--fps=") for token in raw_argv
    )
    args.video_fps_explicit = any(
        token == "--video-fps" or token.startswith("--video-fps=")
        for token in raw_argv
    )
    if args.output_dir is None:
        args.output_dir = (
            LAB_ROOT / "build" / f"rpm-sweep-band-{cadence_slug(args.fps)}"
        )
    args.video_fps = resolved_video_fps(args.fps, args.video_fps)
    return args


def render(args: argparse.Namespace, frames_dir: Path) -> None:
    command = [
        str(args.binary),
        "--model", "mint",
        "--preset", "tread",
        "--temporal-mode", "band",
        "--spin-rps", str(args.start_rps),
        "--sequence-end-spin-rps", str(args.end_rps),
        "--sequence-fixed-phase",
        "--spin-phase-degrees", str(args.phase_degrees),
        "--fps", str(args.fps),
        "--width", str(args.width),
        "--height", str(args.height),
        "--sequence-dir", str(frames_dir),
        "--sequence-frames", str(args.frames),
    ]
    with (args.output_dir / "render.log").open("w", encoding="utf-8") as log:
        subprocess.run(command, check=True, stdout=log, stderr=subprocess.STDOUT)


def linear_rgb(image: Image.Image) -> np.ndarray:
    srgb = np.asarray(image.convert("RGB"), dtype=np.float32) / 255.0
    return np.where(
        srgb <= 0.04045,
        srgb / 12.92,
        ((srgb + 0.055) / 1.055) ** 2.4,
    )


def image_metrics(path: Path) -> dict[str, float]:
    with Image.open(path) as image:
        rgb = linear_rgb(image)
    luminance = (
        rgb[:, :, 0] * 0.2126
        + rgb[:, :, 1] * 0.7152
        + rgb[:, :, 2] * 0.0722
    )
    border = np.concatenate(
        (luminance[0], luminance[-1], luminance[:, 0], luminance[:, -1])
    )
    background = float(np.median(border))
    excess = np.maximum(luminance - background, 0.0)
    return {
        "background_luminance": background,
        "excess_luminance": float(np.sum(excess, dtype=np.float64)),
        "mean_luminance": float(np.mean(luminance, dtype=np.float64)),
        "peak_luminance": float(np.max(luminance)),
        "bright_pixel_fraction": float(np.mean(luminance >= 0.64)),
        "clipped_pixel_fraction": float(np.mean(np.max(rgb, axis=2) >= 0.999)),
    }


def read_manifest(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as source:
        return list(csv.DictReader(source, delimiter="\t"))


def manifest_capture_fps(rows: list[dict[str, str]]) -> float:
    if not rows or "fps" not in rows[0]:
        raise ValueError("manifest does not contain capture fps")
    try:
        values = [float(row["fps"]) for row in rows]
    except (KeyError, ValueError) as error:
        raise ValueError("manifest contains invalid capture fps") from error
    if any(not math.isfinite(value) or value <= 0.0 for value in values):
        raise ValueError("manifest capture fps must be finite and positive")
    capture_fps = values[0]
    if any(not math.isclose(value, capture_fps, rel_tol=0.0, abs_tol=1e-6)
           for value in values[1:]):
        raise ValueError("manifest contains inconsistent capture fps values")
    return capture_fps


def resolve_reuse_video_fps(
    args: argparse.Namespace,
    rows: list[dict[str, str]],
) -> float:
    capture_fps = manifest_capture_fps(rows)
    if args.fps_explicit and not math.isclose(
        args.fps, capture_fps, rel_tol=0.0, abs_tol=1e-6
    ):
        raise ValueError(
            f"--fps {args.fps:g} does not match manifest capture cadence "
            f"{capture_fps:g} fps"
        )
    args.fps = capture_fps
    return args.video_fps if args.video_fps_explicit else capture_fps


def write_metrics(
    path: Path,
    rows: list[dict[str, str]],
    metrics: list[dict[str, float]],
) -> list[float]:
    baseline = max(metrics[0]["excess_luminance"], 1.0e-12)
    normalized = [item["excess_luminance"] / baseline for item in metrics]
    fields = list(rows[0]) + [
        "excess_luminance",
        "normalized_excess_luminance",
        "mean_luminance",
        "peak_luminance",
        "bright_pixel_fraction",
        "clipped_pixel_fraction",
        "adjacent_energy_step_percent",
        "local_energy_residual_percent",
    ]
    with path.open("w", newline="", encoding="utf-8") as destination:
        writer = csv.DictWriter(destination, fieldnames=fields, delimiter="\t")
        writer.writeheader()
        for index, (row, item) in enumerate(zip(rows, metrics, strict=True)):
            adjacent = 0.0 if index == 0 else (
                normalized[index] - normalized[index - 1]
            ) * 100.0
            residual = 0.0
            if 0 < index < len(rows) - 1:
                residual = (
                    normalized[index]
                    - 0.5 * (normalized[index - 1] + normalized[index + 1])
                ) * 100.0
            output = dict(row)
            output.update({
                "excess_luminance": f"{item['excess_luminance']:.9g}",
                "normalized_excess_luminance": f"{normalized[index]:.9g}",
                "mean_luminance": f"{item['mean_luminance']:.9g}",
                "peak_luminance": f"{item['peak_luminance']:.9g}",
                "bright_pixel_fraction": f"{item['bright_pixel_fraction']:.9g}",
                "clipped_pixel_fraction": f"{item['clipped_pixel_fraction']:.9g}",
                "adjacent_energy_step_percent": f"{adjacent:.9g}",
                "local_energy_residual_percent": f"{residual:.9g}",
            })
            writer.writerow(output)
    return normalized


def closest_index(rows: list[dict[str, str]], field: str, target: float) -> int:
    return min(
        range(len(rows)),
        key=lambda index: abs(float(rows[index][field]) - target),
    )


def selected_contact_indices(
    rows: list[dict[str, str]], normalized: list[float]
) -> list[int]:
    candidates = [closest_index(rows, "rps", 0.0)]
    largest_step = max(
        range(1, len(rows)),
        key=lambda index: abs(normalized[index] - normalized[index - 1]),
    )
    first_active = next(
        (index for index, row in enumerate(rows) if row["temporal_active"] == "1"),
        0,
    )
    candidates.extend((
        first_active,
        largest_step - 1,
        largest_step,
        max(range(len(rows)), key=normalized.__getitem__),
    ))
    candidates.extend(
        closest_index(rows, "projected_sweep_pixels", value)
        for value in (0.5, 1.0, 1.5, 2.0, 2.5)
    )
    candidates.extend(
        closest_index(rows, "degrees_per_frame", value)
        for value in (8.0, 9.0, 10.0, 11.0, 12.0)
    )
    candidates.extend(
        closest_index(rows, "rps", value)
        for value in (6.0, 12.0, 18.0, 24.0)
    )
    return sorted(dict.fromkeys(candidates))


def build_contact_sheet(
    path: Path,
    frames_dir: Path,
    rows: list[dict[str, str]],
    normalized: list[float],
) -> None:
    indices = selected_contact_indices(rows, normalized)
    columns = 4
    tile_width = 240
    image_ratio = 0.75
    image_height = int(round(tile_width * image_ratio))
    label_height = 48
    tile_height = image_height + label_height
    row_count = math.ceil(len(indices) / columns)
    sheet = Image.new("RGB", (columns * tile_width, row_count * tile_height), "#11151c")
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()
    for position, index in enumerate(indices):
        source = frames_dir / f"frame-{index:05d}.ppm"
        with Image.open(source) as image:
            tile = image.convert("RGB").resize((tile_width, image_height), Image.Resampling.LANCZOS)
        x = (position % columns) * tile_width
        y = (position // columns) * tile_height
        sheet.paste(tile, (x, y))
        row = rows[index]
        label = (
            f"{float(row['rps']):5.2f} rps  {float(row['degrees_per_frame']):5.2f} deg/f\n"
            f"sweep {float(row['projected_sweep_pixels']):4.2f}px  "
            f"t {float(row['temporal_blend']):4.2f}  "
            f"corr {float(row['bloom_correction_blend']):4.2f}  "
            f"E {normalized[index]:.4f}"
        )
        draw.multiline_text((x + 6, y + image_height + 5), label, fill="#f2f4f8", font=font, spacing=3)
    sheet.save(path)


def write_energy_svg(
    path: Path,
    rows: list[dict[str, str]],
    normalized: list[float],
) -> None:
    width, height = 1000, 560
    left, right, top, bottom = 72, 28, 42, 64
    plot_width = width - left - right
    plot_height = height - top - bottom
    speeds = [float(row["rps"]) for row in rows]
    minimum = min(normalized)
    maximum = max(normalized)
    padding = max(0.01, (maximum - minimum) * 0.15)
    y_min = minimum - padding
    y_max = maximum + padding

    def x_at(speed: float) -> float:
        return left + (speed - speeds[0]) / (speeds[-1] - speeds[0]) * plot_width

    def y_at(value: float) -> float:
        return top + (y_max - value) / (y_max - y_min) * plot_height

    points = " ".join(
        f"{x_at(speed):.2f},{y_at(value):.2f}"
        for speed, value in zip(speeds, normalized, strict=True)
    )
    activation_start = float(rows[closest_index(rows, "projected_sweep_pixels", 0.5)]["rps"])
    activation_end = float(rows[closest_index(rows, "projected_sweep_pixels", 2.5)]["rps"])
    band_start = 8.0 * float(rows[0]["fps"]) / 360.0
    band_end = 12.0 * float(rows[0]["fps"]) / 360.0
    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#11151c"/>',
        '<text x="72" y="25" fill="#f2f4f8" font-family="sans-serif" font-size="17">Fixed-pose band-mode luminance sweep</text>',
    ]
    for speed, color, label in (
        (activation_start, "#43a6ff", "0.5px"),
        (activation_end, "#43a6ff", "2.5px"),
        (band_start, "#b47cff", "8 deg/f"),
        (band_end, "#b47cff", "12 deg/f"),
    ):
        x = x_at(speed)
        lines.append(f'<line x1="{x:.2f}" y1="{top}" x2="{x:.2f}" y2="{height-bottom}" stroke="{color}" stroke-width="1" stroke-dasharray="5 5"/>')
        lines.append(f'<text x="{x+4:.2f}" y="{top+15}" fill="{color}" font-family="sans-serif" font-size="11">{label}</text>')
    for tick in range(0, 25, 2):
        x = x_at(float(tick))
        lines.append(f'<line x1="{x:.2f}" y1="{top}" x2="{x:.2f}" y2="{height-bottom}" stroke="#29313d"/>')
        lines.append(f'<text x="{x:.2f}" y="{height-bottom+22}" text-anchor="middle" fill="#aeb7c4" font-family="sans-serif" font-size="11">{tick}</text>')
    for index in range(6):
        value = y_min + (y_max - y_min) * index / 5.0
        y = y_at(value)
        lines.append(f'<line x1="{left}" y1="{y:.2f}" x2="{width-right}" y2="{y:.2f}" stroke="#29313d"/>')
        lines.append(f'<text x="{left-8}" y="{y+4:.2f}" text-anchor="end" fill="#aeb7c4" font-family="sans-serif" font-size="11">{value:.3f}</text>')
    lines.extend((
        f'<polyline points="{points}" fill="none" stroke="#66f2a6" stroke-width="2"/>',
        f'<line x1="{left}" y1="{y_at(1.0):.2f}" x2="{width-right}" y2="{y_at(1.0):.2f}" stroke="#f3c969" stroke-width="1" stroke-dasharray="3 5"/>',
        f'<text x="{width/2:.1f}" y="{height-18}" text-anchor="middle" fill="#d7dde6" font-family="sans-serif" font-size="13">RPS at {float(rows[0]["fps"]):g} Hz</text>',
        f'<text x="18" y="{height/2:.1f}" transform="rotate(-90 18 {height/2:.1f})" text-anchor="middle" fill="#d7dde6" font-family="sans-serif" font-size="13">linearized excess luminance / stationary</text>',
        '</svg>',
    ))
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def encode_video(
    path: Path,
    frames_dir: Path,
    rows: list[dict[str, str]],
    normalized: list[float],
    video_fps: float,
) -> bool:
    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        return False
    ffprobe = shutil.which("ffprobe")
    if ffprobe is None:
        raise RuntimeError("ffprobe is required for verified RPM-sweep video export")
    with tempfile.TemporaryDirectory(prefix="wheel-rpm-sweep-") as temporary:
        annotated = Path(temporary)
        font = ImageFont.load_default()
        for index, row in enumerate(rows):
            with Image.open(frames_dir / f"frame-{index:05d}.ppm") as source:
                image = source.convert("RGB")
            overlay = Image.new("RGBA", image.size, (0, 0, 0, 0))
            draw = ImageDraw.Draw(overlay)
            draw.rectangle((0, 0, image.width, 28), fill=(0, 0, 0, 180))
            text = (
                f"{float(row['rps']):5.2f} rps | "
                f"{float(row['degrees_per_frame']):5.2f} deg/frame | "
                f"{float(row['projected_sweep_pixels']):4.2f}px | "
                f"E {normalized[index]:.4f}"
            )
            draw.text((7, 8), text, fill=(255, 255, 255, 255), font=font)
            Image.alpha_composite(image.convert("RGBA"), overlay).convert("RGB").save(
                annotated / f"frame-{index:05d}.png"
            )
        metadata = encode_verified_video(
            [
                ffmpeg,
                "-hide_banner", "-loglevel", "error", "-y",
                "-framerate", ffmpeg_rate(video_fps),
                "-i", str(annotated / "frame-%05d.png"),
                "-c:v", "libx264", "-crf", "16", "-preset", "slow",
                "-pix_fmt", "yuv420p",
                *cfr_output_arguments(video_fps),
            ],
            ffprobe,
            path,
            video_fps,
            len(rows),
        )
        print(
            f"Verified RPM-sweep video cadence: "
            f"{float(metadata.average_fps):g} fps, {metadata.frame_count} frames"
        )
    return True


def write_report(
    path: Path,
    rows: list[dict[str, str]],
    metrics: list[dict[str, float]],
    normalized: list[float],
    legacy_hump_percent: float | None,
) -> None:
    adjacent = [0.0] + [
        (normalized[index] - normalized[index - 1]) * 100.0
        for index in range(1, len(normalized))
    ]
    residual = [0.0] * len(normalized)
    for index in range(1, len(normalized) - 1):
        residual[index] = (
            normalized[index]
            - 0.5 * (normalized[index - 1] + normalized[index + 1])
        ) * 100.0
    largest_step = max(range(1, len(rows)), key=lambda index: abs(adjacent[index]))
    largest_residual = max(
        range(1, len(rows) - 1), key=lambda index: abs(residual[index])
    )
    local_extrema = []
    for index in range(1, len(rows) - 1):
        before = normalized[index] - normalized[index - 1]
        after = normalized[index + 1] - normalized[index]
        if before * after < 0.0:
            local_extrema.append(index)
    activation = [
        index for index, row in enumerate(rows)
        if 0.45 <= float(row["projected_sweep_pixels"]) <= 2.55
    ]
    band = [
        index for index, row in enumerate(rows)
        if 7.8 <= float(row["degrees_per_frame"]) <= 12.2
    ]

    def range_text(indices: list[int]) -> str:
        values = [normalized[index] for index in indices]
        return f"{min(values):.6f}–{max(values):.6f}" if values else "n/a"

    first_active = next(
        (index for index, row in enumerate(rows) if row["temporal_active"] == "1"),
        0,
    )
    current_hump_percent = (max(normalized) - 1.0) * 100.0
    lines = [
        "# Band-mode RPM sweep luminance report",
        "",
        f"- Sweep: `{rows[0]['rps']}` to `{rows[-1]['rps']}` rps in `{len(rows)}` fixed-pose frames at `{rows[0]['fps']}` Hz.",
        f"- Bloom correction: `{rows[0]['bloom_correction']}` with emitter factor `{float(rows[0]['emission_bright_factor']):.6f}`.",
        "- Residual continuity uses only the 0.5–2.5 px projected-motion smoothstep; band blend and hysteresis are excluded.",
        "- Energy is Rec.709 luminance after sRGB linearization, with border background removed, normalized to the stationary sharp frame.",
        "- This is an LDR screenshot proxy; framebuffer clipping means it is not a physical HDR-energy measurement.",
        f"- Overall normalized energy range: `{min(normalized):.6f}`–`{max(normalized):.6f}`.",
        f"- Peak above stationary: `{current_hump_percent:.4f}%`.",
        f"- First-active-frame step: `{adjacent[first_active]:+.6f}%` at `{float(rows[first_active]['rps']):.3f}` rps (correction blend `{float(rows[first_active]['bloom_correction_blend']):.6f}`).",
        f"- 0.5–2.5 px activation-region range: `{range_text(activation)}`.",
        f"- 8–12 deg/frame band-region range: `{range_text(band)}`.",
        f"- Largest adjacent step: `{adjacent[largest_step]:+.6f}%` at `{float(rows[largest_step]['rps']):.3f}` rps.",
        f"- Largest one-frame interpolation residual: `{residual[largest_residual]:+.6f}%` at `{float(rows[largest_residual]['rps']):.3f}` rps.",
        f"- Maximum clipped-pixel fraction: `{max(item['clipped_pixel_fraction'] for item in metrics):.6%}`.",
        f"- Detected local extrema: `{len(local_extrema)}`.",
        "",
        "## Largest local extrema",
        "",
        "| RPS | sweep px | deg/frame | temporal blend | correction blend | band blend | normalized energy | residual |",
        "|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    if legacy_hump_percent is not None:
        lines.insert(
            7,
            f"- Previous global-scalar hump: `{legacy_hump_percent:.4f}%`; peak reduction: `{legacy_hump_percent - current_hump_percent:.4f}` percentage points.",
        )
    ranked = sorted(local_extrema, key=lambda index: abs(residual[index]), reverse=True)[:12]
    if not ranked:
        lines.append("| — | — | — | — | — | — | — | no local extrema |")
    for index in ranked:
        row = rows[index]
        lines.append(
            f"| {float(row['rps']):.3f} | {float(row['projected_sweep_pixels']):.3f} | "
            f"{float(row['degrees_per_frame']):.3f} | {float(row['temporal_blend']):.4f} | "
            f"{float(row['bloom_correction_blend']):.4f} | "
            f"{float(row['band_blend']):.4f} | {normalized[index]:.6f} | "
            f"{residual[index]:+.6f}% |"
        )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    args = parse_args()
    if args.frames < 3:
        raise SystemExit("--frames must be at least 3")
    try:
        require_supported_sequence_frame_count(args.frames)
    except ValueError as error:
        raise SystemExit(str(error)) from error
    if not args.binary.is_file():
        raise SystemExit(f"missing renderer binary: {args.binary}")
    video_fps = args.video_fps
    try:
        require_finite_positive_fps(args.fps)
        require_finite_positive_fps(video_fps)
    except ValueError as error:
        raise SystemExit("--fps/--video-fps must be finite and positive") from error
    args.output_dir.mkdir(parents=True, exist_ok=True)
    frames_dir = args.output_dir / "frames"
    frames_dir.mkdir(parents=True, exist_ok=True)
    if not args.reuse:
        prepare_ppm_sequence_output(frames_dir, args.frames)
        render(args, frames_dir)

    rows = read_manifest(frames_dir / "manifest.tsv")
    if len(rows) != args.frames:
        raise SystemExit(f"manifest has {len(rows)} rows, expected {args.frames}")
    try:
        capture_fps = manifest_capture_fps(rows)
        if args.reuse:
            video_fps = resolve_reuse_video_fps(args, rows)
        elif not math.isclose(
            capture_fps, args.fps, rel_tol=0.0, abs_tol=1e-6
        ):
            raise ValueError(
                f"rendered manifest cadence is {capture_fps:g} fps; "
                f"expected {args.fps:g} fps"
            )
    except ValueError as error:
        raise SystemExit(str(error)) from error
    try:
        contiguous_ppm_frame_count(frames_dir, len(rows))
    except ValueError as error:
        raise SystemExit(str(error)) from error
    frame_paths = [frames_dir / f"frame-{index:05d}.ppm" for index in range(len(rows))]

    metrics = [image_metrics(path) for path in frame_paths]
    normalized = write_metrics(
        args.output_dir / "luminance.tsv", rows, metrics
    )
    build_contact_sheet(
        args.output_dir / "contact-sheet.png", frames_dir, rows, normalized
    )
    write_energy_svg(args.output_dir / "energy-curve.svg", rows, normalized)
    write_report(
        args.output_dir / "report.md",
        rows,
        metrics,
        normalized,
        args.legacy_hump_percent,
    )
    video_created = encode_video(
        args.output_dir / "rpm-sweep.mp4",
        frames_dir,
        rows,
        normalized,
        video_fps,
    )

    if not args.keep_frames:
        for frame_path in frame_paths:
            frame_path.unlink()
    print(f"Report: {args.output_dir / 'report.md'}")
    print(f"Contact sheet: {args.output_dir / 'contact-sheet.png'}")
    print(f"Energy curve: {args.output_dir / 'energy-curve.svg'}")
    if video_created:
        print(f"Video: {args.output_dir / 'rpm-sweep.mp4'}")


if __name__ == "__main__":
    main()
