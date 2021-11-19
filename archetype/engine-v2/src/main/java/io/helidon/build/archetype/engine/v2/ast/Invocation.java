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
     * Get the invocation kind.
     *
     * @return kind
     */
    public Kind invocationKind() {
        return kind;
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
     * Invocation builder.
     */
    public static final class Builder extends Expression.Builder<Invocation, Builder> {

        private final Kind kind;

        private Builder(Path location, Position position, Kind kind) {
            super(location, position, Expression.Kind.INVOCATION);
            this.kind = kind;
        }

        @Override
        public Invocation build() {
            return new Invocation(this);
        }
    }
}
