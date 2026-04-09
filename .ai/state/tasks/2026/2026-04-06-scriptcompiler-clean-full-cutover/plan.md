# Plan

- [x] Reproduce the clean-snapshot Helidon `compile_gate` failure and
  keep sampling after each major cut until the dominant hotspot is
  clear.
- [ ] Remove exact normalized guard algebra from the analyzer inner
  loop. Branch propagation and CFG merge must stop rebuilding large
  `Decision` unions through `guards.and(...)` / `Decision.of(...)` on
  every edge.
- [ ] Replace the current path-only transfer shape with direct abstract
  fact refinement plus cheaper pointwise merge logic, then keep exact
  guard reconstruction at the rendering / projection boundary.
- [ ] Keep `Expression.reduce()` cosmetic-only during compilation and
  analysis; do not reintroduce eager reduction as a shortcut.
- [ ] Preserve the current extraction boundary: `Variations` stays
  data-only and `VariationEngine`, `ScriptInliner`,
  `ScriptIndexer`, and `Flow` remain the standalone preparation /
  compute surface.
- [ ] Keep the focused validation slices green after each attempt and
  treat `diff_projects` as externally blocked until baseline `HEAD`
  generation stops failing with `Unresolved variable: security.atz`.

The task is done only when the runtime path stays on the extracted
standalone helpers and the remaining compile-gate regression is gone.
