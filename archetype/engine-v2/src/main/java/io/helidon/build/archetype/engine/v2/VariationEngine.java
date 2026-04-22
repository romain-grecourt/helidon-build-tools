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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import io.helidon.build.archetype.engine.v2.Context.Scope;
import io.helidon.build.archetype.engine.v2.Context.ScopeValue;
import io.helidon.build.archetype.engine.v2.Context.ValueKind;
import io.helidon.build.archetype.engine.v2.Domain.Guard;
import io.helidon.build.archetype.engine.v2.Domain.Guards;
import io.helidon.build.archetype.engine.v2.Domain.Spec;
import io.helidon.build.archetype.engine.v2.Domain.Symbol;
import io.helidon.build.archetype.engine.v2.Flow.SymbolInfo;
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

import static io.helidon.build.archetype.engine.v2.Variations.Finding.Kind.COVERAGE_GAP;
import static io.helidon.build.archetype.engine.v2.Variations.Finding.Kind.PLAN_OVERLAP;
import static io.helidon.build.archetype.engine.v2.Variations.Finding.Kind.REDUNDANT_PIN;
import static io.helidon.build.archetype.engine.v2.Variations.Finding.Kind.REDUNDANT_PLAN;
import static io.helidon.build.archetype.engine.v2.Variations.Finding.Kind.UNSATISFIABLE_PLAN;
import static io.helidon.build.archetype.engine.v2.Variations.Finding.Severity.ERROR;
import static io.helidon.build.archetype.engine.v2.Variations.Finding.Severity.WARNING;
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
    private InputIndex index;
    private Request request;
    private PlanAnalysis analysis;

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
            this.request = null;
            this.analysis = null;
            return new PlainComputation(
                    request.filters(),
                    request.externalValues(),
                    request.externalDefaults(),
                    request.maxIntermediate()).compute();
        }
        if (request.plans().size() == 1) {
            this.request = null;
            this.analysis = null;
            return new PlanComputation(request).compute();
        }
        PlanComputation computation = new PlanComputation(request);
        if (this.request != request || this.analysis == null) {
            this.analysis = computation.analysis();
        }
        this.request = request;
        return computation.compute(this.analysis);
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
            this.request = null;
            this.analysis = null;
            return Diagnostics.EMPTY;
        }
        this.request = request;
        this.analysis = new PlanComputation(request).analysis();
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
        index = new InputIndex(sourceNode, indexer);
    }

    private Map<String, String> resolveExternalValues(Map<String, String> externalValues, Map<String, String> externalDefaults) {
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
        return values;
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
        return values;
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
        InputState input = index.inputs.get(key);
        if (input != null) {
            return input.node.kind().valueType();
        }
        SymbolInfo info = symbolInfo(key);
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

    private SymbolInfo symbolInfo(String key) {
        SymbolInfo info = flow.symbol(key);
        if (info == null && !key.startsWith("~")) {
            info = flow.symbol("~" + key);
        }
        return info;
    }

    private Guard externalConstraint(Map<String, String> externalValues) {
        return externalConstraint(externalValues, null, null);
    }

    private Guard externalConstraint(Map<String, String> externalValues, String planId, List<Finding> findings) {
        Guard guard = Guard.TRUE;
        for (Map.Entry<String, String> entry : externalValues.entrySet()) {
            Guard next = constraint(entry.getKey(), entry.getValue(), planId, findings, guard);
            if (next != null) {
                guard = flow.and(guard, next);
            }
        }
        return guard;
    }

    private Guard constraint(String key, String value, String planId, List<Finding> findings, Guard current) {
        InputState input = index.inputs.get(key);
        Guard next = input != null ? input.constraint(value) : constraint(key, value, symbolInfo(key));
        if (next != null && findings != null && planId != null && subset(current, next)) {
            findings.add(new Finding(REDUNDANT_PIN, WARNING, planId, null, key, value, null));
        }
        return next;
    }

    private Guard constraint(String key, String value, SymbolInfo info) {
        if (info == null) {
            return null;
        }
        Symbol sym = info.symbol();
        Spec domain = sym.domain();
        if (!sym.guardable() || sym.tainted() || domain.kind() == Spec.Kind.OPEN_TEXT) {
            return null;
        }
        switch (domain.kind()) {
            case BOOLEAN:
            case CHOICE:
            case FINITE_TEXT:
                if (!domain.contains(value)) {
                    throw new IllegalStateException(String.format("Invalid value '%s' for key '%s'", value, key));
                }
                Guard direct = flow.guards().eq(sym.id(), value);
                Guard availability = info.availability(value);
                return availability == null ? direct : flow.guards().and(availability, direct);
            case MEMBERSHIP:
                return exactMembership(info, Value.parseList(value).orElse(List.of()));
            default:
                return null;
        }
    }

    private Guard combinedExclude(List<Expression> filters, Map<String, String> externalValues) {
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

    private Expression prune(Expression expr, Map<String, String> externalValues) {
        if (expr == Expression.TRUE || expr == Expression.FALSE || externalValues.isEmpty()) {
            return expr;
        }
        return expr.inline(variable -> {
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
            if (entry.getValue().kind() != ValueKind.EXTERNAL && index.input(entry.getKey()).node.kind() == Kind.INPUT_TEXT) {
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
        InputState input = index.inputs.get(key);
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

    private boolean eval(Expression expr, Map<String, String> values) {
        try {
            return expr.eval(variable -> {
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
                if (!externalValues.containsKey(key)) {
                    Scope scope = context.scope().getOrCreate(key);
                    scope.value(Value.dynamic(value), ValueKind.USER);
                }
            });

            Set<Scope> scopes = new LinkedHashSet<>();
            ScriptInvoker.invoke(index.node, context, new InputResolver.BatchResolver(context), node -> {
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
        Map<String, String> values = new TreeMap<>(Comparator.comparingInt(index::inputId));
        for (Map.Entry<String, ScopeValue<?>> entry : effective.entrySet()) {
            values.put(entry.getKey(), Value.toString(entry.getValue()));
        }
        return values;
    }

    private Guard exactMembership(SymbolInfo info, List<String> values) {
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

    private abstract class Computation {
        final Map<String, String> externalValues;
        final Map<String, String> externalDefaults;
        final Map<String, String> resolvedExternalValues;
        final Map<String, String> resolvedExternalDefaults;
        final long maxIntermediate;

        Computation(Map<String, String> externalValues, Map<String, String> externalDefaults, long maxIntermediate) {
            this.externalValues = new LinkedHashMap<>(externalValues);
            this.externalDefaults = new LinkedHashMap<>(externalDefaults);
            this.resolvedExternalValues = resolveExternalValues(this.externalValues, this.externalDefaults);
            this.resolvedExternalDefaults = resolveExternalDefaults(this.externalValues, this.externalDefaults);
            this.maxIntermediate = maxIntermediate;
        }

        Variations normalized(List<CandidateRegion> regions, List<Expression> filters) {
            Map<String, Variations.Entry> normalized = new HashMap<>();
            for (CandidateRegion region : regions) {
                Variations.Entry entry = materialize(region, filters, externalValues, externalDefaults, resolvedExternalDefaults);
                if (entry != null) {
                    normalized.merge(entry.signature(), entry, Variations.Entry::merge);
                }
            }
            return Variations.of(normalized.values());
        }
    }

    private final class PlainComputation extends Computation {
        private final List<Expression> filters;

        PlainComputation(List<Expression> filters,
                         Map<String, String> externalValues,
                         Map<String, String> externalDefaults,
                         long maxIntermediate) {
            super(externalValues, externalDefaults, maxIntermediate);
            this.filters = filters;
        }

        Variations compute() {
            Guard initial = externalConstraint(resolvedExternalValues);
            Guard excluded = combinedExclude(filters, resolvedExternalValues);
            RegionEnumerator enumerator = new RegionEnumerator(initial, excluded,
                    filters,
                    resolvedExternalValues,
                    maxIntermediate);
            return normalized(enumerator.enumerate(), filters);
        }
    }

    private final class PlanComputation extends Computation {
        private final List<Expression> filters;
        private final List<Plan> plans;

        PlanComputation(Request request) {
            super(request.externalValues(), request.externalDefaults(), request.maxIntermediate());
            this.filters = request.filters();
            this.plans = request.plans();
        }

        PlanAnalysis analysis() {
            List<CompiledPlan> compiled = new ArrayList<>(plans.size());
            for (Plan plan : plans) {
                compiled.add(plan(plan));
            }
            return new PlanAnalysis(compiled, diagnostics(compiled));
        }

        Variations compute() {
            CompiledPlan plan = plan(plans.get(0));
            Guard included = flow.minus(plan.pinned, plan.excluded);
            Guard covered = flow.and(reachable(), included);
            if (flow.isFalse(covered)) {
                throw new IllegalStateException(String.format(
                        "Variation plan '%s' is unsatisfiable: %s",
                        plan.plan.id(),
                        render(included)));
            }
            return compute(plan);
        }

        Variations compute(PlanAnalysis analysis) {
            if (analysis.diagnostics.hasErrors()) {
                Finding finding = analysis.diagnostics.findings().stream()
                        .filter(it -> it.kind() == UNSATISFIABLE_PLAN)
                        .findFirst()
                        .orElseThrow();
                throw new IllegalStateException(String.format(
                        "Variation plan '%s' is unsatisfiable: %s",
                        finding.planId().orElseThrow(),
                        finding.expression().orElseThrow()));
            }
            List<Variations> computed = new ArrayList<>();
            for (CompiledPlan plan : analysis.plans) {
                computed.add(compute(plan));
            }
            return Variations.union(computed);
        }

        Variations compute(CompiledPlan plan) {
            Log.info("");
            Log.info("Computing plan %s...", plan.plan.id());
            Variations computed = new PlainComputation(
                    combineFilters(filters, plan.plan.filters()),
                    plan.externalValues,
                    plan.externalDefaults,
                    maxIntermediate)
                    .compute();
            Log.info("Variations: %d", computed.size());
            return computed;
        }

        CompiledPlan plan(Plan plan) {
            Map<String, String> values = new LinkedHashMap<>(externalValues);
            values.putAll(plan.externalValues());
            Map<String, String> defaults = new LinkedHashMap<>(externalDefaults);
            defaults.putAll(plan.externalDefaults());
            Map<String, String> resolvedValues = resolveExternalValues(values, defaults);
            List<Finding> redundantPins = new ArrayList<>();
            Guard pinned = externalConstraint(resolvedValues, plan.id(), redundantPins);
            Guard excluded = combinedExclude(plan.filters(), resolvedValues);
            return new CompiledPlan(plan, pinned, excluded, values, defaults, redundantPins);
        }

        Guard reachable() {
            return flow.minus(
                    externalConstraint(resolvedExternalValues),
                    combinedExclude(filters, resolvedExternalValues));
        }

        Diagnostics diagnostics(List<CompiledPlan> plans) {
            Guard reachable = reachable();
            List<Finding> findings = new ArrayList<>();
            for (CompiledPlan plan : plans) {
                findings.addAll(plan.findings);
                Guard included = flow.minus(plan.pinned, plan.excluded);
                Guard covered = flow.and(reachable, included);
                if (flow.isFalse(covered)) {
                    String expr = render(included);
                    findings.add(new Finding(UNSATISFIABLE_PLAN, ERROR, plan.plan.id(), null, null, null, expr));
                }
            }

            for (int i = 0; i < plans.size(); i++) {
                CompiledPlan left = plans.get(i);
                Guard leftGuard = flow.and(reachable, flow.minus(left.pinned, left.excluded));
                for (int j = i + 1; j < plans.size(); j++) {
                    CompiledPlan right = plans.get(j);
                    Guard rightGuard = flow.and(reachable, flow.minus(right.pinned, right.excluded));
                    Guard overlapGuard = flow.and(leftGuard, rightGuard);
                    if (!flow.isFalse(overlapGuard)) {
                        String expr = render(overlapGuard);
                        findings.add(new Finding(PLAN_OVERLAP, WARNING, left.plan.id(), right.plan.id(), null, null, expr));
                    }
                    if (!flow.isFalse(leftGuard) && subset(leftGuard, rightGuard) && !subset(rightGuard, leftGuard)) {
                        findings.add(new Finding(REDUNDANT_PLAN, WARNING, left.plan.id(), right.plan.id(), null, null, null));
                    }
                    if (!flow.isFalse(rightGuard) && subset(rightGuard, leftGuard) && !subset(leftGuard, rightGuard)) {
                        findings.add(new Finding(REDUNDANT_PLAN, WARNING, right.plan.id(), left.plan.id(), null, null, null));
                    }
                }
            }

            Guard covered = Guard.FALSE;
            for (CompiledPlan plan : plans) {
                Guard included = flow.and(reachable, flow.minus(plan.pinned, plan.excluded));
                covered = flow.or(covered, included);
            }
            if (plans.size() > 1) {
                Guard uncovered = flow.minus(reachable, covered);
                if (!flow.isFalse(uncovered)) {
                    String expr = render(uncovered);
                    findings.add(new Finding(COVERAGE_GAP, WARNING, null, null, null, null, expr));
                }
            }
            return new Diagnostics(findings);
        }
    }

    private final class RegionEnumerator {
        private final Guard initial;
        private final Guard excluded;
        private final List<Expression> filters;
        private final List<Set<String>> filterVariables;
        private final Map<String, String> fixedValues;
        private final List<InputState> inputs;
        private final long maxIntermediate;
        private final List<CandidateRegion> regions = new ArrayList<>();

        RegionEnumerator(Guard initial,
                         Guard excluded,
                         List<Expression> filters,
                         Map<String, String> fixedValues,
                         long maxIntermediate) {
            this.initial = initial;
            this.excluded = excluded;
            this.filters = filters;
            this.filterVariables = Lists.map(this.filters, Expression::variables);
            this.fixedValues = fixedValues;
            this.inputs = orderedInputs(this.filters);
            this.maxIntermediate = maxIntermediate;
        }

        List<CandidateRegion> enumerate() {
            if (!flow.isFalse(initial)) {
                enumerate(0, initial, new LinkedHashMap<>(), new LinkedHashSet<>());
            }
            return regions;
        }

        void enumerate(int index, Guard region, Map<String, String> values, Set<String> textInputs) {
            if (flow.isFalse(region) || excluded(region, values)) {
                return;
            }
            if (index == inputs.size()) {
                if (inputs.isEmpty()) {
                    addRegion(region, values, textInputs, "<none>");
                } else {
                    addRegion(region, values, textInputs, inputs.get(inputs.size() - 1).key);
                }
                return;
            }

            InputState input = inputs.get(index);
            Guard active = flow.and(region, input.definition);
            Guard inactive = flow.minus(region, input.definition);
            if (!flow.isFalse(inactive)) {
                enumerate(index + 1, inactive, values, textInputs);
            }
            if (flow.isFalse(active)) {
                return;
            }

            switch (input.node.kind()) {
                case INPUT_TEXT:
                    Set<String> nextText = new LinkedHashSet<>(textInputs);
                    nextText.add(input.key);
                    enumerate(index + 1, active, values, nextText);
                    break;
                case INPUT_BOOLEAN:
                case INPUT_ENUM:
                    enumerateScalar(input, index, active, values, textInputs);
                    break;
                case INPUT_LIST:
                    enumerateList(input, index, 0, active, values, textInputs, new ArrayList<>());
                    break;
                default:
                    enumerate(index + 1, active, values, textInputs);
            }
        }

        void enumerateScalar(InputState input,
                             int index,
                             Guard active,
                             Map<String, String> values,
                             Set<String> textInputs) {
            Value<?> exact = input.declaredValue(active);
            if (exact.isPresent()) {
                String value = input.canonicalFiniteValue(exact);
                Guard guard = flow.and(active, input.constraint(value));
                if (!flow.isFalse(guard)) {
                    Map<String, String> nextValues = new LinkedHashMap<>(values);
                    nextValues.put(input.key, value);
                    enumerate(index + 1, guard, nextValues, textInputs);
                }
                return;
            }
            for (String option : input.options) {
                Guard guard = flow.and(active, input.constraint(option));
                if (flow.isFalse(guard)) {
                    continue;
                }
                Map<String, String> nextValues = new LinkedHashMap<>(values);
                nextValues.put(input.key, option);
                enumerate(index + 1, guard, nextValues, textInputs);
            }
        }

        void enumerateList(InputState input,
                           int index,
                           int optionIndex,
                           Guard current,
                           Map<String, String> values,
                           Set<String> textInputs,
                           List<String> selected) {
            if (flow.isFalse(current)) {
                return;
            }
            if (optionIndex == input.options.size()) {
                Map<String, String> nextValues = new LinkedHashMap<>(values);
                nextValues.put(input.key, input.canonicalList(selected));
                enumerate(index + 1, current, nextValues, textInputs);
                return;
            }

            String option = input.options.get(optionIndex);
            Guard availability = input.availability(option);
            Guard contains = flow.guards().contains(input.symInfo.symbol().id(), option);
            if (!flow.isFalse(availability)) {
                Guard included = flow.and(current, flow.guards().and(availability, contains));
                if (!flow.isFalse(included)) {
                    List<String> nextSelected = new ArrayList<>(selected);
                    nextSelected.add(option);
                    enumerateList(input, index, optionIndex + 1, included, values, textInputs, nextSelected);
                }
            }

            Guard excluded = flow.isFalse(availability)
                    ? current
                    : flow.and(current, flow.guards().or(flow.guards().not(availability), flow.guards().not(contains)));
            if (!flow.isFalse(excluded)) {
                enumerateList(input, index, optionIndex + 1, excluded, values, textInputs, selected);
            }
        }

        List<InputState> orderedInputs(List<Expression> filters) {
            if (filters.isEmpty()) {
                return new ArrayList<>(index.inputs.values());
            }
            Map<String, Integer> refs = new HashMap<>();
            for (Expression filter : filters) {
                for (String variable : filter.variables()) {
                    refs.compute(variable, (key, value) -> value == null ? 1 : value + 1);
                }
            }
            List<InputState> ordered = new ArrayList<>(index.inputs.values());
            ordered.sort((left, right) -> {
                int leftRefs = refs.getOrDefault(left.key, 0);
                int rightRefs = refs.getOrDefault(right.key, 0);
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
                return Integer.compare(index.inputId(left.key), index.inputId(right.key));
            });
            return ordered;
        }

        boolean excluded(Guard regionGuard, Map<String, String> values) {
            if (!flow.isFalse(excluded) && subset(regionGuard, excluded)) {
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

        void addRegion(Guard guard, Map<String, String> values, Set<String> textInputs, String inputKey) {
            regions.add(new CandidateRegion(guard, values, textInputs));
            if (regions.size() > maxIntermediate) {
                throw new IllegalStateException(String.format(
                        "Intermediate variation row count %d exceeds the configured limit of %d while joining input '%s'",
                        regions.size(),
                        maxIntermediate,
                        inputKey));
            }
        }
    }

    private final class InputIndex {
        private final Node node;
        private final Map<String, InputState> inputs = new LinkedHashMap<>();

        InputIndex(Node node, ScriptIndexer indexer) {
            this.node = node;
            for (Node input : node.traverse(Kind::isInput)) {
                String key = indexer.key(input);
                SymbolInfo symInfo = symbolInfo(key);
                if (symInfo == null) {
                    throw new IllegalStateException("Input is not part of the flow symbol table: " + key);
                }
                InputState state = inputs.get(key);
                if (state == null) {
                    state = new InputState(input, key, symInfo);
                    inputs.put(key, state);
                }
                state.addSite(input);
            }
        }

        InputState input(String key) {
            InputState state = inputs.get(key);
            if (state == null) {
                throw new IllegalStateException("Missing input for key: " + key);
            }
            return state;
        }

        int inputId(String key) {
            return input(key).node.id();
        }
    }

    private final class InputState {
        private final Node node;
        private final String key;
        private final SymbolInfo symInfo;
        private final List<InputSite> sites = new ArrayList<>();
        private final List<String> options = new ArrayList<>();
        private final Map<String, Guard> availability = new HashMap<>();
        private final Map<String, Guard> constraints = new HashMap<>();
        private Guard definition = Guard.FALSE;

        InputState(Node node, String key, SymbolInfo symInfo) {
            this.node = node;
            this.key = key;
            this.symInfo = symInfo;
            if (node.kind() == Kind.INPUT_BOOLEAN) {
                options.add("true");
                options.add("false");
            }
        }

        Guard availability(String value) {
            if (!supportsOption(value)) {
                return Guard.FALSE;
            }
            if (availability.containsKey(value)) {
                return availability.get(value);
            }
            Guard available = Guard.FALSE;
            for (InputSite site : sites) {
                available = flow.or(available, site.availability(value));
            }
            availability.put(value, available);
            return available;
        }

        Guard constraint(String value) {
            switch (node.kind()) {
                case INPUT_BOOLEAN:
                case INPUT_ENUM:
                    validateOption(value);
                    if (constraints.containsKey(value)) {
                        return constraints.get(value);
                    }
                    Guard allowed = Guard.FALSE;
                    for (InputSite site : sites) {
                        Guard next = site.constraint(value);
                        if (!flow.isFalse(next)) {
                            allowed = flow.or(allowed, next);
                        }
                    }
                    constraints.put(value, allowed);
                    return allowed;
                case INPUT_LIST:
                    return membershipGuard(value);
                default:
                    return null;
            }
        }

        void addSite(Node node) {
            if (node.kind() != this.node.kind()) {
                throw new IllegalStateException(String.format(
                        "Input '%s' is declared with incompatible kinds: %s and %s",
                        key, this.node.kind(), node.kind()));
            }
            InputSite site = new InputSite(node);
            sites.add(site);
            availability.clear();
            constraints.clear();
            definition = flow.or(definition, site.definition);
            if (this.node.kind() == Kind.INPUT_ENUM || this.node.kind() == Kind.INPUT_LIST) {
                for (String option : site.options) {
                    if (!options.contains(option)) {
                        options.add(option);
                    }
                }
            }
        }

        String canonicalFiniteValue(Value<?> value) {
            switch (node.kind()) {
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
                    throw new IllegalStateException("Unsupported finite input kind: " + node.kind());
            }
        }

        long branchCount() {
            switch (node.kind()) {
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
            for (InputSite site : sites) {
                Guard active = flow.and(regionGuard, site.definition);
                if (flow.isFalse(active)) {
                    continue;
                }
                Value<?> value = flow.declaredValue(site.node, key, active);
                if (value.isPresent()) {
                    if (!exact.isPresent()) {
                        exact = value;
                    } else if (!Value.isEqual(exact, value)) {
                        return Value.empty();
                    }
                }
            }
            return exact;
        }

        String defaultValue(Guard regionGuard) {
            for (InputSite site : sites) {
                Guard active = flow.and(regionGuard, site.definition);
                if (!flow.isFalse(active) && site.defaultValue != null) {
                    return site.defaultValue;
                }
            }
            return null;
        }

        void validateOption(String value) {
            if (node.kind() != Kind.INPUT_LIST || !"none".equals(value)) {
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
            for (InputSite site : sites) {
                Guard next = site.membershipGuard(selected);
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

        private final class InputSite {
            private final Node node;
            private final Guard definition;
            private final String[] options;
            private final String defaultValue;

            InputSite(Node node) {
                this.node = node;
                this.definition = flow.activeGuard(node.parent());
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
                Guard availability = symInfo.availability(value);
                Guard active = availability == null ? definition : guards.and(definition, availability);
                return flow.isFalse(active) ? Guard.FALSE : active;
            }

            Guard constraint(String value) {
                Guards guards = flow.guards();
                Symbol sym = symInfo.symbol();
                if (!sym.domain().scalar() || !sym.domain().contains(value) || !sym.guardable()) {
                    return Guard.FALSE;
                }
                return guards.and(availability(value), guards.eq(sym.id(), value));
            }

            Guard membershipGuard(List<String> selected) {
                Guards guards = flow.guards();
                Guard guard = definition;
                Symbol sym = symInfo.symbol();
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
        private final Guard pinned;
        private final Guard excluded;
        private final Map<String, String> externalValues;
        private final Map<String, String> externalDefaults;
        private final List<Finding> findings;

        CompiledPlan(Plan plan,
                     Guard pinned,
                     Guard excluded,
                     Map<String, String> externalValues,
                     Map<String, String> externalDefaults,
                     List<Finding> findings) {
            this.plan = plan;
            this.pinned = pinned;
            this.excluded = excluded;
            this.externalValues = externalValues;
            this.externalDefaults = externalDefaults;
            this.findings = findings;
        }
    }
}
