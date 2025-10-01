# OpenGL 3D Game From Scratch
> **Has a custom 3D game & rendering engine AND a highly optimized terrain generation engine**

## Introduction
This began as an experiment to render 3D graphics on Android using the 2D **Canvas** API. Numerous tricks (aggressive culling, Painter’s algorithm modifications, and more) to achieve acceptable performance. However, this was far from enough for a production-ready game.

The codebase has since evolved into a **full OpenGL** rendering solution, complete with:
## Key Features
- **Custom 3D Rendering Pipeline**: Built on OpenGL, encapsulated in clean classes like `Camera`, `Object3D`, and more.
- **Modular Terrain API**: Create subclasses of `TerrainStructure` to define tiles, place “addons” (like spikes or potions), and customize your landscape.
- **Lazy Terrain Generation**: Terrain structures can have child structures, which are compiled into commands and then “interpreted” at runtime to generate the environment as needed.
- **Data structures optimization**: Custom red-black trees, hashing, segment trees, pre-alloacted data structures.
- **Performance-Focused**: Preallocation, minimal heap allocations, specialized data structures, shader tricks, efficient use of GPU resources.

## Screenshots
![Screenshot of Game Stage](https://github.com/user-attachments/assets/2b78fd37-cfe1-4630-b902-ea5328005814)

## Project Structure
Packages:
- **`game`**: Core game logic, player handling, and world interactions.
- **`game/terrain_api`**: Classes and interfaces for procedural terrain generation. Includes my data structure project  `SymbolicGrid`.
- **`rendering`**: All OpenGL and 3D rendering classes, from camera setup to object definitions.

## Terrain Generation & SymbolicGrid API
The **terrain generation** system is designed for flexibility and performance.
- All terrain patterns extend the **```TerrainStructure```** class - an API for arranging tiles and placing addons on a grid.
- Terrain structures form a "tree" - each structure can incorporate child structures with their own addons and tiles. 
- The ```Tile``` class can be extended for extra capabilities.
- **lazy loading**:  Instead of instantly generating tiles & addons based on provided structures, the information is turned into commands. At any time, the user can tell the terrain to "interpret" a given number of commands. The commands generated for a structure can be reused. This approach resulted in better performance than running the terrain generation on a separate thread and synchronizing.

### Terrain
A grid is built on top of the terrain to allow precise placement of addons (spikes, potions etc). Lots of data structures needed to allow randomization & more advanced queries.
[SymbolicGrid](https://github.com/Lukasz13866417/SymbolicGrid) is my own, self-made library for efficient, randomized 2D grid queries. It significantly boosts performance by:
- Reduced time complexity specialized data structures.
- Minimizing random lookups and heap allocations with pre-allocation.
- Tree-like system of grids (a terrain structure can have child structures who have their own grids within their parent grid)

## Building & Running
Should be very straightforward - no dependencies except what's already provided if you have a standard Android Studio setup
1. **Clone the Repository**  
   ```bash
   git clone https://github.com/Lukasz13866417/Game3D_OpenGL.git
   ```
2. **Open in Android Studio**  
   - Select “Open an Existing Project” and point to the cloned folder.
3. **Build the Project**  
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
