# Wheel Mesh Lab

This is the desktop middle step between a generated concept image and an Android
player asset. It procedurally builds the two current wheel candidates, renders
them interactively, validates their proportions, and exports OBJ/MTL files.

The renderer deliberately creates a native **OpenGL ES 3.1** context on Linux.
That means the player shaders use the same GLSL ES dialect and the same lighting
math as gameplay instead of approximating a Blender material in desktop GLSL.

## Run

Ubuntu/Debian packages, if this is moved to a machine without them:

```bash
sudo apt install build-essential cmake pkg-config libglfw3-dev libglm-dev libgles-dev libegl-dev
```

From the project root:

```bash
./wheel-mesh-lab/run.sh
```

The script configures and builds in `wheel-mesh-lab/build/`, then starts the lab.
The gameplay-relevant mint wheel is selected by default; use `--model violet` or
press `2` to inspect the alternate concept. This machine was verified with OpenGL
ES 3.2 on its NVIDIA driver.

## Controls

| Input | Action |
|---|---|
| Left drag | Orbit the camera |
| Shift+left drag or middle drag | Pan |
| Right drag | Rotate the model independently |
| Mouse wheel | Zoom |
| `1`, `2` | Mint wheel, violet wheel |
| `3`, `4`, `5`, `6` | Side, tread, three-quarter, gameplay-distance camera |
| `F` | Gameplay flat/smooth Blinn-Phong shader |
| `L` | Gameplay bloom on/off |
| `W`, `N` | Triangle wireframe, vertex/face normals |
| `C` | Physics cylinder plus visual AABB |
| `G` | Grid and +X/+Y/+Z axes |
| `O` | Perspective/orthographic camera |
| `I` | Isolate the next material submesh |
| `Space` | Auto-roll around the +X axle |
| `T` | Cycle sharp, reference, protected adaptive/split, and legacy band; raw diagnostics are CLI-only |
| `[`, `]` | Decrease/increase spin by 0.5 rps; negative values reverse it |
| `E` | Export selected model to `exports/` |
| `P` | Save the current view to `screenshots/` |
| `H`, `R` | Hot-reload shaders, reset view |

The window title always reports the selected model, shader mode, bloom state,
spin state, and isolated submesh.

## Models and coordinates

Both models use gameplay coordinates directly:

- +X is the tire axle.
- +Y is up.
- The wheel rotates around +X and moves forward toward -Z in normal gameplay.
- Outer diameter is approximately 1.0 in authoring units.
- The cyan debug cylinder is the gameplay physics proxy: radius 0.5 and axial
  half-width approximately 0.1381, normalized directly from `PhysicsConfig`.
- The gameplay-distance preset uses the current 3.8-unit rear/0.75-unit upper
  camera offset and scales its clip planes and addon light consistently.

Important import note: the legacy `tire_main.obj` has a +Z axle, so
`PlayerAssets` currently applies `MODEL_ROTATION_Y = PI / 2`. These exports
already have a +X axle. When integrating one of them, set that import rotation
to zero (or rotate the exported vertices back to +Z first); applying the legacy
rotation unchanged would turn and then distort the new wheel during nonuniform
player scaling.

The mint model is a conventional monowheel with a recessed hub, eighteen
individually modelled mitred chevrons, and two mint side rings. All eighteen
chevrons use `mint_groove_emissive`; the nonmoving side lights use the separate
`mint_side_emissive` material. A dense, tread-only
`mint_motion_band_emissive` shell is normally hidden and represents the exact
angular-average limit of those grooves in alias-safe mode. It does not cover or
blur the wheel's sides. The violet model is one wheel with sixteen armor
pods distributed **around its
circumference**. Its wider gaps are split into nested groups of four primary,
four secondary, and eight detail grooves. Gameplay crossfades those groups as
the visual rotation per frame rises. Both side rings remain luminous. It is
never a stack of pieces along the axle.

At the highest visual speed, gameplay crossfades the dark violet under-core
toward the energy color. This reads as a stable purple motion glow instead of
bright spots jumping between render frames.

Each color/material is a separate `MeshPart` and draw call. This is intentional:
Android's `ObjMaterialGroupLoader` preserves OBJ `usemtl` groups and
`PlayerAssets` applies the matching gameplay material to each mesh. Android does
not consume the MTL lighting values themselves; the shared material names are
the contract between this lab and gameplay.

## Shader parity

`shaders/flat_lit.*` and `shaders/smooth_lit.*` are the current player shader
sources from `FlatLitShaderPair` and `InfillShaderPair`, including their uniform
names and normal-transform behavior. Because this program runs GLES, their
`#version 300 es` preamble is compiled directly.

