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

import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.ast.Condition;
import io.helidon.build.archetype.engine.v2.ast.Invocation;
import io.helidon.build.archetype.engine.v2.ast.Node;
import io.helidon.build.archetype.engine.v2.ast.Noop;
import io.helidon.build.archetype.engine.v2.ast.Preset;
import io.helidon.build.archetype.engine.v2.ast.Script;
import io.helidon.build.archetype.engine.v2.ast.Statement;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * Block walker.
 *
 * @param <A> visitor argument type
 */
class Walker<A> {

    private final LinkedList<Statement> stack = new LinkedList<>();
    private final LinkedList<Node> parents = new LinkedList<>();
    private final Node.Visitor<A> visitor;
    private boolean traversing;

    /**
     * Create a new walker.
     *
     * @param visitor visitor
     */
    Walker(Node.Visitor<A> visitor) {
        this.visitor = new DelegateVisitor(visitor);
    }

    /**
     * Walk.
     *
     * @param root root block to visit
     * @param arg  visitor argument
     */
    void walk(Block root, A arg) {
        Node.VisitResult result = root.accept(visitor, arg);
        if (result != Node.VisitResult.CONTINUE) {
            return;
        }
        while (!stack.isEmpty()) {
            traversing = false;
            Statement stmt = stack.peek();
            Node parent = parents.peek();
            int parentId = parent != null ? parent.nodeId() : 0;
            int nodeId = stmt.nodeId();
            if (nodeId != parentId) {
                result = stmt.accept(visitor, arg);
            } else {
                if (stmt.statementKind() == Statement.Kind.BLOCK) {
                    result = visitor.postVisitBlock((Block) stmt, arg);
                }
                parentId = parents.pop().nodeId();
            }
            if (!traversing) {
                stack.pop();
                if (result == Node.VisitResult.SKIP_SIBLINGS) {
                    while (!stack.isEmpty()) {
                        Statement peek = stack.peek();
                        if (peek.statementKind() != Statement.Kind.BLOCK) {
                            continue;
                        } else if (peek.nodeId() == parentId) {
                            break;
                        }
                        stack.pop();
                    }
                } else if (result == Node.VisitResult.TERMINATE) {
                    return;
                }
            }
        }
        visitor.postVisitBlock(root, arg);
    }

    private class DelegateVisitor implements Node.Visitor<A> {

        final Node.Visitor<A> delegate;

        DelegateVisitor(Node.Visitor<A> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Node.VisitResult visitScript(Script script, A arg) {
            return delegate.visitScript(script, arg);
        }

        @Override
        public Node.VisitResult visitCondition(Condition condition, A arg) {
            Node.VisitResult result = delegate.visitCondition(condition, arg);
            if (result == Node.VisitResult.CONTINUE) {
                stack.push(condition.then());
                parents.push(condition);
                traversing = true;
            }
            return result;
        }

        @Override
        public Node.VisitResult visitInvocation(Invocation invocation, A arg) {
            Node.VisitResult result = delegate.visitInvocation(invocation, arg);
            if (result == Node.VisitResult.SKIP_SUBTREE || result == Node.VisitResult.TERMINATE) {
                return result;
            }
            Script script = ScriptLoader.load(invocation.location().resolve(invocation.src()));
            if (invocation.invocationKind() == Invocation.Kind.EXEC) {
                stack.push(script.body().wrap(Block.Kind.CD));
            } else {
                stack.push(script.body());
            }
            parents.push(invocation);
            return result;
        }

        @Override
        public Node.VisitResult visitPreset(Preset preset, A arg) {
            return delegate.visitPreset(preset, arg);
        }

        @Override
        public Node.VisitResult preVisitBlock(Block block, A arg) {
            Node.VisitResult result = delegate.preVisitBlock(block, arg);
            if (result != Node.VisitResult.TERMINATE) {
                List<Statement> statements = block.statements();
                int children = statements.size();
                if (result != Node.VisitResult.SKIP_SUBTREE && children > 0) {
                    ListIterator<Statement> it = statements.listIterator(children);
                    while (it.hasPrevious()) {
                        stack.push(it.previous());
                    }
                    parents.push(block);
                    traversing = true;
                } else if (children == 0) {
                    result = visitor.postVisitBlock(block, arg);
                }
            }
            return result;
        }

        @Override
        public Node.VisitResult postVisitBlock(Block block, A arg) {
            return delegate.postVisitBlock(block, arg);
        }

        @Override
        public Node.VisitResult visitNoop(Noop noop, A arg) {
            return delegate.visitNoop(noop, arg);
        }

        @Override
        public Node.VisitResult visitNode(Node node, A arg) {
            return delegate.visitNode(node, arg);
        }
    }
}
