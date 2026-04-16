# Progress

- 2026-04-16: Finished the internal naming cleanup that followed the
  finite-mask cutover in `Domain` and `Flow`. `Domain` now uses
  `ConstraintClause`, `ScalarConstraint`, `MembershipConstraint`, and
  `ClausePartition` instead of the old private `*Shape` /
  `DecisionPartition` names, and the clause algebra now allocates
  exact-size arrays directly instead of carrying
  `ScalarBuilder` / `MembershipBuilder` plus `trim(...)` helpers.
  `Flow.Analyzer.ExactProvenance` is now `Coverage`, the exact-case and
  `definedUnder` plumbing reads in those terms throughout the analyzer,
  and the last stale internal "shape" exception text is gone. Focused
  validation reran green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,FlowTest,ExpressionTest,ScriptCompilerTest,VariationsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`222` tests, `BUILD SUCCESS`, total time `5.134 s`), and
  `git diff --check` is clean.
- 2026-04-15: Made `Flow.Analyzer` one-shot in practice and froze the
  remaining read-only indexed `Flow` tables to arrays. The analyzer no
  longer carries a shared work deque or pass-time reset logic:
  `analyzeControl()` and `analyzeFacts()` now use method-local stacks,
  `entryGuards` / `beforeGuards` are initialized to `Guard.FALSE` once
  in the constructor, and the fact arrays are no longer cleared before
  use. On the read-only side, `Flow.symbolInfos`,
  `Projector.symbolInfos`, and `Ir.blocks` / `Ir.ops` /
  `Ir.controls` are now arrays frozen at the lowering/projection
  boundary, while the lowerer keeps its construction-time buffers as
  growable lists. Focused validation reran green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,FlowTest,ExpressionTest,ScriptCompilerTest,VariationsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`222` tests, `BUILD SUCCESS`, total time `5.000 s`), and
  `git diff --check` is clean.
- 2026-04-15: Replaced the fixed-size indexed analyzer staging tables
  in `Flow.Analyzer` with arrays instead of mutable lists. The
  constructor-owned analysis state now allocates `Guard[]`,
  `State[]`, and `Map<Integer, FactState>[]` once up front, the
  control/fact passes refill them in place with `Arrays.fill(...)` and
  direct indexed writes, and the list-only `Flow.element(...)` helper
  is gone. Added local array lookup helpers for nullable staging slots
  versus finalized required slots so internal missing-state failures
  still surface as explicit exceptions. Focused validation reran green
  with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,FlowTest,ExpressionTest,ScriptCompilerTest,VariationsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`222` tests, `BUILD SUCCESS`, total time `5.231 s`), and
  `git diff --check` is clean.
- 2026-04-15: Renamed the `Flow.Analyzer` control-guard helper methods
  to match what they actually do. `materializeStructuredControls(...)`
  is now `computeControlEntryGuards(...)`, and
  `materializeStructuredPath(...)` is now `computeControlGuard(...)`.
  This is a pure private-method naming cleanup on top of the stateful
  analyzer refactor; behavior is unchanged. Focused validation reran
  green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,FlowTest,ExpressionTest,ScriptCompilerTest,VariationsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`222` tests, `BUILD SUCCESS`, total time `4.788 s`), and
  `git diff --check` is clean.
- 2026-04-15: Made `Flow.Analyzer` constructor-owned and stateful. The
  analysis result collections (`entryGuards`, `beforeGuards`,
  `entryFacts`, `beforeFacts`, `afterFacts`, `entryStates`,
  `beforeStates`, `afterStates`) are now all initialized in the
  constructor as final mutable lists and then filled in place during
  `analyzeControl()`, `analyzeFacts()`, and state materialization.
  The shared work deque is now explicitly cleared at each pass
  boundary, and the control/fact passes no longer replace those lists
  with freshly allocated result collections. Focused validation reran
  green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,FlowTest,ExpressionTest,ScriptCompilerTest,VariationsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`222` tests, `BUILD SUCCESS`, total time `5.308 s`), and
  `git diff --check` is clean.
- 2026-04-15: Removed the remaining private-path defensive null checks
  and freeze copies in `Flow` / `Domain`. Internal constructors and
  helpers now trust their callers instead of repeating
  `requireNonNull(...)` on known-good values, including `Flow.Ir`,
  `Flow.Op`, `Flow.Terminator`, `Flow.SymbolSeed`,
  `Flow.ConditionLowerer`, `Flow.ConditionValue`, `Domain.Spec`,
  `Domain.Symbol`, `Domain.Symbol.Fact`, `Domain.LatticeValue`,
  `Domain.Guard`, `Domain.Residual`, `Domain.Guards`, and
  `Domain.Guards.ResidualValue`. The private `Domain.Spec` ordinal map
  now keeps direct ownership instead of `Map.copyOf(...)`, and
  `Domain.Residual.combine(...)` now stores a directly owned
  `ArrayList` instead of `List.copyOf(...)`. External/facade and
  semantic invariant checks stayed in place, including detached-node
  rejection, unknown symbol / guard ids, and finite-domain validation.
  Focused validation reran green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,FlowTest,ExpressionTest,ScriptCompilerTest,VariationsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`222` tests, `BUILD SUCCESS`, total time `5.013 s`), and
  `git diff --check` is clean.
- 2026-04-15: Hardened the remaining invariant internal lookups in
  `Domain` and `Flow`. `Domain.Symbol.Table.symbol(int)` now rejects
  unknown ids, `Domain.Guards` validates guard ids before
  dereferencing decisions, and `Domain.Residual` now checks its unary
  child shape explicitly. `Flow` now routes invariant symbol-info,
  block, op, control, state, and analyzed-fact lookups through checked
  helpers so missing entries fail with explicit exceptions instead of
  raw `null` dereferences or index failures, while intentionally
  optional probes such as `findId(...)`, availability lookups, caches,
  and unreachable `DEFINE_VALUE` facts remain nullable. Added
  `DomainTest.testSymbolTableRejectsUnknownSymbolId`,
  `DomainTest.testGuardsRejectUnknownGuardId`, and
  `FlowTest.testFlowRejectsUnknownSymbolId`. Focused validation reran
  green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,FlowTest,ExpressionTest,ScriptCompilerTest,VariationsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`222` tests, `BUILD SUCCESS`, total time `4.797 s`), and
  `git diff --check` is clean.
- 2026-04-15: Replaced the remaining trivial private-inner accessors in
  `Flow` with direct field reads. `Ir`, `Block`, and `Op` no longer
  expose the one-line `blocks()` / `symbols()` / `ops()` / `guards()`,
  `ops()` / `terminator()`, or `id()` / `kind()` / `symbolId()`
  wrappers, and `Analyzer.ExactProvenance` now checks
  `blockIds.length == 0` directly instead of carrying a private
  `isEmpty()` helper. `State` and the non-trivial `Model` /
  `SymbolInfoBuilder` helpers were intentionally left alone. Focused
  validation reran green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=FlowTest -Dsurefire.failIfNoSpecifiedTests=false test`
  (`12` tests, `BUILD SUCCESS`, total time `3.010 s`), and the broader
  slice also passed with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,FlowTest,ExpressionTest,ScriptCompilerTest,VariationsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`217` tests, `BUILD SUCCESS`, total time `4.816 s`).
- 2026-04-15: Removed the redundant private `Flow.SourceAnchor`
  wrapper. `Flow.Op` and `Flow.Terminator` now store `Node` directly,
  the projector/indexing code reads those `Node` references directly,
  and `Flow.Lowerer` no longer carries the trivial `anchor(...)`
  helper. This is an internal-only cleanup; lowering, projection, and
  facade behavior are unchanged. Focused validation reran green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=FlowTest -Dsurefire.failIfNoSpecifiedTests=false test`
  (`12` tests, `BUILD SUCCESS`, total time `3.998 s`), and the broader
  slice also passed with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,FlowTest,ExpressionTest,ScriptCompilerTest,VariationsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`217` tests, `BUILD SUCCESS`, total time `6.405 s`).