The three `bloom_*.frag` passes and their constants also match
`BloomPostProcessor`:

- threshold `0.64`
- quarter-resolution bloom with the gameplay four-tap prefilter
- two horizontal/vertical blur iterations
- blur texel-step scale `0.5`
- additive intensity `0.95`

The lab also mirrors the Android post-process state: fullscreen passes disable
depth testing/writes and blending, bloom targets are depthless, and depth writes
are restored after composition. Shader sources, dimensions, blur scale,
iterations, threshold, and intensity are parity-checked.

The bright materials do not depend on a Blender/PBR feature. They use the
existing player shader with ambient=1, diffuse=0, and specular=0, followed by the
game bloom pass. This lets us judge what the current engine can actually render.

## Validation, screenshots, and export

CPU-only validation (also registered with CTest):

```bash
./wheel-mesh-lab/run.sh --validate-only
ctest --test-dir wheel-mesh-lab/build --output-on-failure
```

CTest also executes sharp, 64-sample reference, 22.3-rps legacy-band,
protected and raw adaptive/split, and alias-safe surfaceless GLES smoke captures, plus
the 120/180 Hz clean-mode phase-invariance check. It also compares all eight
copied player/bloom shader stages, bloom constants, and shared cylinder
dimensions against their Java sources. A future gameplay shader or player-proxy
edit therefore fails the lab parity test until its preview copy is intentionally
updated.

The parity test also checks the gameplay shutter fraction, adaptive sample cap
and spacing, transition thresholds, sharp/temporal blend distances,
temporal activation epsilon, neon peak, and the premultiplied source-over
compositing contract used by the desktop temporal modes.

Deterministic headless GPU captures:

```bash
./wheel-mesh-lab/run.sh --smoke-test --model mint --preset side
./wheel-mesh-lab/run.sh --smoke-test --model violet --preset tread
```

Smoke tests create a real OpenGL ES 3 context on a surfaceless EGL pbuffer, so
they work without X11, Wayland, or a hidden desktop window. Auto-roll smoke
captures advance by the exact configured `--fps` step (120 Hz by default). For
exact-pose comparisons, set the
physical wheel phase explicitly and optionally toggle bloom:

```bash
./wheel-mesh-lab/run.sh --smoke-test --model mint --preset tread \
  --spin-phase-degrees 30 --no-bloom \
  --screenshot wheel-mesh-lab/build/mint-phase-30.ppm
```

## Temporal wheel lab

The lab has eight presentation modes. Headless sequences are deterministic at
their configured `--fps`. Interactive auto-roll uses a scheduled presentation
clock by default: isolated loop/swap stalls do not become one giant catch-up
rotation followed by a near-stationary step. The explicit
`--phase-clock previous-delta` option retains that old behavior as a diagnostic
fault control. Clean `adaptive` and `split` filters use the active monitor's
stable nominal refresh period, so ordinary swap jitter cannot change their
representation or sample count. Their representation has no hitch-triggered
attack/release envelope. The old multipass implementations remain available
under the explicit `-raw` names. `alias-safe` is the current Android/production
approach; `band`, `adaptive-raw`, and `split-raw` remain desktop experiments for
direct comparison:

| Mode | Behavior |
|---|---|
| `sharp` | One physical pose; this intentionally exposes wagon-wheel aliasing |
| `reference` | A normalized 64-sample physical exposure; groove and carcass depth both move to every sample pose |
| `adaptive` | One analytic tread-shell draw: centered 0.75-frame Hann filtering plus an independent Nyquist guard for each spatial harmonic |
| `adaptive-raw` | Original adaptive Hann exposure with current-pose carcass depth and 1–12 samples, without the high-speed fallback |
| `band` | Legacy experiment: adaptive sampling/current-pose depth plus the stabilized 8–12°/frame one-pitch sample morph |
| `split` | One analytic tread-shell draw: a trailing one-frame box shutter plus the same per-harmonic Nyquist guard |
| `split-raw` | Original trailing-box split: divide the latest roll delta into `ceil(abs(D)/maxStep)` equal-angle parts and average their midpoints |
| `alias-safe` | Physical grooves below the ambiguity boundary, then an energy-matched crossfade to a dedicated phase-invariant tread band |

