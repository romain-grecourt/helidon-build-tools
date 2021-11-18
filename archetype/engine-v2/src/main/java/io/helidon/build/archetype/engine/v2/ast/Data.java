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

import io.helidon.build.common.GenericType;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Data block.
 */
public final class Data extends Statement {

    private final Kind kind;

    private Data(Builder builder) {
        super(builder);
        this.kind = Objects.requireNonNull(builder.kind, "kind is null");
    }

    /**
     * Get the data kind.
     *
     * @return kind
     */
    public Kind dataKind() {
        return kind;
    }

    /**
     * Data kind.
     */
    public enum Kind {

        /**
         * Replace.
         */
        REPLACE(ValueTypes.STRING_PAIR_LIST);

        private final GenericType<?> type;

        Kind(GenericType<?> type) {
            this.type = type;
        }

        /**
         * Get the value type.
         *
         * @return type
         */
        public GenericType<?> valueType() {
            return type;
        }
    }

    /**
     * Data block builder.
     */
    public static final class Builder extends Statement.Builder<Data, Builder> {

        private final Kind kind;

        /**
         * Create a new builder.
         *
         * @param location location
         * @param position position
         * @param kind     kind
         */
        Builder(Path location, Position position, Kind kind) {
            super(location, position, Statement.Kind.DATA);
            this.kind = kind;
        }

        /**
         * Get the data kind.
         *
         * @return kind
         */
        public Kind dataKind() {
            return kind;
        }

        @Override
        public Data build() {
            return new Data(this);
        }
    }
}
