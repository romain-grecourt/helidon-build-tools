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
 * Model block.
 */
public final class Model extends Block {

    private final Kind kind;

    private Model(Builder builder) {
        super(builder);
        this.kind = Objects.requireNonNull(builder.kind, "kind is null");
    }

    /**
     * Get the model block kind.
     *
     * @return kind
     */
    public Kind modelKind() {
        return kind;
    }

    /**
     * Model blocks kind.
     */
    public enum Kind {

        /**
         * Value.
         */
        VALUE,

        /**
         * Map.
         */
        MAP,

        /**
         * List.
         */
        LIST,
    }

    /**
     * Model block builder.
     */
    public static final class Builder extends Block.Builder<Model, Builder> {

        private final Kind kind;

        /**
         * Create a new model block builder.
         *
         * @param location location
         * @param position position
         * @param kind     kind
         */
        Builder(Path location, Position position, Kind kind) {
            super(location, position, Block.Kind.UNKNOWN);
            this.kind = kind;
        }

        @Override
        public Model build() {
            return new Model(this);
        }
    }
}