Only roll-sensitive tread emission is filtered. The carcass, hub and
`mint_side_emissive` remain sharp, so side glow never leaves a rotational trail.
For clean adaptive/split, the shader reconstructs the complete eighteen-groove
periodic mask on the tread-only shell as a five-harmonic convex blend of raised
cosines. That finite profile is nonnegative, peaks at one and has the authored
`0.26164` mean duty cycle. Each harmonic receives its analytic shutter transfer
plus temporal and derivative-based spatial wrapped-Gaussian filtering. Those are
normalized positive convolutions, so every amount of temporal/spatial blur keeps
the same DC emission without a clamp-and-renormalize approximation. At 0.5
groove cycles per presented frame no phase-dependent term is evaluated and only
the authored mean energy remains. There is no integer sample lattice to change
at runtime.

The reference, raw, band and compatibility alias-safe paths retain physical
exposure samples. Those samples have normalized weights and accumulate RGB plus
weighted alpha coverage in a full-frame RGBA16F target. The result is
premultiplied and composited over
the sharp wheel body with `ONE, ONE_MINUS_SRC_ALPHA`, not additive scene-RGB
blending. It enters the scene before the normal bright prefilter. A separate
additive pass contributes only the per-pixel bloom residual which the ordinary
four-tap scene bright pass did not already supply. If `E=C*A` is the decoded
premultiplied exposure and `kC` is the full emission bright-pass factor, its
target is `Q=E*kC`; the shader recomputes the ordinary four-tap value `O` and
adds `correctionBlend * max(Q-O, 0)`. `correctionBlend` is the projected-motion
smoothstep from 0.5 to 2.5 pixels. It intentionally excludes the band blend, so
switching the high-speed representation cannot pump bloom.
At full correction this is the exact residual target; at activation it removes
the otherwise hard policy jump from sharp bloom to the normalized temporal
bright-pass policy. There is no global or notional-sharp bloom scalar, so
already bright pixels cannot be double counted. Every sample clears and
rebuilds depth, avoiding translucent-copy and shared-depth artifacts. The
raw adaptive and band modes resolve moving grooves against the center-frame carcass, matching
the legacy efficient scene-depth approximation. Clean adaptive/split evaluate
their analytic mask against canonical carcass depth in one pass. Reference instead rotates the
carcass for every sample; differences around a strongly faceted silhouette
expose the cost of that current-pose approximation.

This does **not** blend stored display frames or predict future poses. Clean
adaptive/split analytically integrate their periodic tread at the displayed
center phase. The multipass sample poses are calculated from the exact displayed
phase, signed RPS, virtual frame rate, and the selected shutter; reference
exposure is capped at 1/30 second for hitch-like intervals. Raw adaptive
intervals target at most 0.75 pixels of projected travel in the
quarter-resolution temporal/bloom target and may use twelve physical samples.
Clean adaptive and split always use one emission draw. Band
mode smoothly changes its Hann
weights to uniform weights and the physical exposure angles to one complete
20-degree groove period. A 0.25°/frame hysteresis deadband stabilizes that LOD.

All eighteen emissive grooves are truthful and equal. There is no fake
one-fold brightness lobe, selected marker groove, stored-frame history, or RPS
clamp. Mint groove and side colors pass through gameplay's neon peak/saturation
mapping. At the protected modes' high-speed endpoint the repeated pattern itself
is phase invariant; the explicitly raw controls intentionally are not.

Compare exact poses headlessly:

```bash
./wheel-mesh-lab/run.sh --smoke-test --model mint --preset tread \
  --temporal-mode reference --spin-rps 5 --fps 120 \
  --spin-phase-degrees 30 \
  --screenshot wheel-mesh-lab/build/reference.ppm

./wheel-mesh-lab/run.sh --smoke-test --model mint --preset tread \
  --temporal-mode alias-safe --spin-rps 22.3 --fps 120 \
  --spin-phase-degrees 30 \
  --screenshot wheel-mesh-lab/build/alias-safe-22.3rps.ppm
```

Generate a deterministic sequence plus a tab-separated per-frame manifest:

```bash
./wheel-mesh-lab/run.sh --model mint --preset tread --temporal-mode alias-safe \
  --spin-rps 22.3 --fps 120 \
  --width 640 --height 480 --sequence-frames 120 \
  --sequence-dir wheel-mesh-lab/build/alias-safe-sequence
```

Direct sequences are capped at 100,000 frames (`frame-00000.ppm` through
`frame-99999.ppm`), preserving the fixed-width naming contract used by cleanup,
validation, and video export. At 180 Hz this permits just over nine minutes per
capture.

