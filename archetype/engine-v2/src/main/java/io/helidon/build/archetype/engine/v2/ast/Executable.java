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
 * Executable block.
 */
public final class Executable extends Block {

    private final Kind kind;

    private Executable(Builder builder) {
        super(builder);
        this.kind = Objects.requireNonNull(builder.kind, "kind is null");
    }

    /**
     * Get the executable block kind.
     *
     * @return kind
     */
    public Kind executableKind() {
        return kind;
    }

    /**
     * Executable blocks kind.
     */
    public enum Kind {

        /**
         * Script.
         */
        SCRIPT,

        /**
         * Step.
         */
        STEP,

        /**
         * Input.
         */
        INPUT,

        /**
         * Option.
         */
        OPTION,

        /**
         * Output.
         */
        OUTPUT
    }

    /**
     * Executable builder.
     */
    public static class Builder extends Block.Builder<Executable, Builder> {

        private Kind kind;

        /**
         * Create a new executable builder.
         */
        Builder() {
            super(Block.Kind.EXECUTABLE, BuilderTypes.EXECUTABLE);
        }

        /**
         * Set the executable block kind.
         *
         * @param kind kind
         * @return this builder
         */
        public Builder executableKind(Kind kind) {
            this.kind = kind;
            return this;
        }

        @Override
        public Executable build() {
            return new Executable(this);
        }
    }
}