- 2026-04-15: Reduced the remaining `Flow` API/test-only surface in
  `engine-v2`. `Flow` no longer exposes `process(Ir, Node)`, `ir()`,
  `analysis()`, or `model()`, the nested IR/model carriers used only by
  internal lowering/projection are now private, and the dead
  `RECORD_USE` / `Use` analysis path is removed end-to-end. `FlowTest`
  is rewritten to exercise only the public `Flow` facade
  (`process(...)`, `before(...)`, `symbol(...)`, `activeGuard(...)`,
  `declaredValue(...)`, `guards()`), with new XML-backed fixtures for
  branch merge, same-value merge, and mixed literal-type exact-value
  merge coverage. Focused validation reran green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=FlowTest -Dsurefire.failIfNoSpecifiedTests=false test`
  (`12` tests, `BUILD SUCCESS`, total time `3.184 s`), and the broader
  slice also passed with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,FlowTest,ExpressionTest,ScriptCompilerTest,VariationsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`217` tests, `BUILD SUCCESS`, total time `4.864 s`).
- 2026-04-14: Revalidated the current exact-domain checkpoint and
  updated the `.ai` routers to match it. `git diff --check` is clean,
  and the focused validation slice still passes with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,FlowTest,ExpressionTest,ScriptCompilerTest,VariationsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`217` tests, `BUILD SUCCESS`, total time `5.536 s`). No Helidon
  regression wrapper was rerun in this pass.
- 2026-04-14: Completed another redesign-spec alignment pass in
  `engine-v2`. `Domain.Symbol.Fact.ExactCase` now caches scalar/list
  masks and merges or matches finite exact values by domain masks
  instead of raw `Value` shape, `DecisionShape` / `ScalarShape` /
  `MembershipShape` are now mask-only for finite guard algebra,
  `Flow.Lowerer.definitionSeed(...)` now preserves declared finite
  input domains across text fallbacks, `Flow.Projector` no longer turns
  full-domain finite facts into unconditional option availability, and
  `Flow` / `ScriptCompiler` now distinguish supported in-domain exact
  coverage from out-of-domain fallback text when normalizing guards.
  Added `DomainTest.testFactExactCasesUseFiniteMasksInsteadOfRawLiteralShape`
  plus
  `FlowTest.testAnalyzeMergesEquivalentBooleanExactValuesAcrossLiteralTypes`,
  and updated the expected compiled XML fixtures for
  `open-domain-complement` and `unsupported-block-pruning` to match the
  tighter spec-aligned output. Focused validation reran green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,FlowTest,ExpressionTest,ScriptCompilerTest,VariationsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`217` tests, `BUILD SUCCESS`, total time `4.647 s`).
- 2026-04-11: Removed the plain zero-arg `reduce()` calls that were only
  re-reducing expressions already normalized by producer boundaries in
  `ScriptCompiler`. `reachabilityExpression(...)` now returns
  `flow.expression(...)` directly, projected-condition simplification no
  longer reduces after `normalize(...)`, file-op literal rendering drops
  the extra `.reduce()`, and `FileObject` now trusts its incoming
  condition instead of normalizing again in the constructor. Focused
  validation reran green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,ExpressionTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testModelRejectsDetachedNode+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`214` tests, `BUILD SUCCESS`, total time `5.993 s`).
- 2026-04-10: Removed the remaining `Symbol.fullScalarMask()` /
  `Symbol.fullMembershipMask()` wrappers. Call sites now check
  `symbol.scalar` / `symbol.member` directly, fail early on the wrong
  shape kind, and then read `symbol.fullMask` directly. The same pass
  also simplified `Symbol` construction further: the domain booleans and
  ordinals are now initialized once in the constructor, `member`
  replaces the longer internal `membership` flag, and the redundant
  `ordinals(...)` helper is gone. Focused validation reran green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,ExpressionTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testModelRejectsDetachedNode+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`214` tests, `BUILD SUCCESS`, total time `5.373 s`).
- 2026-04-10: Replaced the computed `Symbol` domain-kind helper with
  constructor-initialized boolean flags. `Domain.Symbol` now stores
  `scalarDomain` and `membershipDomain`, and the scalar/membership
  maskability checks read those booleans directly instead of re-running
  a `switch` on `domain.kind()`. Focused validation reran green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,ExpressionTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testModelRejectsDetachedNode+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`214` tests, `BUILD SUCCESS`, total time `4.930 s`).
- 2026-04-10: Simplified `Domain.Symbol` by collapsing the duplicated
  scalar/membership ordinal storage onto one shared `ordinals` map, one
  shared `valuesByOrdinal` array, and one shared `fullMask` field. The
  scalar and membership wrapper methods remain for semantic clarity and
  to preserve the existing call sites and exception messages. Focused
  validation reran green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,ExpressionTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testModelRejectsDetachedNode+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`214` tests, `BUILD SUCCESS`, total time `4.748 s`).
- 2026-04-10: Inlined the trivial `Domain.canonicalStrings(...)`
  helper at each remaining call site and removed the helper itself.
  `Domain` now constructs the normalized `TreeSet` directly in the
  small number of spec/lattice/shape constructors that need it.
  Focused validation reran green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,ExpressionTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testModelRejectsDetachedNode+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`214` tests, `BUILD SUCCESS`, total time `4.915 s`).
- 2026-04-10: Moved the local `Guards.isPure(...)` /
  `Guards.isFalse(...)` helpers onto `Domain.Guard` itself as
  `guard.isPure()` / `guard.isFalse()`, then switched `Guards` and
  `Flow.isFalse(...)` to use the instance API directly. This is a
  mechanical API cleanup only; guard normalization and algebra stay
  unchanged. Focused validation reran green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,ExpressionTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testModelRejectsDetachedNode+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`214` tests, `BUILD SUCCESS`, total time `5.366 s`).
- 2026-04-10: Removed the now-obsolete exact-SHA baseline compatibility
  retry from
  `.agents/skills/helidon-archetype-regression/scripts/run-regression.sh`
  and deleted the temporary helper patch/script. The baseline branch is
  already fixed at `c2dfdf464`
  (`ScriptCompiler: add runtime-safe parent stubs`), so future Helidon
  comparisons should use that commit or tag
  `scriptcompiler-redesign-spec-2026-04-10` directly rather than
  carrying wrapper-side historical scaffolding. Reran the full wrapper
  on the final tree with
  `.agents/skills/helidon-archetype-regression/scripts/run-regression.sh
  all --helidon-dir /Users/rgrecour/workspace/helidon --baseline-ref
  c2dfdf464e4747824f9298daf8a1df7fd1bd2e39`.
  Wrapper run `20260410-171024-72826` passed every stage:
  `compile_gate` `2.30s < 5s`, `diff_variations` unchanged
  (baseline `8.98s`, current `3.20s`, threshold `15s`), and
  `diff_projects` unchanged (`outputs changed: no`,
  `csv changed: no`, `project trees changed: no`). `~/.m2` finished
  with the current workspace install restored.
- 2026-04-10: Inlined `ScriptCompiler.projectSourceNode(...)` into the
  only caller, `projectSourceGuards()`. The source-guard projection now
  keeps the same iterative `sourceNode.traverse()` pass but no longer
  carries a one-use helper wrapper around it. Focused validation stays
  green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testModelRejectsDetachedNode+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`214` tests, `BUILD SUCCESS`, total time `5.179 s`).
- 2026-04-10: Made the `Flow.Model.node()` projection invariant explicit.
  `Model.node(Node)` no longer returns `null`; it now throws an
  explicit `IllegalArgumentException` when a detached or unprojected
  node is queried, and the new `FlowTest#testModelRejectsDetachedNode`
  locks that behavior. In the same pass,
  `ScriptCompiler.projectSourceNode(...)` was rewritten to use a single
  pre-order `node.traverse()` loop instead of recursion while preserving
  the same inherited render-guard / active-guard propagation through the
  parent map. `ScriptCompiler.facts0(...)` was also simplified to rely
  on the new non-null `flow.before(node)` invariant. Focused
  validation is green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testModelRejectsDetachedNode+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`214` tests, `BUILD SUCCESS`, total time `4.729 s`).
- 2026-04-10: Added the planned high-value `Flow` facade shorthands for
  current `ScriptCompiler` / `VariationEngine` usage:
  `key(Node)`, `scope(Node)`, `before(Node)`, `symbol(String)`,
  `symbol(int)`, `activationCondition(Node)`,
  `declaredValue(Node, String)`, and `declaredValue(Node)`. `ScriptCompiler`
  now uses those helpers directly instead of chaining through
  `flow.model()` / `flow.ir()`, and `VariationEngine.VisitorImpl` now
  carries a `Flow` reference instead of a `Flow.Model` reference. The
  new facade intentionally stops there; it does not expose broader raw
  internals such as `fact(...)` or `nodeFacts(...)`. Focused
  validation is green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`213` tests, `BUILD SUCCESS`, total time `5.079 s`).
- 2026-04-10: Reduced the remaining guard API surface further. The
  canonical sentinels now live on `Domain.Guard` as `Guard.TRUE` and
  `Guard.FALSE`; `Domain.Guards.TRUE_GUARD` /
  `Domain.Guards.FALSE_GUARD` are removed. `Flow.Model.guards()` and
  `Flow.Model.symbols()` are also gone, with callers switched to
  `flow.guards()`, `flow.ir().symbols()`, or `ir.guards()` /
  `ir.symbols()` as appropriate. Focused validation is green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`213` tests, `BUILD SUCCESS`, total time `4.736 s`). I also
  deliberately reran the slice once with the known pre-existing
  `FlowTest#testIrLoweringKeepsDeclaredChoiceForTextPresetAndFallbackVariable`
  included; it still fails unchanged with `OPEN_TEXT` vs `Choice`, so
  this cleanup did not alter that baseline issue.
- 2026-04-10: Finished moving the former `ScriptCompiler` guard-helper
  block onto `Flow`. `Flow` now owns `guards()`, `renderGuard(...)`,
  `activeGuard(...)`, `expression(...)`, `residualGuard(...)`,
  `isFalse(...)`, `and(...)`, `or(...)`, `minus(...)`, `contains(...)`,
  and `equivalent(...)`; `ScriptCompiler` dropped the leftover local
  `guards()` forwarder and all remaining guard algebra call sites now
  dispatch through `flow.*` directly, including validation,
  projected-condition simplification, expression analysis, file-output
  aggregation, and stub synthesis. Focused validation is green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`213` tests, `BUILD SUCCESS`, total time `4.990 s`).
- 2026-04-10: Made the canonical `Domain.Guards` sentinels static and
  removed the `trueGuard()` / `falseGuard()` accessors. `Guards` now
  exposes shared package-private `TRUE_GUARD` / `FALSE_GUARD`
  instances, `Flow` and `ScriptCompiler` use those sentinels directly,
  the trivial forwarding accessors in `Flow.Model` and
  `ScriptCompiler` are gone, and the focused `DomainTest` / `FlowTest`
  assertions now reference the shared constants too. Focused
  validation is green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`213` tests, `BUILD SUCCESS`, total time `4.867 s`). The only
  intermediate failure was expected compile fallout in `DomainTest` and
  `FlowTest` from those removed accessors; updating those assertions to
  use `TRUE_GUARD` / `FALSE_GUARD` fixed it.
- 2026-04-10: Simplified the post-fold branch in
  `Expression.foldConstants()` to use direct early returns. The
  unchanged case now returns `this` immediately, the boolean-constant
  case now returns `TRUE` / `FALSE` immediately, and the stale
  `folded == this` follow-up check is gone. Focused validation is green
  with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`213` tests, `BUILD SUCCESS`, total time `5.273 s`).
- 2026-04-10: Inlined `Expression.foldSmallConstants()` into
  `foldConstants()`. The small-expression fold stack now lives directly
  inside `foldConstants()` and the extra private helper is deleted, so
  the whole constant-fold path is local to a single method plus
  `FoldPart`. Focused validation is green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`213` tests, `BUILD SUCCESS`, total time `4.910 s`).
- 2026-04-10: Moved the constant-fold helpers into
  `Expression.FoldPart`. `foldSmallConstants()` now dispatches with
  `op1.foldUnary(...)` and `left.foldBinary(...)`, while the former
  outer `foldUnary(...)`, `foldBinary(...)`, and `simplifyLogical(...)`
  helpers are deleted and rehomed as `FoldPart` methods. This keeps
  the fold behavior next to the fold state and removes another slice of
  trivial outer plumbing. Focused validation is green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`213` tests, `BUILD SUCCESS`, total time `4.839 s`).
- 2026-04-10: Inlined `Expression.constantExpression()` into the only
  remaining `foldConstants()` fast-path branch. For large
  variable-free expressions, `foldConstants()` now performs the
  `eval()` / `TRUE` / `FALSE` / fallback-to-`this` logic directly and
  the extra private helper is deleted. Focused validation is green
  with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`213` tests, `BUILD SUCCESS`, total time `4.978 s`).
- 2026-04-10: Reduced `Expression.FoldPart` to direct field use inside
  constant folding. The fold path now inlines the old
  `FoldPart.of(Token)` construction, removes the trivial
  `constant()` / `tokens()` / `changed()` accessors plus the final
  `expression()` wrapper, and reads `FoldPart.constant`,
  `FoldPart.tokens`, and `FoldPart.changed` directly in
  `foldSmallConstants(...)`, `foldUnary(...)`, `foldBinary(...)`, and
  `simplifyLogical(...)`. `FoldPart` keeps only its constructor and
  `withChanged()` behavior helper. Focused validation is green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`213` tests, `BUILD SUCCESS`, total time `4.917 s`).
- 2026-04-10: Moved top-level boolean-term splitting into
  `Expression` so `ScriptCompiler` no longer reparses
  `expr.literal()`. `Expression` now exposes public
  `conjunction()` / `disjunction()` plus
  `and(List<Expression>)` / `or(List<Expression>)`, all backed by the
  postfix token tape. The top-level split is now iterative with an
  explicit stack instead of recursive term collection, the list
  overloads treat `null` as an empty/no-op input like the single-term
  overloads already did, and the shared subexpression slicer also
  replaces the old `SyntheticVar` helper. `ScriptCompiler` now uses
  those APIs directly, the old string-based `conjunctionTerms(...)` /
  `disjunctionTerms(...)` and duplicated `andTerms(...)` /
  `orTerms(...)` helpers are deleted, and `ExpressionTest` now covers
  quoted literals, nested boolean terms, list-based composition, and
  `null` list inputs. Focused validation is green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`213` tests, `BUILD SUCCESS`, total time `4.807 s`).
- 2026-04-10: Follow-up cleanup removed the remaining `copyOf`
  wrappers in internal `Flow` / `Domain` code after the user called
  out the leftover list-literal case. `Flow.Ir` / `Flow.Block` now
  keep the provided lists directly, list-literal membership checks and
  lattice materialization no longer wrap `TreeSet`s in `Set.copyOf`,
  and `Domain` drops the same redundant wrappers in
  `stringOrdinals(...)`, `Fact`, `Fact.ExactCase`, and
  `LatticeValue.BooleanSet`. Focused validation reran green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`145` tests, `BUILD SUCCESS`, total time `4.906 s`).