The comparison helper defaults to four PPM sequences and, when `ffmpeg` is
installed, one MP4 per mode plus a labelled 2x2 `comparison.mp4`,
`contact-sheet.png`, and backward-compatible `comparison-frame.png`. Its
default regression layout puts `adaptive-raw` beside `adaptive` and
`split-raw` beside `split`, so the old stutter/alias behavior and each guarded
replacement are visible in the same video. `--modes` can still select
`alias-safe`, `reference`, `band`, or any other two/four-mode combination. Use
`--cadences 120 180` to render the complete comparison into
separate `120hz/` and `180hz/` directories:

```bash
wheel-mesh-lab/tools/render_temporal_suite.py \
  --rps 22.3 --cadences 120 180 --seconds 1 --preset tread
```

The MP4 encoders set both the image-sequence input cadence and the constant-rate
output cadence. They then use `ffprobe` to verify the average/nominal frame rate
and exact frame count before atomically replacing an existing video. This is
important for high-refresh QA: omitting `-framerate` when passing PPM files to
`ffmpeg` silently produces its 25 fps image-sequence default, even when the
directory or output filename says `120hz`.

For ad-hoc QA comparisons made from already-rendered sequences, use the same
verified path instead of calling `ffmpeg` directly:

```bash
wheel-mesh-lab/tools/encode_sequence_comparison.py \
  --fps 120 \
  --input wheel-mesh-lab/build/qa/adaptive-120hz-fixed-sweep \
  --input wheel-mesh-lab/build/qa/split-120hz-fixed-sweep \
  --output wheel-mesh-lab/build/qa/transition-120hz-side-by-side.mp4
```

The inputs must have identical, contiguous `frame-00000.ppm` sequences. Use an
explicitly named lower `--fps` only when producing deliberate slow motion; a
native-cadence comparison should use the simulation/display cadence. Playback
is only a native-cadence visual test when the player and monitor can present
that rate. For example, playing a 180 fps file on a 120 Hz display necessarily
drops frames unevenly and can manufacture apparent judder; use frame stepping
or an explicitly labelled slow-motion encode when reviewing such a sequence on
a lower-refresh display.

For transition validation, the RPM sweep keeps the camera and wheel phase fixed
while ramping one persistent planner from 0 to 24 rps. It writes an annotated
video, contact sheet, SVG energy curve, per-frame luminance TSV and Markdown
report. The default 481 frames give 0.05-rps increments at 120 Hz, exercising
both the 0.5–2.5-pixel activation range and 8–12°/frame band transition. The
annotated MP4 defaults to the same 120 fps capture cadence; pass an explicit
`--video-fps` only to create deliberately retimed playback. Unless overridden,
the artifact directory is likewise named from `--fps` (for example,
`build/rpm-sweep-band-120hz`):

```bash
wheel-mesh-lab/tools/render_rpm_sweep.py --fps 120
```

The luminance metric is a linearized Rec.709 LDR proxy with border background
removed, not an HDR radiometric measurement. Keeping pose fixed makes changes
in that curve attributable to temporal planning/compositing rather than tread
phase. Its manifest and report include `bloom_correction_blend` separately from
the overall temporal blend, making it explicit that the band can activate
the phase-invariant representation without directly changing residual energy.

`reference` is a quality oracle rather than a proposed mobile frame cost.
`band` is retained to reproduce the earlier one-pitch morph. `adaptive-raw`
and `split-raw` isolate the old physical-integration methods without an
anti-aliasing endpoint. They are diagnostic controls, not recommendations.

### Alias-safe production mode

`alias-safe` treats repeated-detail aliasing as a representation problem.
It is retained as the mesh-crossfade implementation closest to the current
Android renderer. Clean `adaptive` and `split` instead calculate the same
high-speed mean as the endpoint of their one-pass harmonic filter. With
eighteen equal grooves, one repeat is 20°. The compatibility crossfade and
the clean fundamental cutoff begin at 0.35 groove
cycles per presented frame (7°) and reach their phase-independent endpoint at
0.50 cycles (10°), before two adjacent presentations can select an ambiguous
groove phase. The crossfade follows a stateless smoothstep on every frame. It is
not passed through the legacy 0.25° LOD deadband: holding a continuously
weighted presentation blend created visible plateaus and brightness jumps during
speed changes.

In `alias-safe`, each physical-groove exposure weight is scaled by
`1-bandBlend`; one tread-only shell is accumulated with weight
`0.26164*bandBlend`, where `0.26164` is the measured angular duty cycle of all
eighteen real grooves. Both contributions are premultiplied before source-over
composition and bloom. This preserves integrated light instead of stacking a
fully bright synthetic shell over fading geometry. The hub, carcass and side
rings use the roll-invariant body pass, and the shell is pinned to a fixed roll
phase, so polygon facets cannot introduce a second apparent rotation.

