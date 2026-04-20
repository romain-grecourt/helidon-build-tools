/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.build.archetype.engine.v2;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import io.helidon.build.archetype.engine.v2.Context.Scope;
import io.helidon.build.archetype.engine.v2.Context.ScopeValue;
import io.helidon.build.archetype.engine.v2.Context.ValueKind;
import io.helidon.build.archetype.engine.v2.Domain.Guard;
import io.helidon.build.archetype.engine.v2.Domain.Guards;
import io.helidon.build.archetype.engine.v2.Domain.Spec;
import io.helidon.build.archetype.engine.v2.Domain.Symbol;
import io.helidon.build.archetype.engine.v2.InputResolver.InvalidInputException;
import io.helidon.build.archetype.engine.v2.InputResolver.ResolvedKind;
import io.helidon.build.archetype.engine.v2.Node.Kind;
import io.helidon.build.archetype.engine.v2.ScriptInvoker.InvocationException;
import io.helidon.build.archetype.engine.v2.Variations.Diagnostics;
import io.helidon.build.archetype.engine.v2.Variations.Finding;
import io.helidon.build.archetype.engine.v2.Variations.Plan;
import io.helidon.build.archetype.engine.v2.Variations.Request;
import io.helidon.build.common.Lists;
import io.helidon.build.common.Maps;
import io.helidon.build.common.logging.Log;
import io.helidon.build.common.logging.LogLevel;

import static java.util.Objects.requireNonNull;

/**
 * Computes {@link Variations} for a prepared script source.
 */
public final class VariationEngine {
    private final Script.Source source;
    private final Path cwd;
    private final ScriptInliner inliner;
    private final ScriptIndexer indexer;
    private final Flow flow;
    private boolean initialized;
    private Prepared prepared;
    private Request cachedPlanRequest;
    private PlanAnalysis cachedPlanAnalysis;

    /**
     * Create a new instance.
     *
     * @param source source
     * @param cwd    cwd
     */
    public VariationEngine(Script.Source source, Path cwd) {
        this.cwd = requireNonNull(cwd, "cwd is null");
        this.source = requireNonNull(source, "source is null");
        Context ctx = new Context();
        ctx.pushCwd(cwd);
        this.inliner = new ScriptInliner(ctx);
        this.indexer = new ScriptIndexer(ctx.scope());
        this.flow = new Flow(ctx.scope());
    }

    /**
     * Compute variations.
     *
     * @param request computation request
     * @return computed variations
     * @throws IllegalStateException if the intermediate variation row count exceeds the configured limit
     */
    public Variations compute(Request request) {
        requireNonNull(request, "request is null");
        init();
        if (request.plans().isEmpty()) {
            clearPlanCache();
            return new PlainComputation(
                    request.filters(),
                    request.externalValues(),
                    request.externalDefaults(),
                    request.maxIntermediateVariations())
                    .compute();
        }
        if (request.plans().size() == 1) {
            clearPlanCache();
            return new PlanComputation(request).computeSingle();
        }
        PlanComputation computation = new PlanComputation(request);
        PlanAnalysis analysis = cachedPlanRequest == request && cachedPlanAnalysis != null
                ? cachedPlanAnalysis
                : computation.analysis();
        cachePlanAnalysis(request, analysis);
        return computation.compute(analysis);
    }

    /**
     * Analyze semantic plan coverage against the current script.
     *
     * @param request computation request
     * @return semantic diagnostics
     */
    public Diagnostics analyze(Request request) {
        requireNonNull(request, "request is null");
        init();
        if (request.plans().isEmpty()) {
            clearPlanCache();
            return Diagnostics.empty();
        }
        PlanAnalysis analysis = new PlanComputation(request).analysis();
        cachePlanAnalysis(request, analysis);
        return analysis.diagnostics;
    }

