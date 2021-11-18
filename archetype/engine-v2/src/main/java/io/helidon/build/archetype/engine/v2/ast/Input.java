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
import java.util.Objects;

/**
 * Input.
 */
public final class Input extends Statement {

    private final Kind kind;
    private final Executable body;

    private Input(Builder builder) {
        super(builder);
        this.kind = Objects.requireNonNull(builder.kind, "kind is null");
        this.body = Objects.requireNonNull(builder.body, "body is null").build();
    }

    /**
     * Get the input name.
     *
     * @return name
     */
    public String name() {
        return Attributes.NAME.get(this, ValueTypes.STRING);
    }

    /**
     * Get the body.
     *
     * @return body
     */
    public Executable body() {
        return body;
    }

    /**
     * Get the input kind.
     *
     * @return kind
     */
    public Kind inputKind() {
        return kind;
    }

    /**
     * Visit this input.
     *
     * @param visitor Visitor
     * @param arg     argument
     * @param <R>     generic type of the result
     * @param <A>     generic type of the arguments
     * @return result
     */
    public <A, R> R accept(Visitor<A, R> visitor, A arg) {
        switch (kind) {
            case TEXT:
                return visitor.visitText(this, arg);
            case BOOLEAN:
                return visitor.visitBoolean(this, arg);
            case LIST:
                return visitor.visitList(this, arg);
            case ENUM:
                return visitor.visitEnum(this, arg);
            default:
                throw new IllegalStateException();
        }
    }

    /**
     * Input visitor.
     *
     * @param <A> argument
     * @param <R> type of the returned value
     */
    @SuppressWarnings("unused")
    public interface Visitor<A, R> {

        /**
         * Visit a boolean input.
         *
         * @param input input
         * @param arg   argument
         * @return visit result
         */
        default R visitBoolean(Input input, A arg) {
            return null;
        }

        /**
         * Visit a text input.
         *
         * @param input input
         * @param arg   argument
         * @return visit result
         */
        default R visitText(Input input, A arg) {
            return null;
        }

        /**
         * Visit an enum input.
         *
         * @param input input
         * @param arg   argument
         * @return visit result
         */
        default R visitEnum(Input input, A arg) {
            return null;
        }

        /**
         * Visit a list input.
         *
         * @param input input
         * @param arg   argument
         * @return visit result
         */
        default R visitList(Input input, A arg) {
            return null;
        }
    }

    /**
     * Inputs kind.
     */
    public enum Kind {

        /**
         * Text.
         */
        TEXT(ValueTypes.STRING),

        /**
         * Boolean.
         */
        BOOLEAN(ValueTypes.BOOLEAN),

        /**
         * Enum.
         */
        ENUM(ValueTypes.STRING),

        /**
         * List.
         */
        LIST(ValueTypes.STRING_LIST);

        private final GenericType<?> type;

        Kind(GenericType<?> type) {
            this.type = type;
        }

        /**
         * Get the value type.
         *
         * @return type
         */
        public GenericType<?> valueType() {
            return type;
        }
    }

    /**
     * Input builder.
     */
    public static final class Builder extends Statement.Builder<Input, Builder> {

        private final Kind kind;
        private final Executable.Builder body;

        /**
         * Create a new builder.
         *
         * @param location location
         * @param position position
         * @param kind     kind
         */
        Builder(Path location, Position position, Kind kind) {
            super(location, position, Statement.Kind.INPUT);
            this.kind = kind;
            this.body = new Executable.Builder(location, position, Executable.Kind.INPUT);
        }

        @Override
        public Builder statement(Statement.Builder<? extends Statement, ?> builder) {
            body.statement(builder);
            return this;
        }

        @Override
        public Input build() {
            return new Input(this);
        }
    }
}
