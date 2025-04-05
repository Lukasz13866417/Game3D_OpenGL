---

# OpenGL 3D Game From Scratch

> **A fully custom 3D game engine built on OpenGL for Android, featuring a robust terrain generation API and lazy, on-the-fly world creation.**

## Table of Contents
1. [Introduction](#introduction)
2. [Key Features](#key-features)
3. [Screenshots](#screenshots)
4. [Project Structure](#project-structure)
5. [Terrain Generation & SymbolicGrid API](#terrain-generation--symbolicgrid-api)
6. [Technical Highlights](#technical-highlights)
7. [Building & Running](#building--running)
8. [Future Plans](#future-plans)
9. [License](#license)
10. [Acknowledgments](#acknowledgments)

---

## Introduction
This project began as an experiment to render 3D graphics on Android using the **Canvas** API—an unconventional approach that required numerous tricks (like face-culling, Painter’s algorithm modifications, and more) to achieve acceptable performance. However, the limitations of 2D rendering soon became a bottleneck.

The codebase has since evolved into a **full OpenGL** rendering solution, complete with:
- A custom terrain-generation API for building large, detailed worlds on the fly.
- An object-oriented structure that separates rendering logic from game logic.
- A focus on performance and low-level optimizations, including preallocation to minimize the impact of garbage collection.

## Key Features
- **Custom 3D Rendering Pipeline**: Built on OpenGL, encapsulated in clean classes like `Camera`, `Object3D`, and more.
- **Modular Terrain API**: Create subclasses of `TerrainStructure` to define tiles, place “addons” (like spikes or potions), and customize your landscape.
- **Lazy Terrain Generation**: Terrain structures can have child structures, which are compiled into commands and then “interpreted” at runtime to generate the environment as needed.
- **SymbolicGrid Integration**: Efficient 2D grid queries and randomization, enabling complex terrain features and item placement without heavy performance hits.
- **Collision Detection**: Leveraging the Möller–Trumbore algorithm (and other geometric routines) for accurate collision and ray-plane intersections.
- **Performance-Focused**: Preallocation, minimal heap allocations, and specialized data structures ensure smooth gameplay and quick load times on mobile devices.

## Screenshots
Below is one of the game stages rendered via OpenGL:

![Screenshot of Game Stage](https://github.com/user-attachments/assets/2b78fd37-cfe1-4630-b902-ea5328005814)

*In the screenshot, you can see the terrain tiles, in-game objects, and the 3D perspective managed by the camera.*

## Project Structure
```
app
├── AndroidManifest.xml
└── java
    └── com.example.game3d_opengl
        ├── game
        │   ├── stages
        │   │   ├── GameplayStage
        │   │   ├── Stage
        │   │   ├── TestStage
        │   │   └── TestStage2
        │   ├── terrain_api
        │   │   ├── addon
        │   │   │   └── Addon
        │   │   ├── grid.symbolic
        │   │   ├── main
        │   │   ├── terrainutil
        │   │   └── Tile
        │   ├── terrain_structures
        │   │   └── TerrainLine
        │   ├── track_elements
        │   │   └── Player
        │   └── WorldActor
        └── rendering
            ├── object3d
            │   ├── Camera
            │   ├── Icon
            │   ├── ModelRenderer
            │   ├── Object3D
            │   └── Polygon3D
            ├── util3d
            │   ├── rColor
            │   ├── rCamera
            │   ├── rGameMath
            │   ├── rGameMiscUtil
            └── MyGLRenderer
                MyGLSurfaceView
                OpenGL2DSActivity
```
- **`game`**: Core game logic, player handling, and world interactions.
- **`terrain_api`**: Classes and interfaces for procedural terrain generation. Includes the `SymbolicGrid` integration.
- **`rendering`**: All OpenGL and 3D rendering classes, from camera setup to object definitions.

## Terrain Generation & SymbolicGrid API
The **terrain generation** system is designed for flexibility and performance. You can:
- **Extend `TerrainStructure`** to define your own shape, tile arrangement, or layering logic.
- Place **addons** (items, spikes, power-ups, etc.) on a grid, orchestrated by the `SymbolicGrid` library.
- Enjoy **lazy loading**: your custom `TerrainStructure` classes compile their commands, which the engine interprets later, generating only what’s needed in real-time.

### Why SymbolicGrid?
[SymbolicGrid](https://github.com/Lukasz13866417/SymbolicGrid) is a separate library for efficient, randomized 2D grid queries. It significantly boosts performance by:
- Reducing memory overhead with specialized data structures.
- Minimizing random lookups and generation overhead.
- Supporting large, on-demand worlds without major slowdowns or stutters.

## Technical Highlights
- **Custom Projection**: Implemented the mathematics of 3D point projection onto a 2D plane, enabling a deeper understanding of how shapes are rasterized.
- **Depth Buffer Handling**: Learned and implemented standard depth buffer logic to correctly render overlapping objects.
- **Collision & Intersection**: Uses Möller–Trumbore for ray-plane (and ray-triangle) intersection, ensuring precise collision detection and interactive gameplay.
- **Preallocation**: To avoid frequent garbage collection, data structures (tiles, addons, etc.) are allocated in pools and reused wherever possible.

## Building & Running
1. **Clone the Repository**  
   ```bash
   git clone https://github.com/Lukasz13866417/YourGameRepo.git
   ```
2. **Open in Android Studio**  
   - Select “Open an Existing Project” and point to the cloned folder.
3. **Build the Project**  
   - Let Gradle sync and resolve all dependencies.
   - Compile via the standard “Run” or “Build” options in Android Studio.
4. **Install on Device/Emulator**  
   - Connect an Android device or use an emulator, then press “Run.”
   - Once installed, the game should launch automatically.

## Future Plans
- **Additional Terrain Features**: Support for dynamic water, weather effects, or day-night cycles.
- **AI and Pathfinding**: Add basic or advanced NPC behaviors with pathfinding (potentially integrated with `SymbolicGrid`).
- **Multiplayer Support**: Investigate the feasibility of real-time or turn-based multiplayer modes.
- **Level Editor**: An in-game or external level editor to design terrain structures and place addons visually.
- **Refined Collision System**: Extend the Möller–Trumbore approach to handle complex polygons or skeletal animations.

## License
*(Choose a license that fits your needs—MIT, Apache, GPL, etc.)*

```
MIT License (example)
Copyright (c) 20XX ...

Permission is hereby granted, free of charge, to any person obtaining a copy of 
this software and associated documentation files ...
```

