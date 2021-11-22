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

package io.helidon.build.archetype.engine.v2.ast;

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toUnmodifiableList;

/**
 * Block statement.
 */
public class Block extends Statement {

    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    private final int id;
    private final Kind kind;
    private final List<Statement> statements;

    /**
     * Create a new block.
     *
     * @param builder builder
     */
    protected Block(Builder builder) {
        super(builder);
        this.kind = Objects.requireNonNull(builder.kind, "kind is null");
        this.statements = builder.statements.stream().map(Statement.Builder::build).collect(toUnmodifiableList());
        this.id = NEXT_ID.updateAndGet(i -> i == Integer.MAX_VALUE ? 1 : i + 1);
    }

    /**
     * Block visitor.
     *
     * @param <R> result type
     * @param <A> argument type
     */
    public interface Visitor<R, A> {

        /**
         * Visit an input block.
         *
         * @param input input
         * @param arg   argument visitor
         * @return visit result
         */
        default R visitInput(Input input, A arg) {
            return visitBlock(input, arg);
        }

        /**
         * Visit an output block.
         *
         * @param output output
         * @param arg   argument visitor
         * @return visit result
         */
        default R visitOutput(Output output, A arg) {
            return visitBlock(output, arg);
        }

        /**
         * Visit a block.
         *
         * @param block block
         * @param arg   argument visitor
         * @return visit result
         */
        default R visitBlock(Block block, A arg) {
            return null;
        }
    }

    /**
     * Visit this block.
     *
     * @param visitor block visitor
     * @param arg     argument
     * @param <R>     result type
     * @param <A>     argument type
     * @return visit result
     */
    public <R, A> R accept(Visitor<R, A> visitor, A arg) {
        return visitor.visitBlock(this, arg);
    }

    @Override
    public final <A> VisitResult accept(Node.Visitor<A> visitor, A arg) {
        VisitResult result = visitor.preVisitBlock(this, arg);
        if (result != VisitResult.CONTINUE) {
            return result;
        }
        LinkedList<Statement> stack = new LinkedList<>(statements);
        LinkedList<Block> parents = new LinkedList<>();
        parents.push(this);
        while (!stack.isEmpty()) {
            Statement stmt = stack.peek();
            Block parent = parents.peek();
            int parentId = parent != null ? parent.id : 0;
            if (stmt.statementKind() != Statement.Kind.BLOCK) {
                result = stmt.accept(visitor, arg);
            } else {
                Block block = (Block) stmt;
                if (block.id == parentId) {
                    result = visitor.postVisitBlock(block, arg);
                    parentId = parents.pop().id;
                } else {
                    result = visitor.preVisitBlock(block, arg);
                    if (result != VisitResult.TERMINATE) {
                        int children = block.statements.size();
                        if (result != VisitResult.SKIP_SUBTREE && children > 0) {
                            ListIterator<Statement> it = block.statements.listIterator(children);
                            while (it.hasPrevious()) {
                                stack.push(it.previous());
                            }
                            parents.push(block);
                            continue;
                        } else if (children == 0) {
                            result = visitor.postVisitBlock(block, arg);
                        }
                    }
                }
            }
            stack.pop();
            if (result == VisitResult.SKIP_SIBLINGS) {
                while (!stack.isEmpty()) {
                    Statement peek = stack.peek();
                    if (peek.statementKind() != Statement.Kind.BLOCK) {
                        continue;
                    } else if (((Block) peek).id == parentId) {
                        break;
                    }
                    stack.pop();
                }
            } else if (result == VisitResult.TERMINATE) {
                return result;
            }
        }
        return visitor.postVisitBlock(this, arg);
    }

    /**
     * Get the block kind.
     *
     * @return kind
     */
    public Kind blockKind() {
        return kind;
    }

    /**
     * Block kind.
     */
    public enum Kind {

        /**
         * Script.
         */
        SCRIPT,

        /**
         * Step.
         */
        STEP,

        /**
         * Option.
         */
        OPTION,

        /**
         * Inputs.
         */
        INPUTS,

        /**
         * Text.
         */
        TEXT,

        /**
         * Boolean.
         */
        BOOLEAN,

        /**
         * Enum.
         */
        ENUM,

        /**
         * List.
         */
        LIST,

        /**
         * Presets.
         */
        PRESETS,

        /**
         * Output.
         */
        OUTPUT,

        /**
         * Templates.
         */
        TEMPLATES,

        /**
         * Template.
         */
        TEMPLATE,

        /**
         * Files.
         */
        FILES,

        /**
         * File.
         */
        FILE,

        /**
         * Model.
         */
        MODEL,

        /**
         * Map.
         */
        MAP,

        /**
         * Transformation.
         */
        TRANSFORMATION,

        /**
         * Includes.
         */
        INCLUDES,

        /**
         * Excludes.
         */
        EXCLUDES,
    }

    /**
     * Create a new builder.
     *
     * @param location location
     * @param position position
     * @param kind     kind
     * @return builder
     */
    public static Builder builder(Path location, Position position, Kind kind) {
        return new Builder(location, position, kind);
    }

    /**
     * Block builder.
     */
    public static class Builder extends Statement.Builder<Block, Builder> {

        protected final List<Statement.Builder<? extends Statement, ?>> statements = new LinkedList<>();
        protected final Kind kind;

        /**
         * Create a new builder.
         *
         * @param location location
         * @param position position
         * @param kind     kind
         */
        protected Builder(Path location, Position position, Kind kind) {
            super(location, position, Statement.Kind.BLOCK);
            this.kind = kind;
        }

        /**
         * Filter the nested statements as a stream of block of the given kind.
         *
         * @param kind kind
         * @return stream of block builder
         */
        protected Stream<Block.Builder> blocks(Block.Kind kind) {
            return statements.stream()
                             .filter(Block.Builder.class::isInstance)
                             .map(Block.Builder.class::cast)
                             .filter(block -> block.kind == kind);
        }

        @Override
        public Builder statement(Statement.Builder<? extends Statement, ?> builder) {
            statements.add(builder);
            return this;
        }

        @Override
        protected Block build0() {
            return new Block(this);
        }
    }
}
