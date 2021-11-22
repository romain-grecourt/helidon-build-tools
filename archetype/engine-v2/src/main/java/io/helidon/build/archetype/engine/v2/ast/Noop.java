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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toUnmodifiableList;

/**
 * No-op statement.
 */
public final class Noop extends Statement {

    private Noop(Builder builder) {
        super(builder);
    }

    /**
     * Noop kind.
     */
    public enum Kind {

        /**
         * Replace.
         */
        REPLACE,

        /**
         * Directory.
         */
        DIRECTORY,

        /**
         * Include.
         */
        INCLUDE,

        /**
         * Exclude.
         */
        EXCLUDE,

        /**
         * Help.
         */
        HELP,

        /**
         * Value.
         */
        VALUE;

        /**
         * Noop kind names.
         */
        public static List<String> NAMES = Arrays.stream(Kind.values())
                                                 .map(Kind::name)
                                                 .map(String::toLowerCase)
                                                 .collect(toUnmodifiableList());
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

    @Override
    public <A> VisitResult accept(Visitor<A> visitor, A arg) {
        return visitor.visitNoop(this, arg);
    }

    /**
     * No-op builder.
     */
    public static final class Builder extends Statement.Builder<Noop, Builder> {

        String value;
        final Kind kind;

        private Builder(Path location, Position position, Kind kind) {
            super(location, position, Statement.Kind.NOOP);
            this.kind = kind;
        }

        /**
         * Filter the nested statements as a stream of noop of the given kind.
         *
         * @param kind kind
         * @return stream of noop builder
         */
        static Stream<Builder> filter(List<Statement.Builder<? extends Statement, ?>> statements,
                                                Noop.Kind kind) {
            return statements.stream()
                             .filter(Noop.Builder.class::isInstance)
                             .map(Noop.Builder.class::cast)
                             .filter(noop -> noop.kind == kind);
        }

        /**
         * Set the value.
         *
         * @param value value
         * @return this builder
         */
        public Builder value(String value) {
            this.value = value;
            return this;
        }

        @Override
        protected Noop build0() {
            return new Noop(this);
        }
    }
}
