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
 * Invocation.
 */
public final class Invocation extends Expression {

    private final Kind kind;

    private Invocation(Builder builder) {
        super(builder);
        this.kind = Objects.requireNonNull(builder.kind, "kind is null");
    }

    /**
     * Get the src.
     *
     * @return src
     */
    public String src() {
        return Attributes.SRC.get(this, ValueTypes.STRING);
    }

    /**
     * Visit this invocation.
     *
     * @param visitor Visitor
     * @param arg     argument
     * @param <R>     generic type of the result
     * @param <A>     generic type of the arguments
     * @return result
     */
    public <A, R> R accept(Visitor<A, R> visitor, A arg) {
        switch (kind) {
            case EXEC:
                return visitor.visitExec(this, arg);
            case SOURCE:
                return visitor.visitSource(this, arg);
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Invocation visitor.
     *
     * @param <A> argument
     * @param <R> type of the returned value
     */
    @SuppressWarnings("unused")
    public interface Visitor<A, R> {

        /**
         * Visit an exec invocation.
         *
         * @param invocation invocation
         * @param arg        argument
         * @return visit result
         */
        default R visitExec(Invocation invocation, A arg) {
            return null;
        }

        /**
         * Visit a source invocation.
         *
         * @param invocation invocation
         * @param arg        argument
         * @return visit result
         */
        default R visitSource(Invocation invocation, A arg) {
            return null;
        }
    }

    /**
     * Invocation kind.
     */
    public enum Kind {

        /**
         * Exec.
         */
        EXEC,

        /**
         * Source.
         */
        SOURCE
    }

    /**
     * Invocation builder.
     */
    public static final class Builder extends Expression.Builder<Invocation, Builder> {

        private final Kind kind;

        /**
         * Create a new builder.
         *
         * @param location location
         * @param position position
         * @param kind     kind
         */
        Builder(Path location, Position position, Kind kind) {
            super(location, position, Expression.Kind.INVOCATION);
            this.kind = kind;
        }

        @Override
        public Invocation build() {
            return new Invocation(this);
        }
    }
}