    private void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        Node sourceNode = inliner.inline(source);
        indexer.index(sourceNode);
        flow.process(sourceNode);
        prepared = new Prepared(sourceNode, indexer);
    }

    private void cachePlanAnalysis(Request request, PlanAnalysis analysis) {
        cachedPlanRequest = request;
        cachedPlanAnalysis = analysis;
    }

    private void clearPlanCache() {
        cachedPlanRequest = null;
        cachedPlanAnalysis = null;
    }

    private Map<String, String> resolveExternalValues(Map<String, String> externalValues,
                                                      Map<String, String> externalDefaults) {
        if (externalValues.isEmpty()) {
            return Map.of();
        }
        Context context = new Context()
                .externalValues(externalValues)
                .externalDefaults(externalDefaults)
                .pushCwd(cwd);
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : externalValues.keySet()) {
            ScopeValue<?> value = context.scope().getOrCreate(key).value();
            if (value.isPresent()) {
                values.put(key, Value.toString(value));
            }
        }
        return Collections.unmodifiableMap(values);
    }

    private Map<String, String> resolveExternalDefaults(Map<String, String> externalValues,
                                                        Map<String, String> externalDefaults) {
        if (externalDefaults.isEmpty()) {
            return Map.of();
        }
        Context context = new Context()
                .externalValues(externalValues)
                .externalDefaults(externalDefaults)
                .pushCwd(cwd);
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : externalDefaults.entrySet()) {
            try {
                String value = Value.toString(context.defaultValue(entry.getKey()));
                if (value != null) {
                    values.put(entry.getKey(), value);
                }
            } catch (RuntimeException ignored) {
                values.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(values);
    }

    private Value<String> externalDefaultValue(String key,
                                               Map<String, String> externalDefaults,
                                               Map<String, String> resolvedExternalDefaults) {
        String value = resolvedExternalDefaults.get(key);
        if (value == null) {
            value = externalDefaults.get(key);
        }
        return Value.of(value);
    }

    private Value.Type valueType(String key) {
        InputModel input = prepared.inputsByKey.get(key);
        if (input != null) {
            return input.kind.valueType();
        }
        Flow.SymbolInfo info = symbolInfo(key);
        if (info == null) {
            return Value.Type.STRING;
        }
        switch (info.symbol().domain().kind()) {
            case BOOLEAN:
                return Value.Type.BOOLEAN;
            case MEMBERSHIP:
                return Value.Type.LIST;
            default:
                return Value.Type.STRING;
        }
    }

    private Flow.SymbolInfo symbolInfo(String key) {
        Flow.SymbolInfo info = flow.symbol(key);
        if (info == null && !key.startsWith("~")) {
            info = flow.symbol("~" + key);
        }
        return info;
    }

    private Guard externalConstraintGuard(Map<String, String> resolvedExternalValues) {
        return externalConstraintGuard(resolvedExternalValues, null, null);
    }

    private Guard externalConstraintGuard(Map<String, String> resolvedExternalValues,
                                          String planId,
                                          List<Finding> findings) {
        Guard guard = Guard.TRUE;
        for (Map.Entry<String, String> entry : resolvedExternalValues.entrySet()) {
            Guard next = constraintGuard(entry.getKey(), entry.getValue(), planId, findings, guard);
            if (next != null) {
                guard = flow.and(guard, next);
            }
        }
        return guard;
    }

    private Guard constraintGuard(String key, String value, String planId, List<Finding> findings, Guard current) {
        InputModel input = prepared.inputsByKey.get(key);
        Guard next = input != null ? input.constraintGuard(value) : constraintGuard(key, value, symbolInfo(key));
        if (next != null && findings != null && planId != null && subset(current, next)) {
            findings.add(Finding.redundantPin(planId, key, value));
        }
        return next;
    }

    private Guard constraintGuard(String key, String value, Flow.SymbolInfo info) {
        if (info == null) {
            return null;
        }
        Symbol symbol = info.symbol();
        if (!symbol.guardable() || symbol.tainted() || symbol.domain().kind() == Spec.Kind.OPEN_TEXT) {
            return null;
        }
        switch (symbol.domain().kind()) {
            case BOOLEAN:
            case CHOICE:
            case FINITE_TEXT:
                if (!symbol.domain().contains(value)) {
                    throw new IllegalStateException(String.format("Invalid value '%s' for key '%s'", value, key));
                }
                Guard direct = flow.guards().eq(symbol.id(), value);
                Guard availability = info.availability(value);
                return availability == null ? direct : flow.guards().and(availability, direct);
            case MEMBERSHIP:
                return exactMembershipGuard(info, Value.parseList(value).orElse(List.of()));
            default:
                return null;
        }
    }

    private Guard combinedExcludeGuard(List<Expression> filters, Map<String, String> externalValues) {
        Guard combined = Guard.FALSE;
        for (Expression filter : filters) {
            Expression pruned = prune(filter, externalValues);
            combined = flow.or(combined, flow.conditionGuard(pruned));
        }
        return combined;
    }

    private List<Expression> combineFilters(List<Expression> left, List<Expression> right) {
        if (left.isEmpty()) {
            return right;
        }
        if (right.isEmpty()) {
            return left;
        }
        List<Expression> filters = new ArrayList<>(left.size() + right.size());
        filters.addAll(left);
        filters.addAll(right);
        return filters;
    }

    private Expression prune(Expression expression, Map<String, String> externalValues) {
        if (expression == Expression.TRUE || expression == Expression.FALSE || externalValues.isEmpty()) {
            return expression;
        }
        return expression.inline(variable -> {
            String value = externalValues.get(variable);
            return value == null ? null : Value.parse(value, valueType(variable));
        });
    }

    private boolean subset(Guard left, Guard right) {
        return flow.isFalse(flow.minus(left, right));
    }

    private String render(Guard guard) {
        return flow.expression(guard).literal();
    }

    private Variations.Entry materialize(CandidateRegion region,
                                         List<Expression> filters,
                                         Map<String, String> externalValues,
                                         Map<String, String> externalDefaults,
                                         Map<String, String> resolvedExternalDefaults) {
        Map<String, String> variation = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : region.values.entrySet()) {
            if (!externalValues.containsKey(entry.getKey())) {
                variation.put(entry.getKey(), entry.getValue());
            }
        }
        for (String key : region.textInputs) {
            if (!externalValues.containsKey(key)) {
                variation.put(key, representativeTextValue(key, region.guard, externalDefaults, resolvedExternalDefaults));
            }
        }

        Map<String, ScopeValue<?>> effective = execute(variation, externalValues, externalDefaults);
        if (effective.isEmpty()) {
            return null;
        }

        Map<String, String> values = orderedValues(effective);
        if (filtered(filters, values)) {
            return null;
        }

        Set<String> unbounded = new TreeSet<>();
        for (Map.Entry<String, ScopeValue<?>> entry : effective.entrySet()) {
            if (prepared.textInputs.contains(entry.getKey()) && entry.getValue().kind() != ValueKind.EXTERNAL) {
                unbounded.add(entry.getKey());
            }
        }

        Map<String, String> normalized = Maps.mapValue(effective,
                (key, value) -> value.kind() == ValueKind.USER,
                value -> Value.toString(value),
                TreeMap::new);
        String signature = Lists.join(normalized.entrySet(), " ");
        return Variations.entry(values, unbounded, signature);
    }

    private String representativeTextValue(String key,
                                           Guard regionGuard,
                                           Map<String, String> externalDefaults,
                                           Map<String, String> resolvedExternalDefaults) {
        InputModel input = prepared.inputsByKey.get(key);
        if (input == null) {
            return "<?>";
        }
        return input.declaredValue(regionGuard).asString()
                .or(() -> externalDefaultValue(key, externalDefaults, resolvedExternalDefaults))
                .or(() -> Value.of(input.defaultValue(regionGuard)))
                .orElse("<?>");
    }

    private boolean filtered(List<Expression> filters, Map<String, String> values) {
        if (filters.isEmpty()) {
            return false;
        }
        for (Expression exclude : filters) {
            if (eval(exclude, values)) {
                if (LogLevel.isDebug()) {
                    Log.debug("Excluding variation, rule: %s, entries: %s", exclude.literal(), values);
                }
                return true;
            }
        }
        return false;
    }

    private boolean eval(Expression expression, Map<String, String> values) {
        if (expression == Expression.TRUE) {
            return true;
        }
        if (expression == Expression.FALSE) {
            return false;
        }
        try {
            return expression.eval(variable -> {
                String value = values.get(variable);
                return value == null ? null : Value.dynamic(value);
            });
        } catch (Expression.UnresolvedVariableException ignored) {
            return false;
        }
    }

    private Map<String, ScopeValue<?>> execute(Map<String, String> variation,
                                               Map<String, String> externalValues,
                                               Map<String, String> externalDefaults) {
        try {
            Context context = new Context()
                    .externalValues(externalValues)
                    .externalDefaults(externalDefaults)
                    .pushCwd(cwd);
            variation.forEach((key, value) -> {
                if (externalValues.containsKey(key)) {
                    return;
                }
                Scope scope = context.scope().getOrCreate(key);
                scope.value(Value.dynamic(value), ValueKind.USER);
            });

            Set<Scope> scopes = new LinkedHashSet<>();
            ScriptInvoker.invoke(prepared.node, context, new InputResolver.BatchResolver(context), node -> {
                scopes.add(context.scope());
                return true;
            });

            Map<String, ScopeValue<?>> values = new LinkedHashMap<>();
            for (Scope scope : scopes) {
                if (scope.parent() == null) {
                    continue;
                }
                scope.values().forEach((key, value) -> {
                    if (!value.isPresent()) {
                        return;
                    }
                    switch (value.kind()) {
                        case USER:
                        case EXTERNAL:
                            if (!scopes.contains(value.scope())) {
                                return;
                            }
                            break;
                        case DEFAULT:
                            for (Object qualifier : value.qualifiers()) {
                                if (qualifier == ResolvedKind.AUTO_CREATED) {
                                    return;
                                }
                            }
                            break;
                        default:
                            return;
                    }
                    values.putIfAbsent(key, value);
                });
            }
            return values;
        } catch (InvocationException ex) {
            if (!(ex.getCause() instanceof InvalidInputException)) {
                Log.debug("Execution error: %s, inputs: %s", ex.getCause().getMessage(), variation);
            }
            return Map.of();
        }
    }

    private Map<String, String> orderedValues(Map<String, ScopeValue<?>> effective) {
        Map<String, List<Map.Entry<String, ScopeValue<?>>>> groups = new LinkedHashMap<>();
        Map<String, Integer> encounterOrder = new HashMap<>();
        int next = 0;
        for (Map.Entry<String, ScopeValue<?>> entry : effective.entrySet()) {
            encounterOrder.put(entry.getKey(), next++);
            groups.computeIfAbsent(groupKey(entry.getKey()), ignored -> new ArrayList<>()).add(entry);
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (List<Map.Entry<String, ScopeValue<?>>> group : groups.values()) {
            for (Map.Entry<String, ScopeValue<?>> entry : group) {
                if (entry.getKey().indexOf('.') < 0) {
                    values.put(entry.getKey(), Value.toString(entry.getValue()));
                }
            }
            List<Map.Entry<String, ScopeValue<?>>> toSort = new ArrayList<>();
            for (Map.Entry<String, ScopeValue<?>> entry : group) {
                if (entry.getKey().indexOf('.') >= 0) {
                    toSort.add(entry);
                }
            }
            toSort.sort((left, right) -> {
                int result = Integer.compare(
                        prepared.inputOrder.getOrDefault(left.getKey(), Integer.MAX_VALUE),
                        prepared.inputOrder.getOrDefault(right.getKey(), Integer.MAX_VALUE));
                if (result != 0) {
                    return result;
                }
                return Integer.compare(encounterOrder.get(left.getKey()), encounterOrder.get(right.getKey()));
            });
            for (Map.Entry<String, ScopeValue<?>> entry : toSort) {
                values.put(entry.getKey(), Value.toString(entry.getValue()));
            }
        }
        return values;
    }

    private String groupKey(String key) {
        int index = key.indexOf('.');
        return index < 0 ? key : key.substring(0, index);
    }

    private Guard exactMembershipGuard(Flow.SymbolInfo info, List<String> values) {
        if (info == null) {
            return Guard.FALSE;
        }
        Set<String> selected = new LinkedHashSet<>(values);
        Guard guard = info.definition();
        Guards guards = flow.guards();
        List<String> options = List.of(info.symbol().domain().values());
        for (String option : options) {
            Guard availability = info.availability(option);
            Guard contains = guards.contains(info.symbol().id(), option);
            if (selected.contains(option)) {
                if (availability == null) {
                    return Guard.FALSE;
                }
                guard = guards.and(guard, guards.and(availability, contains));
            } else if (availability != null) {
                guard = guards.and(guard, guards.or(guards.not(availability), guards.not(contains)));
            }
        }
        if (!info.symbol().domain().containsAll(selected)) {
            return Guard.FALSE;
        }
        return guard;
    }

    private abstract class AbstractComputation {
        final Map<String, String> externalValues;
        final Map<String, String> externalDefaults;
        final Map<String, String> resolvedExternalValues;
        final Map<String, String> resolvedExternalDefaults;
        final long maxIntermediateVariations;

        AbstractComputation(Map<String, String> externalValues,
                            Map<String, String> externalDefaults,
                            long maxIntermediateVariations) {
            this.externalValues = Collections.unmodifiableMap(new LinkedHashMap<>(externalValues));
            this.externalDefaults = Collections.unmodifiableMap(new LinkedHashMap<>(externalDefaults));
            this.resolvedExternalValues = resolveExternalValues(this.externalValues, this.externalDefaults);
            this.resolvedExternalDefaults = resolveExternalDefaults(this.externalValues, this.externalDefaults);
            this.maxIntermediateVariations = maxIntermediateVariations;
        }

        Variations normalized(List<CandidateRegion> regions, List<Expression> filters) {
            return normalized(regions, filters, externalValues, externalDefaults, resolvedExternalDefaults);
        }

        Variations normalized(List<CandidateRegion> regions,
                              List<Expression> filters,
                              Map<String, String> externalValues,
                              Map<String, String> externalDefaults,
                              Map<String, String> resolvedExternalDefaults) {
            Collection<Variations.Entry> normalized = regions.stream()
                    .map(region -> materialize(region, filters, externalValues, externalDefaults, resolvedExternalDefaults))
                    .filter(Objects::nonNull)
                    .collect(new NormalizedCollector());
            return Variations.of(normalized);
        }
    }

    private final class PlainComputation extends AbstractComputation {
        private final List<Expression> filters;

        PlainComputation(List<Expression> filters,
                         Map<String, String> externalValues,
                         Map<String, String> externalDefaults,
                         long maxIntermediateVariations) {
            super(externalValues, externalDefaults, maxIntermediateVariations);
            this.filters = filters;
        }

        Variations compute() {
            Guard initialGuard = externalConstraintGuard(resolvedExternalValues);
            Guard excludedGuard = combinedExcludeGuard(filters, resolvedExternalValues);
            List<CandidateRegion> regions = new RegionEnumerator(initialGuard,
                    excludedGuard,
                    filters,
                    resolvedExternalValues,
                    maxIntermediateVariations)
                    .enumerate();
            return normalized(regions, filters);
        }
    }

    private final class PlanComputation extends AbstractComputation {
        private final List<Expression> filters;
        private final List<Plan> plans;

        PlanComputation(Request request) {
            super(request.externalValues(), request.externalDefaults(), request.maxIntermediateVariations());
            this.filters = request.filters();
            this.plans = request.plans();
        }

        PlanAnalysis analysis() {
            List<CompiledPlan> compiled = new ArrayList<>(plans.size());
            for (Plan plan : plans) {
                compiled.add(compiledPlan(plan));
            }
            return new PlanAnalysis(compiled, diagnostics(compiled));
        }

        Variations computeSingle() {
            CompiledPlan compiledPlan = compiledPlan(plans.get(0));
            ensureSatisfiable(compiledPlan);
            return computePlan(compiledPlan);
        }

        Variations compute(PlanAnalysis analysis) {
            if (analysis.diagnostics.hasErrors()) {
                Finding finding = analysis.diagnostics.findings().stream()
                        .filter(it -> it.kind() == Finding.Kind.UNSATISFIABLE_PLAN)
                        .findFirst()
                        .orElseThrow();
                throw new IllegalStateException(String.format(
                        "Variation plan '%s' is unsatisfiable: %s",
                        finding.planId().orElseThrow(),
                        finding.expression().orElseThrow()));
            }
            List<Variations> computed = new ArrayList<>();
            analysis.plans.forEach(compiledPlan -> computed.add(computePlan(compiledPlan)));
            return Variations.union(computed);
        }

        CompiledPlan compiledPlan(Plan plan) {
            Map<String, String> planValues = new LinkedHashMap<>(externalValues);
            planValues.putAll(plan.externalValues());
            Map<String, String> planDefaults = new LinkedHashMap<>(externalDefaults);
            planDefaults.putAll(plan.externalDefaults());
            Map<String, String> resolvedPlanValues = resolveExternalValues(planValues, planDefaults);
            List<Finding> redundantPins = new ArrayList<>();
            Guard pinned = externalConstraintGuard(resolvedPlanValues, plan.id(), redundantPins);
            Guard excluded = combinedExcludeGuard(plan.filters(), resolvedPlanValues);
            return new CompiledPlan(
                    plan,
                    pinned,
                    excluded,
                    Collections.unmodifiableMap(planValues),
                    Collections.unmodifiableMap(planDefaults),
                    redundantPins);
        }

        Variations computePlan(CompiledPlan compiledPlan) {
            Log.info("");
            Log.info("Computing plan %s...", compiledPlan.plan.id());
            Variations computedPlan = new PlainComputation(
                    combineFilters(filters, compiledPlan.plan.filters()),
                    compiledPlan.externalValues,
                    compiledPlan.externalDefaults,
                    maxIntermediateVariations)
                    .compute();
            Log.info("Variations: %d", computedPlan.size());
            return computedPlan;
        }

        void ensureSatisfiable(CompiledPlan compiledPlan) {
            Guard included = flow.minus(compiledPlan.pinnedRegion, compiledPlan.excludedRegion);
            Guard covered = flow.and(reachableGuard(), included);
            if (!flow.isFalse(covered)) {
                return;
            }
            throw new IllegalStateException(String.format(
                    "Variation plan '%s' is unsatisfiable: %s",
                    compiledPlan.plan.id(),
                    render(included)));
        }

        Guard reachableGuard() {
            return flow.minus(
                    externalConstraintGuard(resolvedExternalValues),
                    combinedExcludeGuard(filters, resolvedExternalValues));
        }

        Diagnostics diagnostics(List<CompiledPlan> compiledPlans) {
            Guard reachable = reachableGuard();
            List<Finding> unsatisfiablePlans = new ArrayList<>();
            List<Finding> overlaps = new ArrayList<>();
            List<Finding> redundantPlans = new ArrayList<>();
            List<Finding> redundantPins = new ArrayList<>();
            for (CompiledPlan compiledPlan : compiledPlans) {
                redundantPins.addAll(compiledPlan.findings);
                Guard included = flow.minus(compiledPlan.pinnedRegion, compiledPlan.excludedRegion);
                Guard covered = flow.and(reachable, included);
                if (flow.isFalse(covered)) {
                    unsatisfiablePlans.add(Finding.unsatisfiablePlan(compiledPlan.plan.id(), render(included)));
                }
            }

            for (int i = 0; i < compiledPlans.size(); i++) {
                CompiledPlan left = compiledPlans.get(i);
                Guard leftGuard = flow.and(reachable, flow.minus(left.pinnedRegion, left.excludedRegion));
                for (int j = i + 1; j < compiledPlans.size(); j++) {
                    CompiledPlan right = compiledPlans.get(j);
                    Guard rightGuard = flow.and(reachable, flow.minus(right.pinnedRegion, right.excludedRegion));
                    Guard overlapGuard = flow.and(leftGuard, rightGuard);
                    if (!flow.isFalse(overlapGuard)) {
                        overlaps.add(Finding.planOverlap(left.plan.id(), right.plan.id(), render(overlapGuard)));
                    }
                    if (!flow.isFalse(leftGuard) && subset(leftGuard, rightGuard) && !subset(rightGuard, leftGuard)) {
                        redundantPlans.add(Finding.redundantPlan(left.plan.id(), right.plan.id()));
                    }
                    if (!flow.isFalse(rightGuard) && subset(rightGuard, leftGuard) && !subset(leftGuard, rightGuard)) {
                        redundantPlans.add(Finding.redundantPlan(right.plan.id(), left.plan.id()));
                    }
                }
            }

            Guard covered = Guard.FALSE;
            for (CompiledPlan compiledPlan : compiledPlans) {
                Guard included = flow.and(reachable, flow.minus(compiledPlan.pinnedRegion, compiledPlan.excludedRegion));
                covered = flow.or(covered, included);
            }
            List<Finding> coverageGaps = List.of();
            if (compiledPlans.size() > 1) {
                Guard uncovered = flow.minus(reachable, covered);
                if (!flow.isFalse(uncovered)) {
                    coverageGaps = List.of(Finding.coverageGap(render(uncovered)));
                }
            }

            List<Finding> findings = new ArrayList<>();
            findings.addAll(unsatisfiablePlans);
            findings.addAll(overlaps);
            findings.addAll(redundantPlans);
            findings.addAll(redundantPins);
            findings.addAll(coverageGaps);
            return new Diagnostics(findings);
        }
    }

    private final class RegionEnumerator {
        private final Guard initialGuard;
        private final Guard excludedGuard;
        private final List<Expression> filters;
        private final List<Set<String>> filterVariables;
        private final Map<String, String> fixedValues;
        private final List<InputModel> inputs;
        private final long maxIntermediateVariations;
        private final List<CandidateRegion> regions = new ArrayList<>();

        RegionEnumerator(Guard initialGuard,
                         Guard excludedGuard,
                         List<Expression> filters,
                         Map<String, String> fixedValues,
                         long maxIntermediateVariations) {
            this.initialGuard = initialGuard;
            this.excludedGuard = excludedGuard;
            this.filters = filters;
            this.filterVariables = Lists.map(this.filters, Expression::variables);
            this.fixedValues = fixedValues;
            this.inputs = orderedInputs(this.filters);
            this.maxIntermediateVariations = maxIntermediateVariations;
        }

        List<CandidateRegion> enumerate() {
            if (!flow.isFalse(initialGuard)) {
                enumerate(0, initialGuard, new LinkedHashMap<>(), new LinkedHashSet<>());
            }
            return regions;
        }

        void enumerate(int index, Guard regionGuard, Map<String, String> values, Set<String> activeTextInputs) {
            if (flow.isFalse(regionGuard) || excluded(regionGuard, values)) {
                return;
            }
            if (index == inputs.size()) {
                addRegion(regionGuard, values, activeTextInputs, inputs.isEmpty() ? "<none>" : inputs.get(inputs.size() - 1).key);
                return;
            }

            InputModel input = inputs.get(index);
            Guard active = flow.and(regionGuard, input.definitionGuard);
            Guard inactive = flow.minus(regionGuard, input.definitionGuard);
            if (!flow.isFalse(inactive)) {
                enumerate(index + 1, inactive, values, activeTextInputs);
            }
            if (flow.isFalse(active)) {
                return;
            }

            switch (input.kind) {
                case INPUT_TEXT:
                    Set<String> nextText = new LinkedHashSet<>(activeTextInputs);
                    nextText.add(input.key);
                    enumerate(index + 1, active, values, nextText);
                    break;
                case INPUT_BOOLEAN:
                case INPUT_ENUM:
                    enumerateScalar(input, index, active, values, activeTextInputs);
                    break;
                case INPUT_LIST:
                    enumerateList(input, index, 0, active, values, activeTextInputs, new ArrayList<>());
                    break;
                default:
                    enumerate(index + 1, active, values, activeTextInputs);
            }
        }

        private void enumerateScalar(InputModel input,
                                     int index,
                                     Guard active,
                                     Map<String, String> values,
                                     Set<String> activeTextInputs) {
            Value<?> exact = input.declaredValue(active);
            if (exact.isPresent()) {
                String value = input.canonicalFiniteValue(exact);
                Guard guard = flow.and(active, input.constraintGuard(value));
                if (!flow.isFalse(guard)) {
                    Map<String, String> nextValues = new LinkedHashMap<>(values);
                    nextValues.put(input.key, value);
                    enumerate(index + 1, guard, nextValues, activeTextInputs);
                }
                return;
            }
            for (String option : input.options) {
                Guard guard = flow.and(active, input.constraintGuard(option));
                if (flow.isFalse(guard)) {
                    continue;
                }
                Map<String, String> nextValues = new LinkedHashMap<>(values);
                nextValues.put(input.key, option);
                enumerate(index + 1, guard, nextValues, activeTextInputs);
            }
        }

        private void enumerateList(InputModel input,
                                   int index,
                                   int optionIndex,
                                   Guard current,
                                   Map<String, String> values,
                                   Set<String> activeTextInputs,
                                   List<String> selected) {
            if (flow.isFalse(current)) {
                return;
            }
            if (optionIndex == input.options.size()) {
                Map<String, String> nextValues = new LinkedHashMap<>(values);
                nextValues.put(input.key, input.canonicalList(selected));
                enumerate(index + 1, current, nextValues, activeTextInputs);
                return;
            }

            String option = input.options.get(optionIndex);
            Guard availability = input.availability(option);
            Guard contains = flow.guards().contains(input.symbolInfo.symbol().id(), option);
            if (!flow.isFalse(availability)) {
                Guard included = flow.and(current, flow.guards().and(availability, contains));
                if (!flow.isFalse(included)) {
                    List<String> nextSelected = new ArrayList<>(selected);
                    nextSelected.add(option);
                    enumerateList(input, index, optionIndex + 1, included, values, activeTextInputs, nextSelected);
                }
            }

            Guard excluded = flow.isFalse(availability)
                    ? current
                    : flow.and(current, flow.guards().or(flow.guards().not(availability), flow.guards().not(contains)));
            if (!flow.isFalse(excluded)) {
                enumerateList(input, index, optionIndex + 1, excluded, values, activeTextInputs, selected);
            }
        }

        private List<InputModel> orderedInputs(List<Expression> filters) {
            if (filters.isEmpty()) {
                return prepared.inputs;
            }
            Map<String, Integer> references = new HashMap<>();
            for (Expression filter : filters) {
                for (String variable : filter.variables()) {
                    references.compute(variable, (key, value) -> value == null ? 1 : value + 1);
                }
            }
            List<InputModel> ordered = new ArrayList<>(prepared.inputs);
            ordered.sort((left, right) -> {
                int leftRefs = references.getOrDefault(left.key, 0);
                int rightRefs = references.getOrDefault(right.key, 0);
                if ((leftRefs > 0) != (rightRefs > 0)) {
                    return leftRefs > 0 ? -1 : 1;
                }
                int result = Long.compare(left.branchCount(), right.branchCount());
                if (result != 0) {
                    return result;
                }
                result = Integer.compare(rightRefs, leftRefs);
                if (result != 0) {
                    return result;
                }
                return Integer.compare(
                        prepared.inputOrder.getOrDefault(left.key, Integer.MAX_VALUE),
                        prepared.inputOrder.getOrDefault(right.key, Integer.MAX_VALUE));
            });
            return ordered;
        }

        private boolean excluded(Guard regionGuard, Map<String, String> values) {
            if (!flow.isFalse(excludedGuard) && subset(regionGuard, excludedGuard)) {
                return true;
            }
            if (filters.isEmpty()) {
                return false;
            }
            Map<String, String> current = null;
            for (int i = 0; i < filters.size(); i++) {
                if (!resolvableFilter(i, values)) {
                    continue;
                }
                if (current == null) {
                    current = currentValues(values);
                }
                if (eval(filters.get(i), current)) {
                    return true;
                }
            }
            return false;
        }

        Map<String, String> currentValues(Map<String, String> values) {
            if (fixedValues.isEmpty()) {
                return values;
            }
            Map<String, String> current = new LinkedHashMap<>(fixedValues);
            current.putAll(values);
            return current;
        }

        boolean resolvableFilter(int index, Map<String, String> values) {
            for (String variable : filterVariables.get(index)) {
                if (!fixedValues.containsKey(variable) && !values.containsKey(variable)) {
                    return false;
                }
            }
            return true;
        }

        void addRegion(Guard guard, Map<String, String> values, Set<String> activeTextInputs, String inputKey) {
            regions.add(new CandidateRegion(guard, values, activeTextInputs));
            if (regions.size() > maxIntermediateVariations) {
                throw new IllegalStateException(String.format(
                        "Intermediate variation row count %d exceeds the configured limit of %d while joining input '%s'",
                        regions.size(),
                        maxIntermediateVariations,
                        inputKey));
            }
        }
    }

    private final class Prepared {
        private final Node node;
        private final List<InputModel> inputs;
        private final Map<String, InputModel> inputsByKey;
        private final Set<String> textInputs;
        private final Map<String, Integer> inputOrder;

        Prepared(Node node, ScriptIndexer indexer) {
            this.node = node;
            List<InputModel> inputs = new ArrayList<>();
            Map<String, InputModel> inputsByKey = new LinkedHashMap<>();
            Set<String> textInputs = new LinkedHashSet<>();
            Map<String, Integer> inputOrder = new LinkedHashMap<>();
            int next = 0;
            for (Node input : node.traverse(Kind::isInput)) {
                String key = indexer.key(input);
                Flow.SymbolInfo symbol = symbolInfo(key);
                if (symbol == null) {
                    throw new IllegalStateException("Input is not part of the flow symbol table: " + key);
                }
                InputModel model = inputsByKey.get(key);
                if (model == null) {
                    model = new InputModel(key, input.kind(), symbol);
                    inputs.add(model);
                    inputsByKey.put(key, model);
                    inputOrder.put(key, next++);
                    if (input.kind() == Kind.INPUT_TEXT) {
                        textInputs.add(key);
                    }
                }
                model.addOccurrence(input);
            }
            this.inputs = inputs;
            this.inputsByKey = inputsByKey;
            this.textInputs = textInputs;
            this.inputOrder = inputOrder;
        }
    }

    private final class InputModel {
        private final String key;
        private final Kind kind;
        private final Flow.SymbolInfo symbolInfo;
        private final List<InputOccurrence> occurrences = new ArrayList<>();
        private final List<String> options = new ArrayList<>();
        private final Map<String, Guard> availabilityGuards = new HashMap<>();
        private final Map<String, Guard> constraintGuards = new HashMap<>();
        private Guard definitionGuard = Guard.FALSE;

        InputModel(String key, Kind kind, Flow.SymbolInfo symbolInfo) {
            this.key = key;
            this.kind = kind;
            this.symbolInfo = symbolInfo;
            if (kind == Kind.INPUT_BOOLEAN) {
                options.add("true");
                options.add("false");
            }
        }

        Guard availability(String value) {
            if (!supportsOption(value)) {
                return Guard.FALSE;
            }
            if (availabilityGuards.containsKey(value)) {
                return availabilityGuards.get(value);
            }
            Guard available = Guard.FALSE;
            for (InputOccurrence occurrence : occurrences) {
                available = flow.or(available, occurrence.availability(value));
            }
            availabilityGuards.put(value, available);
            return available;
        }

        Guard constraintGuard(String value) {
            switch (kind) {
                case INPUT_BOOLEAN:
                case INPUT_ENUM:
                    validateOption(value);
                    if (constraintGuards.containsKey(value)) {
                        return constraintGuards.get(value);
                    }
                    Guard allowed = Guard.FALSE;
                    for (InputOccurrence occurrence : occurrences) {
                        Guard next = occurrence.constraintGuard(value);
                        if (!flow.isFalse(next)) {
                            allowed = flow.or(allowed, next);
                        }
                    }
                    constraintGuards.put(value, allowed);
                    return allowed;
                case INPUT_LIST:
                    return membershipGuard(value);
                default:
                    return null;
            }
        }

        void addOccurrence(Node node) {
            if (node.kind() != kind) {
                throw new IllegalStateException(String.format(
                        "Input '%s' is declared with incompatible kinds: %s and %s",
                        key,
                        kind,
                        node.kind()));
            }
            InputOccurrence occurrence = new InputOccurrence(node);
            occurrences.add(occurrence);
            availabilityGuards.clear();
            constraintGuards.clear();
            definitionGuard = flow.or(definitionGuard, occurrence.definitionGuard);
            if (kind == Kind.INPUT_ENUM || kind == Kind.INPUT_LIST) {
                for (String option : occurrence.options) {
                    if (!options.contains(option)) {
                        options.add(option);
                    }
                }
            }
        }

        String canonicalFiniteValue(Value<?> value) {
            switch (kind) {
                case INPUT_BOOLEAN:
                    return String.valueOf(value.asBoolean().orElse(false));
                case INPUT_ENUM:
                    String scalar = value.asString().orElseThrow(() ->
                            new IllegalStateException("Expected scalar value for input: " + key));
                    validateOption(scalar);
                    return scalar;
                case INPUT_LIST:
                    return canonicalList(value.asList().orElse(List.of()));
                default:
                    throw new IllegalStateException("Unsupported finite input kind: " + kind);
            }
        }

        long branchCount() {
            switch (kind) {
                case INPUT_TEXT:
                    return 1;
                case INPUT_LIST:
                    long count = 1;
                    for (int i = 0; i < options.size(); i++) {
                        if (count > Long.MAX_VALUE / 2) {
                            return Long.MAX_VALUE;
                        }
                        count <<= 1;
                    }
                    return count;
                default:
                    return Math.max(options.size(), 1);
            }
        }

        String canonicalList(List<String> values) {
            if (values.isEmpty()) {
                return "none";
            }
            List<String> ordered = new ArrayList<>();
            Set<String> remaining = new LinkedHashSet<>(values);
            for (String option : options) {
                if (remaining.remove(option)) {
                    ordered.add(option);
                }
            }
            if (!remaining.isEmpty()) {
                validateOption(remaining.iterator().next());
            }
            return ordered.isEmpty() ? "none" : String.join(",", ordered);
        }

        Value<?> declaredValue(Guard regionGuard) {
            Value<?> exact = Value.empty();
            for (InputOccurrence occurrence : occurrences) {
                Guard active = flow.and(regionGuard, occurrence.definitionGuard);
                if (flow.isFalse(active)) {
                    continue;
                }
                Value<?> value = flow.declaredValue(occurrence.node, key, active);
                if (!value.isPresent()) {
                    continue;
                }
                if (!exact.isPresent()) {
                    exact = value;
                } else if (!Value.isEqual(exact, value)) {
                    return Value.empty();
                }
            }
            return exact;
        }

        String defaultValue(Guard regionGuard) {
            for (InputOccurrence occurrence : occurrences) {
                Guard active = flow.and(regionGuard, occurrence.definitionGuard);
                if (!flow.isFalse(active) && occurrence.defaultValue != null) {
                    return occurrence.defaultValue;
                }
            }
            return null;
        }

        void validateOption(String value) {
            if (kind != Kind.INPUT_LIST || !"none".equals(value)) {
                for (String option : options) {
                    if (option.equals(value)) {
                        return;
                    }
                }
                throw invalidValue(value);
            }
        }

        boolean supportsOption(String value) {
            for (String option : options) {
                if (option.equals(value)) {
                    return true;
                }
            }
            return false;
        }

        Guard membershipGuard(String value) {
            List<String> selected = Value.parseList(value).orElse(List.of());
            for (String value0 : selected) {
                validateOption(value0);
            }
            Guard allowed = Guard.FALSE;
            for (InputOccurrence occurrence : occurrences) {
                Guard next = occurrence.membershipGuard(selected);
                if (!flow.isFalse(next)) {
                    allowed = flow.or(allowed, next);
                }
            }
            return allowed;
        }

        IllegalStateException invalidValue(String value) {
            return new IllegalStateException(String.format(
                    "Invalid value '%s' for input '%s', available options: %s",
                    value, key, String.join(", ", options)));
        }

        String[] optionValues(Node node) {
            List<String> values = new ArrayList<>();
            for (Node option : Nodes.options(node)) {
                values.add(option.value().getString());
            }
            return values.toArray(String[]::new);
        }

        private final class InputOccurrence {
            private final Node node;
            private final Guard definitionGuard;
            private final String[] options;
            private final String defaultValue;

            InputOccurrence(Node node) {
                this.node = node;
                this.definitionGuard = flow.activeGuard(node.parent());
                switch (node.kind()) {
                    case INPUT_BOOLEAN:
                        this.options = new String[] {"true", "false"};
                        break;
                    case INPUT_ENUM:
                    case INPUT_LIST:
                        this.options = optionValues(node);
                        break;
                    default:
                        this.options = new String[0];
                        break;
                }
                this.defaultValue = node.attribute("default").asString().orElse(null);
            }

            Guard availability(String value) {
                if (!supportsOption(value)) {
                    return Guard.FALSE;
                }
                Guards guards = flow.guards();
                Guard availability = symbolInfo.availability(value);
                Guard active = availability == null ? definitionGuard : guards.and(definitionGuard, availability);
                return flow.isFalse(active) ? Guard.FALSE : active;
            }

            Guard constraintGuard(String value) {
                Guards guards = flow.guards();
                Symbol sym = symbolInfo.symbol();
                if (!sym.domain().scalar() || !sym.domain().contains(value) || !sym.guardable()) {
                    return Guard.FALSE;
                }
                return guards.and(availability(value), guards.eq(sym.id(), value));
            }

            Guard membershipGuard(List<String> selected) {
                Guards guards = flow.guards();
                Guard guard = definitionGuard;
                Symbol sym = symbolInfo.symbol();
                Spec domain = sym.domain();
                for (String option : options) {
                    Guard availability = availability(option);
                    Guard directContains = domain.contains(option)
                            ? guards.contains(sym.id(), option)
                            : Guard.FALSE;
                    Guard contains = domain.kind() == Spec.Kind.MEMBERSHIP && directContains != Guard.FALSE
                            ? guards.and(availability, directContains)
                            : Guard.FALSE;
                    if (selected.contains(option)) {
                        if (contains == Guard.FALSE) {
                            return Guard.FALSE;
                        }
                        guard = guards.and(guard, contains);
                    } else if (!flow.isFalse(availability)) {
                        guard = guards.and(guard, guards.or(guards.not(availability), guards.not(directContains)));
                    }
                }
                return guard;
            }

            boolean supportsOption(String value) {
                for (String option : options) {
                    if (option.equals(value)) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    private static final class CandidateRegion {
        private final Guard guard;
        private final Map<String, String> values;
        private final Set<String> textInputs;

        CandidateRegion(Guard guard, Map<String, String> values, Set<String> textInputs) {
            this.guard = guard;
            this.values = values;
            this.textInputs = textInputs;
        }
    }

    private static final class PlanAnalysis {
        private final List<CompiledPlan> plans;
        private final Diagnostics diagnostics;

        PlanAnalysis(List<CompiledPlan> plans, Diagnostics diagnostics) {
            this.plans = plans;
            this.diagnostics = diagnostics;
        }
    }

    private static final class CompiledPlan {
        private final Plan plan;
        private final Guard pinnedRegion;
        private final Guard excludedRegion;
        private final Map<String, String> externalValues;
        private final Map<String, String> externalDefaults;
        private final List<Finding> findings;

        CompiledPlan(Plan plan,
                     Guard pinnedRegion,
                     Guard excludedRegion,
                     Map<String, String> externalValues,
                     Map<String, String> externalDefaults,
                     List<Finding> findings) {
            this.plan = plan;
            this.pinnedRegion = pinnedRegion;
            this.excludedRegion = excludedRegion;
            this.externalValues = externalValues;
            this.externalDefaults = externalDefaults;
            this.findings = findings;
        }
    }

    private static final class NormalizedCollector
            implements Collector<Variations.Entry, Map<String, Variations.Entry>, Collection<Variations.Entry>> {

        @Override
        public Supplier<Map<String, Variations.Entry>> supplier() {
            return HashMap::new;
        }

        @Override
        public BiConsumer<Map<String, Variations.Entry>, Variations.Entry> accumulator() {
            return (map, entry) -> map.merge(entry.signature(), entry, Variations.Entry::merge);
        }

        @Override
        public BinaryOperator<Map<String, Variations.Entry>> combiner() {
            return (left, right) -> {
                right.forEach((key, entry) -> left.merge(key, entry, Variations.Entry::merge));
                return left;
            };
        }

        @Override
        public Function<Map<String, Variations.Entry>, Collection<Variations.Entry>> finisher() {
            return Map::values;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Set.of();
        }
    }
}
