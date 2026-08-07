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
sudo apt install build-essential cmake pkg-config libglfw3-dev libglm-dev libgles-dev
```

From the project root:

```bash
./wheel-mesh-lab/run.sh
```

The script configures and builds in `wheel-mesh-lab/build/`, then starts the lab.
This machine was verified with OpenGL ES 3.2 on its NVIDIA driver.

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
| `E` | Export selected model to `exports/` |
| `P` | Save the current view to `screenshots/` |
| `H`, `R` | Hot-reload shaders, reset view |

The window title always reports the selected model, shader mode, bloom state,
and isolated submesh.

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

The mint model is a conventional monowheel with a recessed hub, continuous
mitred chevron grooves, two mint side rings, and a configurable number of
evenly spaced luminous chevrons. The default is four. The
violet model is one wheel with sixteen armor pods distributed **around its
circumference**. Its wider gaps are split into nested groups of four primary,
four secondary, and eight detail grooves. Gameplay crossfades those groups as
the visual rotation per frame rises. Both side rings remain luminous. It is
never a stack of pieces along the axle.

At the highest visual speed, gameplay crossfades the dark violet under-core
toward the energy color. This reads as a stable purple motion glow instead of
bright spots jumping between render frames.

Each color/material is a separate `MeshPart` and draw call. This is intentional:
the current Android `ModelCreator` ignores OBJ `usemtl`/MTL data and
`PlayerAssets` applies one uniform material to the whole player. Before either
prototype enters gameplay, that path must preserve submeshes or load one OBJ per
material.

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

CTest also compares all eight copied player/bloom shader stages, bloom
constants, and shared cylinder dimensions against their Java sources. A future
gameplay shader or player-proxy edit therefore fails the lab parity test until
its preview copy is intentionally updated.

Deterministic hidden-window GPU captures:

```bash
./wheel-mesh-lab/run.sh --smoke-test --model mint --preset side
./wheel-mesh-lab/run.sh --smoke-test --model violet --preset tread
```

Export both prototypes without opening a window:

```bash
./wheel-mesh-lab/run.sh --export-all
```

Try a different number of green grooves in the lab, then export that same
version for gameplay:

```bash
./wheel-mesh-lab/run.sh --model mint --mint-glow-count 6
./wheel-mesh-lab/run.sh --export-all --mint-glow-count 6
```

`--mint-glow-count` accepts values from 1 through 18.

OBJ exports contain `v`, `vn`, `f`, groups, and material assignments. They are
useful for inspection now and intentionally expose the Android material-loader
work needed later.

The concept sheets and the `grooves.png` single-mitre chevron sketch used as
visual references are kept in `reference/`.
