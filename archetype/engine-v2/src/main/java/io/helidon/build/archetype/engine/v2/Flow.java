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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;

import io.helidon.build.archetype.engine.v2.Context.Scope;
import io.helidon.build.archetype.engine.v2.Domain.Guard;
import io.helidon.build.archetype.engine.v2.Domain.GuardedValue;
import io.helidon.build.archetype.engine.v2.Domain.Guards;
import io.helidon.build.archetype.engine.v2.Domain.LatticeValue;
import io.helidon.build.archetype.engine.v2.Domain.Spec;
import io.helidon.build.archetype.engine.v2.Domain.Symbol;
import io.helidon.build.archetype.engine.v2.Domain.Fact;
import io.helidon.build.archetype.engine.v2.Domain.Table;
import io.helidon.build.archetype.engine.v2.Expression.Operator;
import io.helidon.build.archetype.engine.v2.Expression.Token;
import io.helidon.build.archetype.engine.v2.Node.Kind;

import static java.util.Objects.requireNonNull;

final class Flow {
    private final Scope scope;
    private Ir ir;
    private State rootEntry;
    private Map<Node, NodeFacts> nodeFacts = Map.of();
    private SymbolInfo[] symbolInfos = new SymbolInfo[0];

    Flow(Scope scope) {
        this.scope = requireNonNull(scope, "scope is null");
    }

    void process(Node root) {
        requireNonNull(root, "root is null");
        ir = new Lowerer(scope).lower(root);
        Analyzer analyzer = new Analyzer(ir);
        analyzer.analyze();
        Projector projector = new Projector(ir, analyzer, root, scope);
        projector.project();
        rootEntry = analyzer.entryStates[0];
        nodeFacts = projector.nodeFacts;
        symbolInfos = projector.symbolInfos;
    }

    Guards guards() {
        return ir.guards;
    }

    String key(Node node) {
        return node == null ? scope.key() : facts(node).key;
    }

    Scope scope(Node node) {
        if (node == null) {
            return scope;
        }
        String key = facts(node).key;
        return key.isEmpty() ? scope : scope.get("~" + key);
    }

    Guard renderGuard(Node node) {
        return node == null ? Guard.TRUE : facts(node).renderGuard;
    }

    Guard activeGuard(Node node) {
        return node == null ? Guard.TRUE : facts(node).activeGuard;
    }

    State before(Node node) {
        return node == null ? rootEntry : facts(node).before;
    }

    SymbolInfo symbol(String name) {
        int id = ir.table.findId(name);
        return id < 0 ? null : symbolInfos[id];
    }

    Symbol symbol(int id) {
        return ir.table.symbol(id);
    }

    Expression expression(Guard guard) {
        return expression(guard, scope);
    }

    Expression expression(Guard guard, Scope scope) {
        return ir.guards.expression(guard, scope).reduce();
    }

    Expression activationCondition(Node node) {
        if (node == null) {
            return Expression.TRUE;
        }
        Scope scope = scope(node);
        if (node.kind() == Kind.CONDITION) {
            return expression(activeGuard(node), scope).reduce(expression(renderGuard(node), scope));
        }
        return expression(activeGuard(node), scope);
    }

    Value<?> declaredValue(Node node, String key) {
        Fact fact = fact(node, key);
        Guard required = activeGuard(node == null ? null : node.parent());
        if (fact == null || !ir.guards.implies(required, fact.guard())) {
            return Value.empty();
        }
        SymbolInfo info = symbol(key);
        if (info == null && !key.startsWith("~")) {
            info = symbol("~" + key);
        }
        Value.Type type = info == null ? Value.Type.EMPTY : valueType(info);
        Value<?> exact = exactValue(fact, required, type);
        return exact != null ? exact : info == null ? Value.empty() : singletonValue(fact.value(), info.sym.domain(), type);
    }

    Value<?> declaredValue(Node node) {
        return declaredValue(node, key(node));
    }

    Guard residualGuard(Expression expression) {
        return ir.guards.residualGuard(expression);
    }

    boolean isFalse(Guard guard) {
        return guard == null || guard.isFalse();
    }

    Guard and(Guard left, Guard right) {
        if (left == null || right == null) {
            return null;
        }
        return ir.guards.and(left, right);
    }

    Guard or(Guard left, Guard right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return ir.guards.or(left, right);
    }

    Guard minus(Guard left, Guard right) {
        if (left == null) {
            return null;
        }
        if (right == null) {
            return left;
        }
        return ir.guards.minus(left, right);
    }

    boolean contains(Guard left, Guard right) {
        return left != null && right != null && ir.guards.implies(right, left);
    }

    boolean equivalent(Guard left, Guard right) {
        return left != null && right != null && ir.guards.equivalent(left, right);
    }

    private NodeFacts facts(Node node) {
        if (node != null) {
            NodeFacts facts = nodeFacts.get(node);
            if (facts != null) {
                return facts;
            }
        }
        throw new IllegalArgumentException("Node is not part of this flow model: " + node);
    }

    private Fact fact(Node node, String key) {
        State before = before(node);
        if (before.env.isEmpty()) {
            return null;
        }
        int id = ir.table.findId(key);
        if (id < 0 && !key.startsWith("~")) {
            id = ir.table.findId("~" + key);
        }
        return id < 0 ? null : before.env.get(id);
    }

    private Value<?> exactValue(Fact fact, Guard required, Value.Type type) {
        if (!fact.guardedValues().isEmpty()) {
            Value<?> exact = null;
            Guard coverage = Guard.FALSE;
            for (GuardedValue guardedValue : fact.guardedValues()) {
                Guard overlap = ir.guards.and(required, guardedValue.guard());
                if (overlap.equals(Guard.FALSE)) {
                    continue;
                }
                Value<?> candidate = coerceExactValue(guardedValue.value(), type);
                if (exact == null) {
                    exact = candidate;
                } else if (!Value.isEqual(exact, candidate)) {
                    return null;
                }
                coverage = ir.guards.or(coverage, overlap);
            }
            if (exact != null && ir.guards.implies(required, coverage)) {
                return exact;
            }
        }
        return null;
    }

    private static Value.Type valueType(SymbolInfo info) {
        switch (info.sym.domain().kind()) {
            case BOOLEAN:
                return Value.Type.BOOLEAN;
            case CHOICE:
            case FINITE_TEXT:
            case OPEN_TEXT:
                return Value.Type.STRING;
            case MEMBERSHIP:
                return Value.Type.LIST;
            default:
                return Value.Type.EMPTY;
        }
    }

    private static Scope childScope(Scope scope, Node node) {
        switch (node.kind()) {
            case INPUT_BOOLEAN:
            case INPUT_ENUM:
            case INPUT_LIST:
            case INPUT_TEXT:
                return scope.getOrCreate(node);
            default:
                return scope;
        }
    }

    private static Value<?> coerceExactValue(Value<?> value, Value.Type type) {
        if (type == Value.Type.EMPTY) {
            return value;
        }
        try {
            Value<?> coerced = Value.typed(value, type);
            return coerced.isPresent() ? coerced : value;
        } catch (RuntimeException ex) {
            return value;
        }
    }

    private static Value<?> singletonValue(LatticeValue value, Spec spec, Value.Type type) {
        if (value.kind() == LatticeValue.Kind.FINITE_SCALAR) {
            String scalar = value.singletonScalar(spec);
            if (scalar == null) {
                return Value.empty();
            }
            return type == Value.Type.BOOLEAN ? Value.of(Boolean.parseBoolean(scalar)) : Value.of(scalar);
        }
        if (value.kind() == LatticeValue.Kind.OPEN_TEXT) {
            return value.sample() == null ? Value.empty() : Value.of(value.sample());
        }
        return Value.empty();
    }

    private static int requireSymbolId(Table table, String key) {
        int id = table.findId(key);
        if (id < 0) {
            throw new IllegalStateException("Missing symbol: " + key);
        }
        return id;
    }

    private static Guard directBooleanGuard(Table table, Guards guards, String key) {
        Symbol sym = table.symbol(requireSymbolId(table, key));
        if (!sym.guardable() || sym.tainted() || sym.domain().kind() != Spec.Kind.BOOLEAN) {
            return Guard.TRUE;
        }
        return guards.eq(sym.id(), "true");
    }

    private static Guard directOptionGuard(Table table, Guards guards, String key, Node input, Node option) {
        Symbol sym = table.symbol(requireSymbolId(table, key));
        String value = option.value().getString();
        switch (input.kind()) {
            case INPUT_ENUM:
                if (!sym.guardable() || sym.tainted()) {
                    return Guard.TRUE;
                }
                return sym.domain().kind().isScalar() ? guards.eq(sym.id(), value) : Guard.TRUE;
            case INPUT_LIST:
                if (!sym.guardable() || sym.tainted() || sym.domain().kind() != Spec.Kind.MEMBERSHIP) {
                    return Guard.TRUE;
                }
                return guards.contains(sym.id(), value);
            default:
                throw new IllegalArgumentException("Unsupported option parent: " + input.kind());
        }
    }

    static final class SymbolInfo {
        private final Symbol sym;
        private Guard definition = Guard.FALSE;
        private final Map<String, Guard> availability = new LinkedHashMap<>();

        SymbolInfo(Symbol sym) {
            this.sym = sym;
        }

        Symbol symbol() {
            return sym;
        }

        Guard definition() {
            return definition;
        }

        Guard availability(String value) {
            return availability.get(value);
        }

        void addDefinition(Guard guard, Guards guards) {
            definition = guards.or(definition, guard);
        }

