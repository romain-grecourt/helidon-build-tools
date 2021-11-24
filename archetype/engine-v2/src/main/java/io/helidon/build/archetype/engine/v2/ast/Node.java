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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;

/**
 * AST node.
 */
public abstract class Node {

    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    private final Kind kind;
    private final int id;
    final Path location;
    final Position position;

    /**
     * Create a new node.
     *
     * @param builder builder
     */
    protected Node(Builder<?, ?> builder) {
        this(builder.location, builder.position, builder.kind);
    }

    /**
     * Create a new node.
     *
     * @param location location
     * @param position position
     * @param kind     kind
     */
    protected Node(Path location, Position position, Kind kind) {
        this.location = requireNonNull(location, "location is null");
        this.position = requireNonNull(position, "position is null");
        this.kind = kind;
        this.id = NEXT_ID.updateAndGet(i -> i == Integer.MAX_VALUE ? 1 : i + 1);
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
     * Get the node id.
     *
     * @return id
     */
    public int nodeId() {
        return id;
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
     * Visit result.
     */
    public enum VisitResult {

        /**
         * Continue.
         */
        CONTINUE,

        /**
         * Terminate.
         */
        TERMINATE,

        /**
         * Continue without visiting the children.
         */
        SKIP_SUBTREE,

        /**
         * Continue without visiting the siblings.
         */
        SKIP_SIBLINGS
    }

    /**
     * Visitor.
     *
     * @param <T> argument
     */
    public interface Visitor<T> {

        /**
         * Visit a script.
         *
         * @param script script
         * @param arg    argument
         * @return visit result
         */
        default VisitResult visitScript(Script script, T arg) {
            return visitNode(script, arg);
        }

        /**
         * Visit a condition.
         *
         * @param condition condition
         * @param arg       argument
         * @return visit result
         */
        default VisitResult visitCondition(Condition condition, T arg) {
            return visitNode(condition, arg);
        }

        /**
         * Visit an invocation.
         *
         * @param invocation invocation
         * @param arg        argument
         * @return visit result
         */
        default VisitResult visitInvocation(Invocation invocation, T arg) {
            return visitNode(invocation, arg);
        }

        /**
         * Visit a preset.
         *
         * @param preset preset
         * @param arg    argument
         * @return visit result
         */
        default VisitResult visitPreset(Preset preset, T arg) {
            return visitNode(preset, arg);
        }

        /**
         * Visit a block.
         *
         * @param block block
         * @param arg   argument
         * @return visit result
         */
        default VisitResult preVisitBlock(Block block, T arg) {
            return VisitResult.CONTINUE;
        }

        /**
         * Visit a block after traversing the nested statements.
         *
         * @param block block
         * @param arg   argument
         * @return visit result
         */
        default VisitResult postVisitBlock(Block block, T arg) {
            return VisitResult.CONTINUE;
        }

        /**
         * Visit a noop.
         *
         * @param noop noop
         * @param arg  argument
         * @return visit result
         */
        default VisitResult visitNoop(Noop noop, T arg) {
            return visitNode(noop, arg);
        }

        /**
         * Visit a node.
         *
         * @param node node
         * @param arg  arg
         * @return visit result
         */
        default VisitResult visitNode(Node node, T arg) {
            return VisitResult.CONTINUE;
        }
    }

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
        private final Kind kind;
        private T instance;
        final Map<String, String> attributes = new HashMap<>();
        final Path location;
        final Position position;

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

        /**
         * Set the value.
         *
         * @param value value
         * @return this builder
         */
        public U value(String value) {
            throw new UnsupportedOperationException("Unable to add value to " + getClass().getName());
        }

        /**
         * Add attributes.
         *
         * @param attributes attributes
         * @return this builder
         */
        @SuppressWarnings("UnusedReturnValue")
        public U attributes(Map<String, String> attributes) {
            this.attributes.putAll(attributes);
            return (U) this;
        }

        /**
         * Create a new literal builder.
         *
         * @param type type
         * @return this builder
         */
        <V> Literal.Builder<V> newLiteral(GenericType<V> type) {
            return new Literal.Builder<V>(location, position).type(type);
        }

        /**
         * Parse a literal.
         *
         * @param type         type
         * @param rawValue     raw value
         * @param defaultValue default value
         * @return this builder
         */
        <V> Literal parseLiteral(GenericType<V> type, String rawValue, V defaultValue) {
            return newLiteral(type).parse(type, rawValue, defaultValue).build();
        }


        /**
         * Parse an attribute.
         *
         * @param type         type
         * @param key          attribute key
         * @param defaultValue default value
         * @return this builder
         */
        <V> Literal parseAttribute(GenericType<V> type, String key, V defaultValue) {
            return parseLiteral(type, attributes.get(key), defaultValue);
        }

        /**
         * Parse an attribute.
         *
         * @param type type
         * @param key  attribute key
         * @return this builder
         */
        <V> Literal parseAttribute(GenericType<V> type, String key) {
            Literal value = parseLiteral(type, attributes.get(key), null);
            if (value == null) {
                throw new IllegalStateException(String.format(
                        "Unable to get attribute '%s', file=%s, position=%s",
                        this, location, position));
            }
            return value;
        }

        /**
         * Parse the value.
         *
         * @param type type
         * @param key  attribute key
         * @return this builder
         */
        <V> Literal parseValue(GenericType<V> type, String rawValue) {
            Literal value = parseLiteral(type, rawValue, null);
            if (value == null) {
                throw new IllegalStateException(String.format(
                        "Unable to get value '%s', file=%s, position=%s",
                        this, location, position));
            }
            return value;
        }

        /**
         * Get a required attribute.
         *
         * @param key attribute key
         * @return value
         */
        String attribute(String key) {
            String value = attributes.get(key);
            if (value == null) {
                throw new IllegalStateException(String.format(
                        "Unable to get attribute '%s', file=%s, position=%s",
                        this, location, position));
            }
            return value;
        }


        /**
         * Create the new instance.
         *
         * @return new instance
         */
        protected abstract T build0();

        /**
         * Get or create the new instance.
         *
         * @return new instance
         */
        public final T build() {
            if (instance == null) {
                instance = build0();
            }
            return instance;
        }
    }
}
