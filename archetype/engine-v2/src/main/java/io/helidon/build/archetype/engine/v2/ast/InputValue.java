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

/**
 * Base class for all input values.
 *
 * @param <T> value type
 */
public abstract class InputValue<T> extends Statement {

    private final String path;
    private final T value;

    /**
     * Create a new input value.
     *
     * @param builder builder
     */
    protected InputValue(Builder<?, T, ?> builder) {
        super(builder);
        this.path = builder.path;
        this.value = builder.value;
    }

    /**
     * Get the input path.
     *
     * @return path
     */
    public String path() {
        return path;
    }

    /**
     * Get the value.
     *
     * @return value
     */
    public T value() {
        return value;
    }

    /**
     * Input value builder.
     *
     * @param <T> sub-type
     * @param <U> value type
     * @param <V> builder sub-type
     */
    @SuppressWarnings("unchecked")
    public static abstract class Builder<T extends InputValue<U>, U, V extends Builder<T, U, V>>
            extends Statement.Builder<T, V> {

        private String path;
        protected U value;

        Builder() {
            this.value = null;
        }

        Builder(U value) {
            this.value = value;
        }

        /**
         * Set the path.
         *
         * @param path path
         * @return this builder
         */
        public V path(String path) {
            this.path = path;
            return (V) this;
        }

        /**
         * Set the value.
         *
         * @param value value
         * @return this builder
         */
        public V value(U value) {
            this.value = value;
            return (V) this;
        }

        /**
         * Create the new instance.
         *
         * @return new instance
         */
        public abstract T build();
    }
}
