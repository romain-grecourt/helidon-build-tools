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

    private final Block body;

    private Script(Builder builder) {
        super(builder);
        this.body = Objects.requireNonNull(builder.body, "body is null").build();
    }

    /**
     * Get the body.
     * @return body
     */
    public Block body() {
        return body;
    }

    @Override
    public <A> VisitResult accept(Visitor<A> visitor, A arg) {
        VisitResult result = visitor.visitScript(this, arg);
        if (result != VisitResult.CONTINUE) {
            return result;
        }
        return body.accept(visitor, arg);
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
     * Script builder.
     */
    public static final class Builder extends Node.Builder<Script, Builder> {

        private final Block.Builder body;

        private Builder(Path location, Position position) {
            super(location, position, Kind.SCRIPT);
            this.body = Block.builder(location, position, Block.Kind.SCRIPT);
        }

        @Override
        public Builder statement(Statement.Builder<? extends Statement, ?> builder) {
            body.statement(builder);
            return this;
        }

        @Override
        protected Script build0() {
            return new Script(this);
        }
    }
}
