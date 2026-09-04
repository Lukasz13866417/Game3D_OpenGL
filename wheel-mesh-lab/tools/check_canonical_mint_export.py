#!/usr/bin/env python3
"""Verify that the tracked gameplay wheel matches the canonical mint asset."""

from collections import Counter
from pathlib import Path


LAB_ROOT = Path(__file__).resolve().parents[1]
OBJ_PATH = LAB_ROOT / "exports" / "mint-wheel.obj"
MTL_PATH = LAB_ROOT / "exports" / "mint-wheel.mtl"

EXPECTED_OBJECT_MATERIALS = [
    ("mint-wheel_carcass", "mint_rubber"),
    ("mint-wheel_glowing_chevron_grooves", "mint_groove_emissive"),
    (
        "mint-wheel_phase_independent_tread_motion_band",
        "mint_motion_band_emissive",
    ),
    ("mint-wheel_recessed_hub", "mint_hub"),
    ("mint-wheel_mint_side_rings", "mint_side_emissive"),
]
EXPECTED_OBJECT_GEOMETRY = {
    "mint-wheel_carcass": (672, 1_344),
    # Every chevron is real emissive geometry. None is a repeated shader illusion.
    "mint-wheel_glowing_chevron_grooves": (18_648, 6_696),
    # A dense, tread-only crown provides the phase-invariant high-speed exposure.
    "mint-wheel_phase_independent_tread_motion_band": (9_000, 17_280),
    "mint-wheel_recessed_hub": (576, 960),
    "mint-wheel_mint_side_rings": (576, 1_152),
}
EXPECTED_VERTICES = 29_472
EXPECTED_TRIANGLES = 27_432


def fail(message: str) -> None:
    raise SystemExit(f"canonical mint export validation failed: {message}")


def main() -> None:
    obj_lines = OBJ_PATH.read_text(encoding="utf-8").splitlines()
    mtl_lines = MTL_PATH.read_text(encoding="utf-8").splitlines()

    vertex_count = sum(line.startswith("v ") for line in obj_lines)
    normal_count = sum(line.startswith("vn ") for line in obj_lines)
    triangle_count = sum(line.startswith("f ") for line in obj_lines)
    if vertex_count != EXPECTED_VERTICES:
        fail(f"expected {EXPECTED_VERTICES} vertices, found {vertex_count}")
    if normal_count != EXPECTED_VERTICES:
        fail(f"expected {EXPECTED_VERTICES} normals, found {normal_count}")
    if triangle_count != EXPECTED_TRIANGLES:
        fail(f"expected {EXPECTED_TRIANGLES} triangles, found {triangle_count}")

    object_materials: list[tuple[str, str]] = []
    active_object: str | None = None
    object_vertices: Counter[str] = Counter()
    object_triangles: Counter[str] = Counter()
    for line in obj_lines:
        if line.startswith("o "):
            active_object = line[2:]
        elif line.startswith("usemtl "):
            if active_object is None:
                fail("material assignment appears before its object")
            object_materials.append((active_object, line[7:]))
        elif line.startswith("v "):
            if active_object is None:
                fail("vertex appears before its object")
            object_vertices[active_object] += 1
        elif line.startswith("f "):
            if active_object is None:
                fail("face appears before its object")
            object_triangles[active_object] += 1
    if object_materials != EXPECTED_OBJECT_MATERIALS:
        fail(f"unexpected object/material assignments: {object_materials!r}")
    actual_geometry = {
        name: (object_vertices[name], object_triangles[name])
        for name, _ in object_materials
    }
    if actual_geometry != EXPECTED_OBJECT_GEOMETRY:
        fail(f"unexpected per-object geometry: {actual_geometry!r}")

    material_names = [line[7:] for line in mtl_lines if line.startswith("newmtl ")]
    expected_material_names = [material for _, material in EXPECTED_OBJECT_MATERIALS]
    if material_names != expected_material_names:
        fail(f"unexpected MTL materials: {material_names!r}")
    emissive_materials: Counter[str] = Counter()
    active_material: str | None = None
    for line in mtl_lines:
        if line.startswith("newmtl "):
            active_material = line[7:]
        elif line.startswith("Ke "):
            if active_material is None:
                fail("emission appears before its material")
            emissive_materials[active_material] += 1
    expected_emissive = Counter({
        "mint_groove_emissive": 1,
        "mint_motion_band_emissive": 1,
        "mint_side_emissive": 1,
    })
    if emissive_materials != expected_emissive:
        fail(f"unexpected emissive materials: {dict(emissive_materials)!r}")

    print(
        "Canonical mint export: "
        f"{vertex_count} vertices, {triangle_count} triangles, "
        f"{len(material_names)} materials; 18 real emissive chevrons, "
        "a dedicated tread-only motion band, and side emission independently "
        "addressable"
    )


if __name__ == "__main__":
    main()
