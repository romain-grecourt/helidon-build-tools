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
 * Base class for file and template.
 */
public abstract class AbstractFile extends BlockStatement {

    private final String source;
    private final String target;

    /**
     * Create a new file.
     *
     * @param builder builder
     */
    protected AbstractFile(Builder<?, ?> builder) {
        super(builder);
        this.source = builder.source;
        this.target = builder.target;
    }

    /**
     * Get the source.
     *
     * @return source
     */
    public String source() {
        return source;
    }

    /**
     * Get the target.
     *
     * @return target
     */
    public String target() {
        return target;
    }

    /**
     * Base builder.
     *
     * @param <T> sub-type
     * @param <U> builder sub-type
     */
    @SuppressWarnings("unchecked")
    public static abstract class Builder<T extends AbstractFile, U extends Builder<T, U>>
            extends BlockStatement.Builder<T, U> {

        protected String source;
        protected String target;

        /**
         * Set the source.
         *
         * @param source source
         * @return this builder
         */
        public U source(String source) {
            this.source = source;
            return (U) this;
        }

        /**
         * Set the target.
         *
         * @param target target
         * @return this builder
         */
        public U target(String target) {
            this.target = target;
            return (U) this;
        }
    }
}
