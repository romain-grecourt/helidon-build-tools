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
import java.util.stream.Collectors;

/**
 * Input block.
 */
public abstract class Input extends Block {

    private final String label;
    private final String help;

    private Input(Input.Builder builder) {
        super(builder);
        Value labelValue = builder.attributes.get(Attributes.LABEL);
        Value helpValue = builder.attributes.get(Attributes.HELP);
        this.label = labelValue != null ? labelValue.asString() : null;
        this.help = helpValue != null ? helpValue.asString() : null;
    }

    /**
     * Get the input label.
     *
     * @return name
     */
    public String label() {
        return label;
    }

    /**
     * Get the input help.
     *
     * @return name
     */
    public String help() {
        return help;
    }

    /**
     * Input visitor.
     *
     * @param <R> result type
     * @param <A> argument type
     */
    public interface Visitor<R, A> {

        /**
         * Visit an input.
         *
         * @param input input
         * @param arg   argument
         * @return visit result
         */
        default R visitInput(Input input, A arg) {
            return null;
        }

        /**
         * Visit a boolean input.
         *
         * @param input input
         * @param arg   argument
         * @return visit result
         */
        default R visitBoolean(Boolean input, A arg) {
            return visitInput(input, arg);
        }

        /**
         * Visit a text input.
         *
         * @param input input
         * @param arg   argument
         * @return visit result
         */
        default R visitText(Text input, A arg) {
            return visitInput(input, arg);
        }

        /**
         * Visit an enum input.
         *
         * @param input input
         * @param arg   argument
         * @return visit result
         */
        default R visitEnum(Enum input, A arg) {
            return visitInput(input, arg);
        }

        /**
         * Visit a list input.
         *
         * @param input input
         * @param arg   argument
         * @return visit result
         */
        default R visitList(List input, A arg) {
            return visitInput(input, arg);
        }
    }

    /**
     * Visit this input.
     *
     * @param visitor visitor
     * @param arg     argument
     * @param <R>     result type
     * @param <A>     argument type
     * @return visit result
     */
    public abstract <R, A> R accept(Visitor<R, A> visitor, A arg);

    @Override
    public <R, A> R accept(Block.Visitor<R, A> visitor, A arg) {
        return visitor.visitInput(this, arg);
    }

    /**
     * Named input.
     */
    public static abstract class NamedInput extends Input {

        private final String name;

        private NamedInput(Input.Builder builder) {
            super(builder);
            this.name = attribute(Attributes.NAME, builder).asString();
        }

        /**
         * Get the input name.
         *
         * @return name
         */
        public String name() {
            return name;
        }
    }

    /**
     * Option input.
     */
    public static final class Option extends Input {

        private final String value;

        private Option(Input.Builder builder) {
            super(builder);
            this.value = builder.attributes.get(Attributes.VALUE).asString();
        }

        @Override
        public <R, A> R accept(Input.Visitor<R, A> visitor, A arg) {
            return visitor.visitInput(this, arg);
        }

        /**
         * Get the option value.
         *
         * @return value
         */
        public String value() {
            return value;
        }
    }

    /**
     * Text input.
     */
    public static final class Text extends NamedInput {

        private Text(Input.Builder builder) {
            super(builder);
        }

        @Override
        public <R, A> R accept(Input.Visitor<R, A> visitor, A arg) {
            return visitor.visitText(this, arg);
        }
    }

    /**
     * Boolean input.
     */
    public static final class Boolean extends NamedInput {

        private Boolean(Input.Builder builder) {
            super(builder);
        }

        @Override
        public <R, A> R accept(Input.Visitor<R, A> visitor, A arg) {
            return visitor.visitBoolean(this, arg);
        }
    }

    /**
     * Selection based input.
     */
    public static abstract class Options extends NamedInput {

        private final java.util.List<Option> options;

        private Options(Input.Builder builder) {
            super(builder);
            options = builder.statements.stream()
                                        .map(Statement.Builder::build)
                                        .filter(Option.class::isInstance)
                                        .map(Option.class::cast)
                                        .collect(Collectors.toUnmodifiableList());
        }

        /**
         * Get the options.
         *
         * @return options
         */
        public java.util.List<Option> options() {
            return options;
        }
    }

    /**
     * List input.
     */
    public static final class List extends Options {

        private List(Input.Builder builder) {
            super(builder);
        }

        @Override
        public <R, A> R accept(Input.Visitor<R, A> visitor, A arg) {
            return visitor.visitList(this, arg);
        }
    }

    /**
     * Enum input.
     */
    public static final class Enum extends Options {

        private Enum(Input.Builder builder) {
            super(builder);
        }

        @Override
        public <R, A> R accept(Input.Visitor<R, A> visitor, A arg) {
            return visitor.visitEnum(this, arg);
        }
    }

    /**
     * Create a new input block builder.
     *
     * @param location location
     * @param position position
     * @param kind     block kind
     * @return builder
     */
    public static Builder builder(Path location, Position position, Kind kind) {
        return new Builder(location, position, kind);
    }

    /**
     * Input block builder.
     */
    public static class Builder extends Block.Builder {

        private Builder(Path location, Position position, Kind kind) {
            super(location, position, kind);
        }

        @Override
        protected Block build0() {
            switch (kind) {
                case BOOLEAN:
                    return new Input.Boolean(this);
                case TEXT:
                    return new Input.Text(this);
                case ENUM:
                    return new Input.Enum(this);
                case LIST:
                    return new Input.List(this);
                case OPTION:
                    return new Input.Option(this);
                default:
                    throw new IllegalArgumentException("Unknown input block kind: " + kind);
            }
        }
    }
}
