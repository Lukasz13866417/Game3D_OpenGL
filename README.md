# OpenGL 3D Game From Scratch

> A custom Android/OpenGL renderer, deterministic 120 Hz simulation, and shared terrain-authoring pipeline.

## Introduction
This began as an experiment to render 3D graphics on Android using the 2D **Canvas** API. It has
since grown into an OpenGL game with a renderer-neutral simulation and terrain model shared by the
Android app, desktop simulator, and JavaFX editor.

The codebase has since evolved into a **full OpenGL** rendering solution, complete with:
## Key Features
- **Custom 3D Rendering Pipeline**: Built on OpenGL, encapsulated in clean classes like `Camera`, `Object3D`, and more.
- **Canonical terrain stream**: immutable `TerrainSnapshot`/`TerrainCommit` records feed physics
  and rendering from one source of truth.
- **Shared authoring**: Java `BaseTerrainStructure` recipes and versioned JSON documents compile
  through the same deterministic Java 8 pipeline.
- **Renderer-neutral addons**: spikes, potions, and portals are sealed core definitions; Android
  owns their GPU resources and animation state.
- **Desktop tooling**: a headless simulator validates physics and a JavaFX editor creates,
  previews, saves, and publishes terrain content.
- **Performance-focused internals**: bounded streaming, retained GPU caches, batching, and
  specialized grid/reservation data structures.
- **Performance-Focused**: Preallocation, minimal heap allocations, specialized data structures, shader tricks, efficient use of GPU resources.

## Project Structure

```text
:game-core          immutable terrain/addons, collision, and deterministic simulation
:terrain-authoring  structures, brushes, reservations, and the bounded gameplay stream
:terrain-io         versioned JSON, catalogs, validation, and atomic publishing
:app                Android/OpenGL presentation and input adapter
:simulator          headless scenarios, traces, and deterministic regression fixtures
:terrain-editor     JavaFX desktop authoring UI
```

`app/.../terrain/terrain_api` and `app/.../terrain/terrain_structures` are compatibility copies
used only by old diagnostic stages and algorithm tests. Production gameplay is guarded against
importing them; new terrain work belongs in `:terrain-authoring` or versioned JSON.

## Terrain Generation and Content

Terrain patterns extend `com.example.game3d.authoring.BaseTerrainStructure` and capture tile and
addon commands through the familiar brush API. A top-level structure is materialized atomically:
connected seams reuse exact coordinates, addon placement is sealed only after a successful build,
and consumers never observe half-authored content. The resulting immutable records are then
published in bounded commits.

A grid is derived from completed geometry for exact or randomized reservations. Random choices use
a materialization-local seed and editor-created random layouts are saved as explicit placements.
[SymbolicGrid](https://github.com/Lukasz13866417/SymbolicGrid) remains the origin of the specialized
reservation algorithms.

The checked-in source catalog lives under `terrain-content/`. Saving a draft and publishing it are
separate operations:

```bash
./gradlew :terrain-editor:run
./gradlew publishTerrainContent
./gradlew validateTerrainContent
```

Publishing validates every enabled entry and atomically replaces the self-contained runtime asset.
Android and the simulator fall back to the six built-in Java levels if that artifact is missing or
invalid.

## Building & Running
Use Android Studio with its bundled JDK for the app. The desktop editor uses the configured JDK 21
toolchain and JavaFX 21; shared terrain modules still target Java 8.

1. **Clone the Repository**  
   ```bash
   git clone https://github.com/Lukasz13866417/Game3D_OpenGL.git
   ```
2. **Open in Android Studio**  
   - Select “Open an Existing Project” and point to the cloned folder.
3. **Build the Project**

   ```bash
   ./gradlew build
   ```

   - Let Gradle sync and resolve all dependencies.
   - Compile via the standard “Run” or “Build” options in Android Studio.
4. **Install on Device/Emulator**  
   - Connect an Android device or use an emulator, then press “Run.”
   - Once installed, the game should launch automatically.

## License

The source code is provided under the [PolyForm Noncommercial License 1.0.0](https://polyformproject.org/licenses/noncommercial/1.0.0/). 
This means you can view, learn from, and modify the code, but you cannot use it for commercial purposes. 

Game assets (art, music, etc.) are licensed under the [CC BY-NC 4.0 License](https://creativecommons.org/licenses/by-nc/4.0/). 

© 2025 Łukasz Staszewski. All rights reserved.