        void addAvailability(String value, Guard guard, Guards guards) {
            availability.compute(value, (key, current) -> current == null ? guard : guards.or(current, guard));
        }
    }

    static final class State {
        private final Guard path;
        private final Map<Integer, Fact> env;

        State(Guard path, Map<Integer, Fact> env) {
            this.path = path;
            this.env = env;
        }

        Guard path() {
            return path;
        }

        Map<Integer, Fact> env() {
            return env;
        }
    }

    private static final int OP_DECLARE_INPUT = 0;
    private static final int OP_DECLARE_OPTION = 1;
    private static final int OP_DEFINE_VALUE = 2;
    private static final int OP_EMIT = 3;

    private static final int TERM_NONE = -1;
    private static final int TERM_GOTO = 0;
    private static final int TERM_BRANCH = 1;
    private static final int TERM_RETURN = 2;
    private static final int TERM_UNREACHABLE = 3;

    private static final class Ir {
        private final int[] blockControlIds;
        private final int[][] blockOpIds;
        private final int[] terminatorKinds;
        private final Node[] terminatorSources;
        private final int[] terminatorTargetIds;
        private final int[] terminatorTrueIds;
        private final int[] terminatorFalseIds;
        private final Table table;
        private final int[] opKinds;
        private final int[] opBlockIds;
        private final Node[] opSources;
        private final int[] opSymbolIds;
        private final Expression[] opExpressions;
        private final Guards guards;
        private final int[] controlParentIds;
        private final Guard[] controlEdgeGuards;

        Ir(List<Integer> blockControlIds,
           List<List<Integer>> blockOpIds,
           List<Integer> terminatorKinds,
           List<Node> terminatorSources,
           List<Integer> terminatorTargetIds,
           List<Integer> terminatorTrueIds,
           List<Integer> terminatorFalseIds,
           Table table,
           List<Integer> opKinds,
           List<Integer> opBlockIds,
           List<Node> opSources,
           List<Integer> opSymbolIds,
           List<Expression> opExpressions,
           Guards guards,
           List<Integer> controlParentIds,
           List<Guard> controlEdgeGuards) {
            if (controlParentIds.isEmpty()) {
                throw new IllegalArgumentException("controls is empty");
            }
            this.blockControlIds = ints(blockControlIds);
            this.blockOpIds = nestedInts(blockOpIds);
            this.terminatorKinds = ints(terminatorKinds);
            this.terminatorSources = terminatorSources.toArray(Node[]::new);
            this.terminatorTargetIds = ints(terminatorTargetIds);
            this.terminatorTrueIds = ints(terminatorTrueIds);
            this.terminatorFalseIds = ints(terminatorFalseIds);
            this.table = table;
            this.opKinds = ints(opKinds);
            this.opBlockIds = ints(opBlockIds);
            this.opSources = opSources.toArray(Node[]::new);
            this.opSymbolIds = ints(opSymbolIds);
            this.opExpressions = opExpressions.toArray(Expression[]::new);
            this.guards = guards;
            this.controlParentIds = ints(controlParentIds);
            this.controlEdgeGuards = controlEdgeGuards.toArray(Guard[]::new);
        }

        private static int[] ints(List<Integer> values) {
            int[] result = new int[values.size()];
            for (int i = 0; i < values.size(); i++) {
                result[i] = values.get(i);
            }
            return result;
        }

        private static int[][] nestedInts(List<List<Integer>> values) {
            int[][] result = new int[values.size()][];
            for (int i = 0; i < values.size(); i++) {
                result[i] = ints(values.get(i));
            }
            return result;
        }
    }

    private static final class NodeFacts {
        private final State before;
        private final String key;
        private final Guard renderGuard;
        private final Guard activeGuard;

        NodeFacts(State before, String key, Guard renderGuard, Guard activeGuard) {
            this.before = before;
            this.key = key;
            this.renderGuard = renderGuard;
            this.activeGuard = activeGuard;
        }
    }

    private static final class Projector {
        private final Ir ir;
        private final Analyzer analyzer;
        private final Node node;
        private final Scope scope;
        private final Guards guards;
        private final Map<Node, List<Integer>> ops = new IdentityHashMap<>();
        private final Map<Node, Integer> branchTrueIds = new IdentityHashMap<>();
        private final Map<Node, Integer> branchJoinIds = new IdentityHashMap<>();
        private final Map<Node, NodeFacts> nodeFacts = new IdentityHashMap<>();
        private final SymbolInfo[] symbolInfos;

        Projector(Ir ir, Analyzer analyzer, Node node, Scope scope) {
            this.ir = ir;
            this.analyzer = analyzer;
            this.node = node;
            this.scope = scope;
            this.guards = ir.guards;
            List<Symbol> symbols = ir.table.symbols();
            this.symbolInfos = new SymbolInfo[symbols.size()];
            for (int i = 0; i < symbols.size(); i++) {
                symbolInfos[i] = new SymbolInfo(symbols.get(i));
            }
        }

        void project() {
            indexAnchors();
            scanSymbols();
            projectNode(node, scope, analyzer.entryStates[0]);
        }

        void indexAnchors() {
            for (int opId = 0; opId < ir.opKinds.length; opId++) {
                ops.computeIfAbsent(ir.opSources[opId], key -> new ArrayList<>()).add(opId);
            }
            for (int blockId = 0; blockId < ir.blockControlIds.length; blockId++) {
                if (ir.terminatorKinds[blockId] != TERM_BRANCH) {
                    continue;
                }
                int joinId = ir.terminatorFalseIds[blockId];
                if (ir.terminatorKinds[joinId] == TERM_GOTO) {
                    joinId = ir.terminatorTargetIds[joinId];
                }
                Node source = ir.terminatorSources[blockId];
                branchTrueIds.put(source, ir.terminatorTrueIds[blockId]);
                branchJoinIds.put(source, joinId);
            }
        }

        void scanSymbols() {
            for (int opId = 0; opId < ir.opKinds.length; opId++) {
                State before = analyzer.beforeStates[opId];
                switch (ir.opKinds[opId]) {
                    case OP_DECLARE_INPUT: {
                        SymbolInfo info = symbolInfos[ir.opSymbolIds[opId]];
                        info.addDefinition(before.path, guards);
                        if (info.sym.domain().kind() == Spec.Kind.BOOLEAN) {
                            info.addAvailability("true", before.path, guards);
                            info.addAvailability("false", before.path, guards);
                        }
                        break;
                    }
                    case OP_DECLARE_OPTION: {
                        SymbolInfo info = symbolInfos[ir.opSymbolIds[opId]];
                        info.addAvailability(ir.opSources[opId].value().getString(), before.path, guards);
                        break;
                    }
                    case OP_DEFINE_VALUE: {
                        SymbolInfo info = symbolInfos[ir.opSymbolIds[opId]];
                        if (before.path.equals(Guard.FALSE)) {
                            break;
                        }
                        Map<Integer, Fact> env = analyzer.afterStates[opId].env;
                        Fact fact = env.get(ir.opSymbolIds[opId]);
                        if (fact == null) {
                            throw new IllegalStateException("Missing flow fact after op for key: " + opId);
                        }
                        info.addDefinition(fact.guard(), guards);
                        recordAvailability(info, fact);
                        break;
                    }
                    default:
                        // no-op
                }
            }
        }

        void recordAvailability(SymbolInfo info, Fact fact) {
            if (!fact.guardedValues().isEmpty()) {
                for (GuardedValue guardedValue : fact.guardedValues()) {
                    recordExactAvailability(info, guardedValue);
                }
                return;
            }
            if (fact.value().kind() != LatticeValue.Kind.FINITE_SCALAR) {
                return;
            }
            switch (info.sym.domain().kind()) {
                case BOOLEAN:
                case CHOICE:
                case FINITE_TEXT:
                    break;
                default:
                    return;
            }
            if (fact.value().scalarMask() == info.sym.domain().mask()) {
                return;
            }
            for (String value : fact.value().scalarValues(info.sym.domain())) {
                info.addAvailability(value, fact.guard(), guards);
            }
        }

        void recordExactAvailability(SymbolInfo info, GuardedValue guardedValue) {
            switch (info.sym.domain().kind()) {
                case BOOLEAN:
                case CHOICE:
                case FINITE_TEXT:
                case MEMBERSHIP:
                    long mask = guardedValue.mask();
                    while (mask != 0L) {
                        int ordinal = Long.numberOfTrailingZeros(mask);
                        info.addAvailability(info.sym.value(ordinal), guardedValue.guard(), guards);
                        mask &= mask - 1L;
                    }
                    break;
                default:
                    break;
            }
        }

        State projectNode(Node node, Scope scope, State current) {
            List<Integer> opIds = ops.getOrDefault(node, List.of());
            State before;
            State afterOps;
            if (opIds.isEmpty()) {
                before = current;
                afterOps = before;
            } else {
                before = constrainPath(analyzer.beforeStates[opIds.get(0)], current.path);
                afterOps = constrainPath(analyzer.afterStates[opIds.get(opIds.size() - 1)], current.path);
            }
            Scope childScope = childScope(scope, node);
            Scope nodeScope = node.kind().isInput() ? childScope : scope;
            State branchEntry;
            Guard activeGuard;
            Integer branchTrueId = branchTrueIds.get(node);
            Integer branchJoinId = branchJoinIds.get(node);
            if (branchTrueId == null || branchJoinId == null) {
                branchEntry = null;
                activeGuard = activeGuard(node, nodeScope, afterOps);
            } else {
                branchEntry = constrainPath(analyzer.entryStates[branchTrueId], current.path);
                activeGuard = activeGuard(node, nodeScope, branchEntry);
            }
            String nodeKey = nodeKey(node, scope, childScope);
            nodeFacts.put(node, new NodeFacts(before, nodeKey, before.path, activeGuard));

            Scope descendantScope = node.kind().isInput() ? childScope : scope;
            if (branchTrueId != null && branchJoinId != null) {
                State cursor = constrainPath(branchEntry, activeGuard);
                for (Node child : node.children()) {
                    cursor = projectNode(child, descendantScope, cursor);
                }
                return constrainPath(analyzer.entryStates[branchJoinId], current.path);
            }

            State cursor = afterOps;
            for (Node child : node.children()) {
                cursor = projectNode(child, descendantScope, cursor);
            }
            return cursor;
        }

