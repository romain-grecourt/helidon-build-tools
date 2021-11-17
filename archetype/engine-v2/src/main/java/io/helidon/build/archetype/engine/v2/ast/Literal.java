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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static java.util.Collections.unmodifiableList;

/**
 * Literal value.
 */
public final class Literal extends Node implements Value {

    private final Object value;
    private final GenericType<?> type;
    private final boolean readonly;

    private Literal(Builder<?> builder) {
        super(builder);
        this.value = Objects.requireNonNull(builder.value, "value is null");
        this.type = Objects.requireNonNull(builder.type, "value is null");
        this.readonly = builder.readonly;
    }

    @Override
    public GenericType<?> type() {
        return type;
    }

    @Override
    public <A, R> R accept(Visitor<A, R> visitor, A arg) {
        return null;
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
            return new Builder<List<?>>()
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
     * @param type    value type
     * @param value   value
     * @param <T>     value type
     * @return literal
     * @throws IllegalArgumentException if the provided value is not a literal
     */
    public static <T> Literal listAdd(Supplier<Builder<List<T>>> builder, Value literal, GenericType<List<T>> type, T value) {
        if (literal == null) {
            List<T> list = new ArrayList<>();
            list.add(value);
            return builder.get()
                          .type(type)
                          .value(list)
                          .readonly(false)
                          .build();
        }
        if (!(literal instanceof Literal)) {
            throw new IllegalArgumentException("Value is not a literal: " + literal);
        }
        if (type.rawType().equals(List.class)) {
            literal.as(type).add(value);
            return (Literal) literal;
        }
        throw new IllegalArgumentException("Type is not a list: " + type);
    }

    /**
     * Parse a literal.
     *
     * @param builder  builder supplier
     * @param type     value type
     * @param rawValue raw value
     * @param <T>      value type parameter
     * @return literal
     */
    public static <T> Literal parse(Supplier<Builder<T>> builder, GenericType<T> type, String rawValue) {
        return parse(builder, type, rawValue, null);
    }

    /**
     * Parse a literal.
     *
     * @param builder      builder supplier
     * @param type         value type
     * @param rawValue     raw value
     * @param defaultValue default value
     * @param <T>          value type parameter
     * @return literal
     */
    @SuppressWarnings("unchecked")
    public static <T> Literal parse(Supplier<Builder<T>> builder, GenericType<T> type, String rawValue, T defaultValue) {
        if (rawValue == null && defaultValue == null) {
            return null;
        }
        T value;
        if (rawValue != null) {
            if (type.equals(Value.Types.STRING)) {
                value = (T) rawValue;
            } else if (type.equals(Value.Types.BOOLEAN)) {
                value = (T) Boolean.valueOf(rawValue);

            } else if (type.equals(Value.Types.INT)) {
                value = (T) Boolean.valueOf(rawValue);
            } else if (type.equals(Value.Types.STRING_LIST)) {
                value = (T) Arrays.asList(rawValue.split(","));
            } else {
                throw new IllegalArgumentException("Unsupported value type: " + type);
            }
        } else {
            value = defaultValue;
        }
        return builder.get().type(type).value(value).build();
    }

    /**
     * Literal builder.
     *
     * @param <T> value type
     */
    public static final class Builder<T> extends Node.Builder<Literal, Builder<?>> {

        private T value;
        private GenericType<T> type;
        private boolean readonly = true;

        Builder() {
            super(Kind.LITERAL, BuilderTypes.LITERAL);
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
