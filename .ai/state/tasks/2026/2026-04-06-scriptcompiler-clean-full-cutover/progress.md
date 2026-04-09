# Progress

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
