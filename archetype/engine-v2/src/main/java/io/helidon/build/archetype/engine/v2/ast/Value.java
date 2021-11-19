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

import java.util.List;

/**
 * Value.
 */
public interface Value {

    /**
     * Get the value type.
     *
     * @return type
     */
    GenericType<?> type();

    /**
     * Get this value as the given type.
     *
     * @param type type
     * @param <T>  actual type
     * @return instance as the given type
     * @throws ValueTypeException if this instance type does not match the given type
     */
    <T> T as(GenericType<T> type);

    /**
     * Get this value as a {@code string}.
     *
     * @return string
     */
    default String asString() {
        return as(ValueTypes.STRING);
    }

    /**
     * Get this value as a boolean.
     *
     * @return boolean
     */
    default Boolean asBoolean() {
        return as(ValueTypes.BOOLEAN);
    }

    /**
     * Get this value as an int.
     *
     * @return int
     */
    default Integer asInt() {
        return as(ValueTypes.INT);
    }

    /**
     * Get this value as a list.
     *
     * @return list
     */
    default List<String> asList() {
        return as(ValueTypes.STRING_LIST);
    }

    /**
     * Get this value as a read-only value.
     *
     * @return read-only value
     */
    default Value asReadOnly() {
        return this;
    }

    /**
     * Exception raised for unexpected type usages.
     */
    final class ValueTypeException extends IllegalStateException {

        /**
         * Create a new value type exception
         *
         * @param actual   the actual type
         * @param expected the unexpected type
         */
        ValueTypeException(GenericType<?> actual, GenericType<?> expected) {
            super(String.format("Cannot get a value of { %s } as { %s }", actual, expected));
        }
    }
}
