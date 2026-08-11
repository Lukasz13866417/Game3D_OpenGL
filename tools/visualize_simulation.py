#!/usr/bin/env python3
"""Render a Game3D simulator NDJSON trace as a self-contained side-view SVG."""

from __future__ import annotations

import argparse
import html
import json
import math
from pathlib import Path
from typing import Any, Callable, Iterable, Sequence


Point3 = tuple[float, float, float]
ProjectedPoint = tuple[float, float]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Create a side-view SVG showing authoritative terrain, the player-center "
            "trajectory, and jump moments."
        )
    )
    parser.add_argument("trace", type=Path, help="Simulator NDJSON trace")
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        help="Output SVG (default: TRACE.side.svg)",
    )
    parser.add_argument(
        "--horizontal",
        choices=("auto", "x", "z", "distance"),
        default="auto",
        help=(
            "Horizontal projection. auto follows start-to-end travel; distance uses "
            "cumulative player travel and is useful for curved tracks."
        ),
    )
    parser.add_argument(
        "--vertical",
        choices=("y", "x", "z"),
        default="y",
        help=(
            "Vertical projection (default: world Y side view). Use X with "
            "--horizontal z for a top/lateral redirect diagnostic."
        ),
    )
    parser.add_argument("--width", type=int, default=1600, help="SVG width in pixels")
    parser.add_argument("--height", type=int, default=900, help="SVG height in pixels")
    parser.add_argument(
        "--focus-traveled",
        action="store_true",
        help="Hide terrain far outside the horizontal range traversed by the player",
    )
    parser.add_argument(
        "--samples",
        action="store_true",
        help="Draw individual 120 Hz player-center samples on top of the curve",
    )
    parser.add_argument(
        "--solver-debug",
        action="store_true",
        help="Draw attempted-endpoint to resolved-contact vectors from contact traces",
    )
    parser.add_argument(
        "--spin-debug",
        action="store_true",
        help=(
            "Draw sampled tire-phase glyphs and axle spokes with per-sample speed/slip "
            "diagnostics (schema 6+ traces)"
        ),
    )
    parser.add_argument("--title", help="Custom chart title")
    return parser.parse_args()


def read_trace(path: Path) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    header: dict[str, Any] | None = None
    ticks: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as source:
        for line_number, line in enumerate(source, 1):
            if not line.strip():
                continue
            try:
                item = json.loads(line)
            except json.JSONDecodeError as error:
                raise SystemExit(f"{path}:{line_number}: invalid JSON: {error}") from error
            if item.get("type") == "header":
                header = item
            elif item.get("type") == "tick":
                ticks.append(item)
    if header is None:
        raise SystemExit(f"{path}: trace header is missing")
    if not ticks:
        raise SystemExit(f"{path}: trace contains no ticks")
    return header, ticks


def point3(value: Sequence[Any]) -> Point3:
    return float(value[0]), float(value[1]), float(value[2])


def terrain_triangles(
    header: dict[str, Any], ticks: Sequence[dict[str, Any]]
) -> list[dict[str, Any]]:
    segments = historical_terrain_segments(header, ticks)
    if segments:
        triangles: list[dict[str, Any]] = []
        for segment_id in sorted(segments):
            segment = segments[segment_id]
            if not segment.get("solid", True):
                continue
            near_left = segment["nearLeft"]
            near_right = segment["nearRight"]
            far_left = segment["farLeft"]
            far_right = segment["farRight"]
            surface_kind = str(segment.get("surfaceKind", "NORMAL"))
            material = "NORMAL" if surface_kind == "NORMAL" else "BOOST"
            common = {
                "material": material,
                "surfaceKind": surface_kind,
                "ownerSegmentId": segment_id,
            }
            triangles.append(
                {
                    **common,
                    "id": segment_id * 2,
                    "a": near_left,
                    "b": near_right,
                    "c": far_right,
                }
            )
            triangles.append(
                {
                    **common,
                    "id": segment_id * 2 + 1,
                    "a": near_left,
                    "b": far_right,
                    "c": far_left,
                }
            )
        if triangles:
            return triangles

    triangles = header.get("terrainTriangles")
    if triangles:
        return list(triangles)

    # Compatibility with schema-1 traces: contact/full records include nearby triangles.
    by_id: dict[int, dict[str, Any]] = {}
    for tick in ticks:
        for triangle in tick.get("queriedTriangles", []):
            by_id[int(triangle["id"])] = triangle
    if not by_id:
        raise SystemExit(
            "This trace has no terrain geometry. Generate a new trace, or use "
            "--trace contacts/full with the older simulator."
        )
    return [by_id[key] for key in sorted(by_id)]


def historical_terrain_segments(
    header: dict[str, Any], ticks: Sequence[dict[str, Any]]
) -> dict[int, dict[str, Any]]:
    """Return every canonical segment that existed during the recorded run.

    The visualization describes the entire gameplay flow, so retired segments remain
    visible behind the player. A later upsert of an existing ID replaces its geometry.
    """
    by_id: dict[int, dict[str, Any]] = {
        int(segment["id"]): segment
        for segment in header.get("terrainSegments", [])
    }
    for tick in ticks:
        for commit in tick.get("appliedTerrainCommits", []):
            for segment in commit.get("segmentUpserts", []):
                by_id[int(segment["id"])] = segment
    return by_id


def terrain_features(
    header: dict[str, Any], ticks: Sequence[dict[str, Any]]
) -> list[dict[str, Any]]:
    segments = historical_terrain_segments(header, ticks)
    if not segments:
        return list(header.get("terrainFeatures", []))
    by_id: dict[int, dict[str, Any]] = {}
    for segment_id in sorted(segments):
        for feature in segments[segment_id].get("features", []):
            by_id[int(feature["id"])] = feature
    return [by_id[feature_id] for feature_id in sorted(by_id)]


