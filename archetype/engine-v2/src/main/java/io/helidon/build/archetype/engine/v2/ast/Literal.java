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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static java.util.Collections.unmodifiableList;

/**
 * Literal value.
 */
public final class Literal extends Expression implements Value {

    private final Object value;
    private final GenericType<?> type;
    private final boolean readonly;

    private Literal(Builder<?> builder) {
        super(builder);
        this.value = Objects.requireNonNull(builder.value, "value is null");
        this.type = Objects.requireNonNull(builder.type, "type is null");
        this.readonly = builder.readonly;
    }

    @Override
    public GenericType<?> type() {
        return type;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <U> U as(GenericType<U> type) {
        Objects.requireNonNull(type, "type is null");
        if (!this.type.equals(type)) {
            throw new ValueTypeException(this.type, type);
        }
        return (U) value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Value asReadOnly() {
        if (readonly) {
            return this;
        }
        if (type.rawType().equals(List.class)) {
            return new Builder<List<?>>(location(), position())
                    .type((GenericType<List<?>>) type)
                    .value(unmodifiableList((List<?>) value))
                    .build();
        }
        return this;
    }

    /**
     * Add a value to a list literal.
     *
     * @param builder builder supplier
     * @param literal literal
     * @param value   value
     * @return literal
     * @throws IllegalArgumentException if the provided value is not a literal
     */
    public static Literal listAdd(Supplier<Builder<List<String>>> builder, Value literal, String value) {
        if (literal == null) {
            List<String> list = new ArrayList<>();
            list.add(value);
            return builder.get()
                          .type(ValueTypes.STRING_LIST)
                          .value(list)
                          .readonly(false)
                          .build();
        }
        if (!(literal instanceof Literal)) {
            throw new IllegalArgumentException("Value is not a literal: " + literal);
        }
        literal.as(ValueTypes.STRING_LIST).add(value);
        return (Literal) literal;
    }

    /**
     * Literal builder.
     *
     * @param <T> value type
     */
    public static final class Builder<T> extends Expression.Builder<Literal, Builder<?>> {

        private T value;
        private GenericType<T> type;
        private boolean readonly = true;

        /**
         * Create a new builder.
         *
         * @param location location
         * @param position position
         */
        Builder(Path location, Position position) {
            super(location, position, Kind.LITERAL);
        }

        /**
         * Set the value type.
         *
         * @param type type
         * @return this builder
         */
        public Builder<T> type(GenericType<T> type) {
            this.type = type;
            return this;
        }

        /**
         * Set the value type.
         *
         * @param type type
         * @return this builder
         */
        public Builder<T> type(Class<T> type) {
            this.type = GenericType.create(type);
            return this;
        }

        /**
         * Set the value
         *
         * @param value value
         * @return this builder
         */
        public Builder<T> value(T value) {
            this.value = value;
            return this;
        }

        /**
         * Parse the value.
         *
         * @param type         type
         * @param rawValue     raw value
         * @param defaultValue default value
         * @return this builder
         */
        @SuppressWarnings("unchecked")
        public Builder<T> parse(GenericType<T> type, String rawValue, T defaultValue) {
            if (rawValue == null && defaultValue == null) {
                return null;
            }
            Objects.requireNonNull(type, "type is null");
            if (rawValue != null) {
                if (type.equals(ValueTypes.STRING)) {
                    value = (T) rawValue;
                } else if (type.equals(ValueTypes.BOOLEAN)) {
                    value = (T) Boolean.valueOf(rawValue);
                } else if (type.equals(ValueTypes.INT)) {
                    value = (T) Boolean.valueOf(rawValue);
                } else if (type.equals(ValueTypes.STRING_LIST)) {
                    value = (T) Arrays.asList(rawValue.split(","));
                } else {
                    throw new IllegalArgumentException("Unsupported value type: " + type);
                }
            } else {
                value = defaultValue;
            }
            this.type = type;
            return this;
        }

        /**
         * Set the readonly flag.
         *
         * @param readonly readonly
         * @return this builder
         */
        public Builder<T> readonly(boolean readonly) {
            this.readonly = readonly;
            return this;
        }

        @Override
        public Literal build() {
            return new Literal(this);
        }
    }
}