- 2026-04-10: Removed another dead-logic slice from `Flow` and
  `Domain`. `Flow` no longer carries the legacy unstructured
  control-edge reconstruction path:
  `materializeControlPaths(...)`,
  `materializeBlockGuard(...)`,
  `materializeEdgeGuard(...)`,
  `controlEdgeId(...)`,
  `controlEdgeCount(...)`,
  and `hasStructuredControlPaths()` are gone, manual `FlowTest` IR now
  supplies explicit structured `ControlPath`s, and `Flow.Ir` now
  requires non-empty control paths. The same pass also removed the dead
  `Block.id` / `BlockBuilder.id` duplication, the unused `anchor(...,
  scope)` parameter, and the duplicate `ControlFlow.afterByOp` path
  list. `Domain` drops more redundant internal state: `ScalarShape`
  no longer caches `allowedSize`, `MembershipShape` no longer caches
  `requiredSize` / `forbiddenSize`, and
  `DecisionShape.membershipsEqual(...)` is inlined away. Focused
  validation stayed green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`145` tests, `BUILD SUCCESS`, total time `4.737 s`). `git diff
  --check` is clean.
- 2026-04-10: Ran a second dead-code reduction pass over `Flow` and
  `Domain`. `Domain` drops the fully unused
  `Guards.exactImplicationCheap(...)` path and the companion
  `Decision.shapeCountAtMost(...)` helper. `Flow` drops several dead IR
  payloads and wrappers: unused `Op` payload for `PHI`, `refId`,
  `emitKind`, and `blockIds` / `symbolIds`; the unused `Use.id`; the
  unused `SourceAnchor.scope`; unused `Analysis.exitByBlock`,
  `ControlFlow.exitByBlock`, and `FactFlow.exitByBlock`; and several
  internal-only getters in `Model`, `Analysis`, `NodeFacts`, `Op`, and
  `Terminator` that were replaced with direct field access inside
  `Flow`. Focused validation stayed green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`145` tests, `BUILD SUCCESS`).
- 2026-04-10: Renamed `Domain.Value` to `Domain.LatticeValue` across
  `engine-v2` so the symbolic lattice no longer collides with the
  runtime `Value<?>` model in `Flow`, `Domain`, and `ScriptCompiler`.
  The rename is mechanical only; no behavior change was intended. The
  same focused validation slice remains green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`145` tests, `BUILD SUCCESS`).
- 2026-04-10: Started a local Flow/Domain reduction pass on top of
  `04ef5a028` to cut dead internal equality/hashing and defensive-copy
  code now that the hot paths are bitset-based. `Flow.State`
  `equals/hashCode` is removed entirely, analyzer-local `FactState`,
  `ExactCaseState`, and `ExactProvenance` no longer carry dead hash
  implementations, and the analysis/model internals stop wrapping
  already-persistent maps/lists on every hop. `Domain.Decision` now
  uses singleton identity checks for `TRUE`, simpler `hashCode()`,
  direct internal arrays for `Decision`, no extra membership hash
  cache, direct builder-array trimming instead of tiny getter wrappers,
  and simpler set-backed shape storage/hashing for the non-maskable
  path. `Flow` now also uses direct field access across its inner
  analysis/model helpers instead of bouncing through trivial getters.
  `git diff --check` is clean. Focused validation is green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=DomainTest,ScriptCompilerTest,VariationsTest,FlowTest#testIrLoweringBuildsInputsBranchesAndEmits+testAnalyzeMergesBranchValuesAndUseGuards+testAnalyzeMergesSameExactValueAcrossMultiplePaths+testIrLoweringCapturesDefinitionsAndConditionUses+testIrLoweringGuardsNestedBooleanAndOptionDefinitions+testIrLoweringPromotesFiniteLocalTextVariable+testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol+testIrLoweringHandlesMembershipSymbolContainsLiteralList+testIrLoweringKeepsUnsupportedLiteralListContainsResidual \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`145` tests, `BUILD SUCCESS`). A broader rerun that included all
  current `FlowTest`s exposed
  `FlowTest#testIrLoweringKeepsDeclaredChoiceForTextPresetAndFallbackVariable`,
  but that failure is pre-existing: it reproduces unchanged on a clean
  detached worktree at `04ef5a0286463b7dfa628dae2b1935e6b2909ba1`
  using
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=FlowTest#testIrLoweringKeepsDeclaredChoiceForTextPresetAndFallbackVariable \
  -Dsurefire.failIfNoSpecifiedTests=false test`.
- 2026-04-10: Landed the actual baseline-branch fix on
  `archetype-substitute-thruthy-expressions` as local commit
  `c2dfdf464`
  (`ScriptCompiler: add runtime-safe parent stubs`) and tag
  `scriptcompiler-redesign-spec-2026-04-10`. This bakes the
  runtime-safe parent-stub behavior into the baseline itself so future
  Helidon compares do not need the exact-SHA compatibility retry for
  `333e06f877c7bb948e23daa203e2062922bf6ac5`. The focused baseline
  `ScriptCompilerTest` slice had already been revalidated there, a
  direct baseline-only Helidon `generateOnly` run now succeeds and
  generates all `100` projects, and wrapper run
  `20260410-110358-5963` used
  `.agents/skills/helidon-archetype-regression/scripts/run-regression.sh
  all --helidon-dir /Users/rgrecour/workspace/helidon --baseline-ref
  c2dfdf464e4747824f9298daf8a1df7fd1bd2e39` and passed every stage:
  `compile_gate` `2.46s < 5s`, `diff_variations` unchanged
  (baseline `9.22s`, current `3.55s`, threshold `15s`), and
  `diff_projects` unchanged (`outputs changed: no`,
  `csv changed: no`, `project trees changed: no`). `~/.m2` finished
  restored to the current workspace install. Future regression runs
  should use `c2dfdf464` or tag
  `scriptcompiler-redesign-spec-2026-04-10` as the baseline ref.
- 2026-04-10: The exact requested Helidon regression workflow is now
  green with a one-off exact-baseline compatibility retry. Wrapper run
  `20260410-103044-92887` used
  `.agents/skills/helidon-archetype-regression/scripts/run-regression.sh
  all --helidon-dir /Users/rgrecour/workspace/helidon --baseline-ref
  333e06f877c7bb948e23daa203e2062922bf6ac5` and passed every stage:
  `compile_gate` `2.35s < 5s`, `diff_variations` unchanged
  (baseline `8.43s`, current `3.16s`, threshold `15s`), and
  `diff_projects` unchanged (`outputs changed: no`,
  `csv changed: no`, `project trees changed: no`). The previously
  broken baseline generation was recovered by a local uncommitted
  one-off retry that is hard-gated to the exact baseline SHA above and
  patches the baseline worktree compiler to skip demand narrowing for
  nested refs during runtime stub generation. `~/.m2` was restored to
  the current workspace install at the end.
- 2026-04-09: Triple-checked that the exact required good baseline
  `333e06f877c7bb948e23daa203e2062922bf6ac5`
  (`archetype-substitute-thruthy-expressions`, same as tag
  `scriptcompiler-redesign-spec-2026-04-03`) is itself broken on
  Helidon project generation, independent of the wrapper. Created a
  detached worktree at `/tmp/helidon-archetype-baseline-truthy`,
  installed that baseline with
  `mvn -pl maven-plugins/helidon-archetype-maven-plugin -am install \
  -DskipTests`, then ran
  `mvn -f /Users/rgrecour/workspace/helidon/archetypes/archetypes/pom.xml \
  -Dversion.plugin.helidon-build-tools=4.0.0-SNAPSHOT clean install \
  -Darchetype.test.generateOnly=true \
  -Darchetype.test.parallelGeneration=true \
  -Darchetype.test.maxVariations=-1`.
  The direct baseline-only run failed in Helidon CLI generation after
  `14.755 s` with repeated unresolved generated-script variables:
  `security.atz` (`160` log hits, first at line `2867`) and
  `metrics.builtin` (`40` log hits, first at line `3063`). Temporary
  worktree cleanup succeeded and `~/.m2` was restored to the current
  workspace install afterward.
- 2026-04-09: Fixed the remaining broader `engine-v2` regressions in
  `Flow` / `Value`. `Value.typed(Value<?>, Type)` now preserves typed
  conversions without null-unboxing, `Flow.Model.exactValue()` falls
  back to the raw exact value when coercion is incompatible so
  validation can surface the real type error, and
  `Flow.Lowerer.definitionSeed()` now widens declared-input symbols when
  a text definition is attached to the same public path. Focused reruns
  are green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ScriptCompilerTest#testPresetTypeMismatch+testOpenDomainComplement \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  and the broader slice
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,ScriptCompilerTest,VariationsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  now passes with `195` tests and `BUILD SUCCESS`.
- 2026-04-09: Reran the full Helidon regression wrapper against the
  requested good baseline:
  `.agents/skills/helidon-archetype-regression/scripts/run-regression.sh
  all --helidon-dir /Users/rgrecour/workspace/helidon --baseline-ref
  333e06f877c7bb948e23daa203e2062922bf6ac5`.
  Wrapper run `20260409-200653-75357` passes `compile_gate`
  (`2.23s < 5s`) and `diff_variations` (`outputs changed: no`,
  baseline `8.47s`, current `3.38s`, threshold `15s`), but
  `diff_projects` still fails before compare. The baseline-side project
  generation log
  `.agents/skills/helidon-archetype-regression/.state/20260409-200653-75357/logs/diff_projects-baseline-generate.log`
  still dies in Helidon CLI generation with
  `Unresolved variable: security.atz`, so there is no
  `diff_projects-compare.log` for this run. `~/.m2` was restored to the
  current workspace install afterward.
- 2026-04-09: Verified that the current workspace is not the remaining
  `diff_projects` blocker by running the current workspace install
  directly in the Helidon checkout:
  `mvn -f /Users/rgrecour/workspace/helidon/archetypes/archetypes/pom.xml \
  -Dversion.plugin.helidon-build-tools=4.0.0-SNAPSHOT clean install \
  -Darchetype.test.generateOnly=true \
  -Darchetype.test.parallelGeneration=true \
  -Darchetype.test.maxVariations=-1`.
  That direct current-side generation succeeds and produces all
  `100` projects with no unresolved-variable failure. Together with the
  detached baseline-only rerun above, this isolates the remaining
  regression-workflow blocker to baseline-side `generateOnly`
  generation, not the current workspace behavior, focused engine-v2
  unit slice, or `projects.csv` parity.
- 2026-04-09: Committed the Helidon variation fixes as local commit
  `2d083695b` (`Archetype: fix packaged variation projection`).
  `VariationEngine.normalize(...)` now reruns filters after
  `execute(...)`, and `ScriptCompiler.projectSourceGuards(...)` now
  constrains projected facts to the current branch before replaying
  source guards. Focused regressions are green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ScriptCompilerTest#testSiblingPresetsDoNotDisableLaterNestedEnumInput,VariationsTest#testVariationsFiltersCanReferenceNestedInputKeys,VariationsTest#testVariationsKeepNestedEnumWhenParentBooleanIsExternallyFixed,VariationsTest#testVariationsKeepNestedEnumAcrossSourceAndExecGraph \
  -Dsurefire.failIfNoSpecifiedTests=false test`.
