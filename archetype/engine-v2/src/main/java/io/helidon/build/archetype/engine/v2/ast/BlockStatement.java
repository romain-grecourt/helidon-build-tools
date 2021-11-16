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

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Block statements.
 */
public abstract class BlockStatement extends Statement {

    private final List<Statement> stmts;

    protected BlockStatement(Builder<?, ?> builder) {
        super(builder);
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

    /**
     * Get the nested statements of the given type.
     *
     * @param clazz type
     * @param <C>   statement type
     * @return stream of statements
     */
    public <C extends Statement> Stream<C> statements(Class<C> clazz) {
        return stmts.stream().filter(clazz::isInstance).map(clazz::cast);
    }

    /**
     * Statements builder.
     *
     * @param <T> sub-type
     * @param <U> builder sub-type
     */
    @SuppressWarnings("unchecked")
    public static abstract class Builder<T extends BlockStatement, U extends Builder<T, U>>
            extends Statement.Builder<T, U> {

        private final List<Statement.Builder<Statement, ?>> stmts = new LinkedList<>();

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
    }
}
