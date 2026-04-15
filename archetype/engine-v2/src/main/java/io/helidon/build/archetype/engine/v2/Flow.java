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
import java.util.Collections;
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
import io.helidon.build.archetype.engine.v2.Domain.Guards;
import io.helidon.build.archetype.engine.v2.Domain.LatticeValue;
import io.helidon.build.archetype.engine.v2.Domain.Spec;
import io.helidon.build.archetype.engine.v2.Domain.Symbol;
import io.helidon.build.archetype.engine.v2.Domain.Symbol.Fact;
import io.helidon.build.archetype.engine.v2.Domain.Symbol.Table;
import io.helidon.build.archetype.engine.v2.Expression.Token;
import io.helidon.build.archetype.engine.v2.Node.Kind;

import static java.util.Objects.requireNonNull;

final class Flow {
    private final Scope scope;
    private Ir ir;
    private Model model;

    Flow(Scope scope) {
        this.scope = requireNonNull(scope, "scope is null");
    }

    void process(Node root) {
        requireNonNull(root, "root is null");
        ir = new Lowerer(scope).lower(root);
        Analysis analysis = new Analyzer(ir).analyze();
        model = new Projector(ir, analysis, root, scope).project();
    }

    Guards guards() {
        return ir.guards;
    }

    String key(Node node) {
        return node == null ? model.scope.key() : model.node(node).key;
    }

    Scope scope(Node node) {
        if (node == null) {
            return model.scope;
        }
        String key = model.node(node).key;
        return key.isEmpty() ? model.scope : model.scope.get("~" + key);
    }

    Guard renderGuard(Node node) {
        return node == null ? Guard.TRUE : model.node(node).renderGuard;
    }

    Guard activeGuard(Node node) {
        return node == null ? Guard.TRUE : model.node(node).activeGuard;
    }

    State before(Node node) {
        return node == null ? model.analysis.entryByBlock.get(0) : model.node(node).before;
    }

    SymbolInfo symbol(String name) {
        Integer symbolId = ir.symbols.findId(name);
        return symbolId == null ? null : model.symbols.get(symbolId);
    }

    Symbol symbol(int symbolId) {
        return ir.symbols.symbol(symbolId);
    }

    Expression expression(Guard guard) {
        return expression(guard, scope);
    }

