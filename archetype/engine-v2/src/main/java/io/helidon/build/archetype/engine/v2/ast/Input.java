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
 * Base class for all inputs.
 *
 * @param <T> default value type
 */
public abstract class Input<T> extends BlockStatement {

    private final String name;
    private final String prompt;
    private final String label;
    private final T defaultValue;

    /**
     * Create a new input.
     *
     * @param builder builder
     */
    protected Input(Builder<?, T, ?> builder) {
        super(builder);
        this.name = builder.name;
        this.prompt = builder.prompt;
        this.label = builder.label;
        this.defaultValue = builder.defaultValue;
    }

    /**
     * Get the default value.
     *
     * @return default value
     */
    public T defaultValue() {
        return defaultValue;
    }

    /**
     * Get the input name.
     *
     * @return input name
     */
    public String name() {
        return name;
    }

    /**
     * Get the prompt.
     *
     * @return prompt
     */
    public String prompt() {
        return prompt;
    }

    /**
     * Get the label.
     *
     * @return label
     */
    public String label() {
        return label;
    }

    /**
     * Input builder.
     *
     * @param <T> sub-type
     * @param <U> default value tupe
     * @param <V> builder sub-type
     */
    @SuppressWarnings("unchecked")
    public static abstract class Builder<T extends Input<U>, U, V extends Builder<T, U, V>>
            extends BlockStatement.Builder<T, V> {

        private String name;
        private String prompt;
        private String label;
        private U defaultValue;

        /**
         * Set the defaultValue.
         *
         * @param defaultValue default value
         * @return this builder
         */
        public V defaultValue(U defaultValue) {
            this.defaultValue = defaultValue;
            return (V) this;
        }

        /**
         * Set the input name.
         *
         * @param name name
         * @return this builder
         */
        public V name(String name) {
            this.name = name;
            return (V) this;
        }

        /**
         * Set the prompt.
         *
         * @param prompt prompt
         * @return this builder
         */
        public V prompt(String prompt) {
            this.prompt = prompt;
            return (V) this;
        }

        /**
         * Set the label.
         *
         * @param label label
         * @return this builder
         */
        public V label(String label) {
            this.label = label;
            return (V) this;
        }
    }
}
