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
 * Output block.
 */
public final class Output extends Block {

    private final Kind kind;

    private Output(Builder builder) {
        super(builder);
        this.kind = Objects.requireNonNull(builder.kind, "kind is null");
    }

    @Override
    public <A, R> R accept(Visitor<A, R> visitor, A arg) {
        return visitor.visit(this, arg);
    }

    /**
     * Get the output block kind.
     *
     * @return kind
     */
    public Kind outputKind() {
        return kind;
    }

    /**
     * Output blocks kind.
     */
    public enum Kind {

        /**
         * Transformation.
         */
        TRANSFORMATION,

        /**
         * File.
         */
        FILE,

        /**
         * Files.
         */
        FILES,

        /**
         * Template.
         */
        TEMPLATE,

        /**
         * Templates.
         */
        TEMPLATES,

        /**
         * Model.
         */
        MODEL
    }

    /**
     * Output block builder.
     */
    public static final class Builder extends Block.Builder<Output, Builder> {

        private Kind kind;

        /**
         * Create a new output block builder.
         */
        Builder() {
            super(Block.Kind.UNKNOWN, Node.BuilderTypes.OUTPUT);
        }

        /**
         * Set the output block kind.
         *
         * @param kind kind
         * @return this builder
         */
        public Builder outputKind(Kind kind) {
            this.kind = kind;
            return this;
        }

        @Override
        public Output build() {
            return new Output(this);
        }
    }
}
