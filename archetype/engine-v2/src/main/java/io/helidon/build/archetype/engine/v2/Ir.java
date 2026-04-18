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
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

import io.helidon.build.archetype.engine.v2.Context.Scope;
import io.helidon.build.archetype.engine.v2.Domain.Guard;
import io.helidon.build.archetype.engine.v2.Domain.Guards;
import io.helidon.build.archetype.engine.v2.Domain.Spec;
import io.helidon.build.archetype.engine.v2.Domain.Symbol;
import io.helidon.build.archetype.engine.v2.Domain.Table;
import io.helidon.build.archetype.engine.v2.Expression.Operator;
import io.helidon.build.archetype.engine.v2.Expression.Token;
import io.helidon.build.archetype.engine.v2.Node.Kind;

final class Ir {
    private final Block[] blocks;
    private final Table table;
    private final Op[] ops;
    private final Guards guards;
    private final Control[] controls;

    private Ir(List<Block> blocks, Table table, List<Op> ops, Guards guards, List<Control> controls) {
        if (controls.isEmpty()) {
            throw new IllegalArgumentException("controls is empty");
        }
        this.blocks = blocks.toArray(Block[]::new);
        this.table = table;
        this.ops = ops.toArray(Op[]::new);
        this.guards = guards;
        this.controls = controls.toArray(Control[]::new);
    }

    Table table() {
        return table;
    }

    Guards guards() {
        return guards;
    }

    Block[] blocks() {
        return blocks;
    }

    Op[] ops() {
        return ops;
    }

    Control[] controls() {
        return controls;
    }

    Guard directBooleanGuard(String key) {
        return directBooleanGuard(table, guards, key);
    }

    Guard directOptionGuard(String key, Node input, Node option) {
        return directOptionGuard(table, guards, key, input, option);
    }

    Guard lowerCondition(Scope scope, Expression expression) {
        return lowerCondition(table, guards, scope, expression);
    }

    void visitDataflowBlocks(IntFunction<IntPredicate> visitor) {
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(0);
        while (!queue.isEmpty()) {
            int blockId = queue.removeFirst();
            IntPredicate predicate = visitor.apply(blockId);
            blocks[blockId].term.visitTargets(targetId -> {
                if (predicate.test(targetId)) {
                    queue.addLast(targetId);
                }
            });
        }
    }

    static Ir lower(Scope scope, Node root) {
        return new Lowerer(scope).lower(root);
    }

    private static Guard directBooleanGuard(Table table, Guards guards, String key) {
        Symbol sym = table.symbol(table.findId(key));
        if (!sym.guardable() || sym.tainted() || sym.domain().kind() != Spec.Kind.BOOLEAN) {
            return Guard.TRUE;
        }
        return guards.eq(sym.id(), "true");
    }

    private static Guard directOptionGuard(Table table, Guards guards, String key, Node input, Node option) {
        Symbol sym = table.symbol(table.findId(key));
        String value = option.value().getString();
        switch (input.kind()) {
            case INPUT_ENUM:
                if (!sym.guardable() || sym.tainted()) {
                    return Guard.TRUE;
                }
                return sym.domain().scalar() ? guards.eq(sym.id(), value) : Guard.TRUE;
            case INPUT_LIST:
                if (!sym.guardable() || sym.tainted() || sym.domain().kind() != Spec.Kind.MEMBERSHIP) {
                    return Guard.TRUE;
                }
                return guards.contains(sym.id(), value);
            default:
                throw new IllegalArgumentException("Unsupported option parent: " + input.kind());
        }
    }

    private static Guard lowerCondition(Table table, Guards guards, Scope scope, Expression expression) {
        return new ConditionLowerer(table, guards, scope).lower(expression);
    }

    static final class Op {
        private final Kind kind;
        private final int blockId;
        private final Node source;
        private final int symbolId;
        private final Expression expression;

        Op(Kind kind, int blockId, Node source, int symbolId, Expression expression) {
            this.kind = kind;
            this.blockId = blockId;
            this.source = source;
            this.symbolId = symbolId;
            this.expression = expression;
        }