At full band weight, physical groove atlas work is skipped and one continuous
band draw remains. Faster rotation and a presentation hitch therefore cannot
increase temporal work. Low speed still shows the truthful physical chevrons;
high speed becomes independent of both wheel phase and the display's alias
frequency.

The dedicated check verifies both claims for `adaptive`, `split`, and the
`alias-safe` compatibility name at 120 and 180 Hz: several high-speed physical
phases must produce identical output, while two stopped-wheel phases must remain
visibly different. It also verifies that `adaptive-raw` and `split-raw` still
produce distinct diagnostic images instead of silently becoming aliases for
the guarded modes.

```bash
wheel-mesh-lab/tools/check_alias_safe_phase_invariance.py
```

### Stutter diagnostics and timing replay

Do not use average FPS as the stutter test. The live trace records loop, CPU
render stages, swap wait/return, submitted wheel pose, filter delta, and every
representation control in an allocation-free memory buffer. Optional GPU and
window-system instrumentation add their results to the same frame rows without
turning swap return into a claimed scanout timestamp. The trace is written only
after the window closes.

Run the complete clear-control plus `sharp`/`adaptive`/`split` comparison with
one command (four sequential windows):

```bash
python3 wheel-mesh-lab/tools/run_live_stutter_diagnostic.py \
  --duration-seconds 8 --swap-interval 1
```

Nested and virtual compositors sometimes advertise a nominal refresh rate that
does not match the cadence of their outer display. When that mismatch has been
verified independently, pass `--nominal-hz HZ`. The runner records the override,
passes it to the clear-control trace, and locks the wheel planner's explicit
`--fps` to the same value. This changes only diagnostic thresholds/planning; it
does not limit or accelerate buffer swaps.

It writes each raw trace and analysis plus `comparison.json` and `report.md`,
and only attributes a fault to the wheel when it is absent from the clear-only
control. Wheel input is locked during these scripted captures (Escape still
closes the window), preventing an accidental camera move, shader reload, export,
or model switch from contaminating a timing result. The runner explicitly
requests `--model mint`; each trace records the
model slug, configured mint groove count, requested/effective temporal modes,
effective temporal source, and whether temporal grooves were available. A
violet selection or silent temporal-mode fallback is therefore a hard diagnostic
failure rather than a misleading adaptive/split result.

```bash
./wheel-mesh-lab/build/wheel_mesh_lab \
  --model mint --preset tread --temporal-mode adaptive \
  --spin-rps 3.5 --auto-roll --phase-clock scheduled \
  --diagnostic-seconds 8 \
  --frame-timing-trace wheel-mesh-lab/build/stutter/live.tsv \
  --gpu-timing \
  --presentation-events \
    wheel-mesh-lab/build/stutter/live-presentation-events.tsv

python3 wheel-mesh-lab/tools/analyze_frame_timing.py \
  wheel-mesh-lab/build/stutter/live.tsv
```

`--gpu-timing` requires `GL_EXT_disjoint_timer_query`. It inserts four GPU
timestamps per frame into a preallocated 256-frame query ring. Results are read
only after the final query reports availability, so the live loop never waits
for a query result. `gpu_query_latency_frames` records that asynchronous delay;
`disjoint`, `ring_full`, `invalid_timestamps`, and `pending_at_shutdown` rows are
kept as invalid evidence rather than silently converted into durations. The
stage columns mean:

- `gpu_setup_ms`: GPU work between frame start and the scene-pass boundary,
  including render-target resize/setup work, but not elapsed CPU planning time;
- `gpu_scene_ms`: the wheel scene and, when active, temporal
  accumulation/resolve work up to the bloom boundary;
- `gpu_bloom_ms`: bloom extraction/blur/composite work through the final screen
  target;
- `gpu_frame_ms`: the complete timestamped GPU interval across those three
  stages. It excludes screenshot readback, `glfwSwapBuffers`, event polling, and
  CPU time.

The raw GPU start/end timestamps are in the GPU query clock domain. They are
useful for durations but must not be subtracted from CPU or X11 timestamps.
Final collection may call `glFinish` after capture has ended; that shutdown work
is outside the per-frame CPU and swap measurements.

`--presentation-events FILE.tsv` currently requires the native X11/GLX context
and a `--frame-timing-trace`; it cannot be combined with
`--egl-window-context`. It enables two deliberately separate observations:

