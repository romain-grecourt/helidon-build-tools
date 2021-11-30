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
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toUnmodifiableList;

/**
 * Output block.
 */
public class Output extends Block {

    /**
     * Create a new output.
     *
     * @param builder builder
     */
    Output(Output.Builder builder) {
        super(builder);
    }

    /**
     * Output visitor.
     *
     * @param <R> result type
     * @param <A> argument type
     */
    public interface Visitor<R, A> {

        /**
         * Visit a transformation block.
         *
         * @param transformation transformation
         * @param arg            argument
         * @return visit result
         */
        default R visitTransformation(Transformation transformation, A arg) {
            return null;
        }

        /**
         * Visit a files block.
         *
         * @param files files
         * @param arg   argument
         * @return visit result
         */
        default R visitFiles(Files files, A arg) {
            return null;
        }

        /**
         * Visit a templates block.
         *
         * @param templates templates
         * @param arg       argument
         * @return visit result
         */
        default R visitTemplates(Templates templates, A arg) {
            return null;
        }

        /**
         * Visit a file block.
         *
         * @param file file
         * @param arg  argument
         * @return visit result
         */
        default R visitFile(File file, A arg) {
            return null;
        }

        /**
         * Visit a template block.
         *
         * @param template template
         * @param arg      argument
         * @return visit result
         */
        default R visitTemplate(Template template, A arg) {
            return null;
        }

        /**
         * Visit a template block.
         *
         * @param model model
         * @param arg   argument
         * @return visit result
         */
        default R visitModel(Model model, A arg) {
            return null;
        }
    }

    /**
     * Visit this output.
     *
     * @param visitor visitor
     * @param arg     argument
     * @param <R>     result type
     * @param <A>     argument type
     * @return visit result
     */
    public <R, A> R accept(Visitor<R, A> visitor, A arg) {
        return null;
    }

    @Override
    public <R, A> R accept(Block.Visitor<R, A> visitor, A arg) {
        return visitor.visitOutput(this, arg);
    }

    /**
     * Path rule.
     */
    public static final class Transformation extends Output {

        private final String id;
        private final List<Replace> operations;

        private Transformation(Output.Builder builder) {
            super(builder);
            this.id = builder.attribute("id");
            this.operations = Noop.filter(builder.statements, Noop.Kind.REPLACE)
                                  .map(b -> new Replace(
                                          b.attribute("replacement"),
                                          b.attribute("regex")))
                                  .collect(toUnmodifiableList());
        }

        @Override
        public <R, A> R accept(Output.Visitor<R, A> visitor, A arg) {
            return visitor.visitTransformation(this, arg);
        }

        /**
         * Get the id.
         *
         * @return id
         */
        public String id() {
            return id;
        }

        /**
         * Get the operations.
         *
         * @return operations
         */
        public List<Replace> operations() {
            return operations;
        }

        /**
         * Replace operation.
         */
        public static final class Replace {

            private final String replacement;
            private final String regex;

            private Replace(String replacement, String regex) {
                this.replacement = replacement;
                this.regex = regex;
            }

            /**
             * Get the replacement.
             *
             * @return replacement
             */
            public String replacement() {
                return replacement;
            }

            /**
             * Get the regex.
             *
             * @return regex
             */
            public String regex() {
                return regex;
            }
        }
    }

    /**
     * Files.
     */
    public static class Files extends Output {

        private final List<String> transformations;
        private final String directory;
        private final List<String> includes;
        private final List<String> excludes;

        /**
         * Create a new files block.
         *
         * @param builder builder
         */
        Files(Output.Builder builder) {
            super(builder);
            String attr = builder.attributes.get("transformations");
            this.transformations = attr != null ? Arrays.asList(attr.split(",")) : emptyList();
            this.directory = Noop.filter(builder.statements, Noop.Kind.DIRECTORY)
                                 .map(b -> b.value)
                                 .findFirst()
                                 .orElseThrow(() -> new IllegalArgumentException("directory is required"));
            this.includes = Block.filter(builder.statements, Block.Kind.INCLUDES)
                                 .flatMap(b -> Noop.filter(b.statements, Noop.Kind.INCLUDE))
                                 .map(b -> b.value)
                                 .collect(toUnmodifiableList());
            this.excludes = Block.filter(builder.statements, Block.Kind.EXCLUDES)
                                 .flatMap(b -> Noop.filter(b.statements, Noop.Kind.EXCLUDE))
                                 .map(b -> b.value)
                                 .collect(toUnmodifiableList());
        }

        @Override
        public <R, A> R accept(Output.Visitor<R, A> visitor, A arg) {
            return visitor.visitFiles(this, arg);
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
         * Get the includes.
         *
         * @return includes
         */
        public List<String> includes() {
            return includes;
        }

        /**
         * Get the excludes.
         *
         * @return excludes
         */
        public List<String> excludes() {
            return excludes;
        }
    }

    /**
     * Templates.
     */
    public static final class Templates extends Files {

        private final String engine;

        private Templates(Output.Builder builder) {
            super(builder);
            this.engine = builder.attribute("engine");
        }

        @Override
        public <R, A> R accept(Output.Visitor<R, A> visitor, A arg) {
            return visitor.visitTemplates(this, arg);
        }

        /**
         * Get the template engine.
         *
         * @return engine
         */
        public String engine() {
            return engine;
        }
    }

    /**
     * File.
     */
    public static class File extends Output {

        private final String source;
        private final String target;

        /**
         * Create a new file block.
         *
         * @param builder builder
         */
        File(Output.Builder builder) {
            super(builder);
            this.source = builder.attribute("source");
            this.target = builder.attribute("target");
        }

        @Override
        public <R, A> R accept(Output.Visitor<R, A> visitor, A arg) {
            return visitor.visitFile(this, arg);
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
    }

    /**
     * Template.
     */
    public static final class Template extends File {

        private final String engine;

        /**
         * Create a new file block.
         *
         * @param builder builder
         */
        Template(Output.Builder builder) {
            super(builder);
            this.engine = builder.attribute("engine");
        }

        @Override
        public <R, A> R accept(Output.Visitor<R, A> visitor, A arg) {
            return visitor.visitTemplate(this, arg);
        }

        /**
         * Get the engine.
         *
         * @return engine
         */
        public String engine() {
            return engine;
        }
    }

    /**
     * Create a new Output block builder.
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
     * Output block builder.
     */
    public static class Builder extends Block.Builder {

        /**
         * Create a new output builder.
         *
         * @param scriptPath script path
         * @param position   position
         * @param kind       kind
         */
        Builder(Path scriptPath, Position position, Kind kind) {
            super(scriptPath, position, kind);
        }

        @Override
        protected Block doBuild() {
            switch (kind) {
                case OUTPUT:
                    return new Output(this);
                case TRANSFORMATION:
                    return new Transformation(this);
                case FILES:
                    return new Files(this);
                case TEMPLATES:
                    return new Templates(this);
                case FILE:
                    return new File(this);
                case TEMPLATE:
                    return new Template(this);
                default:
                    throw new IllegalArgumentException("Unknown output block kind: " + kind);
            }
        }
    }
}