- 2026-04-09: Reran the Helidon wrapper against the explicit good
  baseline the user requested. Wrapper run `20260409-192148-71454`
  used `--baseline-ref 333e06f877c7bb948e23daa203e2062922bf6ac5`
  (local branch `archetype-substitute-thruthy-expressions`, same as tag
  `scriptcompiler-redesign-spec-2026-04-03`) and passed
  `diff_variations` with no `projects.csv` churn. Baseline wall-clock
  was `7.81s`, current wall-clock was `2.85s`, the threshold is `15s`,
  and `~/.m2` was restored to the current workspace install afterward.
- 2026-04-09: The remaining blockers are now back in the broader
  `engine-v2` test slice, not the Helidon variation count. Focused
  rerun
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,ScriptCompilerTest,VariationsTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  still fails in two places:
  `ScriptCompilerTest.testPresetTypeMismatch`
  (invalid boolean exact-value coercion currently throws an NPE through
  `Value.typed(...)` / `Flow.Model.exactValue()` instead of surfacing
  `EXPR_EVAL_ERROR` plus `PRESET_TYPE_MISMATCH`) and
  `ScriptCompilerTest.testOpenDomainComplement`
  (the open-text fallback for `media.json-lib` is still over-simplified
  to `${media.json-lib} != 'jsonp'` even though the fixture also has a
  source variable on the same public path). The next pass is to fix
  those two tests, then rerun the full Helidon workflow against the
  same explicit baseline and continue to `diff_projects`.
- 2026-04-09: Reran `diff_variations` against an explicit known-good
  baseline with wrapper run `20260409-171723-48076` and
  `--baseline-ref b47015655`
  (`ScriptCompiler: normalize stub definition reachability`), the
  latest commit on `archetype-substitute-thruthy-expressions` that the
  local task state records as fully green against Helidon. The baseline
  side succeeded in `9.69s` and generated `119` total projects. The
  current side failed before CSV snapshot/compare because the Helidon
  exact-count assertion tripped again: current workspace build-tools
  generated `115` variations where the current Helidon POM expects
  `100`. The visible per-plan delta in the log is `mp/observability`
  dropping from `8` baseline variations to `4` current variations.
  Wrapper outputs are therefore `outputs changed: unknown`,
  `current variation within threshold: unknown`, and `~/.m2` was
  restored to the current workspace install afterward.
- 2026-04-09: Committed the structural control-path fix as local commit
  `a55dc6dcf` (`Flow: preserve structured control paths`). Lowered
  blocks now carry structured control-context ids, `analyzeControl()`
  keeps the forward pass reachability-only, and exact lowered block
  paths are materialized from that structural context by conjunction
  only rather than by predecessor `or(...)` reconstruction. The focused
  slice stayed green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,FlowTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`82` tests), `git diff --check` was clean before commit, and Helidon
  wrapper run `20260409-170801-45716` passed `compile_gate` in `2.10s`
  versus the `5s` threshold with `~/.m2` restored to the current
  workspace install.
- 2026-04-09: The intermediate reachability-only follow-up without
  structured block contexts was still the wrong design. Wrapper run
  `20260409-170042-43718` no longer burned time in
  `Flow$Analyzer.enqueueControl()`, but a live
  `jcmd 43846 Thread.print -l` showed the main thread right back in
  `Domain$DecisionShape.subsetOf()` through
  `Flow$Analyzer.materializeBlockGuard()` /
  `materializeEdgeGuard()`. Exact path reconstruction from predecessor
  unions was still reintroducing the same giant joins at the analysis
  boundary.
- 2026-04-09: Wrapper run `20260409-170821-45930`
  `diff_variations` did not produce a usable compare. The run was
  stopped on the baseline side because default baseline `HEAD` was still
  the broken pre-fix commit `e2cd1a3a8`, so baseline generation stayed
  in the old analyzer failure shape before any `projects.csv` compare.
  The wrapper reported `outputs changed: unknown`,
  `current variation within threshold: unknown`, and restored
  `~/.m2` to the current workspace install afterward.
- 2026-04-09: Checkpointed the second analyzer rewrite as local commit
  `e2cd1a3a8` (`Flow: remove analyzer path interning`) after the focused
  `ExpressionTest,DomainTest,FlowTest` slice reran green with `82`
  tests. The branch is clean apart from local `.ai` state.
- 2026-04-09: Committed the first analyzer rewrite as local commit
  `26c0d09ea` (`Flow: remove exact guard algebra from analyzer`) before
  continuing the Helidon follow-up.
- 2026-04-09: Helidon wrapper run `20260409-163559-38737` on top of
  `26c0d09ea` still failed `compile_gate` after a manual stop, but it
  exposed the real flaw in that first rewrite. A live
  `jcmd 38848 GC.class_histogram` showed the analyzer still exploding
  the path interner itself: about `118M`
  `Flow$Analyzer$AnalysisPath`, `97M` `HashMap$Node`, and `118M`
  boxed `Long`, with the main thread still in `Flow$Analyzer.analyze()`
  during `transfer(...)`. The old interned `and/or/implication` path
  maps are not viable.
- 2026-04-09: Rewrote `Flow.Analyzer` a second time to remove the
  interned path DAG entirely. Control reachability is now a separate
  block-level pass, fact/env propagation no longer carries path
  formulas, and `definedUnder` / exact-case provenance are tracked as
  block-id sets that only materialize `Guard`s on demand. Added focused
  `FlowTest` assertions for exact-case materialization and same-value
  merge coverage. The focused slice stays green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,FlowTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`82` tests), and `git diff --check` is clean.
- 2026-04-09: Helidon wrapper run `20260409-164618-40401` on top of
  the second rewrite still failed `compile_gate` after a manual stop,
  but the catastrophic memory blow-up is gone. A live
  `jcmd 40498 GC.class_histogram` dropped to about `210 MB` total with
  no `AnalysisPath` churn at all. The dominant live stack from
  `jcmd 40498 Thread.print -l` is now
  `Domain$DecisionShape.subsetOf()` ->
  `Domain$Guards.or()` ->
  `Flow$Analyzer.enqueueControl()` ->
  `Flow$Analyzer.propagateControl()` ->
  `Flow$Analyzer.analyzeControl()`. The remaining blocker is exact
  block-entry guard merging in control analysis, not fact/env analysis
  or exact-case provenance anymore.
- 2026-04-09: Committed the membership-bitset checkpoint as local commit
  `97348f8b1` (`Domain: bitset membership guards`) before starting the
  analyzer redesign follow-up.
- 2026-04-09: Current dirty follow-up is a structural `Flow.Analyzer`
  rewrite that removes exact `Domain.Guard` algebra from the branch and
  block-merge worklist path. The analyzer now carries an internal
  interned path DAG for state reachability/provenance and only
  materializes exact `Guard`s when facts, uses, or final `Analysis`
  output need them. `Fact` merging was also widened with an
  `EXACT_CASE_MAX` cap plus singleton scalar fallback so
  `BooleanSet` / `ChoiceSet` / `FiniteText` singleton values can still
  propagate exactly without carrying unbounded exact-case lists. The
  focused engine-v2 slice remains green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,FlowTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`81` tests) on top of the dirty analyzer cut.
- 2026-04-09: Fresh Helidon wrapper runs on top of the dirty analyzer
  rewrite confirm the dominant hotspot moved again:
  `20260409-162119-35543` sampled after about `60.9s` with the top
  frame in `Flow$Analyzer.or()` / `mergeExactCase()` /
  `mergeFact()` rather than `Domain$DecisionShape.subsetOf()`;
  follow-up run `20260409-162415-36586` after adding the exact-case cap
  still samples the same area after about `47.4s`, but the old
  `DecisionShape.subsetOf()` path is gone and the run uses materially
  less CPU churn than the first dirty analyzer attempt. Both wrapper
  runs were stopped manually after sampling, and no Helidon/Maven JVM
  was left running afterward.
- 2026-04-09: Finished the non-schema membership bitset sweep on top of
  `00447e4b5`. `Domain.Symbol` now indexes finite membership items the
  same way it already indexed finite scalar values, `MembershipShape`
  is mask-native for maskable membership domains, and the hot
  `DecisionShape` membership paths (`subsetOf`, `intersect`,
  `splitAgainst`, membership rendering, and bulk `containsAll`
  construction) no longer route through `Set.containsAll()` /
  `TreeSet` unions on the maskable path. `Flow` now lowers
  `${membership} contains ['a','b']` and raw list-membership guards via
  the new bulk membership guard instead of chaining one-item guards, and
  exact list cases now cache their literal item set instead of
  rebuilding a `HashSet` on every `containsAll()` check. Added
  `DomainTest.testGuardContainsAllKeepsBroaderMembershipRequirement`,
  `FlowTest.testIrLoweringHandlesMembershipSymbolContainsLiteralList`,
  and the XML fixture `flow/literal-list-contains-membership.xml`.
  Focused validation is green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,FlowTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`81` tests), and `git diff --check` is clean.
- 2026-04-09: Fresh Helidon wrapper run
  `20260409-155935-32332` reran `compile_gate` against
  `/Users/rgrecour/workspace/helidon` with clean `HEAD` as baseline on
  top of the membership rewrite. The run no longer samples the old
  membership `containsAll()` hotspot: live `jstack 32431` after about
  `3m36s` now shows the top frame back in the generic scalar scan inside
  `Domain$DecisionShape.subsetOf()` at `Domain.java:1808`
  (`Decision.of()` -> `Decision.and()` -> `Flow$Analyzer.propagate()`).
  The wrapper was stopped manually after the sample, so
  `compile_gate` still has no final wall-clock/pass result for this
  pass; the current remaining issue is the broader `DecisionShape`
  subset loop rather than membership-set containment specifically.
- 2026-04-09: Checkpointed the array-backed `DecisionShape` rewrite as
  local commit `00447e4b5` (`Domain: store decision shapes in sparse
  arrays`) before continuing with the scalar follow-up.
- 2026-04-09: Finished the scalar-mask rewrite on top of
  `00447e4b5`. `Symbol` now exposes full-mask and ordinal-ordered scalar
  metadata, `ScalarShape` is mask-native for maskable scalar domains,
  and the remaining scalar-heavy `DecisionShape` paths (`normalized`,
  `tryMerge`, `splitAgainst`, `withScalar`, and scalar rendering) now
  use bit operations instead of immutable-set equality and `TreeSet`
  unions on the maskable path. This pass also fixed an inverted
  `ScalarShape.intersect()` subset fast path and added
  `DomainTest.testGuardAndNarrowsMergedScalarChoice` to pin merged
  multi-value scalar narrowing and `!=` rendering. Focused validation is
  green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,FlowTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`79` tests).
- 2026-04-09: Fresh Helidon wrapper run
  `20260409-153948-30517` reran `compile_gate` on top of the
  mask-native scalar rewrite. The previous scalar immutable-set-equality
  hotspot is gone: the fresh `jstack 30594` no longer shows
  `ScalarShape.subsetOf()` / set equality on the top frame. The current
  hotspot moved to membership subset checks in
  `DecisionShape.subsetOf()`, specifically
  `left.required.containsAll(right.required)` /
  `left.forbidden.containsAll(right.forbidden)` at
  `Domain.java:1768`. The compile still did not clear the gate and was
  terminated after the sample, so the wrapper again reported
  `compile_gate: FAIL` with no wall-clock value, and confirmed that
  `~/.m2` was restored to the current workspace install.
