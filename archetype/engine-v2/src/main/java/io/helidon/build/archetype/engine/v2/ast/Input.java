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
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Collections.emptyList;

/**
 * Input block.
 */
public abstract class Input extends Block {

    private final String label;
    private final String help;
    private final String prompt;

    private Input(Input.Builder builder) {
        super(builder);
        label = builder.attributes.get("label");
        prompt = builder.attributes.get("prompt");
        help = Noop.filter(builder.statements, Noop.Kind.HELP)
                   .findFirst()
                   .map(b -> b.value)
                   .orElse(null);
    }

    /**
     * Get the input label.
     *
     * @return label
     */
    public String label() {
        return label;
    }

    /**
     * Get the input prompt.
     *
     * @return prompt
     */
    public String prompt() {
        return prompt;
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
     * @param <A> argument type
     */
    public interface Visitor<A> {

        /**
         * Visit a boolean input.
         *
         * @param input input
         * @param arg   argument
         */
        default void visitBoolean(Boolean input, A arg) {
            visitInput(input, arg);
        }

        /**
         * Visit a text input.
         *
         * @param input input
         * @param arg   argument
         */
        default void visitText(Text input, A arg) {
            visitInput(input, arg);
        }

        /**
         * Visit an enum input.
         *
         * @param input input
         * @param arg   argument
         */
        default void visitEnum(Enum input, A arg) {
            visitInput(input, arg);
        }

        /**
         * Visit a list input.
         *
         * @param input input
         * @param arg   argument
         */
        default void visitList(List input, A arg) {
            visitInput(input, arg);
        }

        /**
         * Visit an input.
         *
         * @param input input
         * @param arg   argument
         */
        @SuppressWarnings("unused")
        default void visitInput(Input input, A arg) {
        }
    }

    /**
     * Visit this input.
     *
     * @param visitor visitor
     * @param arg     argument
     * @param <A>     argument type
     */
    public abstract <A> void accept(Visitor<A> visitor, A arg);

    @Override
    public <A> void accept(Block.Visitor<A> visitor, A arg) {
        visitor.visitInput(this, arg);
    }

    /**
     * Named input.
     */
    public static abstract class NamedInput extends Input {

        private final String name;

        private NamedInput(Input.Builder builder) {
            super(builder);
            this.name = builder.attribute("name");
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
            this.value = builder.attribute("value");
        }

        @Override
        public <A> void accept(Input.Visitor<A> visitor, A arg) {
            visitor.visitInput(this, arg);
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

        private final String defaultValue;

        private Text(Input.Builder builder) {
            super(builder);
            this.defaultValue = builder.attributes.get("default");
        }

        /**
         * Get the default value.
         *
         * @return default value
         */
        public Optional<String> defaultValue() {
            return Optional.ofNullable(defaultValue);
        }

        @Override
        public <A> void accept(Input.Visitor<A> visitor, A arg) {
            visitor.visitText(this, arg);
        }
    }

    /**
     * Boolean input.
     */
    public static final class Boolean extends NamedInput {

        private final boolean defaultValue;

        private Boolean(Input.Builder builder) {
            super(builder);
            defaultValue = java.lang.Boolean.parseBoolean(builder.attributes.get("default"));
        }

        /**
         * Get the default value.
         *
         * @return default value
         */
        public boolean defaultValue() {
            return defaultValue;
        }

        @Override
        public <A> void accept(Input.Visitor<A> visitor, A arg) {
            visitor.visitBoolean(this, arg);
        }
    }

    /**
     * Selection based input.
     */
    public static abstract class Options extends NamedInput {

        protected final java.util.List<Option> options;

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

        private final java.util.List<String> defaultValue;

        private List(Input.Builder builder) {
            super(builder);
            String rawDefault = builder.attributes.get("default");
            if (rawDefault != null) {
                defaultValue = Arrays.asList(rawDefault.split(","));
            } else {
                defaultValue = emptyList();
            }
        }

        /**
         * Get the default indexes.
         *
         * @return default indexes
         */
        public java.util.List<Integer> defaultIndexes() {
            return IntStream.range(0, options.size())
                            .boxed()
                            .filter(i -> defaultValue.contains(options.get(0).value))
                            .collect(Collectors.toList());
        }

        /**
         * Parse a response text.
         *
         * @param response response text
         * @return response values
         */
        public java.util.List<String> parseResponse(String response) {
            return Arrays.stream(response.trim().split("\\s+"))
                         .map(Integer::parseInt)
                         .distinct()
                         .map(i -> options.get(i - 1))
                         .map(Input.Option::value)
                         .collect(Collectors.toList());
        }

        /**
         * Get the default value.
         *
         * @return default value
         */
        public java.util.List<String> defaultValue() {
            return defaultValue;
        }

        @Override
        public <A> void accept(Input.Visitor<A> visitor, A arg) {
            visitor.visitList(this, arg);
        }
    }

    /**
     * Enum input.
     */
    public static final class Enum extends Options {

        private final String defaultValue;

        private Enum(Input.Builder builder) {
            super(builder);
            this.defaultValue = builder.attributes.get("default");
        }

        /**
         * Get the default index.
         *
         * @return default index
         */
        public int defaultIndex() {
            if (defaultValue == null) {
                return -1;
            }
            return IntStream.range(0, options.size())
                            .boxed()
                            .filter(i -> defaultValue.equals(options.get(0).value))
                            .findFirst()
                            .orElse(-1);
        }

        /**
         * Get the default value.
         *
         * @return default value
         */
        public Optional<String> defaultValue() {
            return Optional.ofNullable(defaultValue);
        }

        @Override
        public <A> void accept(Input.Visitor<A> visitor, A arg) {
            visitor.visitEnum(this, arg);
        }
    }

    /**
     * Create a new input block builder.
     *
     * @param scriptPath script path
     * @param position   position
     * @param kind       block kind
     * @return builder
     */
    public static Builder builder(Path scriptPath, Position position, Kind kind) {
        return new Builder(scriptPath, position, kind);
    }

    /**
     * Input block builder.
     */
    public static class Builder extends Block.Builder {

        private Builder(Path scriptPath, Position position, Kind kind) {
            super(scriptPath, position, kind);
        }

        @Override
        protected Block doBuild() {
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