        String nodeKey(Node node, Scope scope, Scope childScope) {
            switch (node.kind()) {
                case INPUT_BOOLEAN:
                case INPUT_ENUM:
                case INPUT_LIST:
                case INPUT_TEXT:
                    return childScope.key();
                case PRESET_BOOLEAN:
                case PRESET_ENUM:
                case PRESET_LIST:
                case PRESET_TEXT:
                case VARIABLE_BOOLEAN:
                case VARIABLE_ENUM:
                case VARIABLE_LIST:
                case VARIABLE_TEXT:
                    return Lowerer.definitionId(scope, node);
                default:
                    return scope.key();
            }
        }

        State constrainPath(State state, Guard required) {
            if (state.path.equals(required)) {
                return state;
            }
            Guard constrained = guards.and(state.path, required);
            return constrained.equals(state.path) ? state : new State(constrained, state.env);
        }

        Guard activeGuard(Node node, Scope nodeScope, State afterOps) {
            Guard control = controlGuard(node, nodeScope, afterOps);
            return control == null ? afterOps.path : guards.and(afterOps.path, control);
        }

        Guard controlGuard(Node node, Scope nodeScope, State state) {
            switch (node.kind()) {
                case CONDITION:
                    return translatedCondition(node.expression(), nodeScope, state);
                case INPUT_BOOLEAN:
                    return directBooleanGuard(ir.table, guards, nodeScope.key());
                case INPUT_OPTION:
                    Node input = node.ancestor(Kind::isInput).orElse(null);
                    if (input == null) {
                        return null;
                    }
                    switch (input.kind()) {
                        case INPUT_ENUM:
                        case INPUT_LIST:
                            return directOptionGuard(ir.table, guards, nodeScope.key(), input, node);
                        default:
                            return null;
                    }
                default:
                    return null;
            }
        }

        Guard translatedCondition(Expression expression, Scope scope, State state) {
            Guard translated = translatedCondition0(expression, scope, state);
            return translated != null ? translated : new ConditionLowerer(ir.table, guards, scope).lower(expression);
        }

        Guard translatedCondition0(Expression expression, Scope scope, State state) {
            int capacity = expression.tokens().size();
            Guard[] guardStack = new Guard[capacity];
            String[] refStack = new String[capacity];
            Value<?>[] literalStack = new Value<?>[capacity];
            int size = 0;
            for (Token token : expression.tokens()) {
                if (token.isVariable()) {
                    guardStack[size] = null;
                    refStack[size] = scope.key(token.variable());
                    literalStack[size] = null;
                    size++;
                    continue;
                }
                if (token.isOperand()) {
                    guardStack[size] = null;
                    refStack[size] = null;
                    literalStack[size] = token.operand();
                    size++;
                    continue;
                }
                switch (token.operator()) {
                    case NOT: {
                        int index = size - 1;
                        Guard negated = booleanGuard(guardStack[index], refStack[index], literalStack[index], state);
                        if (negated == null) {
                            return null;
                        }
                        guardStack[index] = guards.not(negated);
                        refStack[index] = null;
                        literalStack[index] = null;
                        break;
                    }
                    case AND: {
                        int right = --size;
                        Guard rightGuard = booleanGuard(guardStack[right], refStack[right], literalStack[right], state);
                        int left = --size;
                        Guard leftGuard = booleanGuard(guardStack[left], refStack[left], literalStack[left], state);
                        if (leftGuard == null || rightGuard == null) {
                            return null;
                        }
                        guardStack[left] = guards.and(leftGuard, rightGuard);
                        refStack[left] = null;
                        literalStack[left] = null;
                        size = left + 1;
                        break;
                    }
                    case OR: {
                        int right = --size;
                        Guard rightGuard = booleanGuard(guardStack[right], refStack[right], literalStack[right], state);
                        int left = --size;
                        Guard leftGuard = booleanGuard(guardStack[left], refStack[left], literalStack[left], state);
                        if (leftGuard == null || rightGuard == null) {
                            return null;
                        }
                        guardStack[left] = guards.or(leftGuard, rightGuard);
                        refStack[left] = null;
                        literalStack[left] = null;
                        size = left + 1;
                        break;
                    }
                    case EQUAL:
                    case NOT_EQUAL: {
                        int right = --size;
                        int left = --size;
                        Guard equality = equalityGuard(refStack[left], literalStack[left], refStack[right],
                                literalStack[right], state);
                        if (equality == null) {
                            return null;
                        }
                        guardStack[left] = token.operator() == Operator.EQUAL ? equality : guards.not(equality);
                        refStack[left] = null;
                        literalStack[left] = null;
                        size = left + 1;
                        break;
                    }
                    case CONTAINS: {
                        int right = --size;
                        int left = --size;
                        Guard contains = containsGuard(refStack[left], literalStack[left], refStack[right],
                                literalStack[right], state);
                        if (contains == null) {
                            return null;
                        }
                        guardStack[left] = contains;
                        refStack[left] = null;
                        literalStack[left] = null;
                        size = left + 1;
                        break;
                    }
                    default:
                        return null;
                }
            }
            return size == 1 ? booleanGuard(guardStack[0], refStack[0], literalStack[0], state) : null;
        }

        Guard booleanGuard(Guard guard, String ref, Value<?> literal, State state) {
            if (guard != null) {
                return guard;
            }
            if (ref != null) {
                SymbolInfo info = symbolInfo(ref);
                if (info == null || info.sym.domain().kind() != Spec.Kind.BOOLEAN) {
                    return null;
                }
                return translatedEquality(ref, Value.TRUE, state);
            }
            if (literal != null && literal.type() == Value.Type.BOOLEAN) {
                return literal.getBoolean() ? Guard.TRUE : Guard.FALSE;
            }
            return null;
        }

        Guard equalityGuard(String leftRef, Value<?> leftLiteral, String rightRef, Value<?> rightLiteral, State state) {
            if (leftLiteral != null && rightLiteral != null) {
                return Value.isEqual(leftLiteral, rightLiteral) ? Guard.TRUE : Guard.FALSE;
            }
            if (leftRef != null && rightLiteral != null) {
                return translatedEquality(leftRef, rightLiteral, state);
            }
            if (leftLiteral != null && rightRef != null) {
                return translatedEquality(rightRef, leftLiteral, state);
            }
            return null;
        }

        Guard containsGuard(String leftRef, Value<?> leftLiteral, String rightRef, Value<?> rightLiteral, State state) {
            if (leftRef != null && rightLiteral != null) {
                return translatedListContains(leftRef, rightLiteral, state);
            }
            if (leftLiteral != null && rightRef != null && leftLiteral.type() == Value.Type.LIST) {
                return translatedScalarAny(rightRef, new TreeSet<>(leftLiteral.getList()), state);
            }
            if (leftLiteral != null && rightLiteral != null && leftLiteral.type() == Value.Type.LIST) {
                Set<String> required = containsValues(rightLiteral);
                if (required == null) {
                    return null;
                }
                return new TreeSet<>(leftLiteral.getList()).containsAll(required)
                        ? Guard.TRUE
                        : Guard.FALSE;
            }
            return null;
        }

        Guard translatedEquality(String key, Value<?> value, State state) {
            Fact fact = factFor(state, key);
            SymbolInfo info = symbolInfo(key);
            Guard bound = fact == null || info == null ? Guard.FALSE : fact.match(info.sym, value, guards);
            Guard direct = directEquality(key, value);
            return combine(key, fact, bound, direct);
        }

        Guard translatedScalarAny(String key, Set<String> values, State state) {
            Fact fact = factFor(state, key);
            SymbolInfo info = symbolInfo(key);
            Guard bound = fact == null || info == null ? Guard.FALSE : fact.scalarAny(info.sym, values, guards);
            Guard direct = directScalarAny(key, values);
            return combine(key, fact, bound, direct);
        }

        Guard translatedListContains(String key, Value<?> value, State state) {
            Set<String> required = containsValues(value);
            if (required == null) {
                return null;
            }
            Fact fact = factFor(state, key);
            SymbolInfo info = symbolInfo(key);
            Guard bound = fact == null || info == null ? Guard.FALSE : fact.listContains(info.sym, required, guards);
            Guard direct = directListContains(symbolInfo(key), required);
            return combine(key, fact, bound, direct);
        }

