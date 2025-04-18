# OpenGL 3D Game From Scratch
> **A fully custom 3D game engine built on OpenGL for Android, featuring a self-made, cutting-edge terrain generation API.**

## Introduction
This project began as an experiment to render 3D graphics on Android using the **Canvas** API—an unconventional approach that required numerous tricks (like face-culling, Painter’s algorithm modifications, and more) to achieve acceptable performance. However, the limitations of 2D rendering soon became a bottleneck.

The codebase has since evolved into a **full OpenGL** rendering solution, complete with:
## Key Features
- **Custom 3D Rendering Pipeline**: Built on OpenGL, encapsulated in clean classes like `Camera`, `Object3D`, and more.
- **Modular Terrain API**: Create subclasses of `TerrainStructure` to define tiles, place “addons” (like spikes or potions), and customize your landscape.
- **Lazy Terrain Generation**: Terrain structures can have child structures, which are compiled into commands and then “interpreted” at runtime to generate the environment as needed.
- **SymbolicGrid Integration**: Efficient 2D grid queries and randomization, enabling complex terrain features and item placement without heavy performance hits.
- **Performance-Focused**: Preallocation, minimal heap allocations, and specialized data structures ensure smooth gameplay and quick load times on mobile devices.

## Screenshots
![Screenshot of Game Stage](https://github.com/user-attachments/assets/2b78fd37-cfe1-4630-b902-ea5328005814)

## Project Structure
Packages:
- **`game`**: Core game logic, player handling, and world interactions.
- **`game/terrain_api`**: Classes and interfaces for procedural terrain generation. Includes the `SymbolicGrid` integration.
- **`rendering`**: All OpenGL and 3D rendering classes, from camera setup to object definitions.

## Terrain Generation & SymbolicGrid API
The **terrain generation** system is designed for flexibility and performance.
- All terrain patterns extend the **```TerrainStructure```** class - an API for arranging tiles and placing addons on a grid.
- Terrain structures form a "tree" - each structure can incorporate child structures with their own addons and tiles. 
- The ```Tile``` class can be extended for extra capabilities.
- **lazy loading**:  Instead of instantly generating tiles&addons based on provided structures, the information is turned into commands. At any time, the user can tell the terrain to "interpret" a given number of commands. The commands generated for a structure can be reused. This approach resulted in better performance than running the terrain generation on a separate thread and synchronizing.

### SymbolicGrid
[SymbolicGrid](https://github.com/Lukasz13866417/SymbolicGrid) is my own, self-made library for efficient, randomized 2D grid queries. It significantly boosts performance by:
- Reducing memory overhead with specialized data structures.
- Minimizing random lookups and generation overhead.
- Supporting large, on-demand worlds without major slowdowns or stutters.

## Building & Running
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

This project is licensed under the **Creative Commons Attribution 4.0 International License**.  
You are free to share and adapt the material as long as you provide proper attribution. For more details, please see the [Creative Commons Attribution 4.0 International License](https://creativecommons.org/licenses/by/4.0/).
