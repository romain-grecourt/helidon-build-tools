# Decisions

## Track This As A New Task

Do not reopen the completed `scriptcompiler-phase2-kickoff` task. Keep
that record intact and track the user-requested stricter bar in this
separate follow-up.

## No Compatibility Bridge

The acceptance bar for this task is a clean end-to-end cutover to the
new API. `ScriptCompiler` must not retain a private reachability /
constraint model behind `FlowProjector` or any similar bridge.

## Cut Over Variations Too

A partial `ScriptCompiler` cleanup is not enough. `Variations` must
stop using `ScriptCompiler` compatibility helpers and consume
analysis-derived state directly.

## Preserve Current Validation Bar

Regression proof stays the same until the external Helidon baseline
issue changes: keep the focused engine-v2 tests green, keep
`compile_gate` and `diff_variations` green, and continue treating
`diff_projects` as blocked by baseline `HEAD`
`Unresolved variable: security.atz`.

## Keep Reduction Cosmetic

Do not reintroduce eager `Expression.reduce()` during compilation or
analysis. The current boundary keeps reduction cosmetic-only except for
the outer `Flow.expression(Guard, Scope)` rendering step retained for
`VariationsTest.testVariationsConditionals`.

## Current Performance Target

The current Helidon blocker is no longer QMC reduction. Resume
performance work from raw residual OR-expression growth in
`Domain.Guards.or(...)` / `Flow.Analyzer.mergeGuard(...)`, not by
restoring broad normalization across analysis.

## Treat The Current Compile-Gate Failure As A Design Bug

After the scalar and membership bitset passes, the remaining Helidon
failure is not a narrow data-structure hotspot anymore. The analyzer is
still rebuilding exact normalized `Decision` guards on every branch
transfer and join, and Helidon now spends minutes inside repeated
`Decision.of(...)` / `DecisionShape.subsetOf(...)` normalization.

Do not keep chasing this with more `subsetOf()` micro-optimizations.
The next fix must move exact guard algebra out of the analyzer inner
loop and make forward analysis operate on a cheaper abstract state.

## Prefer Direct Abstract-State Refinement In Flow

The intended next cut is:

- refine branch facts directly during propagation instead of calling
  full `guards.and(...)` on every edge;
- join environments pointwise at CFG merges instead of unioning large
  exact `Decision` sets in the hot path; and
- keep exact guard / expression reconstruction at the projection or
  rendering boundary, or under a much tighter bounded representation.
