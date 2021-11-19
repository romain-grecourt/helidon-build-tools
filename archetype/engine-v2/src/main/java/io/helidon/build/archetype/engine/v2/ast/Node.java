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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

/**
 * AST node.
 */
public abstract class Node {

    private final Kind kind;
    private final Path location;
    private final Position position;
    private final Map<Attributes, Value> attributes;

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
    public Map<Attributes, Value> attributes() {
        return attributes;
    }

    /**
     * Visit this node.
     *
     * @param visitor Visitor
     * @param arg     argument
     * @param <A>     generic type of the arguments
     * @return result
     */
    public abstract <A> VisitResult accept(Visitor<A> visitor, A arg);

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
        SCRIPT
    }

    /**
     * Node builder.
     *
     * @param <T> sub-type
     * @param <U> builder sub-type
     */
    @SuppressWarnings("unchecked")
    public static abstract class Builder<T, U extends Builder<T, U>> {

        private static final Path NULL_LOCATION = Path.of("script.xml");
        private static final Position NULL_POSITION = Position.of(0, 0);

        private static final Map<String, Attributes> ATTRIBUTES_MAP =
                Arrays.stream(Attributes.values()).collect(toMap(a -> a.name().toLowerCase(), Function.identity()));

        private final Kind kind;
        private final Map<Attributes, Value> attributes = new HashMap<>();
        private final Path location;
        private final Position position;

        /**
         * Create a new node builder.
         *
         * @param location location
         * @param position position
         * @param kind     kind
         */
        protected Builder(Path location, Position position, Kind kind) {
            this.kind = requireNonNull(kind, "kind is null");
            this.location = location == null ? NULL_LOCATION : location;
            this.position = position == null ? NULL_POSITION : position;
        }

        /**
         * Add a statement.
         *
         * @param builder statement builder
         * @return this builder
         */
        public U statement(Statement.Builder<? extends Statement, ?> builder) {
            throw new UnsupportedOperationException("Unable to add statement to " + getClass().getName());
        }

        private <V> Literal.Builder<V> newLiteral() {
            return new Literal.Builder<>(location, position);
        }

        /**
         * Add an attribute.
         *
         * @param key   key
         * @param value value
         * @return this builder
         */
        public U attribute(Attributes key, Value value) {
            Objects.requireNonNull(key, "key is null");
            if (value != null) {
                attributes.put(key, value);
            }
            return (U) this;
        }

        /**
         * Add a value to a list attribute.
         *
         * @param key   key
         * @param value value
         * @return this builder
         */
        @SuppressWarnings("UnusedReturnValue")
        public U attributeListAdd(Attributes key, String value) {
            attributes.compute(key, (k, v) -> Literal.listAdd(this::newLiteral, v, value));
            return (U) this;
        }

        /**
         * Parse attributes.
         *
         * @param rawAttrs raw attributes
         * @return this builder
         */
        @SuppressWarnings("UnusedReturnValue")
        public U parseAttributes(Map<String, String> rawAttrs) {
            if (rawAttrs != null) {
                rawAttrs.forEach((k, v) -> {
                    Attributes attr = ATTRIBUTES_MAP.get(k);
                    if (attr != null) {
                        GenericType<?> valueType = attr.valueType();
                        if (valueType != null) {
                            parseAttribute(attr, valueType, v);
                        }
                    }
                });
            }
            return (U) this;
        }

        /**
         * Add an attribute.
         *
         * @param key      key
         * @param rawValue raw value
         * @return this builder
         */
        public <V> U parseAttribute(Attributes key, GenericType<V> type, String rawValue, V defaultValue) {
            Objects.requireNonNull(key, "key is null");
            if (rawValue != null) {
                attributes.put(key, this.<V>newLiteral().parse(type, rawValue, defaultValue).build());
            }
            return (U) this;
        }

        /**
         * Parse an attribute.
         *
         * @param key      key
         * @param rawValue raw value
         * @return this builder
         */
        @SuppressWarnings("UnusedReturnValue")
        public U parseAttribute(Attributes key, String rawValue) {
            return parseAttribute(key, key.valueType(), rawValue, null);
        }

        /**
         * Parse an attribute.
         *
         * @param key      key
         * @param type     type
         * @param rawValue raw value
         * @return this builder
         */
        @SuppressWarnings("UnusedReturnValue")
        public <V> U parseAttribute(Attributes key, GenericType<V> type, String rawValue) {
            return parseAttribute(key, type, rawValue, null);
        }

        /**
         * Parse an attribute.
         *
         * @param key      key
         * @param type     type
         * @param rawAttrs raw attributes
         * @return this builder
         */
        @SuppressWarnings("UnusedReturnValue")
        public <V> U parseAttribute(Attributes key, GenericType<V> type, Map<String, String> rawAttrs) {
            return parseAttribute(key, type, rawAttrs.get(key.name().toLowerCase()));
        }

        /**
         * Create the new instance.
         *
         * @return new instance
         */
        public abstract T build();
    }
}
