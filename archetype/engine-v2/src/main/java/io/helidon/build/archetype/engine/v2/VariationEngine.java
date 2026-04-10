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
import java.time.Duration;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import io.helidon.build.archetype.engine.v2.Context.Scope;
import io.helidon.build.archetype.engine.v2.Context.ScopeValue;
import io.helidon.build.archetype.engine.v2.Context.ValueKind;
import io.helidon.build.archetype.engine.v2.InputResolver.InvalidInputException;
import io.helidon.build.archetype.engine.v2.InputResolver.ResolvedKind;
import io.helidon.build.archetype.engine.v2.Node.Kind;
import io.helidon.build.archetype.engine.v2.ScriptInvoker.InvocationException;
import io.helidon.build.common.BitSets;
import io.helidon.build.common.Lists;
import io.helidon.build.common.Maps;
import io.helidon.build.common.logging.Log;
import io.helidon.build.common.logging.LogLevel;

import static io.helidon.build.archetype.engine.v2.Nodes.optionIndex;
import static java.util.Objects.requireNonNull;

/**
 * Computes {@link Variations} for a prepared script source.
 */
public final class VariationEngine {
    private final Script.Source source;
    private final Context ctx = new Context();
    private final ScriptInliner inliner;
    private final ScriptIndexer indexer;
    private final Flow flow;
    private boolean initialized;
    private Node sourceNode;

    /**
     * Create a new instance.
     *
     * @param source source
     * @param cwd    cwd
     */
    public VariationEngine(Script.Source source, Path cwd) {
        ctx.pushCwd(requireNonNull(cwd, "cwd is null"));
        this.source = requireNonNull(source, "source is null");
        this.inliner = new ScriptInliner(ctx);
        this.indexer = new ScriptIndexer(ctx.scope());
        this.flow = new Flow(ctx.scope());
    }

    /**
     * Compute variations.
     *
     * @param filters                  filters
     * @param externalValues           fixed external values
     * @param externalDefaults         external defaults
     * @param maxIntermediateVariations max actual intermediate variation row count
     * @return computed variations
     * @throws IllegalStateException if the intermediate variation row count exceeds the configured limit
     */
    public Variations compute(List<Expression> filters,
                              Map<String, String> externalValues,
                              Map<String, String> externalDefaults,
                              long maxIntermediateVariations) {

        requireNonNull(filters);
        requireNonNull(externalValues);
        requireNonNull(externalDefaults);
        if (maxIntermediateVariations < 0) {
            throw new IllegalArgumentException("maxIntermediateVariations must be >= 0");
        }

        init();
        List<Variations.Entry> variations = new ArrayList<>();
        sourceNode.visit(new VisitorImpl(sourceNode,
                flow.model(),
                ctx.cwd(),
                variations,
                filters,
                externalValues,
                externalDefaults,
                maxIntermediateVariations));
        return Variations.of(variations);
    }

