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

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Preset.
 */
public final class Preset extends Expression {

    private final Input.Kind kind;

    private Preset(Builder builder) {
        super(builder);
        this.kind = Objects.requireNonNull(builder.kind, "kind is null");
    }

    /**
     * Get the input path.
     *
     * @return path
     */
    public String path() {
        return Attributes.PATH.get(this, ValueTypes.STRING);
    }

    /**
     * Get the value.
     *
     * @return value
     */
    public Value value() {
        return Attributes.PATH.get(this);
    }

    /**
     * Visit this preset.
     *
     * @param visitor Visitor
     * @param arg     argument
     * @param <R>     generic type of the result
     * @param <A>     generic type of the arguments
     * @return result
     */
    public <A, R> R accept(Visitor<A, R> visitor, A arg) {
        switch (kind) {
            case BOOLEAN:
                return visitor.visitBoolean(this, arg);
            case TEXT:
                return visitor.visitText(this, arg);
            case ENUM:
                return visitor.visitEnum(this, arg);
            case LIST:
                return visitor.visitList(this, arg);
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Invocation visitor.
     *
     * @param <A> argument
     * @param <R> type of the returned value
     */
    @SuppressWarnings("unused")
    public interface Visitor<A, R> {

        /**
         * Visit a boolean preset.
         *
         * @param preset preset
         * @param arg    argument
         * @return visit result
         */
        default R visitBoolean(Preset preset, A arg) {
            return null;
        }

        /**
         * Visit a text preset.
         *
         * @param preset preset
         * @param arg    argument
         * @return visit result
         */
        default R visitText(Preset preset, A arg) {
            return null;
        }

        /**
         * Visit an enum preset.
         *
         * @param preset preset
         * @param arg    argument
         * @return visit result
         */
        default R visitEnum(Preset preset, A arg) {
            return null;
        }

        /**
         * Visit a list preset.
         *
         * @param preset preset
         * @param arg    argument
         * @return visit result
         */
        default R visitList(Preset preset, A arg) {
            return null;
        }
    }

    /**
     * Get the input kind.
     *
     * @return kind
     */
    public Input.Kind inputKind() {
        return kind;
    }

    /**
     * Preset builder.
     */
    public static final class Builder extends Expression.Builder<Preset, Builder> {

        private final Input.Kind kind;

        /**
         * Create a new builder.
         *
         * @param location location
         * @param position position
         * @param kind     kind
         */
        Builder(Path location, Position position, Input.Kind kind) {
            super(location, position, Kind.PRESET);
            this.kind = kind;
        }

        /**
         * Parse the preset value.
         *
         * @param rawAttrs raw attributes
         * @return this builder
         */
        public Builder parseValue(Map<String, String> rawAttrs) {
            return parseAttribute(Attributes.VALUE, kind.valueType(), rawAttrs.get("value"));
        }

        @Override
        public Preset build() {
            return new Preset(this);
        }
    }
}