        Guard combine(String key, Fact fact, Guard bound, Guard direct) {
            SymbolInfo info = symbolInfo(key);
            Symbol symbol = info == null ? null : info.sym;
            Guard defined = definitionFor(key, fact);
            if (fact == null) {
                return direct == null ? null : defined == null ? direct : guards.and(defined, direct);
            }
            if (direct == null) {
                if (bound == null) {
                    return null;
                }
                if (bound.equals(Guard.FALSE)) {
                    Guard exact = symbol == null ? fact.exactGuard(guards) : fact.supportedExactGuard(symbol, guards);
                    if (defined == null || exact.equals(Guard.FALSE) || !guards.implies(exact, defined)) {
                        return null;
                    }
                }
                return bound;
            }
            Guard available = defined == null ? Guard.TRUE : defined;
            Guard exact = symbol == null ? fact.exactGuard(guards) : fact.supportedExactGuard(symbol, guards);
            Guard unresolved = guards.and(guards.minus(available, exact), direct);
            return guards.or(bound, unresolved);
        }

        Fact factFor(State state, String key) {
            int symbolId = ir.table.findId(key);
            return symbolId < 0 ? null : state.env.get(symbolId);
        }

        Guard definitionFor(String key, Fact fact) {
            if (fact != null) {
                return fact.guard();
            }
            SymbolInfo info = symbolInfo(key);
            return info == null ? null : info.definition;
        }

        SymbolInfo symbolInfo(String key) {
            int symbolId = ir.table.findId(key);
            return symbolId < 0 ? null : symbolInfos[symbolId];
        }

        SymbolInfo scalarSymbolInfo(String key) {
            SymbolInfo info = symbolInfo(key);
            if (info == null) {
                return null;
            }
            Symbol symbol = info.sym;
            if (!symbol.guardable() || symbol.tainted()) {
                return null;
            }
            return symbol.domain().kind().isScalar() ? info : null;
        }

        Guard directEquality(String key, Value<?> value) {
            String scalar = Value.scalarLiteral(value);
            if (scalar != null) {
                SymbolInfo info = scalarSymbolInfo(key);
                if (info != null) {
                    return info.sym.domain().contains(scalar)
                            ? available(info, scalar, guards.eq(info.sym.id(), scalar))
                            : Guard.FALSE;
                }
            }
            return null;
        }

        Guard directScalarAny(String key, Set<String> values) {
            SymbolInfo info = scalarSymbolInfo(key);
            if (info != null) {
                Spec domain = info.sym.domain();
                Set<String> allowed = new TreeSet<>(values);
                allowed.removeIf(value -> !domain.contains(value));
                Guard direct = Guard.FALSE;
                for (String value : allowed) {
                    direct = guards.or(direct, available(info, value, guards.eq(info.sym.id(), value)));
                }
                return direct;
            }
            return null;
        }

        Guard directListContains(SymbolInfo info, Set<String> required) {
            if (info != null) {
                Symbol symbol = info.sym;
                if (symbol.guardable() && !symbol.tainted() && symbol.domain().kind() == Spec.Kind.MEMBERSHIP) {
                    if (!symbol.domain().containsAll(required)) {
                        return Guard.FALSE;
                    }
                    Guard available = Guard.TRUE;
                    for (String value : required) {
                        Guard availability = info.availability(value);
                        if (availability == null) {
                            return Guard.FALSE;
                        }
                        available = guards.and(available, availability);
                    }
                    return guards.and(available, guards.containsAll(symbol.id(), required));
                }
            }
            return null;
        }

        Guard available(SymbolInfo info, String value, Guard direct) {
            Guard availability = info.availability(value);
            return availability == null ? Guard.FALSE : guards.and(availability, direct);
        }

        Set<String> containsValues(Value<?> value) {
            switch (value.type()) {
                case STRING:
                case DYNAMIC:
                    return Set.of(value.getString());
                case LIST:
                    return new TreeSet<>(value.getList());
                default:
                    return null;
            }
        }
    }

    private static final class Lowerer {
        private final Scope scope;
        private final Map<String, SymbolSeed> symbolSeeds = new LinkedHashMap<>();
        private final Map<String, SymbolSeed> declaredInputSymbols = new LinkedHashMap<>();
        private final List<Integer> controlParentIds = new ArrayList<>();
        private final List<Guard> controlEdgeGuards = new ArrayList<>();
        private final List<Integer> blockControlIds = new ArrayList<>();
        private final List<List<Integer>> blockOpIds = new ArrayList<>();
        private final List<Integer> terminatorKinds = new ArrayList<>();
        private final List<Node> terminatorSources = new ArrayList<>();
        private final List<Integer> terminatorTargetIds = new ArrayList<>();
        private final List<Integer> terminatorTrueIds = new ArrayList<>();
        private final List<Integer> terminatorFalseIds = new ArrayList<>();
        private final List<Integer> opKinds = new ArrayList<>();
        private final List<Integer> opBlockIds = new ArrayList<>();
        private final List<Node> opSources = new ArrayList<>();
        private final List<Integer> opSymbolIds = new ArrayList<>();
        private final List<Expression> opExpressions = new ArrayList<>();
        private Table table;
        private Guards guards;

        Lowerer(Scope scope) {
            this.scope = scope;
            controlParentIds.add(-1);
            controlEdgeGuards.add(null);
        }

        Ir lower(Node root) {
            collectDeclaredInputs(root, scope);
            collectSymbols(root, scope);
            Table.Builder symbolsBuilder = Table.builder();
            for (SymbolSeed seed : symbolSeeds.values()) {
                symbolsBuilder.define(seed.name, seed.spec, seed.guardable, seed.tainted);
            }
            table = symbolsBuilder.build();
            guards = new Guards(table);

            int entryBlock = newBlock(0);
            int exitBlock = lowerChildren(root.children(), entryBlock, 0, scope);
            terminateIfMissing(exitBlock, TERM_RETURN, root, -1);

            for (int blockId = 0; blockId < blockControlIds.size(); blockId++) {
                if (terminatorKinds.get(blockId) == TERM_NONE) {
                    setTerminator(blockId, TERM_UNREACHABLE, root, -1, -1, -1);
                }
            }
            return new Ir(blockControlIds,
                    blockOpIds,
                    terminatorKinds,
                    terminatorSources,
                    terminatorTargetIds,
                    terminatorTrueIds,
                    terminatorFalseIds,
                    table,
                    opKinds,
                    opBlockIds,
                    opSources,
                    opSymbolIds,
                    opExpressions,
                    guards,
                    controlParentIds,
                    controlEdgeGuards);
        }

        void collectDeclaredInputs(Node root, Scope scope) {
            traverse(root, scope, (node, nodeScope) -> {
                Scope childScope = childScope(nodeScope, node);
                switch (node.kind()) {
                    case INPUT_BOOLEAN:
                        addDeclaredInput(childScope.key(), Spec.BOOLEAN, true, false);
                        break;
                    case INPUT_ENUM:
                    case INPUT_LIST:
                        addDeclaredInput(childScope.key(), inputSpec(node), optionValues(node).length != 0, false);
                        break;
                    case INPUT_TEXT:
                        addDeclaredInput(childScope.key(), Spec.OPEN_TEXT, false, true);
                        break;
                    default:
                        break;
                }
            });
        }

        void collectSymbols(Node root, Scope scope) {
            traverse(root, scope, (node, nodeScope) -> {
                Scope childScope = childScope(nodeScope, node);
                switch (node.kind()) {
                    case INPUT_BOOLEAN:
                        addSymbol(childScope.key(), Spec.BOOLEAN, true, false);
                        break;
                    case INPUT_ENUM:
                    case INPUT_LIST:
                        addSymbol(childScope.key(), inputSpec(node), optionValues(node).length != 0, false);
                        break;
                    case INPUT_TEXT:
                        addSymbol(childScope.key(), Spec.OPEN_TEXT, false, true);
                        break;
                    case PRESET_BOOLEAN:
                    case VARIABLE_BOOLEAN:
                        addSymbol(definitionId(nodeScope, node), Spec.BOOLEAN, true, false);
                        break;
                    case PRESET_ENUM:
                    case VARIABLE_ENUM:
                    case PRESET_TEXT:
                    case VARIABLE_TEXT:
                        addSymbol(nodeScope, node);
                        break;
                    case PRESET_LIST:
                    case VARIABLE_LIST: {
                        String key = definitionId(nodeScope, node);
                        SymbolSeed declared = declaredInputSymbols.get(key);
                        if (declared != null) {
                            addSymbol(key, declared.spec, declared.guardable, declared.tainted);
                        } else {
                            addSymbol(key, Spec.OPEN_TEXT, false, false);
                        }
                        break;
                    }
                    default:
                        break;
                }
            });
        }

        void traverse(Node root, Scope scope, BiConsumer<Node, Scope> visitor) {
            Map<Node, Scope> scopes = new IdentityHashMap<>();
            scopes.put(root, scope);
            for (Node node : root.traverse()) {
                Scope nodeScope = scopes.get(node);
                if (nodeScope == null) {
                    throw new IllegalStateException("Missing scope for node: " + node);
                }
                visitor.accept(node, nodeScope);
                Scope childScope = childScope(nodeScope, node);
                for (Node child : node.children()) {
                    scopes.put(child, childScope);
                }
            }
        }

        void addDeclaredInput(String name, Spec spec, boolean guardable, boolean tainted) {
            SymbolSeed seed = new SymbolSeed(name, spec, guardable, tainted);
            declaredInputSymbols.merge(name, seed, SymbolSeed::merge);
        }

        int lowerChildren(List<Node> nodes, int blockId, int controlId, Scope scope) {
            int current = blockId;
            for (Node node : nodes) {
                current = lower(node, current, controlId, scope);
            }
            return current;
        }

