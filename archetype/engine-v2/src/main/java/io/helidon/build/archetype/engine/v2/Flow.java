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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import io.helidon.build.archetype.engine.v2.Context.Scope;
import io.helidon.build.archetype.engine.v2.Domain.Guard;
import io.helidon.build.archetype.engine.v2.Domain.GuardedValue;
import io.helidon.build.archetype.engine.v2.Domain.Guards;
import io.helidon.build.archetype.engine.v2.Domain.LatticeValue;
import io.helidon.build.archetype.engine.v2.Domain.Spec;
import io.helidon.build.archetype.engine.v2.Domain.Symbol;
import io.helidon.build.archetype.engine.v2.Domain.Fact;
import io.helidon.build.archetype.engine.v2.Expression.Operator;
import io.helidon.build.archetype.engine.v2.Expression.Token;
import io.helidon.build.archetype.engine.v2.Ir.Block;
import io.helidon.build.archetype.engine.v2.Ir.Op;
import io.helidon.build.archetype.engine.v2.Ir.Terminator;
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
        ir = Ir.lower(scope, root);
        Analyzer analyzer = new Analyzer(ir);
        analyzer.analyze();
        Projector projector = new Projector(ir, analyzer, root, scope);
        projector.project();
        rootEntry = analyzer.entryState(0);
        nodeFacts = projector.nodeFacts;
        symbolInfos = projector.symbolInfos;
    }

    Guards guards() {
        return ir.guards();
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
        int id = ir.table().findId(name);
        return id < 0 ? null : symbolInfos[id];
    }

    Symbol symbol(int id) {
        return ir.table().symbol(id);
    }

    Expression expression(Guard guard) {
        return expression(guard, scope);
    }

    Expression expression(Guard guard, Scope scope) {
        return ir.guards().expression(guard, scope).reduce();
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
        if (fact == null || !ir.guards().implies(required, fact.guard())) {
            return Value.empty();
        }
        SymbolInfo info = symbol(key);
        if (info == null && !key.startsWith("~")) {
            info = symbol("~" + key);
        }
        Value.Type type;
        if (info == null) {
            type = Value.Type.EMPTY;
        } else {
            switch (info.sym.domain().kind()) {
                case BOOLEAN:
                    type = Value.Type.BOOLEAN;
                    break;
                case CHOICE:
                case FINITE_TEXT:
                case OPEN_TEXT:
                    type = Value.Type.STRING;
                    break;
                case MEMBERSHIP:
                    type = Value.Type.LIST;
                    break;
                default:
                    type = Value.Type.EMPTY;
                    break;
            }
        }
        Value<?> exact = value(fact, required, type);
        if (exact != null) {
            return exact;
        }
        if (info == null) {
            return Value.empty();
        }
        return singletonValue(fact.lattice(), info.sym.domain(), type);
    }

    Value<?> declaredValue(Node node) {
        return declaredValue(node, key(node));
    }

    Guard residualGuard(Expression expression) {
        return ir.guards().residualGuard(expression);
    }

    boolean isFalse(Guard guard) {
        return guard == null || guard.isFalse();
    }

    Guard and(Guard left, Guard right) {
        if (left == null || right == null) {
            return null;
        }
        return ir.guards().and(left, right);
    }

    Guard or(Guard left, Guard right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return ir.guards().or(left, right);
    }

    Guard minus(Guard left, Guard right) {
        if (left == null) {
            return null;
        }
        if (right == null) {
            return left;
        }
        return ir.guards().minus(left, right);
    }

    boolean contains(Guard left, Guard right) {
        return left != null && right != null && ir.guards().implies(right, left);
    }

    boolean equivalent(Guard left, Guard right) {
        return left != null && right != null && ir.guards().equivalent(left, right);
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
        int id = ir.table().findId(key);
        if (id < 0 && !key.startsWith("~")) {
            id = ir.table().findId("~" + key);
        }
        return id < 0 ? null : before.env.get(id);
    }

    private Value<?> value(Fact fact, Guard required, Value.Type type) {
        if (!fact.values().isEmpty()) {
            Value<?> exact = null;
            Guard coverage = Guard.FALSE;
            for (GuardedValue value : fact.values()) {
                Guard overlap = ir.guards().and(required, value.guard());
                if (overlap.equals(Guard.FALSE)) {
                    continue;
                }
                Value<?> candidate = coerceValue(value.value(), type);
                if (exact == null) {
                    exact = candidate;
                } else if (!Value.isEqual(exact, candidate)) {
                    return null;
                }
                coverage = ir.guards().or(coverage, overlap);
            }
            if (exact != null && ir.guards().implies(required, coverage)) {
                return exact;
            }
        }
        return null;
    }

    private static Scope childScope(Scope scope, Node node) {
        return node.kind().isInput() ? scope.getOrCreate(node) : scope;
    }

    private static Value<?> coerceValue(Value<?> value, Value.Type type) {
        try {
            Value<?> coerced = Value.typed(value, type);
            return coerced.isPresent() ? coerced : value;
        } catch (RuntimeException ex) {
            return value;
        }
    }

    private static Value<?> singletonValue(LatticeValue lattice, Spec spec, Value.Type type) {
        return coerceValue(lattice.value(spec), type);
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
        static final State EMPTY = new State(Guard.FALSE, Map.of());

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
            List<Symbol> symbols = ir.table().symbols();
            this.symbolInfos = new SymbolInfo[symbols.size()];
            for (int i = 0; i < symbols.size(); i++) {
                symbolInfos[i] = new SymbolInfo(symbols.get(i));
            }
        }

        void project() {
            indexAnchors();
            scanSymbols();
            projectNode(node, scope, analyzer.entryState(0));
        }

        void indexAnchors() {
            Op[] irOps = ir.ops();
            for (int opId = 0; opId < irOps.length; opId++) {
                ops.computeIfAbsent(irOps[opId].source(), key -> new ArrayList<>()).add(opId);
            }
            Block[] blocks = ir.blocks();
            for (Block block : blocks) {
                Terminator term = block.term();
                if (term.kind() == Terminator.Kind.BRANCH) {
                    int id = term.falseId();
                    if (blocks[id].term().kind() == Terminator.Kind.GOTO) {
                        id = blocks[id].term().targetId();
                    }
                    Node source = term.source();
                    branchTrueIds.put(source, term.trueId());
                    branchJoinIds.put(source, id);
                }
            }
        }

        void scanSymbols() {
            Op[] ops = ir.ops();
            Guards guards = ir.guards();
            for (int id = 0; id < ops.length; id++) {
                State before = analyzer.beforeState(id);
                Op op = ops[id];
                switch (op.kind()) {
                    case DECLARE_INPUT: {
                        SymbolInfo info = symbolInfos[op.symbolId()];
                        info.addDefinition(before.path, guards);
                        if (info.sym.domain().kind() == Spec.Kind.BOOLEAN) {
                            info.addAvailability("true", before.path, guards);
                            info.addAvailability("false", before.path, guards);
                        }
                        break;
                    }
                    case DECLARE_OPTION: {
                        SymbolInfo info = symbolInfos[op.symbolId()];
                        info.addAvailability(op.source().value().getString(), before.path, guards);
                        break;
                    }
                    case DEFINE_VALUE: {
                        SymbolInfo info = symbolInfos[op.symbolId()];
                        if (before.path.equals(Guard.FALSE)) {
                            break;
                        }
                        Map<Integer, Fact> env = analyzer.afterState(id).env;
                        Fact fact = env.get(op.symbolId());
                        if (fact == null) {
                            throw new IllegalStateException("Missing flow fact after op for key: " + id);
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
            if (!fact.values().isEmpty()) {
                for (GuardedValue value : fact.values()) {
                    recordExactAvailability(info, value);
                }
                return;
            }
            if (fact.lattice().kind() == LatticeValue.Kind.FINITE_SCALAR) {
                switch (info.sym.domain().kind()) {
                    case BOOLEAN:
                    case CHOICE:
                    case FINITE_TEXT:
                        if (fact.lattice().scalarMask() != info.sym.domain().mask()) {
                            for (String value : fact.lattice().scalarValues(info.sym.domain())) {
                                info.addAvailability(value, fact.guard(), ir.guards());
                            }
                        }
                        break;
                    default:
                }
            }
        }

        void recordExactAvailability(SymbolInfo info, GuardedValue value) {
            switch (info.sym.domain().kind()) {
                case BOOLEAN:
                case CHOICE:
                case FINITE_TEXT:
                case MEMBERSHIP:
                    long mask = value.mask();
                    while (mask != 0L) {
                        int ordinal = Long.numberOfTrailingZeros(mask);
                        info.addAvailability(info.sym.value(ordinal), value.guard(), ir.guards());
                        mask &= mask - 1L;
                    }
                    break;
                default:
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
                before = constrainPath(analyzer.beforeState(opIds.get(0)), current.path);
                afterOps = constrainPath(analyzer.afterState(opIds.get(opIds.size() - 1)), current.path);
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
                branchEntry = constrainPath(analyzer.entryState(branchTrueId), current.path);
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
                return constrainPath(analyzer.entryState(branchJoinId), current.path);
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
                    return scope.definitionKey(node.attribute("path").getString());
                default:
                    return scope.key();
            }
        }

        State constrainPath(State state, Guard required) {
            if (state.path.equals(required)) {
                return state;
            }
            Guard constrained = ir.guards().and(state.path, required);
            return constrained.equals(state.path) ? state : new State(constrained, state.env);
        }

        Guard activeGuard(Node node, Scope nodeScope, State afterOps) {
            Guard control = controlGuard(node, nodeScope, afterOps);
            return control == null ? afterOps.path : ir.guards().and(afterOps.path, control);
        }

        Guard controlGuard(Node node, Scope nodeScope, State state) {
            switch (node.kind()) {
                case CONDITION:
                    return translatedCondition(node.expression(), nodeScope, state);
                case INPUT_BOOLEAN:
                    return ir.directBooleanGuard(nodeScope.key());
                case INPUT_OPTION:
                    Node input = node.ancestor(Kind::isInput).orElse(null);
                    if (input == null) {
                        return null;
                    }
                    switch (input.kind()) {
                        case INPUT_ENUM:
                        case INPUT_LIST:
                            return ir.directOptionGuard(nodeScope.key(), input, node);
                        default:
                            return null;
                    }
                default:
                    return null;
            }
        }

        Guard translatedCondition(Expression expression, Scope scope, State state) {
            Guard translated = translatedCondition0(expression, scope, state);
            if (translated == null) {
                translated = ir.lowerCondition(scope, expression);
            }
            return translated;
        }

        Guard translatedCondition0(Expression expression, Scope scope, State state) {
            int capacity = expression.tokens().size();
            Guards guards = ir.guards();
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
                Operator op = token.operator();
                switch (op) {
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
                    case AND:
                    case OR: {
                        int right = --size;
                        Guard rightGuard = booleanGuard(guardStack[right], refStack[right], literalStack[right], state);
                        int left = --size;
                        Guard leftGuard = booleanGuard(guardStack[left], refStack[left], literalStack[left], state);
                        if (leftGuard == null || rightGuard == null) {
                            return null;
                        }
                        guardStack[left] = op == Operator.AND
                                ? guards.and(leftGuard, rightGuard)
                                : guards.or(leftGuard, rightGuard);
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
                        guardStack[left] = op == Operator.EQUAL ? equality : guards.not(equality);
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
                if (required != null) {
                    return new TreeSet<>(leftLiteral.getList()).containsAll(required)
                            ? Guard.TRUE
                            : Guard.FALSE;
                }
            }
            return null;
        }

        Guard translatedEquality(String key, Value<?> value, State state) {
            Fact fact = factFor(state, key);
            SymbolInfo info = symbolInfo(key);
            Guard bound = fact == null || info == null ? Guard.FALSE : fact.match(info.sym, value, ir.guards());
            Guard direct = directEquality(key, value);
            return combine(key, fact, bound, direct);
        }

        Guard translatedScalarAny(String key, Set<String> values, State state) {
            Fact fact = factFor(state, key);
            SymbolInfo info = symbolInfo(key);
            Guard bound = fact == null || info == null ? Guard.FALSE : fact.scalarAny(info.sym, values, ir.guards());
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
            Guard bound = fact == null || info == null ? Guard.FALSE : fact.listContains(info.sym, required, ir.guards());
            Guard direct = directListContains(symbolInfo(key), required);
            return combine(key, fact, bound, direct);
        }

        Guard combine(String key, Fact fact, Guard bound, Guard direct) {
            SymbolInfo info = symbolInfo(key);
            Symbol symbol = info == null ? null : info.sym;
            Guard defined = definitionFor(key, fact);
            Guards guards = ir.guards();
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
            int id = ir.table().findId(key);
            return id < 0 ? null : state.env.get(id);
        }

        Guard definitionFor(String key, Fact fact) {
            if (fact != null) {
                return fact.guard();
            }
            SymbolInfo info = symbolInfo(key);
            return info == null ? null : info.definition;
        }

        SymbolInfo symbolInfo(String key) {
            int id = ir.table().findId(key);
            return id < 0 ? null : symbolInfos[id];
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
            return symbol.domain().scalar() ? info : null;
        }

        Guard directEquality(String key, Value<?> value) {
            String scalar = Value.scalarLiteral(value);
            if (scalar != null) {
                SymbolInfo info = scalarSymbolInfo(key);
                if (info != null) {
                    return info.sym.domain().contains(scalar)
                            ? available(info, scalar, ir.guards().eq(info.sym.id(), scalar))
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
                Guards guards = ir.guards();
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
                    Guards guards = ir.guards();
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
            return availability == null ? Guard.FALSE : ir.guards().and(availability, direct);
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
}
