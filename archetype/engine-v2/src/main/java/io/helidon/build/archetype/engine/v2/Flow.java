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

    private Flow() {
    }

    static Ir ir(Node root, Scope rootScope) {
        return new Lowerer(requireNonNull(rootScope, "rootScope is null")).lower(requireNonNull(root, "root is null"));
    }

    static Analysis analyze(Ir ir) {
        return new Analyzer(requireNonNull(ir, "ir is null")).analyze();
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
            if (!(o instanceof State)) {
                return false;
            }
            State other = (State) o;
            return path.equals(other.path) && env.equals(other.env);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, env);
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
        private final List<State> beforeByOp;
        private final List<State> afterByOp;
        private final List<Use> usesById;

        Analysis(List<State> beforeByOp, List<State> afterByOp, List<Use> usesById) {
            this.beforeByOp = List.copyOf(beforeByOp);
            this.afterByOp = List.copyOf(afterByOp);
            this.usesById = List.copyOf(usesById);
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

    private static final class Lowerer {
        private final Scope rootScope;
        private final Table.Builder symbolBuilder = Table.builder();
        private final Map<String, SymbolSeed> symbolSeeds = new LinkedHashMap<>();
        private final List<BlockBuilder> blocks = new ArrayList<>();
        private final List<Op> ops = new ArrayList<>();
        private Table symbols;
        private Guards guards;
        private int nextRefId;

        Lowerer(Scope rootScope) {
            this.rootScope = rootScope;
        }

        Ir lower(Node root) {
            collectSymbols(root);
            symbols = symbolBuilder.build();
            guards = new Guards(symbols);

            int entryBlock = newBlock();
            int exitBlock = lowerChildren(root.children(), entryBlock);
            terminateIfMissing(exitBlock, Terminator.ret(anchor(root)));

            List<Block> loweredBlocks = new ArrayList<>(blocks.size());
            for (BlockBuilder block : blocks) {
                Terminator terminator = block.terminator;
                if (terminator == null) {
                    terminator = Terminator.unreachable(anchor(root));
                }
                loweredBlocks.add(new Block(block.id, block.ops, terminator));
            }
            return new Ir(loweredBlocks, symbols, ops, guards);
        }

        void collectSymbols(Node root) {
            for (Node node : root.traverse()) {
                switch (node.kind()) {
                    case INPUT_BOOLEAN:
                        rememberSymbol(inputId(node), new Spec.Boolean(), true, false);
                        break;
                    case INPUT_ENUM:
                        rememberSymbol(inputId(node), inputSpec(node), !optionValues(node).isEmpty(), false);
                        break;
                    case INPUT_LIST:
                        rememberSymbol(inputId(node), inputSpec(node), !optionValues(node).isEmpty(), false);
                        break;
                    case INPUT_TEXT:
                        rememberSymbol(inputId(node), new Spec.OpenText(), false, true);
                        break;
                    case PRESET_BOOLEAN:
                    case VARIABLE_BOOLEAN:
                        rememberSymbol(definitionId(node), new Spec.Boolean(), true, false);
                        break;
                    case PRESET_ENUM:
                    case PRESET_LIST:
                    case VARIABLE_ENUM:
                    case VARIABLE_LIST:
                        rememberSymbol(definitionId(node), new Spec.OpenText(), false, false);
                        break;
                    case PRESET_TEXT:
                    case VARIABLE_TEXT:
                        rememberSymbol(definitionId(node), new Spec.OpenText(), false, true);
                        break;
                    default:
                        break;
                }
            }
            for (SymbolSeed seed : symbolSeeds.values()) {
                symbolBuilder.define(seed.name(), seed.spec(), seed.guardable(), seed.tainted());
            }
        }

        int lowerChildren(List<Node> nodes, int blockId) {
            int current = blockId;
            for (Node node : nodes) {
                current = lower(node, current);
            }
            return current;
        }

        int lower(Node node, int blockId) {
            switch (node.kind()) {
                case INPUT_BOOLEAN:
                case INPUT_ENUM:
                case INPUT_LIST:
                case INPUT_TEXT:
                    return lowerInput(node, blockId);
                case PRESET_BOOLEAN:
                case PRESET_ENUM:
                case PRESET_LIST:
                case PRESET_TEXT:
                case VARIABLE_BOOLEAN:
                case VARIABLE_ENUM:
                case VARIABLE_LIST:
                case VARIABLE_TEXT:
                    return lowerDefinition(node, blockId);
                case CONDITION:
                    return lowerCondition(node, blockId);
                case STEP:
                    append(blockId, Op.emit(nextOpId(), blockId, anchor(node), Op.EmitKind.STEP, nextRefId++));
                    return lowerChildren(node.children(), blockId);
                case FILE:
                    append(blockId, Op.emit(nextOpId(), blockId, anchor(node), Op.EmitKind.FILE, nextRefId++));
                    return lowerChildren(node.children(), blockId);
                case MODEL:
                    append(blockId, Op.emit(nextOpId(), blockId, anchor(node), Op.EmitKind.MODEL, nextRefId++));
                    return lowerChildren(node.children(), blockId);
                case INPUT_OPTION:
                    return lowerChildren(node.children(), blockId);
                default:
                    return lowerChildren(node.children(), blockId);
            }
        }

        int lowerInput(Node node, int blockId) {
            int symbolId = symbols.findId(inputId(node));
            append(blockId, Op.declareInput(nextOpId(), blockId, anchor(node), symbolId, symbolId));
            if (node.kind() == Kind.INPUT_ENUM || node.kind() == Kind.INPUT_LIST) {
                for (Node option : node.children(Kind.INPUT_OPTION::equals)) {
                    append(blockId, Op.declareOption(nextOpId(), blockId, anchor(option), symbolId, nextRefId++));
                }
            }
            return lowerChildren(node.children(), blockId);
        }

        int lowerDefinition(Node node, int blockId) {
            Integer symbolId = symbols.findId(definitionId(node));
            if (symbolId != null && node.value().isPresent()) {
                append(blockId, Op.defineValue(nextOpId(),
                        blockId,
                        anchor(node),
                        symbolId,
                        new Expression(List.of(Token.of(node.value())), true)));
            }
            return lowerChildren(node.children(), blockId);
        }

        int lowerCondition(Node node, int blockId) {
            recordUses(node.expression(), blockId, node);
            return lowerGuardedChildren(node, blockId, guard(node.expression()), node.children());
        }

        int lowerGuardedChildren(Node node, int blockId, Guard guard, List<Node> children) {
            if (children.isEmpty()) {
                return blockId;
            }
            int trueBlock = newBlock();
            int falseBlock = newBlock();
            int joinBlock = newBlock();
            terminate(blockId, Terminator.branch(anchor(node), guard, trueBlock, falseBlock));
            int trueExit = lowerChildren(children, trueBlock);
            terminateIfMissing(trueExit, Terminator.jump(anchor(node), joinBlock));
            terminateIfMissing(falseBlock, Terminator.jump(anchor(node), joinBlock));
            return joinBlock;
        }

        Guard guard(Expression expression) {
            return new ConditionLowerer(symbols, guards).lower(requireNonNull(expression, "expression is null"));
        }

        void append(int blockId, Op op) {
            block(blockId).ops.add(op.id());
            ops.add(op);
        }

        void recordUses(Expression expression, int blockId, Node node) {
            for (String variable : expression.variables()) {
                Integer symbolId = symbols.findId(refId(variable));
                if (symbolId != null) {
                    append(blockId, Op.recordUse(nextOpId(), blockId, anchor(node), symbolId, nextRefId++));
                }
            }
        }

        void rememberSymbol(String name, Spec spec, boolean guardable, boolean tainted) {
            symbolSeeds.merge(name,
                    new SymbolSeed(name, spec, guardable, tainted),
                    SymbolSeed::merge);
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

        SourceAnchor anchor(Node node) {
            return new SourceAnchor(node, rootScope.key());
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
            for (Node option : input.children(Kind.INPUT_OPTION::equals)) {
                values.add(option.value().getString());
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

        static String inputId(Node node) {
            return node.attribute("id").getString();
        }

        static String definitionId(Node node) {
            return Context.Key.normalize(node.attribute("path").getString());
        }

        static String refId(String variable) {
            return Context.Key.normalize(variable);
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

        String name() {
            return name;
        }

        Spec spec() {
            return spec;
        }

        boolean guardable() {
            return guardable;
        }

        boolean tainted() {
            return tainted;
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

        ConditionLowerer(Table symbols, Guards guards) {
            this.symbols = symbols;
            this.guards = guards;
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
                    stack.push(ConditionValue.variable(token.variable(), findSymbol(token.variable())));
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
            return guard != null ? guard : residual(value.expression());
        }

        ConditionValue not(ConditionValue value) {
            Guard guard = value.asGuard(guards);
            Expression expression = negate(value.expression());
            return guard != null ? ConditionValue.guard(guards.not(guard), expression)
                    : ConditionValue.expression(negate(value.expression()));
        }

        ConditionValue and(ConditionValue right, ConditionValue left) {
            Guard leftGuard = left.asGuard(guards);
            Guard rightGuard = right.asGuard(guards);
            Expression expression = combine(left.expression(), "&&", right.expression());
            return leftGuard != null && rightGuard != null
                    ? ConditionValue.guard(guards.and(leftGuard, rightGuard), expression)
                    : ConditionValue.expression(expression);
        }

        ConditionValue or(ConditionValue right, ConditionValue left) {
            Guard leftGuard = left.asGuard(guards);
            Guard rightGuard = right.asGuard(guards);
            Expression expression = combine(left.expression(), "||", right.expression());
            return leftGuard != null && rightGuard != null
                    ? ConditionValue.guard(guards.or(leftGuard, rightGuard), expression)
                    : ConditionValue.expression(expression);
        }

        ConditionValue compare(boolean equal, ConditionValue right, ConditionValue left) {
            Guard direct = compareGuard(left, right);
            if (direct == null) {
                direct = compareGuard(right, left);
            }
            Expression expression = combine(left.expression(), equal ? "==" : "!=", right.expression());
            if (direct != null) {
                return ConditionValue.guard(equal ? direct : guards.not(direct), expression);
            }
            return ConditionValue.expression(expression);
        }

        Guard compareGuard(ConditionValue symbolValue, ConditionValue literalValue) {
            if (symbolValue.symbol() == null || literalValue.literal() == null) {
                return null;
            }
            Symbol symbol = symbolValue.symbol();
            if (!symbol.guardable() || symbol.tainted()) {
                return null;
            }
            switch (symbol.domain().kind()) {
                case BOOLEAN:
                    if (literalValue.literal().type() == Value.Type.BOOLEAN) {
                        return guards.eq(symbol.id(), String.valueOf(literalValue.literal().getBoolean()));
                    }
                    if (literalValue.literal().type() == Value.Type.STRING) {
                        String literal = literalValue.literal().getString();
                        if ("true".equals(literal) || "false".equals(literal)) {
                            return guards.eq(symbol.id(), literal);
                        }
                    }
                    return null;
                case CHOICE:
                case FINITE_TEXT:
                    return literalValue.literal().type() == Value.Type.STRING
                            ? guards.eq(symbol.id(), literalValue.literal().getString())
                            : null;
                default:
                    return null;
            }
        }

        ConditionValue contains(ConditionValue right, ConditionValue left) {
            Guard direct = containsGuard(left, right);
            Expression expression = combine(left.expression(), "contains", right.expression());
            if (direct == null) {
                return ConditionValue.expression(expression);
            }
            return ConditionValue.guard(direct, expression);
        }

        Guard containsGuard(ConditionValue symbolValue, ConditionValue literalValue) {
            if (symbolValue.symbol() == null || literalValue.literal() == null) {
                return null;
            }
            Symbol symbol = symbolValue.symbol();
            if (symbol.domain().kind() != Spec.Kind.MEMBERSHIP || !symbol.guardable() || symbol.tainted()) {
                return null;
            }
            if (literalValue.literal().type() == Value.Type.STRING) {
                return guards.contains(symbol.id(), literalValue.literal().getString());
            }
            if (literalValue.literal().type() == Value.Type.LIST) {
                Guard result = guards.trueGuard();
                for (String item : literalValue.literal().getList()) {
                    result = guards.and(result, guards.contains(symbol.id(), item));
                }
                return result;
            }
            return null;
        }

        Guard residual(Expression expression) {
            return new Guard(guards.trueGuard().id(), expression.reduce());
        }

        Symbol findSymbol(String name) {
            Integer id = symbols.findId(name);
            return id == null ? null : symbols.symbol(id);
        }

        static Expression combine(Expression left, String operator, Expression right) {
            return Expression.create("(" + left.literal() + ") " + operator + " (" + right.literal() + ")").reduce();
        }

        static Expression negate(Expression expression) {
            if (expression == Expression.TRUE) {
                return Expression.FALSE;
            }
            if (expression == Expression.FALSE) {
                return Expression.TRUE;
            }
            return Expression.create("!(" + expression.literal() + ")").reduce();
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

        Expression expression() {
            return expression;
        }

        Symbol symbol() {
            return symbol;
        }

        Value<?> literal() {
            return literal;
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
                propagate(block.terminator(), current, entries, work);
            }
            return new Analysis(beforeByOp, afterByOp, usesById);
        }

        State transfer(Op op, State state) {
            switch (op.kind()) {
                case DECLARE_INPUT:
                    return define(state, op.symbolId(), new Fact(
                            state.path(),
                            Domain.Value.top(ir.symbols().symbol(op.symbolId()).domain())));
                case DEFINE_VALUE:
                    Symbol symbol = ir.symbols().symbol(op.symbolId());
                    Domain.Value value = evaluate(op.expression(), symbol, state);
                    return define(state, op.symbolId(), new Fact(state.path(), value));
                case RECORD_USE:
                    int useIndex = useIndexByOp.get(op.id());
                    Use existing = usesById.get(useIndex);
                    usesById.set(useIndex,
                            new Use(existing.id(), existing.symbolId(), guards.or(existing.guard(), state.path())));
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
            State merged = merge(entries.get(blockId), incoming);
            if (!merged.equals(entries.get(blockId))) {
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
            Guard path = guards.or(current.path(), incoming.path());
            Map<Integer, Fact> env = new LinkedHashMap<>(current.env());
            Set<Integer> keys = new HashSet<>(current.env().keySet());
            keys.addAll(incoming.env().keySet());
            for (int symbolId : keys) {
                Fact left = current.env().get(symbolId);
                Fact right = incoming.env().get(symbolId);
                if (left == null) {
                    env.put(symbolId, right);
                } else if (right == null) {
                    env.put(symbolId, left);
                } else {
                    env.put(symbolId, new Fact(
                            guards.or(left.definedUnder(), right.definedUnder()),
                            Domain.Value.join(left.value(), right.value())));
                }
            }
            return new State(path, env);
        }

        State define(State state, int symbolId, Fact fact) {
            Map<Integer, Fact> env = new LinkedHashMap<>(state.env());
            env.put(symbolId, fact);
            return new State(state.path(), env);
        }

        Domain.Value evaluate(Expression expression, Symbol symbol, State state) {
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