        int lower(Node node, int blockId, int controlId, Scope scope) {
            switch (node.kind()) {
                case INPUT_BOOLEAN:
                case INPUT_ENUM:
                case INPUT_LIST:
                case INPUT_TEXT:
                    return lowerInput(node, blockId, controlId, scope);
                case PRESET_BOOLEAN:
                case PRESET_ENUM:
                case PRESET_LIST:
                case PRESET_TEXT:
                case VARIABLE_BOOLEAN:
                case VARIABLE_ENUM:
                case VARIABLE_LIST:
                case VARIABLE_TEXT:
                    return lowerDefinition(node, blockId, controlId, scope);
                case CONDITION:
                    return lowerCondition(node, blockId, controlId, scope);
                case STEP:
                case FILE:
                case MODEL:
                    append(blockId, OP_EMIT, node, -1, null);
                    return lowerChildren(node.children(), blockId, controlId, scope);
                case INPUT_OPTION:
                    return lowerOption(node, blockId, controlId, scope);
                default:
                    return lowerChildren(node.children(), blockId, controlId, scope);
            }
        }

        int lowerInput(Node node, int blockId, int controlId, Scope scope) {
            Scope inputScope = scope.getOrCreate(node);
            int symbolId = requireSymbolId(table, inputScope.key());
            append(blockId, OP_DECLARE_INPUT, node, symbolId, null);
            switch (node.kind()) {
                case INPUT_BOOLEAN: {
                    Guard guard = directBooleanGuard(table, guards, inputScope.key());
                    return lowerGuardedChildren(node, blockId, controlId, guard, node.children(), inputScope);
                }
                case INPUT_ENUM:
                case INPUT_LIST:
                default:
                    return lowerChildren(node.children(), blockId, controlId, inputScope);
            }
        }

        int lowerOption(Node node, int blockId, int controlId, Scope scope) {
            Node input = node.ancestor(Kind::isInput).orElseThrow(() ->
                    new IllegalStateException("Option without input parent: " + node));
            int symbolId = table.findId(scope.key());
            if (symbolId < 0) {
                throw new IllegalStateException("Missing option symbol for scope: " + scope.key());
            }
            append(blockId, OP_DECLARE_OPTION, node, symbolId, null);
            Guard guard = optionGuard(input, node, scope);
            return lowerGuardedChildren(node, blockId, controlId, guard, node.children(), scope);
        }

        int lowerDefinition(Node node, int blockId, int controlId, Scope scope) {
            int symbolId = table.findId(definitionId(scope, node));
            if (symbolId >= 0) {
                Expression expr = new Expression(List.of(Token.of(node.value())), true);
                append(blockId, OP_DEFINE_VALUE, node, symbolId, expr);
            }
            return lowerChildren(node.children(), blockId, controlId, scope);
        }

        int lowerCondition(Node node, int blockId, int controlId, Scope scope) {
            Guard guard = guard(node.expression(), scope);
            return lowerGuardedChildren(node, blockId, controlId, guard, node.children(), scope);
        }

        Guard optionGuard(Node input, Node option, Scope scope) {
            return directOptionGuard(table, guards, scope.key(), input, option);
        }

        int lowerGuardedChildren(Node node, int blockId, int controlId, Guard guard, List<Node> children, Scope scope) {
            if (children.isEmpty()) {
                return blockId;
            }
            int trueId = nextControl(controlId, guard);
            int falseId = nextControl(controlId, guards.not(guard));
            int trueBlock = newBlock(trueId);
            int falseBlock = newBlock(falseId);
            int joinBlock = newBlock(controlId);
            terminate(blockId, node, trueBlock, falseBlock);
            int trueExit = lowerChildren(children, trueBlock, trueId, scope);
            terminateIfMissing(trueExit, TERM_GOTO, node, joinBlock);
            terminateIfMissing(falseBlock, TERM_GOTO, node, joinBlock);
            return joinBlock;
        }

        Guard guard(Expression expression, Scope scope) {
            return new ConditionLowerer(table, guards, scope).lower(expression);
        }

        void append(int blockId, int kind, Node source, int symbolId, Expression expression) {
            blockOps(blockId).add(opKinds.size());
            opKinds.add(kind);
            opBlockIds.add(blockId);
            opSources.add(source);
            opSymbolIds.add(symbolId);
            opExpressions.add(expression);
        }

        void addSymbol(String name, Spec spec, boolean guardable, boolean tainted) {
            SymbolSeed seed = new SymbolSeed(name, spec, guardable, tainted);
            symbolSeeds.merge(name, seed, SymbolSeed::merge);
        }

        void addSymbol(Scope scope, Node node) {
            SymbolSeed seed = symbolSeed(scope, node);
            addSymbol(seed.name, seed.spec, seed.guardable, seed.tainted);
        }

        SymbolSeed symbolSeed(Scope scope, Node node) {
            String key = definitionId(scope, node);
            SymbolSeed declared = declaredInputSymbols.get(key);
            if (declared != null) {
                return declared;
            }
            switch (node.kind()) {
                case PRESET_ENUM:
                case VARIABLE_ENUM:
                case PRESET_TEXT:
                case VARIABLE_TEXT:
                    return symbolSeed(key, node.value());
                case PRESET_LIST:
                case VARIABLE_LIST:
                default:
                    break;
            }
            return new SymbolSeed(key, Spec.OPEN_TEXT, false, false);
        }

        SymbolSeed symbolSeed(String key, Value<?> value) {
            String literal = literalScalar(value);
            if (literal == null) {
                return new SymbolSeed(key, Spec.OPEN_TEXT, false, true);
            }
            return new SymbolSeed(key, new Spec(Spec.Kind.FINITE_TEXT, literal), true, false);
        }

        String literalScalar(Value<?> value) {
            String literal = Value.scalarLiteral(value);
            if (literal == null || literal.contains("${")) {
                return null;
            }
            return literal;
        }

        void terminate(int blockId, Node source, int trueId, int falseId) {
            if (terminatorKinds.get(blockId) != TERM_NONE) {
                throw new IllegalStateException(String.format("Block %d already terminated", blockId));
            }
            setTerminator(blockId, Flow.TERM_BRANCH, source, -1, trueId, falseId);
        }

        void terminateIfMissing(int blockId, int kind, Node source, int targetId) {
            if (terminatorKinds.get(blockId) == TERM_NONE) {
                setTerminator(blockId, kind, source, targetId, -1, -1);
            }
        }

        int newBlock(int controlId) {
            blockControlIds.add(controlId);
            blockOpIds.add(new ArrayList<>());
            terminatorKinds.add(TERM_NONE);
            terminatorSources.add(null);
            terminatorTargetIds.add(-1);
            terminatorTrueIds.add(-1);
            terminatorFalseIds.add(-1);
            return blockControlIds.size() - 1;
        }

        int nextControl(int parentId, Guard edgeGuard) {
            int id = controlParentIds.size();
            controlParentIds.add(parentId);
            controlEdgeGuards.add(edgeGuard);
            return id;
        }

        List<Integer> blockOps(int blockId) {
            if (blockId < 0 || blockId >= blockOpIds.size()) {
                throw new IllegalStateException("Unknown lowered block id: " + blockId);
            }
            return blockOpIds.get(blockId);
        }

        void setTerminator(int blockId, int kind, Node source, int targetId, int trueId, int falseId) {
            blockOps(blockId);
            terminatorKinds.set(blockId, kind);
            terminatorSources.set(blockId, source);
            terminatorTargetIds.set(blockId, targetId);
            terminatorTrueIds.set(blockId, trueId);
            terminatorFalseIds.set(blockId, falseId);
        }

        static String[] optionValues(Node input) {
            Set<String> values = new TreeSet<>();
            for (Node child : input.children()) {
                Node option = child.unwrap();
                if (option.kind() == Kind.INPUT_OPTION) {
                    values.add(option.value().getString());
                }
            }
            return values.toArray(String[]::new);
        }

        static Spec inputSpec(Node node) {
            String[] values = optionValues(node);
            if (values.length == 0) {
                return Spec.OPEN_TEXT;
            }
            return node.kind() == Kind.INPUT_ENUM ? new Spec(Spec.Kind.CHOICE, values) : new Spec(Spec.Kind.MEMBERSHIP, values);
        }

        static String definitionId(Scope scope, Node node) {
            return scope.getOrCreate("~" + Context.Key.normalize(node.attribute("path").getString())).key();
        }
    }

    private static final class SymbolSeed {
        private final String name;
        private final Spec spec;
        private final boolean guardable;
        private final boolean tainted;

        SymbolSeed(String name, Spec spec, boolean guardable, boolean tainted) {
            this.name = name;
            this.spec = spec;
            this.guardable = guardable;
            this.tainted = tainted;
        }

