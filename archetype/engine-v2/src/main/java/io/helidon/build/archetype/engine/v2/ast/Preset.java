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
 * Preset.
 */
public final class Preset extends Expression {

    private final Kind kind;

    private Preset(Builder builder) {
        super(builder);
        this.kind = Objects.requireNonNull(builder.kind, "kind is null");
    }

    /**
     * Get the input path.
     *
     * @return path
     */
    public String path() {
        return Attributes.PATH.get(this, ValueTypes.STRING);
    }

    /**
     * Get the value.
     *
     * @return value
     */
    public Value value() {
        return Attributes.PATH.get(this);
    }

    /**
     * Preset kind.
     */
    public enum Kind {

        /**
         * Text.
         */
        TEXT(ValueTypes.STRING),

        /**
         * Boolean.
         */
        BOOLEAN(ValueTypes.BOOLEAN),

        /**
         * Enum.
         */
        ENUM(ValueTypes.STRING),

        /**
         * List.
         */
        LIST(ValueTypes.STRING_LIST);

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
     * Get the preset kind.
     *
     * @return kind
     */
    public Kind presetKind() {
        return kind;
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

    /**
     * Preset builder.
     */
    public static final class Builder extends Expression.Builder<Preset, Builder> {

        private final Kind kind;

        private Builder(Path location, Position position, Kind kind) {
            super(location, position, Expression.Kind.PRESET);
            this.kind = kind;
        }

        @Override
        public Preset build() {
            return new Preset(this);
        }
    }
}
