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

import java.util.Objects;

/**
 * Constant.
 */
public class Constant<T> implements Value {

    private final T value;
    private final GenericType<T> type;

    private Constant(T value, GenericType<T> type) {
        this.value = value;
        this.type = type;
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

    /**
     * Create a new constant.
     *
     * @param value value
     * @return constant
     */
    public static Constant create(String value) {
        return new Constant(value, Types.STRING);
    }

    /**
     * Create a new constant.
     *
     * @param value value
     * @return constant
     */
    public static <T> Constant create(T value, GenericType<T> type) {
        return new Constant(value, type);
    }
}
