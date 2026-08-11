# Game3D desktop physics simulator

This module runs the authoritative `game-core` simulation without Android or OpenGL. Physics
always advances at 120 Hz; `--ticks` and `--duration-ms` only select how long to run.

```bash
./gradlew :simulator:run --args="list"
./gradlew :simulator:run --args="run flat_rest --duration-ms 5000 --trace summary"
./gradlew :simulator:run --args="run spike_avoidance --trace contacts --out build/traces/spike.ndjson"
./gradlew :simulator:run --args="run ground_jump --ticks 2 --trace full --out build/traces/full.ndjson"
./gradlew :simulator:run --args="run generated_gameplay_stream --trace summary --out build/traces/generated.ndjson"
./gradlew :simulator:run --args="diff build/traces/left.ndjson build/traces/right.ndjson"
python3 tools/visualize_simulation.py build/traces/spike.ndjson
```

Regenerate every built-in `final-*.ndjson` trace and SVG (including the impact-brake and landing-
buffer comparisons) with:

```bash
tools/regenerate_simulation_visuals.sh
```

`summary` records state, normalized input, jump-rule decisions, feature events, deterministic
state hashes, resolved within-tick motion segments, and authoritative spin segments. Schema 8
records both sensitivity-scaled input deltas and the raw physical deltas used to classify gesture
paths. It also records the canonical terrain revision/digest, initial segment records, and exact
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

Hand-authored scenarios use `TrackBuilder`. Streaming scenarios and Android gameplay use
`StreamingTerrainGenerator`. Both produce the same immutable `TerrainSnapshot` plus ordered
`TerrainCommit` stream; the simulator, gameplay physics, and OpenGL presentation therefore consume
the same terrain records without an Android/legacy conversion layer.

## Side-view visualization

`tools/visualize_simulation.py` writes a self-contained SVG containing the authoritative terrain
triangles, the 120 Hz player-center path, spike/feather features, material-colored boost sections,
and exact event markers. Jump diamonds are labeled with the winning jump rule; landing, bounce,
collection, and death markers explain otherwise ambiguous arcs and terminal paths. Vector input
glyphs identify touch-down, upward charge swipes, downward swipes, and touch-up without relying on
font emoji. Schema-8 upward swipes that accumulate charge but fail either physical X guard are
shown in orange as `BLOCKED`; their tooltip includes scaled/raw deltas, peak X excursion, upward Y,
and the eligibility result. Dashed state overlays show the intervals where the landing-jump buffer or held-impact
brake is armed. A shield/lock marks `LANDING_JUMP_ARMED`; a downward arrow stopped by a bar marks
`BOUNCE_SUPPRESSED`, so a deliberately absorbed hard landing cannot be mistaken for a missing
bounce marker. Schema-5+
motion segments construct the trajectory; events only decorate that resolved path. Schema-8 traces
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
