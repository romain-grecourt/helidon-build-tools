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

/**
 * No-op statement.
 */
public final class Noop extends Statement {

    private Noop(Builder builder) {
        super(builder);
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

    @Override
    public <A> VisitResult accept(Visitor<A> visitor, A arg) {
        return visitor.visitNoop(this, arg);
    }

    /**
     * No-op builder.
     */
    public static final class Builder extends Statement.Builder<Noop, Builder> {

        private Builder(Path location, Position position) {
            super(location, position, Kind.NOOP);
        }

        @Override
        protected Noop build0() {
            return new Noop(this);
        }
    }
}