- 2026-04-09: Next follow-up after the array-backed `DecisionShape`
  cut is to make `ScalarShape` truly mask-native for maskable scalar
  domains. The fresh `jstack` on wrapper run
  `20260409-152638-29405` shows the new residual hotspot in
  immutable-set equality inside `ScalarShape.subsetOf()`, so the next
  implementation should make the mask the primary representation,
  extend `Symbol` with full-mask / ordinal-ordered scalar metadata, and
  convert the remaining scalar-domain helpers in `DecisionShape`
  (`normalized`, `tryMerge`, `splitAgainst`, `withScalar`, and
  expression rendering) to bit operations instead of set equality and
  `TreeSet` unions.
- 2026-04-09: Replaced the remaining `DecisionShape` ordered-map
  storage in `Domain.java` with sorted sparse arrays
  (`int[]` symbol ids plus parallel `ScalarShape[]` /
  `MembershipShape[]`). The map constructors/call sites are gone,
  `subsetOf(...)` now uses two-pointer scans over the sorted arrays, and
  the internal update helpers (`intersect`, `tryMerge`,
  `splitAgainst`, `withScalar`, membership refinements, equality/hash,
  and literal rendering) now work directly on array-backed sparse
  state. Focused validation stayed green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,FlowTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`78` tests).
- 2026-04-09: Fresh Helidon wrapper run
  `20260409-152638-29405` reran `compile_gate` on top of the new
  array-backed `DecisionShape`. The compile still did not finish within
  the gate and was terminated after a fresh `jstack 29477`, but the hot
  path moved again: the top frame is no longer map iteration or lookup
  in `DecisionShape.subsetOf()`. The current stack is
  `ImmutableCollections$Set12.contains` /
  `AbstractImmutableSet.equals` ->
  `Domain$ScalarShape.subsetOf()` ->
  `Domain$DecisionShape.subsetOf()` ->
  `Decision.of()` / `Decision.and()` during
  `Flow$Analyzer.propagate()`. The wrapper reported `compile_gate: FAIL`
  with no wall-clock sample because the Maven process was terminated, and
  it confirmed that `~/.m2` was restored to the current workspace
  install.
- 2026-04-09: Committed the broad checkpoint as local commit
  `909ee308f` (`ScriptCompiler: trim flow analysis overhead`), then
  continued the Helidon performance follow-up on top of it with a new
  dirty delta in `Domain.java`, `Flow.java`, and `DomainTest.java`.
  Two follow-up cuts landed locally:
  pure `Decision.or(...)` now incrementally normalizes unions instead
  of routing every union through full `Decision.of(...)`, and
  `Flow.Analyzer.mergeGuard()` now limits exact pure-guard implication
  proofs to small decisions while still keeping the cheap shapewise
  implication fast path. A later corrective tweak removed an expensive
  `DecisionShape.equals(...)` fast path inside `subsetOf()`, and the
  current dirty pass also caches whole-decision shapewise-subset results
  by registered decision IDs inside `Domain.Guards`.
- 2026-04-09: Focused validation after each local pass stayed green with
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,FlowTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`78` tests). Helidon `compile_gate` still fails, but the live
  hotspot kept moving:
  wrapper run `20260409-134343-22796` sampled
  `Domain.Guards.implies()` / `Decision.subtract()`;
  wrapper run `20260409-134603-23275` sampled
  `Decision.and()` -> `Decision.of()` after the bounded-implication
  change;
  wrapper run `20260409-134742-23743` sampled
  `Flow$Analyzer.merge()` -> `Fact.merge()` -> `Guards.or()` ->
  `Decision.shapewiseSubsetOf()` after removing the expensive equality
  fast path; and wrapper run `20260409-135009-24463` now samples the
  remaining cost even lower in `DecisionShape.subsetOf()` /
  `AbstractCollection.containsAll()` during
  `Decision.orWithoutSubsetChecks()` from `Guards.or()`. `~/.m2` was
  restored to the current workspace install after each killed sample.
- 2026-04-09: Committed the finite-decision merge follow-up as local
  commit `07d1ac9e3` (`Domain: trim pure guard merge checks`) and kept
  working on top of it. The current dirty delta is now scalar-first
  representation work inside `Domain.java`: finite scalar symbols now
  pre-index domain values, `ScalarShape` caches a compact bit-mask for
  maskable scalar domains, `DecisionShape.subsetOf()` compares scalar
  constraints directly instead of calling `containsAll()` on
  full-domain sets, and `DecisionShape` now stores its internal maps as
  sorted `LinkedHashMap` snapshots instead of live `TreeMap`s. Focused
  validation still passes with the same `78`-test engine slice. Fresh
  wrapper run `20260409-145522-27235` still fails `compile_gate`, but
  the live `jstack 27299` no longer shows scalar `containsAll()` on the
  hot path. The current hotspot is now narrower again:
  `Decision.and()` -> `Decision.of()` -> `DecisionShape.subsetOf()`,
  specifically the map iteration / lookup path (`TreeMap.get` from the
  temporary sorted map used during normalization) rather than the
  scalar set-containment check itself.
- 2026-04-09: Committed that scalar-first representation cut as local
  commit `1656888c3` (`Domain: index scalar subset checks`). The branch
  is clean again after the commit, the focused
  `ExpressionTest,DomainTest,FlowTest` slice remained green, and the
  current next target is the remaining `DecisionShape.subsetOf()` map
  access cost during `Decision.and()` / `Decision.of()`.
- 2026-04-09: Ran the Helidon archetype regression wrapper against
  `/Users/rgrecour/workspace/helidon` with clean `HEAD` as baseline.
  Full run `20260409-125222-20887` failed fast in `compile_gate`: the
  compile never finished within a reasonable window, the wrapper was
  stopped manually, and a live `jstack 20949` showed the dirty tree back
  in the finite-decision merge hotspot
  `Domain.DecisionShape.subsetOf()` -> `Decision.of()` ->
  `Decision.or()` -> `Domain.Guards.or()` during
  `Flow.Analyzer.mergeGuard()`. Follow-up `diff_variations` run
  `20260409-125621-21177` also failed before any snapshot compare:
  clean `HEAD` baseline generation itself OOMed with `Java heap space`
  after `132.62s` inside the archetype `compile` goal. A live
  `jstack 21243` on that baseline run showed a different hot path in
  `Lists.addAll()` -> `Expression.or()` -> `Domain.Guards.or()` during
  `Flow.Analyzer.mergeGuard()`, again under heavy GC. `~/.m2` was
  restored to the current workspace install after both wrapper runs.
- 2026-04-09: Removed the leftover `ResidualDebug` instrumentation from
  the current dirty tree. `Domain`, `Flow`, and `ScriptCompiler` no
  longer emit residual counters/origin snapshots, the helper file is
  deleted, and the focused
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,FlowTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  slice reran green with `77` tests. `git diff --check` is clean.
- 2026-04-08: Preserved finite symbol domains across local scalar
  definitions in `Flow.Lowerer`. Text-typed presets / variables no
  longer automatically degrade a symbol to `OPEN_TEXT`: if a declared
  input already exists for that key, the local definition now reuses the
  declared input spec, and otherwise literal scalar enum/text locals now
  seed `Spec.FiniteText` instead of open text. Added `FlowTest`
  regressions that prove both the Helidon-shaped
  `media.json-lib` case and pure finite local text variables now lower
  impossible scalar comparisons to `false`.
- 2026-04-08: Followed the first rerun evidence with one more lowering
  fix: `ConditionLowerer.contains(...)` now handles the reversed finite
  scalar form `['a','b'] contains ${symbol}` by lowering it to the
  corresponding OR of scalar equalities. Added
  `FlowTest.testIrLoweringHandlesLiteralListContainsFiniteScalarSymbol`
  to pin that case. The focused
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,FlowTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  slice is green again and is now at `75` tests; `git diff --check`
  remains clean.
- 2026-04-08: Wrapper run `20260408-191837-99923` on top of the first
  symbol-seeding fix still failed `compile_gate`, but the diagnostic
  output proved the semantic fix landed. `media.json-lib` now appears as
  `kind=CHOICE,guardable=true,tainted=false`, the earlier unhandled
  compare residuals disappeared, and the only remaining repeated
  residual origin was the reversed list-membership form
  `['jsonp','jsonb'] contains ${media.json-lib}` /
  `['jsonb','jackson'] contains ${media.json-lib}`. The live
  `jstack 128` hotspot at that point had already moved away from
  equality and into `Expression.variableCountAtMost()` through
  `Domain.Guards.cacheable()` -> `Guards.or()` -> `Fact.merge()`.
- 2026-04-08: Wrapper run `20260408-192232-1644` after the reversed
  `contains` lowering still failed the `5s` compile gate and was killed
  after about `51.40s`, but it is the strongest evidence yet that the
  residual problem itself is largely gone. The run produced no
  `ResidualDebug` shutdown summary at all, which means none of the
  tracked counters/origins/large residual snapshots fired before
  shutdown. A live `jstack 1749` showed the new hotspot entirely inside
  finite decision merging:
  `Domain.DecisionShape.subsetOf()` -> `Decision.of()` ->
  `Decision.or()` -> `Domain.Guards.or()` during
  `Flow.Analyzer.mergeGuard()`. The next performance cut should target
  subset / implication checks in the finite decision path rather than
  residual expression handling.

- 2026-04-08: Resumed the performance follow-up from clean `HEAD`
  `c6fcbfad5` and now have a dirty worktree limited to
  `Domain.java`, `Expression.java`, `DomainTest.java`, and
  `ExpressionTest.java`.
- 2026-04-08: Current uncommitted `Domain` / `Expression` delta is all
  about the still-failing Helidon `compile_gate`: `Decision` now has
  shapewise-subset fast paths and dedupe/merge guardrails, programmatic
  `Expression.and/or` token composition is lazy through
  `CombinedTokenList`, that combined-list iterator is now iterative and
  caches its hash, `foldConstants()` does bounded partial folding for
  small expressions, `synthetic()` reuses lazy concatenation instead of
  repeatedly flattening logical skeletons, and the same-decision
  residual-OR path in `Domain.Guards.or(...)` now skips
  `compact()` entirely.
