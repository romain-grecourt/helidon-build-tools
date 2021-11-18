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
 * Script.
 */
public final class Script extends Node {

    private final Executable body;

    private Script(Builder builder) {
        super(builder);
        this.body = Objects.requireNonNull(builder.body, "body is null").build();
    }

    /**
     * Get the body.
     *
     * @return body
     */
    public Executable body() {
        return body;
    }

    /**
     * Script builder.
     */
    public static final class Builder extends Node.Builder<Script, Builder> {

        private final Executable.Builder body;

        /**
         * Create a new builder.
         *
         * @param location location
         * @param position position
         */
        Builder(Path location, Position position) {
            super(location, position, Kind.SCRIPT);
            this.body = new Executable.Builder(location, position, Executable.Kind.SCRIPT);
        }

        @Override
        public Builder statement(Statement.Builder<? extends Statement, ?> builder) {
            body.statement(builder);
            return this;
        }

        @Override
        public Script build() {
            return new Script(this);
        }
    }
}
