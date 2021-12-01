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
import java.util.Objects;

import static java.util.stream.Collectors.toUnmodifiableList;

/**
 * Block statement.
 */
public class Block extends Statement {

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
        this.statements = builder.statements.stream()
                                            .filter(b -> !(b instanceof Noop.Builder))
                                            .map(Statement.Builder::build)
                                            .collect(toUnmodifiableList());
    }

    /**
     * Create a new block.
     *
     * @param scriptPath script path
     * @param position   position
     * @param kind       kind
     * @param statements statements
     */
    protected Block(Path scriptPath, Position position, Kind kind, List<Statement> statements) {
        super(scriptPath, position);
        this.kind = Objects.requireNonNull(kind, "kind is null");
        this.statements = Objects.requireNonNull(statements, "statements is null");
    }

    /**
     * Get the nested statements.
     *
     * @return statements
     */
    public List<Statement> statements() {
        return statements;
    }

    /**
     * Wrap this block with a new kind.
     *
     * @param kind kind
     * @return block
     */
    public Block wrap(Block.Kind kind) {
        return new Block(scriptPath, position, kind, List.of(this));
    }

    /**
     * Block visitor.
     *
     * @param <A> argument type
     */
    public interface Visitor<A> {

        /**
         * Visit an input block.
         *
         * @param input input
         * @param arg   argument visitor
         */
        default void visitInput(Input input, A arg) {
            visitBlock(input, arg);
        }

        /**
         * Visit a step block.
         *
         * @param step step
         * @param arg  argument visitor
         */
        default void visitStep(Step step, A arg) {
            visitBlock(step, arg);
        }

        /**
         * Visit an output block.
         *
         * @param output output
         * @param arg    argument visitor
         */
        default void visitOutput(Output output, A arg) {
            visitBlock(output, arg);
        }

        /**
         * Visit a block.
         *
         * @param block block
         * @param arg   argument visitor
         */
        @SuppressWarnings("unused")
        default void visitBlock(Block block, A arg) {
        }
    }

    /**
     * Visit this block.
     *
     * @param visitor block visitor
     * @param arg     argument
     * @param <A>     argument type
     */
    public <A> void accept(Visitor<A> visitor, A arg) {
        visitor.visitBlock(this, arg);
    }

    @Override
    public final <A> VisitResult accept(Node.Visitor<A> visitor, A arg) {
        return visitor.preVisitBlock(this, arg);
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
         * Value.
         */
        VALUE,

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

        /**
         * Change dir.
         */
        CD,
    }

    /**
     * Create a new builder.
     *
     * @param scriptPath script path
     * @param position   position
     * @param kind       kind
     * @return builder
     */
    public static Builder builder(Path scriptPath, Position position, Kind kind) {
        return new Builder(scriptPath, position, kind);
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
         * @param scriptPath script path
         * @param position   position
         * @param kind       kind
         */
        protected Builder(Path scriptPath, Position position, Kind kind) {
            super(scriptPath, position);
            this.kind = kind;
        }

        @Override
        public Builder statement(Statement.Builder<? extends Statement, ?> builder) {
            statements.add(builder);
            return this;
        }

        @Override
        protected Block doBuild() {
            return new Block(this);
        }
    }
}
