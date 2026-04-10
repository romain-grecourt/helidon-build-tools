# Plan

- [x] Reproduce the clean-snapshot Helidon `compile_gate` failure and
  keep sampling after each major cut until the dominant hotspot is
  clear.
- [x] Remove exact normalized guard algebra from the analyzer inner
  loop. Fact/env path algebra and the interned `AnalysisPath` DAG are
  gone, and lowered blocks now carry structured control contexts so the
  analyzer no longer rebuilds exact block-entry `Guard` unions in the
  forward pass.
- [x] Replace the current path-only transfer shape with path-free
  pointwise fact analysis plus block-id provenance for
  `definedUnder` / exact cases, then keep exact guard reconstruction at
  the boundary.
- [x] Tighten the remaining exact-case merge path so bounded
  provenance stays cheap under Helidon. Exact cases now merge through
  bounded block-id provenance rather than eager `or(...)` over exact
  path guards.
- [x] Replace exact control-path merging in `analyzeControl()` with a
  cheaper reachability model so block joins stop spending CPU in
  `Guards.or()` / `DecisionShape.subsetOf()`. Wrapper run
  `20260409-170801-45716` now passes `compile_gate` in `2.10s`
  versus the `5s` threshold.
- [ ] Keep `Expression.reduce()` cosmetic-only during compilation and
  analysis; do not reintroduce eager reduction as a shortcut.
- [ ] Preserve the current extraction boundary: `Variations` stays
  data-only and `VariationEngine`, `ScriptInliner`,
  `ScriptIndexer`, and `Flow` remain the standalone preparation /
  compute surface.
- [ ] Keep the focused validation slices green after each attempt and
  treat `diff_projects` as externally blocked until baseline `HEAD`
  generation stops failing with `Unresolved variable: security.atz`.
  The focused slice and `compile_gate` are green again. Explicit
  baseline rerun `20260409-192148-71454` against the user-requested
  baseline `333e06f877c7bb948e23daa203e2062922bf6ac5` now passes
  `diff_variations` with no `projects.csv` churn, so `diff_projects`
  is unblocked. The broader
  `ScriptCompilerTest` failures
  (`testPresetTypeMismatch`, `testOpenDomainComplement`) are now fixed,
  the broader
  `ExpressionTest,ScriptCompilerTest,VariationsTest` slice is green,
  and wrapper run `20260409-200653-75357` passes both `compile_gate`
  and `diff_variations` against the same baseline. The remaining open
  blocker is now the Helidon `generateOnly` path itself:
  `diff_projects` still cannot compare because baseline-side generation
  in that run fails with `Unresolved variable: security.atz`, and a
  direct current-side `generateOnly` rerun also fails in the same
  checkout with unresolved variables (`security.atz`,
  `metrics.builtin`).

The task is done only when the runtime path stays on the extracted
standalone helpers and the remaining compile-gate regression is gone.
