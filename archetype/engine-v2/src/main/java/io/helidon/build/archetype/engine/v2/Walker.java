/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Condition;
import io.helidon.build.archetype.engine.v2.ast.Invocation;
import io.helidon.build.archetype.engine.v2.ast.Node;
import io.helidon.build.archetype.engine.v2.ast.Node.VisitResult;
import io.helidon.build.archetype.engine.v2.ast.Preset;
import io.helidon.build.archetype.engine.v2.ast.Script;
import io.helidon.build.archetype.engine.v2.ast.Statement;

/**
 * Block walker.
 * Provides facility to do depth-first traversal of the AST tree.
 *
 * @param <A> visitor argument type
 */
public final class Walker<A> {

    private final Deque<Node> callStack = new ArrayDeque<>();
    private final Deque<Statement> stack = new ArrayDeque<>();
    private final Deque<Node> parents = new ArrayDeque<>();
    private final Node.Visitor<A> visitor;
    private final Function<Invocation, Path> pathResolver;
    private boolean traversing;

    /**
     * Traverse the given block node with the specified visitor and argument.
     *
     * @param visitor visitor
     * @param block   node to traverse, must be non {@code null}
     * @param arg     visitor argument
     * @param cwd     cwd supplier
     * @param <A>     visitor argument type
     * @throws NullPointerException if block is {@code null}
     */
    public static <A> void walk(Node.Visitor<A> visitor, Block block, A arg, Supplier<Path> cwd) {
        new Walker<>(visitor, i -> cwd.get()).walk(block, arg);
    }

    /**
     * Traverse the given block node with the specified visitor and argument.
     *
     * @param visitor visitor
     * @param block   node to traverse, must be non {@code null}
     * @param arg     visitor argument
     * @param <A>     visitor argument type
     * @throws NullPointerException if block is {@code null}
     */
    public static <A> void walk(Node.Visitor<A> visitor, Block block, A arg) {
        new Walker<>(visitor, i -> i.scriptPath().getParent()).walk(block, arg);
    }

    private Walker(Node.Visitor<A> visitor, Function<Invocation, Path> pathResolver) {
        this.visitor = new DelegateVisitor(visitor);
        this.pathResolver = pathResolver;
    }

    private VisitResult accept(Node node, A arg, boolean before) {
        try {
            return before ? node.accept(visitor, arg) : node.acceptAfter(visitor, arg);
        } catch (Throwable ex) {
            throw new RuntimeException(ex.getMessage() + "\n"
                    + Stream.concat(Stream.of(node), callStack.stream())
                            .map(n -> "\tat " + n.scriptPath() + ":" + n.position())
                            .collect(Collectors.joining("\n")), ex);
        }
    }

    private void walk(Block block, A arg) {
        Objects.requireNonNull(block, "block is null");
        VisitResult result = accept(block, arg, true);
        if (result != VisitResult.CONTINUE || block.statements().isEmpty()) {
            return;
        }
        while (!stack.isEmpty()) {
            traversing = false;
            Statement stmt = stack.peek();
            Node parent = parents.peek();
            int parentId = parent != null ? parent.nodeId() : 0;
            int nodeId = stmt.nodeId();
            if (nodeId != parentId) {
                result = accept(stmt, arg, true);
            } else {
                if (stmt instanceof Block) {
                    result = accept(stmt, arg, false);
                }
                parentId = parents.pop().nodeId();
            }
            if (!traversing) {
                stack.pop();
                if (result == VisitResult.SKIP_SIBLINGS) {
                    while (!stack.isEmpty()) {
                        Statement peek = stack.peek();
                        if (!(peek instanceof Block)) {
                            continue;
                        } else if (peek.nodeId() == parentId) {
                            break;
                        }
                        stack.pop();
                    }
                } else if (result == VisitResult.TERMINATE) {
                    return;
                }
            }
        }
        accept(block, arg, false);
    }

    private class DelegateVisitor implements Node.Visitor<A> {

        private final Node.Visitor<A> delegate;

        DelegateVisitor(Node.Visitor<A> delegate) {
            this.delegate = delegate;
        }

        @Override
        public VisitResult visitCondition(Condition condition, A arg) {
            VisitResult result = delegate.visitCondition(condition, arg);
            if (result == VisitResult.CONTINUE) {
                stack.push(condition.then());
                parents.push(condition);
                traversing = true;
            }
            return result;
        }

        @Override
        public VisitResult visitInvocation(Invocation invocation, A arg) {
            VisitResult result = delegate.visitInvocation(invocation, arg);
            if (result == VisitResult.SKIP_SUBTREE || result == VisitResult.TERMINATE) {
                return result;
            }
            Script script = ScriptLoader.load(pathResolver.apply(invocation).resolve(invocation.src()));
            if (invocation.kind() == Invocation.Kind.EXEC) {
                stack.push(script.wrap(Block.Kind.CD));
            } else {
                stack.push(script.wrap(Block.Kind.NOOP));
            }
            callStack.push(invocation);
            parents.push(invocation);
            traversing = true;
            return result;
        }

        @Override
        public VisitResult visitPreset(Preset preset, A arg) {
            return delegate.visitPreset(preset, arg);
        }

        @Override
        public VisitResult visitBlock(Block block, A arg) {
            VisitResult result = delegate.visitBlock(block, arg);
            if (result != VisitResult.TERMINATE) {
                List<Statement> statements = block.statements();
                int children = statements.size();
                if (result != VisitResult.SKIP_SUBTREE && children > 0) {
                    ListIterator<Statement> it = statements.listIterator(children);
                    while (it.hasPrevious()) {
                        stack.push(it.previous());
                    }
                    parents.push(block);
                    traversing = true;
                } else {
                    result = visitor.postVisitBlock(block, arg);
                }
            }
            return result;
        }

        @Override
        public VisitResult postVisitBlock(Block block, A arg) {
            switch (block.blockKind()) {
                case NOOP:
                case CD:
                    callStack.pop();
                    break;
                default:
            }
            return delegate.postVisitBlock(block, arg);
        }

        @Override
        public VisitResult visitAny(Node node, A arg) {
            return delegate.visitAny(node, arg);
        }

        @Override
        public VisitResult postVisitAny(Node node, A arg) {
            return delegate.visitAny(node, arg);
        }
    }
}