        SymbolSeed merge(SymbolSeed other) {
            if (!name.equals(other.name)) {
                throw new IllegalArgumentException("Cannot merge different symbols");
            }
            if (spec.kind() == Spec.Kind.OPEN_TEXT || other.spec.kind() == Spec.Kind.OPEN_TEXT) {
                return new SymbolSeed(name, Spec.OPEN_TEXT, false, tainted || other.tainted);
            }
            if (spec.kind() == Spec.Kind.MEMBERSHIP || other.spec.kind() == Spec.Kind.MEMBERSHIP) {
                if (spec.kind() != Spec.Kind.MEMBERSHIP || other.spec.kind() != Spec.Kind.MEMBERSHIP) {
                    return new SymbolSeed(name, Spec.OPEN_TEXT, false, tainted || other.tainted);
                }
                Set<String> items = new TreeSet<>(Arrays.asList(spec.values()));
                items.addAll(Arrays.asList(other.spec.values()));
                return new SymbolSeed(name,
                        new Spec(Spec.Kind.MEMBERSHIP, items.toArray(String[]::new)),
                        guardable && other.guardable,
                        tainted || other.tainted);
            }
            if (spec.kind() == Spec.Kind.BOOLEAN || other.spec.kind() == Spec.Kind.BOOLEAN) {
                if (spec.kind() == Spec.Kind.BOOLEAN && other.spec.kind() == Spec.Kind.BOOLEAN) {
                    return new SymbolSeed(name, Spec.BOOLEAN, guardable && other.guardable, tainted || other.tainted);
                }
                return new SymbolSeed(name, Spec.OPEN_TEXT, false, tainted || other.tainted);
            }
            Set<String> choiceValues = new TreeSet<>(Arrays.asList(spec.values()));
            choiceValues.addAll(Arrays.asList(other.spec.values()));
            return new SymbolSeed(name,
                    spec.kind() == Spec.Kind.CHOICE || other.spec.kind() == Spec.Kind.CHOICE
                            ? new Spec(Spec.Kind.CHOICE, choiceValues.toArray(String[]::new))
                            : new Spec(Spec.Kind.FINITE_TEXT, choiceValues.toArray(String[]::new)),
                    guardable && other.guardable,
                    tainted || other.tainted);
        }
    }

    private static final class ConditionLowerer {
        private final Table table;
        private final Guards guards;
        private final Scope scope;

        ConditionLowerer(Table table, Guards guards, Scope scope) {
            this.table = table;
            this.guards = guards;
            this.scope = scope;
        }

        Guard lower(Expression expression) {
            if (expression == Expression.TRUE) {
                return Guard.TRUE;
            }
            if (expression == Expression.FALSE) {
                return Guard.FALSE;
            }
            int capacity = expression.tokens().size();
            Guard[] guardStack = new Guard[capacity];
            Symbol[] symStack = new Symbol[capacity];
            Value<?>[] literalStack = new Value<?>[capacity];
            int size = 0;
            for (Token token : expression.tokens()) {
                if (token.isVariable()) {
                    guardStack[size] = null;
                    symStack[size] = findSymbol(scope.key(token.variable()));
                    literalStack[size] = null;
                    size++;
                    continue;
                }
                if (token.isOperand()) {
                    guardStack[size] = null;
                    symStack[size] = null;
                    literalStack[size] = token.operand();
                    size++;
                    continue;
                }
                switch (token.operator()) {
                    case NOT: {
                        int index = size - 1;
                        Guard direct = asGuard(guardStack[index], symStack[index], literalStack[index]);
                        if (direct == null) {
                            return residual(expression);
                        }
                        guardStack[index] = guards.not(direct);
                        symStack[index] = null;
                        literalStack[index] = null;
                        break;
                    }
                    case AND:
                    case OR: {
                        int right = --size;
                        Guard rightGuard = asGuard(guardStack[right], symStack[right], literalStack[right]);
                        int left = --size;
                        Guard leftGuard = asGuard(guardStack[left], symStack[left], literalStack[left]);
                        if (leftGuard == null || rightGuard == null) {
                            return residual(expression);
                        }
                        if (token.operator() == Operator.OR) {
                            guardStack[left] = guards.or(leftGuard, rightGuard);
                        } else {
                            guardStack[left] = guards.and(leftGuard, rightGuard);
                        }
                        symStack[left] = null;
                        literalStack[left] = null;
                        size = left + 1;
                        break;
                    }
                    case EQUAL:
                    case NOT_EQUAL: {
                        int right = --size;
                        int left = --size;
                        Guard direct = compare(symStack[left], literalStack[left], symStack[right],
                                literalStack[right], token.operator() == Operator.EQUAL);
                        if (direct == null) {
                            return residual(expression);
                        }
                        guardStack[left] = direct;
                        symStack[left] = null;
                        literalStack[left] = null;
                        size = left + 1;
                        break;
                    }
                    case CONTAINS: {
                        int right = --size;
                        int left = --size;
                        Guard direct = contains(symStack[left], literalStack[left], symStack[right],
                                literalStack[right]);
                        if (direct == null) {
                            return residual(expression);
                        }
                        guardStack[left] = direct;
                        symStack[left] = null;
                        literalStack[left] = null;
                        size = left + 1;
                        break;
                    }
                    default:
                        return residual(expression);
                }
            }
            if (size != 1) {
                throw new IllegalStateException("Unexpected expression stack size: " + size);
            }
            Guard guard = asGuard(guardStack[0], symStack[0], literalStack[0]);
            return guard != null ? guard : residual(expression);
        }

        Guard compare(Symbol leftSymbol, Value<?> leftLiteral, Symbol rightSymbol, Value<?> rightLiteral, boolean equal) {
            Guard direct = compareGuard(leftSymbol, rightLiteral);
            if (direct == null) {
                direct = compareGuard(rightSymbol, leftLiteral);
            }
            return direct == null ? null : equal ? direct : guards.not(direct);
        }

        Guard compareGuard(Symbol sym, Value<?> literal) {
            if (sym == null || literal == null) {
                return null;
            }
            if (!sym.guardable() || sym.tainted()) {
                return null;
            }
            if (literal.type() == Value.Type.BOOLEAN) {
                if (sym.domain().kind() == Spec.Kind.BOOLEAN) {
                    String value = String.valueOf(literal.getBoolean());
                    return guards.eq(sym.id(), value);
                }
                return null;
            }
            if (!sym.domain().kind().isScalar()) {
                return null;
            }
            if (literal.type() == Value.Type.STRING) {
                String scalar = literal.getString();
                if (sym.domain().contains(scalar)) {
                    return guards.eq(sym.id(), scalar);
                }
                return null;
            }
            return null;
        }

        Guard contains(Symbol leftSymbol, Value<?> leftLiteral, Symbol rightSymbol, Value<?> rightLiteral) {
            Guard direct = containsGuard(leftSymbol, rightLiteral);
            if (direct == null) {
                direct = scalarAnyGuard(rightSymbol, leftLiteral);
            }
            return direct;
        }

        Guard containsGuard(Symbol sym, Value<?> literal) {
            if (sym == null || literal == null) {
                return null;
            }
            if (sym.domain().kind() != Spec.Kind.MEMBERSHIP || !sym.guardable() || sym.tainted()) {
                return null;
            }
            if (literal.type() == Value.Type.STRING) {
                return guards.contains(sym.id(), literal.getString());
            }
            if (literal.type() == Value.Type.LIST) {
                return guards.containsAll(sym.id(), new TreeSet<>(literal.getList()));
            }
            return null;
        }

        Guard scalarAnyGuard(Symbol sym, Value<?> literal) {
            if (sym == null || literal == null || literal.type() != Value.Type.LIST) {
                return null;
            }
            if (!sym.guardable() || sym.tainted()) {
                return null;
            }
            if (!sym.domain().kind().isScalar()) {
                return null;
            }
            Guard result = Guard.FALSE;
            for (String item : literal.getList()) {
                result = guards.or(result, guards.eq(sym.id(), item));
            }
            return result;
        }

        Guard asGuard(Guard guard, Symbol sym, Value<?> literal) {
            if (guard != null) {
                return guard;
            }
            if (sym != null && sym.guardable() && !sym.tainted() && sym.domain().kind() == Spec.Kind.BOOLEAN) {
                return guards.eq(sym.id(), "true");
            }
            if (literal != null && literal.type() == Value.Type.BOOLEAN) {
                return literal.getBoolean() ? Guard.TRUE : Guard.FALSE;
            }
            return null;
        }

        Guard residual(Expression expression) {
            return guards.residualGuard(expression);
        }

        Symbol findSymbol(String name) {
            int id = table.findId(name);
            return id < 0 ? null : table.symbol(id);
        }
    }

    private static final class Analyzer {
        private static final int EXACT_CASE_MAX = 16;
        private static final int COVERAGE_MAX = 16;

        private final Ir ir;
        private final Guards guards;
        private final Map<Integer, Map<FactState, Fact>> facts = new LinkedHashMap<>();
        private final Map<Map<Integer, FactState>, Map<Integer, Fact>> envs = new IdentityHashMap<>();
        private final Coverage[] coverages;
        private final Guard[] entryGuards;
        private final Guard[] beforeGuards;
        private final Map<Integer, FactState>[] entryFacts;
        private final Map<Integer, FactState>[] beforeFacts;
        private final Map<Integer, FactState>[] afterFacts;
        private final State[] entryStates;
        private final State[] beforeStates;
        private final State[] afterStates;

        @SuppressWarnings("unchecked")
        Analyzer(Ir ir) {
            this.ir = ir;
            this.guards = ir.guards;
            this.coverages = new Coverage[ir.blockControlIds.length];
            this.entryGuards = new Guard[ir.blockControlIds.length];
            this.beforeGuards = new Guard[ir.opKinds.length];
            this.entryFacts = new Map[ir.blockControlIds.length];
            this.beforeFacts = new Map[ir.opKinds.length];
            this.afterFacts = new Map[ir.opKinds.length];
            this.entryStates = new State[ir.blockControlIds.length];
            this.beforeStates = new State[ir.opKinds.length];
            this.afterStates = new State[ir.opKinds.length];
            Arrays.fill(entryGuards, Guard.FALSE);
            Arrays.fill(beforeGuards, Guard.FALSE);
        }

        void analyze() {
            analyzeControl();
            analyzeFacts();
            materializeStates(entryGuards, entryFacts, entryStates);
            materializeStates(beforeGuards, beforeFacts, beforeStates);
            materializeStates(beforeGuards, afterFacts, afterStates);
        }

