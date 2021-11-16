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

package io.helidon.build.archetype.engine.v2.descriptor;

import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Helidon archetype V2 descriptor.
 */
public class ArchetypeDescriptor {

    private final Map<String, String> attributes;
    private final List<ContextBlock> contexts;
    private final List<Step> steps;
    private final List<InputBlock> inputs;
    private final List<Source> sources;
    private final List<Exec> execs;
    private final Output output;
    private String help;

    ArchetypeDescriptor(Map<String, String> archetypeAttributes,
                        List<ContextBlock> context,
                        List<Step> step,
                        List<InputBlock> inputs,
                        List<Source> source,
                        List<Exec> exec,
                        Output output,
                        String help) {

        this.attributes = archetypeAttributes;
        this.contexts = context == null ? List.of() : context;
        this.steps = step == null ? List.of() : step;
        this.inputs = inputs == null ? List.of() : inputs;
        this.sources = source == null ? List.of() : source;
        this.execs = exec == null ? List.of() : exec;
        this.output = output;
        this.help = help;
    }

    /**
     * Create an archetype descriptor instance from an input stream.
     *
     * @param is input stream
     * @return ArchetypeDescriptor
     */
    public static ArchetypeDescriptor read(InputStream is) {
        return ArchetypeDescriptorReader.read(is);
    }

    /**
     * Get the list of context archetype from main archetype-script xml element.
     *
     * @return List of Context
     */
    public List<ContextBlock> contexts() {
        return contexts;
    }

    /**
     * Get the list of step archetype from main archetype-script xml element.
     *
     * @return List of Step
     */
    public List<Step> steps() {
        return steps;
    }

    /**
     * Get a list of input archetype from main archetype-script xml element.
     *
     * @return List of Input
     */
    public List<InputBlock> inputBlocks() {
        return inputs;
    }

    /**
     * Get the list of source archetype from main archetype-script xml element.
     *
     * @return List of Source
     */
    public List<Source> sources() {
        return sources;
    }

    /**
     * Get the list of exec archetype from main archetype-script xml element.
     *
     * @return List of Exec
     */
    public List<Exec> execs() {
        return execs;
    }

    /**
     * Get the output archetype from main archetype-script xml element.
     *
     * @return Output
     */
    public Output output() {
        return output;
    }

    /**
     * Get the help archetype from main archetype-script xml element.
     *
     * @return help as a String
     */
    public String help() {
        return help;
    }

    /**
     * Get the main archetype-script xml element attributes.
     *
     * @return Map of attributes
     */
    public Map<String, String> attributes() {
        return attributes;
    }

