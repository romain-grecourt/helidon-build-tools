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

/**
 * Condition statement.
 */
public final class Condition extends Statement {

    private final String expression;
    private final Statement then;

    private Condition(Builder builder) {
        super(builder);
        this.expression = builder.expression;
        this.then = builder.then.build();
    }

    /**
     * Get the expression.
     *
     * @return expression
     */
    public String expression() {
        return expression;
    }

    @Override
    public <A> VisitResult accept(Visitor<A> visitor, A arg) {
        VisitResult result = visitor.visitCondition(this, arg);
        if (result != VisitResult.CONTINUE) {
            return result;
        }
        return then.accept(visitor, arg);
    }

    /**
     * Create a new builder.
     *
     * @param location location
     * @param position position
     * @return builder
     */
    public static Builder builder(Path location, Position position) {
        return new Builder(location, position);
    }

    /**
     * Condition statement builder.
     */
    public static final class Builder extends Statement.Builder<Condition, Builder> {

        private String expression;
        private Statement.Builder<?, ?> then;

        private Builder(Path location, Position position) {
            super(location, position, Statement.Kind.CONDITION);
        }

        /**
         * Set the expression
         *
         * @param expression expression
         * @return this builder
         */
        public Builder expression(String expression) {
            this.expression = expression;
            return this;
        }

        /**
         * Set the {@code then} statement.
         *
         * @param then statement builder
         * @return this builder
         */
        public Builder then(Statement.Builder<?, ?> then) {
            this.then = then;
            return this;
        }

        @Override
        public Condition build() {
            return new Condition(this);
        }
    }
}
