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
 * Model value.
 */
public abstract class ModelValue extends BlockStatement {

    private final String key;
    private final int order;

    /**
     * Create a new model value.
     *
     * @param builder builder
     */
    protected ModelValue(Builder<?, ?> builder) {
        super(builder);
        this.key = builder.key;
        this.order = builder.order;
    }

    /**
     * Get the key.
     *
     * @return key
     */
    public String key() {
        return key;
    }

    /**
     * Get the order.
     *
     * @return order
     */
    public int order() {
        return order;
    }

    /**
     * Model value builder.
     *
     * @param <T> builder sub-type
     */
    @SuppressWarnings("unchecked")
    public static abstract class Builder<T extends ModelValue, U extends Builder<T, U>> extends BlockStatement.Builder<T, U> {

        private String key;
        private int order;

        /**
         * Set the key.
         *
         * @param key key
         * @return this builder
         */
        public U key(String key) {
            this.key = key;
            return (U) this;
        }

        /**
         * Set the order.
         *
         * @param order order
         * @return this builder
         */
        public U order(int order) {
            this.order = order;
            return (U) this;
        }
    }
}
