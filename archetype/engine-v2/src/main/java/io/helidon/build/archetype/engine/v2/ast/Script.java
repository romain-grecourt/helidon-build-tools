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
public class Script extends BlockStatement {

    private final Path path;

    private Script(Builder builder) {
        super(builder);
        this.path = Objects.requireNonNull(builder.path, ()-> "path is null, " + position).toAbsolutePath();
    }

    /**
     * Get the script path.
     *
     * @return path
     */
    public Path path() {
        return path;
    }

    @Override
    public <A, R> R accept(Visitor<A, R> visitor, A arg) {
        return visitor.visit(this, arg);
    }

    /**
     * Create a new builder.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Script builder.
     */
    public static final class Builder extends BlockStatement.Builder<Script, Builder> {

        private Path path;

        private Builder() {
        }

        /**
         * Set the path.
         *
         * @param path path
         * @return this builder
         */
        public Builder path(Path path) {
            this.path = path;
            return this;
        }

        @Override
        public Script build() {
            return new Script(this);
        }
    }
}