    /**
     * Set the help archetype from main archetype-script xml element.
     *
     * @param help String contained into help element
     */
    public void help(String help) {
        this.help = help;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ArchetypeDescriptor a = (ArchetypeDescriptor) o;
        return contexts.equals(a.contexts)
                && steps.equals(a.steps)
                && inputs.equals(a.inputs)
                && sources.equals(a.sources)
                && execs.equals(a.execs)
                && output.equals(a.output);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), attributes, contexts, steps, inputs, sources, execs, output);
    }

    @Override
    public String toString() {
        return "ArchetypeDescriptor{"
                + "archetypeAttributes=" + attributes()
                + ", contexts=" + contexts()
                + ", steps=" + steps()
                + ", inputs=" + inputBlocks()
                + ", sources=" + sources()
                + ", execs=" + execs()
                + ", output=" + output()
                + ", help=" + help()
                + '}';
    }

    /**
     * Base class for conditional nodes.
     */
    public abstract static class Conditional {

        private final String ifProperties;

        Conditional(String ifProperties) {
            this.ifProperties = ifProperties;
        }

        /**
         * Get the if properties.
         *
         * @return list of properties
         */
        public String ifProperties() {
            return ifProperties;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Conditional that = (Conditional) o;
            return ifProperties.equals(that.ifProperties);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ifProperties);
        }
    }

    /**
     * Base class for context nodes.
     */
    public abstract static class Context {

        private final String path;

        protected Context(String path) {
            this.path = Objects.requireNonNull(path, "path is null");
        }

        /**
         * Get the context path for this node.
         *
         * @return path
         */
        public String path() {
            return path;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            Context contextNode = (Context) o;
            return path.equals(contextNode.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), path);
        }
    }

    /**
     * Archetype Context.
     */
    public static class ContextBlock {

        private final LinkedList<Context> nodes = new LinkedList<>();

        /**
         * Get the context nodes.
         *
         * @return list of context node, never {@code null}
         */
        public LinkedList<Context> nodes() {
            return nodes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ContextBlock context = (ContextBlock) o;
            return Objects.equals(nodes, context.nodes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nodes);
        }

        @Override
        public String toString() {
            return "Context{"
                    + "nodes=" + nodes
                    + '}';
        }
    }

    /**
     * Archetype boolean in {@link ContextBlock} nodes.
     */
    public static class ContextBoolean extends Context {

        private boolean bool;

        protected ContextBoolean(String path) {
            super(path);
        }

        /**
         * Get the boolean value.
         *
         * @return boolean
         */
        public boolean bool() {
            return bool;
        }

        /**
         * Set the boolean value.
         *
         * @param bool boolean to be set
         */
        void bool(boolean bool) {
            this.bool = bool;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            ContextBoolean b = (ContextBoolean) o;
            return bool == b.bool;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), bool);
        }

        @Override
        public String toString() {
            return "ContextBoolean{"
                    + "path=" + path()
                    + ", bool=" + bool()
                    + '}';
        }
    }

    /**
     * Archetype enum in {@link ContextBlock} nodes.
     */
    public static class ContextEnum extends Context {

        private final LinkedList<String> values;

        protected ContextEnum(String path) {
            super(path);
            values = new LinkedList<>();
        }

        /**
         * Get the enum values.
         *
         * @return values
         */
        public LinkedList<String> values() {
            return values;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            ContextEnum e = (ContextEnum) o;
            return values.equals(e.values);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), values);
        }

        @Override
        public String toString() {
            return "ContextEnum{"
                    + "path=" + path()
                    + ", values=" + values()
                    + '}';
        }
    }

    /**
     * Archetype list in {@link ContextBlock} nodes.
     */
    public static class ContextList extends Context {

        private final List<String> values;

        protected ContextList(String path) {
            super(path);
            values = new LinkedList<>();
        }

        /**
         * Get the list values.
         *
         * @return values
         */
        public List<String> values() {
            return values;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            ContextList list = (ContextList) o;
            return values.equals(list.values);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), values);
        }

        @Override
        public String toString() {
            return "ContextList{"
                    + "path=" + path()
                    + ", values=" + values()
                    + '}';
        }
    }

    /**
     * Archetype text in {@link ContextBlock} nodes.
     */
    public static class ContextText extends Context {

        private String text;

        ContextText(String path) {
            super(path);
        }

        /**
         * Get text string from text element.
         *
         * @return text
         */
        public String text() {
            return text;
        }

        /**
         * Set text string from text element.
         *
         * @param text content
         */
        public void text(String text) {
            this.text = text;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            ContextText ct = (ContextText) o;
            return text.equals(ct.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), text);
        }

        @Override
        public String toString() {
            return "ContextText{"
                    + "path=" + path()
                    + ", text=" + text()
                    + '}';
        }
    }

    /**
     * Archetype exec.
     */
    public static class Exec {

        private final String url;
        private final String src;

        Exec(String url, String src) {
            this.url = url;
            this.src = src;
        }

        /**
         * Get the url.
         *
         * @return url as a String
         */
        public String url() {
            return url;
        }

        /**
         * Get the source.
         *
         * @return source as a String
         */
        public String src() {
            return src;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            Exec e = (Exec) o;
            return url.equals(e.url)
                    && src.equals(e.src);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), url, src);
        }

        @Override
        public String toString() {
            return "Exec{"
                    + "url=" + url()
                    + "src=" + src()
                    + '}';
        }
    }

    /**
     * Archetype file in {@link Output} archetype.
     */
    public static class FileSet extends Conditional {

        private final String source;
        private final String target;

        FileSet(String source, String target, String ifProperties) {
            super(ifProperties);
            this.source = source;
            this.target = target;
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            FileSet that = (FileSet) o;
            return source.equals(that.source)
                    && target.equals(that.target);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), source, target);
        }

        @Override
        public String toString() {
            return "File{"
                    + "source=" + source()
                    + ", target=" + target()
                    + ", if=" + ifProperties()
                    + '}';
        }

    }

    /**
     * Archetype files in {@link Output} archetype.
     */
    public static class FileSets extends Conditional {

        private final LinkedList<String> transformations;
        private final LinkedList<String> includes;
        private final LinkedList<String> excludes;
        private String directory;

        FileSets(String transformations, String ifProperties) {
            super(ifProperties);
            this.transformations = parseTransformation(transformations);
            this.includes = new LinkedList<>();
            this.excludes = new LinkedList<>();
        }

        private LinkedList<String> parseTransformation(String transformations) {
            if (transformations == null) {
                return new LinkedList<String>();
            }
            return new LinkedList<String>(Arrays.asList(transformations.split(",")));
        }

        /**
         * Get the directory of this file set.
         *
         * @return directory optional, never {@code null}
         */
        public Optional<String> directory() {
            return Optional.ofNullable(directory);
        }

        /**
         * Set the directory.
         * @param directory directory
         */
        void directory(String directory) {
            this.directory = Objects.requireNonNull(directory, "directory is null");
        }

        /**
         * Get the exclude filters.
         *
         * @return list of exclude filter, never {@code null}
         */
        public LinkedList<String> excludes() {
            return excludes;
        }

        /**
         * Get the include filters.
         *
         * @return list of include filter, never {@code null}
         */
        public LinkedList<String> includes() {
            return includes;
        }

        /**
         * Get the applied transformations.
         *
         * @return list of transformation, never {@code null}
         */
        public LinkedList<String> transformations() {
            return transformations;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            FileSets fileSet = (FileSets) o;
            return transformations.equals(fileSet.transformations)
                    && includes.equals(fileSet.includes)
                    && excludes.equals(fileSet.excludes)
                    && directory.equals(fileSet.directory);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), transformations, includes, excludes, directory);
        }

        @Override
        public String toString() {
            return "FileSet{"
                    + ", transformations=" + transformations
                    + ", includes=" + includes
                    + ", excludes=" + excludes
                    + ", directory='" + directory + '\''
                    + ", if=" + ifProperties()
                    + '}';
        }
    }

    /**
     * Base class for {@link InputBlock} nodes.
     */
    public abstract static class Input {

        private final String label;
        private final String name;
        private final String def;
        private final String prompt;
        private boolean optional = false;

        Input(String label, String name, String def, String prompt, boolean optional) {
            this.label = label;
            this.name = name;
            this.def = def;
            this.prompt = prompt;
            this.optional = optional;
        }

        /**
         * Get the label.
         *
         * @return label
         */
        public String label() {
            return label;
        }

        /**
         * Get the name.
         *
         * @return name
         */
        public String name() {
            return name;
        }

        /**
         * Get the default value.
         *
         * @return default value
         */
        public String defaultValue() {
            return def;
        }

        /**
         * Get the prompt.
         *
         * @return prompt
         */
        public String prompt() {
            return prompt;
        }

        /**
         * Get the optional attribute.
         *
         * @return boolean
         */
        public boolean isOptional() {
            return optional;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            Input inputNode = (Input) o;
            return label.equals(inputNode.label)
                    && name.equals(inputNode.name)
                    && def.equals(inputNode.def)
                    && prompt.equals(inputNode.prompt)
                    && optional == inputNode.optional;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), label, name, def, prompt, optional);
        }

    }

    /**
     * Archetype Input.
     */
    public static class InputBlock {

        private final LinkedList<Input> nodes = new LinkedList<>();
        private final LinkedList<ContextBlock> contexts = new LinkedList<>();
        private final LinkedList<Step> steps = new LinkedList<>();
        private final LinkedList<InputBlock> inputs = new LinkedList<>();
        private final LinkedList<Source> sources = new LinkedList<>();
        private final LinkedList<Exec> execs = new LinkedList<>();
        private Output output;

        protected InputBlock() {
        }

        /**
         * Get the Input nodes: {@link InputText}, {@link InputBoolean}, {@link InputEnum}, {@link InputList}.
         *
         * @return nodes
         */
        public LinkedList<Input> inputs() {
            return nodes;
        }

        /**
         * Get input contexts.
         *
         * @return contexts
         */
        public LinkedList<ContextBlock> contexts() {
            return contexts;
        }

        /**
         * Get input steps.
         *
         * @return steps
         */
        public LinkedList<Step> steps() {
            return steps;
        }

        /**
         * Get input inputs.
         *
         * @return inputs
         */
        public LinkedList<InputBlock> inputBlocks() {
            return inputs;
        }

        /**
         * Get input sources.
         *
         * @return sources
         */
        public LinkedList<Source> sources() {
            return sources;
        }

        /**
         * Get input execs.
         *
         * @return execs
         */
        public LinkedList<Exec> execs() {
            return execs;
        }

        /**
         * Get input output.
         *
         * @return output
         */
        public Output output() {
            return output;
        }

        /**
         * Set input output.
         *
         * @param  output output
         */
        public void output(Output output) {
            this.output = output;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            InputBlock input = (InputBlock) o;
            return nodes.equals(input.nodes)
                    && contexts.equals(input.contexts)
                    && steps.equals(input.steps)
                    && inputs.equals(input.inputs)
                    && sources.equals(input.sources)
                    && execs.equals(input.execs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), nodes, contexts, steps, inputs, sources, execs, output);
        }

        @Override
        public String toString() {
            return "Input{"
                    + "nodes=" + inputs()
                    + ", contexts=" + contexts()
                    + ", steps=" + steps()
                    + ", inputs=" + inputBlocks()
                    + ", sources=" + sources()
                    + ", execs=" + execs()
                    + ", output=" + output()
                    + '}';
        }
    }

    /**
     * Archetype boolean in {@link InputBlock} archetype.
     */
    public static class InputBoolean extends Input {

        private String help;
        private final LinkedList<ContextBlock> contexts = new LinkedList<>();
        private final LinkedList<Step> steps = new LinkedList<>();
        private final LinkedList<InputBlock> inputs = new LinkedList<>();
        private final LinkedList<Source> sources = new LinkedList<>();
        private final LinkedList<Exec> execs = new LinkedList<>();
        private Output output;

        InputBoolean(String label,
                     String name,
                     String def,
                     String prompt,
                     boolean optional) {
            super(label, name, def, prompt, optional);
        }

        /**
         * Get the help element content.
         *
         * @return help
         */
        public String help() {
            return help;
        }

        /**
         * Set the help element content.
         *
         * @param help help content
         */
        public void help(String help) {
            this.help = help;
        }

        /**
         * Get the contexts.
         *
         * @return contexts
         */
        public LinkedList<ContextBlock> contexts() {
            return contexts;
        }

        /**
         * Get the steps.
         *
         * @return steps
         */
        public LinkedList<Step> steps() {
            return steps;
        }

        /**
         * Get the inputs.
         *
         * @return inputs
         */
        public LinkedList<InputBlock> inputBlocks() {
            return inputs;
        }

        /**
         * Get the sources.
         *
         * @return sources
         */
        public LinkedList<Source> sources() {
            return sources;
        }

        /**
         * Get the execs.
         *
         * @return execs
         */
        public LinkedList<Exec> execs() {
            return execs;
        }

        /**
         * Get the output.
         *
         * @return output
         */
        public Output output() {
            return output;
        }

        /**
         * Set the output.
         *
         * @param output output
         */
        public void output(Output output) {
            this.output = output;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            InputBoolean ib = (InputBoolean) o;
            return help.equals(ib.help);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), help, contexts, steps, inputs, sources, execs, output);
        }

        @Override
        public String toString() {
            return "InputBoolean{"
                    + "help=" + help()
                    + ", contexts=" + contexts()
                    + ", steps=" + steps()
                    + ", inputs=" + inputBlocks()
                    + ", sources=" + sources()
                    + ", execs=" + execs()
                    + ", output=" + output()
                    + ", label=" + label()
                    + ", name=" + name()
                    + ", default=" + defaultValue()
                    + ", prompt=" + prompt()
                    + ", optional=" + isOptional()
                    + '}';
        }
    }

    /**
     * Archetype enum in {@link InputBlock}.
     */
    public static class InputEnum extends Input {

        private String help;
        private final LinkedList<InputOption> options = new LinkedList<>();
        private final LinkedList<ContextBlock> contexts = new LinkedList<>();
        private final LinkedList<Step> steps = new LinkedList<>();
        private final LinkedList<InputBlock> inputs = new LinkedList<>();
        private final LinkedList<Source> sources = new LinkedList<>();
        private final LinkedList<Exec> execs = new LinkedList<>();
        private Output output;

        InputEnum(String label,
                  String name,
                  String def,
                  String prompt,
                  boolean optional) {
            super(label, name, def, prompt, optional);
        }

        /**
         * Get the help element content.
         *
         * @return help
         */
        public String help() {
            return help;
        }

        /**
         * Set the help element content.
         *
         * @param help content
         */
        public void help(String help) {
            this.help = help;
        }

        /**
         * Get the options.
         *
         * @return options
         */
        public LinkedList<InputOption> options() {
            return options;
        }

        /**
         * Get the contexts.
         *
         * @return contexts
         */
        public LinkedList<ContextBlock> contexts() {
            return contexts;
        }

        /**
         * Get the steps.
         *
         * @return steps
         */
        public LinkedList<Step> steps() {
            return steps;
        }

        /**
         * Get the inputs.
         *
         * @return inputs
         */
        public LinkedList<InputBlock> inputsBlocks() {
            return inputs;
        }

        /**
         * Get the sources.
         *
         * @return sources
         */
        public LinkedList<Source> sources() {
            return sources;
        }

        /**
         * Get the execs.
         *
         * @return execs
         */
        public LinkedList<Exec> execs() {
            return execs;
        }

        /**
         * Get the output.
         *
         * @return output
         */
        public Output output() {
            return output;
        }

        /**
         * Set the Output.
         *
         * @param output element
         */
        public void output(Output output) {
            this.output = output;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            InputEnum inputList = (InputEnum) o;
            return help.equals(inputList.help)
                    && options == inputList.options
                    && contexts == inputList.contexts
                    && steps == inputList.steps
                    && inputs == inputList.inputs
                    && sources == inputList.sources
                    && execs == inputList.execs
                    && output.equals(inputList.output);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), help, options, contexts, steps, inputs, sources, execs, output);
        }

        @Override
        public String toString() {
            return "InputEnum{"
                    + "help=" + help()
                    + ", options=" + options()
                    + ", contexts=" + contexts()
                    + ", steps=" + steps()
                    + ", inputs=" + inputsBlocks()
                    + ", sources=" + sources()
                    + ", execs=" + execs()
                    + ", output=" + output()
                    + ", label=" + label()
                    + ", name=" + name()
                    + ", default=" + defaultValue()
                    + ", prompt=" + prompt()
                    + ", optional=" + isOptional()
                    + '}';
        }


    }

    /**
     * Archetype list in {@link InputBlock} archetype.
     */
    public static class InputList extends Input {

        private final String min;
        private final String max;
        private String help;
        private final LinkedList<InputOption> options = new LinkedList<>();
        private final LinkedList<ContextBlock> contexts = new LinkedList<>();
        private final LinkedList<Step> steps = new LinkedList<>();
        private final LinkedList<InputBlock> inputs = new LinkedList<>();
        private final LinkedList<Source> sources = new LinkedList<>();
        private final LinkedList<Exec> execs = new LinkedList<>();
        private Output output;

        InputList(String label,
                  String name,
                  String def,
                  String prompt,
                  boolean optional,
                  String min,
                  String max,
                  String help) {
            super(label, name, def, prompt, optional);
            this.min = min;
            this.max = max;
            this.help = help;
        }

        /**
         * Get the minimum.
         *
         * @return minimum
         */
        public String min() {
            return min;
        }

        /**
         * Get the maximum.
         *
         * @return maximum
         */
        public String max() {
            return max;
        }

        /**
         * Get the help content.
         *
         * @return help content
         */
        public String help() {
            return help;
        }

        /**
         * Set the help content.
         *
         * @param help help
         */
        public void help(String help) {
            this.help = help;
        }

        /**
         * Get the options.
         *
         * @return options
         */
        public LinkedList<InputOption> options() {
            return options;
        }

        /**
         * Get the contexts.
         *
         * @return contexts
         */
        public LinkedList<ContextBlock> contexts() {
            return contexts;
        }

        /**
         * Get the steps.
         *
         * @return steps
         */
        public LinkedList<Step> steps() {
            return steps;
        }

        /**
         * Get the inputs.
         *
         * @return inputs
         */
        public LinkedList<InputBlock> inputBlocks() {
            return inputs;
        }

        /**
         * Get the sources.
         *
         * @return sources
         */
        public LinkedList<Source> sources() {
            return sources;
        }

        /**
         * Get the execs.
         *
         * @return execs
         */
        public LinkedList<Exec> execs() {
            return execs;
        }

        /**
         * Get the output.
         *
         * @return output
         */
        public Output output() {
            return output;
        }

        /**
         * Set the output.
         *
         * @param output output
         */
        public void output(Output output) {
            this.output = output;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            InputList inputList = (InputList) o;
            return min == inputList.min
                    && max == inputList.max;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), min, max, help, options, contexts, steps, inputs, sources, execs, output);
        }

        @Override
        public String toString() {
            return "InputList{"
                    + "min=" + min()
                    + ", max=" + max()
                    + ", help=" + help()
                    + ", options=" + options()
                    + ", contexts=" + contexts()
                    + ", steps=" + steps()
                    + ", inputs=" + inputBlocks()
                    + ", sources=" + sources()
                    + ", execs=" + execs()
                    + ", output=" + output()
                    + ", label=" + label()
                    + ", name=" + name()
                    + ", default=" + defaultValue()
                    + ", prompt=" + prompt()
                    + ", optional=" + isOptional()
                    + '}';
        }
    }

    /**
     * Archetype option used in {@link InputList} and {@link InputEnum}.
     */
    public static class InputOption {

        private final String label;
        private final String value;
        private String help;
        private final LinkedList<ContextBlock> contexts = new LinkedList<>();
        private final LinkedList<Step> steps = new LinkedList<>();
        private final LinkedList<InputBlock> inputs = new LinkedList<>();
        private final LinkedList<Source> sources = new LinkedList<>();
        private final LinkedList<Exec> execs = new LinkedList<>();
        private Output output;

        InputOption(String label, String value) {
            this.label = label;
            this.value = value;
        }

        /**
         * Get the label.
         *
         * @return label
         */
        public String label() {
            return label;
        }

        /**
         * Get the value.
         *
         * @return value
         */
        public String value() {
            return value;
        }

        /**
         * Get the help element content.
         *
         * @return help
         */
        public String help() {
            return help;
        }

        /**
         * Set the help element content.
         *
         * @param help content
         */
        public void help(String help) {
            this.help = help;
        }

        /**
         * Get the contexts.
         *
         * @return contexts
         */
        public LinkedList<ContextBlock> contexts() {
            return contexts;
        }

        /**
         * Get the steps.
         *
         * @return steps
         */
        public LinkedList<Step> steps() {
            return steps;
        }

        /**
         * Get the inputs.
         *
         * @return inputs
         */
        public LinkedList<InputBlock> inputBlocks() {
            return inputs;
        }

        /**
         * Get the sources.
         *
         * @return sources
         */
        public LinkedList<Source> sources() {
            return sources;
        }

        /**
         * Get the execs.
         *
         * @return execs
         */
        public LinkedList<Exec> execs() {
            return execs;
        }

        /**
         * Get the output.
         *
         * @return output
         */
        public Output output() {
            return output;
        }

        /**
         * Set the output.
         *
         * @param output output
         */
        public void output(Output output) {
            this.output = output;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            InputOption option = (InputOption) o;
            return label.equals(option.label)
                    && value.equals(option.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), label, value, help, contexts, steps, inputs, sources, execs, output);
        }

        @Override
        public String toString() {
            return "Option{"
                    + "label=" + label()
                    + ", value=" + value()
                    + ", help=" + help()
                    + ", contexts=" + contexts()
                    + ", steps=" + steps()
                    + ", inputs=" + inputBlocks()
                    + ", sources=" + sources()
                    + ", execs=" + execs()
                    + ", output=" + output()
                    + '}';
        }
    }

    /**
     * Archetype text in {@link InputBlock} nodes.
     */
    public static class InputText extends Input {

        private final String placeHolder;
        private String help;

        InputText(String label,
                  String name,
                  String def,
                  String prompt,
                  boolean optional,
                  String placeHolder) {
            super(label, name, def, prompt, optional);
            this.placeHolder = placeHolder;
        }

        /**
         * Get the placeholder.
         *
         * @return placeholder
         */
        public String placeHolder() {
            return placeHolder;
        }

        /**
         * Get the help element content.
         *
         * @return help
         */
        public String help() {
            return help;
        }

        /**
         * Set the help element content.
         *
         * @param help help content
         */
        public void help(String help) {
            this.help = help;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            InputText text = (InputText) o;
            return placeHolder.equals(text.placeHolder)
                    && help.equals(text.help);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), placeHolder, help);
        }

        @Override
        public String toString() {
            return "Text{"
                    + "placeholder=" + placeHolder()
                    + ", help=" + help()
                    + ", label=" + label()
                    + ", name=" + name()
                    + ", default=" + defaultValue()
                    + ", prompt=" + prompt()
                    + ", optional=" + isOptional()
                    + '}';
        }
    }

    /**
     * Archetype model.
     */
    public static class Model extends Conditional {

        private final LinkedList<ModelKeyedValue> keyValues = new LinkedList<>();
        private final LinkedList<ModelKeyedList> keyLists = new LinkedList<>();
        private final LinkedList<ModelKeyedMap> keyMaps = new LinkedList<>();

        /**
         * Model constructor.
         *
         * @param ifProperties  if attribute
         */
        public Model(String ifProperties) {
            super(ifProperties);
        }

        /**
         * Get the model values with key.
         *
         * @return values
         */
        public LinkedList<ModelKeyedValue> keyedValues() {
            return keyValues;
        }

        /**
         * Get the model lists with key.
         *
         * @return lists
         */
        public LinkedList<ModelKeyedList> keyedLists() {
            return keyLists;
        }

        /**
         * Get the model maps with key.
         *
         * @return maps
         */
        public LinkedList<ModelKeyedMap> keyedMaps() {
            return keyMaps;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            Model m = (Model) o;
            return keyValues.equals(m.keyValues);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), keyValues);
        }

        @Override
        public String toString() {
            return "Model{"
                    + "keyValues=" + keyedValues()
                    + "keyLists=" + keyedLists()
                    + "keyMaps=" + keyedMaps()
                    + "if=" + ifProperties()
                    + '}';
        }

    }

    /**
     * Archetype list with key attribute used in {@link Model} and {@link ModelMap}.
     */
    public static class ModelKeyedList extends ModelList {

        private final String key;

        /**
         * ModelKeyList constructor.
         *
         * @param key           key attribute
         * @param order         order attribute
         * @param ifProperties  if attribute
         */
        public ModelKeyedList(String key, int order, String ifProperties) {
            super(order, ifProperties);
            this.key = key;
        }

        /**
         * Get the key of the list.
         *
         * @return key
         */
        public String key() {
            return key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            ModelKeyedList m = (ModelKeyedList) o;
            return key.equals(m.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), key);
        }

        @Override
        public String toString() {
            return "ValueType{"
                    + ", key=" + key()
                    + "values=" + values()
                    + "maps=" + maps()
                    + "lists=" + lists()
                    + ", if=" + ifProperties()
                    + '}';
        }

    }

    /**
     * Archetype map with key attribute used in {@link Model} and {@link ModelMap}.
     */
    public static class ModelKeyedMap extends ModelMap {

        private final String key;

        /**
         * ModelKeyMap constructor.
         *
         * @param key           key attribute
         * @param order         order attribute
         * @param ifProperties  if attribute
         */
        public ModelKeyedMap(String key, int order, String ifProperties) {
            super(order, ifProperties);
            this.key = key;
        }

        /**
         * Get the key of the map.
         *
         * @return key
         */
        public String key() {
            return key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            ModelKeyedMap m = (ModelKeyedMap) o;
            return key.equals(m.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), key);
        }

        @Override
        public String toString() {
            return "ModelKeyMap{"
                    + ", key=" + key()
                    + "keyValues=" + keyValues()
                    + "keyLists=" + keyLists()
                    + "keyMaps=" + keyMaps()
                    + "order=" + order()
                    + ", if=" + ifProperties()
                    + '}';
        }
    }

    /**
     * Archetype value with key attribute used in {@link Model} and {@link ModelMap}.
     */
    public static class ModelKeyedValue extends ModelValue {

        private final String key;

        /**
         * ModelKeyValue constructor.
         *
         * @param key           key attribute
         * @param url           url attribute
         * @param file          file attribute
         * @param template      template attribute
         * @param order         order attribute
         * @param ifProperties  if attribute
         */
        public ModelKeyedValue(String key,
                               String url,
                               String file,
                               String template,
                               int order,
                               String ifProperties) {
            super(url, file, template, order, ifProperties);
            this.key = key;
        }

        /**
         * Get the key of the map.
         *
         * @return key
         */
        public String key() {
            return key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            ModelKeyedValue m = (ModelKeyedValue) o;
            return key.equals(m.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), key);
        }

        @Override
        public String toString() {
            return "ModelKeyValue{"
                    + ", value=" + value()
                    + ", url=" + url()
                    + ", file=" + file()
                    + ", template=" + template()
                    + ", order=" + order()
                    + '}';
        }
    }

    /**
     * Archetype list without key used in {@link Model}.
     */
    public static class ModelList extends Conditional {

        private final LinkedList<ModelValue> values = new LinkedList<>();
        private final LinkedList<ModelMap> maps = new LinkedList<>();
        private final LinkedList<ModelList> lists = new LinkedList<>();
        private int order = 100;

        /**
         * ListType constructor.
         *
         *  @param order            order attribute
         * @param ifProperties      if attribute
         */
        public ModelList(int order, String ifProperties) {
            super(ifProperties);
            this.order = order;
        }

        /**
         * Get the values element from list element.
         *
         * @return values
         */
        public LinkedList<ModelValue> values() {
            return this.values;
        }

        /**
         * Get the maps element from list element.
         *
         * @return maps
         */
        public LinkedList<ModelMap> maps() {
            return this.maps;
        }

        /**
         * Get the lists element from list element.
         *
         * @return lists
         */
        public LinkedList<ModelList> lists() {
            return this.lists;
        }

        /**
         * Get the list order.
         *
         * @return order
         */
        public int order() {
            return order;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            ModelList m = (ModelList) o;
            return values.equals(m.values);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), values, maps, lists);
        }

        @Override
        public String toString() {
            return "ListType{"
                    + "values=" + values()
                    + "maps=" + maps()
                    + "lists=" + lists()
                    + ", if=" + ifProperties()
                    + '}';
        }
    }

    /**
     * Archetype map without key used in {@link ModelList}.
     */
    public static class ModelMap extends Conditional {

        private final LinkedList<ModelKeyedValue> keyValues = new LinkedList<>();
        private final LinkedList<ModelKeyedList> keyLists = new LinkedList<>();
        private final LinkedList<ModelKeyedMap> keyMaps = new LinkedList<>();
        private int order = 100;

        /**
         * MapType constructor.
         *
         * @param order             order attribute
         * @param ifProperties      if attribute
         */
        public ModelMap(int order, String ifProperties) {
            super(ifProperties);
            this.order = order;
        }

        /**
         * Get the model values with key element from map element.
         *
         * @return values
         */
        public LinkedList<ModelKeyedValue> keyValues() {
            return keyValues;
        }

        /**
         * Get the model lists with key element from map element.
         *
         * @return lists
         */
        public LinkedList<ModelKeyedList> keyLists() {
            return keyLists;
        }

        /**
         * Get the model maps with key element from map element.
         *
         * @return maps
         */
        public LinkedList<ModelKeyedMap> keyMaps() {
            return keyMaps;
        }

        /**
         * Get the map order.
         *
         * @return order
         */
        public int order() {
            return order;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            ModelMap m = (ModelMap) o;
            return keyValues.equals(m.keyValues);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), keyValues, keyLists, keyMaps);
        }

        @Override
        public String toString() {
            return "ListType{"
                    + "keyValues=" + keyValues()
                    + "keyLists=" + keyLists()
                    + "keyMaps=" + keyMaps()
                    + "order=" + order()
                    + ", if=" + ifProperties()
                    + '}';
        }
    }

    /**
     * Model value.
     */
    public static class ModelValue extends Conditional implements Comparable<ModelValue> {

        private String value;
        private final String url;
        private final String file;
        private final String template;
        private int order ;

        /**
         * ValueType constructor.
         *
         * @param url          url attribute
         * @param file         file attribute
         * @param template     template attribute
         * @param order        order attribute
         * @param ifProperties if attribute
         */
        public ModelValue(String url,
                          String file,
                          String template,
                          int order,
                          String ifProperties) {
            super(ifProperties);
            this.url = url;
            this.file = file;
            this.template = template;
            this.order = order;
        }

        /**
         * Get the value.
         *
         * @return value
         */
        public String value() {
            return value;
        }

        /**
         * Get the url.
         *
         * @return url
         */
        public String url() {
            return url;
        }

        /**
         * Get the file.
         *
         * @return file
         */
        public String file() {
            return file;
        }

        /**
         * Get the template.
         *
         * @return template
         */
        public String template() {
            return template;
        }

        /**
         * Get the order.
         *
         * @return order
         */
        public int order() {
            return order;
        }

        /**
         * Set the order.
         *
         * @param order order value
         */
        public void order(int order) {
            this.order = order;
        }

        /**
         * Set the value.
         *
         * @param value value
         */
        public void value(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            ModelValue m = (ModelValue) o;
            return value.equals(m.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), value, url, file, template, order);
        }

        @Override
        public String toString() {
            return "ValueType{"
                    + ", value=" + value()
                    + ", url=" + url()
                    + ", file=" + file()
                    + ", template=" + template()
                    + ", order=" + order()
                    + '}';
        }

        @Override
        public int compareTo(ModelValue value) {
            return Integer.compare(this.order, value.order);
        }
    }

    /**
     * Archetype output.
     */
    public static class Output extends Conditional {

        private Model model;
        private final LinkedList<Transformation> transformations = new LinkedList<>();
        private final LinkedList<FileSets> filesList = new LinkedList<>();
        private final LinkedList<FileSet> fileList = new LinkedList<>();
        private final LinkedList<Template> template = new LinkedList<>();
        private final LinkedList<Templates> templates = new LinkedList<>();

        Output(String ifProperties) {
            super(ifProperties);
        }

        /**
         * Get the applied transformations.
         *
         * @return list of transformation, never {@code null}
         */
        public LinkedList<Transformation> transformations() {
            return transformations;
        }

        /**
         * Get the files elements.
         *
         * @return list of files, never {@code null}
         */
        public LinkedList<FileSets> filesList() {
            return filesList;
        }

        /**
         * Get the file elements.
         *
         * @return list of file, never {@code null}
         */
        public LinkedList<FileSet> fileList() {
            return fileList;
        }

        /**
         * Get the template elements.
         *
         * @return list of template, never {@code null}
         */
        public LinkedList<Template> template() {
            return template;
        }

        /**
         * Get the templates elements.
         *
         * @return list of templates, never {@code null}
         */
        public LinkedList<Templates> templates() {
            return templates;
        }

        /**
         * Get the model element.
         *
         * @return model
         */
        public Model model() {
            return model;
        }

        /**
         * Set the model element.
         *
         * @param model model
         */
        public void model(Model model) {
            this.model = model;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            Output that = (Output) o;
            return model.equals(that.model)
                    && transformations.equals(that.transformations)
                    && filesList.equals(that.filesList)
                    && fileList.equals(that.fileList)
                    && template.equals(that.template)
                    && templates.equals(that.templates);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), model, transformations, filesList, fileList, template, templates);
        }

        @Override
        public String toString() {
            return "Output{"
                    + "model=" + model()
                    + ", transformations=" + transformations()
                    + ", filesList=" + filesList()
                    + ", fileList=" + fileList()
                    + ", template=" + template()
                    + ", templates=" + templates()
                    + '}';
        }

    }

    /**
     * Archetype replace in {@link Transformation}.
     */
    public static class Replacement {

        private final String regex;
        private final String replacement;

        Replacement(String regex, String replacement) {
            this.regex = Objects.requireNonNull(regex, "regex is null");
            this.replacement = Objects.requireNonNull(replacement, "replacement is null");
        }

        /**
         * Get the source regular expression to match the section to be replaced.
         *
         * @return regular expression, never {@code null}
         */
        public String regex() {
            return regex;
        }

        /**
         * Get the replacement for the matches of the source regular expression.
         *
         * @return replacement, never {@code null}
         */
        public String replacement() {
            return replacement;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Replacement that = (Replacement) o;
            return regex.equals(that.regex)
                    && replacement.equals(that.replacement);
        }

        @Override
        public int hashCode() {
            return Objects.hash(regex, replacement);
        }

        @Override
        public String toString() {
            return "Replacement{"
                    + "regex='" + regex + '\''
                    + ", replacement='" + replacement + '\''
                    + '}';
        }
    }

    /**
     * Archetype source.
     */
    public static class Source {

        private final String src;
        private final String url;

        Source(String url, String source) {
            this.src = source;
            this.url = url;
        }

        /**
         * Get the source attribute.
         *
         * @return source
         */
        public String source() {
            return src;
        }

        /**
         * Get the url attribute.
         *
         * @return url
         */
        public String url() {
            return url;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            Source s = (Source) o;
            return src.equals(s.src)
                    && url.equals(s.url);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), src, url);
        }

        @Override
        public String toString() {
            return "Source{"
                    + "src=" + source()
                    + ", url=" + url()
                    + '}';
        }
    }

    /**
     * Archetype step.
     */
    public static class Step extends Conditional {

        private final String label;
        private String help;

        private final LinkedList<ContextBlock> contexts = new LinkedList<>();
        private final LinkedList<Exec> execs = new LinkedList<>();
        private final LinkedList<Source> sources = new LinkedList<>();
        private final LinkedList<InputBlock> inputs = new LinkedList<>();

        protected Step(String label, String ifProperty) {
            super(ifProperty);
            this.label = label;
        }

        /**
         * Get the label.
         *
         * @return label
         */
        public String label() {
            return label;
        }

        /**
         * Get the help.
         *
         * @return help
         */
        public String help() {
            return help;
        }

        /**
         * Get the contexts.
         *
         * @return list of contexts
         */
        public LinkedList<ContextBlock> contexts() {
            return contexts;
        }

        /**
         * Get the execs.
         *
         * @return list of execs
         */
        public LinkedList<Exec> execs() {
            return execs;
        }

        /**
         * Get the sources.
         *
         * @return list of sources
         */
        public LinkedList<Source> sources() {
            return sources;
        }

        /**
         * Get the inputs.
         *
         * @return list of input
         */
        public LinkedList<InputBlock> inputBlocks() {
            return inputs;
        }

        /**
         * Set the help content.
         *
         * @param help help content
         */
        public void help(String help) {
            this.help = help;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            Step step = (Step) o;
            return label.equals(step.label)
                    && help.equals(step.help)
                    && contexts.equals(step.contexts)
                    && execs.equals(step.execs)
                    && sources.equals(step.sources)
                    && inputs.equals(step.inputs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), label, help, contexts, execs, sources, inputs);
        }

        @Override
        public String toString() {
            return "Step{"
                    + "label=" + label()
                    + ", help=" + help()
                    + ", contexts=" + contexts()
                    + ", execs=" + execs()
                    + ", sources=" + sources()
                    + ", inputs=" + inputBlocks()
                    + '}';
        }
    }

    /**
     * Archetype template in {@link Output}.
     */
    public static class Template extends Conditional {

        private Model model;
        private final String engine;
        private final String source;
        private final String target;

        Template(String engine, String source, String target, String ifProperties) {
            super(ifProperties);
            this.engine = engine;
            this.source = source;
            this.target = target;
        }

        /**
         * Get the engine.
         *
         * @return engine
         */
        public String engine() {
            return engine;
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
         * Get the model.
         *
         * @return model
         */
        public Model model() {
            return model;
        }

        /**
         * Set the model.
         *
         * @param model model
         */
        public void model(Model model) {
            this.model = model;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            Template that = (Template) o;
            return model.equals(that.model)
                    && engine.equals(that.engine)
                    && source.equals(that.source)
                    && target.equals(that.target);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), model, engine, source, target);
        }

        @Override
        public String toString() {
            return "Template{"
                    + "model=" + model()
                    + ", engine=" + engine()
                    + ", source=" + source()
                    + ", target=" + target()
                    + '}';
        }

    }

    /**
     * Archetype templates in {@link Output}.
     */
    public static class Templates extends Conditional {

        private Model model;
        private String directory;
        private final LinkedList<String> includes = new LinkedList<>();
        private final LinkedList<String> excludes = new LinkedList<>();
        private final String engine;
        private final String transformation;

        Templates(String engine, String transformation, String ifProperties) {
            super(ifProperties);
            this.engine = engine;
            this.transformation = transformation;
        }

        /**
         * Get the model.
         *
         * @return model
         */
        public Model model() {
            return model;
        }

        /**
         * Get the engine.
         *
         * @return engine
         */
        public String engine() {
            return engine;
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
         * Get the transformation.
         *
         * @return transformation
         */
        public String transformation() {
            return transformation;
        }

        /**
         * Get the include filters.
         *
         * @return list of include filter, never {@code null}
         */
        public LinkedList<String> includes() {
            return includes;
        }

        /**
         * Get the exclude filters.
         *
         * @return list of exclude filter, never {@code null}
         */
        public LinkedList<String> excludes() {
            return excludes;
        }

        /**
         * Set the directory.
         *
         * @param directory directory
         */
        public void directory(String directory) {
            this.directory = directory;
        }

        /**
         * Set the model.
         *
         * @param model model
         */
        public void model(Model model) {
            this.model = model;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            Templates that = (Templates) o;
            return model.equals(that.model)
                    && directory.equals(that.directory)
                    && includes.equals(that.includes)
                    && excludes.equals(that.excludes)
                    && engine.equals(that.engine)
                    && transformation.equals(that.transformation);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), model, directory, includes, excludes, engine, transformation);
        }

        @Override
        public String toString() {
            return "Templates{"
                    + ", transformation=" + transformation()
                    + ", engine=" + engine()
                    + ", directory=" + directory()
                    + ", includes=" + includes()
                    + ", excludes=" + excludes()
                    + ", model=" + model()
                    + '}';
        }
    }

    /**
     * Archetype transformation in {@link Output}.
     */
    public static class Transformation {

        private final String id;
        private final LinkedList<Replacement> replacements;

        Transformation(String id) {
            this.id = Objects.requireNonNull(id, "id is null");
            this.replacements = new LinkedList<>();
        }

        /**
         * Get the transformation id.
         *
         * @return transformation id, never {@code null}
         */
        public String id() {
            return id;
        }

        /**
         * Get the replacements.
         *
         * @return list of replacement, never {@code null}
         */
        public LinkedList<Replacement> replacements() {
            return replacements;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Transformation that = (Transformation) o;
            return id.equals(that.id)
                    && replacements.equals(that.replacements);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, replacements);
        }

        @Override
        public String toString() {
            return "Transformation{"
                    + "id='" + id + '\''
                    + ", replacements=" + replacements
                    + '}';
        }
    }
}
