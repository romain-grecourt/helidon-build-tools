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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Base class for files and templates.
 */
public abstract class AbstractFiles extends BlockStatement {

    private final String directory;
    private final List<String> transformations;
    private final List<String> excludes;
    private final List<String> includes;

    /**
     * Create a new file.
     *
     * @param builder builder
     */
    protected AbstractFiles(Builder<?, ?> builder) {
        super(builder);
        this.directory = builder.directory;
        this.transformations = Collections.unmodifiableList(builder.transformations);
        this.excludes = Collections.unmodifiableList(builder.excludes);
        this.includes = Collections.unmodifiableList(builder.includes);
    }

    /**
     * Get the transformations.
     *
     * @return transformations
     */
    public List<String> transformations() {
        return transformations;
    }

    /**
     * Get the directory.
     *
     * @return directory
     */
    public String directory() {
        return directory;
    }

    /**
     * Get the excludes.
     *
     * @return excludes
     */
    public List<String> excludes() {
        return excludes;
    }

    /**
     * Get the includes.
     *
     * @return includes
     */
    public List<String> includes() {
        return includes;
    }

    /**
     * Base builder.
     *
     * @param <T> sub-type
     * @param <U> builder sub-type
     */
    @SuppressWarnings("unchecked")
    public static abstract class Builder<T extends AbstractFiles, U extends Builder<T, U>>
            extends BlockStatement.Builder<T, U> {

        protected final List<String> transformations = new LinkedList<>();
        protected String directory;
        protected List<String> excludes = new LinkedList<>();
        protected List<String> includes = new LinkedList<>();

        /**
         * Add a transformation.
         *
         * @param transformation transformation
         * @return this builder
         */
        public U transformation(String transformation) {
            transformations.add(transformation);
            return (U) this;
        }

        /**
         * Set the directory.
         *
         * @param directory directory.
         * @return this builder
         */
        public U directory(String directory) {
            this.directory = directory;
            return (U) this;
        }

        /**
         * Add an exclude.
         *
         * @param exclude exclude
         * @return this builder
         */
        public U exclude(String exclude) {
            excludes.add(exclude);
            return (U) this;
        }

        /**
         * Add an include.
         *
         * @param include include
         * @return this builder
         */
        public U include(String include) {
            includes.add(include);
            return (U) this;
        }
    }
}
