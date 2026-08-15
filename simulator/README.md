# Game3D desktop physics simulator

This module runs the authoritative `game-core` simulation without Android or OpenGL. Physics
always advances at 120 Hz; `--ticks` and `--duration-ms` only select how long to run.

```bash
./gradlew :simulator:run --args="list"
./gradlew :simulator:run --args="run flat_rest --duration-ms 5000 --trace summary"
./gradlew :simulator:run --args="run spike_avoidance --trace contacts --out build/traces/spike.ndjson"
./gradlew :simulator:run --args="run ground_jump --ticks 2 --trace full --out build/traces/full.ndjson"
./gradlew :simulator:run --args="run generated_gameplay_stream --trace summary --out build/traces/generated.ndjson"
./gradlew :simulator:run --args="run published_catalog_level --trace summary --out build/traces/catalog.ndjson"
./gradlew :simulator:run --args="diff build/traces/left.ndjson build/traces/right.ndjson"
python3 tools/visualize_simulation.py build/traces/spike.ndjson
```

Regenerate every built-in `final-*.ndjson` trace and SVG (including the impact-brake and landing-
buffer comparisons) with:

```bash
tools/regenerate_simulation_visuals.sh
```

`summary` records state, normalized input, jump-rule decisions, feature events, deterministic
state hashes, resolved within-tick motion segments, and authoritative spin segments. Schema 10
classifies each movement's jump contribution from its raw physical deltas and records the
held-charge grace and decay tuning. It also records the
canonical terrain revision/digest, initial segment records, and exact
ordered terrain commits applied before each fixed tick. Every gameplay event has an
authoritative world position, simulation time, and within-tick fraction without mixing solver
probe endpoints into the gameplay path. It also records the exact signed axle delta (including
multi-turn ticks), physical angular velocity, support normal, and whether each interval used
supported no-slip rolling or the airborne spin motor. `contacts` additionally records every queried
authoritative triangle, swept-TOI contact, attempted endpoint, resolved center, and pre/post-impact
linear/angular velocities. `full` adds analytic cylinder cap/rim data and every transformed vertex from
`app/src/main/assets/tire_main.obj`; the visual vertices are diagnostic and never drive collision.

Interactive mode supports:

```text
next
run 30
until event LAND
until rule AIRBORNE_SPIKE_FIRST
until tick 240
inspect
rewind 20
quit
```

Hand-authored scenarios use `TrackBuilder`. Streaming scenarios and Android gameplay use the same
structure-backed `GameplayTerrainStream` from `:terrain-authoring`. Both produce the immutable
`TerrainSnapshot` plus ordered `TerrainCommit` stream; simulator physics and OpenGL presentation
therefore consume identical terrain records without an Android/legacy conversion layer. The old
`StreamingTerrainGenerator` remains only as a parity oracle until the device-soak cleanup gate.
`published_catalog_level` loads the same runtime catalog artifact packaged into Android and the
simulator, and deliberately selects a custom entry when the catalog has one. Override it with
`-Dgame3d.terrainCatalog=/absolute/path/runtime-catalog.json` when validating an artifact outside
the packaged build resource.

## Side-view visualization

`tools/visualize_simulation.py` writes a self-contained SVG containing the authoritative terrain
triangles, the 120 Hz player-center path, spike/feather features, material-colored boost sections,
and exact event markers. Jump diamonds are labeled with the winning jump rule; landing, bounce,
collection, and death markers explain otherwise ambiguous arcs and terminal paths. Vector input
glyphs identify touch-down, upward charge swipes, downward swipes, and touch-up without relying on
font emoji. Upward movements that are not vertical enough to contribute are shown in orange as
`BLOCKED`; their tooltip includes scaled/raw deltas, cumulative X/upward diagnostics, and whether
the latest movement contributed. Dashed state overlays show the intervals where the landing-jump buffer or held-impact
brake is armed. A shield/lock marks `LANDING_JUMP_ARMED`; a downward arrow stopped by a bar marks
`BOUNCE_SUPPRESSED`, so a deliberately absorbed hard landing cannot be mistaken for a missing
bounce marker. Schema-5+
motion segments construct the trajectory; events only decorate that resolved path. Schema-8+ traces
show commits extending the displayed terrain at the exact frame boundary where simulation received
them.
Attempted solver
endpoints can be displayed separately with `--solver-debug`. `--spin-debug` overlays sampled tire
phase glyphs and axle spokes; each spoke tooltip reports phase, exact tick delta, angular/rim speed,
and supported slip.

```bash
./gradlew :simulator:run --args="run ground_jump --trace summary --out build/traces/jump.ndjson"
python3 tools/visualize_simulation.py build/traces/jump.ndjson \
  --output build/traces/jump.svg --samples

python3 tools/visualize_simulation.py build/traces/jump.ndjson \
  --output build/traces/jump-debug.svg --solver-debug

python3 tools/visualize_simulation.py build/traces/jump.ndjson \
  --output build/traces/jump-spin.svg --spin-debug
```

Use `--horizontal distance` for a curved track, `--horizontal x` or `z` for an explicit world-axis
view, and `--focus-traveled` to clip long unvisited portions of the terrain. The normal side view
uses `--vertical y`. A second X-versus-track-station diagnostic makes an airborne redirect visible:

```bash
python3 tools/visualize_simulation.py build/traces/airborne_redirect.ndjson \
  --output build/traces/airborne_redirect-lateral.svg \
  --horizontal z --vertical x --focus-traveled --samples
```