        void analyzeControl() {
            int blockCount = ir.blockControlIds.length;
            Deque<Integer> stack = new ArrayDeque<>();
            boolean[] reachable = new boolean[blockCount];
            reachable[0] = true;
            stack.add(0);
            while (!stack.isEmpty()) {
                int blockId = stack.removeFirst();
                switch (ir.terminatorKinds[blockId]) {
                    case TERM_GOTO:
                        enqueueControl(ir.terminatorTargetIds[blockId], reachable, stack);
                        break;
                    case TERM_BRANCH:
                        enqueueControl(ir.terminatorTrueIds[blockId], reachable, stack);
                        enqueueControl(ir.terminatorFalseIds[blockId], reachable, stack);
                        break;
                    case TERM_RETURN:
                    case TERM_UNREACHABLE:
                    default:
                        break;
                }
            }

            // keep the forward control pass reachability-only
            // lowered blocks carry their structured path context
            computeControlEntryGuards(reachable);
            for (int blockId = 0; blockId < blockCount; blockId++) {
                if (!reachable[blockId]) {
                    continue;
                }
                Guard entry = entryGuards[blockId];
                for (int opId : ir.blockOpIds[blockId]) {
                    beforeGuards[opId] = entry;
                }
            }
        }

        void enqueueControl(int blockId, boolean[] reachable, Deque<Integer> stack) {
            if (!reachable[blockId]) {
                reachable[blockId] = true;
                stack.addLast(blockId);
            }
        }

        void computeControlEntryGuards(boolean[] reachable) {
            Guard[] controlGuards = new Guard[ir.controlParentIds.length];
            controlGuards[0] = Guard.TRUE;
            for (int blockId = 0; blockId < reachable.length; blockId++) {
                if (!reachable[blockId]) {
                    continue;
                }
                int controlId = ir.blockControlIds[blockId];
                if (controlId < 0) {
                    throw new IllegalStateException("Missing structured control path for block " + blockId);
                }
                entryGuards[blockId] = computeControlGuard(controlId, controlGuards);
            }
        }

        Guard computeControlGuard(int controlId, Guard[] controlGuards) {
            Guard cached = controlGuards[controlId];
            if (cached != null) {
                return cached;
            }
            int parentId = ir.controlParentIds[controlId];
            if (parentId < 0) {
                controlGuards[controlId] = Guard.TRUE;
                return Guard.TRUE;
            }
            Guard materialized = guards.and(computeControlGuard(parentId, controlGuards), ir.controlEdgeGuards[controlId]);
            controlGuards[controlId] = materialized;
            return materialized;
        }

        void analyzeFacts() {
            Deque<Integer> stack = new ArrayDeque<>();
            entryFacts[0] = Map.of();
            stack.add(0);
            while (!stack.isEmpty()) {
                int blockId = stack.removeFirst();
                if (entryGuards[blockId].equals(Guard.FALSE)) {
                    continue;
                }
                Map<Integer, FactState> state = entryFacts[blockId];
                if (state == null) {
                    continue;
                }
                Map<Integer, FactState> current = state;
                for (int opId : ir.blockOpIds[blockId]) {
                    Guard currentGuard = beforeGuards[opId];
                    beforeFacts[opId] = current;
                    current = transfer(opId, currentGuard, current);
                    afterFacts[opId] = current;
                }
                propagateFacts(blockId, current, stack);
            }
        }

        Map<Integer, FactState> transfer(int opId, Guard currentGuard, Map<Integer, FactState> state) {
            switch (ir.opKinds[opId]) {
                case OP_DECLARE_INPUT: {
                    int id = ir.opSymbolIds[opId];
                    FactState declared = new FactState(
                            coverage(ir.opBlockIds[opId]),
                            LatticeValue.top(ir.table.symbol(id).domain()));
                    FactState existing = state.get(id);
                    return define(state, id, existing == null ? declared : mergeFact(id, existing, declared));
                }
                case OP_DEFINE_VALUE: {
                    int id = ir.opSymbolIds[opId];
                    Symbol sym = ir.table.symbol(id);
                    FactState fact = evaluateFact(opId, currentGuard, sym, state);
                    return define(state, id, fact);
                }
                case OP_EMIT:
                default:
                    return state;
            }
        }

        void propagateFacts(int blockId, Map<Integer, FactState> current, Deque<Integer> stack) {
            switch (ir.terminatorKinds[blockId]) {
                case TERM_GOTO:
                    enqueueFacts(ir.terminatorTargetIds[blockId], current, stack);
                    break;
                case TERM_BRANCH:
                    enqueueFacts(ir.terminatorTrueIds[blockId], current, stack);
                    enqueueFacts(ir.terminatorFalseIds[blockId], current, stack);
                    break;
                case TERM_RETURN:
                case TERM_UNREACHABLE:
                default:
                    break;
            }
        }

        void enqueueFacts(int blockId, Map<Integer, FactState> incoming, Deque<Integer> stack) {
            if (!entryGuards[blockId].equals(Guard.FALSE)) {
                Map<Integer, FactState> current = entryFacts[blockId];
                Map<Integer, FactState> merged = mergeEnv(current, incoming);
                if (merged != current) {
                    entryFacts[blockId] = merged;
                    stack.addLast(blockId);
                }
            }
        }

        Map<Integer, FactState> mergeEnv(Map<Integer, FactState> current, Map<Integer, FactState> incoming) {
            if (current == null) {
                return incoming;
            }
            if (incoming == null || current == incoming || current.equals(incoming)) {
                return current;
            }
            Map<Integer, FactState> next = null;
            for (Map.Entry<Integer, FactState> entry : incoming.entrySet()) {
                int id = entry.getKey();
                FactState left = current.get(id);
                FactState right = entry.getValue();
                FactState merged;
                if (left == null) {
                    merged = right;
                } else if (left == right || left.equals(right)) {
                    merged = left;
                } else {
                    merged = mergeFact(id, left, right);
                }
                if (merged != left) {
                    if (next == null) {
                        next = new LinkedHashMap<>(current);
                    }
                    next.put(id, merged);
                }
            }
            return next == null ? current : next;
        }

        Map<Integer, FactState> define(Map<Integer, FactState> state, int id, FactState fact) {
            FactState existing = state.get(id);
            if (Objects.equals(existing, fact)) {
                return state;
            }
            Map<Integer, FactState> env = new LinkedHashMap<>(state);
            env.put(id, fact);
            return env;
        }

        FactState evaluateFact(int opId, Guard currentGuard, Symbol sym, Map<Integer, FactState> state) {
            Expression expression = ir.opExpressions[opId];
            LatticeValue value = evaluateValue(expression, sym, state);
            Value<?> exactValue = exactValue(expression, currentGuard, state);
            return exactValue == null
                    ? new FactState(coverage(ir.opBlockIds[opId]), value)
                    : FactState.exact(ir.opBlockIds[opId], value, exactValue);
        }

        LatticeValue evaluateValue(Expression expression, Symbol sym, Map<Integer, FactState> state) {
            List<Token> tokens = expression.tokens();
            if (tokens.size() == 1) {
                Token token = tokens.get(0);
                if (token.isOperand()) {
                    return literalValue(sym.domain(), token.operand());
                }
                if (token.isVariable()) {
                    int id = ir.table.findId(token.variable());
                    if (id >= 0) {
                        FactState fact = state.get(id);
                        if (fact != null) {
                            return fact.value;
                        }
                    }
                }
            }
            return LatticeValue.top(sym.domain());
        }

        Value<?> exactValue(Expression expression, Guard currentGuard, Map<Integer, FactState> state) {
            List<Token> tokens = expression.tokens();
            if (tokens.size() != 1) {
                return null;
            }
            Token token = tokens.get(0);
            if (token.isOperand()) {
                return token.operand();
            }
            if (token.isVariable()) {
                int id = ir.table.findId(token.variable());
                if (id < 0) {
                    return null;
                }
                Symbol sym = ir.table.symbol(id);
                FactState fact = state.get(id);
                if (fact == null) {
                    return null;
                }
                Value<?> exactFromValue = exactFromValue(sym.domain(), fact, currentGuard);
                if (exactFromValue != null) {
                    return exactFromValue;
                }
                if (fact.exactCases.isEmpty() || !implies(currentGuard, fact.exactCoverage)) {
                    return null;
                }
                Value<?> value = null;
                for (ExactCaseState exactCase : fact.exactCases) {
                    if (!overlaps(currentGuard, exactCase.coverage)) {
                        continue;
                    }
                    if (value == null) {
                        value = exactCase.value;
                    } else if (!Value.isEqual(value, exactCase.value)) {
                        return null;
                    }
                }
                return value;
            }
            return null;
        }

        Value<?> exactFromValue(Spec spec, FactState fact, Guard currentGuard) {
            if (!implies(currentGuard, fact.definedUnder)) {
                return null;
            }
            if (fact.value.kind() == LatticeValue.Kind.FINITE_SCALAR) {
                String scalar = fact.value.singletonScalar(spec);
                if (scalar == null) {
                    return null;
                }
                return spec.kind() == Spec.Kind.BOOLEAN ? Value.of(Boolean.parseBoolean(scalar)) : Value.of(scalar);
            }
            if (fact.value.kind() == LatticeValue.Kind.OPEN_TEXT && fact.value.sample() != null) {
                return Value.of(fact.value.sample());
            }
            return null;
        }