def player_points(ticks: Sequence[dict[str, Any]]) -> list[Point3]:
    # Schema 5+ explicitly records the authoritative resolved path. Events decorate that path and
    # must never inject attempted/depenetrated solver poses into it.
    points = [point3(ticks[0]["before"]["absolutePosition"])]
    for tick in ticks:
        motion_segments = tick.get("motionSegments", [])
        if motion_segments:
            ordered_segments = sorted(
                motion_segments,
                key=lambda segment: (
                    float(segment.get("startFraction", 0.0)),
                    float(segment.get("endFraction", 0.0)),
                ),
            )
            for segment in ordered_segments:
                start = point3(segment["startPosition"])
                end = point3(segment["endPosition"])
                if start != points[-1]:
                    points.append(start)
                if end != points[-1]:
                    points.append(end)
            after = point3(tick["after"]["absolutePosition"])
            if after != points[-1]:
                points.append(after)
            continue

        # Compatibility with schema 1-4 traces, where fractional event poses were the only
        # available within-tick samples.
        timed_events = sorted(
            (
                event
                for event in tick.get("events", [])
                if "position" in event
                and 0.0 < float(event.get("tickFraction", 0.0)) <= 1.0
            ),
            key=lambda event: float(event["tickFraction"]),
        )
        for event in timed_events:
            event_point = point3(event["position"])
            if event_point != points[-1]:
                points.append(event_point)
        after = point3(tick["after"]["absolutePosition"])
        if after != points[-1]:
            points.append(after)
    return points


def tick_player_points(tick: dict[str, Any]) -> list[Point3]:
    """Return one tick's authoritative resolved path, including contact-time bends."""
    before = point3(tick["before"]["absolutePosition"])
    after = point3(tick["after"]["absolutePosition"])
    points = [before]
    motion_segments = sorted(
        tick.get("motionSegments", []),
        key=lambda segment: (
            float(segment.get("startFraction", 0.0)),
            float(segment.get("endFraction", 0.0)),
        ),
    )
    for segment in motion_segments:
        start = point3(segment["startPosition"])
        end = point3(segment["endPosition"])
        if start != points[-1]:
            points.append(start)
        if end != points[-1]:
            points.append(end)
    if after != points[-1]:
        points.append(after)
    return points


def vertical_projector(mode: str) -> Callable[[Point3], float]:
    if mode == "x":
        return lambda point: point[0]
    if mode == "z":
        return lambda point: -point[2]
    return lambda point: point[1]


def clip_polygon_horizontal(
    polygon: Sequence[ProjectedPoint], low: float, high: float
) -> list[ProjectedPoint]:
    def clip_edge(
        points: Sequence[ProjectedPoint], boundary: float, keep_greater: bool
    ) -> list[ProjectedPoint]:
        if not points:
            return []
        result: list[ProjectedPoint] = []
        previous = points[-1]
        previous_inside = (
            previous[0] >= boundary if keep_greater else previous[0] <= boundary
        )
        for current in points:
            current_inside = (
                current[0] >= boundary if keep_greater else current[0] <= boundary
            )
            if current_inside != previous_inside:
                dx = current[0] - previous[0]
                alpha = 0.0 if abs(dx) < 1.0e-15 else (
                    boundary - previous[0]
                ) / dx
                result.append(
                    (
                        boundary,
                        previous[1] + alpha * (current[1] - previous[1]),
                    )
                )
            if current_inside:
                result.append(current)
            previous = current
            previous_inside = current_inside
        return result

    return clip_edge(clip_edge(polygon, low, True), high, False)


def horizontal_projector(
    mode: str, path: Sequence[Point3]
) -> Callable[[Point3], float]:
    if mode == "x":
        return lambda point: point[0]
    if mode == "z":
        return lambda point: -point[2]
    if mode == "distance":
        return cumulative_path_projector(path)

    start = path[0]
    end = path[-1]
    axis_x = end[0] - start[0]
    axis_z = end[2] - start[2]
    axis_length = math.hypot(axis_x, axis_z)
    if axis_length < 1.0e-9:
        for point in path[1:]:
            axis_x = point[0] - start[0]
            axis_z = point[2] - start[2]
            axis_length = math.hypot(axis_x, axis_z)
            if axis_length >= 1.0e-9:
                break
    if axis_length < 1.0e-9:
        axis_x, axis_z, axis_length = 0.0, -1.0, 1.0
    axis_x /= axis_length
    axis_z /= axis_length
    return lambda point: (point[0] - start[0]) * axis_x + (
        point[2] - start[2]
    ) * axis_z


def cumulative_path_projector(path: Sequence[Point3]) -> Callable[[Point3], float]:
    segments: list[tuple[float, float, float, float, float, float]] = []
    cumulative = 0.0
    for start, end in zip(path, path[1:]):
        dx = end[0] - start[0]
        dz = end[2] - start[2]
        length_squared = dx * dx + dz * dz
        if length_squared < 1.0e-14:
            continue
        length = math.sqrt(length_squared)
        segments.append((start[0], start[2], dx, dz, length_squared, cumulative))
        cumulative += length
    if not segments:
        return lambda point: 0.0

    def project(point: Point3) -> float:
        best_distance_squared = math.inf
        best_progress = 0.0
        px, pz = point[0], point[2]
        for sx, sz, dx, dz, length_squared, progress in segments:
            alpha = ((px - sx) * dx + (pz - sz) * dz) / length_squared
            alpha = max(0.0, min(1.0, alpha))
            nearest_x = sx + alpha * dx
            nearest_z = sz + alpha * dz
            distance_squared = (px - nearest_x) ** 2 + (pz - nearest_z) ** 2
            if distance_squared < best_distance_squared:
                best_distance_squared = distance_squared
                best_progress = progress + alpha * math.sqrt(length_squared)
        return best_progress

    return project


def nice_ticks(low: float, high: float, count: int = 8) -> list[float]:
    span = max(1.0e-9, high - low)
    rough = span / max(1, count)
    magnitude = 10.0 ** math.floor(math.log10(rough))
    normalized = rough / magnitude
    if normalized <= 1.0:
        step = magnitude
    elif normalized <= 2.0:
        step = 2.0 * magnitude
    elif normalized <= 5.0:
        step = 5.0 * magnitude
    else:
        step = 10.0 * magnitude
    first = math.ceil(low / step) * step
    values: list[float] = []
    current = first
    while current <= high + step * 1.0e-9:
        values.append(current)
        current += step
    return values


def bounds(values: Iterable[float]) -> tuple[float, float]:
    materialized = list(values)
    low = min(materialized)
    high = max(materialized)
    if high - low < 1.0e-9:
        return low - 0.5, high + 0.5
    padding = (high - low) * 0.04
    return low - padding, high + padding


def svg_escape(value: Any) -> str:
    return html.escape(str(value), quote=True)


def format_number(value: float) -> str:
    if abs(value) >= 100.0:
        return f"{value:.0f}"
    if abs(value) >= 10.0:
        return f"{value:.1f}"
    return f"{value:.2f}"