    private void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        sourceNode = inliner.inline(source);
        indexer.index(sourceNode);
        flow.process(sourceNode);
    }

    private static final class VisitorImpl implements Node.Visitor {
        private final Node sourceNode;
        private final Flow.Model flowModel;
        private final Path cwd;

        private final List<Table> inputs = new ArrayList<>();
        private final List<Column> columns = new ArrayList<>();
        private final Map<Column, Integer> indexes = new LinkedHashMap<>();
        private final Map<BitSet, Map<String, String>> variationCache = new ConcurrentHashMap<>();
        private final Set<String> textInputs = new LinkedHashSet<>();
        private final Collection<Variations.Entry> variations;
        private final List<Expression> filters;
        private final Map<String, String> externalValues;
        private final Map<String, String> externalDefaults;
        private final Map<String, Value.Type> inputTypes;
        private final Map<String, String> resolvedExternalValues;
        private final Map<String, String> resolvedExternalDefaults;
        private final long maxIntermediateVariations;

        VisitorImpl(Node sourceNode,
                    Flow.Model flowModel,
                    Path cwd,
                    Collection<Variations.Entry> variations,
                    List<Expression> filters,
                    Map<String, String> externalValues,
                    Map<String, String> externalDefaults,
                    long maxIntermediateVariations) {
            this.sourceNode = sourceNode;
            this.flowModel = flowModel;
            this.cwd = cwd;
            this.variations = variations;
            this.filters = filters;
            this.externalValues = Collections.unmodifiableMap(new LinkedHashMap<>(externalValues));
            this.externalDefaults = Collections.unmodifiableMap(new LinkedHashMap<>(externalDefaults));
            this.inputTypes = inputTypes();
            this.resolvedExternalValues = resolvedExternalValues();
            this.resolvedExternalDefaults = resolvedExternalDefaults();
            this.maxIntermediateVariations = maxIntermediateVariations;
        }

        @Override
        public boolean visit(Node node) {
            if (!active(node)) {
                return false;
            }
            Table table;
            List<Node> options;
            List<Node> optionNodes;
            switch (node.kind()) {
                case INPUT_TEXT:
                    table = table(node);
                    String textValue = declaredValue(node, table.id).asString()
                            .or(() -> externalDefaultValue(table.id))
                            .or(() -> node.attribute("default").asString())
                            .orElse("<?>");
                    table.columns.add(new Column(table.id, textValue));
                    table.addRow(BitSets.of(0), Expression.TRUE);
                    inputs.add(table);
                    textInputs.add(table.id);
                    break;
                case INPUT_BOOLEAN:
                    table = table(node);
                    table.columns.add(new Column(table.id, "true"));
                    table.columns.add(new Column(table.id, "false"));
                    Value<Boolean> boolValue = declaredValue(node, table.id).asBoolean();
                    if (boolValue.isPresent()) {
                        table.addRow(BitSets.of(boolValue.get() ? 0 : 1), Expression.TRUE);
                    } else {
                        table.addRow(BitSets.of(0), Expression.TRUE);
                        table.addRow(BitSets.of(1), Expression.TRUE);
                    }
                    inputs.add(table);
                    break;
                case INPUT_ENUM:
                    table = table(node);
                    optionNodes = optionNodes(node);
                    options = Lists.map(optionNodes, Node::unwrap);
                    for (Node option : options) {
                        table.columns.add(new Column(table.id, option.value().getString()));
                    }
                    int index = declaredValue(node, table.id).asString()
                            .map(option -> requiredOptionIndex(table.id, option, options))
                            .orElse(-1);
                    if (index >= 0) {
                        Node selected = optionNodes.get(index);
                        table.addRow(BitSets.of(index), prune(node, selected.expression()));
                    } else {
                        for (int i = 0; i < table.columns.size(); i++) {
                            Node option = optionNodes.get(i);
                            table.addRow(BitSets.of(i), prune(node, option.expression()));
                        }
                    }
                    inputs.add(table);
                    break;
                case INPUT_LIST:
                    table = table(node);
                    optionNodes = optionNodes(node);
                    options = Lists.map(optionNodes, Node::unwrap);
                    for (Node option : options) {
                        table.columns.add(new Column(table.id, option.value().getString()));
                    }
                    Value<List<String>> value = declaredValue(node, table.id).asList();
                    if (value.isPresent()) {
                        BitSet bits = new BitSet();
                        Expression expr = Expression.TRUE;
                        for (String option : value.getList()) {
                            int i = requiredOptionIndex(table.id, option, options);
                            bits.set(i);
                            Node selected = optionNodes.get(i);
                            expr = expr.and(prune(node, selected.expression()));
                        }
                        table.addRow(bits, expr);
                    } else {
                        for (int p = 1, permSize = 1 << table.columns.size(); p < permSize; p++) {
                            Expression expr = Expression.TRUE;
                            BitSet bits = BitSets.of((long) p);
                            for (int i = bits.nextSetBit(0); i >= 0 && i < Integer.MAX_VALUE;
                                    i = bits.nextSetBit(i + 1)) {
                                Node option = optionNodes.get(i);
                                expr = expr.and(prune(node, option.expression()));
                            }
                            table.addRow(bits, expr);
                        }
                    }
                    table.columns.add(new Column(table.id, "none"));
                    table.addRow(BitSets.of(table.columns.size() - 1), Expression.TRUE);
                    inputs.add(table);
                    break;
                default:
            }
            return true;
        }

        @Override
        public void postVisit(Node node) {
            if (node.kind() != Kind.SCRIPT) {
                return;
            }

            long computeStartTime = System.currentTimeMillis();

            List<Table> orderedInputs = new ArrayList<>(inputs);
            orderedInputs.sort(Comparator.comparingInt((Table table) -> inputOrder(table.id))
                    .thenComparingInt(table -> table.node.id()));
            for (Table table : orderedInputs) {
                for (Column column : table.columns) {
                    int index = indexes.computeIfAbsent(column, ignored -> indexes.size());
                    if (index == columns.size()) {
                        columns.add(column);
                    }
                }
            }

            List<Table> tables = new ArrayList<>();
            for (Table input : inputs) {
                Table table = table(input.node);
                table.columns.addAll(input.columns);
                for (Row row : input.rows) {
                    BitSet bitSet = new BitSet();
                    for (int i = row.bits.nextSetBit(0); i >= 0 && i < Integer.MAX_VALUE;
                            i = row.bits.nextSetBit(i + 1)) {
                        bitSet.set(indexes.get(input.columns.get(i)));
                    }
                    table.addRow(bitSet, row.expr);
                }
                tables.add(table);
            }

            Set<String> inputIds = tables.stream()
                    .map(table -> table.id)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            for (Table table : tables) {
                table.dependencies.addAll(dependencies(table, inputIds));
            }

            List<Table> pending = new ArrayList<>(tables);
            Map<String, Integer> joined = new HashMap<>();
            Set<String> available = new LinkedHashSet<>();
            Map<String, Integer> totals = new HashMap<>();
            for (Table table : tables) {
                totals.compute(table.id, (key, value) -> value == null ? 1 : value + 1);
            }

            Set<BitSet> merged = new LinkedHashSet<>();
            for (int i = 0; i < tables.size(); i++) {
                Join join = nextJoin(pending, available, merged);
                join.filtered.forEach(merged::remove);
                pending.remove(join.table);

                int count = joined.compute(join.table.id, (key, value) -> value == null ? 1 : value + 1);
                if (count == totals.getOrDefault(join.table.id, -1)) {
                    available.add(join.table.id);
                }

                Log.debug("Progress: %d/%d - %s - filtered: %d, merged: %d",
                        i + 1,
                        tables.size(),
                        join.table,
                        join.filtered.size(),
                        merged.size());

                if (join.filtered.isEmpty()) {
                    if (merged.isEmpty()) {
                        for (Row row : join.table.rows) {
                            mergeRow(node, join, merged, BitSets.copyOf(row.bits), row.expr);
                        }
                    }
                } else {
                    for (Row row1 : join.table.rows) {
                        for (BitSet row2 : join.filtered) {
                            mergeRow(node, join, merged, BitSets.or(BitSets.copyOf(row1.bits), row2), row1.expr);
                        }
                    }
                }
            }
            logDuration(computeStartTime, "Computed " + merged.size() + " variations");

            long normalizeStartTime = System.currentTimeMillis();
            Collection<Variations.Entry> normalized = merged.parallelStream()
                    .map(this::normalize)
                    .filter(Objects::nonNull)
                    .collect(new NormalizedCollector());
            logDuration(normalizeStartTime, "Normalized " + merged.size() + " variations");

            long sortStartTime = System.currentTimeMillis();
            variations.addAll(normalized);
            logDuration(sortStartTime, "Sorted " + variations.size() + " variations");
        }

        void mergeRow(Node node, Join join, Set<BitSet> merged, BitSet bits, Expression rowExpr) {
            if (join.table.expr == Expression.TRUE && rowExpr == Expression.TRUE && filters.isEmpty()) {
                addMerged(merged, bits, join.table.id);
                return;
            }
            Map<String, String> vars = variation(bits);
            if (eval(join.table.node, join.table.expr, vars) && eval(join.table.node, rowExpr, vars) && filter(node, vars)) {
                addMerged(merged, bits, join.table.id);
            }
        }

        void addMerged(Set<BitSet> merged, BitSet bits, String inputId) {
            if (merged.add(bits) && merged.size() > maxIntermediateVariations) {
                throw new IllegalStateException(String.format(
                        "Intermediate variation row count %d exceeds the configured limit of %d while joining input '%s'",
                        merged.size(),
                        maxIntermediateVariations,
                        inputId));
            }
        }

        Map<String, String> variation(BitSet row) {
            return variationCache.computeIfAbsent(row, this::variation0);
        }

        Map<String, String> variation0(BitSet row) {
            Map<String, String> variation = new LinkedHashMap<>();
            for (int i = row.nextSetBit(0); i >= 0 && i < Integer.MAX_VALUE; i = row.nextSetBit(i + 1)) {
                Column column = columns.get(i);
                variation.compute(column.name, (key, value) -> value == null ? column.value : value + "," + column.value);
            }
            variation.putAll(resolvedExternalValues);
            return Collections.unmodifiableMap(variation);
        }

        Variations.Entry normalize(BitSet row) {
            Map<String, String> variation = variation(row);
            Map<String, ScopeValue<?>> effective = execute(variation);
            if (effective.isEmpty()) {
                return null;
            }

            Map<String, String> values = Maps.mapValue(effective, value -> Value.toString(value));
            if (!filter(sourceNode, values)) {
                return null;
            }
            Set<String> unbounded = effective.entrySet().stream()
                    .filter(entry -> textInputs.contains(entry.getKey()) && entry.getValue().kind() != ValueKind.EXTERNAL)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toCollection(TreeSet::new));

            Map<String, String> normalized = Maps.mapValue(effective,
                    (key, value) -> value.kind() == ValueKind.USER,
                    value -> Value.toString(value),
                    TreeMap::new);

            String signature = Lists.join(normalized.entrySet(), " ");
            return Variations.entry(values, unbounded, signature);
        }

        boolean filter(Node node, Map<String, String> variation) {
            if (filters.isEmpty()) {
                return true;
            }
            for (Expression exclude : filters) {
                if (eval(node, exclude, variation)) {
                    if (LogLevel.isDebug()) {
                        Log.debug("Excluding variation, rule: %s, entries: %s", exclude.literal(), variation);
                    }
                    return false;
                }
            }
            return true;
        }

        boolean eval(Node node, Expression expr, Map<String, String> variation) {
            if (expr == Expression.TRUE) {
                return true;
            }
            if (expr == Expression.FALSE) {
                return false;
            }
            try {
                Scope scope = flowModel.scope(node);
                return expr.eval(variable -> {
                    String value = variation.get(scope.key(variable));
                    if (value != null) {
                        return Value.dynamic(value);
                    }
                    return null;
                });
            } catch (Expression.UnresolvedVariableException ignored) {
                return false;
            }
        }

        boolean active(Node node) {
            return prune(node, activation(node)) != Expression.FALSE;
        }

        Expression activation(Node node) {
            switch (node.kind()) {
                case INPUT_TEXT:
                case INPUT_BOOLEAN:
                case INPUT_ENUM:
                case INPUT_LIST:
                    return flowModel.activationCondition(node.parent());
                default:
                    return flowModel.activationCondition(node);
            }
        }

        Expression prune(Node node, Expression expr) {
            if (expr == Expression.TRUE || expr == Expression.FALSE || resolvedExternalValues.isEmpty()) {
                return expr;
            }
            Scope scope = flowModel.scope(node);
            return expr.inline(variable -> {
                String key = scope.key(variable);
                String value = resolvedExternalValues.get(key);
                if (value == null) {
                    return null;
                }
                return typedValue(key, value);
            });
        }

        Map<String, ScopeValue<?>> execute(Map<String, String> variation) {
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
                ScriptInvoker.invoke(sourceNode, context, new InputResolver.BatchResolver(context), node -> {
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

        Value<?> declaredValue(Node node, String key) {
            String value = resolvedExternalValues.get(key);
            if (value != null) {
                return Value.typed(Value.dynamic(value), node.kind().valueType());
            }
            return flowModel.declaredValue(node, key);
        }

        Value<String> externalDefaultValue(String key) {
            String value = resolvedExternalDefaults.get(key);
            if (value == null) {
                value = externalDefaults.get(key);
            }
            return Value.of(value);
        }

        Map<String, String> resolvedExternalValues() {
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

        Map<String, Value.Type> inputTypes() {
            Map<String, Value.Type> types = new LinkedHashMap<>();
            for (Node input : sourceNode.traverse(Kind::isInput)) {
                types.putIfAbsent(flowModel.key(input), input.kind().valueType());
            }
            return Collections.unmodifiableMap(types);
        }

        Value<?> typedValue(String key, String value) {
            Value.Type type = inputTypes.get(key);
            if (type == null) {
                return Value.of(value);
            }
            switch (type) {
                case BOOLEAN:
                    return Value.parseBoolean(value);
                case INTEGER:
                    return Value.parseInt(value);
                case LIST:
                    return Value.parseList(value);
                default:
                    return Value.of(value);
            }
        }

        int inputOrder(String key) {
            int index = 0;
            for (String id : inputTypes.keySet()) {
                if (id.equals(key)) {
                    return index;
                }
                index++;
            }
            return Integer.MAX_VALUE;
        }

        Map<String, String> resolvedExternalDefaults() {
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

        Join nextJoin(List<Table> tables, Set<String> available, Set<BitSet> merged) {
            if (tables.isEmpty()) {
                throw new IllegalStateException("No tables available for join");
            }
            Table best = null;
            Table fallback = null;
            int mergedSize = merged.size();
            long bestCost = Long.MAX_VALUE;
            for (Table table : tables) {
                if (fallback == null) {
                    fallback = table;
                }
                if (!available.containsAll(table.dependencies)) {
                    continue;
                }
                long cost = estimateJoinSize(table, mergedSize);
                if (best == null || cost < bestCost || (cost == bestCost && table.rows.size() < best.rows.size())) {
                    best = table;
                    bestCost = cost;
                }
            }
            return join(best != null ? best : fallback, merged);
        }

        long estimateJoinSize(Table table, int mergedSize) {
            if (table.expr == Expression.FALSE) {
                return mergedSize;
            }
            if (mergedSize == 0) {
                return table.rows.size();
            }
            if (table.expr == Expression.TRUE) {
                return (long) mergedSize * table.rows.size();
            }
            if (table.rows.size() <= 1) {
                return mergedSize;
            }
            long estimatedFiltered = mergedSize >>> 1;
            return mergedSize - estimatedFiltered + estimatedFiltered * table.rows.size();
        }

        Join join(Table table, Set<BitSet> merged) {
            if (table.expr == Expression.FALSE || merged.isEmpty()) {
                return new Join(table, List.of());
            }
            if (table.expr == Expression.TRUE) {
                return new Join(table, new ArrayList<>(merged));
            }

            List<BitSet> filtered = new ArrayList<>();
            for (BitSet row : merged) {
                Map<String, String> variation = variation(row);
                if (eval(table.node, table.expr, variation)) {
                    filtered.add(row);
                }
            }
            return new Join(table, filtered);
        }

        Set<String> dependencies(Table table, Set<String> inputIds) {
            Scope scope = flowModel.scope(table.node);
            Set<String> dependencies = new LinkedHashSet<>(dependencies(table.expr, scope, table.id, inputIds));
            for (Row row : table.rows) {
                dependencies.addAll(dependencies(row.expr, scope, table.id, inputIds));
            }
            return dependencies;
        }

        Set<String> dependencies(Expression expr, Scope scope, String self, Set<String> inputIds) {
            if (expr == Expression.TRUE || expr == Expression.FALSE) {
                return Set.of();
            }
            Set<String> dependencies = new LinkedHashSet<>();
            for (String variable : expr.variables()) {
                String key = scope.key(variable);
                if (!key.equals(self) && inputIds.contains(key)) {
                    dependencies.add(key);
                }
            }
            return dependencies;
        }

        Table table(Node node) {
            Expression expr = flowModel.activationCondition(node.parent());
            return new Table(node, flowModel.key(node), prune(node, expr));
        }

        static List<Node> optionNodes(Node node) {
            return node.children().stream()
                    .filter(child -> child.unwrap().kind() == Kind.INPUT_OPTION)
                    .collect(Collectors.toList());
        }

        static int requiredOptionIndex(String inputId, String option, List<Node> options) {
            int index = optionIndex(option, options);
            if (index >= 0) {
                return index;
            }
            String values = options.stream()
                    .map(Node::value)
                    .map(value -> Value.toString(value))
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException(String.format(
                    "Invalid value '%s' for input '%s', available options: %s",
                    option,
                    inputId,
                    values));
        }

        static void logDuration(long startTime, String msg) {
            long endTime = System.currentTimeMillis();
            Duration duration = Duration.ofMillis(endTime - startTime);
            Log.debug("%s in %d.%ds", msg, duration.toSeconds(), duration.toMillisPart());
        }
    }

    private static final class Column {
        private final String name;
        private final String value;

        Column(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Column)) {
                return false;
            }
            Column column = (Column) o;
            return Objects.equals(name, column.name) && Objects.equals(value, column.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }

        @Override
        public String toString() {
            return name + "=" + value;
        }
    }

    private static final class Row {
        private final BitSet bits;
        private final Expression expr;

        Row(BitSet bits, Expression expr) {
            this.bits = bits;
            this.expr = expr.foldConstants();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Row)) {
                return false;
            }
            Row row = (Row) o;
            return Objects.equals(bits, row.bits) && Objects.equals(expr, row.expr);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bits, expr);
        }
    }

    private static final class Table {
        private final List<Column> columns = new ArrayList<>();
        private final Set<Row> rows = new LinkedHashSet<>();
        private final Set<String> dependencies = new LinkedHashSet<>();
        private final String id;
        private final Node node;
        private final Expression expr;

        Table(Node node, String id, Expression expr) {
            this.id = id;
            this.node = node;
            this.expr = expr;
        }

        void addRow(BitSet bits, Expression expr) {
            expr = expr.foldConstants();
            if (expr != Expression.FALSE) {
                rows.add(new Row(bits, expr));
            }
        }

        @Override
        public String toString() {
            return id;
        }
    }

    private static final class Join {
        private final Table table;
        private final List<BitSet> filtered;

        Join(Table table, List<BitSet> filtered) {
            this.table = table;
            this.filtered = filtered;
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
