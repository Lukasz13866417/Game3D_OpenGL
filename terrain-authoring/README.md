# Shared terrain authoring

The shared authoring module uses a compact row/column occupancy matrix as the semantic grid
implementation. It supports the complete public reservation contract: fixed and random fitting
horizontal/vertical strips, random individual fields, horizontal regions, nested propagation,
and explicit blocked child rows. Random choices come only from the materialization-local
`DeterministicRandom` instance.

The original optimized SymbolicGrid implementation is also preserved in this module under
`com.example.game3d.authoring.grid.symbolic`. It is Android-free and Java 8-compatible. Each
symbolic-grid session owns its node pools, scratch storage, and injected `DeterministicRandom`;
there is no process-global capacity or random state.

SymbolicGrid is currently an optional optimization library, not the production backend. The
occupancy matrix and this module's contract tests remain the semantic authority, so extracting the
optimized indexes cannot alter gameplay or content digests. The Android compatibility copy remains
quarantined only until its post-soak deletion.