        Kind kind() {
            return kind;
        }

        int blockId() {
            return blockId;
        }

        Node source() {
            return source;
        }

        int symbolId() {
            return symbolId;
        }

        Expression expression() {
            return expression;
        }

        enum Kind {
            DECLARE_INPUT,
            DECLARE_OPTION,
            DEFINE_VALUE,
            EMIT
        }
    }

    static final class Terminator {
        private static final Terminator NONE = new Terminator(Kind.NONE, null, -1, -1, -1);

        private final Kind kind;
        private final Node source;
        private final int targetId;
        private final int trueId;
        private final int falseId;

        Terminator(Kind kind, Node source, int targetId, int trueId, int falseId) {
            this.kind = kind;
            this.source = source;
            this.targetId = targetId;
            this.trueId = trueId;
            this.falseId = falseId;
        }

        Kind kind() {
            return kind;
        }

        Node source() {
            return source;
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

        void visitTargets(IntConsumer visitor) {
            switch (kind) {
                case GOTO:
                    visitor.accept(targetId);
                    break;
                case BRANCH:
                    visitor.accept(trueId);
                    visitor.accept(falseId);
                    break;
                case RETURN:
                case UNREACHABLE:
                case NONE:
                default:
                    break;
            }
        }

        enum Kind {
            NONE,
            GOTO,
            BRANCH,
            RETURN,
            UNREACHABLE
        }
    }

    static final class Control {
        private final int parentId;
        private final Guard guard;

        Control(int parentId, Guard guard) {
            this.parentId = parentId;
            this.guard = guard;
        }

        int parentId() {
            return parentId;
        }

        Guard guard() {
            return guard;
        }
    }

    static final class Block {
        private final int controlId;
        private final List<Integer> opIds = new ArrayList<>();
        private Terminator term = Terminator.NONE;

        Block(int controlId) {
            this.controlId = controlId;
        }

        int controlId() {
            return controlId;
        }

        List<Integer> opIds() {
            return opIds;
        }

        Terminator term() {
            return term;
        }
    }

    private static final class Lowerer {
        private final Scope scope;
        private final Map<String, SymbolSeed> symbolSeeds = new LinkedHashMap<>();
        private final Map<String, SymbolSeed> declaredInputSymbols = new LinkedHashMap<>();
        private final List<Control> controls = new ArrayList<>();
        private final List<Block> blocks = new ArrayList<>();
        private final List<Op> ops = new ArrayList<>();
        private Table table;
        private Guards guards;

        Lowerer(Scope scope) {
            this.scope = scope;
            controls.add(new Control(-1, null));
        }

