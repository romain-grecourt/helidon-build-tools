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
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import io.helidon.build.archetype.engine.v2.Context.Scope;
import io.helidon.build.archetype.engine.v2.Domain.Fact;
import io.helidon.build.archetype.engine.v2.Domain.Guard;
import io.helidon.build.archetype.engine.v2.Domain.GuardedValue;
import io.helidon.build.archetype.engine.v2.Domain.Guards;
import io.helidon.build.archetype.engine.v2.Domain.LatticeValue;
import io.helidon.build.archetype.engine.v2.Domain.Spec;
import io.helidon.build.archetype.engine.v2.Domain.Symbol;
import io.helidon.build.archetype.engine.v2.Expression.Operator;
import io.helidon.build.archetype.engine.v2.Expression.Token;
import io.helidon.build.archetype.engine.v2.Ir.Block;
import io.helidon.build.archetype.engine.v2.Ir.Op;
import io.helidon.build.archetype.engine.v2.Ir.Terminator;
import io.helidon.build.archetype.engine.v2.IrAnalyzer.IrState;
import io.helidon.build.archetype.engine.v2.Node.Kind;

import static java.util.Objects.requireNonNull;

final class Flow {
    private final Scope scope;
    private Ir ir;
    private IrState rootEntry;
    private Map<Node, NodeFacts> nodeFacts = Map.of();
    private SymbolInfo[] symbolInfos = new SymbolInfo[0];

    Flow(Scope scope) {
        this.scope = requireNonNull(scope, "scope is null");
    }