        FactState mergeFact(int id, FactState left, FactState right) {
            Coverage definedUnder = Coverage.merge(left.definedUnder, right.definedUnder, -1);
            LatticeValue value = LatticeValue.join(left.value, right.value);
            if (left.exactCases.isEmpty()) {
                return right.exactCases.isEmpty()
                        ? new FactState(definedUnder, value)
                        : new FactState(definedUnder, value, right.exactCoverage, right.exactCases);
            }
            if (right.exactCases.isEmpty()) {
                return new FactState(definedUnder, value, left.exactCoverage, left.exactCases);
            }
            Coverage coverage = Coverage.merge(left.exactCoverage, right.exactCoverage, COVERAGE_MAX);
            if (coverage == null) {
                return new FactState(definedUnder, value);
            }
            Symbol sym = ir.table.symbol(id);
            List<ExactCaseState> merged = new ArrayList<>();
            for (ExactCaseState exactCase : left.exactCases) {
                if (!mergeExactCase(sym, merged, exactCase)) {
                    return new FactState(definedUnder, value);
                }
            }
            for (ExactCaseState exactCase : right.exactCases) {
                if (!mergeExactCase(sym, merged, exactCase)) {
                    return new FactState(definedUnder, value);
                }
            }
            return new FactState(definedUnder, value, coverage, merged);
        }

        boolean mergeExactCase(Symbol sym, List<ExactCaseState> merged, ExactCaseState next) {
            for (int i = 0; i < merged.size(); i++) {
                ExactCaseState current = merged.get(i);
                if (Domain.sameExactValue(sym.domain(), current.value, next.value)) {
                    Coverage coverage = Coverage.merge(current.coverage, next.coverage, COVERAGE_MAX);
                    if (coverage == null) {
                        return false;
                    }
                    merged.set(i, new ExactCaseState(current.value, coverage));
                    return true;
                }
            }
            if (merged.size() >= EXACT_CASE_MAX) {
                return false;
            }
            merged.add(next);
            return true;
        }

        void materializeStates(Guard[] paths, Map<Integer, FactState>[] envs, State[] states) {
            for (int i = 0; i < paths.length; i++) {
                states[i] = new State(paths[i], materializeEnv(envs[i]));
            }
        }

        Map<Integer, Fact> materializeEnv(Map<Integer, FactState> env) {
            if (env == null || env.isEmpty()) {
                return Map.of();
            }
            Map<Integer, Fact> cached = envs.get(env);
            if (cached != null) {
                return cached;
            }
            Map<Integer, Fact> materialized = new LinkedHashMap<>();
            for (Map.Entry<Integer, FactState> entry : env.entrySet()) {
                materialized.put(entry.getKey(), materializeFact(entry.getKey(), entry.getValue()));
            }
            envs.put(env, materialized);
            return materialized;
        }

        private Fact materializeFact(int id, FactState fact) {
            Map<FactState, Fact> cacheBySymbol = facts.computeIfAbsent(id, unused -> new IdentityHashMap<>());
            Fact cached = cacheBySymbol.get(fact);
            if (cached != null) {
                return cached;
            }
            Symbol sym = ir.table.symbol(id);
            List<GuardedValue> guardedValues = new ArrayList<>(fact.exactCases.size());
            for (ExactCaseState exactCase : fact.exactCases) {
                Guard guard = materializeGuard(exactCase.coverage);
                Spec domain = sym.domain();
                guardedValues.add(new GuardedValue(exactCase.value, guard, domain.mask(exactCase.value)));
            }
            Fact materialized = new Fact(materializeGuard(fact.definedUnder), fact.value, guardedValues);
            cacheBySymbol.put(fact, materialized);
            return materialized;
        }

        boolean implies(Guard left, Coverage right) {
            return right.blockIds.length != 0 && guards.implies(left, materializeGuard(right));
        }

        boolean overlaps(Guard left, Coverage right) {
            return right.blockIds.length != 0 && !guards.and(left, materializeGuard(right)).equals(Guard.FALSE);
        }

        Coverage coverage(int blockId) {
            Coverage cached = coverages[blockId];
            if (cached != null) {
                return cached;
            }
            Coverage created = new Coverage(blockId);
            coverages[blockId] = created;
            return created;
        }

        Guard materializeGuard(Coverage coverage) {
            Guard cached = coverage.materializedGuard;
            if (cached != null) {
                return cached;
            }
            if (coverage.blockIds.length == 0) {
                return Guard.FALSE;
            }
            Guard materialized = entryGuards[coverage.blockIds[0]];
            for (int i = 1; i < coverage.blockIds.length; i++) {
                materialized = guards.or(materialized, entryGuards[coverage.blockIds[i]]);
            }
            coverage.materializedGuard = materialized;
            return materialized;
        }

        private static final class FactState {
            private final Coverage definedUnder;
            private final LatticeValue value;
            private final Coverage exactCoverage;
            private final List<ExactCaseState> exactCases;

            FactState(Coverage definedUnder, LatticeValue value) {
                this(definedUnder, value, Coverage.EMPTY, List.of());
            }

            FactState(Coverage definedUnder,
                      LatticeValue value,
                      Coverage exactCoverage,
                      List<ExactCaseState> exactCases) {
                this.definedUnder = definedUnder;
                this.value = value;
                this.exactCoverage = exactCoverage;
                this.exactCases = exactCases;
            }

            static FactState exact(int blockId, LatticeValue value, Value<?> exactValue) {
                Coverage definedUnder = new Coverage(blockId);
                if (exactValue == null || !exactValue.isPresent()) {
                    return new FactState(definedUnder, value);
                }
                List<ExactCaseState> exactCases = List.of(new ExactCaseState(exactValue, definedUnder));
                return new FactState(definedUnder, value, definedUnder, exactCases);
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (!(o instanceof FactState)) {
                    return false;
                }
                FactState other = (FactState) o;
                return definedUnder.equals(other.definedUnder)
                       && value.equals(other.value)
                       && exactCoverage.equals(other.exactCoverage)
                       && exactCases.equals(other.exactCases);
            }
        }

        private static final class ExactCaseState {
            private final Value<?> value;
            private final Coverage coverage;

            ExactCaseState(Value<?> value, Coverage coverage) {
                this.value = value;
                this.coverage = coverage;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (!(o instanceof ExactCaseState)) {
                    return false;
                }
                ExactCaseState other = (ExactCaseState) o;
                return coverage.equals(other.coverage) && Value.isEqual(value, other.value);
            }
        }

        private static final class Coverage {
            private static final Coverage EMPTY = new Coverage();

            private final int[] blockIds;
            private Guard materializedGuard;

            Coverage(int... blockIds) {
                this.blockIds = blockIds;
            }

            static Coverage merge(Coverage left, Coverage right, int maxEntries) {
                if (left.blockIds.length == 0) {
                    return right;
                }
                if (right.blockIds.length == 0 || left == right || left.equals(right)) {
                    return left;
                }
                int[] merged = new int[left.blockIds.length + right.blockIds.length];
                int leftIndex = 0;
                int rightIndex = 0;
                int size = 0;
                while (leftIndex < left.blockIds.length || rightIndex < right.blockIds.length) {
                    int next;
                    if (rightIndex >= right.blockIds.length) {
                        next = left.blockIds[leftIndex++];
                    } else if (leftIndex >= left.blockIds.length) {
                        next = right.blockIds[rightIndex++];
                    } else {
                        int leftId = left.blockIds[leftIndex];
                        int rightId = right.blockIds[rightIndex];
                        if (leftId == rightId) {
                            next = leftId;
                            leftIndex++;
                            rightIndex++;
                        } else if (leftId < rightId) {
                            next = leftId;
                            leftIndex++;
                        } else {
                            next = rightId;
                            rightIndex++;
                        }
                    }
                    if (maxEntries > 0 && size == maxEntries) {
                        return null;
                    }
                    merged[size++] = next;
                }
                if (matches(merged, size, left.blockIds)) {
                    return left;
                }
                if (matches(merged, size, right.blockIds)) {
                    return right;
                }
                return new Coverage(size == merged.length ? merged : Arrays.copyOf(merged, size));
            }

            static boolean matches(int[] merged, int size, int[] other) {
                if (size != other.length) {
                    return false;
                }
                for (int i = 0; i < size; i++) {
                    if (merged[i] != other[i]) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (!(o instanceof Coverage)) {
                    return false;
                }
                Coverage other = (Coverage) o;
                return Arrays.equals(blockIds, other.blockIds);
            }
        }

        static LatticeValue literalValue(Spec spec, Value<?> literal) {
            switch (literal.type()) {
                case BOOLEAN: {
                    String scalar = String.valueOf(literal.getBoolean());
                    if (spec.kind().isScalar() && spec.contains(scalar)) {
                        return LatticeValue.finiteScalar(spec.mask(scalar));
                    }
                    return spec.kind() == Spec.Kind.OPEN_TEXT ? LatticeValue.openText(scalar) : LatticeValue.top(spec);
                }
                case STRING:
                    if (spec.kind().isScalar() && spec.contains(literal.getString())) {
                        return LatticeValue.finiteScalar(spec.mask(literal.getString()));
                    }
                    return LatticeValue.openText(literal.getString());
                case LIST:
                    if (spec.kind() == Spec.Kind.MEMBERSHIP) {
                        long listMask = spec.mask(new TreeSet<>(literal.getList()));
                        return LatticeValue.membership(listMask, listMask);
                    }
                    return LatticeValue.top(spec);
                default:
                    return LatticeValue.top(spec);
            }
        }
    }
}
