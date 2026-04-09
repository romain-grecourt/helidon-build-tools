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
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import io.helidon.build.archetype.engine.v2.Context.Scope;
import io.helidon.build.archetype.engine.v2.Domain.Guard;
import io.helidon.build.archetype.engine.v2.Domain.Guards;
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
    private Analysis analysis;
    private Model model;

    Flow(Scope scope) {
        this.scope = requireNonNull(scope, "scope is null");
    }

    void process(Node root) {
        requireNonNull(root, "root is null");
        ir = lower(root);
        analysis = analyze(ir);
        model = project(root);
    }

    void process(Ir ir, Node root) {
        this.ir = requireNonNull(ir, "ir is null");
        analysis = analyze(ir);
        model = project(root);
    }

    Ir ir() {
        return ir;
    }

    Analysis analysis() {
        return analysis;
    }

    Model model() {
        return model;
    }

    private Ir lower(Node root) {
        return new Lowerer(scope).lower(root);
    }

    private Analysis analyze(Ir ir) {
        requireNonNull(ir, "ir is null");
        return new Analyzer(ir).analyze();
    }

    private Model project(Node root) {
        requireNonNull(root, "root is null");
        return new Projector(ir, analysis, root, scope).project();
    }

    static final class Ir {
        private final List<Block> blocks;
        private final Table symbols;
        private final List<Op> ops;
        private final Guards guards;

        Ir(List<Block> blocks, Table symbols, List<Op> ops, Guards guards) {
            this.blocks = List.copyOf(blocks);
            this.symbols = requireNonNull(symbols, "symbols is null");
            this.ops = List.copyOf(ops);
            this.guards = requireNonNull(guards, "guards is null");
        }

        List<Block> blocks() {
            return blocks;
        }

        Table symbols() {
            return symbols;
        }

        List<Op> ops() {
            return ops;
        }

        Guards guards() {
            return guards;
        }
    }

    static final class Block {
        private final int id;
        private final List<Integer> ops;
        private final Terminator terminator;

        Block(int id, List<Integer> ops, Terminator terminator) {
            this.id = id;
            this.ops = List.copyOf(ops);
            this.terminator = requireNonNull(terminator, "terminator is null");
        }

        int id() {
            return id;
        }

        List<Integer> ops() {
            return ops;
        }

        Terminator terminator() {
            return terminator;
        }
    }

    static final class Op {
        enum Kind {
            DECLARE_INPUT,
            DECLARE_OPTION,
            DEFINE_VALUE,
            RECORD_USE,
            EMIT,
            PHI
        }

        enum EmitKind {
            STEP,
            FILE,
            MODEL
        }

        private final int id;
        private final int blockId;
        private final SourceAnchor source;
        private final Kind kind;
        private final int symbolId;
        private final int refId;
        private final Expression expression;
        private final EmitKind emitKind;
        private final int[] blockIds;
        private final int[] symbolIds;

        private Op(int id,
                   int blockId,
                   SourceAnchor source,
                   Kind kind,
                   int symbolId,
                   int refId,
                   Expression expression,
                   EmitKind emitKind,
                   int[] blockIds,
                   int[] symbolIds) {
            this.id = id;
            this.blockId = blockId;
            this.source = requireNonNull(source, "source is null");
            this.kind = requireNonNull(kind, "kind is null");
            this.symbolId = symbolId;
            this.refId = refId;
            this.expression = expression;
            this.emitKind = emitKind;
            this.blockIds = blockIds == null ? null : blockIds.clone();
            this.symbolIds = symbolIds == null ? null : symbolIds.clone();
        }

        static Op declareInput(int id, int blockId, SourceAnchor source, int symbolId, int refId) {
            return new Op(id, blockId, source, Kind.DECLARE_INPUT, symbolId, refId, null, null, null, null);
        }

        static Op declareOption(int id, int blockId, SourceAnchor source, int symbolId, int refId) {
            return new Op(id, blockId, source, Kind.DECLARE_OPTION, symbolId, refId, null, null, null, null);
        }

        static Op defineValue(int id, int blockId, SourceAnchor source, int symbolId, Expression expression) {
            return new Op(id, blockId, source, Kind.DEFINE_VALUE, symbolId, -1,
                    requireNonNull(expression, "expression is null"), null, null, null);
        }

        static Op recordUse(int id, int blockId, SourceAnchor source, int symbolId, int refId) {
            return new Op(id, blockId, source, Kind.RECORD_USE, symbolId, refId, null, null, null, null);
        }

        static Op emit(int id, int blockId, SourceAnchor source, EmitKind emitKind, int refId) {
            return new Op(id, blockId, source, Kind.EMIT, -1, refId, null,
                    requireNonNull(emitKind, "emitKind is null"), null, null);
        }

        static Op phi(int id, int blockId, SourceAnchor source, int symbolId, int[] blockIds, int[] symbolIds) {
            return new Op(id, blockId, source, Kind.PHI, symbolId, -1, null, null, blockIds, symbolIds);
        }

        int id() {
            return id;
        }

        int blockId() {
            return blockId;
        }

        SourceAnchor source() {
            return source;
        }

        Kind kind() {
            return kind;
        }

        int symbolId() {
            return symbolId;
        }

        int refId() {
            return refId;
        }

        Expression expression() {
            return expression;
        }

        EmitKind emitKind() {
            return emitKind;
        }

        int[] blockIds() {
            return blockIds == null ? null : blockIds.clone();
        }

        int[] symbolIds() {
            return symbolIds == null ? null : symbolIds.clone();
        }
    }

    static final class Terminator {
        enum Kind {
            GOTO,
            BRANCH,
            RETURN,
            UNREACHABLE
        }

        private final Kind kind;
        private final SourceAnchor source;
        private final Guard guard;
        private final int targetId;
        private final int trueId;
        private final int falseId;

        private Terminator(Kind kind, SourceAnchor source, Guard guard, int targetId, int trueId, int falseId) {
            this.kind = requireNonNull(kind, "kind is null");
            this.source = requireNonNull(source, "source is null");
            this.guard = guard;
            this.targetId = targetId;
            this.trueId = trueId;
            this.falseId = falseId;
        }

        static Terminator jump(SourceAnchor source, int targetId) {
            return new Terminator(Kind.GOTO, source, null, targetId, -1, -1);
        }

        static Terminator branch(SourceAnchor source, Guard guard, int trueId, int falseId) {
            return new Terminator(Kind.BRANCH, source, requireNonNull(guard, "guard is null"), -1, trueId, falseId);
        }

        static Terminator ret(SourceAnchor source) {
            return new Terminator(Kind.RETURN, source, null, -1, -1, -1);
        }

        static Terminator unreachable(SourceAnchor source) {
            return new Terminator(Kind.UNREACHABLE, source, null, -1, -1, -1);
        }

        Kind kind() {
            return kind;
        }

        SourceAnchor source() {
            return source;
        }

        Guard guard() {
            return guard;
        }

        int targetId() {
            return targetId;
        }

        int trueId() {
            return trueId;
        }

        int falseId() {
            return falseId;
        }
    }

    static final class SourceAnchor {
        private final Node node;
        private final String scope;

        SourceAnchor(Node node, String scope) {
            this.node = requireNonNull(node, "node is null");
            this.scope = requireNonNull(scope, "scope is null");
        }

        Node node() {
            return node;
        }

        String scope() {
            return scope;
        }
    }

    static final class State {
        private final Guard path;
        private final Map<Integer, Fact> env;

        State(Guard path, Map<Integer, Fact> env) {
            this.path = requireNonNull(path, "path is null");
            this.env = Map.copyOf(env);
        }

        Guard path() {
            return path;
        }

        Map<Integer, Fact> env() {
            return env;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof State)) {
                return false;
            }
            State other = (State) o;
            return (path == other.path || path.equals(other.path))
                    && (env == other.env || env.equals(other.env));
        }

        @Override
        public int hashCode() {
            return 31 * path.hashCode() + env.hashCode();
        }
    }

    static final class Use {
        private final int id;
        private final int symbolId;
        private final Guard guard;

        Use(int id, int symbolId, Guard guard) {
            this.id = id;
            this.symbolId = symbolId;
            this.guard = requireNonNull(guard, "guard is null");
        }

        int id() {
            return id;
        }

        int symbolId() {
            return symbolId;
        }

        Guard guard() {
            return guard;
        }
    }

    static final class Analysis {
        private final List<State> entryByBlock;
        private final List<State> exitByBlock;
        private final List<State> beforeByOp;
        private final List<State> afterByOp;
        private final List<Use> usesById;

        Analysis(List<State> entryByBlock,
                 List<State> exitByBlock,
                 List<State> beforeByOp,
                 List<State> afterByOp,
                 List<Use> usesById) {
            this.entryByBlock = List.copyOf(entryByBlock);
            this.exitByBlock = List.copyOf(exitByBlock);
            this.beforeByOp = List.copyOf(beforeByOp);
            this.afterByOp = List.copyOf(afterByOp);
            this.usesById = List.copyOf(usesById);
        }

        List<State> entryByBlock() {
            return entryByBlock;
        }

        List<State> exitByBlock() {
            return exitByBlock;
        }

        List<State> beforeByOp() {
            return beforeByOp;
        }

        List<State> afterByOp() {
            return afterByOp;
        }

        List<Use> usesById() {
            return usesById;
        }
    }

    static final class Model {
        private final Ir ir;
        private final Analysis analysis;
        private final Scope scope;
        private final Map<Node, NodeFacts> nodes;
        private final List<SymbolInfo> symbols;

        Model(Ir ir, Analysis analysis, Scope scope, Map<Node, NodeFacts> nodes, List<SymbolInfo> symbols) {
            this.ir = requireNonNull(ir, "ir is null");
            this.analysis = requireNonNull(analysis, "analysis is null");
            this.scope = requireNonNull(scope, "scope is null");
            this.nodes = Collections.unmodifiableMap(new IdentityHashMap<>(nodes));
            this.symbols = List.copyOf(symbols);
        }

        Ir ir() {
            return ir;
        }

        Analysis analysis() {
            return analysis;
        }

        Guards guards() {
            return ir.guards();
        }

        Table symbols() {
            return ir.symbols();
        }

        Guard trueGuard() {
            return ir.guards().trueGuard();
        }

        Guard falseGuard() {
            return ir.guards().falseGuard();
        }

        NodeFacts node(Node node) {
            return nodes.get(node);
        }

        String key(Node node) {
            return node == null ? scope.key() : node(node).key();
        }

        Scope scope(Node node) {
            if (node == null) {
                return scope;
            }
            String key = key(node);
            return key.isEmpty() ? scope : scope.get("~" + key);
        }

        Guard renderGuard(Node node) {
            return node == null ? trueGuard() : node(node).renderGuard();
        }

        Guard activeGuard(Node node) {
            return node == null ? trueGuard() : node(node).activeGuard();
        }

        State before(Node node) {
            return node == null ? analysis.entryByBlock().get(0) : node(node).before();
        }

        SymbolInfo symbol(int symbolId) {
            return symbols.get(symbolId);
        }

        SymbolInfo symbol(String name) {
            Integer symbolId = ir.symbols().findId(name);
            return symbolId == null ? null : symbols.get(symbolId);
        }

        Integer findSymbolId(String name) {
            return ir.symbols().findId(name);
        }

        Guard lower(Expression expression, Scope scope) {
            return new ConditionLowerer(ir.symbols(), ir.guards(), scope).lower(expression);
        }

        Expression expression(Guard guard, Scope scope) {
            return ir.guards().toExpression(guard, scope).reduce();
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
            if (fact == null || !guards().implies(required, fact.definedUnder())) {
                return Value.empty();
            }
            Value<?> exact = exactValue(fact, required, valueType(key));
            return exact != null ? exact : singletonValue(fact.value());
        }

        private Fact fact(Node node, String key) {
            State before = before(node);
            if (before.env().isEmpty()) {
                return null;
            }
            Integer symbolId = findSymbolId(key);
            if (symbolId == null && !key.startsWith("~")) {
                symbolId = findSymbolId("~" + key);
            }
            return symbolId == null ? null : before.env().get(symbolId);
        }

        private Value.Type valueType(String key) {
            SymbolInfo info = symbol(key);
            if (info == null && !key.startsWith("~")) {
                info = symbol("~" + key);
            }
            if (info == null) {
                return Value.Type.EMPTY;
            }
            switch (info.symbol().domain().kind()) {
                case BOOLEAN:
                    return Value.Type.BOOLEAN;
                case MEMBERSHIP:
                    return Value.Type.LIST;
                case CHOICE:
                case OPEN_TEXT:
                case FINITE_TEXT:
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
            Guard coverage = falseGuard();
            for (Fact.ExactCase exactCase : fact.exactCases()) {
                Guard overlap = guards().and(required, exactCase.guard());
                if (overlap.equals(falseGuard())) {
                    continue;
                }
                if (exact == null) {
                    exact = type == Value.Type.EMPTY ? exactCase.value() : Value.typed(exactCase.value(), type);
                } else if (!Value.isEqual(exact, exactCase.value())) {
                    return null;
                }
                coverage = guards().or(coverage, overlap);
            }
            return exact != null && guards().implies(required, coverage) ? exact : null;
        }

        private Value<?> singletonValue(Domain.Value value) {
            if (value instanceof Domain.Value.BooleanSet) {
                Set<Boolean> values = ((Domain.Value.BooleanSet) value).values();
                return values.size() == 1 ? Value.of(values.iterator().next()) : Value.empty();
            }
            if (value instanceof Domain.Value.ChoiceSet) {
                Set<String> values = ((Domain.Value.ChoiceSet) value).values();
                return values.size() == 1 ? Value.of(values.iterator().next()) : Value.empty();
            }
            if (value instanceof Domain.Value.FiniteText) {
                Set<String> values = ((Domain.Value.FiniteText) value).values();
                return values.size() == 1 ? Value.of(values.iterator().next()) : Value.empty();
            }
            if (value instanceof Domain.Value.OpenText) {
                String sample = ((Domain.Value.OpenText) value).sample();
                return sample == null ? Value.empty() : Value.of(sample);
            }
            return Value.empty();
        }
    }

    static final class NodeFacts {
        private final State before;
        private final String key;
        private final Guard renderGuard;
        private final Guard activeGuard;

        NodeFacts(State before, String key, Guard renderGuard, Guard activeGuard) {
            this.before = requireNonNull(before, "before is null");
            this.key = requireNonNull(key, "key is null");
            this.renderGuard = requireNonNull(renderGuard, "renderGuard is null");
            this.activeGuard = requireNonNull(activeGuard, "activeGuard is null");
        }

        State before() {
            return before;
        }

        String key() {
            return key;
        }

        Guard renderGuard() {
            return renderGuard;
        }

        Guard activeGuard() {
            return activeGuard;
        }
    }

    static final class SymbolInfo {
        private final Symbol symbol;
        private final Guard definition;
        private final Map<String, Guard> availabilityByValue;

        SymbolInfo(Symbol symbol, Guard definition, Map<String, Guard> availabilityByValue) {
            this.symbol = requireNonNull(symbol, "symbol is null");
            this.definition = definition;
            this.availabilityByValue = Map.copyOf(availabilityByValue);
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

    private static final class Projector {
        private final Ir ir;
        private final Analysis analysis;
        private final Node root;
        private final Scope scope;
        private final Guards guards;
        private final Map<Node, List<Integer>> opsByNode = new IdentityHashMap<>();
        private final Map<Node, BranchInfo> branchesByNode = new IdentityHashMap<>();
        private final List<SymbolInfoBuilder> symbolInfos = new ArrayList<>();

        private Projector(Ir ir, Analysis analysis, Node root, Scope scope) {
            this.ir = ir;
            this.analysis = analysis;
            this.root = root;
            this.scope = scope;
            this.guards = ir.guards();
            for (Symbol symbol : ir.symbols().symbols()) {
                symbolInfos.add(new SymbolInfoBuilder(symbol));
            }
        }

        private Model project() {
            indexAnchors();
            scanSymbols();
            Map<Node, NodeFacts> nodes = new IdentityHashMap<>();
            projectNode(root, scope, analysis.entryByBlock().get(0), nodes);
            List<SymbolInfo> builtSymbols = new ArrayList<>(symbolInfos.size());
            for (SymbolInfoBuilder builder : symbolInfos) {
                builtSymbols.add(builder.build(guards));
            }
            return new Model(ir, analysis, scope, nodes, builtSymbols);
        }

        private void indexAnchors() {
            for (Op op : ir.ops()) {
                opsByNode.computeIfAbsent(op.source().node(), key -> new ArrayList<>()).add(op.id());
            }
            for (Block block : ir.blocks()) {
                Terminator terminator = block.terminator();
                if (terminator.kind() != Terminator.Kind.BRANCH) {
                    continue;
                }
                int joinId = terminator.falseId();
                Terminator falseTerminator = ir.blocks().get(terminator.falseId()).terminator();
                if (falseTerminator.kind() == Terminator.Kind.GOTO) {
                    joinId = falseTerminator.targetId();
                }
                branchesByNode.put(terminator.source().node(), new BranchInfo(terminator.trueId(), joinId));
            }
        }

        private void scanSymbols() {
            for (Op op : ir.ops()) {
                State before = analysis.beforeByOp().get(op.id());
                switch (op.kind()) {
                    case DECLARE_INPUT: {
                        SymbolInfoBuilder builder = symbolInfos.get(op.symbolId());
                        builder.addDefinition(before.path(), guards);
                        if (builder.symbol.domain().kind() == Spec.Kind.BOOLEAN) {
                            builder.addAvailability("true", before.path(), guards);
                            builder.addAvailability("false", before.path(), guards);
                        }
                        break;
                    }
                    case DECLARE_OPTION: {
                        SymbolInfoBuilder builder = symbolInfos.get(op.symbolId());
                        builder.addAvailability(op.source().node().value().getString(), before.path(), guards);
                        break;
                    }
                    case DEFINE_VALUE: {
                        SymbolInfoBuilder builder = symbolInfos.get(op.symbolId());
                        Fact fact = analysis.afterByOp().get(op.id()).env().get(op.symbolId());
                        if (fact != null) {
                            builder.addDefinition(fact.definedUnder(), guards);
                            recordAvailability(builder, fact);
                        }
                        break;
                    }
                    default:
                        break;
                }
            }
        }

        private void recordAvailability(SymbolInfoBuilder builder, Fact fact) {
            if (!fact.exactCases().isEmpty()) {
                for (Fact.ExactCase exactCase : fact.exactCases()) {
                    recordExactAvailability(builder, exactCase);
                }
                return;
            }
            switch (builder.symbol.domain().kind()) {
                case BOOLEAN:
                    if (fact.value() instanceof Domain.Value.BooleanSet) {
                        for (boolean value : ((Domain.Value.BooleanSet) fact.value()).values()) {
                            builder.addAvailability(String.valueOf(value), fact.definedUnder(), guards);
                        }
                    }
                    break;
                case CHOICE:
                    if (fact.value() instanceof Domain.Value.ChoiceSet) {
                        for (String value : ((Domain.Value.ChoiceSet) fact.value()).values()) {
                            builder.addAvailability(value, fact.definedUnder(), guards);
                        }
                    }
                    break;
                case FINITE_TEXT:
                    if (fact.value() instanceof Domain.Value.FiniteText) {
                        for (String value : ((Domain.Value.FiniteText) fact.value()).values()) {
                            builder.addAvailability(value, fact.definedUnder(), guards);
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        private void recordExactAvailability(SymbolInfoBuilder builder, Fact.ExactCase exactCase) {
            switch (builder.symbol.domain().kind()) {
                case BOOLEAN:
                case CHOICE:
                case FINITE_TEXT:
                    String scalar = exactCase.scalarLiteral();
                    if (scalar != null) {
                        builder.addAvailability(scalar, exactCase.guard(), guards);
                    }
                    break;
                case MEMBERSHIP:
                    if (exactCase.value().type() == Value.Type.LIST) {
                        for (String value : exactCase.value().getList()) {
                            builder.addAvailability(value, exactCase.guard(), guards);
                        }
                    }
                    break;
                default:
                    break;
            }
        }

        private State projectNode(Node node, Scope scope, State current, Map<Node, NodeFacts> nodes) {
            Scope childScope = childScope(scope, node);
            Scope nodeScope = node.kind().isInput() ? childScope : scope;
            List<Integer> opIds = opsByNode.getOrDefault(node, List.of());
            State before = opIds.isEmpty() ? current : constrainPath(analysis.beforeByOp().get(opIds.get(0)), current.path());
            State afterOps = opIds.isEmpty() ? before : constrainPath(analysis.afterByOp().get(opIds.get(opIds.size() - 1)),
                    current.path());
            BranchInfo branch = branchesByNode.get(node);
            State branchEntry = branch == null ? null : constrainPath(analysis.entryByBlock().get(branch.trueId), current.path());
            Guard activeGuard = branch != null
                    ? activeGuard(node, nodeScope, branchEntry)
                    : activeGuard(node, nodeScope, afterOps);
            nodes.put(node, new NodeFacts(before, nodeKey(node, scope, childScope), before.path(), activeGuard));

            Scope descendantScope = node.kind().isInput() ? childScope : scope;
            if (branch != null) {
                State cursor = constrainPath(branchEntry, activeGuard);
                for (Node child : node.children()) {
                    cursor = projectNode(child, descendantScope, cursor, nodes);
                }
                return constrainPath(analysis.entryByBlock().get(branch.joinId), current.path());
            }

            State cursor = afterOps;
            for (Node child : node.children()) {
                cursor = projectNode(child, descendantScope, cursor, nodes);
            }
            return cursor;
        }

        private String nodeKey(Node node, Scope scope, Scope childScope) {
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

        private State constrainPath(State state, Guard required) {
            if (state.path().equals(required)) {
                return state;
            }
            Guard constrained = guards.and(state.path(), required);
            return constrained.equals(state.path()) ? state : new State(constrained, state.env());
        }

        private Guard activeGuard(Node node, Scope nodeScope, State afterOps) {
            Guard control = controlGuard(node, nodeScope, afterOps);
            return control == null ? afterOps.path() : guards.and(afterOps.path(), control);
        }

        private Guard controlGuard(Node node, Scope nodeScope, State state) {
            switch (node.kind()) {
                case CONDITION:
                    return translatedCondition(node.expression(), nodeScope, state);
                case INPUT_BOOLEAN:
                    return translatedCondition(Expression.create("${" + nodeScope.key() + "}"), nodeScope, state);
                case INPUT_OPTION:
                    Node input = node.ancestor(Kind::isInput).orElse(null);
                    if (input == null) {
                        return null;
                    }
                    switch (input.kind()) {
                        case INPUT_ENUM:
                            return translatedCondition(Expression.create("${" + nodeScope.key() + "} == '"
                                                                         + node.value().getString() + "'"),
                                    nodeScope,
                                    state);
                        case INPUT_LIST:
                            return translatedCondition(Expression.create("${" + nodeScope.key() + "} contains '"
                                                                         + node.value().getString() + "'"),
                                    nodeScope,
                                    state);
                        default:
                            return null;
                    }
                default:
                    return null;
            }
        }

        private Guard translatedCondition(Expression expression, Scope scope, State state) {
            Guard translated = translatedCondition0(expression, scope, state);
            return translated != null ? translated : new ConditionLowerer(ir.symbols(), guards, scope).lower(expression);
        }

        private Guard translatedCondition0(Expression expression, Scope scope, State state) {
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

        private Guard booleanGuard(ProjectedTerm term, State state) {
            if (term.guard != null) {
                return term.guard;
            }
            if (term.ref != null) {
                Flow.SymbolInfo info = symbolInfo(term.ref);
                if (info == null || info.symbol().domain().kind() != Spec.Kind.BOOLEAN) {
                    return null;
                }
                return translatedEquality(term.ref, Value.TRUE, state);
            }
            if (term.literal != null && term.literal.type() == Value.Type.BOOLEAN) {
                return term.literal.getBoolean() ? guards.trueGuard() : guards.falseGuard();
            }
            return null;
        }

        private Guard equalityGuard(ProjectedTerm left, ProjectedTerm right, State state) {
            if (left.literal != null && right.literal != null) {
                return Value.isEqual(left.literal, right.literal) ? guards.trueGuard() : guards.falseGuard();
            }
            if (left.ref != null && right.literal != null) {
                return translatedEquality(left.ref, right.literal, state);
            }
            if (left.literal != null && right.ref != null) {
                return translatedEquality(right.ref, left.literal, state);
            }
            return null;
        }

        private Guard containsGuard(ProjectedTerm left, ProjectedTerm right, State state) {
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
                return Set.copyOf(new TreeSet<>(left.literal.getList())).containsAll(required)
                        ? guards.trueGuard()
                        : guards.falseGuard();
            }
            return null;
        }

        private Guard translatedEquality(String key, Value<?> value, State state) {
            Fact fact = factFor(state, key);
            Guard bound = fact == null ? guards.falseGuard() : fact.match(value, guards);
            Guard raw = rawEquality(key, value);
            return combine(key, fact, bound, raw);
        }

        private Guard translatedScalarAny(String key, Set<String> values, State state) {
            Fact fact = factFor(state, key);
            Guard bound = fact == null ? guards.falseGuard() : fact.scalarAny(values, guards);
            Guard raw = rawScalarAny(key, values);
            return combine(key, fact, bound, raw);
        }

        private Guard translatedListContains(String key, Value<?> value, State state) {
            Set<String> required = containsValues(value);
            if (required == null) {
                return null;
            }
            Fact fact = factFor(state, key);
            Guard bound = fact == null ? guards.falseGuard() : fact.listContains(required, guards);
            Guard raw = rawListContains(symbolInfo(key), required);
            return combine(key, fact, bound, raw);
        }

        private Guard combine(String key, Fact fact, Guard bound, Guard raw) {
            Guard defined = definitionFor(key, fact);
            if (fact == null) {
                if (raw == null) {
                    return bound;
                }
                return defined == null ? raw : guards.and(defined, raw);
            }
            if (raw == null) {
                if (bound == null) {
                    return null;
                }
                if (bound.equals(guards.falseGuard())) {
                    Guard exact = fact.exactDefined(guards);
                    if (defined == null || exact.equals(guards.falseGuard()) || !guards.implies(exact, defined)) {
                        return null;
                    }
                }
                return bound;
            }
            Guard available = defined == null ? guards.trueGuard() : defined;
            Guard exact = fact.exactDefined(guards);
            Guard unresolved = guards.and(guards.minus(available, exact), raw);
            return guards.or(bound, unresolved);
        }

        private Fact factFor(State state, String key) {
            Integer symbolId = ir.symbols().findId(key);
            return symbolId == null ? null : state.env().get(symbolId);
        }

        private Guard definitionFor(String key, Fact fact) {
            if (fact != null) {
                return fact.definedUnder();
            }
            Flow.SymbolInfo info = symbolInfo(key);
            return info == null ? null : info.definition();
        }

        private Flow.SymbolInfo symbolInfo(String key) {
            Integer symbolId = ir.symbols().findId(key);
            return symbolId == null ? null : symbolInfos.get(symbolId).build(guards);
        }

        private Guard rawEquality(String key, Value<?> value) {
            String scalar = Value.scalarLiteral(value);
            if (scalar == null) {
                return null;
            }
            Flow.SymbolInfo info = symbolInfo(key);
            if (info == null) {
                return null;
            }
            Symbol symbol = info.symbol();
            if (symbol.guardable() && !symbol.tainted()) {
                switch (symbol.domain().kind()) {
                    case BOOLEAN:
                        return Set.of("false", "true").contains(scalar)
                                ? available(info, scalar, guards.eq(symbol.id(), scalar))
                                : guards.falseGuard();
                    case CHOICE:
                        return ((Spec.Choice) symbol.domain()).values().contains(scalar)
                                ? available(info, scalar, guards.eq(symbol.id(), scalar))
                                : guards.falseGuard();
                    case FINITE_TEXT:
                        return ((Spec.FiniteText) symbol.domain()).values().contains(scalar)
                                ? available(info, scalar, guards.eq(symbol.id(), scalar))
                                : guards.falseGuard();
                    default:
                        break;
                }
            }
            return fallbackScalarEquality(info, key, scalar);
        }

        private Guard rawScalarAny(String key, Set<String> values) {
            Flow.SymbolInfo info = symbolInfo(key);
            if (info == null) {
                return null;
            }
            Symbol symbol = info.symbol();
            Set<String> allowed = new TreeSet<>(values);
            if (symbol.guardable() && !symbol.tainted()) {
                switch (symbol.domain().kind()) {
                    case BOOLEAN:
                        allowed.retainAll(Set.of("false", "true"));
                        break;
                    case CHOICE:
                        allowed.retainAll(((Spec.Choice) symbol.domain()).values());
                        break;
                    case FINITE_TEXT:
                        allowed.retainAll(((Spec.FiniteText) symbol.domain()).values());
                        break;
                    default:
                        allowed.clear();
                        break;
                }
                Guard raw = guards.falseGuard();
                for (String value : allowed) {
                    raw = guards.or(raw, available(info, value, guards.eq(symbol.id(), value)));
                }
                return raw;
            }
            Guard raw = guards.falseGuard();
            for (String value : allowed) {
                Guard equality = fallbackScalarEquality(info, key, value);
                if (equality != null) {
                    raw = guards.or(raw, equality);
                }
            }
            return raw.equals(guards.falseGuard()) ? null : raw;
        }

        private Guard rawListContains(Flow.SymbolInfo info, Set<String> required) {
            if (info == null) {
                return null;
            }
            Symbol symbol = info.symbol();
            if (!symbol.guardable() || symbol.tainted() || symbol.domain().kind() != Spec.Kind.MEMBERSHIP) {
                return null;
            }
            Set<String> items = ((Spec.Membership) symbol.domain()).items();
            if (!items.containsAll(required)) {
                return guards.falseGuard();
            }
            Guard available = guards.trueGuard();
            for (String value : required) {
                Guard availability = info.availability(value);
                if (availability == null) {
                    return guards.falseGuard();
                }
                available = guards.and(available, availability);
            }
            return guards.and(available, guards.containsAll(symbol.id(), required));
        }

        private Guard available(Flow.SymbolInfo info, String value, Guard direct) {
            Guard availability = info.availability(value);
            return availability == null ? guards.falseGuard() : guards.and(availability, direct);
        }

        private Guard fallbackScalarEquality(Flow.SymbolInfo info, String key, String value) {
            Guard availability = info.availability(value);
            if (availability == null) {
                return null;
            }
            Expression expression = Expression.create("${" + key + "} == '" + value + "'");
            return guards.and(availability, residualGuard(expression));
        }

        private Guard residualGuard(Expression expression) {
            return guards.residualGuard(expression);
        }

        private Set<String> containsValues(Value<?> value) {
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

        private Scope childScope(Scope scope, Node node) {
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

        private ProjectedTerm(Guard guard, String ref, Value<?> literal) {
            this.guard = guard;
            this.ref = ref;
            this.literal = literal;
        }

        private static ProjectedTerm guard(Guard guard) {
            return new ProjectedTerm(guard, null, null);
        }

        private static ProjectedTerm ref(String ref) {
            return new ProjectedTerm(null, ref, null);
        }

        private static ProjectedTerm value(Value<?> literal) {
            return new ProjectedTerm(null, null, literal);
        }
    }

    private static final class BranchInfo {
        private final int trueId;
        private final int joinId;

        private BranchInfo(int trueId, int joinId) {
            this.trueId = trueId;
            this.joinId = joinId;
        }
    }

    private static final class SymbolInfoBuilder {
        private final Symbol symbol;
        private Guard definition;
        private final Map<String, Guard> availabilityByValue = new LinkedHashMap<>();

        private SymbolInfoBuilder(Symbol symbol) {
            this.symbol = symbol;
        }

        private void addDefinition(Guard guard, Guards guards) {
            definition = definition == null ? guard : guards.or(definition, guard);
        }

        private void addAvailability(String value, Guard guard, Guards guards) {
            availabilityByValue.compute(value, (key, current) -> current == null ? guard : guards.or(current, guard));
        }

        private SymbolInfo build(Guards guards) {
            return new SymbolInfo(symbol,
                    definition == null ? guards.falseGuard() : definition,
                    availabilityByValue);
        }
    }

    private static final class Lowerer {
        private final Scope scope;
        private final Table.Builder symbolBuilder = Table.builder();
        private final Map<String, SymbolSeed> symbolSeeds = new LinkedHashMap<>();
        private final Map<String, SymbolSeed> declaredInputSymbols = new LinkedHashMap<>();
        private final List<BlockBuilder> blocks = new ArrayList<>();
        private final List<Op> ops = new ArrayList<>();
        private Table symbols;
        private Guards guards;
        private int nextRefId;

        Lowerer(Scope scope) {
            this.scope = scope;
        }

        Ir lower(Node root) {
            collectDeclaredInputs(root, scope);
            collectSymbols(root, scope);
            for (SymbolSeed seed : symbolSeeds.values()) {
                symbolBuilder.define(seed.name, seed.spec, seed.guardable, seed.tainted);
            }
            symbols = symbolBuilder.build();
            guards = new Guards(symbols);

            int entryBlock = newBlock();
            int exitBlock = lowerChildren(root.children(), entryBlock, scope);
            terminateIfMissing(exitBlock, Terminator.ret(anchor(root, scope)));

            List<Block> loweredBlocks = new ArrayList<>(blocks.size());
            for (BlockBuilder block : blocks) {
                Terminator terminator = block.terminator;
                if (terminator == null) {
                    terminator = Terminator.unreachable(anchor(root, scope));
                }
                loweredBlocks.add(new Block(block.id, block.ops, terminator));
            }
            return new Ir(loweredBlocks, symbols, ops, guards);
        }

        void collectDeclaredInputs(Node node, Scope scope) {
            Scope childScope = scope;
            switch (node.kind()) {
                case INPUT_BOOLEAN:
                    childScope = scope.getOrCreate(node);
                    rememberDeclaredInput(childScope.key(), new Spec.Boolean(), true, false);
                    break;
                case INPUT_ENUM:
                case INPUT_LIST:
                    childScope = scope.getOrCreate(node);
                    rememberDeclaredInput(childScope.key(), inputSpec(node), !optionValues(node).isEmpty(), false);
                    break;
                case INPUT_TEXT:
                    childScope = scope.getOrCreate(node);
                    rememberDeclaredInput(childScope.key(), new Spec.OpenText(), false, true);
                    break;
                default:
                    break;
            }
            for (Node child : node.children()) {
                collectDeclaredInputs(child, childScope);
            }
        }

        void collectSymbols(Node node, Scope scope) {
            Scope childScope = scope;
            switch (node.kind()) {
                case INPUT_BOOLEAN:
                    childScope = scope.getOrCreate(node);
                    rememberSymbol(childScope.key(), new Spec.Boolean(), true, false);
                    break;
                case INPUT_ENUM:
                case INPUT_LIST:
                    childScope = scope.getOrCreate(node);
                    rememberSymbol(childScope.key(), inputSpec(node), !optionValues(node).isEmpty(), false);
                    break;
                case INPUT_TEXT:
                    childScope = scope.getOrCreate(node);
                    rememberSymbol(childScope.key(), new Spec.OpenText(), false, true);
                    break;
                case PRESET_BOOLEAN:
                case VARIABLE_BOOLEAN:
                    rememberSymbol(definitionId(scope, node), new Spec.Boolean(), true, false);
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
                        rememberSymbol(key, new Spec.OpenText(), false, false);
                    }
                    break;
                }
                default:
                    break;
            }
            for (Node child : node.children()) {
                collectSymbols(child, childScope);
            }
        }

        void rememberDeclaredInput(String name, Spec spec, boolean guardable, boolean tainted) {
            declaredInputSymbols.merge(
                    name,
                    new SymbolSeed(name, spec, guardable, tainted),
                    SymbolSeed::merge);
        }

        int lowerChildren(List<Node> nodes, int blockId, Scope scope) {
            int current = blockId;
            for (Node node : nodes) {
                current = lower(node, current, scope);
            }
            return current;
        }

        int lower(Node node, int blockId, Scope scope) {
            switch (node.kind()) {
                case INPUT_BOOLEAN:
                case INPUT_ENUM:
                case INPUT_LIST:
                case INPUT_TEXT:
                    return lowerInput(node, blockId, scope);
                case PRESET_BOOLEAN:
                case PRESET_ENUM:
                case PRESET_LIST:
                case PRESET_TEXT:
                case VARIABLE_BOOLEAN:
                case VARIABLE_ENUM:
                case VARIABLE_LIST:
                case VARIABLE_TEXT:
                    return lowerDefinition(node, blockId, scope);
                case CONDITION:
                    return lowerCondition(node, blockId, scope);
                case STEP:
                    append(blockId, Op.emit(nextOpId(), blockId, anchor(node, scope), Op.EmitKind.STEP, nextRefId++));
                    return lowerChildren(node.children(), blockId, scope);
                case FILE:
                    append(blockId, Op.emit(nextOpId(), blockId, anchor(node, scope), Op.EmitKind.FILE, nextRefId++));
                    return lowerChildren(node.children(), blockId, scope);
                case MODEL:
                    append(blockId, Op.emit(nextOpId(), blockId, anchor(node, scope), Op.EmitKind.MODEL, nextRefId++));
                    return lowerChildren(node.children(), blockId, scope);
                case INPUT_OPTION:
                    return lowerOption(node, blockId, scope);
                default:
                    return lowerChildren(node.children(), blockId, scope);
            }
        }

        int lowerInput(Node node, int blockId, Scope scope) {
            Scope inputScope = scope.getOrCreate(node);
            int symbolId = symbols.findId(inputScope.key());
            append(blockId, Op.declareInput(nextOpId(), blockId, anchor(node, inputScope), symbolId, symbolId));
            switch (node.kind()) {
                case INPUT_BOOLEAN:
                    return lowerGuardedChildren(node,
                            blockId,
                            guard(Expression.create("${" + inputScope.key() + "}"), inputScope),
                            node.children(),
                            inputScope);
                case INPUT_ENUM:
                case INPUT_LIST:
                default:
                    return lowerChildren(node.children(), blockId, inputScope);
            }
        }

        int lowerOption(Node node, int blockId, Scope scope) {
            Node input = node.ancestor(Kind::isInput).orElseThrow(() ->
                    new IllegalStateException("Option without input parent: " + node));
            Integer symbolId = symbols.findId(scope.key());
            if (symbolId == null) {
                throw new IllegalStateException("Missing option symbol for scope: " + scope.key());
            }
            append(blockId, Op.declareOption(nextOpId(), blockId, anchor(node, scope), symbolId, nextRefId++));
            return lowerGuardedChildren(node, blockId, optionGuard(input, node, scope), node.children(), scope);
        }

        int lowerDefinition(Node node, int blockId, Scope scope) {
            Integer symbolId = symbols.findId(definitionId(scope, node));
            if (symbolId != null) {
                append(blockId, Op.defineValue(nextOpId(),
                        blockId,
                        anchor(node, scope),
                        symbolId,
                        new Expression(List.of(Token.of(node.value())), true)));
            }
            return lowerChildren(node.children(), blockId, scope);
        }

        int lowerCondition(Node node, int blockId, Scope scope) {
            recordUses(node.expression(), blockId, node, scope);
            return lowerGuardedChildren(node, blockId, guard(node.expression(), scope), node.children(), scope);
        }

        Guard optionGuard(Node input, Node option, Scope scope) {
            String key = scope.key();
            switch (input.kind()) {
                case INPUT_ENUM:
                    return guard(Expression.create("${" + key + "} == '" + option.value().getString() + "'"), scope);
                case INPUT_LIST:
                    return guard(Expression.create("${" + key + "} contains '" + option.value().getString() + "'"), scope);
                default:
                    throw new IllegalArgumentException("Unsupported option parent: " + input.kind());
            }
        }

        int lowerGuardedChildren(Node node, int blockId, Guard guard, List<Node> children, Scope scope) {
            if (children.isEmpty()) {
                return blockId;
            }
            int trueBlock = newBlock();
            int falseBlock = newBlock();
            int joinBlock = newBlock();
            terminate(blockId, Terminator.branch(anchor(node, scope), guard, trueBlock, falseBlock));
            int trueExit = lowerChildren(children, trueBlock, scope);
            terminateIfMissing(trueExit, Terminator.jump(anchor(node, scope), joinBlock));
            terminateIfMissing(falseBlock, Terminator.jump(anchor(node, scope), joinBlock));
            return joinBlock;
        }

        Guard guard(Expression expression, Scope scope) {
            return new ConditionLowerer(symbols, guards, scope).lower(requireNonNull(expression, "expression is null"));
        }

        void append(int blockId, Op op) {
            block(blockId).ops.add(op.id());
            ops.add(op);
        }

        void recordUses(Expression expression, int blockId, Node node, Scope scope) {
            for (String variable : expression.variables()) {
                Integer symbolId = symbols.findId(refId(scope, variable));
                if (symbolId != null) {
                    append(blockId, Op.recordUse(nextOpId(), blockId, anchor(node, scope), symbolId, nextRefId++));
                }
            }
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
            return new SymbolSeed(key, new Spec.OpenText(), false, false);
        }

        SymbolSeed scalarDefinitionSeed(String key, Value<?> value) {
            String literal = literalScalar(value);
            if (literal == null) {
                return new SymbolSeed(key, new Spec.OpenText(), false, true);
            }
            return new SymbolSeed(key, new Spec.FiniteText(Set.of(literal)), true, false);
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

        SourceAnchor anchor(Node node, Scope scope) {
            return new SourceAnchor(node, scope.key());
        }

        int newBlock() {
            int id = blocks.size();
            blocks.add(new BlockBuilder(id));
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
                return new Spec.OpenText();
            }
            return node.kind() == Kind.INPUT_ENUM
                    ? new Spec.Choice(values)
                    : new Spec.Membership(values);
        }

        static String inputId(Scope scope, Node node) {
            return scope.getOrCreate(node).key();
        }

        static String definitionId(Scope scope, Node node) {
            return scope.getOrCreate("~" + Context.Key.normalize(node.attribute("path").getString())).key();
        }

        static String refId(Scope scope, String variable) {
            return scope.key(variable);
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
            if (spec.kind() != other.spec.kind()) {
                return new SymbolSeed(name, new Spec.OpenText(), false, tainted || other.tainted);
            }
            switch (spec.kind()) {
                case CHOICE:
                    Set<String> choiceValues = new TreeSet<>(((Spec.Choice) spec).values());
                    choiceValues.addAll(((Spec.Choice) other.spec).values());
                    return new SymbolSeed(name,
                            new Spec.Choice(choiceValues),
                            guardable && other.guardable,
                            tainted || other.tainted);
                case MEMBERSHIP:
                    Set<String> items = new TreeSet<>(((Spec.Membership) spec).items());
                    items.addAll(((Spec.Membership) other.spec).items());
                    return new SymbolSeed(name,
                            new Spec.Membership(items),
                            guardable && other.guardable,
                            tainted || other.tainted);
                case FINITE_TEXT:
                    Set<String> values = new TreeSet<>(((Spec.FiniteText) spec).values());
                    values.addAll(((Spec.FiniteText) other.spec).values());
                    return new SymbolSeed(name,
                            new Spec.FiniteText(values),
                            guardable && other.guardable,
                            tainted || other.tainted);
                case BOOLEAN:
                case OPEN_TEXT:
                default:
                    return new SymbolSeed(name, spec, guardable && other.guardable, tainted || other.tainted);
            }
        }
    }

    private static final class BlockBuilder {
        private final int id;
        private final List<Integer> ops = new ArrayList<>();
        private Terminator terminator;

        BlockBuilder(int id) {
            this.id = id;
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
            if (expression == Expression.TRUE) {
                return guards.trueGuard();
            }
            if (expression == Expression.FALSE) {
                return guards.falseGuard();
            }
            Deque<ConditionValue> stack = new ArrayDeque<>();
            for (Token token : expression.tokens()) {
                if (token.isVariable()) {
                    String key = scope.key(token.variable());
                    stack.push(ConditionValue.variable(key, findSymbol(key)));
                    continue;
                }
                if (token.isOperand()) {
                    stack.push(ConditionValue.literal(token.operand()));
                    continue;
                }
                switch (token.operator()) {
                    case NOT:
                        stack.push(not(stack.pop()));
                        break;
                    case AND:
                        stack.push(and(stack.pop(), stack.pop()));
                        break;
                    case OR:
                        stack.push(or(stack.pop(), stack.pop()));
                        break;
                    case EQUAL:
                        stack.push(compare(true, stack.pop(), stack.pop()));
                        break;
                    case NOT_EQUAL:
                        stack.push(compare(false, stack.pop(), stack.pop()));
                        break;
                    case CONTAINS:
                        stack.push(contains(stack.pop(), stack.pop()));
                        break;
                    default:
                        return residual(expression);
                }
            }
            if (stack.size() != 1) {
                throw new IllegalStateException("Unexpected expression stack size: " + stack.size());
            }
            ConditionValue value = stack.pop();
            Guard guard = value.asGuard(guards);
            return guard != null ? guard : residual(value.expression);
        }

        ConditionValue not(ConditionValue value) {
            Guard guard = value.asGuard(guards);
            Expression expression = negate(value.expression);
            return guard != null ? ConditionValue.guard(guards.not(guard), expression)
                    : ConditionValue.expression(negate(value.expression));
        }

        ConditionValue and(ConditionValue right, ConditionValue left) {
            Guard leftGuard = left.asGuard(guards);
            Guard rightGuard = right.asGuard(guards);
            Expression expression = left.expression.and(right.expression);
            return leftGuard != null && rightGuard != null
                    ? ConditionValue.guard(guards.and(leftGuard, rightGuard), expression)
                    : ConditionValue.expression(expression);
        }

        ConditionValue or(ConditionValue right, ConditionValue left) {
            Guard leftGuard = left.asGuard(guards);
            Guard rightGuard = right.asGuard(guards);
            Expression expression = left.expression.or(right.expression);
            return leftGuard != null && rightGuard != null
                    ? ConditionValue.guard(guards.or(leftGuard, rightGuard), expression)
                    : ConditionValue.expression(expression);
        }

        ConditionValue compare(boolean equal, ConditionValue right, ConditionValue left) {
            Guard direct = compareGuard(left, right);
            if (direct == null) {
                direct = compareGuard(right, left);
            }
            Expression expression = combine(left.expression, equal ? "==" : "!=", right.expression);
            if (direct != null) {
                return ConditionValue.guard(equal ? direct : guards.not(direct), expression);
            }
            return ConditionValue.expression(expression);
        }

        Guard compareGuard(ConditionValue symbolValue, ConditionValue literalValue) {
            if (symbolValue.symbol == null || literalValue.literal == null) {
                return null;
            }
            Symbol symbol = symbolValue.symbol;
            if (!symbol.guardable() || symbol.tainted()) {
                return null;
            }
            switch (symbol.domain().kind()) {
                case BOOLEAN:
                    if (literalValue.literal.type() == Value.Type.BOOLEAN) {
                        return guards.eq(symbol.id(), String.valueOf(literalValue.literal.getBoolean()));
                    }
                    if (literalValue.literal.type() == Value.Type.STRING) {
                        String literal = literalValue.literal.getString();
                        if ("true".equals(literal) || "false".equals(literal)) {
                            return guards.eq(symbol.id(), literal);
                        }
                    }
                    return null;
                case CHOICE:
                case FINITE_TEXT:
                    return literalValue.literal.type() == Value.Type.STRING
                            ? guards.eq(symbol.id(), literalValue.literal.getString())
                            : null;
                default:
                    return null;
            }
        }

        ConditionValue contains(ConditionValue right, ConditionValue left) {
            Guard direct = containsGuard(left, right);
            if (direct == null) {
                direct = scalarAnyGuard(right, left);
            }
            Expression expression = combine(left.expression, "contains", right.expression);
            return direct == null ? ConditionValue.expression(expression) : ConditionValue.guard(direct, expression);
        }

        Guard containsGuard(ConditionValue symbolValue, ConditionValue literalValue) {
            if (symbolValue.symbol == null || literalValue.literal == null) {
                return null;
            }
            Symbol symbol = symbolValue.symbol;
            if (symbol.domain().kind() != Spec.Kind.MEMBERSHIP || !symbol.guardable() || symbol.tainted()) {
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
            switch (symbol.domain().kind()) {
                case BOOLEAN:
                case CHOICE:
                case FINITE_TEXT:
                    Guard result = guards.falseGuard();
                    for (String item : literalValue.literal.getList()) {
                        result = guards.or(result, guards.eq(symbol.id(), item));
                    }
                    return result;
                default:
                    return null;
            }
        }

        Guard residual(Expression expression) {
            return guards.residualGuard(expression);
        }

        Symbol findSymbol(String name) {
            Integer id = symbols.findId(name);
            return id == null ? null : symbols.symbol(id);
        }

        static Expression combine(Expression left, String operator, Expression right) {
            return Expression.create("(" + left.literal() + ") " + operator + " (" + right.literal() + ")");
        }

        static Expression negate(Expression expression) {
            if (expression == Expression.TRUE) {
                return Expression.FALSE;
            }
            if (expression == Expression.FALSE) {
                return Expression.TRUE;
            }
            return Expression.create("!(" + expression.literal() + ")");
        }
    }

    private static final class ConditionValue {
        private final Expression expression;
        private final Guard guard;
        private final Symbol symbol;
        private final Value<?> literal;

        ConditionValue(Expression expression, Guard guard, Symbol symbol, Value<?> literal) {
            this.expression = requireNonNull(expression, "expression is null");
            this.guard = guard;
            this.symbol = symbol;
            this.literal = literal;
        }

        static ConditionValue guard(Guard guard, Expression expression) {
            return new ConditionValue(expression, guard, null, null);
        }

        static ConditionValue expression(Expression expression) {
            return new ConditionValue(expression, null, null, null);
        }

        static ConditionValue variable(String name, Symbol symbol) {
            return new ConditionValue(Expression.create("${" + name + "}"), null, symbol, null);
        }

        static ConditionValue literal(Value<?> literal) {
            return new ConditionValue(new Expression(List.of(Token.of(literal)), true), null, null, literal);
        }

        Guard asGuard(Guards guards) {
            if (guard != null) {
                return guard;
            }
            if (symbol != null && symbol.guardable() && !symbol.tainted() && symbol.domain().kind() == Spec.Kind.BOOLEAN) {
                return guards.eq(symbol.id(), "true");
            }
            if (literal != null && literal.type() == Value.Type.BOOLEAN) {
                return literal.getBoolean() ? guards.trueGuard() : guards.falseGuard();
            }
            return null;
        }
    }

    private static final class Analyzer {
        private final Ir ir;
        private final Guards guards;
        private final State unreachable;
        private final List<State> beforeByOp;
        private final List<State> afterByOp;
        private final List<Use> usesById;
        private final Map<Integer, Integer> useIndexByOp = new HashMap<>();

        Analyzer(Ir ir) {
            this.ir = ir;
            this.guards = ir.guards();
            this.unreachable = new State(guards.falseGuard(), Map.of());
            this.beforeByOp = new ArrayList<>(Collections.nCopies(ir.ops().size(), unreachable));
            this.afterByOp = new ArrayList<>(Collections.nCopies(ir.ops().size(), unreachable));
            this.usesById = new ArrayList<>();
            for (Op op : ir.ops()) {
                if (op.kind() == Op.Kind.RECORD_USE) {
                    int useId = usesById.size();
                    useIndexByOp.put(op.id(), useId);
                    usesById.add(new Use(useId, op.symbolId(), guards.falseGuard()));
                }
            }
        }

        Analysis analyze() {
            List<State> entries = new ArrayList<>(Collections.nCopies(ir.blocks().size(), unreachable));
            List<State> exits = new ArrayList<>(Collections.nCopies(ir.blocks().size(), unreachable));
            entries.set(0, new State(guards.trueGuard(), Map.of()));
            Deque<Integer> work = new ArrayDeque<>();
            work.add(0);
            while (!work.isEmpty()) {
                int blockId = work.removeFirst();
                State state = entries.get(blockId);
                if (state.path().equals(guards.falseGuard())) {
                    continue;
                }
                State current = state;
                Block block = ir.blocks().get(blockId);
                for (int opId : block.ops()) {
                    Op op = ir.ops().get(opId);
                    beforeByOp.set(opId, current);
                    current = transfer(op, current);
                    afterByOp.set(opId, current);
                }
                exits.set(blockId, current);
                propagate(block.terminator(), current, entries, work);
            }
            return new Analysis(entries, exits, beforeByOp, afterByOp, usesById);
        }

        State transfer(Op op, State state) {
            switch (op.kind()) {
                case DECLARE_INPUT: {
                    Fact declared = new Fact(
                            state.path(),
                            Domain.Value.top(ir.symbols().symbol(op.symbolId()).domain()));
                    Fact existing = state.env().get(op.symbolId());
                    return define(state,
                            op.symbolId(),
                            existing == null ? declared : Fact.merge(existing, declared, guards));
                }
                case DEFINE_VALUE:
                    Symbol symbol = ir.symbols().symbol(op.symbolId());
                    return define(state, op.symbolId(), evaluateFact(op.expression(), symbol, state));
                case RECORD_USE:
                    int useIndex = useIndexByOp.get(op.id());
                    Use existing = usesById.get(useIndex);
                    usesById.set(useIndex,
                            new Use(existing.id(), existing.symbolId(), mergeGuard(existing.guard(), state.path())));
                    return state;
                case EMIT:
                case PHI:
                default:
                    return state;
            }
        }

        void propagate(Terminator terminator, State state, List<State> entries, Deque<Integer> work) {
            switch (terminator.kind()) {
                case GOTO:
                    enqueue(terminator.targetId(), state, entries, work);
                    break;
                case BRANCH:
                    enqueue(terminator.trueId(),
                            new State(guards.and(state.path(), terminator.guard()), state.env()),
                            entries,
                            work);
                    enqueue(terminator.falseId(),
                            new State(guards.and(state.path(), guards.not(terminator.guard())), state.env()),
                            entries,
                            work);
                    break;
                case RETURN:
                case UNREACHABLE:
                default:
                    break;
            }
        }

        void enqueue(int blockId, State incoming, List<State> entries, Deque<Integer> work) {
            if (incoming.path().equals(guards.falseGuard())) {
                return;
            }
            State current = entries.get(blockId);
            State merged = merge(current, incoming);
            if (merged != current) {
                entries.set(blockId, merged);
                work.addLast(blockId);
            }
        }

        State merge(State current, State incoming) {
            if (current.path().equals(guards.falseGuard())) {
                return incoming;
            }
            if (incoming.path().equals(guards.falseGuard())) {
                return current;
            }
            if (current == incoming || current.path() == incoming.path() && current.env() == incoming.env()) {
                return current;
            }
            Guard path = mergeGuard(current.path(), incoming.path());
            Map<Integer, Fact> env = null;
            boolean changed = path != current.path();
            Set<Integer> keys = new HashSet<>(current.env().keySet());
            keys.addAll(incoming.env().keySet());
            for (int symbolId : keys) {
                Fact left = current.env().get(symbolId);
                Fact right = incoming.env().get(symbolId);
                Fact merged;
                if (left == null) {
                    merged = right;
                } else if (right == null) {
                    merged = left;
                } else if (left == right || left.equals(right)) {
                    merged = left;
                } else {
                    merged = Fact.merge(left, right, guards);
                }
                if (merged != left) {
                    changed = true;
                    if (env == null) {
                        env = new LinkedHashMap<>(current.env());
                    }
                    env.put(symbolId, merged);
                }
            }
            if (!changed) {
                return current;
            }
            return new State(path, env == null ? current.env() : env);
        }

        Guard mergeGuard(Guard left, Guard right) {
            if (left == right || left.equals(right)) {
                return left;
            }
            if (left.residual() == Expression.TRUE && right.residual() == Expression.TRUE) {
                if (guards.shapewiseImplies(left, right)) {
                    return right;
                }
                if (guards.shapewiseImplies(right, left)) {
                    return left;
                }
                if (guards.exactImplicationCheap(left, right)) {
                    if (guards.implies(left, right)) {
                        return right;
                    }
                    if (guards.implies(right, left)) {
                        return left;
                    }
                }
            }
            return guards.or(left, right);
        }

        State define(State state, int symbolId, Fact fact) {
            Fact existing = state.env().get(symbolId);
            if (Objects.equals(existing, fact)) {
                return state;
            }
            Map<Integer, Fact> env = new LinkedHashMap<>(state.env());
            env.put(symbolId, fact);
            return new State(state.path(), env);
        }

        Fact evaluateFact(Expression expression, Symbol symbol, State state) {
            Domain.Value value = evaluateValue(expression, symbol, state);
            io.helidon.build.archetype.engine.v2.Value<?> exactValue = exactValue(expression, state);
            return exactValue == null
                    ? new Fact(state.path(), value)
                    : Fact.exact(state.path(), value, exactValue);
        }

        Domain.Value evaluateValue(Expression expression, Symbol symbol, State state) {
            List<Token> tokens = expression.tokens();
            if (tokens.size() == 1) {
                Token token = tokens.get(0);
                if (token.isOperand()) {
                    return literalValue(symbol.domain(), token.operand());
                }
                if (token.isVariable()) {
                    Integer symbolId = ir.symbols().findId(token.variable());
                    if (symbolId != null) {
                        Fact fact = state.env().get(symbolId);
                        if (fact != null) {
                            return fact.value();
                        }
                    }
                }
            }
            return Domain.Value.top(symbol.domain());
        }

        io.helidon.build.archetype.engine.v2.Value<?> exactValue(Expression expression, State state) {
            List<Token> tokens = expression.tokens();
            if (tokens.size() != 1) {
                return null;
            }
            Token token = tokens.get(0);
            if (token.isOperand()) {
                return token.operand();
            }
            if (token.isVariable()) {
                Integer symbolId = ir.symbols().findId(token.variable());
                if (symbolId == null) {
                    return null;
                }
                Fact fact = state.env().get(symbolId);
                if (fact == null || fact.exactCases().isEmpty()) {
                    return null;
                }
                io.helidon.build.archetype.engine.v2.Value<?> value = null;
                Guard covered = guards.falseGuard();
                for (Fact.ExactCase exactCase : fact.exactCases()) {
                    if (!guards.implies(exactCase.guard(), state.path())) {
                        covered = guards.or(covered, exactCase.guard());
                        continue;
                    }
                    if (value == null) {
                        value = exactCase.value();
                    } else if (!io.helidon.build.archetype.engine.v2.Value.isEqual(value, exactCase.value())) {
                        return null;
                    }
                    covered = guards.or(covered, exactCase.guard());
                }
                return value != null && guards.implies(state.path(), covered) ? value : null;
            }
            return null;
        }

        static Domain.Value literalValue(Spec spec, Value<?> literal) {
            switch (literal.type()) {
                case BOOLEAN:
                    return Domain.Value.booleanSet(literal.getBoolean());
                case STRING:
                    switch (spec.kind()) {
                        case CHOICE:
                            return Domain.Value.choiceSet(Set.of(literal.getString()));
                        case FINITE_TEXT:
                            return Domain.Value.finiteText(Set.of(literal.getString()));
                        default:
                            return Domain.Value.openText(literal.getString());
                    }
                case LIST:
                    Set<String> values = Set.copyOf(new TreeSet<>(literal.getList()));
                    if (spec.kind() == Spec.Kind.MEMBERSHIP) {
                        return Domain.Value.membershipSet(values, values);
                    }
                    return Domain.Value.listSummary(values, values);
                default:
                    return Domain.Value.top(spec);
            }
        }
    }
}
