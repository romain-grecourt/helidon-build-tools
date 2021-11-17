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
 * Script.
 */
public final class Script extends Node {

    private final Block body;

    private Script(Builder builder) {
        super(builder);
        this.body = Objects.requireNonNull(builder.body, "body is null").build();
    }

    /**
     * Get the script body.
     *
     * @return body
     */
    public Block body() {
        return body;
    }

    @Override
    public <A, R> R accept(Visitor<A, R> visitor, A arg) {
        return null;
    }

    /**
     * Script builder.
     */
    public static final class Builder extends Node.Builder<Script, Builder> {

        private Block.Builder<?, ?> body;

        /**
         * Create a new script builder.
         */
        Builder() {
            super(Kind.SCRIPT, BuilderTypes.SCRIPT);
        }

        /**
         * Set the script body.
         *
         * @param body body
         * @return this builder
         */
        public Builder body(Block.Builder<?, ?> body) {
            this.body = body;
            return this;
        }

        @Override
        public Script build() {
            return new Script(this);
        }
    }
}
