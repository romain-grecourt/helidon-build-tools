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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

/**
 * Base class for AST nodes.
 */
public abstract class Node {

    private final Kind kind;
    private final Path location;
    private final Position position;
    private final Map<Object, Value> attributes;

    protected Node(Builder<?, ?> builder) {
        this.location = requireNonNull(builder.location, "location is null");
        this.position = requireNonNull(builder.position, "position is null");
        this.kind = builder.kind;
        builder.attributes.replaceAll((k, v) -> v.asReadOnly());
        this.attributes = Collections.unmodifiableMap(builder.attributes);
    }

    /**
     * Get the node kind.
     *
     * @return kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Get the source position.
     *
     * @return position
     */
    public Position position() {
        return position;
    }

    /**
     * Get the source location.
     *
     * @return location
     */
    public Path location() {
        return location;
    }

    /**
     * Get the attributes.
     *
     * @return attributes
     */
    public Map<Object, Value> attributes() {
        return attributes;
    }

    /**
     * Visit a node.
     *
     * @param visitor Visitor
     * @param arg     argument
     * @param <R>     generic type of the result
     * @param <A>     generic type of the arguments
     * @return result
     */
    public abstract <A, R> R accept(Visitor<A, R> visitor, A arg);

    /**
     * Node kind.
     */
    public enum Kind {

        /**
         * Statement.
         */
        STATEMENT,

        /**
         * Script.
         */
        SCRIPT,

        /**
         * Literal.
         */
        LITERAL,
    }

    /**
     * All builder types.
     */
    @SuppressWarnings("rawtypes")
    public static final class BuilderTypes {

        /**
         * Builder type for default blocks.
         */
        public static final GenericType<Block.Builder> BLOCK = GenericType.create(Block.Builder.class);

        /**
         * Builder type for executable blocks.
         */
        public static final GenericType<Executable.Builder> EXECUTABLE = GenericType.create(Executable.Builder.class);

        /**
         * Builder type for output blocks.
         */
        public static final GenericType<Output.Builder> OUTPUT = GenericType.create(Output.Builder.class);

        /**
         * Builder type for model blocks.
         */
        public static final GenericType<Model.Builder> MODEL = GenericType.create(Model.Builder.class);

        /**
         * Builder type for if statements.
         */
        public static final GenericType<IfStatement.Builder> IF = GenericType.create(IfStatement.Builder.class);

        /**
         * Builder type for expressions.
         */
        public static final GenericType<Expression.Builder> EXPRESSION = GenericType.create(Expression.Builder.class);

        /**
         * Builder type for scripts.
         */
        public static final GenericType<Script.Builder> SCRIPT = GenericType.create(Script.Builder.class);

        /**
         * Builder type for literals.
         */
        public static final GenericType<Literal.Builder<?>> LITERAL = new GenericType<>(){};

        private BuilderTypes() {
        }
    }

    /**
     * Create a new builder.
     *
     * @param type builder type
     * @param <T>  block sub-type
     * @param <U>  block builder sub-type
     * @return builder
     */
    @SuppressWarnings("unchecked")
    public static <T extends Node, U extends Node.Builder<T, U>> U builder(GenericType<U> type) {
        Objects.requireNonNull(type, "type is null");
        if (type.equals(BuilderTypes.BLOCK)) {
            return (U) new Block.Builder<>();
        }
        if (type.equals(BuilderTypes.EXECUTABLE)) {
            return (U) new Executable.Builder();
        }
        if (type.equals(BuilderTypes.OUTPUT)) {
            return (U) new Output.Builder();
        }
        if (type.equals(BuilderTypes.MODEL)) {
            return (U) new Model.Builder();
        }
        if (type.equals(BuilderTypes.IF)) {
            return (U) new IfStatement.Builder();
        }
        if (type.equals(BuilderTypes.EXPRESSION)) {
            return (U) new Expression.Builder();
        }
        if (type.equals(BuilderTypes.SCRIPT)) {
            return (U) new Script.Builder();
        }
        if (type.rawType().equals(Node.Builder.class)) {
            return (U) new Literal.Builder<>();
        }
        throw new IllegalArgumentException("Unsupported builder type: " + type);
    }

    /**
     * Node builder.
     *
     * @param <T> sub-type
     * @param <U> builder sub-type
     */
    @SuppressWarnings("unchecked")
    public static abstract class Builder<T, U extends Builder<T, U>> {

        private static final Path DEFAULT_LOCATION = Path.of("script.xml");

        private final GenericType<U> type;
        private final Kind kind;
        private final Map<Object, Value> attributes = new HashMap<>();
        private Path location = DEFAULT_LOCATION;
        private Position position;

        /**
         * Create a new node builder.
         *
         * @param kind kind
         */
        protected Builder(Kind kind, GenericType<U> type) {
            this.type = requireNonNull(type, "type is null");
            this.kind = requireNonNull(kind, "kind is null");
        }

        /**
         * Get the builder type.
         *
         * @return type
         */
        public GenericType<U> type() {
            return type;
        }

        /**
         * Set the position.
         *
         * @param position position
         * @return this builder
         */
        public U position(Position position) {
            this.position = position;
            return (U) this;
        }

        /**
         * Set the location.
         *
         * @param location location
         * @return this builder
         */
        public U location(Path location) {
            this.location = location;
            return (U) this;
        }

        /**
         * Add an attribute.
         *
         * @param key   key
         * @param value value
         * @return this builder
         */
        public U attribute(Object key, Value value) {
            Objects.requireNonNull(key, "key is null");
            if (value != null) {
                attributes.put(key, value);
            }
            return (U) this;
        }

        /**
         * Add an attribute.
         *
         * @param key      key
         * @param function mapping function
         * @return this builder
         */
        public U attribute(Object key, BiFunction<Object, Value, Value> function) {
            attributes.compute(key, function);
            return (U) this;
        }

        /**
         * Create the new instance.
         *
         * @return new instance
         */
        public abstract T build();
    }
}
