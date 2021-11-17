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

import java.util.Objects;

/**
 * Base class for all statements.
 */
public abstract class Statement extends Node {

    private final Kind kind;

    protected Statement(Builder<?, ?> builder) {
        super(builder);
        this.kind = Objects.requireNonNull(builder.kind, "kind is null");
    }

    /**
     * Get the statement kind.
     * @return kind
     */
    public Kind statementKind() {
        return kind;
    }

    /**
     * Statements kind.
     */
    public enum Kind {

        /**
         * If statement.
         */
        IF,

        /**
         * Expression.
         */
        EXPRESSION,

        /**
         * Block.
         */
        BLOCK,

        /**
         * Data.
         */
        DATA
    }

    /**
     * Base builder class for statement types.
     *
     * @param <T> sub-type
     * @param <U> builder sub-type
     */
    public static abstract class Builder<T extends Statement, U extends Builder<T, U>> extends Node.Builder<T, U> {

        private final Kind kind;

        /**
         * Create a new statement builder.
         *
         * @param kind kind
         * @param type builder type
         */
        protected Builder(Kind kind, GenericType<U> type) {
            super(Node.Kind.STATEMENT, type);
            this.kind = kind;
        }
    }
}