- 2026-04-08: Added focused regressions
  `DomainTest.testGuardImplicationKeepsBroaderPureDecision`,
  `ExpressionTest.testProgrammaticDeepOrChain`, and
  `ExpressionTest.testProgrammaticDeepComparisonOrReduceSkipsQmcLimit`.
  The focused validation
  `mvn -pl archetype/engine-v2 -am \
  -Dtest=ExpressionTest,DomainTest,FlowTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  passed repeatedly after each change and is currently green with
  `70` tests. `git diff --check` is clean on the current dirty tree.
- 2026-04-08: Fresh wrapper evidence from the dirty tree still fails
  `compile_gate`, but the hotspot moved as the residual-OR path was
  trimmed:
  `20260408-120100-78368` OOMed in `Expression.combineTokens()` from
  `Domain.Guards.or(...)`;
  `20260408-120512-79085` OOMed in `Expression.reduce()/synthetic()`
  through `Domain.Guards.compact(...)`;
  `20260408-120957-79948` OOMed in `Expression.synthetic()` while that
  same `compact()` path still ran;
  `20260408-121249-80654` still OOMed in `Expression.combineTokens()`
  from `synthetic()` after the stricter `compact()` gate; and
  `20260408-121628-81298` moved the failure to
  `Expression.foldSmallConstants()` inside `Domain.Guards.guard()`
  after the same-decision branch stopped calling `compact()`.
- 2026-04-08: The current last tweak is narrower still: that same
  same-decision residual-OR branch now calls
  `guard(decision, residual, false)` so it also skips the final
  `foldConstants()` pass on that hot path. Only the focused local
  `70`-test slice has been rerun after this exact last change; the
  Helidon wrapper has not been rerun on top of it yet.
- 2026-04-08: Reran `compile_gate` on top of that last
  `guard(..., false)` tweak as wrapper run `20260408-170604-88560`.
  It still failed, but the failure moved again: the OOM is now in
  `Expression.foldSmallConstants()` called from
  `Domain.Guards.compact(...)` on the residual-only fallback branch
  (`Domain.Guards.or(...)` line `976`), not from the same-decision
  branch at line `968`. The current next cut should target
  `foldBinary` / `foldUnary` token concatenation or stop calling
  `compact()` on that residual-only fallback path when the raw OR
  expression is already large.
- 2026-04-08: Followed that evidence with three more narrow changes:
  `foldBinary` / `foldUnary` now use lazy token composition,
  the residual-only fallback branch in `Domain.Guards.or(...)` now
  skips both `compact()` and final folding, and
  `cacheable()` / `compact()` now use
  `Expression.variableCountAtMost(...)` instead of building the full
  `variables()` set on huge expressions. Added
  `ExpressionTest.testVariableCountAtMost`; the focused
  `ExpressionTest,DomainTest,FlowTest` slice remains green and is now
  at `71` tests.
- 2026-04-08: Fresh wrapper run `20260408-171007-89099` still OOMed in
  `foldSmallConstants()` on the residual-only fallback path before the
  fallback-branch skip landed. The next wrapper run
  `20260408-172213-91078` no longer reproduced that OOM. It stayed
  alive long enough for a live `jstack 91183`, which showed the new
  hotspot in `Expression$CombinedTokenList.equals()` ->
  `Expression.equals()` -> `Domain.Guard.equals()` ->
  `Flow.State.equals()` during `Flow$Analyzer.enqueue(...)`.
  That run was then killed intentionally; it had shifted from
  normalization/memory blow-up into very expensive guard/state equality
  on giant residual expressions.
- 2026-04-08: Added gated residual diagnostics under the existing
  `helidon.build.archetype.engine.v2.debugReduction` flag to capture
  residual origins and the largest path guards during Helidon
  `compile_gate`. The focused
  `mvn -pl archetype/engine-v2 -am -Dtest=ExpressionTest,DomainTest,FlowTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  slice still passes with `71` tests, and `git diff --check` is clean.
- 2026-04-08: Wrapper run `20260408-185149-95429` was intentionally
  killed after `78.15s` to flush the new shutdown summary. It showed
  only a modest number of true residual origins
  (`92` residual lowers, `75` unhandled compares, `17` unhandled
  contains) versus many more residual merges
  (`371` same-decision residual OR merges, `319` raw fallback OR
  merges). The dominant residual expressions were all
  `media.json-lib` checks plus a few short conjunctions around them.
- 2026-04-08: Narrower rerun `20260408-185441-96146` added symbol-shape
  detail to those origins and confirmed the important point:
  the problematic symbol is
  `media.json-lib, kind=OPEN_TEXT, guardable=false, tainted=true`.
  Those comparisons are therefore not residual because the Helidon
  conditions are exotic; they are residual because the symbol got
  degraded before lowering. The likely trigger is mixed symbol seeding
  for `media.json-lib`: Helidon defines it both as nested `enum`
  inputs and as plain `text` assignments / presets
  (`main.xml` lines around `179`, `194`, `240`, and `292`), and
  `Flow.SymbolSeed.merge(...)` currently collapses mixed kinds to
  `Spec.OpenText()` with `guardable=false`.
- 2026-04-08: The same diagnostic summary showed the giant residual
  growth is mostly downstream accumulation, not many distinct origin
  sites. The largest analyzed path guards were under `docker` scope,
  with smaller but still large guards under `media` and `app-type`,
  which fits the many later Docker/file include conditions that depend
  on `media.json-lib`.

- 2026-04-06: Created this follow-up task after the user rejected the
  previous phase-2 completion claim. The remaining work is not
  `RefsInvoker`; it is the strict removal of `ScriptCompiler`'s old
  internal reachability API and its compatibility projection layer.
- 2026-04-06: Current branch is `scriptcompiler-phase1` with a dirty
  worktree on top of `d75396578`
  (`ScriptCompiler: complete phase 1 flow bridge`). Dirty source files
  already include `Domain.java`, `Flow.java`, `ScriptCompiler.java`,
  `FlowTest.java`, `ScriptCompilerTest.java`, and the new compiler
  fixtures under
  `archetype/engine-v2/src/test/resources/compiler/`.
- 2026-04-06: Local validation is currently green for the focused
  engine-v2 slice (`199` tests), `compile_gate` passed, and
  `diff_variations` passed with no `projects.csv` churn.
  `diff_projects` remains externally blocked because baseline `HEAD`
  generation failed first with `Unresolved variable: security.atz`.
- 2026-04-06: Known remaining legacy cutover points:
  `flowAnalysis()` is built but not on the main runtime projection
  path, `FlowProjector` still projects into private reachability state,
  the old private model still lives inside `ScriptCompiler`, and
  `Variations` still calls compatibility helpers such as
  `activationCondition(...)`.
- 2026-04-06: Resume checkpoint revalidated without changing the dirty
  source tree. Focused engine-v2 tests still pass with `199` tests.
  Wrapper run `20260406-153305-54991` passed `compile_gate`
  (`3.57s` vs `5s`). Wrapper run `20260406-153318-55085` passed
  `diff_variations` with no `projects.csv` churn (`8.10s` vs `15s`).
  Wrapper run `20260406-153351-55578` reran `diff_projects` and still
  failed on baseline `HEAD` generation before compare with
  `Unresolved variable: security.atz`
  from Helidon `main.xml:2593`.
- 2026-04-06: Removed the old private `ScriptCompiler` type names from
  the current dirty tree. `ScriptCompiler.java` no longer contains
  `InputDomain`, `Reachability`, `ConstantBindings`,
  `ConditionSemantics`, `SupportedTerms`, `ConstraintSet`,
  `ScalarConstraint`, `ListConstraint`, or `Split`; the file now uses
  `SymbolFacts`, `GuardRegion`, `ValueCases`, `NodeCondition`, and
  `ResolvedGuard` instead.
- 2026-04-06: The first thin `Domain.Guard` wrapper attempt compiled but
  regressed focused engine-v2 behavior. The fix that restored the green
  slice was:
  source-tree domain reconstruction is back in `initializeDomains()`,
  open-vs-closed symbol facts are rebuilt from the script tree rather
  than only from merged `Flow` symbols, and the old availability /
  complement normalization logic now lives under the renamed
  `SymbolFacts` / `GuardRegion` internal types.
- 2026-04-06: Fresh local validation after the aggressive rename /
  rebuild is green again. Focused engine-v2 slice
  (`ExpressionTest, ScriptCompilerTest, VariationsTest, DomainTest,
  FlowTest, JsonScriptWriterTest`) passed with `199` tests. Wrapper run
  `20260406-161359-58630` passed `compile_gate` (`3.59s` vs `5s`).
  Wrapper run `20260406-161410-58728` passed `diff_variations` with no
  `projects.csv` churn (`8.75s` vs `15s`). Wrapper run
  `20260406-161441-59127` reran `diff_projects`; it still failed on the
  external baseline-side Helidon generation blocker before compare, with
  repeated `Unresolved variable: security.atz` errors in
  `diff_projects-baseline-generate.log`. The wrapper restored
  `~/.m2` to the current workspace install at the end.
- 2026-04-06: Final lexical cleanup also removed the remaining old
  helper type names `ConstraintSet`, `ScalarConstraint`,
  `ListConstraint`, and `Split` from `ScriptCompiler.java`
  (`GuardShape`, `ScalarShape`, `ListShape`, `ShapePartition` now carry
  that logic). `rg` now finds none of the old private API type names in
  the file, and the focused `199`-test engine-v2 slice still passes
  after the rename-only cleanup.
- 2026-04-06: Completed the same lexical cleanup in
  `Domain.Guards`: `ConstraintSet`, `ScalarConstraint`,
  `MembershipConstraint`, and the remaining split helper were renamed to
  `DecisionShape`, `ScalarShape`, `MembershipShape`, and
  `DecisionPartition`. The exact old helper/type-name search now comes
  back empty across both `ScriptCompiler.java` and `Domain.java`.
- 2026-04-06: Revalidated after the final `Domain` rename pass. Focused
  engine-v2 slice
  (`ExpressionTest, ScriptCompilerTest, VariationsTest, DomainTest,
  FlowTest, JsonScriptWriterTest`) still passed with `199` tests.
  Wrapper run `20260406-162513-63917` passed `compile_gate`
  (`3.72s` vs `5s`). Wrapper run `20260406-162529-64142` passed
  `diff_variations` with no `projects.csv` churn (`8.02s` vs `15s`).
- 2026-04-06: Finished the remaining aggressive cutover instead of
  just renaming helpers. `Flow.Model` now owns node key resolution,
  scope lookup, declared-value lookup, and activation-condition
  rendering. `ScriptCompiler` no longer keeps the old `scopes` /
  `declaredValueCache` bridge or the `scopeId(...)` /
  `declaredValue(...)` / `activationCondition(...)` compatibility
  helpers, and `Variations` now reads those queries directly from
  `Flow.Model`.
- 2026-04-06: Revalidated the cutover with focused reports for
  `FlowTest` (`4` tests), `ScriptCompilerTest` (`87` tests), and
  `VariationsTest` (`1` test), all green from
  `target/surefire-reports/`. The Maven test session hung after the
  reports were written and had to be killed, but the compile gate
  `mvn -pl archetype/engine-v2 -am -DskipTests compile` completed with
  `BUILD SUCCESS`.
- 2026-04-07: Removed the single-use defensive reset path from
  `ScriptCompiler`. `clearAnalysisState()` is gone, the staged
  `buildFlowIr()` / `buildFlowAnalysis()` / `buildFlowModel()` helpers
  are gone, and `init()` now performs straight-line one-time setup:
  inline, index, build `Flow`, and project guards. Also dropped the
  `AtomicBoolean`, `volatile`, and `synchronized` scaffolding around
  this path because the compiler is intentionally single-use.
  `mvn -pl archetype/engine-v2 -am -DskipTests compile` passed, and
  focused reports for `FlowTest`, `ScriptCompilerTest`, and
  `VariationsTest` are green again. The focused Maven run hung after
  surefire wrote reports, so the stray Maven JVM was killed.
- 2026-04-07: Removed another stale-defensive sweep from
  `ScriptCompiler`. The `flowIr()` / `flowAnalysis()` / `flowModel()`
  accessors no longer re-check for null after `init()`, the impossible
  `local == null` branch after `localGuard(...)` is gone, the dead
  `LegacyBackend` `flowIr` field/parameter is gone, the unused
  `conditionDemands(expr, scope)` overload and unused
  `rememberConditionRefs(..., conditionDemands)` parameter are gone,
  and a few remaining internal `requireNonNull(...)` assertions on
  private backend helper value objects were dropped. The current file
  size is down to `3695` lines. `mvn -pl archetype/engine-v2 -am
  -DskipTests compile` passed, and focused reports for `FlowTest`,
  `ScriptCompilerTest`, and `VariationsTest` are green again. The
  focused Maven run hung after surefire wrote reports, so the stray
  Maven JVM was killed.
