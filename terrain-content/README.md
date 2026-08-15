# Terrain content

This directory is the checked-in source and publication boundary for gameplay terrain.

- `catalog.terrain-catalog.json` is the explicit, ordered source catalog. The six Java providers
  stay first so an otherwise empty custom catalog preserves the existing level selection.
- `structures/` contains editable `*.terrain-structure.json` documents.
- `levels/` contains editable `*.terrain-level.json` documents.
- `published/terrain/runtime-catalog.json` is the normalized, self-contained runtime artifact
  consumed by Android and the simulator. It is generated; drafts are never packaged directly.

Use `./gradlew publishTerrainContent` after saving valid source documents. Normal Android builds
run `validateTerrainContent`, which validates the last good published artifact without making an
invalid draft part of the game.

Launch the desktop editor with `./gradlew :terrain-editor:run`.