- `GLX_SGI_video_sync` is sampled immediately before and after each swap. Its
  32-bit counter tracks physical display-pipe retraces, and
  `scanout_counter_delta` shows how many retraces elapsed across the call. It
  does **not** identify which application buffer was displayed and is not a
  per-frame presentation timestamp.
- X Present `CompleteNotify` events are collected asynchronously through a
  separate XCB connection so GLFW cannot consume them. Each observed pixmap
  completion is written as its own row in the requested presentation-events
  file, with candidate submission, local arrival, raw UST, MSC, serial, mode,
  and deltas.

A candidate X Present mapping is accepted as exact only after the complete run
has one completion for every submitted swap, sequential frame candidates,
strictly increasing UST/MSC, and no `skip` completion. Otherwise every event is
left `unmatched`, `presentation_exact_mapping` remains `0`, and the analyzer
keeps physical presentation `UNKNOWN`. On the tested NVIDIA 580.173.02
X11/GLX setup, the Present extension can be selected but NVIDIA GLX emits zero
pixmap completion events; the presentation-events file therefore contains only
its header and the frame trace reports `xpresent_no_pixmap_events`. The SGI
retrace counter is still valid display-pipe evidence, but it does not change
that `UNKNOWN` physical per-frame presentation verdict.

The report deliberately gives separate verdicts for CPU budget, submitted-pose
continuity, representation stability, GPU workload, swap-return cadence,
display-pipe retrace observations, and presentation-completion availability. A
capture under three seconds is inconclusive for cadence recurrence. GLFW swap
return is queue back-pressure; it is **not** a confirmed physical scanout
timestamp. On a platform without validated completion feedback, physical
display cadence remains `UNKNOWN` without a high-speed-camera capture.

The instrumented run produces these diagnostic artifacts:

- `live.tsv`: CPU stages, swap timing, wheel identity/planner state, async GPU
  results, before/after retrace counters, and the run-wide completion source,
  event count, and exact-mapping flag;
- `live-presentation-events.tsv`: a separate asynchronous X Present event
  stream, joined to `live.tsv` by `diagnostic_run_id`; a header-only file is a
  valid record that no pixmap completion feedback arrived;
- the analyzer output directory: `summary.json`, `report.md`, and `timing.svg`,
  which keep GPU, retrace, swap-return, and physical-presentation conclusions
  separate.

For a machine-readable view of what GLES actually produced before presentation,
add `--buffer-dump-dir` to a deterministic sequence, timing replay, or smoke
capture:

```bash
./wheel-mesh-lab/build/wheel_mesh_lab \
  --model mint --preset tread --temporal-mode adaptive \
  --spin-rps 2 --fps 120 --sequence-frames 120 \
  --sequence-dir wheel-mesh-lab/build/render-truth/frames \
  --buffer-dump-dir wheel-mesh-lab/build/render-truth/buffers \
  --width 320 --height 240
```

The destination must be empty and bloom must remain enabled. `capture.json`
declares the `wheel-render-truth-v1` schema, exact dimensions, formats, model and
requested/effective modes. `frames.tsv` provides the searchable summary. Every
`frame-NNNNN/` directory contains:

- `final.rgba8`: the exact full-resolution image submitted by the application;
- `scene.rgba8`: resolved wheel and temporal core before bloom;
- `bloom.rgba8`: the actual quarter-resolution, post-blur bloom buffer;
- `emission.rgba32f`: the RGBA16F temporal target read as little-endian float32,
  containing premultiplied groove radiance and weighted alpha coverage; it is
  intentionally absent on sharp frames;
- `frame.json`: source classification, phase/delta, sample phases and weights,
  planner controls, and hashes of every available buffer.

Rows and raw images use OpenGL's bottom-left origin. The source classification is
truthful about `harmonic_shell`, sampled physical grooves, a motion band, or a
physical-plus-band mixture; a single object-ID image would be ambiguous for
weighted temporal overlap. Readback is synchronous, so this mode is deliberately
headless-only and must not be interpreted as a live cadence measurement.

Analyze the raw stages and generate a machine-readable summary plus a visual
four-stage contact sheet:

```bash
python3 wheel-mesh-lab/tools/analyze_render_truth.py \
  wheel-mesh-lab/build/render-truth/buffers \
  --output-dir wheel-mesh-lab/build/render-truth/analysis
```

The analyzer detects complete holds as well as partial slowdown/catch-up pairs
from raw-emission change per commanded phase degree. It also checks emission
energy/coverage, bloom support and thickness, and identifies the first stage
whose output stopped changing. `overview.png` shows emission, scene, bloom and
final output side by side for representative or failing frames.