- 2026-04-07: Rewrote the IntelliJ-flagged list-removal patterns in
  `ScriptCompiler` to remove the suspicious `List.remove()` calls from
  loop bodies. The condition minimizers now use a shared
  `withoutIndex(...)` helper instead of copying then removing by index,
  and the stub-container split now moves the child range via
  `subList(...).clear()` plus append rather than repeated
  `parent.children().remove(index)` in a loop. `rg` now shows only
  non-suspicious `remove()` sites in the file, the current size is
  `3679` lines, `mvn -pl archetype/engine-v2 -am -DskipTests compile`
  passed, and focused reports for `FlowTest`, `ScriptCompilerTest`,
  and `VariationsTest` are green again. The focused Maven run hung
  after surefire wrote reports, so the stray Maven JVM was killed.
- 2026-04-07: Moved `withoutIndex(...)` out of `ScriptCompiler` into
  `common/common` as `Lists.withoutIndex(...)`, added `ListsTest`
  coverage for it, and switched all `ScriptCompiler` call sites to the
  shared helper. `mvn -pl common/common -am -Dtest=ListsTest
  -Dsurefire.failIfNoSpecifiedTests=false test` passed, 
  `mvn -pl archetype/engine-v2 -am -DskipTests compile` passed, and
  focused reports for `FlowTest`, `ScriptCompilerTest`, and
  `VariationsTest` are green again. The focused Maven run hung after
  surefire wrote reports, so the stray Maven JVM was killed.
- 2026-04-07: Removed the stale prompt-style boolean parsing from the
  compiler and JSON writer. `ScriptCompiler.redundantBooleanInput(...)`
  no longer treats script defaults like user responses, so `default`
  only prunes when it is absent or a real script boolean `false`;
  invalid literals such as `no` are no longer coerced to `false`.
  `JsonScriptWriter` now emits boolean defaults as booleans only when
  the script literal is a real script boolean and otherwise preserves
  the raw script text. Added a focused black-box
  `ScriptCompilerTest` regression that compiles a duplicated
  preset-driven boolean input through the public compiler path and a
  `JsonScriptWriterTest` regression for invalid boolean defaults.
  `mvn -pl archetype/engine-v2 -am
  -Dtest=ScriptCompilerTest,JsonScriptWriterTest
  -Dsurefire.failIfNoSpecifiedTests=false test` passed with `96`
  tests and `BUILD SUCCESS`.
- 2026-04-07: Converted the remaining structural `ScriptCompilerTest`
  cases for preset-implied and nested-stub complement behavior to the
  same golden-XML style as the rest of the suite. Added expected
  compiled scripts for `preset-implied-nested-input`,
  `merged-stub-complement`, and `covering-stub-complement`, rewrote
  the three tests to compare compiled `main.xml` against those golden
  files, and removed the now-dead `conditionLiteral(...)` helper plus
  the unused matcher imports it needed. `mvn -pl archetype/engine-v2
  -am -Dtest=ScriptCompilerTest
  -Dsurefire.failIfNoSpecifiedTests=false test` passed with `88`
  tests and `BUILD SUCCESS`.
- 2026-04-07: Converted six more `ScriptCompilerTest` structure-driven
  cases to expected compiled XML: `duplicate-discrete-steps`,
  `duplicate-cross-product-covered-dimension`,
  `option-specific-value-implication`,
  `duplicate-cross-product-unsupported-option-complement`, and the two
  conditional enum guard checks now all assert against golden
  `main.xml` output instead of reconstructing parent guard expressions
  in matcher code. Added expected scripts for those fixtures,
  including one shared `conditional-json-lib.xml` expected output for
  both enum-guard tests. `mvn -pl archetype/engine-v2 -am
  -Dtest=ScriptCompilerTest
  -Dsurefire.failIfNoSpecifiedTests=false test` passed with `88`
  tests and `BUILD SUCCESS`.
- 2026-04-07: Converted the remaining open-domain and list-availability
  structure checks in `ScriptCompilerTest` to golden XML too:
  `open-domain-complement`, `open-domain-option-output`, and
  `list-option-availability` now compare compiled `main.xml` against
  expected scripts under `compiler/expected/` instead of matcher-based
  parent-condition inspection. `mvn -pl archetype/engine-v2 -am
  -Dtest=ScriptCompilerTest
  -Dsurefire.failIfNoSpecifiedTests=false test` passed with `88`
  tests and `BUILD SUCCESS`.
- 2026-04-07: Removed `ScriptCompiler`'s remaining same-class calls to
  its own `sourceNode()` / `flowIr()` / `flowAnalysis()` /
  `flowModel()` accessors. Internal code now references the backing
  `flowModel` field directly instead of bouncing through `flowModel()`,
  including validation, fact lookup, stub rendering, and merged-step
  activation handling. A follow-up `rg` over `ScriptCompiler.java`
  shows only the getter declarations left. `mvn -pl archetype/engine-v2
  -am -Dtest=ScriptCompilerTest
  -Dsurefire.failIfNoSpecifiedTests=false test` passed with `88`
  tests and `BUILD SUCCESS`. An attempted broader
  `ScriptCompilerTest,VariationsTest` run was interrupted after the
  surefire fork spent several minutes active without ever writing a
  `VariationsTest` report.
- 2026-04-07: Follow-up cleanup after the first commit: added the
  previously missed compiler fixture directories
  `root-variable-output-condition/` and `shared-definition-union/` to
  the intended repo change set, and shortened the fixture-backed
  `ScriptCompilerTest` method names so they stay close to their
  resource directory names while still keeping a `test...` prefix.
  `mvn -pl archetype/engine-v2 -am -Dtest=ScriptCompilerTest
  -Dsurefire.failIfNoSpecifiedTests=false test` passed with `88`
  tests and `BUILD SUCCESS`.
- 2026-04-07: Extracted the two front-end preparation passes out of
  `ScriptCompiler` without changing behavior. The old inner
  `InlineInvoker` is now package-private `ScriptInliner`, and the old
  recursive `indexSourceTree()` / `indexNode(...)` path is now
  package-private visitor-based `ScriptIndexer`. `ScriptCompiler.init()`
  now orchestrates those passes directly before building `Flow`.
  Focused validation with `mvn -pl archetype/engine-v2 -am
  -Dtest=ScriptCompilerTest
  tests and `BUILD SUCCESS`.
- 2026-04-07: Flattened the short-lived `ScriptIndexer.Index` wrapper
  back into `ScriptIndexer` itself. The visitor now owns its collected
  `declaredValues`, `definedRefs`, `refTypes`, and `textInputRefs`
  state directly, and `ScriptCompiler.init()` reads those fields from a
  single `ScriptIndexer` instance after `index(sourceNode)`. Focused
  validation with `mvn -pl archetype/engine-v2 -am
  -Dtest=ScriptCompilerTest
  -Dsurefire.failIfNoSpecifiedTests=false test` still passed with `88`
  tests and `BUILD SUCCESS`.
- 2026-04-07: Moved root-script loading into `ScriptInliner` and
  stopped duplicating collaborator state in `ScriptCompiler`.
  `ScriptCompiler` now stores the incoming `Script.Source`, lazily
  materializes `sourceNode` via `sourceNode = inliner.inline(source)`,
  keeps `ScriptInliner` and `ScriptIndexer` as fields, reads index data
  directly from `indexer`, and resolves output base directories through
  `inliner.workDir(node)` instead of a compiler-owned `workDirs` map.
  Focused validation with `mvn -pl archetype/engine-v2 -am
  -Dtest=ScriptCompilerTest
  -Dsurefire.failIfNoSpecifiedTests=false test` passed with `88`
  tests and `BUILD SUCCESS`.
- 2026-04-08: Follow-up on the `Expression.reduce()` demotion boundary.
  Current uncommitted files are `Expression.java`, `Domain.java`,
  `Flow.java`, `ScriptCompiler.java`, and `VariationEngine.java`.
  The new delta removes several non-cosmetic reductions from the
  flow/variation path:
  `Expression.inline(...)` now only folds constants,
  `ScriptCompiler.residualGuard(...)` now delegates to
  `Domain.Guards.residualGuard(...)`,
  `VariationEngine` row storage no longer eagerly reduces row
  expressions, `Domain.Guards.toExpression(...)` and the underlying
  `Decision` / `DecisionShape` / `ScalarShape` / `MembershipShape`
  rendering no longer reduce at each nested layer, and
  `Domain.Guards.or(...)` now limits residual compaction plus cache use
  to small expressions only.
- 2026-04-08: Focused validation is green again with
  `mvn -pl archetype/engine-v2,maven-plugins/helidon-archetype-maven-plugin -am \
  -Dtest=DomainTest,FlowTest,ScriptCompilerTest,VariationsTest#testVariationsList1+testVariationsBoolean1+testVariationEntriesText1+testVariationsSubstitutions+testVariationsConditionals+testVariationsPruneInactiveBranchBeforePresetValidation+testVariationsIgnoreProjectionOverestimate+testVariationsFailWhenIntermediateCountExceedsMax,VariationPlanTest,IntegrationTestMojoTest \
  -Dsurefire.failIfNoSpecifiedTests=false test`
  (`BUILD SUCCESS`, `113` tests).
- 2026-04-08: Helidon wrapper `compile_gate` is still red on the dirty
  tree. Latest failing run is `20260408-091833-67743`. The earlier QMC
  hot path in `Expression.reduce1(...)` was removed, but the current
  blocker is still inside `Flow.analyze(...)`: a `jstack` at
  `2026-04-08 09:19:06` shows the main thread in
  `Expression.or(Expression.java:156)` called from
  `Domain.Guards.or(Domain.java:959)` via
  `Flow.Analyzer.mergeGuard(...)`. In other words, residual
  simplification is no longer the bottleneck; raw residual-expression
  growth and concatenation are.
- 2026-04-07: Shifted `Flow` to the owning runtime object shape used by
  the compiler. `ScriptCompiler` now holds `private final Flow flow =
  new Flow(ctx.scope())`, calls `flow.process(sourceNode)` during
  `init()`, and stops storing separate `flowIr` / `flowAnalysis` /
  `flowModel` fields. The existing compiler getters now forward to
  `flow.ir()`, `flow.analysis()`, and `flow.model()`. Focused
  validation with `mvn -pl archetype/engine-v2 -am
  -Dtest=FlowTest,ScriptCompilerTest
  -Dsurefire.failIfNoSpecifiedTests=false test` passed with `92`
  tests and `BUILD SUCCESS`.
- 2026-04-07: Removed the now-redundant static `Flow.ir(...)`,
  `Flow.analyze(...)`, and `Flow.model(...)` entrypoints. `Flow`
  now exposes only instance lifecycle/state methods, with
  `process(Node)` for normal lowering and one package-private
  `process(Ir, Node)` path used by the focused analyzer test. `FlowTest`
  now drives the object through instance methods only. Focused
  validation with `mvn -pl archetype/engine-v2 -am
  -Dtest=FlowTest,ScriptCompilerTest
  -Dsurefire.failIfNoSpecifiedTests=false test` passed with `92`
  tests and `BUILD SUCCESS`.
- 2026-04-07: Centralized the scalar-only string conversion policy in
  `Value.scalarLiteral(Value<?>)`. `Flow` now uses that helper for
  raw scalar equality lowering, and `Domain.Fact.ExactCase` delegates to
  the same helper instead of re-encoding the switch locally. Focused
  validation with `mvn -pl archetype/engine-v2 -am
  -Dtest=DomainTest,FlowTest,ScriptCompilerTest
  -Dsurefire.failIfNoSpecifiedTests=false test` passed with `94`
  tests and `BUILD SUCCESS`.
- 2026-04-07: Snapshot commit `cc0fbac0e`
  (`Extract script preparation helpers`) captures the helper extraction
  baseline: `ScriptCompiler` now owns package-private `ScriptInliner`,
  `ScriptIndexer`, and stateful `Flow`, with inlining/indexing/flow
  setup done directly in `init()`.
- 2026-04-07: Made `Variations` standalone on top of those extracted
  helpers. The class now has a public source-backed constructor
  `new Variations(source, cwd)`, `compute(...)` is an instance method,
  it prepares its own inlined/indexed/flow-processed source once, and
  the returned computed result remains an immutable `Variations` set.
  Plugin callers and tests no longer go through
  `Variations.compute(ScriptCompiler, ...)`.
- 2026-04-07: Focused validation for the standalone cutover passed with
  `mvn -pl archetype/engine-v2,maven-plugins/helidon-archetype-maven-plugin -am
  -Dtest=DomainTest,FlowTest,ScriptCompilerTest,VariationsTest#testVariationsList1+testVariationsBoolean1+testVariationEntriesText1+testVariationsSubstitutions+testVariationsConditionals+testVariationsPruneInactiveBranchBeforePresetValidation+testVariationsIgnoreProjectionOverestimate+testVariationsFailWhenIntermediateCountExceedsMax,VariationPlanTest,IntegrationTestMojoTest -Dsurefire.failIfNoSpecifiedTests=false test`
  (`BUILD SUCCESS`, `114` tests). A broader run including
  `VariationsTest#testVariationsE2e` / `testVariationsFilters` spent
  minutes CPU-bound inside `Flow.analyze` while processing
  `e2e/main.xml`; the thread dump shows the hot path through
  `Domain$ScalarShape.intersect(...)` from `Flow$Analyzer.mergeGuard(...)`.
