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
import java.util.Objects;

/**
 * Expression.
 */
public abstract class Expression extends Statement {

    private final Kind kind;

    /**
     * Create a new expression.
     *
     * @param builder builder
     */
    protected Expression(Builder<?, ?> builder) {
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

    /**
     * Visit this expression.
     *
     * @param visitor Visitor
     * @param arg     argument
     * @param <R>     generic type of the result
     * @param <A>     generic type of the arguments
     * @return result
     */
    public final <A, R> R accept(Visitor<A, R> visitor, A arg) {
        switch (kind) {
            case INVOCATION:
                return visitor.visitInvocation((Invocation) this, arg);
            case PRESET:
                return visitor.visitPreset((Preset) this, arg);
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Expression visitor.
     *
     * @param <A> argument
     * @param <R> type of the returned value
     */
    @SuppressWarnings("unused")
    public interface Visitor<A, R> {

        /**
         * Visit an invocation.
         *
         * @param invocation invocation
         * @param arg        argument
         * @return visit result
         */
        default R visitInvocation(Invocation invocation, A arg) {
            return null;
        }

        /**
         * Visit a preset.
         *
         * @param preset preset
         * @param arg    argument
         * @return visit result
         */
        default R visitPreset(Preset preset, A arg) {
            return null;
        }
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
         * Preset.
         */
        PRESET
    }

    /**
     * Expression builder.
     *
     * @param <T> sub-type
     * @param <U> builder sub-type
     */
    public static abstract class Builder<T extends Expression, U extends Builder<T, U>> extends Statement.Builder<T, U> {

        private final Kind kind;

        /**
         * Create a new expression builder.
         *
         * @param location location
         * @param position position
         * @param kind     kind
         */
        Builder(Path location, Position position, Kind kind) {
            super(location, position, Statement.Kind.EXPRESSION);
            this.kind = kind;
        }
    }
}