Use the renderer-independent control to tell a wheel regression from a window
system/driver event. It clears one color, swaps, and polls—there are no wheel
meshes, shaders, bloom passes, or model updates:

```bash
./wheel-mesh-lab/build/wheel_cadence_probe \
  --duration-seconds 8 --swap-interval 1 \
  --trace wheel-mesh-lab/build/stutter/control.tsv \
  --presentation-events \
    wheel-mesh-lab/build/stutter/control-presentation-events.tsv
```

The control trace receives the same before/after SGI retrace-counter fields, and
its separate presentation-events file follows the same strict mapping rules.
It has no GPU stage timers because it deliberately performs only clear, swap,
and event polling.

A recorded live trace can be replayed without a compositor. Replay sends its
`loop_delta_ms` through the same scheduled or legacy phase-clock policy, renders
each submission, then expands `swap_interval_ms` onto cumulative nominal refresh
slots. Long intervals therefore repeat the previously submitted image and
queued returns can skip submissions without rounding drift:

```bash
./wheel-mesh-lab/build/wheel_mesh_lab \
  --frame-timing-replay wheel-mesh-lab/build/stutter/live.tsv \
  --sequence-dir wheel-mesh-lab/build/stutter/replay \
  --model mint --preset tread --temporal-mode adaptive --spin-rps 3.5 \
  --phase-clock scheduled --width 320 --height 240
```

Replay writes `submissions.tsv`, a nominal-CFR `manifest.tsv`, hard-linked PPM
frames, and `qa-timing.svg`. `source_gray_code` and the SVG are QA sidecars only;
they never alter production pixels. The fault-injection test proves that an
eight-slot stall produces hold/catch-up with `previous-delta`, while `scheduled`
keeps every submitted pose bounded and continuous. It also reruns the same
trace and compares manifests and PPM hashes.

For what the eye receives, build a low-speed phase template in the same mode,
resolution, camera, and bloom configuration, then run the image-space analyzer:

```bash
./wheel-mesh-lab/build/wheel_mesh_lab \
  --sequence-dir wheel-mesh-lab/build/stutter/templates \
  --sequence-frames 90 --fps 180 --spin-rps 0.111111111 \
  --model mint --preset tread --temporal-mode adaptive \
  --width 320 --height 240

python3 wheel-mesh-lab/tools/analyze_perceptual_stutter.py \
  --sequence wheel-mesh-lab/build/stutter/replay \
  --templates wheel-mesh-lab/build/stutter/templates \
  --output-dir wheel-mesh-lab/build/stutter/perceptual-report
```

That analysis learns a complex phase basis from actual tread pixels and reports
apparent speed, holds, catch-up, periodic beats, glow energy, contrast, alias
safety, and motion legibility at three spatial scales. Its seeded tests include
duplicated frames, the previous-delta bug, a gradual glow pulse, and an unsafe
above-Nyquist pattern. Above half a groove cycle per frame, eighteen identical
grooves cannot communicate unambiguous motion; a phase-invariant result can pass
alias safety while correctly reporting `CUE_ABSENT` rather than claiming that
the wheel visibly rotates.

### Raw trailing frame-split diagnostic

`split-raw` preserves the literal alternative algorithm for diagnosis. The
ordinary `split` mode uses the same trailing-box shutter shape, but evaluates
the periodic integral analytically in one draw instead of changing between an
integer number of midpoint samples. `split-raw`'s default
`--max-roll-step-deg` is **1.5°**.
If the latest presented roll delta satisfies `abs(D) <= maxStep`, it renders the
exact current pose with no temporal replacement and no half-frame lag. Above
the threshold it uses

```text
parts = ceil(abs(D) / maxStep)
sample[i] = previous + (i + 0.5) / parts * D
weight[i] = 1 / parts
```

Only `mint_groove_emissive` is sampled at those roll angles. The body, hub and
side emission stay at the current pose, so there is no yaw/pitch translation or
side-light trail. No sample is extrapolated after the current pose.
Interactive auto-roll and ordinary sequences preserve an unwrapped phase,
including direction and whole turns. The default live clock advances by a
slowly tracked scheduled interval and never repays one delayed swap as a single
pose jump. Exact-pose smoke captures and `--sequence-fixed-phase` deliberately
synthesize `RPS * 2*pi / FPS`, allowing a moving-exposure result to be inspected
without changing the displayed center pose.

