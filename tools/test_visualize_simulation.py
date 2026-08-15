import unittest

from tools.visualize_simulation import (
    build_svg,
    clip_polygon_horizontal,
    player_points,
    terrain_features,
    terrain_triangles,
)


class VisualizeSimulationTest(unittest.TestCase):
    def test_schema_seven_streaming_commits_extend_visualized_terrain(self) -> None:
        initial = {
            "id": 10,
            "nearLeft": [-2.0, 0.0, 1.0],
            "nearRight": [2.0, 0.0, 1.0],
            "farLeft": [-2.0, 0.0, -2.0],
            "farRight": [2.0, 0.0, -2.0],
            "solid": True,
            "surfaceKind": "NORMAL",
            "features": [],
        }
        streamed = {
            "id": 11,
            "nearLeft": [-2.0, 1.0, -2.0],
            "nearRight": [2.0, 1.0, -2.0],
            "farLeft": [-2.0, 1.0, -5.0],
            "farRight": [2.0, 1.0, -5.0],
            "solid": True,
            "surfaceKind": "BOOST_RAMP",
            "features": [
                {
                    "id": 90,
                    "kind": "FEATHER",
                    "center": [0.0, 1.25, -4.0],
                    "triggerRadius": 0.3,
                }
            ],
        }
        header = {
            "schema": 7,
            "terrainSegments": [initial],
            # This compatibility geometry must not hide later canonical commits.
            "terrainTriangles": [{"id": 1}],
            "terrainFeatures": [],
        }
        ticks = [
            {
                "appliedTerrainCommits": [
                    {
                        "revision": 1,
                        "segmentUpserts": [streamed],
                    }
                ]
            }
        ]

        triangles = terrain_triangles(header, ticks)

        self.assertEqual(4, len(triangles))
        self.assertEqual({10, 11}, {item["ownerSegmentId"] for item in triangles})
        self.assertEqual(
            ["NORMAL", "NORMAL", "BOOST", "BOOST"],
            [item["material"] for item in triangles],
        )
        self.assertEqual([90], [item["id"] for item in terrain_features(header, ticks)])

    def test_schema_five_motion_segments_override_arbitrary_event_pose(self) -> None:
        ticks = [
            {
                "before": {"absolutePosition": [0.0, 1.0, 0.0]},
                "after": {"absolutePosition": [0.0, 2.0, -1.0]},
                "motionSegments": [
                    {
                        "startFraction": 0.0,
                        "endFraction": 0.4,
                        "startPosition": [0.0, 1.0, 0.0],
                        "endPosition": [0.0, 1.5, -0.4],
                    },
                    {
                        "startFraction": 0.4,
                        "endFraction": 1.0,
                        "startPosition": [0.0, 1.5, -0.4],
                        "endPosition": [0.0, 2.0, -1.0],
                    },
                ],
                "events": [
                    {
                        "type": "BOUNCE",
                        "position": [0.0, -10.0, -0.4],
                        "tickFraction": 0.4,
                    }
                ],
            }
        ]

        self.assertEqual(
            [
                (0.0, 1.0, 0.0),
                (0.0, 1.5, -0.4),
                (0.0, 2.0, -1.0),
            ],
            player_points(ticks),
        )

    def test_focus_clip_trims_a_triangle_instead_of_retaining_long_vertices(self) -> None:
        clipped = clip_polygon_horizontal(
            [(-100.0, 0.0), (100.0, 0.0), (100.0, 5.0)],
            -2.0,
            3.0,
        )

        self.assertGreaterEqual(len(clipped), 3)
        self.assertTrue(all(-2.0 <= point[0] <= 3.0 for point in clipped))
        self.assertTrue(any(point[0] == -2.0 for point in clipped))
        self.assertTrue(any(point[0] == 3.0 for point in clipped))

    def test_svg_distinguishes_boost_and_marks_timed_bounce_and_death(self) -> None:
        header = {
            "scenario": "diagnostic",
            "fixedHz": 120,
            "dtNanos": 8_333_333,
            "terrainFeatures": [],
        }
        ticks = [
            {
                "tick": 1,
                "timeNanos": 8_333_333,
                "before": {
                    "timeNanos": 0,
                    "absolutePosition": [0.0, 1.0, 0.0],
                },
                "after": {
                    "timeNanos": 8_333_333,
                    "absolutePosition": [0.0, -1.0, -1.0],
                    "dead": True,
                },
                "events": [
                    {
                        "type": "BOUNCE",
                        "detail": "impact",
                        "timeNanos": 4_166_667,
                        "position": [0.0, 0.5, -0.5],
                        "tickFraction": 0.5,
                    },
                    {
                        "type": "PLAYER_DIED",
                        "detail": "fall",
                        "timeNanos": 8_333_333,
                        "position": [0.0, -1.0, -1.0],
                        "tickFraction": 1.0,
                    },
                ],
            }
        ]
        triangles = [
            {
                "a": [-2.0, 0.0, 1.0],
                "b": [2.0, 0.0, 1.0],
                "c": [2.0, 0.0, -100.0],
                "material": "BOOST",
            }
        ]

        svg = build_svg(
            header,
            ticks,
            triangles,
            width=800,
            height=500,
            horizontal_mode="z",
            vertical_mode="y",
            focus_traveled=True,
            show_samples=True,
            title=None,
        )

        self.assertIn("<title>BOOST terrain</title>", svg)
        self.assertIn("BOUNCE 1 at 4.167 ms: impact", svg)
        self.assertIn("PLAYER_DIED 1 at 8.333 ms: fall", svg)
        self.assertIn("Death / terminal end", svg)
        self.assertIn('id="eventMarkers"', svg)

    def test_solver_probe_is_optional_and_never_changes_path(self) -> None:
        header = {
            "scenario": "solver",
            "fixedHz": 120,
            "dtNanos": 8_333_333,
            "terrainFeatures": [],
        }
        ticks = [
            {
                "tick": 1,
                "before": {
                    "timeNanos": 0,
                    "absolutePosition": [0.0, 1.0, 0.0],
                },
                "after": {
                    "timeNanos": 8_333_333,
                    "absolutePosition": [0.0, 0.5, -1.0],
                    "dead": False,
                },
                "motionSegments": [
                    {
                        "startFraction": 0.0,
                        "endFraction": 1.0,
                        "startPosition": [0.0, 1.0, 0.0],
                        "endPosition": [0.0, 0.5, -1.0],
                    }
                ],
                "events": [],
                "contacts": [
                    {
                        "triangleId": 4,
                        "detectedCenter": [0.0, -3.0, -1.0],
                        "resolvedCenter": [0.0, 0.5, -1.0],
                        "timingQuality": "SWEPT_TOI",
                    }
                ],
            }
        ]
        triangles = [
            {
                "a": [-2.0, 0.0, 1.0],
                "b": [2.0, 0.0, 1.0],
                "c": [2.0, 0.0, -2.0],
                "material": "NORMAL",
            }
        ]

        normal = build_svg(
            header,
            ticks,
            triangles,
            width=800,
            height=500,
            horizontal_mode="z",
            vertical_mode="y",
            focus_traveled=True,
            show_samples=False,
            title=None,
        )
        debug = build_svg(
            header,
            ticks,
            triangles,
            width=800,
            height=500,
            horizontal_mode="z",
            vertical_mode="y",
            focus_traveled=True,
            show_samples=False,
            title=None,
            show_solver_debug=True,
        )

        self.assertNotIn('id="solverDebug"', normal)
        self.assertIn('id="solverDebug"', debug)
        self.assertIn("solver probe triangle 4", debug)

    def test_vertical_x_mode_labels_lateral_projection(self) -> None:
        header = {
            "scenario": "redirect",
            "fixedHz": 120,
            "dtNanos": 8_333_333,
            "terrainFeatures": [],
        }
        ticks = [
            {
                "tick": 1,
                "timeNanos": 8_333_333,
                "before": {
                    "timeNanos": 0,
                    "absolutePosition": [0.0, 1.0, 0.0],
                },
                "after": {
                    "timeNanos": 8_333_333,
                    "absolutePosition": [1.0, 1.0, -1.0],
                    "dead": False,
                },
                "events": [],
            }
        ]
        triangles = [
            {
                "a": [-2.0, 0.0, 1.0],
                "b": [2.0, 0.0, 1.0],
                "c": [2.0, 0.0, -2.0],
                "material": "NORMAL",
            }
        ]

        svg = build_svg(
            header,
            ticks,
            triangles,
            width=800,
            height=500,
            horizontal_mode="z",
            vertical_mode="x",
            focus_traveled=True,
            show_samples=False,
            title=None,
        )

        self.assertIn("world X", svg)
        self.assertIn("projection=z/x", svg)

    def test_schema_six_spin_debug_draws_exact_phase_spokes(self) -> None:
        header = {
            "schema": 6,
            "scenario": "spin",
            "fixedHz": 120,
            "dtNanos": 8_333_333,
            "cylinderRadius": 0.25,
            "terrainFeatures": [],
        }
        ticks = [
            {
                "tick": 1,
                "before": {
                    "timeNanos": 0,
                    "absolutePosition": [0.0, 0.25, 0.0],
                },
                "after": {
                    "timeNanos": 8_333_333,
                    "absolutePosition": [0.0, 0.25, -1.0],
                    "velocity": [0.0, 0.0, -4.0],
                    "heading": [0.0, 0.0, -1.0],
                    "cylinderAxis": [1.0, 0.0, 0.0],
                    "supportNormal": [0.0, 1.0, 0.0],
                    "grounded": True,
                    "axleRadians": -1.0,
                    "axleDeltaRadians": -1.0,
                    "angularVelocity": -16.0,
                    "dead": False,
                },
                "spinSegments": [
                    {
                        "mode": "SUPPORTED_ROLL",
                        "deltaRadians": -1.0,
                    }
                ],
                "events": [],
            }
        ]
        triangles = [
            {
                "a": [-2.0, 0.0, 1.0],
                "b": [2.0, 0.0, 1.0],
                "c": [2.0, 0.0, -2.0],
                "material": "NORMAL",
            }
        ]

        svg = build_svg(
            header,
            ticks,
            triangles,
            width=800,
            height=500,
            horizontal_mode="z",
            vertical_mode="y",
            focus_traveled=True,
            show_samples=False,
            title=None,
            show_spin_debug=True,
        )

        self.assertIn('id="spinDebug"', svg)
        self.assertIn("phase=-1.000000 rad", svg)
        self.assertIn("slip=0.000000000", svg)

    def test_svg_draws_vector_gestures_armed_states_and_suppressed_bounce(self) -> None:
        header = {
            "schema": 7,
            "scenario": "input_diagnostics",
            "fixedHz": 120,
            "dtNanos": 8_333_333,
            "terrainFeatures": [],
        }
        ticks = [
            {
                "tick": 1,
                "before": {
                    "timeNanos": 0,
                    "absolutePosition": [0.0, 2.4, 0.0],
                    "touchHeld": False,
                    "landingJumpArmed": False,
                    "impactBrakeArmed": False,
                },
                "after": {
                    "timeNanos": 8_333_333,
                    "absolutePosition": [0.0, 2.3, -0.1],
                    "touchHeld": False,
                    "landingJumpArmed": True,
                    "impactBrakeArmed": False,
                    "dead": False,
                },
                "inputs": [
                    {"type": "TOUCH_DOWN", "timeNanos": 0},
                    {
                        "type": "SWIPE",
                        "timeNanos": 1_000_000,
                        "dxScreenHeights": 0.0,
                        "dyScreenHeights": -0.12,
                    },
                    {"type": "TOUCH_UP", "timeNanos": 2_000_000},
                ],
                "events": [
                    {
                        "type": "LANDING_JUMP_ARMED",
                        "detail": "safe support within window",
                        "timeNanos": 2_000_000,
                        "position": [0.0, 2.4, 0.0],
                        "tickFraction": 0.0,
                    }
                ],
            },
            {
                "tick": 2,
                "before": {
                    "timeNanos": 8_333_333,
                    "absolutePosition": [0.0, 2.3, -0.1],
                    "touchHeld": False,
                    "landingJumpArmed": False,
                    "impactBrakeArmed": False,
                },
                "after": {
                    "timeNanos": 16_666_666,
                    "absolutePosition": [0.0, 1.2, -0.2],
                    "touchHeld": True,
                    "landingJumpArmed": False,
                    "impactBrakeArmed": True,
                    "dead": False,
                },
                "inputs": [
                    {"type": "TOUCH_DOWN", "timeNanos": 9_000_000},
                    {
                        "type": "SWIPE",
                        "timeNanos": 10_000_000,
                        "dxScreenHeights": 0.0,
                        "dyScreenHeights": 0.12,
                    },
                ],
                "events": [],
            },
            {
                "tick": 3,
                "before": {
                    "timeNanos": 16_666_666,
                    "absolutePosition": [0.0, 1.2, -0.2],
                    "touchHeld": True,
                    "landingJumpArmed": False,
                    "impactBrakeArmed": True,
                },
                "after": {
                    "timeNanos": 24_999_999,
                    "absolutePosition": [0.0, 0.5, -0.3],
                    "touchHeld": True,
                    "landingJumpArmed": False,
                    "impactBrakeArmed": False,
                    "dead": False,
                },
                "inputs": [],
                "events": [
                    {
                        "type": "BOUNCE_SUPPRESSED",
                        "detail": "held downward swipe absorbed hard impact",
                        "timeNanos": 24_000_000,
                        "position": [0.0, 0.5, -0.3],
                        "tickFraction": 0.88,
                    },
                    {
                        "type": "LAND",
                        "detail": "first supported tick",
                        "timeNanos": 24_000_000,
                        "position": [0.0, 0.5, -0.3],
                        "tickFraction": 0.88,
                    },
                ],
            },
        ]
        triangles = [
            {
                "a": [-2.0, 0.0, 1.0],
                "b": [2.0, 0.0, 1.0],
                "c": [2.0, 0.0, -3.0],
                "material": "NORMAL",
            }
        ]

        svg = build_svg(
            header,
            ticks,
            triangles,
            width=900,
            height=600,
            horizontal_mode="z",
            vertical_mode="y",
            focus_traveled=True,
            show_samples=False,
            title=None,
        )

        self.assertIn('id="inputMarkers"', svg)
        self.assertIn('data-input-type="TOUCH_DOWN"', svg)
        self.assertIn('data-input-type="SWIPE_UP"', svg)
        self.assertIn('data-input-type="SWIPE_DOWN"', svg)
        self.assertIn('data-input-type="TOUCH_UP"', svg)
        self.assertIn("SWIPE UP at 1.000 ms", svg)
        self.assertIn("SWIPE DOWN at 10.000 ms", svg)
        self.assertIn('id="armedStatePaths"', svg)
        self.assertIn('data-state="landingJumpArmed"', svg)
        self.assertIn('data-state="impactBrakeArmed"', svg)
        self.assertIn('data-event-type="LANDING_JUMP_ARMED"', svg)
        self.assertIn('data-event-type="BOUNCE_SUPPRESSED"', svg)
        self.assertIn("safe support within window", svg)
        self.assertIn("held downward swipe absorbed hard impact", svg)
        self.assertNotIn("LAND 1 at", svg)
        self.assertIn("buffer armed", svg)
        self.assertIn("no bounce", svg)
        self.assertIn("charge swipe", svg)
        self.assertIn("down swipe", svg)

    def test_schema_eight_marks_raw_x_guard_rejection_on_up_swipe(self) -> None:
        header = {
            "schema": 8,
            "scenario": "guard_rejected",
            "fixedHz": 120,
            "dtNanos": 8_333_333,
            "terrainFeatures": [],
        }
        ticks = [
            {
                "tick": 1,
                "before": {
                    "timeNanos": 0,
                    "absolutePosition": [0.0, 0.5, 0.0],
                    "gestureCharge": 0.0,
                },
                "after": {
                    "timeNanos": 8_333_333,
                    "absolutePosition": [0.0, 0.5, -0.1],
                    "gestureCharge": 0.0,
                    "gestureChargePotential": 0.26,
                    "gestureMaxAbsRawDeltaX": 0.05,
                    "gestureRawUpwardDistance": 0.04,
                    "jumpChargePathEligible": False,
                    "dead": False,
                },
                "inputs": [
                    {
                        "type": "SWIPE",
                        "timeNanos": 1_000_000,
                        "dxScreenHeights": 0.01,
                        "dyScreenHeights": -0.032,
                        "rawDxScreenHeights": 0.05,
                        "rawDyScreenHeights": -0.04,
                    }
                ],
                "events": [],
            }
        ]
        triangles = [
            {
                "a": [-2.0, 0.0, 1.0],
                "b": [2.0, 0.0, 1.0],
                "c": [2.0, 0.0, -2.0],
                "material": "NORMAL",
            }
        ]

        svg = build_svg(
            header,
            ticks,
            triangles,
            width=900,
            height=600,
            horizontal_mode="z",
            vertical_mode="y",
            focus_traveled=True,
            show_samples=False,
            title=None,
        )

        self.assertIn('data-charge-status="blocked"', svg)
        self.assertIn("BLOCKED", svg)
        self.assertIn("raw=(0.050000, -0.040000)", svg)
        self.assertIn("max |raw X|=0.050000", svg)
        self.assertIn("path eligible=false", svg)

    def test_schema_ten_classifies_each_upward_movement_independently(self) -> None:
        header = {
            "schema": 10,
            "scenario": "per_movement_charge",
            "fixedHz": 120,
            "dtNanos": 8_333_333,
            "maxJumpChargeXToYRatio": 1.2,
            "terrainFeatures": [],
        }
        ticks = [
            {
                "tick": 1,
                "before": {
                    "timeNanos": 0,
                    "absolutePosition": [0.0, 0.5, 0.0],
                    "gestureCharge": 0.0,
                },
                "after": {
                    "timeNanos": 8_333_333,
                    "absolutePosition": [0.0, 0.5, -0.1],
                    "gestureCharge": 0.26,
                    "gestureChargePotential": 0.26,
                    "gestureMaxAbsRawDeltaX": 0.05,
                    "gestureRawUpwardDistance": 0.08,
                    # The last packet was blocked, but the first one still contributed.
                    "jumpChargePathEligible": False,
                    "dead": False,
                },
                "inputs": [
                    {
                        "type": "SWIPE",
                        "timeNanos": 1_000_000,
                        "dxScreenHeights": 0.0,
                        "dyScreenHeights": -0.04,
                        "rawDxScreenHeights": 0.0,
                        "rawDyScreenHeights": -0.04,
                    },
                    {
                        "type": "SWIPE",
                        "timeNanos": 2_000_000,
                        "dxScreenHeights": 0.05,
                        "dyScreenHeights": -0.04,
                        "rawDxScreenHeights": 0.05,
                        "rawDyScreenHeights": -0.04,
                    },
                ],
                "events": [],
            }
        ]
        triangles = [
            {
                "a": [-2.0, 0.0, 1.0],
                "b": [2.0, 0.0, 1.0],
                "c": [2.0, 0.0, -2.0],
                "material": "NORMAL",
            }
        ]

        svg = build_svg(
            header,
            ticks,
            triangles,
            width=900,
            height=600,
            horizontal_mode="z",
            vertical_mode="y",
            focus_traveled=True,
            show_samples=False,
            title=None,
        )

        self.assertEqual(1, svg.count('data-charge-status="accepted"'))
        self.assertEqual(1, svg.count('data-charge-status="blocked"'))
        self.assertIn("movement contributed=true", svg)
        self.assertIn("movement contributed=false", svg)


if __name__ == "__main__":
    unittest.main()
