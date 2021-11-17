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

import io.helidon.build.common.GenericType;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Block statements.
 */
public class Block extends Statement {

    private final Kind kind;
    private final List<Statement> stmts;

    /**
     * Create a new block.
     *
     * @param builder builder
     */
    protected Block(Builder<?, ?> builder) {
        super(builder);
        this.kind = Objects.requireNonNull(builder.kind, "kind is null");
        this.stmts = builder.stmts.stream()
                                  .map(Statement.Builder::build)
                                  .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Get the nested statements
     *
     * @return nested statements
     */
    public List<Statement> statements() {
        return stmts;
    }

    @Override
    public <A, R> R accept(Visitor<A, R> visitor, A arg) {
        return visitor.visit(this, arg);
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
     * Block statements kind.
     */
    public enum Kind {

        /**
         * Inputs.
         */
        INPUTS,

        /**
         * Input values.
         */
        INPUT_VALUES,

        /**
         * Executable.
         */
        EXECUTABLE,

        /**
         * Output.
         */
        OUTPUT,

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

        private Kind kind;
        private final List<Statement.Builder<Statement, ?>> stmts = new LinkedList<>();

        /**
         * Create a new block builder.
         */
        Builder() {
            super(Statement.Kind.BLOCK, (GenericType<U>) Node.BuilderTypes.BLOCK);
        }

        /**
         * Create a new block builder.
         *
         * @param kind kind
         * @param type builder type
         */
        protected Builder(Kind kind, GenericType<U> type) {
            super(Statement.Kind.BLOCK, type);
            this.kind = kind;
        }

        /**
         * Set the block kind.
         *
         * @param kind kind
         * @return this builder
         */
        public U blockKind(Kind kind) {
            this.kind = kind;
            return (U) this;
        }

        /**
         * Add a statement.
         *
         * @param builder statement builder
         * @return this builder
         */
        public U statement(Statement.Builder<? extends Statement, ?> builder) {
            stmts.add((Statement.Builder<Statement, ?>) builder);
            return (U) this;
        }

        @Override
        public T build() {
            return (T) new Block(this);
        }
    }
}