        Ir lower(Node root) {
            collectDeclaredInputs(root, scope);
            collectSymbols(root, scope);
            List<Symbol> symbols = new ArrayList<>(symbolSeeds.size());
            Map<String, Integer> ids = new LinkedHashMap<>();
            int id = 0;
            for (SymbolSeed seed : symbolSeeds.values()) {
                symbols.add(new Symbol(id, seed.name, seed.spec, seed.guardable, seed.tainted));
                ids.put(seed.name, id);
                id++;
            }
            table = new Table(symbols, ids);
            guards = new Guards(table);

            int entryBlock = newBlock(0);
            int exitBlock = lowerChildren(root.children(), entryBlock, 0, scope);
            terminateIfMissing(exitBlock, Terminator.Kind.RETURN, root, -1);

            for (int blockId = 0; blockId < blocks.size(); blockId++) {
                if (blocks.get(blockId).term.kind() == Terminator.Kind.NONE) {
                    block(blockId).term = new Terminator(Terminator.Kind.UNREACHABLE, root, -1, -1, -1);
                }
            }
            return new Ir(blocks, table, ops, guards, controls);
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
                        addSymbol(nodeScope.definitionKey(node.attribute("path").getString()), Spec.BOOLEAN, true, false);
                        break;
                    case PRESET_ENUM:
                    case VARIABLE_ENUM:
                    case PRESET_TEXT:
                    case VARIABLE_TEXT:
                        addSymbol(nodeScope, node);
                        break;
                    case PRESET_LIST:
                    case VARIABLE_LIST: {
                        String key = nodeScope.definitionKey(node.attribute("path").getString());
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

        void traverse(Node root, Scope rootScope, BiConsumer<Node, Scope> visitor) {
            Map<Node, Scope> scopes = new IdentityHashMap<>();
            scopes.put(root, rootScope);
            for (Node node : root.traverse()) {
                Scope scope = scopes.get(node);
                if (scope == null) {
                    throw new IllegalStateException("Missing scope for node: " + node);
                }
                visitor.accept(node, scope);
                Scope childScope = childScope(scope, node);
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
                    append(blockId, Op.Kind.EMIT, node, -1, null);
                    return lowerChildren(node.children(), blockId, controlId, scope);
                case INPUT_OPTION:
                    return lowerOption(node, blockId, controlId, scope);
                default:
                    return lowerChildren(node.children(), blockId, controlId, scope);
            }
        }

        int lowerInput(Node node, int blockId, int controlId, Scope scope) {
            Scope inputScope = scope.getOrCreate(node);
            int id = table.findId(inputScope.key());
            append(blockId, Op.Kind.DECLARE_INPUT, node, id, null);
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
            int id = table.findId(scope.key());
            if (id < 0) {
                throw new IllegalStateException("Missing option symbol for scope: " + scope.key());
            }
            append(blockId, Op.Kind.DECLARE_OPTION, node, id, null);
            Guard guard = optionGuard(input, node, scope);
            return lowerGuardedChildren(node, blockId, controlId, guard, node.children(), scope);
        }

        int lowerDefinition(Node node, int blockId, int controlId, Scope scope) {
            int id = table.findId(scope.definitionKey(node.attribute("path").getString()));
            if (id >= 0) {
                Expression expr = new Expression(List.of(Token.of(node.value())), true);
                append(blockId, Op.Kind.DEFINE_VALUE, node, id, expr);
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
            terminateIfMissing(trueExit, Terminator.Kind.GOTO, node, joinBlock);
            terminateIfMissing(falseBlock, Terminator.Kind.GOTO, node, joinBlock);
            return joinBlock;
        }

        Guard guard(Expression expression, Scope scope) {
            return Ir.lowerCondition(table, guards, scope, expression);
        }

        void append(int blockId, Op.Kind kind, Node source, int id, Expression expression) {
            block(blockId).opIds.add(ops.size());
            ops.add(new Op(kind, blockId, source, id, expression));
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
            String key = scope.definitionKey(node.attribute("path").getString());
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
            if (block(blockId).term.kind() != Terminator.Kind.NONE) {
                throw new IllegalStateException(String.format("Block %d already terminated", blockId));
            }
            block(blockId).term = new Terminator(Terminator.Kind.BRANCH, source, -1, trueId, falseId);
        }

        void terminateIfMissing(int blockId, Terminator.Kind kind, Node source, int targetId) {
            if (block(blockId).term.kind() == Terminator.Kind.NONE) {
                block(blockId).term = new Terminator(kind, source, targetId, -1, -1);
            }
        }

        int newBlock(int controlId) {
            blocks.add(new Block(controlId));
            return blocks.size() - 1;
        }

        int nextControl(int parentId, Guard edgeGuard) {
            int id = controls.size();
            controls.add(new Control(parentId, edgeGuard));
            return id;
        }

        Block block(int blockId) {
            if (blockId < 0 || blockId >= blocks.size()) {
                throw new IllegalStateException("Unknown block id: " + blockId);
            }
            return blocks.get(blockId);
        }

        static Scope childScope(Scope scope, Node node) {
            return node.kind().isInput() ? scope.getOrCreate(node) : scope;
        }

        static String[] optionValues(Node input) {
            Set<String> values = new TreeSet<>();
            for (Node option : Nodes.options(input)) {
                values.add(option.value().getString());
            }
            return values.toArray(String[]::new);
        }

        static Spec inputSpec(Node node) {
            String[] values = optionValues(node);
            if (values.length == 0) {
                return Spec.OPEN_TEXT;
            }
            if (node.kind() == Kind.INPUT_ENUM) {
                return new Spec(Spec.Kind.CHOICE, values);
            }
            return new Spec(Spec.Kind.MEMBERSHIP, values);
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
            Spec.Kind kind = spec.kind();
            Spec.Kind otherKind = other.spec.kind();
            if (kind == Spec.Kind.OPEN_TEXT || otherKind == Spec.Kind.OPEN_TEXT) {
                return new SymbolSeed(name, Spec.OPEN_TEXT, false, tainted || other.tainted);
            }
            if (kind == Spec.Kind.MEMBERSHIP || otherKind == Spec.Kind.MEMBERSHIP) {
                if (kind != Spec.Kind.MEMBERSHIP || otherKind != Spec.Kind.MEMBERSHIP) {
                    return new SymbolSeed(name, Spec.OPEN_TEXT, false, tainted || other.tainted);
                }
                Set<String> choices = new TreeSet<>(Arrays.asList(spec.values()));
                choices.addAll(Arrays.asList(other.spec.values()));
                String[] values = choices.toArray(String[]::new);
                Spec merged = new Spec(Spec.Kind.MEMBERSHIP, values);
                return new SymbolSeed(name, merged, guardable && other.guardable, tainted || other.tainted);
            }
            if (kind == Spec.Kind.BOOLEAN || otherKind == Spec.Kind.BOOLEAN) {
                if (kind == Spec.Kind.BOOLEAN && otherKind == Spec.Kind.BOOLEAN) {
                    return new SymbolSeed(name, Spec.BOOLEAN, guardable && other.guardable, tainted || other.tainted);
                }
                return new SymbolSeed(name, Spec.OPEN_TEXT, false, tainted || other.tainted);
            }
            Set<String> choices = new TreeSet<>(Arrays.asList(spec.values()));
            choices.addAll(Arrays.asList(other.spec.values()));
            String[] values = choices.toArray(String[]::new);
            Spec merged = kind == Spec.Kind.CHOICE || otherKind == Spec.Kind.CHOICE
                    ? new Spec(Spec.Kind.CHOICE, values)
                    : new Spec(Spec.Kind.FINITE_TEXT, values);
            return new SymbolSeed(name, merged, guardable && other.guardable, tainted || other.tainted);
        }
    }

    private static final class ConditionOperand {
        private final Guard guard;
        private final Symbol symbol;
        private final Value<?> literal;

        ConditionOperand(Guard guard, Symbol symbol, Value<?> literal) {
            this.guard = guard;
            this.symbol = symbol;
            this.literal = literal;
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
            Deque<ConditionOperand> stack = new ArrayDeque<>(capacity);
            for (Token token : expression.tokens()) {
                if (token.isVariable()) {
                    stack.addLast(new ConditionOperand(null, findSymbol(scope.key(token.variable())), null));
                    continue;
                }
                if (token.isOperand()) {
                    stack.addLast(new ConditionOperand(null, null, token.operand()));
                    continue;
                }
                switch (token.operator()) {
                    case NOT: {
                        ConditionOperand operand = stack.removeLast();
                        Guard direct = asGuard(operand);
                        if (direct == null) {
                            return guards.residualGuard(expression);
                        }
                        stack.addLast(new ConditionOperand(guards.not(direct), null, null));
                        break;
                    }
                    case AND:
                    case OR: {
                        Guard right = asGuard(stack.removeLast());
                        Guard left = asGuard(stack.removeLast());
                        if (left == null || right == null) {
                            return guards.residualGuard(expression);
                        }
                        if (token.operator() == Operator.OR) {
                            stack.addLast(new ConditionOperand(guards.or(left, right), null, null));
                        } else {
                            stack.addLast(new ConditionOperand(guards.and(left, right), null, null));
                        }
                        break;
                    }
                    case EQUAL:
                    case NOT_EQUAL: {
                        ConditionOperand right = stack.removeLast();
                        ConditionOperand left = stack.removeLast();
                        Guard direct = compare(left, right, token.operator() == Operator.EQUAL);
                        if (direct == null) {
                            return guards.residualGuard(expression);
                        }
                        stack.addLast(new ConditionOperand(direct, null, null));
                        break;
                    }
                    case CONTAINS: {
                        ConditionOperand right = stack.removeLast();
                        ConditionOperand left = stack.removeLast();
                        Guard direct = contains(left, right);
                        if (direct == null) {
                            return guards.residualGuard(expression);
                        }
                        stack.addLast(new ConditionOperand(direct, null, null));
                        break;
                    }
                    default:
                        return guards.residualGuard(expression);
                }
            }
            if (stack.size() != 1) {
                throw new IllegalStateException("Unexpected expression stack size: " + stack.size());
            }
            Guard guard = asGuard(stack.peekLast());
            return guard != null ? guard : guards.residualGuard(expression);
        }

        Guard compare(ConditionOperand left, ConditionOperand right, boolean equal) {
            Guard direct = compareGuard(left.symbol, right.literal);
            if (direct == null) {
                direct = compareGuard(right.symbol, left.literal);
            }
            if (direct != null && !equal) {
                direct = guards.not(direct);
            }
            return direct;
        }

        Guard compareGuard(Symbol sym, Value<?> literal) {
            if (sym != null && literal != null && sym.guardable() && !sym.tainted()) {
                if (literal.type() != Value.Type.BOOLEAN) {
                    if (sym.domain().scalar() && literal.type() == Value.Type.STRING) {
                        String scalar = literal.getString();
                        if (sym.domain().contains(scalar)) {
                            return guards.eq(sym.id(), scalar);
                        }
                    }
                } else if (sym.domain().kind() == Spec.Kind.BOOLEAN) {
                    String value = String.valueOf(literal.getBoolean());
                    return guards.eq(sym.id(), value);
                }
            }
            return null;
        }

        Guard contains(ConditionOperand left, ConditionOperand right) {
            Guard direct = containsGuard(left.symbol, right.literal);
            if (direct == null) {
                direct = scalarAnyGuard(right.symbol, left.literal);
            }
            return direct;
        }

        Guard containsGuard(Symbol sym, Value<?> literal) {
            if (sym != null && literal != null && sym.domain().kind() == Spec.Kind.MEMBERSHIP
                && sym.guardable() && !sym.tainted()) {
                if (literal.type() != Value.Type.STRING) {
                    if (literal.type() == Value.Type.LIST) {
                        return guards.containsAll(sym.id(), new TreeSet<>(literal.getList()));
                    }
                } else {
                    return guards.contains(sym.id(), literal.getString());
                }
            }
            return null;
        }

        Guard scalarAnyGuard(Symbol sym, Value<?> literal) {
            if (sym == null || literal == null || literal.type() != Value.Type.LIST
                || !sym.guardable() || sym.tainted() || !sym.domain().scalar()) {
                return null;
            }
            Guard result = Guard.FALSE;
            for (String item : literal.getList()) {
                result = guards.or(result, guards.eq(sym.id(), item));
            }
            return result;
        }

        Guard asGuard(ConditionOperand operand) {
            if (operand.guard != null) {
                return operand.guard;
            }
            if (operand.symbol != null && operand.symbol.guardable() && !operand.symbol.tainted()
                && operand.symbol.domain().kind() == Spec.Kind.BOOLEAN) {
                return guards.eq(operand.symbol.id(), "true");
            }
            if (operand.literal != null && operand.literal.type() == Value.Type.BOOLEAN) {
                return operand.literal.getBoolean() ? Guard.TRUE : Guard.FALSE;
            }
            return null;
        }

        Symbol findSymbol(String name) {
            int id = table.findId(name);
            return id < 0 ? null : table.symbol(id);
        }
    }
}