    Expression expression(Guard guard, Scope scope) {
        return ir.guards.toExpression(guard, scope).reduce();
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
        if (fact == null || !ir.guards.implies(required, fact.definedUnder())) {
            return Value.empty();
        }
        SymbolInfo info = symbol(key);
        if (info == null && !key.startsWith("~")) {
            info = symbol("~" + key);
        }
        Value.Type type = info == null ? Value.Type.EMPTY : valueType(info);
        Value<?> exact = exactValue(fact, required, type);
        return exact != null ? exact : info == null ? Value.empty() : singletonValue(fact.value(), info.symbol.domain(), type);
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

    private Fact fact(Node node, String key) {
        State before = before(node);
        if (before.env.isEmpty()) {
            return null;
        }
        Integer symbolId = ir.symbols.findId(key);
        if (symbolId == null && !key.startsWith("~")) {
            symbolId = ir.symbols.findId("~" + key);
        }
        return symbolId == null ? null : before.env.get(symbolId);
    }

    private static Value.Type valueType(SymbolInfo info) {
        switch (info.symbol.domain().kind()) {
            case FINITE_SCALAR:
                return info.symbol.domain().booleanLike() ? Value.Type.BOOLEAN : Value.Type.STRING;
            case FINITE_MEMBERSHIP:
                return Value.Type.LIST;
            case OPEN_TEXT:
                return Value.Type.STRING;
            default:
                return Value.Type.EMPTY;
        }
    }

    private Value<?> exactValue(Fact fact, Guard required, Value.Type type) {
        if (fact.exactCases().isEmpty()) {
            return null;
        }
        Value<?> exact = null;
        Guard coverage = Guard.FALSE;
        for (Fact.ExactCase exactCase : fact.exactCases()) {
            Guard overlap = ir.guards.and(required, exactCase.guard());
            if (overlap.equals(Guard.FALSE)) {
                continue;
            }
            Value<?> candidate = coerceExactValue(exactCase.value(), type);
            if (exact == null) {
                exact = candidate;
            } else if (!Value.isEqual(exact, candidate)) {
                return null;
            }
            coverage = ir.guards.or(coverage, overlap);
        }
        return exact != null && ir.guards.implies(required, coverage) ? exact : null;
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

    private static int requireSymbolId(Table symbols, String key) {
        Integer symbolId = symbols.findId(key);
        if (symbolId == null) {
            throw new IllegalStateException("Missing symbol: " + key);
        }
        return symbolId;
    }

    private static Guard directBooleanGuard(Table symbols, Guards guards, String key) {
        Symbol symbol = symbols.symbol(requireSymbolId(symbols, key));
        if (!symbol.guardable() || symbol.tainted() || !symbol.domain().booleanLike()) {
            return Guard.TRUE;
        }
        return guards.eq(symbol.id(), "true");
    }

    private static Guard directOptionGuard(Table symbols, Guards guards, String key, Node input, Node option) {
        Symbol symbol = symbols.symbol(requireSymbolId(symbols, key));
        String value = option.value().getString();
        switch (input.kind()) {
            case INPUT_ENUM:
                if (!symbol.guardable() || symbol.tainted() || symbol.domain().kind() != Spec.Kind.FINITE_SCALAR) {
                    return Guard.TRUE;
                }
                return guards.eq(symbol.id(), value);
            case INPUT_LIST:
                if (!symbol.guardable() || symbol.tainted() || symbol.domain().kind() != Spec.Kind.FINITE_MEMBERSHIP) {
                    return Guard.TRUE;
                }
                return guards.contains(symbol.id(), value);
            default:
                throw new IllegalArgumentException("Unsupported option parent: " + input.kind());
        }
    }

    static final class SymbolInfo {
        private final Symbol symbol;
        private final Guard definition;
        private final Map<String, Guard> availabilityByValue;

        SymbolInfo(Symbol symbol, Guard definition, Map<String, Guard> availabilityByValue) {
            this.symbol = symbol;
            this.definition = definition;
            this.availabilityByValue = availabilityByValue;
        }

        Symbol symbol() {
            return symbol;
        }

        Guard definition() {
            return definition;
        }

        Guard availability(String value) {
            return availabilityByValue.get(value);
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

    private static final class Ir {
        private final List<Block> blocks;
        private final Table symbols;
        private final List<Op> ops;
        private final Guards guards;
        private final List<ControlPath> controlPaths;

        Ir(List<Block> blocks, Table symbols, List<Op> ops, Guards guards, List<ControlPath> controlPaths) {
            this.blocks = blocks;
            this.symbols = requireNonNull(symbols, "symbols is null");
            this.ops = ops;
            this.guards = requireNonNull(guards, "guards is null");
            this.controlPaths = requireNonNull(controlPaths, "controlPaths is null");
            if (this.controlPaths.isEmpty()) {
                throw new IllegalArgumentException("controlPaths is empty");
            }
        }

    }

    private static final class Block {
        private final List<Integer> ops;
        private final Terminator terminator;
        private final int controlPathId;

        Block(List<Integer> ops, Terminator terminator, int controlPathId) {
            this.ops = ops;
            this.terminator = requireNonNull(terminator, "terminator is null");
            this.controlPathId = controlPathId;
        }
    }

    private static final class Op {
        enum Kind {
            DECLARE_INPUT,
            DECLARE_OPTION,
            DEFINE_VALUE,
            EMIT
        }

        private final int id;
        private final int blockId;
        private final Node source;
        private final Kind kind;
        private final int symbolId;
        private final Expression expression;

        Op(int id, int blockId, Node source, Kind kind, int symbolId, Expression expression) {
            this.id = id;
            this.blockId = blockId;
            this.source = requireNonNull(source, "source is null");
            this.kind = requireNonNull(kind, "kind is null");
            this.symbolId = symbolId;
            this.expression = expression;
        }

        static Op declareInput(int id, int blockId, Node source, int symbolId) {
            return new Op(id, blockId, source, Kind.DECLARE_INPUT, symbolId, null);
        }

        static Op declareOption(int id, int blockId, Node source, int symbolId) {
            return new Op(id, blockId, source, Kind.DECLARE_OPTION, symbolId, null);
        }

        static Op defineValue(int id, int blockId, Node source, int symbolId, Expression expression) {
            return new Op(id, blockId, source, Kind.DEFINE_VALUE, symbolId, requireNonNull(expression, "expression is null"));
        }

        static Op emit(int id, int blockId, Node source) {
            return new Op(id, blockId, source, Kind.EMIT, -1, null);
        }

    }

    private static final class Terminator {
        enum Kind {
            GOTO,
            BRANCH,
            RETURN,
            UNREACHABLE
        }

        private final Kind kind;
        private final Node source;
        private final int targetId;
        private final int trueId;
        private final int falseId;

        Terminator(Kind kind, Node source, int targetId, int trueId, int falseId) {
            this.kind = requireNonNull(kind, "kind is null");
            this.source = requireNonNull(source, "source is null");
            this.targetId = targetId;
            this.trueId = trueId;
            this.falseId = falseId;
        }

        static Terminator jump(Node source, int targetId) {
            return new Terminator(Kind.GOTO, source, targetId, -1, -1);
        }

        static Terminator branch(Node source, int trueId, int falseId) {
            return new Terminator(Kind.BRANCH, source, -1, trueId, falseId);
        }

        static Terminator ret(Node source) {
            return new Terminator(Kind.RETURN, source, -1, -1, -1);
        }

        static Terminator unreachable(Node source) {
            return new Terminator(Kind.UNREACHABLE, source, -1, -1, -1);
        }
    }

    private static final class Analysis {
        private final List<State> entryByBlock;
        private final List<State> beforeByOp;
        private final List<State> afterByOp;

        Analysis(List<State> entryByBlock, List<State> beforeByOp, List<State> afterByOp) {
            this.entryByBlock = entryByBlock;
            this.beforeByOp = beforeByOp;
            this.afterByOp = afterByOp;
        }
    }

    private static final class Model {
        private final Analysis analysis;
        private final Scope scope;
        private final Map<Node, NodeFacts> nodes;
        private final List<SymbolInfo> symbols;

        Model(Analysis analysis, Scope scope, Map<Node, NodeFacts> nodes, List<SymbolInfo> symbols) {
            this.analysis = analysis;
            this.scope = scope;
            this.nodes = nodes;
            this.symbols = symbols;
        }

        NodeFacts node(Node node) {
            requireNonNull(node, "node is null");
            NodeFacts facts = nodes.get(node);
            if (facts != null) {
                return facts;
            }
            throw new IllegalArgumentException("Node is not part of this flow model: " + node.kind() + "#" + node.id());
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
        private final Analysis analysis;
        private final Node root;
        private final Scope scope;
        private final Guards guards;
        private final Map<Node, List<Integer>> ops = new IdentityHashMap<>();
        private final Map<Node, BranchInfo> branches = new IdentityHashMap<>();
        private final List<SymbolInfoBuilder> symbols = new ArrayList<>();

        Projector(Ir ir, Analysis analysis, Node root, Scope scope) {
            this.ir = ir;
            this.analysis = analysis;
            this.root = root;
            this.scope = scope;
            this.guards = ir.guards;
            for (Symbol symbol : ir.symbols.symbols()) {
                symbols.add(new SymbolInfoBuilder(symbol));
            }
        }

        Model project() {
            indexAnchors();
            scanSymbols();
            Map<Node, NodeFacts> nodes = new IdentityHashMap<>();
            projectNode(root, scope, analysis.entryByBlock.get(0), nodes);
            List<SymbolInfo> builtSymbols = new ArrayList<>(symbols.size());
            for (SymbolInfoBuilder builder : symbols) {
                builtSymbols.add(builder.build());
            }
            return new Model(analysis, scope, nodes, builtSymbols);
        }

        void indexAnchors() {
            for (Op op : ir.ops) {
                ops.computeIfAbsent(op.source, key -> new ArrayList<>()).add(op.id);
            }
            for (Block block : ir.blocks) {
                Terminator terminator = block.terminator;
                if (terminator.kind != Terminator.Kind.BRANCH) {
                    continue;
                }
                int joinId = terminator.falseId;
                Terminator falseTerminator = ir.blocks.get(terminator.falseId).terminator;
                if (falseTerminator.kind == Terminator.Kind.GOTO) {
                    joinId = falseTerminator.targetId;
                }
                branches.put(terminator.source, new BranchInfo(terminator.trueId, joinId));
            }
        }

        void scanSymbols() {
            for (Op op : ir.ops) {
                State before = analysis.beforeByOp.get(op.id);
                switch (op.kind) {
                    case DECLARE_INPUT: {
                        SymbolInfoBuilder builder = symbols.get(op.symbolId);
                        builder.addDefinition(before.path, guards);
                        if (builder.symbol.domain().booleanLike()) {
                            builder.addAvailability("true", before.path, guards);
                            builder.addAvailability("false", before.path, guards);
                        }
                        break;
                    }
                    case DECLARE_OPTION: {
                        SymbolInfoBuilder builder = symbols.get(op.symbolId);
                        builder.addAvailability(op.source.value().getString(), before.path, guards);
                        break;
                    }
                    case DEFINE_VALUE: {
                        SymbolInfoBuilder builder = symbols.get(op.symbolId);
                        Fact fact = analysis.afterByOp.get(op.id).env.get(op.symbolId);
                        if (fact != null) {
                            builder.addDefinition(fact.definedUnder(), guards);
                            recordAvailability(builder, fact);
                        }
                        break;
                    }
                    default:
                        // no-op
                }
            }
        }

        void recordAvailability(SymbolInfoBuilder builder, Fact fact) {
            if (!fact.exactCases().isEmpty()) {
                for (Fact.ExactCase exactCase : fact.exactCases()) {
                    recordExactAvailability(builder, exactCase);
                }
                return;
            }
            if (builder.symbol.domain().kind() != Spec.Kind.FINITE_SCALAR
                    || fact.value().kind() != LatticeValue.Kind.FINITE_SCALAR) {
                return;
            }
            if (fact.value().scalarMask() == builder.symbol.domain().mask()) {
                return;
            }
            for (String value : fact.value().scalarValues(builder.symbol.domain())) {
                builder.addAvailability(value, fact.definedUnder(), guards);
            }
        }

        void recordExactAvailability(SymbolInfoBuilder builder, Fact.ExactCase exactCase) {
            switch (builder.symbol.domain().kind()) {
                case FINITE_SCALAR:
                    long scalarMask = exactCase.scalarMask();
                    while (scalarMask != 0L) {
                        int ordinal = Long.numberOfTrailingZeros(scalarMask);
                        builder.addAvailability(builder.symbol.scalarValue(ordinal), exactCase.guard(), guards);
                        scalarMask &= scalarMask - 1L;
                    }
                    break;
                case FINITE_MEMBERSHIP:
                    long listMask = exactCase.listMask();
                    while (listMask != 0L) {
                        int ordinal = Long.numberOfTrailingZeros(listMask);
                        builder.addAvailability(builder.symbol.membershipItem(ordinal), exactCase.guard(), guards);
                        listMask &= listMask - 1L;
                    }
                    break;
                default:
                    break;
            }
        }

        State projectNode(Node node, Scope scope, State current, Map<Node, NodeFacts> nodes) {
            Scope childScope = childScope(scope, node);
            Scope nodeScope = node.kind().isInput() ? childScope : scope;
            List<Integer> opIds = ops.getOrDefault(node, List.of());
            State before = opIds.isEmpty() ? current : constrainPath(analysis.beforeByOp.get(opIds.get(0)), current.path);
            State afterOps = opIds.isEmpty() ? before : constrainPath(analysis.afterByOp.get(opIds.get(opIds.size() - 1)),
                    current.path);
            BranchInfo branch = branches.get(node);
            State branchEntry = branch == null ? null : constrainPath(analysis.entryByBlock.get(branch.trueId), current.path);
            Guard activeGuard = branch != null
                    ? activeGuard(node, nodeScope, branchEntry)
                    : activeGuard(node, nodeScope, afterOps);
            nodes.put(node, new NodeFacts(before, nodeKey(node, scope, childScope), before.path, activeGuard));

            Scope descendantScope = node.kind().isInput() ? childScope : scope;
            if (branch != null) {
                State cursor = constrainPath(branchEntry, activeGuard);
                for (Node child : node.children()) {
                    cursor = projectNode(child, descendantScope, cursor, nodes);
                }
                return constrainPath(analysis.entryByBlock.get(branch.joinId), current.path);
            }

            State cursor = afterOps;
            for (Node child : node.children()) {
                cursor = projectNode(child, descendantScope, cursor, nodes);
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
                    return Flow.directBooleanGuard(ir.symbols, guards, nodeScope.key());
                case INPUT_OPTION:
                    Node input = node.ancestor(Kind::isInput).orElse(null);
                    if (input == null) {
                        return null;
                    }
                    switch (input.kind()) {
                        case INPUT_ENUM:
                        case INPUT_LIST:
                            return Flow.directOptionGuard(ir.symbols, guards, nodeScope.key(), input, node);
                        default:
                            return null;
                    }
                default:
                    return null;
            }
        }

        Guard translatedCondition(Expression expression, Scope scope, State state) {
            Guard translated = translatedCondition0(expression, scope, state);
            return translated != null ? translated : new ConditionLowerer(ir.symbols, guards, scope).lower(expression);
        }

        Guard translatedCondition0(Expression expression, Scope scope, State state) {
            Deque<ProjectedTerm> stack = new ArrayDeque<>();
            for (Token token : expression.tokens()) {
                if (token.isVariable()) {
                    stack.push(ProjectedTerm.ref(scope.key(token.variable())));
                    continue;
                }
                if (token.isOperand()) {
                    stack.push(ProjectedTerm.value(token.operand()));
                    continue;
                }
                switch (token.operator()) {
                    case NOT:
                        Guard negated = booleanGuard(stack.pop(), state);
                        if (negated == null) {
                            return null;
                        }
                        stack.push(ProjectedTerm.guard(guards.not(negated)));
                        break;
                    case AND: {
                        Guard right = booleanGuard(stack.pop(), state);
                        Guard left = booleanGuard(stack.pop(), state);
                        if (left == null || right == null) {
                            return null;
                        }
                        stack.push(ProjectedTerm.guard(guards.and(left, right)));
                        break;
                    }
                    case OR: {
                        Guard right = booleanGuard(stack.pop(), state);
                        Guard left = booleanGuard(stack.pop(), state);
                        if (left == null || right == null) {
                            return null;
                        }
                        stack.push(ProjectedTerm.guard(guards.or(left, right)));
                        break;
                    }
                    case EQUAL:
                    case NOT_EQUAL: {
                        ProjectedTerm right = stack.pop();
                        ProjectedTerm left = stack.pop();
                        Guard equality = equalityGuard(left, right, state);
                        if (equality == null) {
                            return null;
                        }
                        stack.push(ProjectedTerm.guard(token.operator() == Expression.Operator.EQUAL
                                ? equality
                                : guards.not(equality)));
                        break;
                    }
                    case CONTAINS: {
                        ProjectedTerm right = stack.pop();
                        ProjectedTerm left = stack.pop();
                        Guard contains = containsGuard(left, right, state);
                        if (contains == null) {
                            return null;
                        }
                        stack.push(ProjectedTerm.guard(contains));
                        break;
                    }
                    default:
                        return null;
                }
            }
            return stack.size() == 1 ? booleanGuard(stack.pop(), state) : null;
        }

        Guard booleanGuard(ProjectedTerm term, State state) {
            if (term.guard != null) {
                return term.guard;
            }
            if (term.ref != null) {
                Flow.SymbolInfo info = symbolInfo(term.ref);
                if (info == null || !info.symbol.domain().booleanLike()) {
                    return null;
                }
                return translatedEquality(term.ref, Value.TRUE, state);
            }
            if (term.literal != null && term.literal.type() == Value.Type.BOOLEAN) {
                return term.literal.getBoolean() ? Guard.TRUE : Guard.FALSE;
            }
            return null;
        }

        Guard equalityGuard(ProjectedTerm left, ProjectedTerm right, State state) {
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

        Guard containsGuard(ProjectedTerm left, ProjectedTerm right, State state) {
            if (left.ref != null && right.literal != null) {
                return translatedListContains(left.ref, right.literal, state);
            }
            if (left.literal != null && right.ref != null && left.literal.type() == Value.Type.LIST) {
                return translatedScalarAny(right.ref, new TreeSet<>(left.literal.getList()), state);
            }
            if (left.literal != null && right.literal != null && left.literal.type() == Value.Type.LIST) {
                Set<String> required = containsValues(right.literal);
                if (required == null) {
                    return null;
                }
                return new TreeSet<>(left.literal.getList()).containsAll(required)
                        ? Guard.TRUE
                        : Guard.FALSE;
            }
            return null;
        }

        Guard translatedEquality(String key, Value<?> value, State state) {
            Fact fact = factFor(state, key);
            Flow.SymbolInfo info = symbolInfo(key);
            Guard bound = fact == null || info == null ? Guard.FALSE : fact.match(info.symbol, value, guards);
            Guard direct = directEquality(key, value);
            return combine(key, fact, bound, direct);
        }

        Guard translatedScalarAny(String key, Set<String> values, State state) {
            Fact fact = factFor(state, key);
            Flow.SymbolInfo info = symbolInfo(key);
            Guard bound = fact == null || info == null ? Guard.FALSE : fact.scalarAny(info.symbol, values, guards);
            Guard direct = directScalarAny(key, values);
            return combine(key, fact, bound, direct);
        }

        Guard translatedListContains(String key, Value<?> value, State state) {
            Set<String> required = containsValues(value);
            if (required == null) {
                return null;
            }
            Fact fact = factFor(state, key);
            Flow.SymbolInfo info = symbolInfo(key);
            Guard bound = fact == null || info == null ? Guard.FALSE : fact.listContains(info.symbol, required, guards);
            Guard direct = directListContains(symbolInfo(key), required);
            return combine(key, fact, bound, direct);
        }

        Guard combine(String key, Fact fact, Guard bound, Guard direct) {
            Flow.SymbolInfo info = symbolInfo(key);
            Symbol symbol = info == null ? null : info.symbol;
            Guard defined = definitionFor(key, fact);
            if (fact == null) {
                return direct == null ? null : defined == null ? direct : guards.and(defined, direct);
            }
            if (direct == null) {
                if (bound == null) {
                    return null;
                }
                if (bound.equals(Guard.FALSE)) {
                    Guard exact = symbol == null ? fact.exactDefined(guards) : fact.supportedExactDefined(symbol, guards);
                    if (defined == null || exact.equals(Guard.FALSE) || !guards.implies(exact, defined)) {
                        return null;
                    }
                }
                return bound;
            }
            Guard available = defined == null ? Guard.TRUE : defined;
            Guard exact = symbol == null ? fact.exactDefined(guards) : fact.supportedExactDefined(symbol, guards);
            Guard unresolved = guards.and(guards.minus(available, exact), direct);
            return guards.or(bound, unresolved);
        }

        Fact factFor(State state, String key) {
            Integer symbolId = ir.symbols.findId(key);
            return symbolId == null ? null : state.env.get(symbolId);
        }

        Guard definitionFor(String key, Fact fact) {
            if (fact != null) {
                return fact.definedUnder();
            }
            Flow.SymbolInfo info = symbolInfo(key);
            return info == null ? null : info.definition;
        }

        Flow.SymbolInfo symbolInfo(String key) {
            Integer symbolId = ir.symbols.findId(key);
            return symbolId == null ? null : symbols.get(symbolId).build();
        }

        Guard directEquality(String key, Value<?> value) {
            String scalar = Value.scalarLiteral(value);
            if (scalar == null) {
                return null;
            }
            Flow.SymbolInfo info = symbolInfo(key);
            if (info == null) {
                return null;
            }
            Symbol symbol = info.symbol;
            if (!symbol.guardable() || symbol.tainted() || symbol.domain().kind() != Spec.Kind.FINITE_SCALAR) {
                return null;
            }
            return symbol.domain().values().contains(scalar)
                    ? available(info, scalar, guards.eq(symbol.id(), scalar))
                    : Guard.FALSE;
        }

        Guard directScalarAny(String key, Set<String> values) {
            Flow.SymbolInfo info = symbolInfo(key);
            if (info == null) {
                return null;
            }
            Symbol symbol = info.symbol;
            if (!symbol.guardable() || symbol.tainted() || symbol.domain().kind() != Spec.Kind.FINITE_SCALAR) {
                return null;
            }
            Set<String> allowed = new TreeSet<>(values);
            allowed.retainAll(symbol.domain().values());
            Guard direct = Guard.FALSE;
            for (String value : allowed) {
                direct = guards.or(direct, available(info, value, guards.eq(symbol.id(), value)));
            }
            return direct;
        }

        Guard directListContains(Flow.SymbolInfo info, Set<String> required) {
            if (info == null) {
                return null;
            }
            Symbol symbol = info.symbol;
            if (!symbol.guardable() || symbol.tainted() || symbol.domain().kind() != Spec.Kind.FINITE_MEMBERSHIP) {
                return null;
            }
            Set<String> items = symbol.domain().values();
            if (!items.containsAll(required)) {
                return Guard.FALSE;
            }
            Guard available = Guard.TRUE;
            for (String value : required) {
                Guard availability = info.availabilityByValue.get(value);
                if (availability == null) {
                    return Guard.FALSE;
                }
                available = guards.and(available, availability);
            }
            return guards.and(available, guards.containsAll(symbol.id(), required));
        }

        Guard available(Flow.SymbolInfo info, String value, Guard direct) {
            Guard availability = info.availabilityByValue.get(value);
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

        Scope childScope(Scope scope, Node node) {
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
    }

    private static final class ProjectedTerm {
        private final Guard guard;
        private final String ref;
        private final Value<?> literal;

        ProjectedTerm(Guard guard, String ref, Value<?> literal) {
            this.guard = guard;
            this.ref = ref;
            this.literal = literal;
        }

        static ProjectedTerm guard(Guard guard) {
            return new ProjectedTerm(guard, null, null);
        }

        static ProjectedTerm ref(String ref) {
            return new ProjectedTerm(null, ref, null);
        }

        static ProjectedTerm value(Value<?> literal) {
            return new ProjectedTerm(null, null, literal);
        }
    }

    private static final class BranchInfo {
        private final int trueId;
        private final int joinId;

        BranchInfo(int trueId, int joinId) {
            this.trueId = trueId;
            this.joinId = joinId;
        }
    }

    private static final class SymbolInfoBuilder {
        private final Symbol symbol;
        private Guard definition;
        private final Map<String, Guard> availabilityByValue = new LinkedHashMap<>();

        SymbolInfoBuilder(Symbol symbol) {
            this.symbol = symbol;
        }

        void addDefinition(Guard guard, Guards guards) {
            definition = definition == null ? guard : guards.or(definition, guard);
        }

        void addAvailability(String value, Guard guard, Guards guards) {
            availabilityByValue.compute(value, (key, current) -> current == null ? guard : guards.or(current, guard));
        }

        SymbolInfo build() {
            return new SymbolInfo(symbol,
                    definition == null ? Guard.FALSE : definition,
                    availabilityByValue);
        }
    }

    private static final class Lowerer {
        private final Scope scope;
        private final Table.Builder symbolBuilder = Table.builder();
        private final Map<String, SymbolSeed> symbolSeeds = new LinkedHashMap<>();
        private final Map<String, SymbolSeed> declaredInputSymbols = new LinkedHashMap<>();
        private final List<ControlPath> controlPaths = new ArrayList<>();
        private final List<BlockBuilder> blocks = new ArrayList<>();
        private final List<Op> ops = new ArrayList<>();
        private Table symbols;
        private Guards guards;

        Lowerer(Scope scope) {
            this.scope = scope;
            this.controlPaths.add(ControlPath.ROOT);
        }

        Ir lower(Node root) {
            collectDeclaredInputs(root, scope);
            collectSymbols(root, scope);
            for (SymbolSeed seed : symbolSeeds.values()) {
                symbolBuilder.define(seed.name, seed.spec, seed.guardable, seed.tainted);
            }
            symbols = symbolBuilder.build();
            guards = new Guards(symbols);

            int entryBlock = newBlock(ControlPath.ROOT_ID);
            int exitBlock = lowerChildren(root.children(), entryBlock, ControlPath.ROOT_ID, scope);
            terminateIfMissing(exitBlock, Terminator.ret(root));

            List<Block> loweredBlocks = new ArrayList<>(blocks.size());
            for (BlockBuilder block : blocks) {
                Terminator terminator = block.terminator;
                if (terminator == null) {
                    terminator = Terminator.unreachable(root);
                }
                loweredBlocks.add(new Block(block.ops, terminator, block.controlPathId));
            }
            return new Ir(loweredBlocks, symbols, ops, guards, controlPaths);
        }

        void collectDeclaredInputs(Node root, Scope scope) {
            traverse(root, scope, this::collectDeclaredInput);
        }

        void collectDeclaredInput(Node node, Scope scope) {
            Scope childScope = childScope(scope, node);
            switch (node.kind()) {
                case INPUT_BOOLEAN:
                    rememberDeclaredInput(childScope.key(), Spec.booleanSpec(), true, false);
                    break;
                case INPUT_ENUM:
                case INPUT_LIST:
                    rememberDeclaredInput(childScope.key(), inputSpec(node), !optionValues(node).isEmpty(), false);
                    break;
                case INPUT_TEXT:
                    rememberDeclaredInput(childScope.key(), Spec.OPEN_TEXT, false, true);
                    break;
                default:
                    break;
            }
        }

        void collectSymbols(Node root, Scope scope) {
            traverse(root, scope, this::collectSymbol);
        }

        void collectSymbol(Node node, Scope scope) {
            Scope childScope = childScope(scope, node);
            switch (node.kind()) {
                case INPUT_BOOLEAN:
                    rememberSymbol(childScope.key(), Spec.booleanSpec(), true, false);
                    break;
                case INPUT_ENUM:
                case INPUT_LIST:
                    rememberSymbol(childScope.key(), inputSpec(node), !optionValues(node).isEmpty(), false);
                    break;
                case INPUT_TEXT:
                    rememberSymbol(childScope.key(), Spec.OPEN_TEXT, false, true);
                    break;
                case PRESET_BOOLEAN:
                case VARIABLE_BOOLEAN:
                    rememberSymbol(definitionId(scope, node), Spec.booleanSpec(), true, false);
                    break;
                case PRESET_ENUM:
                case VARIABLE_ENUM:
                case PRESET_TEXT:
                case VARIABLE_TEXT:
                    rememberDefinitionSymbol(scope, node);
                    break;
                case PRESET_LIST:
                case VARIABLE_LIST: {
                    String key = definitionId(scope, node);
                    SymbolSeed declared = declaredInputSymbols.get(key);
                    if (declared != null) {
                        rememberSymbol(key, declared.spec, declared.guardable, declared.tainted);
                    } else {
                        rememberSymbol(key, Spec.OPEN_TEXT, false, false);
                    }
                    break;
                }
                default:
                    break;
            }
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

        Scope childScope(Scope scope, Node node) {
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

        void rememberDeclaredInput(String name, Spec spec, boolean guardable, boolean tainted) {
            declaredInputSymbols.merge(
                    name,
                    new SymbolSeed(name, spec, guardable, tainted),
                    SymbolSeed::merge);
        }

        int lowerChildren(List<Node> nodes, int blockId, int controlPathId, Scope scope) {
            int current = blockId;
            for (Node node : nodes) {
                current = lower(node, current, controlPathId, scope);
            }
            return current;
        }

        int lower(Node node, int blockId, int controlPathId, Scope scope) {
            switch (node.kind()) {
                case INPUT_BOOLEAN:
                case INPUT_ENUM:
                case INPUT_LIST:
                case INPUT_TEXT:
                    return lowerInput(node, blockId, controlPathId, scope);
                case PRESET_BOOLEAN:
                case PRESET_ENUM:
                case PRESET_LIST:
                case PRESET_TEXT:
                case VARIABLE_BOOLEAN:
                case VARIABLE_ENUM:
                case VARIABLE_LIST:
                case VARIABLE_TEXT:
                    return lowerDefinition(node, blockId, controlPathId, scope);
                case CONDITION:
                    return lowerCondition(node, blockId, controlPathId, scope);
                case STEP:
                case FILE:
                case MODEL:
                    append(blockId, Op.emit(nextOpId(), blockId, node));
                    return lowerChildren(node.children(), blockId, controlPathId, scope);
                case INPUT_OPTION:
                    return lowerOption(node, blockId, controlPathId, scope);
                default:
                    return lowerChildren(node.children(), blockId, controlPathId, scope);
            }
        }

        int lowerInput(Node node, int blockId, int controlPathId, Scope scope) {
            Scope inputScope = scope.getOrCreate(node);
            int symbolId = symbols.findId(inputScope.key());
            append(blockId, Op.declareInput(nextOpId(), blockId, node, symbolId));
            switch (node.kind()) {
                case INPUT_BOOLEAN:
                    return lowerGuardedChildren(node,
                            blockId,
                            controlPathId,
                            Flow.directBooleanGuard(symbols, guards, inputScope.key()),
                            node.children(),
                            inputScope);
                case INPUT_ENUM:
                case INPUT_LIST:
                default:
                    return lowerChildren(node.children(), blockId, controlPathId, inputScope);
            }
        }

        int lowerOption(Node node, int blockId, int controlPathId, Scope scope) {
            Node input = node.ancestor(Kind::isInput).orElseThrow(() ->
                    new IllegalStateException("Option without input parent: " + node));
            Integer symbolId = symbols.findId(scope.key());
            if (symbolId == null) {
                throw new IllegalStateException("Missing option symbol for scope: " + scope.key());
            }
            append(blockId, Op.declareOption(nextOpId(), blockId, node, symbolId));
            return lowerGuardedChildren(node,
                    blockId,
                    controlPathId,
                    optionGuard(input, node, scope),
                    node.children(),
                    scope);
        }

        int lowerDefinition(Node node, int blockId, int controlPathId, Scope scope) {
            Integer symbolId = symbols.findId(definitionId(scope, node));
            if (symbolId != null) {
                append(blockId, Op.defineValue(nextOpId(),
                        blockId,
                        node,
                        symbolId,
                        new Expression(List.of(Token.of(node.value())), true)));
            }
            return lowerChildren(node.children(), blockId, controlPathId, scope);
        }

        int lowerCondition(Node node, int blockId, int controlPathId, Scope scope) {
            return lowerGuardedChildren(node,
                    blockId,
                    controlPathId,
                    guard(node.expression(), scope),
                    node.children(),
                    scope);
        }

        Guard optionGuard(Node input, Node option, Scope scope) {
            return Flow.directOptionGuard(symbols, guards, scope.key(), input, option);
        }

        int lowerGuardedChildren(Node node,
                                 int blockId,
                                 int controlPathId,
                                 Guard guard,
                                 List<Node> children,
                                 Scope scope) {
            if (children.isEmpty()) {
                return blockId;
            }
            int truePathId = nextControlPath(controlPathId, guard);
            int falsePathId = nextControlPath(controlPathId, guards.not(guard));
            int trueBlock = newBlock(truePathId);
            int falseBlock = newBlock(falsePathId);
            int joinBlock = newBlock(controlPathId);
            terminate(blockId, Terminator.branch(node, trueBlock, falseBlock));
            int trueExit = lowerChildren(children, trueBlock, truePathId, scope);
            terminateIfMissing(trueExit, Terminator.jump(node, joinBlock));
            terminateIfMissing(falseBlock, Terminator.jump(node, joinBlock));
            return joinBlock;
        }

        Guard guard(Expression expression, Scope scope) {
            return new ConditionLowerer(symbols, guards, scope).lower(requireNonNull(expression, "expression is null"));
        }

        void append(int blockId, Op op) {
            block(blockId).ops.add(op.id);
            ops.add(op);
        }

        void rememberSymbol(String name, Spec spec, boolean guardable, boolean tainted) {
            symbolSeeds.merge(name,
                    new SymbolSeed(name, spec, guardable, tainted),
                    SymbolSeed::merge);
        }

        void rememberDefinitionSymbol(Scope scope, Node node) {
            SymbolSeed seed = definitionSeed(scope, node);
            rememberSymbol(seed.name, seed.spec, seed.guardable, seed.tainted);
        }

        SymbolSeed definitionSeed(Scope scope, Node node) {
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
                    return scalarDefinitionSeed(key, node.value());
                case PRESET_LIST:
                case VARIABLE_LIST:
                default:
                    break;
            }
            return new SymbolSeed(key, Spec.OPEN_TEXT, false, false);
        }

        SymbolSeed scalarDefinitionSeed(String key, Value<?> value) {
            String literal = literalScalar(value);
            if (literal == null) {
                return new SymbolSeed(key, Spec.OPEN_TEXT, false, true);
            }
            return new SymbolSeed(key, Spec.finiteText(Set.of(literal)), true, false);
        }

        String literalScalar(Value<?> value) {
            String literal = Value.scalarLiteral(value);
            if (literal == null || literal.contains("${")) {
                return null;
            }
            return literal;
        }

        void terminate(int blockId, Terminator terminator) {
            BlockBuilder block = block(blockId);
            if (block.terminator != null) {
                throw new IllegalStateException("Block " + blockId + " already terminated");
            }
            block.terminator = terminator;
        }

        void terminateIfMissing(int blockId, Terminator terminator) {
            BlockBuilder block = block(blockId);
            if (block.terminator == null) {
                block.terminator = terminator;
            }
        }

        int newBlock(int controlPathId) {
            blocks.add(new BlockBuilder(controlPathId));
            return blocks.size() - 1;
        }

        int nextControlPath(int parentPathId, Guard edgeGuard) {
            int id = controlPaths.size();
            controlPaths.add(new ControlPath(parentPathId, requireNonNull(edgeGuard, "edgeGuard is null")));
            return id;
        }

        int nextOpId() {
            return ops.size();
        }

        BlockBuilder block(int blockId) {
            return blocks.get(blockId);
        }

        static Set<String> optionValues(Node input) {
            Set<String> values = new TreeSet<>();
            for (Node child : input.children()) {
                Node option = child.unwrap();
                if (option.kind() == Kind.INPUT_OPTION) {
                    values.add(option.value().getString());
                }
            }
            return values;
        }

        static Spec inputSpec(Node node) {
            Set<String> values = optionValues(node);
            if (values.isEmpty()) {
                return Spec.OPEN_TEXT;
            }
            return node.kind() == Kind.INPUT_ENUM
                    ? Spec.choice(values)
                    : Spec.finiteMembership(values);
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
            this.name = requireNonNull(name, "name is null");
            this.spec = requireNonNull(spec, "spec is null");
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
            if (spec.kind() != other.spec.kind()) {
                return new SymbolSeed(name, Spec.OPEN_TEXT, false, tainted || other.tainted);
            }
            switch (spec.kind()) {
                case FINITE_SCALAR:
                    if (spec.subKind() == Spec.SubKind.BOOLEAN && other.spec.subKind() == Spec.SubKind.BOOLEAN) {
                        return new SymbolSeed(name, Spec.booleanSpec(), guardable && other.guardable, tainted || other.tainted);
                    }
                    if (spec.subKind() == Spec.SubKind.BOOLEAN || other.spec.subKind() == Spec.SubKind.BOOLEAN) {
                        return new SymbolSeed(name, Spec.OPEN_TEXT, false, tainted || other.tainted);
                    }
                    Set<String> choiceValues = new TreeSet<>(spec.values());
                    choiceValues.addAll(other.spec.values());
                    return new SymbolSeed(name,
                            spec.subKind() == Spec.SubKind.CHOICE || other.spec.subKind() == Spec.SubKind.CHOICE
                                    ? Spec.choice(choiceValues)
                                    : Spec.finiteText(choiceValues),
                            guardable && other.guardable,
                            tainted || other.tainted);
                case FINITE_MEMBERSHIP:
                    Set<String> items = new TreeSet<>(spec.values());
                    items.addAll(other.spec.values());
                    return new SymbolSeed(name,
                            Spec.finiteMembership(items),
                            guardable && other.guardable,
                            tainted || other.tainted);
                default:
                    return new SymbolSeed(name, spec, guardable && other.guardable, tainted || other.tainted);
            }
        }
    }

    private static final class ControlPath {
        static final int ROOT_ID = 0;
        static final ControlPath ROOT = new ControlPath(-1, null);

        private final int parentId;
        private final Guard edgeGuard;

        ControlPath(int parentId, Guard edgeGuard) {
            this.parentId = parentId;
            this.edgeGuard = edgeGuard;
        }
    }

    private static final class BlockBuilder {
        private final int controlPathId;
        private final List<Integer> ops = new ArrayList<>();
        private Terminator terminator;

        BlockBuilder(int controlPathId) {
            this.controlPathId = controlPathId;
        }
    }

    private static final class ConditionLowerer {
        private final Table symbols;
        private final Guards guards;
        private final Scope scope;

        ConditionLowerer(Table symbols, Guards guards, Scope scope) {
            this.symbols = symbols;
            this.guards = guards;
            this.scope = requireNonNull(scope, "scope is null");
        }

        Guard lower(Expression expression) {
            Expression original = requireNonNull(expression, "expression is null");
            if (original == Expression.TRUE) {
                return Guard.TRUE;
            }
            if (original == Expression.FALSE) {
                return Guard.FALSE;
            }
            Deque<ConditionValue> stack = new ArrayDeque<>();
            for (Token token : original.tokens()) {
                if (token.isVariable()) {
                    String key = scope.key(token.variable());
                    stack.push(ConditionValue.variable(findSymbol(key)));
                    continue;
                }
                if (token.isOperand()) {
                    stack.push(ConditionValue.literal(token.operand()));
                    continue;
                }
                ConditionValue value;
                switch (token.operator()) {
                    case NOT:
                        value = not(stack.pop());
                        break;
                    case AND:
                        value = and(stack.pop(), stack.pop());
                        break;
                    case OR:
                        value = or(stack.pop(), stack.pop());
                        break;
                    case EQUAL:
                        value = compare(true, stack.pop(), stack.pop());
                        break;
                    case NOT_EQUAL:
                        value = compare(false, stack.pop(), stack.pop());
                        break;
                    case CONTAINS:
                        value = contains(stack.pop(), stack.pop());
                        break;
                    default:
                        return residual(original);
                }
                if (value == null) {
                    return residual(original);
                }
                stack.push(value);
            }
            if (stack.size() != 1) {
                throw new IllegalStateException("Unexpected expression stack size: " + stack.size());
            }
            ConditionValue value = stack.pop();
            Guard guard = value.asGuard(guards);
            return guard != null ? guard : residual(original);
        }

        ConditionValue not(ConditionValue value) {
            Guard guard = value.asGuard(guards);
            return guard == null ? null : ConditionValue.guard(guards.not(guard));
        }

        ConditionValue and(ConditionValue right, ConditionValue left) {
            Guard leftGuard = left.asGuard(guards);
            Guard rightGuard = right.asGuard(guards);
            return leftGuard != null && rightGuard != null
                    ? ConditionValue.guard(guards.and(leftGuard, rightGuard))
                    : null;
        }

        ConditionValue or(ConditionValue right, ConditionValue left) {
            Guard leftGuard = left.asGuard(guards);
            Guard rightGuard = right.asGuard(guards);
            return leftGuard != null && rightGuard != null
                    ? ConditionValue.guard(guards.or(leftGuard, rightGuard))
                    : null;
        }

        ConditionValue compare(boolean equal, ConditionValue right, ConditionValue left) {
            Guard direct = compareGuard(left, right);
            if (direct == null) {
                direct = compareGuard(right, left);
            }
            return direct == null ? null : ConditionValue.guard(equal ? direct : guards.not(direct));
        }

        Guard compareGuard(ConditionValue symbolValue, ConditionValue literalValue) {
            if (symbolValue.symbol == null || literalValue.literal == null) {
                return null;
            }
            Symbol symbol = symbolValue.symbol;
            if (!symbol.guardable() || symbol.tainted()) {
                return null;
            }
            if (symbol.domain().kind() != Spec.Kind.FINITE_SCALAR) {
                return null;
            }
            if (literalValue.literal.type() == Value.Type.BOOLEAN) {
                return symbol.domain().booleanLike()
                        ? guards.eq(symbol.id(), String.valueOf(literalValue.literal.getBoolean()))
                        : null;
            }
            if (literalValue.literal.type() == Value.Type.STRING) {
                String literal = literalValue.literal.getString();
                return symbol.domain().values().contains(literal) ? guards.eq(symbol.id(), literal) : null;
            }
            return null;
        }

        ConditionValue contains(ConditionValue right, ConditionValue left) {
            Guard direct = containsGuard(left, right);
            if (direct == null) {
                direct = scalarAnyGuard(right, left);
            }
            return direct == null ? null : ConditionValue.guard(direct);
        }

        Guard containsGuard(ConditionValue symbolValue, ConditionValue literalValue) {
            if (symbolValue.symbol == null || literalValue.literal == null) {
                return null;
            }
            Symbol symbol = symbolValue.symbol;
            if (symbol.domain().kind() != Spec.Kind.FINITE_MEMBERSHIP || !symbol.guardable() || symbol.tainted()) {
                return null;
            }
            if (literalValue.literal.type() == Value.Type.STRING) {
                return guards.contains(symbol.id(), literalValue.literal.getString());
            }
            if (literalValue.literal.type() == Value.Type.LIST) {
                return guards.containsAll(symbol.id(), new TreeSet<>(literalValue.literal.getList()));
            }
            return null;
        }

        Guard scalarAnyGuard(ConditionValue symbolValue, ConditionValue literalValue) {
            if (symbolValue.symbol == null || literalValue.literal == null || literalValue.literal.type() != Value.Type.LIST) {
                return null;
            }
            Symbol symbol = symbolValue.symbol;
            if (!symbol.guardable() || symbol.tainted()) {
                return null;
            }
            if (symbol.domain().kind() != Spec.Kind.FINITE_SCALAR) {
                return null;
            }
            Guard result = Guard.FALSE;
            for (String item : literalValue.literal.getList()) {
                result = guards.or(result, guards.eq(symbol.id(), item));
            }
            return result;
        }

        Guard residual(Expression expression) {
            return guards.residualGuard(expression);
        }

        Symbol findSymbol(String name) {
            Integer id = symbols.findId(name);
            return id == null ? null : symbols.symbol(id);
        }
    }

    private static final class ConditionValue {
        private final Guard guard;
        private final Symbol symbol;
        private final Value<?> literal;

        ConditionValue(Guard guard, Symbol symbol, Value<?> literal) {
            this.guard = guard;
            this.symbol = symbol;
            this.literal = literal;
        }

        static ConditionValue guard(Guard guard) {
            return new ConditionValue(requireNonNull(guard, "guard is null"), null, null);
        }

        static ConditionValue variable(Symbol symbol) {
            return new ConditionValue(null, symbol, null);
        }

        static ConditionValue literal(Value<?> literal) {
            return new ConditionValue(null, null, literal);
        }

        Guard asGuard(Guards guards) {
            if (guard != null) {
                return guard;
            }
            if (symbol != null && symbol.guardable() && !symbol.tainted() && symbol.domain().booleanLike()) {
                return guards.eq(symbol.id(), "true");
            }
            if (literal != null && literal.type() == Value.Type.BOOLEAN) {
                return literal.getBoolean() ? Guard.TRUE : Guard.FALSE;
            }
            return null;
        }
    }

    private static final class Analyzer {
        private static final int EXACT_CASE_MAX = 16;
        private static final int EXACT_PROVENANCE_MAX = 16;

        private final Ir ir;
        private final Guards guards;
        private final Map<Integer, Map<FactState, Fact>> materializedFacts = new LinkedHashMap<>();
        private final Map<Map<Integer, FactState>, Map<Integer, Fact>> materializedEnvs = new IdentityHashMap<>();
        private final ExactProvenance[] blockProvenances;
        private List<Guard> blockPaths;

        Analyzer(Ir ir) {
            this.ir = ir;
            this.guards = ir.guards;
            this.blockProvenances = new ExactProvenance[ir.blocks.size()];
        }

        Analysis analyze() {
            ControlFlow control = analyzeControl();
            this.blockPaths = control.entryByBlock;
            FactFlow facts = analyzeFacts(control);
            return new Analysis(
                    materializeStates(control.entryByBlock, facts.entryByBlock),
                    materializeStates(control.beforeByOp, facts.beforeByOp),
                    materializeStates(control.beforeByOp, facts.afterByOp));
        }

        ControlFlow analyzeControl() {
            int blockCount = ir.blocks.size();
            boolean[] reachable = new boolean[blockCount];
            reachable[0] = true;
            Deque<Integer> work = new ArrayDeque<>();
            work.add(0);
            while (!work.isEmpty()) {
                int blockId = work.removeFirst();
                propagateControl(ir.blocks.get(blockId).terminator, reachable, work);
            }

            // keep the forward control pass reachability-only; lowered blocks carry their structured path context
            List<Guard> entries = materializeStructuredControlPaths(reachable);
            List<Guard> beforeByOp = new ArrayList<>(Collections.nCopies(ir.ops.size(), Guard.FALSE));
            for (int blockId = 0; blockId < blockCount; blockId++) {
                if (!reachable[blockId]) {
                    continue;
                }
                Guard entry = entries.get(blockId);
                Block block = ir.blocks.get(blockId);
                for (int opId : block.ops) {
                    beforeByOp.set(opId, entry);
                }
            }
            return new ControlFlow(entries, beforeByOp);
        }

        void propagateControl(Terminator terminator, boolean[] reachable, Deque<Integer> work) {
            switch (terminator.kind) {
                case GOTO:
                    enqueueControl(terminator.targetId, reachable, work);
                    break;
                case BRANCH:
                    enqueueControl(terminator.trueId, reachable, work);
                    enqueueControl(terminator.falseId, reachable, work);
                    break;
                case RETURN:
                case UNREACHABLE:
                default:
                    break;
            }
        }

        void enqueueControl(int blockId, boolean[] reachable, Deque<Integer> work) {
            if (!reachable[blockId]) {
                reachable[blockId] = true;
                work.addLast(blockId);
            }
        }

        List<Guard> materializeStructuredControlPaths(boolean[] reachable) {
            Guard[] guards = new Guard[ir.controlPaths.size()];
            guards[ControlPath.ROOT_ID] = Guard.TRUE;
            List<Guard> result = new ArrayList<>(reachable.length);
            for (int id = 0; id < reachable.length; id++) {
                if (!reachable[id]) {
                    result.add(Guard.FALSE);
                    continue;
                }
                int ctrlId = ir.blocks.get(id).controlPathId;
                if (ctrlId < 0) {
                    throw new IllegalStateException("Missing structured control path for block " + id);
                }
                result.add(materializeStructuredPath(ctrlId, guards));
            }
            return result;
        }

        Guard materializeStructuredPath(int controlPathId, Guard[] pathGuards) {
            Guard cached = pathGuards[controlPathId];
            if (cached != null) {
                return cached;
            }
            ControlPath controlPath = ir.controlPaths.get(controlPathId);
            if (controlPath.parentId < 0) {
                pathGuards[controlPathId] = Guard.TRUE;
                return Guard.TRUE;
            }
            Guard materialized = guards.and(materializeStructuredPath(controlPath.parentId, pathGuards),
                    controlPath.edgeGuard);
            pathGuards[controlPathId] = materialized;
            return materialized;
        }

        FactFlow analyzeFacts(ControlFlow control) {
            List<Map<Integer, FactState>> entries = new ArrayList<>(Collections.nCopies(ir.blocks.size(), null));
            List<Map<Integer, FactState>> beforeByOp = new ArrayList<>(Collections.nCopies(ir.ops.size(), null));
            List<Map<Integer, FactState>> afterByOp = new ArrayList<>(Collections.nCopies(ir.ops.size(), null));
            entries.set(0, Map.of());
            Deque<Integer> work = new ArrayDeque<>();
            work.add(0);
            while (!work.isEmpty()) {
                int blockId = work.removeFirst();
                if (control.entryByBlock.get(blockId).equals(Guard.FALSE)) {
                    continue;
                }
                Map<Integer, FactState> state = entries.get(blockId);
                if (state == null) {
                    continue;
                }
                Map<Integer, FactState> current = state;
                Block block = ir.blocks.get(blockId);
                for (int opId : block.ops) {
                    Op op = ir.ops.get(opId);
                    Guard currentGuard = control.beforeByOp.get(opId);
                    beforeByOp.set(opId, current);
                    current = transfer(op, currentGuard, current);
                    afterByOp.set(opId, current);
                }
                propagateFacts(block.terminator, current, control, entries, work);
            }
            return new FactFlow(entries, beforeByOp, afterByOp);
        }

        Map<Integer, FactState> transfer(Op op, Guard currentGuard, Map<Integer, FactState> state) {
            switch (op.kind) {
                case DECLARE_INPUT: {
                    FactState declared = new FactState(
                            provenance(op.blockId),
                            LatticeValue.top(ir.symbols.symbol(op.symbolId).domain()));
                    FactState existing = state.get(op.symbolId);
                    return define(state,
                            op.symbolId,
                            existing == null ? declared : mergeFact(op.symbolId, existing, declared));
                }
                case DEFINE_VALUE:
                    Symbol symbol = ir.symbols.symbol(op.symbolId);
                    return define(state, op.symbolId, evaluateFact(op, currentGuard, symbol, state));
                case EMIT:
                default:
                    return state;
            }
        }

        void propagateFacts(Terminator terminator,
                                    Map<Integer, FactState> current,
                                    ControlFlow control,
                                    List<Map<Integer, FactState>> entries,
                                    Deque<Integer> work) {
            switch (terminator.kind) {
                case GOTO:
                    enqueueFacts(terminator.targetId, current, control, entries, work);
                    break;
                case BRANCH:
                    enqueueFacts(terminator.trueId, current, control, entries, work);
                    enqueueFacts(terminator.falseId, current, control, entries, work);
                    break;
                case RETURN:
                case UNREACHABLE:
                default:
                    break;
            }
        }

        void enqueueFacts(int blockId,
                                  Map<Integer, FactState> incoming,
                                  ControlFlow control,
                                  List<Map<Integer, FactState>> entries,
                                  Deque<Integer> work) {
            if (control.entryByBlock.get(blockId).equals(Guard.FALSE)) {
                return;
            }
            Map<Integer, FactState> current = entries.get(blockId);
            Map<Integer, FactState> merged = mergeEnv(current, incoming);
            if (merged != current) {
                entries.set(blockId, merged);
                work.addLast(blockId);
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
                int symbolId = entry.getKey();
                FactState left = current.get(symbolId);
                FactState right = entry.getValue();
                FactState merged;
                if (left == null) {
                    merged = right;
                } else if (left == right || left.equals(right)) {
                    merged = left;
                } else {
                    merged = mergeFact(symbolId, left, right);
                }
                if (merged != left) {
                    if (next == null) {
                        next = new LinkedHashMap<>(current);
                    }
                    next.put(symbolId, merged);
                }
            }
            return next == null ? current : next;
        }

        Map<Integer, FactState> define(Map<Integer, FactState> state, int symbolId, FactState fact) {
            FactState existing = state.get(symbolId);
            if (Objects.equals(existing, fact)) {
                return state;
            }
            Map<Integer, FactState> env = new LinkedHashMap<>(state);
            env.put(symbolId, fact);
            return env;
        }

        FactState evaluateFact(Op op, Guard currentGuard, Symbol symbol, Map<Integer, FactState> state) {
            LatticeValue value = evaluateValue(op.expression, symbol, state);
            Value<?> exactValue = exactValue(op.expression, currentGuard, state);
            return exactValue == null
                    ? new FactState(provenance(op.blockId), value)
                    : FactState.exact(op.blockId, value, exactValue);
        }

        LatticeValue evaluateValue(Expression expression, Symbol symbol, Map<Integer, FactState> state) {
            List<Token> tokens = expression.tokens();
            if (tokens.size() == 1) {
                Token token = tokens.get(0);
                if (token.isOperand()) {
                    return literalValue(symbol.domain(), token.operand());
                }
                if (token.isVariable()) {
                    Integer symbolId = ir.symbols.findId(token.variable());
                    if (symbolId != null) {
                        FactState fact = state.get(symbolId);
                        if (fact != null) {
                            return fact.value;
                        }
                    }
                }
            }
            return LatticeValue.top(symbol.domain());
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
                Integer symbolId = ir.symbols.findId(token.variable());
                if (symbolId == null) {
                    return null;
                }
                Symbol symbol = ir.symbols.symbol(symbolId);
                FactState fact = state.get(symbolId);
                if (fact == null) {
                    return null;
                }
                Value<?> exactFromValue = exactFromValue(symbol.domain(), fact, currentGuard);
                if (exactFromValue != null) {
                    return exactFromValue;
                }
                if (fact.exactCases.isEmpty() || !implies(currentGuard, fact.exactCoverage)) {
                    return null;
                }
                Value<?> value = null;
                for (ExactCaseState exactCase : fact.exactCases) {
                    if (!overlaps(currentGuard, exactCase.provenance)) {
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
                return spec.booleanLike() ? Value.of(Boolean.parseBoolean(scalar)) : Value.of(scalar);
            }
            if (fact.value.kind() == LatticeValue.Kind.OPEN_TEXT && fact.value.sample() != null) {
                return Value.of(fact.value.sample());
            }
            return null;
        }

        FactState mergeFact(int symbolId, FactState left, FactState right) {
            ExactProvenance definedUnder = ExactProvenance.merge(left.definedUnder, right.definedUnder, -1);
            LatticeValue value = LatticeValue.join(left.value, right.value);
            if (left.exactCases.isEmpty()) {
                return right.exactCases.isEmpty()
                        ? new FactState(definedUnder, value)
                        : new FactState(definedUnder, value, right.exactCoverage, right.exactCases);
            }
            if (right.exactCases.isEmpty()) {
                return new FactState(definedUnder, value, left.exactCoverage, left.exactCases);
            }
            ExactProvenance coverage = ExactProvenance.merge(left.exactCoverage, right.exactCoverage, EXACT_PROVENANCE_MAX);
            if (coverage == null) {
                return new FactState(definedUnder, value);
            }
            Symbol symbol = ir.symbols.symbol(symbolId);
            List<ExactCaseState> merged = new ArrayList<>();
            for (ExactCaseState exactCase : left.exactCases) {
                if (!mergeExactCase(symbol, merged, exactCase)) {
                    return new FactState(definedUnder, value);
                }
            }
            for (ExactCaseState exactCase : right.exactCases) {
                if (!mergeExactCase(symbol, merged, exactCase)) {
                    return new FactState(definedUnder, value);
                }
            }
            return new FactState(definedUnder, value, coverage, merged);
        }

        boolean mergeExactCase(Symbol symbol, List<ExactCaseState> merged, ExactCaseState next) {
            for (int i = 0; i < merged.size(); i++) {
                ExactCaseState current = merged.get(i);
                if (Domain.sameExactValue(symbol.domain(), current.value, next.value)) {
                    ExactProvenance provenance = ExactProvenance.merge(current.provenance, next.provenance,
                            EXACT_PROVENANCE_MAX);
                    if (provenance == null) {
                        return false;
                    }
                    merged.set(i, new ExactCaseState(current.value, provenance));
                    return true;
                }
            }
            if (merged.size() >= EXACT_CASE_MAX) {
                return false;
            }
            merged.add(next);
            return true;
        }

        List<State> materializeStates(List<Guard> paths, List<Map<Integer, FactState>> envs) {
            List<State> result = new ArrayList<>(paths.size());
            for (int i = 0; i < paths.size(); i++) {
                result.add(new State(paths.get(i), materializeEnv(envs.get(i))));
            }
            return result;
        }

        Map<Integer, Fact> materializeEnv(Map<Integer, FactState> env) {
            if (env == null || env.isEmpty()) {
                return Map.of();
            }
            Map<Integer, Fact> cached = materializedEnvs.get(env);
            if (cached != null) {
                return cached;
            }
            Map<Integer, Fact> materialized = new LinkedHashMap<>();
            for (Map.Entry<Integer, FactState> entry : env.entrySet()) {
                materialized.put(entry.getKey(), materializeFact(entry.getKey(), entry.getValue()));
            }
            materializedEnvs.put(env, materialized);
            return materialized;
        }

        private Fact materializeFact(int symbolId, FactState fact) {
            Map<FactState, Fact> cacheBySymbol = materializedFacts.computeIfAbsent(symbolId, unused -> new IdentityHashMap<>());
            Fact cached = cacheBySymbol.get(fact);
            if (cached != null) {
                return cached;
            }
            Symbol symbol = ir.symbols.symbol(symbolId);
            List<Fact.ExactCase> exactCases = new ArrayList<>(fact.exactCases.size());
            for (ExactCaseState exactCase : fact.exactCases) {
                exactCases.add(new Fact.ExactCase(exactCase.value, materializeGuard(exactCase.provenance), symbol.domain()));
            }
            Fact materialized = new Fact(materializeGuard(fact.definedUnder), fact.value, exactCases);
            cacheBySymbol.put(fact, materialized);
            return materialized;
        }

        boolean implies(Guard left, ExactProvenance right) {
            return right.blockIds.length != 0 && guards.implies(left, materializeGuard(right));
        }

        boolean overlaps(Guard left, ExactProvenance right) {
            return right.blockIds.length != 0 && !guards.and(left, materializeGuard(right)).equals(Guard.FALSE);
        }

        ExactProvenance provenance(int blockId) {
            ExactProvenance cached = blockProvenances[blockId];
            if (cached != null) {
                return cached;
            }
            ExactProvenance created = new ExactProvenance(blockId);
            blockProvenances[blockId] = created;
            return created;
        }

        Guard materializeGuard(ExactProvenance provenance) {
            Guard cached = provenance.materializedGuard;
            if (cached != null) {
                return cached;
            }
            if (provenance.blockIds.length == 0) {
                return Guard.FALSE;
            }
            Guard materialized = blockPaths.get(provenance.blockIds[0]);
            for (int i = 1; i < provenance.blockIds.length; i++) {
                materialized = guards.or(materialized, blockPaths.get(provenance.blockIds[i]));
            }
            provenance.materializedGuard = materialized;
            return materialized;
        }

        private static final class ControlFlow {
            private final List<Guard> entryByBlock;
            private final List<Guard> beforeByOp;

            ControlFlow(List<Guard> entryByBlock, List<Guard> beforeByOp) {
                this.entryByBlock = entryByBlock;
                this.beforeByOp = beforeByOp;
            }
        }

        private static final class FactFlow {
            private final List<Map<Integer, FactState>> entryByBlock;
            private final List<Map<Integer, FactState>> beforeByOp;
            private final List<Map<Integer, FactState>> afterByOp;

            FactFlow(List<Map<Integer, FactState>> entryByBlock,
                     List<Map<Integer, FactState>> beforeByOp,
                     List<Map<Integer, FactState>> afterByOp) {
                this.entryByBlock = entryByBlock;
                this.beforeByOp = beforeByOp;
                this.afterByOp = afterByOp;
            }
        }

        private static final class FactState {
            private final ExactProvenance definedUnder;
            private final LatticeValue value;
            private final ExactProvenance exactCoverage;
            private final List<ExactCaseState> exactCases;

            FactState(ExactProvenance definedUnder, LatticeValue value) {
                this(definedUnder, value, ExactProvenance.EMPTY, List.of());
            }

            FactState(ExactProvenance definedUnder,
                      LatticeValue value,
                      ExactProvenance exactCoverage,
                      List<ExactCaseState> exactCases) {
                this.definedUnder = definedUnder;
                this.value = value;
                this.exactCoverage = exactCoverage;
                this.exactCases = exactCases;
            }

            static FactState exact(int blockId, LatticeValue value, Value<?> exactValue) {
                ExactProvenance definedUnder = new ExactProvenance(blockId);
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
            private final ExactProvenance provenance;

            ExactCaseState(Value<?> value, ExactProvenance provenance) {
                this.value = value;
                this.provenance = provenance;
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
                return provenance.equals(other.provenance)
                        && Value.isEqual(value, other.value);
            }
        }

        private static final class ExactProvenance {
            private static final ExactProvenance EMPTY = new ExactProvenance();

            private final int[] blockIds;
            private Guard materializedGuard;

            ExactProvenance(int... blockIds) {
                this.blockIds = blockIds;
            }

            static ExactProvenance merge(ExactProvenance left, ExactProvenance right, int maxEntries) {
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
                return new ExactProvenance(size == merged.length ? merged : Arrays.copyOf(merged, size));
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
                if (!(o instanceof ExactProvenance)) {
                    return false;
                }
                ExactProvenance other = (ExactProvenance) o;
                return Arrays.equals(blockIds, other.blockIds);
            }
        }

        static LatticeValue literalValue(Spec spec, Value<?> literal) {
            switch (literal.type()) {
                case BOOLEAN: {
                    String scalar = String.valueOf(literal.getBoolean());
                    if (spec.kind() == Spec.Kind.FINITE_SCALAR && spec.values().contains(scalar)) {
                        return LatticeValue.finiteScalar(spec.mask(scalar));
                    }
                    return spec.kind() == Spec.Kind.OPEN_TEXT ? LatticeValue.openText(scalar) : LatticeValue.top(spec);
                }
                case STRING:
                    if (spec.kind() == Spec.Kind.FINITE_SCALAR && spec.values().contains(literal.getString())) {
                        return LatticeValue.finiteScalar(spec.mask(literal.getString()));
                    }
                    return LatticeValue.openText(literal.getString());
                case LIST:
                    if (spec.kind() == Spec.Kind.FINITE_MEMBERSHIP) {
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
