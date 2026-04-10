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

## Exact-Case Provenance Needs Widening Too

Moving state paths onto a cheaper internal DAG was the right cut, but
it exposed the next design pressure immediately: exact-case provenance
can still accumulate too many distinct merged paths and dominate
`Fact.merge()` under Helidon.

Treat exact-case tracking as a bounded precision feature, not an
unlimited exact-history log. Preserve exact singleton scalar facts when
they are cheap, but widen aggressively once merged exact cases stop
paying for themselves.

## Remove Analyzer Path Interning Entirely

The first analyzer rewrite was still the wrong design. Helidon wrapper
run `20260409-163559-38737` proved that even without exact guard
materialization in `Fact.merge()`, the interned `AnalysisPath` DAG still
exploded to about `118M` `AnalysisPath`, `97M` `HashMap$Node`, and
`118M` boxed `Long`.

Do not resume from that model. Keep exact fact provenance out of
hash-interened `and/or/implication` maps.

## Keep Fact Analysis Path-Free

The second rewrite is the new baseline: fact/env propagation no longer
carries path formulas, control reachability is computed separately, and
`definedUnder` / exact cases track block-id provenance that only
materializes `Guard`s on demand. The focused `82`-test engine slice is
green on this shape.

## Control Guard Join Is The New Blocker

After removing the analyzer path maps, the live Helidon blocker moved to
exact block-entry guard merging in
`Flow$Analyzer.enqueueControl()` / `analyzeControl()`. Wrapper run
`20260409-164618-40401` shows the main thread back in
`Domain$DecisionShape.subsetOf()` through `Domain$Guards.or()`, but the
fact/env side and the old memory blow-up are gone.

Resume from replacing exact control-path merging with a cheaper
reachability provenance model. Do not go back to tuning
`DecisionShape.subsetOf()` or reintroducing inner-loop path maps.

## Lowered Blocks Need Structured Control Context

The reachability-only control pass was only half of the fix. Wrapper run
`20260409-170042-43718` proved that reconstructing lowered block paths
from predecessor unions just moved the same `Guards.or()` /
`DecisionShape.subsetOf()` blow-up into
`Flow$Analyzer.materializeBlockGuard()` / `materializeEdgeGuard()`.

For lowered scripts, exact control paths must come from the language's
structured nesting rather than from generic CFG predecessor unions:

- true/false branch-entry blocks extend the parent path with one
  conjunct;
- join blocks reuse the enclosing parent path directly; and
- nested blocks inherit the current structural control context.

Keep the generic predecessor-union path materializer only as a fallback
for manually constructed `Flow.Ir` tests. The real lowered-script path
must use structural control-context ids carried by `Block`.

## Projected Source Guards Must Respect The Current Branch

The packaged `mp/observability` collapse was not a raw
`VariationEngine` issue. `projectSourceGuards()` was replaying
boolean/input control expressions against unconstrained pre-node facts,
so sibling preset exact cases from one branch could make a later nested
input in another branch look unreachable.

When projecting source guards, constrain fact definitions and exact
cases to the current projected branch before using them in
`ExpressionAnalyzer`. Exact cases that only exist on disjoint sibling
paths must not participate in projected reachability.

## Current Helidon Delta Is No Longer Observability

After the projected-facts fix, Helidon wrapper run
`20260409-183624-66348` shows that the packaged
`mp/observability` dimension is restored and the current workspace again
hits Helidon's exact-count gate with `100` total rows.

The remaining `diff_variations` churn against baseline `b47015655` is
entirely `19` removed `mp/database` rows (`18` with
`media.json-lib=jsonb`, `1` with `media.json-lib=jackson`) and no added
rows. Do not treat the old observability collapse as the remaining
blocker anymore.

## Keep `diff_projects` Blocked Until The 19-Row Delta Is Resolved

The regression workflow guardrail still applies: stop at
`diff_variations` while `projects.csv` differs. Even though the timing
gate is green and `~/.m2` restoration is correct, do not run
`diff_projects` until the remaining `mp/database` removals are either
validated as intentional or fixed.

## Use The Exact Requested Baseline Ref

For the Helidon regression workflow, treat the current tip of local
branch `archetype-substitute-thruthy-expressions` as the requested
known-good baseline unless the user says otherwise. On 2026-04-09 that
matches tag `scriptcompiler-redesign-spec-2026-04-03` and resolves to
`333e06f877c7bb948e23daa203e2062922bf6ac5`.

Do not substitute an older reachable commit just because it was
previously recorded in local task notes. The explicit user baseline wins.

## Supersede The Wrong `b47015655` Comparison

The earlier `diff_variations` run against `b47015655` was a baseline
selection mistake, not evidence of a remaining product delta. The
correct rerun against
`333e06f877c7bb948e23daa203e2062922bf6ac5` passes with no
`projects.csv` changes, so the temporary 19-row `mp/database` removal
story is obsolete.

## `diff_projects` Is Still Externally Blocked On Helidon Generation

After fixing `testPresetTypeMismatch` and `testOpenDomainComplement`,
the focused `engine-v2` test slice is green and wrapper run
`20260409-200653-75357` passes both `compile_gate` and
`diff_variations` against the exact requested baseline
`333e06f877c7bb948e23daa203e2062922bf6ac5`.

`diff_projects` still cannot produce a compare, but the remaining
failure is not evidence of `projects.csv` or generated-file churn from
the latest patch:

- the wrapper's baseline-side `diff_projects` generation still dies in
  Helidon CLI generation with `Unresolved variable: security.atz`; and
- a direct current-side `generateOnly` rerun against the same Helidon
  checkout also fails with unresolved generated-script variables
  (`security.atz`, plus `metrics.builtin` in some runs).

Treat the remaining regression-workflow gap as an external Helidon
`generateOnly` blocker in the current checkout, not as a new focused
`Flow` / `Value` regression. The user-facing status is therefore:
unit slice green, `compile_gate` green, `diff_variations` green,
`diff_projects` still blocked by Helidon generation.