    void process(Node root) {
        requireNonNull(root, "root is null");
        ir = Ir.lower(scope, root);
        IrAnalyzer analyzer = new IrAnalyzer(ir);
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

    IrState before(Node node) {
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

    Guard conditionGuard(Expression expression) {
        return ir.lowerCondition(scope, requireNonNull(expression, "expression is null"));
    }

    Value<?> declaredValue(Node node, String key) {
        Guard required = activeGuard(node == null ? null : node.parent());
        return declaredValue(node, key, required);
    }

    Value<?> declaredValue(Node node, String key, Guard required) {
        Fact fact = fact(node, key);
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
        IrState before = before(node);
        if (before.env().isEmpty()) {
            return null;
        }
        int id = ir.table().findId(key);
        if (id < 0 && !key.startsWith("~")) {
            id = ir.table().findId("~" + key);
        }
        return id < 0 ? null : before.env().get(id);
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

    private static final class NodeFacts {
        private final IrState before;
        private final String key;
        private final Guard renderGuard;
        private final Guard activeGuard;

        NodeFacts(IrState before, String key, Guard renderGuard, Guard activeGuard) {
            this.before = before;
            this.key = key;
            this.renderGuard = renderGuard;
            this.activeGuard = activeGuard;
        }
    }

    private static final class Projector {
        private final Ir ir;
        private final IrAnalyzer analyzer;
        private final Node node;
        private final Scope scope;
        private final Map<Node, List<Integer>> ops = new IdentityHashMap<>();
        private final Map<Node, Integer> branchTrueIds = new IdentityHashMap<>();
        private final Map<Node, Integer> branchJoinIds = new IdentityHashMap<>();
        private final Map<Node, NodeFacts> nodeFacts = new IdentityHashMap<>();
        private final SymbolInfo[] symbolInfos;

        Projector(Ir ir, IrAnalyzer analyzer, Node node, Scope scope) {
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
                IrState before = analyzer.beforeState(id);
                Op op = ops[id];
                SymbolInfo info;
                switch (op.kind()) {
                    case DECLARE_INPUT:
                        info = symbolInfos[op.symbolId()];
                        info.addDefinition(before.path(), guards);
                        if (info.sym.domain().kind() == Spec.Kind.BOOLEAN) {
                            info.addAvailability("true", before.path(), guards);
                            info.addAvailability("false", before.path(), guards);
                        }
                        break;
                    case DECLARE_OPTION:
                        info = symbolInfos[op.symbolId()];
                        info.addAvailability(op.source().value().getString(), before.path(), guards);
                        break;
                    case DEFINE_VALUE:
                        info = symbolInfos[op.symbolId()];
                        if (before.path().equals(Guard.FALSE)) {
                            break;
                        }
                        Map<Integer, Fact> env = analyzer.afterState(id).env();
                        Fact fact = env.get(op.symbolId());
                        if (fact == null) {
                            throw new IllegalStateException("Missing flow fact after op for key: " + id);
                        }
                        info.addDefinition(fact.guard(), guards);
                        recordAvailability(info, fact);
                        break;
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
            LatticeValue lattice = fact.lattice();
            if (lattice.kind() == LatticeValue.Kind.FINITE_SCALAR) {
                switch (info.sym.domain().kind()) {
                    case BOOLEAN:
                    case CHOICE:
                    case FINITE_TEXT:
                        if (lattice.scalarMask() != info.sym.domain().mask()) {
                            for (String value : lattice.scalarValues(info.sym.domain())) {
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

        IrState projectNode(Node node, Scope scope, IrState current) {
            List<Integer> opIds = ops.getOrDefault(node, List.of());
            IrState before;
            IrState afterOps;
            if (opIds.isEmpty()) {
                before = current;
                afterOps = before;
            } else {
                before = constrainPath(analyzer.beforeState(opIds.get(0)), current.path());
                afterOps = constrainPath(analyzer.afterState(opIds.get(opIds.size() - 1)), current.path());
            }
            Scope childScope = childScope(scope, node);
            Scope nodeScope = node.kind().isInput() ? childScope : scope;
            IrState branchEntry;
            Guard activeGuard;
            int branchTrueId = branchTrueIds.getOrDefault(node, -1);
            int branchJoinId = branchJoinIds.getOrDefault(node, -1);
            if (branchTrueId < 0 || branchJoinId < 0) {
                branchEntry = null;
                activeGuard = activeGuard(node, nodeScope, afterOps);
            } else {
                branchEntry = constrainPath(analyzer.entryState(branchTrueId), current.path());
                activeGuard = activeGuard(node, nodeScope, branchEntry);
            }
            String nodeKey = nodeKey(node, scope, childScope);
            nodeFacts.put(node, new NodeFacts(before, nodeKey, before.path(), activeGuard));

            Scope descendantScope = node.kind().isInput() ? childScope : scope;
            if (branchTrueId >= 0 && branchJoinId >= 0) {
                IrState cursor = constrainPath(branchEntry, activeGuard);
                for (Node child : node.children()) {
                    cursor = projectNode(child, descendantScope, cursor);
                }
                return constrainPath(analyzer.entryState(branchJoinId), current.path());
            }

            IrState cursor = afterOps;
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

        IrState constrainPath(IrState state, Guard required) {
            if (state.path().equals(required)) {
                return state;
            }
            Guard constrained = ir.guards().and(state.path(), required);
            return constrained.equals(state.path()) ? state : new IrState(constrained, state.env());
        }

        Guard activeGuard(Node node, Scope nodeScope, IrState afterOps) {
            Guard control = controlGuard(node, nodeScope, afterOps);
            return control == null ? afterOps.path() : ir.guards().and(afterOps.path(), control);
        }

        Guard controlGuard(Node node, Scope nodeScope, IrState state) {
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

        Guard translatedCondition(Expression expression, Scope scope, IrState state) {
            Guard translated = translatedCondition0(expression, scope, state);
            if (translated == null) {
                translated = ir.lowerCondition(scope, expression);
            }
            return translated;
        }

        Guard translatedCondition0(Expression expression, Scope scope, IrState state) {
            Guards guards = ir.guards();
            Deque<ReferenceOperand> stack = new ArrayDeque<>(expression.tokens().size());
            for (Token token : expression.tokens()) {
                if (token.isVariable()) {
                    stack.addLast(new ReferenceOperand(null, scope.key(token.variable()), null));
                    continue;
                }
                if (token.isOperand()) {
                    stack.addLast(new ReferenceOperand(null, null, token.operand()));
                    continue;
                }
                ReferenceOperand right;
                ReferenceOperand left;
                switch (token.operator()) {
                    case NOT:
                        Guard negated = booleanGuard(stack.removeLast(), state);
                        if (negated == null) {
                            return null;
                        }
                        stack.addLast(new ReferenceOperand(guards.not(negated), null, null));
                        break;
                    case AND:
                    case OR:
                        right = stack.removeLast();
                        left = stack.removeLast();
                        Guard rightGuard = booleanGuard(right, state);
                        Guard leftGuard = booleanGuard(left, state);
                        if (leftGuard == null || rightGuard == null) {
                            return null;
                        }
                        stack.addLast(new ReferenceOperand(token.operator() == Operator.AND
                                ? guards.and(leftGuard, rightGuard)
                                : guards.or(leftGuard, rightGuard), null, null));
                        break;
                    case EQUAL:
                    case NOT_EQUAL:
                        right = stack.removeLast();
                        left = stack.removeLast();
                        Guard equality = equalityGuard(left, right, state);
                        if (equality == null) {
                            return null;
                        }
                        if (token.operator() == Operator.EQUAL) {
                            stack.addLast(new ReferenceOperand(equality, null, null));
                        } else {
                            stack.addLast(new ReferenceOperand(guards.not(equality), null, null));
                        }
                        break;
                    case CONTAINS:
                        right = stack.removeLast();
                        left = stack.removeLast();
                        Guard contains = containsGuard(left, right, state);
                        if (contains == null) {
                            return null;
                        }
                        stack.addLast(new ReferenceOperand(contains, null, null));
                        break;
                    default:
                        return null;
                }
            }
            return stack.size() == 1 ? booleanGuard(stack.peekLast(), state) : null;
        }

        Guard booleanGuard(ReferenceOperand operand, IrState state) {
            if (operand.guard != null) {
                return operand.guard;
            }
            if (operand.ref != null) {
                SymbolInfo info = symbolInfo(operand.ref);
                if (info != null && info.sym.domain().kind() == Spec.Kind.BOOLEAN) {
                    return translatedEquality(operand.ref, Value.TRUE, state);
                }
                return null;
            }
            if (operand.literal != null && operand.literal.type() == Value.Type.BOOLEAN) {
                return operand.literal.getBoolean() ? Guard.TRUE : Guard.FALSE;
            }
            return null;
        }

        Guard equalityGuard(ReferenceOperand left, ReferenceOperand right, IrState state) {
            if (left.literal != null && right.literal != null) {
                return Value.isEqual(left.literal, right.literal) ? Guard.TRUE : Guard.FALSE;
            }
            if (left.ref != null && right.literal != null) {
                return translatedEquality(left.ref, right.literal, state);
            }
            if (left.literal != null && right.ref != null) {
                return translatedEquality(right.ref, left.literal, state);
            }
            return null;
        }

        Guard containsGuard(ReferenceOperand left, ReferenceOperand right, IrState state) {
            if (left.ref != null && right.literal != null) {
                return translatedListContains(left.ref, right.literal, state);
            }
            if (left.literal != null && right.ref != null && left.literal.type() == Value.Type.LIST) {
                return translatedScalarAny(right.ref, new TreeSet<>(left.literal.getList()), state);
            }
            if (left.literal != null && right.literal != null && left.literal.type() == Value.Type.LIST) {
                Set<String> required = containsValues(right.literal);
                if (required != null) {
                    return new TreeSet<>(left.literal.getList()).containsAll(required)
                            ? Guard.TRUE
                            : Guard.FALSE;
                }
            }
            return null;
        }

        Guard translatedEquality(String key, Value<?> value, IrState state) {
            Fact fact = factFor(state, key);
            SymbolInfo info = symbolInfo(key);
            Guard bound = fact == null || info == null ? Guard.FALSE : fact.match(info.sym, value, ir.guards());
            Guard direct = directEquality(key, value);
            return combine(key, fact, bound, direct);
        }

        Guard translatedScalarAny(String key, Set<String> values, IrState state) {
            Fact fact = factFor(state, key);
            SymbolInfo info = symbolInfo(key);
            Guard bound = fact == null || info == null ? Guard.FALSE : fact.scalarAny(info.sym, values, ir.guards());
            Guard direct = directScalarAny(key, values);
            return combine(key, fact, bound, direct);
        }

        Guard translatedListContains(String key, Value<?> value, IrState state) {
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

        Fact factFor(IrState state, String key) {
            int id = ir.table().findId(key);
            return id < 0 ? null : state.env().get(id);
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
            if (info == null) {
                return null;
            }
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

    private static final class ReferenceOperand {
        private final Guard guard;
        private final String ref;
        private final Value<?> literal;

        ReferenceOperand(Guard guard, String ref, Value<?> literal) {
            this.guard = guard;
            this.ref = ref;
            this.literal = literal;
        }
    }
}
