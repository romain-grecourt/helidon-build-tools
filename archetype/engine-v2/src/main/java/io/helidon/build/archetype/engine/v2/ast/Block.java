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
import java.util.stream.Collectors;

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
    protected Block(Builder<?, ?> builder) {
        super(builder);
        this.kind = Objects.requireNonNull(builder.kind, "kind is null");
        this.statements = builder.statements.stream()
                                            .map(Statement.Builder::build)
                                            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Get the nested statements
     *
     * @return nested statements
     */
    public List<Statement> statements() {
        return statements;
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
     * Visit this block.
     *
     * @param visitor Visitor
     * @param arg     argument
     * @param <R>     generic type of the result
     * @param <A>     generic type of the arguments
     * @return result
     */
    public final <A, R> R accept(Visitor<A, R> visitor, A arg) {
        switch (kind) {
            case INPUTS:
                return visitor.visitInputs(this, arg);
            case PRESETS:
                return visitor.visitPresets(this, arg);
            case EXECUTABLE:
                return visitor.visitExecutable((Executable) this, arg);
            case OUTPUT:
                return visitor.visitOutput((Output) this, arg);
            case MODEL:
                return visitor.visitModel((Model) this, arg);
            case UNKNOWN:
                return visitor.visitUnknown(this, arg);
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Block visitor.
     *
     * @param <A> argument
     * @param <R> type of the returned value
     */
    @SuppressWarnings("unused")
    public interface Visitor<A, R> {

        /**
         * Visit an inputs block.
         *
         * @param block block
         * @param arg   argument
         * @return visit result
         */
        default R visitInputs(Block block, A arg) {
            return null;
        }

        /**
         * Visit a presets block.
         *
         * @param block block
         * @param arg   argument
         * @return visit result
         */
        default R visitPresets(Block block, A arg) {
            return null;
        }

        /**
         * Visit an executable block.
         *
         * @param block block
         * @param arg   argument
         * @return visit result
         */
        default R visitExecutable(Executable block, A arg) {
            return null;
        }

        /**
         * Visit an executable block.
         *
         * @param block block
         * @param arg   argument
         * @return visit result
         */
        default R visitOutput(Output block, A arg) {
            return null;
        }

        /**
         * Visit a model block.
         *
         * @param block block
         * @param arg   argument
         * @return visit result
         */
        default R visitModel(Model block, A arg) {
            return null;
        }

        /**
         * Visit an unknown block.
         *
         * @param block block
         * @param arg   argument
         * @return visit result
         */
        default R visitUnknown(Block block, A arg) {
            return null;
        }
    }

    /**
     * Block statements kind.
     */
    public enum Kind {

        /**
         * Inputs.
         */
        INPUTS,

        /**
         * Presets.
         */
        PRESETS,

        /**
         * Executable.
         */
        EXECUTABLE,

        /**
         * Output.
         */
        OUTPUT,

        /**
         * Model.
         */
        MODEL,

        /**
         * Unknown.
         */
        UNKNOWN
    }

    /**
     * Statements builder.
     *
     * @param <T> sub-type
     * @param <U> builder sub-type
     */
    @SuppressWarnings("unchecked")
    public static class Builder<T extends Block, U extends Builder<T, U>> extends Statement.Builder<T, U> {

        private final Kind kind;
        private final List<Statement.Builder<Statement, ?>> statements = new LinkedList<>();

        /**
         * Create a new block builder.
         *
         * @param location location
         * @param position position
         * @param kind     kind
         */
        Builder(Path location, Position position, Kind kind) {
            super(location, position, Statement.Kind.BLOCK);
            this.kind = kind;
        }

        @Override
        public U statement(Statement.Builder<? extends Statement, ?> builder) {
            statements.add((Statement.Builder<Statement, ?>) builder);
            return (U) this;
        }

        @Override
        public T build() {
            return (T) new Block(this);
        }
    }
}
