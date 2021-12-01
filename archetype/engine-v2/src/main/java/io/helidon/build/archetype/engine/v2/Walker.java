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
import io.helidon.build.archetype.engine.v2.ast.Preset;
import io.helidon.build.archetype.engine.v2.ast.Script;
import io.helidon.build.archetype.engine.v2.ast.Statement;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.ListIterator;

/**
 * Block walker.
 */
public final class Walker {

    private final Deque<Statement> stack = new ArrayDeque<>();
    private final Deque<Node> parents = new ArrayDeque<>();
    private final Node.Visitor visitor;
    private boolean traversing;

    /**
     * Traverse the given block node with the specified visitor and argument.
     *
     * @param visitor visitor
     * @param root    node to traverse
     */
    public static void walk(Node.Visitor visitor, Block root) {
        new Walker(visitor).walk(root);
    }

    private Walker(Node.Visitor visitor) {
        this.visitor = new DelegateVisitor(visitor);
    }

    private void walk(Block root) {
        Node.VisitResult result = root.accept(visitor);
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
                result = stmt.accept(visitor);
            } else {
                if (stmt instanceof Block) {
                    result = stmt.acceptAfter(visitor);
                }
                parentId = parents.pop().nodeId();
            }
            if (!traversing) {
                stack.pop();
                if (result == Node.VisitResult.SKIP_SIBLINGS) {
                    while (!stack.isEmpty()) {
                        Statement peek = stack.peek();
                        if (!(peek instanceof Block)) {
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
        root.acceptAfter(visitor);
    }

    private class DelegateVisitor implements Node.Visitor {

        final Node.Visitor delegate;

        DelegateVisitor(Node.Visitor delegate) {
            this.delegate = delegate;
        }

        @Override
        public Node.VisitResult visitScript(Script script) {
            return delegate.visitScript(script);
        }

        @Override
        public Node.VisitResult visitCondition(Condition condition) {
            Node.VisitResult result = delegate.visitCondition(condition);
            if (result == Node.VisitResult.CONTINUE) {
                stack.push(condition.then());
                parents.push(condition);
                traversing = true;
            }
            return result;
        }

        @Override
        public Node.VisitResult visitInvocation(Invocation invocation) {
            Node.VisitResult result = delegate.visitInvocation(invocation);
            if (result == Node.VisitResult.SKIP_SUBTREE || result == Node.VisitResult.TERMINATE) {
                return result;
            }
            Script script = ScriptLoader.load(invocation.scriptPath().resolve(invocation.src()));
            if (invocation.invocationKind() == Invocation.Kind.EXEC) {
                stack.push(script.body().wrap(Block.Kind.CD));
            } else {
                stack.push(script.body());
            }
            parents.push(invocation);
            return result;
        }

        @Override
        public Node.VisitResult visitPreset(Preset preset) {
            return delegate.visitPreset(preset);
        }

        @Override
        public Node.VisitResult visitBlock(Block block) {
            Node.VisitResult result = delegate.visitBlock(block);
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
                    result = visitor.postVisitBlock(block);
                }
            }
            return result;
        }

        @Override
        public Node.VisitResult postVisitBlock(Block block) {
            return delegate.postVisitBlock(block);
        }

        @Override
        public Node.VisitResult visitNode(Node node) {
            return delegate.visitNode(node);
        }
    }
}