In the interactive window, `--fps` is not a frame limiter. GLFW presents at the
native swap cadence, and the title reports a smoothed `~N fps actual` value.
Reference, raw, band and alias-safe planners use the selected live phase-clock
interval. Clean adaptive/split filtering comes from the video mode of the
monitor containing the largest part of the window (with primary-monitor and
configured-`--fps` fallbacks). The title reports both measured loop cadence and
the clean filter's nominal Hz.
Deterministic headless captures continue to use `--fps` exactly.

This is a full-frame **trailing box shutter**, not the centered 0.75-frame Hann
shutter used by `reference` and `band`. It therefore has roughly half a frame of
visual phase lag whenever active, and crossing `maxStep` is intentionally a
discrete change from sharp-current to a two-or-more-sample exposure. The labels
on generated comparisons call out that difference explicitly.

The raw planner stores samples dynamically. `split-raw` interactive rendering
has a hard 64-sample work budget and a 0.20-part hysteresis band around split-count
boundaries, preventing timing jitter or a hitch from producing unbounded work
or rebuilding the entire sample lattice repeatedly. Offline/headless captures
retain the 128-sample ceiling, so `split-oracle` behavior remains available.
If either ceiling is reached, the manifest sets `sample_cap_applied=1`; all
rendered samples still span the complete previous-to-current interval and
retain normalized total weight, so exposure extent and energy are preserved
even though the requested maximum spacing can no longer be honored. This desktop experiment's
sample counts cannot be copied directly into Android's current fixed 12-cell
atlas: with the 1.5° default, 120 Hz already needs 20 samples at 10 rps and 45
at 22.3 rps (and exceeds 12 above about 6 rps). The 128 ceiling covers the
default 1.5° step throughout the supplied 0–24 rps sweep at 120 Hz without
capping. Clean `split` has no physical split lattice or sample-count boundary;
it uses one analytic emission draw at every speed.

More subdivisions improve only the numerical approximation of this trailing
box integral; they do not turn it into a temporal anti-aliasing guarantee. For
the wheel's repeating 20° groove pitch, a 10° box still retains about 63.7% of
the fundamental pattern contrast (`abs(sinc(pi/2))`). The first zero is at a
20° exposure, and wider boxes have sinc sidelobes whose sign can invert the
apparent groove phase. The legacy `band` experiment avoids that failure by
forcing a one-pitch sample average. `alias-safe` is cleaner and cheaper at its
endpoint because it uses dedicated continuous geometry instead of approximating
that average with many discrete poses.

Inspect the deliberately unprotected algorithm with a different angular bound:

```bash
./wheel-mesh-lab/run.sh --model mint --preset tread --temporal-mode split-raw \
  --max-roll-step-deg 1.5 --spin-rps 22.3 --fps 120 --auto-roll
```

The comparison helper defaults to labelled
`adaptive-raw / adaptive / split-raw / split` tiles and writes both
`comparison.mp4` and `contact-sheet.png`:

```bash
wheel-mesh-lab/tools/render_temporal_suite.py \
  --output-dir wheel-mesh-lab/build/temporal-split-22.3rps-120fps \
  --rps 22.3 --fps 120 --frames 120 --preset tread
```

For a fair quality check against the same trailing box kernel, the helper also
offers `split-oracle`. It invokes the identical `split-raw` renderer with 128 equal
midpoint samples instead of comparing its result only against the differently
centered Hann reference:

```bash
wheel-mesh-lab/tools/render_temporal_suite.py \
  --modes split-raw split-oracle --split-oracle-samples 128 \
  --output-dir wheel-mesh-lab/build/temporal-split-vs-box-oracle \
  --rps 22.3 --fps 120 --frames 120 --preset tread
```

Export both prototypes without opening a window:

```bash
./wheel-mesh-lab/run.sh --export-all
```

For a noncanonical design comparison, make some chevrons dark and export that
same variant:

```bash
./wheel-mesh-lab/run.sh --model mint --mint-glow-count 6
./wheel-mesh-lab/run.sh --export-all --mint-glow-count 6
```

`--mint-glow-count` accepts values from 1 through 18. Omitting it uses the
canonical all-eighteen-lit gameplay model. Smaller values are deliberately
noncanonical experiments; they keep all eighteen chevrons as real geometry and
assign the non-luminous remainder to `mint_tread`.

OBJ exports contain `v`, `vn`, `f`, groups, and material assignments. They are
used directly by Android's multipart material loader. The distinct groove,
motion-band and side-emission material names are also the semantic contract used
by the temporal renderer.

The concept sheets and the `grooves.png` single-mitre chevron sketch used as
visual references are kept in `reference/`.