- 2026-04-07: Split the short-lived mixed `Variations` shape into two
  public types. `Variations` is back to being data-only, while the new
  `VariationEngine` now owns all source preparation and computation
  logic. The plugin and tests instantiate `VariationEngine` directly,
  and the focused engine/plugin validation command above still passes
  unchanged after the split.
- 2026-04-07: Revalidated and committed the split as snapshot
  `706b50217`
  (`Split variation computation from Variations`). Focused validation
  with `mvn -pl archetype/engine-v2,maven-plugins/helidon-archetype-maven-plugin -am
  -Dtest=DomainTest,FlowTest,ScriptCompilerTest,VariationsTest#testVariationsList1+testVariationsBoolean1+testVariationEntriesText1+testVariationsSubstitutions+testVariationsConditionals+testVariationsPruneInactiveBranchBeforePresetValidation+testVariationsIgnoreProjectionOverestimate+testVariationsFailWhenIntermediateCountExceedsMax,VariationPlanTest,IntegrationTestMojoTest -Dsurefire.failIfNoSpecifiedTests=false test`
  passed with `BUILD SUCCESS` (`113` tests), `git diff --check` stayed
  clean, and no further fixes were needed before commit.
- 2026-04-08: Committed the follow-up expression-demotion and
  guard-compaction pass as `c6fcbfad5`
  (`ScriptCompiler: trim eager expression normalization`). The clean
  committed delta spans `Domain.java`, `Expression.java`, `Flow.java`,
  `ScriptCompiler.java`, `ScriptIndexer.java`,
  `VariationEngine.java`, `Lists.java`, and `ListsTest.java`.
- 2026-04-08: Revalidated the clean snapshot with
  `mvn -pl archetype/engine-v2,maven-plugins/helidon-archetype-maven-plugin -am
  -Dtest=DomainTest,FlowTest,ScriptCompilerTest,VariationsTest#testVariationsList1+testVariationsBoolean1+testVariationEntriesText1+testVariationsSubstitutions+testVariationsConditionals+testVariationsPruneInactiveBranchBeforePresetValidation+testVariationsIgnoreProjectionOverestimate+testVariationsFailWhenIntermediateCountExceedsMax,VariationPlanTest,IntegrationTestMojoTest -Dsurefire.failIfNoSpecifiedTests=false test`,
  `mvn -pl common/common,archetype/engine-v2 -am
  -Dtest=ListsTest,DomainTest,FlowTest
  -Dsurefire.failIfNoSpecifiedTests=false test`, and
  `mvn -pl archetype/engine-v2 -am
  -Dtest=ScriptCompilerTest,FlowTest,DomainTest
  -Dsurefire.failIfNoSpecifiedTests=false test`. All three focused
  slices are green, and no stray Maven or Helidon JVMs were left
  running after the troubleshooting session.
- 2026-04-08: Refreshed the `.ai` handoff to the clean `c6fcbfad5`
  snapshot. The immediate resume point is still the failing Helidon
  wrapper run `20260408-091833-67743`, and the next code change should
  target raw residual OR-expression growth in `Domain.Guards.or(...)`
  rather than restoring eager reduction or undoing the extracted
  `VariationEngine` / `ScriptInliner` / `ScriptIndexer` split.
- 2026-04-08: Converted the new `FlowTest` script-shape regressions
  from programmatic `Nodes.*` construction to XML fixtures under
  `archetype/engine-v2/src/test/resources/flow/`. The fixtures now use
  real `XMLScriptReader` shapes, with `if="..."` on supported nodes and
  `<output>` around file emissions, so the tests exercise the same
  loader path as `ScriptCompiler`. Focused validation with
  `mvn -pl archetype/engine-v2 -am
  -Dtest=ExpressionTest,DomainTest,FlowTest
  -Dsurefire.failIfNoSpecifiedTests=false test`
  passed with `75` tests and `BUILD SUCCESS`.
- 2026-04-09: Extracted the old token-specific
  `Expression$CombinedTokenList` helper into the common module as
  generic `Lists.concatView(...)` / `Lists.appendView(...)`. The new
  common `ConcatViewList` implementation keeps the same lazy
  concatenation, iterative traversal, and cached hash behavior, and the
  new `ListsTest` coverage now pins deep equality/hash plus nullable
  tail handling at the utility layer. Focused validation with
  `mvn -pl common/common,archetype/engine-v2 -am
  -Dtest=ListsTest,ExpressionTest,DomainTest,FlowTest
  -Dsurefire.failIfNoSpecifiedTests=false test`
  passed with `87` tests and `BUILD SUCCESS`.
- 2026-04-09: Fixed unsupported literal-list `contains` handling in
  `Flow`. The literal-list/literal path now null-checks
  `containsValues(...)` before `containsAll(...)`, and
  `Expression.foldConstants()` now preserves unsupported typed
  operations instead of throwing while residual guards are normalized.
  Added XML-backed `FlowTest` coverage for `['json'] contains true` and
  an `ExpressionTest` regression for failed constant folding. Focused
  validation with `mvn -pl archetype/engine-v2 -am
  -Dtest=ExpressionTest,DomainTest,FlowTest
  -Dsurefire.failIfNoSpecifiedTests=false test`
  passed with `77` tests and `BUILD SUCCESS`.
- 2026-04-09: Finished the investigation the user asked for on the
  remaining Helidon `mp/observability` mismatch. The raw-source
  `VariationEngine` path was already correct; the remaining 4-row
  packaged-artifact collapse reproduced as a `ScriptCompiler` bug in
  the new XML-backed
  `ScriptCompilerTest.testSiblingPresetsDoNotDisableLaterNestedEnumInput`.
  The fix constrains pre-node facts to the current projected branch
  before `projectSourceGuards()` replays boolean/input control
  expressions, so sibling preset exact cases no longer make later nested
  inputs look unreachable. Added expected compiled XML
  `compiler/expected/observability-sibling-presets.xml`, kept the
  `VariationEngine` post-`exec` filter regression in place, and updated
  the observability `VariationsTest` expectations to cover only the
  preserved variation dimensions. Focused validation with
  `mvn -pl archetype/engine-v2 -am
  -Dtest=ScriptCompilerTest#testSiblingPresetsDoNotDisableLaterNestedEnumInput,VariationsTest#testVariationsFiltersCanReferenceNestedInputKeys,VariationsTest#testVariationsKeepNestedEnumWhenParentBooleanIsExternallyFixed,VariationsTest#testVariationsKeepNestedEnumAcrossSourceAndExecGraph
  -Dsurefire.failIfNoSpecifiedTests=false test`
  passed with `4` tests and `BUILD SUCCESS`.
- 2026-04-09: A broader `engine-v2` slice still is not clean:
  `mvn -pl archetype/engine-v2 -am
  -Dtest=ExpressionTest,ScriptCompilerTest,VariationsTest
  -Dsurefire.failIfNoSpecifiedTests=false test`
  now reports two additional failures outside the observability fix
  scope. `ScriptCompilerTest.testPresetTypeMismatch` still hits a
  `Flow.Model.exactValue()` / `Value.typed(...)` null-unboxing path on
  invalid boolean exact values, and
  `ScriptCompilerTest.testOpenDomainComplement` still emits the
  narrower `${media.json-lib} != 'jsonp'` condition instead of the
  expected open-domain-aware form. Those failures were not addressed in
  this pass.
- 2026-04-09: Ran the Helidon regression wrapper via the
  `helidon-archetype-regression` skill:
  `.agents/skills/helidon-archetype-regression/scripts/run-regression.sh
  diff_variations --helidon-dir /Users/rgrecour/workspace/helidon
  --baseline-ref b47015655`.
  Wrapper run `20260409-183624-66348` confirms the packaged
  `mp/observability` regression is fixed and the current side now
  satisfies Helidon's exact-count gate with `100` total rows in
  `2.89s` (`< 15s`). The compare is still red against baseline
  `b47015655`, but the remaining delta is now only `19` removed rows and
  `0` added rows. Every removed row is
  `flavor=mp app-type=database`; `18` of them carry
  `media.json-lib=jsonb` and `1` carries `media.json-lib=jackson`.
  Representative artifacts live under
  `.agents/skills/helidon-archetype-regression/.state/20260409-183624-66348/`,
  and `~/.m2` was restored to the current workspace install. Because
  `diff_variations` still reports output churn, `diff_projects` remains
  blocked.
- 2026-04-09: Corrected the baseline choice. The user had explicitly
  asked for the current tip of local branch
  `archetype-substitute-thruthy-expressions`, which matches tag
  `scriptcompiler-redesign-spec-2026-04-03` and resolves to
  `333e06f877c7bb948e23daa203e2062922bf6ac5`
  (`Clarify expression raw token naming`, 2026-04-02). The earlier
  wrapper run against `b47015655` used the wrong historical commit and
  should not be used for product conclusions.
- 2026-04-09: Reran the Helidon regression wrapper against the correct
  requested baseline:
  `.agents/skills/helidon-archetype-regression/scripts/run-regression.sh
  diff_variations --helidon-dir /Users/rgrecour/workspace/helidon
  --baseline-ref 333e06f877c7bb948e23daa203e2062922bf6ac5`.
  Wrapper run `20260409-192148-71454` passed cleanly:
  `outputs changed: no`, baseline wall-clock `7.81s`, current
  wall-clock `2.85s`, threshold `15s`, and `~/.m2` was restored to the
  current workspace install. This confirms the packaged variation
  output now matches the requested known-good baseline, so
  `diff_projects` is unblocked again.
- 2026-04-09: Committed the packaged variation fix and its regressions
  as local commit `2d083695b`
  (`Archetype: fix packaged variation projection`). The commit includes
  the `VariationEngine` post-`exec` filter recheck, the projected-fact
  fix in `ScriptCompiler`, and the new compiler/variation fixtures that
  reproduce the earlier `mp/observability` packaged-artifact failure.
