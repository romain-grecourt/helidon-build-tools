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

import java.util.Objects;

/**
 * Expression.
 */
public final class Expression extends Statement {

    private final Kind kind;

    private Expression(Builder builder) {
        super(builder);
        this.kind = Objects.requireNonNull(builder.kind, "kind is null");
    }

    /**
     * Get the expression kind.
     *
     * @return kind
     */
    public Kind expressionKind() {
        return kind;
    }

    @Override
    public <A, R> R accept(Visitor<A, R> visitor, A arg) {
        return null;
    }

    /**
     * Expressions kind.
     */
    public enum Kind {

        /**
         * Invocation.
         */
        INVOCATION,

        /**
         * Input value.
         */
        INPUT_VALUE
    }

    /**
     * Expression builder.
     */
    public static class Builder extends Statement.Builder<Expression, Builder> {

        private Kind kind;

        /**
         * Create a new expression builder.
         */
        Builder() {
            super(Statement.Kind.EXPRESSION, BuilderTypes.EXPRESSION);
        }

        /**
         * Set the expression kind.
         *
         * @param kind kind
         * @return this builder
         */
        public Builder expressionKind(Kind kind) {
            this.kind = kind;
            return this;
        }

        @Override
        public Expression build() {
            return new Expression(this);
        }
    }
}