def build_svg(
    header: dict[str, Any],
    ticks: Sequence[dict[str, Any]],
    triangles: Sequence[dict[str, Any]],
    *,
    width: int,
    height: int,
    horizontal_mode: str,
    vertical_mode: str,
    focus_traveled: bool,
    show_samples: bool,
    title: str | None,
    show_solver_debug: bool = False,
    show_spin_debug: bool = False,
) -> str:
    if width < 500 or height < 350:
        raise SystemExit("SVG dimensions must be at least 500x350")

    path = player_points(ticks)
    project = horizontal_projector(horizontal_mode, path)
    project_vertical = vertical_projector(vertical_mode)
    projected_path = [(project(point), project_vertical(point)) for point in path]
    projected_triangles: list[tuple[list[ProjectedPoint], str]] = []
    for triangle in triangles:
        projected_triangles.append(
            (
                [
                    (
                        project(point3(triangle["a"])),
                        project_vertical(point3(triangle["a"])),
                    ),
                    (
                        project(point3(triangle["b"])),
                        project_vertical(point3(triangle["b"])),
                    ),
                    (
                        project(point3(triangle["c"])),
                        project_vertical(point3(triangle["c"])),
                    ),
                ],
                str(triangle.get("material", "NORMAL")),
            )
        )

    traveled_low = min(point[0] for point in projected_path)
    traveled_high = max(point[0] for point in projected_path)
    traveled_padding = max(1.0, (traveled_high - traveled_low) * 0.08)
    if focus_traveled:
        clip_low = traveled_low - traveled_padding
        clip_high = traveled_high + traveled_padding
        clipped_triangles: list[tuple[list[ProjectedPoint], str]] = []
        for triangle, material in projected_triangles:
            clipped = clip_polygon_horizontal(triangle, clip_low, clip_high)
            if len(clipped) >= 2:
                clipped_triangles.append((clipped, material))
        projected_triangles = clipped_triangles

    all_x = [point[0] for point in projected_path]
    all_y = [point[1] for point in projected_path]
    for triangle, _material in projected_triangles:
        all_x.extend(point[0] for point in triangle)
        all_y.extend(point[1] for point in triangle)

    features = terrain_features(header, ticks)
    projected_features: list[tuple[dict[str, Any], float, float]] = []
    for feature in features:
        center = point3(feature["center"])
        feature_x = project(center)
        if focus_traveled and not (
            traveled_low - traveled_padding <= feature_x <= traveled_high + traveled_padding
        ):
            continue
        feature_vertical = project_vertical(center)
        projected_features.append((feature, feature_x, feature_vertical))
        all_x.append(feature_x)
        if vertical_mode == "y":
            all_y.append(feature_vertical + float(feature.get("height", 0.0)))
        else:
            radius = float(
                feature.get("radius", feature.get("triggerRadius", 0.0))
            )
            all_y.extend((feature_vertical - radius, feature_vertical + radius))

    min_x, max_x = bounds(all_x)
    min_y, max_y = bounds(all_y)
    left, right, top, bottom = 92.0, 35.0, 126.0, 72.0
    plot_width = width - left - right
    plot_height = height - top - bottom

    def sx(value: float) -> float:
        return left + (value - min_x) / (max_x - min_x) * plot_width

    def sy(value: float) -> float:
        return top + (max_y - value) / (max_y - min_y) * plot_height

    scenario = header.get("scenario", "simulation")
    view_name = "side-view" if vertical_mode == "y" else "projected"
    chart_title = title or f"{scenario} — {view_name} simulation"
    duration_ms = float(
        ticks[-1]["after"].get("timeNanos", ticks[-1].get("timeNanos", 0))
    ) / 1_000_000.0
    fixed_hz = header.get("fixedHz", "?")

    svg: list[str] = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        (
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" '
            f'height="{height}" viewBox="0 0 {width} {height}" role="img" '
            f'aria-label="{svg_escape(chart_title)}">'
        ),
        "<defs>",
        (
            '<linearGradient id="pathGradient" x1="0%" y1="0%" x2="100%" y2="0%">'
            '<stop offset="0%" stop-color="#42e695"/>'
            '<stop offset="45%" stop-color="#57b8ff"/>'
            '<stop offset="100%" stop-color="#b57cff"/>'
            "</linearGradient>"
        ),
        (
            f'<clipPath id="plotClip"><rect x="{left:.2f}" y="{top:.2f}" '
            f'width="{plot_width:.2f}" height="{plot_height:.2f}"/></clipPath>'
        ),
        "</defs>",
        f'<rect width="{width}" height="{height}" fill="#0c1118"/>',
        (
            f'<text x="{left:.2f}" y="38" fill="#f1f6fb" '
            f'font-family="system-ui,sans-serif" font-size="24" font-weight="700">'
            f"{svg_escape(chart_title)}</text>"
        ),
        (
            f'<text x="{left:.2f}" y="65" fill="#9bacbd" '
            f'font-family="system-ui,sans-serif" font-size="13">'
            f"{fixed_hz} Hz · {len(ticks)} ticks · {duration_ms:.1f} ms · "
            f"projection={svg_escape(horizontal_mode)}/{svg_escape(vertical_mode)}</text>"
        ),
        (
            f'<rect x="{left:.2f}" y="{top:.2f}" width="{plot_width:.2f}" '
            f'height="{plot_height:.2f}" fill="#111a24" stroke="#2b3948"/>'
        ),
    ]

    for tick_value in nice_ticks(min_x, max_x):
        pixel = sx(tick_value)
        svg.append(
            f'<line x1="{pixel:.2f}" y1="{top:.2f}" x2="{pixel:.2f}" '
            f'y2="{top + plot_height:.2f}" stroke="#22303d" stroke-width="1"/>'
        )
        svg.append(
            f'<text x="{pixel:.2f}" y="{top + plot_height + 25:.2f}" '
            f'fill="#8fa1b3" text-anchor="middle" font-family="monospace" '
            f'font-size="12">{svg_escape(format_number(tick_value))}</text>'
        )
    for tick_value in nice_ticks(min_y, max_y):
        pixel = sy(tick_value)
        svg.append(
            f'<line x1="{left:.2f}" y1="{pixel:.2f}" '
            f'x2="{left + plot_width:.2f}" y2="{pixel:.2f}" '
            f'stroke="#22303d" stroke-width="1"/>'
        )
        svg.append(
            f'<text x="{left - 12:.2f}" y="{pixel + 4:.2f}" fill="#8fa1b3" '
            f'text-anchor="end" font-family="monospace" font-size="12">'
            f"{svg_escape(format_number(tick_value))}</text>"
        )

    svg.append(
        f'<g clip-path="url(#plotClip)" id="terrain" '
        f'fill-opacity="0.60" stroke-opacity="0.78" stroke-width="1">'
    )
    for triangle, material in projected_triangles:
        points = " ".join(f"{sx(x):.2f},{sy(y):.2f}" for x, y in triangle)
        if material == "BOOST":
            fill, stroke = "#7a612f", "#f1c45b"
        else:
            fill, stroke = "#42624b", "#83c88e"
        svg.append(
            f'<polygon points="{points}" fill="{fill}" stroke="{stroke}">'
            f"<title>{svg_escape(material)} terrain</title></polygon>"
        )
    svg.append("</g>")

    svg.append('<g clip-path="url(#plotClip)" id="features">')
    for feature, feature_x, feature_y in projected_features:
        center_x = sx(feature_x)
        if feature.get("kind") == "SPIKE":
            spike_height = float(feature.get("height", 1.0))
            radius = max(float(feature.get("radius", 0.2)), (max_x - min_x) * 0.002)
            if vertical_mode == "y":
                points = (
                    f"{sx(feature_x - radius):.2f},{sy(feature_y):.2f} "
                    f"{center_x:.2f},{sy(feature_y + spike_height):.2f} "
                    f"{sx(feature_x + radius):.2f},{sy(feature_y):.2f}"
                )
                svg.append(
                    f'<polygon points="{points}" fill="#ff6b6b" '
                    f'fill-opacity="0.75" stroke="#ffb0b0"><title>Spike '
                    f'{feature.get("id")}</title></polygon>'
                )
            else:
                radius_y = abs(sy(feature_y + radius) - sy(feature_y))
                radius_x = abs(sx(feature_x + radius) - center_x)
                svg.append(
                    f'<ellipse cx="{center_x:.2f}" cy="{sy(feature_y):.2f}" '
                    f'rx="{radius_x:.2f}" ry="{radius_y:.2f}" '
                    f'fill="#ff6b6b" fill-opacity="0.45" stroke="#ffb0b0">'
                    f'<title>Spike {feature.get("id")} footprint</title></ellipse>'
                )
        elif feature.get("kind") == "FEATHER":
            trigger_radius = float(feature.get("triggerRadius", 0.0))
            if trigger_radius > 0.0:
                halo_rx = abs(sx(feature_x + trigger_radius) - center_x)
                halo_ry = abs(sy(feature_y + trigger_radius) - sy(feature_y))
                svg.append(
                    f'<ellipse cx="{center_x:.2f}" cy="{sy(feature_y):.2f}" '
                    f'rx="{halo_rx:.2f}" ry="{halo_ry:.2f}" '
                    f'fill="#ffe47a" fill-opacity="0.06" stroke="#ffe47a" '
                    f'stroke-opacity="0.45" stroke-dasharray="4 3">'
                    f"<title>Feather {feature.get('id')} trigger radius "
                    f"{trigger_radius:g}</title></ellipse>"
                )
            svg.append(
                f'<circle cx="{center_x:.2f}" cy="{sy(feature_y):.2f}" r="6" '
                f'fill="#ffe47a" stroke="#fff3b4"><title>Feather '
                f'{feature.get("id")}</title></circle>'
            )
    svg.append("</g>")

    path_points = " ".join(f"{sx(x):.2f},{sy(y):.2f}" for x, y in projected_path)
    svg.append(
        f'<polyline points="{path_points}" fill="none" stroke="#071019" '
        f'stroke-width="6.5" stroke-linecap="round" stroke-linejoin="round" '
        f'clip-path="url(#plotClip)"/>'
    )
    svg.append(
        f'<polyline points="{path_points}" fill="none" stroke="url(#pathGradient)" '
        f'stroke-width="3" stroke-linecap="round" stroke-linejoin="round" '
        f'clip-path="url(#plotClip)"/>'
    )

    # These overlays expose the two input states that deliberately survive across ticks. They are
    # dashed and translucent so the authoritative center path remains visible underneath them.
    armed_states = (
        (
            "landingJumpArmed",
            "#62d5ff",
            "7 4",
            "landing jump buffer armed",
        ),
        (
            "impactBrakeArmed",
            "#48e0b5",
            "2 4",
            "impact brake armed",
        ),
    )
    svg.append(
        '<g id="armedStatePaths" clip-path="url(#plotClip)" '
        'fill="none" stroke-linecap="round" stroke-width="7">'
    )
    for field, color, dash, description in armed_states:
        for tick in ticks:
            before_armed = bool(tick["before"].get(field, False))
            after_armed = bool(tick["after"].get(field, False))
            if not (before_armed or after_armed):
                continue
            state_points = " ".join(
                f"{sx(project(item)):.2f},{sy(project_vertical(item)):.2f}"
                for item in tick_player_points(tick)
            )
            svg.append(
                f'<polyline points="{state_points}" stroke="{color}" '
                f'stroke-opacity="0.48" stroke-dasharray="{dash}" '
                f'data-state="{field}"><title>{svg_escape(description)} '
                f'at tick {tick.get("tick")}</title></polyline>'
            )
    svg.append("</g>")

    if show_spin_debug:
        radius = float(header.get("cylinderRadius", 0.0))
        if radius <= 0.0:
            raise SystemExit(
                "--spin-debug requires a schema 6+ trace with cylinderRadius"
            )
        stride = max(1, len(ticks) // 72)
        sample_indexes = set(range(stride - 1, len(ticks), stride))
        sample_indexes.add(len(ticks) - 1)
        for index, tick in enumerate(ticks):
            if tick.get("events") or any(
                segment.get("mode") == "LANDING_SNAP"
                for segment in tick.get("spinSegments", [])
            ):
                sample_indexes.add(index)

        # Use constant-size phase glyphs: independent chart-axis scaling can otherwise turn a
        # physically circular tire into a tall, unreadable ellipse on long-track plots.
        glyph_radius = 7.0
        svg.append(
            '<g id="spinDebug" clip-path="url(#plotClip)" '
            'font-family="monospace">'
        )
        for index in sorted(sample_indexes):
            tick = ticks[index]
            player = tick["after"]
            position = point3(player["absolutePosition"])
            center_x = sx(project(position))
            center_y = sy(project_vertical(position))
            axle = float(player.get("axleRadians", 0.0))
            omega = float(player.get("angularVelocity", 0.0))
            rim_speed = -omega * radius
            tangent_speed = math.nan
            support_normal = player.get("supportNormal")
            axis = player.get("cylinderAxis")
            heading = player.get("heading")
            if player.get("grounded") and support_normal and axis and heading:
                nx, ny, nz = point3(support_normal)
                ax, ay, az = point3(axis)
                hx, hy, hz = point3(heading)
                tangent = (
                    ny * az - nz * ay,
                    nz * ax - nx * az,
                    nx * ay - ny * ax,
                )
                tangent_length = math.sqrt(sum(value * value for value in tangent))
                if tangent_length > 1.0e-12:
                    tangent = tuple(value / tangent_length for value in tangent)
                    if sum(a * b for a, b in zip(tangent, (hx, hy, hz))) < 0.0:
                        tangent = tuple(-value for value in tangent)
                    velocity = point3(player["velocity"])
                    tangent_speed = sum(
                        value * direction
                        for value, direction in zip(velocity, tangent)
                    )
            slip = rim_speed - tangent_speed
            spoke_x = center_x + math.sin(axle) * glyph_radius
            spoke_y = center_y - math.cos(axle) * glyph_radius
            tooltip = (
                f"tick {tick.get('tick')}; phase={axle:.6f} rad; "
                f"delta={float(player.get('axleDeltaRadians', 0.0)):.6f} rad; "
                f"omega={omega:.6f} rad/s; rim={rim_speed:.6f}"
            )
            if math.isfinite(tangent_speed):
                tooltip += (
                    f"; tangent={tangent_speed:.6f}; slip={slip:.9f}"
                )
            svg.append(
                f'<circle cx="{center_x:.2f}" cy="{center_y:.2f}" '
                f'r="{glyph_radius:.2f}" '
                'fill="#10212a" fill-opacity="0.20" stroke="#d4f8ff" '
                'stroke-opacity="0.45" stroke-width="0.8"/>'
            )
            svg.append(
                f'<line x1="{center_x:.2f}" y1="{center_y:.2f}" '
                f'x2="{spoke_x:.2f}" y2="{spoke_y:.2f}" '
                'stroke="#ff74bd" stroke-width="1.4">'
                f"<title>{svg_escape(tooltip)}</title></line>"
            )
        svg.append("</g>")

    if show_solver_debug:
        svg.append(
            '<g id="solverDebug" stroke="#ff5c67" stroke-width="1.5" '
            'stroke-dasharray="5 4" fill="none" clip-path="url(#plotClip)">'
        )
        for tick in ticks:
            for contact in tick.get("contacts", []):
                if "detectedCenter" not in contact or "resolvedCenter" not in contact:
                    continue
                detected = point3(contact["detectedCenter"])
                resolved = point3(contact["resolvedCenter"])
                svg.append(
                    f'<line x1="{sx(project(detected)):.2f}" '
                    f'y1="{sy(project_vertical(detected)):.2f}" '
                    f'x2="{sx(project(resolved)):.2f}" '
                    f'y2="{sy(project_vertical(resolved)):.2f}">'
                    f'<title>solver probe triangle {contact.get("triangleId")} '
                    f'({svg_escape(contact.get("timingQuality", "unknown"))})'
                    f"</title></line>"
                )
        svg.append("</g>")

    if show_samples:
        stride = max(1, len(projected_path) // 1500)
        svg.append('<g fill="#cce8ff" fill-opacity="0.48" clip-path="url(#plotClip)">')
        for x_value, y_value in projected_path[::stride]:
            svg.append(
                f'<circle cx="{sx(x_value):.2f}" cy="{sy(y_value):.2f}" r="1.2"/>'
            )
        svg.append("</g>")

    # Input is resolution-independent in the trace, so draw it as constant-pixel glyphs rather
    # than scaling it with world coordinates. The four stable offsets keep a down/swipe/up trio
    # legible even when Android delivered all of it to the same fixed tick.
    input_offsets = {
        "TOUCH_DOWN": (-40.0, -30.0),
        "SWIPE_UP": (0.0, -48.0),
        "SWIPE_DOWN": (0.0, 48.0),
        "TOUCH_UP": (40.0, -30.0),
        "CANCEL_GESTURE": (40.0, 30.0),
    }
    repeated_input_counts: dict[tuple[int, str], int] = {}
    svg.append('<g id="inputMarkers" font-family="system-ui,sans-serif">')
    for tick_index, tick in enumerate(ticks):
        event_position = point3(tick["before"]["absolutePosition"])
        event_x = sx(project(event_position))
        event_y = sy(project_vertical(event_position))
        for input_event in tick.get("inputs", []):
            input_type = str(input_event.get("type", ""))
            marker_kind = input_type
            dx = float(input_event.get("dxScreenHeights", 0.0))
            dy = float(input_event.get("dyScreenHeights", 0.0))
            raw_dx = float(input_event.get("rawDxScreenHeights", dx))
            raw_dy = float(input_event.get("rawDyScreenHeights", dy))
            if input_type == "SWIPE":
                if abs(dy) < 1.0e-15:
                    continue
                marker_kind = "SWIPE_UP" if dy < 0.0 else "SWIPE_DOWN"
            if marker_kind not in input_offsets:
                continue
            repeat_key = (tick_index, marker_kind)
            repeat = repeated_input_counts.get(repeat_key, 0)
            repeated_input_counts[repeat_key] = repeat + 1
            offset_x, offset_y = input_offsets[marker_kind]
            # Repeated move packets of the same sign fan out horizontally.
            offset_x += (repeat + 1) // 2 * (14.0 if repeat % 2 == 0 else -14.0)
            marker_x = max(
                left + 12.0,
                min(left + plot_width - 12.0, event_x + offset_x),
            )
            marker_y = max(
                top + 12.0,
                min(top + plot_height - 22.0, event_y + offset_y),
            )
            fallback_time = tick["before"].get("timeNanos", tick.get("timeNanos", 0))
            time_ms = float(input_event.get("timeNanos", fallback_time)) / 1_000_000.0
            charge_status = ""
            charge_path_known = "jumpChargePathEligible" in tick.get("after", {})
            charge_path_blocked = False
            if marker_kind == "TOUCH_DOWN":
                color, label = "#d7a7ff", "PRESS"
                detail = f"TOUCH_DOWN at {time_ms:.3f} ms"
            elif marker_kind == "TOUCH_UP":
                color, label = "#f0c5ff", "RELEASE"
                detail = f"TOUCH_UP at {time_ms:.3f} ms"
            elif marker_kind == "CANCEL_GESTURE":
                color, label = "#ff7f9b", "CANCEL"
                detail = f"CANCEL_GESTURE at {time_ms:.3f} ms"
            elif marker_kind == "SWIPE_UP":
                after_state = tick.get("after", {})
                potential = float(after_state.get("gestureChargePotential", 0.0))
                charge_path_blocked = (
                    charge_path_known
                    and potential > 1.0e-12
                    and not bool(after_state.get("jumpChargePathEligible", False))
                )
                if charge_path_blocked:
                    color, label = "#ff8f70", "BLOCKED"
                    charge_status = ' data-charge-status="blocked"'
                else:
                    color, label = "#b6f779", "CHARGE"
                    charge_status = ' data-charge-status="accepted"'
                detail = (
                    f"SWIPE UP at {time_ms:.3f} ms: "
                    f"scaled=({dx:.6f}, {dy:.6f}), "
                    f"raw=({raw_dx:.6f}, {raw_dy:.6f}) screen heights"
                )
                if charge_path_known:
                    detail += (
                        f", max |raw X|="
                        f"{float(after_state.get('gestureMaxAbsRawDeltaX', 0.0)):.6f}, "
                        f"raw upward Y="
                        f"{float(after_state.get('gestureRawUpwardDistance', 0.0)):.6f}, "
                        f"path eligible="
                        f"{str(bool(after_state.get('jumpChargePathEligible', False))).lower()}"
                    )
            else:
                color, label = "#48e0b5", "DOWN"
                detail = (
                    f"SWIPE DOWN at {time_ms:.3f} ms: "
                    f"scaled=({dx:.6f}, {dy:.6f}), "
                    f"raw=({raw_dx:.6f}, {raw_dy:.6f}) screen heights"
                )
            svg.append(
                f'<line x1="{event_x:.2f}" y1="{event_y:.2f}" '
                f'x2="{marker_x:.2f}" y2="{marker_y:.2f}" stroke="{color}" '
                'stroke-opacity="0.42" stroke-width="1" stroke-dasharray="2 3"/>'
            )
            svg.append(
                f'<g transform="translate({marker_x:.2f},{marker_y:.2f})" '
                f'data-input-type="{marker_kind}"{charge_status} role="img" '
                f'aria-label="{svg_escape(detail)}"><title>'
                f'{svg_escape(detail)}</title>'
            )
            if marker_kind == "TOUCH_DOWN":
                svg.append(
                    f'<circle r="7" fill="#17202b" stroke="{color}" '
                    f'stroke-width="1.5"/><circle r="2.6" fill="{color}"/>'
                )
            elif marker_kind == "TOUCH_UP":
                svg.append(
                    f'<circle cy="2" r="6" fill="#17202b" stroke="{color}" '
                    f'stroke-width="1.5"/><line x1="0" y1="-1" x2="0" y2="-9" '
                    f'stroke="{color}" stroke-width="1.8"/>'
                    f'<path d="M -3 -6 L 0 -9 L 3 -6" fill="none" '
                    f'stroke="{color}" stroke-width="1.8"/>'
                )
            elif marker_kind == "CANCEL_GESTURE":
                svg.append(
                    f'<circle r="7" fill="#17202b" stroke="{color}"/>'
                    f'<path d="M -3.5 -3.5 L 3.5 3.5 M 3.5 -3.5 L -3.5 3.5" '
                    f'stroke="{color}" stroke-width="1.8"/>'
                )
            elif marker_kind == "SWIPE_UP":
                svg.append(
                    f'<line x1="0" y1="7" x2="0" y2="-7" stroke="{color}" '
                    f'stroke-width="2.2"/><path d="M -4 -3 L 0 -8 L 4 -3" '
                    f'fill="none" stroke="{color}" stroke-width="2.2"/>'
                )
                if charge_path_blocked:
                    svg.append(
                        f'<line x1="-6" y1="1" x2="6" y2="1" stroke="{color}" '
                        'stroke-width="2.5"/>'
                    )
            else:
                svg.append(
                    f'<line x1="0" y1="-7" x2="0" y2="7" stroke="{color}" '
                    f'stroke-width="2.2"/><path d="M -4 3 L 0 8 L 4 3" '
                    f'fill="none" stroke="{color}" stroke-width="2.2"/>'
                )
            svg.append(
                f'<text x="0" y="18" fill="{color}" text-anchor="middle" '
                f'font-size="8" font-weight="700">{label}</text></g>'
            )
    svg.append("</g>")

    jumps: list[tuple[dict[str, Any], dict[str, Any], ProjectedPoint]] = []
    for tick in ticks:
        for event in tick.get("events", []):
            if event.get("type") == "JUMP":
                event_position = point3(
                    event.get("position", tick["before"]["absolutePosition"])
                )
                jumps.append(
                    (
                        tick,
                        event,
                        (project(event_position), project_vertical(event_position)),
                    )
                )

    svg.append('<g id="jumpMarkers" font-family="system-ui,sans-serif">')
    for jump_number, (tick, event, projected) in enumerate(jumps, 1):
        px, py = sx(projected[0]), sy(projected[1])
        size = 7.0
        diamond = (
            f"{px:.2f},{py - size:.2f} {px + size:.2f},{py:.2f} "
            f"{px:.2f},{py + size:.2f} {px - size:.2f},{py:.2f}"
        )
        fallback_time = tick["before"].get(
            "timeNanos",
            int(tick.get("timeNanos", 0)) - int(header.get("dtNanos", 0)),
        )
        time_ms = float(event.get("timeNanos", fallback_time)) / 1_000_000.0
        rule = event.get("detail", "JUMP")
        label_y = max(top + 18.0, py - 24.0 - (jump_number % 3) * 16.0)
        svg.append(
            f'<line x1="{px:.2f}" y1="{py - size:.2f}" x2="{px:.2f}" '
            f'y2="{label_y + 5:.2f}" stroke="#ffce56" stroke-width="1" '
            f'stroke-dasharray="4 3"/>'
        )
        svg.append(
            f'<polygon points="{diamond}" fill="#ffcc4d" stroke="#fff1b5" '
            f'stroke-width="1.5"><title>Jump at tick '
            f'{tick.get("tick")} ({time_ms:.1f} ms): '
            f'{svg_escape(rule)}</title></polygon>'
        )
        svg.append(
            f'<text x="{px + 6:.2f}" y="{label_y:.2f}" fill="#ffe08a" '
            f'font-size="11">J{jump_number} {time_ms:.0f}ms · '
            f'{svg_escape(rule)}</text>'
        )
    svg.append("</g>")

    event_styles = {
        "LAND": ("#62d5ff", "L"),
        "BOUNCE": ("#ff9f43", "B"),
        "LANDING_JUMP_ARMED": ("#62d5ff", "A"),
        "BOUNCE_SUPPRESSED": ("#48e0b5", "N"),
        "FEATHER_COLLECTED": ("#ffe47a", "F"),
        "SPIKE_HIT": ("#ff6b6b", "S"),
        "PLAYER_DIED": ("#ff4f6d", "D"),
        "INVARIANT_FAILURE": ("#ff4f6d", "!"),
    }
    event_counts: dict[str, int] = {}
    svg.append('<g id="eventMarkers" font-family="system-ui,sans-serif">')
    for tick in ticks:
        suppressed_positions = [
            point3(item["position"])
            for item in tick.get("events", [])
            if item.get("type") == "BOUNCE_SUPPRESSED" and "position" in item
        ]
        for event in tick.get("events", []):
            event_type = str(event.get("type", ""))
            if event_type not in event_styles or "position" not in event:
                continue
            event_position = point3(event["position"])
            # A braked hard impact also becomes stable support and therefore emits LAND. Rendering
            # both glyphs at the same exact center would hide the important no-bounce result.
            if event_type == "LAND" and any(
                sum((left - right) ** 2 for left, right in zip(event_position, item))
                < 1.0e-18
                for item in suppressed_positions
            ):
                continue
            color, code = event_styles[event_type]
            event_counts[event_type] = event_counts.get(event_type, 0) + 1
            number = event_counts[event_type]
            px = sx(project(event_position))
            py = sy(project_vertical(event_position))
            fallback_time = tick["after"].get(
                "timeNanos", tick.get("timeNanos", 0)
            )
            time_ms = float(event.get("timeNanos", fallback_time)) / 1_000_000.0
            detail = str(event.get("detail", event_type))
            title_text = (
                f"{event_type} {number} at {time_ms:.3f} ms: {detail}"
            )
            if event_type == "LANDING_JUMP_ARMED":
                shield = (
                    f"{px - 7:.2f},{py - 7:.2f} "
                    f"{px + 7:.2f},{py - 7:.2f} "
                    f"{px + 6:.2f},{py + 1:.2f} "
                    f"{px:.2f},{py + 8:.2f} "
                    f"{px - 6:.2f},{py + 1:.2f}"
                )
                svg.append(
                    f'<g data-event-type="LANDING_JUMP_ARMED" role="img" '
                    f'aria-label="{svg_escape(title_text)}"><title>'
                    f'{svg_escape(title_text)}</title><polygon points="{shield}" '
                    f'fill="#17303d" stroke="{color}" stroke-width="1.5"/>'
                    f'<path d="M {px - 3:.2f} {py - 1:.2f} V {py - 3:.2f} '
                    f'A 3 3 0 0 1 {px + 3:.2f} {py - 3:.2f} V {py - 1:.2f}" '
                    f'fill="none" stroke="{color}" stroke-width="1.2"/>'
                    f'<rect x="{px - 4:.2f}" y="{py - 1:.2f}" width="8" height="6" '
                    f'fill="{color}"/></g>'
                )
            elif event_type == "BOUNCE_SUPPRESSED":
                svg.append(
                    f'<g data-event-type="BOUNCE_SUPPRESSED" role="img" '
                    f'aria-label="{svg_escape(title_text)}" stroke="{color}" '
                    f'fill="none" stroke-width="2"><title>'
                    f'{svg_escape(title_text)}</title>'
                    f'<line x1="{px:.2f}" y1="{py - 11:.2f}" '
                    f'x2="{px:.2f}" y2="{py - 2:.2f}"/>'
                    f'<path d="M {px - 4:.2f} {py - 6:.2f} L {px:.2f} {py - 1:.2f} '
                    f'L {px + 4:.2f} {py - 6:.2f}"/>'
                    f'<line x1="{px - 8:.2f}" y1="{py + 3:.2f}" '
                    f'x2="{px + 8:.2f}" y2="{py + 3:.2f}" '
                    f'stroke-width="3"/></g>'
                )
            elif event_type in ("PLAYER_DIED", "INVARIANT_FAILURE"):
                svg.append(
                    f'<g stroke="{color}" stroke-width="2.5"><line '
                    f'x1="{px - 6:.2f}" y1="{py - 6:.2f}" '
                    f'x2="{px + 6:.2f}" y2="{py + 6:.2f}"/><line '
                    f'x1="{px + 6:.2f}" y1="{py - 6:.2f}" '
                    f'x2="{px - 6:.2f}" y2="{py + 6:.2f}">'
                    f"<title>{svg_escape(title_text)}</title></line></g>"
                )
            elif event_type == "BOUNCE":
                points = (
                    f"{px:.2f},{py - 6:.2f} "
                    f"{px + 6:.2f},{py + 5:.2f} "
                    f"{px - 6:.2f},{py + 5:.2f}"
                )
                svg.append(
                    f'<polygon points="{points}" fill="{color}" '
                    f'stroke="#ffe0b7"><title>{svg_escape(title_text)}'
                    f"</title></polygon>"
                )
            else:
                svg.append(
                    f'<circle cx="{px:.2f}" cy="{py:.2f}" r="5" '
                    f'fill="{color}" stroke="#ecf8ff"><title>'
                    f"{svg_escape(title_text)}</title></circle>"
                )
            svg.append(
                f'<text x="{px + 7:.2f}" y="{py - 7:.2f}" '
                f'fill="{color}" font-size="10">{code}{number}</text>'
            )
    svg.append("</g>")

    start_x, start_y = sx(projected_path[0][0]), sy(projected_path[0][1])
    end_x, end_y = sx(projected_path[-1][0]), sy(projected_path[-1][1])
    ended_dead = bool(ticks[-1]["after"].get("dead", False))
    end_fill = "#ff4f6d" if ended_dead else "#b57cff"
    end_stroke = "#ffd4dc" if ended_dead else "#f0e2ff"
    end_title = "Death / terminal end" if ended_dead else "End"
    svg.extend(
        [
            f'<circle cx="{start_x:.2f}" cy="{start_y:.2f}" r="5" '
            f'fill="#42e695" stroke="#d8ffef"><title>Start</title></circle>',
            f'<circle cx="{end_x:.2f}" cy="{end_y:.2f}" r="5" '
            f'fill="{end_fill}" stroke="{end_stroke}"><title>'
            f"{end_title}</title></circle>",
            (
                f'<text x="{left + plot_width / 2:.2f}" y="{height - 18}" '
                f'fill="#a8b6c5" text-anchor="middle" '
                f'font-family="system-ui,sans-serif" font-size="13">'
                "horizontal track projection (world units)</text>"
            ),
            (
                f'<text x="22" y="{top + plot_height / 2:.2f}" fill="#a8b6c5" '
                f'text-anchor="middle" font-family="system-ui,sans-serif" '
                f'font-size="13" transform="rotate(-90 22 '
                f'{top + plot_height / 2:.2f})">world '
                f'{svg_escape(vertical_mode.upper())}</text>'
            ),
            (
                f'<g transform="translate({width - 610},42)" '
                f'font-family="system-ui,sans-serif" font-size="12">'
                '<line x1="0" y1="0" x2="32" y2="0" stroke="#57b8ff" '
                'stroke-width="3"/><text x="40" y="4" fill="#c8d4df">'
                'player center</text><rect x="145" y="-6" width="18" height="12" '
                'fill="#42624b" stroke="#83c88e"/><text x="172" y="4" '
                'fill="#c8d4df">normal</text><rect x="230" y="-6" width="18" '
                'height="12" fill="#7a612f" stroke="#f1c45b"/><text x="257" '
                'y="4" fill="#c8d4df">boost</text><polygon '
                'points="322,-7 329,0 322,7 315,0" fill="#ffcc4d"/>'
                '<text x="338" y="4" fill="#c8d4df">jump</text>'
                '<circle cx="405" cy="0" r="5" fill="#62d5ff"/>'
                '<text x="416" y="4" fill="#c8d4df">land</text>'
                '<polygon points="476,-6 482,5 470,5" fill="#ff9f43"/>'
                '<text x="489" y="4" fill="#c8d4df">bounce</text>'
                '<circle cx="0" cy="22" r="5" fill="#ffe47a"/>'
                '<text x="12" y="26" fill="#c8d4df">collect</text>'
                '<g transform="translate(82,22)" stroke="#ff4f6d" '
                'stroke-width="2"><line x1="-5" y1="-5" x2="5" y2="5"/>'
                '<line x1="5" y1="-5" x2="-5" y2="5"/></g>'
                '<text x="95" y="26" fill="#c8d4df">death</text>'
                '<g transform="translate(175,22)" stroke="#62d5ff">'
                '<polygon points="-7,-7 7,-7 6,1 0,8 -6,1" fill="#17303d"/>'
                '<path d="M -3,-1 V -3 A 3,3 0 0 1 3,-3 V -1" fill="none"/>'
                '<rect x="-4" y="-1" width="8" height="6" fill="#62d5ff"/></g>'
                '<text x="188" y="26" fill="#c8d4df">buffer armed</text>'
                '<g transform="translate(302,22)" stroke="#48e0b5" fill="none" '
                'stroke-width="2"><line x1="0" y1="-9" x2="0" y2="-1"/>'
                '<path d="M -4,-5 L 0,0 L 4,-5"/><line x1="-8" y1="4" '
                'x2="8" y2="4" stroke-width="3"/></g>'
                '<text x="315" y="26" fill="#c8d4df">no bounce</text>'
                '<g transform="translate(0,48)" stroke="#d7a7ff">'
                '<circle r="6" fill="#17202b"/><circle r="2.4" fill="#d7a7ff"/></g>'
                '<text x="11" y="52" fill="#c8d4df">press</text>'
                '<g transform="translate(88,48)" stroke="#b6f779" fill="none" '
                'stroke-width="2"><line x1="0" y1="6" x2="0" y2="-6"/>'
                '<path d="M -4,-2 L 0,-7 L 4,-2"/></g>'
                '<text x="100" y="52" fill="#c8d4df">charge swipe</text>'
                '<g transform="translate(211,48)" stroke="#48e0b5" fill="none" '
                'stroke-width="2"><line x1="0" y1="-6" x2="0" y2="6"/>'
                '<path d="M -4,2 L 0,7 L 4,2"/></g>'
                '<text x="223" y="52" fill="#c8d4df">down swipe</text>'
                '<g transform="translate(329,48)" stroke="#f0c5ff" fill="none" '
                'stroke-width="1.5"><circle cy="2" r="5"/>'
                '<line x1="0" y1="-1" x2="0" y2="-8"/>'
                '<path d="M -3,-5 L 0,-8 L 3,-5"/></g>'
                '<text x="340" y="52" fill="#c8d4df">release</text></g>'
            ),
            "</svg>",
        ]
    )
    return "\n".join(svg)


def main() -> None:
    args = parse_args()
    header, ticks = read_trace(args.trace)
    triangles = terrain_triangles(header, ticks)
    output = args.output or args.trace.with_suffix(".side.svg")
    output.parent.mkdir(parents=True, exist_ok=True)
    svg = build_svg(
        header,
        ticks,
        triangles,
        width=args.width,
        height=args.height,
        horizontal_mode=args.horizontal,
        vertical_mode=args.vertical,
        focus_traveled=args.focus_traveled,
        show_samples=args.samples,
        title=args.title,
        show_solver_debug=args.solver_debug,
        show_spin_debug=args.spin_debug,
    )
    output.write_text(svg, encoding="utf-8")
    jump_count = sum(
        1
        for tick in ticks
        for event in tick.get("events", [])
        if event.get("type") == "JUMP"
    )
    print(
        f"Wrote {output} ({len(ticks)} ticks, {len(triangles)} terrain triangles, "
        f"{jump_count} jumps)"
    )


if __name__ == "__main__":
    main()
